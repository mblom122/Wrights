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
 * Reflects a placed blueprint through the middle of the anchor, or around its corner.
 * The original build stays where the player put it. Each image is a new schematic.
 */
public final class Mirror {
    public static final int PAIR = 0;
    public static final int CROSS = 1;
    public static final int EIGHT = 2;
    public static final int SIXTEEN = 3;
    public static final int AXIS_X = 1;
    public static final int AXIS_Y = 2;
    public static final int AXIS_Z = 4;

    private Mirror() {
    }

    public static int clampPattern(int pattern) {
        if (pattern == CROSS || pattern == EIGHT || pattern == SIXTEEN) {
            return pattern;
        }
        return PAIR;
    }

    /** Original build plus the reflected ones. Axis bits replace the pattern when any are set. */
    public static int copyCount(int pattern) {
        return copyCount(pattern, 0);
    }

    public static int copyCount(int pattern, int axes) {
        int mask = axes & 7;
        if (mask != 0) {
            return 1 << Integer.bitCount(mask);
        }
        return switch (clampPattern(pattern)) {
            case SIXTEEN -> 16;
            case EIGHT -> 8;
            case CROSS -> 4;
            default -> 2;
        };
    }

    /** Planes to draw. An empty axis mask keeps the face that was clicked. */
    public static List<Direction> planes(Direction face, int axes) {
        int mask = axes & 7;
        if (mask == 0 || face == null) {
            return face == null ? List.of() : List.of(face);
        }
        List<Direction> planes = new ArrayList<>();
        if ((mask & AXIS_X) != 0) {
            planes.add(axisFace(face, Direction.Axis.X));
        }
        if ((mask & AXIS_Y) != 0) {
            planes.add(axisFace(face, Direction.Axis.Y));
        }
        if ((mask & AXIS_Z) != 0) {
            planes.add(axisFace(face, Direction.Axis.Z));
        }
        return planes;
    }

    public record Placed(Schematic schematic, BlockPos origin) {
    }

