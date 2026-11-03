package com.wrights.blueprint;

/**
 * Predefined stands for a small shape. Closed form, no world lookups.
 * A floor that fits in 3×3 is the middle block. A diameter-3 sphere is its center.
 * A solid 3×3×3 is the center, then the two face centers that reach the corners.
 * A larger floor or wall is stamped in 3×3 tiles, the same way main's plate mode does.
 * A beam is groups of 3. Fat volumes and anything longer than 16 stay on the old route.
 */
public final class SmallBoxStands {
    /** Past this, a floor stamp or a beam group is a big build and keeps the old crawler. */
    public static final int MAX_SIDE = 16;

    private SmallBoxStands() {
    }

    public static boolean applies(int sx, int sy, int sz, int reachSqr) {
        if (reachSqr < 2 || sx <= 0 || sy <= 0 || sz <= 0) {
            return false;
        }
        if (Math.max(sx, Math.max(sy, sz)) > MAX_SIDE) {
            return false;
        }
        if (sx <= 3 && sy <= 3 && sz <= 3) {
            return true;
        }
        // One flat side: a floor or a wall. Two flat sides: a beam. A fat box is not this.
        int thin = (sx == 1 ? 1 : 0) + (sy == 1 ? 1 : 0) + (sz == 1 ? 1 : 0);
        return thin >= 1;
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
            return new int[0][];
        }
        if (sx > 3 || sy > 3 || sz > 3) {
            int thin = (sx == 1 ? 1 : 0) + (sy == 1 ? 1 : 0) + (sz == 1 ? 1 : 0);
            return thin >= 2 ? lineStands(sx, sy, sz) : plateStands(sx, sy, sz);
        }
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

    /** 3×3 stamps. A 6×1×6 floor is four stands, each the middle of its tile. */
    private static int[][] plateStands(int sx, int sy, int sz) {
        int flat = sx == 1 ? 0 : sy == 1 ? 1 : 2;
        int a = flat == 0 ? sy : sx;
        int b = flat == 2 ? sy : sz;
        int na = (a + 2) / 3;
        int nb = (b + 2) / 3;
        int[][] out = new int[na * nb][3];
        int n = 0;
        for (int tb = 0; tb < nb; tb++) {
            for (int ta = 0; ta < na; ta++) {
                int a0 = ta * 3;
                int b0 = tb * 3;
                int ia = a0 + (Math.min(a, a0 + 3) - a0) / 2;
                int ib = b0 + (Math.min(b, b0 + 3) - b0) / 2;
                if (flat == 0) {
                    out[n] = new int[] {0, ia, ib};
                } else if (flat == 1) {
                    out[n] = new int[] {ia, 0, ib};
                } else {
                    out[n] = new int[] {ia, ib, 0};
                }
                n++;
            }
        }
        return out;
    }

    /** Groups of 3 along the long axis, standing on the middle of each group. */
    private static int[][] lineStands(int sx, int sy, int sz) {
        int axis = sx >= sy && sx >= sz ? 0 : sz >= sy ? 2 : 1;
        int length = axis == 0 ? sx : axis == 1 ? sy : sz;
        int groups = (length + 2) / 3;
        int[][] out = new int[groups][3];
        for (int g = 0; g < groups; g++) {
            int start = g * 3;
            int mid = start + (Math.min(length, start + 3) - start) / 2;
            out[g] = axis == 0 ? new int[] {mid, 0, 0} : axis == 1 ? new int[] {0, mid, 0} : new int[] {0, 0, mid};
        }
        return out;
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
