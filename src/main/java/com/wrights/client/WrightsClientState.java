package com.wrights.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BoxEdits;
import com.wrights.blueprint.BlueprintClientFlags;
import com.wrights.item.ControllerItem;
import com.wrights.network.BlueprintPayloads;

public final class WrightsClientState {
    public enum Mode {
        NONE,
        CAPTURE,
        SAVING,
        BUILD,
        JOBS
    }

    public static Mode mode = Mode.NONE;
    public static BlockPos cornerA;
    public static BlockPos cornerB;
    public static String name = "Blueprint";
    public static UUID editingId;
    public static int generation;
    public static int seq;
    public static List<BlueprintPayloads.Summary> library = List.of();
    public static List<BlueprintPayloads.FolderLine> folders = List.of();
    public static Set<UUID> openFolders = Set.of();
    public static boolean busy;
    public static List<BlueprintPayloads.JobLine> jobs = List.of();
    public static boolean libraryKnown;
    /** False = this world's library. True = shared AppData / global library. */
    public static boolean libraryGlobal;
    public static UUID selected;
    public static UUID buildId;
    public static String buildName = "";
    public static int fullX = 1;
    public static int fullY = 1;
    public static int fullZ = 1;
    public static int viewX = 1;
    public static int viewY = 1;
    public static int viewZ = 1;
    public static int blockCount;
    public static boolean deployChosen;
    public static int deployCount = 1;
    public static boolean bindFromMenu;
    public static final List<JobNotice> jobNotices = new ArrayList<>();
    public static boolean sampled;
    public static boolean concealed;
    public static boolean follow;
    public static boolean bindSupply;
    public static boolean supplyBound;
    public static BlockPos supplyPos;
    public static long supplyHighlightUntil;
    public static int digitalKind;
    public static boolean digitalOnline;
    public static boolean digitalBound;
    public static BlockPos digitalPos;
    public static String digitalDim = "";
    public static List<BlueprintPayloads.DigitalInfoLine> digitalLines = List.of();
    public static long digitalInfoAt;
    public static BlockPos anchor;
    public static int pivotX;
    public static int pivotY;
    public static int pivotZ;
    public static List<Ghost> ghosts = List.of();
    public static List<Ghost> captureGhosts = List.of();
    public static boolean captureConcealed;
    public static List<Ghost> editGhosts = List.of();
    public static UUID editId;
    public static String editName = "";
    public static int editX = 1;
    public static int editY = 1;
    public static int editZ = 1;
    public static int editBlocks;
    public static boolean previewSampled;
    public static boolean previewConcealed;
    public static int editPivotX;
    public static int editPivotY;
    public static int editPivotZ;
    public static int buildCooldown;
    public enum CaptureStyle {
        CORNERS,
        BOX,
        MOVE
    }

    public static CaptureStyle captureStyle = CaptureStyle.CORNERS;
    public static boolean boxDragging;
    public static boolean moveGrabbed;
    public static int styleHudTicks;
    public static boolean styleAnnounce;
    public static boolean mainPicker;
    private static int pickCooldown;
    private static int boxSlot = -1;
    private static int moveSlot = -1;
    private static int moveOffX;
    private static int moveOffY;
    private static int moveOffZ;
    private static int moveSizeX = 1;
    private static int moveSizeY = 1;
    private static int moveSizeZ = 1;
    private static BlockPos moveOriginA;
    private static BlockPos moveOriginB;

    private WrightsClientState() {
    }

    public record Ghost(int x, int y, int z, float r, float g, float b, int stateId) {
    }

    public record ActiveBuild(BlockPos origin, int clipX, int clipY, int clipZ, List<Ghost> ghosts) {
    }

    public static final Map<UUID, ActiveBuild> activeBuilds = new LinkedHashMap<>();
    public static final Set<Long> activeCells = new HashSet<>();
    public static final Set<UUID> markedJobs = new HashSet<>();
    private static final Map<UUID, Pace> paces = new HashMap<>();

    public static void applyJobHologram(BlueprintPayloads.JobHologram payload) {
        if (payload.clear()) {
            if (payload.jobId().equals(Util.NIL_UUID)) {
                activeBuilds.clear();
            } else {
                activeBuilds.remove(payload.jobId());
            }
        } else if (finishedJob(payload.jobId())) {
            activeBuilds.remove(payload.jobId());
        } else {
            List<Ghost> ghosts = new ArrayList<>();
            for (BlueprintPayloads.Bit bit : payload.bits()) {
                BlockState state = Block.stateById(bit.stateId());
                if (state.isAir()) {
                    continue;
                }
                ghosts.add(new Ghost(Short.toUnsignedInt(bit.x()), Short.toUnsignedInt(bit.y()), Short.toUnsignedInt(bit.z()), 1.0F, 1.0F, 1.0F, bit.stateId()));
            }
            activeBuilds.put(payload.jobId(), new ActiveBuild(payload.origin(), payload.clipX(), payload.clipY(), payload.clipZ(), ghosts));
        }
        rebuildActiveCells();
    }

    public static void applyJobs(BlueprintPayloads.JobSync payload) {
        jobs = List.copyOf(payload.jobs());
        busy = false;
        long now = System.currentTimeMillis();
        Set<UUID> seen = new HashSet<>();
        for (BlueprintPayloads.JobLine job : jobs) {
            seen.add(job.id());
            if (!job.finished()) {
                busy = true;
            }
            notePace(job, now);
        }
        paces.keySet().retainAll(seen);
        markedJobs.retainAll(seen);
        if (activeBuilds.keySet().removeIf(WrightsClientState::finishedJob)) {
            rebuildActiveCells();
        }
    }

