package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a wright stands while placing a shape, and how many stands that takes.
 * Closed form: no block list, no world lookups. Kind ids match {@link Shapes.Kind} ordinals.
 * {@code kind < 0} is a solid box.
 *
 * <p>Reach 2 (gold and above) places the flat 3×3 on a wall or floor, and on a solid volume
 * the 11-block diagonal lattice {@code x + y + 4z ≡ 0 (mod 11)}. A 3×3×3 sphere is the one
 * stand at its center. Reach 0 is one block per cell.
 *
 * <p>Anything that fits in a 3×3×3 uses the small-box cheatsheet instead of the lattice.
 * A stand only owns cells inside its reach: at reach 2 that is the 19-block sphere, so the
 * eight corners of a solid 3×3×3 are a different stand. The same table is used when a larger
 * job is cut down to a piece of that size. Reach 1 cannot touch a diagonal, so a 3×3 floor
 * is three rows, not one stamp.
 */
public final class ShapeHover {
    public static final int KIND_BOX = -1;
    public static final int CIRCLE = 0;
    public static final int RING = 1;
    public static final int SQUARE = 2;
    public static final int BOX = 3;
    public static final int SPHERE = 4;
    public static final int CUBE = 5;
    public static final int CYLINDER = 6;
    public static final int DOME = 7;
    public static final int PYRAMID = 8;
    public static final int RECTANGLE = 9;

    public static final int DONE = -2;

    private static final int LATTICE = 11;
    private static final int[] BALL_X = new int[19];
    private static final int[] BALL_Y = new int[19];
    private static final int[] BALL_Z = new int[19];
    private static final int[] REP_X = new int[LATTICE];
    private static final int[] REP_Y = new int[LATTICE];
    private static final int[] REP_Z = new int[LATTICE];
    private static final int[] CROSS_X = new int[7];
    private static final int[] CROSS_Y = new int[7];
    private static final int[] CROSS_Z = new int[7];

