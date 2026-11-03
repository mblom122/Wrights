package com.wrights.drone;

import java.util.Locale;

/**
 * Part tiers, Basic (no part fitted) through Apex. Every {@link PartSlot} uses the same ladder.
 * A rotor tier sets flight speed and energy draw; a fabricator tier sets how many blocks one operation touches;
 * a Power Core tier sets how much of the rotor's draw it covers.
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
        return Math.clamp(tier, BASIC, APEX);
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
     * Squared block distance from the cell the wright occupies.
     * 0 = standing cell (1), 1 = cell + six faces (7), 2 = 3×3×3 minus corners (19).
     */
    public static int operationReachSqr(int tier) {
        return switch (clamp(tier)) {
            case BASIC -> 0;
            case SCOUT -> 1;
            case RANGER, VECTOR, TEMPEST, APEX -> 2;
            default -> 0;
        };
    }

    /** How many cells {@link #operationReachSqr} covers at this fabricator tier. */
    public static int operationReachCells(int tier) {
        return switch (operationReachSqr(tier)) {
            case 0 -> 1;
            case 1 -> 7;
            default -> 19;
        };
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

    /**
     * Energy a rotor tier draws per block, in percent of Basic. A Power Core of a tier is rated for the rotor of
     * the same tier: it divides by the same figure, so a matched pair works at the Basic cost.
     */
    public static int rotorDraw(int tier) {
        return switch (clamp(tier)) {
            case SCOUT -> 115;
            case RANGER -> 130;
            case VECTOR -> 150;
            case TEMPEST -> 175;
            case APEX -> 200;
            default -> 100;
        };
    }

    /** Percent of the configured block cost a wright pays: its rotor's draw over its core's rating. */
    public static int energyPercent(int rotor, int core) {
        return Math.round(100.0F * rotorDraw(rotor) / rotorDraw(core));
    }

    /** {@code base} Forge Energy scaled by {@link #energyPercent}, rounded up so a block never becomes free. */
    public static int energyCost(int base, int rotor, int core) {
        if (base <= 0) {
            return base;
        }
        return (int) Math.max(1L, ((long) base * rotorDraw(rotor) + rotorDraw(core) - 1) / rotorDraw(core));
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
