package com.wrights.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.item.WrightItem;
import com.wrights.menu.WrightportMenu;
import com.wrights.util.Messages;

public final class WrightportOps {
    private WrightportOps() {
    }

    public static void apply(ServerPlayer player, WrightportMenu menu, int action, int index) {
        if (!(player.level() instanceof Level level)) {
            return;
        }
        if (!(level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return;
        }
        if (!(player.containerMenu instanceof WrightportMenu open) || !open.pos().equals(menu.pos())) {
            return;
        }
        Swarm swarm = port.swarm();
        if (HangarOps.targetsUnit(action) && HangarOps.awayUnit(player, swarm, index)) {
            Messages.actionBar(player, 0xFFFF5555, "message.wrights.wright_away");
            return;
        }
        if (port.owner() == null) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        CraftDeliveries.push(CraftDeliveries.Hangar.placed(port.owner(), level.dimension(), menu.pos()));
        Swarm next;
        try {
            next = HangarOps.editOwned(player, port.owner(), swarm, action, index);
        } finally {
            CraftDeliveries.pop();
        }
        if (next == swarm) {
            return;
        }
        port.setSwarm(next);
        open.broadcastChanges();
    }

    public static void rename(ServerPlayer player, WrightportMenu menu, int index, String name) {
        if (!(player.level() instanceof Level level)) {
            return;
        }
        if (!(level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return;
        }
        if (!(player.containerMenu instanceof WrightportMenu open) || !open.pos().equals(menu.pos())) {
            return;
        }
        if (port.owner() == null) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        } else if (!port.owner().equals(player.getUUID())) {
            return;
        }
        Swarm next = HangarOps.rename(player, port.swarm(), index, name);
        if (next == port.swarm()) {
            return;
        }
        port.setSwarm(next);
        open.broadcastChanges();
    }

    /**
     * Shift-click a wright item from the hangar player inventory into the first free bay slot.
     * Shrinks {@code stack} by one on success.
     */
    public static boolean quickDepositWright(ServerPlayer player, WrightportMenu menu, ItemStack stack) {
        if (!(stack.getItem() instanceof WrightItem) || stack.isEmpty()) {
            return false;
        }
        if (!(player.level() instanceof Level level)) {
            return false;
        }
        if (!(level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return false;
        }
        if (!(player.containerMenu instanceof WrightportMenu open) || !open.pos().equals(menu.pos())) {
            return false;
        }
        Swarm swarm = port.swarm();
        if (swarm.units().size() >= Swarm.capacity()) {
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return false;
        }
        if (port.owner() == null) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        java.util.UUID owner = port.owner() == null ? player.getUUID() : port.owner();
        int capRoom = WrightLedger.room(player.serverLevel().getServer(), owner);
        if (capRoom <= 0) {
            Messages.actionBar(player, "message.wrights.wright_cap", WrightsConfig.maxWrightsOwned());
            return false;
        }
        Loadout parts = WrightItem.parts(stack);
        String name = WrightItem.nameOf(stack);
        stack.shrink(1);
        Swarm next = swarm.addUnit(parts, name);
        HangarDock.show(player,
                CraftDeliveries.Hangar.placed(owner, player.level().dimension(), menu.pos()),
                java.util.List.of(parts));
        port.setSwarm(next);
        if (capRoom <= 1) {
            Messages.actionBar(player, "message.wrights.wright_cap_added", 1, WrightsConfig.maxWrightsOwned());
        } else {
            Messages.actionBar(player, "message.wrights.wrights_added", 1);
        }
        open.broadcastChanges();
        return true;
    }

    public static void batch(ServerPlayer player, WrightportMenu menu, int action, int value, int[] units) {
        if (!(player.level() instanceof Level level)) {
            return;
        }
        if (!(level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return;
        }
        if (!(player.containerMenu instanceof WrightportMenu open) || !open.pos().equals(menu.pos())) {
            return;
        }
        Swarm swarm = port.swarm();
        int[] home = HangarOps.isAdd(action) ? units : HangarOps.homeUnits(player, swarm, units);
        if (!HangarOps.isAdd(action) && units != null && units.length > 0 && home.length == 0) {
            Messages.actionBar(player, 0xFFFF5555, "message.wrights.wright_away");
            return;
        }
        if (port.owner() == null && HangarOps.isAdd(action)) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        CraftDeliveries.push(CraftDeliveries.Hangar.placed(
                port.owner() == null ? player.getUUID() : port.owner(), level.dimension(), menu.pos()));
        Swarm next;
        try {
            next = port.owner() == null
                    ? HangarOps.applyBatch(player, swarm, action, value, home)
                    : HangarOps.applyBatchOwned(player, port.owner(), swarm, action, value, home);
        } finally {
            CraftDeliveries.pop();
        }
        if (next == swarm) {
            return;
        }
        port.setSwarm(next);
        open.broadcastChanges();
    }
}
