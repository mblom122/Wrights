package com.wrights.energy;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.item.ModDataComponents;
import com.wrights.item.ModItems;
import com.wrights.job.BuildJob;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Forge Energy for the wrightport that launched a wright. The flying wright does not store any. */
public final class PortEnergy {
    private PortEnergy() {
    }

    /** ITEM capability for wrightport / personal wrightport stacks. Capacity follows server config. */
    public static IEnergyStorage itemStorage(ItemStack stack) {
        int cap = WrightsConfig.portEnergyCapacity();
        int rate = WrightsConfig.portEnergyTransfer();
        return new ComponentEnergyStorage(stack, ModDataComponents.PORT_ENERGY.get(), cap, rate, rate);
    }

    public static int itemStored(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer stored = stack.get(ModDataComponents.PORT_ENERGY.get());
        return stored == null ? 0 : Mth.clamp(stored, 0, WrightsConfig.portEnergyCapacity());
    }

    public static void writeItem(ItemStack stack, int energy) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(ModDataComponents.PORT_ENERGY.get(), Mth.clamp(energy, 0, WrightsConfig.portEnergyCapacity()));
    }

    /** Always {@code stored / capacity}, including {@code 0 / max} for new and JEI stacks. */
    public static void appendItemTooltip(ItemStack stack, List<Component> tooltip) {
        if (tooltip == null) {
            return;
        }
        int stored = itemStored(stack);
        int capacity = WrightsConfig.portEnergyCapacity();
        tooltip.add(fePair(stored, capacity));
    }

    /** Amount + {@code FE} in white for tooltips. */
    public static Component fe(int amount) {
        return Component.literal(format(amount) + " FE").withStyle(ChatFormatting.WHITE);
    }

    /** {@code stored / capacity FE} in white for tooltips. */
    public static Component fePair(int stored, int capacity) {
        return Component.literal(format(stored) + " / " + format(capacity) + " FE").withStyle(ChatFormatting.WHITE);
    }

    /** Lit bars on a wrightport item's charge gauge. */
    public static int itemBars(ItemStack stack) {
        return EnergyBars.lit(itemStored(stack), WrightsConfig.portEnergyCapacity());
    }

    public static boolean itemBarVisible(ItemStack stack) {
        return WrightsConfig.portEnergyCapacity() > 0;
    }

    public static int itemBarWidth(ItemStack stack) {
        int capacity = WrightsConfig.portEnergyCapacity();
        int stored = itemStored(stack);
        if (capacity <= 0 || stored <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(13, stored * 13 / capacity));
    }

    public static int itemBarColor(ItemStack stack) {
        int capacity = WrightsConfig.portEnergyCapacity();
        int stored = itemStored(stack);
        if (capacity <= 0 || stored <= 0) {
            return 0xFF8C1A;
        }
        if (stored * 4 < capacity) {
            return 0xFFC45A2A;
        }
        if (stored * 2 < capacity) {
            return 0xFFE0B060;
        }
        return 0xFF3CFE9A;
    }

    /**
     * Move Forge Energy from other carried batteries into every personal wrightport the player
     * holds or wears. Used while standing on a wireless item charger so the port is not starved
     * when another inventory item keeps winning the charger's single slot each tick.
     */
    public static void fillPersonalPortsFromCarried(Player player) {
        if (player == null) {
            return;
        }
        int rate = WrightsConfig.portEnergyTransfer();
        if (rate <= 0) {
            return;
        }
        fillPersonalPorts(player.getInventory(), player, rate);
        fillPersonalPortsCurios(player, rate);
    }

    private static void fillPersonalPorts(Inventory inventory, Player player, int rate) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.PERSONAL_WRIGHTPORT.get())) {
                moveIntoPort(player, stack, rate);
            }
        }
    }

    private static void fillPersonalPortsCurios(Player player, int rate) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return;
        }
        for (var stacks : handler.getCurios().values()) {
            IDynamicStackHandler inv = stacks.getStacks();
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (stack.is(ModItems.PERSONAL_WRIGHTPORT.get())) {
                    moveIntoPort(player, stack, rate);
                }
            }
        }
    }

    private static void moveIntoPort(Player player, ItemStack destination, int rate) {
        IEnergyStorage dest = destination.getCapability(Capabilities.EnergyStorage.ITEM);
        if (dest == null || !dest.canReceive()) {
            return;
        }
        int room = dest.receiveEnergy(rate, true);
        if (room <= 0) {
            return;
        }
        int taken = takeFromOthers(player, destination, room);
        if (taken > 0) {
            dest.receiveEnergy(taken, false);
        }
    }

    private static int takeFromOthers(Player player, ItemStack skip, int need) {
        int left = need;
        left = takeInventoryExcept(player.getInventory(), skip, left);
        if (left > 0) {
            left = takeCuriosExcept(player, skip, left);
        }
        return need - left;
    }

    private static int takeInventoryExcept(Inventory inventory, ItemStack skip, int need) {
        int left = need;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == skip || stack.is(ModItems.PERSONAL_WRIGHTPORT.get())) {
                continue;
            }
            left -= pull(stack, left, false);
        }
        return left;
    }

    private static int takeCuriosExcept(Player player, ItemStack skip, int need) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return need;
        }
        int left = need;
        for (var stacks : handler.getCurios().values()) {
            IDynamicStackHandler inv = stacks.getStacks();
            for (int slot = 0; slot < inv.getSlots() && left > 0; slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (stack == skip || stack.is(ModItems.PERSONAL_WRIGHTPORT.get())) {
                    continue;
                }
                left -= pull(stack, left, false);
            }
        }
        return left;
    }

    private static String format(int value) {
        return java.text.NumberFormat.getIntegerInstance().format(Math.max(0, value));
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

        public void loadAmount(int amount) {
            refresh();
            this.energy = Mth.clamp(amount, 0, this.capacity);
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

    /** The configured {@code base} cost of one block for this wright's rotor and Power Core; Basic without a hand. */
    public static int costFor(BuildJob.Hand hand, int base) {
        return hand == null ? base : hand.energyCost(base);
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

    /** True when some wright on the job can pay for one more block of the given base cost. */
    public static boolean canFund(ServerLevel level, ServerPlayer player, BuildJob job, int base) {
        if (base <= 0) {
            return true;
        }
        if (job == null || job.hands().isEmpty()) {
            return player != null && available(player) >= base;
        }
        for (BuildJob.Hand hand : job.hands()) {
            int cost = hand.energyCost(base);
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
