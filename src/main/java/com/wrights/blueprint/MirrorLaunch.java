package com.wrights.blueprint;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsConfig;
import com.wrights.item.ControllerItem;
import com.wrights.job.JobManager;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.BlockEntityNbt;
import com.wrights.util.BlockFilters;
import com.wrights.util.Messages;

/** Starts one wright job for each reflected copy. */
public final class MirrorLaunch {
    public record Result(int started, boolean nowhere) {
    }

    private MirrorLaunch() {
    }

    /** Places the block in the other hand, then asks wrights to build the reflected copies. */
    public static void placeHeld(ServerPlayer player, BlueprintPayloads.MirrorPlace payload) {
        if (!ControllerItem.isHeld(player) || payload == null) {
            return;
        }
        InteractionHand hand = blockHand(player);
        if (hand == null) {
            Messages.actionBar(player, "message.wrights.mirror_need_block");
            return;
        }
        Direction face = Direction.from3DDataValue(Mth.clamp(payload.face(), 0, 5));
        BlockPos clicked = payload.pos();
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        if (player.distanceToSqr(at) > range()) {
            Messages.actionBar(player, "message.wrights.place_out_of_range");
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos side = clicked.relative(face);
        BlockState beforeClick = level.getBlockState(clicked);
        BlockState beforeSide = level.getBlockState(side);
        ItemStack stack = player.getItemInHand(hand);
        UseOnContext context = new UseOnContext(player, hand, new BlockHitResult(at, face, clicked, false));
        BlockPlaceContext planned = new BlockPlaceContext(context);
        if (!planned.canPlace()) {
            return;
        }
        BlockPos target = planned.getClickedPos();
        if (!payload.replace() && !level.getBlockState(target).isAir()) {
            return;
        }
        InteractionResult result = stack.useOn(context);
        if (!result.consumesAction()) {
            return;
        }
        player.swing(hand, true);
        BlockPos placed = null;
        if (!level.getBlockState(clicked).equals(beforeClick)) {
            placed = clicked.immutable();
        } else if (!level.getBlockState(side).equals(beforeSide)) {
            placed = side.immutable();
        }
        if (placed == null || !payload.mirror() || !payload.enabled() || payload.anchor() == null) {
            if (placed != null && payload.enabled() && !payload.mirror()) {
                Messages.actionBar(player, "message.wrights.mirror_need_anchor");
            }
            return;
        }
        if (player.distanceToSqr(payload.anchor().getX() + 0.5D, payload.anchor().getY() + 0.5D, payload.anchor().getZ() + 0.5D) > range()) {
            Messages.actionBar(player, "message.wrights.mirror_far");
            return;
        }
        BlockState state = level.getBlockState(placed);
        boolean copyNbt = payload.copyNbt();
        boolean settings = copyNbt && payload.copySettings() && WrightsConfig.copySettings();
        boolean all = copyNbt && payload.copyAll() && WrightsConfig.copyAll() && player.getAbilities().instabuild && !player.isSpectator();
        if (all) {
            settings = false;
        }
        if (!BlockFilters.canCopy(level, placed, state, false, copyNbt)) {
            if (state.hasBlockEntity()) {
                Messages.actionBar(player, "message.wrights.mirror_skip_nbt");
            }
            return;
        }
        CompoundTag nbt = null;
        boolean full = false;
        if (state.hasBlockEntity() && (settings || all)) {
            BlockEntity entity = level.getBlockEntity(placed);
            if (entity != null) {
                CompoundTag raw = entity.saveWithoutMetadata(level.registryAccess());
                if (all) {
                    nbt = raw.isEmpty() ? null : raw;
                    full = nbt != null;
                } else if (WrightsConfig.settingsAllowed(state.getBlock())) {
                    nbt = BlockEntityNbt.settings(raw);
                }
            }
        }
        Schematic.Entry entry = new Schematic.Entry((short) 0, (short) 0, (short) 0, state, BlockFilters.isSecondary(state), nbt, full);
        Schematic source = new Schematic(UUID.randomUUID(), "Mirror", 1, 1, 1, List.of(entry), placed, placed, "", 0, 0, 0, true);
        Direction plane = Direction.from3DDataValue(Mth.clamp(payload.anchorFace(), 0, 5));
        List<Mirror.Placed> copies = Mirror.place(source, placed, 1, 1, 1, payload.anchor(), plane, payload.pattern(), payload.shapes());
        if (copies.isEmpty()) {
            Messages.actionBar(player, "message.wrights.mirror_same");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        int started = 0;
        for (Mirror.Placed copy : copies) {
            BlockPos min = copy.origin();
            Schematic schematic = copy.schematic();
            BlockPos far = min.offset(schematic.sizeX() - 1, schematic.sizeY() - 1, schematic.sizeZ() - 1);
            if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(far)) {
                continue;
            }
            if (!payload.replace() && (!level.isLoaded(min) || !level.getBlockState(min).isAir())) {
                continue;
            }
            library.putSchematic(schematic);
            boolean replace = payload.replace();
            boolean ok = JobManager.startBlueprintBuild(player, schematic.id(), min, schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), 0, false, replace, replace, replace, true, true, false, copyNbt, settings, all);
            if (!ok) {
                library.removeSchematic(schematic.id());
                continue;
            }
            started++;
        }
        if (started > 0) {
            Messages.actionBar(player, "message.wrights.mirror_started", started);
        }
    }

    private static InteractionHand blockHand(ServerPlayer player) {
        boolean mainTool = player.getMainHandItem().getItem() instanceof ControllerItem;
        boolean offTool = player.getOffhandItem().getItem() instanceof ControllerItem;
        if (mainTool && player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        if (offTool && player.getMainHandItem().getItem() instanceof BlockItem) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static double range() {
        double reach = WrightsConfig.MAX_RANGE.getAsInt();
        return reach * reach;
    }

    public static Result spawn(ServerPlayer player, Schematic source, BlockPos origin, int clipX, int clipY, int clipZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean playerFirst, boolean queue, boolean deferSupplies, boolean placeNbt, boolean placeSettings, boolean placeAll, BlockPos plane, int face, int pattern, boolean shapes) {
        Direction direction = Direction.from3DDataValue(Mth.clamp(face, 0, 5));
        java.util.List<Mirror.Placed> copies = Mirror.place(source, origin, clipX, clipY, clipZ, plane, direction, pattern, shapes);
        if (copies.isEmpty()) {
            return new Result(0, true);
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        int started = 0;
        for (Mirror.Placed placed : copies) {
            BlockPos min = placed.origin();
            Schematic schematic = placed.schematic();
            BlockPos far = min.offset(schematic.sizeX() - 1, schematic.sizeY() - 1, schematic.sizeZ() - 1);
            if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(far)) {
                continue;
            }
            library.putSchematic(schematic);
            boolean ok = JobManager.startBlueprintBuild(player, schematic.id(), min, schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll);
            if (!ok) {
                library.removeSchematic(schematic.id());
                continue;
            }
            started++;
        }
        return new Result(started, false);
    }
}
