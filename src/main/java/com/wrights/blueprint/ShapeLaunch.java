package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsConfig;
import com.wrights.item.ControllerItem;
import com.wrights.job.JobManager;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

/** Builds a shape schematic and starts one wright job for it. */
public final class ShapeLaunch {
    private ShapeLaunch() {
    }

    public static void place(ServerPlayer player, BlueprintPayloads.ShapePlace payload) {
        if (!ControllerItem.isHeld(player) || payload == null || WrightsConfig.refuse(player, WrightsConfig.allowShape())) {
            return;
        }
        Shapes.Kind kind = Shapes.Kind.byId(payload.kind());
        int size = Shapes.clampSize(payload.size());
        int height = Shapes.clampHeight(payload.height());
        int plane = Shapes.clampPlane(payload.plane());
        boolean hollow = payload.hollow();
        boolean replace = payload.replace() && WrightsConfig.allowShapeReplace();
        boolean voidBlocks = payload.voidBlocks() && WrightsConfig.allowVoidBlocks();
        List<Integer> order = ShapeSupply.parse(payload.supplyOrder());
        BlockPos center;
        if (payload.useAnchor()) {
            if (!payload.anchorSet()) {
                Messages.actionBar(player, "message.wrights.shape_need_anchor");
                return;
            }
            center = payload.anchor().immutable();
            if (player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) > range()) {
                Messages.actionBar(player, "message.wrights.shape_far");
                return;
            }
        } else {
            Direction face = Direction.from3DDataValue(Mth.clamp(payload.face(), 0, 5));
            BlockPos clicked = payload.pos();
            if (clicked == null) {
                Messages.actionBar(player, "message.wrights.shape_look");
                return;
            }
            if (player.distanceToSqr(clicked.getCenter()) > range()) {
                Messages.actionBar(player, "message.wrights.place_out_of_range");
                return;
            }
            center = Shapes.lookCenter(clicked, face);
        }
        List<BlockPos> worlds = Shapes.blocks(kind, center, size, height, hollow, plane);
        if (worlds.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_empty");
            return;
        }
        ServerLevel level = player.serverLevel();
        Random random = new Random(player.getUUID().getMostSignificantBits() ^ level.getGameTime() ^ worlds.size());
        List<BlockState> palette = ShapeSupply.palette(player, order, payload.randomizeHotbar());
        if (palette.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_need_block");
            return;
        }
        List<BlockPos> ready = new ArrayList<>(worlds.size());
        List<BlockState> states = new ArrayList<>(worlds.size());
        Map<Item, Integer> need = new HashMap<>();
        for (BlockPos world : worlds) {
            if (!level.isLoaded(world) || !BoxEdits.inWorld(world) || !level.mayInteract(player, world)) {
                continue;
            }
            BlockState current = level.getBlockState(world);
            if (!replace && !voidBlocks && !current.isAir()) {
                continue;
            }
            BlockState next = palette.size() == 1 || !payload.randomizeHotbar()
                    ? palette.get(0)
                    : palette.get(random.nextInt(palette.size()));
            if (next == null || next.isAir()) {
                continue;
            }
            if (!replace && !current.isAir() && current.getBlock() == next.getBlock()) {
                continue;
            }
            ready.add(world.immutable());
            states.add(next);
            Item item = next.getBlock().asItem();
            need.merge(item, 1, Integer::sum);
        }
        if (ready.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_nowhere");
            return;
        }
        if (!player.getAbilities().instabuild) {
            for (Map.Entry<Item, Integer> entry : need.entrySet()) {
                if (!ShapeSupply.canAfford(player, entry.getKey(), entry.getValue(), order)) {
                    Messages.actionBar(player, "message.wrights.shape_missing");
                    return;
                }
            }
        }
        BlockPos origin = Shapes.origin(ready);
        int maxX = origin.getX();
        int maxY = origin.getY();
        int maxZ = origin.getZ();
        List<Schematic.Entry> entries = new ArrayList<>(ready.size());
        for (int i = 0; i < ready.size(); i++) {
            BlockPos world = ready.get(i);
            maxX = Math.max(maxX, world.getX());
            maxY = Math.max(maxY, world.getY());
            maxZ = Math.max(maxZ, world.getZ());
            BlockState state = states.get(i);
            entries.add(new Schematic.Entry(
                    (short) (world.getX() - origin.getX()),
                    (short) (world.getY() - origin.getY()),
                    (short) (world.getZ() - origin.getZ()),
                    state,
                    false));
        }
        int sizeX = maxX - origin.getX() + 1;
        int sizeY = maxY - origin.getY() + 1;
        int sizeZ = maxZ - origin.getZ() + 1;
        int cap = Math.max(1, WrightsConfig.MAX_AXIS.getAsInt());
        if (sizeX > cap || sizeY > cap || sizeZ > cap) {
            Messages.actionBar(player, "message.wrights.shape_too_big");
            return;
        }
        String name = "Shape - " + kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
        Schematic schematic = new Schematic(
                UUID.randomUUID(),
                name,
                sizeX,
                sizeY,
                sizeZ,
                entries,
                origin,
                new BlockPos(maxX, maxY, maxZ),
                "",
                0,
                0,
                0,
                true).withPaste(false, voidBlocks, false, false);
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        library.putSchematic(schematic);
        boolean playerFirst = playerSourcesFirst(order);
        boolean ok = JobManager.startBlueprintBuild(
                player,
                schematic.id(),
                origin,
                sizeX,
                sizeY,
                sizeZ,
                0,
                false,
                voidBlocks,
                false,
                false,
                playerFirst,
                true,
                false,
                false,
                false,
                false);
        if (!ok) {
            library.removeSchematic(schematic.id());
            Messages.actionBar(player, "message.wrights.shape_failed");
            return;
        }
        Messages.actionBar(player, "message.wrights.shape_started", ready.size());
    }

    private static boolean playerSourcesFirst(List<Integer> order) {
        for (int source : order) {
            int id = ShapeSupply.clamp(source);
            if (id == ShapeSupply.BOUND_CHEST) {
                return false;
            }
            if (id == ShapeSupply.MAIN_HAND || id == ShapeSupply.OFFHAND || id == ShapeSupply.ACTION_BAR || id == ShapeSupply.INVENTORY) {
                return true;
            }
        }
        return true;
    }

    private static double range() {
        double reach = WrightsConfig.MAX_RANGE.getAsInt();
        return reach * reach;
    }
}
