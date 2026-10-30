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
import com.wrights.item.ModItems;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.item.WrightItem;
import com.wrights.menu.HangarMenu;
import com.wrights.storage.DigitalNetworks;
import com.wrights.storage.ItemSource;
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

    public static final int ADD_COUNT = 1;
    public static final int TO_TIER = 2;
    public static final int MAX_AFFORD = 3;
    public static final int TAKE_MARKED = 4;
    public static final int DOWN_ONE = 5;

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

    public static void batch(ServerPlayer player, HangarMenu menu, int action, int value, int[] units) {
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
        Swarm next = applyBatch(player, swarm, action, value, units);
        if (next == swarm) {
            return;
        }
        Swarm.write(worn, next);
        hangar.setChanged();
        open.broadcastChanges();
    }

    public static void editPort(ServerPlayer player, int kind, int index, int action, int unit) {
        ItemStack stack = WrightportPlaces.stack(player, kind, index);
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            Messages.actionBar(player, "message.wrights.manage_missing");
            return;
        }
        Swarm swarm = Swarm.from(stack);
        if (targetsUnit(action) && awayUnit(player, swarm, unit)) {
            Messages.actionBar(player, "message.wrights.wright_away");
            ManageLock.hold(player, kind, index);
            return;
        }
        storePort(player, kind, index, swarm, edit(player, swarm, action, unit));
    }

    public static void batchPort(ServerPlayer player, int kind, int index, int action, int value, int[] units) {
        ItemStack stack = WrightportPlaces.stack(player, kind, index);
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            Messages.actionBar(player, "message.wrights.manage_missing");
            return;
        }
        Swarm swarm = Swarm.from(stack);
        int[] home = action == ADD_COUNT ? units : homeUnits(player, swarm, units);
        if (action != ADD_COUNT && units != null && units.length > 0 && home.length == 0) {
            Messages.actionBar(player, "message.wrights.wright_away");
            ManageLock.hold(player, kind, index);
            return;
        }
        storePort(player, kind, index, swarm, applyBatch(player, swarm, action, value, home));
    }

    static Swarm applyBatch(ServerPlayer player, Swarm swarm, int action, int value, int[] units) {
        return switch (action) {
            case ADD_COUNT -> insertCount(player, swarm, value);
            case TO_TIER -> moveMarked(player, swarm, units, WrightTier.clamp(value));
            case MAX_AFFORD -> raiseMarked(player, swarm, units);
            case TAKE_MARKED -> takeMarked(player, swarm, units);
            case DOWN_ONE -> downOne(player, swarm, units);
            default -> swarm;
        };
    }

    private static void storePort(ServerPlayer player, int kind, int index, Swarm swarm, Swarm next) {
        if (next != swarm) {
            WrightportPlaces.write(player, kind, index, next);
        }
        ManageLock.hold(player, kind, index);
    }

    static boolean targetsUnit(int action) {
        return action == REMOVE || action == SPEED || action == UP || action == DOWN
                || action == UPGRADE || action == TAKE || action == DOWNGRADE;
    }

    static boolean awayUnit(ServerPlayer player, Swarm swarm, int unit) {
        return unit >= 0 && unit < swarm.units().size() && ManageLock.away(player, swarm.units().get(unit).id());
    }

    static int[] homeUnits(ServerPlayer player, Swarm swarm, int[] units) {
        if (units == null || units.length == 0) {
            return new int[0];
        }
        int[] kept = new int[units.length];
        int count = 0;
        for (int unit : units) {
            if (unit < 0 || unit >= swarm.units().size()) {
                continue;
            }
            if (ManageLock.away(player, swarm.units().get(unit).id())) {
                continue;
            }
            kept[count++] = unit;
        }
        if (count == kept.length) {
            return kept;
        }
        int[] trimmed = new int[count];
        System.arraycopy(kept, 0, trimmed, 0, count);
        return trimmed;
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
        return insertCount(player, swarm, wanted(mode, room));
    }

    private static Swarm insertCount(ServerPlayer player, Swarm swarm, int count) {
        int room = Swarm.capacity() - swarm.units().size();
        if (room <= 0) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return swarm;
        }
        if (count <= 0) {
            return swarm;
        }
        List<Integer> tiers = pullWrights(player, Math.min(room, count));
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
        var chest = ItemSource.container(player, ItemSource.chestOf(player));
        if (chest != null) {
            for (int slot = 0; slot < chest.getContainerSize() && tiers.size() < count; slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!(stack.getItem() instanceof WrightItem)) {
                    continue;
                }
                int take = Math.min(stack.getCount(), count - tiers.size());
                int tier = WrightItem.tier(stack);
                stack.shrink(take);
                if (stack.isEmpty()) {
                    chest.setItem(slot, ItemStack.EMPTY);
                }
                for (int i = 0; i < take; i++) {
                    tiers.add(tier);
                }
            }
            chest.setChanged();
        }
        int need = count - tiers.size();
        if (need > 0) {
            var link = ItemSource.linkOf(player);
            var level = DigitalNetworks.levelOf(player, link.pos());
            if (level != null) {
                tiers.addAll(DigitalNetworks.extractWrights(player, level, link, need));
            }
        }
        need = count - tiers.size();
        if (need > 0 && ItemSource.craftable(player, ModItems.WRIGHT.get())) {
            var link = ItemSource.linkOf(player);
            var level = DigitalNetworks.levelOf(player, link.pos());
            if (level != null && DigitalNetworks.requestCraft(player, level, link, ModItems.WRIGHT.get(), need)) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
            }
        }
        if (player.getAbilities().instabuild) {
            while (tiers.size() < count) {
                tiers.add(WrightTier.BASE);
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
        if (tier <= WrightTier.BASE) {
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

    private static Swarm moveMarked(ServerPlayer player, Swarm swarm, int[] units, int target) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            return swarm;
        }
        if (picked.length == 1) {
            return setTier(player, swarm, picked[0], target);
        }
        Swarm next = swarm;
        int moved = 0;
        int stuck = 0;
        for (int index : picked) {
            int tier = WrightTier.clamp(next.units().get(index).speed());
            if (tier == target) {
                continue;
            }
            if (target > tier && !canAfford(player, tier, target)) {
                stuck++;
                continue;
            }
            next = applyTier(player, next, index, tier, target);
            moved++;
        }
        if (moved == 0 && stuck == 0) {
            Messages.actionBar(player, "message.wrights.marked_already", Component.translatable(WrightTier.nameKey(target)));
            return swarm;
        }
        if (moved == 0) {
            Messages.actionBar(player, "message.wrights.marked_none");
            return swarm;
        }
        if (stuck > 0) {
            Messages.actionBar(player, "message.wrights.marked_short", moved, stuck);
        } else {
            Messages.actionBar(player, "message.wrights.marked_set", moved, Component.translatable(WrightTier.nameKey(target)));
        }
        return next;
    }

    private static Swarm setTier(ServerPlayer player, Swarm swarm, int index, int target) {
        int tier = WrightTier.clamp(swarm.units().get(index).speed());
        target = WrightTier.clamp(target);
        if (tier == target) {
            Messages.actionBar(player, "message.wrights.tier_same", Component.translatable(WrightTier.nameKey(target)));
            return swarm;
        }
        if (target > tier && !canAfford(player, tier, target)) {
            Messages.actionBar(player, "message.wrights.upgrade_need", Component.translatable(WrightTier.nameKey(missingStep(player, tier, target))));
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.tier_set", Component.translatable(WrightTier.nameKey(target)));
        return applyTier(player, swarm, index, tier, target);
    }

    private static Swarm raiseMarked(ServerPlayer player, Swarm swarm, int[] units) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            return swarm;
        }
        boolean below = false;
        for (int index : picked) {
            if (WrightTier.clamp(swarm.units().get(index).speed()) < WrightTier.NETHERITE) {
                below = true;
                break;
            }
        }
        if (!below) {
            Messages.actionBar(player, "message.wrights.marked_max");
            return swarm;
        }
        Swarm next = swarm;
        boolean[] raised = new boolean[picked.length];
        int guard = picked.length * WrightTier.NETHERITE;
        while (guard-- > 0) {
            int best = -1;
            int bestTier = Integer.MAX_VALUE;
            for (int i = 0; i < picked.length; i++) {
                int tier = WrightTier.clamp(next.units().get(picked[i]).speed());
                if (tier >= WrightTier.NETHERITE || tier >= bestTier) {
                    continue;
                }
                Item upgrade = WrightTier.upgradeItem(tier + 1);
                if (upgrade != null && afford(player, upgrade, 1)) {
                    best = i;
                    bestTier = tier;
                }
            }
            if (best < 0) {
                break;
            }
            if (ItemSource.take(player, WrightTier.upgradeItem(bestTier + 1), 1) != ItemSource.Result.OK) {
                break;
            }
            next = next.withSpeed(picked[best], bestTier + 1);
            raised[best] = true;
        }
        int count = 0;
        for (boolean one : raised) {
            if (one) {
                count++;
            }
        }
        if (count == 0) {
            Messages.actionBar(player, "message.wrights.marked_raised_none");
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.marked_raised", count);
        return next;
    }

    private static Swarm downOne(ServerPlayer player, Swarm swarm, int[] units) {
        int[] picked = unique(swarm, units);
        Swarm next = swarm;
        int moved = 0;
        for (int index : picked) {
            int tier = WrightTier.clamp(next.units().get(index).speed());
            if (tier <= WrightTier.BASE) {
                continue;
            }
            next = applyTier(player, next, index, tier, tier - 1);
            moved++;
        }
        if (moved == 0) {
            Messages.actionBar(player, "message.wrights.marked_basic");
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.marked_down", moved);
        return next;
    }

    private static Swarm takeMarked(ServerPlayer player, Swarm swarm, int[] units) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            Messages.actionBar(player, "message.wrights.no_wrights");
            return swarm;
        }
        int[] order = picked.clone();
        java.util.Arrays.sort(order);
        Swarm next = swarm;
        for (int i = order.length - 1; i >= 0; i--) {
            int index = order[i];
            give(player, WrightTier.clamp(next.units().get(index).speed()), 1);
            next = next.removeUnit(index);
        }
        Messages.actionBar(player, "message.wrights.wrights_removed", order.length);
        return next;
    }

    private static Swarm applyTier(ServerPlayer player, Swarm swarm, int index, int tier, int target) {
        if (target > tier) {
            for (int step = tier + 1; step <= target; step++) {
                Item upgrade = WrightTier.upgradeItem(step);
                if (upgrade != null && ItemSource.take(player, upgrade, 1) != ItemSource.Result.OK) {
                    return swarm;
                }
            }
        } else {
            for (int step = tier; step > target; step--) {
                Item upgrade = WrightTier.upgradeItem(step);
                if (upgrade != null) {
                    giveStack(player, new ItemStack(upgrade));
                }
            }
        }
        return swarm.withSpeed(index, target);
    }

    private static boolean canAfford(ServerPlayer player, int tier, int target) {
        for (int step = tier + 1; step <= target; step++) {
            Item upgrade = WrightTier.upgradeItem(step);
            if (upgrade == null || !afford(player, upgrade, 1)) {
                return false;
            }
        }
        return true;
    }

    private static int missingStep(ServerPlayer player, int tier, int target) {
        for (int step = tier + 1; step <= target; step++) {
            Item upgrade = WrightTier.upgradeItem(step);
            if (upgrade == null || !afford(player, upgrade, 1)) {
                return step;
            }
        }
        return target;
    }

    private static boolean afford(ServerPlayer player, Item item, int count) {
        return player.getAbilities().instabuild || ItemSource.canAfford(player, item, count);
    }

    private static int[] unique(Swarm swarm, int[] units) {
        if (units == null || units.length == 0) {
            return new int[0];
        }
        boolean[] seen = new boolean[swarm.units().size()];
        int count = 0;
        for (int unit : units) {
            if (unit >= 0 && unit < seen.length && !seen[unit]) {
                seen[unit] = true;
                count++;
            }
        }
        int[] picked = new int[count];
        int cursor = 0;
        for (int i = 0; i < seen.length; i++) {
            if (seen[i]) {
                picked[cursor++] = i;
            }
        }
        return picked;
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
        ItemSource.Result result = ItemSource.take(player, item, 1);
        if (result == ItemSource.Result.CRAFTING) {
            Messages.actionBar(player, "message.wrights.digital_crafting");
            return false;
        }
        return result == ItemSource.Result.OK;
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
        if (!ItemSource.canAfford(player, Items.IRON_INGOT, IRON)
                || !ItemSource.canAfford(player, Items.REDSTONE, REDSTONE)
                || !ItemSource.canAfford(player, Items.AMETHYST_SHARD, AMETHYST)) {
            if (tryCraftParts(player, Items.IRON_INGOT, IRON)
                    || tryCraftParts(player, Items.REDSTONE, REDSTONE)
                    || tryCraftParts(player, Items.AMETHYST_SHARD, AMETHYST)) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
                return swarm;
            }
            Messages.actionBar(player, "message.wrights.drone_parts", IRON, REDSTONE, AMETHYST);
            return swarm;
        }
        if (takeOrCraft(player, Items.IRON_INGOT, IRON)
                || takeOrCraft(player, Items.REDSTONE, REDSTONE)
                || takeOrCraft(player, Items.AMETHYST_SHARD, AMETHYST)) {
            return swarm;
        }
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
        int gold = Math.max(1, speed);
        int redstone = Math.max(1, speed);
        if (!ItemSource.canAfford(player, Items.GOLD_INGOT, gold) || !ItemSource.canAfford(player, Items.REDSTONE, redstone)) {
            if (tryCraftParts(player, Items.GOLD_INGOT, gold) || tryCraftParts(player, Items.REDSTONE, redstone)) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
                return swarm;
            }
            Messages.actionBar(player, "message.wrights.speed_parts", gold, redstone);
            return swarm;
        }
        if (takeOrCraft(player, Items.GOLD_INGOT, gold) || takeOrCraft(player, Items.REDSTONE, redstone)) {
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.speed_up", speed + 1);
        return swarm.withSpeed(index, speed + 1);
    }

    /** Returns true when a craft was requested instead of taking items. */
    private static boolean takeOrCraft(ServerPlayer player, Item item, int count) {
        ItemSource.Result result = ItemSource.take(player, item, count);
        if (result == ItemSource.Result.CRAFTING) {
            Messages.actionBar(player, "message.wrights.digital_crafting");
            return true;
        }
        return result != ItemSource.Result.OK;
    }

    private static boolean tryCraftParts(ServerPlayer player, Item item, int count) {
        long have = ItemSource.available(player, item);
        return have < count && ItemSource.craftable(player, item)
                && ItemSource.take(player, item, count) == ItemSource.Result.CRAFTING;
    }
}
