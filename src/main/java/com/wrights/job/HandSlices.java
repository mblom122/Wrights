package com.wrights.job;

import com.wrights.job.WorkPicker.Falling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.wrights.drone.CrewSplit;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;

/** Splits a job into slices per wright and balances the open cells between them; also counts what is left. */
public final class HandSlices {
    private HandSlices() {
    }

    private static void migrateSlices(BuildJob job) {
        boolean any = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (!hand.slices.isEmpty()) {
                any = true;
                break;
            }
        }
        ensureSlices(job, !any);
    }

    static void ensureSlices(BuildJob job, boolean includeIdle) {
        for (BuildJob.Hand hand : job.hands()) {
            if (!hand.slices.isEmpty() || hand.lanes <= 0 || (hand.idle && !includeIdle)) {
                continue;
            }
            int lanes = Math.max(1, hand.lanes);
            BuildJob.Slice slice = CrewSplit.slab(
                    lanes,
                    Math.max(1, job.spanX()),
                    Math.max(1, job.spanY()),
                    Math.max(1, job.spanZ()),
                    Math.min(Math.max(0, hand.lane), lanes - 1));
            slice.cursor = Math.max(0, hand.cursor);
            hand.slices.add(slice);
        }
    }

    static void evenOut(BuildJob job, Schematic schematic) {
        if (schematic == null || job.hands().size() < 2) {
            ensureSlices(job, false);
            return;
        }
        ensureSlices(job, false);
        int passes = job.hands().size() * 8;
        for (int pass = 0; pass < passes; pass++) {
            BuildJob.Hand poor = null;
            BuildJob.Hand rich = null;
            BuildJob.Slice donor = null;
            int least = Integer.MAX_VALUE;
            int most = -1;
            for (BuildJob.Hand hand : job.hands()) {
                int load = 0;
                BuildJob.Slice fullest = null;
                int fullestCount = 0;
                for (BuildJob.Slice slice : hand.slices) {
                    int left = countPaste(schematic, slice);
                    load += left;
                    if (left > fullestCount) {
                        fullestCount = left;
                        fullest = slice;
                    }
                }
                if (load < least) {
                    least = load;
                    poor = hand;
                }
                if (load > most) {
                    most = load;
                    rich = hand;
                    donor = fullest;
                }
            }
            if (poor == null || rich == null || poor == rich || donor == null || most < least + 2) {
                return;
            }
            BuildJob.Slice half = splitPaste(schematic, donor);
            if (half == null) {
                return;
            }
            poor.slices.add(half);
            poor.idle = false;
        }
    }

    static void shareWithIdle(ServerLevel level, BuildJob job, Schematic schematic) {
        if (schematic == null || job.hands().size() < 2) {
            return;
        }
        boolean pending = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.idle) {
                pending = true;
                break;
            }
        }
        if (!pending) {
            return;
        }
        migrateSlices(job);
        for (int n = 0; n < job.hands().size(); n++) {
            BuildJob.Hand receiver = null;
            for (BuildJob.Hand hand : job.hands()) {
                if (!anyOpen(level, schematic, job, hand)) {
                    receiver = hand;
                    break;
                }
                hand.idle = false;
            }
            if (receiver == null) {
                return;
            }
            BuildJob.Slice donor = fullestPaste(level, job, schematic, receiver);
            BuildJob.Slice half = donor == null ? null : splitPaste(level, schematic, job, donor);
            if (half == null) {
                return;
            }
            receiver.slices.add(half);
            receiver.idle = false;
        }
    }

    /** A wright with nothing left to place (and nothing left to help with) goes home. */
    static void releaseIdle(ServerLevel level, ServerPlayer player, BuildJob job, Schematic schematic) {
        if (schematic == null || job.hands().size() < 2) {
            return;
        }
        boolean busy = false;
        int going = 0;
        boolean[] open = new boolean[job.hands().size()];
        for (int i = 0; i < job.hands().size(); i++) {
            BuildJob.Hand hand = job.hands().get(i);
            open[i] = anyOpen(level, schematic, job, hand);
            if (hand.idle && !open[i]) {
                going++;
            } else if (!hand.idle) {
                busy = true;
            }
        }
        if (!busy || going == 0) {
            return;
        }
        int slot = 0;
        for (int i = 0; i < job.hands().size(); i++) {
            BuildJob.Hand hand = job.hands().get(i);
            if (!hand.idle || open[i] || hand.entityId == null) {
                continue;
            }
            DroneFleet.recall(level, player, hand.entityId, slot++, going, hand.homePort);
            hand.entityId = null;
        }
    }

    /** A wright with nothing to place until another wright finishes the block under it goes home. */
    private static void parkWaiters(ServerLevel level, ServerPlayer player, BuildJob job, boolean someoneWorking) {
        if (!someoneWorking || job.hands().size() < 2) {
            return;
        }
        boolean othersBusy = false;
        int going = 0;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.waitingOnOthers && hand.entityId != null) {
                going++;
            } else if (!hand.idle && !hand.waitingOnOthers) {
                othersBusy = true;
            }
        }
        if (!othersBusy || going == 0) {
            return;
        }
        int slot = 0;
        for (BuildJob.Hand hand : job.hands()) {
            if (!hand.waitingOnOthers || hand.entityId == null) {
                continue;
            }
            DroneFleet.recall(level, player, hand.entityId, slot++, going, hand.homePort);
            hand.entityId = null;
        }
    }

    static void shareUndo(BuildJob job) {
        if (job.hands().size() < 2) {
            return;
        }
        boolean pending = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.idle) {
                pending = true;
                break;
            }
        }
        if (!pending) {
            return;
        }
        migrateSlices(job);
        for (int n = 0; n < job.hands().size(); n++) {
            BuildJob.Hand receiver = null;
            for (BuildJob.Hand hand : job.hands()) {
                if (countRefunds(job, hand) == 0) {
                    receiver = hand;
                    break;
                }
                hand.idle = false;
            }
            if (receiver == null) {
                return;
            }
            BuildJob.Slice donor = fullestRefund(job, receiver);
            BuildJob.Slice half = donor == null ? null : splitRefunds(job, donor);
            if (half == null) {
                for (BuildJob.Hand hand : job.hands()) {
                    if (countRefunds(job, hand) == 0) {
                        hand.idle = false;
                    }
                }
                return;
            }
            receiver.slices.add(half);
            receiver.idle = false;
        }
    }

    private static int countPaste(Schematic schematic, BuildJob.Slice slice) {
        int count = 0;
        for (int i = Math.max(0, slice.cursor); i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (slice.contains(entry.x(), entry.y(), entry.z())) {
                count++;
            }
        }
        return count;
    }

    /** True if this wright still has at least one unsettled cell (cheap; no world/falling checks). */
    static boolean anyOpen(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        if (!hand.slices.isEmpty()) {
            for (BuildJob.Slice slice : hand.slices) {
                if (anyOpen(level, schematic, job, slice)) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!WorkPicker.ownsEntry(job, hand, entry)) {
                continue;
            }
            if (cheapUnsettled(job, entry)) {
                return true;
            }
        }
        return false;
    }

    static boolean anyOpen(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Slice slice) {
        for (int i = Math.max(0, slice.cursor); i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z())) {
                continue;
            }
            if (cheapUnsettled(job, entry)) {
                return true;
            }
        }
        // Holes behind the cursor still count.
        for (int i = 0; i < Math.min(slice.cursor, schematic.blockCount()); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z())) {
                continue;
            }
            if (cheapUnsettled(job, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cheapUnsettled(BuildJob job, Schematic.Entry entry) {
        return !job.outsideClip(entry) && !CellPlacement.skippedContent(job, entry) && !job.isSettled(entry.x(), entry.y(), entry.z());
    }

    /** Blocks still left to place or skip in this wright's slices. */
    private static int countOpen(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        int count = 0;
        for (BuildJob.Slice slice : hand.slices) {
            count += countOpen(level, schematic, job, slice);
        }
        if (!hand.slices.isEmpty()) {
            return count;
        }
        int total = schematic.blockCount();
        int from = Math.max(0, Math.min(total, hand.cursor));
        int examined = 0;
        for (int offset = 0; offset < total && examined < JobManager.CREW_SCAN_CAP; offset++) {
            int i = (from + offset) % total;
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (!WorkPicker.ownsEntry(job, hand, entry)) {
                continue;
            }
            if (isOpenCell(level, schematic, job, entry)) {
                count++;
            }
        }
        return count;
    }

    private static int countOpen(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Slice slice) {
        int count = 0;
        int total = schematic.blockCount();
        int from = Math.max(0, Math.min(total, slice.cursor));
        int examined = 0;
        for (int offset = 0; offset < total && examined < JobManager.CREW_SCAN_CAP; offset++) {
            int i = (from + offset) % total;
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z())) {
                continue;
            }
            if (isOpenCell(level, schematic, job, entry)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOpenCell(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry) {
        if (job.outsideClip(entry) || CellPlacement.skippedContent(job, entry)) {
            return false;
        }
        // Settled / matching world cells are done — skip the heavier survival checks.
        if (job.isSettled(entry.x(), entry.y(), entry.z())) {
            return false;
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isLoaded(pos) && level.getBlockState(pos).equals(entry.state())) {
            return false;
        }
        return WorkPicker.falling(level, schematic, job, entry) != Falling.SKIP;
    }

    /** True while any schematic cell is still unsettled (O(n) hash lookups only — safe every tick). */
    static boolean jobHasRemainingWork(ServerLevel level, Schematic schematic, BuildJob job) {
        if (schematic == null) {
            return false;
        }
        for (int i = 0; i < schematic.blockCount(); i++) {
            if (cheapUnsettled(job, schematic.entry(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cells that are not skipped forever, but cannot be placed yet (need the block under them,
     * or another support that is still coming). Distinct from {@link BuildJob#skipped()}.
     * Uses a cached hint — a full schematic walk every HUD sync was freezing large builds.
     */
    public static int countHeld(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (job == null || job.finished() || job.undoing() || job.kind() != BuildJob.Kind.PASTE) {
            return 0;
        }
        return job.heldHint();
    }

    static void refreshHeldHint(ServerLevel level, Schematic schematic, BuildJob job) {
        if (schematic == null) {
            job.setHeldHint(0);
            return;
        }
        int held = 0;
        int count = schematic.blockCount();
        int from = Math.max(0, Math.min(count, job.cursor()));
        int examined = 0;
        for (int offset = 0; offset < count && examined < JobManager.CREW_SCAN_CAP; offset++) {
            int i = (from + offset) % count;
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                continue;
            }
            if (WorkPicker.falling(level, schematic, job, entry) == Falling.WAIT) {
                held++;
            }
        }
        job.setHeldHint(held);
    }

    private static int countRefunds(BuildJob job, BuildJob.Hand hand) {
        int count = 0;
        for (BuildJob.Slice slice : hand.slices) {
            count += countRefunds(job, slice);
        }
        return count;
    }

    private static int countRefunds(BuildJob job, BuildJob.Slice slice) {
        int count = 0;
        for (BuildJob.Refund refund : job.refunds()) {
            if (slice.contains(refund.x(), refund.y(), refund.z())) {
                count++;
            }
        }
        return count;
    }

    private static BuildJob.Slice fullestPaste(ServerLevel level, BuildJob job, Schematic schematic, BuildJob.Hand except) {
        BuildJob.Slice donor = null;
        int most = 1;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand == except) {
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                int left = countOpen(level, schematic, job, slice);
                if (left > most) {
                    most = left;
                    donor = slice;
                }
            }
        }
        return donor;
    }

    private static BuildJob.Slice fullestRefund(BuildJob job, BuildJob.Hand except) {
        BuildJob.Slice donor = null;
        int most = 1;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand == except) {
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                int left = countRefunds(job, slice);
                if (left > most) {
                    most = left;
                    donor = slice;
                }
            }
        }
        return donor;
    }

    private static BuildJob.Slice splitPaste(Schematic schematic, BuildJob.Slice slice) {
        int start = Math.max(0, slice.cursor);
        int count = countPaste(schematic, slice);
        if (count < 2) {
            return null;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int at = 0;
        for (int i = start; i < schematic.blockCount() && at < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z())) {
                continue;
            }
            xs[at] = entry.x();
            ys[at] = entry.y();
            zs[at] = entry.z();
            at++;
        }
        return splitPoints(slice, xs, ys, zs, at);
    }

    private static BuildJob.Slice splitPaste(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Slice slice) {
        int count = countOpen(level, schematic, job, slice);
        if (count < 2) {
            return null;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int at = 0;
        for (int i = 0; i < schematic.blockCount() && at < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z()) || !isOpenCell(level, schematic, job, entry)) {
                continue;
            }
            xs[at] = entry.x();
            ys[at] = entry.y();
            zs[at] = entry.z();
            at++;
        }
        return splitPoints(slice, xs, ys, zs, at);
    }

    private static BuildJob.Slice splitRefunds(BuildJob job, BuildJob.Slice slice) {
        int count = countRefunds(job, slice);
        if (count < 2) {
            return null;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int at = 0;
        for (BuildJob.Refund refund : job.refunds()) {
            if (!slice.contains(refund.x(), refund.y(), refund.z())) {
                continue;
            }
            xs[at] = refund.x();
            ys[at] = refund.y();
            zs[at] = refund.z();
            at++;
            if (at >= count) {
                break;
            }
        }
        return splitPoints(slice, xs, ys, zs, at);
    }

    private static BuildJob.Slice splitPoints(BuildJob.Slice slice, int[] xs, int[] ys, int[] zs, int count) {
        if (count < 2) {
            return null;
        }
        int minX = xs[0];
        int maxX = xs[0];
        int minZ = zs[0];
        int maxZ = zs[0];
        for (int i = 1; i < count; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        // Never cut a column in half. The wright who owns the sand also owns the block under it.
        int axis = spanX >= spanZ && spanX > 0 ? 0 : spanZ > 0 ? 2 : -1;
        if (axis < 0) {
            return null;
        }
        int minC = axis == 0 ? minX : minZ;
        int span = axis == 0 ? spanX : spanZ;
        if (span <= 0) {
            return null;
        }
        int[] bins = new int[span + 1];
        for (int i = 0; i < count; i++) {
            int coord = axis == 0 ? xs[i] : axis == 1 ? ys[i] : zs[i];
            bins[coord - minC]++;
        }
        int running = 0;
        int bestCut = -1;
        int bestDiff = Integer.MAX_VALUE;
        for (int bin = 0; bin < bins.length - 1; bin++) {
            running += bins[bin];
            int right = count - running;
            if (running == 0 || right == 0) {
                continue;
            }
            int diff = Math.abs(running - right);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestCut = bin + 1;
            }
        }
        if (bestCut < 0) {
            return null;
        }
        int cut = minC + bestCut;
        BuildJob.Slice given = new BuildJob.Slice();
        given.x0 = slice.x0;
        given.y0 = slice.y0;
        given.z0 = slice.z0;
        given.x1 = slice.x1;
        given.y1 = slice.y1;
        given.z1 = slice.z1;
        given.cursor = slice.cursor;
        if (axis == 0) {
            slice.x1 = cut - 1;
            given.x0 = cut;
        } else if (axis == 1) {
            slice.y1 = cut - 1;
            given.y0 = cut;
        } else {
            slice.z1 = cut - 1;
            given.z0 = cut;
        }
        return given;
    }
}
