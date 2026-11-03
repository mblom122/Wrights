package com.wrights.job;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsConfig;
import com.wrights.blueprint.Shapes;
import com.wrights.blueprint.SmallBoxStands;
import com.wrights.drone.WrightTier;
import com.wrights.entity.DroneEntity;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;

/**
 * Shared stand → fly → AOE burst used by Shape, blueprint paste, path Place, mine, and undo.
 * Kind-specific ownership (shape shares vs schematic slices) stays in the callers.
 */
public final class HoverWork {
    private HoverWork() {
    }

    /** True when the block under the wright is {@code stand}. Reach is measured from that block. */
    public static boolean wrightOnStand(DroneEntity drone, BlockPos stand) {
        if (drone == null || stand == null) {
            return false;
        }
        BlockPos foot = BlockPos.containing(drone.getX(), drone.getY() - 0.85D, drone.getZ());
        return foot.equals(stand);
    }

    public static Vec3 hoverAbove(BlockPos stand, int lane) {
        return Vec3.atCenterOf(stand).add(0.0D, 0.85D + (Math.max(0, lane) % 2) * 0.45D, 0.0D);
    }

    /**
     * Move toward the stand. {@code true} while still launching or not on the stand yet —
     * the caller should skip placing this tick.
     */
    public static boolean approachStand(DroneEntity drone, BuildJob.Hand hand, BlockPos stand) {
        return approachStand(drone, hand, stand, null);
    }

    /**
     * Same as {@link #approachStand(DroneEntity, BuildJob.Hand, BlockPos)} and optionally mirrors
     * the drone foot into {@code job}'s last-drone pos (solo paste / undo).
     */
    public static boolean approachStand(DroneEntity drone, BuildJob.Hand hand, BlockPos stand, BuildJob job) {
        if (drone == null || stand == null) {
            return true;
        }
        Vec3 hover = hoverAbove(stand, hand == null ? 0 : hand.lane);
        if (drone.launching()) {
            if (hand != null) {
                hand.lastPos = drone.blockPosition();
            }
            if (job != null) {
                job.setLastDronePos(drone.blockPosition());
            }
            return true;
        }
        if (wrightOnStand(drone, stand)) {
            return false;
        }
        drone.moveToward(hover, CellPlacement.flyStep(hand == null ? drone.rotor() : hand.rotor, drone.distanceToSqr(hover)));
        if (hand != null) {
            hand.lastPos = drone.blockPosition();
        }
        if (job != null) {
            job.setLastDronePos(drone.blockPosition());
        }
        return true;
    }

    /** Count one AOE operation for this stand key when the burst placed or removed something. */
    public static void countOp(BuildJob job, BuildJob.Hand hand, int standKey) {
        if (job == null || hand == null || standKey == Integer.MIN_VALUE) {
            return;
        }
        if (hand.opStand != standKey) {
            job.operationOne();
            hand.opStand = standKey;
        }
    }

    /** Same as {@link #countOp(BuildJob, BuildJob.Hand, int)} using the stand block as the key. */
    public static void countOp(BuildJob job, BuildJob.Hand hand, BlockPos stand) {
        if (stand == null) {
            return;
        }
        countOp(job, hand, Long.hashCode(stand.asLong()));
    }

    /**
     * World block this paste/mine hand should stand on. Small leftover AABBs use
     * {@link SmallBoxStands}; otherwise the seed schematic cell.
     */
    public static BlockPos pasteStand(
            ServerLevel level,
            ServerPlayer player,
            Schematic schematic,
            BuildJob job,
            BuildJob.Hand hand,
            int seedIndex) {
        if (job == null || schematic == null || seedIndex < 0 || seedIndex >= schematic.blockCount()) {
            return null;
        }
        BlockPos small = smallPasteStand(level, player, schematic, job, hand, seedIndex);
        if (small != null) {
            return small;
        }
        Schematic.Entry seed = schematic.entry(seedIndex);
        return job.min().offset(seed.x(), seed.y(), seed.z());
    }

