package com.wrights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;
import com.wrights.item.ModItems;
import com.wrights.menu.WrightportMenu;

public class WrightportBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer items = new SimpleContainer(1);
    /** Client-only lid. 0 closed, 1 open. Not saved. */
    private float hatch;
    private float hatchPrev;
    private int hatchHold;

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
            stack = new ItemStack(ModItems.PERSONAL_WRIGHTPORT.get());
            items.setItem(0, stack);
        }
        Swarm.write(stack, swarm);
        items.setChanged();
    }

    public SimpleContainer items() {
        return items;
    }

    /** Opens while a wright is in the column above the port, then stays open a moment after it leaves. */
    public void animateHatch() {
        this.hatchPrev = this.hatch;
        Level level = this.level;
        boolean traffic = false;
        if (level != null) {
            BlockPos pos = this.worldPosition;
            AABB box = new AABB(pos.getX() - 2.2D, pos.getY() - 0.5D, pos.getZ() - 2.2D,
                    pos.getX() + 3.2D, pos.getY() + 7.5D, pos.getZ() + 3.2D);
            traffic = !level.getEntitiesOfClass(DroneEntity.class, box, Entity::isAlive).isEmpty();
        }
        if (traffic) {
            this.hatchHold = 16;
        } else if (this.hatchHold > 0) {
            this.hatchHold--;
        }
        float target = traffic || this.hatchHold > 0 ? 1.0F : 0.0F;
        if (this.hatch < target) {
            this.hatch = Math.min(target, this.hatch + 0.14F);
        } else if (this.hatch > target) {
            this.hatch = Math.max(target, this.hatch - 0.07F);
        }
    }

    public float hatchOpen(float partialTick) {
        return Mth.lerp(partialTick, this.hatchPrev, this.hatch);
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
