package com.wrights;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

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

    public static final ModConfigSpec.IntValue MAX_AXIS = BUILDER
            .comment(
                    "Largest selection length on one axis, in blocks. 512 matches Building Gadgets and RFTools.",
                    "A larger box uses more memory and can lag the server while it is copied or built.")
            .defineInRange("maxAxis", 512, 1, 4096);

    public static final ModConfigSpec.IntValue MAX_BLOCKS = BUILDER
            .comment(
                    "Largest selection volume, in blocks. Air counts toward this cap. 100000 matches Building Gadgets.",
                    "A larger volume uses more memory and can lag the server while it is copied or built.")
            .defineInRange("maxBlocks", 100_000, 1, 16_777_216);

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

    static final ModConfigSpec SPEC = BUILDER.build();

    private WrightsConfig() {
    }

    public static int maxWrightsPerJob() {
        return read(MAX_WRIGHTS_PER_JOB, 10);
    }

    public static int maxWrightsCarried() {
        return read(MAX_WRIGHTS_CARRIED, 64);
    }

    public static int maxPreviewBlocks() {
        return read(MAX_PREVIEW_BLOCKS, 48_000);
    }

    public static int ghostBudget() {
        return read(GHOST_BUDGET, 4096);
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
