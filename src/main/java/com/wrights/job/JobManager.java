package com.wrights.job;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintActions;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.WrightsMod;
import com.wrights.drone.WrightTier;
import com.wrights.drone.Wrightports;
import com.wrights.drone.CrewSplit;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;
import com.wrights.entity.ModEntities;
import com.wrights.item.ControllerData;
import com.wrights.item.ControllerItem;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.BlockFilters;
import com.wrights.util.Messages;

@EventBusSubscriber(modid = WrightsMod.MOD_ID)
public final class JobManager {
    private static final double DRONE_REACH_SQR = 2.25D;

    private JobManager() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    public static void tick(MinecraftServer server) {
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
                } else {
                    tickPaste(server, library, job);
                }
            } catch (RuntimeException ex) {
                WrightsMod.LOGGER.error("Wrights job {} failed and was stopped", job.id(), ex);
                discardDrone(server, job);
                library.removeJob(job);
            }
            if (!job.finished() && library.job(job.id()).isPresent()) {
                library.setDirty();
            }
        }
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
            job.setPaused(true);
            library.setDirty();
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.RESUME && !job.finished()) {
            bindCrewIds(player, library);
            if (job.paused() && !restorePausedCrew(player, library, job)) {
                Messages.actionBar(player, "message.wrights.paused_no_drones");
            } else if (job.paused()) {
                job.setPaused(false);
                library.setDirty();
            }
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.REDO) {
            redo(player, library, job);
            return;
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.UNDO) {
            startUndo(player, library, job, false);
            return;
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.UNDO_FORCE) {
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
        if (!instant(player) && !job.hasCrew() && !assignCrew(player, job, schematic)) {
            return;
        }
        job.restart();
        library.setDirty();
        if (!instant(player) && !job.hasCrew() && player.level() instanceof ServerLevel level && level.dimension().equals(job.dimension())) {
            ensureDrone(level, player, job, job.min());
        }
        BlueprintNetwork.showJobHologram(player, job, schematic);
        BlueprintNetwork.syncJobs(player);
        Messages.actionBar(player, "message.wrights.launched");
    }

    private static void startUndo(ServerPlayer player, SchematicLibrary library, BuildJob job, boolean force) {
        if (job.undoing() || job.kind() != BuildJob.Kind.PASTE || !job.finished()) {
            return;
        }
        if (job.refundCount() == 0) {
            Messages.actionBar(player, "message.wrights.undo_none");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (!force) {
            int extra = overflow(player, job);
            if (extra > 0) {
                BlueprintNetwork.undoPrompt(player, job.id(), extra, dropKind(player, job));
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
    }

    public static void bindSupply(ServerPlayer player, BlockPos pos, boolean clear) {
        if (!ControllerItem.isHeld(player)) {
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
        if (containerAt(level, pos) == null) {
            Messages.actionBar(player, "message.wrights.supply_not_container");
            return;
        }
        session.setSupply(pos.immutable(), level.dimension());
        library.setSupply(player.getUUID(), net.minecraft.core.GlobalPos.of(level.dimension(), pos));
        BlueprintNetwork.syncSupply(player);
        Messages.actionBar(player, "message.wrights.supply_chest");
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
            bindCrewIds(player, library);
        }
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (!job.finished() && job.paused() != paused) {
                if (!paused && !restorePausedCrew(player, library, job)) {
                    blocked++;
                    continue;
                }
                job.setPaused(paused);
                changed = true;
            }
        }
        if (changed) {
            library.setDirty();
            Messages.actionBar(player, paused ? "message.wrights.paused_all" : "message.wrights.resumed_all");
        } else if (blocked > 0) {
            Messages.actionBar(player, "message.wrights.paused_no_drones");
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
        if (!instant(player) && !crewReady(player)) {
            return;
        }
        cancel(player, false);
        BuildJob job = BuildJob.paste(
                player.getUUID(),
                data.controllerId().orElseThrow(),
                origin.dimension(),
                origin.pos(),
                schematic.id(),
                schematic.blockCount());
        job.setAnchor(origin.pos().offset(schematic.anchorX(), schematic.anchorY(), schematic.anchorZ()));
        if (!instant(player) && !assignCrew(player, job, schematic)) {
            return;
        }
        library.addJob(job);
        ServerLevel level = player.getServer().getLevel(origin.dimension());
        if (level != null && player.level() == level && !instant(player) && !job.hasCrew()) {
            ensureDrone(level, player, job, origin.pos());
        }
        launched(player, job);
    }

    public static boolean startBlueprintSave(ServerPlayer player, String name, UUID replaceId, ResourceKey<Level> dimension, BlockPos cornerA, BlockPos cornerB, boolean copyFluids) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (hasCopy(library, player.getUUID())) {
            Messages.actionBar(player, "message.wrights.busy");
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
        job.setBlueprintName(name);
        job.setCopyFluids(copyFluids);
        job.setReplaceSchematicId(replaceId);
        library.addJob(job);
        Messages.actionBar(player, "message.wrights.saving");
        return true;
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, 0);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, true, false, false, false);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) {
        return startBlueprintBuild(player, schematicId, origin, sizeX, sizeY, sizeZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, true);
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean supplyPlayerFirst) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(schematicId).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
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
        job.setPasteOptions(placeFluids, voidBlocks, voidFluids, voidNbt);
        job.setAnchor(origin.offset(schematic.anchorX(), schematic.anchorY(), schematic.anchorZ()));
        com.wrights.blueprint.BlueprintSessions.BlueprintSession session = com.wrights.blueprint.BlueprintSessions.get(player);
        job.setSupply(session.supplyPos(), session.supplyDimension());
        job.setSupplyPlayerFirst(supplyPlayerFirst);
        if (!instant(player) && !assignCrew(player, job, schematic, drones)) {
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

    private static boolean overlapsBuild(SchematicLibrary library, ResourceKey<Level> dimension, Schematic schematic, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
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
                        Messages.actionBar(player, "message.wrights.paused_chunk");
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
                    job.partial().add(new Schematic.Entry(
                            (short) (pos.getX() - job.min().getX()),
                            (short) (pos.getY() - job.min().getY()),
                            (short) (pos.getZ() - job.min().getZ()),
                            state,
                            BlockFilters.isSecondary(state)));
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
        if (!BlockFilters.canCopy(level, pos, state, job.copyFluids())) {
            return false;
        }
        if (!BlockFilters.isSecondary(state)) {
            return true;
        }
        BlockPos below = pos.below();
        return job.contains(below) && BlockFilters.canCopy(level, below, level.getBlockState(below));
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
        if (previous != null) {
            schematic = schematic.withPaste(previous.placeFluids(), previous.voidBlocks(), previous.voidFluids(), previous.voidNbt());
        }
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
                Messages.actionBar(player, "message.wrights.copy_empty_skipped", job.skippedSpecial());
            } else {
                Messages.actionBar(player, "message.wrights.copy_empty");
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
                Messages.actionBar(player, "message.wrights.copy_empty");
                BlueprintActions.finishSave(player, null, false);
            } else if (job.skippedSpecial() > 0) {
                Messages.actionBar(player, "message.wrights.copy_done_skipped", saved.blockCount(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ()), job.skippedSpecial());
                BlueprintActions.finishSave(player, saved.id(), true);
            } else {
                Messages.actionBar(player, "message.wrights.blueprint_saved", saved.name(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ()), saved.blockCount());
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
            Messages.actionBar(player, "message.wrights.copy_done_skipped", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()), job.skippedSpecial());
        } else {
            Messages.actionBar(player, "message.wrights.copy_done", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()));
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
        if (player == null) {
            return;
        }
        if (!job.hasCrew() && job.cursor() >= job.total()) {
            finishPaste(server, library, player, job);
            return;
        }
        if (job.paused()) {
            return;
        }
        if (instant(player)) {
            placeAll(level, player, library, job, schematic);
            return;
        }
        if (holdWithoutDrones(player, library, job)) {
            return;
        }
        if (job.hasCrew()) {
            if (!player.level().dimension().equals(job.dimension())) {
                if (job.tickMessage()) {
                    Messages.actionBar(player, "message.wrights.other_dimension");
                }
                return;
            }
            if (!nearSite(player, job)) {
                if (job.tickMessage()) {
                    Messages.actionBar(player, "message.wrights.out_of_range");
                }
                return;
            }
            job.workedTick();
            tickCrew(level, player, library, job, schematic);
            return;
        }
        if (!player.level().dimension().equals(job.dimension())) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.other_dimension");
            }
            return;
        }
        if (!nearSite(player, job)) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.out_of_range");
            }
            return;
        }
        job.workedTick();
        int at = nextSolo(level, schematic, job);
        if (at == SUPPORT_WAIT) {
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
                Messages.actionBar(player, "message.wrights.paused_chunk");
            }
            return;
        }
        Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.9D, 0.0D);
        if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
            double step = drone.distanceToSqr(hover) > 64.0D ? 0.85D : 0.4D;
            drone.moveToward(hover, step);
            job.setLastDronePos(drone.blockPosition());
            library.setDirty();
            return;
        }
        int budget = WrightsConfig.BLOCKS_PER_TICK.getAsInt();
        for (int i = 0; i < budget; i++) {
            int index = i == 0 ? at : nextSolo(level, schematic, job);
            if (index == SUPPORT_WAIT || index < 0) {
                break;
            }
            Schematic.Entry entry = schematic.entry(index);
            BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
            if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
                break;
            }
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                markSolo(job, index);
                continue;
            }
            if (falling(level, schematic, job, entry) == Falling.SKIP) {
                job.skippedOne();
                markSolo(job, index);
                continue;
            }
            PlaceResult placed = placeOne(level, player, job, pos, entry, false);
            if (placed == PlaceResult.PAUSE) {
                if (job.tickMessage()) {
                    Messages.actionBar(player, "message.wrights.paused_chunk");
                }
                library.setDirty();
                return;
            }
            if (placed == PlaceResult.WAIT) {
                break;
            }
            markSolo(job, index);
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
        if (player == null || job.paused()) {
            return;
        }
        if (instant(player)) {
            undoAll(level, server, library, player, job);
            return;
        }
        if (holdWithoutDrones(player, library, job)) {
            return;
        }
        if (job.hasCrew()) {
            if (!player.level().dimension().equals(job.dimension())) {
                if (job.tickMessage()) {
                    Messages.actionBar(player, "message.wrights.other_dimension");
                }
                return;
            }
            if (!nearSite(player, job)) {
                if (job.tickMessage()) {
                    Messages.actionBar(player, "message.wrights.out_of_range");
                }
                return;
            }
            job.workedTick();
            tickUndoCrew(level, player, library, job);
            return;
        }
        if (!player.level().dimension().equals(job.dimension())) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.other_dimension");
            }
            return;
        }
        if (!nearSite(player, job)) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.wrights.out_of_range");
            }
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
                Messages.actionBar(player, "message.wrights.paused_chunk");
            }
            return;
        }
        Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.9D, 0.0D);
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
            if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
                job.restoreRefund(refund);
                break;
            }
            if (!level.isLoaded(pos)) {
                if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                    job.restoreRefund(refund);
                    if (job.tickMessage()) {
                        Messages.actionBar(player, "message.wrights.paused_chunk");
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
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        FluidState fluid = current.getFluidState();
        if (!fluid.isEmpty()) {
            clearFluidSpread(level, pos, fluid.getType(), job);
        }
        Item item = BlockFilters.payment(refund.state());
        if (refund.paid() && item != null) {
            giveBack(level, player, job, item);
            job.returnedOne();
        }
    }

    private record FluidWalk(BlockPos pos, int across, int fallen, int travel) {
    }

    /**
     * Placement lets a source flow, and undo removes only the recorded cell.
     * Flowing blocks never get a fluid tick, so they stay. Two sources can also
     * turn the blocks between them into new sources. Clear that spill, and leave
     * a body of water that continues outside the build.
     */
    private static void clearFluidSpread(ServerLevel level, BlockPos origin, Fluid fluid, BuildJob job) {
        int reach = fluid.isSame(Fluids.LAVA) && !level.dimensionType().ultraWarm() ? 4 : 8;
        Set<Long> kept = new HashSet<>();
        BlockPos base = job.min();
        for (BuildJob.Refund refund : job.refunds()) {
            FluidState state = refund.state().getFluidState();
            if (!state.isEmpty() && state.getType().isSame(fluid)) {
                kept.add(BlockPos.asLong(base.getX() + refund.x(), base.getY() + refund.y(), base.getZ() + refund.z()));
            }
        }
        BlockPos boxMin = new BlockPos(base.getX() - reach, base.getY() - 64, base.getZ() - reach);
        BlockPos boxMax = new BlockPos(
                base.getX() + job.boxSizeX() - 1 + reach,
                base.getY() + job.boxSizeY() - 1 + 16,
                base.getZ() + job.boxSizeZ() - 1 + reach);
        ArrayDeque<FluidWalk> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> seenSources = new HashSet<>();
        List<BlockPos> removed = new ArrayList<>();
        queue.add(new FluidWalk(origin, 0, 0, 0));
        seen.add(origin.asLong());
        while (!queue.isEmpty() && removed.size() < 2048) {
            FluidWalk step = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = step.pos().relative(direction);
                int across = step.across();
                int fallen = step.fallen();
                int travel = step.travel() + 1;
                if (direction == Direction.DOWN) {
                    fallen++;
                    across = 0;
                } else if (direction != Direction.UP) {
                    across++;
                }
                if (across > reach || fallen > 64 || travel > 128 || next.getY() > origin.getY() + 16) {
                    continue;
                }
                long key = next.asLong();
                if (!seen.add(key) || kept.contains(key) || level.isOutsideBuildHeight(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!isFreeFluid(state, fluid)) {
                    continue;
                }
                if (state.getFluidState().isSource()) {
                    List<BlockPos> pool = new ArrayList<>();
                    if (!enclosedSourcePool(level, next, fluid, kept, seenSources, boxMin, boxMax, pool)) {
                        continue;
                    }
                    for (BlockPos source : pool) {
                        seen.add(source.asLong());
                        level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        removed.add(source.immutable());
                        queue.add(new FluidWalk(source, 0, 0, 0));
                    }
                    continue;
                }
                level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                removed.add(next.immutable());
                queue.add(new FluidWalk(next.immutable(), across, fallen, travel));
            }
        }
        level.updateNeighborsAt(origin, Blocks.AIR);
        for (BlockPos pos : removed) {
            level.updateNeighborsAt(pos, Blocks.AIR);
        }
    }

    private static boolean isFreeFluid(BlockState state, Fluid fluid) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty() || !fluidState.getType().isSame(fluid)) {
            return false;
        }
        return BlockFilters.isFluid(state) || state.is(Blocks.BUBBLE_COLUMN);
    }

    /** True when every connected source stays inside the build's spread, so it is not a lake. */
    private static boolean enclosedSourcePool(ServerLevel level, BlockPos seed, Fluid fluid, Set<Long> kept, Set<Long> seenSources, BlockPos boxMin, BlockPos boxMax, List<BlockPos> pool) {
        if (!seenSources.add(seed.asLong())) {
            return false;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            if (pool.size() > 256) {
                return false;
            }
            BlockPos at = queue.removeFirst();
            if (at.getX() < boxMin.getX() || at.getY() < boxMin.getY() || at.getZ() < boxMin.getZ()
                    || at.getX() > boxMax.getX() || at.getY() > boxMax.getY() || at.getZ() > boxMax.getZ()) {
                return false;
            }
            pool.add(at.immutable());
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (level.isOutsideBuildHeight(next)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    return false;
                }
                long key = next.asLong();
                if (kept.contains(key) || seenSources.contains(key)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!isFreeFluid(state, fluid) || !state.getFluidState().isSource()) {
                    continue;
                }
                seenSources.add(key);
                queue.add(next.immutable());
            }
        }
        return pool.size() <= 256;
    }

    private static void finishUndo(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        sendHome(server, player, job);
        int removed = Math.max(0, job.undoTotal() - job.skipped() - job.refundCount());
        int returned = job.returned();
        job.markUndone();
        archive(library, job, false);
        BlueprintNetwork.syncJobs(player);
        if (removed <= 0) {
            Messages.actionBar(player, "message.wrights.undo_none");
        } else {
            Messages.actionBar(player, "message.wrights.undo_done", removed, returned);
        }
    }

    private static String finishedName(SchematicLibrary library, BuildJob job) {
        String name = job.blueprintName();
        if (name == null || name.isBlank()) {
            name = "Build";
            if (job.schematicId() != null) {
                name = library.schematic(job.schematicId()).map(Schematic::name).orElse(name);
            }
        }
        return name;
    }

    private static void finishPaste(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        sendHome(server, player, job);
        int placed = job.placed();
        int skipped = job.skipped();
        archive(library, job, false);
        com.wrights.network.BlueprintNetwork.clearJobHologram(player, job.id());
        com.wrights.network.BlueprintNetwork.jobDone(player, finishedName(library, job), job.elapsedSeconds());
        com.wrights.network.BlueprintNetwork.syncJobs(player);
        if (placed == 0 && skipped > 0) {
            Messages.actionBar(player, "message.wrights.all_skipped");
        } else if (placed == 0) {
            Messages.actionBar(player, "message.wrights.already_built");
        } else {
            Messages.actionBar(player, "message.wrights.paste_done", placed, skipped);
        }
    }

    private static boolean nearSite(ServerPlayer player, BuildJob job) {
        if (!player.level().dimension().equals(job.dimension())) {
            return false;
        }
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        return player.distanceToSqr(Vec3.atCenterOf(job.min())) <= range * range;
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

    private enum PlaceResult {
        DONE,
        PAUSE,
        WAIT
    }

    /** A falling block is not ready yet, so the wright should keep working and come back. */
    private static final int SUPPORT_WAIT = -3;

    private static boolean instant(ServerPlayer player) {
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            return false;
        }
        return com.wrights.blueprint.BlueprintSessions.get(player).instantJobs();
    }

    private static void launched(ServerPlayer player, BuildJob job) {
        if (job.hasCrew() && job.hands().size() > 1) {
            Messages.actionBar(player, "message.wrights.launched_crew", job.hands().size());
        } else {
            Messages.actionBar(player, "message.wrights.launched");
        }
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
        return instant(player) ? 0 : 1;
    }

    public static void bindCrewIds(ServerPlayer player, SchematicLibrary library) {
        boolean worn = !Wrightports.worn(player).isEmpty();
        Swarm swarm = Wrightports.swarm(player);
        Set<UUID> known = new HashSet<>();
        for (Swarm.Unit unit : swarm.units()) {
            known.add(unit.id());
        }
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
            if (!worn) {
                dirty |= job.rememberCrewQuota(assignedCount(job));
                continue;
            }
            List<UUID> current = new ArrayList<>();
            for (UUID unit : job.crewUnits()) {
                if (known.contains(unit) && taken.add(unit)) {
                    current.add(unit);
                }
            }
            int had = assignedCount(job);
            if (current.size() < had) {
                dirty |= job.rememberCrewQuota(had);
            }
            if (current.isEmpty() && job.crewUnits().isEmpty() && !(job.paused() && job.crewQuota() > 0)) {
                int need = job.hasCrew() ? job.hands().size() : (instant(player) ? 0 : 1);
                int jobCap = WrightsConfig.maxWrightsPerJob();
                for (Swarm.Unit unit : swarm.units()) {
                    if (current.size() >= need || current.size() >= jobCap) {
                        break;
                    }
                    if (taken.add(unit.id())) {
                        current.add(unit.id());
                    }
                }
            }
            if (current.isEmpty() && had > 0) {
                dirty |= detachCrew(player, job);
                continue;
            }
            int jobCap = WrightsConfig.maxWrightsPerJob();
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
            }
        }
        if (dirty) {
            library.setDirty();
        }
    }

    private static int assignedCount(BuildJob job) {
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
            discardDrone(player.getServer(), job);
            changed = true;
        }
        if (!job.crewUnits().isEmpty()) {
            job.setCrewUnits(List.of());
            changed = true;
        }
        if (!job.paused()) {
            job.setPaused(true);
            changed = true;
        }
        return changed;
    }

    public static void setJobDrones(ServerPlayer player, UUID id, UUID unitId, boolean add) {
        if (!com.wrights.item.ControllerItem.isHeld(player)) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        bindCrewIds(player, library);
        BuildJob job = library.job(id).orElse(null);
        if (job == null || !job.owner().equals(player.getUUID()) || !usesDrones(job)) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        Swarm swarm = Wrightports.swarm(player);
        Swarm.Unit unit = null;
        for (Swarm.Unit candidate : swarm.units()) {
            if (candidate.id().equals(unitId)) {
                unit = candidate;
                break;
            }
        }
        if (add && unit == null) {
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (add && claimedByOther(library, player.getUUID(), job.id(), unitId)) {
            Messages.actionBar(player, "message.wrights.drones_busy");
            BlueprintNetwork.syncJobs(player);
            return;
        }
        if (add && !job.crewUnits().contains(unitId) && job.crewUnits().size() >= WrightsConfig.maxWrightsPerJob()) {
            Messages.actionBar(player, "message.wrights.job_wright_cap", WrightsConfig.maxWrightsPerJob());
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
        boolean known = false;
        for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
            if (unit.id().equals(unitId)) {
                known = true;
                break;
            }
        }
        if (!known) {
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
                && target.crewUnits().size() >= WrightsConfig.maxWrightsPerJob()) {
            Messages.actionBar(player, "message.wrights.job_wright_cap", WrightsConfig.maxWrightsPerJob());
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

    private static void placeCrew(ServerPlayer player, SchematicLibrary library, BuildJob job, List<UUID> ids) {
        if (ids.isEmpty()) {
            sendHome(player.getServer(), player, job);
            job.setCrewQuota(0);
            job.clearAssigned();
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

    private static boolean usesDrones(BuildJob job) {
        return job.kind() == BuildJob.Kind.PASTE && !job.finished();
    }

    private static boolean claimedByOther(SchematicLibrary library, UUID owner, UUID jobId, UUID unitId) {
        for (BuildJob other : library.jobsFor(owner)) {
            if (other.id().equals(jobId) || !usesDrones(other) || other.awaitingDrones()) {
                continue;
            }
            if (other.crewUnits().contains(unitId)) {
                return true;
            }
        }
        return false;
    }

    private static List<Swarm.Unit> freeUnits(ServerPlayer player, SchematicLibrary library, UUID exceptJob, int limit) {
        Swarm swarm = Wrightports.swarm(player);
        List<Swarm.Unit> free = new ArrayList<>();
        for (Swarm.Unit unit : swarm.units()) {
            if (claimedByOther(library, player.getUUID(), exceptJob, unit.id())) {
                continue;
            }
            free.add(unit);
            if (free.size() >= limit) {
                break;
            }
        }
        return free;
    }

    private static void fitCrew(ServerPlayer player, SchematicLibrary library, BuildJob job, List<UUID> ids) {
        Swarm swarm = Wrightports.swarm(player);
        List<Integer> speeds = new ArrayList<>();
        List<UUID> kept = new ArrayList<>();
        for (UUID unitId : ids) {
            for (Swarm.Unit unit : swarm.units()) {
                if (unit.id().equals(unitId)) {
                    speeds.add(Math.max(1, Math.min(Swarm.MAX_SPEED, unit.speed())));
                    kept.add(unitId);
                    break;
                }
            }
        }
        Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
        int room = placeableBlocks(schematic, job);
        int limit = Math.min(room, WrightsConfig.maxWrightsPerJob());
        if (kept.size() > limit) {
            if (kept.size() > room) {
                Messages.actionBar(player, "message.wrights.drone_block_cap", Math.max(0, room));
            }
            while (kept.size() > limit) {
                kept.remove(kept.size() - 1);
                speeds.remove(speeds.size() - 1);
            }
        }
        if (kept.isEmpty()) {
            discardDrone(player.getServer(), job);
            job.clearAssigned();
            job.setPaused(true);
            return;
        }
        if (!job.hasCrew() && job.droneId() != null) {
            discardId(player.getServer().getLevel(job.dimension()), job.droneId());
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
                evenOut(job, schematic);
            }
        } else {
            int before = job.hands().size();
            ensureSlices(job, true);
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
                hand.speed = Math.max(1, Math.min(Swarm.MAX_SPEED, speeds.get(i)));
            }
            if (job.hands().size() != before) {
                evenOut(job, schematic);
            }
        }
        ServerLevel level = player.getServer().getLevel(job.dimension());
        int slot = 0;
        for (UUID droppedId : dropped) {
            recall(level, player, droppedId, slot++, dropped.size());
        }
        job.setCrewUnits(kept);
    }

    private static boolean missingDrones(ServerPlayer player, BuildJob job) {
        if (instant(player) || job.kind() != BuildJob.Kind.PASTE) {
            return false;
        }
        return Wrightports.worn(player).isEmpty() || Wrightports.swarm(player).units().isEmpty() || job.awaitingDrones();
    }

    /**
     * Puts the crew back to the size it had when the personal wrightport was taken off.
     * Uses wrights that are still in the port, then fills the rest from whatever is free.
     */
    private static boolean restorePausedCrew(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (instant(player) || job.kind() != BuildJob.Kind.PASTE || job.finished()) {
            return true;
        }
        if (Wrightports.worn(player).isEmpty() || Wrightports.swarm(player).units().isEmpty()) {
            return false;
        }
        int quota = job.crewQuota();
        if (quota <= 0) {
            return !job.awaitingDrones() && (job.hasCrew() || !job.crewUnits().isEmpty());
        }
        Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
        int room = placeableBlocks(schematic, job);
        if (room <= 0) {
            return false;
        }
        int wanted = Math.min(quota, Math.min(room, WrightsConfig.maxWrightsPerJob()));
        Swarm swarm = Wrightports.swarm(player);
        Set<UUID> have = new HashSet<>();
        for (Swarm.Unit unit : swarm.units()) {
            have.add(unit.id());
        }
        List<UUID> ids = new ArrayList<>();
        for (UUID unit : job.crewUnits()) {
            if (ids.size() >= wanted) {
                break;
            }
            if (have.contains(unit)) {
                ids.add(unit);
            }
        }
        for (Swarm.Unit unit : swarm.units()) {
            if (ids.size() >= wanted) {
                break;
            }
            if (ids.contains(unit.id()) || claimedByOther(library, player.getUUID(), job.id(), unit.id())) {
                continue;
            }
            ids.add(unit.id());
        }
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

    private static boolean holdWithoutDrones(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (!missingDrones(player, job)) {
            return false;
        }
        boolean dirty = job.rememberCrewQuota(assignedCount(job));
        if (!job.paused()) {
            job.setPaused(true);
            dirty = true;
            Messages.actionBar(player, "message.wrights.paused_no_drones");
            BlueprintNetwork.syncJobs(player);
        }
        if (dirty) {
            library.setDirty();
        }
        return true;
    }

    private static boolean crewReady(ServerPlayer player) {
        if (Wrightports.worn(player).isEmpty()) {
            Messages.actionBar(player, "message.wrights.need_wrightport");
            return false;
        }
        if (Swarm.from(Wrightports.worn(player)).units().isEmpty()) {
            Messages.actionBar(player, "message.wrights.need_drone");
            return false;
        }
        return true;
    }

    private static boolean assignCrew(ServerPlayer player, BuildJob job, Schematic schematic) {
        return assignCrew(player, job, schematic, 0);
    }

    private static boolean assignCrew(ServerPlayer player, BuildJob job, Schematic schematic, int requested) {
        if (!crewReady(player)) {
            return false;
        }
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
        int fromPort = Swarm.from(Wrightports.worn(player)).assignCount();
        int wanted = Math.min(requested > 0 ? requested : fromPort, Math.min(blocks, WrightsConfig.maxWrightsPerJob()));
        List<Swarm.Unit> units = freeUnits(player, library, job.id(), wanted);
        if (units.isEmpty()) {
            Messages.actionBar(player, "message.wrights.drones_busy");
            return false;
        }
        List<Integer> speeds = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (Swarm.Unit unit : units) {
            speeds.add(Math.max(1, Math.min(Swarm.MAX_SPEED, unit.speed())));
            ids.add(unit.id());
        }
        job.setCrew(speeds, sizeX, sizeY, sizeZ);
        job.setCrewUnits(ids);
        job.setCrewQuota(ids.size());
        evenOut(job, schematic);
        return job.hasCrew();
    }

    private static void tickCrew(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job, Schematic schematic) {
        boolean working = false;
        boolean scanning = false;
        boolean waiting = false;
        boolean waitingChunk = false;
        int[] skipBudget = {8192};
        for (BuildJob.Hand hand : job.hands()) {
            int index = nextWork(level, schematic, job, hand, skipBudget);
            if (index == -2) {
                scanning = true;
                continue;
            }
            if (index == SUPPORT_WAIT) {
                waiting = true;
                hand.idle = false;
                continue;
            }
            if (index < 0) {
                hand.idle = true;
                continue;
            }
            hand.idle = false;
            working = true;
            Schematic.Entry next = schematic.entry(index);
            BlockPos nextPos = job.min().offset(next.x(), next.y(), next.z());
            DroneEntity drone = ensureHand(level, player, job, hand, nextPos);
            if (drone != null) {
                drone.setTier(hand.speed);
            }
            if (drone == null) {
                waitingChunk = true;
                continue;
            }
            Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.85D + (hand.lane % 2) * 0.45D, 0.0D);
            if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
                drone.moveToward(hover, flyStep(hand.speed, drone.distanceToSqr(hover)));
                hand.lastPos = drone.blockPosition();
                continue;
            }
            int budget = placeBudget(hand);
            for (int n = 0; n < budget; n++) {
                int at = nextWork(level, schematic, job, hand, skipBudget);
                if (at == -2) {
                    scanning = true;
                    break;
                }
                if (at == SUPPORT_WAIT) {
                    waiting = true;
                    break;
                }
                if (at < 0) {
                    hand.idle = true;
                    break;
                }
                Schematic.Entry entry = schematic.entry(at);
                BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
                if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
                    break;
                }
                if (entry.secondary() && supportWaiting(level, job, hand, at, pos, entry)) {
                    break;
                }
                if (job.outsideClip(entry)) {
                    noteProgress(schematic, job, hand, entry, at);
                    job.advance();
                    continue;
                }
                PlaceResult placed = placeOne(level, player, job, pos, entry, false);
                if (placed == PlaceResult.PAUSE) {
                    waitingChunk = true;
                    library.setDirty();
                    if (job.tickMessage()) {
                        Messages.actionBar(player, "message.wrights.paused_chunk");
                    }
                    return;
                }
                if (placed == PlaceResult.WAIT) {
                    waiting = true;
                    break;
                }
                noteProgress(schematic, job, hand, entry, at);
                job.advance();
            }
        }
        shareWithIdle(job, schematic);
        if (!working && !scanning) {
            for (BuildJob.Hand hand : job.hands()) {
                int peek = nextInLane(level, schematic, job, hand);
                if (peek == SUPPORT_WAIT) {
                    waiting = true;
                } else if (peek >= 0) {
                    working = true;
                    break;
                }
            }
        }
        library.setDirty();
        if (!working && !scanning && !waiting) {
            finishPaste(level.getServer(), library, player, job);
        } else if (waitingChunk && job.tickMessage()) {
            Messages.actionBar(player, "message.wrights.paused_chunk");
        }
    }

    private static void tickUndoCrew(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (!job.hasRefunds()) {
            finishUndo(level.getServer(), library, player, job);
            return;
        }
        boolean working = false;
        for (BuildJob.Hand hand : job.hands()) {
            int index = refundIndex(job, hand);
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
            }
            if (drone == null) {
                continue;
            }
            Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.85D + (hand.lane % 2) * 0.45D, 0.0D);
            if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
                drone.moveToward(hover, flyStep(hand.speed, drone.distanceToSqr(hover)));
                hand.lastPos = drone.blockPosition();
                continue;
            }
            int budget = placeBudget(hand);
            for (int n = 0; n < budget; n++) {
                int at = refundIndex(job, hand);
                if (at < 0) {
                    hand.idle = true;
                    break;
                }
                BuildJob.Refund refund = job.refunds().get(at);
                BlockPos pos = job.min().offset(refund.x(), refund.y(), refund.z());
                if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
                    break;
                }
                job.takeRefund(at);
                if (!level.isLoaded(pos)) {
                    if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                        job.restoreRefund(at, refund);
                        if (job.tickMessage()) {
                            Messages.actionBar(player, "message.wrights.paused_chunk");
                        }
                        return;
                    }
                    job.skippedOne();
                    continue;
                }
                removeOne(level, player, job, pos, refund, false);
            }
        }
        shareUndo(job);
        if (!working) {
            for (BuildJob.Hand hand : job.hands()) {
                if (refundIndex(job, hand) >= 0) {
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

    private static boolean supportWaiting(ServerLevel level, BuildJob job, BuildJob.Hand hand, int index, BlockPos pos, Schematic.Entry entry) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() == entry.state().getBlock() || (!below.isAir() && !below.canBeReplaced())) {
            return false;
        }
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand) {
                continue;
            }
            if (other.slices.isEmpty()) {
                if (other.cursor <= index) {
                    return true;
                }
                continue;
            }
            for (BuildJob.Slice slice : other.slices) {
                if (slice.contains(entry.x(), entry.y() - 1, entry.z()) && slice.cursor <= index) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean satisfied(ServerLevel level, BuildJob job, Schematic.Entry entry) {
        if (job.outsideClip(entry)) {
            return true;
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isOutsideBuildHeight(pos)) {
            return true;
        }
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!job.placeFluids() && com.wrights.util.BlockFilters.isFluid(entry.state())) {
            return true;
        }
        return level.getBlockState(pos).equals(entry.state());
    }

    private static int nextWork(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand, int[] budget) {
        int seen = -1;
        while (budget[0] > 0) {
            int index = nextInLane(level, schematic, job, hand);
            if (index == SUPPORT_WAIT) {
                return SUPPORT_WAIT;
            }
            if (index < 0) {
                hand.rescan = false;
                return -1;
            }
            if (index == seen) {
                return -2;
            }
            seen = index;
            Schematic.Entry entry = schematic.entry(index);
            if (!satisfied(level, job, entry)) {
                Falling hold = falling(level, schematic, job, entry);
                if (hold == Falling.SKIP) {
                    job.skippedOne();
                    noteProgress(schematic, job, hand, entry, index);
                    if (!hand.rescan) {
                        job.advance();
                    }
                    budget[0]--;
                    continue;
                }
                if (hold == Falling.WAIT) {
                    return SUPPORT_WAIT;
                }
                return index;
            }
            budget[0]--;
            noteProgress(schematic, job, hand, entry, index);
            if (!hand.rescan) {
                job.advance();
            }
        }
        int rest = nextInLane(level, schematic, job, hand);
        if (rest == SUPPORT_WAIT) {
            return SUPPORT_WAIT;
        }
        return rest < 0 ? -1 : -2;
    }

    private static int nextInLane(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        if (hand.slices.isEmpty()) {
            for (BuildJob.Hand other : job.hands()) {
                if (!other.slices.isEmpty()) {
                    return -1;
                }
            }
            java.util.function.Predicate<Schematic.Entry> lane = entry -> CrewSplit.lane(entry.x(), entry.y(), entry.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
            int found = scan(level, schematic, job, hand.cursor, hand.probe, lane);
            if (found < 0 && Math.max(hand.cursor, hand.probe) > hand.cursor) {
                hand.probe = hand.cursor;
                found = scan(level, schematic, job, hand.cursor, hand.cursor, lane);
            }
            if (found >= 0) {
                hand.probe = found;
            }
            return found;
        }
        int best = -1;
        boolean waiting = false;
        for (BuildJob.Slice slice : hand.slices) {
            int found = scanSlice(level, schematic, job, slice);
            if (found == SUPPORT_WAIT) {
                waiting = true;
                continue;
            }
            if (found >= 0 && (best < 0 || found < best)) {
                best = found;
            }
        }
        if (best >= 0) {
            return best;
        }
        return waiting ? SUPPORT_WAIT : -1;
    }

    private static int scanSlice(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Slice slice) {
        int start = Math.max(slice.cursor, slice.probe);
        java.util.function.Predicate<Schematic.Entry> include = entry -> slice.contains(entry.x(), entry.y(), entry.z());
        int found = scan(level, schematic, job, slice.cursor, start, include);
        // Waiting on a floor is not an empty scan. Rewinding here finds the same block again and never returns.
        if (found == -1 && start > slice.cursor) {
            slice.probe = slice.cursor;
            found = scan(level, schematic, job, slice.cursor, slice.cursor, include);
        }
        if (found >= 0) {
            slice.probe = found;
        }
        return found;
    }

    /**
     * Next block this range can act on. A falling block whose floor is not down yet is left in place,
     * so the wright can build that floor and wrap back to the sand or gravel.
     */
    private static int scan(ServerLevel level, Schematic schematic, BuildJob job, int cursor, int start, java.util.function.Predicate<Schematic.Entry> include) {
        boolean waiting = false;
        for (int i = Math.max(0, start); i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!include.test(entry)) {
                continue;
            }
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                if (i == cursor) {
                    return i;
                }
                continue;
            }
            if (falling(level, schematic, job, entry) == Falling.WAIT) {
                waiting = true;
                continue;
            }
            return i;
        }
        return waiting ? SUPPORT_WAIT : -1;
    }

    private enum Falling {
        READY,
        WAIT,
        SKIP
    }

    private static Falling falling(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry) {
        if (!BlockFilters.falls(entry.state())) {
            return Falling.READY;
        }
        return fallingAt(level, schematic, job, entry, new HashSet<>());
    }

    private static Falling fallingAt(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry, Set<Long> seen) {
        if (!seen.add(BlockPos.asLong(entry.x(), entry.y(), entry.z()))) {
            return Falling.SKIP;
        }
        BlockPos below = job.min().offset(entry.x(), entry.y(), entry.z()).below();
        if (level.isOutsideBuildHeight(below) || !level.isLoaded(below)) {
            return Falling.WAIT;
        }
        int support = schematic.indexAt(entry.x(), entry.y() - 1, entry.z());
        BlockState under = level.getBlockState(below);
        if (support >= 0 && !under.equals(schematic.entry(support).state())) {
            if (floorComing(level, schematic, job, support, seen)) {
                return Falling.WAIT;
            }
            if (under.isAir() || under.canBeReplaced()) {
                Schematic.Entry floor = schematic.entry(support);
                if (reopen(job, support, floor.x(), floor.y(), floor.z())) {
                    return Falling.WAIT;
                }
            }
            return Falling.SKIP;
        }
        if (FallingBlock.isFree(under)) {
            return Falling.SKIP;
        }
        return Falling.READY;
    }

    /** Pulls an unplaced support block back into a wright's queue after the crew was split. */
    private static boolean reopen(BuildJob job, int index, int x, int y, int z) {
        boolean opened = false;
        for (BuildJob.Hand hand : job.hands()) {
            boolean owned = false;
            for (BuildJob.Slice slice : hand.slices) {
                if (!slice.contains(x, y, z)) {
                    continue;
                }
                owned = true;
                if (slice.cursor > index) {
                    slice.cursor = index;
                    slice.probe = index;
                    hand.idle = false;
                    opened = true;
                }
            }
            if (!owned && hand.slices.isEmpty()
                    && CrewSplit.lane(x, y, z, hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane
                    && hand.cursor > index) {
                hand.cursor = index;
                hand.probe = index;
                hand.idle = false;
                opened = true;
            }
        }
        return opened;
    }

    private static boolean floorComing(ServerLevel level, Schematic schematic, BuildJob job, int support, Set<Long> seen) {
        Schematic.Entry floor = schematic.entry(support);
        if (!stillQueued(job, floor.x(), floor.y(), floor.z(), support)) {
            return false;
        }
        if (!BlockFilters.falls(floor.state())) {
            return true;
        }
        return fallingAt(level, schematic, job, floor, seen) != Falling.SKIP;
    }

    private static boolean stillQueued(BuildJob job, int x, int y, int z, int index) {
        if (job.hands().isEmpty()) {
            return job.cursor() <= index;
        }
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.slices.isEmpty()) {
                if (hand.cursor <= index) {
                    return true;
                }
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                if (slice.contains(x, y, z) && slice.cursor <= index) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int nextSolo(ServerLevel level, Schematic schematic, BuildJob job) {
        int start = Math.max(job.cursor(), job.pasteProbe());
        int found = scan(level, schematic, job, job.cursor(), start, entry -> true);
        if (found == -1 && start > job.cursor()) {
            job.setPasteProbe(job.cursor());
            found = scan(level, schematic, job, job.cursor(), job.cursor(), entry -> true);
        }
        if (found >= 0) {
            job.setPasteProbe(found);
        }
        return found;
    }

    private static void markSolo(BuildJob job, int index) {
        if (index <= job.cursor()) {
            while (job.cursor() <= index && job.cursor() < job.total()) {
                job.advance();
            }
            if (job.pasteProbe() < job.cursor()) {
                job.setPasteProbe(job.cursor());
            }
            return;
        }
        job.setPasteProbe(index + 1);
    }

    private static int refundIndex(BuildJob job, BuildJob.Hand hand) {
        List<BuildJob.Refund> refunds = job.refunds();
        for (int i = refunds.size() - 1; i >= 0; i--) {
            BuildJob.Refund refund = refunds.get(i);
            if (ownsRefund(hand, job, refund)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean ownsRefund(BuildJob.Hand hand, BuildJob job, BuildJob.Refund refund) {
        if (hand.slices.isEmpty()) {
            return CrewSplit.lane(refund.x(), refund.y(), refund.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
        }
        for (BuildJob.Slice slice : hand.slices) {
            if (slice.contains(refund.x(), refund.y(), refund.z())) {
                return true;
            }
        }
        return false;
    }

    private static void noteProgress(Schematic schematic, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry, int index) {
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

    private static void migrateSlices(BuildJob job) {
        boolean any = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (!hand.slices.isEmpty()) {
                any = true;
                break;
            }
        }
        ensureSlices(job, !any);
    }

    private static void ensureSlices(BuildJob job, boolean includeIdle) {
        for (BuildJob.Hand hand : job.hands()) {
            if (!hand.slices.isEmpty() || hand.lanes <= 0 || (hand.idle && !includeIdle)) {
                continue;
            }
            int lanes = Math.max(1, hand.lanes);
            BuildJob.Slice slice = CrewSplit.slab(
                    lanes,
                    Math.max(1, job.spanX()),
                    Math.max(1, job.spanY()),
                    Math.max(1, job.spanZ()),
                    Math.min(Math.max(0, hand.lane), lanes - 1));
            slice.cursor = Math.max(0, hand.cursor);
            hand.slices.add(slice);
        }
    }

    private static void evenOut(BuildJob job, Schematic schematic) {
        if (schematic == null || job.hands().size() < 2) {
            ensureSlices(job, false);
            return;
        }
        ensureSlices(job, false);
        int passes = job.hands().size() * 8;
        for (int pass = 0; pass < passes; pass++) {
            BuildJob.Hand poor = null;
            BuildJob.Hand rich = null;
            BuildJob.Slice donor = null;
            int least = Integer.MAX_VALUE;
            int most = -1;
            for (BuildJob.Hand hand : job.hands()) {
                int load = 0;
                BuildJob.Slice fullest = null;
                int fullestCount = 0;
                for (BuildJob.Slice slice : hand.slices) {
                    int left = countPaste(schematic, slice);
                    load += left;
                    if (left > fullestCount) {
                        fullestCount = left;
                        fullest = slice;
                    }
                }
                if (load < least) {
                    least = load;
                    poor = hand;
                }
                if (load > most) {
                    most = load;
                    rich = hand;
                    donor = fullest;
                }
            }
            if (poor == null || rich == null || poor == rich || donor == null || most < least + 2) {
                return;
            }
            BuildJob.Slice half = splitPaste(schematic, donor);
            if (half == null) {
                return;
            }
            poor.slices.add(half);
            poor.idle = false;
        }
    }

    private static void shareWithIdle(BuildJob job, Schematic schematic) {
        if (schematic == null || job.hands().size() < 2) {
            return;
        }
        boolean pending = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.idle) {
                pending = true;
                break;
            }
        }
        if (!pending) {
            return;
        }
        migrateSlices(job);
        for (int n = 0; n < job.hands().size(); n++) {
            BuildJob.Hand receiver = null;
            for (BuildJob.Hand hand : job.hands()) {
                if (countPaste(schematic, hand) == 0) {
                    receiver = hand;
                    break;
                }
                hand.idle = false;
            }
            if (receiver == null) {
                return;
            }
            BuildJob.Slice donor = fullestPaste(job, schematic, receiver);
            BuildJob.Slice half = donor == null ? null : splitPaste(schematic, donor);
            if (half == null) {
                for (BuildJob.Hand hand : job.hands()) {
                    if (countPaste(schematic, hand) == 0) {
                        hand.idle = false;
                    }
                }
                return;
            }
            receiver.slices.add(half);
            receiver.idle = false;
        }
    }

    private static void shareUndo(BuildJob job) {
        if (job.hands().size() < 2) {
            return;
        }
        boolean pending = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.idle) {
                pending = true;
                break;
            }
        }
        if (!pending) {
            return;
        }
        migrateSlices(job);
        for (int n = 0; n < job.hands().size(); n++) {
            BuildJob.Hand receiver = null;
            for (BuildJob.Hand hand : job.hands()) {
                if (countRefunds(job, hand) == 0) {
                    receiver = hand;
                    break;
                }
                hand.idle = false;
            }
            if (receiver == null) {
                return;
            }
            BuildJob.Slice donor = fullestRefund(job, receiver);
            BuildJob.Slice half = donor == null ? null : splitRefunds(job, donor);
            if (half == null) {
                for (BuildJob.Hand hand : job.hands()) {
                    if (countRefunds(job, hand) == 0) {
                        hand.idle = false;
                    }
                }
                return;
            }
            receiver.slices.add(half);
            receiver.idle = false;
        }
    }

    private static int countPaste(Schematic schematic, BuildJob.Hand hand) {
        int count = 0;
        for (BuildJob.Slice slice : hand.slices) {
            count += countPaste(schematic, slice);
        }
        return count;
    }

    private static int countPaste(Schematic schematic, BuildJob.Slice slice) {
        int count = 0;
        for (int i = Math.max(0, slice.cursor); i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (slice.contains(entry.x(), entry.y(), entry.z())) {
                count++;
            }
        }
        return count;
    }

    private static int countRefunds(BuildJob job, BuildJob.Hand hand) {
        int count = 0;
        for (BuildJob.Slice slice : hand.slices) {
            count += countRefunds(job, slice);
        }
        return count;
    }

    private static int countRefunds(BuildJob job, BuildJob.Slice slice) {
        int count = 0;
        for (BuildJob.Refund refund : job.refunds()) {
            if (slice.contains(refund.x(), refund.y(), refund.z())) {
                count++;
            }
        }
        return count;
    }

    private static BuildJob.Slice fullestPaste(BuildJob job, Schematic schematic, BuildJob.Hand except) {
        BuildJob.Slice donor = null;
        int most = 1;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand == except) {
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                int left = countPaste(schematic, slice);
                if (left > most) {
                    most = left;
                    donor = slice;
                }
            }
        }
        return donor;
    }

    private static BuildJob.Slice fullestRefund(BuildJob job, BuildJob.Hand except) {
        BuildJob.Slice donor = null;
        int most = 1;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand == except) {
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                int left = countRefunds(job, slice);
                if (left > most) {
                    most = left;
                    donor = slice;
                }
            }
        }
        return donor;
    }

    private static BuildJob.Slice splitPaste(Schematic schematic, BuildJob.Slice slice) {
        int start = Math.max(0, slice.cursor);
        int count = countPaste(schematic, slice);
        if (count < 2) {
            return null;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int at = 0;
        for (int i = start; i < schematic.blockCount() && at < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z())) {
                continue;
            }
            xs[at] = entry.x();
            ys[at] = entry.y();
            zs[at] = entry.z();
            at++;
        }
        return splitPoints(slice, xs, ys, zs, at);
    }

    private static BuildJob.Slice splitRefunds(BuildJob job, BuildJob.Slice slice) {
        int count = countRefunds(job, slice);
        if (count < 2) {
            return null;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int at = 0;
        for (BuildJob.Refund refund : job.refunds()) {
            if (!slice.contains(refund.x(), refund.y(), refund.z())) {
                continue;
            }
            xs[at] = refund.x();
            ys[at] = refund.y();
            zs[at] = refund.z();
            at++;
            if (at >= count) {
                break;
            }
        }
        return splitPoints(slice, xs, ys, zs, at);
    }

    private static BuildJob.Slice splitPoints(BuildJob.Slice slice, int[] xs, int[] ys, int[] zs, int count) {
        if (count < 2) {
            return null;
        }
        int minX = xs[0];
        int maxX = xs[0];
        int minZ = zs[0];
        int maxZ = zs[0];
        for (int i = 1; i < count; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        // Never cut a column in half. The wright who owns the sand also owns the block under it.
        int axis = spanX >= spanZ && spanX > 0 ? 0 : spanZ > 0 ? 2 : -1;
        if (axis < 0) {
            return null;
        }
        int minC = axis == 0 ? minX : minZ;
        int span = axis == 0 ? spanX : spanZ;
        if (span <= 0) {
            return null;
        }
        int[] bins = new int[span + 1];
        for (int i = 0; i < count; i++) {
            int coord = axis == 0 ? xs[i] : axis == 1 ? ys[i] : zs[i];
            bins[coord - minC]++;
        }
        int running = 0;
        int bestCut = -1;
        int bestDiff = Integer.MAX_VALUE;
        for (int bin = 0; bin < bins.length - 1; bin++) {
            running += bins[bin];
            int right = count - running;
            if (running == 0 || right == 0) {
                continue;
            }
            int diff = Math.abs(running - right);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestCut = bin + 1;
            }
        }
        if (bestCut < 0) {
            return null;
        }
        int cut = minC + bestCut;
        BuildJob.Slice given = new BuildJob.Slice();
        given.x0 = slice.x0;
        given.y0 = slice.y0;
        given.z0 = slice.z0;
        given.x1 = slice.x1;
        given.y1 = slice.y1;
        given.z1 = slice.z1;
        given.cursor = slice.cursor;
        if (axis == 0) {
            slice.x1 = cut - 1;
            given.x0 = cut;
        } else if (axis == 1) {
            slice.y1 = cut - 1;
            given.y0 = cut;
        } else {
            slice.z1 = cut - 1;
            given.z0 = cut;
        }
        return given;
    }

    private static int placeBudget(BuildJob.Hand hand) {
        int cap = WrightsConfig.BLOCKS_PER_TICK.getAsInt();
        int eighths = hand.placeCredit + WrightTier.placeEighths(hand.speed);
        int budget = Math.min(cap, eighths / 8);
        int leftover = eighths - budget * 8;
        hand.placeCredit = leftover >= 8 ? leftover % 8 : leftover;
        return budget;
    }

    private static double flyStep(int speed, double distanceSqr) {
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
                PlaceResult placed = placeOne(level, player, job, pos, entry, true);
                if (placed == PlaceResult.PAUSE) {
                    library.setDirty();
                    return;
                }
                if (placed == PlaceResult.WAIT) {
                    continue;
                }
                done[i] = true;
                progress = true;
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

    private static PlaceResult placeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet) {
        if (level.isOutsideBuildHeight(pos)) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (!level.isLoaded(pos)) {
            if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                return PlaceResult.PAUSE;
            }
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (!canBuild(player, level, pos)) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        BlockState state = entry.state();
        Item payment = BlockFilters.payment(state);
        BlockState existing = level.getBlockState(pos);
        if (existing.equals(state)) {
            return PlaceResult.DONE;
        }
        if (!job.placeFluids() && BlockFilters.isFluid(state)) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (keptStored(job, existing, state)) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        boolean clearFirst = shouldVoid(job, existing, state);
        if (clearFirst && existing.getDestroySpeed(level, pos) < 0.0F) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (!clearFirst && !existing.isAir() && !existing.canBeReplaced()) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (entry.secondary()) {
            BlockState below = level.getBlockState(pos.below());
            if (below.getBlock() != state.getBlock()) {
                if (below.isAir() || below.canBeReplaced()) {
                    return PlaceResult.WAIT;
                }
                job.skippedOne();
                return PlaceResult.DONE;
            }
        } else if (!freePlace(player) && payment != null) {
            if (!hasItem(player, level, job, payment)) {
                if (job.supplyPos() != null && supply(level, player, job) == null && job.tickMessage()) {
                    Messages.actionBar(player, "message.wrights.supply_missing");
                }
                job.skippedOne();
                return PlaceResult.DONE;
            }
        }
        if (BlockFilters.falls(state)) {
            BlockPos below = pos.below();
            if (level.isOutsideBuildHeight(below) || !level.isLoaded(below) || FallingBlock.isFree(level.getBlockState(below))) {
                return PlaceResult.WAIT;
            }
        }
        if (clearFirst) {
            voidCell(level, pos);
            existing = level.getBlockState(pos);
        }

        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        BlockState after = level.getBlockState(pos);
        if (after.getBlock() != state.getBlock()) {
            if (!after.equals(existing)) {
                level.setBlock(pos, existing, Block.UPDATE_ALL);
            }
            job.skippedOne();
            return PlaceResult.DONE;
        }
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, existing, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            level.setBlock(pos, existing, Block.UPDATE_ALL);
            job.skippedOne();
            return PlaceResult.DONE;
        }
        boolean paid = !entry.secondary() && !freePlace(player) && payment != null;
        if (paid) {
            consumeItem(player, level, job, payment);
        }
        job.recordPlaced(entry.x(), entry.y(), entry.z(), after, paid);
        if (!quiet) {
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.levelEvent(2001, pos, Block.getId(state));
        }
        job.placedOne();
        return PlaceResult.DONE;
    }

    /** A chest or other stored block stays unless voiding those was turned on. */
    private static boolean keptStored(BuildJob job, BlockState existing, BlockState target) {
        return existing.hasBlockEntity() && !existing.equals(target) && !job.voidNbt();
    }

    /** Remove a solid block or a fluid that does not match, when that void option is on. */
    private static boolean shouldVoid(BuildJob job, BlockState existing, BlockState target) {
        if (existing.isAir() || existing.equals(target) || existing.canBeReplaced()) {
            return false;
        }
        if (existing.hasBlockEntity() && !job.voidNbt()) {
            return false;
        }
        if (BlockFilters.isFluid(existing)) {
            return job.voidFluids();
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

    private static boolean canBuild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isSpectator()) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        return !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private static boolean freePlace(ServerPlayer player) {
        return player.getAbilities().instabuild && !player.isSpectator();
    }

    private static boolean hasItem(ServerPlayer player, ServerLevel level, BuildJob job, Item item) {
        if (holds(player.getInventory(), item)) {
            return true;
        }
        Container container = supply(level, player, job);
        return container != null && holds(container, item);
    }

    private static void consumeItem(ServerPlayer player, ServerLevel level, BuildJob job, Item item) {
        Container container = supply(level, player, job);
        boolean chestFirst = job.supplyPos() != null && !job.supplyPlayerFirst();
        if (chestFirst && container != null && take(container, item)) {
            container.setChanged();
            return;
        }
        if (take(player.getInventory(), item)) {
            return;
        }
        if (!chestFirst && container != null && take(container, item)) {
            container.setChanged();
        }
    }

    private static boolean holds(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean take(Container container, Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }

    private static void giveBack(ServerLevel level, ServerPlayer player, BuildJob job, Item item) {
        ItemStack stack = new ItemStack(item);
        BlockPos supplyPos = job.supplyPos();
        if (supplyPos != null) {
            Container container = supply(level, player, job);
            if (container != null) {
                stack = insert(container, stack);
                container.setChanged();
            }
            if (!stack.isEmpty()) {
                dropStack(level, container == null ? player.blockPosition() : supplyPos, stack);
            }
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static Container supply(ServerLevel level, ServerPlayer player, BuildJob job) {
        BlockPos pos = job.supplyPos();
        ServerLevel stored = supplyLevel(level, player, job);
        if (pos == null || !stored.mayInteract(player, pos)) {
            return null;
        }
        return containerAt(stored, pos);
    }

    private static ServerLevel supplyLevel(ServerLevel fallback, ServerPlayer player, BuildJob job) {
        String raw = job.supplyDimension();
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(raw);
        if (id == null) {
            return fallback;
        }
        ServerLevel stored = player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        return stored == null ? fallback : stored;
    }

    private static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, false);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container container) {
            return container;
        }
        return null;
    }

    private static ItemStack insert(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
                container.setItem(slot, existing);
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private static void dropStack(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack));
    }

    private static int dropKind(ServerPlayer player, BuildJob job) {
        if (job.supplyPos() == null) {
            return BlueprintPayloads.UndoPrompt.AT_PLAYER;
        }
        if (!(player.level() instanceof ServerLevel level) || supply(level, player, job) == null) {
            return BlueprintPayloads.UndoPrompt.CHEST_GONE;
        }
        return BlueprintPayloads.UndoPrompt.AT_CHEST;
    }

    private static int overflow(ServerPlayer player, BuildJob job) {
        java.util.List<Item> paid = new java.util.ArrayList<>();
        for (BuildJob.Refund refund : job.refunds()) {
            if (!refund.paid()) {
                continue;
            }
            Item item = BlockFilters.payment(refund.state());
            if (item != null) {
                paid.add(item);
            }
        }
        if (paid.isEmpty()) {
            return 0;
        }
        ItemStack[] slots = slotsFor(player, job);
        if (slots == null) {
            return paid.size();
        }
        int extra = 0;
        for (Item item : paid) {
            if (!fits(slots, item)) {
                extra++;
            }
        }
        return extra;
    }

    private static ItemStack[] slotsFor(ServerPlayer player, BuildJob job) {
        if (job.supplyPos() == null) {
            Inventory inventory = player.getInventory();
            ItemStack[] slots = new ItemStack[36];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = inventory.getItem(slot).copy();
            }
            return slots;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Container container = supply(level, player, job);
        if (container == null) {
            return null;
        }
        ItemStack[] slots = new ItemStack[container.getContainerSize()];
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = container.getItem(slot).copy();
        }
        return slots;
    }

    private static boolean fits(ItemStack[] slots, Item item) {
        ItemStack stack = new ItemStack(item);
        for (int slot = 0; slot < slots.length; slot++) {
            ItemStack existing = slots[slot];
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = stack;
                return true;
            }
        }
        return false;
    }

    private static DroneEntity ensureDrone(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos workPos) {
        if (job.droneId() != null) {
            Entity existing = level.getEntity(job.droneId());
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                paint(drone, job);
                return drone;
            }
            BlockPos last = job.lastDronePos();
            if (last != null && !level.isLoaded(last) && !level.isLoaded(workPos)) {
                return null;
            }
        }
        net.minecraft.world.phys.AABB search = new net.minecraft.world.phys.AABB(workPos).inflate(96.0D);
        for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, search, entity -> entity.jobId().filter(job.id()::equals).isPresent() && entity.isAlive())) {
            job.setDroneId(drone.getUUID());
            paint(drone, job);
            return drone;
        }
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        Vec3 site = Vec3.atCenterOf(workPos).add(0.0D, 1.15D, 0.0D);
        depart(drone, player, site, 0, 1);
        job.setDroneId(drone.getUUID());
        job.setLastDronePos(drone.blockPosition());
        level.addFreshEntity(drone);
        paint(drone, job);
        return drone;
    }

    private static void paint(DroneEntity drone, BuildJob job) {
        int tier = job.hands().isEmpty() ? 1 : job.hands().get(0).speed;
        drone.setTier(tier);
    }

    private static DroneEntity ensureHand(ServerLevel level, ServerPlayer player, BuildJob job, BuildJob.Hand hand, BlockPos workPos) {
        if (hand.entityId != null) {
            Entity existing = level.getEntity(hand.entityId);
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                return drone;
            }
            BlockPos last = hand.lastPos;
            if (last != null && !level.isLoaded(last) && !level.isLoaded(workPos)) {
                return null;
            }
            hand.entityId = null;
        }
        AABB search = new AABB(workPos).inflate(96.0D);
        for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, search, entity -> entity.isAlive() && entity.jobId().filter(job.id()::equals).isPresent())) {
            if (job.ownsDrone(drone.getUUID())) {
                continue;
            }
            hand.entityId = drone.getUUID();
            hand.lastPos = drone.blockPosition();
            return drone;
        }
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        Vec3 site = Vec3.atCenterOf(workPos).add(0.0D, 1.15D, 0.0D);
        depart(drone, player, site, hand.lane, hand.lanes);
        hand.entityId = drone.getUUID();
        hand.lastPos = drone.blockPosition();
        level.addFreshEntity(drone);
        return drone;
    }

    private static void depart(DroneEntity drone, ServerPlayer player, Vec3 work, int lane, int lanes) {
        if (player != null && player.level() == drone.level() && !Wrightports.worn(player).isEmpty()) {
            drone.departFrom(player, work, lane, lanes);
            return;
        }
        drone.moveTo(work.x, work.y, work.z, player == null ? 0.0F : player.getYRot(), 0.0F);
    }

    private static void sendHome(MinecraftServer server, ServerPlayer player, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        if (player == null || level == null || player.level() != level) {
            discardDrone(server, job);
            return;
        }
        List<UUID> ids = new ArrayList<>();
        if (job.droneId() != null) {
            ids.add(job.droneId());
        }
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.entityId != null && !ids.contains(hand.entityId)) {
                ids.add(hand.entityId);
            }
        }
        int slot = 0;
        for (UUID id : ids) {
            recall(level, player, id, slot++, ids.size());
        }
        job.setDroneId(null);
        for (BuildJob.Hand hand : job.hands()) {
            hand.entityId = null;
        }
    }

    private static void recall(ServerLevel level, ServerPlayer player, UUID id, int slot, int count) {
        if (level == null || id == null) {
            return;
        }
        if (player == null || player.level() != level) {
            discardId(level, id);
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity instanceof DroneEntity drone && drone.isAlive()) {
            drone.flyHome(player, slot, count);
        }
    }

    private static void discardDrone(MinecraftServer server, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        discardId(level, job.droneId());
        job.setDroneId(null);
        for (BuildJob.Hand hand : job.hands()) {
            discardId(level, hand.entityId);
            hand.entityId = null;
        }
    }

    private static void discardId(ServerLevel level, UUID id) {
        if (level == null || id == null) {
            return;
        }
        Entity entity = level.getEntity(id);
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
