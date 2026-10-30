package com.wrights;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WrightsClientConfig {
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
            .comment("When true, a new job uses every free drone. It still never uses more drones than the job has blocks, or more than maxWrightsPerJob in the server config.")
            .define("droneMax", false);
    public static final ModConfigSpec.IntValue DRONE_COUNT = BUILDER
            .comment("How many drones a new job uses when droneMax is false. Still limited by free drones, by the number of blocks, and by maxWrightsPerJob in the server config.")
            .defineInRange("droneCount", 5, 1, 512);
    public static final ModConfigSpec.BooleanValue BUILD_MENU = BUILDER
            .comment("When true, right-click in build mode opens the build menu instead of starting the job.")
            .define("buildMenu", false);
    public static final ModConfigSpec.BooleanValue COPY_FLUIDS = BUILDER
            .comment("When true, a new blueprint also saves water and lava inside the box.")
            .define("copyFluids", false);
    public static final ModConfigSpec.BooleanValue COPY_NBT_BLOCKS = BUILDER
            .comment("When true, a new blueprint also saves chests and machines as empty / freshly placed blocks (no inventory contents).")
            .define("copyNbtBlocks", false);
    public static final ModConfigSpec.BooleanValue COPY_NBT_SETTINGS = BUILDER
            .comment("When true with copyNbtBlocks, also saves machine settings (sides, modes, filters) but never items, fluids, energy, or upgrades.")
            .define("copyNbtSettings", false);
    public static final ModConfigSpec.BooleanValue COPY_NBT_ALL = BUILDER
            .comment("When true with copyNbtBlocks, saves every tag on the block. Creative only, and the server can turn it off.")
            .define("copyNbtAll", false);
    public static final ModConfigSpec.BooleanValue PLACE_FLUIDS = BUILDER
            .comment("When true, a build places water and lava saved in the blueprint.")
            .define("placeFluids", true);
    public static final ModConfigSpec.BooleanValue VOID_BLOCKS = BUILDER
            .comment("When true, a build removes a block that does not match the blueprint before placing.")
            .define("voidBlocks", false);
    public static final ModConfigSpec.BooleanValue VOID_FLUIDS = BUILDER
            .comment("When true, a build removes water or lava that does not match the blueprint before placing.")
            .define("voidFluids", false);
    public static final ModConfigSpec.BooleanValue VOID_NBT = BUILDER
            .comment("When true, voiding may also remove chests and other blocks that store contents. Off leaves those blocks alone.")
            .define("voidNbt", false);
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
            .comment("Last controller task. 0 ready, 1 new blueprint, 2 building, 3 jobs, 4 manage, 5 info.")
            .defineInRange("lastMode", 0, 0, 5);
    public static final ModConfigSpec.IntValue LAST_STYLE = BUILDER
            .comment("Last new-blueprint tool. 0 corners, 1 box, 2 move.")
            .defineInRange("lastStyle", 1, 0, 2);
    public static final ModConfigSpec.ConfigValue<String> LAST_BUILD = BUILDER
            .comment("Last blueprint that was being built.")
            .define("lastBuild", "");
    public static final ModConfigSpec.ConfigValue<String> RECENT_BLOCKS = BUILDER
            .comment("Blocks recently picked while editing a blueprint.")
            .define("recentBlocks", "");
    public static final ModConfigSpec.ConfigValue<String> LIST_SORT = BUILDER
            .comment("Sort for each blueprint tab: key and direction. Keys are manual, name, size, blocks, used, last used.")
            .define("listSort", "0:1,5:0,4:0,1:1");
    public static final ModConfigSpec.ConfigValue<String> LIST_MANUAL = BUILDER
            .comment("Last hand-ordered blueprint list for each tab, kept when another sort is chosen.")
            .define("listManual", "");
    public static final ModConfigSpec.BooleanValue PORT_PLAYER_FIRST = BUILDER
            .comment("When true, a job takes wrights from you before a wrightport block. When false, the block is used first.")
            .define("portPlayerFirst", true);
    public static final ModConfigSpec.BooleanValue SUPPLY_PLAYER_FIRST = BUILDER
            .comment("When true, a build takes blocks from your inventory before a bound chest. When false, the chest is used first.")
            .define("supplyPlayerFirst", true);
    public static final ModConfigSpec.IntValue HUD_HINT = BUILDER
            .comment("Where controller hints sit. 0 top, 1 above the hotbar, 2 left, 3 right, 4 middle, 5 a dragged spot.")
            .defineInRange("hudHint", 1, 0, 5);
    public static final ModConfigSpec.IntValue HUD_HINT_X = BUILDER
            .defineInRange("hudHintX", 500, 0, 1000);
    public static final ModConfigSpec.IntValue HUD_HINT_Y = BUILDER
            .defineInRange("hudHintY", 820, 0, 1000);
    public static final ModConfigSpec.IntValue HUD_TOAST = BUILDER
            .comment("Where job toasts sit. Same spots as hudHint.")
            .defineInRange("hudToast", 3, 0, 5);
    public static final ModConfigSpec.IntValue HUD_TOAST_X = BUILDER
            .defineInRange("hudToastX", 900, 0, 1000);
    public static final ModConfigSpec.IntValue HUD_TOAST_Y = BUILDER
            .defineInRange("hudToastY", 80, 0, 1000);

    static final ModConfigSpec SPEC = BUILDER.build();

    private WrightsClientConfig() {
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
        DRONE_COUNT.set(clamp(count, 1, Math.min(512, WrightsConfig.maxWrightsPerJob())));
        save();
    }

    public static void setBuildMenu(boolean enabled) {
        BUILD_MENU.set(enabled);
        save();
    }

    public static void setCopyFluids(boolean enabled) {
        COPY_FLUIDS.set(enabled);
        save();
    }

    public static void setCopyNbtBlocks(boolean enabled) {
        COPY_NBT_BLOCKS.set(enabled);
        if (!enabled) {
            COPY_NBT_SETTINGS.set(false);
            COPY_NBT_ALL.set(false);
        }
        save();
    }

    public static void setCopyNbtSettings(boolean enabled) {
        if (enabled && !COPY_NBT_BLOCKS.get()) {
            COPY_NBT_BLOCKS.set(true);
        }
        if (enabled) {
            COPY_NBT_ALL.set(false);
        }
        COPY_NBT_SETTINGS.set(enabled);
        save();
    }

    public static void setCopyNbtAll(boolean enabled) {
        if (enabled && !COPY_NBT_BLOCKS.get()) {
            COPY_NBT_BLOCKS.set(true);
        }
        if (enabled) {
            COPY_NBT_SETTINGS.set(false);
        }
        COPY_NBT_ALL.set(enabled);
        save();
    }

    public static void setPlaceFluids(boolean enabled) {
        PLACE_FLUIDS.set(enabled);
        save();
    }

    public static void setVoidBlocks(boolean enabled) {
        VOID_BLOCKS.set(enabled);
        save();
    }

    public static void setVoidFluids(boolean enabled) {
        VOID_FLUIDS.set(enabled);
        save();
    }

    public static void setVoidNbt(boolean enabled) {
        VOID_NBT.set(enabled);
        save();
    }

    public static void setPortPlayerFirst(boolean enabled) {
        PORT_PLAYER_FIRST.set(enabled);
        save();
    }

    public static void setSupplyPlayerFirst(boolean enabled) {
        SUPPLY_PLAYER_FIRST.set(enabled);
        save();
    }

    public static void setHudSpot(boolean hint, int spot) {
        (hint ? HUD_HINT : HUD_TOAST).set(clamp(spot, 0, 5));
        save();
    }

    public static void setHudCustom(boolean hint, int x, int y) {
        (hint ? HUD_HINT : HUD_TOAST).set(5);
        (hint ? HUD_HINT_X : HUD_TOAST_X).set(clamp(x, 0, 1000));
        (hint ? HUD_HINT_Y : HUD_TOAST_Y).set(clamp(y, 0, 1000));
    }

    public static int deployTarget(int freeDrones, int blocks) {
        if (freeDrones <= 0 || blocks <= 0) {
            return 0;
        }
        int wanted = DRONE_MAX.get() ? freeDrones : DRONE_COUNT.get();
        return Math.max(1, Math.min(wanted, Math.min(freeDrones, Math.min(blocks, WrightsConfig.maxWrightsPerJob()))));
    }

    public static void save() {
        SPEC.save();
    }

    public static int sortKey(int tab) {
        return sortPair(tab)[0];
    }

    public static boolean sortAscending(int tab) {
        return sortPair(tab)[1] == 1;
    }

    public static void setListSort(int tab, int key, boolean ascending) {
        int[][] pairs = new int[4][];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = sortPair(i);
        }
        int index = clamp(tab, 0, pairs.length - 1);
        pairs[index][0] = clamp(key, 0, 5);
        pairs[index][1] = ascending ? 1 : 0;
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                joined.append(',');
            }
            joined.append(pairs[i][0]).append(':').append(pairs[i][1]);
        }
        LIST_SORT.set(joined.toString());
        save();
    }

    public static List<UUID> manualOrder(int tab) {
        String raw = LIST_MANUAL.get();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String prefix = clamp(tab, 0, 3) + ":";
        for (String part : raw.split("\\|")) {
            if (!part.startsWith(prefix)) {
                continue;
            }
            String body = part.substring(prefix.length());
            if (body.isEmpty()) {
                return List.of();
            }
            List<UUID> ids = new ArrayList<>();
            for (String token : body.split(",")) {
                try {
                    ids.add(UUID.fromString(token));
                } catch (IllegalArgumentException ignored) {
                    // A damaged entry is skipped so the rest of the order still loads.
                }
            }
            return ids;
        }
        return List.of();
    }

    public static void setManualOrder(int tab, List<UUID> ids) {
        String[] parts = new String[] {"0:", "1:", "2:", "3:"};
        String raw = LIST_MANUAL.get();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split("\\|")) {
                int colon = part.indexOf(':');
                if (colon != 1) {
                    continue;
                }
                int which = part.charAt(0) - '0';
                if (which >= 0 && which < parts.length) {
                    parts[which] = part;
                }
            }
        }
        int index = clamp(tab, 0, parts.length - 1);
        StringBuilder body = new StringBuilder();
        body.append(index).append(':');
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append(ids.get(i));
        }
        parts[index] = body.toString();
        LIST_MANUAL.set(String.join("|", parts));
        save();
    }

    private static int[] sortPair(int tab) {
        int[][] defaults = {{0, 1}, {5, 0}, {4, 0}, {1, 1}};
        int index = clamp(tab, 0, defaults.length - 1);
        int[] pair = defaults[index].clone();
        String raw = LIST_SORT.get();
        if (raw == null || raw.isEmpty()) {
            return pair;
        }
        String[] parts = raw.split(",");
        if (index >= parts.length) {
            return pair;
        }
        String[] bits = parts[index].split(":");
        if (bits.length < 2) {
            return pair;
        }
        try {
            pair[0] = clamp(Integer.parseInt(bits[0].trim()), 0, 5);
            pair[1] = "1".equals(bits[1].trim()) ? 1 : 0;
        } catch (NumberFormatException ignored) {
            return defaults[index].clone();
        }
        return pair;
    }

    public static void setRecentBlocks(String ids) {
        RECENT_BLOCKS.set(ids == null ? "" : ids);
        save();
    }

    public static void setLastMode(int mode, int style, String buildId) {
        LAST_MODE.set(clamp(mode, 0, 5));
        LAST_STYLE.set(clamp(style, 0, 2));
        LAST_BUILD.set(buildId == null ? "" : buildId);
        save();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
