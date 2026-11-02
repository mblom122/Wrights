package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a blueprint into pieces a wright can place in one hover, stopping at the crew cap.
 * A piece fits when every block is inside the operations sphere of one stand and the count
 * is within the tier cap. Larger builds are left to equal slabs; this planner only runs
 * while the block count can still fall under {@code cap * maxParts}.
 */
public final class HoverPieces {
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

        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    private HoverPieces() {
    }

    public static boolean fits(int[] xs, int[] ys, int[] zs, int n, int reachSqr, int cap) {
        if (n <= 0) {
            return true;
        }
        if (n > cap || reachSqr < 0) {
            return false;
        }
        if (n == 1 || reachSqr == 0) {
            return n == 1;
        }
        for (int i = 0; i < n; i++) {
            if (covers(xs, ys, zs, n, xs[i], ys[i], zs[i], reachSqr)) {
                return true;
            }
        }
        long ax = 0;
        long ay = 0;
        long az = 0;
        for (int i = 0; i < n; i++) {
            ax += xs[i];
            ay += ys[i];
            az += zs[i];
        }
        int cx = (int) Math.round(ax / (double) n);
        int cy = (int) Math.round(ay / (double) n);
        int cz = (int) Math.round(az / (double) n);
        return covers(xs, ys, zs, n, cx, cy, cz, reachSqr);
    }

    private static boolean covers(int[] xs, int[] ys, int[] zs, int n, int ax, int ay, int az, int reachSqr) {
        for (int i = 0; i < n; i++) {
            int dx = xs[i] - ax;
            int dy = ys[i] - ay;
            int dz = zs[i] - az;
            if (dx * dx + dy * dy + dz * dz > reachSqr) {
                return false;
            }
        }
        return true;
    }

    /**
     * Halve the biggest piece that does not fit in one hover until every piece fits or
     * {@code maxParts} pieces exist. Points are schematic-local.
     */
    public static List<Part> plan(int[] xs, int[] ys, int[] zs, int n, int reachSqr, int cap, int maxParts) {
        List<Part> parts = new ArrayList<>();
        if (n <= 0 || maxParts <= 0) {
            return parts;
        }
        int minX = xs[0];
        int maxX = xs[0];
        int minY = ys[0];
        int maxY = ys[0];
        int minZ = zs[0];
        int maxZ = zs[0];
        for (int i = 1; i < n; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        parts.add(new Part(minX, minY, minZ, maxX, maxY, maxZ));
        int guard = Math.max(1, maxParts) * 4;
        while (parts.size() < maxParts && guard-- > 0) {
            int donor = -1;
            int most = 1;
            for (int i = 0; i < parts.size(); i++) {
                int count = count(xs, ys, zs, n, parts.get(i));
                if (count > most && !fitsPart(xs, ys, zs, n, parts.get(i), reachSqr, cap)) {
                    most = count;
                    donor = i;
                }
            }
            if (donor < 0) {
                break;
            }
            if (!splitInto(parts, donor, xs, ys, zs, n)) {
                break;
            }
        }
        return parts;
    }

    private static boolean fitsPart(int[] xs, int[] ys, int[] zs, int n, Part part, int reachSqr, int cap) {
        int count = count(xs, ys, zs, n, part);
        if (count > cap) {
            return false;
        }
        int[] px = new int[count];
        int[] py = new int[count];
        int[] pz = new int[count];
        int at = 0;
        for (int i = 0; i < n && at < count; i++) {
            if (!part.contains(xs[i], ys[i], zs[i])) {
                continue;
            }
            px[at] = xs[i];
            py[at] = ys[i];
            pz[at] = zs[i];
            at++;
        }
        return fits(px, py, pz, at, reachSqr, cap);
    }

    private static int count(int[] xs, int[] ys, int[] zs, int n, Part part) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (part.contains(xs[i], ys[i], zs[i])) {
                count++;
            }
        }
        return count;
    }

    /** Replace {@code parts[donor]} with the low half and append the high half. */
    private static boolean splitInto(List<Part> parts, int donor, int[] xs, int[] ys, int[] zs, int n) {
        Part part = parts.get(donor);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (!part.contains(xs[i], ys[i], zs[i])) {
                continue;
            }
            count++;
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        if (count < 2) {
            return false;
        }
        int spanX = maxX - minX;
        int spanY = maxY - minY;
        int spanZ = maxZ - minZ;
        int axis;
        int minC;
        int span;
        if (spanX >= spanY && spanX >= spanZ && spanX > 0) {
            axis = 0;
            minC = minX;
            span = spanX;
        } else if (spanZ >= spanY && spanZ > 0) {
            axis = 2;
            minC = minZ;
            span = spanZ;
        } else if (spanY > 0) {
            axis = 1;
            minC = minY;
            span = spanY;
        } else {
            return false;
        }
        int[] bins = new int[span + 1];
        for (int i = 0; i < n; i++) {
            if (!part.contains(xs[i], ys[i], zs[i])) {
                continue;
            }
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
            return false;
        }
        int cut = minC + bestCut;
        Part low;
        Part high;
        if (axis == 0) {
            low = new Part(part.x0, part.y0, part.z0, cut - 1, part.y1, part.z1);
            high = new Part(cut, part.y0, part.z0, part.x1, part.y1, part.z1);
        } else if (axis == 1) {
            low = new Part(part.x0, part.y0, part.z0, part.x1, cut - 1, part.z1);
            high = new Part(part.x0, cut, part.z0, part.x1, part.y1, part.z1);
        } else {
            low = new Part(part.x0, part.y0, part.z0, part.x1, part.y1, cut - 1);
            high = new Part(part.x0, part.y0, cut, part.x1, part.y1, part.z1);
        }
        parts.set(donor, low);
        parts.add(high);
        return true;
    }

    public static void main(String[] args) {
        int[] xs = new int[8 * 19];
        int[] ys = new int[8 * 19];
        int[] zs = new int[8 * 19];
        int n = 0;
        for (int oz = 0; oz < 2; oz++) {
            for (int oy = 0; oy < 2; oy++) {
                for (int ox = 0; ox < 2; ox++) {
                    int cx = ox * 3 + 1;
                    int cy = oy * 3 + 1;
                    int cz = oz * 3 + 1;
                    for (int z = -1; z <= 1; z++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int x = -1; x <= 1; x++) {
                                if (x * x + y * y + z * z > 2) {
                                    continue;
                                }
                                xs[n] = cx + x;
                                ys[n] = cy + y;
                                zs[n] = cz + z;
                                n++;
                            }
                        }
                    }
                }
            }
        }
        List<Part> parts = plan(xs, ys, zs, n, 2, 19, 10);
        if (parts.size() != 8) {
            throw new IllegalStateException("pieces " + parts.size());
        }
        for (Part part : parts) {
            if (!fitsPart(xs, ys, zs, n, part, 2, 19)) {
                throw new IllegalStateException("piece does not fit one hover");
            }
            if (count(xs, ys, zs, n, part) != 19) {
                throw new IllegalStateException("piece blocks " + count(xs, ys, zs, n, part));
            }
        }
        System.out.println("HoverPieces checks passed");
    }
}
