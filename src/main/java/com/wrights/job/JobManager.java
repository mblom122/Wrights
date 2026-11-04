package com.wrights.job;

import com.wrights.job.WorkPicker.Falling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.blueprint.BlueprintActions;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.WrightsMod;
import com.wrights.drone.WrightTier;
import com.wrights.drone.WrightHomes;
import com.wrights.drone.CrewSplit;
import com.wrights.entity.DroneEntity;
import com.wrights.item.ControllerData;
import com.wrights.item.ControllerItem;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.storage.DigitalNetworks;
import com.wrights.util.BlockFilters;
import com.wrights.util.Messages;

@EventBusSubscriber(modid = WrightsMod.MOD_ID)
public final class JobManager {
    static final double DRONE_REACH_SQR = 2.25D;
    /** Small-pattern tick did not apply; the strip crawler runs. */
    static final int PATTERN_SKIP = 0;
    /** This hand's stand was handled (placed, flying, or idle). */
    static final int PATTERN_DONE = 1;
    static final int PATTERN_ITEMS = 2;
    static final int PATTERN_CHUNK = 3;
    static final int PATTERN_ENERGY = 4;
    /**
     * Sentinel for a place-reach cluster probe. The legal cells are the fabricator reach sphere
     * ({@link WrightTier#operationReachSqr}), not this euclidean distance. Flight probes use {@link #FLY_NEAR_SQR}.
     */
    static final double PLACE_NEAR_SQR = 9.0D;
    /** Soft near band when the local cluster is empty; prefer these before a long index walk. */
    static final double FLY_NEAR_SQR = 64.0D;
    /**
     * When choosing a fly-to hover, allow this much extra distance over the nearest READY cell
     * if another cell in that band covers a denser place-reach cluster (e.g. wall center vs corner).
     */
    static final double SEED_SLACK_SQR = 8.0D;
    /** Chebyshev probe around the drone for {@link #PLACE_NEAR_SQR} (hover sits slightly above the cell). */
    static final int CLUSTER_RADIUS = 4;
    /** Chebyshev probe for {@link #FLY_NEAR_SQR}. */
    static final int FLY_NEAR_RADIUS = 8;
    /**
     * Hard cap on how many schematic cells one scan/borrow may inspect. Large jobs used to walk the
     * entire list from index 0 on every place, which locked the server tick and blocked Stop/Remove.
     */
    static final int CREW_SCAN_CAP = 512;
    /** Shared across all hands for one crew tick — skips of already-done cells, not world scans. */
    private static final int CREW_SKIP_BUDGET = 256;

    private JobManager() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    public static void tick(MinecraftServer server) {
        DigitalNetworks.tickCrafts(server);
        com.wrights.drone.CraftDeliveries.tick(server);
        com.wrights.drone.HangarDock.tick(server);
        WrightFlights.sweepReturns();
        SchematicLibrary library = SchematicLibrary.get(server);
        List<BuildJob> jobs = new ArrayList<>(library.jobs());
        for (BuildJob job : jobs) {
            if (job.finished() && !job.undoing()) {
                continue;
            }
            try {
                if (job.undoing()) {
                    tickUndo(server, library, job);
                } else if (job.kind() == BuildJob.Kind.COPY) {
                    tickCopy(server, library, job);
                } else if (job.kind() == BuildJob.Kind.SHAPE) {
                    ShapeJobs.tickShape(server, library, job);
                } else {
                    tickPaste(server, library, job);
                }
            } catch (RuntimeException ex) {
                WrightsMod.LOGGER.error("Wrights job {} failed and was stopped", job.id(), ex);
                DroneFleet.discardDrone(server, job);
                library.removeJob(job);
            }
            // Progress is in memory; dirty every second is enough and avoids heavy NBT churn mid-build.
            if (!job.finished() && library.job(job.id()).isPresent() && server.getTickCount() % 20 == 0) {
                library.setDirty();
            }
        }
        DroneFleet.lendStocked(server, library);
        if (server.getTickCount() % 10 == 0) {
            java.util.Set<java.util.UUID> owners = new java.util.HashSet<>();
            for (BuildJob job : library.jobs()) {
                owners.add(job.owner());
            }
            for (java.util.UUID owner : owners) {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(owner);
                if (player != null) {
                    com.wrights.network.BlueprintNetwork.syncJobs(player);
                }
            }
        }
    }

    public static java.util.Optional<BuildJob> jobFor(ServerPlayer player) {
        return SchematicLibrary.get(player.getServer()).jobFor(player.getUUID());
    }

