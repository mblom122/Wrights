package com.autobots;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AutobotsClientConfig {
    public static final int[] COLORS = {
            0xF0C420,
            0xC47A3A,
            0x7EB6FF,
            0x6FCF97,
            0xE7EEF4,
            0xE07A7A
    };

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue WALL_RED = BUILDER
            .comment("Selection wall color, red channel.")
            .defineInRange("wallRed", 240, 0, 255);
    public static final ModConfigSpec.IntValue WALL_GREEN = BUILDER
            .defineInRange("wallGreen", 196, 0, 255);
    public static final ModConfigSpec.IntValue WALL_BLUE = BUILDER
            .defineInRange("wallBlue", 32, 0, 255);
    public static final ModConfigSpec.IntValue WALL_TRANSPARENCY = BUILDER
            .comment("How see-through the selection walls are, from 0 to 100 percent.")
            .defineInRange("wallTransparency", 20, 0, 100);
    public static final ModConfigSpec.IntValue EDGE_RED = BUILDER
            .defineInRange("edgeRed", 255, 0, 255);
    public static final ModConfigSpec.IntValue EDGE_GREEN = BUILDER
            .defineInRange("edgeGreen", 214, 0, 255);
    public static final ModConfigSpec.IntValue EDGE_BLUE = BUILDER
            .defineInRange("edgeBlue", 64, 0, 255);
    public static final ModConfigSpec.IntValue EDGE_OPACITY = BUILDER
            .comment("How solid the selection outline is, from 0 to 100 percent.")
            .defineInRange("edgeOpacity", 100, 0, 100);
    public static final ModConfigSpec.IntValue GHOST_TRANSPARENCY = BUILDER
            .comment("How see-through blueprint blocks are in the world, from 0 to 100 percent. 0 is solid and 100 is invisible.")
            .defineInRange("ghostTransparency", 50, 0, 100);
    public static final ModConfigSpec.BooleanValue CORNER_MARKERS = BUILDER
            .comment("Draw the small blocks on the eight corners of a selection.")
            .define("cornerMarkers", true);
    public static final ModConfigSpec.BooleanValue INSTANT_JOBS = BUILDER
            .comment("When true, building and undoing finish in one step. When false, the drone places and removes a few blocks at a time.")
            .define("instantJobs", false);
    public static final ModConfigSpec.BooleanValue DRONE_MAX = BUILDER
            .comment("When true, a new job uses every free drone. It still never uses more drones than the job has blocks.")
            .define("droneMax", false);
    public static final ModConfigSpec.IntValue DRONE_COUNT = BUILDER
            .comment("How many drones a new job uses when droneMax is false. Still limited by free drones and by the number of blocks.")
            .defineInRange("droneCount", 5, 1, 8);
    public static final ModConfigSpec.IntValue PLACE_X = BUILDER
            .comment("Blocks to your right when a paste is not aimed at a block.")
            .defineInRange("placeX", 0, -32, 32);
    public static final ModConfigSpec.IntValue PLACE_Y = BUILDER
            .comment("Blocks up from your feet when a paste is not aimed at a block.")
            .defineInRange("placeY", 0, -32, 32);
    public static final ModConfigSpec.IntValue PLACE_Z = BUILDER
            .comment("Blocks in front of you when a paste is not aimed at a block.")
            .defineInRange("placeZ", 1, -32, 32);
    public static final ModConfigSpec.IntValue LAST_MODE = BUILDER
            .comment("Last controller task. 0 ready, 1 new blueprint, 2 building, 3 jobs.")
            .defineInRange("lastMode", 0, 0, 3);
    public static final ModConfigSpec.IntValue LAST_STYLE = BUILDER
            .comment("Last new-blueprint tool. 0 corners, 1 box, 2 move.")
            .defineInRange("lastStyle", 1, 0, 2);
    public static final ModConfigSpec.ConfigValue<String> LAST_BUILD = BUILDER
            .comment("Last blueprint that was being built.")
            .define("lastBuild", "");
    public static final ModConfigSpec.ConfigValue<String> RECENT_BLOCKS = BUILDER
            .comment("Blocks recently picked while editing a blueprint.")
            .define("recentBlocks", "");

    static final ModConfigSpec SPEC = BUILDER.build();

    private AutobotsClientConfig() {
    }

    public static int wallRgb() {
        return (WALL_RED.get() << 16) | (WALL_GREEN.get() << 8) | WALL_BLUE.get();
    }

    public static int edgeRgb() {
        return (EDGE_RED.get() << 16) | (EDGE_GREEN.get() << 8) | EDGE_BLUE.get();
    }

    public static float wallAlpha() {
        return 1.0F - WALL_TRANSPARENCY.get() / 100.0F;
    }

    public static float ghostAlpha() {
        return 1.0F - GHOST_TRANSPARENCY.get() / 100.0F;
    }

    public static float edgeAlpha() {
        return EDGE_OPACITY.get() / 100.0F;
    }

    public static float channel(int rgb, int shift) {
        return ((rgb >> shift) & 255) / 255.0F;
    }

    public static void setWallRgb(int rgb) {
        WALL_RED.set((rgb >> 16) & 255);
        WALL_GREEN.set((rgb >> 8) & 255);
        WALL_BLUE.set(rgb & 255);
        save();
    }

    public static void setEdgeRgb(int rgb) {
        EDGE_RED.set((rgb >> 16) & 255);
        EDGE_GREEN.set((rgb >> 8) & 255);
        EDGE_BLUE.set(rgb & 255);
        save();
    }

    public static void setWallTransparency(int value) {
        WALL_TRANSPARENCY.set(clamp(value, 0, 100));
    }

    public static void setGhostTransparency(int value) {
        GHOST_TRANSPARENCY.set(clamp(value, 0, 100));
    }

    public static void setEdgeOpacity(int value) {
        EDGE_OPACITY.set(clamp(value, 0, 100));
    }

    public static void setPlace(int x, int y, int z) {
        PLACE_X.set(clamp(x, -32, 32));
        PLACE_Y.set(clamp(y, -32, 32));
        PLACE_Z.set(clamp(z, -32, 32));
        save();
    }

    public static void setCornerMarkers(boolean enabled) {
        CORNER_MARKERS.set(enabled);
        save();
    }

    public static void setInstantJobs(boolean enabled) {
        INSTANT_JOBS.set(enabled);
        save();
    }

    public static void setDroneMax(boolean enabled) {
        DRONE_MAX.set(enabled);
        save();
    }

    public static void setDroneCount(int count) {
        DRONE_COUNT.set(clamp(count, 1, 8));
        save();
    }

    public static int deployTarget(int freeDrones, int blocks) {
        if (freeDrones <= 0 || blocks <= 0) {
            return 0;
        }
        int wanted = DRONE_MAX.get() ? freeDrones : DRONE_COUNT.get();
        return Math.max(1, Math.min(wanted, Math.min(freeDrones, blocks)));
    }

    public static void save() {
        SPEC.save();
    }

    public static void setRecentBlocks(String ids) {
        RECENT_BLOCKS.set(ids == null ? "" : ids);
        save();
    }

    public static void setLastMode(int mode, int style, String buildId) {
        LAST_MODE.set(clamp(mode, 0, 3));
        LAST_STYLE.set(clamp(style, 0, 2));
        LAST_BUILD.set(buildId == null ? "" : buildId);
        save();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
