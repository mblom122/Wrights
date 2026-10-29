package com.wrights.drone;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import com.wrights.item.ModItems;

/**
 * Copper through netherite. The tier is the wright's speed: a higher tier places more
 * blocks and flies faster.
 */
public final class WrightTier {
    public static final int COPPER = 1;
    public static final int IRON = 2;
    public static final int GOLD = 3;
    public static final int DIAMOND = 4;
    public static final int NETHERITE = 5;

    private WrightTier() {
    }

    public static int clamp(int tier) {
        return Mth.clamp(tier, COPPER, Swarm.MAX_SPEED);
    }

    public static int textColor(int tier) {
        return switch (clamp(tier)) {
            case IRON -> 0xFFD8DCE2;
            case GOLD -> 0xFFF6D56A;
            case DIAMOND -> 0xFF7EF0E8;
            case NETHERITE -> 0xFFE0C2D0;
            default -> 0xFFE08A45;
        };
    }

    public static int modelColor(int tier) {
        return switch (clamp(tier)) {
            case IRON -> 0xFFC5C9CF;
            case GOLD -> 0xFFF0C44A;
            case DIAMOND -> 0xFF5EDED4;
            case NETHERITE -> 0xFF6B5564;
            default -> 0xFFC47A3A;
        };
    }

    public static String nameKey(int tier) {
        return "drone.wrights.speed." + clamp(tier);
    }

    /** The crafted item that raises a wright of {@code tier - 1} to this tier. Copper has none. */
    public static Item upgradeItem(int tier) {
        return switch (clamp(tier)) {
            case IRON -> ModItems.UPGRADE_IRON.get();
            case GOLD -> ModItems.UPGRADE_GOLD.get();
            case DIAMOND -> ModItems.UPGRADE_DIAMOND.get();
            case NETHERITE -> ModItems.UPGRADE_NETHERITE.get();
            default -> null;
        };
    }
}