    public static List<Placed> place(Schematic source, BlockPos origin, int clipX, int clipY, int clipZ, BlockPos anchor, Direction face, int pattern, boolean shapes, int axes) {
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
        for (Shot shot : shots(anchor, face, pattern, shapes, axes)) {
            List<BlockPos> worlds = new ArrayList<>();
            List<Schematic.Entry> raw = new ArrayList<>();
            Set<BlockPos> local = new HashSet<>();
            for (Schematic.Entry entry : source.entries()) {
                if (!inside(entry, clipX, clipY, clipZ)) {
                    continue;
                }
                BlockPos moved = shot.move(origin.offset(entry.x(), entry.y(), entry.z()));
                if (moved == null || taken.contains(moved) || !local.add(moved)) {
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
            List<Schematic.Entry> entries = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                Schematic.Entry entry = raw.get(i);
                BlockPos world = worlds.get(i);
                CompoundTag nbt = entry.nbt() == null ? null : entry.nbt().copy();
                entries.add(new Schematic.Entry(
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
            Schematic schematic = new Schematic(UUID.randomUUID(), name + " · " + index, sizeX, sizeY, sizeZ, entries, min, max, "", 0, 0, 0, true)
                    .withPaste(source.placeFluids(), source.voidBlocks(), source.voidFluids(), source.voidNbt());
            placed.add(new Placed(schematic, min));
            index++;
        }
        return placed;
    }

    /** One schematic for every copy, so they share a single job and one undo. */
    public static Placed unite(List<Placed> copies) {
        if (copies == null || copies.isEmpty()) {
            return null;
        }
        if (copies.size() == 1) {
            return copies.get(0);
        }
        List<BlockPos> worlds = new ArrayList<>();
        List<Schematic.Entry> raw = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (Placed placed : copies) {
            if (placed == null || placed.schematic() == null || placed.origin() == null) {
                continue;
            }
            BlockPos origin = placed.origin();
            for (Schematic.Entry entry : placed.schematic().entries()) {
                BlockPos world = origin.offset(entry.x(), entry.y(), entry.z());
                if (!seen.add(world)) {
                    continue;
                }
                worlds.add(world);
                raw.add(entry);
            }
        }
        if (worlds.isEmpty()) {
            return null;
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
        int cap = Math.max(1, WrightsConfig.MAX_AXIS.getAsInt());
        if (sizeX > cap || sizeY > cap || sizeZ > cap || sizeX > Short.MAX_VALUE || sizeY > Short.MAX_VALUE || sizeZ > Short.MAX_VALUE) {
            return null;
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
                    entry.state(),
                    entry.secondary(),
                    nbt,
                    entry.nbtFull()));
        }
        Schematic first = copies.get(0).schematic();
        String name = mirrorName(first.name());
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        Schematic schematic = new Schematic(UUID.randomUUID(), name, sizeX, sizeY, sizeZ, local, min, max, "", 0, 0, 0, true)
                .withPaste(first.placeFluids(), first.voidBlocks(), first.voidFluids(), first.voidNbt());
        return new Placed(schematic, min);
    }

    /** Mirror copies share one sparse schematic, so the preview marks each block instead of the bounding box. */
    public static boolean marksBlocks(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.equals("Mirror") || name.endsWith(" · mirror")) {
            return true;
        }
        int cut = name.lastIndexOf(" · ");
        if (cut <= 0) {
            return false;
        }
        String tail = name.substring(cut + 3);
        if (tail.isEmpty()) {
            return false;
        }
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String mirrorName(String name) {
        if (name == null || name.isBlank()) {
            return "Mirror";
        }
        int cut = name.lastIndexOf(" · ");
        if (cut > 0) {
            String tail = name.substring(cut + 3);
            boolean digits = !tail.isEmpty();
            for (int i = 0; i < tail.length(); i++) {
                if (!Character.isDigit(tail.charAt(i))) {
                    digits = false;
                    break;
                }
            }
            if (digits) {
                name = name.substring(0, cut);
            }
        }
        if (name.length() > 40) {
            name = name.substring(0, 40).trim();
        }
        if (name.isBlank() || name.equals("Mirror")) {
            return "Mirror";
        }
        return name + " · mirror";
    }

    public static List<AABB> volumes(BlockPos origin, int clipX, int clipY, int clipZ, BlockPos anchor, Direction face, int pattern, int axes) {
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
        for (Shot shot : shots(anchor, face, pattern, false, axes)) {
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
    public static List<BlockPos> copiesOf(BlockPos pos, BlockPos anchor, Direction face, int pattern, int axes) {
        List<BlockPos> copies = new ArrayList<>();
        if (pos == null || anchor == null || face == null) {
            return copies;
        }
        Set<BlockPos> seen = new HashSet<>();
        seen.add(pos);
        for (Shot shot : shots(anchor, face, pattern, false, axes)) {
            BlockPos moved = shot.move(pos);
            if (moved != null && seen.add(moved)) {
                copies.add(moved);
            }
        }
        return copies;
    }

    public static BlockState restyle(BlockState state, BlockPos from, BlockPos to, BlockPos anchor, Direction face, int pattern, boolean shapes, int axes) {
        if (state == null || from == null || to == null || !shapes) {
            return state;
        }
        for (Shot shot : shots(anchor, face, pattern, true, axes)) {
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

    private static List<Shot> shots(BlockPos anchor, Direction face, int pattern, boolean shapes, int axes) {
        List<Shot> shots = new ArrayList<>();
        if ((axes & 7) != 0) {
            shots.addAll(axisShots(anchor, axes & 7, shapes));
            return shots;
        }
        int patternId = clampPattern(pattern);
        Direction.Axis axis = face.getAxis();
        if (patternId == SIXTEEN) {
            for (int step = 1; step <= 15; step++) {
                int turn = step;
                shots.add(new Shot() {
                    @Override
                    public BlockPos move(BlockPos pos) {
                        return circle(pos, anchor, turn, axis);
                    }

                    @Override
                    public BlockState restyle(BlockState state) {
                        return shapes ? spinState(state, axis, Math.round(turn / 4.0F)) : state;
                    }
                });
            }
            return shots;
        }
        if (patternId == EIGHT) {
            for (int step = 1; step <= 7; step++) {
                int kind = step;
                shots.add(new Shot() {
                    @Override
                    public BlockPos move(BlockPos pos) {
                        return around(pos, anchor, kind, axis);
                    }

                    @Override
                    public BlockState restyle(BlockState state) {
                        return shapes ? styleEight(state, kind, axis) : state;
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

    /** Every non-empty mix of the chosen axes. Each plane cuts through the middle of the anchor block. */
    private static List<Shot> axisShots(BlockPos anchor, int mask, boolean shapes) {
        List<Direction.Axis> chosen = new ArrayList<>();
        if ((mask & AXIS_X) != 0) {
            chosen.add(Direction.Axis.X);
        }
        if ((mask & AXIS_Y) != 0) {
            chosen.add(Direction.Axis.Y);
        }
        if ((mask & AXIS_Z) != 0) {
            chosen.add(Direction.Axis.Z);
        }
        List<Shot> shots = new ArrayList<>();
        int count = chosen.size();
        for (int subset = 1; subset < (1 << count); subset++) {
            int bits = subset;
            shots.add(new Shot() {
                @Override
                public BlockPos move(BlockPos pos) {
                    BlockPos at = pos;
                    for (int i = 0; i < count; i++) {
                        if ((bits & (1 << i)) == 0) {
                            continue;
                        }
                        at = reflectCenter(at, anchor, chosen.get(i));
                        if (at == null) {
                            return null;
                        }
                    }
                    return at;
                }

                @Override
                public BlockState restyle(BlockState state) {
                    if (!shapes) {
                        return state;
                    }
                    BlockState styled = state;
                    for (int i = 0; i < count; i++) {
                        if ((bits & (1 << i)) != 0) {
                            styled = flipAxis(styled, chosen.get(i));
                        }
                    }
                    return styled;
                }
            });
        }
        return shots;
    }

    /** Keep the clicked side when it is this axis. Otherwise the copy sits on the positive side. */
    private static Direction axisFace(Direction face, Direction.Axis axis) {
        if (face != null && face.getAxis() == axis) {
            return face;
        }
        return Direction.get(Direction.AxisDirection.POSITIVE, axis);
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

    /** Reflect through the middle of the anchor block, so a block two steps away lands two steps the other way. */
    private static BlockPos reflectCenter(BlockPos pos, BlockPos anchor, Direction.Axis axis) {
        int anchorValue = axis.choose(anchor.getX(), anchor.getY(), anchor.getZ());
        int value = axis.choose(pos.getX(), pos.getY(), pos.getZ());
        long mirrored = anchorValue * 2L - value;
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

    /** The clicked face picks the axis. The plane cuts through the middle of the anchor block. */
    private static BlockPos reflect(BlockPos pos, BlockPos anchor, Direction face) {
        return reflectCenter(pos, anchor, face.getAxis());
    }

    /** Steps of 22.5 degrees around the middle, in the plane of the clicked face. Quarter turns stay on the grid. */
    private static BlockPos circle(BlockPos pos, BlockPos anchor, int step, Direction.Axis axis) {
        int turn = Math.floorMod(step, 16);
        int dx = pos.getX() - anchor.getX();
        int dy = pos.getY() - anchor.getY();
        int dz = pos.getZ() - anchor.getZ();
        int u = planeU(dx, dy, dz, axis);
        int v = planeV(dx, dy, dz, axis);
        int fixed = planeFixed(dx, dy, dz, axis);
        if (turn % 4 == 0) {
            int[] spun = spin2(u, v, turn / 4);
            return assemble(anchor, axis, spun[0], spun[1], fixed);
        }
        double angle = turn * (Math.PI / 8.0D);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double nu = u * cos - v * sin;
        double nv = u * sin + v * cos;
        Integer bu = blockOfCenter(anchorPart(anchor, axis, true) + 0.5D + nu);
        Integer bv = blockOfCenter(anchorPart(anchor, axis, false) + 0.5D + nv);
        if (bu == null || bv == null) {
            return null;
        }
        return assemble(anchor, axis, bu - anchorPart(anchor, axis, true), bv - anchorPart(anchor, axis, false), fixed);
    }

    private static Integer blockOfCenter(double center) {
        if (center >= Integer.MAX_VALUE || center < Integer.MIN_VALUE) {
            return null;
        }
        double nearest = Math.rint(center);
        if (Math.abs(center - nearest) < 1.0e-4D) {
            center = nearest;
        }
        return (int) Math.floor(center);
    }

    /** Quarter turns and flips around the anchor corner, in the plane of the clicked face. */
    private static BlockPos around(BlockPos pos, BlockPos anchor, int kind, Direction.Axis axis) {
        int dx = pos.getX() - anchor.getX();
        int dy = pos.getY() - anchor.getY();
        int dz = pos.getZ() - anchor.getZ();
        int u = planeU(dx, dy, dz, axis);
        int v = planeV(dx, dy, dz, axis);
        int nu = u;
        int nv = v;
        switch (kind) {
            case 1 -> nu = -u;
            case 2 -> nv = -v;
            case 3 -> {
                nu = -u;
                nv = -v;
            }
            case 4 -> {
                int[] spun = spin2(u, v, 1);
                nu = spun[0];
                nv = spun[1];
            }
            case 5 -> {
                int[] spun = spin2(u, v, 3);
                nu = spun[0];
                nv = spun[1];
            }
            case 6 -> {
                nu = v;
                nv = u;
            }
            default -> {
                nu = -v;
                nv = -u;
            }
        }
        return assemble(anchor, axis, nu, nv, planeFixed(dx, dy, dz, axis));
    }

    private static BlockState styleEight(BlockState state, int kind, Direction.Axis axis) {
        if (axis == Direction.Axis.Y) {
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
        return switch (kind) {
            case 1 -> mirrorPlane(state, axis, true);
            case 2 -> mirrorPlane(state, axis, false);
            case 3 -> spinState(state, axis, 2);
            case 4 -> spinState(state, axis, 1);
            case 5 -> spinState(state, axis, 3);
            case 6 -> mirrorPlane(spinState(state, axis, 1), axis, true);
            default -> mirrorPlane(spinState(state, axis, 1), axis, false);
        };
    }

    /** Clockwise quarters around {@code axis}, matching a top-face circle when the axis is Y. */
    private static BlockState spinState(BlockState state, Direction.Axis axis, int quarters) {
        int turn = Math.floorMod(quarters, 4);
        if (turn == 0) {
            return state;
        }
        if (axis == Direction.Axis.Y) {
            Rotation rotation = switch (turn) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            return state.rotate(rotation);
        }
        BlockState spun = state;
        for (int i = 0; i < turn; i++) {
            spun = mapDirections(spun, dir -> turnDir(dir, axis), value -> turnAxis(value, axis));
        }
        if (turn == 2) {
            spun = flipHalves(spun);
        }
        return spun;
    }

    private static BlockState mirrorPlane(BlockState state, Direction.Axis axis, boolean alongU) {
        BlockState mapped = mapDirections(state, dir -> alongU ? mirrorU(dir, axis) : mirrorV(dir, axis), null);
        boolean vertical = alongU ? axis == Direction.Axis.Z : axis == Direction.Axis.X;
        return vertical ? flipHalves(mapped) : mapped;
    }

    /** The two plane axes, then the clicked axis. Clockwise is (u, v) to (-v, u). */
    private static int planeU(int dx, int dy, int dz, Direction.Axis axis) {
        return switch (axis) {
            case X -> dz;
            case Z -> dy;
            default -> dx;
        };
    }

    private static int planeV(int dx, int dy, int dz, Direction.Axis axis) {
        return switch (axis) {
            case X -> dy;
            case Z -> dx;
            default -> dz;
        };
    }

    private static int planeFixed(int dx, int dy, int dz, Direction.Axis axis) {
        return switch (axis) {
            case X -> dx;
            case Z -> dz;
            default -> dy;
        };
    }

    private static int anchorPart(BlockPos anchor, Direction.Axis axis, boolean u) {
        int dx = anchor.getX();
        int dy = anchor.getY();
        int dz = anchor.getZ();
        return u ? planeU(dx, dy, dz, axis) : planeV(dx, dy, dz, axis);
    }

    private static int[] spin2(int u, int v, int quarter) {
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new int[] {-v, u};
            case 2 -> new int[] {-u, -v};
            case 3 -> new int[] {v, -u};
            default -> new int[] {u, v};
        };
    }

    private static BlockPos assemble(BlockPos anchor, Direction.Axis axis, int u, int v, int fixed) {
        long x;
        long y;
        long z;
        switch (axis) {
            case X -> {
                x = anchor.getX() + (long) fixed;
                y = anchor.getY() + (long) v;
                z = anchor.getZ() + (long) u;
            }
            case Z -> {
                x = anchor.getX() + (long) v;
                y = anchor.getY() + (long) u;
                z = anchor.getZ() + (long) fixed;
            }
            default -> {
                x = anchor.getX() + (long) u;
                y = anchor.getY() + (long) fixed;
                z = anchor.getZ() + (long) v;
            }
        }
        if (x > Integer.MAX_VALUE || x < Integer.MIN_VALUE || y > Integer.MAX_VALUE || y < Integer.MIN_VALUE || z > Integer.MAX_VALUE || z < Integer.MIN_VALUE) {
            return null;
        }
        return new BlockPos((int) x, (int) y, (int) z);
    }

    private static Direction turnDir(Direction dir, Direction.Axis axis) {
        return switch (axis) {
            case X -> switch (dir) {
                case UP -> Direction.NORTH;
                case NORTH -> Direction.DOWN;
                case DOWN -> Direction.SOUTH;
                case SOUTH -> Direction.UP;
                default -> dir;
            };
            case Z -> switch (dir) {
                case UP -> Direction.EAST;
                case EAST -> Direction.DOWN;
                case DOWN -> Direction.WEST;
                case WEST -> Direction.UP;
                default -> dir;
            };
            default -> switch (dir) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                default -> dir;
            };
        };
    }

    private static Direction.Axis turnAxis(Direction.Axis value, Direction.Axis around) {
        if (value == around) {
            return value;
        }
        return switch (around) {
            case X -> value == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
            case Z -> value == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
            default -> value == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        };
    }

    private static Direction mirrorU(Direction dir, Direction.Axis axis) {
        Direction.Axis flipped = switch (axis) {
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.Y;
            default -> Direction.Axis.X;
        };
        return dir.getAxis() == flipped ? dir.getOpposite() : dir;
    }

    private static Direction mirrorV(Direction dir, Direction.Axis axis) {
        Direction.Axis flipped = switch (axis) {
            case X -> Direction.Axis.Y;
            case Z -> Direction.Axis.X;
            default -> Direction.Axis.Z;
        };
        return dir.getAxis() == flipped ? dir.getOpposite() : dir;
    }

    private static BlockState mapDirections(BlockState state, java.util.function.UnaryOperator<Direction> dirs, java.util.function.UnaryOperator<Direction.Axis> axes) {
        BlockState next = state;
        for (Property<?> property : state.getProperties()) {
            next = mapOne(state, next, property, dirs, axes);
        }
        return next;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState mapOne(BlockState source, BlockState next, Property<T> property, java.util.function.UnaryOperator<Direction> dirs, java.util.function.UnaryOperator<Direction.Axis> axes) {
        if (property.getValueClass() == Direction.class) {
            Property<Direction> facing = (Property<Direction>) property;
            Direction turned = dirs.apply(source.getValue(facing));
            if (facing.getPossibleValues().contains(turned)) {
                return next.setValue(facing, turned);
            }
        } else if (axes != null && property.getValueClass() == Direction.Axis.class) {
            Property<Direction.Axis> axisProp = (Property<Direction.Axis>) property;
            Direction.Axis turned = axes.apply(source.getValue(axisProp));
            if (axisProp.getPossibleValues().contains(turned)) {
                return next.setValue(axisProp, turned);
            }
        }
        return next;
    }

    private static BlockState flipAxis(BlockState state, Direction.Axis axis) {
        if (axis == Direction.Axis.Y) {
            return flipVertical(state);
        }
        return state.mirror(axis == Direction.Axis.X ? net.minecraft.world.level.block.Mirror.FRONT_BACK : net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
    }

    private static BlockState flipHalves(BlockState state) {
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
        return state;
    }

    private static BlockState flipVertical(BlockState state) {
        state = flipHalves(state);
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
