package com.wrights.drone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.NonNullList;
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
    public static final int ASSIGN = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;
    public static final int INSERT = 6;
    public static final int EXTRACT = 7;
    public static final int TAKE = 9;
    /** Edit action: fit the next tier of a part. Plus {@link PartSlot#ordinal()}; see {@link #partNext}. */
    private static final int PART_NEXT = 20;
    /** Edit action: take a part out. Plus {@link PartSlot#ordinal()}; see {@link #partRemove}. */
    private static final int PART_REMOVE = 30;

    public static final int ADD_COUNT = 1;
    public static final int BEST_PARTS = 3;
    public static final int TAKE_MARKED = 4;
    public static final int STRIP_MARKED = 5;
    /** Pull inventory + digital stock only (no craft order). */
    public static final int ADD_STOCK = 6;
    /** Order a craft for new wrights (no inventory pull). */
    public static final int ADD_CRAFT = 7;
    /** Batch action: fit the tier in {@code value} to every marked wright. Plus {@link PartSlot#ordinal()}; see {@link #partFit}. */
    private static final int PART_FIT = 40;

    /** Hangar tip strip: fitted / added. */
    private static final int TIP_ADD = 0xFF6FCF97;
    /** Hangar tip strip: took out a part or wright. */
    private static final int TIP_TAKE = 0xFFC47A3A;
    /** Hangar tip strip: warnings (need part, no space, …). */
    private static final int TIP_WARN = 0xFFFF5555;

    public static final int ONE = 0;
    public static final int SIXTEEN = 1;
    public static final int STACK = 2;
    public static final int ALL = 3;

    public static final int IRON = 4;
    public static final int REDSTONE = 2;
    public static final int AMETHYST = 1;

    private HangarOps() {
    }

    public static int partNext(PartSlot slot) {
        return PART_NEXT + slot.ordinal();
    }

    public static int partRemove(PartSlot slot) {
        return PART_REMOVE + slot.ordinal();
    }

    public static int partFit(PartSlot slot) {
        return PART_FIT + slot.ordinal();
    }

    /** The slot an action at {@code base} + ordinal names, or null when the action is something else. */
    private static PartSlot slotOf(int action, int base) {
        int ordinal = action - base;
        PartSlot[] slots = PartSlot.values();
        return ordinal >= 0 && ordinal < slots.length ? slots[ordinal] : null;
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
        int[] home = isAdd(action) ? units : homeUnits(player, swarm, units);
        if (!isAdd(action) && units != null && units.length > 0 && home.length == 0) {
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
                case ADD_STOCK -> insertStock(player, owner, swarm, value);
                case ADD_CRAFT -> insertCraft(player, owner, swarm, value);
                case BEST_PARTS -> bestParts(player, swarm, units, value != 0);
                case TAKE_MARKED -> takeMarked(player, swarm, units);
                case STRIP_MARKED -> stripMarked(player, swarm, units);
                default -> {
                    PartSlot slot = slotOf(action, PART_FIT);
                    yield slot == null ? swarm : fitMarked(player, swarm, units, slot, WrightTier.clamp(value));
                }
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

    /** Shift-click one wright item into a personal wrightport hangar bay. */
    public static boolean quickDepositPort(ServerPlayer player, int kind, int index, ItemStack stack) {
        if (!(stack.getItem() instanceof WrightItem) || stack.isEmpty()) {
            return false;
        }
        ItemStack port = WrightportPlaces.stack(player, kind, index);
        if (!(port.getItem() instanceof PersonalWrightportItem)) {
            return false;
        }
        Swarm swarm = Swarm.from(port);
        if (swarm.units().size() >= Swarm.capacity()) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return false;
        }
        int capRoom = WrightLedger.room(player.serverLevel().getServer(), player.getUUID());
        if (capRoom <= 0) {
            Messages.actionBar(player, "message.wrights.wright_cap", WrightsConfig.maxWrightsOwned());
            return false;
        }
        Loadout parts = WrightItem.parts(stack);
        String name = WrightItem.nameOf(stack);
        stack.shrink(1);
        Swarm next = swarm.addUnit(parts, name);
        HangarDock.show(player, CraftDeliveries.Hangar.personal(player.getUUID(), kind, index), java.util.List.of(parts));
        WrightportPlaces.write(player, kind, index, next);
        if (capRoom <= 1) {
            Messages.actionBar(player, "message.wrights.wright_cap_added", 1, WrightsConfig.maxWrightsOwned());
        } else {
            Messages.actionBar(player, "message.wrights.wrights_added", 1);
        }
        ManageLock.hold(player, kind, index);
        if (player.containerMenu instanceof com.wrights.menu.PersonalHangarMenu open
                && open.kind() == kind && open.index() == index) {
            open.broadcastChanges();
        }
        return true;
    }

    static boolean targetsUnit(int action) {
        return action == REMOVE || action == UP || action == DOWN || action == TAKE
                || slotOf(action, PART_NEXT) != null || slotOf(action, PART_REMOVE) != null;
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
                case ASSIGN -> swarm.withAssign(index);
                case UP -> swarm.move(index, -1);
                case DOWN -> swarm.move(index, 1);
                case INSERT -> insert(player, owner, swarm, index);
                case EXTRACT -> extract(player, swarm, index);
                case TAKE -> take(player, swarm, index);
                default -> {
                    PartSlot next = slotOf(action, PART_NEXT);
                    PartSlot out = slotOf(action, PART_REMOVE);
                    if (next != null) {
                        yield fitNext(player, swarm, index, next);
                    }
                    yield out == null ? swarm : fitOne(player, swarm, index, out, WrightTier.BASIC);
                }
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

    static boolean isAdd(int action) {
        return action == ADD_COUNT || action == ADD_STOCK || action == ADD_CRAFT;
    }

    private static Swarm insertCount(ServerPlayer player, UUID owner, Swarm swarm, int count) {
        int room = peekAddRoom(player, owner, swarm);
        if (room <= 0 || count <= 0) {
            return insertPulled(player, owner, swarm, count, new Pull(List.of(), 0));
        }
        return insertPulled(player, owner, swarm, count, pullWrights(player, Math.min(room, count)));
    }

    private static Swarm insertStock(ServerPlayer player, UUID owner, Swarm swarm, int count) {
        int room = peekAddRoom(player, owner, swarm);
        if (room <= 0 || count <= 0) {
            return insertPulled(player, owner, swarm, count, new Pull(List.of(), 0));
        }
        return insertPulled(player, owner, swarm, count, pullWrightsStock(player, Math.min(room, count)));
    }

    private static int peekAddRoom(ServerPlayer player, UUID owner, Swarm swarm) {
        int portRoom = Swarm.capacity() - swarm.units().size();
        if (portRoom <= 0) {
            return 0;
        }
        return Math.min(portRoom, WrightLedger.room(player.serverLevel().getServer(), owner));
    }

    private static Swarm insertCraft(ServerPlayer player, UUID owner, Swarm swarm, int count) {
        int room = addRoom(player, owner, swarm);
        if (room <= 0 || count <= 0) {
            return swarm;
        }
        int want = Math.min(room, count);
        if (player.getAbilities().instabuild) {
            return insertPulled(player, owner, swarm, want, new Pull(creativeTaken(want), 0));
        }
        if (orderCraft(player, ModItems.WRIGHT.get(), want)) {
            Messages.actionBar(player, "message.wrights.digital_crafting");
        } else if (!DigitalNetworks.craftFailureExplained()) {
            Messages.actionBar(player, "message.wrights.need_wright_item");
        }
        return swarm;
    }

    private static int addRoom(ServerPlayer player, UUID owner, Swarm swarm) {
        int portRoom = Swarm.capacity() - swarm.units().size();
        if (portRoom <= 0) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return 0;
        }
        int capRoom = WrightLedger.room(player.serverLevel().getServer(), owner);
        if (capRoom <= 0) {
            Messages.actionBar(player, "message.wrights.wright_cap", WrightsConfig.maxWrightsOwned());
            return 0;
        }
        return Math.min(portRoom, capRoom);
    }

    private static Swarm insertPulled(ServerPlayer player, UUID owner, Swarm swarm, int count, Pull pulled) {
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
        List<Taken> tiers = pulled.tiers();
        if (tiers.size() > room) {
            tiers = new ArrayList<>(tiers.subList(0, room));
        }
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
            next = next.addUnit(taken.parts(), taken.name());
        }
        HangarDock.show(player, tiers.stream().map(Taken::parts).toList());
        if (tiers.size() >= capRoom) {
            Messages.actionBar(player, "message.wrights.wright_cap_added", tiers.size(), WrightsConfig.maxWrightsOwned());
        } else {
            Messages.actionBar(player, "message.wrights.wrights_added", tiers.size());
        }
        return next;
    }

    private static List<Taken> creativeTaken(int count) {
        List<Taken> tiers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tiers.add(new Taken(Loadout.BASIC, ""));
        }
        return tiers;
    }

    private static Pull pullWrights(ServerPlayer player, int count) {
        Pull stock = pullWrightsStock(player, count);
        List<Taken> tiers = new ArrayList<>(stock.tiers());
        int need = count - tiers.size();
        int crafting = 0;
        if (!player.getAbilities().instabuild && need > 0 && orderCraft(player, ModItems.WRIGHT.get(), need)) {
            crafting = need;
        }
        if (player.getAbilities().instabuild) {
            while (tiers.size() < count) {
                tiers.add(new Taken(Loadout.BASIC, ""));
            }
        }
        return new Pull(tiers, crafting);
    }

    private static Pull pullWrightsStock(ServerPlayer player, int count) {
        List<Taken> tiers = new ArrayList<>();
        if (count <= 0) {
            return new Pull(tiers, 0);
        }
        // Cursor first so clicking a bay with a picked-up wright deposits that stack.
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            while (tiers.size() < count && carried.getItem() instanceof WrightItem && !carried.isEmpty()) {
                Loadout parts = WrightItem.parts(carried);
                String name = WrightItem.nameOf(carried);
                carried.shrink(1);
                tiers.add(new Taken(parts, name));
            }
            player.containerMenu.setCarried(carried);
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && tiers.size() < count; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof WrightItem)) {
                continue;
            }
            int take = Math.min(stack.getCount(), count - tiers.size());
            Loadout parts = WrightItem.parts(stack);
            String name = WrightItem.nameOf(stack);
            stack.shrink(take);
            for (int i = 0; i < take; i++) {
                tiers.add(new Taken(parts, name));
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
                Loadout parts = WrightItem.parts(stack);
                String name = WrightItem.nameOf(stack);
                stack.shrink(take);
                if (stack.isEmpty()) {
                    chest.setItem(slot, ItemStack.EMPTY);
                }
                for (int i = 0; i < take; i++) {
                    tiers.add(new Taken(parts, name));
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
                for (ItemStack wright : DigitalNetworks.extractWrights(player, level, link, need)) {
                    tiers.add(new Taken(WrightItem.parts(wright), WrightItem.nameOf(wright)));
                }
            }
        }
        int fromDigital = tiers.size() - beforeDigital;
        if (fromDigital > 0) {
            noteUse(ModItems.WRIGHT.get(), fromDigital, 0);
        }
        if (player.getAbilities().instabuild) {
            while (tiers.size() < count) {
                tiers.add(new Taken(Loadout.BASIC, ""));
            }
        }
        return new Pull(tiers, 0);
    }

    private record Taken(Loadout parts, String name) {
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
        boolean blocked = false;
        if (backward) {
            for (int i = 0; i < count && !next.units().isEmpty(); i++) {
                int index = next.units().size() - 1;
                if (!giveUnit(player, next.units().get(index))) {
                    blocked = true;
                    break;
                }
                next = next.removeUnit(index);
                taken++;
            }
        } else if (start >= 0 && start < next.units().size()) {
            if (!giveUnit(player, next.units().get(start))) {
                Messages.actionBar(player, "message.wrights.inv_no_space");
                return swarm;
            }
            next = next.removeUnit(start);
            taken = 1;
        }
        if (blocked && taken == 0) {
            Messages.actionBar(player, "message.wrights.inv_no_space");
        } else if (taken > 0) {
            Messages.actionBar(player, TIP_TAKE, "message.wrights.wrights_removed", taken);
            if (blocked) {
                Messages.actionBar(player, TIP_WARN, "message.wrights.inv_no_space");
            }
        }
        return next;
    }

    private static Swarm fitNext(ServerPlayer player, Swarm swarm, int index, PartSlot slot) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        int tier = swarm.units().get(index).parts().tier(slot);
        if (tier >= WrightTier.APEX) {
            Messages.actionBar(player, "message.wrights.part_max", Component.translatable(slot.fittedKey(tier)));
            return swarm;
        }
        return fitOne(player, swarm, index, slot, tier + 1);
    }

    /** Fits the {@code target} part to one wright, or takes the part out when {@code target} is Basic. */
    private static Swarm fitOne(ServerPlayer player, Swarm swarm, int index, PartSlot slot, int target) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        Loadout parts = swarm.units().get(index).parts();
        int tier = parts.tier(slot);
        if (tier == target) {
            boolean empty = target <= WrightTier.BASIC;
            Messages.actionBar(player, empty ? "message.wrights.part_empty" : "message.wrights.part_same",
                    Component.translatable(empty ? slot.nameKey() : slot.fittedKey(target)));
            return swarm;
        }
        Item need = target > WrightTier.BASIC ? slot.item(target) : null;
        Item back = slot.item(tier);
        if (!canSwapRefund(player, need, back)) {
            Messages.actionBar(player, "message.wrights.inv_no_space");
            return swarm;
        }
        boolean took = false;
        if (target > WrightTier.BASIC) {
            ItemSource.Take taken = takeUpgrade(player, need, 1);
            if (taken.result() == ItemSource.Result.CRAFTING) {
                Messages.actionBar(player, "message.wrights.digital_crafted", Math.max(1, taken.crafted()), new ItemStack(need).getHoverName());
                return swarm;
            }
            if (taken.result() != ItemSource.Result.OK) {
                if (!DigitalNetworks.craftFailureExplained()) {
                    Messages.actionBar(player, "message.wrights.part_need", Component.translatable(slot.fittedKey(target)));
                }
                return swarm;
            }
            took = true;
            // Green = fitted/added, orange = took out. Red stays for warnings.
            Messages.actionBar(player, TIP_ADD, "message.wrights.part_fitted", Component.translatable(slot.fittedKey(target)));
        } else {
            Messages.actionBar(player, TIP_TAKE, "message.wrights.part_removed", Component.translatable(slot.fittedKey(tier)));
        }
        if (!refund(player, slot, tier)) {
            if (took && need != null) {
                // Space was checked first; restore the taken upgrade if refund somehow failed.
                player.getInventory().add(new ItemStack(need));
            }
            Messages.actionBar(player, "message.wrights.inv_no_space");
            return swarm;
        }
        return swarm.withParts(index, parts.with(slot, target));
    }

    /**
     * Swaps one part without messages. Same swarm when the new part could not be taken
     * or the old part would not fit in the inventory.
     */
    private static Swarm swapPart(ServerPlayer player, Swarm swarm, int index, PartSlot slot, int target) {
        Loadout parts = swarm.units().get(index).parts();
        int tier = parts.tier(slot);
        if (tier == target) {
            return swarm;
        }
        Item need = target > WrightTier.BASIC ? slot.item(target) : null;
        Item back = slot.item(tier);
        if (need != null && !afford(player, need, 1)) {
            return swarm;
        }
        if (!canSwapRefund(player, need, back)) {
            return swarm;
        }
        if (need != null) {
            // Stock only — marked bulk crafts are ordered separately. Prefer the cursor stack.
            ItemSource.Take taken = takeUpgrade(player, need, 1, false);
            if (taken.result() != ItemSource.Result.OK) {
                return swarm;
            }
        }
        if (!refund(player, slot, tier)) {
            return swarm;
        }
        return swarm.withParts(index, parts.with(slot, target));
    }

    private static Swarm fitMarked(ServerPlayer player, Swarm swarm, int[] units, PartSlot slot, int target) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            return swarm;
        }
        if (picked.length == 1) {
            return fitOne(player, swarm, picked[0], slot, target);
        }
        Item part = slot.item(target);
        Component label = Component.translatable(slot.fittedKey(target));
        Swarm next = swarm;
        int moved = 0;
        int missing = 0;
        int noSpace = 0;
        for (int index : picked) {
            int tier = next.units().get(index).parts().tier(slot);
            if (tier == target) {
                continue;
            }
            Item back = slot.item(tier);
            if (part != null && !afford(player, part, 1)) {
                missing++;
                continue;
            }
            if (!canSwapRefund(player, part, back)) {
                noSpace++;
                continue;
            }
            Swarm changed = swapPart(player, next, index, slot, target);
            if (changed == next) {
                missing++;
                continue;
            }
            next = changed;
            moved++;
        }
        boolean crafting = missing > 0 && part != null && orderCraft(player, part, missing);
        if (moved == 0) {
            if (noSpace > 0) {
                Messages.actionBar(player, "message.wrights.inv_no_space");
            } else if (missing == 0) {
                Messages.actionBar(player, "message.wrights.marked_same", label);
            } else if (crafting) {
                Messages.actionBar(player, "message.wrights.digital_crafting");
            } else if (!DigitalNetworks.craftFailureExplained()) {
                Messages.actionBar(player, "message.wrights.part_need", label);
            }
            return swarm;
        }
        if (noSpace > 0) {
            Messages.actionBar(player, "message.wrights.marked_no_space", moved, noSpace);
        } else if (missing > 0 && !crafting) {
            Messages.actionBar(player, "message.wrights.marked_short", moved, missing);
        } else {
            Messages.actionBar(player, TIP_ADD, "message.wrights.marked_fitted", moved, label);
        }
        return next;
    }

    /**
     * Fits every marked wright with the best part of each slot that is on hand.
     * When {@code allowCraft} is set, orders digital crafts for upgrades that are not stocked.
     */
    private static Swarm bestParts(ServerPlayer player, Swarm swarm, int[] units, boolean allowCraft) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            return swarm;
        }
        boolean below = false;
        for (int index : picked) {
            below |= !swarm.units().get(index).parts().isMax();
        }
        if (!below) {
            Messages.actionBar(player, "message.wrights.marked_max");
            return swarm;
        }
        Swarm next = swarm;
        int raised = 0;
        boolean noSpace = false;
        for (int index : picked) {
            boolean any = false;
            for (PartSlot slot : PartSlot.values()) {
                int tier = next.units().get(index).parts().tier(slot);
                int goal = bestOnHand(player, slot, tier);
                if (goal <= tier) {
                    continue;
                }
                if (!canSwapRefund(player, slot.item(goal), slot.item(tier))) {
                    noSpace = true;
                    continue;
                }
                Swarm changed = swapPart(player, next, index, slot, goal);
                any |= changed != next;
                next = changed;
            }
            if (any) {
                raised++;
            }
        }
        if (raised > 0) {
            Messages.actionBar(player, TIP_ADD, "message.wrights.marked_raised", raised);
            return next;
        }
        if (noSpace) {
            Messages.actionBar(player, "message.wrights.inv_no_space");
            return swarm;
        }
        if (!allowCraft) {
            Messages.actionBar(player, "message.wrights.marked_raised_none");
            return swarm;
        }
        boolean ordered = false;
        for (PartSlot slot : PartSlot.values()) {
            int[] missing = new int[WrightTier.APEX + 1];
            for (int index : picked) {
                int tier = swarm.units().get(index).parts().tier(slot);
                if (tier >= WrightTier.APEX) {
                    continue;
                }
                int want = WrightTier.APEX;
                for (int step = WrightTier.APEX; step > tier; step--) {
                    if (ItemSource.craftable(player, slot.item(step))) {
                        want = step;
                        break;
                    }
                }
                missing[want]++;
            }
            for (int step = WrightTier.SCOUT; step <= WrightTier.APEX; step++) {
                if (missing[step] > 0 && orderCraft(player, slot.item(step), missing[step])) {
                    ordered = true;
                }
            }
        }
        if (ordered) {
            Messages.actionBar(player, "message.wrights.digital_crafting");
        } else if (!DigitalNetworks.craftFailureExplained()) {
            Messages.actionBar(player, "message.wrights.marked_raised_none");
        }
        return swarm;
    }

    /** Highest tier above {@code tier} whose part is on hand, or {@code tier} when none is. */
    private static int bestOnHand(ServerPlayer player, PartSlot slot, int tier) {
        for (int step = WrightTier.APEX; step > tier; step--) {
            if (afford(player, slot.item(step), 1)) {
                return step;
            }
        }
        return tier;
    }

    private static Swarm stripMarked(ServerPlayer player, Swarm swarm, int[] units) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            Messages.actionBar(player, "message.wrights.no_wrights");
            return swarm;
        }
        List<ItemStack> refunds = new ArrayList<>();
        int withParts = 0;
        for (int index : picked) {
            Loadout parts = swarm.units().get(index).parts();
            if (parts.isBasic()) {
                continue;
            }
            withParts++;
            for (PartSlot slot : PartSlot.values()) {
                Item item = slot.item(parts.tier(slot));
                if (item != null) {
                    refunds.add(new ItemStack(item));
                }
            }
        }
        if (withParts == 0) {
            Messages.actionBar(player, "message.wrights.marked_bare");
            return swarm;
        }
        if (!canFitAll(player, refunds)) {
            Messages.actionBar(player, "message.wrights.inv_no_space");
            return swarm;
        }
        Swarm next = swarm;
        int stripped = 0;
        for (int index : picked) {
            Loadout parts = next.units().get(index).parts();
            if (parts.isBasic()) {
                continue;
            }
            for (PartSlot slot : PartSlot.values()) {
                if (!refund(player, slot, parts.tier(slot))) {
                    Messages.actionBar(player, "message.wrights.inv_no_space");
                    return swarm;
                }
            }
            next = next.withParts(index, Loadout.BASIC);
            stripped++;
        }
        Messages.actionBar(player, TIP_TAKE, "message.wrights.marked_stripped", stripped);
        return next;
    }

    private static boolean refund(ServerPlayer player, PartSlot slot, int tier) {
        Item part = slot.item(tier);
        return part == null || giveStack(player, new ItemStack(part));
    }

    private static Swarm takeMarked(ServerPlayer player, Swarm swarm, int[] units) {
        int[] picked = unique(swarm, units);
        if (picked.length == 0) {
            Messages.actionBar(player, "message.wrights.no_wrights");
            return swarm;
        }
        List<ItemStack> stacks = new ArrayList<>(picked.length);
        for (int index : picked) {
            stacks.add(WrightItem.stack(swarm.units().get(index)));
        }
        if (!canFitAll(player, stacks)) {
            Messages.actionBar(player, "message.wrights.inv_no_space");
            return swarm;
        }
        int[] order = picked.clone();
        java.util.Arrays.sort(order);
        Swarm next = swarm;
        for (int i = order.length - 1; i >= 0; i--) {
            int index = order[i];
            if (!giveUnit(player, next.units().get(index))) {
                Messages.actionBar(player, "message.wrights.inv_no_space");
                return next;
            }
            next = next.removeUnit(index);
        }
        Messages.actionBar(player, TIP_TAKE, "message.wrights.wrights_removed", order.length);
        return next;
    }

    private static int carriedCount(ServerPlayer player, Item item) {
        if (item == null || player.containerMenu == null) {
            return 0;
        }
        ItemStack carried = player.containerMenu.getCarried();
        return !carried.isEmpty() && carried.is(item) ? carried.getCount() : 0;
    }

    /** Bag / chest / digital plus the stack on the cursor. */
    private static boolean afford(ServerPlayer player, Item item, int count) {
        if (count <= 0 || item == null || player.getAbilities().instabuild) {
            return true;
        }
        return carriedCount(player, item) + ItemSource.available(player, item) >= count;
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
        return takeUpgrade(player, item, count, true);
    }

    /**
     * Takes exactly {@code count} upgrade parts for hangar fitting.
     * Cursor stack first (so a picked-up part is consumed once), then inventory / chest / digital.
     * Never takes from both cursor and bag for the same unit when the cursor already covered {@code count}.
     */
    private static ItemSource.Take takeUpgrade(ServerPlayer player, Item item, int count, boolean allowCraft) {
        if (item == null || count <= 0) {
            return new ItemSource.Take(ItemSource.Result.OK, 0, 0);
        }
        int fromCarried = shrinkCarried(player, item, count);
        int left = count - fromCarried;
        if (left <= 0) {
            return new ItemSource.Take(ItemSource.Result.OK, 0, 0);
        }
        ItemSource.Take taken = ItemSource.takeDetailed(player, item, left, allowCraft);
        if (taken.result() == ItemSource.Result.OK) {
            noteUse(item, taken.fromDigital(), 0);
            return taken;
        }
        if (taken.result() == ItemSource.Result.CRAFTING) {
            noteUse(item, 0, taken.crafted());
            // Cursor already paid fromCarried — put those back; craft request covers the rest.
            if (fromCarried > 0) {
                restoreCarried(player, item, fromCarried);
            }
            return taken;
        }
        if (fromCarried > 0) {
            restoreCarried(player, item, fromCarried);
        }
        return taken;
    }

    /** Shrinks the menu cursor stack when it matches {@code item}. */
    private static int shrinkCarried(ServerPlayer player, Item item, int count) {
        if (item == null || count <= 0 || player.containerMenu == null) {
            return 0;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty() || !carried.is(item)) {
            return 0;
        }
        int used = Math.min(count, carried.getCount());
        carried.shrink(used);
        player.containerMenu.setCarried(carried);
        return used;
    }

    /** Puts failed cursor takes back onto the cursor (or inventory if the cursor changed). */
    private static void restoreCarried(ServerPlayer player, Item item, int count) {
        if (item == null || count <= 0 || player.containerMenu == null) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(new ItemStack(item, count));
            return;
        }
        if (carried.is(item)) {
            carried.grow(count);
            player.containerMenu.setCarried(carried);
            return;
        }
        giveStack(player, new ItemStack(item, count));
    }

    /**
     * Puts {@code stack} in the player inventory only. Returns false when it would not fully fit —
     * never places on the cursor or drops on the ground.
     */
    private static boolean giveStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (player.getAbilities().instabuild) {
            player.getInventory().add(stack.copy());
            return true;
        }
        if (!canFit(player, stack)) {
            return false;
        }
        ItemStack copy = stack.copy();
        player.getInventory().add(copy);
        return copy.isEmpty();
    }

    /** True when every stack fits in the player inventory (main + hotbar). */
    private static boolean canFitAll(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty() || player.getAbilities().instabuild) {
            return true;
        }
        NonNullList<ItemStack> sim = copyPlayerItems(player);
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack left = stack.copy();
            mergeInto(sim, left);
            if (!left.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean canFit(ServerPlayer player, ItemStack stack) {
        return canFitAll(player, List.of(stack));
    }

    /**
     * After optionally consuming one {@code need} from the player inventory (digital/chest takes
     * free no inventory space), can the {@code refund} part fully fit?
     */
    private static boolean canSwapRefund(ServerPlayer player, Item need, Item refund) {
        if (refund == null || player.getAbilities().instabuild) {
            return true;
        }
        NonNullList<ItemStack> sim = copyPlayerItems(player);
        if (need != null) {
            removeFrom(sim, need, 1);
        }
        ItemStack left = new ItemStack(refund);
        mergeInto(sim, left);
        return left.isEmpty();
    }

    private static NonNullList<ItemStack> copyPlayerItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        NonNullList<ItemStack> copy = NonNullList.withSize(inventory.items.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            copy.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    private static void mergeInto(NonNullList<ItemStack> items, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            ItemStack have = items.get(i);
            if (have.isEmpty() || !ItemStack.isSameItemSameComponents(have, stack)) {
                continue;
            }
            int room = have.getMaxStackSize() - have.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, stack.getCount());
            have.grow(move);
            stack.shrink(move);
        }
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            if (!items.get(i).isEmpty()) {
                continue;
            }
            int put = Math.min(stack.getMaxStackSize(), stack.getCount());
            items.set(i, stack.split(put));
        }
    }

    private static void removeFrom(NonNullList<ItemStack> items, Item item, int count) {
        int left = count;
        for (int i = 0; i < items.size() && left > 0; i++) {
            ItemStack have = items.get(i);
            if (have.isEmpty() || have.getItem() != item) {
                continue;
            }
            int take = Math.min(left, have.getCount());
            have.shrink(take);
            left -= take;
            if (have.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
        }
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

    private static boolean giveUnit(ServerPlayer player, Swarm.Unit unit) {
        return giveStack(player, WrightItem.stack(unit));
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
        HangarDock.show(player, List.of(Loadout.BASIC));
        return swarm.addUnit();
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
