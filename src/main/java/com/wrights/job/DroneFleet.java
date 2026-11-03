package com.wrights.job;

import com.wrights.job.WorkPicker.Falling;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.DockRecovery;
import com.wrights.drone.WrightTier;
import com.wrights.drone.WrightportPlaces;
import com.wrights.drone.WrightHomes;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;
import com.wrights.item.ControllerData;
import com.wrights.item.ControllerItem;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.BlockFilters;

/** Wright flights for a job: launching and docking, retiring and lending, recalling and discarding, and what each crew is owed. */
public final class DroneFleet {
    private DroneFleet() {
    }

    static DroneEntity ensureDrone(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos workPos) {
        if (job.droneId() != null) {
            Entity existing = level.getEntity(job.droneId());
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                paint(drone, job);
                bindUnit(job, drone, 0);
                return drone;
            }
            BlockPos last = job.lastDronePos();
            if (last != null && !level.isLoaded(last) && !level.isLoaded(workPos)) {
                return null;
            }
        }
        // Never adopt a leftover flyer on the site — that looks like a snap. Always leave from home.
        discardUnownedJobFlyers(level, job, workPos);
        if (stillFlyingHome(job, 0)) {
            return null;
        }
        BlockPos home = job.hands().isEmpty() ? null : job.hands().get(0).homePort;
        Vec3 site = Vec3.atCenterOf(workPos).add(0.0D, 1.15D, 0.0D);
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        bindUnit(job, drone, 0);
        job.setDroneId(drone.getUUID());
        level.addFreshEntity(drone);
        depart(drone, player, site, 0, 1, home);
        job.setLastDronePos(drone.blockPosition());
        paint(drone, job);
        return drone;
    }

    private static void bindUnit(BuildJob job, DroneEntity drone, int handIndex) {
        if (handIndex >= 0 && handIndex < job.crewUnits().size()) {
            drone.assignUnit(job.crewUnits().get(handIndex));
        }
    }

    /** The flyer for this wright is still on its way back, so another one must not launch. */
    private static boolean stillFlyingHome(BuildJob job, int handIndex) {
        return handIndex >= 0 && handIndex < job.crewUnits().size() && WrightFlights.isReturning(job.crewUnits().get(handIndex));
    }

    private static void paint(DroneEntity drone, BuildJob job) {
        BuildJob.Hand lead = job.hands().isEmpty() ? null : job.hands().get(0);
        drone.setParts(lead == null ? com.wrights.drone.Loadout.BASIC : lead.parts());
    }

    static DroneEntity ensureHand(ServerLevel level, ServerPlayer player, BuildJob job, BuildJob.Hand hand, BlockPos workPos) {
        if (hand.entityId != null) {
            Entity existing = level.getEntity(hand.entityId);
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                carryStrand(job, hand, drone);
                bindUnit(job, drone, job.hands().indexOf(hand));
                return drone;
            }
            BlockPos last = hand.lastPos;
            if (last != null && !level.isLoaded(last) && !level.isLoaded(workPos)) {
                return null;
            }
            hand.entityId = null;
        }
        // Never adopt a leftover flyer on the site — that looks like a snap. Always leave from home.
        discardUnownedJobFlyers(level, job, workPos);
        if (stillFlyingHome(job, job.hands().indexOf(hand))) {
            return null;
        }
        Vec3 site = Vec3.atCenterOf(workPos).add(0.0D, 1.15D, 0.0D);
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        bindUnit(job, drone, job.hands().indexOf(hand));
        hand.entityId = drone.getUUID();
        carryStrand(job, hand, drone);
        level.addFreshEntity(drone);
        depart(drone, player, site, hand.lane, hand.lanes, hand.homePort);
        hand.lastPos = drone.blockPosition();
        return drone;
    }

    /** Drops stray flyers still tagged for this job so a later spawn cannot reuse them on site. */
    private static void discardUnownedJobFlyers(ServerLevel level, BuildJob job, BlockPos workPos) {
        if (level == null || job == null || workPos == null) {
            return;
        }
        AABB search = new AABB(workPos).inflate(96.0D);
        for (DroneEntity drone : level.getEntitiesOfClass(
                DroneEntity.class,
                search,
                entity -> entity.isAlive() && entity.jobId().filter(job.id()::equals).isPresent())) {
            if (!job.ownsDrone(drone.getUUID())) {
                drone.discard();
            }
        }
    }

    private static void carryStrand(BuildJob job, BuildJob.Hand hand, DroneEntity drone) {
        if (!hand.stranded) {
            return;
        }
        int index = job.hands().indexOf(hand);
        UUID unit = index >= 0 && index < job.crewUnits().size() ? job.crewUnits().get(index) : null;
        drone.strand(unit);
    }

    /** Wrights that are out from this dock stay out. They are recovered when they fly home. */
    public static Set<UUID> strandFromDock(ServerLevel level, BlockPos pos) {
        Set<UUID> away = new HashSet<>();
        if (level == null || pos == null || level.getServer() == null) {
            return away;
        }
        SchematicLibrary library = SchematicLibrary.get(level.getServer());
        boolean dirty = false;
        for (BuildJob job : library.jobs()) {
            if (job.finished()) {
                continue;
            }
            List<UUID> units = job.crewUnits();
            for (int i = 0; i < units.size() && i < job.hands().size(); i++) {
                BuildJob.Hand hand = job.hands().get(i);
                if (hand.homePort == null || !hand.homePort.equals(pos)) {
                    continue;
                }
                away.add(units.get(i));
                hand.stranded = true;
                if (level.getBlockEntity(pos) instanceof WrightportBlockEntity port) {
                    for (Swarm.Unit unit : port.swarm().units()) {
                        if (unit.id().equals(units.get(i)) && unit.name() != null && !unit.name().isBlank()) {
                            hand.wrightName = unit.name();
                            break;
                        }
                    }
                }
                dirty = true;
                ServerLevel jobLevel = level.getServer().getLevel(job.dimension());
                if (jobLevel == null || hand.entityId == null) {
                    continue;
                }
                if (jobLevel.getEntity(hand.entityId) instanceof DroneEntity drone && drone.isAlive()) {
                    drone.strand(units.get(i));
                }
            }
        }
        if (dirty) {
            library.setDirty();
        }
        return away;
    }

    /**
     * A wright whose dock is gone should not hover there. Fly it to another port that
     * covers the build and has a free slot. Otherwise put it in digital storage, or drop it.
     */
    static void shelterLost(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (level == null || job == null || job.finished() || !job.hasCrew()) {
            return;
        }
        List<UUID> units = job.crewUnits();
        Map<BlockPos, Integer> reserved = new HashMap<>();
        boolean dirty = false;
        int flying = 0;
        for (int i = Math.min(job.hands().size(), units.size()) - 1; i >= 0; i--) {
            if (i >= job.hands().size()) {
                continue;
            }
            BuildJob.Hand hand = job.hands().get(i);
            UUID id = units.get(i);
            if (hand.sheltering) {
                Entity entity = hand.entityId == null ? null : level.getEntity(hand.entityId);
                if (entity instanceof DroneEntity drone && drone.isAlive() && drone.returning()) {
                    continue;
                }
                if (player != null && WrightHomes.stillThere(level, player, hand.homePort, id)) {
                    hand.sheltering = false;
                    hand.stranded = false;
                    hand.entityId = null;
                    dirty = true;
                    continue;
                }
                hand.sheltering = false;
            }
            BlockPos home = hand.homePort;
            boolean gone = WrightHomes.dockGone(level, player, home, id);
            if (!gone && hand.stranded && player != null) {
                gone = !WrightHomes.stillThere(level, player, home, id);
            }
            if (!gone) {
                continue;
            }
            String name = hand.wrightName == null ? "" : hand.wrightName;
            if (player != null) {
                Swarm.Unit known = WrightHomes.findUnit(player, id);
                if (known != null && known.name() != null && !known.name().isBlank()) {
                    name = known.name();
                    hand.wrightName = name;
                }
            }
            WrightportPlaces.removeUnit(player, id);
            int tier = WrightTier.clamp(hand.rotor);
            BlockPos next = WrightHomes.openPort(level, job.min(), job.owner(), reserved);
            DroneEntity drone = null;
            if (hand.entityId != null && level.getEntity(hand.entityId) instanceof DroneEntity found && found.isAlive()) {
                drone = found;
            }
            if (player != null && drone != null && next != null) {
                reserved.merge(next, 1, Integer::sum);
                hand.homePort = next;
                hand.stranded = true;
                hand.sheltering = true;
                hand.idle = false;
                hand.waitingOnOthers = false;
                drone.strand(id, name);
                drone.setHomePort(next);
                drone.flyHome(player, flying, Math.max(1, job.hands().size()), next);
                flying++;
                dirty = true;
                continue;
            }
            BlockPos where = next != null
                    ? next
                    : (drone != null ? drone.blockPosition() : (hand.lastPos != null ? hand.lastPos : job.min()));
            BlockPos docked = DockRecovery.recover(level, player, where, id, hand.parts(), name);
            dirty = true;
            if (docked != null) {
                noteDocked(level.getServer(), id, docked);
                if (drone != null && drone.isAlive()) {
                    drone.forgetStrand();
                    drone.discard();
                }
            } else {
                noteReleased(level.getServer(), id);
            }
        }
        if (dirty) {
            library.setDirty();
        }
    }

    /** The wright reached a real port and is stored there under the same id. */
    public static void noteDocked(MinecraftServer server, UUID unitId, BlockPos port) {
        if (server == null || unitId == null || port == null) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(server);
        for (BuildJob job : library.jobs()) {
            int index = job.crewUnits().indexOf(unitId);
            if (index < 0 || index >= job.hands().size()) {
                continue;
            }
            BuildJob.Hand hand = job.hands().get(index);
            hand.homePort = port.immutable();
            hand.stranded = false;
            hand.sheltering = false;
            hand.entityId = null;
            library.setDirty();
            return;
        }
    }

    /** The wright was stored in digital storage or dropped, so the job must let go of it. */
    public static void noteReleased(MinecraftServer server, UUID unitId) {
        if (server == null || unitId == null) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(server);
        for (BuildJob job : library.jobs()) {
            int index = job.crewUnits().indexOf(unitId);
            if (index < 0 || index >= job.hands().size()) {
                continue;
            }
            retire(server, library, job, index);
            return;
        }
    }

    private static void retire(MinecraftServer server, SchematicLibrary library, BuildJob job, int index) {
        List<BuildJob.Hand> hands = job.hands();
        if (index < 0 || index >= hands.size()) {
            return;
        }
        BuildJob.Hand hand = hands.get(index);
        ServerLevel level = server.getLevel(job.dimension());
        if (level != null && hand.entityId != null && level.getEntity(hand.entityId) instanceof DroneEntity drone) {
            drone.forgetStrand();
            drone.discard();
        }
        hand.sheltering = false;
        hand.stranded = false;
        hand.entityId = null;
        if (hands.size() <= 1) {
            job.setCrewUnits(List.of());
            if (job.queued()) {
                job.setPaused(false);
                job.clearHold();
            } else if (!job.paused()) {
                job.noteHold(BuildJob.HOLD_NO_WRIGHTS, null);
                job.setPaused(true);
            }
            library.setDirty();
            return;
        }
        for (int i = 0; i < hands.size(); i++) {
            if (i == index) {
                continue;
            }
            hands.get(i).slices.addAll(hand.slices);
            break;
        }
        hands.remove(index);
        List<UUID> units = new ArrayList<>(job.crewUnits());
        if (index < units.size()) {
            units.remove(index);
        }
        job.setCrewUnits(units);
        for (int i = 0; i < hands.size(); i++) {
            hands.get(i).lane = i;
            hands.get(i).lanes = hands.size();
        }
        library.setDirty();
    }

    /**
     * Every launch leaves from home: personal → behind the player, placed → wrightport lip.
     * Never appear on the job first. TELEPORT mode is the only path that starts at the work.
     */
    private static void depart(DroneEntity drone, ServerPlayer player, Vec3 work, int lane, int lanes, BlockPos homePort) {
        drone.setHomePort(homePort);
        float yaw = player == null ? 0.0F : player.getYRot();
        if (!WrightsConfig.flyToJob()) {
            drone.moveTo(work.x, work.y, work.z, yaw, 0.0F);
            drone.setYRot(yaw);
            return;
        }
        boolean playerHere = player != null && player.level() == drone.level();
        if (homePort != null) {
            if (drone.level() instanceof ServerLevel server && server.isLoaded(homePort)) {
                drone.departFromBlock(homePort, work, lane, lanes);
                return;
            }
            if (playerHere) {
                drone.departFromPlayer(player, work, lane, lanes);
                drone.setHomePort(homePort);
                return;
            }
            drone.moveTo(work.x, work.y + 8.0D, work.z, yaw, 0.0F);
            drone.setYRot(yaw);
            return;
        }
        if (playerHere) {
            drone.departFromPlayer(player, work, lane, lanes);
            return;
        }
        drone.moveTo(work.x, work.y + 8.0D, work.z, yaw, 0.0F);
        drone.setYRot(yaw);
    }

    /** Dock the crew, keep the job and its ghost, and wait until the port can pay for another block. */
    static void stallForEnergy(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job) {
        job.setWaitKind(BuildJob.WAIT_ENERGY);
        job.setQueued(true);
        job.setCrewQuota(Math.max(1, Math.max(job.crewQuota(), job.hands().size())));
        for (BuildJob.Hand hand : job.hands()) {
            hand.awaitingItems = false;
            hand.idle = false;
            recallHand(level, player, hand);
        }
        if (job.hands().isEmpty()) {
            parkSolo(level, player, job);
        }
        JobAnnouncements.noteHistory(player, library, job, JobEventLog.STALLED_ITEMS, Component.translatable("history.wrights.stalled_energy").getString());
        library.setDirty();
    }

    static void parkForItems(ServerLevel level, ServerPlayer player, BuildJob.Hand hand) {
        hand.awaitingItems = true;
        hand.idle = false;
        hand.waitingOnOthers = false;
        recallHand(level, player, hand);
    }

    /** Send this wright home. It stays on the crew and comes back when it has something to place. */
    private static void recallHand(ServerLevel level, ServerPlayer player, BuildJob.Hand hand) {
        if (hand.entityId == null) {
            return;
        }
        recall(level, player, hand.entityId, 0, 1, hand.homePort);
        hand.entityId = null;
    }

    static void parkSolo(ServerLevel level, ServerPlayer player, BuildJob job) {
        if (job.droneId() == null) {
            return;
        }
        recall(level, player, job.droneId(), 0, 1, null);
        job.setDroneId(null);
    }

    /**
     * A wright with nothing left it can pay for on this job can join another build that its supply can continue.
     */
    static void lendStocked(MinecraftServer server, SchematicLibrary library) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean moved = false;
            for (BuildJob job : new ArrayList<>(library.jobsFor(player.getUUID()))) {
                if (moved || job.finished() || job.paused() || job.undoing() || job.kind() != BuildJob.Kind.PASTE || !job.hasCrew()) {
                    continue;
                }
                ServerLevel level = server.getLevel(job.dimension());
                Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
                if (level == null || schematic == null) {
                    continue;
                }
                List<UUID> units = job.crewUnits();
                for (int i = 0; i < job.hands().size() && i < units.size(); i++) {
                    BuildJob.Hand hand = job.hands().get(i);
                    if (!hand.awaitingItems || hand.entityId != null) {
                        continue;
                    }
                    BuildJob target = lendTarget(server, library, player, job, hand);
                    if (target == null) {
                        continue;
                    }
                    moveUnit(player, library, job, target, units.get(i));
                    moved = true;
                    break;
                }
            }
        }
    }

    private static BuildJob lendTarget(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob source, BuildJob.Hand hand) {
        BuildJob best = null;
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (job == source || job.finished() || job.undoing() || job.kind() != BuildJob.Kind.PASTE) {
                continue;
            }
            int cap = WrightsConfig.maxWrightsPerJob(job);
            if (job.crewUnits().size() >= cap || !CrewRoster.coversJob(player, job, hand.homePort)) {
                continue;
            }
            ServerLevel level = server.getLevel(job.dimension());
            Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
            if (level == null || schematic == null) {
                continue;
            }
            int room = CrewRoster.crewRoom(schematic, job);
            if (job.crewUnits().size() >= Math.min(room, cap)) {
                continue;
            }
            Owed owed = owed(level, schematic, job);
            if (!owed.allows(player, level, job, hand.homePort)) {
                continue;
            }
            boolean waiting = job.queued() || !job.hasCrew();
            if (!waiting) {
                for (BuildJob.Hand other : job.hands()) {
                    if (other.awaitingItems) {
                        waiting = true;
                        break;
                    }
                }
            }
            if (waiting) {
                return job;
            }
            if (best == null) {
                best = job;
            }
        }
        return best;
    }

    /**
     * A stalled job does not keep wrights that have gone home. The wanted count stays, and whoever is free
     * when the items arrive can continue it.
     */
    static void releaseStalledHome(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (player == null || job.paused() || job.finished() || job.waitKind() != BuildJob.WAIT_ITEMS) {
            return;
        }
        List<UUID> units = job.crewUnits();
        if (units.isEmpty()) {
            return;
        }
        List<UUID> keepIds = new ArrayList<>();
        List<BuildJob.Hand> keepHands = new ArrayList<>();
        List<BuildJob.Slice> spare = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            BuildJob.Hand hand = i < job.hands().size() ? job.hands().get(i) : null;
            if (CrewRoster.unitIsOut(player, job, units.get(i))) {
                keepIds.add(units.get(i));
                if (hand != null) {
                    keepHands.add(hand);
                }
            } else if (hand != null) {
                spare.addAll(hand.slices);
            }
        }
        if (keepIds.size() == units.size()) {
            return;
        }
        int quota = Math.max(1, Math.max(job.crewQuota(), units.size()));
        if (keepIds.isEmpty()) {
            sendHome(player.getServer(), player, job);
            job.clearAssigned();
            job.setCrewQuota(quota);
            job.setQueued(true);
            job.setPaused(false);
            job.clearHold();
            job.setWaitKind(BuildJob.WAIT_ITEMS);
            library.setDirty();
            return;
        }
        if (!spare.isEmpty() && !keepHands.isEmpty()) {
            keepHands.get(0).slices.addAll(spare);
        }
        job.hands().clear();
        job.hands().addAll(keepHands);
        for (int i = 0; i < job.hands().size(); i++) {
            BuildJob.Hand hand = job.hands().get(i);
            hand.lane = i;
            hand.lanes = job.hands().size();
        }
        job.setCrewUnits(keepIds);
        job.setCrewQuota(quota);
        job.setPaused(false);
        job.clearHold();
        library.setDirty();
    }

    /** True when the blocks still open need items that no free wright can pay for yet. */
    static boolean shortOnItems(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(job.dimension());
        if (level == null || job.schematicId() == null) {
            return false;
        }
        Schematic schematic = library.schematic(job.schematicId()).orElse(null);
        if (schematic == null) {
            return false;
        }
        Owed owed = owed(level, schematic, job);
        if (owed.idle() || owed.items.isEmpty()) {
            return false;
        }
        for (WrightHomes.FreeWright free : CrewRoster.freeUnits(player, library, job, Integer.MAX_VALUE)) {
            if (owed.allows(player, level, job, free.homePort())) {
                return false;
            }
        }
        return !owed.allows(player, level, job, null);
    }

    /** Takes a wright that went home to wait for items off that job, so a new job can have it. */
    static void releaseHomeWaiter(ServerPlayer player, SchematicLibrary library, UUID unitId, UUID keepJob) {
        for (BuildJob other : new ArrayList<>(library.jobsFor(player.getUUID()))) {
            if (keepJob != null && other.id().equals(keepJob)) {
                continue;
            }
            if (!CrewRoster.usesDrones(other) || other.awaitingDrones() || other.waitKind() != BuildJob.WAIT_ITEMS) {
                continue;
            }
            if (!other.crewUnits().contains(unitId) || CrewRoster.unitIsOut(player, other, unitId)) {
                continue;
            }
            leaveCrew(player, library, other, unitId);
        }
    }

    private static void leaveCrew(ServerPlayer player, SchematicLibrary library, BuildJob source, UUID unitId) {
        List<UUID> from = new ArrayList<>(source.crewUnits());
        if (!from.remove(unitId)) {
            return;
        }
        int quota = Math.max(source.crewQuota(), from.size() + 1);
        if (from.isEmpty()) {
            sendHome(player.getServer(), player, source);
            source.clearAssigned();
            source.setCrewQuota(Math.max(1, quota));
            source.setQueued(true);
            source.setPaused(false);
            source.clearHold();
        } else {
            CrewRoster.placeCrew(player, library, source, from);
            source.setCrewQuota(Math.max(source.crewQuota(), quota));
            source.setQueued(true);
            source.setPaused(false);
            source.clearHold();
        }
        library.setDirty();
    }

    private static void moveUnit(ServerPlayer player, SchematicLibrary library, BuildJob source, BuildJob target, UUID unitId) {
        leaveCrew(player, library, source, unitId);
        List<UUID> to = new ArrayList<>(target.crewUnits());
        if (!to.contains(unitId)) {
            to.add(unitId);
        }
        CrewRoster.placeCrew(player, library, target, to);
        target.setPaused(false);
        target.clearHold();
        if (target.crewQuota() < target.crewUnits().size()) {
            target.setCrewQuota(target.crewUnits().size());
        }
        library.setDirty();
    }

    /** What is still unplaced, and whether any of it needs no item. */
    static Owed owed(ServerLevel level, Schematic schematic, BuildJob job) {
        Owed owed = new Owed();
        int count = Math.min(job.total(), schematic.blockCount());
        for (int i = 0; i < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                continue;
            }
            if (!level.isLoaded(job.min().offset(entry.x(), entry.y(), entry.z()))) {
                owed.open = true;
                continue;
            }
            Falling hold = WorkPicker.falling(level, schematic, job, entry);
            if (hold == Falling.WAIT) {
                owed.pending = true;
                continue;
            }
            if (hold == Falling.SKIP || entry.secondary()) {
                owed.open = true;
                continue;
            }
            Item payment = BlockFilters.payment(entry.state());
            if (payment == null && com.wrights.util.BlockExtras.of(entry).isEmpty()) {
                owed.open = true;
                continue;
            }
            if (payment != null && owed.items.size() < 64 && !owed.items.contains(payment)) {
                owed.items.add(payment);
            }
            if (WorkPicker.willApplyExtras(null, job, entry)) {
                for (Item extra : com.wrights.util.BlockExtras.of(entry)) {
                    if (owed.items.size() >= 64) {
                        break;
                    }
                    if (!owed.items.contains(extra)) {
                        owed.items.add(extra);
                    }
                }
            }
        }
        return owed;
    }

    static final class Owed {
        boolean open;
        boolean pending;
        final List<Item> items = new ArrayList<>();

        boolean idle() {
            return !open && !pending && items.isEmpty();
        }

        boolean allows(ServerPlayer player, ServerLevel level, BuildJob job, BlockPos home) {
            // Placed ports with no chest/digital must not relaunch — that caused fly-out/home loops.
            if (!JobSupply.portSupplyReady(level, player, home)) {
                return false;
            }
            BuildJob.Hand hand = new BuildJob.Hand();
            hand.homePort = home;
            if (!items.isEmpty()) {
                for (Item item : items) {
                    if (JobSupply.hasItem(player, level, job, hand, item)) {
                        return true;
                    }
                }
                // Still owes materials — do not relaunch just because some unpaid/open cells remain.
                return false;
            }
            return open || pending;
        }
    }

    static void sendHome(MinecraftServer server, ServerPlayer player, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        if (player == null || level == null || player.level() != level) {
            discardDrone(server, job);
            return;
        }
        List<UUID> ids = new ArrayList<>();
        List<BlockPos> homes = new ArrayList<>();
        if (job.droneId() != null) {
            ids.add(job.droneId());
            homes.add(job.hands().isEmpty() ? null : job.hands().get(0).homePort);
        }
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.entityId != null && !ids.contains(hand.entityId)) {
                ids.add(hand.entityId);
                homes.add(hand.homePort);
            }
        }
        int slot = 0;
        for (UUID id : ids) {
            recall(level, player, id, slot, ids.size(), homes.get(slot));
            slot++;
        }
        job.setDroneId(null);
        for (BuildJob.Hand hand : job.hands()) {
            hand.entityId = null;
        }
    }

    static void recall(ServerLevel level, ServerPlayer player, UUID id, int slot, int count) {
        recall(level, player, id, slot, count, null);
    }

    static void recall(ServerLevel level, ServerPlayer player, UUID id, int slot, int count, BlockPos homePort) {
        if (level == null || id == null) {
            return;
        }
        if (player == null || player.level() != level) {
            discardId(level, id);
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity instanceof DroneEntity drone && drone.isAlive()) {
            BlockPos home = homePort != null ? homePort : drone.homePort();
            drone.flyHome(player, slot, count, home);
        }
    }

    static void discardDrone(MinecraftServer server, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        discardId(level, job.droneId());
        job.setDroneId(null);
        for (BuildJob.Hand hand : job.hands()) {
            discardId(level, hand.entityId);
            hand.entityId = null;
        }
    }

    static void discardId(ServerLevel level, UUID id) {
        if (level == null || id == null) {
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity instanceof DroneEntity drone) {
            drone.recoverIfStranded();
        }
        if (entity != null) {
            entity.discard();
        }
    }

    static ItemStack findController(ServerPlayer player, UUID controllerId) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ControllerItem && controllerId.equals(ControllerData.from(stack).controllerId().orElse(null))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
