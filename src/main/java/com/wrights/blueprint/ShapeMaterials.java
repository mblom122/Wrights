package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Weighted block palette for Shape mode. Higher weight means more likely to be placed.
 */
public final class ShapeMaterials {
    public record Entry(Item item, int weight) {
        public Entry {
            weight = clampWeight(weight);
        }

        public BlockState state() {
            if (!(item instanceof BlockItem blockItem)) {
                return null;
            }
            BlockState state = blockItem.getBlock().defaultBlockState();
            return state.isAir() ? null : state;
        }
    }

    private ShapeMaterials() {
    }

    public static int clampWeight(int weight) {
        return Mth.clamp(weight, 1, 1000);
    }

    public static List<Entry> parse(String raw) {
        LinkedHashMap<Item, Integer> map = new LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split("[;|]")) {
                if (part.isBlank()) {
                    continue;
                }
                String[] bits = part.split("[=,]", 2);
                ResourceLocation id = ResourceLocation.tryParse(bits[0].trim().toLowerCase(Locale.ROOT));
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(id);
                if (!(item instanceof BlockItem)) {
                    continue;
                }
                int weight = 1;
                if (bits.length > 1) {
                    try {
                        weight = Integer.parseInt(bits[1].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                map.put(item, clampWeight(weight));
            }
        }
        List<Entry> out = new ArrayList<>(map.size());
        for (Map.Entry<Item, Integer> entry : map.entrySet()) {
            out.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    public static String encode(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (entry == null || entry.item() == null) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.item());
            if (id == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(id).append('=').append(clampWeight(entry.weight()));
        }
        return out.toString();
    }

    public static List<Entry> add(List<Entry> current, Item item) {
        List<Entry> next = new ArrayList<>(current == null ? List.of() : current);
        if (!(item instanceof BlockItem)) {
            return next;
        }
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).item() == item) {
                return next;
            }
        }
        next.add(new Entry(item, 1));
        return next;
    }

    public static List<Entry> remove(List<Entry> current, int index) {
        List<Entry> next = new ArrayList<>(current == null ? List.of() : current);
        if (index < 0 || index >= next.size() || next.size() <= 1) {
            return next;
        }
        next.remove(index);
        return next;
    }

    public static List<Entry> setWeight(List<Entry> current, int index, int weight) {
        List<Entry> next = new ArrayList<>(current == null ? List.of() : current);
        if (index < 0 || index >= next.size()) {
            return next;
        }
        Entry old = next.get(index);
        next.set(index, new Entry(old.item(), weight));
        return next;
    }

    public static BlockState pick(List<Entry> entries, Random random) {
        List<BlockState> states = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Entry entry : entries) {
            BlockState state = entry.state();
            if (state == null) {
                continue;
            }
            states.add(state);
            int weight = clampWeight(entry.weight());
            weights.add(weight);
            total += weight;
        }
        if (states.isEmpty() || total <= 0) {
            return null;
        }
        if (states.size() == 1 || random == null) {
            return states.get(0);
        }
        int roll = random.nextInt(total);
        int cursor = 0;
        for (int i = 0; i < states.size(); i++) {
            cursor += weights.get(i);
            if (roll < cursor) {
                return states.get(i);
            }
        }
        return states.get(states.size() - 1);
    }

    public static List<BlockState> states(List<Entry> entries) {
        List<BlockState> out = new ArrayList<>();
        for (Entry entry : entries) {
            BlockState state = entry.state();
            if (state != null) {
                out.add(state);
            }
        }
        return out;
    }

    public static Item itemOf(Block block) {
        return block == null ? null : block.asItem();
    }
}
