package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import com.wrights.util.BlockFilters;
import com.wrights.util.Messages;

/**
 * Starts a shape job. Membership is tested while the job runs —
 * never materializes a multi-million BlockPos / schematic list at launch.
 */
public final class ShapeLaunch {
    /** Materials shortage screen id for shape builds. */
    public static final java.util.UUID MATERIALS_ID = new java.util.UUID(0x57726967687473L, 0x5368617065L);
    /** Materials shortage screen id for exchange builds. */
    public static final java.util.UUID EXCHANGE_MATERIALS_ID = new java.util.UUID(0x57726967687473L, 0x45786368616E67L);

    private ShapeLaunch() {
    }

    public static void place(ServerPlayer player, BlueprintPayloads.ShapePlace payload) {
        if (!ControllerItem.isHeld(player) || payload == null) {
            return;
        }
        boolean exchange = payload.exchange();
        // Place tool reuses ShapePlace with exchange=false; allow Shape or Place tool.
        boolean allowed = exchange
                ? WrightsConfig.allowExchange()
                : (WrightsConfig.allowShape() || WrightsConfig.allowPlaceTool());
        if (WrightsConfig.refuse(player, allowed)) {
            return;
        }
        List<ShapeMaterials.Entry> materials = ShapeMaterials.parse(payload.materials());
        if (materials.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_need_materials");
            return;
        }
        boolean hasState = false;
        for (ShapeMaterials.Entry entry : materials) {
            if (entry != null && entry.state() != null) {
                hasState = true;
                break;
            }
        }
        if (!hasState) {
            Messages.actionBar(player, "message.wrights.shape_need_materials");
            return;
        }
        // Client already resolved ExchangeForm → Kind into payload.kind.
        Shapes.Kind kind = Shapes.Kind.byId(payload.kind());
        int size = Shapes.clampSize(payload.size());
        int height = Shapes.clampHeight(payload.height());
        int thickness = Shapes.clampHeight(payload.thickness());
        int plane = Shapes.clampPlane(payload.plane());
        int yaw = Shapes.clampYaw(payload.yaw());
        int pivot = Shapes.clampPivot(payload.pivot());
        boolean hollow = payload.hollow() && WrightsConfig.allowShapeHollow();
        boolean voidBlocks = payload.voidBlocks() && WrightsConfig.allowShapeVoid();
        boolean silk = exchange && payload.silk();
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
            // Exchange pins on the aimed solid; Shape places into the adjacent cell unless already pinned.
            target = payload.lookPinned() || exchange
                    ? clicked.immutable()
                    : Shapes.lookCorner(clicked, Direction.from3DDataValue(Mth.clamp(payload.face(), 0, 5)));
        }
        Shapes.Bounds box = Shapes.pinnedBounds(kind, target, size, height, thickness, hollow, plane, yaw, pivot);
        BlockPos min = box.min();
        BlockPos max = box.max();
        if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(max)) {
            Messages.actionBar(player, "message.wrights.shape_too_big");
            return;
        }
        int sizeX = box.sizeX();
        int sizeY = box.sizeY();
        int sizeZ = box.sizeZ();
        int axisCap = Math.max(1, WrightsConfig.MAX_AXIS.getAsInt());
        if (sizeX > axisCap || sizeY > axisCap || sizeZ > axisCap) {
            Messages.actionBar(player, "message.wrights.shape_too_big");
            return;
        }
        long volume = (long) sizeX * (long) sizeY * (long) sizeZ;
        int maxBlocks = Math.max(1, WrightsConfig.MAX_BLOCKS.getAsInt());
        if (volume <= 0L || volume > maxBlocks) {
            Messages.actionBar(player, "message.wrights.shape_too_many", maxBlocks);
            return;
        }
        BlockPos center = Shapes.pinnedCenter(kind, target, size, height, thickness, hollow, plane, yaw, pivot);
        ShapeMaterials.FillMode fillMode = ShapeMaterials.FillMode.byId(payload.fillMode());
        int orderBatch = ShapeMaterials.clampBatch(payload.orderBatch());
        long seed = player.getUUID().getMostSignificantBits()
                ^ player.serverLevel().getGameTime()
                ^ volume
                ^ materials.hashCode()
                ^ fillMode.ordinal();
        boolean creative = player.getAbilities().instabuild || player.isSpectator();
        String fromMaterials = exchange ? (payload.fromMaterials() == null ? "" : payload.fromMaterials()) : "";
        // Same gate as blueprint builds: short stock opens Materials needed unless Build anyway (force).
        if (!payload.force() && !creative && player.serverLevel() instanceof ServerLevel level) {
            List<Requirements.Cost> costs = estimateCosts(
                    level,
                    kind,
                    center,
                    size,
                    height,
                    thickness,
                    hollow,
                    plane,
                    yaw,
                    pivot,
                    min,
                    max,
                    voidBlocks,
                    exchange,
                    fromMaterials,
                    materials,
                    fillMode,
                    orderBatch,
                    seed);
            BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
            List<Requirements.Cost> listed = Requirements.ordered(player, costs, session.supplyPos(), session.supplyDimension());
            if (Requirements.anyShort(listed)) {
                BlueprintNetwork.materials(
                        player,
                        exchange ? EXCHANGE_MATERIALS_ID : MATERIALS_ID,
                        true,
                        listed);
                return;
            }
        }
        // Build anyway starts with what you have; when stock runs out the wright goes home and waits.
        boolean deferSupplies = false;
        int drones = Math.max(0, payload.drones());
        boolean placeTool = !exchange && payload.placeTool();
        boolean ok = JobManager.startShapeBuild(
                player,
                min,
                max,
                center,
                kind.ordinal(),
                size,
                height,
                thickness,
                plane,
                yaw,
                pivot,
                hollow,
                voidBlocks,
                ShapeMaterials.encode(materials),
                fillMode.ordinal(),
                orderBatch,
                seed,
                ShapeSupply.encode(order),
                deferSupplies,
                exchange,
                fromMaterials,
                silk,
                drones,
                placeTool);
        if (!ok) {
            Messages.actionBar(player, exchange ? "message.wrights.exchange_failed" : "message.wrights.shape_failed");
            return;
        }
        Messages.actionBar(player, exchange ? "message.wrights.exchange_started" : "message.wrights.shape_started", volume);
    }

    /**
     * Count placeable cells in the shape (or exchangeable solids), then assign the material
     * palette so Materials needed matches what the job will try to spend.
     */
    private static List<Requirements.Cost> estimateCosts(
            ServerLevel level,
            Shapes.Kind kind,
            BlockPos center,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int plane,
            int yaw,
            int pivot,
            BlockPos min,
            BlockPos max,
            boolean voidBlocks,
            boolean exchange,
            String fromMaterials,
            List<ShapeMaterials.Entry> materials,
            ShapeMaterials.FillMode fillMode,
            int orderBatch,
            long seed) {
        BlockPos pinLocal = Shapes.pinLocal(kind, size, height, thickness, hollow, plane, pivot);
        int placeable = 0;
        int budget = Math.min(65_536, Math.max(1, WrightsConfig.MAX_BLOCKS.getAsInt()));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        outer:
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (placeable >= budget) {
                        break outer;
                    }
                    cursor.set(x, y, z);
                    if (!Shapes.containsWorld(
                            kind, cursor, center, pinLocal, size, height, thickness, hollow, plane, yaw)) {
                        continue;
                    }
                    BlockState world = level.getBlockState(cursor);
                    if (exchange) {
                        if (!exchangeable(level, cursor, world, fromMaterials)) {
                            continue;
                        }
                    } else if (!voidBlocks && !world.isAir() && !world.canBeReplaced()) {
                        continue;
                    }
                    placeable++;
                }
            }
        }
        if (placeable <= 0) {
            return List.of();
        }
        List<BlockState> planned = ShapeMaterials.assign(
                materials,
                placeable,
                fillMode,
                orderBatch,
                new Random(seed),
                true,
                item -> Long.MAX_VALUE,
                true);
        Map<Item, Integer> counts = new HashMap<>();
        for (BlockState state : planned) {
            Item payment = BlockFilters.payment(state);
            if (payment != null) {
                counts.merge(payment, 1, Integer::sum);
            }
        }
        List<Requirements.Cost> costs = new ArrayList<>(counts.size());
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            costs.add(new Requirements.Cost(Item.getId(entry.getKey()), entry.getValue()));
        }
        return costs;
    }

    private static boolean exchangeable(ServerLevel level, BlockPos pos, BlockState state, String fromRaw) {
        if (!BlockFilters.canExchange(level, pos, state)) {
            return false;
        }
        List<ShapeMaterials.Entry> from = ShapeMaterials.parse(fromRaw);
        if (from.isEmpty()) {
            return true;
        }
        Item item = state.getBlock().asItem();
        if (!(item instanceof net.minecraft.world.item.BlockItem)) {
            return false;
        }
        for (ShapeMaterials.Entry entry : from) {
            if (entry != null && entry.item() == item) {
                return true;
            }
        }
        return false;
    }

    private static double range() {
        double reach = WrightsConfig.MAX_RANGE.getAsInt();
        return reach * reach;
    }
}
