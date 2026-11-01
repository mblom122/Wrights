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
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.Requirements;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

/** Builds a shape schematic and starts one wright job for it. */
public final class ShapeLaunch {
    /** Materials shortage screen id for shape builds. */
    public static final UUID MATERIALS_ID = new UUID(0x57726967687473L, 0x5368617065L);

    private ShapeLaunch() {
    }

    public static void place(ServerPlayer player, BlueprintPayloads.ShapePlace payload) {
        if (!ControllerItem.isHeld(player) || payload == null || WrightsConfig.refuse(player, WrightsConfig.allowShape())) {
            return;
        }
        List<ShapeMaterials.Entry> materials = ShapeMaterials.parse(payload.materials());
        if (materials.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_need_materials");
            return;
        }
        Shapes.Kind kind = Shapes.Kind.byId(payload.kind());
        int size = Shapes.clampSize(payload.size());
        int height = Shapes.clampHeight(payload.height());
        int thickness = Shapes.clampHeight(payload.thickness());
        int plane = Shapes.clampPlane(payload.plane());
        int yaw = Shapes.clampYaw(payload.yaw());
        int pivot = Shapes.clampPivot(payload.pivot());
        boolean hollow = payload.hollow() && WrightsConfig.allowShapeHollow();
        boolean voidBlocks = payload.voidBlocks() && WrightsConfig.allowShapeVoid();
        List<Integer> order = ShapeSupply.parse(payload.supplyOrder());
        BlockPos target;
        boolean useAnchor = payload.useAnchor() && WrightsConfig.allowShapeAnchor();
        if (useAnchor) {
            if (!payload.anchorSet()) {
                Messages.actionBar(player, "message.wrights.shape_need_anchor");
                return;
            }
            target = payload.anchor().immutable();
            if (player.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > range()) {
                Messages.actionBar(player, "message.wrights.shape_far");
                return;
            }
        } else {
            BlockPos clicked = payload.pos();
            if (clicked == null) {
                Messages.actionBar(player, "message.wrights.shape_look");
                return;
            }
            if (player.distanceToSqr(clicked.getCenter()) > range()) {
                Messages.actionBar(player, "message.wrights.place_out_of_range");
                return;
            }
            // lookPinned: client already resolved the pin cell (locked look snap).
            target = payload.lookPinned()
                    ? clicked.immutable()
                    : Shapes.lookCorner(clicked, Direction.from3DDataValue(Mth.clamp(payload.face(), 0, 5)));
        }
        // RFTools streams cells; Wrights materializes a list. Cap allocation so 512³ config
        // cannot OutOfMemoryError the JVM (see maxShapeCells).
        int budget = Math.max(1, WrightsConfig.maxShapeCells());
        List<BlockPos> worlds;
        try {
            worlds = Shapes.blocksPinned(
                    kind, target, size, height, thickness, hollow, plane, yaw, pivot, budget + 1);
        } catch (OutOfMemoryError error) {
            System.gc();
            Messages.actionBar(player, "message.wrights.shape_too_many", budget);
            return;
        }
        if (worlds.size() > budget) {
            Messages.actionBar(player, "message.wrights.shape_too_many", budget);
            return;
        }
        if (worlds.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_empty");
            return;
        }
        ServerLevel level = player.serverLevel();
        ShapeMaterials.FillMode fillMode = ShapeMaterials.FillMode.byId(payload.fillMode());
        int orderBatch = ShapeMaterials.clampBatch(payload.orderBatch());
        Random random = new Random(player.getUUID().getMostSignificantBits() ^ level.getGameTime() ^ worlds.size() ^ materials.hashCode() ^ fillMode.ordinal());
        // Filter in place to avoid a second multi-million list.
        int write = 0;
        for (int i = 0; i < worlds.size(); i++) {
            BlockPos world = worlds.get(i);
            if (!level.isLoaded(world) || !BoxEdits.inWorld(world) || !level.mayInteract(player, world)) {
                continue;
            }
            BlockState current = level.getBlockState(world);
            if (!voidBlocks && !current.isAir()) {
                continue;
            }
            worlds.set(write++, world);
        }
        if (write < worlds.size()) {
            worlds.subList(write, worlds.size()).clear();
        }
        List<BlockPos> ready = worlds;
        if (ready.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_nowhere");
            return;
        }
        boolean creative = player.getAbilities().instabuild || player.isSpectator();
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        List<BlockState> planned = ShapeMaterials.assign(
                materials,
                ready.size(),
                fillMode,
                orderBatch,
                random,
                creative,
                item -> ShapeSupply.available(player, item, order),
                payload.force() || creative || fillMode != ShapeMaterials.FillMode.PRIORITY);
        if (planned.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_need_materials");
            return;
        }
        Map<Item, Integer> need = new HashMap<>();
        for (BlockState state : planned) {
            need.merge(state.getBlock().asItem(), 1, Integer::sum);
        }
        if (planned.size() < ready.size()) {
            int shortfall = ready.size() - planned.size();
            Item primary = materials.get(0).item();
            need.merge(primary, shortfall, Integer::sum);
        }
        if (!payload.force() && !creative) {
            List<Requirements.Cost> costs = new ArrayList<>(need.size());
            for (Map.Entry<Item, Integer> entry : need.entrySet()) {
                costs.add(new Requirements.Cost(Item.getId(entry.getKey()), entry.getValue()));
            }
            List<Requirements.Cost> listed = Requirements.ordered(player, costs, session.supplyPos(), session.supplyDimension());
            if (Requirements.anyShort(listed) || planned.size() < ready.size()) {
                BlueprintNetwork.materials(player, MATERIALS_ID, true, listed);
                return;
            }
        }
        if (planned.size() < ready.size()) {
            planned = ShapeMaterials.assign(materials, ready.size(), fillMode, orderBatch, random, creative, item -> ShapeSupply.available(player, item, order), true);
        }
        List<BlockState> states = planned;
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
        String label = kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
        String name = "Shape - " + label;
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
                true).withPaste(false, voidBlocks, voidBlocks, false);
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        library.putSchematic(schematic);
        boolean playerFirst = playerSourcesFirst(order);
        boolean deferSupplies = payload.force() && !player.getAbilities().instabuild && !player.isSpectator();
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
                voidBlocks,
                false,
                playerFirst,
                true,
                deferSupplies,
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
