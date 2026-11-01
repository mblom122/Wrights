package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import com.wrights.WrightsConfig;

/**
 * Block positions for Shape mode. Offsets are relative to the center block.
 */
public final class Shapes {
    public enum Kind {
        CIRCLE(false, false),
        RING(false, true),
        /** Legacy flat square — kept for saved configs; use {@link #CUBE} with height 1. */
        SQUARE(false, false),
        /** Legacy line — kept for saved configs; use {@link #CYLINDER} with width 1. */
        LINE(false, false),
        SPHERE(true, false),
        CUBE(true, true),
        CYLINDER(true, true),
        DOME(true, true),
        PYRAMID(true, true);

        private final boolean volume;
        private final boolean usesHeight;

        Kind(boolean volume, boolean usesHeight) {
            this.volume = volume;
            this.usesHeight = usesHeight;
        }

        public boolean volume() {
            return volume;
        }

        /** True when the Height slider changes this shape. */
        public boolean usesHeight() {
            return usesHeight;
        }

        /** False for legacy kinds hidden from the picker. */
        public boolean selectable() {
            return this != SQUARE && this != LINE;
        }

        public static Kind byId(int id) {
            Kind[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }
    }

    /** Flat shapes sit on this plane. 0 = XZ, 1 = XY, 2 = YZ. */
    public static final int PLANE_XZ = 0;
    public static final int PLANE_XY = 1;
    public static final int PLANE_YZ = 2;

    private Shapes() {
    }

    public static int clampSize(int size) {
        int cap = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
        return Mth.clamp(size, 1, cap);
    }

    public static int clampHeight(int height) {
        return clampSize(height);
    }

    public static int clampPlane(int plane) {
        return Mth.clamp(plane, 0, 2);
    }

    /** Quarter-turns around Y: 0–3. */
    public static int clampYaw(int yaw) {
        return Math.floorMod(yaw, 4);
    }

    /** World positions for one shape centered on {@code center}. */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, boolean hollow, int plane) {
        return blocks(kind, center, size, height, 1, hollow, plane, false, 0);
    }

