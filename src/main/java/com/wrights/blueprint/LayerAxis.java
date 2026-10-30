package com.wrights.blueprint;

import com.wrights.schematic.Schematic;

/** Which coordinate a layer edit slices, inserts, or moves. */
public final class LayerAxis {
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;

    private LayerAxis() {
    }

    public static int clamp(int axis) {
        return axis < X || axis > Z ? Y : axis;
    }

    public static int of(int x, int y, int z, int axis) {
        return switch (clamp(axis)) {
            case X -> x;
            case Z -> z;
            default -> y;
        };
    }

    public static int get(Schematic.Entry entry, int axis) {
        return of(entry.x(), entry.y(), entry.z(), axis);
    }

    public static int size(Schematic schematic, int axis) {
        return of(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), axis);
    }

    public static int anchor(Schematic schematic, int axis) {
        return of(schematic.anchorX(), schematic.anchorY(), schematic.anchorZ(), axis);
    }

    public static Schematic.Entry set(Schematic.Entry entry, int axis, int value) {
        short next = (short) value;
        return switch (clamp(axis)) {
            case X -> entry.moved(next, entry.y(), entry.z());
            case Z -> entry.moved(entry.x(), entry.y(), next);
            default -> entry.moved(entry.x(), next, entry.z());
        };
    }

    /** World coordinate drawn across the layer view. */
    public static int horizontal(int axis, int x, int y, int z) {
        return clamp(axis) == X ? z : x;
    }

    /** World coordinate drawn down the layer view. Y grows upward, so side views flip it. */
    public static int vertical(int axis, int x, int y, int z) {
        return clamp(axis) == Y ? z : y;
    }

    public static boolean flipVertical(int axis) {
        return clamp(axis) != Y;
    }

    public static int[] cell(int axis, int layer, int horizontal, int vertical) {
        return switch (clamp(axis)) {
            case X -> new int[] { layer, vertical, horizontal };
            case Z -> new int[] { horizontal, vertical, layer };
            default -> new int[] { horizontal, layer, vertical };
        };
    }

    public static int horizontalAxis(int axis) {
        return clamp(axis) == X ? Z : X;
    }

    public static int verticalAxis(int axis) {
        return clamp(axis) == Y ? Z : Y;
    }

    public static String letter(int axis) {
        return switch (clamp(axis)) {
            case X -> "X";
            case Z -> "Z";
            default -> "Y";
        };
    }
}
