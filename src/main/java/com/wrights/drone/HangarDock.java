package com.wrights.drone;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;
import com.wrights.network.BlueprintPayloads;

/**
 * Spawns short-lived fly-in wrights when Manage adds units to a personal or placed port.
 * Job depart / return flight is unchanged.
 */
public final class HangarDock {
    private static final Map<UUID, Set<UUID>> PENDING = new HashMap<>();

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
        Set<UUID> pending = PENDING.computeIfAbsent(player.getUUID(), id -> new LinkedHashSet<>());
        int count = tiers.size();
        for (int i = 0; i < count; i++) {
            DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
            drone.setTier(Math.max(1, tiers.get(i)));
            drone.dockIn(player, port, i, count);
            level.addFreshEntity(drone);
            pending.add(drone.getUUID());
        }
        sync(player, pending.size());
    }

    /** A manage-add fly-in finished (or was cancelled). Unlocks Manage when none remain. */
    public static void arrived(ServerLevel level, UUID owner, UUID drone) {
        if (level == null || owner == null || drone == null) {
            return;
        }
        Set<UUID> pending = PENDING.get(owner);
        if (pending == null || !pending.remove(drone)) {
            return;
        }
        int left = pending.size();
        if (left == 0) {
            PENDING.remove(owner);
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            sync(player, left);
        }
    }

    private static void sync(ServerPlayer player, int remaining) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DockWait(Math.max(0, remaining)));
    }
}
