package com.wrights.blueprint;

/**
 * Walks a shape that is larger than one chunk section as separate cubes.
 * Each cube is at most 16×16×16, the size of a section, and the crew splits
 * that cube before the next one starts. A box that already fits in 16³ is one
 * piece, so a small shape keeps a single plan. Cubes run lowest layer first.
 */
public final class ShapeCubes {
    /** One Minecraft section on a side. Larger than this is staged. */
    public static final int EDGE = 16;

    private ShapeCubes() {
    }

    public static int count(int sx, int sy, int sz) {
        if (sx <= 0 || sy <= 0 || sz <= 0) {
            return 0;
        }
        if (sx <= EDGE && sy <= EDGE && sz <= EDGE) {
            return 1;
        }
        int nx = (sx + EDGE - 1) / EDGE;
        int ny = (sy + EDGE - 1) / EDGE;
        int nz = (sz + EDGE - 1) / EDGE;
        long n = (long) nx * (long) ny * (long) nz;
        return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
    }

    /**
     * Cube {@code index} in full-box coordinates, or null when the index is past the end.
     * Y is the slow index, so a layer finishes before the layer above it.
     */
    public static ShapeShares.Part at(int sx, int sy, int sz, int index) {
        if (sx <= 0 || sy <= 0 || sz <= 0 || index < 0) {
            return null;
        }
        if (sx <= EDGE && sy <= EDGE && sz <= EDGE) {
            return index == 0 ? new ShapeShares.Part(0, 0, 0, sx, sy, sz) : null;
        }
        int nx = (sx + EDGE - 1) / EDGE;
        int ny = (sy + EDGE - 1) / EDGE;
        int nz = (sz + EDGE - 1) / EDGE;
        int layer = nx * nz;
        if (layer <= 0) {
            return null;
        }
        int ty = index / layer;
        if (ty < 0 || ty >= ny) {
            return null;
        }
        int rem = index - ty * layer;
        int tz = rem / nx;
        int tx = rem - tz * nx;
        if (tz < 0 || tz >= nz) {
            return null;
        }
        int x0 = tx * EDGE;
        int y0 = ty * EDGE;
        int z0 = tz * EDGE;
        return new ShapeShares.Part(
                x0,
                y0,
                z0,
                Math.min(sx, x0 + EDGE),
                Math.min(sy, y0 + EDGE),
                Math.min(sz, z0 + EDGE));
    }

    public static void main(String[] args) {
        if (count(4, 4, 4) != 1) {
            throw new IllegalStateException("a 4 cube is one piece");
        }
        ShapeShares.Part small = at(4, 4, 4, 0);
        if (small == null || small.spanX() != 4 || at(4, 4, 4, 1) != null) {
            throw new IllegalStateException("small box");
        }
        if (count(16, 16, 16) != 1) {
            throw new IllegalStateException("one section stays one cube");
        }
        if (count(512, 512, 512) != 32 * 32 * 32) {
            throw new IllegalStateException("512 cube count");
        }
        ShapeShares.Part first = at(512, 512, 512, 0);
        ShapeShares.Part last = at(512, 512, 512, count(512, 512, 512) - 1);
        if (first == null || first.x0 != 0 || first.spanX() != 16 || first.spanY() != 16 || first.spanZ() != 16) {
            throw new IllegalStateException("first cube");
        }
        if (last == null || last.x0 != 496 || last.y0 != 496 || last.z0 != 496 || last.x1 != 512) {
            throw new IllegalStateException("last cube");
        }
        if (at(512, 512, 512, count(512, 512, 512)) != null) {
            throw new IllegalStateException("past the end");
        }
        ShapeShares.Part low = at(1, 32, 1, 0);
        ShapeShares.Part high = at(1, 32, 1, 1);
        if (low == null || high == null || low.y0 != 0 || low.y1 != 16 || high.y0 != 16 || high.y1 != 32) {
            throw new IllegalStateException("pillar should climb");
        }
        cover(20, 17, 4);
        cover(17, 17, 17);
        System.out.println("ShapeCubes checks passed");
    }

    private static void cover(int sx, int sy, int sz) {
        int n = count(sx, sy, sz);
        boolean[] seen = new boolean[sx * sy * sz];
        for (int i = 0; i < n; i++) {
            ShapeShares.Part part = at(sx, sy, sz, i);
            if (part == null || part.spanX() > EDGE || part.spanY() > EDGE || part.spanZ() > EDGE || part.volume() <= 0) {
                throw new IllegalStateException("bad cube " + i);
            }
            for (int z = part.z0; z < part.z1; z++) {
                for (int y = part.y0; y < part.y1; y++) {
                    for (int x = part.x0; x < part.x1; x++) {
                        int index = x + y * sx + z * sx * sy;
                        if (seen[index]) {
                            throw new IllegalStateException("overlap");
                        }
                        seen[index] = true;
                    }
                }
            }
        }
        for (boolean cell : seen) {
            if (!cell) {
                throw new IllegalStateException("gap");
            }
        }
    }
}
