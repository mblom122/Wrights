package com.wrights.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import com.wrights.item.ModItems;

/**
 * Stock through netherite. A crafted wright has no upgrade. Any higher tier can
 * be applied directly with that tier's upgrade item; intermediate steps are not
 * required. Each step, including stock to copper, is the same pace gap.
 */
public final class WrightTier {
    public static final int BASE = 0;
    public static final int COPPER = 1;
    public static final int IRON = 2;
    public static final int GOLD = 3;
    public static final int DIAMOND = 4;
    public static final int NETHERITE = 5;

    private WrightTier() {
    }

    public static int clamp(int tier) {
        return Mth.clamp(tier, BASE, Swarm.MAX_SPEED);
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
     * Eighths of a block this tier adds each tick. Stock places one block every
     * other tick. Each upgrade adds nineteen eighths, the old copper-to-iron step.
     */
    public static int placeEighths(int tier) {
        return 4 + 19 * (clamp(tier) - BASE);
    }

    /** Flight step in blocks per tick while the wright is within eight blocks of its work. */
    public static double flyClose(int tier) {
        return 0.23D + 0.5325D * (clamp(tier) - BASE);
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
