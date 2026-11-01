package com.wrights.drone;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;

/**
 * Spawns short-lived fly-in wrights when Manage adds units to a personal or placed port.
 * Job depart / return flight is unchanged.
 */
public final class HangarDock {
    private HangarDock() {
    }

    /** Uses the hangar currently pushed on {@link CraftDeliveries}. */
    public static void show(ServerPlayer player, List<Integer> tiers) {
        show(player, CraftDeliveries.active(), tiers);
    }

    public static void show(ServerPlayer player, @Nullable CraftDeliveries.Hangar hangar, List<Integer> tiers) {
        if (player == null || hangar == null || tiers == null || tiers.isEmpty()) {
            return;
        }
        if (hangar.placed()) {
            ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(hangar.dimension());
            if (level == null || hangar.pos() == null || !level.isLoaded(hangar.pos())) {
                return;
            }
            spawn(player, level, hangar.pos(), tiers);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        spawn(player, level, null, tiers);
    }

    private static void spawn(ServerPlayer player, ServerLevel level, @Nullable BlockPos port, List<Integer> tiers) {
        int count = tiers.size();
        for (int i = 0; i < count; i++) {
            DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
            drone.setTier(Math.max(1, tiers.get(i)));
            drone.dockIn(player, port, i, count);
            level.addFreshEntity(drone);
        }
    }
}
