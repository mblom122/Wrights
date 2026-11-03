package com.wrights.storage;

import java.util.List;

import net.minecraft.world.item.Item;

import com.wrights.item.ModItems;

/** Wrights items shown on the Info tab and queried against digital storage. */
public final class DigitalCatalog {
    private DigitalCatalog() {
    }

    public static List<Item> items() {
        List<Item> items = new java.util.ArrayList<>();
        items.add(ModItems.WRIGHT.get());
        ModItems.ROTORS.forEach(part -> items.add(part.get()));
        ModItems.FABRICATORS.forEach(part -> items.add(part.get()));
        return List.copyOf(items);
    }
}
