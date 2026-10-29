package com.wrights.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.menu.WrightportMenu;

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
        Swarm next = HangarOps.edit(player, swarm, action, index);
        if (next == swarm) {
            return;
        }
        port.setSwarm(next);
        open.broadcastChanges();
    }
}
