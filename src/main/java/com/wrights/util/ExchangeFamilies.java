package com.wrights.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

/**
 * Exchange "Similar blocks" matching — AE2-style item-tag families
 * (planks, cobblestones, wool, …). Looking at one member expands to the whole tag.
 */
public final class ExchangeFamilies {
    public enum Match {
        SAME,
        SIMILAR,
        ANY;

        public static Match byId(int id) {
            Match[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }

        public boolean needsLook() {
            return this != ANY;
        }
    }

    /**
     * Curated substitution families. Prefer specific crafting-style tags over broad
     * ones like mineable/pickaxe. Parent tags (e.g. {@code c:cobblestones}) cover variants.
     */
    private static final List<TagKey<Item>> FAMILIES = List.of(
            ItemTags.PLANKS,
            ItemTags.LOGS,
            ItemTags.LOGS_THAT_BURN,
            ItemTags.WOOL,
            ItemTags.WOOL_CARPETS,
            ItemTags.STONE_BRICKS,
            ItemTags.LEAVES,
            ItemTags.SAPLINGS,
            ItemTags.FLOWERS,
            ItemTags.TERRACOTTA,
            ItemTags.DIRT,
            ItemTags.SAND,
            ItemTags.COAL_ORES,
            ItemTags.COPPER_ORES,
            ItemTags.IRON_ORES,
            ItemTags.GOLD_ORES,
            ItemTags.DIAMOND_ORES,
            ItemTags.EMERALD_ORES,
            ItemTags.LAPIS_ORES,
            ItemTags.REDSTONE_ORES,
            ItemTags.WOODEN_SLABS,
            ItemTags.WOODEN_STAIRS,
            ItemTags.WOODEN_FENCES,
            ItemTags.WOODEN_DOORS,
            ItemTags.WOODEN_TRAPDOORS,
            ItemTags.WOODEN_BUTTONS,
            ItemTags.WOODEN_PRESSURE_PLATES,
            ItemTags.BEDS,
            ItemTags.CANDLES,
            Tags.Items.COBBLESTONES,
            Tags.Items.STONES,
            Tags.Items.SANDS,
            Tags.Items.GRAVELS,
            Tags.Items.GLASS_BLOCKS,
            Tags.Items.GLASS_PANES,
            Tags.Items.CONCRETES,
            Tags.Items.CONCRETE_POWDERS,
            Tags.Items.GLAZED_TERRACOTTAS,
            Tags.Items.NETHERRACKS,
            Tags.Items.END_STONES,
            Tags.Items.BRICKS,
            Tags.Items.SANDSTONE_BLOCKS,
            Tags.Items.OBSIDIANS,
            Tags.Items.STRIPPED_LOGS,
            Tags.Items.SHULKER_BOXES,
            Tags.Items.STORAGE_BLOCKS_COPPER);

    private ExchangeFamilies() {
    }

    /** Seed plus every block-item sharing a curated family tag with it. */
    public static Set<Item> similarTo(Item seed) {
        LinkedHashSet<Item> out = new LinkedHashSet<>();
        if (!(seed instanceof BlockItem)) {
            return out;
        }
        out.add(seed);
        Holder.Reference<Item> holder = seed.builtInRegistryHolder();
        for (TagKey<Item> tag : FAMILIES) {
            if (!holder.is(tag)) {
                continue;
            }
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> {
                for (Holder<Item> entry : set) {
                    Item item = entry.value();
                    if (item instanceof BlockItem) {
                        out.add(item);
                    }
                }
            });
        }
        return out;
    }

    public static List<com.wrights.blueprint.ShapeMaterials.Entry> similarEntries(Item seed) {
        List<com.wrights.blueprint.ShapeMaterials.Entry> entries = new ArrayList<>();
        for (Item item : similarTo(seed)) {
            entries.add(new com.wrights.blueprint.ShapeMaterials.Entry(item, 1));
        }
        return entries;
    }
}
