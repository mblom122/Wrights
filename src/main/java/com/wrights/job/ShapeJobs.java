package com.wrights.job;

import com.wrights.job.CrewRoster.CrewGap;
import com.wrights.job.JobManager.PlaceResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintNetwork;
import com.wrights.drone.WrightTier;
import com.wrights.drone.WrightHomes;
import com.wrights.entity.DroneEntity;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.BlockFilters;
import com.wrights.util.Messages;

/**
 * Shape, Exchange and Place jobs: starting them, planning where each wright stands, and the per-tick
 * work of a crew filling a shape. Shared crew, supply and flight helpers stay in {@link JobManager}.
 */
public final class ShapeJobs {
    private ShapeJobs() {
    }

    /**
     * Shape/Exchange crew tiles. Wrights finish a local cube (cluster multi-place) before the next,
     * instead of everyone chasing one shared scan line through a 200³ box.
     */
    static final int SHAPE_CUBE = 16;

    /**
     * Start a streaming shape build: store formula params and place cells each tick.
     * Never materializes a full BlockPos / Schematic.Entry list.
     */
    public static boolean startShapeBuild(
            ServerPlayer player,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            int kind,
            int size,
            int height,
            int thickness,
            int axis,
            int yaw,
            int pivot,
            boolean hollow,
            boolean voidBlocks,
            String materials,
            int fillMode,
            int orderBatch,
            long seed,
            String supplyOrder,
            boolean deferSupplies) {
        return startShapeBuild(player, min, max, center, kind, size, height, thickness, axis, yaw, pivot, hollow, voidBlocks, materials, fillMode, orderBatch, seed, supplyOrder, deferSupplies, false, "", false);
    }

    public static boolean startShapeBuild(
            ServerPlayer player,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            int kind,
            int size,
            int height,
            int thickness,
            int axis,
            int yaw,
            int pivot,
            boolean hollow,
            boolean voidBlocks,
            String materials,
            int fillMode,
            int orderBatch,
            long seed,
            String supplyOrder,
            boolean deferSupplies,
            boolean exchange,
            String exchangeFrom) {
        return startShapeBuild(player, min, max, center, kind, size, height, thickness, axis, yaw, pivot, hollow, voidBlocks, materials, fillMode, orderBatch, seed, supplyOrder, deferSupplies, exchange, exchangeFrom, false, 0, false, true);
    }

    public static boolean startShapeBuild(
            ServerPlayer player,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            int kind,
            int size,
            int height,
            int thickness,
            int axis,
            int yaw,
            int pivot,
            boolean hollow,
            boolean voidBlocks,
            String materials,
            int fillMode,
            int orderBatch,
            long seed,
            String supplyOrder,
            boolean deferSupplies,
            boolean exchange,
            String exchangeFrom,
            boolean exchangeSilk) {
        return startShapeBuild(player, min, max, center, kind, size, height, thickness, axis, yaw, pivot, hollow, voidBlocks, materials, fillMode, orderBatch, seed, supplyOrder, deferSupplies, exchange, exchangeFrom, exchangeSilk, 0, false, true);
    }