    private static boolean finishedJob(UUID id) {
        for (BlueprintPayloads.JobLine job : jobs) {
            if (job.id().equals(id)) {
                return job.finished();
            }
        }
        return false;
    }

    private static void rebuildActiveCells() {
        activeCells.clear();
        for (ActiveBuild build : activeBuilds.values()) {
            for (Ghost ghost : build.ghosts()) {
                if (ghost.x() >= build.clipX() || ghost.y() >= build.clipY() || ghost.z() >= build.clipZ()) {
                    continue;
                }
                activeCells.add(BlockPos.asLong(build.origin().getX() + ghost.x(), build.origin().getY() + ghost.y(), build.origin().getZ() + ghost.z()));
            }
        }
    }

    public static void toggleMark(UUID id) {
        if (!markedJobs.add(id)) {
            markedJobs.remove(id);
        }
    }

    public static int percent(BlueprintPayloads.JobLine job) {
        int total = Math.max(1, job.total());
        return Mth.clamp(job.done() * 100 / total, 0, 100);
    }

    public static String timeLeft(BlueprintPayloads.JobLine job) {
        if (job.finished() || job.paused()) {
            return "";
        }
        Pace pace = paces.get(job.id());
        if (pace == null || pace.shownSeconds < 0) {
            return "";
        }
        double seconds = pace.shownSeconds - (System.currentTimeMillis() - pace.shownAt) / 1000.0D;
        if (seconds < 1) {
            return "1s";
        }
        return formatSeconds((long) Math.ceil(seconds));
    }

    private static String formatSeconds(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 10) {
            return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        long min = minutes % 60;
        return min == 0 ? hours + "h" : hours + "h " + min + "m";
    }

    private static void notePace(BlueprintPayloads.JobLine job, long now) {
        Pace pace = paces.computeIfAbsent(job.id(), id -> new Pace());
        if (job.finished() || job.done() < pace.done) {
            pace.samples.clear();
            pace.shownSeconds = -1;
            pace.done = job.done();
            pace.shownAt = now;
            return;
        }
        if (job.paused()) {
            pace.samples.clear();
            pace.shownAt = now;
            pace.done = job.done();
            return;
        }
        pace.samples.addLast(new Sample(now, job.done()));
        long keep = now - 15_000L;
        while (pace.samples.size() > 2 && pace.samples.peekFirst().at < keep) {
            pace.samples.removeFirst();
        }
        pace.done = job.done();
        double rate = paceRate(pace, job);
        if (rate <= 0.02D) {
            return;
        }
        int left = Math.max(0, job.total() - job.done());
        double raw = left / rate;
        if (pace.shownSeconds < 0) {
            pace.shownSeconds = raw;
            pace.shownAt = now;
            return;
        }
        double aged = Math.max(0, pace.shownSeconds - (now - pace.shownAt) / 1000.0D);
        pace.shownAt = now;
        double error = raw - aged;
        double deadband = Math.max(3.0D, aged * 0.1D);
        if (Math.abs(error) <= deadband) {
            pace.shownSeconds = aged;
            return;
        }
        double delta = error * 0.4D;
        double cap = Math.max(3.0D, raw * 0.1D);
        if (delta > cap) {
            delta = cap;
        } else if (delta < -cap) {
            delta = -cap;
        }
        pace.shownSeconds = Math.max(0, aged + delta);
    }

    private static double paceRate(Pace pace, BlueprintPayloads.JobLine job) {
        double lifetime = 0;
        double worked = job.workedTicks() / 20.0D;
        if (worked >= 8.0D && job.done() > 0) {
            lifetime = job.done() / worked;
        }
        double window = 0;
        if (pace.samples.size() >= 2) {
            Sample first = pace.samples.peekFirst();
            Sample last = pace.samples.peekLast();
            double span = (last.at - first.at) / 1000.0D;
            int gained = last.done - first.done;
            if (span >= 8.0D && gained > 0) {
                window = gained / span;
            }
        }
        if (window > 0 && lifetime > 0) {
            return lifetime * 0.75D + window * 0.25D;
        }
        return window > 0 ? window : lifetime;
    }

    private static final class Sample {
        private final long at;
        private final int done;

        private Sample(long at, int done) {
            this.at = at;
            this.done = done;
        }
    }

    private static final class Pace {
        private int done;
        private long shownAt;
        private double shownSeconds = -1;
        private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    }

    public static void updateFlags() {
        BlueprintClientFlags.capture = mode == Mode.CAPTURE || mode == Mode.SAVING;
        BlueprintClientFlags.build = mode == Mode.BUILD;
        BlueprintClientFlags.suppressController = BlueprintClientFlags.capture || BlueprintClientFlags.build;
        rememberMode();
    }

    private static int rememberedMode = -1;
    private static int rememberedStyle = -1;
    private static String rememberedBuild = "";

    private static void rememberMode() {
        int modeId = mode == Mode.CAPTURE ? 1 : mode == Mode.BUILD ? 2 : mode == Mode.JOBS ? 3 : 0;
        if (modeId == 0) {
            return;
        }
        String id = rememberedBuild;
        if (mode == Mode.BUILD && buildId != null) {
            id = buildId.toString();
        } else if (id.isEmpty()) {
            String stored = com.wrights.WrightsClientConfig.LAST_BUILD.get();
            id = stored == null ? "" : stored;
        }
        int style = captureStyle.ordinal();
        if (modeId == rememberedMode && style == rememberedStyle && id.equals(rememberedBuild)) {
            return;
        }
        rememberedMode = modeId;
        rememberedStyle = style;
        rememberedBuild = id;
        com.wrights.WrightsClientConfig.setLastMode(modeId, style, id);
    }

