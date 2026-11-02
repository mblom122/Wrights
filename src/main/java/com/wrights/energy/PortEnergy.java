package com.wrights.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.job.BuildJob;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Forge Energy for the wrightport that launched a wright. The flying wright does not store any. */
public final class PortEnergy {
    private PortEnergy() {
    }

    /** Placed-port buffer. A new port starts empty and has to be charged. */
    public static final class Buffer extends EnergyStorage {
        public Buffer() {
            super(1_000_000, 10_000, 10_000, 0);
            refresh();
            this.energy = 0;
        }

        public void load(CompoundTag tag) {
            refresh();
            if (tag != null && tag.contains("Energy")) {
                this.energy = Mth.clamp(tag.getInt("Energy"), 0, this.capacity);
            } else {
                this.energy = 0;
            }
        }

        public void save(CompoundTag tag) {
            refresh();
            tag.putInt("Energy", this.energy);
        }

        public boolean canPay(int cost) {
            refresh();
            return cost <= 0 || this.energy >= cost;
        }

        public boolean consume(int cost) {
            refresh();
            if (cost <= 0) {
                return true;
            }
            if (this.energy < cost) {
                return false;
            }
            this.energy -= cost;
            return true;
        }

        public void refund(int amount) {
            refresh();
            if (amount <= 0) {
                return;
            }
            this.energy = Math.min(this.capacity, this.energy + amount);
        }

        public int stored() {
            refresh();
            return this.energy;
        }

        public int limit() {
            refresh();
            return this.capacity;
        }

        private void refresh() {
            int cap = WrightsConfig.portEnergyCapacity();
            int rate = WrightsConfig.portEnergyTransfer();
            this.capacity = cap;
            this.maxReceive = rate;
            this.maxExtract = rate;
            if (this.energy > cap) {
                this.energy = cap;
            }
        }
    }

    public static boolean tryPay(ServerLevel level, ServerPlayer player, BuildJob.Hand hand, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (hand != null && hand.homePort != null) {
            if (level == null || !level.isLoaded(hand.homePort)) {
                return false;
            }
            BlockEntity entity = level.getBlockEntity(hand.homePort);
            if (entity instanceof WrightportBlockEntity port) {
                return port.consumeEnergy(cost);
            }
            return false;
        }
        return player != null && takeFromPlayer(player, cost);
    }

    public static void refund(ServerLevel level, ServerPlayer player, BuildJob.Hand hand, int cost) {
        if (cost <= 0) {
            return;
        }
        if (hand != null && hand.homePort != null && level != null && level.isLoaded(hand.homePort)) {
            BlockEntity entity = level.getBlockEntity(hand.homePort);
            if (entity instanceof WrightportBlockEntity port) {
                port.refundEnergy(cost);
                return;
            }
        }
        if (player != null) {
            giveToPlayer(player, cost);
        }
    }

    /** True when the job can pay for one more block of the given cost. */
    public static boolean canFund(ServerLevel level, ServerPlayer player, BuildJob job, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (job == null || job.hands().isEmpty()) {
            return player != null && available(player) >= cost;
        }
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.homePort != null && level != null && level.isLoaded(hand.homePort)) {
                BlockEntity entity = level.getBlockEntity(hand.homePort);
                if (entity instanceof WrightportBlockEntity port && port.energyStored() >= cost) {
                    return true;
                }
                continue;
            }
            if (player != null && available(player) >= cost) {
                return true;
            }
        }
        return false;
    }

    private static boolean takeFromPlayer(ServerPlayer player, int cost) {
        int rate = WrightsConfig.personalEnergyRate();
        if (cost > rate || available(player) < cost) {
            return false;
        }
        int left = cost;
        left = pullInventory(player.getInventory(), left, false);
        if (left > 0) {
            pullCurios(player, left, false);
        }
        return true;
    }

    private static void giveToPlayer(ServerPlayer player, int amount) {
        int left = pushInventory(player.getInventory(), amount);
        if (left > 0) {
            pushCurios(player, left);
        }
    }

    private static int available(ServerPlayer player) {
        int rate = WrightsConfig.personalEnergyRate();
        int remaining = pullInventory(player.getInventory(), rate, true);
        remaining = pullCurios(player, remaining, true);
        return rate - remaining;
    }

    private static int pullInventory(Inventory inventory, int need, boolean simulate) {
        int left = need;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            left -= pull(inventory.getItem(slot), left, simulate);
        }
        return left;
    }

    private static int pushInventory(Inventory inventory, int amount) {
        int left = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            left -= push(inventory.getItem(slot), left);
        }
        return left;
    }

    private static int pullCurios(ServerPlayer player, int need, boolean simulate) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return need;
        }
        int left = need;
        for (var stacks : handler.getCurios().values()) {
            IDynamicStackHandler inv = stacks.getStacks();
            for (int slot = 0; slot < inv.getSlots() && left > 0; slot++) {
                left -= pull(inv.getStackInSlot(slot), left, simulate);
            }
        }
        return left;
    }

    private static int pushCurios(ServerPlayer player, int amount) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return amount;
        }
        int left = amount;
        for (var stacks : handler.getCurios().values()) {
            IDynamicStackHandler inv = stacks.getStacks();
            for (int slot = 0; slot < inv.getSlots() && left > 0; slot++) {
                left -= push(inv.getStackInSlot(slot), left);
            }
        }
        return left;
    }

    private static int pull(ItemStack stack, int need, boolean simulate) {
        if (stack == null || stack.isEmpty() || need <= 0) {
            return 0;
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null) {
            return 0;
        }
        return energy.extractEnergy(need, simulate);
    }

    private static int push(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return 0;
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null) {
            return 0;
        }
        return energy.receiveEnergy(amount, false);
    }
}