    public static boolean cancel(ServerPlayer player, boolean tellPlayer) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        List<BuildJob> jobs = new ArrayList<>(library.jobsFor(player.getUUID()));
        jobs.removeIf(BuildJob::finished);
        if (jobs.isEmpty()) {
            return false;
        }
        for (BuildJob job : jobs) {
            stopOne(player, library, job, false);
        }
        if (tellPlayer) {
            Messages.actionBar(player, jobs.size() == 1 ? "message.wrights.stopped" : "message.wrights.stopped_all");
        }
        com.wrights.network.BlueprintNetwork.syncJobs(player);
        return true;
    }

    public static void control(ServerPlayer player, java.util.UUID id, int action) {
        if (!com.wrights.item.ControllerItem.isHeld(player)) {
            Messages.actionBar(player, "message.wrights.need_controller");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (action == com.wrights.network.BlueprintPayloads.JobControl.PAUSE_ALL) {
            setPausedAll(player, library, true);
            return;
        }
        if (action == com.wrights.network.BlueprintPayloads.JobControl.RESUME_ALL) {
            setPausedAll(player, library, false);
            return;
        }
        if (action == com.wrights.network.BlueprintPayloads.JobControl.REMOVE_ACTIVE) {
            removeSection(player, library, false);
            return;
        }
        if (action == com.wrights.network.BlueprintPayloads.JobControl.REMOVE_DONE) {
            removeSection(player, library, true);
            return;
        }
        BuildJob job = library.job(id).orElse(null);
        if (job == null || !job.owner().equals(player.getUUID())) {
            com.wrights.network.BlueprintNetwork.syncJobs(player);
            return;
        }
        if (action == com.wrights.network.BlueprintPayloads.JobControl.STOP) {
            stopOne(player, library, job, true);
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.PAUSE && !job.finished()) {
            job.noteHold(BuildJob.HOLD_MANUAL, null);
            job.setPaused(true);
            library.setDirty();
            JobAnnouncements.noteHistory(player, library, job, JobEventLog.PAUSED, Component.translatable("history.wrights.paused_manual").getString());
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.RESUME && !job.finished()) {
            if (job.queued()) {
                job.setPaused(false);
                job.clearHold();
                library.setDirty();
                JobAnnouncements.noteHistory(player, library, job, JobEventLog.RESUMED, Component.translatable("history.wrights.resumed").getString());
            } else {
                CrewRoster.bindCrewIds(player, library);
                if (job.paused() && !CrewRoster.restorePausedCrew(player, library, job)) {
                    CrewRoster.explainResume(player, job);
                } else if (job.paused()) {
                    job.setPaused(false);
                    job.clearHold();
                    library.setDirty();
                    JobAnnouncements.noteHistory(player, library, job, JobEventLog.RESUMED, Component.translatable("history.wrights.resumed").getString());
                }
            }
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.REDO) {
            redo(player, library, job);
            return;
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.UNDO) {
            if (WrightsConfig.refuse(player, WrightsConfig.allowUndo())) {
                return;
            }
            startUndo(player, library, job, false);
            return;
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.UNDO_FORCE) {
            if (WrightsConfig.refuse(player, WrightsConfig.allowUndo())) {
                return;
            }
            startUndo(player, library, job, true);
            return;
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.REMOVE) {
            removeOneJob(player, library, job);
            return;
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.VISIT) {
            visit(player, job);
            return;
        }
        com.wrights.network.BlueprintNetwork.syncJobs(player);
    }

    private static void stopOne(ServerPlayer player, SchematicLibrary library, BuildJob job, boolean tell) {
        if (job.finished()) {
            return;
        }
        if (job.kind() == BuildJob.Kind.COPY && job.resultSchematicId() != null) {
            library.removeSchematic(job.resultSchematicId());
        }
        if (job.kind() == BuildJob.Kind.PASTE) {
            com.wrights.network.BlueprintNetwork.clearJobHologram(player, job.id());
            DroneFleet.sendHome(player.getServer(), player, job);
            archive(library, job, true);
        } else {
            DroneFleet.discardDrone(player.getServer(), job);
            library.removeJob(job);
        }
        if (tell) {
            Messages.actionBar(player, "message.wrights.stopped");
        }
        String detail = Component.translatable(
                "history.wrights.stopped_detail",
                Math.max(0, job.progressDone()),
                Math.max(1, job.total())).getString();
        JobAnnouncements.noteHistory(player, library, job, JobEventLog.STOPPED, detail);
    }

    private static void redo(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (!job.finished() || job.kind() != BuildJob.Kind.PASTE || job.schematicId() == null) {
            return;
        }
        Schematic schematic = library.schematic(job.schematicId()).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return;
        }
        if (overlapsBuild(library, job.dimension(), schematic, job.min(), job.clipX(), job.clipY(), job.clipZ())) {
            Messages.actionBar(player, "message.wrights.build_overlap");
            return;
        }
        if (!instant(player) && !job.hasCrew() && !CrewRoster.assignCrew(player, job, schematic)) {
            return;
        }
        job.restart();
        library.setDirty();
        if (!instant(player) && !job.hasCrew() && player.level() instanceof ServerLevel level && level.dimension().equals(job.dimension())) {
            DroneFleet.ensureDrone(level, player, job, job.min());
        }
        BlueprintNetwork.showJobHologram(player, job, schematic);
        BlueprintNetwork.syncJobs(player);
        launched(player, job);
    }

    private static void startUndo(ServerPlayer player, SchematicLibrary library, BuildJob job, boolean force) {
        if (job.undoing() || !job.finished() || (job.kind() != BuildJob.Kind.PASTE && job.kind() != BuildJob.Kind.SHAPE)) {
            return;
        }
        if (job.refundCount() == 0) {
            Messages.actionBar(player, "message.wrights.undo_none");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (!force) {
            int extra = JobSupply.overflow(player, job);
            if (extra > 0) {
                BlueprintNetwork.undoPrompt(player, job.id(), extra, JobSupply.dropKind(player, job));
                return;
            }
        }
        if (!job.beginUndo()) {
            Messages.actionBar(player, "message.wrights.undo_none");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        library.setDirty();
        if (!instant(player) && !job.hasCrew() && player.level() instanceof ServerLevel level && level.dimension().equals(job.dimension())) {
            DroneFleet.ensureDrone(level, player, job, job.lastRefundPos());
        }
        BlueprintNetwork.clearJobHologram(player, job.id());
        BlueprintNetwork.syncJobs(player);
        Messages.actionBar(player, "message.wrights.undo_started");
        JobAnnouncements.noteHistory(player, library, job, JobEventLog.UNDOING, Component.translatable("history.wrights.undoing").getString());
    }

    /** Creative teleport to the session's bound supply chest. */
    public static void visitSupply(ServerPlayer player) {
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            Messages.actionBar(player, "message.wrights.visit_survival");
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        BlockPos chest = session.supplyPos();
        if (chest == null || session.supplyDimension() == null || player.getServer() == null) {
            Messages.actionBar(player, "message.wrights.supply_none");
            return;
        }
        ServerLevel level = player.getServer().getLevel(session.supplyDimension());
        if (level == null) {
            Messages.actionBar(player, "message.wrights.visit_blocked");
            return;
        }
        level.getChunk(chest.getX() >> 4, chest.getZ() >> 4);
        BlockPos spot = standNearSupply(level, chest);
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, player.getYRot(), player.getXRot());
        Messages.actionBar(player, "message.wrights.visited", spot.getX(), spot.getY(), spot.getZ());
    }

    private static BlockPos standNearSupply(ServerLevel level, BlockPos chest) {
        BlockPos[] tries = {
                chest.above(),
                chest.north(), chest.south(), chest.east(), chest.west(),
                chest.north().above(), chest.south().above(), chest.east().above(), chest.west().above()
        };
        for (BlockPos spot : tries) {
            if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()) {
                return spot;
            }
        }
        return chest.above();
    }

    public static void bindSupply(ServerPlayer player, BlockPos pos, boolean clear) {
        if (!ControllerItem.isHeld(player)) {
            Messages.actionBar(player, "message.wrights.need_controller");
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.BUILD) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (clear || pos == null) {
            session.setSupply(null, null);
            library.setSupply(player.getUUID(), null);
            BlueprintNetwork.syncSupply(player);
            Messages.actionBar(player, "message.wrights.supply_inventory");
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        if (!level.mayInteract(player, pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) {
            Messages.actionBar(player, "message.wrights.out_of_range");
            return;
        }
        if (JobSupply.containerAt(level, pos) == null) {
            Messages.actionBar(player, "message.wrights.supply_not_container");
            return;
        }
        session.setSupply(pos.immutable(), level.dimension());
        library.setSupply(player.getUUID(), net.minecraft.core.GlobalPos.of(level.dimension(), pos));
        BlueprintNetwork.syncSupply(player);
        Messages.actionBar(player, "message.wrights.supply_chest");
    }

    /** Each placed wrightport keeps its own chest, and that chest has to sit inside the port boundary. */
    public static void bindPortChest(ServerPlayer player, BlockPos portPos, BlockPos chestPos, boolean clear) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (portPos == null || !(level.getBlockEntity(portPos) instanceof com.wrights.block.WrightportBlockEntity port)) {
            return;
        }
        if (port.owner() != null && !port.owner().equals(player.getUUID())) {
            Messages.actionBar(player, "message.wrights.port_chest_owner");
            return;
        }
        if (clear || chestPos == null) {
            port.setSupplyChest(null);
            Messages.actionBar(player, "message.wrights.port_chest_cleared");
            return;
        }
        if (!level.mayInteract(player, chestPos) || JobSupply.containerAt(level, chestPos) == null) {
            Messages.actionBar(player, "message.wrights.supply_not_container");
            return;
        }
        if (!com.wrights.drone.WrightHomes.portCovers(level.dimension(), portPos, level.dimension(), chestPos)) {
            Messages.actionBar(player, "message.wrights.port_chest_far");
            return;
        }
        port.setSupplyChest(chestPos);
        Messages.actionBar(player, "message.wrights.port_chest_bound");
    }

    private static void archive(SchematicLibrary library, BuildJob job, boolean stopped) {
        job.markFinished(library.nextHistoryOrder(), stopped);
        library.pruneHistory(job.owner());
        library.setDirty();
    }

    private static void setPausedAll(ServerPlayer player, SchematicLibrary library, boolean paused) {
        boolean changed = false;
        int blocked = 0;
        if (!paused) {
            CrewRoster.bindCrewIds(player, library);
        }
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (!job.finished() && job.paused() != paused) {
                if (!paused && job.queued()) {
                    job.setPaused(false);
                    job.clearHold();
                    changed = true;
                    continue;
                }
                if (!paused && !CrewRoster.restorePausedCrew(player, library, job)) {
                    blocked++;
                    continue;
                }
                if (paused) {
                    job.noteHold(BuildJob.HOLD_MANUAL, null);
                    JobAnnouncements.noteHistory(player, library, job, JobEventLog.PAUSED, Component.translatable("history.wrights.paused_all").getString());
                } else {
                    job.clearHold();
                    JobAnnouncements.noteHistory(player, library, job, JobEventLog.RESUMED, Component.translatable("history.wrights.resumed").getString());
                }
                job.setPaused(paused);
                changed = true;
            }
        }
        if (changed) {
            library.setDirty();
            Messages.actionBar(player, paused ? "message.wrights.paused_all" : "message.wrights.resumed_all");
            } else if (blocked > 0) {
            Messages.actionBar(player, "message.wrights.resume_none");
        }
        BlueprintNetwork.syncJobs(player);
    }

    private static void removeSection(ServerPlayer player, SchematicLibrary library, boolean finished) {
        List<BuildJob> jobs = new ArrayList<>(library.jobsFor(player.getUUID()));
        int count = 0;
        for (BuildJob job : jobs) {
            if (job.finished() == finished) {
                discardJob(player, library, job);
                count++;
            }
        }
        if (count == 1) {
            Messages.actionBar(player, "message.wrights.job_removed");
        } else if (count > 1) {
            Messages.actionBar(player, "message.wrights.removed_jobs", count);
        }
        BlueprintNetwork.syncJobs(player);
    }

    private static void removeOneJob(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        JobAnnouncements.noteHistory(player, library, job, JobEventLog.REMOVED, Component.translatable("history.wrights.removed").getString());
        discardJob(player, library, job);
        BlueprintNetwork.syncJobs(player);
        Messages.actionBar(player, "message.wrights.job_removed");
    }

    private static void discardJob(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (job.kind() == BuildJob.Kind.COPY && job.resultSchematicId() != null && !job.finished()) {
            library.removeSchematic(job.resultSchematicId());
        }
        if (job.kind() == BuildJob.Kind.PASTE) {
            BlueprintNetwork.clearJobHologram(player, job.id());
        }
        DroneFleet.discardDrone(player.getServer(), job);
        library.removeJob(job);
    }

    private static void visit(ServerPlayer player, BuildJob job) {
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            Messages.actionBar(player, "message.wrights.visit_survival");
            return;
        }
        ServerLevel level = player.getServer().getLevel(job.dimension());
        if (level == null) {
            Messages.actionBar(player, "message.wrights.visit_blocked");
            return;
        }
        BlockPos spot = standBefore(level, job);
        if (spot == null) {
            Messages.actionBar(player, "message.wrights.visit_blocked");
            return;
        }
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, player.getYRot(), player.getXRot());
        Messages.actionBar(player, "message.wrights.visited", spot.getX(), spot.getY(), spot.getZ());
    }

    private static BlockPos standBefore(ServerLevel level, BuildJob job) {
        BlockPos anchor = job.anchor();
        BlockPos min = job.min();
        int maxX = min.getX() + job.boxSizeX() - 1;
        int maxY = min.getY() + job.boxSizeY() - 1;
        int maxZ = min.getZ() + job.boxSizeZ() - 1;
        int x = anchor.getX();
        int y = anchor.getY();
        int z = anchor.getZ();
        boolean inside = x >= min.getX() && x <= maxX && y >= min.getY() && y <= maxY && z >= min.getZ() && z <= maxZ;
        if (inside) {
            int west = x - min.getX();
            int east = maxX - x;
            int north = z - min.getZ();
            int south = maxZ - z;
            int best = Math.min(Math.min(west, east), Math.min(north, south));
            if (best == west) {
                x = min.getX() - 1;
            } else if (best == east) {
                x = maxX + 1;
            } else if (best == north) {
                z = min.getZ() - 1;
            } else {
                z = maxZ + 1;
            }
        } else {
            int cx = min.getX() + job.boxSizeX() / 2;
            int cz = min.getZ() + job.boxSizeZ() / 2;
            if (Math.abs(x - cx) >= Math.abs(z - cz)) {
                x += x < cx ? -1 : 1;
            } else {
                z += z < cz ? -1 : 1;
            }
        }
        BlockPos[] tries = {
                new BlockPos(x, y, z),
                new BlockPos(x, y + 1, z),
                new BlockPos(x, y - 1, z),
                new BlockPos(x + 1, y, z),
                new BlockPos(x - 1, y, z),
                new BlockPos(x, y, z + 1),
                new BlockPos(x, y, z - 1),
                new BlockPos(x, y + 2, z)
        };
        for (BlockPos candidate : tries) {
            BlockPos open = standAbove(level, candidate, min, maxX, maxY, maxZ);
            if (open != null) {
                return open;
            }
        }
        return null;
    }

    private static BlockPos standAbove(ServerLevel level, BlockPos feet, BlockPos min, int maxX, int maxY, int maxZ) {
        BlockPos candidate = feet;
        for (int step = 0; step < 16; step++) {
            if (!insideBox(candidate, min, maxX, maxY, maxZ)) {
                if (!level.isLoaded(candidate)) {
                    level.getChunkAt(candidate);
                }
                if (openStand(level, candidate)) {
                    return candidate;
                }
            }
            candidate = candidate.above();
        }
        return null;
    }

    private static boolean insideBox(BlockPos pos, BlockPos min, int maxX, int maxY, int maxZ) {
        return pos.getX() >= min.getX() && pos.getX() <= maxX
                && pos.getY() >= min.getY() && pos.getY() <= maxY
                && pos.getZ() >= min.getZ() && pos.getZ() <= maxZ;
    }

    private static boolean openStand(ServerLevel level, BlockPos feet) {
        BlockState legs = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return !legs.blocksMotion() && !head.blocksMotion() && !level.isOutsideBuildHeight(feet);
    }

    public static void startCopy(ServerPlayer player, ItemStack stack, ControllerData data) {
        data = data.ensureId();
        cancel(player, false);
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (!jobSlotOpen(player, library)) {
            return;
        }
        UUID previous = data.schematicId().orElse(null);
        if (previous != null && !library.isInUse(previous)) {
            library.removeSchematic(previous);
        }
        data = data.withoutSchematic();
        ControllerData.write(stack, data);

        GlobalPos cornerA = data.cornerA().orElseThrow();
        GlobalPos cornerB = data.cornerB().orElseThrow();
        BlockPos min = new BlockPos(
                Math.min(cornerA.pos().getX(), cornerB.pos().getX()),
                Math.min(cornerA.pos().getY(), cornerB.pos().getY()),
                Math.min(cornerA.pos().getZ(), cornerB.pos().getZ()));
        BlockPos max = new BlockPos(
                Math.max(cornerA.pos().getX(), cornerB.pos().getX()),
                Math.max(cornerA.pos().getY(), cornerB.pos().getY()),
                Math.max(cornerA.pos().getZ(), cornerB.pos().getZ()));
        BuildJob job = BuildJob.copy(player.getUUID(), data.controllerId().orElseThrow(), cornerA.dimension(), min, max);
        library.addJob(job);
        Messages.actionBar(player, "message.wrights.copy_started");
        JobAnnouncements.noteHistory(
                player,
                library,
                job,
                JobEventLog.LAUNCHED,
                Component.translatable("history.wrights.copy_started", Math.max(1, job.total())).getString());
    }

    public static void startPaste(ServerPlayer player, ItemStack stack, ControllerData data, GlobalPos origin) {
        data = data.ensureId().withoutAim();
        ControllerData.write(stack, data);
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(data.schematicId().orElseThrow()).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.wrights.need_copy");
            return;
        }
        if (!instant(player) && !CrewRoster.crewReady(player, origin.dimension(), origin.pos())) {
            return;
        }
        cancel(player, false);
        if (!jobSlotOpen(player, library)) {
            return;
        }
        BuildJob job = BuildJob.paste(
                player.getUUID(),
                data.controllerId().orElseThrow(),
                origin.dimension(),
                origin.pos(),
                schematic.id(),
                schematic.blockCount());
        job.setAnchor(origin.pos().offset(schematic.anchorX(), schematic.anchorY(), schematic.anchorZ()));
        job.setBlueprintName(JobTitles.blueprintJobTitle(schematic.name()));
        if (!instant(player) && !CrewRoster.assignCrew(player, job, schematic)) {
            return;
        }
        library.addJob(job);
        ServerLevel level = player.getServer().getLevel(origin.dimension());
        if (level != null && player.level() == level && !instant(player) && !job.hasCrew()) {
            DroneFleet.ensureDrone(level, player, job, origin.pos());
        }
        launched(player, job);
    }

    public static boolean startBlueprintSave(ServerPlayer player, String name, UUID replaceId, ResourceKey<Level> dimension, BlockPos cornerA, BlockPos cornerB, boolean copyFluids, boolean copyNbtBlocks, boolean copyNbtSettings, boolean copyNbtAll, byte[] turns) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (hasCopy(library, player.getUUID())) {
            Messages.actionBar(player, "message.wrights.busy");
            return false;
        }
        if (!jobSlotOpen(player, library)) {
            return false;
        }
        if (replaceId != null && library.isInUse(replaceId)) {
            Messages.actionBar(player, "message.wrights.blueprint_in_use");
            return false;
        }
        BlockPos min = new BlockPos(
                Math.min(cornerA.getX(), cornerB.getX()),
                Math.min(cornerA.getY(), cornerB.getY()),
                Math.min(cornerA.getZ(), cornerB.getZ()));
        BlockPos max = new BlockPos(
                Math.max(cornerA.getX(), cornerB.getX()),
                Math.max(cornerA.getY(), cornerB.getY()),
                Math.max(cornerA.getZ(), cornerB.getZ()));
        BuildJob job = BuildJob.copy(player.getUUID(), player.getUUID(), dimension, min, max);
        job.setBlueprintName(JobTitles.blueprintJobTitle(name == null || name.isBlank() ? "Saving" : name));
        job.setCopyFluids(copyFluids);
        job.setCopyNbt(copyNbtBlocks, copyNbtSettings, copyNbtAll);
        job.setCopyTurns(turns);
        job.setReplaceSchematicId(replaceId);
        library.addJob(job);
        Messages.actionBar(player, "message.wrights.saving");
        JobAnnouncements.noteHistory(
                player,
                library,
                job,
                JobEventLog.LAUNCHED,
                Component.translatable("history.wrights.save_started", Math.max(1, job.total())).getString());
        return true;
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, 0);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, true, false, false, false);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, true, false);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean supplyPlayerFirst) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, supplyPlayerFirst, false);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean supplyPlayerFirst, boolean queue) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, supplyPlayerFirst, queue, false, true, true, true);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean supplyPlayerFirst, boolean queue, boolean deferSupplies) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, supplyPlayerFirst, queue, deferSupplies, true, true, true);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean supplyPlayerFirst, boolean queue, boolean deferSupplies, boolean placeNbt, boolean placeSettings, boolean placeAll) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt,
                supplyPlayerFirst ? com.wrights.blueprint.ShapeSupply.DEFAULT_ORDER : com.wrights.blueprint.ShapeSupply.CHEST_FIRST_ORDER,
                queue, deferSupplies, placeNbt, placeSettings, placeAll);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, String supplyOrder, boolean queue, boolean deferSupplies, boolean placeNbt, boolean placeSettings, boolean placeAll) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(schematicId).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return false;
        }
        if (!jobSlotOpen(player, library)) {
            return false;
        }
        if (overlapsBuild(library, player.level().dimension(), schematic, origin, sizeX, sizeY, sizeZ)) {
            Messages.actionBar(player, "message.wrights.build_overlap");
            return false;
        }
        BuildJob job = BuildJob.paste(
                player.getUUID(),
                player.getUUID(),
                player.level().dimension(),
                origin,
                schematic.id(),
                schematic.blockCount());
        job.setClip(sizeX, sizeY, sizeZ);
        job.setPasteOptions(placeFluids, voidBlocks, voidFluids, voidNbt, placeNbt, placeSettings, placeAll);
        job.setAnchor(origin.offset(schematic.anchorX(), schematic.anchorY(), schematic.anchorZ()));
        com.wrights.blueprint.BlueprintSessions.BlueprintSession session = com.wrights.blueprint.BlueprintSessions.get(player);
        job.setSupply(session.supplyPos(), session.supplyDimension());
        job.setSupplyOrder(supplyOrder);
        if (queue && !instant(player) && CrewRoster.freeUnits(player, library, job, 1).isEmpty()) {
            int room = CrewRoster.crewRoom(schematic, job);
            int wanted = drones > 0 ? Math.min(drones, room) : room;
            if (wanted <= 0) {
                Messages.actionBar(player, "message.wrights.drone_block_cap", 0);
                return false;
            }
            job.setCrewQuota(wanted);
            job.setQueued(true);
            if (deferSupplies) {
                job.setDeferSupplies(true);
            }
            library.addJob(job);
            noteQueued(player, job, wanted);
            BlueprintNetwork.showJobHologram(player, job, schematic);
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (!instant(player) && !CrewRoster.assignCrew(player, job, schematic, drones)) {
            return false;
        }
        if (deferSupplies && !instant(player)) {
            job.setDeferSupplies(true);
            job.setQueued(true);
            if (job.crewQuota() <= 0) {
                job.setCrewQuota(Math.max(1, job.hands().size()));
            }
        }
        library.addJob(job);
        if (job.deferSupplies()) {
            noteQueued(player, job, Math.max(1, job.crewQuota()));
            BlueprintNetwork.showJobHologram(player, job, schematic);
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (player.level() instanceof ServerLevel level && !instant(player) && !job.hasCrew()) {
            DroneFleet.ensureDrone(level, player, job, origin);
        }
        launched(player, job);
        BlueprintNetwork.showJobHologram(player, job, schematic);
        BlueprintNetwork.syncJobs(player);
        return true;
    }

    /** One wright job that breaks every mirrored copy. Drops follow the tool that started it. */
    public static boolean startMirrorMine(ServerPlayer player, Schematic schematic, BlockPos anchor, boolean any, boolean voiding, ItemStack tool) {
        if (player == null || schematic == null || schematic.blockCount() == 0) {
            if (player != null) {
                Messages.actionBar(player, "message.wrights.mine_none");
            }
            return false;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (!jobSlotOpen(player, library)) {
            return false;
        }
        library.putSchematic(schematic);
        BlockPos origin = schematic.sourceMin();
        BuildJob job = BuildJob.paste(
                player.getUUID(),
                player.getUUID(),
                player.level().dimension(),
                origin,
                schematic.id(),
                schematic.blockCount());
        job.setClip(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ());
        job.setMining(true);
        job.setMineAny(any);
        job.setMineVoid(voiding);
        job.setMineTool(tool);
        job.setBlueprintName("Mirror - Mine");
        job.setAnchor(anchor == null ? origin : anchor);
        if (!instant(player) && CrewRoster.freeUnits(player, library, job, 1).isEmpty()) {
            int room = CrewRoster.crewRoom(schematic, job);
            job.setCrewQuota(room);
            job.setQueued(true);
            library.addJob(job);
            noteQueued(player, job, room);
            BlueprintNetwork.showJobHologram(player, job, schematic);
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (!instant(player) && !CrewRoster.assignCrew(player, job, schematic, 0)) {
            library.removeSchematic(schematic.id());
            return false;
        }
        library.addJob(job);
        if (player.level() instanceof ServerLevel level && !instant(player) && !job.hasCrew()) {
            DroneFleet.ensureDrone(level, player, job, origin);
        }
        launched(player, job);
        BlueprintNetwork.showJobHologram(player, job, schematic);
        BlueprintNetwork.syncJobs(player);
        return true;
    }

    private static boolean hasCopy(SchematicLibrary library, UUID playerId) {
        for (BuildJob job : library.jobsFor(playerId)) {
            if (job.kind() == BuildJob.Kind.COPY) {
                return true;
            }
        }
        return false;
    }

    /** False when the player already has {@link WrightsConfig#maxJobs()} unfinished jobs. */
    static boolean jobSlotOpen(ServerPlayer player, SchematicLibrary library) {
        int cap = WrightsConfig.maxJobs();
        if (library.activeJobCount(player.getUUID()) < cap) {
            return true;
        }
        Messages.actionBar(player, "message.wrights.job_limit", cap);
        return false;
    }

    private static boolean overlapsBuild(SchematicLibrary library, ResourceKey<Level> dimension, Schematic schematic, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        BlockPos newMax = origin.offset(Math.max(0, sizeX - 1), Math.max(0, sizeY - 1), Math.max(0, sizeZ - 1));
        if (overlapsJobBoxes(library, dimension, origin, newMax)) {
            return true;
        }
        Set<BlockPos> occupied = new HashSet<>();
        for (BuildJob job : library.jobs()) {
            if (job.kind() != BuildJob.Kind.PASTE || job.finished() || !job.dimension().equals(dimension) || job.schematicId() == null) {
                continue;
            }
            Schematic existing = library.schematic(job.schematicId()).orElse(null);
            if (existing == null) {
                continue;
            }
            for (Schematic.Entry entry : existing.entries()) {
                if (job.outsideClip(entry)) {
                    continue;
                }
                occupied.add(job.min().offset(entry.x(), entry.y(), entry.z()));
            }
        }
        for (Schematic.Entry entry : schematic.entries()) {
            if (entry.x() >= sizeX || entry.y() >= sizeY || entry.z() >= sizeZ) {
                continue;
            }
            if (occupied.contains(origin.offset(entry.x(), entry.y(), entry.z()))) {
                return true;
            }
        }
        return false;
    }

    /** Cheap AABB overlap for streaming shape jobs (and vs paste clips). */
    static boolean overlapsJobBoxes(SchematicLibrary library, ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        for (BuildJob job : library.jobs()) {
            if (job.finished() || job.undoing() || !job.dimension().equals(dimension)) {
                continue;
            }
            BlockPos otherMin = job.min();
            BlockPos otherMax;
            if (job.kind() == BuildJob.Kind.SHAPE || job.kind() == BuildJob.Kind.COPY) {
                otherMax = job.max();
            } else {
                otherMax = job.min().offset(Math.max(0, job.boxSizeX() - 1), Math.max(0, job.boxSizeY() - 1), Math.max(0, job.boxSizeZ() - 1));
            }
            if (min.getX() <= otherMax.getX() && max.getX() >= otherMin.getX()
                    && min.getY() <= otherMax.getY() && max.getY() >= otherMin.getY()
                    && min.getZ() <= otherMax.getZ() && max.getZ() >= otherMin.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static void tickCopy(MinecraftServer server, SchematicLibrary library, BuildJob job) {
        if (job.paused()) {
            return;
        }
        if (job.phase() == BuildJob.Phase.BINDING) {
            tryBindCopy(server, library, job);
            return;
        }
        ServerLevel level = server.getLevel(job.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner());
        if (level == null || player == null) {
            return;
        }
        int budget = WrightsConfig.COPY_POSITIONS_PER_TICK.getAsInt();
        boolean counted = false;
        for (int i = 0; i < budget && job.cursor() < job.total(); i++) {
            BlockPos pos = job.scanPos();
            if (level.isOutsideBuildHeight(pos)) {
                job.advance();
                continue;
            }
            if (!ready(player, level, pos, job, true)) {
                return;
            }
            if (!counted) {
                job.workedTick();
                counted = true;
            }
            if (!level.isLoaded(pos)) {
                if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                    if (job.tickMessage()) {
                        noteChunk(player, library, job);
                    }
                    return;
                }
                job.advance();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                if (!canStore(level, job, pos, state)) {
                    job.skippedSpecialOne();
                } else {
                    net.minecraft.nbt.CompoundTag nbt = null;
                    boolean nbtFull = false;
                    if (state.hasBlockEntity() && (job.copyNbtAll() || job.copyNbtSettings())) {
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null) {
                            net.minecraft.nbt.CompoundTag raw = be.saveWithoutMetadata(level.registryAccess());
                            if (job.copyNbtAll()) {
                                nbt = raw.isEmpty() ? null : raw;
                                nbtFull = nbt != null;
                            } else if (WrightsConfig.settingsAllowed(state.getBlock())) {
                                nbt = com.wrights.util.BlockEntityNbt.settings(raw);
                            }
                        }
                    }
                    job.partial().add(new Schematic.Entry(
                            (short) (pos.getX() - job.min().getX()),
                            (short) (pos.getY() - job.min().getY()),
                            (short) (pos.getZ() - job.min().getZ()),
                            state,
                            BlockFilters.isSecondary(state),
                            nbt,
                            nbtFull));
                }
            }
            job.advance();
        }
        library.setDirty();
        if (job.cursor() < job.total()) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.copy_progress", job.cursor(), job.total());
            }
            return;
        }
        finishCopy(library, job);
        tryBindCopy(server, library, job);
    }

    private static boolean canStore(ServerLevel level, BuildJob job, BlockPos pos, BlockState state) {
        if (!BlockFilters.canCopy(level, pos, state, job.copyFluids(), job.copyNbtBlocks())) {
            return false;
        }
        if (!BlockFilters.isSecondary(state)) {
            return true;
        }
        BlockPos below = pos.below();
        return job.contains(below) && BlockFilters.canCopy(level, below, level.getBlockState(below), job.copyFluids(), job.copyNbtBlocks());
    }

    private static Schematic applyCopyTurns(Schematic schematic, byte[] turns) {
        if (schematic == null || turns == null || turns.length == 0) {
            return schematic;
        }
        com.wrights.blueprint.BlueprintTransforms.Op[] ops = com.wrights.blueprint.BlueprintTransforms.Op.values();
        for (byte turn : turns) {
            int ordinal = turn & 0xFF;
            if (ordinal < ops.length) {
                schematic = com.wrights.blueprint.BlueprintTransforms.apply(schematic, ops[ordinal]);
            }
        }
        return schematic;
    }

    private static void finishCopy(SchematicLibrary library, BuildJob job) {
        int sizeX = job.max().getX() - job.min().getX() + 1;
        int sizeY = job.max().getY() - job.min().getY() + 1;
        int sizeZ = job.max().getZ() - job.min().getZ() + 1;
        List<Schematic.Entry> entries = new ArrayList<>(job.partial());
        entries.sort(Schematic.BUILD_ORDER);
        if (entries.isEmpty()) {
            job.setPhase(BuildJob.Phase.BINDING);
            job.setResultSchematicId(null);
            return;
        }
        UUID id = job.replaceSchematicId() != null ? job.replaceSchematicId() : UUID.randomUUID();
        String name = job.blueprintName() == null ? "Blueprint" : job.blueprintName();
        int anchorX = 0;
        int anchorY = 0;
        int anchorZ = 0;
        Schematic previous = null;
        if (job.replaceSchematicId() != null) {
            previous = library.schematic(job.replaceSchematicId()).orElse(null);
            if (previous == null) {
                previous = com.wrights.schematic.GlobalBlueprintStore.get().schematic(job.replaceSchematicId()).orElse(null);
            }
            if (previous != null) {
                anchorX = previous.anchorX();
                anchorY = previous.anchorY();
                anchorZ = previous.anchorZ();
            }
        }
        Schematic schematic = new Schematic(
                id,
                name,
                sizeX,
                sizeY,
                sizeZ,
                entries,
                job.min(),
                job.max(),
                job.dimension().location().toString(),
                anchorX,
                anchorY,
                anchorZ);
        // Bake creation choices into the blueprint so the next place starts from them,
        // not from whatever the player last toggled in the UI.
        boolean placeFluids = job.copyFluids();
        boolean voidBlocks = false;
        boolean voidFluids = false;
        boolean voidNbt = false;
        if (previous != null) {
            voidBlocks = previous.voidBlocks();
            voidFluids = previous.voidFluids();
            voidNbt = previous.voidNbt();
        }
        schematic = schematic.withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
        schematic = applyCopyTurns(schematic, job.copyTurns());
        library.putSchematic(schematic);
        job.setResultSchematicId(schematic.id());
        job.setPhase(BuildJob.Phase.BINDING);
        job.partial().clear();
    }

    private static void tryBindCopy(MinecraftServer server, SchematicLibrary library, BuildJob job) {
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner());
        if (player == null) {
            return;
        }
        if (job.resultSchematicId() == null) {
            if (job.skippedSpecial() > 0) {
                JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("message.wrights.copy_empty_skipped", job.skippedSpecial()));
            } else {
                JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("message.wrights.copy_empty"));
            }
            library.removeJob(job);
            if (job.blueprintName() != null) {
                BlueprintActions.finishSave(player, null, false);
            }
            return;
        }
        if (job.blueprintName() != null) {
            Schematic saved = library.schematic(job.resultSchematicId()).orElse(null);
            library.removeJob(job);
            if (saved == null) {
                JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("message.wrights.copy_empty"));
                BlueprintActions.finishSave(player, null, false);
            } else if (job.skippedSpecial() > 0) {
                JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved_skipped", saved.blockCount(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ()), job.skippedSpecial()));
                BlueprintActions.finishSave(player, saved.id(), true);
            } else {
                JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved", saved.blockCount(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ())));
                BlueprintActions.finishSave(player, saved.id(), true);
            }
            return;
        }
        ItemStack stack = DroneFleet.findController(player, job.controllerId());
        if (stack.isEmpty()) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.hold_controller");
            }
            return;
        }
        Schematic schematic = library.schematic(job.resultSchematicId()).orElse(null);
        if (schematic == null) {
            library.removeJob(job);
            Messages.actionBar(player, "message.wrights.copy_empty");
            return;
        }
        ControllerData data = ControllerData.from(stack);
        UUID previous = data.schematicId().orElse(null);
        if (previous != null && !previous.equals(schematic.id()) && !library.isInUse(previous)) {
            library.removeSchematic(previous);
        }
        ControllerData.write(stack, data.withSchematic(schematic.id(), schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), schematic.blockCount()));
        if (job.skippedSpecial() > 0) {
            JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved_skipped", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()), job.skippedSpecial()));
        } else {
            JobAnnouncements.announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ())));
        }
        library.removeJob(job);
    }

    private static void tickPaste(MinecraftServer server, SchematicLibrary library, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner());
        Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
        if (level == null || schematic == null) {
            DroneFleet.discardDrone(server, job);
            library.removeJob(job);
            if (player != null) {
                Messages.actionBar(player, "message.wrights.paste_missing");
            }
            return;
        }
        DroneFleet.shelterLost(level, player, library, job);
        if (player == null) {
            return;
        }
        if (!job.hasCrew() && job.cursor() >= job.total()) {
            finishPaste(server, library, player, job);
            return;
        }
        if (job.paused()) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            return;
        }
        if (instant(player)) {
            CellPlacement.placeAll(level, player, library, job, schematic);
            return;
        }
        if (job.waitKind() == BuildJob.WAIT_ENERGY) {
            int cost = WrightsConfig.energyCost(job, false, CellPlacement.mirrorPlace(job));
            if (!com.wrights.energy.PortEnergy.canFund(level, player, job, cost)) {
                library.setDirty();
                return;
            }
            job.setWaitKind(BuildJob.WAIT_NONE);
            job.setQueued(false);
        }
        if (job.queued()) {
            CrewRoster.topUpQueued(player, library, job);
            if (!job.hasCrew()) {
                job.setWaitKind(DroneFleet.shortOnItems(player, library, job) ? BuildJob.WAIT_ITEMS : BuildJob.WAIT_NONE);
                return;
            }
        }
        if (CrewRoster.holdWithoutDrones(player, library, job)) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            return;
        }
        if (job.hasCrew()) {
            if (!inReach(player, library, job)) {
                job.setWaitKind(BuildJob.WAIT_NONE);
                if (!releaseCrewOutOfRange(player, library, job)) {
                    ShapeJobs.holdCrewFlyers(level, job);
                }
                return;
            }
            if (job.waitKind() != BuildJob.WAIT_ITEMS) {
                job.workedTick();
            }
            tickCrew(level, player, library, job, schematic);
            return;
        }
        if (!inReach(player, library, job)) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            if (!releaseCrewOutOfRange(player, library, job)) {
                ShapeJobs.holdCrewFlyers(level, job);
            }
            return;
        }
        if (job.waitKind() != BuildJob.WAIT_ITEMS) {
            job.workedTick();
        }
        int at = WorkPicker.nextSolo(level, player, schematic, job);
        if (at == SUPPORT_WAIT) {
            job.setWaitKind(BuildJob.WAIT_FLOOR);
            library.setDirty();
            return;
        }
        if (at == NEED_ITEMS) {
            job.setWaitKind(BuildJob.WAIT_ITEMS);
            JobAnnouncements.noteHistory(player, library, job, JobEventLog.STALLED_ITEMS, Component.translatable("history.wrights.stalled_items").getString());
            DroneFleet.parkSolo(level, player, job);
            DroneFleet.releaseStalledHome(player, library, job);
            library.setDirty();
            return;
        }
        if (at < 0 || job.cursor() >= job.total() || job.cursor() >= schematic.blockCount()) {
            finishPaste(server, library, player, job);
            return;
        }
        Schematic.Entry next = schematic.entry(at);
        BlockPos nextPos = job.min().offset(next.x(), next.y(), next.z());
        BlockPos stand = HoverWork.pasteStand(level, player, schematic, job, null, at);
        if (stand == null) {
            stand = nextPos;
        }
        DroneEntity drone = DroneFleet.ensureDrone(level, player, job, stand);
        if (drone == null) {
            if (job.tickMessage()) {
                noteChunk(player, library, job);
            }
            return;
        }
        // Place jobs show the block right away; mine jobs only show haul after the break.
        ItemStack soloShow = job.mining() ? ItemStack.EMPTY : stackFor(schematic, at);
        if (HoverWork.approachStand(drone, null, stand, job)) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_NONE);
            library.setDirty();
            return;
        }
        drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_NONE);
        // Same actions/tick budget as crew (place / mine / void) — do not clamp to blocksPerTick.
        int budget = Math.max(1, WrightTier.actionsPerTick(drone.fabricator()));
        int burst = 0;
        for (int i = 0; i < budget; i++) {
            int index = i == 0 ? at : WorkPicker.nextSolo(level, player, schematic, job);
            if (index == SUPPORT_WAIT) {
                job.setWaitKind(BuildJob.WAIT_FLOOR);
                drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_FLOOR);
                break;
            }
            if (index == NEED_ITEMS) {
                job.setWaitKind(BuildJob.WAIT_ITEMS);
                drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_ITEMS);
                DroneFleet.parkSolo(level, player, job);
                DroneFleet.releaseStalledHome(player, library, job);
                break;
            }
            if (index < 0) {
                break;
            }
            Schematic.Entry entry = schematic.entry(index);
            BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
            if (!job.mining()) {
                ItemStack aiming = BlockFilters.icon(entry.state());
                if (!aiming.isEmpty()) {
                    soloShow = aiming;
                }
            }
            drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_NONE);
            if (i == 0) {
                if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > PLACE_NEAR_SQR) {
                break;
            }
            } else if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, drone.fabricator())) {
                break;
            }
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                WorkPicker.markSolo(job, index);
                continue;
            }
            if (WorkPicker.falling(level, schematic, job, entry) == Falling.SKIP) {
                CellPlacement.giveUpCell(job, entry, index);
                WorkPicker.markSolo(job, index);
                continue;
            }
            PlaceResult placed = CellPlacement.actOne(level, player, job, pos, entry, false, null);
            if (placed == PlaceResult.PAUSE) {
                job.setWaitKind(BuildJob.WAIT_CHUNK);
                drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_CHUNK);
                if (job.tickMessage()) {
                    noteChunk(player, library, job);
                }
                library.setDirty();
                return;
            }
            if (placed == PlaceResult.WAIT) {
                job.setWaitKind(BuildJob.WAIT_ITEMS);
                drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_ITEMS);
                break;
            }
            if (placed == PlaceResult.ENERGY) {
                DroneFleet.stallForEnergy(level, player, library, job);
                return;
            }
            WorkPicker.markSolo(job, index);
            burst++;
        }
        if (burst > 0) {
            job.operationOne();
        }
        if (job.waitKind() != BuildJob.WAIT_ITEMS && job.waitKind() != BuildJob.WAIT_FLOOR) {
            job.setWaitKind(BuildJob.WAIT_NONE);
        }
        library.setDirty();
        if (job.cursor() >= job.total()) {
            finishPaste(server, library, player, job);
        }
    }

    private static void tickUndo(MinecraftServer server, SchematicLibrary library, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner());
        if (level == null) {
            DroneFleet.discardDrone(server, job);
            archive(library, job, true);
            return;
        }
        DroneFleet.shelterLost(level, player, library, job);
        if (player == null || job.paused()) {
            return;
        }
        if (instant(player)) {
            CellPlacement.undoAll(level, server, library, player, job);
            return;
        }
        if (CrewRoster.holdWithoutDrones(player, library, job)) {
            return;
        }
        if (job.hasCrew()) {
            if (!inReach(player, library, job)) {
                return;
            }
            job.workedTick();
            tickUndoCrew(level, player, library, job);
            return;
        }
        if (!inReach(player, library, job)) {
            return;
        }
        job.workedTick();
        if (!job.hasRefunds()) {
            finishUndo(server, library, player, job);
            return;
        }
        int near = HoverWork.nearestRefund(job, job.lastDronePos() != null
                ? Vec3.atCenterOf(job.lastDronePos())
                : Vec3.atCenterOf(job.lastRefundPos()));
        if (near < 0) {
            finishUndo(server, library, player, job);
            return;
        }
        BuildJob.Refund peek = job.refunds().get(near);
        BlockPos stand = job.min().offset(peek.x(), peek.y(), peek.z());
        DroneEntity drone = DroneFleet.ensureDrone(level, player, job, stand);
        if (drone == null) {
            if (job.tickMessage()) {
                noteChunk(player, library, job);
            }
            return;
        }
        if (HoverWork.approachStand(drone, null, stand, job)) {
            return;
        }
        if (HoverWork.burstRemove(level, player, library, job, null, drone, stand) == HoverWork.Result.CHUNK) {
                    if (job.tickMessage()) {
                        noteChunk(player, library, job);
                    }
                    return;
                }
        if (!job.hungFalls().isEmpty()) {
            CellPlacement.scrubHangRegion(level, job);
        }
        if (!job.hasRefunds()) {
            finishUndo(server, library, player, job);
        } else if (job.tickMessage()) {
            Messages.actionBar(player, "message.wrights.undo_progress", job.undoTotal() - job.refundCount(), job.undoTotal(), job.returned());
        }
    }

    static void removeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, BuildJob.Refund refund, boolean quiet) {
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() != refund.state().getBlock()) {
            job.skippedOne();
            return;
        }
        if (!quiet) {
            level.levelEvent(2001, pos, Block.getId(current));
            level.playSound(null, pos, current.getSoundType(level, pos, player).getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        BlockState putBack = refund.restore() != null ? refund.restore() : Blocks.AIR.defaultBlockState();
        // Same hang rules as paste: no neighbour shape updates, scrub fall ticks around the cell.
        CellPlacement.writeUndo(level, job, pos, putBack);
        if (putBack.isAir()) {
        FluidState fluid = current.getFluidState();
        if (!fluid.isEmpty()) {
                FluidClearing.clearFluidSpread(level, pos, fluid.getType(), job);
                // Fluid cleanup can re-schedule falls — scrub hung sand again.
                CellPlacement.scrubHangAround(level, pos);
        }
        }
        // Creative / unpaid placements skip returns; paid Survival placements get items back.
        if (refund.paid()) {
        Item item = BlockFilters.payment(refund.state());
            if (item != null) {
                JobSupply.giveBack(level, player, job, item);
            job.returnedOne();
        }
            for (int extraId : refund.extras()) {
                Item extra = Item.byId(extraId);
                if (extra != null && extra != Items.AIR) {
                    JobSupply.giveBack(level, player, job, extra);
                    job.returnedOne();
                }
            }
        }
    }

    static void finishUndo(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        ServerLevel finishLevel = server.getLevel(job.dimension());
        if (finishLevel != null) {
            // Undo removals can re-schedule fall ticks a tick later — clear hung sand one last time.
            CellPlacement.scrubHangRegion(finishLevel, job);
        }
        DroneFleet.sendHome(server, player, job);
        int removed = Math.max(0, job.undoTotal() - job.skipped() - job.refundCount());
        int returned = job.returned();
        job.markUndone();
        archive(library, job, false);
        BlueprintNetwork.clearJobHologram(player, job.id());
        BlueprintNetwork.syncJobs(player);
        Component detail = removed <= 0
                ? Component.translatable("message.wrights.undo_none")
                : Component.translatable("hud.wrights.done_undo", removed, returned);
        JobAnnouncements.announce(
                player, library, job, "hud.wrights.task_undo", JobAnnouncements.withOperations(detail, job));
    }

    static void finishPaste(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        ServerLevel finishLevel = server.getLevel(job.dimension());
        if (finishLevel != null && !job.mining()) {
            // Drop any fall ticks neighbour places scheduled during the paste so hung sand stays.
            CellPlacement.scrubHangRegion(finishLevel, job);
        }
        DroneFleet.sendHome(server, player, job);
        int placed = job.placed();
        int skipped = job.skipped();
        archive(library, job, false);
        Component detail;
        if (job.mining()) {
            BlueprintNetwork.clearJobHologram(player, job.id());
            BlueprintNetwork.syncJobs(player);
            if (placed == 0 && skipped > 0) {
                detail = Component.translatable("hud.wrights.done_mined", placed, skipped);
            } else if (placed == 0) {
                detail = Component.translatable("message.wrights.mine_none");
            } else {
                detail = Component.translatable("hud.wrights.done_mined", placed, skipped);
            }
            JobAnnouncements.announce(player, library, job, null, JobAnnouncements.withOperations(detail, job));
            return;
        }
        library.schematic(job.schematicId())
                .filter(schematic -> schematic.ephemeral() && com.wrights.blueprint.Mirror.marksBlocks(schematic.name()))
                .ifPresentOrElse(
                        schematic -> com.wrights.network.BlueprintNetwork.showJobHologram(player, job, schematic),
                        () -> com.wrights.network.BlueprintNetwork.clearJobHologram(player, job.id()));
        com.wrights.network.BlueprintNetwork.syncJobs(player);
        if (placed == 0 && skipped > 0) {
            detail = Component.translatable("message.wrights.all_skipped");
        } else if (placed == 0 && job.kind() == BuildJob.Kind.SHAPE) {
            // Shape OUT cells advance with no skip count — empty membership used to read as "already built".
            detail = Component.translatable("message.wrights.shape_nowhere");
        } else if (placed == 0) {
            detail = Component.translatable("message.wrights.already_built");
        } else {
            detail = Component.translatable("hud.wrights.done_placed", placed, skipped);
        }
        JobAnnouncements.announce(player, library, job, null, JobAnnouncements.withOperations(detail, job));
    }

    static boolean silkTouch(MinecraftServer server, ItemStack tool) {
        if (server == null || tool == null || tool.isEmpty()) {
            return false;
        }
        var silk = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH);
        return silk.isPresent() && tool.getEnchantments().getLevel(silk.get()) > 0;
    }

    /** Temporary silk-touch pick used when Exchange returns the replaced blocks. */
    static ItemStack silkTouchPick(MinecraftServer server) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        if (server == null) {
            return pick;
        }
        var silk = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH);
        if (silk.isPresent()) {
            pick.enchant(silk.get(), 1);
        }
        return pick;
    }

    static void noteChunk(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (job.hold() != BuildJob.HOLD_CHUNK) {
            job.noteHold(BuildJob.HOLD_CHUNK, null);
            library.setDirty();
            BlueprintNetwork.syncJobs(player);
        }
        if (job.tickMessage()) {
            Messages.actionBar(player, "message.wrights.paused_chunk");
        }
    }

    private static boolean nearSite(ServerPlayer player, BuildJob job) {
        BlockPos[] box = jobBox(job);
        return WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
    }

    /** The build's block box, stretched to include the anchor the player aims at. */
    static BlockPos[] jobBox(BuildJob job) {
        BlockPos min = job.min();
        BlockPos far = min.offset(Math.max(0, job.boxSizeX() - 1), Math.max(0, job.boxSizeY() - 1), Math.max(0, job.boxSizeZ() - 1));
        BlockPos anchor = job.anchor();
        return new BlockPos[] {
                new BlockPos(
                        Math.min(min.getX(), Math.min(far.getX(), anchor.getX())),
                        Math.min(min.getY(), Math.min(far.getY(), anchor.getY())),
                        Math.min(min.getZ(), Math.min(far.getZ(), anchor.getZ()))),
                new BlockPos(
                        Math.max(min.getX(), Math.max(far.getX(), anchor.getX())),
                        Math.max(min.getY(), Math.max(far.getY(), anchor.getY())),
                        Math.max(min.getZ(), Math.max(far.getZ(), anchor.getZ())))
        };
    }

    /** Block crews keep working from the wrightport. Personal crews wait on the player. */
    static boolean inReach(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        boolean personal = !job.hasCrew();
        boolean block = false;
        boolean blockOk = true;
        BlockPos badPort = null;
        if (job.hasCrew()) {
            personal = false;
            for (BuildJob.Hand hand : job.hands()) {
                if (hand.homePort == null) {
                    personal = true;
                    continue;
                }
                block = true;
                BlockPos[] box = jobBox(job);
                if (!WrightHomes.portCoversArea(job.dimension(), hand.homePort, job.dimension(), box[0], box[1])) {
                    blockOk = false;
                    badPort = hand.homePort;
                }
            }
        }
        int reason = BuildJob.HOLD_NONE;
        String message = null;
        BlockPos shown = null;
        if (block && !blockOk) {
            reason = BuildJob.HOLD_PORT_RANGE;
            message = "message.wrights.paused_port_range";
            shown = badPort;
        } else if (personal && !player.level().dimension().equals(job.dimension())) {
            reason = BuildJob.HOLD_DIMENSION;
            message = "message.wrights.other_dimension";
        } else if (personal && !nearSite(player, job)) {
            reason = BuildJob.HOLD_PLAYER_RANGE;
            message = "message.wrights.out_of_range";
        }
        if (reason == BuildJob.HOLD_NONE) {
            if (job.hold() != BuildJob.HOLD_NONE && !job.paused()) {
                job.clearHold();
                library.setDirty();
                BlueprintNetwork.syncJobs(player);
            }
            return true;
        }
        boolean changed = job.hold() != reason;
        job.noteHold(reason, shown);
        if (reason == BuildJob.HOLD_PORT_RANGE && !job.paused()) {
            job.setPaused(true);
            changed = true;
            if (shown != null) {
                CrewRoster.tellPortAt(player, player.getServer().getLevel(job.dimension()), shown, message, "message.wrights.paused_port_range_named");
            }
            JobAnnouncements.noteHistory(player, library, job, JobEventLog.PAUSED, Component.translatable("history.wrights.paused_port").getString());
            BlueprintNetwork.syncJobs(player);
        } else if (reason != BuildJob.HOLD_PLAYER_RANGE && reason != BuildJob.HOLD_DIMENSION && job.tickMessage()) {
            // Player-range / wrong-dimension: crew docks silently and the job keeps its quota.
            Messages.actionBar(player, message);
        }
        if (changed) {
            library.setDirty();
            if (reason != BuildJob.HOLD_PORT_RANGE) {
                BlueprintNetwork.syncJobs(player);
            }
        }
        return false;
    }

    /**
     * Personal (or mixed) crew left player range: fly everyone home, drop them from the crew so they
     * are free for other jobs, keep {@link BuildJob#crewQuota()} so the card still shows Assigned N,
     * and queue the job to refill when someone is in range again. No Manage camera / hangar dock packet.
     *
     * @return true when this hold was handled (caller should not freeze flyers in place)
     */
    static boolean releaseCrewOutOfRange(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (player == null || library == null || job == null) {
            return false;
        }
        int hold = job.hold();
        if (hold != BuildJob.HOLD_PLAYER_RANGE && hold != BuildJob.HOLD_DIMENSION) {
            return false;
        }
        if (!job.hasCrew() && job.crewUnits().isEmpty() && job.droneId() == null) {
            if (!job.queued() && job.crewQuota() > 0) {
                job.setQueued(true);
                library.setDirty();
            }
            return true;
        }
        int recalled = CrewRoster.assignedCount(job);
        int quota = Math.max(job.crewQuota(), recalled);
        job.rememberCrewQuota(quota);
        job.setCrewQuota(Math.max(1, quota));
        DroneFleet.sendHome(player.getServer(), player, job);
        job.clearAssigned();
        job.setQueued(true);
        job.setPaused(false);
        job.clearHold();
        library.setDirty();
        BlueprintNetwork.syncJobs(player);
        if (recalled > 0) {
            Component header = Component.translatable("hud.wrights.wrights_returned");
            Component name = Component.literal(JobTitles.jobCardTitle(job, library));
            Component detail = hold == BuildJob.HOLD_DIMENSION
                    ? Component.translatable("hud.wrights.wrights_returned_dimension", recalled)
                    : Component.translatable("hud.wrights.wrights_returned_range", recalled);
            BlueprintNetwork.jobDone(player, header, name, detail, -1);
            JobAnnouncements.noteHistory(
                    player,
                    library,
                    job,
                    hold == BuildJob.HOLD_DIMENSION ? JobEventLog.WRONG_DIMENSION : JobEventLog.OUT_OF_RANGE,
                    detail.getString());
        }
        return true;
    }

    private static boolean ready(ServerPlayer player, ServerLevel level, BlockPos pos, BuildJob job, boolean copy) {
        if (!player.level().dimension().equals(job.dimension())) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.other_dimension");
            }
            return false;
        }
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) {
            if (job.tickMessage()) {
                Messages.actionBar(player, copy ? "message.wrights.copy_out_of_range" : "message.wrights.out_of_range");
            }
            return false;
        }
        return true;
    }

    enum PlaceResult {
        DONE,
        PAUSE,
        WAIT,
        ENERGY
    }

    /** A falling block is not ready yet, so the wright should keep working and come back. */
    static final int SUPPORT_WAIT = -3;
    /** Every block this wright can reach needs an item its supply does not have yet. */
    static final int NEED_ITEMS = -4;

    static boolean instant(ServerPlayer player) {
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            return false;
        }
        return com.wrights.blueprint.BlueprintSessions.get(player).instantJobs();
    }

    static void launched(ServerPlayer player, BuildJob job) {
        int sent = CrewRoster.assignedCount(job);
        if (sent > 1) {
            Messages.actionBar(player, "message.wrights.launched_crew", sent);
        } else {
            Messages.actionBar(player, "message.wrights.launched");
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        JobAnnouncements.noteHistory(player, library, job, JobEventLog.LAUNCHED, launchedHistoryDetail(player, job, sent));
    }

    /** Queued for crew in range — action bar + History with assigned count. */
    static void noteQueued(ServerPlayer player, BuildJob job, int assigned) {
        int wanted = Math.max(1, assigned);
        Messages.actionBar(player, "message.wrights.queued", wanted);
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        JobAnnouncements.noteHistory(
                player,
                library,
                job,
                JobEventLog.QUEUED,
                Component.translatable(
                        "history.wrights.queued_detail",
                        wanted,
                        Math.max(1, job.total())).getString());
    }

    private static String launchedHistoryDetail(ServerPlayer player, BuildJob job, int sent) {
        int blocks = Math.max(1, job.total());
        int assigned = Math.max(job.crewQuota(), sent);
        if (instant(player) || sent <= 0) {
            return Component.translatable("history.wrights.launched_detail_solo", blocks).getString();
        }
        if (assigned > sent) {
            return Component.translatable("history.wrights.launched_detail_partial", sent, assigned, blocks).getString();
        }
        if (sent == 1) {
            return Component.translatable("history.wrights.launched_detail_one", blocks).getString();
        }
        return Component.translatable("history.wrights.launched_detail", sent, blocks).getString();
    }

    private static void tickCrew(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job, Schematic schematic) {
        boolean working = false;
        boolean scanning = false;
        boolean waiting = false;
        boolean waitingChunk = false;
        boolean waitingItems = false;
        int[] skipBudget = {CREW_SKIP_BUDGET};
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.sheltering) {
                working = true;
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_SHELTER);
                continue;
            }
            hand.waitingOnOthers = false;
            int index = WorkPicker.nextWork(level, player, schematic, job, hand, skipBudget);
            if (index == -2) {
                scanning = true;
                hand.aim = -1;
                // Stay on site while the scan catches up — do not send the wright home.
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_NONE);
                continue;
            }
            if (index == SUPPORT_WAIT) {
                waiting = true;
                hand.awaitingItems = false;
                hand.waitingOnOthers = true;
                hand.idle = false;
                hand.aim = -1;
                // Stay near the build so this wright can place as soon as the support lands.
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_FLOOR);
                continue;
            }
            if (index == NEED_ITEMS) {
                waiting = true;
                waitingItems = true;
                hand.aim = -1;
                DroneFleet.parkForItems(level, player, hand);
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_ITEMS);
                continue;
            }
            if (index < 0) {
                // Own share is empty — help another wright, or wait on site for held blocks.
                int borrowed = WorkPicker.borrowWork(level, player, schematic, job, hand, true);
                if (borrowed >= 0) {
                    index = borrowed;
                } else {
                    hand.awaitingItems = false;
                    hand.idle = true;
                    hand.aim = -1;
                    publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_IDLE);
                    continue;
                }
            }
            index = WorkPicker.uncrowd(level, player, schematic, job, hand, index);
            if (index < 0) {
                hand.awaitingItems = false;
                hand.idle = true;
                hand.aim = -1;
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_IDLE);
                continue;
            }
            WorkPicker.claimAim(job, hand, index);
            hand.awaitingItems = false;
            hand.idle = false;
            working = true;
            int handStatus = job.paused() ? DroneEntity.WAIT_PAUSED : DroneEntity.WAIT_NONE;
            Schematic.Entry next = schematic.entry(index);
            BlockPos nextPos = job.min().offset(next.x(), next.y(), next.z());
            BlockPos stand = HoverWork.pasteStand(level, player, schematic, job, hand, index);
            if (stand == null) {
                stand = nextPos;
            }
            DroneEntity drone = DroneFleet.ensureHand(level, player, job, hand, stand);
            if (drone != null) {
                drone.setParts(hand.parts());
            }
            if (drone == null) {
                waitingChunk = true;
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_CHUNK);
                continue;
            }
            if (HoverWork.approachStand(drone, hand, stand)) {
                publishHandStatus(level, player, schematic, job, hand, handStatus);
                continue;
            }
            int budget = CellPlacement.actionBudget(hand);
            int burst = 0;
            for (int n = 0; n < budget; n++) {
                int at = WorkPicker.nextWork(level, player, schematic, job, hand, skipBudget);
                if (at == -2) {
                    scanning = true;
                    break;
                }
                if (at == SUPPORT_WAIT) {
                    waiting = true;
                    hand.waitingOnOthers = true;
                    hand.aim = -1;
                    handStatus = DroneEntity.WAIT_FLOOR;
                    break;
                }
                if (at == NEED_ITEMS) {
                    waiting = true;
                    waitingItems = true;
                    hand.aim = -1;
                    handStatus = DroneEntity.WAIT_ITEMS;
                    DroneFleet.parkForItems(level, player, hand);
                    break;
                }
                if (at < 0) {
                    at = WorkPicker.borrowWork(level, player, schematic, job, hand, true);
                    if (at < 0) {
                        hand.idle = true;
                        hand.aim = -1;
                        handStatus = DroneEntity.WAIT_IDLE;
                        break;
                    }
                }
                at = WorkPicker.uncrowd(level, player, schematic, job, hand, at);
                if (at < 0) {
                    hand.idle = true;
                    hand.aim = -1;
                    handStatus = DroneEntity.WAIT_IDLE;
                    break;
                }
                Schematic.Entry entry = schematic.entry(at);
                BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
                // First cell may be near the stand foot; further cells use ops-reach AOE.
                if (n == 0) {
                    if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > PLACE_NEAR_SQR) {
                        WorkPicker.claimAim(job, hand, at);
                    break;
                }
                } else if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, ShapeJobs.handFabricator(hand))) {
                    WorkPicker.claimAim(job, hand, at);
                    break;
                }
                WorkPicker.claimAim(job, hand, at);
                if (entry.secondary() && WorkPicker.supportWaiting(level, job, hand, at, pos, entry)) {
                    waiting = true;
                    hand.waitingOnOthers = true;
                    handStatus = DroneEntity.WAIT_FLOOR;
                    break;
                }
                if (job.outsideClip(entry)) {
                    noteProgress(schematic, job, hand, entry, at);
                    continue;
                }
                PlaceResult placed = CellPlacement.actOne(level, player, job, pos, entry, false, hand);
                if (placed == PlaceResult.PAUSE) {
                    job.setWaitKind(BuildJob.WAIT_CHUNK);
                    waitingChunk = true;
                    publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_CHUNK);
                    library.setDirty();
                    if (job.tickMessage()) {
                        noteChunk(player, library, job);
                    }
                    return;
                }
                if (placed == PlaceResult.WAIT) {
                    waitingItems = true;
                    waiting = true;
                    handStatus = DroneEntity.WAIT_ITEMS;
                    DroneFleet.parkForItems(level, player, hand);
                    break;
                }
                if (placed == PlaceResult.ENERGY) {
                    DroneFleet.stallForEnergy(level, player, library, job);
                    return;
                }
                noteProgress(schematic, job, hand, entry, at);
                burst++;
            }
            if (burst > 0) {
                HoverWork.countOp(job, hand, stand);
            }
            publishHandStatus(level, player, schematic, job, hand, handStatus);
        }
        // Neighbour places can re-schedule fall ticks on hung sand a tick or two later — keep clearing them.
        if (!job.hungFalls().isEmpty()) {
            CellPlacement.scrubHangRegion(level, job);
        }
        // Slice sharing walks open counts — only when someone is idle, and not every tick.
        if (level.getGameTime() % 20L == 0L) {
            HandSlices.shareWithIdle(level, job, schematic);
        }
        // Only send spare wrights home when the job has nothing left to place or hold.
        if (!HandSlices.jobHasRemainingWork(level, schematic, job)) {
            HandSlices.releaseIdle(level, player, job, schematic);
        }
        if (!working && !scanning) {
            for (BuildJob.Hand hand : job.hands()) {
                int peek = WorkPicker.nextInLane(level, player, schematic, job, hand);
                if (peek == SUPPORT_WAIT) {
                    waiting = true;
                } else if (peek == NEED_ITEMS) {
                    waiting = true;
                    waitingItems = true;
                } else if (peek >= 0) {
                    working = true;
                    break;
                }
            }
        }
        if (level.getGameTime() % 20L == 0L) {
            HandSlices.refreshHeldHint(level, schematic, job);
        }
        // Large jobs skip per-block ghost packets; refresh the stepped hologram on a throttle instead.
        // Skip while Waiting — the ghost is unchanged and rebuilding 65k-entry holograms freezes Stop/Pause.
        if (job.total() > 8000 && level.getGameTime() % 40L == 0L
                && job.waitKind() != BuildJob.WAIT_ITEMS
                && job.waitKind() != BuildJob.WAIT_FLOOR
                && job.waitKind() != BuildJob.WAIT_CHUNK) {
            BlueprintNetwork.showJobHologram(player, job, schematic);
        }
        int reason = BuildJob.WAIT_NONE;
        if (waitingChunk) {
            reason = BuildJob.WAIT_CHUNK;
        } else if (waitingItems) {
            reason = BuildJob.WAIT_ITEMS;
        } else if (waiting) {
            reason = BuildJob.WAIT_FLOOR;
        }
        job.setWaitKind(reason);
        if (reason == BuildJob.WAIT_ITEMS) {
            JobAnnouncements.noteHistory(player, library, job, JobEventLog.STALLED_ITEMS, Component.translatable("history.wrights.stalled_items").getString());
            DroneFleet.releaseStalledHome(player, library, job);
        }
        if (!working && !scanning && !waiting) {
            finishPaste(level.getServer(), library, player, job);
        } else if (waitingChunk && job.tickMessage()) {
            noteChunk(player, library, job);
        }
    }

    private static void tickUndoCrew(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (!job.hasRefunds()) {
            finishUndo(level.getServer(), library, player, job);
            return;
        }
        boolean working = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.sheltering) {
                working = true;
                continue;
            }
            int index = WorkPicker.refundIndex(level, job, hand);
            if (index < 0) {
                hand.idle = true;
                continue;
            }
            hand.idle = false;
            working = true;
            BuildJob.Refund peek = job.refunds().get(index);
            BlockPos stand = job.min().offset(peek.x(), peek.y(), peek.z());
            DroneEntity drone = DroneFleet.ensureHand(level, player, job, hand, stand);
            if (drone != null) {
                drone.setParts(hand.parts());
            }
            if (drone == null) {
                continue;
            }
            if (HoverWork.approachStand(drone, hand, stand)) {
                continue;
            }
            if (HoverWork.burstRemove(level, player, library, job, hand, drone, stand) == HoverWork.Result.CHUNK) {
                        if (job.tickMessage()) {
                            noteChunk(player, library, job);
                        }
                        return;
                    }
                }
        if (!job.hungFalls().isEmpty()) {
            CellPlacement.scrubHangRegion(level, job);
            }
        HandSlices.shareUndo(job);
        if (!working) {
            for (BuildJob.Hand hand : job.hands()) {
                if (WorkPicker.refundIndex(level, job, hand) >= 0) {
                    working = true;
                    break;
                }
            }
        }
        if (!working || !job.hasRefunds()) {
            finishUndo(level.getServer(), library, player, job);
        } else if (job.tickMessage()) {
            Messages.actionBar(player, "message.wrights.undo_progress", job.undoTotal() - job.refundCount(), job.undoTotal(), job.returned());
        }
    }

    /** Push current / next block and wait reason onto the flying wright for hover tooltips. */
    private static void publishHandStatus(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, int waitReason) {
        if (hand.entityId == null || !(level.getEntity(hand.entityId) instanceof DroneEntity drone) || !drone.isAlive()) {
            return;
        }
        int reason = waitReason;
        if (job.paused() && reason == DroneEntity.WAIT_NONE) {
            reason = DroneEntity.WAIT_PAUSED;
        }
        if (reason == DroneEntity.WAIT_IDLE || reason == DroneEntity.WAIT_HOME || reason == DroneEntity.WAIT_SHELTER) {
            drone.setWorkStatus(ItemStack.EMPTY, ItemStack.EMPTY, reason);
            return;
        }
        // Refresh every few ticks so the hanging place/mine block tracks the job without a full scan each tick.
        if (reason == drone.waitReason() && (level.getGameTime() + Math.max(0, hand.lane)) % 5L != 0L) {
            return;
        }
        int[] targets = peekHandTargets(level, schematic, job, hand);
        // Mine jobs keep CURRENT empty until harvest fills haul; place jobs show the target immediately.
        ItemStack current = job.mining() ? ItemStack.EMPTY : stackFor(schematic, targets[0]);
        ItemStack next = job.mining() ? ItemStack.EMPTY : stackFor(schematic, targets[1]);
        drone.setWorkStatus(current, next, reason);
    }

    /**
     * First actionable cell this hand owns (or can help with), then the one after it.
     * Does not move cursors. Negative indexes mean none.
     */
    private static int[] peekHandTargets(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        int current = -1;
        int next = -1;
        // Cheap ownership check: only fall back to helping others when no owned open cell is nearby.
        boolean ownShareEmpty = !HandSlices.anyOpen(level, schematic, job, hand);
        int count = schematic.blockCount();
        int from = Math.max(0, Math.min(count, hand.cursor));
        int examined = 0;
        for (int offset = 0; offset < count && examined < CREW_SCAN_CAP && next < 0; offset++) {
            int i = (from + offset) % count;
            examined++;
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                continue;
            }
            if (!ownShareEmpty && !WorkPicker.ownsEntry(job, hand, entry)) {
                continue;
            }
            if (ownShareEmpty && WorkPicker.aimedByOther(job, hand, i)) {
                continue;
            }
            if (WorkPicker.falling(level, schematic, job, entry) != Falling.READY) {
                continue;
            }
            if (current < 0) {
                current = i;
                continue;
            }
            next = i;
        }
        return new int[] { current, next };
    }

    private static ItemStack stackFor(Schematic schematic, int index) {
        if (schematic == null || index < 0 || index >= schematic.blockCount()) {
            return ItemStack.EMPTY;
        }
        return BlockFilters.icon(schematic.entry(index).state());
    }

    static void noteProgress(Schematic schematic, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry, int index) {
        int next = index + 1;
        if (hand.slices.isEmpty()) {
            if (firstForHand(schematic, job, hand, hand.cursor) == index) {
                hand.cursor = next;
                hand.probe = hand.cursor;
            } else if (hand.probe <= index) {
                hand.probe = next;
            }
            return;
        }
        for (BuildJob.Slice slice : hand.slices) {
            if (!slice.contains(entry.x(), entry.y(), entry.z()) || slice.cursor > index) {
                continue;
            }
            if (firstInSlice(schematic, slice, slice.cursor) == index) {
                slice.cursor = next;
                slice.probe = slice.cursor;
            } else if (slice.probe <= index) {
                slice.probe = next;
            }
        }
        int earliest = Integer.MAX_VALUE;
        for (BuildJob.Slice slice : hand.slices) {
            earliest = Math.min(earliest, slice.cursor);
        }
        hand.cursor = earliest == Integer.MAX_VALUE ? next : earliest;
    }

    private static int firstForHand(Schematic schematic, BuildJob job, BuildJob.Hand hand, int from) {
        for (int i = Math.max(0, from); i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (CrewSplit.lane(entry.x(), entry.y(), entry.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane) {
                return i;
            }
        }
        return -1;
    }

    private static int firstInSlice(Schematic schematic, BuildJob.Slice slice, int from) {
        for (int i = Math.max(0, from); i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (slice.contains(entry.x(), entry.y(), entry.z())) {
                return i;
            }
        }
        return -1;
    }

}