    public static void prepareJoin() {
        mode = Mode.NONE;
        cornerA = null;
        cornerB = null;
        editingId = null;
        buildId = null;
        ghosts = List.of();
        follow = false;
        boxDragging = false;
        moveGrabbed = false;
        generation = -1;
        seq = -1;
        int style = com.wrights.WrightsClientConfig.LAST_STYLE.get();
        CaptureStyle[] styles = CaptureStyle.values();
        captureStyle = styles[Math.max(0, Math.min(styles.length - 1, style))];
        resumeSent = false;
        updateFlags();
    }

    public static void releaseSelection() {
        cornerA = null;
        cornerB = null;
        boxDragging = false;
        moveGrabbed = false;
        clearCapturePreview();
    }

    public static void showCapturePreview(List<Ghost> ghosts, boolean concealed) {
        captureGhosts = ghosts == null ? List.of() : ghosts;
        captureConcealed = concealed;
    }

    public static void clearCapturePreview() {
        captureGhosts = List.of();
        captureConcealed = false;
    }

    private static boolean resumeSent;

    public static void forgetMode() {
        WrightsClientConfig.setLastMode(0, captureStyle.ordinal(), "");
        rememberedMode = 0;
        rememberedBuild = "";
    }

    public static void noteUnequipped() {
        resumeSent = false;
    }

    public static void tryResume() {
        if (mode != Mode.NONE || resumeSent) {
            return;
        }
        int last = com.wrights.WrightsClientConfig.LAST_MODE.get();
        if (last == 3) {
            resumeSent = true;
            enterJobs();
            return;
        }
        if (last != 1 && last != 2) {
            return;
        }
        resumeSent = true;
        java.util.UUID id = net.minecraft.Util.NIL_UUID;
        String raw = com.wrights.WrightsClientConfig.LAST_BUILD.get();
        if (raw != null && !raw.isBlank()) {
            try {
                id = java.util.UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                id = net.minecraft.Util.NIL_UUID;
            }
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.wrights.network.BlueprintPayloads.Resume(last, id));
    }

    public static void beginCaptureLocal() {
        mode = Mode.CAPTURE;
        cornerA = null;
        cornerB = null;
        name = com.wrights.blueprint.BlueprintActions.suggestName(libraryNames());
        editingId = null;
        ghosts = List.of();
        follow = false;
        boxDragging = false;
        moveGrabbed = false;
        styleHudTicks = 70;
        updateFlags();
    }

    private static List<String> libraryNames() {
        List<String> names = new ArrayList<>();
        for (com.wrights.network.BlueprintPayloads.Summary summary : library) {
            names.add(summary.name());
        }
        return names;
    }

    public static void enterJobs() {
        leaveCapture();
        mode = Mode.JOBS;
        bindSupply = false;
        updateFlags();
    }

    public static void enterNewMode() {
        if (mode == Mode.CAPTURE || mode == Mode.SAVING) {
            return;
        }
        beginCaptureLocal();
        PacketDistributor.sendToServer(new BlueprintPayloads.StartCapture());
    }

    public static void enterBuildFromLast() {
        if (mode == Mode.BUILD && buildId != null) {
            return;
        }
        UUID id = lastBlueprintId();
        BlueprintPayloads.Summary known = summary(id);
        if (known != null) {
            armBuild(known);
            PacketDistributor.sendToServer(new BlueprintPayloads.BeginBuild(known.id()));
            return;
        }
        mode = Mode.BUILD;
        buildId = null;
        buildName = "";
        ghosts = List.of();
        follow = false;
        bindSupply = false;
        cornerA = null;
        cornerB = null;
        boxDragging = false;
        moveGrabbed = false;
        clearCapturePreview();
        updateFlags();
        UUID send = id != null && !libraryKnown ? id : Util.NIL_UUID;
        PacketDistributor.sendToServer(new BlueprintPayloads.BeginBuild(send));
    }

    public static void cycleMainMode(int delta) {
        int index = switch (mode) {
            case CAPTURE, SAVING, BUILD -> 0;
            case JOBS -> 1;
            default -> delta > 0 ? -1 : 2;
        };
        int next = Math.floorMod(index + delta, 2);
        if (next == 0) {
            enterBuildFromLast();
            bindSupply = false;
        } else {
            enterJobs();
        }
        mainPicker = true;
        styleHudTicks = 70;
        styleAnnounce = true;
    }

    private static UUID lastBlueprintId() {
        UUID saved = parseId(rememberedBuild);
        if (saved == null) {
            saved = parseId(com.wrights.WrightsClientConfig.LAST_BUILD.get());
        }
        if (saved != null && (!libraryKnown || summary(saved) != null)) {
            return saved;
        }
        UUID best = null;
        long bestTime = 0L;
        for (BlueprintPayloads.Summary entry : library) {
            if (entry.lastUsed() > bestTime) {
                bestTime = entry.lastUsed();
                best = entry.id();
            }
        }
        return best;
    }

