package com.wrights.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import com.wrights.network.BlueprintPayloads;
import com.wrights.drone.WrightHomes;
import com.wrights.entity.DroneEntity;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

/**
 * Tracks wrights that are flying: which ones are on their way home, and where a lost one was last
 * seen. Also answers "where is my wright" for the wright list and the creative teleport.
 */
public final class WrightFlights {
    private WrightFlights() {
    }

    /** Wrights still flying home. A queued job must not launch a second flyer for the same one. */
    private static final Set<UUID> RETURNING = new HashSet<>();
    /** Last place a wright was seen flying, so a lost one can be highlighted after the entity is gone. */
    private static final Map<UUID, Flyer> FLYERS = new HashMap<>();
    private static int returnClock;

    /** Drops flight bookkeeping so a world opened later in the same session starts clean. */
    public static void reset() {
        RETURNING.clear();
        FLYERS.clear();
        returnClock = 0;
    }

    public static void noteFlyer(UUID unitId, DroneEntity drone) {
        if (unitId == null || drone == null || drone.level().isClientSide) {
            return;
        }
        Flyer flyer = FLYERS.computeIfAbsent(unitId, id -> new Flyer());
        flyer.entityId = drone.getUUID();
        flyer.dimension = drone.level().dimension();
        flyer.pos = drone.blockPosition();
        flyer.returning = drone.returning();
        flyer.seen = returnClock;
        if (drone.returning()) {
            holdReturn(unitId);
        }
    }

    /** Drops a return lock when the flyer has been gone long enough that it is not coming back. */
    static void sweepReturns() {
        returnClock++;
        int now = returnClock;
        RETURNING.removeIf(id -> {
            Flyer flyer = FLYERS.get(id);
            return flyer == null || !flyer.returning || now - flyer.seen > 100;
        });
        FLYERS.entrySet().removeIf(entry -> now - entry.getValue().seen > 200);
    }

    public static List<BlueprintPayloads.WrightSpot> wrightSpots(ServerPlayer player) {
        List<BlueprintPayloads.WrightSpot> spots = new ArrayList<>();
        if (player == null || player.getServer() == null) {
            return spots;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Set<UUID> seen = new HashSet<>();
        int now = returnClock;
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (job.finished()) {
                continue;
            }
            ServerLevel level = player.getServer().getLevel(job.dimension());
            List<UUID> crew = job.crewUnits();
            for (int i = 0; i < crew.size(); i++) {
                UUID unit = crew.get(i);
                if (unit == null || !seen.add(unit)) {
                    continue;
                }
                BuildJob.Hand hand = i < job.hands().size() ? job.hands().get(i) : null;
                spots.add(spotFor(player.getServer(), job, hand, unit, level, now));
            }
        }
        return spots;
    }

    public static void visitWright(ServerPlayer player, UUID unit) {
        if (player == null || unit == null) {
            return;
        }
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            Messages.actionBar(player, "message.wrights.visit_survival");
            return;
        }
        if (!ownsWright(player, unit)) {
            Messages.actionBar(player, "message.wrights.visit_wright_missing");
            return;
        }
        BlueprintPayloads.WrightSpot spot = null;
        for (BlueprintPayloads.WrightSpot found : wrightSpots(player)) {
            if (unit.equals(found.unit())) {
                spot = found;
                break;
            }
        }
        if (spot == null || player.getServer() == null) {
            Messages.actionBar(player, "message.wrights.visit_wright_missing");
            return;
        }
        ServerLevel level = null;
        for (ServerLevel candidate : player.getServer().getAllLevels()) {
            if (candidate.dimension().location().toString().equals(spot.dimension())) {
                level = candidate;
                break;
            }
        }
        if (level == null) {
            Messages.actionBar(player, "message.wrights.visit_wright_missing");
            return;
        }
        BlockPos pos = spot.pos();
        if (pos != null && level.getBlockState(pos).blocksMotion() && !level.getBlockState(pos.above()).blocksMotion()) {
            pos = pos.above();
        }
        if (pos == null) {
            Messages.actionBar(player, "message.wrights.visit_wright_missing");
            return;
        }
        player.teleportTo(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        Messages.actionBar(player, "message.wrights.visited", pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean ownsWright(ServerPlayer player, UUID unit) {
        if (WrightHomes.findUnit(player, unit) != null) {
            return true;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (job.crewUnits().contains(unit)) {
                return true;
            }
        }
        return false;
    }

    private static BlueprintPayloads.WrightSpot spotFor(MinecraftServer server, BuildJob job, BuildJob.Hand hand, UUID unit, ServerLevel level, int now) {
        DroneEntity drone = livingFlyer(server, level, hand, unit, now);
        if (drone != null && drone.level() instanceof ServerLevel) {
            return new BlueprintPayloads.WrightSpot(unit, drone.level().dimension().location().toString(), drone.blockPosition(), drone.returning() ? 1 : 0);
        }
        Flyer flyer = FLYERS.get(unit);
        BlockPos pos = flyer == null ? null : flyer.pos;
        String dimension = job.dimension().location().toString();
        if (flyer != null && flyer.dimension != null) {
            dimension = flyer.dimension.location().toString();
        }
        if (pos == null && hand != null && hand.lastPos != null) {
            pos = hand.lastPos;
        }
        if (pos == null && hand != null && hand.homePort != null) {
            pos = hand.homePort;
        }
        if (pos == null) {
            pos = job.anchor() != null ? job.anchor() : job.min();
        }
        return new BlueprintPayloads.WrightSpot(unit, dimension, pos, 2);
    }

    private static DroneEntity livingFlyer(MinecraftServer server, ServerLevel level, BuildJob.Hand hand, UUID unit, int now) {
        if (level != null && hand != null && hand.entityId != null && level.getEntity(hand.entityId) instanceof DroneEntity drone && drone.isAlive()) {
            return drone;
        }
        Flyer flyer = FLYERS.get(unit);
        if (flyer == null || flyer.entityId == null || flyer.dimension == null || now - flyer.seen > 40) {
            return null;
        }
        ServerLevel flyerLevel = server.getLevel(flyer.dimension);
        if (flyerLevel != null && flyerLevel.getEntity(flyer.entityId) instanceof DroneEntity drone && drone.isAlive()) {
            return drone;
        }
        return null;
    }

    private static final class Flyer {
        private UUID entityId;
        private ResourceKey<Level> dimension;
        private BlockPos pos;
        private boolean returning;
        private int seen;
    }

    public static void holdReturn(UUID unitId) {
        if (unitId != null) {
            RETURNING.add(unitId);
            Flyer flyer = FLYERS.get(unitId);
            if (flyer != null) {
                flyer.returning = true;
                flyer.seen = returnClock;
            }
        }
    }

    public static void releaseReturn(UUID unitId) {
        if (unitId != null) {
            RETURNING.remove(unitId);
        }
    }

    static boolean isReturning(UUID unitId) {
        return unitId != null && RETURNING.contains(unitId);
    }
}
