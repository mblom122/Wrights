package com.wrights.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.wrights.block.WrightportBlockEntity;
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
            Messages.actionBar(player, "message.wrights.wright_away");
            return;
        }
        if (port.owner() == null) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        Swarm next = HangarOps.editOwned(player, port.owner(), swarm, action, index);
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
        int[] home = action == HangarOps.ADD_COUNT ? units : HangarOps.homeUnits(player, swarm, units);
        if (action != HangarOps.ADD_COUNT && units != null && units.length > 0 && home.length == 0) {
            Messages.actionBar(player, "message.wrights.wright_away");
            return;
        }
        if (port.owner() == null && action == HangarOps.ADD_COUNT) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        Swarm next = port.owner() == null
                ? HangarOps.applyBatch(player, swarm, action, value, home)
                : HangarOps.applyBatchOwned(player, port.owner(), swarm, action, value, home);
        if (next == swarm) {
            return;
        }
        port.setSwarm(next);
        open.broadcastChanges();
    }
}
