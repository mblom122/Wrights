package com.wrights.storage;

import java.util.List;

import net.minecraft.world.item.Item;

import com.wrights.item.ModItems;

/** Wrights items shown on the Info tab and queried against digital storage. */
public final class DigitalCatalog {
    private DigitalCatalog() {
    }

    public static List<Item> items() {
        return List.of(
                ModItems.WRIGHT.get(),
                ModItems.UPGRADE_IRON.get(),
                ModItems.UPGRADE_GOLD.get(),
                ModItems.UPGRADE_DIAMOND.get(),
                ModItems.UPGRADE_NETHERITE.get());
    }
}
