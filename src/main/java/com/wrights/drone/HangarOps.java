package com.wrights.drone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsConfig;
import com.wrights.item.ModItems;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.item.WrightItem;
import com.wrights.network.BlueprintPayloads;
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
        CraftDeliveries.push(CraftDeliveries.Hangar.personal(player.getUUID(), kind, index));
        try {
            storePort(player, kind, index, swarm, edit(player, swarm, action, unit));
        } finally {
            CraftDeliveries.pop();
        }
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
        CraftDeliveries.push(CraftDeliveries.Hangar.personal(player.getUUID(), kind, index));
        try {
            storePort(player, kind, index, swarm, applyBatch(player, swarm, action, value, home));
        } finally {
            CraftDeliveries.pop();
        }
    }

    static Swarm applyBatch(ServerPlayer player, Swarm swarm, int action, int value, int[] units) {
        return applyBatchOwned(player, player.getUUID(), swarm, action, value, units);
    }

    static Swarm applyBatchOwned(ServerPlayer player, UUID owner, Swarm swarm, int action, int value, int[] units) {
        beginUseNotes();
        try {
            return switch (action) {
                case ADD_COUNT -> insertCount(player, owner, swarm, value);
                case TO_TIER -> moveMarked(player, swarm, units, WrightTier.clamp(value));
                case MAX_AFFORD -> raiseMarked(player, swarm, units);
                case TAKE_MARKED -> takeMarked(player, swarm, units);
                case DOWN_ONE -> downOne(player, swarm, units);
                default -> swarm;
            };
        } finally {
            flushUseNotes(player);
        }
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
        return editOwned(player, player.getUUID(), swarm, action, index);
    }

    public static Swarm editOwned(ServerPlayer player, UUID owner, Swarm swarm, int action, int index) {
        beginUseNotes();
        try {
            return switch (action) {
                case BUILD -> build(player, owner, swarm);
                case REMOVE -> swarm.removeUnit(index);
                case SPEED -> speed(player, swarm, index);
                case ASSIGN -> swarm.withAssign(index);
                case UP -> swarm.move(index, -1);
                case DOWN -> swarm.move(index, 1);
                case INSERT -> insert(player, owner, swarm, index);
                case EXTRACT -> extract(player, swarm, index);
                case UPGRADE -> upgrade(player, swarm, index);
                case TAKE -> take(player, swarm, index);
                case DOWNGRADE -> downgrade(player, swarm, index);
                default -> swarm;
            };
        } finally {
            flushUseNotes(player);
        }
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

    private static Swarm insert(ServerPlayer player, UUID owner, Swarm swarm, int mode) {
        int room = Swarm.capacity() - swarm.units().size();
        return insertCount(player, owner, swarm, wanted(mode, room));
    }

    private static Swarm insertCount(ServerPlayer player, UUID owner, Swarm swarm, int count) {
        int portRoom = Swarm.capacity() - swarm.units().size();
        if (portRoom <= 0) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return swarm;
        }
        int capRoom = WrightLedger.room(player.serverLevel().getServer(), owner);
        if (capRoom <= 0) {
            Messages.actionBar(player, "message.wrights.wright_cap", WrightsConfig.maxWrightsOwned());
            return swarm;
        }
        int room = Math.min(portRoom, capRoom);
        if (count <= 0) {
            return swarm;
        }
        Pull pulled = pullWrights(player, Math.min(room, count));
        List<Taken> tiers = pulled.tiers();
        if (tiers.isEmpty()) {
            if (pulled.crafting() > 0) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
            } else if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.need_wright_item");
            }
            return swarm;
        }
        Swarm next = swarm;
        for (Taken taken : tiers) {
            next = next.addUnit(taken.tier(), taken.name());
        }
        HangarDock.show(player, tiers.stream().map(Taken::tier).toList());
        if (tiers.size() >= capRoom) {
            Messages.actionBar(player, "message.wrights.wright_cap_added", tiers.size(), WrightsConfig.maxWrightsOwned());
        } else {
            Messages.actionBar(player, "message.wrights.wrights_added", tiers.size());
        }
        return next;
    }

    private static Pull pullWrights(ServerPlayer player, int count) {
        List<Taken> tiers = new ArrayList<>();
        if (count <= 0) {
            return new Pull(tiers, 0);
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && tiers.size() < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof WrightItem)) {
                continue;
            }
            int take = Math.min(stack.getCount(), count - tiers.size());
            int tier = WrightItem.tier(stack);
            String name = WrightItem.nameOf(stack);
            stack.shrink(take);
            for (int i = 0; i < take; i++) {
                tiers.add(new Taken(tier, name));
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
                String name = WrightItem.nameOf(stack);
                stack.shrink(take);
                if (stack.isEmpty()) {
                    chest.setItem(slot, ItemStack.EMPTY);
                }
                for (int i = 0; i < take; i++) {
                    tiers.add(new Taken(tier, name));
                }
            }
            chest.setChanged();
        }
        int beforeDigital = tiers.size();
        int need = count - tiers.size();
        if (need > 0) {
            var link = ItemSource.linkOf(player);
            var level = DigitalNetworks.levelOf(player, link.pos());
            if (level != null) {
                for (int tier : DigitalNetworks.extractWrights(player, level, link, need)) {
                    tiers.add(new Taken(tier, ""));
                }
            }
        }
        int fromDigital = tiers.size() - beforeDigital;
        if (fromDigital > 0) {
            noteUse(ModItems.WRIGHT.get(), fromDigital, 0);
        }
        need = count - tiers.size();
        int crafting = 0;
        if (!player.getAbilities().instabuild && need > 0 && orderCraft(player, ModItems.WRIGHT.get(), need)) {
            crafting = need;
        }
        if (player.getAbilities().instabuild) {
            while (tiers.size() < count) {
                tiers.add(new Taken(WrightTier.BASE, ""));
            }
        }
        return new Pull(tiers, crafting);
    }

    private record Taken(int tier, String name) {
    }

    private record Pull(List<Taken> tiers, int crafting) {
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
                giveUnit(player, next.units().get(index));
                next = next.removeUnit(index);
                taken++;
            }
        } else if (start >= 0 && start < next.units().size()) {
            giveUnit(player, next.units().get(start));
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
        int craftNeed = 0;
        for (int index : picked) {
            int tier = WrightTier.clamp(next.units().get(index).speed());
            if (tier == target) {
                continue;
            }
            if (target > tier && !canAfford(player, tier, target)) {
                craftNeed++;
                continue;
            }
            next = applyTier(player, next, index, tier, target);
            moved++;
        }
        boolean crafting = craftNeed > 0 && orderCraft(player, WrightTier.upgradeItem(target), craftNeed);
        if (!crafting) {
            stuck += craftNeed;
        }
        if (moved == 0 && stuck == 0 && !crafting) {
            Messages.actionBar(player, "message.wrights.marked_already", Component.translatable(WrightTier.nameKey(target)));
            return swarm;
        }
        if (moved == 0) {
            if (crafting) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
            } else if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.marked_none");
            }
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
            if (orderCraft(player, WrightTier.upgradeItem(target), 1)) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
            } else if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.upgrade_need", Component.translatable(WrightTier.nameKey(target)));
            }
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
        for (int i = 0; i < picked.length; i++) {
            int tier = WrightTier.clamp(next.units().get(picked[i]).speed());
            int goal = highestAffordable(player, tier);
            if (goal <= tier) {
                continue;
            }
            Swarm changed = applyTier(player, next, picked[i], tier, goal);
            if (changed == next) {
                continue;
            }
            next = changed;
            raised[i] = true;
        }
        int count = 0;
        for (boolean one : raised) {
            if (one) {
                count++;
            }
        }
        if (count == 0) {
            int[] missing = new int[WrightTier.NETHERITE + 1];
            for (int index : picked) {
                int tier = WrightTier.clamp(swarm.units().get(index).speed());
                if (tier >= WrightTier.NETHERITE) {
                    continue;
                }
                int want = WrightTier.NETHERITE;
                for (int step = WrightTier.NETHERITE; step > tier; step--) {
                    if (ItemSource.craftable(player, WrightTier.upgradeItem(step))) {
                        want = step;
                        break;
                    }
                }
                missing[want]++;
            }
            boolean ordered = false;
            for (int step = WrightTier.COPPER; step <= WrightTier.NETHERITE; step++) {
                if (missing[step] > 0 && orderCraft(player, WrightTier.upgradeItem(step), missing[step])) {
                    ordered = true;
                }
            }
            if (ordered) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
            } else if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.marked_raised_none");
            }
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.marked_raised", count);
        return next;
    }

    /** Highest tier above {@code tier} that only needs that tier's upgrade item. */
    private static int highestAffordable(ServerPlayer player, int tier) {
        int best = tier;
        for (int step = WrightTier.NETHERITE; step > tier; step--) {
            if (canAfford(player, tier, step)) {
                return step;
            }
        }
        return best;
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
            giveUnit(player, next.units().get(index));
            next = next.removeUnit(index);
        }
        Messages.actionBar(player, "message.wrights.wrights_removed", order.length);
        return next;
    }

    private static Swarm applyTier(ServerPlayer player, Swarm swarm, int index, int tier, int target) {
        if (target > tier) {
            Item upgrade = WrightTier.upgradeItem(target);
            if (upgrade != null && takeUpgrade(player, upgrade, 1).result() != ItemSource.Result.OK) {
                return swarm;
            }
            refundInstalled(player, tier);
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

    /** Only the destination upgrade is spent; lower steps are not required. */
    private static boolean canAfford(ServerPlayer player, int tier, int target) {
        if (target <= tier) {
            return true;
        }
        Item upgrade = WrightTier.upgradeItem(target);
        return upgrade == null || afford(player, upgrade, 1);
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

    private static boolean orderCraft(ServerPlayer player, Item item, long amount) {
        if (item == null || amount <= 0L || !ItemSource.craftable(player, item)) {
            return false;
        }
        var link = ItemSource.linkOf(player);
        var level = DigitalNetworks.levelOf(player, link == null ? null : link.pos());
        boolean wright = item == ModItems.WRIGHT.get();
        if (wright) {
            CraftDeliveries.expect(player, amount);
        }
        boolean ordered = level != null && DigitalNetworks.requestCraft(player, level, link, item, amount);
        if (!ordered) {
            if (wright) {
                CraftDeliveries.cancel(player.getUUID());
            }
            return false;
        }
        if (!wright) {
            noteUse(item, 0, (int) Math.min(Integer.MAX_VALUE, amount));
        }
        return true;
    }

    private static final ThreadLocal<Map<Item, int[]>> USE_NOTES = ThreadLocal.withInitial(HashMap::new);

    private static void beginUseNotes() {
        USE_NOTES.get().clear();
        DigitalNetworks.beginCraftFeedback();
    }

    private static void noteUse(Item item, int pulled, int crafted) {
        if (item == null || (pulled <= 0 && crafted <= 0)) {
            return;
        }
        int[] counts = USE_NOTES.get().computeIfAbsent(item, key -> new int[2]);
        counts[0] += Math.max(0, pulled);
        counts[1] += Math.max(0, crafted);
    }

    private static void flushUseNotes(ServerPlayer player) {
        Map<Item, int[]> notes = USE_NOTES.get();
        if (notes.isEmpty()) {
            return;
        }
        List<BlueprintPayloads.DigitalUseLine> lines = new ArrayList<>();
        for (Map.Entry<Item, int[]> entry : notes.entrySet()) {
            int pulled = entry.getValue()[0];
            int crafted = entry.getValue()[1];
            if (pulled > 0 || crafted > 0) {
                lines.add(new BlueprintPayloads.DigitalUseLine(Item.getId(entry.getKey()), pulled, crafted));
            }
        }
        notes.clear();
        if (!lines.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DigitalUse(lines));
        }
    }

    private static ItemSource.Take takeUpgrade(ServerPlayer player, Item item, int count) {
        ItemSource.Take taken = ItemSource.takeDetailed(player, item, count);
        if (taken.result() == ItemSource.Result.OK) {
            noteUse(item, taken.fromDigital(), 0);
        } else if (taken.result() == ItemSource.Result.CRAFTING) {
            noteUse(item, 0, taken.crafted());
        }
        return taken;
    }

    private static void refundInstalled(ServerPlayer player, int tier) {
        Item previous = WrightTier.upgradeItem(tier);
        if (previous != null) {
            giveStack(player, new ItemStack(previous));
        }
    }

    private static void giveStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(stack);
            player.containerMenu.broadcastChanges();
            return;
        }
        if (ItemStack.isSameItemSameComponents(carried, stack)) {
            int room = carried.getMaxStackSize() - carried.getCount();
            if (room > 0) {
                int move = Math.min(room, stack.getCount());
                carried.grow(move);
                stack.shrink(move);
                player.containerMenu.setCarried(carried);
                player.containerMenu.broadcastChanges();
            }
        }
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
        if (upgrade == null) {
            Messages.actionBar(player, "message.wrights.upgrade_need", Component.translatable(WrightTier.nameKey(nextTier)));
            return swarm;
        }
        ItemSource.Take taken = takeUpgrade(player, upgrade, 1);
        if (taken.result() == ItemSource.Result.CRAFTING) {
            Messages.actionBar(player, "message.wrights.digital_crafted", Math.max(1, taken.crafted()), new ItemStack(upgrade).getHoverName());
            return swarm;
        }
        if (taken.result() != ItemSource.Result.OK) {
            if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.upgrade_need", Component.translatable(WrightTier.nameKey(nextTier)));
            }
            return swarm;
        }
        refundInstalled(player, tier);
        Messages.actionBar(player, "message.wrights.upgrade_done", Component.translatable(WrightTier.nameKey(nextTier)));
        return swarm.withSpeed(index, nextTier);
    }

    static Swarm rename(ServerPlayer player, Swarm swarm, int index, String name) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        if (ManageLock.away(player, swarm.units().get(index).id())) {
            Messages.actionBar(player, "message.wrights.wright_away");
            return swarm;
        }
        return swarm.withName(index, name);
    }

    public static void renamePort(ServerPlayer player, int kind, int index, int unit, String name) {
        ItemStack stack = WrightportPlaces.stack(player, kind, index);
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            Messages.actionBar(player, "message.wrights.manage_missing");
            return;
        }
        Swarm swarm = Swarm.from(stack);
        storePort(player, kind, index, swarm, rename(player, swarm, unit, name));
    }

    private static void giveUnit(ServerPlayer player, Swarm.Unit unit) {
        ItemStack stack = WrightItem.stack(WrightTier.clamp(unit.speed()), 1);
        WrightItem.writeName(stack, unit.name());
        giveStack(player, stack);
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

    private static Swarm build(ServerPlayer player, UUID owner, Swarm swarm) {
        if (swarm.units().size() >= Swarm.capacity()) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return swarm;
        }
        if (WrightLedger.room(player.serverLevel().getServer(), owner) <= 0) {
            Messages.actionBar(player, "message.wrights.wright_cap", WrightsConfig.maxWrightsOwned());
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
            if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.drone_parts", IRON, REDSTONE, AMETHYST);
            }
            return swarm;
        }
        if (takeOrCraft(player, Items.IRON_INGOT, IRON)
                || takeOrCraft(player, Items.REDSTONE, REDSTONE)
                || takeOrCraft(player, Items.AMETHYST_SHARD, AMETHYST)) {
            return swarm;
        }
        Messages.actionBar(player, "message.wrights.drone_built", swarm.units().size() + 1);
        HangarDock.show(player, List.of(WrightTier.BASE));
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
            if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.speed_parts", gold, redstone);
            }
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
