package com.wrights.drone;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;
import com.wrights.network.BlueprintPayloads;

/**
 * Spawns short-lived fly-in wrights when Manage adds units to a personal or placed port.
 * Job depart / return flight is unchanged.
 */
public final class HangarDock {
    private static final int REOPEN_DELAY_TICKS = 20;
    private static final Map<UUID, Set<UUID>> PENDING = new HashMap<>();
    private static final Map<UUID, CraftDeliveries.Hangar> TARGETS = new HashMap<>();

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
            spawn(player, level, hangar, hangar.pos(), tiers);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        spawn(player, level, hangar, null, tiers);
    }

    private static void spawn(ServerPlayer player, ServerLevel level, CraftDeliveries.Hangar hangar,
            @Nullable BlockPos port, List<Integer> tiers) {
        TARGETS.put(player.getUUID(), hangar);
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

    /** A manage-add fly-in finished (or was cancelled). Reopens Manage when none remain. */
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
        } else if (left == 0) {
            TARGETS.remove(owner);
        }
    }

    private static void sync(ServerPlayer player, int remaining) {
        CraftDeliveries.Hangar hangar = TARGETS.get(player.getUUID());
        int left = Math.max(0, remaining);
        if (left > 0) {
            PacketDistributor.sendToPlayer(player, encode(left, hangar));
            return;
        }
        TARGETS.remove(player.getUUID());
        CraftDeliveries.Hangar target = hangar;
        MinecraftServer server = player.getServer();
        // Extra second after the last physical dock so the lid can settle before Manage reopens.
        server.tell(new TickTask(server.getTickCount() + REOPEN_DELAY_TICKS, () -> finishWait(player, target)));
    }

    private static void finishWait(ServerPlayer player, @Nullable CraftDeliveries.Hangar hangar) {
        if (player == null || !player.isAlive()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, encode(0, hangar));
        if (hangar != null && hangar.placed()) {
            reopenPlaced(player, hangar);
        }
    }

    private static BlueprintPayloads.DockWait encode(int remaining, @Nullable CraftDeliveries.Hangar hangar) {
        if (hangar != null && hangar.placed() && hangar.pos() != null && hangar.dimension() != null) {
            return new BlueprintPayloads.DockWait(remaining, 2, -1, -1,
                    hangar.dimension().location().toString(), hangar.pos());
        }
        if (hangar != null && !hangar.placed()) {
            return new BlueprintPayloads.DockWait(remaining, 1, hangar.kind(), hangar.index(), "", BlockPos.ZERO);
        }
        return new BlueprintPayloads.DockWait(remaining, 0, -1, -1, "", BlockPos.ZERO);
    }

    private static void reopenPlaced(ServerPlayer player, CraftDeliveries.Hangar hangar) {
        if (player == null || hangar == null || hangar.pos() == null || hangar.dimension() == null || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.getServer().getLevel(hangar.dimension());
        if (level == null || !(level.getBlockEntity(hangar.pos()) instanceof WrightportBlockEntity port)) {
            return;
        }
        player.openMenu(port, buffer -> buffer.writeBlockPos(hangar.pos()));
    }
}
