package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

import com.wrights.WrightsConfig;
import com.wrights.schematic.Schematic;

/**
 * Reflects a placed blueprint across a face, or around the anchor corner.
 * The original build stays where the player put it. Each image is a new schematic.
 */
public final class Mirror {
    public static final int PAIR = 0;
    public static final int CROSS = 1;
    public static final int EIGHT = 2;

    private Mirror() {
    }

    public static int clampPattern(int pattern) {
        if (pattern == CROSS || pattern == EIGHT) {
            return pattern;
        }
        return PAIR;
    }

    /** Original build plus the reflected ones. */
    public static int copyCount(int pattern) {
        return switch (clampPattern(pattern)) {
            case EIGHT -> 8;
            case CROSS -> 4;
            default -> 2;
        };
    }

    public record Placed(Schematic schematic, BlockPos origin) {
    }

    public static List<Placed> place(Schematic source, BlockPos origin, int clipX, int clipY, int clipZ, BlockPos anchor, Direction face, int pattern, boolean shapes) {
        List<Placed> placed = new ArrayList<>();
        if (source == null || anchor == null || face == null || origin == null) {
            return placed;
        }
        Set<BlockPos> taken = new HashSet<>();
        for (Schematic.Entry entry : source.entries()) {
            if (inside(entry, clipX, clipY, clipZ)) {
                taken.add(origin.offset(entry.x(), entry.y(), entry.z()));
            }
        }
        int index = 1;
        int cap = Math.max(1, WrightsConfig.MAX_AXIS.getAsInt());
        for (Shot shot : shots(anchor, face, pattern, shapes)) {
            List<BlockPos> worlds = new ArrayList<>();
            List<Schematic.Entry> raw = new ArrayList<>();
            for (Schematic.Entry entry : source.entries()) {
                if (!inside(entry, clipX, clipY, clipZ)) {
                    continue;
                }
                BlockPos moved = shot.move(origin.offset(entry.x(), entry.y(), entry.z()));
                if (moved == null || taken.contains(moved)) {
                    continue;
                }
                worlds.add(moved);
                raw.add(entry);
            }
            if (raw.isEmpty()) {
                continue;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos world : worlds) {
                minX = Math.min(minX, world.getX());
                minY = Math.min(minY, world.getY());
                minZ = Math.min(minZ, world.getZ());
                maxX = Math.max(maxX, world.getX());
                maxY = Math.max(maxY, world.getY());
                maxZ = Math.max(maxZ, world.getZ());
            }
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            if (sizeX > cap || sizeY > cap || sizeZ > cap || sizeX > Short.MAX_VALUE || sizeY > Short.MAX_VALUE || sizeZ > Short.MAX_VALUE) {
                continue;
            }
            List<Schematic.Entry> local = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                Schematic.Entry entry = raw.get(i);
                BlockPos world = worlds.get(i);
                CompoundTag nbt = entry.nbt() == null ? null : entry.nbt().copy();
                local.add(new Schematic.Entry(
                        (short) (world.getX() - minX),
                        (short) (world.getY() - minY),
                        (short) (world.getZ() - minZ),
                        shot.restyle(entry.state()),
                        entry.secondary(),
                        nbt,
                        entry.nbtFull()));
            }
            taken.addAll(worlds);
            String name = source.name();
            if (name.length() > 40) {
                name = name.substring(0, 40).trim();
            }
            BlockPos min = new BlockPos(minX, minY, minZ);
            BlockPos max = new BlockPos(maxX, maxY, maxZ);
            Schematic schematic = new Schematic(UUID.randomUUID(), name + " · " + index, sizeX, sizeY, sizeZ, local, min, max, "", 0, 0, 0, true)
                    .withPaste(source.placeFluids(), source.voidBlocks(), source.voidFluids(), source.voidNbt());
            placed.add(new Placed(schematic, min));
            index++;
        }
        return placed;
    }

    public static List<AABB> volumes(BlockPos origin, int clipX, int clipY, int clipZ, BlockPos anchor, Direction face, int pattern) {
        List<AABB> boxes = new ArrayList<>();
        if (origin == null || anchor == null || face == null || clipX < 1 || clipY < 1 || clipZ < 1) {
            return boxes;
        }
        int x0 = origin.getX();
        int y0 = origin.getY();
        int z0 = origin.getZ();
        int x1 = x0 + clipX - 1;
        int y1 = y0 + clipY - 1;
        int z1 = z0 + clipZ - 1;
        BlockPos[] corners = {
                new BlockPos(x0, y0, z0), new BlockPos(x1, y0, z0), new BlockPos(x0, y1, z0), new BlockPos(x1, y1, z0),
                new BlockPos(x0, y0, z1), new BlockPos(x1, y0, z1), new BlockPos(x0, y1, z1), new BlockPos(x1, y1, z1)
        };
        for (Shot shot : shots(anchor, face, pattern, false)) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            boolean any = false;
            for (BlockPos corner : corners) {
                BlockPos moved = shot.move(corner);
                if (moved == null) {
                    continue;
                }
                any = true;
                minX = Math.min(minX, moved.getX());
                minY = Math.min(minY, moved.getY());
                minZ = Math.min(minZ, moved.getZ());
                maxX = Math.max(maxX, moved.getX());
                maxY = Math.max(maxY, moved.getY());
                maxZ = Math.max(maxZ, moved.getZ());
            }
            if (any) {
                boxes.add(new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D));
            }
        }
        return boxes;
    }

    /** Reflected copies of one block. The original position is left out. */
    public static List<BlockPos> copiesOf(BlockPos pos, BlockPos anchor, Direction face, int pattern) {
        List<BlockPos> copies = new ArrayList<>();
        if (pos == null || anchor == null || face == null) {
            return copies;
        }
        Set<BlockPos> seen = new HashSet<>();
        seen.add(pos);
        for (Shot shot : shots(anchor, face, pattern, false)) {
            BlockPos moved = shot.move(pos);
            if (moved != null && seen.add(moved)) {
                copies.add(moved);
            }
        }
        return copies;
    }

    public static BlockState restyle(BlockState state, BlockPos from, BlockPos to, BlockPos anchor, Direction face, int pattern, boolean shapes) {
        if (state == null || from == null || to == null || !shapes) {
            return state;
        }
        for (Shot shot : shots(anchor, face, pattern, true)) {
            BlockPos moved = shot.move(from);
            if (to.equals(moved)) {
                return shot.restyle(state);
            }
        }
        return state;
    }

    private static boolean inside(Schematic.Entry entry, int clipX, int clipY, int clipZ) {
        return entry.x() >= 0 && entry.y() >= 0 && entry.z() >= 0 && entry.x() < clipX && entry.y() < clipY && entry.z() < clipZ;
    }

    private interface Shot {
        BlockPos move(BlockPos pos);

        BlockState restyle(BlockState state);
    }

    private static List<Shot> shots(BlockPos anchor, Direction face, int pattern, boolean shapes) {
        List<Shot> shots = new ArrayList<>();
        if (clampPattern(pattern) == EIGHT) {
            for (int step = 1; step <= 7; step++) {
                int kind = step;
                shots.add(new Shot() {
                    @Override
                    public BlockPos move(BlockPos pos) {
                        return around(pos, anchor, kind);
                    }

                    @Override
                    public BlockState restyle(BlockState state) {
                        return shapes ? styleEight(state, kind) : state;
                    }
                });
            }
            return shots;
        }
        shots.add(faceShot(anchor, face, shapes));
        if (clampPattern(pattern) == CROSS) {
            Direction second = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            shots.add(faceShot(anchor, second, shapes));
            shots.add(new Shot() {
                @Override
                public BlockPos move(BlockPos pos) {
                    BlockPos once = reflect(pos, anchor, face);
                    return once == null ? null : reflect(once, anchor, second);
                }

                @Override
                public BlockState restyle(BlockState state) {
                    if (!shapes) {
                        return state;
                    }
                    return flipAxis(flipAxis(state, face.getAxis()), second.getAxis());
                }
            });
        }
        return shots;
    }

    private static Shot faceShot(BlockPos anchor, Direction face, boolean shapes) {
        return new Shot() {
            @Override
            public BlockPos move(BlockPos pos) {
                return reflect(pos, anchor, face);
            }

            @Override
            public BlockState restyle(BlockState state) {
                return shapes ? flipAxis(state, face.getAxis()) : state;
            }
        };
    }

    /** Reflect across the boundary of {@code face}. The clicked block stays on the near side. */
    private static BlockPos reflect(BlockPos pos, BlockPos anchor, Direction face) {
        Direction.Axis axis = face.getAxis();
        int anchorValue = axis.choose(anchor.getX(), anchor.getY(), anchor.getZ());
        int plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? anchorValue + 1 : anchorValue;
        int value = axis.choose(pos.getX(), pos.getY(), pos.getZ());
        long mirrored = plane * 2L - value - 1L;
        if (mirrored > Integer.MAX_VALUE || mirrored < Integer.MIN_VALUE) {
            return null;
        }
        int next = (int) mirrored;
        return switch (axis) {
            case X -> new BlockPos(next, pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), next, pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), next);
        };
    }

    /** Quarter turns and flips around the anchor corner, so every copy stays on the block grid. */
    private static BlockPos around(BlockPos pos, BlockPos anchor, int kind) {
        int ox = pos.getX() - anchor.getX();
        int oy = pos.getY() - anchor.getY();
        int oz = pos.getZ() - anchor.getZ();
        int nx = ox;
        int nz = oz;
        switch (kind) {
            case 1 -> {
                nx = -ox;
                nz = oz;
            }
            case 2 -> {
                nx = ox;
                nz = -oz;
            }
            case 3 -> {
                nx = -ox;
                nz = -oz;
            }
            case 4 -> {
                nx = -oz;
                nz = ox;
            }
            case 5 -> {
                nx = oz;
                nz = -ox;
            }
            case 6 -> {
                nx = oz;
                nz = ox;
            }
            default -> {
                nx = -oz;
                nz = -ox;
            }
        }
        long x = anchor.getX() + (long) nx;
        long y = anchor.getY() + (long) oy;
        long z = anchor.getZ() + (long) nz;
        if (x > Integer.MAX_VALUE || x < Integer.MIN_VALUE || y > Integer.MAX_VALUE || y < Integer.MIN_VALUE || z > Integer.MAX_VALUE || z < Integer.MIN_VALUE) {
            return null;
        }
        return new BlockPos((int) x, (int) y, (int) z);
    }

    private static BlockState styleEight(BlockState state, int kind) {
        return switch (kind) {
            case 1 -> state.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK);
            case 2 -> state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
            case 3 -> state.rotate(Rotation.CLOCKWISE_180);
            case 4 -> state.rotate(Rotation.CLOCKWISE_90);
            case 5 -> state.rotate(Rotation.COUNTERCLOCKWISE_90);
            case 6 -> state.rotate(Rotation.CLOCKWISE_90).mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK);
            default -> state.rotate(Rotation.CLOCKWISE_90).mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
        };
    }

    private static BlockState flipAxis(BlockState state, Direction.Axis axis) {
        if (axis == Direction.Axis.Y) {
            return flipVertical(state);
        }
        return state.mirror(axis == Direction.Axis.X ? net.minecraft.world.level.block.Mirror.FRONT_BACK : net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
    }

    private static BlockState flipVertical(BlockState state) {
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            SlabType type = state.getValue(BlockStateProperties.SLAB_TYPE);
            if (type == SlabType.TOP) {
                state = state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
            } else if (type == SlabType.BOTTOM) {
                state = state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
            }
        }
        if (state.hasProperty(BlockStateProperties.HALF)) {
            Half half = state.getValue(BlockStateProperties.HALF);
            state = state.setValue(BlockStateProperties.HALF, half == Half.TOP ? Half.BOTTOM : Half.TOP);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            state = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half == DoubleBlockHalf.UPPER ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        }
        for (Property<?> property : state.getProperties()) {
            state = flipUpDown(state, property);
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState flipUpDown(BlockState state, Property<T> property) {
        if (property.getValueClass() != Direction.class) {
            return state;
        }
        String name = property.getName();
        if (!name.equals("facing") && !name.equals("vertical_direction")) {
            return state;
        }
        Property<Direction> facing = (Property<Direction>) property;
        Direction value = state.getValue(facing);
        if (value.getAxis() != Direction.Axis.Y) {
            return state;
        }
        return state.setValue(facing, value.getOpposite());
    }
}
