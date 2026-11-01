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
        CIRCLE(false),
        RING(false),
        SQUARE(false),
        LINE(false),
        SPHERE(true),
        CUBE(true),
        CYLINDER(true),
        DOME(true),
        PYRAMID(true);

        private final boolean volume;

        Kind(boolean volume) {
            this.volume = volume;
        }

        public boolean volume() {
            return volume;
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

    /** World positions for one shape centered on {@code center}. */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, boolean hollow, int plane) {
        List<BlockPos> out = new ArrayList<>();
        if (center == null || kind == null) {
            return out;
        }
        int r = clampSize(size);
        int h = clampHeight(height);
        int flat = clampPlane(plane);
        switch (kind) {
            case CIRCLE -> disk(out, center, r, hollow, flat, false);
            case RING -> disk(out, center, r, true, flat, false);
            case SQUARE -> square(out, center, r, hollow, flat);
            case LINE -> line(out, center, r, flat);
            case SPHERE -> sphere(out, center, r, hollow);
            case CUBE -> cube(out, center, r, hollow);
            case CYLINDER -> cylinder(out, center, r, h, hollow);
            case DOME -> dome(out, center, r, hollow);
            case PYRAMID -> pyramid(out, center, r, h, hollow);
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

    private static void cube(List<BlockPos> out, BlockPos center, int radius, boolean hollow) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean edge = Math.abs(x) == radius || Math.abs(y) == radius || Math.abs(z) == radius;
                    if (hollow && !edge) {
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

    private static void dome(List<BlockPos> out, BlockPos center, int radius, boolean hollow) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= radius; y++) {
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

    /** Center for look-at builds: the block next to the clicked face. */
    public static BlockPos lookCenter(BlockPos clicked, Direction face) {
        if (clicked == null) {
            return null;
        }
        return face == null ? clicked.immutable() : clicked.relative(face);
    }
}
