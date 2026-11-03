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
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.blueprint.BlueprintActions;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.WrightsMod;
import com.wrights.drone.DockRecovery;
import com.wrights.drone.WrightTier;
import com.wrights.drone.WrightportPlaces;
import com.wrights.drone.WrightHomes;
import com.wrights.drone.CrewSplit;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;
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
     * Sentinel for a place-reach cluster probe. The legal cells are the operations sphere
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
                discardDrone(server, job);
                library.removeJob(job);
            }
            // Progress is in memory; dirty every second is enough and avoids heavy NBT churn mid-build.
            if (!job.finished() && library.job(job.id()).isPresent() && server.getTickCount() % 20 == 0) {
                library.setDirty();
            }
        }
        lendStocked(server, library);
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
            sendHome(player.getServer(), player, job);
            archive(library, job, true);
        } else {
            discardDrone(player.getServer(), job);
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
            ensureDrone(level, player, job, job.min());
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
            ensureDrone(level, player, job, job.lastRefundPos());
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
        discardDrone(player.getServer(), job);
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
            ensureDrone(level, player, job, origin.pos());
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
            ensureDrone(level, player, job, origin);
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
            ensureDrone(level, player, job, origin);
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
        ItemStack stack = findController(player, job.controllerId());
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
            discardDrone(server, job);
            library.removeJob(job);
            if (player != null) {
                Messages.actionBar(player, "message.wrights.paste_missing");
            }
            return;
        }
        shelterLost(level, player, library, job);
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
            placeAll(level, player, library, job, schematic);
            return;
        }
        if (job.waitKind() == BuildJob.WAIT_ENERGY) {
            int cost = WrightsConfig.energyCost(job, false, mirrorPlace(job));
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
                job.setWaitKind(shortOnItems(player, library, job) ? BuildJob.WAIT_ITEMS : BuildJob.WAIT_NONE);
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
            parkSolo(level, player, job);
            releaseStalledHome(player, library, job);
            library.setDirty();
            return;
        }
        if (at < 0 || job.cursor() >= job.total() || job.cursor() >= schematic.blockCount()) {
            finishPaste(server, library, player, job);
            return;
        }
        Schematic.Entry next = schematic.entry(at);
        BlockPos nextPos = job.min().offset(next.x(), next.y(), next.z());
        DroneEntity drone = ensureDrone(level, player, job, nextPos);
        if (drone == null) {
            if (job.tickMessage()) {
                noteChunk(player, library, job);
            }
            return;
        }
        Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.9D, 0.0D);
        // Place jobs show the block right away; mine jobs only show haul after the break.
        ItemStack soloShow = job.mining() ? ItemStack.EMPTY : stackFor(schematic, at);
        if (drone.launching()) {
            job.setLastDronePos(drone.blockPosition());
            job.setWaitKind(BuildJob.WAIT_NONE);
            drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_NONE);
            library.setDirty();
            return;
        }
        if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
            double step = drone.distanceToSqr(hover) > 64.0D ? 0.85D : 0.4D;
            drone.moveToward(hover, step);
            job.setLastDronePos(drone.blockPosition());
            job.setWaitKind(BuildJob.WAIT_NONE);
            drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_NONE);
            library.setDirty();
            return;
        }
        drone.setWorkStatus(soloShow, ItemStack.EMPTY, DroneEntity.WAIT_NONE);
        int budget = Math.min(WrightsConfig.BLOCKS_PER_TICK.getAsInt(), WrightTier.operationCap(drone.ops()));
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
                parkSolo(level, player, job);
                releaseStalledHome(player, library, job);
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
            } else if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, drone.ops())) {
                break;
            }
            if (job.outsideClip(entry) || WorkPicker.satisfied(level, job, entry)) {
                WorkPicker.markSolo(job, index);
                continue;
            }
            if (WorkPicker.falling(level, schematic, job, entry) == Falling.SKIP) {
                giveUpCell(job, entry, index);
                WorkPicker.markSolo(job, index);
                continue;
            }
            PlaceResult placed = actOne(level, player, job, pos, entry, false, null);
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
                stallForEnergy(level, player, library, job);
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
            discardDrone(server, job);
            archive(library, job, true);
            return;
        }
        shelterLost(level, player, library, job);
        if (player == null || job.paused()) {
            return;
        }
        if (instant(player)) {
            undoAll(level, server, library, player, job);
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
        BlockPos nextPos = job.lastRefundPos();
        DroneEntity drone = ensureDrone(level, player, job, nextPos);
        if (drone == null) {
            if (job.tickMessage()) {
                noteChunk(player, library, job);
            }
            return;
        }
        Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.9D, 0.0D);
        if (drone.launching()) {
            job.setLastDronePos(drone.blockPosition());
            return;
        }
        if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
            double step = drone.distanceToSqr(hover) > 64.0D ? 0.85D : 0.4D;
            drone.moveToward(hover, step);
            job.setLastDronePos(drone.blockPosition());
            return;
        }
        int budget = WrightsConfig.BLOCKS_PER_TICK.getAsInt();
        for (int i = 0; i < budget && job.hasRefunds(); i++) {
            BuildJob.Refund refund = job.pollRefund();
            BlockPos pos = job.min().offset(refund.x(), refund.y(), refund.z());
            if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, drone.ops())) {
                job.restoreRefund(refund);
                break;
            }
            if (!level.isLoaded(pos)) {
                if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                    job.restoreRefund(refund);
                    if (job.tickMessage()) {
                        noteChunk(player, library, job);
                    }
                    return;
                }
                job.skippedOne();
                continue;
            }
            removeOne(level, player, job, pos, refund, false);
        }
        if (!job.hasRefunds()) {
            finishUndo(server, library, player, job);
        } else if (job.tickMessage()) {
            Messages.actionBar(player, "message.wrights.undo_progress", job.undoTotal() - job.refundCount(), job.undoTotal(), job.returned());
        }
    }

    private static void removeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, BuildJob.Refund refund, boolean quiet) {
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
        level.setBlock(pos, putBack, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        if (putBack.isAir()) {
            FluidState fluid = current.getFluidState();
            if (!fluid.isEmpty()) {
                FluidClearing.clearFluidSpread(level, pos, fluid.getType(), job);
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

    private static void finishUndo(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        sendHome(server, player, job);
        int removed = Math.max(0, job.undoTotal() - job.skipped() - job.refundCount());
        int returned = job.returned();
        job.markUndone();
        archive(library, job, false);
        BlueprintNetwork.clearJobHologram(player, job.id());
        BlueprintNetwork.syncJobs(player);
        Component detail = removed <= 0
                ? Component.translatable("message.wrights.undo_none")
                : Component.translatable("hud.wrights.done_undo", removed, returned);
        JobAnnouncements.announce(player, library, job, "hud.wrights.task_undo", detail);
    }

    static void finishPaste(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        ServerLevel finishLevel = server.getLevel(job.dimension());
        if (finishLevel != null && !job.mining()) {
            // Drop any fall ticks neighbour places scheduled during the paste so hung sand stays.
            scrubHangRegion(finishLevel, job);
        }
        sendHome(server, player, job);
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
    private static ItemStack silkTouchPick(MinecraftServer server) {
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
        sendHome(player.getServer(), player, job);
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
                parkForItems(level, player, hand);
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
            DroneEntity drone = ensureHand(level, player, job, hand, nextPos);
            if (drone != null) {
                drone.setTier(hand.speed);
                drone.setOps(ShapeJobs.handOps(hand));
            }
            if (drone == null) {
                waitingChunk = true;
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_CHUNK);
                continue;
            }
            Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.85D + (hand.lane % 2) * 0.45D, 0.0D);
            if (drone.launching()) {
                hand.lastPos = drone.blockPosition();
                publishHandStatus(level, player, schematic, job, hand, handStatus);
                continue;
            }
            if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
                drone.moveToward(hover, flyStep(hand.speed, drone.distanceToSqr(hover)));
                hand.lastPos = drone.blockPosition();
                publishHandStatus(level, player, schematic, job, hand, handStatus);
                continue;
            }
            int budget = placeBudget(hand);
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
                    parkForItems(level, player, hand);
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
                // Ops reach is tight (0–2); fly-arrive allows ~1.5 from hover. Near the cell, place it.
                // Farther AOE candidates wait for another hover.
                if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, ShapeJobs.handOps(hand))
                        && drone.distanceToSqr(Vec3.atCenterOf(pos)) > PLACE_NEAR_SQR) {
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
                PlaceResult placed = actOne(level, player, job, pos, entry, false, hand);
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
                    parkForItems(level, player, hand);
                    break;
                }
                if (placed == PlaceResult.ENERGY) {
                    stallForEnergy(level, player, library, job);
                    return;
                }
                noteProgress(schematic, job, hand, entry, at);
                burst++;
            }
            if (burst > 0 && hand.opStand != index) {
                job.operationOne();
                hand.opStand = index;
            }
            publishHandStatus(level, player, schematic, job, hand, handStatus);
        }
        // Neighbour places can re-schedule fall ticks on hung sand a tick or two later — keep clearing them.
        if (!job.hungFalls().isEmpty()) {
            scrubHangRegion(level, job);
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
            releaseStalledHome(player, library, job);
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
            int index = WorkPicker.refundIndex(job, hand);
            if (index < 0) {
                hand.idle = true;
                continue;
            }
            hand.idle = false;
            working = true;
            BuildJob.Refund peek = job.refunds().get(index);
            BlockPos nextPos = job.min().offset(peek.x(), peek.y(), peek.z());
            DroneEntity drone = ensureHand(level, player, job, hand, nextPos);
            if (drone != null) {
                drone.setTier(hand.speed);
                drone.setOps(ShapeJobs.handOps(hand));
            }
            if (drone == null) {
                continue;
            }
            Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.85D + (hand.lane % 2) * 0.45D, 0.0D);
            if (drone.launching()) {
                hand.lastPos = drone.blockPosition();
                continue;
            }
            if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
                drone.moveToward(hover, flyStep(hand.speed, drone.distanceToSqr(hover)));
                hand.lastPos = drone.blockPosition();
                continue;
            }
            int budget = placeBudget(hand);
            for (int n = 0; n < budget; n++) {
                int at = WorkPicker.refundIndex(job, hand);
                if (at < 0) {
                    hand.idle = true;
                    break;
                }
                BuildJob.Refund refund = job.refunds().get(at);
                BlockPos pos = job.min().offset(refund.x(), refund.y(), refund.z());
                if (n == 0) {
                    if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > PLACE_NEAR_SQR) {
                        break;
                    }
                } else if (!ShapeJobs.inOperationReachFromHover(drone.position(), pos, ShapeJobs.handOps(hand))) {
                    break;
                }
                job.takeRefund(at);
                if (!level.isLoaded(pos)) {
                    if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                        job.restoreRefund(at, refund);
                        if (job.tickMessage()) {
                            noteChunk(player, library, job);
                        }
                        return;
                    }
                    job.skippedOne();
                    continue;
                }
                removeOne(level, player, job, pos, refund, false);
            }
        }
        HandSlices.shareUndo(job);
        if (!working) {
            for (BuildJob.Hand hand : job.hands()) {
                if (WorkPicker.refundIndex(job, hand) >= 0) {
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

    static int placeBudget(BuildJob.Hand hand) {
        // One operation is one hover. The tier cap (Netherite 19) is the lag bound;
        // clamping it to blocksPerTick split a 19-block sphere into a second tick and a second op.
        return Math.max(1, WrightTier.operationCap(ShapeJobs.handOps(hand)));
    }

    static double flyStep(int speed, double distanceSqr) {
        double step = WrightTier.flyClose(speed);
        if (distanceSqr > 64.0D) {
            step *= 2.1D;
        }
        return step;
    }

    private static void placeAll(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job, Schematic schematic) {
        int count = Math.min(job.total(), schematic.blockCount());
        boolean[] done = new boolean[count];
        int guard = count + 1;
        boolean progress = true;
        while (progress && guard-- > 0) {
            progress = false;
            for (int i = 0; i < count; i++) {
                if (done[i]) {
                    continue;
                }
                Schematic.Entry entry = schematic.entry(i);
                if (job.outsideClip(entry)) {
                    done[i] = true;
                    progress = true;
                    continue;
                }
                BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
                if (!level.isLoaded(pos)) {
                    level.getChunkAt(pos);
                }
                PlaceResult placed = actOne(level, player, job, pos, entry, true, null);
                if (placed == PlaceResult.PAUSE) {
                    library.setDirty();
                    return;
                }
                if (placed == PlaceResult.WAIT || placed == PlaceResult.ENERGY) {
                    if (placed == PlaceResult.ENERGY) {
                        job.setWaitKind(BuildJob.WAIT_ENERGY);
                        job.setQueued(true);
                    }
                    continue;
                }
                done[i] = true;
                progress = true;
                // Instant finish has no hover AOE — each successful cell is one operation.
                job.operationOne();
            }
        }
        for (int i = 0; i < count; i++) {
            if (!done[i]) {
                job.skippedOne();
            }
        }
        while (job.cursor() < job.total()) {
            job.advance();
        }
        library.setDirty();
        if (job.cursor() >= job.total()) {
            finishPaste(player.getServer(), library, player, job);
        }
    }

    private static void undoAll(ServerLevel level, MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        while (job.hasRefunds()) {
            BuildJob.Refund refund = job.pollRefund();
            BlockPos pos = job.min().offset(refund.x(), refund.y(), refund.z());
            if (!level.isLoaded(pos)) {
                level.getChunkAt(pos);
            }
            if (!level.isLoaded(pos)) {
                job.skippedOne();
                continue;
            }
            removeOne(level, player, job, pos, refund, true);
        }
        finishUndo(server, library, player, job);
    }

    private static PlaceResult actOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet, BuildJob.Hand hand) {
        if (job.mining()) {
            return mineOne(level, player, job, pos, entry, hand);
        }
        return placeOne(level, player, job, pos, entry, quiet, hand);
    }

    /** Breaks one mirrored block and sends its drops to the player who started the job. */
    private static PlaceResult mineOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, BuildJob.Hand hand) {
        if (level.isOutsideBuildHeight(pos)) {
            job.skippedOne();
            clearPlacedGhost(player, job, entry);
            return PlaceResult.DONE;
        }
        if (!level.isLoaded(pos)) {
            if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                return PlaceResult.PAUSE;
            }
            job.skippedOne();
            clearPlacedGhost(player, job, entry);
            return PlaceResult.DONE;
        }
        if (!level.mayInteract(player, pos) || !com.wrights.blueprint.MirrorLaunch.accepts(level, pos, entry.state().getBlock(), job.mineAny())) {
            job.skippedOne();
            clearPlacedGhost(player, job, entry);
            return PlaceResult.DONE;
        }
        if (!freePlace(player)) {
            int cost = WrightsConfig.energyCost(job, false, false);
            if (cost > 0 && !com.wrights.energy.PortEnergy.tryPay(level, player, hand, cost)) {
                return PlaceResult.ENERGY;
            }
        }
        java.util.List<ItemStack> drops = com.wrights.blueprint.MirrorLaunch.harvest(player, level, pos, job.mineTool(), job.mineVoid());
        showCarry(level, job, hand, drops, null);
        clearPlacedGhost(player, job, entry);
        job.placedOne();
        return PlaceResult.DONE;
    }

    /**
     * Fold this break’s drops into the wright’s haul, then clear the place/mine preview
     * so the under-wright icon switches to what was actually received (mine / exchange void-off).
     */
    private static void showCarry(
            ServerLevel level,
            BuildJob job,
            BuildJob.Hand hand,
            java.util.List<ItemStack> drops,
            DroneEntity actor) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        DroneEntity drone = resolveCargoDrone(level, job, hand, actor);
        if (drone == null) {
            return;
        }
        drone.takeHaul(drops);
        if (!drone.carried().isEmpty()) {
            drone.clearCurrentBlock();
        }
    }

    /** Prefer the wright that just acted, then hand / job flyer, then any job-bound flyer nearby. */
    private static DroneEntity resolveCargoDrone(
            ServerLevel level,
            BuildJob job,
            BuildJob.Hand hand,
            DroneEntity actor) {
        if (actor != null && actor.isAlive()) {
            return actor;
        }
        if (hand != null && hand.entityId != null) {
            Entity existing = level.getEntity(hand.entityId);
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                return drone;
            }
        }
        if (job.droneId() != null) {
            Entity existing = level.getEntity(job.droneId());
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                return drone;
            }
        }
        for (BuildJob.Hand other : job.hands()) {
            if (other.entityId == null) {
                continue;
            }
            Entity existing = level.getEntity(other.entityId);
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                return drone;
            }
        }
        BlockPos at = job.min();
        for (DroneEntity drone : level.getEntitiesOfClass(
                DroneEntity.class,
                new AABB(at).inflate(96.0D),
                entity -> entity.isAlive() && entity.jobId().filter(job.id()::equals).isPresent())) {
            return drone;
        }
        return null;
    }

    /**
     * Under-wright place preview for Shape / Exchange status.
     * Exchange void-off while idle/paused leaves current empty so haul shows after a swap;
     * the fly-to-next path sets the place material again when outbound. Void-on keeps the place icon.
     */
    static ItemStack shapeStatusCargo(BuildJob job, BlockState state, int waitReason) {
        if (state == null) {
            return ItemStack.EMPTY;
        }
        if (job != null && job.exchangeJob() && !job.voidBlocks()
                && (waitReason == DroneEntity.WAIT_NONE || waitReason == DroneEntity.WAIT_PAUSED)) {
            return ItemStack.EMPTY;
        }
        return BlockFilters.icon(state);
    }

    static PlaceResult placeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet) {
        return placeOne(level, player, job, pos, entry, quiet, null, null);
    }

    static PlaceResult placeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet, BuildJob.Hand hand) {
        return placeOne(level, player, job, pos, entry, quiet, hand, null);
    }

    static PlaceResult placeOne(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BlockPos pos,
            Schematic.Entry entry,
            boolean quiet,
            BuildJob.Hand hand,
            DroneEntity actor) {
        if (level.isOutsideBuildHeight(pos)) {
            return skipCell(player, job, entry);
        }
        if (!level.isLoaded(pos)) {
            if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                return PlaceResult.PAUSE;
            }
            return skipCell(player, job, entry);
        }
        if (!canBuild(player, level, pos)) {
            return skipCell(player, job, entry);
        }
        BlockState state = entry.state();
        Item payment = BlockFilters.payment(state);
        java.util.List<Item> extras = WorkPicker.willApplyExtras(player, job, entry)
                ? com.wrights.util.BlockExtras.of(entry)
                : java.util.List.of();
        BlockState existing = level.getBlockState(pos);
        if (existing.equals(state)) {
            applyEntryNbt(level, player, job, pos, entry);
            clearPlacedGhost(player, job, entry);
            return PlaceResult.DONE;
        }
        if (skippedContent(job, entry)) {
            return skipCell(player, job, entry);
        }
        if (keptStored(job, existing, state)) {
            return skipCell(player, job, entry);
        }
        boolean clearFirst = shouldVoid(job, existing, state);
        if (clearFirst && existing.getDestroySpeed(level, pos) < 0.0F) {
            return skipCell(player, job, entry);
        }
        // Void off: leave the world block alone and never revisit this schematic cell.
        if (!clearFirst && !existing.isAir() && !existing.canBeReplaced()) {
            return skipCell(player, job, entry);
        }
        if (entry.secondary()) {
            BlockState below = level.getBlockState(pos.below());
            if (below.getBlock() != state.getBlock()) {
                if (below.isAir() || below.canBeReplaced()) {
                    return PlaceResult.WAIT;
                }
                return skipCell(player, job, entry);
            }
        } else if (level.isLoaded(pos.below()) && !state.canSurvive(level, pos)) {
            // Wrong floor, or cactus against a neighbor — do not place (and do not retry forever).
            return skipCell(player, job, entry);
        } else if (!freePlace(player) && (payment != null || !extras.isEmpty())) {
            if (!WorkPicker.hasPlaceCosts(player, level, job, hand, entry)) {
                return PlaceResult.WAIT;
            }
        }
        // Hang gravity blocks when nothing solid is under them (worldgen-style), instead of
        // waiting forever or refusing the cell.
        boolean suspendFall = false;
        if (BlockFilters.falls(state)) {
            BlockPos below = pos.below();
            if (level.isOutsideBuildHeight(below) || !level.isLoaded(below)) {
                return PlaceResult.WAIT;
            }
            suspendFall = FallingBlock.isFree(level.getBlockState(below));
        }
        int paidEnergy = 0;
        if (!freePlace(player)) {
            int cost = WrightsConfig.energyCost(job, clearFirst, mirrorPlace(job));
            if (cost > 0 && !com.wrights.energy.PortEnergy.tryPay(level, player, hand, cost)) {
                return PlaceResult.ENERGY;
            }
            paidEnergy = cost;
        }
        // Exchange undo needs the block that was swapped out (voided or harvested).
        BlockState restore = job.exchangeJob() && clearFirst && !existing.isAir() && !existing.canBeReplaced()
                ? existing
                : null;
        if (clearFirst) {
            if (job.exchangeJob()) {
                if (job.voidBlocks()) {
                    // Void on: destroy with no returns to the player.
                    voidCell(level, pos);
                } else {
                    // Void off: still swap, but harvest drops back (silk if enabled).
                    BlockState removed = existing;
                    ItemStack tool = job.exchangeSilk() ? silkTouchPick(level.getServer()) : ItemStack.EMPTY;
                    java.util.List<ItemStack> drops =
                            com.wrights.blueprint.MirrorLaunch.harvest(player, level, pos, tool, false);
                    // If drop claim missed entities, still show the swapped-out block on the wright.
                    if (drops.isEmpty()) {
                        ItemStack visual = BlockFilters.icon(removed);
                        if (!visual.isEmpty()) {
                            drops = java.util.List.of(visual);
                        }
                    }
                    showCarry(level, job, hand, drops, actor);
                }
            } else {
                voidCell(level, pos);
            }
            existing = level.getBlockState(pos);
        }

        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        writePlaced(level, pos, state, suspendFall);
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != state.getBlock()) {
            if (paidEnergy > 0) {
                com.wrights.energy.PortEnergy.refund(level, player, hand, paidEnergy);
            }
            if (!after.equals(existing)) {
                level.setBlock(pos, existing, Block.UPDATE_ALL);
            }
            return skipCell(player, job, entry);
        }
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, existing, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            level.setBlock(pos, existing, Block.UPDATE_ALL);
            return skipCell(player, job, entry);
        }
        if (suspendFall) {
            job.noteHungFall(pos.immutable());
        }
        boolean paid = !entry.secondary() && !freePlace(player) && (payment != null || !extras.isEmpty());
        int[] extraIds = BuildJob.Refund.NO_EXTRAS;
        if (paid) {
            if (payment != null) {
                JobSupply.consumeItem(player, level, job, hand, payment);
            }
            if (!extras.isEmpty()) {
                extraIds = new int[extras.size()];
                for (int i = 0; i < extras.size(); i++) {
                    Item extra = extras.get(i);
                    JobSupply.consumeItem(player, level, job, hand, extra);
                    extraIds[i] = Item.getId(extra);
                }
            }
        }
        boolean first = !job.isSettled(entry.x(), entry.y(), entry.z());
        applyEntryNbt(level, player, job, pos, entry);
        job.recordPlaced(entry.x(), entry.y(), entry.z(), after, paid, extraIds, restore);
        clearPlacedGhost(player, job, entry);
        if (!quiet) {
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.levelEvent(2001, pos, Block.getId(state));
        }
        if (first) {
            job.placedOne();
        }
        return PlaceResult.DONE;
    }

    /**
     * Writes a block into the world. Gravity blocks with nothing under them are hung like
     * worldgen: {@link FallingBlock#onPlace} is skipped (NeoForge snapshot capture), clients
     * are notified without neighbour updates, and any fall ticks from later neighbour places
     * during this paste are scrubbed so the sand stays until something updates it after the job.
     */
    private static void writePlaced(ServerLevel level, BlockPos pos, BlockState state, boolean suspendFall) {
        if (!suspendFall) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            // A normal place can schedule fall ticks on already-hung sand beside it — drop those.
            scrubHangAround(level, pos);
            return;
        }
        BlockState old = level.getBlockState(pos);
        boolean capturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = true;
        try {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } finally {
            level.captureBlockSnapshots = capturing;
            if (!level.capturedBlockSnapshots.isEmpty()) {
                level.capturedBlockSnapshots.remove(level.capturedBlockSnapshots.size() - 1);
            }
        }
        // Capture skips markAndNotify — push the new block to clients ourselves.
        level.sendBlockUpdated(pos, old, state, Block.UPDATE_CLIENTS);
        cancelHangAt(level, pos);
        scrubHangAround(level, pos);
    }

    private static void scrubHangAround(ServerLevel level, BlockPos center) {
        cancelHangAt(level, center);
        for (Direction direction : Direction.values()) {
            cancelHangAt(level, center.relative(direction));
        }
    }

    private static void scrubHangRegion(ServerLevel level, BuildJob job) {
        for (long packed : job.hungFalls()) {
            cancelHangAt(level, BlockPos.of(packed));
        }
    }

    private static void cancelHangAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof FallingBlock)) {
            return;
        }
        BlockPos below = pos.below();
        if (level.isOutsideBuildHeight(below) || !level.isLoaded(below) || !FallingBlock.isFree(level.getBlockState(below))) {
            return;
        }
        cancelFallTick(level, pos, block);
    }

    @SuppressWarnings("unchecked")
    private static void cancelFallTick(ServerLevel level, BlockPos pos, Block block) {
        if (!(level.getChunkAt(pos).getBlockTicks() instanceof LevelChunkTicks<?> ticks)) {
            return;
        }
        ((LevelChunkTicks<Block>) ticks).removeIf(tick -> tick.pos().equals(pos) && tick.type() == block);
    }

    /**
     * Permanent skip for this job (occupied without void, can't survive, etc.).
     * Soft waits (need floor first) use {@link Falling#WAIT} and do not call this.
     */
    private static PlaceResult skipCell(ServerPlayer player, BuildJob job, Schematic.Entry entry) {
        boolean first = !job.isSettled(entry.x(), entry.y(), entry.z());
        clearPlacedGhost(player, job, entry);
        if (first) {
            job.skippedOne();
        }
        return PlaceResult.DONE;
    }

    /** Permanent give-up from falling/ground rules. Count once; never retry this cell. */
    static void giveUpCell(BuildJob job, Schematic.Entry entry, int index) {
        if (job.retire(index)) {
            job.skippedOne();
        }
        job.settleGhost(entry.x(), entry.y(), entry.z());
    }

    /** The cell is filled, so the preview ghost for it is finished. */
    private static void clearPlacedGhost(ServerPlayer player, BuildJob job, Schematic.Entry entry) {
        job.settleGhost(entry.x(), entry.y(), entry.z());
        // Per-block packets on a 20k+ job flood the client; the throttled hologram refresh covers those.
        if (job.total() > 8000) {
            return;
        }
        com.wrights.network.BlueprintNetwork.dropJobGhost(player, job.id(), entry.x(), entry.y(), entry.z());
    }

    static boolean skippedContent(BuildJob job, Schematic.Entry entry) {
        net.minecraft.world.level.block.state.BlockState state = entry.state();
        if (!job.placeFluids() && BlockFilters.isFluid(state)) {
            return true;
        }
        return !job.placeNbtBlocks() && state.hasBlockEntity();
    }

    /**
     * A placed machine starts like a hand-placed one. Copied settings are applied on top.
     * A full copy replaces that, and only a creative player may place one.
     */
    private static void applyEntryNbt(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        Item item = be.getBlockState().getBlock().asItem();
        if (item != Items.AIR) {
            try {
                be.applyComponentsFromItemStack(new ItemStack(item));
            } catch (RuntimeException ignored) {
                // A mod item with a broken default component must not abort the place.
            }
        }
        if (entry.nbt() == null) {
            return;
        }
        net.minecraft.nbt.CompoundTag data = entry.nbt();
        boolean full = job.placeAll() && entry.nbtFull() && WrightsConfig.copyAll() && creative(player);
        if (!full) {
            if (!job.placeSettings() || !WrightsConfig.copySettings() || !WrightsConfig.settingsAllowed(be.getBlockState().getBlock())) {
                return;
            }
            data = com.wrights.util.BlockEntityNbt.settings(data);
            if (data == null) {
                return;
            }
        }
        try {
            be.loadWithComponents(data, level.registryAccess());
            be.setChanged();
        } catch (RuntimeException ignored) {
            // Bad or version-mismatched settings NBT must not break the place.
        }
    }

    static boolean creative(ServerPlayer player) {
        return player.getAbilities().instabuild && !player.isSpectator();
    }

    /** A chest or other stored block stays unless voiding those was turned on. */
    static boolean keptStored(BuildJob job, BlockState existing, BlockState target) {
        return existing.hasBlockEntity() && !existing.equals(target) && !job.voidNbt();
    }

    /** Remove a solid block or a fluid that does not match, when that void option is on. */
    static boolean shouldVoid(BuildJob job, BlockState existing, BlockState target) {
        if (existing.isAir() || existing.equals(target) || existing.canBeReplaced()) {
            return false;
        }
        if (existing.hasBlockEntity() && !job.voidNbt()) {
            return false;
        }
        if (BlockFilters.isFluid(existing)) {
            return job.voidFluids();
        }
        // Exchange always clears matching solids; Void only controls whether drops return.
        if (job.exchangeJob()) {
            return true;
        }
        return job.voidBlocks();
    }

    private static void voidCell(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container container) {
            container.clearContent();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }

    static boolean canBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isSpectator()) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        return !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    static boolean mirrorPlace(BuildJob job) {
        return job != null && !job.mining() && !job.exchangeJob()
                && com.wrights.blueprint.Mirror.marksBlocks(job.blueprintName());
    }

    static boolean freePlace(ServerPlayer player) {
        return player.getAbilities().instabuild && !player.isSpectator();
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
        int tier = job.hands().isEmpty() ? 1 : job.hands().get(0).speed;
        drone.setTier(tier);
        drone.setOps(job.hands().isEmpty() ? tier : ShapeJobs.handOps(job.hands().get(0)));
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
            int tier = WrightTier.clamp(hand.speed);
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
            BlockPos docked = DockRecovery.recover(level, player, where, id, tier, ShapeJobs.handOps(hand), name);
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
    private static void lendStocked(MinecraftServer server, SchematicLibrary library) {
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
    private static void releaseStalledHome(ServerPlayer player, SchematicLibrary library, BuildJob job) {
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
    private static boolean shortOnItems(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(job.dimension());
        if (level == null || job.schematicId() == null) {
            return false;
        }
        Schematic schematic = library.schematic(job.schematicId()).orElse(null);
        if (schematic == null) {
            return false;
        }
        Owed owed = owed(level, schematic, job);
        if (owed.idle() || owed.open || owed.items.isEmpty()) {
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
            if (open) {
                return true;
            }
            if (items.isEmpty()) {
                return false;
            }
            BuildJob.Hand hand = new BuildJob.Hand();
            hand.homePort = home;
            for (Item item : items) {
                if (JobSupply.hasItem(player, level, job, hand, item)) {
                    return true;
                }
            }
            return false;
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

    private static ItemStack findController(ServerPlayer player, UUID controllerId) {
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
