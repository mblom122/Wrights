package com.wrights.job;

import com.wrights.job.DroneFleet.Owed;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.drone.WrightTier;
import com.wrights.drone.Wrightports;
import com.wrights.drone.WrightHomes;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;
import com.wrights.item.ControllerItem;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

/** Who is on which job: crew slots, quotas, moving units between jobs, resuming parked crews and explaining crew gaps. */
public final class CrewRoster {
    private CrewRoster() {
    }

    public static int droneSlots(ServerPlayer player, BuildJob job) {
        if (!usesDrones(job)) {
            return 0;
        }
        if (!job.crewUnits().isEmpty()) {
            return job.crewUnits().size();
        }
        if (job.hasCrew()) {
            return job.hands().size();
        }
        return JobManager.instant(player) ? 0 : 1;
    }

    public static void bindCrewIds(ServerPlayer player, SchematicLibrary library) {
        Set<UUID> taken = new HashSet<>();
        boolean dirty = false;
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (!usesDrones(job) || job.awaitingDrones()) {
                if (!job.crewUnits().isEmpty()) {
                    job.setCrewUnits(List.of());
                    dirty = true;
                }
                continue;
            }
            ServerLevel jobLevel = player.getServer().getLevel(job.dimension());
            List<UUID> current = new ArrayList<>();
            List<UUID> crew = job.crewUnits();
            for (int i = 0; i < crew.size(); i++) {
                UUID unit = crew.get(i);
                BuildJob.Hand hand = i < job.hands().size() ? job.hands().get(i) : null;
                if (hand != null && hand.sheltering && taken.add(unit)) {
                    current.add(unit);
                    continue;
                }
                BlockPos home = hand == null ? null : hand.homePort;
                if (WrightHomes.stillThere(jobLevel, player, home, unit) && taken.add(unit)) {
                    current.add(unit);
                } else if (WrightHomes.dockGone(jobLevel, player, home, unit) && taken.add(unit)) {
                    current.add(unit);
                }
            }
            int had = assignedCount(job);
            if (current.size() < had) {
                dirty |= job.rememberCrewQuota(had);
            }
            if (current.isEmpty() && job.crewUnits().isEmpty() && !job.queued() && !(job.paused() && job.crewQuota() > 0)) {
                int need = job.hasCrew() ? job.hands().size() : (JobManager.instant(player) ? 0 : 1);
                int jobCap = WrightsConfig.maxWrightsPerJob(job);
                for (WrightHomes.FreeWright free : freeUnits(player, library, job, need)) {
                    if (current.size() >= need || current.size() >= jobCap) {
                        break;
                    }
                    if (taken.add(free.unit().id())) {
                        current.add(free.unit().id());
                    }
                }
            }
            if (current.isEmpty() && had > 0) {
                dirty |= detachCrew(player, job);
                continue;
            }
            int jobCap = WrightsConfig.maxWrightsPerJob(job);
            if (current.size() > jobCap) {
                current.subList(jobCap, current.size()).clear();
            }
            if (job.crewQuota() == 0 && !current.isEmpty()) {
                job.setCrewQuota(current.size());
                dirty = true;
            }
            if (!current.equals(job.crewUnits())) {
                job.setCrewUnits(current);
                dirty = true;
            }
            taken.addAll(current);
            if (job.hasCrew() && current.size() != job.hands().size()) {
                fitCrew(player, library, job, current);
                dirty = true;
            } else if (job.hasCrew() && !current.isEmpty()) {
                for (int i = 0; i < job.hands().size() && i < current.size(); i++) {
                    if (job.hands().get(i).sheltering) {
                        continue;
                    }
                    WrightHomes.FreeWright found = WrightHomes.locate(jobLevel, player, current.get(i), job.min());
                    if (found != null) {
                        job.hands().get(i).homePort = found.homePort();
                    }
                }
            }
        }
        if (dirty) {
            library.setDirty();
        }
    }

    static int assignedCount(BuildJob job) {
        return Math.max(job.crewUnits().size(), job.hasCrew() ? job.hands().size() : 0);
    }

    /** Drops wright ids that left the port, but keeps the job's work split for a later resume. */
    private static boolean detachCrew(ServerPlayer player, BuildJob job) {
        boolean changed = false;
        boolean flying = job.droneId() != null;
        if (!flying) {
            for (BuildJob.Hand hand : job.hands()) {
                if (hand.entityId != null) {
                    flying = true;
                    break;
                }
            }
        }
        if (flying) {
            DroneFleet.discardDrone(player.getServer(), job);
            changed = true;
        }
        if (!job.crewUnits().isEmpty()) {
            job.setCrewUnits(List.of());
            changed = true;
        }
        if (job.queued()) {
            job.setPaused(false);
            job.clearHold();
            return changed;
        }
        if (!job.paused()) {
            job.noteHold(BuildJob.HOLD_NO_WRIGHTS, null);
            job.setPaused(true);
            changed = true;
        }
        return changed;
    }

    /**
     * Jobs card − / +: set how many wrights this job should keep assigned.
     * Raises wait for recruits in range; lowers send surplus home when the count drops below the live crew.
     */
    public static void setJobQuota(ServerPlayer player, UUID id, int wanted) {
        if (!com.wrights.item.ControllerItem.isHeld(player)) {
            Messages.actionBar(player, "message.wrights.need_controller");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        bindCrewIds(player, library);
        BuildJob job = library.job(id).orElse(null);
        if (job == null || !job.owner().equals(player.getUUID()) || !usesDrones(job) || job.finished()) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        int cap = WrightsConfig.maxWrightsPerJob(job);
        int quota = Math.max(0, Math.min(cap, wanted));
        int live = assignedCount(job);
        if (quota == job.crewQuota() && live <= quota) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (live > quota) {
            List<UUID> ids = new ArrayList<>(job.crewUnits());
            while (ids.size() > quota) {
                ids.remove(ids.size() - 1);
            }
            placeCrew(player, library, job, ids);
            job.setCrewQuota(quota);
        } else {
            job.setCrewQuota(quota);
        }
        if (quota > assignedCount(job)) {
            job.setQueued(true);
            job.setPaused(false);
            if (job.hold() == BuildJob.HOLD_NO_WRIGHTS) {
                job.clearHold();
            }
            topUpQueued(player, library, job);
        }
        library.setDirty();
        BlueprintNetwork.syncJobs(player);
    }

    public static void setJobDrones(ServerPlayer player, UUID id, UUID unitId, boolean add) {
        if (!com.wrights.item.ControllerItem.isHeld(player)) {
            Messages.actionBar(player, "message.wrights.need_controller");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        bindCrewIds(player, library);
        BuildJob job = library.job(id).orElse(null);
        if (job == null || !job.owner().equals(player.getUUID()) || !usesDrones(job)) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        Swarm.Unit unit = WrightHomes.findUnit(player, unitId);
        if (add && unit == null) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (add && claimedByOther(player, library, job.id(), unitId)) {
            Messages.actionBar(player, "message.wrights.drones_busy");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (add && com.wrights.drone.ManageLock.blocks(player, unitId)) {
            Messages.actionBar(player, "message.wrights.wright_locked");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (add && !job.crewUnits().contains(unitId) && job.crewUnits().size() >= WrightsConfig.maxWrightsPerJob(job)) {
            Messages.actionBar(player, "message.wrights.job_wright_cap", WrightsConfig.maxWrightsPerJob(job));
            BlueprintNetwork.syncJobs(player);
            return;
        }
        List<UUID> ids = new ArrayList<>(job.crewUnits());
        if (add) {
            if (!ids.contains(unitId)) {
                ids.add(unitId);
            }
        } else {
            ids.remove(unitId);
        }
        if (ids.equals(job.crewUnits()) && (ids.isEmpty() || job.hands().size() == ids.size())) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        placeCrew(player, library, job, ids);
        library.setDirty();
        BlueprintNetwork.syncJobs(player);
    }

    public static void moveDrone(ServerPlayer player, UUID unitId, UUID targetId) {
        if (!com.wrights.item.ControllerItem.isHeld(player)) {
            Messages.actionBar(player, "message.wrights.need_controller");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        bindCrewIds(player, library);
        boolean free = targetId == null || (targetId.getMostSignificantBits() == 0L && targetId.getLeastSignificantBits() == 0L);
        BuildJob target = null;
        if (!free) {
            target = library.job(targetId).orElse(null);
            if (target == null || !target.owner().equals(player.getUUID()) || !usesDrones(target)) {
                BlueprintNetwork.syncJobs(player);
                return;
            }
        }
        if (WrightHomes.findUnit(player, unitId) == null) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (target != null && com.wrights.drone.ManageLock.blocks(player, unitId)) {
            Messages.actionBar(player, "message.wrights.wright_locked");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        BuildJob source = null;
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (usesDrones(job) && !job.awaitingDrones() && job.crewUnits().contains(unitId)) {
                source = job;
                break;
            }
        }
        if (source == null && free) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (source != null && target != null && source.id().equals(target.id())) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (target != null && (source == null || !source.id().equals(target.id()))
                && !target.crewUnits().contains(unitId)
                && target.crewUnits().size() >= WrightsConfig.maxWrightsPerJob(target)) {
            Messages.actionBar(player, "message.wrights.job_wright_cap", WrightsConfig.maxWrightsPerJob(target));
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (source != null) {
            List<UUID> ids = new ArrayList<>(source.crewUnits());
            ids.remove(unitId);
            placeCrew(player, library, source, ids);
        }
        if (target != null) {
            List<UUID> ids = new ArrayList<>(target.crewUnits());
            if (!ids.contains(unitId)) {
                ids.add(unitId);
            }
            placeCrew(player, library, target, ids);
        }
        library.setDirty();
        BlueprintNetwork.syncJobs(player);
    }

    static void placeCrew(ServerPlayer player, SchematicLibrary library, BuildJob job, List<UUID> ids) {
        if (ids.isEmpty()) {
            DroneFleet.sendHome(player.getServer(), player, job);
            job.setCrewQuota(0);
            job.clearAssigned();
            job.noteHold(BuildJob.HOLD_NO_WRIGHTS, null);
            job.setPaused(true);
        } else {
            fitCrew(player, library, job, ids);
            job.setCrewQuota(assignedCount(job));
        }
    }

    private static int placeableBlocks(Schematic schematic, BuildJob job) {
        if (schematic == null) {
            return Integer.MAX_VALUE;
        }
        if (job.clipX() == Integer.MAX_VALUE) {
            return Math.max(0, schematic.blockCount());
        }
        int count = 0;
        for (Schematic.Entry entry : schematic.entries()) {
            if (!job.outsideClip(entry)) {
                count++;
            }
        }
        return count;
    }

    /** Cap crew by useful parallel share (cluster multi-place), not one wright per block. */
    static int crewRoom(Schematic schematic, BuildJob job) {
        // Shape/Exchange stream with no schematic used to pass Integer.MAX_VALUE into usefulCrewFor,
        // which overflowed to "1 block" and ejected the whole crew when Manage tried to add one.
        int blocks;
        if (job.kind() == BuildJob.Kind.SHAPE || schematic == null) {
            blocks = Math.max(1, job.total());
        } else {
            blocks = Math.max(0, placeableBlocks(schematic, job));
        }
        if (blocks <= 0) {
            return 0;
        }
        int sizeX;
        int sizeY;
        int sizeZ;
        if (job.kind() == BuildJob.Kind.SHAPE) {
            sizeX = Math.max(1, job.boxSizeX());
            sizeY = Math.max(1, job.boxSizeY());
            sizeZ = Math.max(1, job.boxSizeZ());
        } else if (schematic != null) {
            sizeX = schematic.sizeX();
            sizeY = schematic.sizeY();
            sizeZ = schematic.sizeZ();
            if (job.clipX() != Integer.MAX_VALUE) {
                sizeX = Math.min(sizeX, job.clipX());
                sizeY = Math.min(sizeY, job.clipY());
                sizeZ = Math.min(sizeZ, job.clipZ());
            }
        } else {
            sizeX = Math.max(1, job.spanX());
            sizeY = Math.max(1, job.spanY());
            sizeZ = Math.max(1, job.spanZ());
        }
        int useful = WrightsConfig.usefulCrewFor(blocks, sizeX, sizeY, sizeZ, WrightTier.NETHERITE);
        return Math.min(useful, WrightsConfig.maxWrightsPerJob(job));
    }

    static int crewRoom(Schematic schematic, BuildJob job, int ops) {
        int blocks;
        if (job.kind() == BuildJob.Kind.SHAPE || schematic == null) {
            blocks = Math.max(1, job.total());
        } else {
            blocks = Math.max(0, placeableBlocks(schematic, job));
        }
        if (blocks <= 0) {
            return 0;
        }
        int sizeX;
        int sizeY;
        int sizeZ;
        if (job.kind() == BuildJob.Kind.SHAPE) {
            sizeX = Math.max(1, job.boxSizeX());
            sizeY = Math.max(1, job.boxSizeY());
            sizeZ = Math.max(1, job.boxSizeZ());
        } else if (schematic != null) {
            sizeX = schematic.sizeX();
            sizeY = schematic.sizeY();
            sizeZ = schematic.sizeZ();
            if (job.clipX() != Integer.MAX_VALUE) {
                sizeX = Math.min(sizeX, job.clipX());
                sizeY = Math.min(sizeY, job.clipY());
                sizeZ = Math.min(sizeZ, job.clipZ());
            }
        } else {
            sizeX = Math.max(1, job.spanX());
            sizeY = Math.max(1, job.spanY());
            sizeZ = Math.max(1, job.spanZ());
        }
        int useful = WrightsConfig.usefulCrewFor(blocks, sizeX, sizeY, sizeZ, ops);
        return Math.min(useful, WrightsConfig.maxWrightsPerJob(job));
    }

    static boolean usesDrones(BuildJob job) {
        return (job.kind() == BuildJob.Kind.PASTE || job.kind() == BuildJob.Kind.SHAPE) && !job.finished();
    }

    static boolean claimedByOther(ServerPlayer player, SchematicLibrary library, UUID jobId, UUID unitId) {
        if (WrightFlights.isReturning(unitId)) {
            return true;
        }
        for (BuildJob other : library.jobsFor(player.getUUID())) {
            if ((jobId != null && other.id().equals(jobId)) || !usesDrones(other) || other.awaitingDrones()) {
                continue;
            }
            if (!other.crewUnits().contains(unitId)) {
                continue;
            }
            // Only wrights actually out on the build (or flying home) count as busy.
            // Docked crew leftovers must show up as available again.
            if (!unitIsOut(player, other, unitId)) {
                continue;
            }
            return true;
        }
        return false;
    }

    static boolean unitIsOut(ServerPlayer player, BuildJob job, UUID unitId) {
        int index = job.crewUnits().indexOf(unitId);
        if (index < 0 || index >= job.hands().size() || player.getServer() == null) {
            return false;
        }
        UUID entityId = job.hands().get(index).entityId;
        if (entityId == null) {
            return false;
        }
        ServerLevel level = player.getServer().getLevel(job.dimension());
        return level != null && level.getEntity(entityId) instanceof DroneEntity drone && drone.isAlive();
    }

    /** A personal port counts while you are near the build. A block port counts while its area meets the build. */
    static boolean coversJob(ServerPlayer player, BuildJob job, BlockPos home) {
        BlockPos[] box = JobManager.jobBox(job);
        if (home == null) {
            return WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
        }
        return WrightHomes.portCoversArea(job.dimension(), home, job.dimension(), box[0], box[1]);
    }

    /**
     * Adds free wrights until the queued count is met.
     * Homes that cannot reach the build are left alone, and ones already reserved from out of range are sent back.
     */
    static void topUpQueued(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (job.paused() || !job.queued()) {
            return;
        }
        int quota = job.crewQuota();
        if (quota <= 0) {
            job.setQueued(false);
            return;
        }
        ServerLevel level = player.getServer().getLevel(job.dimension());
        List<UUID> ids = new ArrayList<>();
        boolean changed = false;
        for (UUID id : job.crewUnits()) {
            BlockPos home = homeOfCrew(job, id);
            if (!coversJob(player, job, home) || !WrightHomes.stillThere(level, player, home, id)) {
                changed = true;
                continue;
            }
            ids.add(id);
        }
        int need = quota - ids.size();
        Owed owed = null;
        if (need > 0 && level != null && job.schematicId() != null) {
            Schematic schematic = library.schematic(job.schematicId()).orElse(null);
            if (schematic != null) {
                owed = DroneFleet.owed(level, schematic, job);
                if (owed.idle() && ids.isEmpty()) {
                    JobManager.finishPaste(player.getServer(), library, player, job);
                    return;
                }
            }
        }
        // Jobs that had any personal wrights (pure or mixed) refill from you after a range dock —
        // not from a wrightport on the build, which looks like a snap onto the site.
        boolean personalOnly = job.crewSource() == 1 || job.crewSource() == 3;
        if (need > 0 && level != null) {
            for (WrightHomes.FreeWright free : freeUnits(player, library, job, Integer.MAX_VALUE)) {
                UUID id = free.unit().id();
                if (personalOnly && !free.personal()) {
                    continue;
                }
                if (ids.contains(id) || !coversJob(player, job, free.homePort())) {
                    continue;
                }
                if (!JobSupply.portSupplyReady(level, player, free.homePort())) {
                    continue;
                }
                if (owed != null && !owed.allows(player, level, job, free.homePort())) {
                    continue;
                }
                ids.add(id);
                changed = true;
                if (ids.size() >= quota) {
                    break;
                }
            }
        }
        if (ids.isEmpty()) {
            if (job.hasCrew() || !job.crewUnits().isEmpty()) {
                DroneFleet.sendHome(player.getServer(), player, job);
                job.clearAssigned();
                job.setQueued(true);
                job.setPaused(false);
                job.clearHold();
                library.setDirty();
            }
            return;
        }
        boolean launched = job.hasCrew()
                && job.hands().size() == ids.size()
                && job.crewUnits().size() == ids.size()
                && job.crewUnits().containsAll(ids);
        if (changed || !launched) {
            fitCrew(player, library, job, ids);
            if (job.queued() && !job.hasCrew()) {
                job.setPaused(false);
                job.clearHold();
                job.setQueued(true);
            }
            library.setDirty();
        }
        if (job.queued() && !job.deferSupplies() && job.hasCrew() && job.crewUnits().size() >= quota) {
            job.setQueued(false);
            library.setDirty();
            BlueprintNetwork.syncJobs(player);
            int count = job.crewUnits().size();
            Component detail = Component.translatable("hud.wrights.wrights_rejoined_detail", count);
            BlueprintNetwork.jobDone(
                    player,
                    Component.translatable("hud.wrights.wrights_rejoined"),
                    Component.literal(JobTitles.jobCardTitle(job, library)),
                    detail,
                    -1);
            JobAnnouncements.noteHistory(player, library, job, JobEventLog.REJOINED, detail.getString());
        }
    }

    static List<WrightHomes.FreeWright> freeUnits(ServerPlayer player, SchematicLibrary library, BuildJob job, int limit) {
        ServerLevel level = player.getServer().getLevel(job.dimension());
        if (level == null) {
            return List.of();
        }
        boolean playerFirst = com.wrights.blueprint.BlueprintSessions.get(player).portPlayerFirst();
        BlockPos[] box = JobManager.jobBox(job);
        boolean near = WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
        return WrightHomes.freeFor(player, level, box[0], box[1], near, playerFirst, id -> !claimedByOther(player, library, job.id(), id), limit);
    }

    private static List<UUID> pickResume(ServerPlayer player, SchematicLibrary library, BuildJob job, int wanted) {
        ServerLevel level = player.getServer().getLevel(job.dimension());
        List<BlockPos> origins = new ArrayList<>();
        boolean fromPlayer = job.hands().isEmpty();
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.homePort == null) {
                fromPlayer = true;
            } else if (!origins.contains(hand.homePort)) {
                origins.add(hand.homePort);
            }
        }
        List<WrightHomes.FreeWright> personal = new ArrayList<>();
        List<WrightHomes.FreeWright> blocks = new ArrayList<>();
        if (level != null) {
            BlockPos[] box = JobManager.jobBox(job);
            boolean near = WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
            for (WrightHomes.FreeWright free : WrightHomes.freeFor(player, level, box[0], box[1], near, true, id -> !claimedByOther(player, library, job.id(), id), Integer.MAX_VALUE)) {
                if (free.personal()) {
                    personal.add(free);
                } else {
                    blocks.add(free);
                }
            }
        }
        List<UUID> ids = new ArrayList<>();
        if (!origins.isEmpty()) {
            takeResume(ids, wanted, blocks, free -> origins.contains(free.homePort()), job.crewUnits());
            if (ids.isEmpty()) {
                takeResume(ids, wanted, personal, free -> true, job.crewUnits());
            }
            if (ids.isEmpty()) {
                takeResume(ids, wanted, blocks, free -> true, job.crewUnits());
            }
        } else {
            takeResume(ids, wanted, personal, free -> true, job.crewUnits());
            if (ids.isEmpty()) {
                takeResume(ids, wanted, blocks, free -> true, job.crewUnits());
            }
        }
        if (fromPlayer && !origins.isEmpty() && ids.isEmpty()) {
            takeResume(ids, wanted, personal, free -> true, job.crewUnits());
        }
        return ids;
    }

    private static void takeResume(List<UUID> ids, int wanted, List<WrightHomes.FreeWright> from, Predicate<WrightHomes.FreeWright> ok, List<UUID> prefer) {
        for (UUID id : prefer) {
            if (ids.size() >= wanted) {
                return;
            }
            for (WrightHomes.FreeWright free : from) {
                if (ok.test(free) && free.unit().id().equals(id) && !ids.contains(id)) {
                    ids.add(id);
                    break;
                }
            }
        }
        for (WrightHomes.FreeWright free : from) {
            if (ids.size() >= wanted) {
                return;
            }
            UUID id = free.unit().id();
            if (ok.test(free) && !ids.contains(id)) {
                ids.add(id);
            }
        }
    }

    static void explainResume(ServerPlayer player, BuildJob job) {
        ServerLevel level = player.getServer().getLevel(job.dimension());
        boolean fromBlock = false;
        BlockPos origin = null;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.homePort != null) {
                fromBlock = true;
                origin = hand.homePort;
                break;
            }
        }
        BlockPos[] box = JobManager.jobBox(job);
        boolean playerNear = WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
        int ports = level == null ? 0 : WrightHomes.coveringPortCount(level, job.min(), player.getUUID());
        if (fromBlock && origin != null && level != null && !level.isLoaded(origin)) {
            Messages.actionBar(player, "message.wrights.paused_chunk");
        } else if (fromBlock && origin != null && !WrightHomes.portCoversArea(job.dimension(), origin, job.dimension(), box[0], box[1]) && !playerNear) {
            tellPortAt(player, level, origin, "message.wrights.resume_port_far", "message.wrights.resume_port_far_named");
        } else if (!fromBlock && !playerNear && ports == 0) {
            Messages.actionBar(player, "message.wrights.resume_player_far");
        } else {
            Messages.actionBar(player, "message.wrights.resume_none");
        }
    }

    private static BlockPos homeOfCrew(BuildJob job, UUID unit) {
        int index = job.crewUnits().indexOf(unit);
        if (index >= 0 && index < job.hands().size()) {
            return job.hands().get(index).homePort;
        }
        return null;
    }

    /** Wrights near the player that are not out on a job. Home wrights waiting on items count as free. */
    public static int freeWrightCount(ServerPlayer player) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        return WrightHomes.countUnits(player, id -> !claimedByOther(player, library, null, id));
    }

    /** Wrights in the worn port and in nearby placed wrightports, including ones already out. */
    public static int storedWrightCount(ServerPlayer player) {
        return WrightHomes.countUnits(player, id -> true);
    }

    private static void fitCrew(ServerPlayer player, SchematicLibrary library, BuildJob job, List<UUID> ids) {
        List<Integer> speeds = new ArrayList<>();
        List<Integer> powers = new ArrayList<>();
        List<UUID> kept = new ArrayList<>();
        List<BlockPos> homes = new ArrayList<>();
        int bestOps = WrightTier.BASE;
        ServerLevel jobLevel = player.getServer().getLevel(job.dimension());
        for (UUID unitId : ids) {
            DroneFleet.releaseHomeWaiter(player, library, unitId, job.id());
            WrightHomes.FreeWright found = WrightHomes.locate(jobLevel, player, unitId, job.min());
            if (found == null) {
                continue;
            }
            speeds.add(WrightTier.clamp(found.unit().speed()));
            int power = WrightTier.clamp(found.unit().power());
            powers.add(power);
            bestOps = Math.max(bestOps, power);
            kept.add(unitId);
            homes.add(found.homePort());
        }
        Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
        int room = kept.isEmpty() ? 0 : crewRoom(schematic, job, bestOps);
        // Never eject wrights already on the job when Manage adds one — only cap growth.
        int already = 0;
        for (UUID unitId : kept) {
            if (job.crewUnits().contains(unitId)) {
                already++;
            }
        }
        int limit = Math.max(room, already);
        if (kept.size() > limit) {
            if (kept.size() > room) {
                Messages.actionBar(player, "message.wrights.drone_block_cap", Math.max(0, room));
            }
            while (kept.size() > limit) {
                kept.remove(kept.size() - 1);
                speeds.remove(speeds.size() - 1);
                if (!powers.isEmpty()) {
                    powers.remove(powers.size() - 1);
                }
                if (!homes.isEmpty()) {
                    homes.remove(homes.size() - 1);
                }
            }
        }
        if (kept.isEmpty()) {
            DroneFleet.discardDrone(player.getServer(), job);
            boolean stayQueued = job.queued();
            job.clearAssigned();
            if (stayQueued) {
                job.setQueued(true);
                job.setPaused(false);
                job.clearHold();
                return;
            }
            job.noteHold(BuildJob.HOLD_NO_WRIGHTS, null);
            job.setPaused(true);
            return;
        }
        if (!job.hasCrew() && job.droneId() != null) {
            DroneFleet.discardId(player.getServer().getLevel(job.dimension()), job.droneId());
            job.setDroneId(null);
        }
        List<UUID> dropped = new ArrayList<>();
        if (!job.hasCrew() || schematic == null) {
            int sizeX = Math.max(1, job.spanX());
            int sizeY = Math.max(1, job.spanY());
            int sizeZ = Math.max(1, job.spanZ());
            if (schematic != null) {
                sizeX = schematic.sizeX();
                sizeY = schematic.sizeY();
                sizeZ = schematic.sizeZ();
                if (job.clipX() != Integer.MAX_VALUE) {
                    sizeX = Math.min(sizeX, job.clipX());
                    sizeY = Math.min(sizeY, job.clipY());
                    sizeZ = Math.min(sizeZ, job.clipZ());
                }
            }
            int carried = job.hasCrew() ? 0 : job.cursor();
            if (schematic == null) {
                dropped = job.resizeCrew(speeds, sizeX, sizeY, sizeZ, job.hasCrew() || job.cursor() > 0);
            } else {
                job.setCrew(speeds, sizeX, sizeY, sizeZ);
                if (carried > 0) {
                    for (BuildJob.Hand hand : job.hands()) {
                        hand.cursor = carried;
                    }
                }
                HandSlices.evenOut(job, schematic);
            }
        } else {
            int before = job.hands().size();
            HandSlices.ensureSlices(job, true);
            while (job.hands().size() > speeds.size()) {
                BuildJob.Hand removed = job.hands().remove(job.hands().size() - 1);
                if (removed.entityId != null) {
                    dropped.add(removed.entityId);
                }
                if (!job.hands().isEmpty()) {
                    job.hands().get(0).slices.addAll(removed.slices);
                }
            }
            while (job.hands().size() < speeds.size()) {
                BuildJob.Hand extra = new BuildJob.Hand();
                extra.idle = true;
                job.hands().add(extra);
            }
            for (int i = 0; i < job.hands().size(); i++) {
                BuildJob.Hand hand = job.hands().get(i);
                hand.lane = i;
                hand.lanes = job.hands().size();
                hand.speed = WrightTier.clamp(speeds.get(i));
                if (i < powers.size()) {
                    hand.ops = powers.get(i);
                }
            }
            if (job.hands().size() != before) {
                HandSlices.evenOut(job, schematic);
            }
        }
        ServerLevel level = player.getServer().getLevel(job.dimension());
        int slot = 0;
        for (UUID droppedId : dropped) {
            DroneFleet.recall(level, player, droppedId, slot++, dropped.size());
        }
        job.setCrewUnits(kept);
        for (int i = 0; i < job.hands().size() && i < homes.size(); i++) {
            job.hands().get(i).homePort = homes.get(i);
            if (i < powers.size()) {
                job.hands().get(i).ops = powers.get(i);
            }
        }
    }

    private static boolean missingDrones(ServerPlayer player, BuildJob job) {
        if (JobManager.instant(player) || job.kind() != BuildJob.Kind.PASTE) {
            return false;
        }
        if (crewStillThere(player, job)) {
            return false;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        return job.awaitingDrones() || freeUnits(player, library, job, 1).isEmpty();
    }

    private static boolean crewStillThere(ServerPlayer player, BuildJob job) {
        if (job.crewUnits().isEmpty()) {
            return false;
        }
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.sheltering) {
                return true;
            }
        }
        ServerLevel level = player.getServer().getLevel(job.dimension());
        for (UUID unit : job.crewUnits()) {
            if (WrightHomes.stillThere(level, player, homeOfCrew(job, unit), unit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the crew back to the size it had when the personal wrightport was taken off.
     * Uses wrights that are still in the port, then fills the rest from whatever is free.
     */
    static boolean restorePausedCrew(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (JobManager.instant(player) || job.kind() != BuildJob.Kind.PASTE || job.finished()) {
            return true;
        }
        int quota = job.crewQuota();
        if (quota <= 0) {
            return !job.awaitingDrones() && (job.hasCrew() || !job.crewUnits().isEmpty());
        }
        Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
        int room = crewRoom(schematic, job);
        if (room <= 0) {
            return false;
        }
        int wanted = Math.min(quota, room);
        List<UUID> ids = pickResume(player, library, job, wanted);
        if (ids.isEmpty()) {
            return false;
        }
        if (!job.awaitingDrones() && job.hasCrew() && ids.size() == job.hands().size() && ids.size() == job.crewUnits().size() && ids.containsAll(job.crewUnits())) {
            return true;
        }
        boolean fresh = !job.hasCrew();
        fitCrew(player, library, job, ids);
        if (fresh && job.hasCrew()) {
            for (BuildJob.Hand hand : job.hands()) {
                hand.cursor = 0;
                hand.rescan = true;
                for (BuildJob.Slice slice : hand.slices) {
                    slice.cursor = 0;
                }
            }
        }
        return job.hasCrew() && !job.awaitingDrones();
    }

    static boolean holdWithoutDrones(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (job.queued() || !missingDrones(player, job)) {
            return false;
        }
        boolean dirty = job.rememberCrewQuota(assignedCount(job));
        if (!job.paused()) {
            job.noteHold(BuildJob.HOLD_NO_WRIGHTS, null);
            job.setPaused(true);
            dirty = true;
            tellNoCrew(player, job, true);
            if (crewGap(player, job) != CrewGap.NONE_AVAILABLE) {
                glowClosestPort(player, player.getServer().getLevel(job.dimension()), job.min());
            }
            BlueprintNetwork.syncJobs(player);
        }
        if (dirty) {
            library.setDirty();
        }
        return true;
    }

    /** Why a build cannot take a wright right now. Queue mode never asks. */
    enum CrewGap {
        OUT_OF_RANGE,
        NONE_AVAILABLE,
        PORT_EMPTY
    }

    static void tellNoCrew(ServerPlayer player, BuildJob job, boolean paused) {
        if (crewGap(player, job) == CrewGap.PORT_EMPTY) {
            ServerLevel level = player.getServer().getLevel(job.dimension());
            tellEmptyPort(player, level, job.min(), paused);
            return;
        }
        Messages.actionBar(player, noCrewMessage(player, job, paused));
    }

    private static void tellEmptyPort(ServerPlayer player, ServerLevel level, BlockPos site, boolean paused) {
        BlockPos covered = level == null || site == null ? null : WrightHomes.closestPort(level, site, player.getUUID(), true);
        String name = portLabel(player, level, covered);
        if (name.isBlank()) {
            Messages.actionBar(player, paused ? "message.wrights.paused_port_empty" : "message.wrights.port_empty");
        } else {
            Messages.actionBar(player, paused ? "message.wrights.paused_port_empty_named" : "message.wrights.port_empty_named", name);
        }
    }

    static void tellPortAt(ServerPlayer player, ServerLevel level, BlockPos pos, String plainKey, String namedKey) {
        String name = portLabel(player, level, pos);
        if (name.isBlank() || pos == null) {
            Messages.actionBar(player, plainKey, pos == null ? 0 : pos.getX(), pos == null ? 0 : pos.getY(), pos == null ? 0 : pos.getZ());
        } else {
            Messages.actionBar(player, namedKey, name, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private static String portLabel(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return "";
        }
        if (level != null && level.getBlockEntity(pos) instanceof WrightportBlockEntity port && !port.label().isBlank()) {
            return port.label();
        }
        if (player.getServer() == null || level == null) {
            return "";
        }
        return com.wrights.drone.WrightLedger.get(player.getServer()).label(level.dimension().location().toString(), pos);
    }

    private static String noCrewMessage(ServerPlayer player, BuildJob job, boolean paused) {
        return switch (crewGap(player, job)) {
            case NONE_AVAILABLE -> paused ? "message.wrights.paused_none_available" : "message.wrights.none_available";
            case PORT_EMPTY -> paused ? "message.wrights.paused_port_empty" : "message.wrights.port_empty";
            case OUT_OF_RANGE -> paused ? "message.wrights.paused_none_in_range" : "message.wrights.none_in_range";
        };
    }

    /** A wright exists that can reach this site, ignoring jobs a paste is about to replace. */
    static boolean crewReady(ServerPlayer player, ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos site) {
        ServerLevel level = player.getServer().getLevel(dimension);
        WrightHomes.Reach reach = WrightHomes.reach(player, level, site, id -> true);
        int owned = (reach.playerNear() ? reach.personal() : 0) + reach.portWrights();
        if (owned > 0) {
            return true;
        }
        if (reach.ports() > 0 && reach.personal() == 0) {
            tellEmptyPort(player, level, site, false);
        } else {
            Messages.actionBar(player, "message.wrights.none_in_range");
        }
        glowClosestPort(player, level, site);
        return false;
    }

    static CrewGap crewGap(ServerPlayer player, BuildJob job) {
        ServerLevel level = player.getServer().getLevel(job.dimension());
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BlockPos[] box = JobManager.jobBox(job);
        boolean near = WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
        WrightHomes.Reach reach = WrightHomes.reach(player, level, box[0], box[1], near, id -> !claimedByOther(player, library, job.id(), id));
        int owned = (near ? reach.personal() : 0) + reach.portWrights();
        int free = (near ? reach.personalFree() : 0) + reach.portFree();
        if (owned > 0 && free == 0) {
            return CrewGap.NONE_AVAILABLE;
        }
        if (reach.ports() > 0 && reach.portWrights() == 0 && reach.personal() == 0) {
            return CrewGap.PORT_EMPTY;
        }
        return CrewGap.OUT_OF_RANGE;
    }

    /** Points at the nearest wrightport for ten seconds when a build cannot take one. */
    static void glowClosestPort(ServerPlayer player, ServerLevel level, BlockPos site) {
        if (level == null || site == null) {
            return;
        }
        BlockPos covered = WrightHomes.closestPort(level, site, player.getUUID(), true);
        if (covered != null) {
            PacketDistributor.sendToPlayer(player, new com.wrights.network.BlueprintPayloads.PortGlow(
                    level.dimension().location().toString(), covered, true, site));
            return;
        }
        BlockPos near = WrightHomes.closestPort(level, site, player.getUUID(), false);
        if (near == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new com.wrights.network.BlueprintPayloads.PortGlow(
                level.dimension().location().toString(), near, false, BlockPos.ZERO));
    }

    static boolean assignCrew(ServerPlayer player, BuildJob job, Schematic schematic) {
        return assignCrew(player, job, schematic, 0);
    }

    static boolean assignCrew(ServerPlayer player, BuildJob job, Schematic schematic, int requested) {
        int sizeX = schematic.sizeX();
        int sizeY = schematic.sizeY();
        int sizeZ = schematic.sizeZ();
        if (job.clipX() != Integer.MAX_VALUE) {
            sizeX = Math.min(sizeX, job.clipX());
            sizeY = Math.min(sizeY, job.clipY());
            sizeZ = Math.min(sizeZ, job.clipZ());
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        bindCrewIds(player, library);
        int blocks = placeableBlocks(schematic, job);
        if (blocks <= 0) {
            Messages.actionBar(player, "message.wrights.drone_block_cap", 0);
            return false;
        }
        int room = crewRoom(schematic, job, WrightHomes.bestOps(player, id -> !claimedByOther(player, library, job.id(), id)));
        int wanted = requested > 0 ? Math.min(requested, room) : room;
        ServerLevel level = player.getServer().getLevel(job.dimension());
        List<WrightHomes.FreeWright> units = freeUnits(player, library, job, wanted);
        if (level != null) {
            units = units.stream()
                    .filter(unit -> JobSupply.portSupplyReady(level, player, unit.homePort()))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }
        if (units.isEmpty()) {
            if (level != null && !freeUnits(player, library, job, wanted).isEmpty()) {
                Messages.actionBar(player, "message.wrights.port_need_supply");
            } else {
                tellNoCrew(player, job, false);
                if (crewGap(player, job) != CrewGap.NONE_AVAILABLE) {
                    glowClosestPort(player, level, job.min());
                }
            }
            return false;
        }
        List<Integer> speeds = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (WrightHomes.FreeWright unit : units) {
            DroneFleet.releaseHomeWaiter(player, library, unit.unit().id(), job.id());
            speeds.add(WrightTier.clamp(unit.unit().speed()));
            ids.add(unit.unit().id());
        }
        job.setCrew(speeds, sizeX, sizeY, sizeZ);
        job.setCrewUnits(ids);
        for (int i = 0; i < job.hands().size() && i < units.size(); i++) {
            job.hands().get(i).homePort = units.get(i).homePort();
            job.hands().get(i).ops = WrightTier.clamp(units.get(i).unit().power());
        }
        job.setCrewQuota(ids.size());
        HandSlices.evenOut(job, schematic);
        return job.hasCrew();
    }
}
