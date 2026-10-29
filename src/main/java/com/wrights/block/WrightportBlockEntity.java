package com.wrights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.drone.Swarm;
import com.wrights.item.ModItems;
import com.wrights.menu.WrightportMenu;

public class WrightportBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer items = new SimpleContainer(1);

    public WrightportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WRIGHTPORT.get(), pos, state);
        items.addListener(ignored -> setChanged());
    }

    public Swarm swarm() {
        return Swarm.from(items.getItem(0));
    }

    public void setSwarm(Swarm swarm) {
        ItemStack stack = items.getItem(0);
        if (stack.isEmpty()) {
            stack = new ItemStack(ModItems.CRADLE.get());
            items.setItem(0, stack);
        }
        Swarm.write(stack, swarm);
        items.setChanged();
    }

    public SimpleContainer items() {
        return items;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.wrights.wrightport");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (items.getItem(0).isEmpty()) {
            setSwarm(Swarm.EMPTY);
        }
        return new WrightportMenu(id, inventory, this.getBlockPos());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Wrights", items.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.fromTag(tag.getList("Wrights", Tag.TAG_COMPOUND), registries);
    }
}
