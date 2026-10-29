package com.autobots.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

import com.autobots.AutobotsConfig;
import com.autobots.item.ControllerItem;
import com.autobots.job.JobManager;
import com.autobots.network.BlueprintNetwork;
import com.autobots.network.BlueprintPayloads;
import com.autobots.schematic.Requirements;
import com.autobots.schematic.Schematic;
import com.autobots.schematic.SchematicLibrary;
import com.autobots.util.Messages;

public final class BlueprintActions {
    private BlueprintActions() {
    }

    public static String cleanName(String raw) {
        if (raw == null) {
            return "Blueprint";
        }
        String trimmed = raw.trim().replace('\r', ' ').replace('\n', ' ');
        if (trimmed.isEmpty()) {
            return "Blueprint";
        }
        return trimmed.length() > 48 ? trimmed.substring(0, 48) : trimmed;
    }

    public static void requestLibrary(ServerPlayer player) {
        if (!held(player)) {
            return;
        }
        BlueprintNetwork.syncLibrary(player);
    }

    public static void resume(ServerPlayer player, int mode, java.util.UUID buildId) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        com.autobots.item.ControllerItem.quietLegacy(player);
        if (session.mode() != BlueprintSessions.Mode.NONE) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (mode == 2 && buildId != null && !buildId.equals(net.minecraft.Util.NIL_UUID)) {
            beginBuild(player, buildId);
            return;
        }
        if (mode == 1) {
            session.startCapture(player.level().dimension());
            BlueprintNetwork.syncCapture(player);
        }
    }

    public static void startCapture(ServerPlayer player) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        session.startCapture(player.level().dimension());
        BlueprintNetwork.syncCapture(player);
        Messages.actionBar(player, "message.autobots.capture_first");
    }

    public static void pick(ServerPlayer player, BlockPos pos, int action) {
        if (!held(player) || pos == null) {
            return;
        }
        if (action == BlueprintPayloads.CapturePick.SECOND) {
            setCorner(player, pos, true);
        } else if (action == BlueprintPayloads.CapturePick.BOX_BEGIN) {
            beginBox(player, pos);
        } else if (action == BlueprintPayloads.CapturePick.BOX_COMMIT) {
            commitBox(player, pos);
        } else {
            setCorner(player, pos, false);
        }
    }

    public static void setCorner(ServerPlayer player, BlockPos pos, boolean wantSecond) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (session.dimension() != null && !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.autobots.different_dimension");
            return;
        }
        if (session.dimension() == null) {
            session.setDimension(player.level().dimension());
        }
        boolean second = wantSecond && session.cornerA() != null;
        BlockPos nextA = second ? session.cornerA() : pos;
        BlockPos nextB = second ? pos : session.cornerB();
        if (nextA != null && nextB != null && !acceptBox(player, nextA, nextB)) {
            return;
        }
        session.setCornerA(nextA);
        session.setCornerB(nextB);
        session.bump();
        chime(player, 1.5F);
        if (nextB == null) {
            Messages.actionBar(player, "message.autobots.corner1", nextA.getX(), nextA.getY(), nextA.getZ());
        } else {
            Messages.actionBar(player, "message.autobots.corner2",
                    BoxEdits.span(nextA, nextB, 0),
                    BoxEdits.span(nextA, nextB, 1),
                    BoxEdits.span(nextA, nextB, 2),
                    BoxEdits.volume(nextA, nextB),
                    BoxEdits.solid(player.level(), nextA, nextB));
        }
        BlueprintNetwork.syncCapture(player);
    }

    private static void beginBox(ServerPlayer player, BlockPos pos) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (session.dimension() != null && !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.autobots.different_dimension");
            return;
        }
        if (session.dimension() == null) {
            session.setDimension(player.level().dimension());
        }
        if (!BoxEdits.inWorld(pos)) {
            Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
            deny(player);
            return;
        }
        session.setCornerA(pos);
        session.setCornerB(null);
        session.bump();
        chime(player, 1.5F);
        Messages.actionBar(player, "message.autobots.corner1", pos.getX(), pos.getY(), pos.getZ());
        BlueprintNetwork.syncCapture(player);
    }

    private static void commitBox(ServerPlayer player, BlockPos pos) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (session.cornerA() == null) {
            beginBox(player, pos);
            return;
        }
        if (session.dimension() != null && !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.autobots.different_dimension");
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (!acceptBox(player, session.cornerA(), pos)) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        session.setCornerB(pos);
        session.bump();
        chime(player, 1.6F);
        Messages.actionBar(player, "message.autobots.corner2",
                BoxEdits.span(session.cornerA(), pos, 0),
                BoxEdits.span(session.cornerA(), pos, 1),
                BoxEdits.span(session.cornerA(), pos, 2),
                BoxEdits.volume(session.cornerA(), pos),
                BoxEdits.solid(player.level(), session.cornerA(), pos));
        BlueprintNetwork.syncCapture(player);
    }

    public static void updateCapture(ServerPlayer player, int generation, int seq, boolean hasA, BlockPos cornerA, boolean hasB, BlockPos cornerB, String name) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (generation != session.generation() || seq < session.seq()) {
            return;
        }
        if (session.dimension() != null && !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.autobots.different_dimension");
            session.setSeq(seq);
            BlueprintNetwork.syncCapture(player);
            return;
        }
        session.setSeq(seq);
        session.setName(cleanName(name));
        if (!hasA || cornerA == null) {
            session.setCornerA(null);
            session.setCornerB(null);
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (!BoxEdits.inWorld(cornerA)) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (!hasB || cornerB == null) {
            session.setCornerA(cornerA);
            session.setCornerB(null);
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (!acceptBox(player, cornerA, cornerB)) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        session.setCornerA(cornerA);
        session.setCornerB(cornerB);
        BlueprintNetwork.syncCapture(player);
    }

    public static void confirm(ServerPlayer player, String name) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (session.cornerA() == null || session.cornerB() == null) {
            Messages.actionBar(player, "message.autobots.need_corners");
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (session.dimension() == null || !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.autobots.different_dimension");
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (!acceptBox(player, session.cornerA(), session.cornerB())) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        String clean = cleanName(name);
        session.setName(clean);
        session.setMode(BlueprintSessions.Mode.SAVING);
        BlueprintNetwork.syncCapture(player);
        boolean started = JobManager.startBlueprintSave(
                player,
                clean,
                session.editingId(),
                session.dimension(),
                session.cornerA(),
                session.cornerB());
        if (!started) {
            session.setMode(BlueprintSessions.Mode.CAPTURE);
            BlueprintNetwork.syncCapture(player);
        }
    }

    public static void cancel(ServerPlayer player) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.get(player).clear();
        BlueprintNetwork.syncCapture(player);
    }

    public static void finishSave(ServerPlayer player, java.util.UUID id, boolean saved) {
        BlueprintSessions.get(player).clear();
        BlueprintNetwork.syncLibrary(player);
        BlueprintNetwork.syncCapture(player);
        BlueprintNetwork.finished(player, saved && id != null ? id : net.minecraft.Util.NIL_UUID, saved);
    }

    public static void delete(ServerPlayer player, java.util.UUID id) {
        if (!held(player)) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (library.isInUse(id)) {
            Messages.actionBar(player, "message.autobots.blueprint_in_use");
            return;
        }
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        library.removeSchematic(id);
        Messages.actionBar(player, "message.autobots.blueprint_deleted", schematic.name());
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (id.equals(session.buildId())) {
            session.clear();
            BlueprintNetwork.syncCapture(player);
        }
        BlueprintNetwork.syncLibrary(player);
    }

    public static void rename(ServerPlayer player, java.util.UUID id, String name) {
        if (!held(player)) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        String clean = cleanName(name);
        library.putSchematic(schematic.withName(clean));
        Messages.actionBar(player, "message.autobots.blueprint_renamed", clean);
        BlueprintNetwork.syncLibrary(player);
    }

    public static void beginEdit(ServerPlayer player, java.util.UUID id) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = editable(player, id, false);
        if (schematic == null) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        boolean drafting = building(player, id);
        session.armEdit(schematic, drafting, drafting && session.draftDirty());
        BlueprintNetwork.preview(player, schematic, drafting, true);
    }

    public static void finishEdit(ServerPlayer player, boolean keep) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        Schematic baseline = session.editBaseline();
        boolean wasDraft = session.editWasDraft();
        boolean baselineDirty = session.editBaselineDirty();
        session.clearEdit();
        if (baseline == null) {
            return;
        }
        if (wasDraft && session.mode() == BlueprintSessions.Mode.BUILD && baseline.id().equals(session.buildId())) {
            if (!keep) {
                session.setDraft(baseline);
                session.setDraftDirty(baselineDirty);
                BlueprintNetwork.preview(player, baseline, false, false);
                Messages.actionBar(player, "message.autobots.edit_discarded");
                return;
            }
            Schematic draft = session.draft();
            if (draft != null && draft != baseline) {
                Messages.actionBar(player, "message.autobots.session_saved", draft.name());
            } else {
                Messages.actionBar(player, "message.autobots.session_clean");
            }
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic current = library.schematic(baseline.id()).orElse(null);
        if (keep) {
            if (current != null && current != baseline) {
                Messages.actionBar(player, "message.autobots.session_saved", current.name());
            } else {
                Messages.actionBar(player, "message.autobots.session_clean");
            }
            return;
        }
        if (current != baseline) {
            library.putSchematic(baseline);
            BlueprintNetwork.syncLibrary(player);
            BlueprintNetwork.preview(player, baseline, false, false);
            Messages.actionBar(player, "message.autobots.edit_discarded");
        }
    }

    public static void recapture(ServerPlayer player, java.util.UUID id) {
        if (!held(player)) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        if (library.isInUse(id)) {
            Messages.actionBar(player, "message.autobots.blueprint_in_use");
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        session.startCapture(player.level().dimension());
        session.setEditingId(id);
        session.setName(schematic.name());
        boolean samePlace = schematic.hasSource()
                && schematic.sourceDimension().equals(player.level().dimension().location().toString());
        if (samePlace) {
            session.setCornerA(schematic.sourceMin());
            session.setCornerB(schematic.sourceMax());
            if (!BoxEdits.fits(schematic.sourceMin(), schematic.sourceMax())) {
                session.setCornerA(null);
                session.setCornerB(null);
                Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
            }
        } else if (schematic.hasSource()) {
            Messages.actionBar(player, "message.autobots.edit_other_dimension");
        } else {
            Messages.actionBar(player, "message.autobots.capture_first");
        }
        BlueprintNetwork.syncCapture(player);
    }

    public static void setAnchor(ServerPlayer player, java.util.UUID id, int x, int y, int z) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = editable(player, id, true);
        if (schematic == null) {
            return;
        }
        remember(player, id, schematic.withAnchor(x, y, z));
    }

    public static void editCell(ServerPlayer player, java.util.UUID id, int x, int y, int z, int stateId, boolean remove) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = editable(player, id, true);
        if (schematic == null) {
            return;
        }
        if (x < 0 || y < 0 || z < 0 || x >= schematic.sizeX() || y >= schematic.sizeY() || z >= schematic.sizeZ()) {
            return;
        }
        java.util.ArrayList<Schematic.Entry> next = new java.util.ArrayList<>();
        for (Schematic.Entry entry : schematic.entries()) {
            if (entry.x() == x && entry.y() == y && entry.z() == z) {
                continue;
            }
            next.add(entry);
        }
        if (!remove) {
            net.minecraft.world.level.block.state.BlockState state = net.minecraft.world.level.block.Block.stateById(stateId);
            if (!com.autobots.util.BlockFilters.canStore(state)) {
                Messages.actionBar(player, "message.autobots.cannot_store");
                return;
            }
            if (next.size() >= AutobotsConfig.MAX_BLOCKS.getAsInt()) {
                Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
                return;
            }
            next.add(new Schematic.Entry((short) x, (short) y, (short) z, state, com.autobots.util.BlockFilters.isSecondary(state)));
            next.sort(java.util.Comparator.comparing(Schematic.Entry::secondary));
        }
        remember(player, id, schematic.withEntries(next));
    }

    public static void editLayer(ServerPlayer player, java.util.UUID id, int operation, int from, int to) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = editable(player, id, true);
        if (schematic == null) {
            return;
        }
        int height = Math.max(1, schematic.sizeY());
        int source = Math.max(0, Math.min(height - 1, from));
        java.util.ArrayList<Schematic.Entry> entries = new java.util.ArrayList<>();
        int anchorY = schematic.anchorY();
        if (operation == BlueprintPayloads.EditLayer.REMOVE) {
            if (height == 1) {
                for (Schematic.Entry entry : schematic.entries()) {
                    if (entry.y() != source) {
                        entries.add(entry);
                    }
                }
                storeLayout(player, id, schematic, 1, entries, 0);
                return;
            }
            for (Schematic.Entry entry : schematic.entries()) {
                if (entry.y() == source) {
                    continue;
                }
                int y = entry.y() > source ? entry.y() - 1 : entry.y();
                entries.add(new Schematic.Entry(entry.x(), (short) y, entry.z(), entry.state(), entry.secondary()));
            }
            if (anchorY == source) {
                anchorY = Math.max(0, source - 1);
            } else if (anchorY > source) {
                anchorY--;
            }
            storeLayout(player, id, schematic, height - 1, entries, anchorY);
            return;
        }
        if (operation == BlueprintPayloads.EditLayer.INSERT) {
            if (height >= AutobotsConfig.MAX_AXIS.getAsInt()) {
                Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
                return;
            }
            int at = Math.max(0, Math.min(height, to));
            shiftUp(schematic, entries, at);
            if (anchorY >= at) {
                anchorY++;
            }
            storeLayout(player, id, schematic, height + 1, entries, anchorY);
            return;
        }
        if (operation == BlueprintPayloads.EditLayer.MOVE) {
            int dest = Math.max(0, Math.min(height - 1, to));
            if (dest == source) {
                return;
            }
            for (Schematic.Entry entry : schematic.entries()) {
                int y = movedY(entry.y(), source, dest);
                entries.add(new Schematic.Entry(entry.x(), (short) y, entry.z(), entry.state(), entry.secondary()));
            }
            storeLayout(player, id, schematic, height, entries, movedY(anchorY, source, dest));
            return;
        }
        if (operation == BlueprintPayloads.EditLayer.COPY) {
            int dest = Math.max(0, Math.min(height, to));
            int added = 0;
            for (Schematic.Entry entry : schematic.entries()) {
                if (entry.y() == source) {
                    added++;
                }
            }
            if (height >= AutobotsConfig.MAX_AXIS.getAsInt() || schematic.blockCount() + added > AutobotsConfig.MAX_BLOCKS.getAsInt()) {
                Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
                return;
            }
            shiftUp(schematic, entries, dest);
            for (Schematic.Entry entry : schematic.entries()) {
                if (entry.y() != source) {
                    continue;
                }
                entries.add(new Schematic.Entry(entry.x(), (short) dest, entry.z(), entry.state(), entry.secondary()));
            }
            entries.sort(java.util.Comparator.comparing(Schematic.Entry::secondary));
            if (anchorY >= dest) {
                anchorY++;
            }
            storeLayout(player, id, schematic, height + 1, entries, anchorY);
        }
    }

    private static void shiftUp(Schematic schematic, java.util.List<Schematic.Entry> into, int at) {
        for (Schematic.Entry entry : schematic.entries()) {
            int y = entry.y() >= at ? entry.y() + 1 : entry.y();
            into.add(new Schematic.Entry(entry.x(), (short) y, entry.z(), entry.state(), entry.secondary()));
        }
    }

    private static int movedY(int y, int from, int dest) {
        if (y == from) {
            return dest;
        }
        if (from < dest && y > from && y <= dest) {
            return y - 1;
        }
        if (dest < from && y >= dest && y < from) {
            return y + 1;
        }
        return y;
    }

    private static void storeLayout(ServerPlayer player, java.util.UUID id, Schematic schematic, int sizeY, java.util.List<Schematic.Entry> entries, int anchorY) {
        remember(player, id, schematic.withLayout(sizeY, entries, anchorY));
    }

    public static void transform(ServerPlayer player, java.util.UUID id, int operation) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = editable(player, id, true);
        if (schematic == null) {
            return;
        }
        BlueprintTransforms.Op[] ops = BlueprintTransforms.Op.values();
        if (operation < 0 || operation >= ops.length) {
            return;
        }
        remember(player, id, BlueprintTransforms.apply(schematic, ops[operation]));
    }

    public static void previewBlueprint(ServerPlayer player, java.util.UUID id) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = SchematicLibrary.get(player.getServer()).schematic(id).orElse(null);
        if (schematic == null) {
            return;
        }
        BlueprintNetwork.preview(player, schematic, false, false);
    }

    public static void sendMaterials(ServerPlayer player, java.util.UUID id, int clipX, int clipY, int clipZ) {
        if (!held(player)) {
            return;
        }
        Schematic schematic = workingCopy(player, id);
        if (schematic == null) {
            schematic = SchematicLibrary.get(player.getServer()).schematic(id).orElse(null);
        }
        if (schematic == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            return;
        }
        BlueprintNetwork.materials(player, id, false, Requirements.of(schematic, clipX, clipY, clipZ));
    }

    public static void saveSession(ServerPlayer player) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        Schematic draft = session.draft();
        if (session.mode() != BlueprintSessions.Mode.BUILD || draft == null || session.buildId() == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            return;
        }
        if (!session.draftDirty()) {
            Messages.actionBar(player, "message.autobots.session_clean");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (library.isInUse(session.buildId())) {
            Messages.actionBar(player, "message.autobots.blueprint_in_use");
            return;
        }
        library.putSchematic(draft);
        session.setDraftDirty(false);
        BlueprintNetwork.syncLibrary(player);
        Messages.actionBar(player, "message.autobots.session_saved", draft.name());
    }

    public static void beginBuild(ServerPlayer player, java.util.UUID id) {
        if (!held(player)) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        session.clear();
        session.setMode(BlueprintSessions.Mode.BUILD);
        session.setBuildId(id);
        session.setName(schematic.name());
        session.setDraft(schematic);
        session.setDraftDirty(false);
        BlueprintNetwork.preview(player, schematic, true, false);
        BlueprintNetwork.syncSupply(player);
    }

    public static void stop(ServerPlayer player) {
        if (!held(player)) {
            return;
        }
        JobManager.cancel(player, true);
        BlueprintNetwork.syncLibrary(player);
    }

    public static void launch(ServerPlayer player, java.util.UUID id, BlockPos anchorPoint, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ) {
        if (!held(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.BUILD || !id.equals(session.buildId())) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = session.draft() != null ? session.draft() : library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            return;
        }
        int sx = clampSize(sizeX);
        int sy = clampSize(sizeY);
        int sz = clampSize(sizeZ);
        int px = clampAnchor(pivotX, schematic.sizeX());
        int py = clampAnchor(pivotY, schematic.sizeY());
        int pz = clampAnchor(pivotZ, schematic.sizeZ());
        BlockPos origin = anchorPoint.offset(-px, -py, -pz);
        if (!BoxEdits.inWorld(anchorPoint) || !BoxEdits.inWorld(origin)
                || player.distanceToSqr(anchorPoint.getX() + 0.5D, anchorPoint.getY() + 0.5D, anchorPoint.getZ() + 0.5D) > rangeSqr()) {
            Messages.actionBar(player, "message.autobots.place_out_of_range");
            return;
        }
        java.util.List<Requirements.Cost> missing = Requirements.missing(player, Requirements.of(schematic, sx, sy, sz));
        if (!missing.isEmpty()) {
            BlueprintNetwork.materials(player, id, true, missing);
        }
        java.util.UUID buildSchematicId = schematic.id();
        Schematic snapshot = null;
        if (session.draftDirty()) {
            snapshot = schematic.ephemeralCopy();
            library.putSchematic(snapshot);
            buildSchematicId = snapshot.id();
        }
        if (!JobManager.startBlueprintBuild(player, buildSchematicId, origin, sx, sy, sz) && snapshot != null) {
            library.removeSchematic(snapshot.id());
        }
        BlueprintNetwork.syncLibrary(player);
    }

    public static List<BlueprintPayloads.Summary> summaries(ServerPlayer player) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        List<Schematic> schematics = new ArrayList<>(library.schematics());
        schematics.sort(Comparator.comparing(Schematic::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Schematic::id));
        List<BlueprintPayloads.Summary> summaries = new ArrayList<>(schematics.size());
        for (Schematic schematic : schematics) {
            if (schematic.ephemeral()) {
                continue;
            }
            String source = "";
            if (schematic.hasSource()) {
                BlockPos min = schematic.sourceMin();
                source = min.getX() + ", " + min.getY() + ", " + min.getZ();
            }
            summaries.add(new BlueprintPayloads.Summary(
                    schematic.id(),
                    schematic.name(),
                    schematic.sizeX(),
                    schematic.sizeY(),
                    schematic.sizeZ(),
                    schematic.blockCount(),
                    source));
        }
        return summaries;
    }

    public static boolean busy(ServerPlayer player) {
        return SchematicLibrary.get(player.getServer()).jobFor(player.getUUID()).isPresent();
    }

    public static BlueprintPayloads.Preview previewOf(Schematic schematic, boolean forBuild, boolean openEditor) {
        List<Schematic.Entry> entries = schematic.entries();
        boolean concealed = entries.size() > AutobotsConfig.MAX_PREVIEW_BLOCKS;
        List<BlueprintPayloads.Bit> bits = new ArrayList<>();
        if (!concealed) {
            for (Schematic.Entry entry : entries) {
                bits.add(new BlueprintPayloads.Bit(entry.x(), entry.y(), entry.z(), Block.getId(entry.state())));
            }
        }
        return new BlueprintPayloads.Preview(
                schematic.id(),
                schematic.name(),
                schematic.sizeX(),
                schematic.sizeY(),
                schematic.sizeZ(),
                schematic.blockCount(),
                false,
                concealed,
                bits,
                schematic.anchorX(),
                schematic.anchorY(),
                schematic.anchorZ(),
                forBuild,
                openEditor);
    }

    private static boolean held(ServerPlayer player) {
        return ControllerItem.isHeld(player);
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(AutobotsConfig.MAX_AXIS.getAsInt(), size));
    }

    private static int clampAnchor(int value, int size) {
        if (size <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(size - 1, value));
    }

    private static boolean building(ServerPlayer player, java.util.UUID id) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        return session.mode() == BlueprintSessions.Mode.BUILD && id.equals(session.buildId());
    }

    private static Schematic workingCopy(ServerPlayer player, java.util.UUID id) {
        if (!building(player, id)) {
            return null;
        }
        return BlueprintSessions.get(player).draft();
    }

    private static void remember(ServerPlayer player, java.util.UUID id, Schematic updated) {
        if (building(player, id)) {
            BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
            session.setDraft(updated);
            session.setDraftDirty(true);
            BlueprintNetwork.preview(player, updated, true, false);
            return;
        }
        SchematicLibrary.get(player.getServer()).putSchematic(updated);
        BlueprintNetwork.syncLibrary(player);
        BlueprintNetwork.preview(player, updated, false, false);
    }

    private static Schematic editable(ServerPlayer player, java.util.UUID id, boolean changing) {
        Schematic draft = workingCopy(player, id);
        if (draft != null) {
            return draft;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.autobots.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return null;
        }
        if (changing && library.isInUse(id)) {
            Messages.actionBar(player, "message.autobots.blueprint_in_use");
            return null;
        }
        return schematic;
    }

    private static double rangeSqr() {
        double range = AutobotsConfig.MAX_RANGE.getAsInt();
        return range * range;
    }

    private static boolean acceptBox(ServerPlayer player, BlockPos a, BlockPos b) {
        if (!BoxEdits.inWorld(a) || !BoxEdits.inWorld(b) || !BoxEdits.fits(a, b)) {
            Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
            deny(player);
            return false;
        }
        if (player.level().dimension() != null && !BoxEdits.near(player, a, b)) {
            Messages.actionBar(player, "message.autobots.too_far");
            deny(player);
            return false;
        }
        return true;
    }

    private static void chime(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45F, pitch);
    }

    private static void deny(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.35F, 0.7F);
    }
}
