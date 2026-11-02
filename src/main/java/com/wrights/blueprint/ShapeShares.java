package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a box by halving its longest side at the middle, then halving each new piece the same way.
 * A 4×4×4 divided for 8 wrights is eight 2×2×2 cubes. A piece that already fits in one hover is left whole.
 */
public final class ShapeShares {
    public static final class Part {
        public final int x0;
        public final int y0;
        public final int z0;
        public final int x1;
        public final int y1;
        public final int z1;

        public Part(int x0, int y0, int z0, int x1, int y1, int z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        public int spanX() {
            return x1 - x0;
        }

        public int spanY() {
            return y1 - y0;
        }

        public int spanZ() {
            return z1 - z0;
        }

        public int volume() {
            return Math.max(0, spanX()) * Math.max(0, spanY()) * Math.max(0, spanZ());
        }
    }

    private ShapeShares() {
    }

    public static List<Part> plan(int sx, int sy, int sz, int reachSqr, int cap, int maxParts) {
        List<Part> parts = new ArrayList<>();
        if (sx <= 0 || sy <= 0 || sz <= 0 || maxParts <= 0) {
            return parts;
        }
        parts.add(new Part(0, 0, 0, sx, sy, sz));
        while (parts.size() < maxParts) {
            int pick = largestSplittable(parts, reachSqr, cap);
            if (pick < 0) {
                break;
            }
            Part[] halves = halve(parts.get(pick));
            if (halves == null) {
                break;
            }
            parts.set(pick, halves[0]);
            parts.add(halves[1]);
        }
        return parts;
    }

    /** Low half, then high half. Null when every side is already one block. */
    public static Part[] halve(Part part) {
        if (part == null) {
            return null;
        }
        int dx = part.spanX();
        int dy = part.spanY();
        int dz = part.spanZ();
        int axis = 0;
        int len = dx;
        if (dy > len) {
            axis = 1;
            len = dy;
        }
        if (dz > len) {
            axis = 2;
            len = dz;
        }
        if (len < 2) {
            return null;
        }
        int mid = len / 2;
        if (axis == 0) {
            int cut = part.x0 + mid;
            return new Part[] {
                    new Part(part.x0, part.y0, part.z0, cut, part.y1, part.z1),
                    new Part(cut, part.y0, part.z0, part.x1, part.y1, part.z1)
            };
        }
        if (axis == 1) {
            int cut = part.y0 + mid;
            return new Part[] {
                    new Part(part.x0, part.y0, part.z0, part.x1, cut, part.z1),
                    new Part(part.x0, cut, part.z0, part.x1, part.y1, part.z1)
            };
        }
        int cut = part.z0 + mid;
        return new Part[] {
                new Part(part.x0, part.y0, part.z0, part.x1, part.y1, cut),
                new Part(part.x0, part.y0, cut, part.x1, part.y1, part.z1)
        };
    }

    public static boolean fitsOneHover(Part part, int reachSqr, int cap) {
        if (part == null || part.volume() <= 0) {
            return true;
        }
        return ShapeHover.count(part.spanX(), part.spanY(), part.spanZ(), false, ShapeHover.KIND_BOX, reachSqr, cap) <= 1;
    }

    private static int largestSplittable(List<Part> parts, int reachSqr, int cap) {
        int pick = -1;
        int bestVolume = -1;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (fitsOneHover(part, reachSqr, cap) || halve(part) == null) {
                continue;
            }
            int volume = part.volume();
            if (volume > bestVolume) {
                bestVolume = volume;
                pick = i;
            }
        }
        return pick;
    }

    public static void main(String[] args) {
        List<Part> eight = plan(4, 4, 4, 2, 19, 8);
        if (eight.size() != 8) {
            throw new IllegalStateException("4 cube pieces " + eight.size());
        }
        for (Part part : eight) {
            if (part.spanX() != 2 || part.spanY() != 2 || part.spanZ() != 2) {
                throw new IllegalStateException("expected 2x2x2, got "
                        + part.spanX() + "x" + part.spanY() + "x" + part.spanZ());
            }
        }
        List<Part> two = plan(4, 4, 4, 2, 19, 2);
        if (two.size() != 2 || two.get(0).spanX() != 2 || two.get(0).spanY() != 4 || two.get(0).spanZ() != 4) {
            throw new IllegalStateException("first cut should be two 2x4x4");
        }
        List<Part> four = plan(4, 4, 4, 2, 19, 4);
        if (four.size() != 4) {
            throw new IllegalStateException("four pieces " + four.size());
        }
        for (Part part : four) {
            int longSide = Math.max(part.spanX(), Math.max(part.spanY(), part.spanZ()));
            int shortSide = Math.min(part.spanX(), Math.min(part.spanY(), part.spanZ()));
            if (longSide != 4 || shortSide != 2 || part.volume() != 16) {
                throw new IllegalStateException("expected 2x2x4");
            }
        }
        System.out.println("ShapeShares checks passed");
    }
}
