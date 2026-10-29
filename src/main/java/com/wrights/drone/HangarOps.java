package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.wrights.block.HangarBlockEntity;
import com.wrights.item.WrightItem;
import com.wrights.menu.HangarMenu;
import com.wrights.util.Messages;

public final class HangarOps {
    public static final int BUILD = 0;
    public static final int REMOVE = 1;
    public static final int SPEED = 2;
    public static final int ASSIGN = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;
    public static final int INSERT = 6;
    public static final int EXTRACT = 7;
    public static final int UPGRADE = 8;
    public static final int TAKE = 9;
    public static final int DOWNGRADE = 10;

    public static final int ONE = 0;
    public static final int SIXTEEN = 1;
    public static final int STACK = 2;
    public static final int ALL = 3;

    public static final int IRON = 4;
    public static final int REDSTONE = 2;
    public static final int AMETHYST = 1;

    private HangarOps() {
    }

    public static void apply(ServerPlayer player, HangarMenu menu, int action, int index) {
        if (!(player.level() instanceof Level level)) {
            return;
        }
        if (!(level.getBlockEntity(menu.pos()) instanceof HangarBlockEntity hangar)) {
            return;
        }
        if (!(player.containerMenu instanceof HangarMenu open) || !open.pos().equals(menu.pos())) {
            return;
        }
        ItemStack worn = hangar.items().getItem(0);
        if (worn.isEmpty()) {
            Messages.actionBar(player, "message.wrights.dock_wrightport");
            return;
        }
        Swarm swarm = Swarm.from(worn);
        Swarm next = edit(player, swarm, action, index);
        if (next == swarm) {
            return;
        }
        Swarm.write(worn, next);
        hangar.setChanged();
        open.broadcastChanges();
    }

    public static Swarm edit(ServerPlayer player, Swarm swarm, int action, int index) {
        return switch (action) {
            case BUILD -> build(player, swarm);
            case REMOVE -> swarm.removeUnit(index);
            case SPEED -> speed(player, swarm, index);
            case ASSIGN -> swarm.withAssign(index);
            case UP -> swarm.move(index, -1);
            case DOWN -> swarm.move(index, 1);
            case INSERT -> insert(player, swarm, index);
            case EXTRACT -> extract(player, swarm, index);
            case UPGRADE -> upgrade(player, swarm, index);
            case TAKE -> take(player, swarm, index);
            case DOWNGRADE -> downgrade(player, swarm, index);
            default -> swarm;
        };
    }

    private static int wanted(int mode, int available) {
        int ask = switch (mode) {
            case SIXTEEN -> 16;
            case STACK -> 64;
            case ALL -> available;
            default -> 1;
        };
        return Math.min(Math.max(0, available), Math.max(0, ask));
    }