    /** World positions for one shape centered on {@code center}. When {@code flipVertical}, mirrors on Y through the center. */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, boolean hollow, int plane, boolean flipVertical) {
        return blocks(kind, center, size, height, 1, hollow, plane, flipVertical, 0);
    }

    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int plane, boolean flipVertical) {
        return blocks(kind, center, size, height, thickness, hollow, plane, flipVertical, 0);
    }

    /**
     * @param height box / cylinder / pyramid / dome height (box height 1 = flat square); for rings, tube diameter (1 = flat).
     *               Cylinder width 1 uses height as the line length.
     * @param thickness ring / torus tube width in the ring plane; ignored by other kinds
     * @param yaw quarter-turns around Y through {@code center} (0–3)
     */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int plane, boolean flipVertical, int yaw) {
        List<BlockPos> out = new ArrayList<>();
        if (center == null || kind == null) {
            return out;
        }
        int r = clampSize(size);
        int h = clampHeight(height);
        int band = clampHeight(thickness);
        int flat = clampPlane(plane);
        switch (kind) {
            case CIRCLE -> disk(out, center, r, hollow, flat, false);
            case RING -> ring(out, center, r, band, h, flat, hollow);
            case SQUARE -> square(out, center, r, hollow, flat);
            case LINE -> lineSegment(out, center, Math.max(h, 2 * r + 1), flat);
            case SPHERE -> sphere(out, center, r, hollow);
            case CUBE -> {
                // Height 1 is the old Square (plane chooses floor/wall). Taller is a rectangular box.
                if (h <= 1) {
                    square(out, center, r, hollow, flat);
                } else {
                    box(out, center, r, h, hollow);
                }
            }
            case CYLINDER -> {
                // Width 1 = single-block line (old Line). Width 2 = radius 1, and so on.
                int rad = Math.max(0, r - 1);
                if (rad == 0) {
                    lineSegment(out, center, h, flat);
                } else {
                    cylinder(out, center, rad, h, hollow);
                }
            }
            case DOME -> dome(out, center, r, h, hollow);
            case PYRAMID -> pyramid(out, center, r, h, hollow);
        }
        if (flipVertical) {
            int cy = center.getY();
            for (int i = 0; i < out.size(); i++) {
                BlockPos pos = out.get(i);
                out.set(i, new BlockPos(pos.getX(), cy - (pos.getY() - cy), pos.getZ()));
            }
        }
        int turns = clampYaw(yaw);
        if (turns != 0) {
            int cx = center.getX();
            int cz = center.getZ();
            for (int i = 0; i < out.size(); i++) {
                BlockPos pos = out.get(i);
                int dx = pos.getX() - cx;
                int dz = pos.getZ() - cz;
                for (int t = 0; t < turns; t++) {
                    int nextX = dz;
                    int nextZ = -dx;
                    dx = nextX;
                    dz = nextZ;
                }
                out.set(i, new BlockPos(cx + dx, pos.getY(), cz + dz));
            }
        }
        return out;
    }

    /**
     * World positions with the shape's bounding-box min corner (lowest X/Y/Z) at {@code corner}.
     * Used by “where I look” placement so the aim cell is the bottom corner of the box, not the center.
     */
    public static List<BlockPos> blocksFromCorner(Kind kind, BlockPos corner, int size, int height, boolean hollow, int plane, boolean flipVertical) {
        return blocksFromCorner(kind, corner, size, height, 1, hollow, plane, flipVertical, 0);
    }

    public static List<BlockPos> blocksFromCorner(Kind kind, BlockPos corner, int size, int height, int thickness, boolean hollow, int plane, boolean flipVertical) {
        return blocksFromCorner(kind, corner, size, height, thickness, hollow, plane, flipVertical, 0);
    }

    public static List<BlockPos> blocksFromCorner(Kind kind, BlockPos corner, int size, int height, int thickness, boolean hollow, int plane, boolean flipVertical, int yaw) {
        List<BlockPos> local = blocks(kind, BlockPos.ZERO, size, height, thickness, hollow, plane, flipVertical, yaw);
        if (local.isEmpty() || corner == null) {
            return local;
        }
        BlockPos min = origin(local);
        int dx = corner.getX() - min.getX();
        int dy = corner.getY() - min.getY();
        int dz = corner.getZ() - min.getZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return local;
        }
        List<BlockPos> out = new ArrayList<>(local.size());
        for (BlockPos pos : local) {
            out.add(pos.offset(dx, dy, dz));
        }
        return out;
    }

    /** Origin for a wright schematic that covers every offset. */
    public static BlockPos origin(List<BlockPos> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return BlockPos.ZERO;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : worlds) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static void disk(List<BlockPos> out, BlockPos center, int radius, boolean hollow, int plane, boolean filledOnly) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                int d2 = a * a + b * b;
                if (d2 > r2) {
                    continue;
                }
                if (hollow && !filledOnly && d2 < inner2) {
                    continue;
                }
                out.add(flat(center, a, b, plane));
            }
        }
    }

    /**
     * Flat annulus when {@code height} is 1; otherwise a torus (true donut) with elliptical tube
     * cross-section. {@code thickness} is the tube width in the ring plane; {@code height} is the
     * tube diameter along the plane normal.
     */
    private static void ring(List<BlockPos> out, BlockPos center, int radius, int thickness, int height, int plane, boolean hollow) {
        int band = Mth.clamp(thickness, 1, radius);
        int tall = Math.max(1, height);
        if (tall <= 1) {
            int inner = Math.max(0, radius - band);
            int r2 = radius * radius;
            int inner2 = inner * inner;
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    int d2 = a * a + b * b;
                    if (d2 > r2 || d2 < inner2) {
                        continue;
                    }
                    out.add(ringCell(center, a, b, 0, plane));
                }
            }
            return;
        }
        // Major radius to tube centerline; minor radii for elliptical tube section.
        double rx = band * 0.5D;
        double ry = tall * 0.5D;
        double major = Math.max(rx, radius - rx);
        int alongMax = (tall - 1) / 2;
        int alongMin = -(tall / 2);
        // Shell thickness in ellipse-parameter space (~one block of the larger minor axis).
        double shell = hollow ? Math.max(0.35D, 1.0D / Math.max(rx, ry)) : 0.0D;
        for (int along = alongMin; along <= alongMax; along++) {
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    double dist = Math.sqrt((double) a * a + (double) b * b);
                    double da = dist - major;
                    double nx = da / rx;
                    double ny = along / ry;
                    double q = nx * nx + ny * ny;
                    if (q > 1.0D) {
                        continue;
                    }
                    if (hollow && q < (1.0D - shell) * (1.0D - shell)) {
                        continue;
                    }
                    out.add(ringCell(center, a, b, along, plane));
                }
            }
        }
    }

    /** Ring/torus in the chosen plane; {@code along} is offset on the plane normal. */
    private static BlockPos ringCell(BlockPos center, int a, int b, int along, int plane) {
        return switch (plane) {
            case PLANE_XY -> center.offset(a, b, along);
            case PLANE_YZ -> center.offset(along, a, b);
            default -> center.offset(a, along, b);
        };
    }

    private static void square(List<BlockPos> out, BlockPos center, int radius, boolean hollow, int plane) {
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                boolean edge = Math.abs(a) == radius || Math.abs(b) == radius;
                if (hollow && !edge) {
                    continue;
                }
                out.add(flat(center, a, b, plane));
            }
        }
    }

    private static void line(List<BlockPos> out, BlockPos center, int radius, int plane) {
        for (int i = -radius; i <= radius; i++) {
            out.add(flat(center, i, 0, plane));
        }
    }

    /** One-block-thick run of {@code length} blocks along the plane’s main axis, from the origin outward. */
    private static void lineSegment(List<BlockPos> out, BlockPos origin, int length, int plane) {
        int top = Math.max(0, length - 1);
        for (int i = 0; i <= top; i++) {
            out.add(flat(origin, i, 0, plane));
        }
    }

    private static void sphere(List<BlockPos> out, BlockPos center, int radius, boolean hollow) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > r2) {
                        continue;
                    }
                    if (hollow && d2 < inner2) {
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    /** Rectangular box: Size is half-width on X/Z, Height grows upward from the base. */
    private static void box(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int top = Math.max(0, height - 1);
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= top; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean shell = Math.abs(x) == radius || Math.abs(z) == radius || y == 0 || y == top;
                    if (hollow && !shell) {
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    private static void cylinder(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        int top = Math.max(0, height - 1);
        for (int y = 0; y <= top; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + z * z;
                    if (d2 > r2) {
                        continue;
                    }
                    boolean shell = d2 >= inner2;
                    boolean cap = y == 0 || y == top;
                    if (hollow && !shell && !cap) {
                        continue;
                    }
                    if (hollow && !shell && cap) {
                        // Hollow keeps the rim on caps, not a filled lid.
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    /** Hemisphere ellipsoid: Size is the floor radius, Height is how tall the dome rises. */
    private static void dome(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int tall = Math.max(1, height);
        long rr = (long) radius * radius;
        long hh = (long) tall * tall;
        long limit = rr * hh;
        int innerR = hollow ? Math.max(0, radius - 1) : -1;
        int innerH = hollow ? Math.max(0, tall - 1) : -1;
        long innerLimit = hollow ? (long) innerR * innerR * innerH * innerH : -1L;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= tall; y++) {
                for (int z = -radius; z <= radius; z++) {
                    long d = (long) x * x * hh + (long) z * z * hh + (long) y * y * rr;
                    if (d > limit) {
                        continue;
                    }
                    if (hollow && innerR > 0 && innerH > 0) {
                        long inner = (long) x * x * (long) innerH * innerH
                                + (long) z * z * (long) innerH * innerH
                                + (long) y * y * (long) innerR * innerR;
                        if (inner < innerLimit) {
                            continue;
                        }
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    private static void pyramid(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int top = Math.max(1, height);
        for (int y = 0; y < top; y++) {
            int layer = Math.max(0, radius - (radius * y) / top);
            for (int x = -layer; x <= layer; x++) {
                for (int z = -layer; z <= layer; z++) {
                    boolean edge = Math.abs(x) == layer || Math.abs(z) == layer || y == 0 || y == top - 1;
                    if (hollow && !edge) {
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    private static BlockPos flat(BlockPos center, int a, int b, int plane) {
        return switch (plane) {
            case PLANE_XY -> center.offset(a, b, 0);
            case PLANE_YZ -> center.offset(0, a, b);
            default -> center.offset(a, 0, b);
        };
    }

    /**
     * Aim cell for look-at builds: the block next to the clicked face.
     * That cell becomes the shape’s bottom corner ({@link #blocksFromCorner}), not its center.
     */
    public static BlockPos lookCorner(BlockPos clicked, Direction face) {
        if (clicked == null) {
            return null;
        }
        return face == null ? clicked.immutable() : clicked.relative(face);
    }

    /** @deprecated use {@link #lookCorner} */
    @Deprecated
    public static BlockPos lookCenter(BlockPos clicked, Direction face) {
        return lookCorner(clicked, face);
    }
}
