package com.wrights.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.ModBlocks;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.hangar.HangarLayout;
import com.wrights.network.PortEnergySync;

public class WrightportMenu extends AbstractContainerMenu {
    /** Hidden swarm-data slot; player inventory starts after this. */
    public static final int PLAYER_INV_START = 1;
    public static final int PLAYER_INV_END = PLAYER_INV_START + 27;
    public static final int HOTBAR_END = PLAYER_INV_END + 9;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    private int sentStored = Integer.MIN_VALUE;
    private int sentCapacity = Integer.MIN_VALUE;

    public WrightportMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public WrightportMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.WRIGHTPORT.get(), id);
        this.pos = pos.immutable();
        this.player = inventory.player;
        Level level = inventory.player.level();
        this.access = ContainerLevelAccess.create(level, this.pos);
        net.minecraft.world.Container container = level.getBlockEntity(this.pos) instanceof WrightportBlockEntity port
                ? port.items()
                : new SimpleContainer(1);
        addSlot(new Slot(container, 0, 0, 0) {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        int left = HangarLayout.invSlotLeft();
        int top = HangarLayout.invSlotTop();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + (row + 1) * 9, left + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, left + col * 18, top + 58));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        access.execute((level, at) -> {
            if (!(level.getBlockEntity(at) instanceof WrightportBlockEntity port)) {
                return;
            }
            int stored = port.energyStored();
            int capacity = port.energyCapacity();
            if (stored == sentStored && capacity == sentCapacity) {
                return;
            }
            sentStored = stored;
            sentCapacity = capacity;
            PacketDistributor.sendToPlayer(server, new PortEnergySync(at, stored, capacity));
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.WRIGHTPORT.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || index < PLAYER_INV_START) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        copy = stack.copy();
        if (index < PLAYER_INV_END) {
            if (!moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_END) {
            if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
