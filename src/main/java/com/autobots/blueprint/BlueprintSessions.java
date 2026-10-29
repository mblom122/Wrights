package com.autobots.blueprint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.autobots.AutobotsMod;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID)
public final class BlueprintSessions {
    public enum Mode {
        NONE,
        CAPTURE,
        SAVING,
        BUILD
    }

    private static final Map<UUID, BlueprintSession> SESSIONS = new HashMap<>();

    private BlueprintSessions() {
    }

    public static BlueprintSession get(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new BlueprintSession());
    }

    public static boolean suppressController(ServerPlayer player) {
        Mode mode = get(player).mode;
        return mode == Mode.CAPTURE || mode == Mode.SAVING || mode == Mode.BUILD;
    }

    public static void remove(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.autobots.item.ControllerItem.quietLegacy(player);
            com.autobots.network.BlueprintNetwork.syncCapture(player);
            com.autobots.network.BlueprintNetwork.syncJobHolograms(player);
        }
    }

    public static final class BlueprintSession {
        private Mode mode = Mode.NONE;
        private BlockPos cornerA;
        private BlockPos cornerB;
        private ResourceKey<Level> dimension;
        private String name = "Blueprint";
        private UUID editingId;
        private UUID buildId;
        private int generation;
        private int seq;

        public Mode mode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public BlockPos cornerA() {
            return cornerA;
        }

        public void setCornerA(BlockPos cornerA) {
            this.cornerA = cornerA;
        }

        public BlockPos cornerB() {
            return cornerB;
        }

        public void setCornerB(BlockPos cornerB) {
            this.cornerB = cornerB;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public void setDimension(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public UUID editingId() {
            return editingId;
        }

        public void setEditingId(UUID editingId) {
            this.editingId = editingId;
        }

        public UUID buildId() {
            return buildId;
        }

        public void setBuildId(UUID buildId) {
            this.buildId = buildId;
        }

        public int generation() {
            return generation;
        }

        public int seq() {
            return seq;
        }

        public void setSeq(int seq) {
            this.seq = seq;
        }

        public void bump() {
            generation++;
            seq = 0;
        }

        public void startCapture(ResourceKey<Level> dimension) {
            mode = Mode.CAPTURE;
            cornerA = null;
            cornerB = null;
            editingId = null;
            buildId = null;
            name = "Blueprint";
            this.dimension = dimension;
            bump();
        }

        public void clear() {
            mode = Mode.NONE;
            cornerA = null;
            cornerB = null;
            editingId = null;
            buildId = null;
            bump();
        }
    }
}