    /**
     * When remaining owned work fits in a ≤3³ box, stand on the predefined stamp that still
     * covers an open cell. {@code null} leaves the seed cell as the stand.
     */
    private static BlockPos smallPasteStand(
            ServerLevel level,
            ServerPlayer player,
            Schematic schematic,
            BuildJob job,
            BuildJob.Hand hand,
            int seedIndex) {
        int reach = WrightTier.operationReachSqr(ShapeJobs.handFabricator(hand));
        if (reach < 2 || job.min() == null) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int found = 0;
        int count = schematic.blockCount();
        int from = Math.max(0, hand == null ? 0 : hand.cursor);
        int examined = 0;
        for (int offset = 0; offset < count && examined < JobManager.CREW_SCAN_CAP; offset++) {
            int i = (from + offset) % count;
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                continue;
            }
            if (hand != null && !WorkPicker.ownsEntry(job, hand, entry)) {
                continue;
            }
            found++;
            minX = Math.min(minX, entry.x());
            minY = Math.min(minY, entry.y());
            minZ = Math.min(minZ, entry.z());
            maxX = Math.max(maxX, entry.x());
            maxY = Math.max(maxY, entry.y());
            maxZ = Math.max(maxZ, entry.z());
            if (found > 27) {
                return null;
            }
        }
        if (found <= 0) {
            return null;
        }
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        if (!SmallBoxStands.applies(sx, sy, sz, reach)) {
            return null;
        }
        int kind = Shapes.Kind.CUBE.ordinal();
        int[][] stands = SmallBoxStands.stands(sx, sy, sz, false, kind, reach);
        if (stands.length == 0) {
            return null;
        }
        int lane = hand == null ? 0 : Math.max(0, hand.lane);
        for (int step = 0; step < stands.length; step++) {
            int[] stand = stands[(lane + step) % stands.length];
            if (pasteStandHasReady(level, player, schematic, job, hand, minX, minY, minZ, sx, sy, sz, stand, reach)) {
                return job.min().offset(minX + stand[0], minY + stand[1], minZ + stand[2]);
            }
        }
        Schematic.Entry seed = schematic.entry(seedIndex);
        return job.min().offset(seed.x(), seed.y(), seed.z());
    }

    private static boolean pasteStandHasReady(
            ServerLevel level,
            ServerPlayer player,
            Schematic schematic,
            BuildJob job,
            BuildJob.Hand hand,
            int ox,
            int oy,
            int oz,
            int sx,
            int sy,
            int sz,
            int[] stand,
            int reach) {
        int count = schematic.blockCount();
        for (int i = 0; i < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            int lx = entry.x() - ox;
            int ly = entry.y() - oy;
            int lz = entry.z() - oz;
            if (lx < 0 || ly < 0 || lz < 0 || lx >= sx || ly >= sy || lz >= sz) {
                continue;
            }
            if (!SmallBoxStands.covers(stand, lx, ly, lz, reach)) {
                continue;
            }
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                continue;
            }
            if (hand != null && !WorkPicker.ownsEntry(job, hand, entry)) {
                continue;
            }
            if (WorkPicker.aimedByOther(job, hand, i)) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Nearest refund to {@code origin} (solo undo has no hand shares). {@code -1} when empty.
     */
    public static int nearestRefund(BuildJob job, Vec3 origin) {
        List<BuildJob.Refund> refunds = job.refunds();
        if (refunds.isEmpty()) {
            return -1;
        }
        if (origin == null || job.min() == null) {
            return refunds.size() - 1;
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = refunds.size() - 1; i >= 0; i--) {
            BuildJob.Refund refund = refunds.get(i);
            BlockPos pos = job.min().offset(refund.x(), refund.y(), refund.z());
            double dist = origin.distanceToSqr(Vec3.atCenterOf(pos));
            if (best < 0 || dist < bestDist || (dist == bestDist && i > best)) {
                best = i;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Remove up to the fabricator budget of refunds in reach of the current hover.
     * {@code CHUNK} means abort the tick (chunk gate); otherwise keep going.
     */
    public static Result burstRemove(
            ServerLevel level,
            ServerPlayer player,
            SchematicLibrary library,
            BuildJob job,
            BuildJob.Hand hand,
            DroneEntity drone,
            BlockPos stand) {
        if (level == null || job == null || drone == null) {
            return Result.DONE;
        }
        int budget = hand != null
                ? CellPlacement.placeBudget(hand)
                : Math.max(1, WrightTier.operationCap(drone.fabricator()));
        int removed = 0;
        for (int n = 0; n < budget; n++) {
            int at = hand == null
                    ? nearestRefund(job, drone.position())
                    : WorkPicker.refundIndex(level, job, hand);
            if (at < 0) {
                if (hand != null) {
                    hand.idle = true;
                }
                break;
            }
            BuildJob.Refund refund = job.refunds().get(at);
            BlockPos pos = job.min().offset(refund.x(), refund.y(), refund.z());
            if (n == 0) {
                if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > JobManager.PLACE_NEAR_SQR) {
                    break;
                }
            } else if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, ShapeJobs.handFabricator(hand))) {
                break;
            }
            job.takeRefund(at);
            if (!level.isLoaded(pos)) {
                if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                    job.restoreRefund(at, refund);
                    return Result.CHUNK;
                }
                job.skippedOne();
                continue;
            }
            JobManager.removeOne(level, player, job, pos, refund, false);
            removed++;
        }
        if (removed > 0 && hand != null) {
            countOp(job, hand, stand);
        } else if (removed > 0) {
            job.operationOne();
        }
        return Result.DONE;
    }

    public enum Result {
        DONE,
        CHUNK
    }
}
