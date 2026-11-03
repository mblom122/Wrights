package com.wrights.drone;

import java.util.Locale;

import net.minecraft.util.Mth;

/**
 * Part tiers, Basic (no part fitted) through Apex. Every {@link PartSlot} uses the same ladder.
 * A rotor tier sets flight speed; a fabricator tier sets how many blocks one operation touches.
 */
public final class WrightTier {
    public static final int BASIC = 0;
    public static final int SCOUT = 1;
    public static final int RANGER = 2;
    public static final int VECTOR = 3;
    public static final int TEMPEST = 4;
    public static final int APEX = 5;

    /** Basic wright speed score. */
    public static final int SPEED_BASIC = 200;
    /** Apex wright speed score. */
    public static final int SPEED_APEX = 1200;

    private WrightTier() {
    }

    public static int clamp(int tier) {
        return Mth.clamp(tier, BASIC, APEX);
    }

    /** Flight score for a rotor tier (Basic 200 … Apex 1200). */
    public static int speed(int tier) {
        return switch (clamp(tier)) {
            case SCOUT -> 300;
            case RANGER -> 450;
            case VECTOR -> 700;
            case TEMPEST -> 900;
            case APEX -> SPEED_APEX;
            default -> SPEED_BASIC;
        };
    }

    public static int textColor(int tier) {
        return switch (clamp(tier)) {
            case SCOUT -> 0xFFFFB45A;
            case RANGER -> 0xFF8CF59C;
            case VECTOR -> 0xFF7FE0FF;
            case TEMPEST -> 0xFFC6A2FF;
            case APEX -> 0xFFFF6F86;
            default -> 0xFFD5DCE4;
        };
    }

    /** Accent tint of a part at this tier. */
    public static int modelColor(int tier) {
        return switch (clamp(tier)) {
            case SCOUT -> 0xFFFFA040;
            case RANGER -> 0xFF5CE07A;
            case VECTOR -> 0xFF4FC8FF;
            case TEMPEST -> 0xFFA67CFF;
            case APEX -> 0xFFFF4F6A;
            default -> 0xFF8E98A4;
        };
    }

    public static String nameKey(int tier) {
        return "tier.wrights." + clamp(tier);
    }

    /** Blocks one operation may touch. Basic is the occupied cell; Apex is the full 19-block sphere. */
    public static int operationCap(int tier) {
        return switch (clamp(tier)) {
            case BASIC -> 1;
            case SCOUT -> 3;
            case RANGER -> 7;
            case VECTOR -> 11;
            case TEMPEST -> 15;
            default -> 19;
        };
    }

    /**
     * Squared block distance from the cell the wright occupies. 0 is that cell, 1 is its six faces,
     * 2 is the 3×3×3 cube without the eight corners (19 cells). A cell two steps away on a line is 4, outside.
     */
    public static int operationReachSqr(int tier) {
        int mark = clamp(tier);
        if (mark <= BASIC) {
            return 0;
        }
        return mark <= RANGER ? 1 : 2;
    }

    /** Flight step in blocks per tick while the wright is within eight blocks of its work. */
    public static double flyClose(int tier) {
        int score = speed(tier);
        // 200 → 0.25, 1200 → 2.75
        return 0.25D + 2.5D * (score - SPEED_BASIC) / (double) (SPEED_APEX - SPEED_BASIC);
    }

    /** Near-build flight distance covered in one second (20 ticks × {@link #flyClose}). */
    public static double flyBlocksPerSecond(int tier) {
        return flyClose(tier) * 20.0D;
    }

    /** Short label for tooltips / Manage (e.g. {@code 5}, {@code 17.5}, {@code 55}). */
    public static String flyRateLabel(int tier) {
        double bps = flyBlocksPerSecond(tier);
        if (Math.abs(bps - Math.rint(bps)) < 1.0E-6D) {
            return Integer.toString((int) Math.rint(bps));
        }
        return String.format(Locale.ROOT, "%.1f", bps);
    }
}
