package com.wrights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.menu.HangarMenu;

public class HangarBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer items = new SimpleContainer(1);

    public HangarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HANGAR.get(), pos, state);
        items.addListener(ignored -> setChanged());
    }

    public Container items() {
        return items;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.wrights.hangar");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HangarMenu(id, inventory, this.getBlockPos());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Wrightport", items.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String key = tag.contains("Wrightport") ? "Wrightport" : "Cradle";
        items.fromTag(tag.getList(key, Tag.TAG_COMPOUND), registries);
    }
}
