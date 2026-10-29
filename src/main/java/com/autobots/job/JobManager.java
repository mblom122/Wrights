package com.autobots.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.autobots.AutobotsConfig;
import com.autobots.blueprint.BlueprintActions;
import com.autobots.blueprint.BoxEdits;
import com.autobots.AutobotsMod;
import com.autobots.entity.DroneEntity;
import com.autobots.entity.ModEntities;
import com.autobots.item.ControllerData;
import com.autobots.item.ControllerItem;
import com.autobots.schematic.Schematic;
import com.autobots.schematic.SchematicLibrary;
import com.autobots.util.BlockFilters;
import com.autobots.util.Messages;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID)
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
            try {
                if (job.kind() == BuildJob.Kind.COPY) {
                    tickCopy(server, library, job);
                } else {
                    tickPaste(server, library, job);
                }
            } catch (RuntimeException ex) {
                AutobotsMod.LOGGER.error("Autobots job {} failed and was stopped", job.id(), ex);
                discardDrone(server, job);
                library.removeJob(job);
            }
            if (library.job(job.id()).isPresent()) {
                library.setDirty();
            }
        }
    }

    public static java.util.Optional<BuildJob> jobFor(ServerPlayer player) {
        return SchematicLibrary.get(player.getServer()).jobFor(player.getUUID());
    }

    public static boolean cancel(ServerPlayer player, boolean tellPlayer) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BuildJob job = library.jobFor(player.getUUID()).orElse(null);
        if (job == null) {
            return false;
        }
        if (job.kind() == BuildJob.Kind.COPY && job.resultSchematicId() != null) {
            library.removeSchematic(job.resultSchematicId());
        }
        discardDrone(player.getServer(), job);
        library.removeJob(job);
        if (tellPlayer) {
            Messages.actionBar(player, "message.autobots.stopped");
        }
        return true;
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
        Messages.actionBar(player, "message.autobots.copy_started");
    }

    public static void startPaste(ServerPlayer player, ItemStack stack, ControllerData data, GlobalPos origin) {
        data = data.ensureId().withoutAim();
        ControllerData.write(stack, data);
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(data.schematicId().orElseThrow()).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.autobots.need_copy");
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
        library.addJob(job);
        ServerLevel level = player.getServer().getLevel(origin.dimension());
        if (level != null && player.level() == level) {
            ensureDrone(level, player, job, origin.pos());
        }
        Messages.actionBar(player, "message.autobots.launched");
    }

    public static boolean startBlueprintSave(ServerPlayer player, String name, UUID replaceId, ResourceKey<Level> dimension, BlockPos cornerA, BlockPos cornerB) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (library.jobFor(player.getUUID()).isPresent()) {
            Messages.actionBar(player, "message.autobots.busy");
            return false;
        }
        if (replaceId != null && library.isInUse(replaceId)) {
            Messages.actionBar(player, "message.autobots.blueprint_in_use");
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
        job.setReplaceSchematicId(replaceId);
        library.addJob(job);
        Messages.actionBar(player, "message.autobots.saving");
        return true;
    }

    public static boolean startBlueprintBuild(ServerPlayer player, UUID schematicId, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (library.jobFor(player.getUUID()).isPresent()) {
            Messages.actionBar(player, "message.autobots.busy");
            return false;
        }
        Schematic schematic = library.schematic(schematicId).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
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
        library.addJob(job);
        if (player.level() instanceof ServerLevel level) {
            ensureDrone(level, player, job, origin);
        }
        Messages.actionBar(player, "message.autobots.launched");
        return true;
    }

    private static void tickCopy(MinecraftServer server, SchematicLibrary library, BuildJob job) {
        if (job.phase() == BuildJob.Phase.BINDING) {
            tryBindCopy(server, library, job);
            return;
        }
        ServerLevel level = server.getLevel(job.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner());
        if (level == null || player == null) {
            return;
        }
        int budget = AutobotsConfig.COPY_POSITIONS_PER_TICK.getAsInt();
        for (int i = 0; i < budget && job.cursor() < job.total(); i++) {
            BlockPos pos = job.scanPos();
            if (level.isOutsideBuildHeight(pos)) {
                job.advance();
                continue;
            }
            if (!ready(player, level, pos, job, true)) {
                return;
            }
            if (!level.isLoaded(pos)) {
                if (AutobotsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                    if (job.tickMessage()) {
                        Messages.actionBar(player, "message.autobots.paused_chunk");
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
                Messages.actionBar(player, "message.autobots.copy_progress", job.cursor(), job.total());
            }
            return;
        }
        finishCopy(library, job);
        tryBindCopy(server, library, job);
    }

    private static boolean canStore(ServerLevel level, BuildJob job, BlockPos pos, BlockState state) {
        if (!BlockFilters.canCopy(level, pos, state)) {
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
        entries.sort(Comparator.comparing(Schematic.Entry::secondary));
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
        if (job.replaceSchematicId() != null) {
            Schematic previous = library.schematic(job.replaceSchematicId()).orElse(null);
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
                Messages.actionBar(player, "message.autobots.copy_empty_skipped", job.skippedSpecial());
            } else {
                Messages.actionBar(player, "message.autobots.copy_empty");
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
                Messages.actionBar(player, "message.autobots.copy_empty");
                BlueprintActions.finishSave(player, null, false);
            } else if (job.skippedSpecial() > 0) {
                Messages.actionBar(player, "message.autobots.copy_done_skipped", saved.blockCount(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ()), job.skippedSpecial());
                BlueprintActions.finishSave(player, saved.id(), true);
            } else {
                Messages.actionBar(player, "message.autobots.blueprint_saved", saved.name(), BoxEdits.volume(saved.sizeX(), saved.sizeY(), saved.sizeZ()), saved.blockCount());
                BlueprintActions.finishSave(player, saved.id(), true);
            }
            return;
        }
        ItemStack stack = findController(player, job.controllerId());
        if (stack.isEmpty()) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.autobots.hold_controller");
            }
            return;
        }
        Schematic schematic = library.schematic(job.resultSchematicId()).orElse(null);
        if (schematic == null) {
            library.removeJob(job);
            Messages.actionBar(player, "message.autobots.copy_empty");
            return;
        }
        ControllerData data = ControllerData.from(stack);
        UUID previous = data.schematicId().orElse(null);
        if (previous != null && !previous.equals(schematic.id()) && !library.isInUse(previous)) {
            library.removeSchematic(previous);
        }
        ControllerData.write(stack, data.withSchematic(schematic.id(), schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), schematic.blockCount()));
        if (job.skippedSpecial() > 0) {
            Messages.actionBar(player, "message.autobots.copy_done_skipped", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()), job.skippedSpecial());
        } else {
            Messages.actionBar(player, "message.autobots.copy_done", schematic.blockCount(), BoxEdits.volume(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()));
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
                Messages.actionBar(player, "message.autobots.paste_missing");
            }
            return;
        }
        if (player == null) {
            return;
        }
        if (job.cursor() >= job.total()) {
            finishPaste(server, library, player, job);
            return;
        }
        while (job.cursor() < schematic.blockCount() && job.outsideClip(schematic.entry(job.cursor()))) {
            job.advance();
        }
        if (job.cursor() >= job.total()) {
            finishPaste(server, library, player, job);
            return;
        }
        Schematic.Entry next = schematic.entry(job.cursor());
        BlockPos nextPos = job.min().offset(next.x(), next.y(), next.z());
        if (!ready(player, level, nextPos, job, false)) {
            return;
        }
        DroneEntity drone = ensureDrone(level, player, job, nextPos);
        if (drone == null) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.autobots.paused_chunk");
            }
            return;
        }
        Vec3 hover = Vec3.atCenterOf(nextPos).add(0.0D, 0.9D, 0.0D);
        if (drone.distanceToSqr(hover) > DRONE_REACH_SQR) {
            double step = drone.distanceToSqr(hover) > 64.0D ? 0.85D : 0.4D;
            drone.moveToward(hover, step);
            job.setLastDronePos(drone.blockPosition());
            library.setDirty();
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.autobots.paste_progress", job.cursor(), job.total(), job.skipped());
            }
            return;
        }
        int budget = AutobotsConfig.BLOCKS_PER_TICK.getAsInt();
        for (int i = 0; i < budget && job.cursor() < schematic.blockCount(); i++) {
            Schematic.Entry entry = schematic.entry(job.cursor());
            BlockPos pos = job.min().offset(entry.x(), entry.y(), entry.z());
            if (!ready(player, level, pos, job, false)) {
                return;
            }
            if (drone.distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
                break;
            }
            if (job.outsideClip(entry)) {
                job.advance();
                continue;
            }
            if (placeOne(level, player, job, pos, entry) == PlaceResult.PAUSE) {
                if (job.tickMessage()) {
                    Messages.actionBar(player, "message.autobots.paused_chunk");
                }
                library.setDirty();
                return;
            }
            job.advance();
        }
        library.setDirty();
        if (job.cursor() >= job.total()) {
            finishPaste(server, library, player, job);
        } else if (job.tickMessage()) {
            Messages.actionBar(player, "message.autobots.paste_progress", job.cursor(), job.total(), job.skipped());
        }
    }

    private static void finishPaste(MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
        discardDrone(server, job);
        library.removeJob(job);
        Messages.actionBar(player, "message.autobots.paste_done", job.placed(), job.skipped());
    }

    private static boolean ready(ServerPlayer player, ServerLevel level, BlockPos pos, BuildJob job, boolean copy) {
        if (!player.level().dimension().equals(job.dimension())) {
            if (job.tickMessage()) {
                Messages.actionBar(player, "message.autobots.other_dimension");
            }
            return false;
        }
        double range = AutobotsConfig.MAX_RANGE.getAsInt();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) {
            if (job.tickMessage()) {
                Messages.actionBar(player, copy ? "message.autobots.copy_out_of_range" : "message.autobots.out_of_range");
            }
            return false;
        }
        return true;
    }

    private enum PlaceResult {
        DONE,
        PAUSE
    }

    private static PlaceResult placeOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry) {
        if (level.isOutsideBuildHeight(pos)) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (!level.isLoaded(pos)) {
            if (AutobotsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
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
        BlockState existing = level.getBlockState(pos);
        if (existing.equals(state)) {
            return PlaceResult.DONE;
        }
        if (!existing.isAir() && !existing.canBeReplaced()) {
            job.skippedOne();
            return PlaceResult.DONE;
        }
        if (entry.secondary()) {
            if (level.getBlockState(pos.below()).getBlock() != state.getBlock()) {
                job.skippedOne();
                return PlaceResult.DONE;
            }
        } else if (!freePlace(player)) {
            Item item = state.getBlock().asItem();
            if (item == Items.AIR || !hasItem(player, item)) {
                job.skippedOne();
                return PlaceResult.DONE;
            }
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
        if (!entry.secondary() && !freePlace(player)) {
            consumeItem(player, state.getBlock().asItem());
        }
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.levelEvent(2001, pos, Block.getId(state));
        job.placedOne();
        return PlaceResult.DONE;
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

    private static boolean hasItem(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeItem(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    private static DroneEntity ensureDrone(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos workPos) {
        if (job.droneId() != null) {
            Entity existing = level.getEntity(job.droneId());
            if (existing instanceof DroneEntity drone && drone.isAlive()) {
                return drone;
            }
            BlockPos last = job.lastDronePos();
            if (last != null && !level.isLoaded(last) && !level.isLoaded(workPos)) {
                return null;
            }
        }
        DroneEntity drone = new DroneEntity(ModEntities.DRONE.get(), level);
        drone.setJobId(job.id());
        Vec3 eye = player.getEyePosition();
        drone.moveTo(eye.x, eye.y, eye.z, player.getYRot(), 0.0F);
        job.setDroneId(drone.getUUID());
        job.setLastDronePos(drone.blockPosition());
        level.addFreshEntity(drone);
        return drone;
    }

    private static void discardDrone(MinecraftServer server, BuildJob job) {
        if (job.droneId() == null) {
            return;
        }
        ServerLevel level = server.getLevel(job.dimension());
        if (level != null) {
            Entity entity = level.getEntity(job.droneId());
            if (entity != null) {
                entity.discard();
            }
        }
        job.setDroneId(null);
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
