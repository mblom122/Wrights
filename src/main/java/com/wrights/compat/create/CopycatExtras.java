package com.wrights.compat.create;

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
 * Create copycats and Copycats+ store the consumed skin as {@code Item} + {@code Material}.
 * Parsed from NBT only — Create does not need to be on the compile classpath.
 */
public final class CopycatExtras {
    private CopycatExtras() {
    }

    public static void collect(BlockState state, CompoundTag nbt, Consumer<Item> out) {
        if (state == null || nbt == null || nbt.isEmpty() || out == null) {
            return;
        }
        if (!nbt.contains("Material", Tag.TAG_COMPOUND) && !nbt.contains("material", Tag.TAG_COMPOUND)) {
            return;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null || !isCopycatMod(blockId.getNamespace())) {
            return;
        }
        Item fromStack = itemFromConsumed(nbt);
        if (fromStack != null) {
            out.accept(fromStack);
            return;
        }
        CompoundTag materialTag = nbt.contains("Material", Tag.TAG_COMPOUND)
                ? nbt.getCompound("Material")
                : nbt.getCompound("material");
        BlockState material = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), materialTag);
        if (material == null || material.isAir() || isCopycatBase(material)) {
            return;
        }
        Item item = material.getBlock().asItem();
        if (item != null && item != Items.AIR) {
            out.accept(item);
        }
    }

    private static boolean isCopycatMod(String namespace) {
        return "create".equals(namespace)
                || "copycats".equals(namespace)
                || "copycatsplus".equals(namespace);
    }

    private static boolean isCopycatBase(BlockState material) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(material.getBlock());
        if (id == null) {
            return true;
        }
        String path = id.getPath();
        return path.equals("copycat_base") || path.contains("copycat_base");
    }

    private static Item itemFromConsumed(CompoundTag nbt) {
        if (!nbt.contains("Item", Tag.TAG_COMPOUND) && !nbt.contains("item", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag itemTag = nbt.contains("Item", Tag.TAG_COMPOUND) ? nbt.getCompound("Item") : nbt.getCompound("item");
        if (itemTag.isEmpty() || !itemTag.contains("id", Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemTag.getString("id"));
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null || item == Items.AIR ? null : item;
    }
}
