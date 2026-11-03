package com.wrights.job;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.wrights.WrightsConfig;
import com.wrights.drone.CrewSplit;
import com.wrights.entity.DroneEntity;
import com.wrights.schematic.Schematic;
import com.wrights.util.BlockFilters;

/** Decides which cell a wright works on next: lane and cluster scans, aim claims, support and falling-block ordering. */
public final class WorkPicker {
    private WorkPicker() {
    }

    /** The upper half of a door or bed cannot go down until the block under it is there. */
    private static boolean secondaryWaiting(ServerLevel level, BuildJob job, Schematic.Entry entry) {
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (!level.isLoaded(pos.below())) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() != entry.state().getBlock() && (below.isAir() || below.canBeReplaced());
    }

    static boolean supportWaiting(ServerLevel level, BuildJob job, BuildJob.Hand hand, int index, BlockPos pos, Schematic.Entry entry) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() == entry.state().getBlock() || (!below.isAir() && !below.canBeReplaced())) {
            return false;
        }
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand) {
                continue;
            }
            if (other.slices.isEmpty()) {
                if (other.cursor <= index) {
                    return true;
                }
                continue;
            }
            for (BuildJob.Slice slice : other.slices) {
                if (slice.contains(entry.x(), entry.y() - 1, entry.z()) && slice.cursor <= index) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean satisfied(ServerLevel level, BuildJob job, Schematic.Entry entry) {
        if (job.outsideClip(entry)) {
            return true;
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isOutsideBuildHeight(pos)) {
            return true;
        }
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (job.mining()) {
            return !com.wrights.blueprint.MirrorLaunch.accepts(level, pos, entry.state().getBlock(), job.mineAny());
        }
        if (CellPlacement.skippedContent(job, entry)) {
            return true;
        }
        // Already placed or skipped once. Do not retry if the block later pops (cactus) or is broken.
        if (job.isSettled(entry.x(), entry.y(), entry.z())) {
            return true;
        }
        return level.getBlockState(pos).equals(entry.state());
    }

    static int nextWork(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, int[] budget) {
        int seen = -1;
        while (budget[0] > 0) {
            int index = nextInLane(level, player, schematic, job, hand);
            if (index == JobManager.SUPPORT_WAIT || index == JobManager.NEED_ITEMS) {
                return index;
            }
            if (index < 0) {
                hand.rescan = false;
                return -1;
            }
            if (index == seen) {
                return -2;
            }
            seen = index;
            Schematic.Entry entry = schematic.entry(index);
            if (!satisfied(level, job, entry)) {
                Falling hold = falling(level, schematic, job, entry);
                if (hold == Falling.SKIP) {
                    CellPlacement.giveUpCell(job, entry, index);
                    JobManager.noteProgress(schematic, job, hand, entry, index);
                    budget[0]--;
                    continue;
                }
                if (hold == Falling.WAIT) {
                    return JobManager.SUPPORT_WAIT;
                }
                return index;
            }
            job.settleGhost(entry.x(), entry.y(), entry.z());
            budget[0]--;
            JobManager.noteProgress(schematic, job, hand, entry, index);
        }
        int rest = nextInLane(level, player, schematic, job, hand);
        if (rest == JobManager.SUPPORT_WAIT || rest == JobManager.NEED_ITEMS) {
            return rest;
        }
        return rest < 0 ? -1 : -2;
    }

    static int nextInLane(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        if (hand.slices.isEmpty()) {
            for (BuildJob.Hand other : job.hands()) {
                if (!other.slices.isEmpty()) {
                    return -1;
                }
            }
            java.util.function.Predicate<Schematic.Entry> lane = entry -> CrewSplit.lane(entry.x(), entry.y(), entry.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
            catchUpCursor(level, schematic, job, hand, null, lane);
            int found = scan(level, player, schematic, job, hand, hand.cursor, hand.probe, lane);
            if (found < 0 && Math.max(hand.cursor, hand.probe) > hand.cursor) {
                hand.probe = hand.cursor;
                found = prefer(found, scan(level, player, schematic, job, hand, hand.cursor, hand.cursor, lane));
            }
            if (found >= 0) {
                hand.probe = found;
                return found;
            }
            // Empty share, waiting on floor, or short on items — try helping another wright.
            int borrowed = borrowWork(level, player, schematic, job, hand, found < 0);
            if (borrowed >= 0) {
                return borrowed;
            }
            return found;
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        Vec3 origin = workOrigin(level, job, hand);
        boolean waiting = false;
        boolean shortItems = false;
        for (BuildJob.Slice slice : hand.slices) {
            int found = scanSlice(level, player, schematic, job, hand, slice);
            if (found == JobManager.SUPPORT_WAIT) {
                waiting = true;
                continue;
            }
            if (found == JobManager.NEED_ITEMS) {
                shortItems = true;
                continue;
            }
            if (found < 0) {
                continue;
            }
            // On site: nearest open cell in any owned slice. First dispatch (no origin): lowest index.
            if (origin == null) {
                if (best < 0 || found < best) {
                    best = found;
                }
                continue;
            }
            double dist = entryDistSqr(job, schematic.entry(found), origin);
            if (best < 0 || dist < bestDist || (dist == bestDist && found < best)) {
                best = found;
                bestDist = dist;
            }
        }
        if (best >= 0) {
            return best;
        }
        int borrowed = borrowWork(level, player, schematic, job, hand, !waiting && !shortItems);
        if (borrowed >= 0) {
            return borrowed;
        }
        if (shortItems) {
            return JobManager.NEED_ITEMS;
        }
        return waiting ? JobManager.SUPPORT_WAIT : -1;
    }

    private static int scanSlice(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, BuildJob.Slice slice) {
        java.util.function.Predicate<Schematic.Entry> include = entry -> slice.contains(entry.x(), entry.y(), entry.z());
        catchUpCursor(level, schematic, job, hand, slice, include);
        int start = Math.max(slice.cursor, slice.probe);
        int found = scan(level, player, schematic, job, hand, slice.cursor, start, include);
        // A falling block can sit ahead of the floor it needs. Look back once so that floor is still placed.
        if (found < 0 && start > slice.cursor) {
            slice.probe = slice.cursor;
            found = prefer(found, scan(level, player, schematic, job, hand, slice.cursor, slice.cursor, include));
        }
        if (found >= 0) {
            slice.probe = found;
        }
        return found;
    }

    /** Push cursors past cells that are already done so late-job scans stay cheap. */
    private static void catchUpCursor(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand, BuildJob.Slice slice, java.util.function.Predicate<Schematic.Entry> include) {
        int cursor = slice == null ? hand.cursor : slice.cursor;
        int probe = slice == null ? hand.probe : slice.probe;
        while (cursor < schematic.blockCount()) {
            Schematic.Entry entry = schematic.entry(cursor);
            if (!include.test(entry)) {
                cursor++;
                continue;
            }
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                if (!job.outsideClip(entry)) {
                    job.settleGhost(entry.x(), entry.y(), entry.z());
                }
                cursor++;
                continue;
            }
            if (falling(level, schematic, job, entry) == Falling.SKIP) {
                CellPlacement.giveUpCell(job, entry, cursor);
                cursor++;
                continue;
            }
            break;
        }
        if (probe < cursor) {
            probe = cursor;
        }
        if (slice == null) {
            hand.cursor = cursor;
            hand.probe = probe;
        } else {
            slice.cursor = cursor;
            slice.probe = probe;
            hand.cursor = Math.min(hand.cursor, cursor);
        }
    }

    /** A later look that finds a real block wins. A missing item is remembered when the look-back finds nothing else. */
    private static int prefer(int first, int wrapped) {
        if (wrapped >= 0) {
            return wrapped;
        }
        if (first == JobManager.NEED_ITEMS || wrapped == JobManager.NEED_ITEMS) {
            return JobManager.NEED_ITEMS;
        }
        if (first == JobManager.SUPPORT_WAIT || wrapped == JobManager.SUPPORT_WAIT) {
            return JobManager.SUPPORT_WAIT;
        }
        return wrapped;
    }

    /**
     * Next block this range can act on. A falling block whose floor is not down yet is left in place,
     * so the wright can build that floor and wrap back to the sand or gravel. A block this supply cannot
     * pay for is left in place too, and the scan keeps looking.
     * On site: finish the current aim if it is still in place reach; otherwise prefer a ready cell in
     * the local cluster (spatial probe), then a soft near band, then nearest among a capped index walk.
     * Before the wright has a position, first-by-index.
     */
    private static int scan(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, int cursor, int start, java.util.function.Predicate<Schematic.Entry> include) {
        boolean waiting = false;
        boolean shortItems = false;
        Vec3 origin = workOrigin(level, job, hand);
        boolean nearest = origin != null;
        if (nearest && aimStillOpen(level, player, schematic, job, hand, include, false)) {
            Schematic.Entry aimed = schematic.entry(hand.aim);
            if (ShapeJobs.inOperationReachFromHover(origin, job.min().offset(aimed.x(), aimed.y(), aimed.z()), ShapeJobs.handOps(hand))) {
                return hand.aim;
            }
            // Flying to a distant aim: grab any ready cell we can place without turning around.
            int local = scanCluster(level, player, schematic, job, hand, origin, JobManager.PLACE_NEAR_SQR, JobManager.CLUSTER_RADIUS, include, false, false);
            if (local >= 0) {
                return local;
            }
            return hand.aim;
        }
        if (nearest) {
            int local = scanCluster(level, player, schematic, job, hand, origin, JobManager.PLACE_NEAR_SQR, JobManager.CLUSTER_RADIUS, include, false, false);
            if (local >= 0) {
                return local;
            }
            int near = scanCluster(level, player, schematic, job, hand, origin, JobManager.FLY_NEAR_SQR, JobManager.FLY_NEAR_RADIUS, include, false, false);
            if (near >= 0) {
                return near;
            }
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        int count = schematic.blockCount();
        // Continue from the cursor/probe — never rescan the whole schematic from 0 each place.
        int from = Math.max(0, Math.min(count, nearest ? Math.max(cursor, start) : start));
        int examined = 0;
        for (int offset = 0; offset < count && examined < JobManager.CREW_SCAN_CAP; offset++) {
            int i = nearest ? (from + offset) % count : from + offset;
            if (!nearest && i >= count) {
                break;
            }
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (!include.test(entry)) {
                continue;
            }
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                if (!nearest && i == cursor) {
                    return i;
                }
                continue;
            }
            Falling hold = falling(level, schematic, job, entry);
            if (hold == Falling.WAIT) {
                waiting = true;
                continue;
            }
            if (hold == Falling.SKIP) {
                CellPlacement.giveUpCell(job, entry, i);
                continue;
            }
            if (entry.secondary() && secondaryWaiting(level, job, entry)) {
                waiting = true;
                continue;
            }
            if (!canPay(player, level, job, hand, entry)) {
                shortItems = true;
                continue;
            }
            if (!nearest) {
                return i;
            }
            double dist = entryDistSqr(job, entry, origin);
            if (best < 0 || dist < bestDist || (dist == bestDist && i < best)) {
                best = i;
                bestDist = dist;
            }
        }
        if (best >= 0) {
            return best;
        }
        if (shortItems) {
            return JobManager.NEED_ITEMS;
        }
        return waiting ? JobManager.SUPPORT_WAIT : -1;
    }

    /**
     * Another part of this build that this wright can pay for.
     * {@code takeOwned} is true when this wright's own share is empty, so it may help with
     * cells that still belong to another wright.
     * Skips cells another wright is already flying to so helpers spread out.
     * Once on site, prefers the closest untaken ready cell (cluster first, then soft near, then index).
     */
    static int borrowWork(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, boolean takeOwned) {
        Vec3 origin = workOrigin(level, job, hand);
        if (origin != null && aimStillOpen(level, player, schematic, job, hand, null, takeOwned)) {
            Schematic.Entry aimed = schematic.entry(hand.aim);
            if (ShapeJobs.inOperationReachFromHover(origin, job.min().offset(aimed.x(), aimed.y(), aimed.z()), ShapeJobs.handOps(hand))) {
                return hand.aim;
            }
            int local = scanCluster(level, player, schematic, job, hand, origin, JobManager.PLACE_NEAR_SQR, JobManager.CLUSTER_RADIUS, null, takeOwned, true);
            if (local >= 0) {
                return local;
            }
            return hand.aim;
        }
        if (origin != null) {
            int local = scanCluster(level, player, schematic, job, hand, origin, JobManager.PLACE_NEAR_SQR, JobManager.CLUSTER_RADIUS, null, takeOwned, true);
            if (local >= 0) {
                return local;
            }
            int near = scanCluster(level, player, schematic, job, hand, origin, JobManager.FLY_NEAR_SQR, JobManager.FLY_NEAR_RADIUS, null, takeOwned, true);
            if (near >= 0) {
                return near;
            }
        }
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        int count = schematic.blockCount();
        int from = Math.max(0, Math.min(count, hand.cursor));
        int examined = 0;
        for (int offset = 0; offset < count && examined < JobManager.CREW_SCAN_CAP; offset++) {
            int i = (from + offset) % count;
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                continue;
            }
            if (falling(level, schematic, job, entry) != Falling.READY) {
                continue;
            }
            if (entry.secondary() && secondaryWaiting(level, job, entry)) {
                continue;
            }
            if (aimedByOther(job, hand, i)) {
                continue;
            }
            if (!canPay(player, level, job, hand, entry)) {
                continue;
            }
            if (!takeOwned && heldByOther(level, player, job, hand, entry)) {
                continue;
            }
            if (origin == null) {
                return i;
            }
            double dist = entryDistSqr(job, entry, origin);
            if (best < 0 || dist < bestDist || (dist == bestDist && i < best)) {
                best = i;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Nearest ready, payable cell within {@code reachSqr} of the drone, found by probing schematic
     * positions around the wright (not schematic index order). Falling WAIT/SKIP cells are skipped so
     * a nearby floor can still be chosen first. {@code helper} applies borrow rules (skip others' aims).
     */
    private static int scanCluster(
            ServerLevel level,
            ServerPlayer player,
            Schematic schematic,
            BuildJob job,
            BuildJob.Hand hand,
            Vec3 origin,
            double reachSqr,
            int radius,
            Predicate<Schematic.Entry> include,
            boolean takeOwned,
            boolean helper) {
        BlockPos min = job.min();
        int cx = (int) Math.floor(origin.x) - min.getX();
        int cy = (int) Math.floor(origin.y) - min.getY();
        int cz = (int) Math.floor(origin.z) - min.getZ();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int i = schematic.indexAt(cx + dx, cy + dy, cz + dz);
                    if (i < 0) {
                        continue;
                    }
                    Schematic.Entry entry = schematic.entry(i);
                    if (include != null && !include.test(entry)) {
                        continue;
                    }
                    if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                        continue;
                    }
                    Falling hold = falling(level, schematic, job, entry);
                    if (hold != Falling.READY) {
                        continue;
                    }
                    if (entry.secondary() && secondaryWaiting(level, job, entry)) {
                        continue;
                    }
                    if (!canPay(player, level, job, hand, entry)) {
                        continue;
                    }
                    if (helper) {
                        if (aimedByOther(job, hand, i)) {
                            continue;
                        }
                        if (!takeOwned && heldByOther(level, player, job, hand, entry)) {
                            continue;
                        }
                    }
                    double dist = entryDistSqr(job, entry, origin);
                    BlockPos cell = job.min().offset(entry.x(), entry.y(), entry.z());
                    if (reachSqr <= JobManager.PLACE_NEAR_SQR) {
                        if (!ShapeJobs.inOperationReachFromHover(origin, cell, ShapeJobs.handOps(hand))) {
                            continue;
                        }
                    } else if (dist > reachSqr) {
                        continue;
                    }
                    if (best < 0 || dist < bestDist || (dist == bestDist && i < best)) {
                        best = i;
                        bestDist = dist;
                    }
                }
            }
        }
        return best;
    }

    /** Live drone / hand position, else the job's last known solo drone spot. */
    private static Vec3 workOrigin(ServerLevel level, BuildJob job, BuildJob.Hand hand) {
        Vec3 fromHand = handOrigin(level, hand);
        if (fromHand != null) {
            return fromHand;
        }
        if (job != null && job.lastDronePos() != null) {
            return Vec3.atCenterOf(job.lastDronePos());
        }
        return null;
    }

    /** Live drone position, else last known spot. Null before the wright has been sent out. */
    static Vec3 handOrigin(ServerLevel level, BuildJob.Hand hand) {
        if (hand == null) {
            return null;
        }
        if (hand.entityId != null) {
            Entity entity = level.getEntity(hand.entityId);
            if (entity instanceof DroneEntity drone && drone.isAlive()) {
                return drone.position();
            }
        }
        if (hand.lastPos != null) {
            return Vec3.atCenterOf(hand.lastPos);
        }
        return null;
    }

    private static double entryDistSqr(BuildJob job, Schematic.Entry entry, Vec3 origin) {
        return origin.distanceToSqr(Vec3.atCenterOf(job.min().offset(entry.x(), entry.y(), entry.z())));
    }

    /**
     * True when {@link BuildJob.Hand#aim} is still a placeable cell for this wright.
     * {@code include} limits own-share scans; null means any cell (helper borrow).
     */
    private static boolean aimStillOpen(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, Predicate<Schematic.Entry> include, boolean takeOwned) {
        if (hand == null) {
            return false;
        }
        int index = hand.aim;
        if (index < 0 || index >= schematic.blockCount()) {
            return false;
        }
        Schematic.Entry entry = schematic.entry(index);
        if (include != null && !include.test(entry)) {
            return false;
        }
        if (job.outsideClip(entry) || satisfied(level, job, entry)) {
            return false;
        }
        if (falling(level, schematic, job, entry) != Falling.READY) {
            return false;
        }
        if (entry.secondary() && secondaryWaiting(level, job, entry)) {
            return false;
        }
        if (!canPay(player, level, job, hand, entry)) {
            return false;
        }
        if (include == null && !takeOwned && heldByOther(level, player, job, hand, entry)) {
            return false;
        }
        return !aimedByOther(job, hand, index);
    }

    static boolean aimedByOther(BuildJob job, BuildJob.Hand hand, int index) {
        if (index < 0) {
            return false;
        }
        for (BuildJob.Hand other : job.hands()) {
            if (other != hand && other.aim == index) {
                return true;
            }
        }
        return false;
    }

    /** Mark this cell as taken for the crew; clear any helper that was also aiming here. */
    static void claimAim(BuildJob job, BuildJob.Hand hand, int index) {
        hand.aim = index;
        if (index < 0) {
            return;
        }
        for (BuildJob.Hand other : job.hands()) {
            if (other != hand && other.aim == index) {
                other.aim = -1;
            }
        }
    }

    /**
     * If another wright is already closer to this cell (or tied and claimed first), pick a
     * different open cell so the crew does not stack on one block.
     */
    static int uncrowd(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, int index) {
        if (index < 0 || !aimedByOther(job, hand, index)) {
            return index;
        }
        Schematic.Entry entry = schematic.entry(index);
        Vec3 at = Vec3.atCenterOf(job.min().offset(entry.x(), entry.y(), entry.z()));
        double ours = aimDistance(level, hand, at);
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand || other.aim != index) {
                continue;
            }
            if (aimDistance(level, other, at) <= ours) {
                hand.aim = -1;
                int alt = borrowWork(level, player, schematic, job, hand, true);
                return alt;
            }
            other.aim = -1;
        }
        return index;
    }

    private static double aimDistance(ServerLevel level, BuildJob.Hand hand, Vec3 at) {
        Vec3 origin = handOrigin(level, hand);
        return origin == null ? Double.MAX_VALUE : origin.distanceToSqr(at);
    }

    private static boolean heldByOther(ServerLevel level, ServerPlayer player, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand || other.idle || other.awaitingItems || other.waitingOnOthers || !ownsEntry(job, other, entry)) {
                continue;
            }
            if (canPay(player, level, job, other, entry)) {
                return true;
            }
        }
        return false;
    }

    static boolean ownsEntry(BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        if (hand.slices.isEmpty()) {
            return CrewSplit.lane(entry.x(), entry.y(), entry.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
        }
        for (BuildJob.Slice slice : hand.slices) {
            if (slice.contains(entry.x(), entry.y(), entry.z())) {
                return true;
            }
        }
        return false;
    }

    private static boolean canPay(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        if (job.mining() || entry.secondary() || CellPlacement.freePlace(player)) {
            return true;
        }
        return hasPlaceCosts(player, level, job, hand, entry);
    }

    /** True when inventory / chest / digital can cover the block and any NBT extras for this cell. */
    static boolean hasPlaceCosts(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        for (java.util.Map.Entry<Item, Integer> cost : placeCostCounts(player, job, entry).entrySet()) {
            if (!JobSupply.hasAmount(player, level, job, hand, cost.getKey(), cost.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** Block item plus any NBT extras (Framed Blocks camo, …) that this place will apply, tallied by item. */
    private static java.util.LinkedHashMap<Item, Integer> placeCostCounts(ServerPlayer player, BuildJob job, Schematic.Entry entry) {
        java.util.LinkedHashMap<Item, Integer> costs = new java.util.LinkedHashMap<>();
        Item payment = BlockFilters.payment(entry.state());
        if (payment != null) {
            costs.merge(payment, 1, Integer::sum);
        }
        if (willApplyExtras(player, job, entry)) {
            for (Item extra : com.wrights.util.BlockExtras.of(entry)) {
                costs.merge(extra, 1, Integer::sum);
            }
        }
        return costs;
    }

    /** True when pasted NBT (settings or full) will restore camo / machine config on this cell. */
    static boolean willApplyExtras(@javax.annotation.Nullable ServerPlayer player, BuildJob job, Schematic.Entry entry) {
        if (entry == null || entry.nbt() == null || entry.secondary()) {
            return false;
        }
        boolean full = job.placeAll() && entry.nbtFull() && WrightsConfig.copyAll() && player != null && CellPlacement.creative(player);
        if (full) {
            return true;
        }
        return job.placeSettings()
                && WrightsConfig.copySettings()
                && WrightsConfig.settingsAllowed(entry.state().getBlock());
    }

    enum Falling {
        READY,
        WAIT,
        SKIP
    }

    static Falling falling(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry) {
        if (job.mining()) {
            return Falling.READY;
        }
        if (BlockFilters.falls(entry.state())) {
            return fallingAt(level, schematic, job, entry, new HashSet<>());
        }
        if (BlockFilters.needsGround(entry.state())) {
            return groundAt(level, schematic, job, entry, new HashSet<>());
        }
        return Falling.READY;
    }

    /**
     * A plant with nothing under it, and no block in the build that will land there, cannot be placed.
     * Waiting would sit forever when the ground layer of the pattern is the plant itself.
     */
    private static Falling groundAt(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry, Set<Long> seen) {
        if (!seen.add(BlockPos.asLong(entry.x(), entry.y(), entry.z()))) {
            return Falling.SKIP;
        }
        if (BlockFilters.isCactus(entry.state())) {
            return cactusAt(level, schematic, job, entry, seen);
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        BlockPos below = pos.below();
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || !level.isLoaded(below)) {
            return Falling.WAIT;
        }
        if (entry.state().canSurvive(level, pos)) {
            return Falling.READY;
        }
        int support = schematic.indexAt(entry.x(), entry.y() - 1, entry.z());
        if (support >= 0 && floorComing(level, schematic, job, support, seen)) {
            return Falling.WAIT;
        }
        return Falling.SKIP;
    }

    /**
     * A cactus tower is only valid when sand sits under its lowest cactus. That sand may be inside
     * the blueprint, or — when the cactus is on the bottom layer — already in the world under the paste.
     */
    private static Falling cactusAt(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry, Set<Long> seen) {
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return Falling.WAIT;
        }
        int baseY = entry.y();
        while (baseY > 0) {
            int underCactus = schematic.indexAt(entry.x(), baseY - 1, entry.z());
            if (underCactus < 0 || !BlockFilters.isCactus(schematic.entry(underCactus).state())) {
                break;
            }
            baseY--;
        }
        BlockPos soilPos = job.min().offset(entry.x(), baseY, entry.z()).below();
        if (level.isOutsideBuildHeight(soilPos) || !level.isLoaded(soilPos)) {
            return Falling.WAIT;
        }
        int soilIndex = schematic.indexAt(entry.x(), baseY - 1, entry.z());
        BlockState soilNow = level.getBlockState(soilPos);
        if (BlockFilters.cactusSoil(soilNow)) {
            if (entry.state().canSurvive(level, pos)) {
                return Falling.READY;
            }
            // Upper cactus waiting on a lower cactus in this column.
            if (entry.y() > baseY) {
                int lower = schematic.indexAt(entry.x(), entry.y() - 1, entry.z());
                if (lower >= 0 && floorComing(level, schematic, job, lower, seen)) {
                    return Falling.WAIT;
                }
            }
            // Neighbors (or a missing mid-tower piece that will never land) make this illegal.
            return Falling.SKIP;
        }
        if (soilIndex >= 0) {
            Schematic.Entry soil = schematic.entry(soilIndex);
            if (BlockFilters.cactusSoil(soil.state())) {
                if (floorComing(level, schematic, job, soilIndex, seen)) {
                    return Falling.WAIT;
                }
                // Sand in the pattern will not be placed (void off over stone, etc.).
                return Falling.SKIP;
            }
            // Pattern puts a non-sand block under the tower — cactus can never live there.
            return Falling.SKIP;
        }
        // Lowest cactus is on the bottom of the blueprint: world under the paste must already be sand.
        return Falling.SKIP;
    }

    /**
     * Sand/gravel can be hung in mid-air the same way worldgen does: place them, then cancel the
     * fall tick so they sit until a later neighbor update. Prefer waiting for a still-coming
     * support block so towers build bottom-up when possible; if that support will never land,
     * place suspended instead of holding forever or skipping.
     */
    private static Falling fallingAt(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry, Set<Long> seen) {
        if (!seen.add(BlockPos.asLong(entry.x(), entry.y(), entry.z()))) {
            return Falling.READY;
        }
        BlockPos below = job.min().offset(entry.x(), entry.y(), entry.z()).below();
        if (level.isOutsideBuildHeight(below) || !level.isLoaded(below)) {
            return Falling.WAIT;
        }
        int support = schematic.indexAt(entry.x(), entry.y() - 1, entry.z());
        BlockState under = level.getBlockState(below);
        if (support >= 0 && !under.equals(schematic.entry(support).state())) {
            if (floorComing(level, schematic, job, support, seen)) {
                return Falling.WAIT;
            }
            if (under.isAir() || under.canBeReplaced()) {
                Schematic.Entry floor = schematic.entry(support);
                if (reopen(job, support, floor.x(), floor.y(), floor.z())) {
                    return Falling.WAIT;
                }
            }
        }
        return Falling.READY;
    }

    /** Pulls an unplaced support block back into a wright's queue after the crew was split. */
    private static boolean reopen(BuildJob job, int index, int x, int y, int z) {
        boolean opened = false;
        for (BuildJob.Hand hand : job.hands()) {
            boolean owned = false;
            for (BuildJob.Slice slice : hand.slices) {
                if (!slice.contains(x, y, z)) {
                    continue;
                }
                owned = true;
                if (slice.cursor > index) {
                    slice.cursor = index;
                    slice.probe = index;
                    hand.idle = false;
                    opened = true;
                }
            }
            if (!owned && hand.slices.isEmpty()
                    && CrewSplit.lane(x, y, z, hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane
                    && hand.cursor > index) {
                hand.cursor = index;
                hand.probe = index;
                hand.idle = false;
                opened = true;
            }
        }
        return opened;
    }

    private static boolean floorComing(ServerLevel level, Schematic schematic, BuildJob job, int support, Set<Long> seen) {
        Schematic.Entry floor = schematic.entry(support);
        if (job.isSettled(floor.x(), floor.y(), floor.z())) {
            return false;
        }
        if (!stillQueued(job, floor.x(), floor.y(), floor.z(), support)) {
            return false;
        }
        // Occupied without void, or otherwise never placeable — do not wait forever for it.
        if (!canEventuallyPlace(level, job, floor)) {
            return false;
        }
        if (BlockFilters.falls(floor.state())) {
            return fallingAt(level, schematic, job, floor, seen) != Falling.SKIP;
        }
        if (BlockFilters.needsGround(floor.state())) {
            return groundAt(level, schematic, job, floor, seen) != Falling.SKIP;
        }
        return true;
    }

    /** False when this cell will be skipped (solid in the way, kept machine, clipped content). */
    private static boolean canEventuallyPlace(ServerLevel level, BuildJob job, Schematic.Entry entry) {
        if (job.outsideClip(entry) || CellPlacement.skippedContent(job, entry)) {
            return false;
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        if (!level.isLoaded(pos)) {
            return true;
        }
        BlockState existing = level.getBlockState(pos);
        if (existing.equals(entry.state())) {
            return false;
        }
        if (CellPlacement.keptStored(job, existing, entry.state())) {
            return false;
        }
        if (!CellPlacement.shouldVoid(job, existing, entry.state()) && !existing.isAir() && !existing.canBeReplaced()) {
            return false;
        }
        return true;
    }

    private static boolean stillQueued(BuildJob job, int x, int y, int z, int index) {
        if (job.hands().isEmpty()) {
            return job.cursor() <= index;
        }
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.slices.isEmpty()) {
                if (hand.cursor <= index) {
                    return true;
                }
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                if (slice.contains(x, y, z) && slice.cursor <= index) {
                    return true;
                }
            }
        }
        return false;
    }

    static int nextSolo(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job) {
        int start = Math.max(job.cursor(), job.pasteProbe());
        int found = scan(level, player, schematic, job, null, job.cursor(), start, entry -> true);
        if (found < 0 && start > job.cursor()) {
            job.setPasteProbe(job.cursor());
            found = prefer(found, scan(level, player, schematic, job, null, job.cursor(), job.cursor(), entry -> true));
        }
        if (found >= 0) {
            job.setPasteProbe(found);
        }
        return found;
    }

    static void markSolo(BuildJob job, int index) {
        if (index <= job.cursor()) {
            while (job.cursor() <= index && job.cursor() < job.total()) {
                job.advance();
            }
            if (job.pasteProbe() < job.cursor()) {
                job.setPasteProbe(job.cursor());
            }
            return;
        }
        job.setPasteProbe(index + 1);
    }

    static int refundIndex(BuildJob job, BuildJob.Hand hand) {
        List<BuildJob.Refund> refunds = job.refunds();
        for (int i = refunds.size() - 1; i >= 0; i--) {
            BuildJob.Refund refund = refunds.get(i);
            if (ownsRefund(hand, job, refund)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean ownsRefund(BuildJob.Hand hand, BuildJob job, BuildJob.Refund refund) {
        if (hand.slices.isEmpty()) {
            return CrewSplit.lane(refund.x(), refund.y(), refund.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
        }
        for (BuildJob.Slice slice : hand.slices) {
            if (slice.contains(refund.x(), refund.y(), refund.z())) {
                return true;
            }
        }
        return false;
    }
}
