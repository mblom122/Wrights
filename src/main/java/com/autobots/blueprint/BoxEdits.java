package com.autobots.blueprint;

import com.autobots.AutobotsConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;

public final class BoxEdits {
    private BoxEdits() {
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    public static int span(BlockPos a, BlockPos b, int axis) {
        BlockPos low = min(a, b);
        BlockPos high = max(a, b);
        return switch (axis) {
            case 0 -> high.getX() - low.getX() + 1;
            case 1 -> high.getY() - low.getY() + 1;
            default -> high.getZ() - low.getZ() + 1;
        };
    }

    public static long volume(BlockPos a, BlockPos b) {
        return (long) span(a, b, 0) * span(a, b, 1) * span(a, b, 2);
    }

    public static long volume(int sizeX, int sizeY, int sizeZ) {
        return (long) Math.max(0, sizeX) * Math.max(0, sizeY) * Math.max(0, sizeZ);
    }

    public static int solid(BlockGetter level, BlockPos a, BlockPos b) {
        BlockPos low = min(a, b);
        BlockPos high = max(a, b);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int y = low.getY(); y <= high.getY(); y++) {
            for (int z = low.getZ(); z <= high.getZ(); z++) {
                for (int x = low.getX(); x <= high.getX(); x++) {
                    if (!level.getBlockState(cursor.set(x, y, z)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static boolean fits(BlockPos a, BlockPos b) {
        int maxAxis = AutobotsConfig.MAX_AXIS.getAsInt();
        if (span(a, b, 0) > maxAxis || span(a, b, 1) > maxAxis || span(a, b, 2) > maxAxis) {
            return false;
        }
        return volume(a, b) <= AutobotsConfig.MAX_BLOCKS.getAsInt();
    }

    public static boolean inWorld(BlockPos pos) {
        return Math.abs(pos.getX()) <= 30_000_000
                && Math.abs(pos.getZ()) <= 30_000_000
                && pos.getY() >= -2048
                && pos.getY() <= 2048;
    }

    public static boolean near(ServerPlayer player, BlockPos a, BlockPos b) {
        BlockPos low = min(a, b);
        BlockPos high = max(a, b);
        double x = clamp(player.getX(), low.getX(), high.getX() + 1.0D);
        double y = clamp(player.getY() + 0.5D, low.getY(), high.getY() + 1.0D);
        double z = clamp(player.getZ(), low.getZ(), high.getZ() + 1.0D);
        double range = AutobotsConfig.MAX_RANGE.getAsInt();
        return player.distanceToSqr(x, y, z) <= range * range;
    }

    public static BlockPos[] move(BlockPos a, BlockPos b, int dx, int dy, int dz) {
        return new BlockPos[] {a.offset(dx, dy, dz), b.offset(dx, dy, dz)};
    }

    public static BlockPos[] resize(BlockPos a, BlockPos b, int axis, int delta) {
        BlockPos low = min(a, b);
        BlockPos high = max(a, b);
        int size = Math.max(1, span(a, b, axis) + delta);
        int x = axis == 0 ? low.getX() + size - 1 : high.getX();
        int y = axis == 1 ? low.getY() + size - 1 : high.getY();
        int z = axis == 2 ? low.getZ() + size - 1 : high.getZ();
        return new BlockPos[] {low, new BlockPos(x, y, z)};
    }

    public static BlockPos[] fromMinAndSize(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        int sx = Math.max(1, sizeX);
        int sy = Math.max(1, sizeY);
        int sz = Math.max(1, sizeZ);
        return new BlockPos[] {
                new BlockPos(x, y, z),
                new BlockPos(x + sx - 1, y + sy - 1, z + sz - 1)
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
