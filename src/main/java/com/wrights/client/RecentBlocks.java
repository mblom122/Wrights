package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import com.wrights.WrightsClientConfig;
import com.wrights.util.BlockFilters;

public final class RecentBlocks {
    private static final int LIMIT = 18;
    private static final List<String> ORDER = new ArrayList<>();
    private static boolean loaded;

    private RecentBlocks() {
    }

    public record Pick(Item item, Block fluid) {
    }

    public static List<Pick> picks() {
        load();
        List<Pick> out = new ArrayList<>();
        for (String token : ORDER) {
            Pick pick = decode(token);
            if (pick != null) {
                out.add(pick);
            }
        }
        return out;
    }

    public static void remember(Item item) {
        if (!(item instanceof BlockItem)) {
            return;
        }
        bump(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    public static void rememberFluid(Block block) {
        if (block == null || !BlockFilters.isSourceFluid(block.defaultBlockState())) {
            return;
        }
        bump("~" + BuiltInRegistries.BLOCK.getKey(block));
    }

    private static void bump(String token) {
        load();
        ORDER.remove(token);
        ORDER.add(0, token);
        if (ORDER.size() > LIMIT) {
            ORDER.subList(LIMIT, ORDER.size()).clear();
        }
        WrightsClientConfig.setRecentBlocks(String.join(",", ORDER));
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        String raw = WrightsClientConfig.RECENT_BLOCKS.get();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            if (part.isBlank() || decode(part.trim()) == null || ORDER.contains(part.trim())) {
                continue;
            }
            ORDER.add(part.trim());
        }
    }

    private static Pick decode(String token) {
        if (token.startsWith("~")) {
            ResourceLocation id = ResourceLocation.tryParse(token.substring(1));
            if (id == null) {
                return null;
            }
            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null || !BlockFilters.isSourceFluid(block.defaultBlockState())) {
                return null;
            }
            return new Pick(null, block);
        }
        ResourceLocation id = ResourceLocation.tryParse(token);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (!(item instanceof BlockItem)) {
            return null;
        }
        return new Pick(item, null);
    }
}
