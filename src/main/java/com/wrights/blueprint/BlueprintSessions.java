package com.wrights.blueprint;

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

import com.wrights.WrightsMod;
import com.wrights.schematic.Schematic;

@EventBusSubscriber(modid = WrightsMod.MOD_ID)
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
            com.wrights.item.ControllerItem.quietLegacy(player);
            com.wrights.network.BlueprintNetwork.syncCapture(player);
            com.wrights.network.BlueprintNetwork.syncJobHolograms(player);
            com.wrights.network.BlueprintNetwork.syncSupply(player);
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
        private Schematic draft;
        private boolean draftDirty;
        private Schematic editBaseline;
        private boolean editWasDraft;
        private boolean editBaselineDirty;
        private boolean instantJobs;
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

        public Schematic draft() {
            return draft;
        }

        public void setDraft(Schematic draft) {
            this.draft = draft;
        }

        public boolean draftDirty() {
            return draftDirty;
        }

        public void setDraftDirty(boolean draftDirty) {
            this.draftDirty = draftDirty;
        }

        public Schematic editBaseline() {
            return editBaseline;
        }

        public boolean editWasDraft() {
            return editWasDraft;
        }

        public boolean editBaselineDirty() {
            return editBaselineDirty;
        }

        public void armEdit(Schematic baseline, boolean wasDraft, boolean baselineDirty) {
            this.editBaseline = baseline;
            this.editWasDraft = wasDraft;
            this.editBaselineDirty = baselineDirty;
        }

        public void clearEdit() {
            editBaseline = null;
            editWasDraft = false;
            editBaselineDirty = false;
        }

        public boolean instantJobs() {
            return instantJobs;
        }

        public void setInstantJobs(boolean instantJobs) {
            this.instantJobs = instantJobs;
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
            draft = null;
            draftDirty = false;
            clearEdit();
            name = "Blueprint";
            this.dimension = dimension;
            bump();
        }

        private BlockPos supplyPos;

        public BlockPos supplyPos() {
            return supplyPos;
        }

        public void setSupply(BlockPos supplyPos) {
            this.supplyPos = supplyPos;
        }

        public void clear() {
            mode = Mode.NONE;
            cornerA = null;
            cornerB = null;
            editingId = null;
            buildId = null;
            draft = null;
            draftDirty = false;
            clearEdit();
            bump();
        }
    }
}