    static {
        int n = 0;
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    if (x * x + y * y + z * z <= 2) {
                        BALL_X[n] = x;
                        BALL_Y[n] = y;
                        BALL_Z[n] = z;
                        n++;
                    }
                }
            }
        }
        if (n != 19) {
            throw new IllegalStateException("sphere " + n);
        }
        boolean[] seen = new boolean[LATTICE];
        for (int i = 0; i < 19; i++) {
            int h = Math.floorMod(BALL_X[i] + BALL_Y[i] + 4 * BALL_Z[i], LATTICE);
            if (!seen[h]) {
                seen[h] = true;
                REP_X[h] = BALL_X[i];
                REP_Y[h] = BALL_Y[i];
                REP_Z[h] = BALL_Z[i];
            }
        }
        for (int h = 0; h < LATTICE; h++) {
            if (!seen[h]) {
                throw new IllegalStateException("lattice residue " + h);
            }
        }
        int c = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int d = -1; d <= 1; d++) {
                if (axis != 0 && d == 0) {
                    continue;
                }
                CROSS_X[c] = axis == 0 ? d : 0;
                CROSS_Y[c] = axis == 1 ? d : 0;
                CROSS_Z[c] = axis == 2 ? d : 0;
                c++;
            }
        }
        if (c != 7) {
            throw new IllegalStateException("cross " + c);
        }
    }

    public enum Mode {
        /** One cell per block. Reach 0. */
        CELL,
        /** Step of 3 along a beam or pillar. */
        LINE,
        /** 3×3 stamps on a wall or floor. */
        PLATE,
        /** The whole shape sits in the sphere of one stand. */
        ONE,
        /** Solid or hollow 3×3×3 cube: center, then the two opposite faces. */
        CUBE3,
        /** 2×2×2: two opposite corners. */
        PAIR,
        /** Box that fits in 3×3×3: predefined stands, each limited to the reach sphere. */
        SMALL,
        /** Hollow box faces, tiled by 3. */
        SHELL,
        /** Fat volume, reach 2: 11-block diagonal lattice. */
        LATTICE,
        /** Reach 1 and cap 7: the 7-cell cross. */
        CROSS,
        /** Reach 1 and a small cap: lines of 3 through the volume. */
        SWEEP
    }

    public record Query(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr, int cap, int axis, int from, int to) {
        public Query {
            sx = Math.max(1, sx);
            sy = Math.max(1, sy);
            sz = Math.max(1, sz);
            cap = Math.max(1, cap);
            axis = Math.max(0, Math.min(2, axis));
            int span = axis == 0 ? sx : axis == 1 ? sy : sz;
            if (from < 0) {
                from = 0;
            }
            if (to <= from) {
                from = 0;
                to = span;
            }
            to = Math.min(span, to);
        }
    }

    public record Stand(int x, int y, int z, int cursor, boolean done, Mode mode) {
    }

    private ShapeHover() {
    }

    public static int count(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr, int cap) {
        sx = Math.max(1, sx);
        sy = Math.max(1, sy);
        sz = Math.max(1, sz);
        cap = Math.max(1, cap);
        Mode mode = mode(sx, sy, sz, hollow, kind, reachSqr, cap);
        return switch (mode) {
            case CELL -> sx * sy * sz;
            case ONE -> 1;
            case CUBE3 -> 3;
            case PAIR -> 2;
            case SMALL -> smallPlan(new Query(sx, sy, sz, hollow, kind, reachSqr, cap, 0, 0, sx)).n;
            case LINE -> lineCount(sx, sy, sz, reachSqr);
            case PLATE -> plateCount(sx, sy, sz);
            case SHELL -> shellCount(sx, sy, sz);
            case LATTICE -> (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ((long) sx * sy * sz + 10L) / 11L));
            case CROSS -> (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ((long) sx * sy * sz + 6L) / 7L));
            case SWEEP -> sweepCount(sx, sy, sz);
        };
    }

    public static Mode mode(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr, int cap) {
        sx = Math.max(1, sx);
        sy = Math.max(1, sy);
        sz = Math.max(1, sz);
        cap = Math.max(1, cap);
        if (reachSqr <= 0 || cap <= 1) {
            return Mode.CELL;
        }
        if (oneBall(sx, sy, sz, hollow, kind, reachSqr)) {
            return Mode.ONE;
        }
        // A piece or a job that fits in 3×3×3. The 19-block sphere (reach 2) is the limit.
        if (sx <= 3 && sy <= 3 && sz <= 3) {
            return Mode.SMALL;
        }
        int thin = (sx == 1 ? 1 : 0) + (sy == 1 ? 1 : 0) + (sz == 1 ? 1 : 0);
        if (thin >= 2) {
            return Mode.LINE;
        }
        if (thin == 1 && reachSqr >= 2 && cap >= 9) {
            return Mode.PLATE;
        }
        if (hollow && boxShell(kind) && sx > 1 && sy > 1 && sz > 1 && reachSqr >= 2 && cap >= 9) {
            return Mode.SHELL;
        }
        if (reachSqr >= 2) {
            return Mode.LATTICE;
        }
        if (cap >= 7) {
            return Mode.CROSS;
        }
        return Mode.SWEEP;
    }

    public static boolean onBoxShell(int x, int y, int z, int sx, int sy, int sz) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return false;
        }
        return x == 0 || x == sx - 1 || y == 0 || y == sy - 1 || z == 0 || z == sz - 1;
    }

    /** The stand for {@code cursor}, or a done stand. {@code -1} starts at the first stand. */
    public static Stand current(Query q, int cursor) {
        Mode mode = mode(q.sx, q.sy, q.sz, q.hollow, q.kind, q.reachSqr, q.cap);
        if (cursor == DONE) {
            return new Stand(0, 0, 0, DONE, true, mode);
        }
        return switch (mode) {
            case LATTICE -> currentLattice(q, cursor);
            case CROSS -> currentCross(q, cursor);
            case CELL -> currentPacked(q, cursor, Mode.CELL);
            case SWEEP -> currentPacked(q, cursor, Mode.SWEEP);
            case LINE, PLATE, ONE, CUBE3, PAIR, SMALL -> currentOrdinal(q, mode, cursor);
            case SHELL -> currentShell(q, cursor);
        };
    }

    /** Cursor of the stand after {@code cursor}, or {@link #DONE}. */
    public static int advance(Query q, int cursor) {
        Stand stand = current(q, cursor);
        if (stand.done) {
            return DONE;
        }
        return switch (stand.mode) {
            case LATTICE -> scanDiagonal(q, stand.x, stand.y, stand.z, true);
            case CROSS -> scanLattice(q, stand.x, stand.y, stand.z, true, true);
            case CELL -> advanceCell(q, stand.x, stand.y, stand.z);
            case SWEEP -> advanceSweep(q, stand.x, stand.y, stand.z);
            case SHELL -> {
                Stand following = shellAfter(q, stand.cursor);
                yield following.done ? DONE : following.cursor;
            }
            default -> {
                Stand following = currentOrdinal(q, stand.mode, stand.cursor + 1);
                yield following.done ? DONE : following.cursor;
            }
        };
    }

    /**
     * Cells this stand is allowed to touch, in local box coordinates.
     * Returns how many were written. At most 27.
     */
    public static int fillCells(Query q, Stand stand, int[] xs, int[] ys, int[] zs) {
        if (stand.done || xs == null) {
            return 0;
        }
        int n = 0;
        int limit = Math.min(xs.length, Math.min(ys.length, zs.length));
        switch (stand.mode) {
            case CELL -> n = put(q, xs, ys, zs, n, limit, stand.x, stand.y, stand.z);
            case LINE -> n = fillLine(q, stand, xs, ys, zs, n, limit);
            case PLATE, SHELL -> n = fillTile(q, stand, xs, ys, zs, n, limit);
            case ONE, CUBE3, PAIR -> {
                for (int i = 0; i < 19 && n < limit; i++) {
                    n = put(q, xs, ys, zs, n, limit, stand.x + BALL_X[i], stand.y + BALL_Y[i], stand.z + BALL_Z[i]);
                }
            }
            case SMALL -> n = fillSmall(q, stand, xs, ys, zs, n, limit);
            case LATTICE -> {
                for (int i = 0; i < LATTICE && n < limit; i++) {
                    n = put(q, xs, ys, zs, n, limit, stand.x + REP_X[i], stand.y + REP_Y[i], stand.z + REP_Z[i]);
                }
            }
            case CROSS -> {
                for (int i = 0; i < 7 && n < limit; i++) {
                    n = put(q, xs, ys, zs, n, limit, stand.x + CROSS_X[i], stand.y + CROSS_Y[i], stand.z + CROSS_Z[i]);
                }
            }
            case SWEEP -> n = fillSweep(q, stand, xs, ys, zs, n, limit);
            default -> {
            }
        }
        return n;
    }

    private static Stand currentOrdinal(Query q, Mode mode, int cursor) {
        int index = cursor < 0 ? 0 : cursor;
        return switch (mode) {
            case CELL -> cellStand(q, index);
            case LINE -> lineStand(q, index);
            case PLATE -> plateStand(q, index);
            case SHELL -> shellStand(q, index);
            case SWEEP -> sweepStand(q, index);
            case ONE -> oneStand(q, index);
            case CUBE3 -> cubeStand(q, index);
            case PAIR -> pairStand(q, index);
            case SMALL -> smallStand(q, index);
            default -> done(mode);
        };
    }

    private static Stand oneStand(Query q, int index) {
        if (index > 0) {
            return done(Mode.ONE);
        }
        int x = (q.sx - 1) / 2;
        int y = (q.sy - 1) / 2;
        int z = (q.sz - 1) / 2;
        if (!along(q, x, y, z)) {
            return done(Mode.ONE);
        }
        return new Stand(x, y, z, 0, false, Mode.ONE);
    }

    private static Stand cubeStand(Query q, int index) {
        int[] xs = {1, 0, 2};
        int seen = 0;
        for (int i = 0; i < 3; i++) {
            if (!along(q, xs[i], 1, 1)) {
                continue;
            }
            if (seen == index) {
                return new Stand(xs[i], 1, 1, index, false, Mode.CUBE3);
            }
            seen++;
        }
        return done(Mode.CUBE3);
    }

    private static Stand pairStand(Query q, int index) {
        int[] xs = {0, 1};
        int[] ys = {0, 1};
        int[] zs = {0, 1};
        int seen = 0;
        for (int i = 0; i < 2; i++) {
            if (!along(q, xs[i], ys[i], zs[i])) {
                continue;
            }
            if (seen == index) {
                return new Stand(xs[i], ys[i], zs[i], index, false, Mode.PAIR);
            }
            seen++;
        }
        return done(Mode.PAIR);
    }

    /**
     * Predefined stands for a box that fits in 3×3×3. Each stand owns only cells inside its
     * reach sphere (19 blocks at reach 2; the 6 neighbors at reach 1). Cached per query.
     */
    private static final class SmallPlan {
        final int n;
        final int[] x;
        final int[] y;
        final int[] z;
        final int[][] cx;
        final int[][] cy;
        final int[][] cz;

        SmallPlan(int n, int[] x, int[] y, int[] z, int[][] cx, int[][] cy, int[][] cz) {
            this.n = n;
            this.x = x;
            this.y = y;
            this.z = z;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }

        int bursts(int cap) {
            int limit = Math.max(1, cap);
            int ops = 0;
            for (int i = 0; i < n; i++) {
                int cells = cx[i].length;
                ops += (cells + limit - 1) / limit;
            }
            return ops;
        }
    }

    private static final Map<Long, SmallPlan> SMALL_PLANS = new HashMap<>();

    private static long smallKey(Query q) {
        long key = q.sx;
        key = key * 8 + q.sy;
        key = key * 8 + q.sz;
        key = key * 2 + (q.hollow ? 1 : 0);
        key = key * 16 + Math.max(0, q.reachSqr);
        key = key * 64 + Math.max(1, q.cap);
        key = key * 4 + q.axis;
        key = key * 16 + (q.from + 2);
        key = key * 16 + q.to;
        key = key * 32 + (q.kind + 8);
        return key;
    }

    private static SmallPlan smallPlan(Query q) {
        long key = smallKey(q);
        SmallPlan cached = SMALL_PLANS.get(key);
        if (cached != null) {
            return cached;
        }
        SmallPlan plan = buildSmall(q);
        SMALL_PLANS.put(key, plan);
        return plan;
    }

    private static SmallPlan buildSmall(Query q) {
        SmallPlan balls = coverByBall(q);
        SmallPlan lines = coverByLines(q);
        if (lines.n == 0) {
            return balls;
        }
        int cap = Math.max(1, q.cap);
        if (lines.bursts(cap) < balls.bursts(cap)) {
            return lines;
        }
        return balls;
    }

    /** Cells this share still has to place. Hollow box shells skip the interior. */
    private static List<int[]> smallCells(Query q) {
        List<int[]> cells = new ArrayList<>();
        boolean shell = q.hollow && boxShell(q.kind);
        for (int z = 0; z < q.sz; z++) {
            for (int y = 0; y < q.sy; y++) {
                for (int x = 0; x < q.sx; x++) {
                    if (!along(q, x, y, z)) {
                        continue;
                    }
                    if (shell && !onBoxShell(x, y, z, q.sx, q.sy, q.sz)) {
                        continue;
                    }
                    cells.add(new int[] {x, y, z});
                }
            }
        }
        return cells;
    }

    /**
     * Repeatedly stand where the reach sphere still covers the most remaining cells.
     * At reach 2 that sphere is the 19-block shape, so a solid 3×3×3 is the center plus
     * the two face centers that pick up the eight corners.
     */
    private static SmallPlan coverByBall(Query q) {
        List<int[]> cells = smallCells(q);
        int reach = Math.max(0, q.reachSqr);
        boolean[] used = new boolean[cells.size()];
        List<Integer> sx = new ArrayList<>();
        List<Integer> sy = new ArrayList<>();
        List<Integer> sz = new ArrayList<>();
        List<int[]> ownX = new ArrayList<>();
        List<int[]> ownY = new ArrayList<>();
        List<int[]> ownZ = new ArrayList<>();
        int guard = cells.size() + 1;
        while (guard-- > 0) {
            int best = -1;
            int bestCount = 0;
            int bestCenter = Integer.MAX_VALUE;
            int bx = 0;
            int by = 0;
            int bz = 0;
            for (int i = 0; i < cells.size(); i++) {
                // The stand may be a block this shape already placed. The face centers of a
                // 3×3×3 are inside the center sphere, and the corners are reached from there.
                int[] stand = cells.get(i);
                int count = 0;
                for (int j = 0; j < cells.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    if (dist2(stand, cells.get(j)) <= reach) {
                        count++;
                    }
                }
                int center = centerDist(q, stand[0], stand[1], stand[2]);
                if (count > bestCount || (count == bestCount && count > 0 && center < bestCenter)) {
                    best = i;
                    bestCount = count;
                    bestCenter = center;
                    bx = stand[0];
                    by = stand[1];
                    bz = stand[2];
                }
            }
            if (best < 0 || bestCount <= 0) {
                break;
            }
            int[] xs = new int[bestCount];
            int[] ys = new int[bestCount];
            int[] zs = new int[bestCount];
            int n = 0;
            for (int j = 0; j < cells.size(); j++) {
                if (used[j]) {
                    continue;
                }
                int[] cell = cells.get(j);
                if (dist2(bx, by, bz, cell[0], cell[1], cell[2]) > reach) {
                    continue;
                }
                used[j] = true;
                xs[n] = cell[0];
                ys[n] = cell[1];
                zs[n] = cell[2];
                n++;
            }
            orderToward(xs, ys, zs, n, bx, by, bz);
            sx.add(bx);
            sy.add(by);
            sz.add(bz);
            ownX.add(xs);
            ownY.add(ys);
            ownZ.add(zs);
        }
        return packSmall(sx, sy, sz, ownX, ownY, ownZ);
    }

    /** Rows of at most three. Reach 1 cannot take the corners of a 3×3 from the middle. */
    private static SmallPlan coverByLines(Query q) {
        int reach = Math.max(0, q.reachSqr);
        int radius = 0;
        while ((radius + 1) * (radius + 1) <= reach) {
            radius++;
        }
        int span = Math.min(Math.max(1, q.cap), radius == 0 ? 1 : radius * 2 + 1);
        SmallPlan best = null;
        int bestBursts = Integer.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            SmallPlan plan = linesOn(q, axis, span);
            int bursts = plan.bursts(q.cap);
            if (plan.n > 0 && bursts < bestBursts) {
                bestBursts = bursts;
                best = plan;
            }
        }
        return best == null ? new SmallPlan(0, new int[0], new int[0], new int[0], new int[0][], new int[0][], new int[0][]) : best;
    }

    private static SmallPlan linesOn(Query q, int axis, int span) {
        List<int[]> cells = smallCells(q);
        boolean[] used = new boolean[cells.size()];
        List<Integer> sx = new ArrayList<>();
        List<Integer> sy = new ArrayList<>();
        List<Integer> sz = new ArrayList<>();
        List<int[]> ownX = new ArrayList<>();
        List<int[]> ownY = new ArrayList<>();
        List<int[]> ownZ = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (used[i]) {
                continue;
            }
            int[] start = cells.get(i);
            int[] xs = new int[span];
            int[] ys = new int[span];
            int[] zs = new int[span];
            int n = 0;
            for (int step = 0; step < span; step++) {
                int x = start[0] + (axis == 0 ? step : 0);
                int y = start[1] + (axis == 1 ? step : 0);
                int z = start[2] + (axis == 2 ? step : 0);
                int found = -1;
                for (int j = 0; j < cells.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    int[] cell = cells.get(j);
                    if (cell[0] == x && cell[1] == y && cell[2] == z) {
                        found = j;
                        break;
                    }
                }
                if (found < 0) {
                    break;
                }
                used[found] = true;
                xs[n] = x;
                ys[n] = y;
                zs[n] = z;
                n++;
            }
            int mid = n / 2;
            int[] ox = new int[n];
            int[] oy = new int[n];
            int[] oz = new int[n];
            System.arraycopy(xs, 0, ox, 0, n);
            System.arraycopy(ys, 0, oy, 0, n);
            System.arraycopy(zs, 0, oz, 0, n);
            orderToward(ox, oy, oz, n, ox[mid], oy[mid], oz[mid]);
            sx.add(ox[0]);
            sy.add(oy[0]);
            sz.add(oz[0]);
            ownX.add(ox);
            ownY.add(oy);
            ownZ.add(oz);
        }
        return packSmall(sx, sy, sz, ownX, ownY, ownZ);
    }

    private static SmallPlan packSmall(
            List<Integer> sx,
            List<Integer> sy,
            List<Integer> sz,
            List<int[]> ownX,
            List<int[]> ownY,
            List<int[]> ownZ) {
        int n = sx.size();
        int[] x = new int[n];
        int[] y = new int[n];
        int[] z = new int[n];
        int[][] cx = new int[n][];
        int[][] cy = new int[n][];
        int[][] cz = new int[n][];
        for (int i = 0; i < n; i++) {
            x[i] = sx.get(i);
            y[i] = sy.get(i);
            z[i] = sz.get(i);
            cx[i] = ownX.get(i);
            cy[i] = ownY.get(i);
            cz[i] = ownZ.get(i);
        }
        return new SmallPlan(n, x, y, z, cx, cy, cz);
    }

    private static int dist2(int[] a, int[] b) {
        return dist2(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    private static int dist2(int ax, int ay, int az, int bx, int by, int bz) {
        int dx = ax - bx;
        int dy = ay - by;
        int dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int centerDist(Query q, int x, int y, int z) {
        int dx = x * 2 - (q.sx - 1);
        int dy = y * 2 - (q.sy - 1);
        int dz = z * 2 - (q.sz - 1);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Stand cell first, then the rest of its sphere, nearest first. */
    private static void orderToward(int[] xs, int[] ys, int[] zs, int n, int sx, int sy, int sz) {
        for (int i = 1; i < n; i++) {
            int x = xs[i];
            int y = ys[i];
            int z = zs[i];
            int d = dist2(x, y, z, sx, sy, sz);
            int j = i;
            while (j > 0 && dist2(xs[j - 1], ys[j - 1], zs[j - 1], sx, sy, sz) > d) {
                xs[j] = xs[j - 1];
                ys[j] = ys[j - 1];
                zs[j] = zs[j - 1];
                j--;
            }
            xs[j] = x;
            ys[j] = y;
            zs[j] = z;
        }
    }

    private static Stand smallStand(Query q, int index) {
        if (index < 0) {
            index = 0;
        }
        SmallPlan plan = smallPlan(q);
        if (index >= plan.n) {
            return done(Mode.SMALL);
        }
        return new Stand(plan.x[index], plan.y[index], plan.z[index], index, false, Mode.SMALL);
    }

    private static int fillSmall(Query q, Stand stand, int[] xs, int[] ys, int[] zs, int n, int limit) {
        SmallPlan plan = smallPlan(q);
        int index = stand.cursor();
        if (index < 0 || index >= plan.n) {
            return n;
        }
        int[] cx = plan.cx[index];
        int[] cy = plan.cy[index];
        int[] cz = plan.cz[index];
        for (int i = 0; i < cx.length && n < limit; i++) {
            xs[n] = cx[i];
            ys[n] = cy[i];
            zs[n] = cz[i];
            n++;
        }
        return n;
    }

    private static Stand cellStand(Query q, int index) {
        int span = span(q);
        int found = 0;
        for (int i = 0; i < q.sx * q.sy * q.sz; i++) {
            int x = i % q.sx;
            int y = (i / q.sx) % q.sy;
            int z = i / (q.sx * q.sy);
            if (!along(q, x, y, z)) {
                continue;
            }
            if (found == index) {
                return new Stand(x, y, z, index, false, Mode.CELL);
            }
            found++;
            if (found > index && index >= 0) {
                break;
            }
        }
        if (span <= 0) {
            return done(Mode.CELL);
        }
        return done(Mode.CELL);
    }

    private static Stand lineStand(Query q, int index) {
        int axis = longAxis(q.sx, q.sy, q.sz);
        int length = axis == 0 ? q.sx : axis == 1 ? q.sy : q.sz;
        int step = q.reachSqr <= 0 ? 1 : 3;
        int origin = axis == 0 ? 0 : 0;
        if (q.axis != axis) {
            int x = axis == 0 ? origin : 0;
            int y = axis == 1 ? origin : 0;
            int z = axis == 2 ? origin : 0;
            if (!along(q, x, y, z)) {
                return done(Mode.LINE);
            }
            int groups = (length + step - 1) / step;
            if (index < 0 || index >= groups) {
                return done(Mode.LINE);
            }
            return lineAt(axis, index, index, step, length);
        }
        if (q.from >= length || q.to <= q.from) {
            return done(Mode.LINE);
        }
        int first = Math.max(0, q.from) / step;
        int lastCoord = Math.min(length, q.to) - 1;
        if (lastCoord < q.from) {
            return done(Mode.LINE);
        }
        int groups = lastCoord / step - first + 1;
        if (index < 0 || index >= groups) {
            return done(Mode.LINE);
        }
        return lineAt(axis, first + index, index, step, length);
    }

    private static Stand lineAt(int axis, int group, int cursor, int step, int length) {
        int start = group * step;
        int end = Math.min(length, start + step);
        int mid = start + (end - start) / 2;
        int x = axis == 0 ? mid : 0;
        int y = axis == 1 ? mid : 0;
        int z = axis == 2 ? mid : 0;
        return new Stand(x, y, z, cursor, false, Mode.LINE);
    }

    private static Stand plateStand(Query q, int index) {
        int flat = q.sx == 1 ? 0 : q.sy == 1 ? 1 : 2;
        int a = flat == 0 ? q.sy : q.sx;
        int b = flat == 2 ? q.sy : q.sz;
        int na = (a + 2) / 3;
        int nb = (b + 2) / 3;
        int a0 = 0;
        int a1 = na;
        int b0 = 0;
        int b1 = nb;
        if (q.axis == plateAAxis(flat)) {
            if (q.from >= a || q.to <= q.from) {
                return done(Mode.PLATE);
            }
            a0 = Math.max(0, q.from) / 3;
            int last = Math.min(a, q.to) - 1;
            if (last < q.from) {
                return done(Mode.PLATE);
            }
            a1 = Math.min(na, last / 3 + 1);
        } else if (q.axis == plateBAxis(flat)) {
            if (q.from >= b || q.to <= q.from) {
                return done(Mode.PLATE);
            }
            b0 = Math.max(0, q.from) / 3;
            int last = Math.min(b, q.to) - 1;
            if (last < q.from) {
                return done(Mode.PLATE);
            }
            b1 = Math.min(nb, last / 3 + 1);
        } else if (!along(q, 0, 0, 0)) {
            return done(Mode.PLATE);
        }
        int da = a1 - a0;
        int db = b1 - b0;
        if (da <= 0 || db <= 0 || index < 0 || index >= da * db) {
            return done(Mode.PLATE);
        }
        int ta = a0 + (index % da);
        int tb = b0 + (index / da);
        int[] xyz = plateAnchor(q, flat, ta, tb, a, b);
        return new Stand(xyz[0], xyz[1], xyz[2], index, false, Mode.PLATE);
    }

    private static Stand currentShell(Query q, int cursor) {
        if (cursor >= 0) {
            int[] anchor = shellAnchor(q.sx, q.sy, q.sz, cursor);
            if (anchor != null && shellTileHits(q, cursor)) {
                return new Stand(anchor[0], anchor[1], anchor[2], cursor, false, Mode.SHELL);
            }
        }
        return shellAfter(q, cursor);
    }

    /** Next shell tile after {@code ordinal}, or a done stand. {@code -1} starts at the first tile. */
    private static Stand shellAfter(Query q, int ordinal) {
        int total = shellCount(q.sx, q.sy, q.sz);
        for (int next = ordinal + 1; next < total; next++) {
            int[] anchor = shellAnchor(q.sx, q.sy, q.sz, next);
            if (anchor == null || !shellTileHits(q, next)) {
                continue;
            }
            return new Stand(anchor[0], anchor[1], anchor[2], next, false, Mode.SHELL);
        }
        return done(Mode.SHELL);
    }

    private static Stand shellStand(Query q, int index) {
        int total = shellCount(q.sx, q.sy, q.sz);
        int span = q.axis == 0 ? q.sx : q.axis == 1 ? q.sy : q.sz;
        if (q.from <= 0 && q.to >= span) {
            if (index < 0 || index >= total) {
                return done(Mode.SHELL);
            }
            int[] direct = shellAnchor(q.sx, q.sy, q.sz, index);
            if (direct != null) {
                return new Stand(direct[0], direct[1], direct[2], index, false, Mode.SHELL);
            }
        }
        int seen = 0;
        for (int ordinal = 0; ordinal < total; ordinal++) {
            int[] anchor = shellAnchor(q.sx, q.sy, q.sz, ordinal);
            if (anchor == null) {
                continue;
            }
            if (!shellTileHits(q, ordinal)) {
                continue;
            }
            if (seen == index) {
                return new Stand(anchor[0], anchor[1], anchor[2], index, false, Mode.SHELL);
            }
            seen++;
        }
        return done(Mode.SHELL);
    }

    private static Stand sweepStand(Query q, int index) {
        int nx = (q.sx + 2) / 3;
        int seen = 0;
        for (int z = 0; z < q.sz; z++) {
            for (int y = 0; y < q.sy; y++) {
                for (int g = 0; g < nx; g++) {
                    int start = g * 3;
                    int end = Math.min(q.sx, start + 3);
                    int mid = start + (end - start) / 2;
                    boolean hit = false;
                    for (int x = start; x < end; x++) {
                        if (along(q, x, y, z)) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        continue;
                    }
                    if (seen == index) {
                        return new Stand(mid, y, z, index, false, Mode.SWEEP);
                    }
                    seen++;
                }
            }
        }
        return done(Mode.SWEEP);
    }

    private static Stand currentLattice(Query q, int cursor) {
        if (cursor < 0) {
            int packed = firstLattice(q, false);
            if (packed < 0) {
                return done(Mode.LATTICE);
            }
            return unpack(q, packed, Mode.LATTICE);
        }
        int[] p = unpackParts(q, cursor);
        if (!latticeCenter(p[0], p[1], p[2], false) || !covers(q, p[0], p[1], p[2], false)) {
            int packed = firstLattice(q, false);
            if (packed < 0) {
                return done(Mode.LATTICE);
            }
            return unpack(q, packed, Mode.LATTICE);
        }
        return new Stand(p[0], p[1], p[2], cursor, false, Mode.LATTICE);
    }

    private static Stand currentCross(Query q, int cursor) {
        if (cursor < 0) {
            int packed = firstLattice(q, true);
            if (packed < 0) {
                return done(Mode.CROSS);
            }
            return unpack(q, packed, Mode.CROSS);
        }
        int[] p = unpackParts(q, cursor);
        if (!latticeCenter(p[0], p[1], p[2], true) || !covers(q, p[0], p[1], p[2], true)) {
            int packed = firstLattice(q, true);
            if (packed < 0) {
                return done(Mode.CROSS);
            }
            return unpack(q, packed, Mode.CROSS);
        }
        return new Stand(p[0], p[1], p[2], cursor, false, Mode.CROSS);
    }

    private static Stand currentPacked(Query q, int cursor, Mode mode) {
        if (cursor < 0) {
            int packed = mode == Mode.CELL ? firstCell(q) : firstSweep(q);
            return packed < 0 ? done(mode) : unpack(q, packed, mode);
        }
        int[] p = unpackParts(q, cursor);
        boolean ok = mode == Mode.CELL
                ? inside(q, p[0], p[1], p[2]) && along(q, p[0], p[1], p[2])
                : sweepCovers(q, p[0], p[1], p[2]);
        if (!ok) {
            int packed = mode == Mode.CELL ? firstCell(q) : firstSweep(q);
            return packed < 0 ? done(mode) : unpack(q, packed, mode);
        }
        return new Stand(p[0], p[1], p[2], cursor, false, mode);
    }

    private static int firstCell(Query q) {
        return slabCell(q, 0, 0, 0, false);
    }

    private static int advanceCell(Query q, int x, int y, int z) {
        return slabCell(q, x, y, z, true);
    }

    /** Next owned cell in x, then y, then z. The share is one solid slab, so this does not scan the box. */
    private static int slabCell(Query q, int x, int y, int z, boolean after) {
        if (q.sx <= 0 || q.sy <= 0 || q.sz <= 0 || q.from >= q.to) {
            return DONE;
        }
        if (after) {
            x++;
        }
        if (q.axis == 0) {
            if (x < q.from) {
                x = q.from;
            }
            if (x >= q.to || x >= q.sx) {
                x = q.from;
                y++;
            }
            if (y >= q.sy) {
                y = 0;
                z++;
            }
            if (z >= q.sz || x < q.from || x >= q.sx || x >= q.to) {
                return DONE;
            }
            return pack(q, x, y, z);
        }
        if (q.axis == 1) {
            if (x >= q.sx) {
                x = 0;
                y++;
            }
            if (y >= q.to || y >= q.sy) {
                x = 0;
                y = q.from;
                z++;
            }
            if (y < q.from) {
                x = 0;
                y = q.from;
            }
            if (z >= q.sz || y < q.from || y >= q.to || y >= q.sy) {
                return DONE;
            }
            return pack(q, x, y, z);
        }
        if (x >= q.sx) {
            x = 0;
            y++;
        }
        if (y >= q.sy) {
            y = 0;
            z++;
        }
        if (z < q.from) {
            x = 0;
            y = 0;
            z = q.from;
        }
        if (z >= q.to || z >= q.sz) {
            return DONE;
        }
        return pack(q, x, y, z);
    }

    private static int firstSweep(Query q) {
        return sweepAt(q, 0, 0, 0);
    }

    private static int advanceSweep(Query q, int x, int y, int z) {
        int start = x - Math.floorMod(x, 3) + 3;
        if (start >= q.sx) {
            start = 0;
            y++;
            if (y >= q.sy) {
                y = 0;
                z++;
            }
        }
        return sweepAt(q, start, y, z);
    }

    private static int sweepAt(Query q, int start, int y, int z) {
        if (q.from >= q.to || z >= q.sz) {
            return DONE;
        }
        if (q.axis == 0) {
            if (q.from >= q.sx) {
                return DONE;
            }
            int rowStart = q.from - Math.floorMod(q.from, 3);
            if (start < q.from) {
                start = rowStart;
            }
            if (start >= q.to || start >= q.sx) {
                start = rowStart;
                y++;
                if (y >= q.sy) {
                    y = 0;
                    z++;
                }
            }
            if (z >= q.sz || y >= q.sy || start >= q.sx || start >= q.to) {
                return DONE;
            }
        } else if (q.axis == 1) {
            if (y < q.from) {
                y = q.from;
                start = 0;
            }
            if (y >= q.to || y >= q.sy || z >= q.sz || start >= q.sx) {
                return DONE;
            }
        } else {
            if (z < q.from) {
                z = q.from;
                y = 0;
                start = 0;
            }
            if (z >= q.to || z >= q.sz || y >= q.sy || start >= q.sx) {
                return DONE;
            }
        }
        int end = Math.min(q.sx, start + 3);
        if (end <= start) {
            return DONE;
        }
        return pack(q, start + (end - start) / 2, y, z);
    }

    private static boolean sweepCovers(Query q, int x, int y, int z) {
        int start = x - Math.floorMod(x, 3);
        int end = Math.min(q.sx, start + 3);
        for (int xx = start; xx < end; xx++) {
            if (inside(q, xx, y, z) && along(q, xx, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static int firstLattice(Query q, boolean cross) {
        if (cross) {
            return scanLattice(q, -1, q.sy + 1, -2, true, false);
        }
        return scanDiagonal(q, -1, q.sy + 1, -2, false);
    }

    /**
     * Lattice centers in diagonal order. Consecutive stands on one diagonal are {@code (1,-1,0)},
     * which is inside the arrive radius, so the wright places again on the next tick.
     */
    private static int scanDiagonal(Query q, int x, int y, int z, boolean strict) {
        int x0 = centerLo(q, 0);
        int x1 = centerHi(q, 0, q.sx);
        int y0 = centerLo(q, 1);
        int y1 = centerHi(q, 1, q.sy);
        int z0 = centerLo(q, 2);
        int z1 = centerHi(q, 2, q.sz);
        int zStart = strict ? z : z0;
        for (int zz = zStart; zz <= z1; zz++) {
            int base = Math.floorMod(-4 * zz, LATTICE);
            int sMin = x0 + y0;
            int sMax = x1 + y1;
            int s = sMin + Math.floorMod(base - Math.floorMod(sMin, LATTICE), LATTICE);
            for (; s <= sMax; s += LATTICE) {
                int xa = Math.max(x0, s - y1);
                int xb = Math.min(x1, s - y0);
                if (strict && zz == z && s == x + y) {
                    xa = Math.max(xa, x + 1);
                } else if (strict && zz == z && s < x + y) {
                    continue;
                }
                for (int xx = xa; xx <= xb; xx++) {
                    int yy = s - xx;
                    if (covers(q, xx, yy, zz, false)) {
                        return pack(q, xx, yy, zz);
                    }
                }
            }
        }
        return DONE;
    }

    /** Iron cross centers, x then y then z. The separating step is longer than the lattice hop. */
    private static int scanLattice(Query q, int x, int y, int z, boolean cross, boolean strict) {
        int z0 = centerLo(q, 2);
        int z1 = centerHi(q, 2, q.sz);
        int y0 = centerLo(q, 1);
        int y1 = centerHi(q, 1, q.sy);
        int x0 = centerLo(q, 0);
        int x1 = centerHi(q, 0, q.sx);
        int zStart = strict ? z : z0;
        for (int zz = zStart; zz <= z1; zz++) {
            int yStart = strict && zz == z ? y : y0;
            for (int yy = yStart; yy <= y1; yy++) {
                int xStart = strict && zz == z && yy == y ? x + 1 : x0;
                if (xStart > x1) {
                    continue;
                }
                int xx = align(xStart, yy, zz, cross);
                for (; xx <= x1; xx += cross ? 7 : LATTICE) {
                    if (covers(q, xx, yy, zz, cross)) {
                        return pack(q, xx, yy, zz);
                    }
                }
            }
        }
        return DONE;
    }

    private static int align(int xStart, int y, int z, boolean cross) {
        int mod = cross ? 7 : LATTICE;
        int need = cross
                ? Math.floorMod(-2 * y - 3 * z, mod)
                : Math.floorMod(-y - 4 * z, mod);
        int delta = Math.floorMod(need - Math.floorMod(xStart, mod), mod);
        return xStart + delta;
    }

    private static boolean latticeCenter(int x, int y, int z, boolean cross) {
        if (cross) {
            return Math.floorMod(x + 2 * y + 3 * z, 7) == 0;
        }
        return Math.floorMod(x + y + 4 * z, LATTICE) == 0;
    }

    private static boolean covers(Query q, int x, int y, int z, boolean cross) {
        if (!latticeCenter(x, y, z, cross)) {
            return false;
        }
        int n = cross ? 7 : LATTICE;
        for (int i = 0; i < n; i++) {
            int cx = x + (cross ? CROSS_X[i] : REP_X[i]);
            int cy = y + (cross ? CROSS_Y[i] : REP_Y[i]);
            int cz = z + (cross ? CROSS_Z[i] : REP_Z[i]);
            if (inside(q, cx, cy, cz) && along(q, cx, cy, cz)) {
                return true;
            }
        }
        return false;
    }

    private static int fillLine(Query q, Stand stand, int[] xs, int[] ys, int[] zs, int n, int limit) {
        int axis = longAxis(q.sx, q.sy, q.sz);
        int length = axis == 0 ? q.sx : axis == 1 ? q.sy : q.sz;
        int coord = axis == 0 ? stand.x : axis == 1 ? stand.y : stand.z;
        int step = q.reachSqr <= 0 ? 1 : 3;
        int start = coord - (coord % step);
        int end = Math.min(length, start + step);
        for (int c = start; c < end; c++) {
            int x = axis == 0 ? c : 0;
            int y = axis == 1 ? c : 0;
            int z = axis == 2 ? c : 0;
            n = put(q, xs, ys, zs, n, limit, x, y, z);
        }
        return n;
    }

    private static int fillSweep(Query q, Stand stand, int[] xs, int[] ys, int[] zs, int n, int limit) {
        int start = stand.x - (stand.x % 3);
        int end = Math.min(q.sx, start + 3);
        for (int x = start; x < end; x++) {
            n = put(q, xs, ys, zs, n, limit, x, stand.y, stand.z);
        }
        return n;
    }

    private static int fillTile(Query q, Stand stand, int[] xs, int[] ys, int[] zs, int n, int limit) {
        if (stand.mode == Mode.PLATE) {
            int flat = q.sx == 1 ? 0 : q.sy == 1 ? 1 : 2;
            int a = flat == 0 ? q.sy : q.sx;
            int b = flat == 2 ? q.sy : q.sz;
            int[] ij = plateIndex(flat, stand, a, b);
            return fillPlateTile(q, flat, ij[0], ij[1], a, b, xs, ys, zs, n, limit);
        }
        return fillShellTile(q, stand, xs, ys, zs, n, limit);
    }

    private static int fillPlateTile(Query q, int flat, int ta, int tb, int a, int b, int[] xs, int[] ys, int[] zs, int n, int limit) {
        int a0 = ta * 3;
        int b0 = tb * 3;
        int a1 = Math.min(a, a0 + 3);
        int b1 = Math.min(b, b0 + 3);
        for (int ib = b0; ib < b1; ib++) {
            for (int ia = a0; ia < a1; ia++) {
                int x;
                int y;
                int z;
                if (flat == 0) {
                    x = 0;
                    y = ia;
                    z = ib;
                } else if (flat == 1) {
                    x = ia;
                    y = 0;
                    z = ib;
                } else {
                    x = ia;
                    y = ib;
                    z = 0;
                }
                n = put(q, xs, ys, zs, n, limit, x, y, z);
            }
        }
        return n;
    }

    private static int fillShellTile(Query q, Stand stand, int[] xs, int[] ys, int[] zs, int n, int limit) {
        int ordinal = stand.cursor;
        int[] box = shellTileBox(q.sx, q.sy, q.sz, ordinal);
        if (box == null) {
            return n;
        }
        for (int z = box[4]; z <= box[5]; z++) {
            for (int y = box[2]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[1]; x++) {
                    if (!onBoxShell(x, y, z, q.sx, q.sy, q.sz)) {
                        continue;
                    }
                    n = put(q, xs, ys, zs, n, limit, x, y, z);
                }
            }
        }
        return n;
    }

    private static int put(Query q, int[] xs, int[] ys, int[] zs, int n, int limit, int x, int y, int z) {
        if (n >= limit || !inside(q, x, y, z) || !along(q, x, y, z)) {
            return n;
        }
        xs[n] = x;
        ys[n] = y;
        zs[n] = z;
        return n + 1;
    }

    private static boolean along(Query q, int x, int y, int z) {
        int c = q.axis == 0 ? x : q.axis == 1 ? y : z;
        return c >= q.from && c < q.to;
    }

    private static boolean inside(Query q, int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < q.sx && y < q.sy && z < q.sz;
    }

    private static int span(Query q) {
        return q.to - q.from;
    }

    /** Centers that can touch this share. A rep is at most one block off its center. */
    private static int centerLo(Query q, int axis) {
        if (q.axis != axis) {
            return -1;
        }
        return Math.max(-1, q.from - 1);
    }

    private static int centerHi(Query q, int axis, int size) {
        if (q.axis != axis) {
            return size;
        }
        return Math.min(size, Math.max(-1, q.to));
    }

    private static int plateAAxis(int flat) {
        return flat == 0 ? 1 : 0;
    }

    private static int plateBAxis(int flat) {
        return flat == 2 ? 1 : 2;
    }

    /**
     * Bits for one axis. Coordinates run from -1 through {@code size}, and the three widths
     * fit in a positive int for every box the config allows (axis 4096, volume at most 512³).
     */
    private static int axisBits(int size) {
        int range = Math.max(1, size) + 2;
        int bits = 1;
        while ((1 << bits) < range && bits < 16) {
            bits++;
        }
        return bits;
    }

    private static int pack(Query q, int x, int y, int z) {
        int bx = axisBits(q.sx);
        int by = axisBits(q.sy);
        int px = x + 1;
        int py = y + 1;
        int pz = z + 1;
        if (px < 0 || py < 0 || pz < 0 || px >= (1 << bx) || py >= (1 << by) || pz >= (1 << axisBits(q.sz))) {
            return DONE;
        }
        if (bx + by + axisBits(q.sz) > 30) {
            return DONE;
        }
        return px | (py << bx) | (pz << (bx + by));
    }

    private static int[] unpackParts(Query q, int packed) {
        int bx = axisBits(q.sx);
        int by = axisBits(q.sy);
        int x = (packed & ((1 << bx) - 1)) - 1;
        int y = ((packed >> bx) & ((1 << by) - 1)) - 1;
        int z = (packed >>> (bx + by)) - 1;
        return new int[] {x, y, z};
    }

    private static Stand unpack(Query q, int packed, Mode mode) {
        int[] p = unpackParts(q, packed);
        return new Stand(p[0], p[1], p[2], packed, false, mode);
    }

    private static Stand done(Mode mode) {
        return new Stand(0, 0, 0, DONE, true, mode);
    }

    private static boolean oneBall(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr) {
        if (reachSqr < 2) {
            return false;
        }
        if ((kind == SPHERE || kind == CIRCLE || kind == DOME) && sx <= 3 && sy <= 3 && sz <= 3) {
            return true;
        }
        int cx = (sx - 1) / 2;
        int cy = (sy - 1) / 2;
        int cz = (sz - 1) / 2;
        int far = 0;
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int d = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                    if (d > far) {
                        far = d;
                    }
                }
            }
        }
        return far <= reachSqr && !(hollow && boxShell(kind) && sx == 3 && sy == 3 && sz == 3);
    }

    private static boolean boxShell(int kind) {
        return kind == BOX || kind == CUBE || kind == KIND_BOX;
    }

    private static int lineCount(int sx, int sy, int sz, int reachSqr) {
        int length = Math.max(sx, Math.max(sy, sz));
        int step = reachSqr <= 0 ? 1 : 3;
        return (length + step - 1) / step;
    }

    private static int plateCount(int sx, int sy, int sz) {
        int flat = sx == 1 ? 0 : sy == 1 ? 1 : 2;
        int a = flat == 0 ? sy : sx;
        int b = flat == 2 ? sy : sz;
        return ((a + 2) / 3) * ((b + 2) / 3);
    }

    static int shellCount(int sx, int sy, int sz) {
        if (sx <= 1 || sy <= 1 || sz <= 1) {
            return plateCount(Math.max(sx, 1), Math.max(sy, 1), Math.max(sz, 1));
        }
        int n = 2 * tiles(sx, sy);
        n += 2 * tiles(sy, Math.max(0, sz - 2));
        n += 2 * tiles(Math.max(0, sx - 2), Math.max(0, sz - 2));
        return Math.max(1, n);
    }

    private static int tiles(int a, int b) {
        if (a <= 0 || b <= 0) {
            return 0;
        }
        return ((a + 2) / 3) * ((b + 2) / 3);
    }

    private static int sweepCount(int sx, int sy, int sz) {
        return ((sx + 2) / 3) * sy * sz;
    }

    private static int longAxis(int sx, int sy, int sz) {
        if (sx >= sy && sx >= sz) {
            return 0;
        }
        if (sz >= sy) {
            return 2;
        }
        return 1;
    }

    private static int[] plateAnchor(Query q, int flat, int ta, int tb, int a, int b) {
        int a0 = ta * 3;
        int b0 = tb * 3;
        int a1 = Math.min(a, a0 + 3);
        int b1 = Math.min(b, b0 + 3);
        int ia = a0 + (a1 - a0) / 2;
        int ib = b0 + (b1 - b0) / 2;
        if (flat == 0) {
            return new int[] {0, ia, ib};
        }
        if (flat == 1) {
            return new int[] {ia, 0, ib};
        }
        return new int[] {ia, ib, 0};
    }

    private static int[] plateIndex(int flat, Stand stand, int a, int b) {
        int ia = flat == 0 ? stand.y : stand.x;
        int ib = flat == 2 ? stand.y : stand.z;
        return new int[] {ia / 3, ib / 3};
    }

    private static boolean tileHitsShare(Query q, int flat, int ta, int tb, int a, int b) {
        int a0 = ta * 3;
        int b0 = tb * 3;
        int a1 = Math.min(a, a0 + 3);
        int b1 = Math.min(b, b0 + 3);
        for (int ib = b0; ib < b1; ib++) {
            for (int ia = a0; ia < a1; ia++) {
                int x = flat == 0 ? 0 : ia;
                int y = flat == 0 ? ia : flat == 1 ? 0 : ib;
                int z = flat == 2 ? 0 : ib;
                if (flat == 1) {
                    z = ib;
                }
                if (along(q, x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shellTileHits(Query q, int ordinal) {
        int[] box = shellTileBox(q.sx, q.sy, q.sz, ordinal);
        if (box == null) {
            return false;
        }
        for (int z = box[4]; z <= box[5]; z++) {
            for (int y = box[2]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[1]; x++) {
                    if (onBoxShell(x, y, z, q.sx, q.sy, q.sz) && along(q, x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Inclusive tile box, or null when the ordinal is past the faces. */
    private static int[] shellTileBox(int sx, int sy, int sz, int ordinal) {
        int cursor = ordinal;
        for (int face = 0; face < 6; face++) {
            int[] span = faceSpan(sx, sy, sz, face);
            if (span == null) {
                continue;
            }
            int na = tiles(span[1], 1) == 0 ? 0 : (span[1] + 2) / 3;
            int nb = (span[2] + 2) / 3;
            int count = na * nb;
            if (count <= 0) {
                continue;
            }
            if (cursor >= count) {
                cursor -= count;
                continue;
            }
            int tb = cursor / na;
            int ta = cursor % na;
            int a0 = span[3] + ta * 3;
            int b0 = span[4] + tb * 3;
            int a1 = Math.min(span[3] + span[1], a0 + 3) - 1;
            int b1 = Math.min(span[4] + span[2], b0 + 3) - 1;
            int x0;
            int x1;
            int y0;
            int y1;
            int z0;
            int z1;
            if (span[0] == 0) {
                x0 = a0;
                x1 = a1;
                y0 = b0;
                y1 = b1;
                z0 = z1 = face == 0 ? 0 : sz - 1;
            } else if (span[0] == 1) {
                y0 = a0;
                y1 = a1;
                z0 = b0;
                z1 = b1;
                x0 = x1 = face == 2 ? 0 : sx - 1;
            } else {
                x0 = a0;
                x1 = a1;
                z0 = b0;
                z1 = b1;
                y0 = y1 = face == 4 ? 0 : sy - 1;
            }
            return new int[] {x0, x1, y0, y1, z0, z1};
        }
        return null;
    }

    private static int[] shellAnchor(int sx, int sy, int sz, int ordinal) {
        int[] box = shellTileBox(sx, sy, sz, ordinal);
        if (box == null) {
            return null;
        }
        return new int[] {
            box[0] + (box[1] - box[0]) / 2,
            box[2] + (box[3] - box[2]) / 2,
            box[4] + (box[5] - box[4]) / 2
        };
    }

    /**
     * Face span: axis (0 x, 1 y, 2 z of the varying pair's first), length a, length b, origin a, origin b.
     * Faces: z=0, z=max, x=0, x=max, y=0, y=max. Edge faces omit the rim already owned by an earlier face.
     */
    private static int[] faceSpan(int sx, int sy, int sz, int face) {
        return switch (face) {
            case 0, 1 -> new int[] {0, sx, sy, 0, 0};
            case 2, 3 -> sz <= 2 ? null : new int[] {1, sy, sz - 2, 0, 1};
            case 4, 5 -> sx <= 2 || sz <= 2 ? null : new int[] {2, sx - 2, sz - 2, 1, 1};
            default -> null;
        };
    }

    /**
     * Standalone check. Run from the repo with the mod classes, or compile this file alone.
     */
    public static void main(String[] args) {
        int reach = 2;
        int cap = 19;
        expect(count(3, 3, 3, false, SPHERE, reach, cap), 1, "sphere 3");
        expect(count(3, 3, 3, false, CUBE, reach, cap), 3, "cube 3");
        expect(count(1, 100, 1, false, KIND_BOX, reach, cap), 34, "pillar");
        expect(count(100, 1, 100, false, KIND_BOX, reach, cap), 1156, "floor");
        expect(count(100, 100, 100, true, CUBE, reach, cap), 6734, "hollow cube");
        expect(count(3, 3, 3, false, CUBE, 0, 1), 27, "no aoe cube");
        expect(mode(100, 100, 100, false, CUBE, reach, cap), Mode.LATTICE, "fat lattice");
        coverCube(5);
        coverCube(8);
        coverShares(8);
        Query sphere = new Query(3, 3, 3, false, SPHERE, reach, cap, 0, 0, 3);
        Stand mid = current(sphere, -1);
        if (mid.done || mid.x != 1 || mid.y != 1 || mid.z != 1) {
            throw new IllegalStateException("sphere stand " + mid);
        }
        Query wide = new Query(4096, 4096, 8, false, KIND_BOX, reach, cap, 0, 0, 4096);
        int packed = pack(wide, 4095, 100, 7);
        int[] back = unpackParts(wide, packed);
        if (packed < 0 || back[0] != 4095 || back[1] != 100 || back[2] != 7) {
            throw new IllegalStateException("wide pack " + packed + " " + back[0] + "," + back[1] + "," + back[2]);
        }
        int[] edge = unpackParts(wide, pack(wide, -1, 4096, 0));
        if (edge[0] != -1 || edge[1] != 4096 || edge[2] != 0) {
            throw new IllegalStateException("edge pack " + edge[0] + "," + edge[1] + "," + edge[2]);
        }
        coverPlate(9);
        coverCells(4, 3, 2);
        coverSweep(6, 4, 3);
        coverShell(6);
        expect(count(3, 1, 3, false, KIND_BOX, 2, 19), 1, "floor 3");
        expect(count(3, 3, 1, false, SQUARE, 2, 19), 1, "wall 3");
        expect(count(2, 2, 2, false, KIND_BOX, 2, 19), 2, "cube 2");
        expect(count(3, 1, 3, false, KIND_BOX, 1, 7), 3, "floor 3 reach 1");
        Query floor = new Query(3, 1, 3, false, KIND_BOX, 2, 19, 0, 0, 3);
        Stand floorStand = current(floor, -1);
        if (floorStand.done || floorStand.x != 1 || floorStand.y != 0 || floorStand.z != 1) {
            throw new IllegalStateException("floor stand " + floorStand);
        }
        coverSmall(3, 3, 3, 2, 19, 3);
        coverSmall(3, 2, 2, 2, 19, -1);
        coverSmall(2, 3, 2, 2, 19, -1);
        coverSmall(3, 3, 2, 2, 19, -1);
        coverSmall(3, 1, 3, 1, 7, 3);
        System.out.println("ShapeHover checks passed");
    }

    /** Every cell of a small box belongs to one stand, and that stand can actually touch it. */
    private static void coverSmall(int sx, int sy, int sz, int reach, int cap, int expectStands) {
        Query q = new Query(sx, sy, sz, false, KIND_BOX, reach, cap, 0, 0, sx);
        boolean[] hit = new boolean[sx * sy * sz];
        int cursor = -1;
        int stands = 0;
        int[] xs = new int[27];
        int[] ys = new int[27];
        int[] zs = new int[27];
        int guard = sx * sy * sz + 2;
        while (guard-- > 0) {
            Stand stand = current(q, cursor);
            if (stand.done()) {
                break;
            }
            stands++;
            int cells = fillCells(q, stand, xs, ys, zs);
            for (int i = 0; i < cells; i++) {
                int d = (xs[i] - stand.x()) * (xs[i] - stand.x())
                        + (ys[i] - stand.y()) * (ys[i] - stand.y())
                        + (zs[i] - stand.z()) * (zs[i] - stand.z());
                if (d > reach) {
                    throw new IllegalStateException("stand cannot touch " + xs[i] + "," + ys[i] + "," + zs[i]);
                }
                int id = xs[i] + ys[i] * sx + zs[i] * sx * sy;
                if (hit[id]) {
                    throw new IllegalStateException("cell twice " + xs[i] + "," + ys[i] + "," + zs[i]);
                }
                hit[id] = true;
            }
            int next = advance(q, stand.cursor());
            if (next == stand.cursor()) {
                throw new IllegalStateException("small cursor stuck");
            }
            cursor = next;
        }
        for (int i = 0; i < hit.length; i++) {
            if (!hit[i]) {
                throw new IllegalStateException("small missed " + sx + "x" + sy + "x" + sz + " #" + i);
            }
        }
        if (expectStands >= 0 && stands != expectStands) {
            throw new IllegalStateException(sx + "x" + sy + "x" + sz + " stands " + stands);
        }
    }

    private static void coverCube(int n) {
        Query q = new Query(n, n, n, false, CUBE, 2, 19, 0, 0, n);
        boolean[] hit = new boolean[n * n * n];
        int cursor = -1;
        int hovers = 0;
        int guard = n * n * n + 5;
        while (guard-- > 0) {
            Stand stand = current(q, cursor);
            if (stand.done) {
                break;
            }
            hovers++;
            int[] xs = new int[27];
            int[] ys = new int[27];
            int[] zs = new int[27];
            int cells = fillCells(q, stand, xs, ys, zs);
            for (int i = 0; i < cells; i++) {
                int id = xs[i] + ys[i] * n + zs[i] * n * n;
                if (hit[id]) {
                    throw new IllegalStateException("double " + n + " at " + xs[i] + "," + ys[i] + "," + zs[i]);
                }
                hit[id] = true;
            }
            int next = advance(q, stand.cursor);
            if (next == stand.cursor) {
                throw new IllegalStateException("stuck cursor");
            }
            cursor = next;
        }
        for (int i = 0; i < hit.length; i++) {
            if (!hit[i]) {
                throw new IllegalStateException("miss " + n + " id " + i + " hovers " + hovers);
            }
        }
    }

    private static void coverShares(int n) {
        boolean[] hit = new boolean[n * n * n];
        int mid = n / 2;
        coverShare(n, 0, mid, hit);
        coverShare(n, mid, n, hit);
        for (int i = 0; i < hit.length; i++) {
            if (!hit[i]) {
                throw new IllegalStateException("share miss " + i);
            }
        }
    }

    private static void coverShare(int n, int from, int to, boolean[] hit) {
        Query q = new Query(n, n, n, false, CUBE, 2, 19, 0, from, to);
        int cursor = -1;
        int guard = n * n * n + 5;
        while (guard-- > 0) {
            Stand stand = current(q, cursor);
            if (stand.done) {
                break;
            }
            int[] xs = new int[27];
            int[] ys = new int[27];
            int[] zs = new int[27];
            int cells = fillCells(q, stand, xs, ys, zs);
            for (int i = 0; i < cells; i++) {
                if (xs[i] < from || xs[i] >= to) {
                    throw new IllegalStateException("share leak");
                }
                int id = xs[i] + ys[i] * n + zs[i] * n * n;
                if (hit[id]) {
                    throw new IllegalStateException("share double");
                }
                hit[id] = true;
            }
            cursor = advance(q, stand.cursor);
        }
    }

    private static void coverPlate(int n) {
        boolean[] hit = new boolean[n * n];
        int mid = n / 2;
        walkPlate(n, 0, mid, hit);
        walkPlate(n, mid, n, hit);
        for (int i = 0; i < hit.length; i++) {
            if (!hit[i]) {
                throw new IllegalStateException("plate miss " + i);
            }
        }
    }

    private static void walkPlate(int n, int from, int to, boolean[] hit) {
        Query q = new Query(n, 1, n, false, KIND_BOX, 2, 19, 0, from, to);
        int cursor = -1;
        int guard = n * n + 5;
        while (guard-- > 0) {
            Stand stand = current(q, cursor);
            if (stand.done) {
                break;
            }
            int[] xs = new int[27];
            int[] ys = new int[27];
            int[] zs = new int[27];
            int cells = fillCells(q, stand, xs, ys, zs);
            for (int i = 0; i < cells; i++) {
                if (xs[i] < from || xs[i] >= to || ys[i] != 0) {
                    throw new IllegalStateException("plate leak");
                }
                int id = xs[i] + zs[i] * n;
                if (hit[id]) {
                    throw new IllegalStateException("plate double");
                }
                hit[id] = true;
            }
            cursor = advance(q, stand.cursor);
        }
    }

    private static void coverCells(int sx, int sy, int sz) {
        boolean[] hit = new boolean[sx * sy * sz];
        int mid = sy / 2;
        walkCells(sx, sy, sz, 0, mid, hit);
        walkCells(sx, sy, sz, mid, sy, hit);
        for (int i = 0; i < hit.length; i++) {
            if (!hit[i]) {
                throw new IllegalStateException("cell miss " + i);
            }
        }
    }

    private static void walkCells(int sx, int sy, int sz, int from, int to, boolean[] hit) {
        Query q = new Query(sx, sy, sz, false, KIND_BOX, 0, 1, 1, from, to);
        int cursor = -1;
        int guard = sx * sy * sz + 5;
        while (guard-- > 0) {
            Stand stand = current(q, cursor);
            if (stand.done) {
                break;
            }
            int[] xs = new int[27];
            int[] ys = new int[27];
            int[] zs = new int[27];
            int cells = fillCells(q, stand, xs, ys, zs);
            for (int i = 0; i < cells; i++) {
                if (ys[i] < from || ys[i] >= to) {
                    throw new IllegalStateException("cell leak");
                }
                int id = xs[i] + ys[i] * sx + zs[i] * sx * sy;
                if (hit[id]) {
                    throw new IllegalStateException("cell double");
                }
                hit[id] = true;
            }
            cursor = advance(q, stand.cursor);
        }
    }

    private static void coverSweep(int sx, int sy, int sz) {
        boolean[] hit = new boolean[sx * sy * sz];
        int mid = sx / 2;
        walkMode(sx, sy, sz, 0, mid, 0, 1, 3, hit, false);
        walkMode(sx, sy, sz, mid, sx, 0, 1, 3, hit, false);
        for (int i = 0; i < hit.length; i++) {
            if (!hit[i]) {
                throw new IllegalStateException("sweep miss " + i);
            }
        }
    }

    private static void coverShell(int n) {
        boolean[] hit = new boolean[n * n * n];
        int mid = n / 2;
        walkMode(n, n, n, 0, mid, 2, 19, 0, hit, true);
        walkMode(n, n, n, mid, n, 2, 19, 0, hit, true);
        for (int i = 0; i < hit.length; i++) {
            int x = i % n;
            int y = (i / n) % n;
            int z = i / (n * n);
            boolean shell = onBoxShell(x, y, z, n, n, n);
            if (shell && !hit[i]) {
                throw new IllegalStateException("shell miss " + x + "," + y + "," + z);
            }
            if (!shell && hit[i]) {
                throw new IllegalStateException("shell interior " + x + "," + y + "," + z);
            }
        }
    }

    private static void walkMode(
            int sx, int sy, int sz, int from, int to, int reach, int cap, int axis, boolean[] hit, boolean hollow) {
        Query q = new Query(sx, sy, sz, hollow, hollow ? CUBE : KIND_BOX, reach, cap, axis, from, to);
        int cursor = -1;
        int guard = sx * sy * sz + 5;
        while (guard-- > 0) {
            Stand stand = current(q, cursor);
            if (stand.done) {
                break;
            }
            int[] xs = new int[27];
            int[] ys = new int[27];
            int[] zs = new int[27];
            int cells = fillCells(q, stand, xs, ys, zs);
            for (int i = 0; i < cells; i++) {
                int coord = axis == 0 ? xs[i] : axis == 1 ? ys[i] : zs[i];
                if (coord < from || coord >= to) {
                    throw new IllegalStateException("mode leak");
                }
                int id = xs[i] + ys[i] * sx + zs[i] * sx * sy;
                if (hit[id]) {
                    throw new IllegalStateException("mode double " + xs[i] + "," + ys[i] + "," + zs[i]);
                }
                hit[id] = true;
            }
            int next = advance(q, stand.cursor);
            if (next == stand.cursor) {
                throw new IllegalStateException("mode stuck");
            }
            cursor = next;
        }
    }

    private static void expect(int actual, int want, String label) {
        if (actual != want) {
            throw new IllegalStateException(label + " " + actual + " != " + want);
        }
    }

    private static void expect(Mode actual, Mode want, String label) {
        if (actual != want) {
            throw new IllegalStateException(label + " " + actual);
        }
    }
}
