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
import net.minecraft.network.chat.Component;
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
import com.wrights.drone.Swarm;
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
        JOBS,
        MANAGE,
        INFO,
        LINK
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
    /** Free wrights the server can see. -1 until the build screen has asked. */
    public static int poolFree = -1;
    /** Wrights stored nearby, including ones already out. -1 until the build screen has asked. */
    public static int poolStored = -1;
    public static boolean bindFromMenu;
    public static final List<JobNotice> jobNotices = new ArrayList<>();
    public static DockNotice dockNotice;
    public static boolean storedSettings;
    public static boolean storedAll;
    public static boolean sampled;
    public static boolean concealed;
    public static boolean follow;
    public static boolean bindSupply;
    /** Build-tool slot that sets the face a mirror is measured from. */
    public static boolean mirrorAim;
    /** Build-tool slot for Shape mode. */
    public static boolean shapeAim;

    /** True only while the mirror build-tool is the active tool in Build mode. */
    public static boolean mirrorToolActive() {
        return mode == Mode.BUILD && mirrorAim;
    }

    /** True only while the shape build-tool is the active tool in Build mode. */
    public static boolean shapeToolActive() {
        return mode == Mode.BUILD && shapeAim;
    }
    /** Placed wrightport waiting for a chest click. Null when not binding. */
    public static BlockPos bindPort;
    public static boolean supplyBound;
    public static BlockPos supplyPos;
    public static long supplyHighlightUntil;
    public static String portGlowDim = "";
    public static BlockPos portGlowPos;
    public static BlockPos portGlowAnchor;
    public static boolean portGlowLine;
    public static long portGlowUntil;
    public static String mirrorGlowDim = "";
    public static BlockPos mirrorGlowPos;
    public static long mirrorGlowUntil;
    public static String shapeGlowDim = "";
    public static BlockPos shapeGlowPos;
    public static long shapeGlowUntil;
    public static int digitalKind;
    public static boolean digitalOnline;
    public static boolean digitalBound;
    public static BlockPos digitalPos;
    public static String digitalDim = "";
    public static List<BlueprintPayloads.DigitalInfoLine> digitalLines = List.of();
    public static long digitalInfoAt;
    public static List<Component> digitalUseTip = List.of();
    public static long digitalUseUntil;
    public static Component notice = Component.empty();
    public static long noticeUntil;
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
    /** Ticks the mode list stays up after a change. One second. */
    public static final int STYLE_HUD_TICKS = 20;
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

    public record ActiveBuild(BlockPos origin, int clipX, int clipY, int clipZ, List<Ghost> ghosts, String dimension, boolean mine) {
    }

    public static final Map<UUID, ActiveBuild> activeBuilds = new LinkedHashMap<>();
    public static final Set<Long> activeCells = new HashSet<>();
    public static final Set<UUID> markedJobs = new HashSet<>();
    public static final Set<UUID> markedWrights = new HashSet<>();
    public static Map<UUID, BlueprintPayloads.WrightSpot> wrightSpots = Map.of();
    public static List<BlueprintPayloads.PlacedPort> placedPorts = List.of();
    public static final Set<String> markedPorts = new HashSet<>();
    public static String placedDim = "";
    public static BlockPos placedPos = BlockPos.ZERO;
    public static Swarm placedSwarm = Swarm.EMPTY;
    public static boolean placedKnown;
    public static boolean placedMissing;
    private static final Map<UUID, Pace> paces = new HashMap<>();

    public static void applyJobHologram(BlueprintPayloads.JobHologram payload) {
        if (payload.clear()) {
            if (payload.jobId().equals(Util.NIL_UUID)) {
                activeBuilds.clear();
            } else {
                activeBuilds.remove(payload.jobId());
            }
        } else if (finishedJob(payload.jobId()) && !cellMarksJob(payload.jobId())) {
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
            activeBuilds.put(payload.jobId(), new ActiveBuild(payload.origin(), payload.clipX(), payload.clipY(), payload.clipZ(), ghosts, payload.dimension(), payload.mine()));
        }
        rebuildActiveCells();
    }

    /** A placed cell is finished. Breaking that block later must not bring its ghost back. */
    public static void dropJobGhost(UUID jobId, int x, int y, int z) {
        ActiveBuild build = activeBuilds.get(jobId);
        if (build == null) {
            return;
        }
        List<Ghost> next = new ArrayList<>();
        boolean hit = false;
        for (Ghost ghost : build.ghosts()) {
            if (ghost.x() == x && ghost.y() == y && ghost.z() == z) {
                hit = true;
                continue;
            }
            next.add(ghost);
        }
        if (!hit) {
            return;
        }
        activeBuilds.put(jobId, new ActiveBuild(build.origin(), build.clipX(), build.clipY(), build.clipZ(), next, build.dimension(), build.mine()));
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
        // A removed job never sends another clear, so drop outlines that no longer have a job.
        // Finished mirror jobs stay in the list and keep their block marks.
        if (activeBuilds.keySet().removeIf(id -> !seen.contains(id) || (finishedJob(id) && !cellMarksJob(id)))) {
            rebuildActiveCells();
        }
    }

    private static boolean cellMarksJob(UUID id) {
        for (BlueprintPayloads.JobLine job : jobs) {
            if (job.id().equals(id)) {
                return job.cellMarks();
            }
        }
        return false;
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

    public static String portMark(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    public static boolean portMarked(String dimension, BlockPos pos) {
        return markedPorts.contains(portMark(dimension, pos));
    }

    public static void togglePortMark(String dimension, BlockPos pos) {
        String key = portMark(dimension, pos);
        if (!markedPorts.add(key)) {
            markedPorts.remove(key);
        }
    }

    public static void applyPlacedPorts(List<BlueprintPayloads.PlacedPort> ports) {
        List<BlueprintPayloads.PlacedPort> mine = new ArrayList<>();
        if (ports != null) {
            for (BlueprintPayloads.PlacedPort port : ports) {
                if (port != null && !port.unowned()) {
                    mine.add(port);
                }
            }
        }
        placedPorts = List.copyOf(mine);
        Set<String> live = new HashSet<>();
        for (BlueprintPayloads.PlacedPort port : placedPorts) {
            live.add(portMark(port.dimension(), port.pos()));
        }
        markedPorts.retainAll(live);
    }

    public static void applyPlacedSwarm(String dimension, BlockPos pos, boolean missing, Swarm swarm) {
        placedDim = dimension == null ? "" : dimension;
        placedPos = pos == null ? BlockPos.ZERO : pos;
        placedMissing = missing;
        placedKnown = true;
        placedSwarm = swarm == null ? Swarm.EMPTY : swarm;
    }

    public static void toggleMark(UUID id) {
        if (!markedJobs.add(id)) {
            markedJobs.remove(id);
        }
    }

    public static void applyWrightSpots(BlueprintPayloads.WrightSpots payload) {
        Map<UUID, BlueprintPayloads.WrightSpot> next = new HashMap<>();
        if (payload != null && payload.spots() != null) {
            for (BlueprintPayloads.WrightSpot spot : payload.spots()) {
                if (spot != null && spot.unit() != null) {
                    next.put(spot.unit(), spot);
                }
            }
        }
        wrightSpots = next;
        markedWrights.retainAll(next.keySet());
    }

    public static BlueprintPayloads.WrightSpot wrightSpot(UUID unit) {
        return unit == null ? null : wrightSpots.get(unit);
    }

    public static boolean wrightMarked(UUID unit) {
        return unit != null && markedWrights.contains(unit);
    }

    public static void toggleWright(UUID unit) {
        if (unit == null || !markedWrights.add(unit)) {
            if (unit != null) {
                markedWrights.remove(unit);
            }
        }
    }

    public static String jobStatusKey(BlueprintPayloads.JobLine job) {
        if (job == null) {
            return "menu.wrights.running";
        }
        if (job.undoing()) {
            return "menu.wrights.undoing";
        }
        if (job.finished()) {
            if (job.stopped()) {
                return "menu.wrights.stopped";
            }
            if (job.undone()) {
                return "menu.wrights.undone";
            }
            return "menu.wrights.completed";
        }
        if (job.saving()) {
            return "menu.wrights.saving";
        }
        if (job.paused()) {
            return holdStatusKey(job.hold(), true);
        }
        if (job.waitKind() == com.wrights.job.BuildJob.WAIT_ITEMS) {
            return "menu.wrights.stalled";
        }
        if (job.queued()) {
            return "menu.wrights.queued";
        }
        if (job.hold() != com.wrights.job.BuildJob.HOLD_NONE && job.hold() != com.wrights.job.BuildJob.HOLD_MANUAL) {
            return holdStatusKey(job.hold(), false);
        }
        return "menu.wrights.running";
    }

    public static String holdStatusKey(int hold, boolean paused) {
        return switch (hold) {
            case com.wrights.job.BuildJob.HOLD_NO_WRIGHTS -> "menu.wrights.hold_no_wrights";
            case com.wrights.job.BuildJob.HOLD_PLAYER_RANGE -> paused ? "menu.wrights.hold_player" : "menu.wrights.hold_waiting_player";
            case com.wrights.job.BuildJob.HOLD_PORT_RANGE -> "menu.wrights.hold_port";
            case com.wrights.job.BuildJob.HOLD_DIMENSION -> "menu.wrights.hold_dimension";
            case com.wrights.job.BuildJob.HOLD_CHUNK -> "menu.wrights.hold_chunk";
            default -> "menu.wrights.paused";
        };
    }

    public static int percent(BlueprintPayloads.JobLine job) {
        int total = Math.max(1, job.total());
        return Mth.clamp(job.done() * 100 / total, 0, 100);
    }

    /** Why a running job is standing still, or null when it is placing blocks. */
    public static Component jobWait(BlueprintPayloads.JobLine job) {
        if (job == null || job.finished() || job.paused() || job.undoing()) {
            return null;
        }
        String key = switch (job.waitKind()) {
            case com.wrights.job.BuildJob.WAIT_FLOOR -> "hud.wrights.wait_floor";
            case com.wrights.job.BuildJob.WAIT_CHUNK -> "hud.wrights.wait_chunk";
            case com.wrights.job.BuildJob.WAIT_ITEMS -> "hud.wrights.wait_items";
            default -> null;
        };
        return key == null ? null : Component.translatable(key);
    }

    public static String timeLeft(BlueprintPayloads.JobLine job) {
        if (job.finished() || job.paused()) {
            return "";
        }
        Pace pace = paces.get(job.id());
        if (pace == null || pace.shownSeconds < 0) {
            return "";
        }
        double seconds = pace.shownSeconds;
        if (job.waitKind() != com.wrights.job.BuildJob.WAIT_ITEMS) {
            seconds -= (System.currentTimeMillis() - pace.shownAt) / 1000.0D;
        }
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
        if (job.paused() || job.waitKind() == com.wrights.job.BuildJob.WAIT_ITEMS) {
            pace.samples.clear();
            pace.shownAt = now;
            pace.done = job.done();
            return;
        }
        boolean progressed = job.done() > pace.done;
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
        if (!progressed && pace.shownSeconds >= 0 && raw > pace.shownSeconds) {
            return;
        }
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
        if (window > 0) {
            return window;
        }
        if (pace.shownSeconds >= 0) {
            return 0;
        }
        return lifetime;
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
        int modeId = switch (mode) {
            case CAPTURE -> 1;
            case BUILD -> 2;
            case JOBS -> 3;
            case MANAGE -> 4;
            case INFO -> 5;
            case LINK -> 6;
            default -> 0;
        };
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
        bindPort = null;
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
        if (last == 3 && WrightsConfig.allowJobs()) {
            resumeSent = true;
            enterJobs();
            return;
        }
        if (last == 4 && WrightsConfig.allowManage()) {
            resumeSent = true;
            enterManage();
            return;
        }
        if (last == 5 && WrightsConfig.allowInfo()) {
            resumeSent = true;
            enterInfo();
            return;
        }
        if (last == 6 && WrightsConfig.allowLink()) {
            resumeSent = true;
            enterLink();
            return;
        }
        if ((last == 1 && !WrightsConfig.allowCapture()) || (last == 2 && !WrightsConfig.allowPlace())) {
            resumeSent = true;
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
        clearCapturePreview();
        follow = false;
        boxDragging = false;
        moveGrabbed = false;
        moveOriginA = null;
        moveOriginB = null;
        captureDirty = false;
        clearedGeneration = generation;
        styleHudTicks = STYLE_HUD_TICKS;
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

    public static void enterManage() {
        leaveCapture();
        mode = Mode.MANAGE;
        bindSupply = false;
        updateFlags();
    }

    public static void enterInfo() {
        leaveCapture();
        mode = Mode.INFO;
        bindSupply = false;
        updateFlags();
    }

    public static void enterLink() {
        leaveCapture();
        mode = Mode.LINK;
        bindSupply = false;
        updateFlags();
    }

    /** True once New has been left for Build, so coming back starts a blank capture. */
    private static boolean captureDirty;
    /** Server generation at the moment New was cleared. Older syncs must not restore that box. */
    private static int clearedGeneration = -1;

    public static void enterNewMode() {
        if (mode == Mode.SAVING) {
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

    private static int[] mainModes() {
        int count = 0;
        if (WrightsConfig.showBuilding()) {
            count++;
        }
        if (WrightsConfig.allowJobs()) {
            count++;
        }
        if (WrightsConfig.allowManage()) {
            count++;
        }
        if (WrightsConfig.allowInfo()) {
            count++;
        }
        if (WrightsConfig.allowLink()) {
            count++;
        }
        int[] modes = new int[Math.max(1, count)];
        int n = 0;
        if (WrightsConfig.showBuilding()) {
            modes[n++] = 0;
        }
        if (WrightsConfig.allowJobs()) {
            modes[n++] = 1;
        }
        if (WrightsConfig.allowManage()) {
            modes[n++] = 2;
        }
        if (WrightsConfig.allowInfo()) {
            modes[n++] = 3;
        }
        if (WrightsConfig.allowLink()) {
            modes[n++] = 4;
        }
        if (n == 0) {
            return new int[0];
        }
        if (n == modes.length) {
            return modes;
        }
        int[] trimmed = new int[n];
        System.arraycopy(modes, 0, trimmed, 0, n);
        return trimmed;
    }

    public static void cycleMainMode(int delta) {
        int[] modes = mainModes();
        if (modes.length == 0) {
            return;
        }
        int current = switch (mode) {
            case CAPTURE, SAVING, BUILD -> 0;
            case JOBS -> 1;
            case MANAGE -> 2;
            case INFO -> 3;
            case LINK -> 4;
            default -> -1;
        };
        int at = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                at = i;
                break;
            }
        }
        if (current < 0) {
            at = delta > 0 ? -1 : 0;
        }
        int next = modes[Math.floorMod(at + delta, modes.length)];
        if (next == 0) {
            enterBuildFromLast();
            bindSupply = false;
        } else if (next == 1) {
            enterJobs();
        } else if (next == 2) {
            enterManage();
        } else if (next == 3) {
            enterInfo();
        } else {
            enterLink();
        }
        mainPicker = true;
        styleHudTicks = STYLE_HUD_TICKS;
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
        clearCapturePreview();
        // A chosen blueprint is placed from Use template. Bind inventory is a separate tool.
        bindSupply = false;
        bindFromMenu = false;
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
            if (incoming == Mode.CAPTURE && payload.generation() <= clearedGeneration) {
                cornerA = null;
                cornerB = null;
                boxDragging = false;
            }
            mode = incoming;
            if (incoming != Mode.CAPTURE) {
                boxDragging = false;
            } else if (previous != Mode.CAPTURE) {
                styleHudTicks = Math.max(styleHudTicks, STYLE_HUD_TICKS);
            }
            if (incoming != Mode.BUILD) {
                buildId = null;
                ghosts = List.of();
                concealed = false;
                follow = false;
            }
            if ((incoming != Mode.CAPTURE && incoming != Mode.SAVING) || cornerA == null || cornerB == null) {
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
        boolean switching = editId == null || !payload.id().equals(editId);
        editId = payload.id();
        editName = payload.name();
        editX = Math.max(1, payload.sizeX());
        editY = Math.max(1, payload.sizeY());
        editZ = Math.max(1, payload.sizeZ());
        editBlocks = payload.blocks();
        previewSampled = payload.sampled();
        previewConcealed = payload.concealed();
        storedSettings = payload.storedSettings();
        storedAll = payload.storedAll();
        if (switching) {
            WrightsClientConfig.setPlaceNbtBlocks(true);
            WrightsClientConfig.setPlaceNbtSettings(true);
            WrightsClientConfig.setPlaceNbtAll(true);
        }
        editPivotX = clamp(payload.anchorX(), editX);
        editPivotY = clamp(payload.anchorY(), editY);
        editPivotZ = clamp(payload.anchorZ(), editZ);
        if (payload.forBuild()) {
            boolean same = mode == Mode.BUILD && payload.id().equals(buildId);
            boolean sizeChanged = fullX != editX || fullY != editY || fullZ != editZ;
            // Saving a new template and choosing Build arrives here while still capturing.
            // Keep an in-progress Bind inventory selection, which is already in build mode.
            if (mode != Mode.BUILD) {
                bindSupply = false;
                bindFromMenu = false;
            }
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

    public static void showJobDone(net.minecraft.network.chat.Component name, net.minecraft.network.chat.Component detail, int seconds) {
        net.minecraft.network.chat.Component title = name == null ? net.minecraft.network.chat.Component.translatable("hud.wrights.tool_build") : name;
        net.minecraft.network.chat.Component line = detail == null ? net.minecraft.network.chat.Component.empty() : detail;
        jobNotices.add(new JobNotice(title, line, Math.max(0, seconds)));
        while (jobNotices.size() > 3) {
            jobNotices.remove(0);
        }
    }

    public static void showDockToast(com.wrights.network.BlueprintPayloads.DockToast toast) {
        if (toast == null || toast.docked() + toast.digital() + toast.dropped() <= 0) {
            return;
        }
        dockNotice = new DockNotice(toast);
    }

    public static void applyDeploy() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        com.wrights.drone.Swarm swarm = com.wrights.drone.Wrightports.swarm(minecraft.player);
        int next = WrightsClientConfig.deployTarget(freeWrights(), placeableBlocks());
        if (next <= 0 || next == swarm.assignCount()) {
            return;
        }
        com.wrights.drone.Wrightports.replaceWorn(minecraft.player, swarm.withAssign(next));
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAssign(next));
    }

    public static void applyPool(int free, int stored) {
        poolFree = Math.max(0, free);
        poolStored = Math.max(0, stored);
        clampDeploy();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PlacementScreen screen) {
            screen.refresh();
        }
    }

    public static int deployCap() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        int blocks = Math.max(0, placeableBlocks());
        if (blocks <= 0) {
            return 0;
        }
        int stored = knownStored();
        if (stored <= 0) {
            return 0;
        }
        int free = knownFree();
        if (free <= 0) {
            return 0;
        }
        int ceiling = Math.min(blocks, WrightsConfig.maxWrightsPerJob());
        return Math.min(Math.max(1, WrightsClientConfig.droneCount()), Math.min(free, ceiling));
    }

    /** Wrights that can leave a worn port or a nearby placed wrightport. */
    public static int freeWrights() {
        return knownFree();
    }

    private static int knownFree() {
        if (poolFree >= 0) {
            return poolFree;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        return com.wrights.drone.WrightHomes.countUnits(minecraft.player, WrightsClientState::wrightFree);
    }

    private static int knownStored() {
        if (poolStored >= 0) {
            return poolStored;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        return com.wrights.drone.WrightHomes.countUnits(minecraft.player, id -> true);
    }

    private static boolean wrightFree(java.util.UUID id) {
        for (BlueprintPayloads.JobLine job : jobs) {
            if (!job.finished() && !job.saving() && job.crew().contains(id)) {
                return false;
            }
        }
        return true;
    }

    /** How many wrights this build can ask for. With nobody free, that is still the job's own limit. */
    public static int requestCap() {
        int blocks = Math.max(0, placeableBlocks());
        if (blocks <= 0) {
            return 0;
        }
        int ceiling = Math.min(blocks, WrightsConfig.maxWrightsPerJob());
        int freeCap = deployCap();
        return freeCap > 0 ? freeCap : ceiling;
    }

    /** True when a new build should wait, because no wright is free to send right now. */
    public static boolean queueInstead() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        if (minecraft.player.getAbilities().instabuild && !minecraft.player.isSpectator() && WrightsClientConfig.INSTANT_JOBS.get()) {
            return false;
        }
        return deployCap() <= 0 && requestCap() > 0;
    }

    /** Keeps this job's wright count inside what the port and the blueprint can use. */
    public static void clampDeploy() {
        int cap = requestCap();
        if (cap <= 0) {
            deployCount = 0;
            return;
        }
        if (!deployChosen) {
            if (deployCap() > 0) {
                deployCount = WrightsClientConfig.deployTarget(freeWrights(), placeableBlocks());
            } else {
                int wanted = WrightsClientConfig.droneCount();
                deployCount = Math.max(1, wanted);
            }
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

    public static void beginPortChest(BlockPos port) {
        bindPort = port == null ? null : port.immutable();
    }

    public static void finishPortChest(BlockPos chest) {
        if (bindPort == null || chest == null) {
            bindPort = null;
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.BindPortChest(bindPort, chest, false));
        bindPort = null;
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
        return tryBuild(force, WrightsClientConfig.QUEUE_JOBS.get());
    }

    public static boolean tryBuild(boolean force, boolean queue) {
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
                WrightsClientConfig.SUPPLY_PLAYER_FIRST.get(),
                queue,
                WrightsClientConfig.PLACE_NBT_BLOCKS.get(),
                placeSettingsChosen(),
                placeAllChosen(),
                mirrorSend(),
                mirrorSend() ? WrightsClientConfig.mirrorAnchor() : BlockPos.ZERO,
                WrightsClientConfig.MIRROR_FACE.get(),
                WrightsClientConfig.MIRROR_PATTERN.get(),
                WrightsClientConfig.MIRROR_SHAPES.get(),
                WrightsClientConfig.MIRROR_AXES.get()));
        return true;
    }

    private static boolean mirrorSend() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || WrightsClientConfig.mirrorBlocks()) {
            return false;
        }
        return WrightsConfig.allowMirrorBlueprints() && WrightsClientConfig.mirrorArmed(minecraft.level.dimension().location().toString());
    }

    public static void setMirrorAnchor(BlockPos pos, Direction face) {
        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        WrightsClientConfig.setMirror(pos, face.get3DDataValue(), dimension);
    }

    public static void setShapeAnchor(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        String dimension = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        WrightsClientConfig.setShapeAnchor(pos, dimension);
        glowShapeAnchor(dimension, pos);
    }

    private static BlueprintPayloads.ShapePlace pendingShape;

    public static boolean tryShape(boolean force) {
        if (pendingShape == null) {
            return false;
        }
        return tryShape(force, pendingShape.pos(), Direction.from3DDataValue(pendingShape.face()));
    }

    public static boolean tryShape(boolean force, BlockPos pos, Direction face) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ControllerItem.isHeld(minecraft.player)) {
            return false;
        }
        if (WrightsClientConfig.shapeMaterials().isEmpty()) {
            minecraft.player.displayClientMessage(Component.translatable("message.wrights.shape_need_materials"), true);
            return false;
        }
        String dimension = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        boolean useAnchor = WrightsClientConfig.SHAPE_USE_ANCHOR.get();
        boolean anchorSet = WrightsClientConfig.shapePlaced(dimension);
        if (useAnchor && !anchorSet) {
            minecraft.player.displayClientMessage(Component.translatable("message.wrights.shape_need_anchor"), true);
            return false;
        }
        if (!useAnchor && (pos == null || face == null)) {
            minecraft.player.displayClientMessage(Component.translatable("message.wrights.shape_look"), true);
            return false;
        }
        pendingShape = new BlueprintPayloads.ShapePlace(
                pos == null ? BlockPos.ZERO : pos,
                face == null ? 0 : face.get3DDataValue(),
                useAnchor,
                anchorSet,
                anchorSet ? WrightsClientConfig.shapeAnchor() : BlockPos.ZERO,
                WrightsClientConfig.SHAPE_KIND.get(),
                WrightsClientConfig.SHAPE_SIZE.get(),
                WrightsClientConfig.SHAPE_HEIGHT.get(),
                WrightsClientConfig.SHAPE_PLANE.get(),
                WrightsClientConfig.SHAPE_HOLLOW.get(),
                WrightsClientConfig.SHAPE_REPLACE.get(),
                WrightsClientConfig.SHAPE_VOID.get(),
                force,
                WrightsClientConfig.SHAPE_MATERIALS.get(),
                WrightsClientConfig.SHAPE_SUPPLY.get());
        PacketDistributor.sendToServer(pendingShape);
        return true;
    }

    public static boolean showStoredSettings() {
        return storedSettings || storedAll;
    }

    public static boolean showStoredAll() {
        Minecraft minecraft = Minecraft.getInstance();
        return storedAll && WrightsConfig.copyAll() && minecraft.player != null
                && minecraft.player.getAbilities().instabuild && !minecraft.player.isSpectator();
    }

    /** Settings to apply on this build. Off when All is on, since All already includes them. */
    public static boolean placeSettingsChosen() {
        return showStoredSettings() && WrightsClientConfig.PLACE_NBT_SETTINGS.get() && !placeAllChosen();
    }

    public static boolean placeAllChosen() {
        return showStoredAll() && WrightsClientConfig.PLACE_NBT_ALL.get();
    }

    /** Keep full NBT on save when the All control is hidden, so a survival save does not strip it. */
    public static boolean keepStoredAll() {
        if (!storedAll) {
            return false;
        }
        if (!showStoredAll()) {
            return true;
        }
        return WrightsClientConfig.PLACE_NBT_ALL.get();
    }

    public static boolean keepStoredSettings() {
        if (keepStoredAll()) {
            return false;
        }
        if (!showStoredSettings()) {
            return storedSettings;
        }
        return WrightsClientConfig.PLACE_NBT_SETTINGS.get();
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
                && (minecraft.screen == null || minecraft.screen instanceof PlacementScreen)
                && !(mirrorToolActive() && WrightsClientConfig.mirrorBlocks())) {
            anchor = lookTarget();
        }
    }

    public static void tickCapture() {
        if (!jobNotices.isEmpty()) {
            jobNotices.removeIf(notice -> --notice.ticks <= 0);
        }
        if (dockNotice != null && --dockNotice.ticks <= 0) {
            dockNotice = null;
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
            styleHudTicks = STYLE_HUD_TICKS;
            styleAnnounce = true;
            return;
        }
        if (moveGrabbed) {
            moveGrabbed = false;
            sendUpdate();
        }
        releaseBoxDrag();
        captureStyle = values[next];
        styleHudTicks = STYLE_HUD_TICKS;
        styleAnnounce = true;
        rememberMode();
    }

    public static void cycleBuildTool(int direction) {
        mainPicker = false;
        if (mode == Mode.SAVING) {
            styleHudTicks = STYLE_HUD_TICKS;
            styleAnnounce = true;
            return;
        }
        int styles = styleSteps();
        int extras = 2 + (WrightsConfig.allowMirror() ? 1 : 0) + (WrightsConfig.allowShape() ? 1 : 0);
        int count = styles + extras;
        int next = Math.floorMod(buildToolSlot() + direction, count);
        if (next < styles) {
            selectNewStyle(styleAt(next));
        } else {
            int tool = next - styles;
            int cursor = 0;
            if (tool == cursor) {
                selectBuildTool(false);
            } else {
                cursor++;
                if (WrightsConfig.allowMirror()) {
                    if (tool == cursor) {
                        selectMirrorTool();
                    } else {
                        cursor++;
                        if (WrightsConfig.allowShape() && tool == cursor) {
                            selectShapeTool();
                        } else {
                            selectBuildTool(true);
                        }
                    }
                } else if (WrightsConfig.allowShape() && tool == cursor) {
                    selectShapeTool();
                } else {
                    selectBuildTool(true);
                }
            }
        }
        styleHudTicks = STYLE_HUD_TICKS;
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

    /** New template styles, then Use template, Mirror, Shape, and Bind inventory. */
    private static int buildToolSlot() {
        if (mode == Mode.CAPTURE) {
            int offset = switch (captureStyle) {
                case BOX -> 1;
                case MOVE -> hasBox() ? 2 : 0;
                case CORNERS -> 0;
            };
            return Math.min(offset, styleSteps() - 1);
        }
        int slot = styleSteps();
        if (mode == Mode.BUILD && !bindSupply && !mirrorAim && !shapeAim) {
            return slot;
        }
        slot++;
        if (WrightsConfig.allowMirror()) {
            if (mode == Mode.BUILD && mirrorAim) {
                return slot;
            }
            slot++;
        }
        if (WrightsConfig.allowShape()) {
            if (mode == Mode.BUILD && shapeAim) {
                return slot;
            }
            slot++;
        }
        if (mode == Mode.BUILD && bindSupply) {
            return slot;
        }
        return styleSteps();
    }

    public static void selectMirrorTool() {
        selectBuildTool(false);
        mirrorAim = true;
        shapeAim = false;
        WrightsClientConfig.setMirrorBlocks(true);
    }

    public static void selectShapeTool() {
        selectBuildTool(false);
        shapeAim = true;
        mirrorAim = false;
    }

    private static void selectBuildTool(boolean bind) {
        if (mode == Mode.CAPTURE) {
            captureDirty = true;
        }
        if (mode != Mode.BUILD) {
            boxDragging = false;
            moveGrabbed = false;
            moveOriginA = null;
            moveOriginB = null;
            clearCapturePreview();
            enterBuildFromLast();
        }
        bindSupply = bind;
        mirrorAim = false;
        shapeAim = false;
        if (bind) {
            bindFromMenu = false;
        }
    }

    private static void selectNewStyle(CaptureStyle style) {
        mirrorAim = false;
        shapeAim = false;
        if (mode == Mode.SAVING) {
            return;
        }
        boolean restart = mode != Mode.CAPTURE || captureDirty;
        if (restart) {
            enterNewMode();
        }
        if (mode != Mode.CAPTURE || (style == captureStyle && !restart)) {
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

    public static void glowPort(String dimension, BlockPos pos, boolean line, BlockPos anchor) {
        portGlowDim = dimension == null ? "" : dimension;
        portGlowPos = pos;
        portGlowLine = line && anchor != null;
        portGlowAnchor = portGlowLine ? anchor : null;
        portGlowUntil = System.currentTimeMillis() + 10_000L;
    }

    public static boolean portGlowing() {
        return portGlowPos != null && System.currentTimeMillis() < portGlowUntil;
    }

    /** Marks the mirror anchor for five seconds, with a line back to the player. */
    public static void glowMirrorAnchor(String dimension, BlockPos pos) {
        mirrorGlowDim = dimension == null ? "" : dimension;
        mirrorGlowPos = pos == null ? null : pos.immutable();
        mirrorGlowUntil = System.currentTimeMillis() + 5000L;
    }

    public static boolean mirrorGlowing() {
        return mirrorGlowPos != null && System.currentTimeMillis() < mirrorGlowUntil;
    }

    public static void glowShapeAnchor(String dimension, BlockPos pos) {
        shapeGlowDim = dimension == null ? "" : dimension;
        shapeGlowPos = pos == null ? null : pos.immutable();
        shapeGlowUntil = System.currentTimeMillis() + 5000L;
    }

    public static boolean shapeGlowing() {
        return shapeGlowPos != null && System.currentTimeMillis() < shapeGlowUntil;
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

    public static void showDigitalUse(List<BlueprintPayloads.DigitalUseLine> lines) {
        List<Component> tip = new ArrayList<>();
        if (lines != null) {
            for (BlueprintPayloads.DigitalUseLine line : lines) {
                net.minecraft.world.item.Item item = net.minecraft.world.item.Item.byId(line.itemId());
                Component name = new net.minecraft.world.item.ItemStack(item).getHoverName();
                if (line.pulled() > 0) {
                    tip.add(Component.translatable("message.wrights.digital_pulled", line.pulled(), name));
                }
                if (line.crafted() > 0) {
                    tip.add(Component.translatable("message.wrights.digital_crafted", line.crafted(), name));
                }
            }
        }
        digitalUseTip = tip;
        digitalUseUntil = tip.isEmpty() ? 0L : System.currentTimeMillis() + 4500L;
    }

    public static void showNotice(Component text) {
        notice = text == null ? Component.empty() : text;
        noticeUntil = notice.getString().isEmpty() ? 0L : System.currentTimeMillis() + 6000L;
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
                if (hasBox() && aimingAtSelection()) {
                    minecraft.setScreen(new BlueprintAdjustScreen());
                    return;
                }
                clearCapturePreview();
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
        if (hasBox()) {
            if (aimingAtSelection()) {
                minecraft.setScreen(new BlueprintAdjustScreen());
                return;
            }
            if (clicked == null) {
                return;
            }
            cornerA = clicked;
            cornerB = null;
            clearCapturePreview();
            PacketDistributor.sendToServer(new BlueprintPayloads.CapturePick(clicked, BlueprintPayloads.CapturePick.BOX_BEGIN));
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

    private static boolean aimingAtSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !hasBox()) {
            return false;
        }
        AABB box = selection(cornerA, cornerB);
        AABB target = box.inflate(0.25D);
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(WrightsConfig.MAX_RANGE.getAsInt()));
        if (target.contains(eye)) {
            return true;
        }
        Vec3 hit = target.clip(eye, end).orElse(null);
        if (hit == null) {
            return false;
        }
        BlockHitResult world = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        if (world.getType() != HitResult.Type.BLOCK || world.getLocation().distanceToSqr(eye) + 1.0E-3D >= hit.distanceToSqr(eye)) {
            return true;
        }
        return box.inflate(0.01D).contains(Vec3.atCenterOf(world.getBlockPos()));
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

    /** Ctrl-scroll grows or shrinks the boundary face under the crosshair. */
    public static boolean nudgeBoundary(double scrollY) {
        if (scrollY == 0.0D) {
            return false;
        }
        int outward = scrollY > 0.0D ? 1 : -1;
        if (mode == Mode.CAPTURE && hasBox() && !boxDragging) {
            return nudgeSelection(outward);
        }
        if (mode == Mode.BUILD && !bindSupply && anchor != null && buildId != null && !(mirrorToolActive() && WrightsClientConfig.mirrorBlocks())) {
            return nudgeAnchored(outward);
        }
        return false;
    }

    private static boolean nudgeSelection(int outward) {
        AABB box = selection(cornerA, cornerB);
        Direction face = lookedFace(box);
        if (face == null) {
            return false;
        }
        BlockPos low = BoxEdits.min(cornerA, cornerB);
        BlockPos high = BoxEdits.max(cornerA, cornerB);
        int[] min = {low.getX(), low.getY(), low.getZ()};
        int[] max = {high.getX(), high.getY(), high.getZ()};
        int axis = face.getAxis().ordinal();
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            max[axis] += outward;
        } else {
            min[axis] -= outward;
        }
        if (min[axis] > max[axis]) {
            return true;
        }
        BlockPos nextMin = new BlockPos(min[0], min[1], min[2]);
        BlockPos nextMax = new BlockPos(max[0], max[1], max[2]);
        if (!boxAllowed(nextMin, nextMax)) {
            return true;
        }
        int dMinX = nextMin.getX() - low.getX();
        int dMinY = nextMin.getY() - low.getY();
        int dMinZ = nextMin.getZ() - low.getZ();
        int dMaxX = nextMax.getX() - high.getX();
        int dMaxY = nextMax.getY() - high.getY();
        int dMaxZ = nextMax.getZ() - high.getZ();
        cornerA = nextMin;
        cornerB = nextMax;
        if (moveGrabbed) {
            BlockPos aim = aimFree(WrightsConfig.MAX_RANGE.getAsInt());
            moveOffX = aim.getX() - nextMin.getX();
            moveOffY = aim.getY() - nextMin.getY();
            moveOffZ = aim.getZ() - nextMin.getZ();
            moveSizeX = nextMax.getX() - nextMin.getX() + 1;
            moveSizeY = nextMax.getY() - nextMin.getY() + 1;
            moveSizeZ = nextMax.getZ() - nextMin.getZ() + 1;
            if (moveOriginA != null && moveOriginB != null) {
                BlockPos originMin = BoxEdits.min(moveOriginA, moveOriginB);
                BlockPos originMax = BoxEdits.max(moveOriginA, moveOriginB);
                moveOriginA = originMin.offset(dMinX, dMinY, dMinZ);
                moveOriginB = originMax.offset(dMaxX, dMaxY, dMaxZ);
            }
        }
        sendUpdate();
        return true;
    }

    private static boolean nudgeAnchored(int outward) {
        int minX = anchor.getX() - pivotX;
        int minY = anchor.getY() - pivotY;
        int minZ = anchor.getZ() - pivotZ;
        int[] min = {minX, minY, minZ};
        int[] size = {Math.max(1, viewX), Math.max(1, viewY), Math.max(1, viewZ)};
        AABB box = new AABB(minX, minY, minZ, minX + size[0], minY + size[1], minZ + size[2]);
        Direction face = lookedFace(box);
        if (face == null) {
            return false;
        }
        int axis = face.getAxis().ordinal();
        int next = size[axis] + outward;
        if (next < 1 || next > WrightsConfig.MAX_AXIS.getAsInt()) {
            return true;
        }
        if (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            min[axis] -= outward;
        }
        BlockPos nextMin = new BlockPos(min[0], min[1], min[2]);
        BlockPos nextMax = nextMin.offset(
                (axis == 0 ? next : size[0]) - 1,
                (axis == 1 ? next : size[1]) - 1,
                (axis == 2 ? next : size[2]) - 1);
        if (!BoxEdits.inWorld(nextMin) || !BoxEdits.inWorld(nextMax) || !insideBuildHeight(nextMin, nextMax)) {
            return true;
        }
        size[axis] = next;
        viewX = size[0];
        viewY = size[1];
        viewZ = size[2];
        follow = false;
        anchor = new BlockPos(nextMin.getX() + pivotX, nextMin.getY() + pivotY, nextMin.getZ() + pivotZ);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PlacementScreen placement) {
            placement.refresh();
        }
        return true;
    }

    private static boolean boxAllowed(BlockPos min, BlockPos max) {
        if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(max) || !BoxEdits.fits(min, max)) {
            return false;
        }
        return insideBuildHeight(min, max);
    }

    private static boolean insideBuildHeight(BlockPos min, BlockPos max) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return true;
        }
        int floor = minecraft.level.getMinBuildHeight();
        int ceil = minecraft.level.getMaxBuildHeight() - 1;
        int lowY = Math.min(min.getY(), max.getY());
        int highY = Math.max(min.getY(), max.getY());
        return lowY >= floor && highY <= ceil;
    }

    private static Direction lookedFace(AABB box) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 look = minecraft.player.getViewVector(1.0F);
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        double best = range;
        double bestAlign = 0.0D;
        Direction face = null;
        double slack = 0.08D;
        for (Direction direction : Direction.values()) {
            double plane = switch (direction) {
                case WEST -> box.minX;
                case EAST -> box.maxX;
                case DOWN -> box.minY;
                case UP -> box.maxY;
                case NORTH -> box.minZ;
                case SOUTH -> box.maxZ;
            };
            double component = switch (direction.getAxis()) {
                case X -> look.x;
                case Y -> look.y;
                case Z -> look.z;
            };
            if (Math.abs(component) < 1.0E-6D) {
                continue;
            }
            double origin = switch (direction.getAxis()) {
                case X -> eye.x;
                case Y -> eye.y;
                case Z -> eye.z;
            };
            double distance = (plane - origin) / component;
            if (distance < 0.0D || distance > best + 1.0E-4D) {
                continue;
            }
            Vec3 hit = eye.add(look.scale(distance));
            if (hit.x < box.minX - slack || hit.x > box.maxX + slack
                    || hit.y < box.minY - slack || hit.y > box.maxY + slack
                    || hit.z < box.minZ - slack || hit.z > box.maxZ + slack) {
                continue;
            }
            double align = Math.abs(component);
            if (face != null && distance > best - 1.0E-4D && align <= bestAlign) {
                continue;
            }
            best = distance;
            bestAlign = align;
            face = direction;
        }
        return face;
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
        public final net.minecraft.network.chat.Component name;
        public final net.minecraft.network.chat.Component detail;
        public final int seconds;
        public int ticks = 100;

        public JobNotice(net.minecraft.network.chat.Component name, net.minecraft.network.chat.Component detail, int seconds) {
            this.name = name;
            this.detail = detail;
            this.seconds = seconds;
        }
    }

    /** Wrights that left a missing dock. Replaced while more of them arrive, then it fades. */
    public static final class DockNotice {
        public final int docked;
        public final int dx;
        public final int dy;
        public final int dz;
        public final String port;
        public final int digital;
        public final int sx;
        public final int sy;
        public final int sz;
        public final int dropped;
        public final int gx;
        public final int gy;
        public final int gz;
        public int ticks = 140;

        public DockNotice(com.wrights.network.BlueprintPayloads.DockToast toast) {
            this.docked = toast.docked();
            this.dx = toast.dx();
            this.dy = toast.dy();
            this.dz = toast.dz();
            this.port = toast.port() == null ? "" : toast.port();
            this.digital = toast.digital();
            this.sx = toast.sx();
            this.sy = toast.sy();
            this.sz = toast.sz();
            this.dropped = toast.dropped();
            this.gx = toast.gx();
            this.gy = toast.gy();
            this.gz = toast.gz();
        }
    }
}
