package com.wrights.job;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
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
import net.neoforged.neoforge.network.PacketDistributor;
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
import com.wrights.drone.Wrightports;
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
    private static final double DRONE_REACH_SQR = 2.25D;

    private JobManager() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    public static void tick(MinecraftServer server) {
        DigitalNetworks.tickCrafts(server);
        com.wrights.drone.CraftDeliveries.tick(server);
        sweepReturns();
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
        } else if (action == com.wrights.network.BlueprintPayloads.JobControl.RESUME && !job.finished()) {
            if (job.queued()) {
                job.setPaused(false);
                job.clearHold();
                library.setDirty();
            } else {
                bindCrewIds(player, library);
                if (job.paused() && !restorePausedCrew(player, library, job)) {
                    explainResume(player, job);
                } else if (job.paused()) {
                    job.setPaused(false);
                    job.clearHold();
                    library.setDirty();
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
        if (containerAt(level, pos) == null) {
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
        if (!level.mayInteract(player, chestPos) || containerAt(level, chestPos) == null) {
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
            bindCrewIds(player, library);
        }
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (!job.finished() && job.paused() != paused) {
                if (!paused && job.queued()) {
                    job.setPaused(false);
                    job.clearHold();
                    changed = true;
                    continue;
                }
                if (!paused && !restorePausedCrew(player, library, job)) {
                    blocked++;
                    continue;
                }
                if (paused) {
                    job.noteHold(BuildJob.HOLD_MANUAL, null);
                } else {
                    job.clearHold();
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
        if (!instant(player) && !crewReady(player, origin.dimension(), origin.pos())) {
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

    public static boolean startBlueprintSave(ServerPlayer player, String name, UUID replaceId, ResourceKey<Level> dimension, BlockPos cornerA, BlockPos cornerB, boolean copyFluids, boolean copyNbtBlocks, boolean copyNbtSettings, boolean copyNbtAll, byte[] turns) {
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
        job.setCopyNbt(copyNbtBlocks, copyNbtSettings, copyNbtAll);
        job.setCopyTurns(turns);
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
        job.setPasteOptions(placeFluids, voidBlocks, voidFluids, voidNbt, placeNbt, placeSettings, placeAll);
        job.setAnchor(origin.offset(schematic.anchorX(), schematic.anchorY(), schematic.anchorZ()));
        com.wrights.blueprint.BlueprintSessions.BlueprintSession session = com.wrights.blueprint.BlueprintSessions.get(player);
        job.setSupply(session.supplyPos(), session.supplyDimension());
        job.setSupplyPlayerFirst(supplyPlayerFirst);
        if (queue && !instant(player) && freeUnits(player, library, job, 1).isEmpty()) {
            int blocks = placeableBlocks(schematic, job);
            int room = Math.min(Math.max(0, blocks), WrightsConfig.maxWrightsPerJob());
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
            Messages.actionBar(player, "message.wrights.queued", wanted);
            BlueprintNetwork.showJobHologram(player, job, schematic);
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (!instant(player) && !assignCrew(player, job, schematic, drones)) {
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
            Messages.actionBar(player, "message.wrights.queued", Math.max(1, job.crewQuota()));
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
        if (!instant(player) && freeUnits(player, library, job, 1).isEmpty()) {
            int blocks = Math.max(1, schematic.blockCount());
            int room = Math.min(blocks, WrightsConfig.maxWrightsPerJob());
            job.setCrewQuota(room);
            job.setQueued(true);
            library.addJob(job);
            Messages.actionBar(player, "message.wrights.queued", room);
            BlueprintNetwork.showJobHologram(player, job, schematic);
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (!instant(player) && !assignCrew(player, job, schematic, 0)) {
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
        if (previous != null) {
            schematic = schematic.withPaste(previous.placeFluids(), previous.voidBlocks(), previous.voidFluids(), previous.voidNbt());
        }
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
                announce(player, library, job, "hud.wrights.task_save", Component.translatable("message.wrights.copy_empty_skipped", job.skippedSpecial()));
            } else {
                announce(player, library, job, "hud.wrights.task_save", Component.translatable("message.wrights.copy_empty"));
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
                announce(player, library, job, "hud.wrights.task_save", Component.translatable("message.wrights.copy_empty"));
                BlueprintActions.finishSave(player, null, false);
            } else if (job.skippedSpecial() > 0) {
                announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved_skipped", saved.blockCount(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ()), job.skippedSpecial()));
                BlueprintActions.finishSave(player, saved.id(), true);
            } else {
                announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved", saved.blockCount(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ())));
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
            announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved_skipped", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()), job.skippedSpecial()));
        } else {
            announce(player, library, job, "hud.wrights.task_save", Component.translatable("hud.wrights.done_saved", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ())));
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
        if (job.queued()) {
            topUpQueued(player, library, job);
            if (!job.hasCrew()) {
                job.setWaitKind(shortOnItems(player, library, job) ? BuildJob.WAIT_ITEMS : BuildJob.WAIT_NONE);
                return;
            }
        }
        if (holdWithoutDrones(player, library, job)) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            return;
        }
        if (job.hasCrew()) {
            if (!inReach(player, library, job)) {
                job.setWaitKind(BuildJob.WAIT_NONE);
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
            return;
        }
        if (job.waitKind() != BuildJob.WAIT_ITEMS) {
            job.workedTick();
        }
        int at = nextSolo(level, player, schematic, job);
        if (at == SUPPORT_WAIT) {
            job.setWaitKind(BuildJob.WAIT_FLOOR);
            library.setDirty();
            return;
        }
        if (at == NEED_ITEMS) {
            job.setWaitKind(BuildJob.WAIT_ITEMS);
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
        if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
            double step = drone.distanceToSqr(hover) > 64.0D ? 0.85D : 0.4D;
            drone.moveToward(hover, step);
            job.setLastDronePos(drone.blockPosition());
            job.setWaitKind(BuildJob.WAIT_NONE);
            library.setDirty();
            return;
        }
        int budget = WrightsConfig.BLOCKS_PER_TICK.getAsInt();
        for (int i = 0; i < budget; i++) {
            int index = i == 0 ? at : nextSolo(level, player, schematic, job);
            if (index == SUPPORT_WAIT) {
                job.setWaitKind(BuildJob.WAIT_FLOOR);
                break;
            }
            if (index == NEED_ITEMS) {
                job.setWaitKind(BuildJob.WAIT_ITEMS);
                parkSolo(level, player, job);
                releaseStalledHome(player, library, job);
                break;
            }
            if (index < 0) {
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
                giveUpCell(job, entry, index);
                markSolo(job, index);
                continue;
            }
            PlaceResult placed = actOne(level, player, job, pos, entry, false, null);
            if (placed == PlaceResult.PAUSE) {
                job.setWaitKind(BuildJob.WAIT_CHUNK);
                if (job.tickMessage()) {
                    noteChunk(player, library, job);
                }
                library.setDirty();
                return;
            }
            if (placed == PlaceResult.WAIT) {
                job.setWaitKind(BuildJob.WAIT_ITEMS);
                break;
            }
            markSolo(job, index);
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
        if (holdWithoutDrones(player, library, job)) {
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
        BlueprintNetwork.clearJobHologram(player, job.id());
        BlueprintNetwork.syncJobs(player);
        Component detail = removed <= 0
                ? Component.translatable("message.wrights.undo_none")
                : Component.translatable("hud.wrights.done_undo", removed, returned);
        announce(player, library, job, "hud.wrights.task_undo", detail);
    }

    private static void finishPaste(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
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
            announce(player, library, job, null, detail);
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
        } else if (placed == 0) {
            detail = Component.translatable("message.wrights.already_built");
        } else {
            detail = Component.translatable("hud.wrights.done_placed", placed, skipped);
        }
        announce(player, library, job, null, detail);
    }

    /** Mode, the sub-modes that were on, and the task. The detail says what the job actually did. */
    private static void announce(ServerPlayer player, SchematicLibrary library, BuildJob job, String taskKey, Component detail) {
        if (player == null || job == null) {
            return;
        }
        Component title = jobTitle(player.getServer(), library, job, taskKey);
        Component line = detail == null ? Component.empty() : detail;
        if (line.getString().isEmpty()) {
            Messages.actionBar(player, title);
        } else {
            Messages.actionBar(player, title.copy().append(Component.literal(". ")).append(line));
        }
        BlueprintNetwork.jobDone(player, title, line, job.elapsedSeconds());
    }

    private static Component jobTitle(MinecraftServer server, SchematicLibrary library, BuildJob job, String taskKey) {
        Schematic schematic = job.schematicId() == null || library == null ? null : library.schematic(job.schematicId()).orElse(null);
        boolean mirror = job.mining() || (schematic != null && schematic.ephemeral() && com.wrights.blueprint.Mirror.marksBlocks(schematic.name()));
        String mode = mirror ? "hud.wrights.tool_mirror" : (job.kind() == BuildJob.Kind.COPY ? "hud.wrights.tool_new" : "hud.wrights.tool_build");
        String subject = subjectName(job, schematic);
        boolean blockPlace = mirror && subject.isEmpty() && !job.mining();
        String task = taskKey;
        if (task == null) {
            if (job.mining()) {
                task = "hud.wrights.task_mine";
            } else if (job.kind() == BuildJob.Kind.COPY) {
                task = "hud.wrights.task_save";
            } else if (blockPlace) {
                task = "hud.wrights.task_place";
            } else {
                task = "hud.wrights.task_build";
            }
        }
        MutableComponent title = Component.translatable(mode).append(" - ").append(Component.translatable(task));
        if (job.mining()) {
            if (job.mineAny()) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_mine_any"));
            }
            if (job.mineVoid()) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_mine_void"));
            }
            if (silkTouch(server, job.mineTool())) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_mine_silk"));
            }
        } else if (job.kind() == BuildJob.Kind.COPY) {
            if (job.copyFluids()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_fluids"));
            }
            if (job.copyNbtAll()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_nbt_all"));
            } else if (job.copyNbtSettings()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_nbt_settings"));
            } else if (job.copyNbtBlocks()) {
                title.append(" - ").append(Component.translatable("screen.wrights.copy_nbt_blocks"));
            }
        } else if (blockPlace) {
            if (job.voidBlocks()) {
                title.append(" - ").append(Component.translatable("menu.wrights.mirror_replace"));
            }
        } else if (!"hud.wrights.task_undo".equals(task) || job.voidBlocks() || job.voidFluids() || job.voidNbt()) {
            if (job.voidBlocks()) {
                title.append(" - ").append(Component.translatable("screen.wrights.void_blocks"));
            }
            if (job.voidFluids()) {
                title.append(" - ").append(Component.translatable("screen.wrights.void_fluids"));
            }
            if (job.voidNbt()) {
                title.append(" - ").append(Component.translatable("screen.wrights.void_nbt"));
            }
        }
        if (!subject.isEmpty()) {
            title.append(" - ").append(Component.literal(subject));
        }
        return title;
    }

    private static String subjectName(BuildJob job, Schematic schematic) {
        String name = job.blueprintName();
        if (name != null && (name.equals("Mirror - Mine") || name.equals("Build") || name.equals("Saving") || name.equals("Blueprint"))) {
            name = null;
        }
        if ((name == null || name.isBlank()) && schematic != null) {
            name = schematic.name();
        }
        if (name == null || name.isBlank()) {
            return "";
        }
        name = stripMirrorSuffix(name);
        if (name.equals("Mirror")) {
            return "";
        }
        return name;
    }

    private static String stripMirrorSuffix(String name) {
        if (name.endsWith(" · mirror")) {
            return name.substring(0, name.length() - " · mirror".length()).trim();
        }
        int cut = name.lastIndexOf(" · ");
        if (cut <= 0) {
            return name;
        }
        String tail = name.substring(cut + 3);
        if (tail.isEmpty()) {
            return name;
        }
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return name;
            }
        }
        return name.substring(0, cut).trim();
    }

    private static boolean silkTouch(MinecraftServer server, ItemStack tool) {
        if (server == null || tool == null || tool.isEmpty()) {
            return false;
        }
        var silk = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH);
        return silk.isPresent() && tool.getEnchantments().getLevel(silk.get()) > 0;
    }

    private static void noteChunk(ServerPlayer player, SchematicLibrary library, BuildJob job) {
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
    private static BlockPos[] jobBox(BuildJob job) {
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
    private static boolean inReach(ServerPlayer player, SchematicLibrary library, BuildJob job) {
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
                tellPortAt(player, player.getServer().getLevel(job.dimension()), shown, message, "message.wrights.paused_port_range_named");
            }
            BlueprintNetwork.syncJobs(player);
        } else if (job.tickMessage()) {
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
    /** Every block this wright can reach needs an item its supply does not have yet. */
    private static final int NEED_ITEMS = -4;

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
                int need = job.hasCrew() ? job.hands().size() : (instant(player) ? 0 : 1);
                int jobCap = WrightsConfig.maxWrightsPerJob();
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

    private static boolean usesDrones(BuildJob job) {
        return job.kind() == BuildJob.Kind.PASTE && !job.finished();
    }

    private static boolean claimedByOther(ServerPlayer player, SchematicLibrary library, UUID jobId, UUID unitId) {
        if (isReturning(unitId)) {
            return true;
        }
        for (BuildJob other : library.jobsFor(player.getUUID())) {
            if ((jobId != null && other.id().equals(jobId)) || !usesDrones(other) || other.awaitingDrones()) {
                continue;
            }
            if (!other.crewUnits().contains(unitId)) {
                continue;
            }
            // Home and waiting on items: the wright is not on the build, so another job may take it.
            if (other.waitKind() == BuildJob.WAIT_ITEMS && !unitIsOut(player, other, unitId)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean unitIsOut(ServerPlayer player, BuildJob job, UUID unitId) {
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
    private static boolean coversJob(ServerPlayer player, BuildJob job, BlockPos home) {
        BlockPos[] box = jobBox(job);
        if (home == null) {
            return WrightHomes.playerCovers(player, job.dimension(), box[0], box[1]);
        }
        return WrightHomes.portCoversArea(job.dimension(), home, job.dimension(), box[0], box[1]);
    }

    /**
     * Adds free wrights until the queued count is met.
     * Homes that cannot reach the build are left alone, and ones already reserved from out of range are sent back.
     */
    private static void topUpQueued(ServerPlayer player, SchematicLibrary library, BuildJob job) {
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
                owed = owed(level, schematic, job);
                if (owed.idle() && ids.isEmpty()) {
                    finishPaste(player.getServer(), library, player, job);
                    return;
                }
            }
        }
        if (need > 0 && level != null) {
            for (WrightHomes.FreeWright free : freeUnits(player, library, job, Integer.MAX_VALUE)) {
                UUID id = free.unit().id();
                if (ids.contains(id) || !coversJob(player, job, free.homePort())) {
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
                sendHome(player.getServer(), player, job);
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
            Messages.actionBar(player, "message.wrights.queue_ready", job.crewUnits().size());
        }
    }

    private static List<WrightHomes.FreeWright> freeUnits(ServerPlayer player, SchematicLibrary library, BuildJob job, int limit) {
        ServerLevel level = player.getServer().getLevel(job.dimension());
        if (level == null) {
            return List.of();
        }
        boolean playerFirst = com.wrights.blueprint.BlueprintSessions.get(player).portPlayerFirst();
        BlockPos[] box = jobBox(job);
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
            BlockPos[] box = jobBox(job);
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

    private static void explainResume(ServerPlayer player, BuildJob job) {
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
        BlockPos[] box = jobBox(job);
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
        List<UUID> kept = new ArrayList<>();
        List<BlockPos> homes = new ArrayList<>();
        ServerLevel jobLevel = player.getServer().getLevel(job.dimension());
        for (UUID unitId : ids) {
            releaseHomeWaiter(player, library, unitId, job.id());
            WrightHomes.FreeWright found = WrightHomes.locate(jobLevel, player, unitId, job.min());
            if (found == null) {
                continue;
            }
            speeds.add(WrightTier.clamp(found.unit().speed()));
            kept.add(unitId);
            homes.add(found.homePort());
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
                if (!homes.isEmpty()) {
                    homes.remove(homes.size() - 1);
                }
            }
        }
        if (kept.isEmpty()) {
            discardDrone(player.getServer(), job);
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
                hand.speed = WrightTier.clamp(speeds.get(i));
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
        for (int i = 0; i < job.hands().size() && i < homes.size(); i++) {
            job.hands().get(i).homePort = homes.get(i);
        }
    }

    private static boolean missingDrones(ServerPlayer player, BuildJob job) {
        if (instant(player) || job.kind() != BuildJob.Kind.PASTE) {
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
    private static boolean restorePausedCrew(ServerPlayer player, SchematicLibrary library, BuildJob job) {
        if (instant(player) || job.kind() != BuildJob.Kind.PASTE || job.finished()) {
            return true;
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

    private static boolean holdWithoutDrones(ServerPlayer player, SchematicLibrary library, BuildJob job) {
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
    private enum CrewGap {
        OUT_OF_RANGE,
        NONE_AVAILABLE,
        PORT_EMPTY
    }

    private static void tellNoCrew(ServerPlayer player, BuildJob job, boolean paused) {
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

    private static void tellPortAt(ServerPlayer player, ServerLevel level, BlockPos pos, String plainKey, String namedKey) {
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
    private static boolean crewReady(ServerPlayer player, ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos site) {
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

    private static CrewGap crewGap(ServerPlayer player, BuildJob job) {
        ServerLevel level = player.getServer().getLevel(job.dimension());
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BlockPos[] box = jobBox(job);
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
    private static void glowClosestPort(ServerPlayer player, ServerLevel level, BlockPos site) {
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

    private static boolean assignCrew(ServerPlayer player, BuildJob job, Schematic schematic) {
        return assignCrew(player, job, schematic, 0);
    }

    private static boolean assignCrew(ServerPlayer player, BuildJob job, Schematic schematic, int requested) {
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
        int room = Math.min(blocks, WrightsConfig.maxWrightsPerJob());
        int wanted = requested > 0 ? Math.min(requested, room) : room;
        List<WrightHomes.FreeWright> units = freeUnits(player, library, job, wanted);
        if (units.isEmpty()) {
            tellNoCrew(player, job, false);
            if (crewGap(player, job) != CrewGap.NONE_AVAILABLE) {
                glowClosestPort(player, player.getServer().getLevel(job.dimension()), job.min());
            }
            return false;
        }
        List<Integer> speeds = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (WrightHomes.FreeWright unit : units) {
            releaseHomeWaiter(player, library, unit.unit().id(), job.id());
            speeds.add(WrightTier.clamp(unit.unit().speed()));
            ids.add(unit.unit().id());
        }
        job.setCrew(speeds, sizeX, sizeY, sizeZ);
        job.setCrewUnits(ids);
        for (int i = 0; i < job.hands().size() && i < units.size(); i++) {
            job.hands().get(i).homePort = units.get(i).homePort();
        }
        job.setCrewQuota(ids.size());
        evenOut(job, schematic);
        return job.hasCrew();
    }

    private static void tickCrew(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job, Schematic schematic) {
        boolean working = false;
        boolean scanning = false;
        boolean waiting = false;
        boolean waitingChunk = false;
        boolean waitingItems = false;
        int[] skipBudget = {8192};
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.sheltering) {
                working = true;
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_SHELTER);
                continue;
            }
            hand.waitingOnOthers = false;
            int index = nextWork(level, player, schematic, job, hand, skipBudget);
            if (index == -2) {
                scanning = true;
                recallHand(level, player, hand);
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_NONE);
                continue;
            }
            if (index == SUPPORT_WAIT) {
                waiting = true;
                hand.awaitingItems = false;
                hand.waitingOnOthers = true;
                hand.idle = false;
                recallHand(level, player, hand);
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_FLOOR);
                continue;
            }
            if (index == NEED_ITEMS) {
                waiting = true;
                waitingItems = true;
                parkForItems(level, player, hand);
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_ITEMS);
                continue;
            }
            if (index < 0) {
                // Own share is empty — help another wright, or go idle and head home.
                int borrowed = borrowWork(level, player, schematic, job, hand, true);
                if (borrowed >= 0) {
                    index = borrowed;
                } else {
                    hand.awaitingItems = false;
                    hand.idle = true;
                    publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_IDLE);
                    continue;
                }
            }
            hand.awaitingItems = false;
            hand.idle = false;
            working = true;
            int handStatus = job.paused() ? DroneEntity.WAIT_PAUSED : DroneEntity.WAIT_NONE;
            Schematic.Entry next = schematic.entry(index);
            BlockPos nextPos = job.min().offset(next.x(), next.y(), next.z());
            DroneEntity drone = ensureHand(level, player, job, hand, nextPos);
            if (drone != null) {
                drone.setTier(hand.speed);
            }
            if (drone == null) {
                waitingChunk = true;
                publishHandStatus(level, player, schematic, job, hand, DroneEntity.WAIT_CHUNK);
                continue;
            }
            Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.85D + (hand.lane % 2) * 0.45D, 0.0D);
            if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
                drone.moveToward(hover, flyStep(hand.speed, drone.distanceToSqr(hover)));
                hand.lastPos = drone.blockPosition();
                publishHandStatus(level, player, schematic, job, hand, handStatus);
                continue;
            }
            int budget = placeBudget(hand);
            for (int n = 0; n < budget; n++) {
                int at = nextWork(level, player, schematic, job, hand, skipBudget);
                if (at == -2) {
                    scanning = true;
                    recallHand(level, player, hand);
                    break;
                }
                if (at == SUPPORT_WAIT) {
                    waiting = true;
                    hand.waitingOnOthers = true;
                    handStatus = DroneEntity.WAIT_FLOOR;
                    recallHand(level, player, hand);
                    break;
                }
                if (at == NEED_ITEMS) {
                    waiting = true;
                    waitingItems = true;
                    handStatus = DroneEntity.WAIT_ITEMS;
                    parkForItems(level, player, hand);
                    break;
                }
                if (at < 0) {
                    hand.idle = true;
                    handStatus = DroneEntity.WAIT_IDLE;
                    break;
                }
                Schematic.Entry entry = schematic.entry(at);
                BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
                if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
                    break;
                }
                if (entry.secondary() && supportWaiting(level, job, hand, at, pos, entry)) {
                    waiting = true;
                    hand.waitingOnOthers = true;
                    handStatus = DroneEntity.WAIT_FLOOR;
                    recallHand(level, player, hand);
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
                    recallHand(level, player, hand);
                    break;
                }
                noteProgress(schematic, job, hand, entry, at);
            }
            publishHandStatus(level, player, schematic, job, hand, handStatus);
        }
        shareWithIdle(level, job, schematic);
        releaseIdle(level, player, job, schematic);
        parkWaiters(level, player, job, working);
        if (!working && !scanning) {
            for (BuildJob.Hand hand : job.hands()) {
                int peek = nextInLane(level, player, schematic, job, hand);
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
        library.setDirty();
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

    /** The upper half of a door or bed cannot go down until the block under it is there. */
    private static boolean secondaryWaiting(ServerLevel level, BuildJob job, Schematic.Entry entry) {
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (!level.isLoaded(pos.below())) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() != entry.state().getBlock() && (below.isAir() || below.canBeReplaced());
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
        if (job.mining()) {
            return !com.wrights.blueprint.MirrorLaunch.accepts(level, pos, entry.state().getBlock(), job.mineAny());
        }
        if (skippedContent(job, entry)) {
            return true;
        }
        // Already placed or skipped once. Do not retry if the block later pops (cactus) or is broken.
        if (job.isSettled(entry.x(), entry.y(), entry.z())) {
            return true;
        }
        return level.getBlockState(pos).equals(entry.state());
    }

    private static int nextWork(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, int[] budget) {
        int seen = -1;
        while (budget[0] > 0) {
            int index = nextInLane(level, player, schematic, job, hand);
            if (index == SUPPORT_WAIT || index == NEED_ITEMS) {
                return index;
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
                    giveUpCell(job, entry, index);
                    noteProgress(schematic, job, hand, entry, index);
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
        }
        int rest = nextInLane(level, player, schematic, job, hand);
        if (rest == SUPPORT_WAIT || rest == NEED_ITEMS) {
            return rest;
        }
        return rest < 0 ? -1 : -2;
    }

    private static int nextInLane(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        if (hand.slices.isEmpty()) {
            for (BuildJob.Hand other : job.hands()) {
                if (!other.slices.isEmpty()) {
                    return -1;
                }
            }
            java.util.function.Predicate<Schematic.Entry> lane = entry -> CrewSplit.lane(entry.x(), entry.y(), entry.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
            int found = scan(level, player, schematic, job, hand, hand.cursor, hand.probe, lane);
            if (found < 0 && Math.max(hand.cursor, hand.probe) > hand.cursor) {
                hand.probe = hand.cursor;
                found = prefer(found, scan(level, player, schematic, job, hand, hand.cursor, hand.cursor, lane));
            }
            if (found >= 0) {
                hand.probe = found;
                return found;
            }
            // Empty share, waiting on floor, or short on items — try helping another wright.
            int borrowed = borrowWork(level, player, schematic, job, hand, found < 0);
            if (borrowed >= 0) {
                return borrowed;
            }
            return found;
        }
        int best = -1;
        boolean waiting = false;
        boolean shortItems = false;
        for (BuildJob.Slice slice : hand.slices) {
            int found = scanSlice(level, player, schematic, job, hand, slice);
            if (found == SUPPORT_WAIT) {
                waiting = true;
                continue;
            }
            if (found == NEED_ITEMS) {
                shortItems = true;
                continue;
            }
            if (found >= 0 && (best < 0 || found < best)) {
                best = found;
            }
        }
        if (best >= 0) {
            return best;
        }
        int borrowed = borrowWork(level, player, schematic, job, hand, !waiting && !shortItems);
        if (borrowed >= 0) {
            return borrowed;
        }
        if (shortItems) {
            return NEED_ITEMS;
        }
        return waiting ? SUPPORT_WAIT : -1;
    }

    private static int scanSlice(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, BuildJob.Slice slice) {
        int start = Math.max(slice.cursor, slice.probe);
        java.util.function.Predicate<Schematic.Entry> include = entry -> slice.contains(entry.x(), entry.y(), entry.z());
        int found = scan(level, player, schematic, job, hand, slice.cursor, start, include);
        // A falling block can sit ahead of the floor it needs. Look back once so that floor is still placed.
        if (found < 0 && start > slice.cursor) {
            slice.probe = slice.cursor;
            found = prefer(found, scan(level, player, schematic, job, hand, slice.cursor, slice.cursor, include));
        }
        if (found >= 0) {
            slice.probe = found;
        }
        return found;
    }

    /** A later look that finds a real block wins. A missing item is remembered when the look-back finds nothing else. */
    private static int prefer(int first, int wrapped) {
        if (wrapped >= 0) {
            return wrapped;
        }
        if (first == NEED_ITEMS || wrapped == NEED_ITEMS) {
            return NEED_ITEMS;
        }
        if (first == SUPPORT_WAIT || wrapped == SUPPORT_WAIT) {
            return SUPPORT_WAIT;
        }
        return wrapped;
    }

    /**
     * Next block this range can act on. A falling block whose floor is not down yet is left in place,
     * so the wright can build that floor and wrap back to the sand or gravel. A block this supply cannot
     * pay for is left in place too, and the scan keeps looking.
     */
    private static int scan(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, int cursor, int start, java.util.function.Predicate<Schematic.Entry> include) {
        boolean waiting = false;
        boolean shortItems = false;
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
            Falling hold = falling(level, schematic, job, entry);
            if (hold == Falling.WAIT) {
                waiting = true;
                continue;
            }
            if (hold == Falling.SKIP) {
                giveUpCell(job, entry, i);
                continue;
            }
            if (entry.secondary() && secondaryWaiting(level, job, entry)) {
                waiting = true;
                continue;
            }
            if (!canPay(player, level, job, hand, entry)) {
                shortItems = true;
                continue;
            }
            return i;
        }
        if (shortItems) {
            return NEED_ITEMS;
        }
        return waiting ? SUPPORT_WAIT : -1;
    }

    /**
     * Another part of this build that this wright can pay for.
     * {@code takeOwned} is true when this wright's own share is empty, so it may help with
     * cells that still belong to another wright.
     */
    private static int borrowWork(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job, BuildJob.Hand hand, boolean takeOwned) {
        for (int i = 0; i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                continue;
            }
            if (falling(level, schematic, job, entry) != Falling.READY) {
                continue;
            }
            if (!canPay(player, level, job, hand, entry)) {
                continue;
            }
            if (!takeOwned && heldByOther(level, player, job, hand, entry)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean heldByOther(ServerLevel level, ServerPlayer player, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand || other.idle || other.awaitingItems || other.waitingOnOthers || !ownsEntry(job, other, entry)) {
                continue;
            }
            if (canPay(player, level, job, other, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsEntry(BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        if (hand.slices.isEmpty()) {
            return CrewSplit.lane(entry.x(), entry.y(), entry.z(), hand.lanes, job.spanX(), job.spanY(), job.spanZ()) == hand.lane;
        }
        for (BuildJob.Slice slice : hand.slices) {
            if (slice.contains(entry.x(), entry.y(), entry.z())) {
                return true;
            }
        }
        return false;
    }

    private static boolean canPay(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Schematic.Entry entry) {
        if (job.mining() || entry.secondary() || freePlace(player)) {
            return true;
        }
        Item payment = com.wrights.util.BlockFilters.payment(entry.state());
        return payment == null || hasItem(player, level, job, hand, payment);
    }

    private enum Falling {
        READY,
        WAIT,
        SKIP
    }

    private static Falling falling(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry) {
        if (job.mining()) {
            return Falling.READY;
        }
        if (BlockFilters.falls(entry.state())) {
            return fallingAt(level, schematic, job, entry, new HashSet<>());
        }
        if (BlockFilters.needsGround(entry.state())) {
            return groundAt(level, schematic, job, entry, new HashSet<>());
        }
        return Falling.READY;
    }

    /**
     * A plant with nothing under it, and no block in the build that will land there, cannot be placed.
     * Waiting would sit forever when the ground layer of the pattern is the plant itself.
     */
    private static Falling groundAt(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry, Set<Long> seen) {
        if (!seen.add(BlockPos.asLong(entry.x(), entry.y(), entry.z()))) {
            return Falling.SKIP;
        }
        if (BlockFilters.isCactus(entry.state())) {
            return cactusAt(level, schematic, job, entry, seen);
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        BlockPos below = pos.below();
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || !level.isLoaded(below)) {
            return Falling.WAIT;
        }
        if (entry.state().canSurvive(level, pos)) {
            return Falling.READY;
        }
        int support = schematic.indexAt(entry.x(), entry.y() - 1, entry.z());
        if (support >= 0 && floorComing(level, schematic, job, support, seen)) {
            return Falling.WAIT;
        }
        return Falling.SKIP;
    }

    /**
     * A cactus tower is only valid when sand sits under its lowest cactus. That sand may be inside
     * the blueprint, or — when the cactus is on the bottom layer — already in the world under the paste.
     */
    private static Falling cactusAt(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry, Set<Long> seen) {
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return Falling.WAIT;
        }
        int baseY = entry.y();
        while (baseY > 0) {
            int underCactus = schematic.indexAt(entry.x(), baseY - 1, entry.z());
            if (underCactus < 0 || !BlockFilters.isCactus(schematic.entry(underCactus).state())) {
                break;
            }
            baseY--;
        }
        BlockPos soilPos = job.min().offset(entry.x(), baseY, entry.z()).below();
        if (level.isOutsideBuildHeight(soilPos) || !level.isLoaded(soilPos)) {
            return Falling.WAIT;
        }
        int soilIndex = schematic.indexAt(entry.x(), baseY - 1, entry.z());
        BlockState soilNow = level.getBlockState(soilPos);
        if (BlockFilters.cactusSoil(soilNow)) {
            if (entry.state().canSurvive(level, pos)) {
                return Falling.READY;
            }
            // Upper cactus waiting on a lower cactus in this column.
            if (entry.y() > baseY) {
                int lower = schematic.indexAt(entry.x(), entry.y() - 1, entry.z());
                if (lower >= 0 && floorComing(level, schematic, job, lower, seen)) {
                    return Falling.WAIT;
                }
            }
            // Neighbors (or a missing mid-tower piece that will never land) make this illegal.
            return Falling.SKIP;
        }
        if (soilIndex >= 0) {
            Schematic.Entry soil = schematic.entry(soilIndex);
            if (BlockFilters.cactusSoil(soil.state())) {
                if (floorComing(level, schematic, job, soilIndex, seen)) {
                    return Falling.WAIT;
                }
                // Sand in the pattern will not be placed (void off over stone, etc.).
                return Falling.SKIP;
            }
            // Pattern puts a non-sand block under the tower — cactus can never live there.
            return Falling.SKIP;
        }
        // Lowest cactus is on the bottom of the blueprint: world under the paste must already be sand.
        return Falling.SKIP;
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
        if (job.isSettled(floor.x(), floor.y(), floor.z())) {
            return false;
        }
        if (!stillQueued(job, floor.x(), floor.y(), floor.z(), support)) {
            return false;
        }
        // Occupied without void, or otherwise never placeable — do not wait forever for it.
        if (!canEventuallyPlace(level, job, floor)) {
            return false;
        }
        if (BlockFilters.falls(floor.state())) {
            return fallingAt(level, schematic, job, floor, seen) != Falling.SKIP;
        }
        if (BlockFilters.needsGround(floor.state())) {
            return groundAt(level, schematic, job, floor, seen) != Falling.SKIP;
        }
        return true;
    }

    /** False when this cell will be skipped (solid in the way, kept machine, clipped content). */
    private static boolean canEventuallyPlace(ServerLevel level, BuildJob job, Schematic.Entry entry) {
        if (job.outsideClip(entry) || skippedContent(job, entry)) {
            return false;
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        if (!level.isLoaded(pos)) {
            return true;
        }
        BlockState existing = level.getBlockState(pos);
        if (existing.equals(entry.state())) {
            return false;
        }
        if (keptStored(job, existing, entry.state())) {
            return false;
        }
        if (!shouldVoid(job, existing, entry.state()) && !existing.isAir() && !existing.canBeReplaced()) {
            return false;
        }
        return true;
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

    private static int nextSolo(ServerLevel level, ServerPlayer player, Schematic schematic, BuildJob job) {
        int start = Math.max(job.cursor(), job.pasteProbe());
        int found = scan(level, player, schematic, job, null, job.cursor(), start, entry -> true);
        if (found < 0 && start > job.cursor()) {
            job.setPasteProbe(job.cursor());
            found = prefer(found, scan(level, player, schematic, job, null, job.cursor(), job.cursor(), entry -> true));
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
        // Hover text does not need per-tick updates; skip the schematic scan most ticks.
        if (reason == drone.waitReason() && (level.getGameTime() + Math.max(0, hand.lane)) % 5L != 0L) {
            return;
        }
        int[] targets = peekHandTargets(level, schematic, job, hand);
        ItemStack current = stackFor(schematic, targets[0]);
        ItemStack next = stackFor(schematic, targets[1]);
        drone.setWorkStatus(current, next, reason);
    }

    /**
     * First actionable cell this hand owns (or can help with), then the one after it.
     * Does not move cursors. Negative indexes mean none.
     */
    private static int[] peekHandTargets(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        int current = -1;
        int next = -1;
        // Compute once — calling countOpen inside the loop was O(n²) on large blueprints and hitching flight.
        boolean ownShareEmpty = countOpen(level, schematic, job, hand) == 0;
        for (int i = 0; i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                continue;
            }
            if (!ownShareEmpty && !ownsEntry(job, hand, entry)) {
                continue;
            }
            if (falling(level, schematic, job, entry) != Falling.READY) {
                continue;
            }
            if (current < 0) {
                current = i;
                continue;
            }
            next = i;
            break;
        }
        return new int[] { current, next };
    }

    private static ItemStack stackFor(Schematic schematic, int index) {
        if (schematic == null || index < 0 || index >= schematic.blockCount()) {
            return ItemStack.EMPTY;
        }
        return BlockFilters.icon(schematic.entry(index).state());
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

    private static void shareWithIdle(ServerLevel level, BuildJob job, Schematic schematic) {
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
                if (countOpen(level, schematic, job, hand) == 0) {
                    receiver = hand;
                    break;
                }
                hand.idle = false;
            }
            if (receiver == null) {
                return;
            }
            BuildJob.Slice donor = fullestPaste(level, job, schematic, receiver);
            BuildJob.Slice half = donor == null ? null : splitPaste(level, schematic, job, donor);
            if (half == null) {
                return;
            }
            receiver.slices.add(half);
            receiver.idle = false;
        }
    }

    /** A wright with nothing left to place (and nothing left to help with) goes home. */
    private static void releaseIdle(ServerLevel level, ServerPlayer player, BuildJob job, Schematic schematic) {
        if (schematic == null || job.hands().size() < 2) {
            return;
        }
        boolean busy = false;
        int going = 0;
        int[] open = new int[job.hands().size()];
        for (int i = 0; i < job.hands().size(); i++) {
            BuildJob.Hand hand = job.hands().get(i);
            open[i] = countOpen(level, schematic, job, hand);
            if (hand.idle && open[i] == 0) {
                going++;
            } else if (!hand.idle) {
                busy = true;
            }
        }
        if (!busy || going == 0) {
            return;
        }
        int slot = 0;
        for (int i = 0; i < job.hands().size(); i++) {
            BuildJob.Hand hand = job.hands().get(i);
            if (!hand.idle || open[i] > 0 || hand.entityId == null) {
                continue;
            }
            recall(level, player, hand.entityId, slot++, going, hand.homePort);
            hand.entityId = null;
        }
    }

    /** A wright with nothing to place until another wright finishes the block under it goes home. */
    private static void parkWaiters(ServerLevel level, ServerPlayer player, BuildJob job, boolean someoneWorking) {
        if (!someoneWorking || job.hands().size() < 2) {
            return;
        }
        boolean othersBusy = false;
        int going = 0;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.waitingOnOthers && hand.entityId != null) {
                going++;
            } else if (!hand.idle && !hand.waitingOnOthers) {
                othersBusy = true;
            }
        }
        if (!othersBusy || going == 0) {
            return;
        }
        int slot = 0;
        for (BuildJob.Hand hand : job.hands()) {
            if (!hand.waitingOnOthers || hand.entityId == null) {
                continue;
            }
            recall(level, player, hand.entityId, slot++, going, hand.homePort);
            hand.entityId = null;
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

    /** Blocks still left to place or skip in this wright's slices. */
    private static int countOpen(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Hand hand) {
        int count = 0;
        for (BuildJob.Slice slice : hand.slices) {
            count += countOpen(level, schematic, job, slice);
        }
        if (!hand.slices.isEmpty()) {
            return count;
        }
        for (int i = 0; i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!ownsEntry(job, hand, entry)) {
                continue;
            }
            if (isOpenCell(level, schematic, job, entry)) {
                count++;
            }
        }
        return count;
    }

    private static int countOpen(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Slice slice) {
        int count = 0;
        for (int i = 0; i < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z())) {
                continue;
            }
            if (isOpenCell(level, schematic, job, entry)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOpenCell(ServerLevel level, Schematic schematic, BuildJob job, Schematic.Entry entry) {
        if (job.outsideClip(entry) || skippedContent(job, entry)) {
            return false;
        }
        // Settled / matching world cells are done — skip the heavier survival checks.
        if (job.isSettled(entry.x(), entry.y(), entry.z())) {
            return false;
        }
        BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
        if (level.isLoaded(pos) && level.getBlockState(pos).equals(entry.state())) {
            return false;
        }
        return falling(level, schematic, job, entry) != Falling.SKIP;
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

    private static BuildJob.Slice fullestPaste(ServerLevel level, BuildJob job, Schematic schematic, BuildJob.Hand except) {
        BuildJob.Slice donor = null;
        int most = 1;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand == except) {
                continue;
            }
            for (BuildJob.Slice slice : hand.slices) {
                int left = countOpen(level, schematic, job, slice);
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

    private static BuildJob.Slice splitPaste(ServerLevel level, Schematic schematic, BuildJob job, BuildJob.Slice slice) {
        int count = countOpen(level, schematic, job, slice);
        if (count < 2) {
            return null;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int at = 0;
        for (int i = 0; i < schematic.blockCount() && at < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (!slice.contains(entry.x(), entry.y(), entry.z()) || !isOpenCell(level, schematic, job, entry)) {
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
                PlaceResult placed = actOne(level, player, job, pos, entry, true, null);
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
        ItemStack carried = com.wrights.blueprint.MirrorLaunch.harvest(player, level, pos, job.mineTool(), job.mineVoid());
        showCarry(level, job, hand, carried);
        clearPlacedGhost(player, job, entry);
        job.placedOne();
        return PlaceResult.DONE;
    }

    /** The wright that just mined keeps that drop visible until it docks. */
    private static void showCarry(ServerLevel level, BuildJob job, BuildJob.Hand hand, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        UUID id = hand != null && hand.entityId != null ? hand.entityId : job.droneId();
        if (id == null) {
            return;
        }
        if (level.getEntity(id) instanceof DroneEntity drone && drone.isAlive()) {
            drone.setCarried(stack);
        }
    }

    private static PlaceResult placeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet) {
        return placeOne(level, player, job, pos, entry, quiet, null);
    }

    private static PlaceResult placeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet, BuildJob.Hand hand) {
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
        } else if (!freePlace(player) && payment != null) {
            if (!hasItem(player, level, job, hand, payment)) {
                return PlaceResult.WAIT;
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
            return skipCell(player, job, entry);
        }
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, existing, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            level.setBlock(pos, existing, Block.UPDATE_ALL);
            return skipCell(player, job, entry);
        }
        boolean paid = !entry.secondary() && !freePlace(player) && payment != null;
        if (paid) {
            consumeItem(player, level, job, hand, payment);
        }
        boolean first = !job.isSettled(entry.x(), entry.y(), entry.z());
        applyEntryNbt(level, player, job, pos, entry);
        job.recordPlaced(entry.x(), entry.y(), entry.z(), after, paid);
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

    /** Count a skip once and mark the cell settled so wrap-back does not retry it. */
    private static PlaceResult skipCell(ServerPlayer player, BuildJob job, Schematic.Entry entry) {
        boolean first = !job.isSettled(entry.x(), entry.y(), entry.z());
        clearPlacedGhost(player, job, entry);
        if (first) {
            job.skippedOne();
        }
        return PlaceResult.DONE;
    }

    /** Falling/ground gave up: count once, settle the cell, never ask for it again. */
    private static void giveUpCell(BuildJob job, Schematic.Entry entry, int index) {
        if (job.retire(index)) {
            job.skippedOne();
        }
        job.settleGhost(entry.x(), entry.y(), entry.z());
    }

    /** The cell is filled, so the preview ghost for it is finished. */
    private static void clearPlacedGhost(ServerPlayer player, BuildJob job, Schematic.Entry entry) {
        job.settleGhost(entry.x(), entry.y(), entry.z());
        com.wrights.network.BlueprintNetwork.dropJobGhost(player, job.id(), entry.x(), entry.y(), entry.z());
    }

    private static boolean skippedContent(BuildJob job, Schematic.Entry entry) {
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

    private static boolean creative(ServerPlayer player) {
        return player.getAbilities().instabuild && !player.isSpectator();
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
        return hasItem(player, level, job, null, item);
    }

    /**
     * Personal wrights may use the player inventory. A placed port never does: only its own chest, inside its
     * boundary, and the shared digital store.
     */
    private static boolean hasItem(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Item item) {
        com.wrights.storage.DigitalLink link = com.wrights.storage.ItemSource.linkOf(player);
        if (hand != null && hand.homePort != null) {
            return com.wrights.storage.ItemSource.hasForPort(player, level, item, portChest(level, hand.homePort), link);
        }
        Container container = supply(level, player, job);
        boolean playerFirst = job.supplyPos() == null || job.supplyPlayerFirst();
        return com.wrights.storage.ItemSource.hasForJob(player, level, item, container, playerFirst, link);
    }

    private static void consumeItem(ServerPlayer player, ServerLevel level, BuildJob job, Item item) {
        consumeItem(player, level, job, null, item);
    }

    private static void consumeItem(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, Item item) {
        com.wrights.storage.DigitalLink link = com.wrights.storage.ItemSource.linkOf(player);
        if (hand != null && hand.homePort != null) {
            com.wrights.storage.ItemSource.consumeForPort(player, level, item, portChest(level, hand.homePort), link);
            return;
        }
        Container container = supply(level, player, job);
        boolean playerFirst = job.supplyPos() == null || job.supplyPlayerFirst();
        com.wrights.storage.ItemSource.consumeForJob(player, level, item, container, playerFirst, link);
    }

    /** The chest bound to this port, when it is still inside that port's chunk square. */
    private static Container portChest(ServerLevel level, BlockPos portPos) {
        if (portPos == null || !level.isLoaded(portPos)) {
            return null;
        }
        if (!(level.getBlockEntity(portPos) instanceof com.wrights.block.WrightportBlockEntity port)) {
            return null;
        }
        BlockPos chestPos = port.supplyChest();
        if (chestPos == null || !level.isLoaded(chestPos)) {
            return null;
        }
        if (!com.wrights.drone.WrightHomes.portCovers(level.dimension(), portPos, level.dimension(), chestPos)) {
            return null;
        }
        return containerAt(level, chestPos);
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
                bindUnit(job, drone, 0);
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
            bindUnit(job, drone, 0);
            return drone;
        }
        if (stillFlyingHome(job, 0)) {
            return null;
        }
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        bindUnit(job, drone, 0);
        Vec3 site = Vec3.atCenterOf(workPos).add(0.0D, 1.15D, 0.0D);
        BlockPos home = job.hands().isEmpty() ? null : job.hands().get(0).homePort;
        depart(drone, player, site, 0, 1, home);
        job.setDroneId(drone.getUUID());
        job.setLastDronePos(drone.blockPosition());
        level.addFreshEntity(drone);
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
        return handIndex >= 0 && handIndex < job.crewUnits().size() && isReturning(job.crewUnits().get(handIndex));
    }

    private static void paint(DroneEntity drone, BuildJob job) {
        int tier = job.hands().isEmpty() ? 1 : job.hands().get(0).speed;
        drone.setTier(tier);
    }

    private static DroneEntity ensureHand(ServerLevel level, ServerPlayer player, BuildJob job, BuildJob.Hand hand, BlockPos workPos) {
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
        AABB search = new AABB(workPos).inflate(96.0D);
        for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, search, entity -> entity.isAlive() && entity.jobId().filter(job.id()::equals).isPresent())) {
            if (job.ownsDrone(drone.getUUID())) {
                continue;
            }
            hand.entityId = drone.getUUID();
            hand.lastPos = drone.blockPosition();
            carryStrand(job, hand, drone);
            bindUnit(job, drone, job.hands().indexOf(hand));
            return drone;
        }
        if (stillFlyingHome(job, job.hands().indexOf(hand))) {
            return null;
        }
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        bindUnit(job, drone, job.hands().indexOf(hand));
        Vec3 site = Vec3.atCenterOf(workPos).add(0.0D, 1.15D, 0.0D);
        depart(drone, player, site, hand.lane, hand.lanes, hand.homePort);
        hand.entityId = drone.getUUID();
        hand.lastPos = drone.blockPosition();
        carryStrand(job, hand, drone);
        level.addFreshEntity(drone);
        return drone;
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
    private static void shelterLost(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job) {
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
            BlockPos docked = DockRecovery.recover(level, player, where, id, tier, name);
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

    private static void depart(DroneEntity drone, ServerPlayer player, Vec3 work, int lane, int lanes, BlockPos homePort) {
        if (!WrightsConfig.flyToJob()) {
            drone.setHomePort(homePort);
            float yaw = player == null ? 0.0F : player.getYRot();
            drone.moveTo(work.x, work.y, work.z, yaw, 0.0F);
            drone.setYRot(yaw);
            return;
        }
        if (homePort != null && player != null && player.level() == drone.level() && player.level().isLoaded(homePort)) {
            drone.departFromBlock(homePort, work, lane, lanes);
            return;
        }
        if (player != null && player.level() == drone.level()) {
            drone.departFromPlayer(player, work, lane, lanes);
            return;
        }
        drone.moveTo(work.x, work.y, work.z, player == null ? 0.0F : player.getYRot(), 0.0F);
    }

    private static void parkForItems(ServerLevel level, ServerPlayer player, BuildJob.Hand hand) {
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

    private static void parkSolo(ServerLevel level, ServerPlayer player, BuildJob job) {
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
        int cap = WrightsConfig.maxWrightsPerJob();
        BuildJob best = null;
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (job == source || job.finished() || job.undoing() || job.kind() != BuildJob.Kind.PASTE) {
                continue;
            }
            if (job.crewUnits().size() >= cap || !coversJob(player, job, hand.homePort)) {
                continue;
            }
            ServerLevel level = server.getLevel(job.dimension());
            Schematic schematic = job.schematicId() == null ? null : library.schematic(job.schematicId()).orElse(null);
            if (level == null || schematic == null) {
                continue;
            }
            int room = placeableBlocks(schematic, job);
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
            if (unitIsOut(player, job, units.get(i))) {
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
        for (WrightHomes.FreeWright free : freeUnits(player, library, job, Integer.MAX_VALUE)) {
            if (owed.allows(player, level, job, free.homePort())) {
                return false;
            }
        }
        return !owed.allows(player, level, job, null);
    }

    /** Takes a wright that went home to wait for items off that job, so a new job can have it. */
    private static void releaseHomeWaiter(ServerPlayer player, SchematicLibrary library, UUID unitId, UUID keepJob) {
        for (BuildJob other : new ArrayList<>(library.jobsFor(player.getUUID()))) {
            if (keepJob != null && other.id().equals(keepJob)) {
                continue;
            }
            if (!usesDrones(other) || other.awaitingDrones() || other.waitKind() != BuildJob.WAIT_ITEMS) {
                continue;
            }
            if (!other.crewUnits().contains(unitId) || unitIsOut(player, other, unitId)) {
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
            placeCrew(player, library, source, from);
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
        placeCrew(player, library, target, to);
        target.setPaused(false);
        target.clearHold();
        if (target.crewQuota() < target.crewUnits().size()) {
            target.setCrewQuota(target.crewUnits().size());
        }
        library.setDirty();
    }

    /** What is still unplaced, and whether any of it needs no item. */
    private static Owed owed(ServerLevel level, Schematic schematic, BuildJob job) {
        Owed owed = new Owed();
        int count = Math.min(job.total(), schematic.blockCount());
        for (int i = 0; i < count; i++) {
            Schematic.Entry entry = schematic.entry(i);
            if (job.outsideClip(entry) || satisfied(level, job, entry)) {
                continue;
            }
            if (!level.isLoaded(job.min().offset(entry.x(), entry.y(), entry.z()))) {
                owed.open = true;
                continue;
            }
            Falling hold = falling(level, schematic, job, entry);
            if (hold == Falling.WAIT) {
                owed.pending = true;
                continue;
            }
            if (hold == Falling.SKIP || entry.secondary()) {
                owed.open = true;
                continue;
            }
            Item payment = com.wrights.util.BlockFilters.payment(entry.state());
            if (payment == null) {
                owed.open = true;
                continue;
            }
            if (owed.items.size() < 64 && !owed.items.contains(payment)) {
                owed.items.add(payment);
            }
        }
        return owed;
    }

    private static final class Owed {
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
                if (hasItem(player, level, job, hand, item)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Wrights still flying home. A queued job must not launch a second flyer for the same one. */
    private static final Set<UUID> RETURNING = new HashSet<>();
    /** Last place a wright was seen flying, so a lost one can be highlighted after the entity is gone. */
    private static final Map<UUID, Flyer> FLYERS = new HashMap<>();
    private static int returnClock;

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
    private static void sweepReturns() {
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

    private static boolean isReturning(UUID unitId) {
        return unitId != null && RETURNING.contains(unitId);
    }

    private static void sendHome(MinecraftServer server, ServerPlayer player, BuildJob job) {
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

    private static void recall(ServerLevel level, ServerPlayer player, UUID id, int slot, int count) {
        recall(level, player, id, slot, count, null);
    }

    private static void recall(ServerLevel level, ServerPlayer player, UUID id, int slot, int count, BlockPos homePort) {
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
