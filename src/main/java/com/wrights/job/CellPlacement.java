package com.wrights.job;

import com.wrights.job.JobManager.PlaceResult;
import com.wrights.job.WorkPicker.Falling;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.wrights.WrightsConfig;
import com.wrights.network.BlueprintNetwork;
import com.wrights.drone.WrightTier;
import com.wrights.entity.DroneEntity;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.BlockFilters;

/** Places, mines and voids a single cell: gating, item costs, block entity data, hang/fall scrubbing and skip bookkeeping. */
public final class CellPlacement {
    private CellPlacement() {
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

    static void placeAll(ServerLevel level, ServerPlayer player, SchematicLibrary library, BuildJob job, Schematic schematic) {
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
            JobManager.finishPaste(player.getServer(), library, player, job);
        }
    }

    static void undoAll(ServerLevel level, MinecraftServer server, SchematicLibrary library, ServerPlayer player, BuildJob job) {
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
            JobManager.removeOne(level, player, job, pos, refund, true);
        }
        JobManager.finishUndo(server, library, player, job);
    }

    static PlaceResult actOne(ServerLevel level, ServerPlayer player, BuildJob job, BlockPos pos, Schematic.Entry entry, boolean quiet, BuildJob.Hand hand) {
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
                    ItemStack tool = job.exchangeSilk() ? JobManager.silkTouchPick(level.getServer()) : ItemStack.EMPTY;
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

    static void scrubHangRegion(ServerLevel level, BuildJob job) {
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
}
