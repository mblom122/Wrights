package com.autobots.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleContainer;

import com.autobots.block.HangarBlockEntity;
import com.autobots.block.ModBlocks;
import com.autobots.drone.Cradles;
import com.autobots.item.CradleItem;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

public class HangarMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    public HangarMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public HangarMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.HANGAR.get(), id);
        this.pos = pos.immutable();
        Level level = inventory.player.level();
        this.access = ContainerLevelAccess.create(level, this.pos);
        net.minecraft.world.Container container = level.getBlockEntity(this.pos) instanceof HangarBlockEntity hangar
                ? hangar.items()
                : new SimpleContainer(1);
        addSlot(new Slot(container, 0, 8, 28) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CradleItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addWornSlot(inventory);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 216));
        }
    }

    private void addWornSlot(Inventory inventory) {
        Player player = inventory.player;
        ICuriosItemHandler curios = CuriosApi.getCuriosInventory(player).orElse(null);
        ICurioStacksHandler stacks = curios == null ? null : curios.getStacksHandler(Cradles.SLOT).orElse(null);
        if (stacks != null) {
            addSlot(new CurioSlot(player, stacks.getStacks(), 0, Cradles.SLOT, 46, 28, stacks.getRenders(), false));
            return;
        }
        addSlot(new Slot(new SimpleContainer(1), 0, 46, 28) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.HANGAR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 2, false) && !moveItemStackTo(stack, 2, 38, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index == 1) {
            if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 2, 38, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 2, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
