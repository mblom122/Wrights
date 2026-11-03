package com.wrights.blueprint;

/**
 * Predefined stands for a piece of a build that is 3×3×3 or smaller. Closed form, no world lookups.
 * The piece is a whole small job, or one wright's own share of a bigger job.
 * A floor that fits in 3×3 is the middle block. A diameter-3 sphere is its center.
 * A solid 3×3×3 is the center, then the two face centers that reach the corners.
 * Anything with a side longer than 3 stays on the nearest-first crawler, so wrights
 * keep working their own strips and picking the closest open cell.
 */
public final class SmallBoxStands {
    /** Past this, the shape is a normal build and keeps the nearest-first crawler. */
    public static final int MAX_SIDE = 3;

    private static final int[][] NONE = new int[0][];
    private static final java.util.Map<Long, int[][]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private SmallBoxStands() {
    }

    public static boolean applies(int sx, int sy, int sz, int reachSqr) {
        if (reachSqr < 2 || sx <= 0 || sy <= 0 || sz <= 0) {
            return false;
        }
        return Math.max(sx, Math.max(sy, sz)) <= MAX_SIDE;
    }

    /** How many stands this box needs. {@code 0} when the box is larger than 3×3×3. */
    public static int count(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr) {
        int[][] stands = stands(sx, sy, sz, hollow, kind, reachSqr);
        return stands.length;
    }

    /**
     * Stand positions in local box coordinates, in the order they should be built.
     * Empty when {@link #applies} is false.
     */
    public static int[][] stands(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr) {
        if (!applies(sx, sy, sz, reachSqr)) {
            return NONE;
        }
        // Called every tick for every wright that holds a piece. Callers only read the arrays.
        long key = ((((((long) sx * 4 + sy) * 4 + sz) * 2 + (hollow ? 1 : 0)) * 256 + (kind & 255)) * 16)
                + Math.min(reachSqr, 15);
        return CACHE.computeIfAbsent(key, k -> plan(sx, sy, sz, hollow, kind, reachSqr));
    }

    private static int[][] plan(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr) {
        if (oneStand(sx, sy, sz, hollow, kind, reachSqr)) {
            return new int[][] {center(sx, sy, sz)};
        }
        boolean shell = hollow && (kind == Shapes.Kind.BOX.ordinal() || kind == Shapes.Kind.CUBE.ordinal());
        boolean[][][] open = new boolean[sx][sy][sz];
        int left = 0;
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    if (shell && !onShell(x, y, z, sx, sy, sz)) {
                        continue;
                    }
                    open[x][y][z] = true;
                    left++;
                }
            }
        }
        int[][] out = new int[Math.max(1, left)][3];
        int n = 0;
        int guard = left + 1;
        while (left > 0 && guard-- > 0) {
            int bestX = 0;
            int bestY = 0;
            int bestZ = 0;
            int bestCount = 0;
            int bestCenter = Integer.MAX_VALUE;
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    for (int x = 0; x < sx; x++) {
                        if (shell && !onShell(x, y, z, sx, sy, sz)) {
                            continue;
                        }
                        int count = 0;
                        for (int cz = 0; cz < sz; cz++) {
                            for (int cy = 0; cy < sy; cy++) {
                                for (int cx = 0; cx < sx; cx++) {
                                    if (!open[cx][cy][cz]) {
                                        continue;
                                    }
                                    if (dist(x, y, z, cx, cy, cz) <= reachSqr) {
                                        count++;
                                    }
                                }
                            }
                        }
                        int mid = centerDist(sx, sy, sz, x, y, z);
                        if (count > bestCount || (count == bestCount && count > 0 && mid < bestCenter)) {
                            bestCount = count;
                            bestCenter = mid;
                            bestX = x;
                            bestY = y;
                            bestZ = z;
                        }
                    }
                }
            }
            if (bestCount <= 0) {
                break;
            }
            out[n][0] = bestX;
            out[n][1] = bestY;
            out[n][2] = bestZ;
            n++;
            for (int cz = 0; cz < sz; cz++) {
                for (int cy = 0; cy < sy; cy++) {
                    for (int cx = 0; cx < sx; cx++) {
                        if (open[cx][cy][cz] && dist(bestX, bestY, bestZ, cx, cy, cz) <= reachSqr) {
                            open[cx][cy][cz] = false;
                            left--;
                        }
                    }
                }
            }
        }
        int[][] trimmed = new int[n][3];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    /** True when this local cell is inside the reach of {@code stand}. */
    public static boolean covers(int[] stand, int x, int y, int z, int reachSqr) {
        return stand != null && stand.length >= 3 && dist(stand[0], stand[1], stand[2], x, y, z) <= reachSqr;
    }

    private static boolean oneStand(int sx, int sy, int sz, boolean hollow, int kind, int reachSqr) {
        if (kind == Shapes.Kind.SPHERE.ordinal()
                || kind == Shapes.Kind.CIRCLE.ordinal()
                || kind == Shapes.Kind.DOME.ordinal()) {
            return true;
        }
        int[] mid = center(sx, sy, sz);
        int far = 0;
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    far = Math.max(far, dist(mid[0], mid[1], mid[2], x, y, z));
                }
            }
        }
        boolean hollowBox = hollow && (kind == Shapes.Kind.BOX.ordinal() || kind == Shapes.Kind.CUBE.ordinal());
        return far <= reachSqr && !(hollowBox && sx == 3 && sy == 3 && sz == 3);
    }

    private static int[] center(int sx, int sy, int sz) {
        return new int[] {(sx - 1) / 2, (sy - 1) / 2, (sz - 1) / 2};
    }

    private static int centerDist(int sx, int sy, int sz, int x, int y, int z) {
        int[] mid = center(sx, sy, sz);
        return dist(mid[0], mid[1], mid[2], x, y, z);
    }

    private static boolean onShell(int x, int y, int z, int sx, int sy, int sz) {
        return x == 0 || x == sx - 1 || y == 0 || y == sy - 1 || z == 0 || z == sz - 1;
    }

    private static int dist(int x, int y, int z, int ox, int oy, int oz) {
        int dx = x - ox;
        int dy = y - oy;
        int dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
