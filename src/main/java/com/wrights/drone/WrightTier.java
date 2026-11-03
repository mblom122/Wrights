package com.wrights.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import com.wrights.item.ModItems;

/**
 * Stock through netherite. A crafted wright has no upgrade. Any higher tier can
 * be applied directly with that tier's upgrade item; intermediate steps are not
 * required.
 *
 * <p>Speed (Basic 200 through Netherite 1200) only changes flight. How many blocks
 * one operation can touch is a separate tier, {@link #operationCap(int)}.
 */
public final class WrightTier {
    public static final int BASE = 0;
    public static final int COPPER = 1;
    public static final int IRON = 2;
    public static final int GOLD = 3;
    public static final int DIAMOND = 4;
    public static final int NETHERITE = 5;

    /** Basic wright speed score. */
    public static final int SPEED_BASE = 200;
    /** Netherite wright speed score. */
    public static final int SPEED_NETHERITE = 1200;

    private WrightTier() {
    }

    public static int clamp(int tier) {
        return Mth.clamp(tier, BASE, Swarm.MAX_SPEED);
    }

    /** Flight score for this tier (Basic 200 … Netherite 1200). Placing does not use it. */
    public static int speed(int tier) {
        return switch (clamp(tier)) {
            case COPPER -> 300;
            case IRON -> 450;
            case GOLD -> 700;
            case DIAMOND -> 900;
            case NETHERITE -> SPEED_NETHERITE;
            default -> SPEED_BASE;
        };
    }

    /** How much faster than Basic this tier's speed is (0 for Basic, 500 for Netherite). */
    public static int speedBonusPercent(int tier) {
        int score = speed(tier);
        return Math.max(0, (score - SPEED_BASE) * 100 / SPEED_BASE);
    }

    public static int textColor(int tier) {
        return switch (clamp(tier)) {
            case COPPER -> 0xFFFFB45A;
            case IRON -> 0xFF8CF59C;
            case GOLD -> 0xFF7FE0FF;
            case DIAMOND -> 0xFFC6A2FF;
            case NETHERITE -> 0xFFFF6F86;
            default -> 0xFFD5DCE4;
        };
    }

    public static int modelColor(int tier) {
        return switch (clamp(tier)) {
            case COPPER -> 0xFFC47A3A;
            case IRON -> 0xFFC5C9CF;
            case GOLD -> 0xFFF0C44A;
            case DIAMOND -> 0xFF5EDED4;
            case NETHERITE -> 0xFF6B5564;
            default -> 0xFF8E98A4;
        };
    }

    public static String nameKey(int tier) {
        return "drone.wrights.speed." + clamp(tier);
    }

    /**
     * Old place-rate eighths. Kept so older call sites still compile. New placing uses
     * {@link #operationCap(int)} and does not read speed.
     */
    public static int placeEighths(int tier) {
        int score = speed(tier);
        return 4 + (100 - 4) * (score - SPEED_BASE) / (SPEED_NETHERITE - SPEED_BASE);
    }

    /** Blocks one operation may touch. Basic is the occupied cell; Netherite is the full 19-block sphere. */
    public static int operationCap(int tier) {
        return switch (clamp(tier)) {
            case BASE -> 1;
            case COPPER -> 3;
            case IRON -> 7;
            case GOLD -> 11;
            case DIAMOND -> 15;
            default -> 19;
        };
    }

    /**
     * Squared block distance from the cell the wright occupies. 0 is that cell, 1 is its six faces,
     * 2 is the 3×3×3 cube without the eight corners (19 cells). A cell two steps away on a line is 4, outside.
     */
    public static int operationReachSqr(int tier) {
        int mark = clamp(tier);
        if (mark <= BASE) {
            return 0;
        }
        return mark <= IRON ? 1 : 2;
    }

    /** Item that sets operations to this tier. Basic has none. */
    public static Item operationItem(int tier) {
        return switch (clamp(tier)) {
            case COPPER -> ModItems.OPS_COPPER.get();
            case IRON -> ModItems.OPS_IRON.get();
            case GOLD -> ModItems.OPS_GOLD.get();
            case DIAMOND -> ModItems.OPS_DIAMOND.get();
            case NETHERITE -> ModItems.OPS_NETHERITE.get();
            default -> null;
        };
    }

    /** Flight step in blocks per tick while the wright is within eight blocks of its work. */
    public static double flyClose(int tier) {
        int score = speed(tier);
        // 200 → 0.25, 1200 → 2.75
        return 0.25D + 2.5D * (score - SPEED_BASE) / (double) (SPEED_NETHERITE - SPEED_BASE);
    }

    /** The crafted item that raises a wright of {@code tier - 1} to this tier. Stock has none. */
    public static Item upgradeItem(int tier) {
        return switch (clamp(tier)) {
            case COPPER -> ModItems.UPGRADE_COPPER.get();
            case IRON -> ModItems.UPGRADE_IRON.get();
            case GOLD -> ModItems.UPGRADE_GOLD.get();
            case DIAMOND -> ModItems.UPGRADE_DIAMOND.get();
            case NETHERITE -> ModItems.UPGRADE_NETHERITE.get();
            default -> null;
        };
    }
}
