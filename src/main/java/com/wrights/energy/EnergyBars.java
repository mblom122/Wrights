package com.wrights.energy;

/** The six-bar charge gauge on the wrightport tower and the personal wrightport's cells. */
public final class EnergyBars {
    public static final int BARS = 6;

    private EnergyBars() {
    }

    /** Lit bars, 0 only when empty and 6 only when more than five sixths full. */
    public static int lit(long stored, long capacity) {
        if (stored <= 0 || capacity <= 0) {
            return 0;
        }
        long lit = (Math.min(stored, capacity) * BARS + capacity - 1) / capacity;
        return Math.clamp(lit, 1, BARS);
    }
}
