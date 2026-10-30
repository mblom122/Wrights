package com.wrights.block;

public enum HangarSignal {
    GREEN,
    YELLOW,
    RED;

    public static HangarSignal byId(int id) {
        HangarSignal[] values = values();
        if (id < 0 || id >= values.length) {
            return GREEN;
        }
        return values[id];
    }

    public int id() {
        return ordinal();
    }

    public int tint() {
        return switch (this) {
            case GREEN -> 0xFF3DDC64;
            case YELLOW -> 0xFFFFC030;
            case RED -> 0xFFFF3B3B;
        };
    }
}
