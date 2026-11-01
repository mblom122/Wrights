package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.ToLongFunction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shape material palette with Random (weighted), Priority (use until stock runs out),
 * and Order (alternate left-to-right in batches) fill modes.
 */
public final class ShapeMaterials {
    public enum FillMode {
        RANDOM,
        PRIORITY,
        ORDER;

        public static FillMode byId(int id) {
            FillMode[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }
    }

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

    public static int clampBatch(int batch) {
        return Mth.clamp(batch, 1, 64);
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

    public static List<Entry> move(List<Entry> current, int index, int delta) {
        List<Entry> next = new ArrayList<>(current == null ? List.of() : current);
        int target = index + delta;
        if (index < 0 || index >= next.size() || target < 0 || target >= next.size()) {
            return next;
        }
        Entry value = next.remove(index);
        next.add(target, value);
        return next;
    }

    /**
     * Assign one state per cell. Priority uses stock left-to-right; when {@code fillShortfall}
     * is false it may return fewer states than {@code count}.
     */
    public static List<BlockState> assign(List<Entry> entries, int count, FillMode mode, int orderBatch, Random random, boolean creative, ToLongFunction<Item> stock, boolean fillShortfall) {
        List<BlockState> out = new ArrayList<>(Math.max(0, count));
        if (count <= 0) {
            return out;
        }
        List<Entry> usable = usable(entries);
        if (usable.isEmpty()) {
            return out;
        }
        return switch (mode == null ? FillMode.RANDOM : mode) {
            case PRIORITY -> assignPriority(usable, count, creative, stock, fillShortfall);
            case ORDER -> assignOrder(usable, count, orderBatch);
            case RANDOM -> assignRandom(usable, count, random);
        };
    }

    public static List<BlockState> assign(List<Entry> entries, int count, FillMode mode, int orderBatch, Random random, boolean creative, ToLongFunction<Item> stock) {
        return assign(entries, count, mode, orderBatch, random, creative, stock, true);
    }

    private static List<Entry> usable(List<Entry> entries) {
        List<Entry> usable = new ArrayList<>();
        if (entries == null) {
            return usable;
        }
        for (Entry entry : entries) {
            if (entry != null && entry.state() != null) {
                usable.add(entry);
            }
        }
        return usable;
    }

    private static List<BlockState> assignRandom(List<Entry> usable, int count, Random random) {
        List<BlockState> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockState state = pick(usable, random);
            if (state != null) {
                out.add(state);
            }
        }
        return out;
    }

    private static List<BlockState> assignOrder(List<Entry> usable, int count, int orderBatch) {
        List<BlockState> out = new ArrayList<>(count);
        int batch = clampBatch(orderBatch);
        for (int i = 0; i < count; i++) {
            int index = (i / batch) % usable.size();
            out.add(usable.get(index).state());
        }
        return out;
    }

    private static List<BlockState> assignPriority(List<Entry> usable, int count, boolean creative, ToLongFunction<Item> stock, boolean fillShortfall) {
        List<BlockState> out = new ArrayList<>(count);
        int left = count;
        for (int i = 0; i < usable.size() && left > 0; i++) {
            Entry entry = usable.get(i);
            BlockState state = entry.state();
            long have = creative || stock == null ? left : Math.max(0L, stock.applyAsLong(entry.item()));
            int take = (int) Math.min(left, Math.min(Integer.MAX_VALUE, have));
            if (take <= 0) {
                continue;
            }
            for (int n = 0; n < take; n++) {
                out.add(state);
            }
            left -= take;
        }
        if (left > 0 && fillShortfall) {
            BlockState fallback = usable.get(usable.size() - 1).state();
            for (int n = 0; n < left; n++) {
                out.add(fallback);
            }
        }
        return out;
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
