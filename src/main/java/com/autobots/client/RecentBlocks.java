package com.autobots.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import com.autobots.AutobotsClientConfig;

public final class RecentBlocks {
    private static final int LIMIT = 18;
    private static final List<Item> ITEMS = new ArrayList<>();
    private static boolean loaded;

    private RecentBlocks() {
    }

    public static List<Item> items() {
        load();
        return List.copyOf(ITEMS);
    }

    public static void remember(Item item) {
        if (!(item instanceof BlockItem)) {
            return;
        }
        load();
        ITEMS.remove(item);
        ITEMS.add(0, item);
        if (ITEMS.size() > LIMIT) {
            ITEMS.subList(LIMIT, ITEMS.size()).clear();
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < ITEMS.size(); i++) {
            if (i > 0) {
                joined.append(',');
            }
            joined.append(BuiltInRegistries.ITEM.getKey(ITEMS.get(i)));
        }
        AutobotsClientConfig.setRecentBlocks(joined.toString());
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        String raw = AutobotsClientConfig.RECENT_BLOCKS.get();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(part.trim());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                if (item instanceof BlockItem && !ITEMS.contains(item)) {
                    ITEMS.add(item);
                }
            });
        }
    }
}
