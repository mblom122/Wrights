package com.wrights.job;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.network.BlueprintPayloads;
import com.wrights.drone.WrightHomes;
import com.wrights.storage.DigitalNetworks;
import com.wrights.util.BlockFilters;

/**
 * Where a wright gets the items it places and where leftovers go: the player inventory for a personal
 * port, a placed port's own chest, the bound supply container, and the digital store.
 */
public final class JobSupply {
    private JobSupply() {
    }

    static boolean hasItem(ServerPlayer player, ServerLevel level, BuildJob job, Item item) {
        return hasAmount(player, level, job, null, item, 1);
    }

    /**
     * Personal wrights may use the player inventory. A placed port never does: only its own chest, inside its
     * boundary, and the shared digital store.
     */
    static boolean hasItem(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Item item) {
        return hasAmount(player, level, job, hand, item, 1);
    }

    /** Enough stock for {@code amount}, or a craft request for the shortfall when the network can make it. */
    static boolean hasAmount(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Item item, int amount) {
        if (item == null || amount <= 0 || player.getAbilities().instabuild) {
            return true;
        }
        com.wrights.storage.DigitalLink link = com.wrights.storage.ItemSource.linkOf(player);
        long have;
        if (hand != null && hand.homePort != null) {
            have = com.wrights.storage.ItemSource.jobSupplyCount(player, level, portChest(level, hand.homePort), link, item);
        } else {
            Container container = supply(level, player, job);
            have = com.wrights.blueprint.ShapeSupply.available(player, item, com.wrights.blueprint.ShapeSupply.parse(job.supplyOrder()), container);
        }
        if (have >= amount) {
            return true;
        }
        long missing = amount - have;
        if (com.wrights.storage.DigitalNetworks.craftable(level, link, item)) {
            com.wrights.storage.DigitalNetworks.requestCraft(player, level, link, item, missing);
        }
        return false;
    }

    static void consumeItem(ServerPlayer player, ServerLevel level, BuildJob job, Item item) {
        consumeItem(player, level, job, null, item);
    }

    static void consumeItem(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Item item) {
        com.wrights.storage.DigitalLink link = com.wrights.storage.ItemSource.linkOf(player);
        if (hand != null && hand.homePort != null) {
            com.wrights.storage.ItemSource.consumeForPort(player, level, item, portChest(level, hand.homePort), link);
            return;
        }
        Container container = supply(level, player, job);
        com.wrights.blueprint.ShapeSupply.takeOne(player, item, com.wrights.blueprint.ShapeSupply.parse(job.supplyOrder()), container);
    }

    /** The chest bound to this port, when it is still inside that port's chunk square. */
    private static Container portChest(ServerLevel level, BlockPos portPos) {
        if (portPos == null || !level.isLoaded(portPos)) {
            return null;
        }
        if (!(level.getBlockEntity(portPos) instanceof com.wrights.block.WrightportBlockEntity port)) {
            return null;
        }
        BlockPos chestPos = port.supplyChest();
        if (chestPos == null || !level.isLoaded(chestPos)) {
            return null;
        }
        if (!com.wrights.drone.WrightHomes.portCovers(level.dimension(), portPos, level.dimension(), chestPos)) {
            return null;
        }
        return containerAt(level, chestPos);
    }

    private static boolean holds(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean take(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }

    static void giveBack(ServerLevel level, ServerPlayer player, BuildJob job, Item item) {
        ItemStack stack = new ItemStack(item);
        BlockPos supplyPos = job.supplyPos();
        if (supplyPos != null) {
            Container container = supply(level, player, job);
            if (container != null) {
                stack = insert(container, stack);
                container.setChanged();
            }
            if (!stack.isEmpty()) {
                dropStack(level, container == null ? player.blockPosition() : supplyPos, stack);
            }
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static Container supply(ServerLevel level, ServerPlayer player, BuildJob job) {
        BlockPos pos = job.supplyPos();
        ServerLevel stored = supplyLevel(level, player, job);
        if (pos == null || !stored.mayInteract(player, pos)) {
            return null;
        }
        return containerAt(stored, pos);
    }

    private static ServerLevel supplyLevel(ServerLevel fallback, ServerPlayer player, BuildJob job) {
        String raw = job.supplyDimension();
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(raw);
        if (id == null) {
            return fallback;
        }
        ServerLevel stored = player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        return stored == null ? fallback : stored;
    }

    static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, false);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container container) {
            return container;
        }
        return null;
    }

    private static ItemStack insert(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
                container.setItem(slot, existing);
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private static void dropStack(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack));
    }

    static int dropKind(ServerPlayer player, BuildJob job) {
        if (job.supplyPos() == null) {
            return BlueprintPayloads.UndoPrompt.AT_PLAYER;
        }
        if (!(player.level() instanceof ServerLevel level) || supply(level, player, job) == null) {
            return BlueprintPayloads.UndoPrompt.CHEST_GONE;
        }
        return BlueprintPayloads.UndoPrompt.AT_CHEST;
    }

    static int overflow(ServerPlayer player, BuildJob job) {
        java.util.List<Item> paid = new java.util.ArrayList<>();
        for (BuildJob.Refund refund : job.refunds()) {
            if (!refund.paid()) {
                continue;
            }
            Item item = BlockFilters.payment(refund.state());
            if (item != null) {
                paid.add(item);
            }
            for (int extraId : refund.extras()) {
                Item extra = Item.byId(extraId);
                if (extra != null && extra != Items.AIR) {
                    paid.add(extra);
                }
            }
        }
        if (paid.isEmpty()) {
            return 0;
        }
        ItemStack[] slots = slotsFor(player, job);
        if (slots == null) {
            return paid.size();
        }
        int extra = 0;
        for (Item item : paid) {
            if (!fits(slots, item)) {
                extra++;
            }
        }
        return extra;
    }

    private static ItemStack[] slotsFor(ServerPlayer player, BuildJob job) {
        if (job.supplyPos() == null) {
            Inventory inventory = player.getInventory();
            ItemStack[] slots = new ItemStack[36];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = inventory.getItem(slot).copy();
            }
            return slots;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Container container = supply(level, player, job);
        if (container == null) {
            return null;
        }
        ItemStack[] slots = new ItemStack[container.getContainerSize()];
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = container.getItem(slot).copy();
        }
        return slots;
    }

    private static boolean fits(ItemStack[] slots, Item item) {
        ItemStack stack = new ItemStack(item);
        for (int slot = 0; slot < slots.length; slot++) {
            ItemStack existing = slots[slot];
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = stack;
                return true;
            }
        }
        return false;
    }
}