    private static BlueprintPayloads.Summary summary(UUID id) {
        if (id == null) {
            return null;
        }
        for (BlueprintPayloads.Summary entry : library) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private static UUID parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(raw);
            return id.equals(Util.NIL_UUID) ? null : id;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static BlueprintPayloads.JobLine jobAimed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        String here = minecraft.level.dimension().location().toString();
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(WrightsConfig.MAX_RANGE.getAsInt()));
        BlueprintPayloads.JobLine best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlueprintPayloads.JobLine job : jobs) {
            if (job.finished() || job.saving() || !here.equals(job.dimension())) {
                continue;
            }
            BlockPos origin = job.origin();
            AABB box = new AABB(
                    origin.getX(),
                    origin.getY(),
                    origin.getZ(),
                    origin.getX() + Math.max(1, job.sizeX()),
                    origin.getY() + Math.max(1, job.sizeY()),
                    origin.getZ() + Math.max(1, job.sizeZ()));
            var hit = box.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = hit.get().distanceToSqr(eye);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = job;
            }
        }
        return best;
    }

    private static void leaveCapture() {
        if (mode != Mode.CAPTURE && mode != Mode.SAVING) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        cornerA = null;
        cornerB = null;
        editingId = null;
        boxDragging = false;
        moveGrabbed = false;
    }

    public static void markSaving() {
        mode = Mode.SAVING;
        updateFlags();
    }

    public static void clearLocal() {
        mode = Mode.NONE;
        cornerA = null;
        cornerB = null;
        editingId = null;
        buildId = null;
        ghosts = List.of();
        clearCapturePreview();
        follow = false;
        boxDragging = false;
        moveGrabbed = false;
        updateFlags();
    }

    public static void armBuild(BlueprintPayloads.Summary summary) {
        mode = Mode.BUILD;
        buildId = summary.id();
        buildName = summary.name();
        fullX = Math.max(1, summary.sizeX());
        fullY = Math.max(1, summary.sizeY());
        fullZ = Math.max(1, summary.sizeZ());
        viewX = fullX;
        viewY = fullY;
        viewZ = fullZ;
        blockCount = summary.blocks();
        sampled = false;
        concealed = false;
        ghosts = List.of();
        follow = true;
        anchor = lookTarget();
        pivotX = 0;
        pivotY = 0;
        pivotZ = 0;
        cornerA = null;
        cornerB = null;
        updateFlags();
    }

    public static void applyLibrary(BlueprintPayloads.LibrarySync payload) {
        library = List.copyOf(payload.entries());
        folders = List.copyOf(payload.folders());
        openFolders = Set.copyOf(payload.open());
        busy = payload.busy();
        libraryGlobal = payload.global();
        libraryKnown = true;
        if (selected != null && library.stream().noneMatch(entry -> entry.id().equals(selected))
                && folders.stream().noneMatch(folder -> folder.id().equals(selected))) {
            selected = null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WrightsMenuScreen menu) {
            menu.refresh();
        }
    }

    public static void setLibraryScope(boolean global) {
        if (libraryGlobal == global && libraryKnown) {
            return;
        }
        libraryGlobal = global;
        libraryKnown = false;
        selected = null;
        PacketDistributor.sendToServer(new BlueprintPayloads.LibraryRequest(global));
    }

    public static void setOpenLocal(UUID id, boolean open) {
        Set<UUID> next = new HashSet<>(openFolders);
        if (open) {
            next.add(id);
        } else {
            next.remove(id);
        }
        openFolders = Set.copyOf(next);
    }

    public static void toggleFavoriteLocal(UUID id) {
        List<BlueprintPayloads.Summary> next = new ArrayList<>(library.size());
        for (BlueprintPayloads.Summary summary : library) {
            next.add(summary.id().equals(id) ? summary.withFavorite(!summary.favorite()) : summary);
        }
        library = List.copyOf(next);
    }

    public static void applyCapture(BlueprintPayloads.CaptureSync payload) {
        Mode previous = mode;
        int previousGeneration = generation;
        boolean hadBoth = cornerA != null && cornerB != null;
        Mode incoming = modeOf(payload.mode());
        boolean apply = payload.generation() > generation || (payload.generation() == generation && payload.seq() >= seq);
        if (apply) {
            boolean keepLive = boxDragging && incoming == Mode.CAPTURE && payload.hasA() && !payload.hasB();
            BlockPos liveB = cornerB;
            generation = payload.generation();
            seq = payload.seq();
            name = payload.name();
            editingId = payload.hasEdit() ? payload.editId() : null;
            cornerA = payload.hasA() ? payload.cornerA() : null;
            cornerB = payload.hasB() ? payload.cornerB() : null;
            if (keepLive && liveB != null) {
                cornerB = liveB;
            }
            mode = incoming;
            if (incoming != Mode.CAPTURE) {
                boxDragging = false;
            } else if (previous != Mode.CAPTURE) {
                styleHudTicks = Math.max(styleHudTicks, 70);
            }
            if (incoming != Mode.BUILD) {
                buildId = null;
                ghosts = List.of();
                concealed = false;
                follow = false;
            }
            if (incoming != Mode.CAPTURE && incoming != Mode.SAVING) {
                clearCapturePreview();
            }
        }
        updateFlags();
        Minecraft minecraft = Minecraft.getInstance();
        boolean hasBoth = cornerA != null && cornerB != null;
        if (apply && !boxDragging && mode == Mode.CAPTURE && hasBoth && (!hadBoth || payload.generation() > previousGeneration)) {
            if (!(minecraft.screen instanceof BlueprintAdjustScreen)) {
                minecraft.setScreen(new BlueprintAdjustScreen());
            }
        }
        if (previous == Mode.SAVING && mode == Mode.CAPTURE && hasBoth && minecraft.screen == null) {
            minecraft.setScreen(new BlueprintAdjustScreen());
        }
        if (minecraft.screen instanceof BlueprintAdjustScreen adjust) {
            adjust.refresh();
        }
        if (minecraft.screen instanceof WrightsMenuScreen menu) {
            menu.refresh();
        }
    }

    public static void applyPreview(BlueprintPayloads.Preview payload) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Ghost> parsed = new ArrayList<>();
        for (BlueprintPayloads.Bit bit : payload.bits()) {
            BlockState state = Block.stateById(bit.stateId());
            if (state.isAir()) {
                continue;
            }
            int rgb = 0x8AA0B8;
            if (minecraft.level != null && minecraft.player != null) {
                int color = state.getMapColor(minecraft.level, minecraft.player.blockPosition()).col;
                if (color != 0) {
                    rgb = color;
                }
            }
            parsed.add(new Ghost(
                    Short.toUnsignedInt(bit.x()),
                    Short.toUnsignedInt(bit.y()),
                    Short.toUnsignedInt(bit.z()),
                    channel(rgb, 16),
                    channel(rgb, 8),
                    channel(rgb, 0),
                    bit.stateId()));
        }
        editGhosts = parsed;
        editId = payload.id();
        editName = payload.name();
        editX = Math.max(1, payload.sizeX());
        editY = Math.max(1, payload.sizeY());
        editZ = Math.max(1, payload.sizeZ());
        editBlocks = payload.blocks();
        previewSampled = payload.sampled();
        previewConcealed = payload.concealed();
        editPivotX = clamp(payload.anchorX(), editX);
        editPivotY = clamp(payload.anchorY(), editY);
        editPivotZ = clamp(payload.anchorZ(), editZ);
        if (payload.forBuild()) {
            boolean same = mode == Mode.BUILD && payload.id().equals(buildId);
            boolean sizeChanged = fullX != editX || fullY != editY || fullZ != editZ;
            mode = Mode.BUILD;
            buildId = payload.id();
            buildName = payload.name();
            fullX = editX;
            fullY = editY;
            fullZ = editZ;
            blockCount = payload.blocks();
            sampled = payload.sampled();
            concealed = payload.concealed();
            ghosts = parsed;
            pivotX = editPivotX;
            pivotY = editPivotY;
            pivotZ = editPivotZ;
            if (!same || sizeChanged) {
                viewX = fullX;
                viewY = fullY;
                viewZ = fullZ;
            }
            if (!same) {
                follow = true;
                anchor = lookTarget();
                deployChosen = false;
                WrightsClientConfig.setPlaceFluids(payload.placeFluids());
                WrightsClientConfig.setVoidBlocks(payload.voidBlocks());
                WrightsClientConfig.setVoidFluids(payload.voidFluids());
                WrightsClientConfig.setVoidNbt(payload.voidNbt());
            }
            cornerA = null;
            cornerB = null;
        }
        updateFlags();
        if (payload.openEditor()) {
            if (!(minecraft.screen instanceof BlueprintEditScreen)) {
                minecraft.setScreen(new BlueprintEditScreen());
            }
        } else if (payload.forBuild() && (minecraft.screen instanceof WrightsMenuScreen || minecraft.screen instanceof BlueprintAdjustScreen)) {
            minecraft.setScreen(null);
        }
        if (minecraft.screen instanceof WrightsMenuScreen menu) {
            menu.refresh();
        }
        if (minecraft.screen instanceof BlueprintEditScreen editor) {
            editor.refresh();
        }
        if (minecraft.screen instanceof PlacementScreen placement) {
            placement.refresh();
        }
    }

    public static int placeableBlocks() {
        if (blockCount <= 0) {
            return 0;
        }
        boolean smaller = viewX < fullX || viewY < fullY || viewZ < fullZ;
        if (!smaller || sampled || ghosts.isEmpty()) {
            return blockCount;
        }
        int inside = 0;
        for (Ghost ghost : ghosts) {
            if (ghost.x() < viewX && ghost.y() < viewY && ghost.z() < viewZ) {
                inside++;
            }
        }
        return inside > 0 ? inside : blockCount;
    }

    public static void showJobDone(String name, int seconds) {
        String title = name == null || name.isBlank() ? "Build" : name;
        jobNotices.add(new JobNotice(title, Math.max(0, seconds)));
        while (jobNotices.size() > 3) {
            jobNotices.remove(0);
        }
    }

    public static void applyDeploy() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        com.wrights.drone.Swarm swarm = com.wrights.drone.Wrightports.swarm(minecraft.player);
        int next = WrightsClientConfig.deployTarget(swarm.units().size(), placeableBlocks());
        if (next <= 0 || next == swarm.assignCount()) {
            return;
        }
        com.wrights.drone.Wrightports.replaceWorn(minecraft.player, swarm.withAssign(next));
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAssign(next));
    }

    public static int deployCap() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        int free = com.wrights.drone.Wrightports.swarm(minecraft.player).units().size();
        return Math.min(free, Math.min(Math.max(0, placeableBlocks()), WrightsConfig.maxWrightsPerJob()));
    }

    /** Keeps this job's wright count inside what the port and the blueprint can use. */
    public static void clampDeploy() {
        int cap = deployCap();
        if (cap <= 0) {
            deployCount = 0;
            return;
        }
        if (!deployChosen) {
            Minecraft minecraft = Minecraft.getInstance();
            int free = minecraft.player == null ? 0 : com.wrights.drone.Wrightports.swarm(minecraft.player).units().size();
            deployCount = WrightsClientConfig.deployTarget(free, placeableBlocks());
        }
        deployCount = Mth.clamp(Math.max(1, deployCount), 1, cap);
    }

    public static int stepped(int current, int direction, int min, int max) {
        if (max < min) {
            return min;
        }
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        boolean alt = net.minecraft.client.gui.screens.Screen.hasAltDown();
        boolean control = net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (control || (shift && alt)) {
            return direction > 0 ? max : min;
        }
        int step = alt ? 64 : shift ? 16 : 1;
        return Mth.clamp(current + Integer.signum(direction) * step, min, max);
    }

    public static void finishSupplyBind(BlockPos pos) {
        supplyBound = true;
        supplyPos = pos.immutable();
        PacketDistributor.sendToServer(new BlueprintPayloads.BindSupply(pos, false));
        if (!bindFromMenu) {
            return;
        }
        bindSupply = false;
        bindFromMenu = false;
        Minecraft.getInstance().setScreen(new PlacementScreen());
    }

    public static boolean tryBuild() {
        return tryBuild(false);
    }

    public static boolean tryBuild(boolean force) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ControllerItem.isHeld(minecraft.player)) {
            return false;
        }
        if (mode != Mode.BUILD || buildId == null || anchor == null || buildCooldown > 0) {
            return false;
        }
        clampDeploy();
        buildCooldown = 2;
        follow = true;
        PacketDistributor.sendToServer(new BlueprintPayloads.LaunchBuild(
                buildId,
                anchor,
                viewX,
                viewY,
                viewZ,
                pivotX,
                pivotY,
                pivotZ,
                Math.max(0, deployCount),
                WrightsClientConfig.PLACE_FLUIDS.get(),
                WrightsClientConfig.VOID_BLOCKS.get(),
                WrightsClientConfig.VOID_FLUIDS.get(),
                WrightsClientConfig.VOID_NBT.get(),
                force,
                WrightsClientConfig.SUPPLY_PLAYER_FIRST.get()));
        return true;
    }

    public static void stepAnchor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (anchor == null || minecraft.player == null) {
            return;
        }
        follow = false;
        net.minecraft.core.Direction facing = minecraft.player.getDirection();
        anchor = anchor.offset(facing.getStepX() * Math.max(1, viewX), 0, facing.getStepZ() * Math.max(1, viewZ));
    }

    public static void tickFollow() {
        if (buildCooldown > 0) {
            buildCooldown--;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (mode == Mode.BUILD && follow && minecraft.player != null && ControllerItem.isHeld(minecraft.player)
                && (minecraft.screen == null || minecraft.screen instanceof PlacementScreen)) {
            anchor = lookTarget();
        }
    }

    public static void tickCapture() {
        if (!jobNotices.isEmpty()) {
            jobNotices.removeIf(notice -> --notice.ticks <= 0);
        }
        if (pickCooldown > 0) {
            pickCooldown--;
        }
        if (styleHudTicks > 0) {
            styleHudTicks--;
        } else {
            styleAnnounce = false;
        }
        if (moveGrabbed) {
            tickMove();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!boxDragging) {
            return;
        }
        if (minecraft.player == null || minecraft.screen != null || mode != Mode.CAPTURE || captureStyle != CaptureStyle.BOX
                || !ControllerItem.isHeld(minecraft.player) || minecraft.player.getInventory().selected != boxSlot || cornerA == null) {
            releaseBoxDrag();
            return;
        }
        cornerB = aimCorner(cornerA);
    }

    public static void cycleStyle(int direction) {
        mainPicker = false;
        CaptureStyle[] values = CaptureStyle.values();
        int next = captureStyle.ordinal();
        for (int step = 0; step < values.length; step++) {
            next = Math.floorMod(next + direction, values.length);
            if (values[next] != CaptureStyle.MOVE || hasBox()) {
                break;
            }
        }
        if (values[next] == captureStyle) {
            styleHudTicks = 70;
            styleAnnounce = true;
            return;
        }
        if (moveGrabbed) {
            moveGrabbed = false;
            sendUpdate();
        }
        releaseBoxDrag();
        captureStyle = values[next];
        styleHudTicks = 70;
        styleAnnounce = true;
        rememberMode();
    }

    public static void cycleBuildTool(int direction) {
        mainPicker = false;
        if (mode == Mode.SAVING) {
            styleHudTicks = 70;
            styleAnnounce = true;
            return;
        }
        int styles = styleSteps();
        int count = 2 + styles;
        int next = Math.floorMod(buildToolSlot() + direction, count);
        if (next == 0) {
            selectBuildTool(false);
        } else if (next == count - 1) {
            selectBuildTool(true);
        } else {
            selectNewStyle(styleAt(next - 1));
        }
        styleHudTicks = 70;
        styleAnnounce = true;
    }

    private static int styleSteps() {
        return hasBox() ? CaptureStyle.values().length : CaptureStyle.values().length - 1;
    }

    private static CaptureStyle styleAt(int offset) {
        if (offset <= 0) {
            return CaptureStyle.CORNERS;
        }
        if (offset == 1) {
            return CaptureStyle.BOX;
        }
        return CaptureStyle.MOVE;
    }

    /** Use template, each capture style under New template, then Bind inventory. */
    private static int buildToolSlot() {
        if (mode == Mode.CAPTURE) {
            int offset = switch (captureStyle) {
                case BOX -> 1;
                case MOVE -> hasBox() ? 2 : 0;
                case CORNERS -> 0;
            };
            return 1 + Math.min(offset, styleSteps() - 1);
        }
        if (mode == Mode.BUILD && bindSupply) {
            return 1 + styleSteps();
        }
        return 0;
    }

    private static void selectBuildTool(boolean bind) {
        if (mode != Mode.BUILD) {
            boxDragging = false;
            moveGrabbed = false;
            enterBuildFromLast();
        }
        bindSupply = bind;
        if (bind) {
            bindFromMenu = false;
        }
    }

    private static void selectNewStyle(CaptureStyle style) {
        if (mode != Mode.CAPTURE) {
            enterNewMode();
        }
        if (mode != Mode.CAPTURE || style == captureStyle) {
            return;
        }
        if (moveGrabbed) {
            moveGrabbed = false;
            sendUpdate();
        }
        releaseBoxDrag();
        captureStyle = style;
        rememberMode();
    }

    public static boolean supplyHighlighting() {
        return supplyPos != null && System.currentTimeMillis() < supplyHighlightUntil;
    }

    public static void applySupply(BlueprintPayloads.SupplySync payload) {
        supplyBound = payload.chest();
        supplyPos = payload.chest() ? payload.pos() : null;
        if (!supplyBound) {
            supplyHighlightUntil = 0L;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PlacementScreen placement) {
            placement.refresh();
        }
    }

    public static void applyDigital(BlueprintPayloads.DigitalSync payload) {
        digitalKind = payload.kind();
        digitalOnline = payload.online();
        digitalBound = payload.kind() != com.wrights.storage.DigitalLink.Kind.NONE.ordinal();
        digitalPos = digitalBound ? payload.pos() : null;
        digitalDim = payload.dim() == null ? "" : payload.dim();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WrightsMenuScreen menu) {
            menu.refreshDigital();
        }
    }

    public static void applyDigitalInfo(BlueprintPayloads.DigitalInfoSync payload) {
        applyDigital(new BlueprintPayloads.DigitalSync(payload.kind(), payload.online(), payload.pos(), payload.dim()));
        digitalLines = payload.lines() == null ? List.of() : List.copyOf(payload.lines());
        digitalInfoAt = System.currentTimeMillis();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WrightsMenuScreen menu) {
            menu.refreshDigital();
        }
    }

    public static void openUndoPrompt(BlueprintPayloads.UndoPrompt payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new UndoConfirmScreen(payload.id(), payload.extra(), payload.drop(), minecraft.screen));
    }

    public static void onCaptureClick(BlockPos clicked, boolean shift) {
        if (mode != Mode.CAPTURE || pickCooldown > 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ControllerItem.isHeld(minecraft.player)) {
            return;
        }
        pickCooldown = 4;
        if (captureStyle == CaptureStyle.MOVE) {
            if (!hasBox()) {
                return;
            }
            if (shift) {
                cancelMove();
                return;
            }
            if (moveGrabbed) {
                moveGrabbed = false;
                sendUpdate();
                minecraft.setScreen(new BlueprintAdjustScreen());
                return;
            }
            if (grabMove()) {
                if (minecraft.screen instanceof BlueprintAdjustScreen) {
                    minecraft.setScreen(null);
                }
            }
            return;
        }
        if (captureStyle == CaptureStyle.BOX) {
            if (shift) {
                releaseBoxDrag();
                return;
            }
            if (!boxDragging) {
                if (cornerA != null && cornerB == null) {
                    boxDragging = true;
                    boxSlot = minecraft.player.getInventory().selected;
                    cornerB = aimCorner(cornerA);
                    return;
                }
                BlockPos pos = clicked != null ? clicked : aimCorner(null);
                cornerA = pos;
                cornerB = pos;
                boxDragging = true;
                boxSlot = minecraft.player.getInventory().selected;
                PacketDistributor.sendToServer(new BlueprintPayloads.CapturePick(pos, BlueprintPayloads.CapturePick.BOX_BEGIN));
                return;
            }
            BlockPos pos = cornerB != null ? cornerB : aimCorner(cornerA);
            cornerB = pos;
            boxDragging = false;
            PacketDistributor.sendToServer(new BlueprintPayloads.CapturePick(pos, BlueprintPayloads.CapturePick.BOX_COMMIT));
            return;
        }
        if (clicked == null) {
            return;
        }
        int action = shift ? BlueprintPayloads.CapturePick.SECOND : BlueprintPayloads.CapturePick.CORNER;
        PacketDistributor.sendToServer(new BlueprintPayloads.CapturePick(clicked, action));
    }

    public static boolean hasBox() {
        return cornerA != null && cornerB != null;
    }

    private static boolean grabMove() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !hasBox()) {
            return false;
        }
        AABB box = selection(cornerA, cornerB).inflate(0.35D);
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(WrightsConfig.MAX_RANGE.getAsInt()));
        Vec3 hit = box.clip(eye, end).orElse(null);
        if (hit == null) {
            return false;
        }
        BlockPos min = BoxEdits.min(cornerA, cornerB);
        BlockPos handle = BlockPos.containing(hit);
        moveOffX = handle.getX() - min.getX();
        moveOffY = handle.getY() - min.getY();
        moveOffZ = handle.getZ() - min.getZ();
        moveSizeX = BoxEdits.span(cornerA, cornerB, 0);
        moveSizeY = BoxEdits.span(cornerA, cornerB, 1);
        moveSizeZ = BoxEdits.span(cornerA, cornerB, 2);
        moveOriginA = cornerA;
        moveOriginB = cornerB;
        moveSlot = minecraft.player.getInventory().selected;
        moveGrabbed = true;
        return true;
    }

    private static void tickMove() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || mode != Mode.CAPTURE || captureStyle != CaptureStyle.MOVE
                || !ControllerItem.isHeld(minecraft.player) || minecraft.player.getInventory().selected != moveSlot || !hasBox()) {
            cancelMove();
            return;
        }
        BlockPos aim = aimFree(WrightsConfig.MAX_RANGE.getAsInt());
        BlockPos min = new BlockPos(aim.getX() - moveOffX, aim.getY() - moveOffY, aim.getZ() - moveOffZ);
        BlockPos max = min.offset(moveSizeX - 1, moveSizeY - 1, moveSizeZ - 1);
        if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(max) || !BoxEdits.fits(min, max)) {
            return;
        }
        if (min.equals(BoxEdits.min(cornerA, cornerB)) && max.equals(BoxEdits.max(cornerA, cornerB))) {
            return;
        }
        cornerA = min;
        cornerB = max;
        sendUpdate();
    }

    private static void cancelMove() {
        moveGrabbed = false;
        if (moveOriginA != null && moveOriginB != null && mode == Mode.CAPTURE) {
            cornerA = moveOriginA;
            cornerB = moveOriginB;
            sendUpdate();
        }
        moveOriginA = null;
        moveOriginB = null;
    }

    private static AABB selection(BlockPos first, BlockPos second) {
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1.0D,
                Math.max(first.getY(), second.getY()) + 1.0D,
                Math.max(first.getZ(), second.getZ()) + 1.0D);
    }

    private static BlockPos aimFree(double range) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return BlockPos.ZERO;
        }
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 look = minecraft.player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.containing(eye.add(look.scale(12.0D)));
        int y = Mth.clamp(pos.getY(), minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight() - 1);
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    public static void releaseBoxDrag() {
        if (!boxDragging) {
            return;
        }
        boxDragging = false;
        cornerB = null;
    }

    private static BlockPos aimCorner(BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return BlockPos.ZERO;
        }
        double range = origin == null ? 8.0D : WrightsConfig.MAX_RANGE.getAsInt();
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(range));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.containing(hit.getLocation());
        if (origin != null) {
            int limit = Math.max(0, WrightsConfig.MAX_AXIS.getAsInt() - 1);
            pos = new BlockPos(
                    Mth.clamp(pos.getX(), origin.getX() - limit, origin.getX() + limit),
                    Mth.clamp(pos.getY(), origin.getY() - limit, origin.getY() + limit),
                    Mth.clamp(pos.getZ(), origin.getZ() - limit, origin.getZ() + limit));
        }
        int y = Mth.clamp(pos.getY(), minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight() - 1);
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    public static void applyFinished(BlueprintPayloads.CaptureFinished payload) {
        if (payload.saved() && !payload.id().equals(Util.NIL_UUID)) {
            selected = payload.id();
        }
        if (payload.stay()) {
            if (payload.saved() && !payload.id().equals(Util.NIL_UUID)) {
                editingId = payload.id();
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof BlueprintAdjustScreen adjust) {
                adjust.onSaveFinished(payload.saved());
            }
            return;
        }
        mode = Mode.NONE;
        cornerA = null;
        cornerB = null;
        editingId = null;
        boxDragging = false;
        moveGrabbed = false;
        clearCapturePreview();
        updateFlags();
        Minecraft.getInstance().setScreen(new WrightsMenuScreen());
    }

    public static void sendUpdate() {
        if (mode != Mode.CAPTURE) {
            return;
        }
        seq++;
        boolean hasA = cornerA != null;
        boolean hasB = cornerB != null;
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureUpdate(
                generation,
                seq,
                hasA,
                hasA ? cornerA : BlockPos.ZERO,
                hasB,
                hasB ? cornerB : BlockPos.ZERO,
                name == null ? com.wrights.blueprint.BlueprintActions.suggestName(libraryNames()) : name));
    }

    private static BlockPos solidA;
    private static BlockPos solidB;
    private static int solidCount = -1;

    public static int solidSelection() {
        if (cornerA == null || cornerB == null) {
            solidCount = -1;
            return 0;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0;
        }
        if (cornerA.equals(solidA) && cornerB.equals(solidB) && solidCount >= 0) {
            return solidCount;
        }
        solidA = cornerA;
        solidB = cornerB;
        solidCount = BoxEdits.solid(minecraft.level, cornerA, cornerB);
        return solidCount;
    }

    public static BlockPos lookTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player != null && minecraft.level != null) {
            double range = WrightsConfig.MAX_RANGE.getAsInt();
            Vec3 eye = player.getEyePosition(1.0F);
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
            BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(hit.getLocation()) <= range * range) {
                return hit.getBlockPos().relative(hit.getDirection());
            }
            return fallbackAnchor(player);
        }
        return BlockPos.ZERO;
    }

    public static BlockPos fallbackAnchor(Player player) {
        Direction facing = player.getDirection();
        int forward = WrightsClientConfig.PLACE_Z.get();
        int side = WrightsClientConfig.PLACE_X.get();
        int up = WrightsClientConfig.PLACE_Y.get();
        BlockPos pos = player.blockPosition();
        if (forward > 0) {
            pos = pos.relative(facing, forward);
        } else if (forward < 0) {
            pos = pos.relative(facing.getOpposite(), -forward);
        }
        Direction right = facing.getClockWise();
        if (side > 0) {
            pos = pos.relative(right, side);
        } else if (side < 0) {
            pos = pos.relative(right.getOpposite(), -side);
        }
        return pos.offset(0, up, 0);
    }

    private static Mode modeOf(int ordinal) {
        Mode[] values = Mode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Mode.NONE;
        }
        return values[ordinal];
    }

    private static int clamp(int value, int size) {
        if (size <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(size - 1, value));
    }

    private static float channel(int rgb, int shift) {
        return Math.min(1.0F, ((rgb >> shift) & 255) / 255.0F * 1.25F + 0.08F);
    }

    public static final class JobNotice {
        public final String name;
        public final int seconds;
        public int ticks = 100;

        public JobNotice(String name, int seconds) {
            this.name = name;
            this.seconds = seconds;
        }
    }
}
