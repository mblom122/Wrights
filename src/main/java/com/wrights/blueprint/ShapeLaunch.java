package com.wrights.blueprint;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import com.wrights.WrightsConfig;
import com.wrights.item.ControllerItem;
import com.wrights.job.JobManager;
import com.wrights.network.BlueprintPayloads;
import com.wrights.util.Messages;

/**
 * Starts a shape job. Membership is tested while the job runs —
 * never materializes a multi-million BlockPos / schematic list at launch.
 */
public final class ShapeLaunch {
    /** Materials shortage screen id for shape builds. */
    public static final java.util.UUID MATERIALS_ID = new java.util.UUID(0x57726967687473L, 0x5368617065L);

    private ShapeLaunch() {
    }

    public static void place(ServerPlayer player, BlueprintPayloads.ShapePlace payload) {
        if (!ControllerItem.isHeld(player) || payload == null) {
            return;
        }
        boolean exchange = payload.exchange();
        if (WrightsConfig.refuse(player, exchange ? WrightsConfig.allowExchange() : WrightsConfig.allowShape())) {
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
        Shapes.Kind kind = exchange ? Shapes.exchangeKind(payload.kind()) : Shapes.Kind.byId(payload.kind());
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
            target = payload.lookPinned()
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
        boolean deferSupplies = payload.force() && !creative;
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
                exchange ? (payload.fromMaterials() == null ? "" : payload.fromMaterials()) : "",
                silk);
        if (!ok) {
            Messages.actionBar(player, exchange ? "message.wrights.exchange_failed" : "message.wrights.shape_failed");
            return;
        }
        Messages.actionBar(player, exchange ? "message.wrights.exchange_started" : "message.wrights.shape_started", volume);
    }

    private static double range() {
        double reach = WrightsConfig.MAX_RANGE.getAsInt();
        return reach * reach;
    }
}
