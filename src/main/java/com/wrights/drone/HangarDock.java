package com.wrights.drone;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;
import com.wrights.network.BlueprintPayloads;

/**
 * Spawns short-lived fly-in wrights when a hangar adds units to a personal or placed port.
 * After the last one docks, waits one second, then reopens that hangar.
 */
public final class HangarDock {
    /** One second after the last fly-in finishes before the hangar UI comes back. */
    private static final int REOPEN_DELAY_TICKS = 20;
    private static final Map<UUID, Set<UUID>> PENDING = new HashMap<>();
    private static final Map<UUID, CraftDeliveries.Hangar> TARGETS = new HashMap<>();
    /** Player → hangar reopen at server game time. */
    private static final Map<UUID, Reopen> REOPEN = new HashMap<>();

    private HangarDock() {
    }

    private record Reopen(CraftDeliveries.Hangar hangar, long atGameTime) {
    }

    /** Forgets in-flight fly-ins when the server stops. */
    public static void reset() {
        PENDING.clear();
        TARGETS.clear();
        REOPEN.clear();
    }

    /** Uses the hangar currently pushed on {@link CraftDeliveries}. */
    public static void show(ServerPlayer player, List<Loadout> tiers) {
        show(player, CraftDeliveries.active(), tiers);
    }

    public static void show(ServerPlayer player, @Nullable CraftDeliveries.Hangar hangar, List<Loadout> tiers) {
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
            @Nullable BlockPos port, List<Loadout> tiers) {
        REOPEN.remove(player.getUUID());
        TARGETS.put(player.getUUID(), hangar);
        Set<UUID> pending = PENDING.computeIfAbsent(player.getUUID(), id -> new LinkedHashSet<>());
        int count = tiers.size();
        for (int i = 0; i < count; i++) {
            DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
            drone.setParts(tiers.get(i));
            drone.dockIn(player, port, i, count);
            level.addFreshEntity(drone);
            pending.add(drone.getUUID());
        }
        sync(player, pending.size());
    }

    /** A hangar-add fly-in finished (or was cancelled). Reopens the hangar when none remain. */
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
            REOPEN.remove(owner);
        }
    }

    private static void sync(ServerPlayer player, int remaining) {
        CraftDeliveries.Hangar hangar = TARGETS.get(player.getUUID());
        int left = Math.max(0, remaining);
        PacketDistributor.sendToPlayer(player, encode(left, hangar));
        if (left > 0) {
            return;
        }
        // Last wright is gone — clear dock-wait on the client now, reopen hangar one second later.
        TARGETS.remove(player.getUUID());
        if (hangar == null || player.getServer() == null) {
            REOPEN.remove(player.getUUID());
            return;
        }
        long at = player.level().getGameTime() + REOPEN_DELAY_TICKS;
        REOPEN.put(player.getUUID(), new Reopen(hangar, at));
    }

    /** Called each server tick — opens the hangar one second after the last dock-in. */
    public static void tick(MinecraftServer server) {
        if (server == null || REOPEN.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Reopen>> it = REOPEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Reopen> entry = it.next();
            if (now < entry.getValue().atGameTime()) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                continue;
            }
            reopen(player, entry.getValue().hangar());
        }
    }

    private static void reopen(ServerPlayer player, @Nullable CraftDeliveries.Hangar hangar) {
        if (hangar == null) {
            return;
        }
        if (hangar.placed()) {
            if (player.containerMenu instanceof com.wrights.menu.WrightportMenu open
                    && hangar.pos() != null
                    && open.pos().equals(hangar.pos())) {
                return;
            }
            reopenPlaced(player, hangar);
            return;
        }
        if (player.containerMenu instanceof com.wrights.menu.PersonalHangarMenu open
                && open.kind() == hangar.kind()
                && open.index() == hangar.index()) {
            return;
        }
        reopenPersonal(player, hangar);
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

    private static void reopenPersonal(ServerPlayer player, CraftDeliveries.Hangar hangar) {
        if (player == null || hangar == null || hangar.placed() || !player.isAlive()) {
            return;
        }
        PersonalHangars.open(player, new WrightportPlaces.Spot(hangar.kind(), hangar.index()));
    }
}
