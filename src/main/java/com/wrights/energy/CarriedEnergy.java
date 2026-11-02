package com.wrights.energy;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Forge Energy sitting in the player's inventory and Curios slots. The personal wrightport spends this. */
public final class CarriedEnergy {
    private CarriedEnergy() {
    }

    public record Amount(long stored, long capacity) {
        public static final Amount EMPTY = new Amount(0L, 0L);
    }

    public static Amount of(Player player) {
        if (player == null) {
            return Amount.EMPTY;
        }
        long stored = 0L;
        long capacity = 0L;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            long[] added = read(inventory.getItem(slot));
            stored += added[0];
            capacity += added[1];
        }
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler != null) {
            for (var stacks : handler.getCurios().values()) {
                IDynamicStackHandler slots = stacks.getStacks();
                for (int slot = 0; slot < slots.getSlots(); slot++) {
                    long[] added = read(slots.getStackInSlot(slot));
                    stored += added[0];
                    capacity += added[1];
                }
            }
        }
        return new Amount(stored, capacity);
    }

    private static long[] read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new long[] {0L, 0L};
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null) {
            return new long[] {0L, 0L};
        }
        long stored = Math.max(0, energy.getEnergyStored());
        long capacity = Math.max(stored, energy.getMaxEnergyStored());
        if (stored <= 0L && capacity <= 0L) {
            return new long[] {0L, 0L};
        }
        return new long[] {stored, capacity};
    }
}