    public static boolean startShapeBuild(
            ServerPlayer player,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            int kind,
            int size,
            int height,
            int thickness,
            int axis,
            int yaw,
            int pivot,
            boolean hollow,
            boolean voidBlocks,
            String materials,
            int fillMode,
            int orderBatch,
            long seed,
            String supplyOrder,
            boolean deferSupplies,
            boolean exchange,
            String exchangeFrom,
            boolean exchangeSilk,
            int drones,
            boolean placeTool,
            boolean queue) {
        if (player == null || min == null || max == null) {
            return false;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (!JobManager.jobSlotOpen(player, library)) {
            return false;
        }
        if (JobManager.overlapsJobBoxes(library, player.level().dimension(), min, max)) {
            Messages.actionBar(player, "message.wrights.build_overlap");
            return false;
        }
        BuildJob job = BuildJob.shape(player.getUUID(), player.getUUID(), player.level().dimension(), min, max);
        job.setShapeStream(kind, size, height, thickness, axis, yaw, pivot, hollow, center, materials, fillMode, orderBatch, seed, exchange, exchangeFrom, exchangeSilk);
        job.setPlaceTool(placeTool && !exchange);
        // Exchange always swaps; Void only controls whether replaced blocks are returned.
        job.setPasteOptions(false, voidBlocks, voidBlocks, false, false, false, false);
        job.setClip(job.boxSizeX(), job.boxSizeY(), job.boxSizeZ());
        job.setAnchor(min);
        String title = JobTitles.shapeJobTitle(exchange, kind, size, height, thickness, axis);
        if (placeTool && !exchange && title.startsWith("Shape - ")) {
            title = "Place - " + title.substring("Shape - ".length());
        }
        job.setBlueprintName(title);
        com.wrights.blueprint.BlueprintSessions.BlueprintSession session = com.wrights.blueprint.BlueprintSessions.get(player);
        job.setSupply(session.supplyPos(), session.supplyDimension());
        job.setSupplyOrder(supplyOrder);
        // Exchange: count real swaps before launching. Already-matching cells are not work.
        Schematic exchangeWork = null;
        if (exchange && player.level() instanceof ServerLevel preLevel) {
            exchangeWork = buildExchangeSchematic(preLevel, job);
            if (exchangeWork == null || exchangeWork.blockCount() <= 0) {
                Messages.centerTip(player, "message.wrights.exchange_none");
                return true;
            }
        }
        int workBlocks = exchangeWork != null ? Math.max(1, exchangeWork.blockCount()) : Math.max(1, job.total());
        int room = Math.min(
                WrightsConfig.usefulCrewFor(workBlocks, job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(),
                        WrightHomes.bestFabricator(player, id -> !CrewRoster.claimedByOther(player, SchematicLibrary.get(player.getServer()), null, id))),
                WrightsConfig.maxWrightsPerJob(job));
        int wanted = drones > 0 ? Math.min(drones, room) : room;
        wanted = Math.max(1, wanted);
        if (deferSupplies && !JobManager.instant(player)) {
            job.setDeferSupplies(true);
            job.setQueued(true);
            job.setCrewQuota(wanted);
        }
        if (queue && !JobManager.instant(player) && CrewRoster.freeUnits(player, library, job, 1).isEmpty() && !job.deferSupplies()) {
            job.setCrewQuota(wanted);
            job.setQueued(true);
            library.addJob(job);
            if (player.level() instanceof ServerLevel queuedLevel) {
                attachExchangeHologram(player, library, queuedLevel, job, exchangeWork);
            }
            JobManager.noteQueued(player, job, wanted);
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (!JobManager.instant(player) && !job.deferSupplies() && !assignShapeCrew(player, job, wanted)) {
            return false;
        }
        library.addJob(job);
        if (job.deferSupplies()) {
            if (player.level() instanceof ServerLevel deferredLevel) {
                attachExchangeHologram(player, library, deferredLevel, job, exchangeWork);
            }
            JobManager.noteQueued(player, job, Math.max(1, job.crewQuota()));
            BlueprintNetwork.syncJobs(player);
            return true;
        }
        if (player.level() instanceof ServerLevel level) {
            attachExchangeHologram(player, library, level, job, exchangeWork);
            if (!JobManager.instant(player)) {
                if (job.hasCrew()) {
                    for (BuildJob.Hand hand : job.hands()) {
                        DroneFleet.ensureHand(level, player, job, hand, min);
                    }
                } else {
                    DroneFleet.ensureDrone(level, player, job, min);
                }
            }
        }
        JobManager.launched(player, job);
        BlueprintNetwork.syncJobs(player);
        return true;
    }

    /**
     * Place tool path modes: one ephemeral paste job for every cell (Bridge / Stairs / To me),
     * so width × height × length is a single job card — not one job per block.
     */
    public static boolean startPlaceCells(
            ServerPlayer player,
            java.util.List<BlockPos> cells,
            boolean voidBlocks,
            String materials,
            int fillMode,
            int orderBatch,
            long seed,
            String supplyOrder,
            String title,
            int drones) {
        if (player == null || cells == null || cells.isEmpty()) {
            return false;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (!JobManager.jobSlotOpen(player, library)) {
            return false;
        }
        BlockPos min = cells.get(0);
        BlockPos max = cells.get(0);
        for (BlockPos at : cells) {
            min = new BlockPos(
                    Math.min(min.getX(), at.getX()),
                    Math.min(min.getY(), at.getY()),
                    Math.min(min.getZ(), at.getZ()));
            max = new BlockPos(
                    Math.max(max.getX(), at.getX()),
                    Math.max(max.getY(), at.getY()),
                    Math.max(max.getZ(), at.getZ()));
        }
        if (JobManager.overlapsJobBoxes(library, player.level().dimension(), min, max)) {
            Messages.actionBar(player, "message.wrights.build_overlap");
            return false;
        }
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> palette =
                com.wrights.blueprint.ShapeMaterials.parse(materials);
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable = new java.util.ArrayList<>();
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : palette) {
            if (entry != null && entry.state() != null) {
                usable.add(entry);
            }
        }
        if (usable.isEmpty()) {
            return false;
        }
        com.wrights.blueprint.ShapeMaterials.FillMode mode =
                com.wrights.blueprint.ShapeMaterials.FillMode.byId(fillMode);
        java.util.List<BlockState> planned = com.wrights.blueprint.ShapeMaterials.assign(
                usable,
                cells.size(),
                mode,
                com.wrights.blueprint.ShapeMaterials.clampBatch(orderBatch),
                new java.util.Random(seed),
                true,
                item -> Long.MAX_VALUE,
                true);
        java.util.List<Schematic.Entry> entries = new java.util.ArrayList<>(cells.size());
        for (int i = 0; i < cells.size() && i < planned.size(); i++) {
            BlockPos at = cells.get(i);
            int dx = at.getX() - min.getX();
            int dy = at.getY() - min.getY();
            int dz = at.getZ() - min.getZ();
            if (dx < Short.MIN_VALUE || dx > Short.MAX_VALUE
                    || dy < Short.MIN_VALUE || dy > Short.MAX_VALUE
                    || dz < Short.MIN_VALUE || dz > Short.MAX_VALUE) {
                continue;
            }
            BlockState state = planned.get(i);
            if (state == null || state.isAir()) {
                continue;
            }
            entries.add(new Schematic.Entry((short) dx, (short) dy, (short) dz, state, false));
        }
        if (entries.isEmpty()) {
            return false;
        }
        String jobTitle = title == null || title.isBlank() ? "Place" : title.trim();
        if (!jobTitle.startsWith("Place")) {
            jobTitle = "Place - " + jobTitle;
        }
        Schematic schematic = new Schematic(
                java.util.UUID.randomUUID(),
                jobTitle,
                sizeX,
                sizeY,
                sizeZ,
                entries,
                min,
                max,
                player.level().dimension().location().toString(),
                0,
                0,
                0,
                true);
        library.putSchematic(schematic);
        boolean ok = JobManager.startBlueprintBuild(
                player,
                schematic.id(),
                min,
                sizeX,
                sizeY,
                sizeZ,
                Math.max(0, drones),
                false,
                voidBlocks,
                voidBlocks,
                false,
                supplyOrder,
                false,
                false,
                false,
                false,
                false);
        if (!ok) {
            library.removeSchematic(schematic.id());
            return false;
        }
        for (BuildJob job : library.jobs()) {
            if (schematic.id().equals(job.schematicId()) && job.owner().equals(player.getUUID())) {
                job.setBlueprintName(jobTitle);
                job.setPlaceTool(true);
                job.setPasteOptions(false, voidBlocks, voidBlocks, false, false, false, false);
                BlueprintNetwork.syncJobs(player);
                break;
            }
        }
        return true;
    }

    /**
     * How many hovers this shape needs at {@code fabricator}. Only for small shapes: a full cover of a
     * 16³ box is hundreds of milliseconds and would hitch the server. {@code -1} means "too big,
     * use the cheap grid estimate".
     */
    private static int planShapeStations(BuildJob job, int fabricator) {
        if (job == null || job.kind() != BuildJob.Kind.SHAPE) {
            return -1;
        }
        int total = job.total();
        if (total <= 0 || total > 192 || job.shapeCenter() == null) {
            return -1;
        }
        com.wrights.blueprint.Shapes.Kind shapeKind = com.wrights.blueprint.Shapes.Kind.byId(job.shapeKind());
        BlockPos pin = com.wrights.blueprint.Shapes.pinLocal(
                shapeKind,
                job.shapeSize(),
                job.shapeHeight(),
                job.shapeThickness(),
                job.shapeHollow(),
                job.shapeAxis(),
                job.shapePivot());
        int reach = WrightTier.operationReachSqr(fabricator);
        int cap = Math.max(1, WrightTier.actionsPerTick(fabricator));
        int[] xs = new int[total];
        int[] ys = new int[total];
        int[] zs = new int[total];
        int n = 0;
        for (int i = 0; i < total; i++) {
            BlockPos pos = job.scanPosAt(i);
            if (!com.wrights.blueprint.Shapes.containsWorld(
                    shapeKind,
                    pos,
                    job.shapeCenter(),
                    pin,
                    job.shapeSize(),
                    job.shapeHeight(),
                    job.shapeThickness(),
                    job.shapeHollow(),
                    job.shapeAxis(),
                    job.shapeYaw())) {
                continue;
            }
            xs[n] = pos.getX();
            ys[n] = pos.getY();
            zs[n] = pos.getZ();
            n++;
        }
        if (n == 0) {
            return 1;
        }
        if (reach <= 0) {
            return n;
        }
        boolean[] alive = new boolean[n];
        java.util.Arrays.fill(alive, true);
        int left = n;
        int stations = 0;
        int cx = job.shapeCenter().getX();
        int cy = job.shapeCenter().getY();
        int cz = job.shapeCenter().getZ();
        while (left > 0 && stations < n) {
            int bestX = cx;
            int bestY = cy;
            int bestZ = cz;
            int bestN = 0;
            for (int j = 0; j < n; j++) {
                if (!alive[j]) {
                    continue;
                }
                int dx = xs[j] - cx;
                int dy = ys[j] - cy;
                int dz = zs[j] - cz;
                if (dx * dx + dy * dy + dz * dz <= reach) {
                    bestN++;
                    if (bestN >= cap) {
                        break;
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (!alive[i]) {
                    continue;
                }
                int covered = 0;
                for (int j = 0; j < n; j++) {
                    if (!alive[j]) {
                        continue;
                    }
                    int dx = xs[j] - xs[i];
                    int dy = ys[j] - ys[i];
                    int dz = zs[j] - zs[i];
                    if (dx * dx + dy * dy + dz * dz <= reach) {
                        covered++;
                        if (covered >= cap) {
                            break;
                        }
                    }
                }
                if (covered > bestN) {
                    bestN = covered;
                    bestX = xs[i];
                    bestY = ys[i];
                    bestZ = zs[i];
                }
            }
            int took = 0;
            for (int j = 0; j < n && took < cap; j++) {
                if (!alive[j]) {
                    continue;
                }
                int dx = xs[j] - bestX;
                int dy = ys[j] - bestY;
                int dz = zs[j] - bestZ;
                if (dx * dx + dy * dy + dz * dz <= reach) {
                    alive[j] = false;
                    left--;
                    took++;
                }
            }
            if (took == 0) {
                break;
            }
            stations++;
        }
        return Math.max(1, stations);
    }

    /** Assign up to {@code requested} free wrights to a streaming Shape / Exchange / Place job. */
    private static boolean assignShapeCrew(ServerPlayer player, BuildJob job, int requested) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        CrewRoster.bindCrewIds(player, library);
        int bestFabricator = WrightHomes.bestFabricator(player, id -> !CrewRoster.claimedByOther(player, library, job.id(), id));
        int stations = smallBoxCrew(job, bestFabricator);
        if (stations <= 0) {
            stations = planShapeStations(job, bestFabricator);
        }
        int fitted = stations > 0
                ? stations
                : WrightsConfig.usefulCrewFor(
                        Math.max(1, job.total()), job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), bestFabricator);
        int room = Math.min(fitted, WrightsConfig.maxWrightsPerJob(job));
        int wanted = Math.max(1, Math.min(requested > 0 ? requested : room, room));
        ServerLevel level = player.getServer().getLevel(job.dimension());
        List<WrightHomes.FreeWright> free = CrewRoster.freeUnits(player, library, job, Integer.MAX_VALUE);
        List<WrightHomes.FreeWright> units = new ArrayList<>();
        for (WrightHomes.FreeWright unit : free) {
            if (units.size() < wanted && level != null && JobSupply.portSupplyReady(level, player, unit.homePort())
                    && homeCanPay(player, level, job, unit.homePort())) {
                units.add(unit);
            }
        }
        // Nobody can pay: a forced start still sends wrights, and the job stalls until stock arrives.
        if (units.isEmpty()) {
            units = free.subList(0, Math.min(wanted, free.size()));
        }
        if (units.isEmpty()) {
            CrewRoster.tellNoCrew(player, job, false);
            if (CrewRoster.crewGap(player, job) != CrewGap.NONE_AVAILABLE) {
                CrewRoster.glowClosestPort(player, player.getServer().getLevel(job.dimension()), job.min());
            }
            return false;
        }
        List<Integer> rotors = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (WrightHomes.FreeWright unit : units) {
            DroneFleet.releaseHomeWaiter(player, library, unit.unit().id(), job.id());
            rotors.add(WrightTier.clamp(unit.unit().rotor()));
            ids.add(unit.unit().id());
        }
        job.setCrew(rotors, job.boxSizeX(), job.boxSizeY(), job.boxSizeZ());
        job.setCrewUnits(ids);
        for (int i = 0; i < job.hands().size() && i < units.size(); i++) {
            job.hands().get(i).homePort = units.get(i).homePort();
            job.hands().get(i).fit(units.get(i).unit().parts());
            // Each hand walks the AABB from 0; ownership filters which cells it places.
            job.hands().get(i).cursor = 0;
            job.hands().get(i).aim = -1;
        }
        job.setCrewQuota(ids.size());
        return job.hasCrew();
    }

    /**
     * Exchange streams without a stored blueprint — build an ephemeral ghost of every
     * matching cell so queued swaps stay visible until each cell is placed.
     */
    private static void attachExchangeHologram(ServerPlayer player, SchematicLibrary library, ServerLevel level, BuildJob job) {
        attachExchangeHologram(player, library, level, job, null);
    }

    private static void attachExchangeHologram(
            ServerPlayer player,
            SchematicLibrary library,
            ServerLevel level,
            BuildJob job,
            Schematic prepared) {
        if (player == null || library == null || level == null || job == null || !job.exchangeJob()) {
            return;
        }
        Schematic schematic = prepared != null && prepared.blockCount() > 0 ? prepared : buildExchangeSchematic(level, job);
        if (schematic == null || schematic.blockCount() <= 0) {
            return;
        }
        library.putSchematic(schematic);
        job.setSchematicId(schematic.id());
        BlueprintNetwork.showJobHologram(player, job, schematic);
    }

    private static Schematic buildExchangeSchematic(ServerLevel level, BuildJob job) {
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> materials =
                com.wrights.blueprint.ShapeMaterials.parse(job.shapeMaterials());
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable = new java.util.ArrayList<>();
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : materials) {
            if (entry != null && entry.state() != null) {
                usable.add(entry);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }
        com.wrights.blueprint.Shapes.Kind shapeKind = com.wrights.blueprint.Shapes.Kind.byId(job.shapeKind());
        BlockPos pinLocal = com.wrights.blueprint.Shapes.pinLocal(
                shapeKind,
                job.shapeSize(),
                job.shapeHeight(),
                job.shapeThickness(),
                job.shapeHollow(),
                job.shapeAxis(),
                job.shapePivot());
        int width = job.boxSizeX();
        int height = job.boxSizeY();
        int depth = job.boxSizeZ();
        int budget = Math.min(65_536, Math.max(1, WrightsConfig.MAX_BLOCKS.getAsInt()));
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        outer:
        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (cells.size() >= budget) {
                        break outer;
                    }
                    cursor.set(job.min().getX() + x, job.min().getY() + y, job.min().getZ() + z);
                    if (!com.wrights.blueprint.Shapes.containsWorld(
                            shapeKind,
                            cursor,
                            job.shapeCenter(),
                            pinLocal,
                            job.shapeSize(),
                            job.shapeHeight(),
                            job.shapeThickness(),
                            job.shapeHollow(),
                            job.shapeAxis(),
                            job.shapeYaw())) {
                        continue;
                    }
                    if (level.isOutsideBuildHeight(cursor) || !com.wrights.blueprint.BoxEdits.inWorld(cursor)) {
                        continue;
                    }
                    BlockState current = level.getBlockState(cursor);
                    if (!exchangeMatches(level, cursor, current, job.exchangeFrom())) {
                        continue;
                    }
                    cells.add(cursor.immutable());
                }
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        com.wrights.blueprint.ShapeMaterials.FillMode fillMode =
                com.wrights.blueprint.ShapeMaterials.FillMode.byId(job.shapeFillMode());
        java.util.List<BlockState> planned = com.wrights.blueprint.ShapeMaterials.assign(
                usable,
                cells.size(),
                fillMode,
                job.shapeOrderBatch(),
                new java.util.Random(job.shapeSeed()),
                true,
                item -> Long.MAX_VALUE,
                true);
        java.util.List<Schematic.Entry> entries = new java.util.ArrayList<>(cells.size());
        for (int i = 0; i < cells.size() && i < planned.size(); i++) {
            BlockPos at = cells.get(i);
            int dx = at.getX() - job.min().getX();
            int dy = at.getY() - job.min().getY();
            int dz = at.getZ() - job.min().getZ();
            if (dx < Short.MIN_VALUE || dx > Short.MAX_VALUE
                    || dy < Short.MIN_VALUE || dy > Short.MAX_VALUE
                    || dz < Short.MIN_VALUE || dz > Short.MAX_VALUE) {
                continue;
            }
            BlockState state = planned.get(i);
            if (state == null || state.isAir()) {
                continue;
            }
            // Already the destination — not a real swap; do not ghost or send wrights for it.
            if (level.getBlockState(at).equals(state)) {
                continue;
            }
            entries.add(new Schematic.Entry((short) dx, (short) dy, (short) dz, state, false));
        }
        if (entries.isEmpty()) {
            return null;
        }
        String title = job.blueprintName() == null || job.blueprintName().isBlank() ? "Exchange" : job.blueprintName();
        return new Schematic(
                java.util.UUID.randomUUID(),
                title,
                width,
                height,
                depth,
                entries,
                job.min(),
                job.max(),
                level.dimension().location().toString(),
                0,
                0,
                0,
                true);
    }

    static void tickShape(MinecraftServer server, SchematicLibrary library, BuildJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner());
        if (level == null) {
            DroneFleet.discardDrone(server, job);
            library.removeJob(job);
            return;
        }
        DroneFleet.shelterLost(level, player, library, job);
        if (player == null) {
            return;
        }
        if (job.cursor() >= job.total()) {
            JobManager.finishPaste(server, library, player, job);
            return;
        }
        if (job.paused()) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            publishShapeDroneStatus(level, job, null, DroneEntity.WAIT_PAUSED);
            return;
        }
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> materials =
                com.wrights.blueprint.ShapeMaterials.parse(job.shapeMaterials());
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable = new java.util.ArrayList<>();
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : materials) {
            if (entry != null && entry.state() != null) {
                usable.add(entry);
            }
        }
        if (usable.isEmpty()) {
            Messages.actionBar(player, "message.wrights.shape_need_materials");
            DroneFleet.discardDrone(server, job);
            library.removeJob(job);
            BlueprintNetwork.syncJobs(player);
            return;
        }
        com.wrights.blueprint.ShapeMaterials.FillMode fillMode =
                com.wrights.blueprint.ShapeMaterials.FillMode.byId(job.shapeFillMode());
        java.util.Random random = new java.util.Random(job.shapeSeed() ^ (((long) job.cursor()) << 1));
        boolean fast = JobManager.instant(player);
        com.wrights.blueprint.Shapes.Kind shapeKind = com.wrights.blueprint.Shapes.Kind.byId(job.shapeKind());
        BlockPos shapePinLocal = com.wrights.blueprint.Shapes.pinLocal(
                shapeKind,
                job.shapeSize(),
                job.shapeHeight(),
                job.shapeThickness(),
                job.shapeHollow(),
                job.shapeAxis(),
                job.shapePivot());
        // Stalled Shape/Exchange: wait at home until Replace-with / materials are available again.
        if (job.waitKind() == BuildJob.WAIT_ITEMS && !fast) {
            BlockState owed = shapeOwedState(level, job, usable, fillMode, random, shapeKind, shapePinLocal);
            if (owed != null && !crewCanPay(player, library, level, job, usable)) {
                if (job.droneId() != null) {
                    publishShapeDroneStatus(level, job, owed, DroneEntity.WAIT_ITEMS);
                    DroneFleet.parkSolo(level, player, job);
                }
                // The crew tick does not run while stalled — any wright still out (mid-approach or
                // idle when the stock ran out) would hang there in its last state forever.
                for (BuildJob.Hand hand : job.hands()) {
                    if (hand.entityId != null) {
                        setShapeHandStatus(level, hand, owed, DroneEntity.WAIT_ITEMS);
                        DroneFleet.parkForItems(level, player, hand);
                    }
                    hand.idleTicks = 0;
                }
                library.setDirty();
                return;
            }
            // Stay queued: the top-up picks wrights whose own port can pay, instead of relaunching the old crew.
            job.setWaitKind(BuildJob.WAIT_NONE);
        }
        if (job.waitKind() == BuildJob.WAIT_ENERGY && !fast) {
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
            if (!job.hasCrew() && !JobManager.instant(player) && job.droneId() == null) {
                return;
            }
        }
        if (CrewRoster.holdWithoutDrones(player, library, job)) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            return;
        }
        if (!JobManager.inReach(player, library, job)) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            if (!JobManager.releaseCrewOutOfRange(player, library, job)) {
                holdCrewFlyers(level, job);
            }
            return;
        }
        if (job.waitKind() != BuildJob.WAIT_ITEMS) {
            job.workedTick();
        }
        // One wright uses the same station targeting as a crew. The old cursor walk
        // always started at AABB index 0 (a corner) and never picked a place-hover.
        if (!fast && job.hasCrew()) {
            tickShapeCrew(server, library, level, player, job, usable, fillMode, random, shapeKind, shapePinLocal);
            return;
        }
        int scanBudget = Math.max(1, WrightsConfig.COPY_POSITIONS_PER_TICK.getAsInt());
        // Fast shape uses the config tick limit, not the fabricator actions/tick budget.
        int tickLimit = fast
                ? Math.min(8192, scanBudget)
                : Math.max(1, WrightsConfig.BLOCKS_PER_TICK.getAsInt());
        int scanned = 0;
        int placed = 0;
        DroneEntity drone = null;
        BlockState statusState = null;
        int statusWait = DroneEntity.WAIT_NONE;
        while (job.cursor() < job.total() && scanned < scanBudget && placed < tickLimit) {
            BlockPos pos = job.scanPos();
            if (!com.wrights.blueprint.Shapes.containsWorld(
                    shapeKind,
                    pos,
                    job.shapeCenter(),
                    shapePinLocal,
                    job.shapeSize(),
                    job.shapeHeight(),
                    job.shapeThickness(),
                    job.shapeHollow(),
                    job.shapeAxis(),
                    job.shapeYaw())) {
                job.advance();
                scanned++;
                continue;
            }
            if (level.isOutsideBuildHeight(pos) || !com.wrights.blueprint.BoxEdits.inWorld(pos)) {
                job.advance();
                job.skippedOne();
                scanned++;
                continue;
            }
            if (!level.isLoaded(pos)) {
                if (WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean()) {
                    job.setWaitKind(BuildJob.WAIT_CHUNK);
                    publishShapeDroneStatus(level, job, statusState, DroneEntity.WAIT_CHUNK);
                    library.setDirty();
                    return;
                }
                job.advance();
                job.skippedOne();
                scanned++;
                continue;
            }
            if (!level.mayInteract(player, pos) || !CellPlacement.canBuild(player, level, pos)) {
                job.advance();
                job.skippedOne();
                scanned++;
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (job.exchangeJob()) {
                if (!exchangeMatches(level, pos, current, job.exchangeFrom())) {
                    job.advance();
                    job.skippedOne();
                    scanned++;
                    continue;
                }
            } else if (!job.voidBlocks() && !current.isAir() && !current.canBeReplaced()) {
                job.advance();
                job.skippedOne();
                scanned++;
                continue;
            }
            BlockState state = shapeNextState(job, usable, fillMode, random);
            if (state == null) {
                statusState = usable.get(0).state();
                statusWait = DroneEntity.WAIT_ITEMS;
                job.setWaitKind(BuildJob.WAIT_ITEMS);
                break;
            }
            // Exchange already matching — count it without flying out first.
            if (job.exchangeJob() && current.equals(state)) {
                job.advance();
                job.shapeOrderAdvance();
                job.skippedOne();
                scanned++;
                continue;
            }
            statusState = state;
            if (!fast) {
                Vec3 hover = Vec3.atCenterOf(pos).add(0.0D, 0.9D, 0.0D);
                drone = DroneFleet.ensureDrone(level, player, job, pos);
                if (drone == null) {
                    if (job.tickMessage()) {
                        JobManager.noteChunk(player, library, job);
                    }
                    return;
                }
                if (drone.launching()) {
                    job.setLastDronePos(drone.blockPosition());
                    job.setWaitKind(BuildJob.WAIT_NONE);
                    drone.setWorkStatus(BlockFilters.icon(state), ItemStack.EMPTY, DroneEntity.WAIT_NONE);
                    library.setDirty();
                    return;
                }
                if (drone.distanceToSqr(hover) > JobManager.DRONE_REACH_SQR) {
                    double step = drone.distanceToSqr(hover) > 64.0D ? 0.85D : 0.4D;
                    drone.moveToward(hover, step);
                    job.setLastDronePos(drone.blockPosition());
                    job.setWaitKind(BuildJob.WAIT_NONE);
                    drone.setWorkStatus(BlockFilters.icon(state), ItemStack.EMPTY, DroneEntity.WAIT_NONE);
                    library.setDirty();
                    return;
                }
            }
            int dx = pos.getX() - job.min().getX();
            int dy = pos.getY() - job.min().getY();
            int dz = pos.getZ() - job.min().getZ();
            if (dx < Short.MIN_VALUE || dx > Short.MAX_VALUE || dy < Short.MIN_VALUE || dy > Short.MAX_VALUE
                    || dz < Short.MIN_VALUE || dz > Short.MAX_VALUE) {
                job.advance();
                job.skippedOne();
                scanned++;
                continue;
            }
            Schematic.Entry entry = new Schematic.Entry((short) dx, (short) dy, (short) dz, state, false);
            PlaceResult result = CellPlacement.placeOne(level, player, job, pos, entry, false, null, drone);
            if (result == PlaceResult.PAUSE) {
                job.setWaitKind(BuildJob.WAIT_CHUNK);
                publishShapeDroneStatus(level, job, state, DroneEntity.WAIT_CHUNK);
                library.setDirty();
                return;
            }
            if (result == PlaceResult.WAIT) {
                statusState = state;
                statusWait = DroneEntity.WAIT_ITEMS;
                job.setWaitKind(BuildJob.WAIT_ITEMS);
                break;
            }
            if (result == PlaceResult.ENERGY) {
                DroneFleet.stallForEnergy(level, player, library, job);
                return;
            }
            job.advance();
            job.shapeOrderAdvance();
            scanned++;
            placed++;
            statusWait = DroneEntity.WAIT_NONE;
        }
        if (placed > 0) {
            job.operationOne();
        }
        if (job.waitKind() != BuildJob.WAIT_ITEMS && job.waitKind() != BuildJob.WAIT_CHUNK) {
            job.setWaitKind(BuildJob.WAIT_NONE);
            job.setStallNoticed(false);
            statusWait = DroneEntity.WAIT_NONE;
        } else if (job.waitKind() == BuildJob.WAIT_ITEMS) {
            statusWait = DroneEntity.WAIT_ITEMS;
        } else if (job.waitKind() == BuildJob.WAIT_CHUNK) {
            statusWait = DroneEntity.WAIT_CHUNK;
        }
        if (statusState != null || statusWait != DroneEntity.WAIT_NONE) {
            if (drone != null) {
                drone.setWorkStatus(CellPlacement.shapeStatusCargo(job, statusState, statusWait), ItemStack.EMPTY, statusWait);
            } else {
                publishShapeDroneStatus(level, job, statusState, statusWait);
            }
        }
        // Out of materials: send the wright home with its carry (same as blueprint stall).
        if (job.waitKind() == BuildJob.WAIT_ITEMS && !fast) {
            JobAnnouncements.stalled(player, library, job, false, job.droneId() != null ? 1 : 0);
            DroneFleet.parkSolo(level, player, job);
            job.setQueued(true);
            job.setCrewQuota(Math.max(1, job.crewQuota()));
        }
        // Large exchange ghosts skip per-block drops — refresh the stepped hologram on a throttle.
        if (job.exchangeJob() && job.schematicId() != null && job.total() > 8000
                && level.getGameTime() % 40L == 0L
                && job.waitKind() != BuildJob.WAIT_ITEMS
                && job.waitKind() != BuildJob.WAIT_CHUNK) {
            library.schematic(job.schematicId()).ifPresent(schematic -> BlueprintNetwork.showJobHologram(player, job, schematic));
        }
        library.setDirty();
        if (job.cursor() >= job.total()) {
            JobManager.finishPaste(server, library, player, job);
        }
    }

    /**
     * Multi-wright Shape/Exchange: each hand owns a block-axis share, finishes that region
     * (cluster multi-place), then claims half of the busiest remaining share until the job ends.
     * Avoids every wright flying to the same shared {@link BuildJob#scanPos()} cell.
     */
    private static void tickShapeCrew(
            MinecraftServer server,
            SchematicLibrary library,
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.ShapeMaterials.FillMode fillMode,
            java.util.Random random,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        boolean waitingItems = false;
        boolean waitingChunk = false;
        int scanCap = Math.max(64, WrightsConfig.COPY_POSITIONS_PER_TICK.getAsInt());
        ensureShapeShares(job);
        // At most one strip split per tick — many idle hands used to halve the same share every tick.
        boolean splitThisTick = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.sheltering) {
                setShapeHandStatus(level, hand, null, DroneEntity.WAIT_SHELTER);
                continue;
            }
            int patterned = tickSmallPattern(
                    level, player, library, job, hand, usable, fillMode, random, shapeKind, shapePinLocal);
            if (patterned == JobManager.PATTERN_ENERGY) {
                DroneFleet.stallForEnergy(level, player, library, job);
                return;
            }
            if (patterned == JobManager.PATTERN_CHUNK) {
                waitingChunk = true;
                continue;
            }
            if (patterned == JobManager.PATTERN_ITEMS) {
                waitingItems = true;
                continue;
            }
            if (patterned == JobManager.PATTERN_DONE) {
                continue;
            }
            int at = nextShapeIndex(level, player, job, hand, usable, shapeKind, shapePinLocal, true, scanCap);
            if (at < 0 && !splitThisTick && tryRebalanceShapeShare(player, library, level, job, hand, usable, shapeKind, shapePinLocal, scanCap)) {
                splitThisTick = true;
                at = nextShapeIndex(level, player, job, hand, usable, shapeKind, shapePinLocal, true, scanCap);
            }
            if (at < 0) {
                // Leftovers: help near peers, then any unresolved READY cell in the job.
                at = nextShapeIndex(level, player, job, hand, usable, shapeKind, shapePinLocal, false, scanCap);
            }
            if (at < 0 && job.cursor() < job.total() && hand.cursor >= job.total()) {
                int ownedStart = firstOwnedShapeScanIndex(job, hand);
                if (ownedStart >= 0 && ownedStart < job.total() && !job.isShapeResolved(ownedStart)) {
                    hand.cursor = ownedStart;
                    at = nextShapeIndex(level, player, job, hand, usable, shapeKind, shapePinLocal, true, scanCap);
                    if (at < 0) {
                        at = nextShapeIndex(level, player, job, hand, usable, shapeKind, shapePinLocal, false, scanCap);
                    }
                }
            }
            if (at < 0) {
                hand.idle = true;
                hand.aim = -1;
                hand.shareRemain = 0;
                setShapeHandStatus(level, hand, null, DroneEntity.WAIT_IDLE);
                DroneFleet.tickIdle(level, player, hand);
                continue;
            }
            hand.idleTicks = 0;
            if (at == JobManager.NEED_ITEMS) {
                waitingItems = true;
                hand.aim = -1;
                setShapeHandStatus(level, hand, usable.get(0).state(), DroneEntity.WAIT_ITEMS);
                DroneFleet.parkForItems(level, player, hand);
                continue;
            }
            WorkPicker.claimAim(job, hand, at);
            hand.idle = false;
            hand.awaitingItems = false;
            BlockPos pos = job.scanPosAt(at);
            BlockState state = shapeNextState(job, usable, fillMode, random);
            if (state == null) {
                waitingItems = true;
                hand.aim = -1;
                setShapeHandStatus(level, hand, usable.get(0).state(), DroneEntity.WAIT_ITEMS);
                DroneFleet.parkForItems(level, player, hand);
                continue;
            }
            DroneEntity drone = DroneFleet.ensureHand(level, player, job, hand, pos);
            if (drone != null) {
                drone.setParts(hand.parts());
            }
            if (drone == null) {
                waitingChunk = true;
                setShapeHandStatus(level, hand, state, DroneEntity.WAIT_CHUNK);
                continue;
            }
            // Stand on the stamp (small box) or the aim cell, then AOE from there — same as paste/undo.
            BlockPos fly = smallBoxStand(level, player, job, hand, shapeKind, shapePinLocal);
            if (fly == null) {
                fly = pos;
            }
            if (HoverWork.approachStand(drone, hand, fly)) {
                drone.setWorkStatus(BlockFilters.icon(state), ItemStack.EMPTY, DroneEntity.WAIT_NONE);
                continue;
            }
            int budget = CellPlacement.actionBudget(hand);
            int placed = 0;
            int scanned = 0;
            boolean usedAnchor = false;
            boolean handItems = false;
            while (placed < budget && scanned < scanCap && job.cursor() < job.total()) {
                int index;
                if (!usedAnchor) {
                    index = at;
                    usedAnchor = true;
                } else {
                    // Fill this hover's whole reach first, even across a share border, then look further.
                    index = nextInReach(level, player, job, hand, drone, shapeKind, shapePinLocal);
                    if (index < 0) {
                        index = nextShapeIndex(
                                level, player, job, hand, usable, shapeKind, shapePinLocal, true, scanCap);
                    }
                    if (index < 0) {
                        index = nextShapeIndex(
                                level, player, job, hand, usable, shapeKind, shapePinLocal, false, scanCap);
                    }
                }
                if (index < 0 || index == JobManager.NEED_ITEMS) {
                    if (index == JobManager.NEED_ITEMS) {
                        handItems = true;
                    }
                    break;
                }
                // onlyOwned first. A help target is FOREIGN to this strip; place it anyway.
                // Breaking there used to leave the wright "placing" the same leftover forever.
                ShapeCell standKind = classifyShapeCell(
                        level, player, job, hand, index, shapeKind, shapePinLocal, true);
                if (standKind == ShapeCell.FOREIGN) {
                    standKind = classifyShapeCell(
                            level, player, job, hand, index, shapeKind, shapePinLocal, false);
                }
                if (standKind == ShapeCell.OUT || standKind == ShapeCell.DONE) {
                    if (standKind == ShapeCell.OUT) {
                        resolveShapeCell(job, hand, index, false);
                    }
                    scanned++;
                    continue;
                }
                if (standKind == ShapeCell.SKIP) {
                    resolveShapeCell(job, hand, index, true);
                    scanned++;
                    continue;
                }
                if (standKind != ShapeCell.READY) {
                    break;
                }
                WorkPicker.claimAim(job, hand, index);
                BlockPos cell = job.scanPosAt(index);
                // Primary aim: fly-arrive uses JobManager.DRONE_REACH_SQR (~1.5); fabricator reach can be 0–2 and
                // used to reject the cell we just hovered over, leaving one wright "placing" forever.
                if (placed == 0) {
                    if (drone.distanceToSqr(Vec3.atCenterOf(cell)) > JobManager.PLACE_NEAR_SQR) {
                        break;
                    }
                } else if (!inOperationReachFromHover(drone.position(), cell, handFabricator(hand))) {
                    break;
                }
                BlockState placeState = shapeNextState(job, usable, fillMode, random);
                if (placeState == null) {
                    handItems = true;
                    break;
                }
                int dx = cell.getX() - job.min().getX();
                int dy = cell.getY() - job.min().getY();
                int dz = cell.getZ() - job.min().getZ();
                if (dx < Short.MIN_VALUE || dx > Short.MAX_VALUE || dy < Short.MIN_VALUE || dy > Short.MAX_VALUE
                        || dz < Short.MIN_VALUE || dz > Short.MAX_VALUE) {
                    resolveShapeCell(job, hand, index, true);
                    scanned++;
                    continue;
                }
                Schematic.Entry entry = new Schematic.Entry((short) dx, (short) dy, (short) dz, placeState, false);
                PlaceResult result = CellPlacement.placeOne(level, player, job, cell, entry, false, hand, drone);
                if (result == PlaceResult.PAUSE) {
                    waitingChunk = true;
                    job.setWaitKind(BuildJob.WAIT_CHUNK);
                    setShapeHandStatus(level, hand, placeState, DroneEntity.WAIT_CHUNK);
                    library.setDirty();
                    return;
                }
                if (result == PlaceResult.WAIT) {
                    handItems = true;
                    break;
                }
                if (result == PlaceResult.ENERGY) {
                    DroneFleet.stallForEnergy(level, player, library, job);
                    return;
                }
                resolveShapeCell(job, hand, index, false);
                job.shapeOrderAdvance();
                drone.setWorkStatus(BlockFilters.icon(placeState), ItemStack.EMPTY, DroneEntity.WAIT_NONE);
                placed++;
                scanned++;
            }
            if (placed > 0) {
                HoverWork.countOp(job, hand, fly);
            }
            // Per wright: another hand running dry must not park one whose own supply still pays.
            if (handItems) {
                waitingItems = true;
                setShapeHandStatus(level, hand, usable.get(0).state(), DroneEntity.WAIT_ITEMS);
                DroneFleet.parkForItems(level, player, hand);
            }
        }
        if (waitingChunk) {
            job.setWaitKind(BuildJob.WAIT_CHUNK);
        } else if (waitingItems) {
            job.setWaitKind(BuildJob.WAIT_ITEMS);
            // Nobody can pay: the stall branch recalls the rest next tick, so count the whole crew.
            int returned = !job.stallNoticed() && !crewCanPay(player, library, level, job, usable)
                    ? job.hands().size()
                    : JobManager.awaitingHome(job);
            JobAnnouncements.stalled(player, library, job, false, returned);
            job.setQueued(true);
            job.setCrewQuota(Math.max(1, job.crewQuota()));
        } else {
            job.setWaitKind(BuildJob.WAIT_NONE);
            job.setStallNoticed(false);
        }
        if (job.exchangeJob() && job.schematicId() != null && job.total() > 8000
                && level.getGameTime() % 40L == 0L
                && job.waitKind() != BuildJob.WAIT_ITEMS
                && job.waitKind() != BuildJob.WAIT_CHUNK) {
            library.schematic(job.schematicId()).ifPresent(schematic -> BlueprintNetwork.showJobHologram(player, job, schematic));
        }
        library.setDirty();
        // Done only when every scan cell was actually resolved (placed, skipped, or outside
        // the shape). Hand cursors are not a finish signal — a scoring pass used to walk them
        // to the end and complete the job after one or two blocks.
        if (job.cursor() >= job.total()) {
            JobManager.finishPaste(server, library, player, job);
        }
    }

    /**
     * Mark an AABB cell processed. Does not jump {@code hand.cursor} past earlier unresolved
     * cells — placing the middle of a line must not abandon the ends.
     */
    private static void resolveShapeCell(BuildJob job, BuildJob.Hand hand, int index, boolean skipped) {
        if (index == hand.aim) {
            hand.aim = -1;
        }
        boolean fresh = job.markShapeResolved(index);
        if (fresh) {
            job.advance();
            if (skipped) {
                job.skippedOne();
            }
            if (hand.shareRemain > 0) {
                hand.shareRemain--;
            }
        }
        pullShapeCursor(job, hand);
    }

    /** Move this hand's scan cursor forward only through cells that are already resolved or not owned. */
    private static void pullShapeCursor(BuildJob job, BuildJob.Hand hand) {
        int total = job.total();
        int cursor = Math.max(0, hand.cursor);
        int guard = 0;
        while (cursor < total && guard++ < 64) {
            BlockPos pos = job.scanPosAt(cursor);
            int lx = pos.getX() - job.min().getX();
            int ly = pos.getY() - job.min().getY();
            int lz = pos.getZ() - job.min().getZ();
            if (!ownsShapeCell(job, hand, lx, ly, lz) || job.isShapeResolved(cursor)) {
                cursor++;
                continue;
            }
            break;
        }
        hand.cursor = cursor;
    }

    private static void setShapeHandStatus(ServerLevel level, BuildJob.Hand hand, BlockState state, int waitReason) {
        if (hand == null || hand.entityId == null) {
            return;
        }
        Entity entity = level.getEntity(hand.entityId);
        if (!(entity instanceof DroneEntity drone) || !drone.isAlive()) {
            return;
        }
        drone.setWorkStatus(state == null ? ItemStack.EMPTY : BlockFilters.icon(state), ItemStack.EMPTY, waitReason);
    }

    /**
     * Player/port range hold stops placement ticks but used to leave flyers on WAIT_NONE, so Manage
     * still said "Working" with a frozen target. Mark every linked flyer as paused/held instead.
     */
    static void holdCrewFlyers(ServerLevel level, BuildJob job) {
        if (level == null || job == null) {
            return;
        }
        ItemStack cargo = ItemStack.EMPTY;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.entityId == null) {
                continue;
            }
            if (!(level.getEntity(hand.entityId) instanceof DroneEntity drone) || !drone.isAlive()) {
                continue;
            }
            if (cargo.isEmpty() && !drone.currentBlock().isEmpty()) {
                cargo = drone.currentBlock().copy();
            }
            drone.setWorkStatus(cargo.isEmpty() ? drone.currentBlock() : cargo, ItemStack.EMPTY, DroneEntity.WAIT_PAUSED);
        }
        if (job.droneId() != null && level.getEntity(job.droneId()) instanceof DroneEntity solo && solo.isAlive()) {
            solo.setWorkStatus(solo.currentBlock(), ItemStack.EMPTY, DroneEntity.WAIT_PAUSED);
        }
    }

    /**
     * Next placeable shape cell for this hand. Prefers a nearby cluster, then walks the AABB.
     * {@code onlyOwned} limits to this hand's cube strip; false is help-mode (spatial only, no resolve).
     * Returns a scan index, {@link #JobManager.NEED_ITEMS}, or -1.
     */
    private static int nextShapeIndex(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            boolean onlyOwned,
            int scanCap) {
        if (usable.isEmpty()) {
            return JobManager.NEED_ITEMS;
        }
        if (!shapeCanPayAny(player, level, job, hand, usable)) {
            return JobManager.NEED_ITEMS;
        }
        Vec3 origin = WorkPicker.handOrigin(level, hand);
        if (origin != null && hand.aim >= 0 && hand.aim < job.total()) {
            ShapeCell probe = classifyShapeCell(level, player, job, hand, hand.aim, shapeKind, shapePinLocal, onlyOwned);
            if (probe == ShapeCell.READY) {
                if (inOperationReachFromHover(origin, job.scanPosAt(hand.aim), handFabricator(hand))) {
                    return hand.aim;
                }
                int local = shapeClusterIndex(
                        level, player, job, hand, origin, JobManager.PLACE_NEAR_SQR, JobManager.CLUSTER_RADIUS,
                        shapeKind, shapePinLocal, onlyOwned, false);
                if (local >= 0) {
                    return local;
                }
                return hand.aim;
            }
            if (probe == ShapeCell.DONE) {
                pullShapeCursor(job, hand);
                hand.aim = -1;
            } else if (onlyOwned && probe == ShapeCell.SKIP) {
                resolveShapeCell(job, hand, hand.aim, true);
                hand.aim = -1;
            } else if (onlyOwned && probe == ShapeCell.OUT) {
                resolveShapeCell(job, hand, hand.aim, false);
                hand.aim = -1;
            } else {
                hand.aim = -1;
            }
        }
        int patterned = smallBoxReadyIndex(level, player, job, hand, shapeKind, shapePinLocal);
        if (patterned >= 0) {
            return patterned;
        }
        // Small shapes pick the hover that covers the most cells (the middle of a sphere).
        // Nearest-first is only for large jobs, where scoring every cell would hitch the tick.
        if (origin != null && !shapeOneStation(job, hand) && job.total() > 512) {
            // Already in place-reach: closest READY (finish this hover, cut fly time).
            // One-station shares skip this — spawning on the corner must not beat the hover
            // that covers the whole 1×5 / 2×4 / strip.
            int local = shapeClusterIndex(
                    level, player, job, hand, origin, JobManager.PLACE_NEAR_SQR, JobManager.CLUSTER_RADIUS,
                    shapeKind, shapePinLocal, onlyOwned, false);
            if (local >= 0) {
                return local;
            }
            // Soft fly band: closest first. Density nudge only while AOE×crew still matters.
            int near = shapeClusterIndex(
                    level, player, job, hand, origin, JobManager.FLY_NEAR_SQR, JobManager.FLY_NEAR_RADIUS,
                    shapeKind, shapePinLocal, onlyOwned, shapeOptimizeRoute(job, hand));
            if (near >= 0) {
                return near;
            }
        }
        // Help mode: join leftovers near the busiest share after rebalance.
        if (!onlyOwned) {
            return shapeHelpTarget(level, player, job, hand, shapeKind, shapePinLocal, scanCap);
        }
        int seed = shapeBestSeedIndex(level, player, job, hand, origin, shapeKind, shapePinLocal, scanCap);
        if (seed >= 0) {
            return seed;
        }
        return -1;
    }

    /**
     * Open, unclaimed cell inside this wright's reach from where it hovers now, owned or not.
     * Without this a hover at the edge of a thin share only placed the share's own cells and left
     * the rest of its reach for the neighbour, so full crews built one-block-wide lines.
     * Another wright's aim is still respected, so nobody piles onto a claimed cell. Returns -1 when none.
     */
    private static int nextInReach(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            DroneEntity drone,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        if (drone == null || job.min() == null) {
            return -1;
        }
        int reach = WrightTier.operationReachSqr(handFabricator(hand));
        BlockPos foot = BlockPos.containing(drone.getX(), drone.getY() - 0.85D, drone.getZ());
        int best = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int dist = dx * dx + dy * dy + dz * dz;
                    if (dist > reach) {
                        continue;
                    }
                    BlockPos pos = foot.offset(dx, dy, dz);
                    int index = job.scanIndex(pos);
                    if (index < 0 || job.isShapeResolved(index) || WorkPicker.aimedByOther(job, hand, index)) {
                        continue;
                    }
                    if (classifyShapeCell(level, player, job, hand, index, shapeKind, shapePinLocal, false)
                            != ShapeCell.READY) {
                        continue;
                    }
                    boolean owned = ownsShapeCell(
                            job, hand, pos.getX() - job.min().getX(), pos.getY() - job.min().getY(),
                            pos.getZ() - job.min().getZ());
                    // Nearest first; at equal distance the wright's own cells go before a neighbour's.
                    int score = dist * 2 + (owned ? 0 : 1);
                    if (score < bestScore || (score == bestScore && index < best)) {
                        bestScore = score;
                        best = index;
                    }
                }
            }
        }
        return best;
    }

    /** Operations tier for AOE sizing. */
    static int handFabricator(BuildJob.Hand hand) {
        return hand == null ? WrightTier.BASIC : WrightTier.clamp(hand.fabricator);
    }

    /** True when {@code pos} is still within this hover's place reach for the given fabricator tier. */
    static boolean inOperationReachFromHover(Vec3 hover, BlockPos pos, int fabricator) {
        if (hover == null || pos == null) {
            return false;
        }
        return inOperationReachFromCell(BlockPos.containing(hover.x, hover.y - 0.85D, hover.z), pos, fabricator);
    }

    /** Sphere around a block the wright would stand in. Corners of the 3×3×3 are outside. */
    private static boolean inOperationReachFromCell(BlockPos anchor, BlockPos cell, int fabricator) {
        if (anchor == null || cell == null) {
            return false;
        }
        int dx = cell.getX() - anchor.getX();
        int dy = cell.getY() - anchor.getY();
        int dz = cell.getZ() - anchor.getZ();
        return dx * dx + dy * dy + dz * dz <= WrightTier.operationReachSqr(fabricator);
    }

    /**
     * The part of the job box one wright stamps from predefined stands. A job that is itself 3×3×3
     * or smaller is one piece for every wright. A bigger job is cut into slabs (see
     * {@link #ensureShapeShares}); a wright whose own slab happens to be 3×3×3 or smaller stamps
     * that slab from the same predefined stands instead of crawling it. Offsets are local to the job box.
     */
    private record SmallPiece(int ox, int oy, int oz, int sx, int sy, int sz, boolean whole) {
        BlockPos at(BuildJob job, int x, int y, int z) {
            return job.min().offset(ox + x, oy + y, oz + z);
        }
    }

    /** This wright's piece, or null when it keeps the nearest-first crawler. */
    private static SmallPiece smallPiece(BuildJob job, BuildJob.Hand hand, int reach) {
        if (job == null || hand == null || job.min() == null) {
            return null;
        }
        if (com.wrights.blueprint.SmallBoxStands.applies(job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), reach)) {
            return new SmallPiece(0, 0, 0, job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), true);
        }
        if (job.hands().size() <= 1 || hand.shareFrom < 0 || hand.shareTo <= hand.shareFrom) {
            return null;
        }
        ShapeStrip strip = shapeStrip(job);
        int from = Math.max(0, hand.shareFrom);
        int to = Math.min(shapeAxisSpan(job), hand.shareTo);
        int width = to - from;
        if (width <= 0) {
            return null;
        }
        SmallPiece piece = strip.alongX
                ? new SmallPiece(from, 0, 0, width, strip.sy, strip.sz, false)
                : strip.alongZ
                        ? new SmallPiece(0, 0, from, strip.sx, strip.sy, width, false)
                        : new SmallPiece(0, from, 0, strip.sx, width, strip.sz, false);
        return com.wrights.blueprint.SmallBoxStands.applies(piece.sx(), piece.sy(), piece.sz(), reach) ? piece : null;
    }

    /** Stands for a piece. A slab of a bigger shape is planned as a plain solid box. */
    private static int[][] pieceStands(BuildJob job, SmallPiece piece, int reach) {
        int kind = piece.whole() ? job.shapeKind() : com.wrights.blueprint.Shapes.Kind.CUBE.ordinal();
        boolean hollow = piece.whole() && job.shapeHollow();
        return com.wrights.blueprint.SmallBoxStands.stands(piece.sx(), piece.sy(), piece.sz(), hollow, kind, reach);
    }

    /**
     * One hover for a small piece, the way main's small-box plans work.
     * A 3×3×3 sphere is the center. Nothing is placed until the wright is standing on that stamp,
     * so the reach sphere covers the whole piece in one operation. Pieces larger than 3×3×3 return
     * {@link #JobManager.PATTERN_SKIP} and keep the nearest-first crawler.
     */
    private static int tickSmallPattern(
            ServerLevel level,
            ServerPlayer player,
            SchematicLibrary library,
            BuildJob job,
            BuildJob.Hand hand,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.ShapeMaterials.FillMode fillMode,
            java.util.Random random,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        int reach = WrightTier.operationReachSqr(handFabricator(hand));
        SmallPiece piece = smallPiece(job, hand, reach);
        if (piece == null) {
            return JobManager.PATTERN_SKIP;
        }
        resolveSmallOutside(level, player, job, hand, piece, shapeKind, shapePinLocal);
        int[] stand = chooseSmallStand(level, player, job, hand, piece, shapeKind, shapePinLocal);
        if (stand == null) {
            if (!piece.whole()) {
                // Slab finished: the crawler path decides between rebalancing, helping and idling.
                return JobManager.PATTERN_SKIP;
            }
            hand.idle = true;
            hand.aim = -1;
            hand.shareRemain = 0;
            setShapeHandStatus(level, hand, null, DroneEntity.WAIT_IDLE);
            return JobManager.PATTERN_DONE;
        }
        BlockPos fly = piece.at(job, stand[0], stand[1], stand[2]);
        int anchor = job.scanIndex(fly);
        if (anchor >= 0) {
            WorkPicker.claimAim(job, hand, anchor);
        }
        hand.idle = false;
        BlockState state = shapeNextState(job, usable, fillMode, random);
        if (state == null) {
            hand.aim = -1;
            setShapeHandStatus(level, hand, usable.isEmpty() ? null : usable.get(0).state(), DroneEntity.WAIT_ITEMS);
            DroneFleet.parkForItems(level, player, hand);
            return JobManager.PATTERN_ITEMS;
        }
        DroneEntity drone = DroneFleet.ensureHand(level, player, job, hand, fly);
        if (drone != null) {
            drone.setParts(hand.parts());
        }
        if (drone == null) {
            setShapeHandStatus(level, hand, state, DroneEntity.WAIT_CHUNK);
            return JobManager.PATTERN_CHUNK;
        }
        if (HoverWork.approachStand(drone, hand, fly)) {
            drone.setWorkStatus(BlockFilters.icon(state), ItemStack.EMPTY, DroneEntity.WAIT_NONE);
            return JobManager.PATTERN_DONE;
        }
        int budget = CellPlacement.actionBudget(hand);
        int placed = 0;
        while (placed < budget) {
            int index = -1;
            int bestDist = Integer.MAX_VALUE;
            for (int z = 0; z < piece.sz(); z++) {
                for (int y = 0; y < piece.sy(); y++) {
                    for (int x = 0; x < piece.sx(); x++) {
                        if (!com.wrights.blueprint.SmallBoxStands.covers(stand, x, y, z, reach)) {
                            continue;
                        }
                        int cell = job.scanIndex(piece.at(job, x, y, z));
                        if (cell < 0 || job.isShapeResolved(cell) || WorkPicker.aimedByOther(job, hand, cell)) {
                            continue;
                        }
                        ShapeCell kind = classifyShapeCell(
                                level, player, job, hand, cell, shapeKind, shapePinLocal, false);
                        if (kind == ShapeCell.OUT) {
                            resolveShapeCell(job, hand, cell, false);
                            continue;
                        }
                        if (kind == ShapeCell.SKIP) {
                            resolveShapeCell(job, hand, cell, true);
                            continue;
                        }
                        if (kind != ShapeCell.READY) {
                            continue;
                        }
                        int dist = (x - stand[0]) * (x - stand[0])
                                + (y - stand[1]) * (y - stand[1])
                                + (z - stand[2]) * (z - stand[2]);
                        if (dist < bestDist) {
                            bestDist = dist;
                            index = cell;
                        }
                    }
                }
            }
            if (index < 0) {
                break;
            }
            BlockPos cell = job.scanPosAt(index);
            BlockState placeState = shapeNextState(job, usable, fillMode, random);
            if (placeState == null) {
                setShapeHandStatus(level, hand, usable.get(0).state(), DroneEntity.WAIT_ITEMS);
                DroneFleet.parkForItems(level, player, hand);
                return JobManager.PATTERN_ITEMS;
            }
            int dx = cell.getX() - job.min().getX();
            int dy = cell.getY() - job.min().getY();
            int dz = cell.getZ() - job.min().getZ();
            Schematic.Entry entry = new Schematic.Entry((short) dx, (short) dy, (short) dz, placeState, false);
            PlaceResult result = CellPlacement.placeOne(level, player, job, cell, entry, false, hand, drone);
            if (result == PlaceResult.PAUSE) {
                job.setWaitKind(BuildJob.WAIT_CHUNK);
                setShapeHandStatus(level, hand, placeState, DroneEntity.WAIT_CHUNK);
                library.setDirty();
                return JobManager.PATTERN_CHUNK;
            }
            if (result == PlaceResult.WAIT) {
                setShapeHandStatus(level, hand, placeState, DroneEntity.WAIT_ITEMS);
                DroneFleet.parkForItems(level, player, hand);
                return JobManager.PATTERN_ITEMS;
            }
            if (result == PlaceResult.ENERGY) {
                return JobManager.PATTERN_ENERGY;
            }
            resolveShapeCell(job, hand, index, false);
            job.shapeOrderAdvance();
            drone.setWorkStatus(BlockFilters.icon(placeState), ItemStack.EMPTY, DroneEntity.WAIT_NONE);
            placed++;
        }
        if (placed > 0) {
            HoverWork.countOp(job, hand, anchor);
        }
        if (anchor >= 0 && smallStandHasReady(
                level, player, job, hand, piece, shapeKind, shapePinLocal, stand, reach)) {
            WorkPicker.claimAim(job, hand, anchor);
        }
        return JobManager.PATTERN_DONE;
    }

    /** Air outside the shape, and cells that cannot be placed, do not need a hover. */
    private static void resolveSmallOutside(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            SmallPiece piece,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        for (int z = 0; z < piece.sz(); z++) {
            for (int y = 0; y < piece.sy(); y++) {
                for (int x = 0; x < piece.sx(); x++) {
                    int index = job.scanIndex(piece.at(job, x, y, z));
                    if (index < 0 || job.isShapeResolved(index)) {
                        continue;
                    }
                    ShapeCell kind = classifyShapeCell(
                            level, player, job, hand, index, shapeKind, shapePinLocal, false);
                    if (kind == ShapeCell.OUT) {
                        resolveShapeCell(job, hand, index, false);
                    } else if (kind == ShapeCell.SKIP) {
                        resolveShapeCell(job, hand, index, true);
                    }
                }
            }
        }
    }

    /** Predefined stand count for a small shape. {@code 0} leaves crew sizing alone. */
    private static int smallBoxCrew(BuildJob job, int fabricator) {
        if (job == null) {
            return 0;
        }
        int reach = WrightTier.operationReachSqr(fabricator);
        return com.wrights.blueprint.SmallBoxStands.count(
                job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), job.shapeHollow(), job.shapeKind(), reach);
    }

    /**
     * World block this wright should stand on for its small piece, or null when the piece is
     * larger than 3×3×3 (those keep the nearest-first crawler).
     */
    private static BlockPos smallBoxStand(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        SmallPiece piece = smallPiece(job, hand, WrightTier.operationReachSqr(handFabricator(hand)));
        if (piece == null) {
            return null;
        }
        int[] local = chooseSmallStand(level, player, job, hand, piece, shapeKind, shapePinLocal);
        if (local == null) {
            return null;
        }
        return piece.at(job, local[0], local[1], local[2]);
    }

    /** A READY cell of this wright's predefined stand, or {@code -1} when the cheatsheet does not apply. */
    private static int smallBoxReadyIndex(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        int reach = WrightTier.operationReachSqr(handFabricator(hand));
        SmallPiece piece = smallPiece(job, hand, reach);
        if (piece == null) {
            return -1;
        }
        int[] stand = chooseSmallStand(level, player, job, hand, piece, shapeKind, shapePinLocal);
        if (stand == null) {
            return -1;
        }
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int z = 0; z < piece.sz(); z++) {
            for (int y = 0; y < piece.sy(); y++) {
                for (int x = 0; x < piece.sx(); x++) {
                    if (!com.wrights.blueprint.SmallBoxStands.covers(stand, x, y, z, reach)) {
                        continue;
                    }
                    int index = job.scanIndex(piece.at(job, x, y, z));
                    if (index < 0 || WorkPicker.aimedByOther(job, hand, index)) {
                        continue;
                    }
                    if (classifyShapeCell(level, player, job, hand, index, shapeKind, shapePinLocal, true)
                            != ShapeCell.READY) {
                        continue;
                    }
                    int dist = (x - stand[0]) * (x - stand[0])
                            + (y - stand[1]) * (y - stand[1])
                            + (z - stand[2]) * (z - stand[2]);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = index;
                    }
                }
            }
        }
        return best;
    }

    /** This lane's stand, or the next stand that still has a block to place. */
    private static int[] chooseSmallStand(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            SmallPiece piece,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        if (job == null || hand == null || piece == null) {
            return null;
        }
        int reach = WrightTier.operationReachSqr(handFabricator(hand));
        int[][] stands = pieceStands(job, piece, reach);
        if (stands.length == 0) {
            return null;
        }
        int lane = Math.max(0, hand.lane);
        for (int step = 0; step < stands.length; step++) {
            int[] stand = stands[(lane + step) % stands.length];
            if (smallStandHasReady(level, player, job, hand, piece, shapeKind, shapePinLocal, stand, reach)) {
                return stand;
            }
        }
        return null;
    }

    private static boolean smallStandHasReady(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            SmallPiece piece,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int[] stand,
            int reach) {
        for (int z = 0; z < piece.sz(); z++) {
            for (int y = 0; y < piece.sy(); y++) {
                for (int x = 0; x < piece.sx(); x++) {
                    if (!com.wrights.blueprint.SmallBoxStands.covers(stand, x, y, z, reach)) {
                        continue;
                    }
                    int index = job.scanIndex(piece.at(job, x, y, z));
                    if (index < 0 || job.isShapeResolved(index)) {
                        continue;
                    }
                    // Another wright already took this stamp. Do not split it.
                    if (WorkPicker.aimedByOther(job, hand, index)) {
                        return false;
                    }
                    if (classifyShapeCell(level, player, job, hand, index, shapeKind, shapePinLocal, false)
                            == ShapeCell.READY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** This hand's cells fit in one place-AOE hover (short line, small plate, or one beam strip). */
    private static boolean shapeOneStation(BuildJob job, BuildJob.Hand hand) {
        int lanes = Math.max(1, hand == null ? 1 : hand.lanes);
        int total = Math.max(0, job.total());
        int share = Math.max(1, (total + lanes - 1) / lanes);
        return share <= WrightsConfig.placeAoeCapacity(job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), handFabricator(hand));
    }

    /** Dense hover scoring while this hand's AOE share still fits a couple waves; else closest-first. */
    private static boolean shapeOptimizeRoute(BuildJob job, BuildJob.Hand hand) {
        int lanes = Math.max(1, hand == null ? 1 : hand.lanes);
        int total = Math.max(0, job.total());
        int share = Math.max(1, (total + lanes - 1) / lanes);
        return WrightsConfig.optimizePlaceRoute(
                total, job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), lanes, share, handFabricator(hand));
    }

    /**
     * Long-range fly-to for this hand. Large jobs stay closest-first (cheap). Small jobs may
     * nudge to a denser place-reach hover so a short beam/wall is not crawled from a corner.
     */
    private static int shapeBestSeedIndex(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            Vec3 origin,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        int total = job.total();
        if (total <= 0) {
            return -1;
        }
        // Density scoring only while this share / job still fits a couple AOE waves.
        boolean compact = shapeOptimizeRoute(job, hand);
        pullShapeCursor(job, hand);
        int examined = 0;
        int nearest = -1;
        double nearestDist = Double.MAX_VALUE;
        int[] candidates = compact ? new int[Math.min(total, scanCap)] : null;
        int candidateCount = 0;
        int from = Math.max(0, hand.cursor);
        for (int i = from; i < total && examined < scanCap; i++) {
            BlockPos pos = job.scanPosAt(i);
            int lx = pos.getX() - job.min().getX();
            int ly = pos.getY() - job.min().getY();
            int lz = pos.getZ() - job.min().getZ();
            if (!ownsShapeCell(job, hand, lx, ly, lz)) {
                continue;
            }
            if (WorkPicker.aimedByOther(job, hand, i)) {
                examined++;
                continue;
            }
            ShapeCell kind = classifyShapeCell(level, player, job, hand, i, shapeKind, shapePinLocal, true);
            examined++;
            if (kind == ShapeCell.DONE) {
                continue;
            }
            if (kind == ShapeCell.OUT) {
                resolveShapeCell(job, hand, i, false);
                continue;
            }
            if (kind == ShapeCell.SKIP) {
                resolveShapeCell(job, hand, i, true);
                continue;
            }
            if (kind == ShapeCell.BLOCKED || kind != ShapeCell.READY) {
                continue;
            }
            double dist = origin == null
                    ? 0.0D
                    : origin.distanceToSqr(Vec3.atCenterOf(pos));
            if (nearest < 0 || dist < nearestDist || (dist == nearestDist && i < nearest)) {
                nearest = i;
                nearestDist = dist;
            }
            if (candidates != null && candidateCount < candidates.length) {
                candidates[candidateCount++] = i;
            }
            if (!compact) {
                break;
            }
        }
        if (nearest < 0) {
            return -1;
        }
        if (!compact || candidates == null || candidateCount <= 1) {
            return nearest;
        }
        // One place-AOE share, far, or pre-launch: densest hover (station center).
        // Larger shares that are already nearby: near-first, denser only within JobManager.SEED_SLACK_SQR.
        boolean oneHover = shapeOneStation(job, hand) || candidateCount <= WrightsConfig.placeAoeCapacity(
                job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), handFabricator(hand));
        boolean far = origin == null || nearestDist > JobManager.FLY_NEAR_SQR || oneHover || job.total() <= 512;
        int best = nearest;
        int bestScore = shapeClusterReadyCount(
                level, player, job, hand, Vec3.atCenterOf(job.scanPosAt(nearest)), shapeKind, shapePinLocal, true);
        double bestDist = nearestDist;
        double cx = job.min().getX() + job.boxSizeX() * 0.5D;
        double cy = job.min().getY() + job.boxSizeY() * 0.5D;
        double cz = job.min().getZ() + job.boxSizeZ() * 0.5D;
        for (int c = 0; c < candidateCount; c++) {
            int index = candidates[c];
            BlockPos pos = job.scanPosAt(index);
            double dist = origin == null
                    ? pos.distToCenterSqr(cx, cy, cz)
                    : origin.distanceToSqr(Vec3.atCenterOf(pos));
            if (!far && dist > nearestDist + JobManager.SEED_SLACK_SQR) {
                continue;
            }
            int score = shapeClusterReadyCount(
                    level, player, job, hand, Vec3.atCenterOf(pos), shapeKind, shapePinLocal, true);
            if (score > bestScore
                    || (score == bestScore && dist < bestDist)
                    || (score == bestScore && dist == bestDist && index < best)) {
                best = index;
                bestScore = score;
                bestDist = dist;
            }
        }
        // Hollow shapes: the best hover is the empty middle, which is not itself a block to place.
        if (job.total() <= 512 && job.shapeCenter() != null) {
            int centerIndex = job.scanIndex(job.shapeCenter());
            if (centerIndex >= 0) {
                int score = shapeClusterReadyCount(
                        level, player, job, hand, Vec3.atCenterOf(job.shapeCenter()), shapeKind, shapePinLocal, true);
                if (score > bestScore) {
                    best = centerIndex;
                }
            }
        }
        return best;
    }

    /** How many owned READY cells sit inside place-reach of {@code origin}. */
    private static int shapeClusterReadyCount(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            Vec3 origin,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            boolean onlyOwned) {
        BlockPos min = job.min();
        int ox = (int) Math.floor(origin.x) - min.getX();
        int oy = (int) Math.floor(origin.y) - min.getY();
        int oz = (int) Math.floor(origin.z) - min.getZ();
        int count = 0;
        for (int dy = -JobManager.CLUSTER_RADIUS; dy <= JobManager.CLUSTER_RADIUS; dy++) {
            for (int dx = -JobManager.CLUSTER_RADIUS; dx <= JobManager.CLUSTER_RADIUS; dx++) {
                for (int dz = -JobManager.CLUSTER_RADIUS; dz <= JobManager.CLUSTER_RADIUS; dz++) {
                    BlockPos pos = min.offset(ox + dx, oy + dy, oz + dz);
                    int index = job.scanIndex(pos);
                    if (index < 0 || WorkPicker.aimedByOther(job, hand, index)) {
                        continue;
                    }
                    if (!inOperationReachFromCell(BlockPos.containing(origin), pos, handFabricator(hand))) {
                        continue;
                    }
                    if (classifyShapeCell(level, player, job, hand, index, shapeKind, shapePinLocal, onlyOwned)
                            == ShapeCell.READY) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * True when another busy hand exclusively owns this cell.
     * Co-owners of the same share range (last thin slice) may both place there.
     */
    private static boolean ownedByBusyPeer(BuildJob job, BuildJob.Hand hand, int lx, int ly, int lz) {
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand || other.idle || other.sheltering) {
                continue;
            }
            if (hand.shareFrom >= 0
                    && hand.shareFrom == other.shareFrom
                    && hand.shareTo == other.shareTo) {
                continue;
            }
            if (ownsShapeCell(job, other, lx, ly, lz)) {
                return true;
            }
        }
        return false;
    }

    private enum ShapeCell {
        READY,
        SKIP,
        OUT,
        FOREIGN,
        BLOCKED,
        /** Already counted toward job progress — move the hand past it without counting again. */
        DONE
    }

    private static ShapeCell classifyShapeCell(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            int index,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            boolean onlyOwned) {
        if (index < 0 || index >= job.total()) {
            return ShapeCell.BLOCKED;
        }
        if (job.isShapeResolved(index)) {
            return ShapeCell.DONE;
        }
        BlockPos pos = job.scanPosAt(index);
        int lx = pos.getX() - job.min().getX();
        int ly = pos.getY() - job.min().getY();
        int lz = pos.getZ() - job.min().getZ();
        if (onlyOwned && !ownsShapeCell(job, hand, lx, ly, lz)) {
            return ShapeCell.FOREIGN;
        }
        if (!com.wrights.blueprint.Shapes.containsWorld(
                shapeKind,
                pos,
                job.shapeCenter(),
                shapePinLocal,
                job.shapeSize(),
                job.shapeHeight(),
                job.shapeThickness(),
                job.shapeHollow(),
                job.shapeAxis(),
                job.shapeYaw())) {
            return ShapeCell.OUT;
        }
        if (level.isOutsideBuildHeight(pos) || !com.wrights.blueprint.BoxEdits.inWorld(pos)) {
            return ShapeCell.SKIP;
        }
        if (!level.isLoaded(pos)) {
            return WrightsConfig.REQUIRE_CHUNKS_LOADED.getAsBoolean() ? ShapeCell.BLOCKED : ShapeCell.SKIP;
        }
        if (!level.mayInteract(player, pos) || !CellPlacement.canBuild(player, level, pos)) {
            return ShapeCell.SKIP;
        }
        BlockState current = level.getBlockState(pos);
        if (job.exchangeJob()) {
            if (!exchangeMatches(level, pos, current, job.exchangeFrom())) {
                return ShapeCell.SKIP;
            }
            // Skip no-op swaps before a wright flies out for them.
            if (exchangeAlreadyDestination(job, current, index)) {
                return ShapeCell.SKIP;
            }
        } else if (!job.voidBlocks() && !current.isAir() && !current.canBeReplaced()) {
            return ShapeCell.SKIP;
        }
        return ShapeCell.READY;
    }

    /** True when this exchange cell already holds the block we would place there. */
    private static boolean exchangeAlreadyDestination(BuildJob job, BlockState current, int index) {
        if (job == null || current == null || !job.exchangeJob()) {
            return false;
        }
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable = new java.util.ArrayList<>();
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : com.wrights.blueprint.ShapeMaterials.parse(job.shapeMaterials())) {
            if (entry != null && entry.state() != null) {
                usable.add(entry);
            }
        }
        if (usable.isEmpty()) {
            return false;
        }
        com.wrights.blueprint.ShapeMaterials.FillMode fillMode =
                com.wrights.blueprint.ShapeMaterials.FillMode.byId(job.shapeFillMode());
        BlockState planned = shapeStateAt(job, usable, fillMode, null, job.shapeOrderIndex(), index);
        return planned != null && current.equals(planned);
    }

    /**
     * READY cell near {@code origin}. Closest by default; when {@code preferDense}, still near-first
     * but within {@link #JobManager.SEED_SLACK_SQR} of the nearest hit prefer a denser place-reach hover.
     * Help mode may clean leftovers on any share; {@link #aimedByOther} still prevents pile-ons.
     */
    private static int shapeClusterIndex(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            Vec3 origin,
            double reachSqr,
            int radius,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            boolean onlyOwned,
            boolean preferDense) {
        BlockPos min = job.min();
        int cx = (int) Math.floor(origin.x) - min.getX();
        int cy = (int) Math.floor(origin.y) - min.getY();
        int cz = (int) Math.floor(origin.z) - min.getZ();
        int nearest = -1;
        double nearestDist = Double.MAX_VALUE;
        int[] denseCandidates = preferDense ? new int[64] : null;
        double[] denseDists = preferDense ? new double[64] : null;
        int denseCount = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = min.offset(cx + dx, cy + dy, cz + dz);
                    int index = job.scanIndex(pos);
                    if (index < 0 || WorkPicker.aimedByOther(job, hand, index)) {
                        continue;
                    }
                    ShapeCell kind = classifyShapeCell(level, player, job, hand, index, shapeKind, shapePinLocal, onlyOwned);
                    if (kind != ShapeCell.READY) {
                        continue;
                    }
                    double dist = origin.distanceToSqr(Vec3.atCenterOf(pos));
                    if (reachSqr <= JobManager.PLACE_NEAR_SQR) {
                        if (!inOperationReachFromHover(origin, pos, handFabricator(hand))) {
                            continue;
                        }
                    } else if (dist > reachSqr) {
                        continue;
                    }
                    if (nearest < 0 || dist < nearestDist || (dist == nearestDist && index < nearest)) {
                        nearest = index;
                        nearestDist = dist;
                    }
                    if (denseCandidates != null && denseCount < denseCandidates.length) {
                        denseCandidates[denseCount] = index;
                        denseDists[denseCount] = dist;
                        denseCount++;
                    }
                }
            }
        }
        if (nearest < 0 || !preferDense || denseCandidates == null || denseCount <= 1) {
            return nearest;
        }
        int aoe = WrightsConfig.placeAoeCapacity(job.boxSizeX(), job.boxSizeY(), job.boxSizeZ(), handFabricator(hand));
        boolean oneHover = denseCount <= aoe;
        int best = nearest;
        int bestScore = shapeClusterReadyCount(
                level, player, job, hand, Vec3.atCenterOf(job.scanPosAt(nearest)), shapeKind, shapePinLocal, onlyOwned);
        double bestDist = nearestDist;
        for (int i = 0; i < denseCount; i++) {
            if (!oneHover && denseDists[i] > nearestDist + JobManager.SEED_SLACK_SQR) {
                continue;
            }
            int index = denseCandidates[i];
            int score = shapeClusterReadyCount(
                    level, player, job, hand, Vec3.atCenterOf(job.scanPosAt(index)), shapeKind, shapePinLocal, onlyOwned);
            if (score > bestScore
                    || (score == bestScore && denseDists[i] < bestDist)
                    || (score == bestScore && denseDists[i] == bestDist && index < best)) {
                best = index;
                bestScore = score;
                bestDist = denseDists[i];
            }
        }
        return best;
    }

    private static boolean shapeCanPayAny(
            ServerPlayer player,
            ServerLevel level,
            BuildJob job,
            BuildJob.Hand hand,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable) {
        if (usable == null || usable.isEmpty()) {
            return false;
        }
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : usable) {
            if (entry != null && shapeCanPay(player, level, job, hand, entry.state())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when a wright from {@code home} could pay for at least one of the job's materials. A placed port
     * spends only its own chest and the digital store; a personal wright ({@code home} null) spends from you.
     */
    static boolean homeCanPay(ServerPlayer player, ServerLevel level, BuildJob job, BlockPos home) {
        if (job.kind() != BuildJob.Kind.SHAPE || job.mining() || CellPlacement.freePlace(player)) {
            return true;
        }
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable = new java.util.ArrayList<>();
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : com.wrights.blueprint.ShapeMaterials.parse(job.shapeMaterials())) {
            if (entry != null && entry.state() != null) {
                usable.add(entry);
            }
        }
        if (usable.isEmpty()) {
            return true;
        }
        BuildJob.Hand hand = new BuildJob.Hand();
        hand.homePort = home;
        return shapeCanPayAny(player, level, job, hand, usable);
    }

    /**
     * Some wright on the job, or a free one in range, could pay for at least one of the job's materials from its
     * own supply. Part of the stock is enough: the job builds what it can and stalls again when it runs out.
     */
    private static boolean crewCanPay(ServerPlayer player, SchematicLibrary library, ServerLevel level, BuildJob job,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable) {
        for (BuildJob.Hand hand : job.hands()) {
            if (shapeCanPayAny(player, level, job, hand, usable)) {
                return true;
            }
        }
        for (WrightHomes.FreeWright free : CrewRoster.freeUnits(player, library, job, Integer.MAX_VALUE)) {
            if (!CrewRoster.coversJob(player, job, free.homePort())) {
                continue;
            }
            BuildJob.Hand hand = new BuildJob.Hand();
            hand.homePort = free.homePort();
            if (shapeCanPayAny(player, level, job, hand, usable)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback help: prefer work near a peer that still has real leftovers, then any
     * unresolved READY cell in the job (endgame cleanup when shares no longer match reality).
     */
    private static int shapeHelpTarget(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        BuildJob.Hand busiest = busiestShapeShare(level, player, job, hand, shapeKind, shapePinLocal, scanCap);
        if (busiest != null) {
            Vec3 otherOrigin = WorkPicker.handOrigin(level, busiest);
            if (otherOrigin != null) {
                int near = shapeClusterIndex(
                        level, player, job, hand, otherOrigin, JobManager.FLY_NEAR_SQR, JobManager.FLY_NEAR_RADIUS,
                        shapeKind, shapePinLocal, false, true);
                if (near >= 0) {
                    return near;
                }
            }
            if (busiest.aim >= 0 && busiest.aim < job.total()) {
                Vec3 aimOrigin = Vec3.atCenterOf(job.scanPosAt(busiest.aim));
                int near = shapeClusterIndex(
                        level, player, job, hand, aimOrigin, JobManager.FLY_NEAR_SQR, JobManager.FLY_NEAR_RADIUS,
                        shapeKind, shapePinLocal, false, true);
                if (near >= 0) {
                    return near;
                }
            }
        }
        for (BuildJob.Hand other : job.hands()) {
            if (other == hand || other.aim < 0 || other.aim >= job.total()) {
                continue;
            }
            Vec3 aimOrigin = Vec3.atCenterOf(job.scanPosAt(other.aim));
            int near = shapeClusterIndex(
                    level, player, job, hand, aimOrigin, JobManager.FLY_NEAR_SQR, JobManager.FLY_NEAR_RADIUS,
                    shapeKind, shapePinLocal, false, true);
            if (near >= 0 && near != other.aim) {
                return near;
            }
        }
        return shapeAnyReadyIndex(level, player, job, hand, shapeKind, shapePinLocal, scanCap);
    }

    /** Any unresolved READY cell in the AABB, resolving OUT/SKIP as we go. */
    private static int shapeAnyReadyIndex(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        int total = job.total();
        int examined = 0;
        int budget = Math.max(scanCap * 4, 256);
        for (int i = 0; i < total && examined < budget; i++) {
            if (job.isShapeResolved(i)) {
                continue;
            }
            examined++;
            if (WorkPicker.aimedByOther(job, hand, i)) {
                continue;
            }
            ShapeCell kind = classifyShapeCell(level, player, job, hand, i, shapeKind, shapePinLocal, false);
            if (kind == ShapeCell.READY) {
                return i;
            }
            if (kind == ShapeCell.OUT) {
                resolveShapeCell(job, hand, i, false);
            } else if (kind == ShapeCell.SKIP) {
                resolveShapeCell(job, hand, i, true);
            }
        }
        return -1;
    }

    /**
     * Assign equal block-axis shares once when a multi-wright shape crew starts.
     * Hands that join later keep {@code shareFrom < 0} until they claim half of a busy share.
     */
    private static void ensureShapeShares(BuildJob job) {
        if (job == null || job.hands().isEmpty()) {
            return;
        }
        int span = shapeAxisSpan(job);
        if (span <= 0) {
            return;
        }
        boolean anyAssigned = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.shareFrom >= 0 && hand.shareTo > hand.shareFrom) {
                anyAssigned = true;
                break;
            }
        }
        if (anyAssigned) {
            return;
        }
        int n = job.hands().size();
        int cross = shapeAxisCrossSection(job);
        for (int i = 0; i < n; i++) {
            BuildJob.Hand hand = job.hands().get(i);
            hand.shareFrom = i * span / n;
            hand.shareTo = (i + 1) * span / n;
            int width = Math.max(0, hand.shareTo - hand.shareFrom);
            hand.shareRemain = width * cross;
            if (width <= 0) {
                hand.shareFrom = -1;
                hand.shareTo = -1;
                hand.shareRemain = 0;
            }
        }
    }

    /**
     * Finished (or empty-share) wright takes half of the busiest share that still has
     * real READY work. A 1-block-deep share is co-owned instead of split.
     */
    private static boolean tryRebalanceShapeShare(
            ServerPlayer player,
            SchematicLibrary library,
            ServerLevel level,
            BuildJob job,
            BuildJob.Hand hand,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        if (job == null || hand == null || level == null || job.hands().size() < 2 || job.cursor() >= job.total()) {
            return false;
        }
        BuildJob.Hand best = busiestShapeShare(level, player, job, hand, shapeKind, shapePinLocal, scanCap);
        if (best == null) {
            return false;
        }
        int from = best.shareFrom;
        int to = best.shareTo;
        int width = to - from;
        if (width <= 0) {
            return false;
        }
        // Already on that strip — do not thrash shares.
        if (hand.shareFrom == from && hand.shareTo == to) {
            return false;
        }
        int oldRemain = Math.max(0, best.shareRemain);
        if (width >= 2) {
            int mid = from + width / 2;
            if (mid <= from || mid >= to) {
                return false;
            }
            boolean leftWork = shapeShareRangeHasWork(
                    level, player, job, best, from, mid, shapeKind, shapePinLocal, scanCap);
            boolean rightWork = shapeShareRangeHasWork(
                    level, player, job, best, mid, to, shapeKind, shapePinLocal, scanCap);
            if (!leftWork && !rightWork) {
                return false;
            }
            if (leftWork && rightWork) {
                // Fair split — donor keeps the near half, helper takes the far half.
                best.shareFrom = from;
                best.shareTo = mid;
                hand.shareFrom = mid;
                hand.shareTo = to;
            } else if (leftWork) {
                // Only the near half still has blocks — co-own that half.
                best.shareFrom = from;
                best.shareTo = mid;
                hand.shareFrom = from;
                hand.shareTo = mid;
            } else {
                // Only the far half still has blocks — co-own that half.
                best.shareFrom = mid;
                best.shareTo = to;
                hand.shareFrom = mid;
                hand.shareTo = to;
            }
            boolean shared = hand.shareFrom == best.shareFrom && hand.shareTo == best.shareTo;
            if (shared) {
                best.shareRemain = oldRemain;
                hand.shareRemain = oldRemain;
            } else {
                int donorWidth = Math.max(1, best.shareTo - best.shareFrom);
                int helperWidth = Math.max(1, hand.shareTo - hand.shareFrom);
                int parts = donorWidth + helperWidth;
                best.shareRemain = Math.max(0, (int) ((long) oldRemain * donorWidth / parts));
                hand.shareRemain = Math.max(0, oldRemain - best.shareRemain);
            }
        } else {
            // Last slice — co-own until it clears.
            hand.shareFrom = from;
            hand.shareTo = to;
            hand.shareRemain = oldRemain;
        }
        int ownedStart = firstOwnedShapeScanIndex(job, hand);
        hand.cursor = ownedStart >= 0 ? ownedStart : 0;
        hand.aim = -1;
        hand.idle = false;
        hand.probe = 0;
        JobAnnouncements.noteHistory(player, library, job, JobEventLog.SHARE_SPLIT,
                Component.translatable("history.wrights.share_split").getString());
        return true;
    }

    /**
     * Hand whose owned strip still has placeable work. Ignores stale {@code shareRemain}
     * volume estimates that kept pointing at empty halves.
     */
    private static BuildJob.Hand busiestShapeShare(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand skip,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        BuildJob.Hand best = null;
        int bestLeft = 0;
        int bestWidth = 0;
        for (BuildJob.Hand other : job.hands()) {
            if (other == skip || other.sheltering) {
                continue;
            }
            if (other.shareFrom < 0 || other.shareTo <= other.shareFrom) {
                continue;
            }
            if (!shapeShareHasWork(level, player, job, other, shapeKind, shapePinLocal, scanCap)) {
                if (other.idle) {
                    other.shareRemain = 0;
                }
                continue;
            }
            int width = other.shareTo - other.shareFrom;
            int left = Math.max(1, other.shareRemain > 0 ? other.shareRemain : width);
            if (left > bestLeft || (left == bestLeft && width > bestWidth)) {
                best = other;
                bestLeft = left;
                bestWidth = width;
            }
        }
        return best;
    }

    private static boolean shapeShareHasWork(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand hand,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        if (hand == null || hand.shareFrom < 0 || hand.shareTo <= hand.shareFrom) {
            return false;
        }
        return shapeShareRangeHasWork(
                level, player, job, hand, hand.shareFrom, hand.shareTo, shapeKind, shapePinLocal, scanCap);
    }

    private static boolean shapeShareRangeHasWork(
            ServerLevel level,
            ServerPlayer player,
            BuildJob job,
            BuildJob.Hand owner,
            int shareFrom,
            int shareTo,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal,
            int scanCap) {
        if (shareFrom < 0 || shareTo <= shareFrom) {
            return false;
        }
        int total = job.total();
        int examined = 0;
        int budget = Math.max(32, scanCap);
        int savedFrom = owner.shareFrom;
        int savedTo = owner.shareTo;
        owner.shareFrom = shareFrom;
        owner.shareTo = shareTo;
        try {
            int start = firstOwnedShapeScanIndex(job, owner);
            if (start < 0) {
                return false;
            }
            for (int i = start; i < total && examined < budget; i++) {
                BlockPos pos = job.scanPosAt(i);
                int lx = pos.getX() - job.min().getX();
                int ly = pos.getY() - job.min().getY();
                int lz = pos.getZ() - job.min().getZ();
                if (!ownsShapeCell(job, owner, lx, ly, lz)) {
                    // Past this strip along the scan order — stop early when we leave the range.
                    ShapeStrip strip = shapeStrip(job);
                    int along = strip.alongX ? lx : (strip.alongZ ? lz : ly);
                    if (along >= shareTo) {
                        break;
                    }
                    continue;
                }
                if (job.isShapeResolved(i)) {
                    continue;
                }
                examined++;
                ShapeCell kind = classifyShapeCell(level, player, job, owner, i, shapeKind, shapePinLocal, true);
                if (kind == ShapeCell.READY) {
                    return true;
                }
            }
        } finally {
            owner.shareFrom = savedFrom;
            owner.shareTo = savedTo;
        }
        return false;
    }

    /** Local block ownership along the long box axis via {@link BuildJob.Hand#shareFrom}/{@code shareTo}. */
    private static boolean ownsShapeCell(BuildJob job, BuildJob.Hand hand, int lx, int ly, int lz) {
        if (job.hands().size() <= 1) {
            return true;
        }
        if (hand.shareFrom < 0 || hand.shareTo <= hand.shareFrom) {
            return false;
        }
        ShapeStrip strip = shapeStrip(job);
        int along = strip.alongX ? lx : (strip.alongZ ? lz : ly);
        return along >= hand.shareFrom && along < hand.shareTo;
    }

    /** First AABB scan index inside this hand's share, or -1 when the share is empty. */
    private static int firstOwnedShapeScanIndex(BuildJob job, BuildJob.Hand hand) {
        if (job.hands().size() <= 1) {
            return 0;
        }
        if (hand.shareFrom < 0 || hand.shareTo <= hand.shareFrom) {
            return -1;
        }
        ShapeStrip strip = shapeStrip(job);
        int local = hand.shareFrom;
        if (strip.alongX) {
            return Math.min(job.total() - 1, Math.max(0, local));
        }
        if (strip.alongZ) {
            return Math.min(job.total() - 1, Math.max(0, local) * strip.sx * strip.sy);
        }
        return Math.min(job.total() - 1, Math.max(0, local) * strip.sx);
    }

    private static int shapeAxisSpan(BuildJob job) {
        ShapeStrip strip = shapeStrip(job);
        return strip.alongX ? strip.sx : (strip.alongZ ? strip.sz : strip.sy);
    }

    private static int shapeAxisCrossSection(BuildJob job) {
        ShapeStrip strip = shapeStrip(job);
        if (strip.alongX) {
            return Math.max(1, strip.sy * strip.sz);
        }
        if (strip.alongZ) {
            return Math.max(1, strip.sx * strip.sy);
        }
        return Math.max(1, strip.sx * strip.sz);
    }

    private static ShapeStrip shapeStrip(BuildJob job) {
        int sx = Math.max(1, job.boxSizeX());
        int sy = Math.max(1, job.boxSizeY());
        int sz = Math.max(1, job.boxSizeZ());
        int cube = shapeCubeSize(sx, sy, sz);
        int nx = Math.max(1, (sx + cube - 1) / cube);
        int ny = Math.max(1, (sy + cube - 1) / cube);
        int nz = Math.max(1, (sz + cube - 1) / cube);
        boolean alongX = nx >= nz && nx >= ny;
        boolean alongZ = !alongX && nz >= ny;
        int spanCubes = alongX ? nx : (alongZ ? nz : ny);
        return new ShapeStrip(sx, sy, sz, cube, nx, ny, nz, alongX, alongZ, spanCubes);
    }

    private record ShapeStrip(
            int sx, int sy, int sz, int cube, int nx, int ny, int nz,
            boolean alongX, boolean alongZ, int spanCubes) {
    }

    private static int shapeCubeSize(int sx, int sy, int sz) {
        int maxSide = Math.max(sx, Math.max(sy, sz));
        if (maxSide <= SHAPE_CUBE) {
            return Math.max(1, maxSide);
        }
        return SHAPE_CUBE;
    }

    /** Next Shape/Exchange cell that still needs a payment block, or null when nothing is left. */
    private static BlockState shapeOwedState(
            ServerLevel level,
            BuildJob job,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.ShapeMaterials.FillMode fillMode,
            java.util.Random random,
            com.wrights.blueprint.Shapes.Kind shapeKind,
            BlockPos shapePinLocal) {
        int width = job.boxSizeX();
        int height = job.boxSizeY();
        int orderIndex = job.shapeOrderIndex();
        int scanCap = Math.max(1, WrightsConfig.COPY_POSITIONS_PER_TICK.getAsInt() * 4);
        for (int i = 0; i < scanCap && job.cursor() + i < job.total(); i++) {
            int at = job.cursor() + i;
            int x = job.min().getX() + (at % width);
            int y = job.min().getY() + ((at / width) % height);
            int z = job.min().getZ() + (at / (width * height));
            BlockPos pos = new BlockPos(x, y, z);
            if (!com.wrights.blueprint.Shapes.containsWorld(
                    shapeKind,
                    pos,
                    job.shapeCenter(),
                    shapePinLocal,
                    job.shapeSize(),
                    job.shapeHeight(),
                    job.shapeThickness(),
                    job.shapeHollow(),
                    job.shapeAxis(),
                    job.shapeYaw())) {
                continue;
            }
            if (level.isOutsideBuildHeight(pos) || !com.wrights.blueprint.BoxEdits.inWorld(pos) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (job.exchangeJob()) {
                if (!exchangeMatches(level, pos, current, job.exchangeFrom())) {
                    continue;
                }
            } else if (!job.voidBlocks() && !current.isAir() && !current.canBeReplaced()) {
                continue;
            }
            return shapeStateAt(job, usable, fillMode, random, orderIndex, at);
        }
        return null;
    }

    private static BlockState shapeStateAt(
            BuildJob job,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.ShapeMaterials.FillMode fillMode,
            java.util.Random random,
            int orderIndex,
            int cursor) {
        if (usable == null || usable.isEmpty()) {
            return null;
        }
        return switch (fillMode == null ? com.wrights.blueprint.ShapeMaterials.FillMode.RANDOM : fillMode) {
            case ORDER -> {
                int batch = Math.max(1, job.shapeOrderBatch());
                int index = (orderIndex / batch) % usable.size();
                yield usable.get(index).state();
            }
            case PRIORITY -> usable.get(0).state();
            case RANDOM -> {
                java.util.Random local = new java.util.Random(job.shapeSeed() ^ (((long) cursor) << 1));
                yield com.wrights.blueprint.ShapeMaterials.pick(usable, local);
            }
        };
    }

    /** {@code hand} decides the source: its port's chest and digital, or you when it has no port. */
    private static boolean shapeCanPay(ServerPlayer player, ServerLevel level, BuildJob job, BuildJob.Hand hand, BlockState state) {
        if (state == null || CellPlacement.freePlace(player)) {
            return true;
        }
        Item payment = BlockFilters.payment(state);
        if (payment == null) {
            return true;
        }
        if (hand != null && !JobSupply.portSupplyReady(level, player, hand.homePort)) {
            return false;
        }
        Schematic.Entry entry = new Schematic.Entry((short) 0, (short) 0, (short) 0, state, false);
        return WorkPicker.hasPlaceCosts(player, level, job, hand, entry);
    }

    /** Push Shape/Exchange current block + wait reason onto the assigned wright for Jade / info look. */
    private static void publishShapeDroneStatus(ServerLevel level, BuildJob job, BlockState state, int waitReason) {
        if (level == null || job == null) {
            return;
        }
        UUID id = job.droneId();
        if (id == null && !job.crewUnits().isEmpty()) {
            id = job.crewUnits().get(0);
        }
        if (id == null || !(level.getEntity(id) instanceof DroneEntity drone) || !drone.isAlive()) {
            return;
        }
        int reason = waitReason;
        if (job.paused() && reason == DroneEntity.WAIT_NONE) {
            reason = DroneEntity.WAIT_PAUSED;
        }
        drone.setWorkStatus(CellPlacement.shapeStatusCargo(job, state, reason), ItemStack.EMPTY, reason);
    }

    private static BlockState shapeNextState(
            BuildJob job,
            java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> usable,
            com.wrights.blueprint.ShapeMaterials.FillMode fillMode,
            java.util.Random random) {
        if (usable == null || usable.isEmpty()) {
            return null;
        }
        return switch (fillMode == null ? com.wrights.blueprint.ShapeMaterials.FillMode.RANDOM : fillMode) {
            case ORDER -> {
                int batch = Math.max(1, job.shapeOrderBatch());
                int index = (job.shapeOrderIndex() / batch) % usable.size();
                yield usable.get(index).state();
            }
            case PRIORITY -> usable.get(0).state();
            case RANDOM -> com.wrights.blueprint.ShapeMaterials.pick(usable, random);
        };
    }

    /** Empty from-list = any exchangeable solid; otherwise match block item against the filter. */
    private static boolean exchangeMatches(ServerLevel level, BlockPos pos, BlockState current, String fromRaw) {
        if (!BlockFilters.canExchange(level, pos, current)) {
            return false;
        }
        java.util.List<com.wrights.blueprint.ShapeMaterials.Entry> from =
                com.wrights.blueprint.ShapeMaterials.parse(fromRaw);
        if (from.isEmpty()) {
            return true;
        }
        net.minecraft.world.item.Item item = current.getBlock().asItem();
        if (!(item instanceof net.minecraft.world.item.BlockItem)) {
            return false;
        }
        for (com.wrights.blueprint.ShapeMaterials.Entry entry : from) {
            if (entry != null && entry.item() == item) {
                return true;
            }
        }
        return false;
    }
}
