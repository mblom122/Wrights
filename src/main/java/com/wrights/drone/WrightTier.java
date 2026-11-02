package com.wrights.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import com.wrights.item.ModItems;

/**
 * Stock through netherite. A crafted wright has no upgrade. Any higher tier can
 * be applied directly with that tier's upgrade item; intermediate steps are not
 * required.
 *
 * <p>Each tier has one Speed score (shown in tooltips). Place rate and near-work
 * flight scale with that score from Basic (200) up to Netherite (1200), matching
 * the rounded Scheme B endpoints.
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

    /**
     * Single speed score for this tier (Basic 200 … Netherite 1200). Tooltips and
     * place/fly both use this.
     */
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
            case COPPER -> 0xFFE08A45;
            case IRON -> 0xFFD8DCE2;
            case GOLD -> 0xFFF6D56A;
            case DIAMOND -> 0xFF7EF0E8;
            case NETHERITE -> 0xFFE0C2D0;
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
     * Eighths of a block this tier adds each tick. Basic places one block every
     * other tick (4 eighths). Scales with {@link #speed(int)} so Netherite reaches
     * 12.5 blocks/tick (100 eighths).
     */
    public static int placeEighths(int tier) {
        int score = speed(tier);
        // 200 → 4, 1200 → 100
        return 4 + (100 - 4) * (score - SPEED_BASE) / (SPEED_NETHERITE - SPEED_BASE);
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
