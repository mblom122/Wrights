package com.wrights.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.blueprint.BlueprintSessions;
import com.wrights.schematic.SchematicLibrary;

/**
 * Shared resolve order for hangar ops and build jobs:
 * player inventory → bound chest → digital extract → autocraft request.
 */
public final class ItemSource {
    public enum Result {
        OK,
        MISSING,
        CRAFTING
    }

    private ItemSource() {
    }

    public static DigitalLink linkOf(ServerPlayer player) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        DigitalLink live = session.digitalLink();
        if (live != null && live.bound()) {
            return live;
        }
        return SchematicLibrary.get(player.getServer()).digital(player.getUUID());
    }

    public static GlobalPos chestOf(ServerPlayer player) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.supplyPos() != null && session.supplyDimension() != null) {
            return GlobalPos.of(session.supplyDimension(), session.supplyPos());
        }
        return SchematicLibrary.get(player.getServer()).supply(player.getUUID());
    }

    public static int playerCount(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    public static int chestCount(ServerPlayer player, Item item) {
        return chestCount(player, chestOf(player), item);
    }

    public static int chestCount(ServerPlayer player, GlobalPos chest, Item item) {
        Container container = container(player, chest);
        if (container == null) {
            return 0;
        }
        int stored = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                stored += stack.getCount();
            }
        }
        return stored;
    }

    public static long digitalCount(ServerPlayer player, Item item) {
        DigitalLink link = linkOf(player);
        ServerLevel level = DigitalNetworks.levelOf(player, link == null ? null : link.pos());
        if (level == null) {
            return 0L;
        }
        return DigitalNetworks.count(level, link, item);
    }

    /** Non-player stock: bound chest plus digital network. */
    public static long supplyCount(ServerPlayer player, Item item) {
        return chestCount(player, item) + digitalCount(player, item);
    }

    public static long available(ServerPlayer player, Item item) {
        if (player.getAbilities().instabuild) {
            return Long.MAX_VALUE / 4L;
        }
        return playerCount(player, item) + supplyCount(player, item);
    }

    public static boolean canAfford(ServerPlayer player, Item item, int count) {
        return count <= 0 || available(player, item) >= count;
    }

    public static boolean craftable(ServerPlayer player, Item item) {
        DigitalLink link = linkOf(player);
        ServerLevel level = DigitalNetworks.levelOf(player, link == null ? null : link.pos());
        return level != null && DigitalNetworks.craftable(level, link, item);
    }

    /**
     * Takes {@code count} items. When stock is short but craftable, requests a craft and
     * returns {@link Result#CRAFTING} without taking anything.
     */
    public static Result take(ServerPlayer player, Item item, int count) {
        return take(player, item, count, true);
    }

    public static Result take(ServerPlayer player, Item item, int count, boolean allowCraft) {
        if (count <= 0) {
            return Result.OK;
        }
        if (player.getAbilities().instabuild) {
            int have = playerCount(player, item);
            if (have > 0) {
                shrinkPlayer(player, item, Math.min(have, count));
            }
            return Result.OK;
        }
        long have = available(player, item);
        if (have < count) {
            if (allowCraft && craftable(player, item)) {
                DigitalLink link = linkOf(player);
                ServerLevel level = DigitalNetworks.levelOf(player, link.pos());
                if (level != null && DigitalNetworks.requestCraft(player, level, link, item, count - have)) {
                    return Result.CRAFTING;
                }
            }
            return Result.MISSING;
        }
        int left = count;
        left -= shrinkPlayer(player, item, left);
        if (left > 0) {
            left -= shrinkChest(player, item, left);
        }
        if (left > 0) {
            DigitalLink link = linkOf(player);
            ServerLevel level = DigitalNetworks.levelOf(player, link == null ? null : link.pos());
            if (level != null) {
                left -= (int) DigitalNetworks.extract(player, level, link, item, left);
            }
        }
        return left <= 0 ? Result.OK : Result.MISSING;
    }

    /**
     * Build-job path: player/chest order respects {@code playerFirst}, then digital.
     * When only craftable, requests craft and returns false so the job retries later.
     */
    public static boolean hasForJob(ServerPlayer player, ServerLevel level, Item item, Container chest, boolean playerFirst, DigitalLink link) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (holds(player.getInventory(), item)) {
            return true;
        }
        if (chest != null && holds(chest, item)) {
            return true;
        }
        if (DigitalNetworks.count(level, link, item) > 0L) {
            return true;
        }
        if (DigitalNetworks.craftable(level, link, item)) {
            DigitalNetworks.requestCraft(player, level, link, item, 1L);
        }
        return false;
    }

    public static void consumeForJob(ServerPlayer player, ServerLevel level, Item item, Container chest, boolean playerFirst, DigitalLink link) {
        if (player.getAbilities().instabuild) {
            if (holds(player.getInventory(), item)) {
                shrinkPlayer(player, item, 1);
            }
            return;
        }
        if (playerFirst) {
            if (shrinkPlayer(player, item, 1) > 0) {
                return;
            }
            if (chest != null && takeOne(chest, item)) {
                chest.setChanged();
                return;
            }
        } else {
            if (chest != null && takeOne(chest, item)) {
                chest.setChanged();
                return;
            }
            if (shrinkPlayer(player, item, 1) > 0) {
                return;
            }
        }
        DigitalNetworks.extract(player, level, link, item, 1L);
    }

    public static long jobSupplyCount(ServerPlayer player, ServerLevel level, Container chest, DigitalLink link, Item item) {
        long total = 0L;
        if (chest != null) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        total += DigitalNetworks.count(level, link, item);
        return total;
    }

    private static int shrinkPlayer(ServerPlayer player, Item item, int count) {
        if (count <= 0) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int taken = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && taken < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int used = Math.min(count - taken, stack.getCount());
            stack.shrink(used);
            taken += used;
        }
        return taken;
    }

    private static int shrinkChest(ServerPlayer player, Item item, int count) {
        Container container = container(player, chestOf(player));
        if (container == null || count <= 0) {
            return 0;
        }
        int taken = 0;
        for (int slot = 0; slot < container.getContainerSize() && taken < count; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int used = Math.min(count - taken, stack.getCount());
            stack.shrink(used);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            taken += used;
        }
        if (taken > 0) {
            container.setChanged();
        }
        return taken;
    }

    private static boolean holds(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean takeOne(Container container, Item item) {
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

    public static Container container(ServerPlayer player, GlobalPos chest) {
        if (chest == null) {
            return null;
        }
        ServerLevel level = player.server.getLevel(chest.dimension());
        if (level == null || !level.mayInteract(player, chest.pos())) {
            return null;
        }
        return containerAt(level, chest.pos());
    }

    public static Container containerAt(ServerLevel level, BlockPos pos) {
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
}