    private static Swarm insert(ServerPlayer player, Swarm swarm, int mode) {
        int room = Swarm.capacity() - swarm.units().size();
        if (room <= 0) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return swarm;
        }
        List<Integer> tiers = pullWrights(player, wanted(mode, room));
        if (tiers.isEmpty()) {
            Messages.actionBar(player, "message.wrights.need_wright_item");
            return swarm;
        }
        Swarm next = swarm;
        for (int tier : tiers) {
            next = next.addUnit(tier);
        }
        Messages.actionBar(player, "message.wrights.wrights_added", tiers.size());
        return next;
    }

    private static List<Integer> pullWrights(ServerPlayer player, int count) {
        List<Integer> tiers = new ArrayList<>();
        if (count <= 0) {
            return tiers;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && tiers.size() < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof WrightItem)) {
                continue;
            }
            int take = Math.min(stack.getCount(), count - tiers.size());
            int tier = WrightItem.tier(stack);
            stack.shrink(take);
            for (int i = 0; i < take; i++) {
                tiers.add(tier);
            }
        }
        if (player.getAbilities().instabuild) {
            while (tiers.size() < count) {
                tiers.add(WrightTier.COPPER);
            }
        }
        return tiers;
    }

    private static Swarm extract(ServerPlayer player, Swarm swarm, int mode) {
        return takeFrom(player, swarm, swarm.units().size() - 1, wanted(mode, swarm.units().size()), true);
    }

    private static Swarm take(ServerPlayer player, Swarm swarm, int index) {
        return takeFrom(player, swarm, index, 1, false);
    }

    private static Swarm takeFrom(ServerPlayer player, Swarm swarm, int start, int count, boolean backward) {
        if (swarm.units().isEmpty() || count <= 0) {
            Messages.actionBar(player, "message.wrights.no_wrights");
            return swarm;
        }
        Swarm next = swarm;
        int taken = 0;
        if (backward) {
            for (int i = 0; i < count && !next.units().isEmpty(); i++) {
                int index = next.units().size() - 1;
                give(player, WrightTier.clamp(next.units().get(index).speed()), 1);
                next = next.removeUnit(index);
                taken++;
            }
        } else if (start >= 0 && start < next.units().size()) {
            give(player, WrightTier.clamp(next.units().get(start).speed()), 1);
            next = next.removeUnit(start);
            taken = 1;
        }
        if (taken > 0) {
            Messages.actionBar(player, "message.wrights.wrights_removed", taken);
        }
        return next;
    }

    private static Swarm downgrade(ServerPlayer player, Swarm swarm, int index) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        int tier = WrightTier.clamp(swarm.units().get(index).speed());
        if (tier <= WrightTier.COPPER) {
            Messages.actionBar(player, "message.wrights.downgrade_none");
            return swarm;
        }
        Item upgrade = WrightTier.upgradeItem(tier);
        if (upgrade != null) {
            giveStack(player, new ItemStack(upgrade));
        }
        Messages.actionBar(player, "message.wrights.downgrade_done", Component.translatable(WrightTier.nameKey(tier)));
        return swarm.withSpeed(index, tier - 1);
    }

    private static void giveStack(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static Swarm upgrade(ServerPlayer player, Swarm swarm, int index) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        int tier = WrightTier.clamp(swarm.units().get(index).speed());
        if (tier >= WrightTier.NETHERITE) {
            Messages.actionBar(player, "message.wrights.upgrade_max");
            return swarm;
        }
        int nextTier = tier + 1;
        Item upgrade = WrightTier.upgradeItem(nextTier);
        if (upgrade == null || !consume(player, upgrade)) {
            Messages.actionBar(player, "message.wrights.upgrade_need", Component.translatable(WrightTier.nameKey(nextTier)));
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.upgrade_done", Component.translatable(WrightTier.nameKey(nextTier)));
        return swarm.withSpeed(index, nextTier);
    }

    private static boolean consume(ServerPlayer player, Item item) {
        if (player.getAbilities().instabuild && player.getInventory().countItem(item) <= 0) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void give(ServerPlayer player, int tier, int count) {
        int left = count;
        while (left > 0) {
            int n = Math.min(64, left);
            ItemStack stack = WrightItem.stack(tier, n);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            left -= n;
        }
    }

    private static Swarm build(ServerPlayer player, Swarm swarm) {
        if (swarm.units().size() >= Swarm.capacity()) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return swarm;
        }
        if (!pay(player, Items.IRON_INGOT, IRON) || !pay(player, Items.REDSTONE, REDSTONE) || !pay(player, Items.AMETHYST_SHARD, AMETHYST)) {
            Messages.actionBar(player, "message.wrights.drone_parts", IRON, REDSTONE, AMETHYST);
            return swarm;
        }
        take(player, Items.IRON_INGOT, IRON);
        take(player, Items.REDSTONE, REDSTONE);
        take(player, Items.AMETHYST_SHARD, AMETHYST);
        Messages.actionBar(player, "message.wrights.drone_built", swarm.units().size() + 1);
        return swarm.addUnit();
    }

    private static Swarm speed(ServerPlayer player, Swarm swarm, int index) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        int speed = swarm.units().get(index).speed();
        if (speed >= Swarm.MAX_SPEED) {
            Messages.actionBar(player, "message.wrights.speed_max");
            return swarm;
        }
        int gold = speed;
        int redstone = speed;
        if (!pay(player, Items.GOLD_INGOT, gold) || !pay(player, Items.REDSTONE, redstone)) {
            Messages.actionBar(player, "message.wrights.speed_parts", gold, redstone);
            return swarm;
        }
        take(player, Items.GOLD_INGOT, gold);
        take(player, Items.REDSTONE, redstone);
        Messages.actionBar(player, "message.wrights.speed_up", speed + 1);
        return swarm.withSpeed(index, speed + 1);
    }

    private static boolean pay(Player player, Item item, int count) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int have = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        return have >= count;
    }

    private static void take(Player player, Item item, int count) {
        if (player.getAbilities().instabuild) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int used = Math.min(count, stack.getCount());
            stack.shrink(used);
            count -= used;
        }
    }
}
