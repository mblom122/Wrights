package com.wrights.compat.ae2;

import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Extra costs for an AE2 cable bus: center cable, attached parts, and facade skins.
 * Facade NBT stores the camouflage {@link BlockState}; one of that block is spent per side
 * (same idea as Framed Blocks camo — the facade item is all {@code ae2:facade}).
 */
public final class Ae2CableExtras {
    private static final String[] PART_SLOTS = {
            "cable", "down", "up", "north", "south", "west", "east"
    };
    private static final String[] FACADE_SLOTS = {
            "facadeDown", "facadeUp", "facadeNorth", "facadeSouth", "facadeWest", "facadeEast"
    };

    private Ae2CableExtras() {
    }

    public static void collect(BlockState state, CompoundTag nbt, Consumer<Item> out) {
        if (state == null || nbt == null || nbt.isEmpty() || out == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !"ae2".equals(id.getNamespace()) || !"cable_bus".equals(id.getPath())) {
            return;
        }
        for (String slot : PART_SLOTS) {
            addPart(nbt, slot, out);
        }
        for (String slot : FACADE_SLOTS) {
            addFacade(nbt, slot, out);
        }
    }

    private static void addPart(CompoundTag nbt, String key, Consumer<Item> out) {
        if (!nbt.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag part = nbt.getCompound(key);
        if (!part.contains("id", Tag.TAG_STRING)) {
            return;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(part.getString("id"));
        if (itemId == null) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item != null && item != Items.AIR) {
            out.accept(item);
        }
    }

    private static void addFacade(CompoundTag nbt, String key, Consumer<Item> out) {
        if (!nbt.contains(key)) {
            return;
        }
        Tag raw = nbt.get(key);
        if (!(raw instanceof CompoundTag facadeTag) || facadeTag.isEmpty()) {
            return;
        }
        BlockState skin = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), facadeTag);
        if (skin == null || skin.isAir()) {
            return;
        }
        Item item = skin.getBlock().asItem();
        if (item != null && item != Items.AIR) {
            out.accept(item);
        }
    }
}
