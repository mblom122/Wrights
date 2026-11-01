package com.wrights;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

import com.wrights.blueprint.Mirror;
import com.wrights.util.Messages;

public final class WrightsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_WRIGHTS_PER_JOB = BUILDER
            .comment(
                    "Most wrights one job may use at once.",
                    "Each working wright is an entity. Raising this can lag the server.")
            .defineInRange("maxWrightsPerJob", 10, 1, 512);

    public static final ModConfigSpec.IntValue MAX_WRIGHTS_CARRIED = BUILDER
            .comment(
                    "Most wrights one wrightport can hold, worn or placed.",
                    "Raising this can lag the server when many of those wrights are working at once.")
            .defineInRange("maxWrightsCarried", 64, 1, 1024);

    public static final ModConfigSpec.IntValue MAX_WRIGHTS_OWNED = BUILDER
            .comment(
                    "Most wrights one player may store in total.",
                    "This counts every personal wrightport they carry and every wrightport block they placed.",
                    "Each port is still limited by maxWrightsCarried.")
            .defineInRange("maxWrightsOwned", 200, 1, 1_000_000);

    public static final ModConfigSpec.IntValue MAX_AXIS = BUILDER
            .comment(
                    "Largest selection length on one axis, in blocks. Default 512.",
                    "A larger box uses more memory and can lag the server while it is copied or built.")
            .defineInRange("maxAxis", 512, 1, 4096);

    public static final ModConfigSpec.IntValue MAX_SHAPE_AXIS = BUILDER
            .comment(
                    "Largest size, height, or thickness a Shape may use, in blocks. Default 512.",
                    "Separate from maxAxis (blueprints). Effective cap is min(maxShapeAxis, maxAxis).",
                    "Huge solid shapes are still capped by maxBlocks — preview uses a wire box when over shapePreviewListCap.")
            .defineInRange("maxShapeAxis", 512, 1, 4096);

    public static final ModConfigSpec.IntValue MAX_BLOCKS = BUILDER
            .comment(
                    "Largest AABB volume for blueprints and Shape builds. Separate from maxShapeAxis (slider length).",
                    "Default 512³ (134217728) — a full box at the default max axis on every side.",
                    "Shape mode streams cells during the job — it does not allocate the full list up front.")
            .defineInRange("maxBlocks", 512 * 512 * 512, 1, 512 * 512 * 512);

    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment(
                    "How many blocks one wright may place in one server tick. Its tier can place fewer than this.",
                    "Raising this can lag the server.")
            .defineInRange("blocksPerTick", 16, 1, 1024);

    public static final ModConfigSpec.IntValue COPY_POSITIONS_PER_TICK = BUILDER
            .comment(
                    "How many positions a copy may read in one tick.",
                    "Raising this finishes copies faster and can lag the server.")
            .defineInRange("copyPositionsPerTick", 1024, 1, 262_144);

    public static final ModConfigSpec.IntValue MAX_RANGE = BUILDER
            .comment("The job pauses while you are farther than this many blocks from the block it is working on.")
            .defineInRange("maxRange", 96, 8, 4096);

    public static final ModConfigSpec.EnumValue<Travel> TRAVEL_TO_JOB = BUILDER
            .comment(
                    "How a wright reaches a job.",
                    "FLY leaves the wrightport, or your back, and flies to the job.",
                    "TELEPORT appears at the job.")
            .defineEnum("travelToJob", Travel.FLY);

    public static final ModConfigSpec.EnumValue<Travel> TRAVEL_HOME = BUILDER
            .comment(
                    "How a wright comes home when the job is done.",
                    "FLY returns from the job to the wrightport, or to your back.",
                    "TELEPORT appears above the wrightport, or above you, and docks.")
            .defineEnum("travelHome", Travel.FLY);

    public static final ModConfigSpec.IntValue MAX_PREVIEW_BLOCKS = BUILDER
            .comment(
                    "Most solid blocks drawn for one blueprint preview. Larger blueprints still copy and build, but their block models stay hidden.",
                    "Raising this can lag the client.")
            .defineInRange("maxPreviewBlocks", 48_000, 0, 1_000_000);

    public static final ModConfigSpec.IntValue GHOST_BUDGET = BUILDER
            .comment(
                    "Most blueprint blocks drawn in the world at once, nearest first.",
                    "Raising this can lag the client.")
            .defineInRange("ghostBudget", 4096, 0, 1_000_000);

    public static final ModConfigSpec.IntValue GHOST_RANGE = BUILDER
            .comment(
                    "Blueprint blocks farther than this many blocks are not drawn in the world.",
                    "Raising this can lag the client.")
            .defineInRange("ghostRange", 48, 8, 512);

    public static final ModConfigSpec.IntValue SHAPE_PREVIEW_LIST_CAP = BUILDER
            .comment(
                    "Most shape cells the client may materialize for ghost previews.",
                    "Above this, Shape mode draws only the wire box. 0 uses ghostBudget-derived default.",
                    "Raising this can lag the client on large shapes.")
            .defineInRange("shapePreviewListCap", 0, 0, 1_000_000);

    public static final ModConfigSpec.BooleanValue REQUIRE_CHUNKS_LOADED = BUILDER
            .comment("While true, a job waits for the chunk to load. While false, unloaded positions are skipped.")
            .define("requireChunksLoaded", true);

    public static final ModConfigSpec.BooleanValue COPY_SETTINGS = BUILDER
            .comment(
                    "When false, Copy settings is unavailable. Machines are saved as freshly placed blocks.",
                    "Copy settings keeps sides and modes. It tries to drop items, fluids, energy, and upgrades, but a mod can store those under a name the filter does not know.")
            .define("copySettings", true);

    public static final ModConfigSpec.BooleanValue COPY_ALL = BUILDER
            .comment(
                    "When false, Copy all is unavailable.",
                    "Copy all saves every tag on the block, including items and energy, and can duplicate them. It only works in creative, even when this is true.")
            .define("copyAll", true);

    public static final ModConfigSpec.BooleanValue COPY_SETTINGS_WHITELIST = BUILDER
            .comment(
                    "When true, Copy settings only keeps data for blocks from copySettingsMods.",
                    "Other machines are saved as freshly placed blocks. Copy all ignores this list. Off by default, so every mod can use Copy settings.")
            .define("copySettingsWhitelist", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COPY_SETTINGS_MODS = BUILDER
            .comment(
                    "Mod ids that may keep settings while copySettingsWhitelist is true.",
                    "mekanism is energy cubes and machines. ae2 is Applied Energistics 2. Add other ids, such as mekanismgenerators, when you want those too.")
            .defineListAllowEmpty("copySettingsMods", List.of("mekanism", "ae2"), () -> "", WrightsConfig::modId);

    static {
        BUILDER.comment(
                "Turn features off for a modpack. A parent that is off also turns off its parts.",
                "The menu hides what is off. The server still refuses the action.")
                .push("features");
    }

    public static final ModConfigSpec.BooleanValue ALLOW_BUILDING = BUILDER
            .comment("Building, including blueprints and mirroring.")
            .define("building", true);
    public static final ModConfigSpec.BooleanValue ALLOW_BLUEPRINTS = BUILDER
            .comment("The blueprint library, capturing, placing, and editing.")
            .define("blueprints", true);
    public static final ModConfigSpec.BooleanValue ALLOW_CAPTURE = BUILDER
            .comment("Copying a box of the world into a new blueprint.")
            .define("capture", true);
    public static final ModConfigSpec.BooleanValue ALLOW_PLACE = BUILDER
            .comment("Sending wrights to build a blueprint.")
            .define("place", true);
    public static final ModConfigSpec.BooleanValue ALLOW_EDIT = BUILDER
            .comment("Changing a saved blueprint, including recapture.")
            .define("edit", true);
    public static final ModConfigSpec.BooleanValue ALLOW_UNDO = BUILDER
            .comment("Taking a finished build back apart and returning the items.")
            .define("undo", true);
    public static final ModConfigSpec.BooleanValue ALLOW_GLOBAL = BUILDER
            .comment("The global library, shared outside this world.")
            .define("globalLibrary", true);
    public static final ModConfigSpec.BooleanValue ALLOW_TRANSFER = BUILDER
            .comment("Importing and exporting blueprints through the clipboard.")
            .define("transfer", true);
    public static final ModConfigSpec.BooleanValue ALLOW_COPY_FLUIDS = BUILDER
            .comment("Saving water and lava into a blueprint.")
            .define("copyFluids", true);
    public static final ModConfigSpec.BooleanValue ALLOW_PLACE_FLUIDS = BUILDER
            .comment("Placing water and lava from a blueprint.")
            .define("placeFluids", true);
    public static final ModConfigSpec.BooleanValue ALLOW_VOID_BLOCKS = BUILDER
            .comment("Removing blocks that do not match the blueprint before placing.")
            .define("voidBlocks", true);
    public static final ModConfigSpec.BooleanValue ALLOW_VOID_FLUIDS = BUILDER
            .comment("Removing water or lava that does not match the blueprint before placing.")
            .define("voidFluids", true);
    public static final ModConfigSpec.BooleanValue ALLOW_VOID_NBT = BUILDER
            .comment("Voiding may also remove chests and other blocks that store contents.")
            .define("voidNbt", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MIRROR = BUILDER
            .comment("Mirroring. Off hides the mirror page and refuses mirrored copies.")
            .define("mirror", true);
    public static final ModConfigSpec.BooleanValue ALLOW_SHAPE = BUILDER
            .comment("Shape building. Off hides the shape page and refuses shape jobs.")
            .define("shape", true);
    public static final ModConfigSpec.BooleanValue ALLOW_SHAPE_HOLLOW = BUILDER
            .comment("Shape builds may use Hollow (shell only).")
            .define("shapeHollow", true);
    public static final ModConfigSpec.BooleanValue ALLOW_SHAPE_VOID = BUILDER
            .comment("Shape builds may void existing blocks at placement cells before placing. Also needs voidBlocks.")
            .define("shapeVoid", true);
    public static final ModConfigSpec.BooleanValue ALLOW_SHAPE_ANCHOR = BUILDER
            .comment("Shape builds may use On anchor / Set under me. Off forces Where I look.")
            .define("shapeAnchor", true);
    public static final ModConfigSpec.BooleanValue ALLOW_EXCHANGE = BUILDER
            .comment("Exchange mode. Replaces matching blocks inside a shape volume with chosen materials.")
            .define("exchange", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MIRROR_BLOCKS = BUILDER
            .comment("Mirroring the block in the other hand.")
            .define("mirrorBlocks", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MIRROR_BLUEPRINTS = BUILDER
            .comment("Mirroring a queued blueprint.")
            .define("mirrorBlueprints", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MIRROR_REPLACE = BUILDER
            .comment("Mirrored block copies may replace a block that is already there.")
            .define("mirrorReplace", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MIRROR_MINE = BUILDER
            .comment("Breaking a block can also break the mirrored copies.")
            .define("mirrorMine", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MIRROR_AXES = BUILDER
            .comment("Choosing X, Y, and Z in the mirror menu. Off keeps the axis from the anchor face.")
            .define("mirrorAxes", true);
    public static final ModConfigSpec.BooleanValue ALLOW_PATTERN_4 = BUILDER
            .comment("Copies above 2. Off caps the mirror slider at 2.")
            .define("mirrorPattern4", true);
    public static final ModConfigSpec.BooleanValue ALLOW_PATTERN_8 = BUILDER
            .comment("Copies above 4. Off caps the mirror slider at 4.")
            .define("mirrorPattern8", true);
    public static final ModConfigSpec.BooleanValue ALLOW_PATTERN_16 = BUILDER
            .comment("Copies above 8. Off caps the mirror slider at 8. On allows up to 32.")
            .define("mirrorPattern16", true);
    public static final ModConfigSpec.BooleanValue ALLOW_JOBS = BUILDER
            .comment("The jobs page.")
            .define("jobs", true);
    public static final ModConfigSpec.BooleanValue ALLOW_MANAGE = BUILDER
            .comment("The manage page for wrightports.")
            .define("manage", true);
    public static final ModConfigSpec.BooleanValue ALLOW_INFO = BUILDER
            .comment("The info page for supply and stock.")
            .define("info", true);
    public static final ModConfigSpec.BooleanValue ALLOW_LINK = BUILDER
            .comment("Binding a digital storage network.")
            .define("link", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private WrightsConfig() {
    }

    public enum Travel {
        FLY,
        TELEPORT
    }

    public static boolean flyToJob() {
        return travel(TRAVEL_TO_JOB) == Travel.FLY;
    }

    public static boolean flyHome() {
        return travel(TRAVEL_HOME) == Travel.FLY;
    }

    private static Travel travel(ModConfigSpec.EnumValue<Travel> value) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            return Travel.FLY;
        }
    }

    public static int maxWrightsPerJob() {
        return read(MAX_WRIGHTS_PER_JOB, 10);
    }

    public static int maxWrightsCarried() {
        return read(MAX_WRIGHTS_CARRIED, 64);
    }

    public static int maxWrightsOwned() {
        return read(MAX_WRIGHTS_OWNED, 200);
    }

    public static int maxPreviewBlocks() {
        return read(MAX_PREVIEW_BLOCKS, 48_000);
    }

    public static int ghostBudget() {
        return read(GHOST_BUDGET, 4096);
    }

    /**
     * How many ghost cells to bake/send for large shapes and blueprints.
     * Prefer a silhouette sample over blanking the preview.
     */
    public static int previewSampleBudget() {
        return Math.max(256, Math.min(maxPreviewBlocks(), ghostBudget()));
    }

    public static int ghostRange() {
        return read(GHOST_RANGE, 48);
    }

    public static boolean copySettings() {
        return read(COPY_SETTINGS, true);
    }

    public static boolean copyAll() {
        return read(COPY_ALL, true);
    }

    public static boolean copySettingsWhitelist() {
        return read(COPY_SETTINGS_WHITELIST, false);
    }

    public static boolean allowBuilding() {
        return read(ALLOW_BUILDING, true);
    }

    public static boolean allowBlueprints() {
        return allowBuilding() && read(ALLOW_BLUEPRINTS, true);
    }

    public static boolean allowCapture() {
        return allowBlueprints() && read(ALLOW_CAPTURE, true);
    }

    public static boolean allowPlace() {
        return allowBlueprints() && read(ALLOW_PLACE, true);
    }

    public static boolean allowEdit() {
        return allowBlueprints() && read(ALLOW_EDIT, true);
    }

    public static boolean allowUndo() {
        return allowBlueprints() && read(ALLOW_UNDO, true);
    }

    public static boolean allowGlobal() {
        return allowBlueprints() && read(ALLOW_GLOBAL, true);
    }

    public static boolean allowTransfer() {
        return allowBlueprints() && read(ALLOW_TRANSFER, true);
    }

    public static boolean allowCopyFluids() {
        return allowBlueprints() && read(ALLOW_COPY_FLUIDS, true);
    }

    public static boolean allowPlaceFluids() {
        return allowPlace() && read(ALLOW_PLACE_FLUIDS, true);
    }

    public static boolean allowVoidBlocks() {
        return allowPlace() && read(ALLOW_VOID_BLOCKS, true);
    }

    public static boolean allowVoidFluids() {
        return allowPlace() && read(ALLOW_VOID_FLUIDS, true);
    }

    public static boolean allowVoidNbt() {
        return allowVoidBlocks() && read(ALLOW_VOID_NBT, true);
    }

    public static boolean allowMirror() {
        return allowBuilding() && read(ALLOW_MIRROR, true) && (read(ALLOW_MIRROR_BLOCKS, true) || read(ALLOW_MIRROR_BLUEPRINTS, true));
    }

    public static boolean allowMirrorBlocks() {
        return allowMirror() && read(ALLOW_MIRROR_BLOCKS, true);
    }

    public static boolean allowMirrorBlueprints() {
        return allowMirror() && read(ALLOW_MIRROR_BLUEPRINTS, true);
    }

    public static boolean allowMirrorReplace() {
        return allowMirrorBlocks() && read(ALLOW_MIRROR_REPLACE, true);
    }

    public static boolean allowMirrorMine() {
        return allowMirrorBlocks() && read(ALLOW_MIRROR_MINE, true);
    }

    public static boolean allowMirrorAxes() {
        return allowMirror() && read(ALLOW_MIRROR_AXES, true);
    }

    public static boolean allowShape() {
        return allowBuilding() && read(ALLOW_SHAPE, true);
    }

    public static boolean allowShapeHollow() {
        return allowShape() && read(ALLOW_SHAPE_HOLLOW, true);
    }

    public static boolean allowShapeVoid() {
        return allowShape() && allowVoidBlocks() && read(ALLOW_SHAPE_VOID, true);
    }

    public static boolean allowShapeAnchor() {
        return allowShape() && read(ALLOW_SHAPE_ANCHOR, true);
    }

    public static boolean allowExchange() {
        return allowBuilding() && read(ALLOW_EXCHANGE, true);
    }

    /**
     * Cap for shape size / height / thickness. Never above {@link #MAX_AXIS}.
     */
    public static int maxShapeAxis() {
        int shape = Math.max(1, read(MAX_SHAPE_AXIS, 512));
        int axis = Math.max(1, read(MAX_AXIS, 512));
        return Math.min(shape, axis);
    }

    /**
     * Soft cap for materializing shape cell lists on the client.
     * Above this, Wrights draws the wire box only (no per-block ghost list).
     */
    public static int shapePreviewListCap() {
        int configured = read(SHAPE_PREVIEW_LIST_CAP, 0);
        if (configured > 0) {
            return configured;
        }
        return Math.max(4_096, Math.min(65_536, previewSampleBudget() * 8));
    }

    public static boolean allowJobs() {
        return read(ALLOW_JOBS, true);
    }

    public static boolean allowManage() {
        return read(ALLOW_MANAGE, true);
    }

    public static boolean allowInfo() {
        return read(ALLOW_INFO, true);
    }

    public static boolean allowLink() {
        return read(ALLOW_LINK, true);
    }

    /** Building has a blueprint list, mirror page, shape page, or exchange page to show. */
    public static boolean showBuilding() {
        return allowBlueprints() || allowMirror() || allowShape() || allowExchange();
    }

    /** Highest copy count the server still allows. */
    public static int maxMirrorCopies() {
        if (!read(ALLOW_PATTERN_4, true)) {
            return 2;
        }
        if (!read(ALLOW_PATTERN_8, true)) {
            return 4;
        }
        if (!read(ALLOW_PATTERN_16, true)) {
            return 8;
        }
        return 32;
    }

    public static int allowedPattern(int copies) {
        return Math.min(Mirror.clampCopies(copies), maxMirrorCopies());
    }

    public static int allowedAxes(int axes) {
        return allowMirrorAxes() ? (axes & 7) : 0;
    }

    /** Tells the player and returns true when the feature is off. */
    public static boolean refuse(ServerPlayer player, boolean allowed) {
        if (allowed || player == null) {
            return !allowed;
        }
        Messages.actionBar(player, "message.wrights.feature_off");
        return true;
    }

    /** Copy settings may keep this block's data. Copy all does not ask. */
    public static boolean settingsAllowed(Block block) {
        if (!copySettingsWhitelist()) {
            return true;
        }
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        try {
            for (String id : COPY_SETTINGS_MODS.get()) {
                if (namespace.equals(id.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (IllegalStateException exception) {
            return true;
        }
        return false;
    }

    private static boolean modId(Object value) {
        if (!(value instanceof String id) || id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean read(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.getAsBoolean();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }

    private static int read(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.getAsInt();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }
}
