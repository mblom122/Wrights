package com.wrights;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WrightsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Solid blocks sent in one preview packet. Larger blueprints still copy and build. */
    public static final int MAX_PREVIEW_BLOCKS = 48_000;

    public static final ModConfigSpec.IntValue MAX_AXIS = BUILDER
            .comment("Largest selection length on one axis, in blocks. 512 matches Building Gadgets and RFTools.")
            .defineInRange("maxAxis", 512, 1, 512);

    public static final ModConfigSpec.IntValue MAX_BLOCKS = BUILDER
            .comment("Largest selection volume, in blocks. Air counts toward this cap. 100000 matches Building Gadgets.")
            .defineInRange("maxBlocks", 100_000, 1, 2_097_152);

    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("How many blocks one wright may place in one server tick. A netherite wright places 10 when this allows it.")
            .defineInRange("blocksPerTick", 16, 1, 64);

    public static final ModConfigSpec.IntValue COPY_POSITIONS_PER_TICK = BUILDER
            .comment("How many positions a copy may read in one tick. Reading is cheap, so this can be higher than placement.")
            .defineInRange("copyPositionsPerTick", 1024, 1, 8192);

    public static final ModConfigSpec.IntValue MAX_RANGE = BUILDER
            .comment("The job pauses while you are farther than this many blocks from the block it is working on.")
            .defineInRange("maxRange", 96, 8, 256);

    public static final ModConfigSpec.BooleanValue REQUIRE_CHUNKS_LOADED = BUILDER
            .comment("While true, a job waits for the chunk to load. While false, unloaded positions are skipped.")
            .define("requireChunksLoaded", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private WrightsConfig() {
    }
}
