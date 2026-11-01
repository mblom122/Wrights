package com.wrights.compat.framedblocks;

import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import xfacthd.framedblocks.api.camo.CamoContainer;
import xfacthd.framedblocks.api.camo.CamoContainerHelper;
import xfacthd.framedblocks.api.util.Utils;

/**
 * Extra items a framed block with copied NBT spends on paste: camo skin(s), glowstone,
 * phantom paste, and reinforcement. Mirrors Framed Blocks' Create schematic requirements.
 */
public final class FramedBlockExtras {
    private FramedBlockExtras() {
    }

    public static void collect(BlockState state, CompoundTag nbt, Consumer<Item> out) {
        if (state == null || nbt == null || nbt.isEmpty() || out == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !"framedblocks".equals(id.getNamespace())) {
            return;
        }
        addCamo(nbt, "camo", out);
        addCamo(nbt, "camo_two", out);
        if (nbt.getBoolean("glowing")) {
            out.accept(Items.GLOWSTONE_DUST);
        }
        if (nbt.getBoolean("intangible")) {
            Item paste = Utils.PHANTOM_PASTE.value();
            if (paste != null && paste != Items.AIR) {
                out.accept(paste);
            }
        }
        if (nbt.getBoolean("reinforced")) {
            Item reinforcement = Utils.FRAMED_REINFORCEMENT.value();
            if (reinforcement != null && reinforcement != Items.AIR) {
                out.accept(reinforcement);
            }
        }
    }

    private static void addCamo(CompoundTag nbt, String key, Consumer<Item> out) {
        if (!nbt.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        CamoContainer<?, ?> camo = CamoContainerHelper.readFromDisk(nbt.getCompound(key));
        if (camo.isEmpty() || !camo.canTriviallyConvertToItemStack()) {
            return;
        }
        ItemStack drop = CamoContainerHelper.dropCamo(camo);
        if (!drop.isEmpty()) {
            out.accept(drop.getItem());
        }
    }
}
