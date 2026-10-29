package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.Direction;

import com.wrights.schematic.Schematic;
import com.wrights.util.BlockFilters;

public final class BlueprintTransforms {
    public enum Op {
        ROTATE,
        ROTATE_CCW,
        FLIP_X,
        FLIP_Y,
        FLIP_Z,
        CENTER,
        GROUND
    }

    private BlueprintTransforms() {
    }

    public static Schematic apply(Schematic schematic, Op op) {
        return switch (op) {
            case ROTATE -> turn(schematic, true);
            case ROTATE_CCW -> turn(schematic, false);
            case FLIP_X -> mirror(schematic, true);
            case FLIP_Z -> mirror(schematic, false);
            case FLIP_Y -> flipVertical(schematic);
            case CENTER -> center(schematic);
            case GROUND -> ground(schematic);
        };
    }

    public static Schematic center(Schematic schematic) {
        Schematic.Entry nearest = nearest(schematic, schematic.sizeX() / 2, schematic.sizeY() / 2, schematic.sizeZ() / 2, false);
        if (nearest == null) {
            return schematic.withAnchor(schematic.sizeX() / 2, schematic.sizeY() / 2, schematic.sizeZ() / 2);
        }
        return schematic.withAnchor(nearest.x(), nearest.y(), nearest.z());
    }

    public static Schematic ground(Schematic schematic) {
        Schematic.Entry nearest = nearest(schematic, schematic.sizeX() / 2, 0, schematic.sizeZ() / 2, true);
        if (nearest == null) {
            return schematic.withAnchor(schematic.sizeX() / 2, 0, schematic.sizeZ() / 2);
        }
        return schematic.withAnchor(nearest.x(), nearest.y(), nearest.z());
    }

    private static Schematic turn(Schematic schematic, boolean clockwise) {
        int sizeX = schematic.sizeX();
        int sizeZ = schematic.sizeZ();
        List<Schematic.Entry> entries = new ArrayList<>();
        for (Schematic.Entry entry : schematic.entries()) {
            int x = entry.x();
            int z = entry.z();
            int nextX = clockwise ? sizeZ - 1 - z : z;
            int nextZ = clockwise ? x : sizeX - 1 - x;
            BlockState state = entry.state().rotate(clockwise ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
            entries.add(new Schematic.Entry((short) nextX, entry.y(), (short) nextZ, state, BlockFilters.isSecondary(state)));
        }
        int anchorX = clockwise ? sizeZ - 1 - schematic.anchorZ() : schematic.anchorZ();
        int anchorZ = clockwise ? schematic.anchorX() : sizeX - 1 - schematic.anchorX();
        return finish(schematic, sizeZ, schematic.sizeY(), sizeX, entries, anchorX, schematic.anchorY(), anchorZ);
    }

    private static Schematic mirror(Schematic schematic, boolean alongX) {
        List<Schematic.Entry> entries = new ArrayList<>();
        Mirror mirror = alongX ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT;
        for (Schematic.Entry entry : schematic.entries()) {
            int x = alongX ? schematic.sizeX() - 1 - entry.x() : entry.x();
            int z = alongX ? entry.z() : schematic.sizeZ() - 1 - entry.z();
            BlockState state = entry.state().mirror(mirror);
            entries.add(new Schematic.Entry((short) x, entry.y(), (short) z, state, BlockFilters.isSecondary(state)));
        }
        int anchorX = alongX ? schematic.sizeX() - 1 - schematic.anchorX() : schematic.anchorX();
        int anchorZ = alongX ? schematic.anchorZ() : schematic.sizeZ() - 1 - schematic.anchorZ();
        return finish(schematic, schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), entries, anchorX, schematic.anchorY(), anchorZ);
    }

    private static Schematic flipVertical(Schematic schematic) {
        List<Schematic.Entry> entries = new ArrayList<>();
        int sizeY = schematic.sizeY();
        for (Schematic.Entry entry : schematic.entries()) {
            int y = sizeY - 1 - entry.y();
            BlockState state = flipState(entry.state());
            entries.add(new Schematic.Entry(entry.x(), (short) y, entry.z(), state, BlockFilters.isSecondary(state)));
        }
        return finish(schematic, schematic.sizeX(), sizeY, schematic.sizeZ(), entries, schematic.anchorX(), sizeY - 1 - schematic.anchorY(), schematic.anchorZ());
    }

    private static Schematic finish(Schematic schematic, int sizeX, int sizeY, int sizeZ, List<Schematic.Entry> entries, int anchorX, int anchorY, int anchorZ) {
        entries.sort(Comparator.comparing(Schematic.Entry::secondary));
        return new Schematic(
                schematic.id(),
                schematic.name(),
                sizeX,
                sizeY,
                sizeZ,
                entries,
                schematic.sourceMin(),
                schematic.sourceMax(),
                schematic.sourceDimension(),
                anchorX,
                anchorY,
                anchorZ);
    }

    private static Schematic.Entry nearest(Schematic schematic, int x, int y, int z, boolean lowest) {
        Schematic.Entry best = null;
        int bestScore = Integer.MAX_VALUE;
        int floor = Integer.MAX_VALUE;
        if (lowest) {
            for (Schematic.Entry entry : schematic.entries()) {
                floor = Math.min(floor, entry.y());
            }
        }
        for (Schematic.Entry entry : schematic.entries()) {
            if (lowest && entry.y() != floor) {
                continue;
            }
            int score = Math.abs(entry.x() - x) + Math.abs(entry.y() - y) + Math.abs(entry.z() - z);
            if (score < bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return best;
    }

    private static BlockState flipState(BlockState state) {
        BlockState flipped = flipFacing(state);
        flipped = swapNamed(flipped, "half", "top", "bottom");
        flipped = swapNamed(flipped, "half", "lower", "upper");
        flipped = swapNamed(flipped, "type", "top", "bottom");
        return swapNamed(flipped, "face", "floor", "ceiling");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState flipFacing(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!property.getName().equals("facing") || property.getValueClass() != Direction.class) {
                continue;
            }
            Direction facing = (Direction) state.getValue(property);
            if (facing.getAxis() == Direction.Axis.Y) {
                return state.setValue((Property) property, facing.getOpposite());
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState swapNamed(BlockState state, String propertyName, String first, String second) {
        for (Property<?> property : state.getProperties()) {
            if (!property.getName().equals(propertyName)) {
                continue;
            }
            String current = state.getValue(property).toString();
            String target = current.equals(first) ? second : current.equals(second) ? first : null;
            if (target == null) {
                return state;
            }
            for (Comparable option : property.getPossibleValues()) {
                if (option.toString().equals(target)) {
                    return state.setValue((Property) property, option);
                }
            }
        }
        return state;
    }
}
