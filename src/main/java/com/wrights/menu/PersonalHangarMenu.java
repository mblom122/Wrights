package com.wrights.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.wrights.drone.HangarOps;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightportPlaces;
import com.wrights.hangar.HangarLayout;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.item.WrightItem;

/** Hangar for one carried personal wrightport — same layout as the placed wrightport screen. */
public class PersonalHangarMenu extends AbstractContainerMenu {
    public static final int PLAYER_INV_START = 1;
    public static final int PLAYER_INV_END = PLAYER_INV_START + 27;
    public static final int HOTBAR_END = PLAYER_INV_END + 9;

    private final int kind;
    private final int index;
    private final Player player;
    private final SimpleContainer ghost = new SimpleContainer(1);

    public PersonalHangarMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readByte(), buf.readVarInt());
    }

    public PersonalHangarMenu(int id, Inventory inventory, int kind, int index) {
        super(ModMenus.PERSONAL_HANGAR.get(), id);
        this.kind = kind;
        this.index = index;
        this.player = inventory.player;
        syncGhost();
        addSlot(new Slot(ghost, 0, 0, 0) {
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

    public int kind() {
        return kind;
    }

    public int index() {
        return index;
    }

    public ItemStack portStack() {
        return WrightportPlaces.stack(player, kind, index);
    }

    private void syncGhost() {
        ItemStack live = portStack();
        if (live.getItem() instanceof PersonalWrightportItem) {
            ItemStack copy = live.copy();
            if (!ItemStack.isSameItemSameComponents(ghost.getItem(0), copy)) {
                ghost.setItem(0, copy);
            }
        } else {
            ghost.setItem(0, ItemStack.EMPTY);
        }
    }

    @Override
    public void broadcastChanges() {
        syncGhost();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.player == player && portStack().getItem() instanceof PersonalWrightportItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || index < PLAYER_INV_START || index >= HOTBAR_END) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (stack.getItem() instanceof WrightItem) {
            if (player instanceof ServerPlayer server) {
                if (!HangarOps.quickDepositPort(server, kind, this.index, stack)) {
                    return ItemStack.EMPTY;
                }
            } else if (Swarm.from(ghost.getItem(0)).units().size() < Swarm.capacity()) {
                stack.shrink(1);
            } else {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            syncGhost();
            return copy;
        }
        if (index < PLAYER_INV_END) {
            if (!moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false)) {
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
