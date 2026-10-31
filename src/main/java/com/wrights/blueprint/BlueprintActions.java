package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

import com.wrights.WrightsConfig;
import com.wrights.item.ControllerItem;
import com.wrights.job.JobManager;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.BlueprintShelf;
import com.wrights.schematic.GlobalBlueprintStore;
import com.wrights.schematic.Requirements;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

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

    /** Next free "Blueprint 1", "Blueprint 2", and so on. Folders are not part of this. */
    public static String suggestName(Iterable<String> existing) {
        Set<String> taken = keys(existing);
        for (int n = 1; n < 10000; n++) {
            String candidate = "Blueprint " + n;
            if (!taken.contains(key(candidate))) {
                return candidate;
            }
        }
        return "Blueprint";
    }

    /** Keeps {@code requested} when it is free. Otherwise appends the next free number. */
    public static String uniqueName(String requested, Iterable<String> existing) {
        if (requested == null || requested.isBlank()) {
            return suggestName(existing);
        }
        String base = cleanName(requested);
        Set<String> taken = keys(existing);
        if (!taken.contains(key(base))) {
            return base;
        }
        String stem = base;
        int start = 2;
        int space = base.lastIndexOf(' ');
        if (space > 0 && space < base.length() - 1) {
            String tail = base.substring(space + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                stem = base.substring(0, space);
                try {
                    start = Integer.parseInt(tail) + 1;
                } catch (NumberFormatException ignored) {
                    start = 2;
                }
            }
        }
        for (int n = start; n < start + 10000; n++) {
            String suffix = " " + n;
            String head = stem.length() + suffix.length() > 48 ? stem.substring(0, 48 - suffix.length()) : stem;
            String candidate = head + suffix;
            if (!taken.contains(key(candidate))) {
                return candidate;
            }
        }
        return base;
    }

    /** Same name with "1" added. Another "1" is added while that name is already used. */
    public static String copyName(String original, Iterable<String> existing) {
        Set<String> taken = keys(existing);
        String stem = cleanName(original);
        for (int extra = 1; extra <= 48; extra++) {
            int keep = Math.max(0, 48 - extra);
            String head = stem.length() > keep ? stem.substring(0, keep) : stem;
            String candidate = head + "1".repeat(extra);
            if (!taken.contains(key(candidate))) {
                return candidate;
            }
        }
        return "Blueprint1";
    }

    private static Set<String> keys(Iterable<String> names) {
        Set<String> taken = new HashSet<>();
        if (names == null) {
            return taken;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                taken.add(key(name));
            }
        }
        return taken;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> takenBlueprintNames(SchematicLibrary library) {
        return blueprintNames(library, null);
    }

    private static List<String> globalBlueprintNames(GlobalBlueprintStore store, UUID except) {
        List<String> names = new ArrayList<>();
        for (Schematic schematic : store.schematics()) {
            if (except != null && except.equals(schematic.id())) {
                continue;
            }
            names.add(schematic.name());
        }
        return names;
    }

    private static List<String> blueprintNames(SchematicLibrary library, UUID except) {
        List<String> names = new ArrayList<>();
        for (Schematic schematic : library.schematics()) {
            if (except == null || !except.equals(schematic.id())) {
                names.add(schematic.name());
            }
        }
        for (com.wrights.job.BuildJob job : library.jobs()) {
            if (job.kind() != com.wrights.job.BuildJob.Kind.COPY || job.blueprintName() == null) {
                continue;
            }
            if (except != null && except.equals(job.replaceSchematicId())) {
                continue;
            }
            names.add(job.blueprintName());
        }
        return names;
    }

    public static void requestLibrary(ServerPlayer player) {
        requestLibrary(player, false);
    }

    public static void requestLibrary(ServerPlayer player, boolean global) {
        if (global && !WrightsConfig.allowGlobal()) {
            global = false;
        }
        BlueprintSessions.get(player).setLibraryGlobal(global);
        if (global) {
            com.wrights.schematic.GlobalBlueprintStore.reload();
        }
        BlueprintNetwork.syncLibrary(player);
    }

    private static boolean global(ServerPlayer player) {
        return BlueprintSessions.get(player).libraryGlobal();
    }

    public static void resume(ServerPlayer player, int mode, java.util.UUID buildId) {
        if (!requireTool(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        com.wrights.item.ControllerItem.quietLegacy(player);
        if (session.mode() != BlueprintSessions.Mode.NONE) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (mode == 2) {
            beginBuild(player, buildId);
            return;
        }
        if (mode == 1) {
            if (WrightsConfig.refuse(player, WrightsConfig.allowCapture())) {
                return;
            }
            session.startCapture(player.level().dimension(), suggestName(blueprintNames(SchematicLibrary.get(player.getServer()), null)));
            BlueprintNetwork.syncCapture(player);
        }
    }

    public static void startCapture(ServerPlayer player) {
        if (!requireTool(player) || WrightsConfig.refuse(player, WrightsConfig.allowCapture())) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        session.startCapture(player.level().dimension(), suggestName(libraryNames(player, null)));
        BlueprintNetwork.syncCapture(player);
        Messages.actionBar(player, "message.wrights.capture_first");
    }

    public static void pick(ServerPlayer player, BlockPos pos, int action) {
        if (!requireTool(player) || pos == null) {
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
        if (!requireTool(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (session.dimension() != null && !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.wrights.different_dimension");
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
            Messages.actionBar(player, "message.wrights.corner1", nextA.getX(), nextA.getY(), nextA.getZ());
        } else {
            Messages.actionBar(player, "message.wrights.corner2",
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
            Messages.actionBar(player, "message.wrights.different_dimension");
            return;
        }
        if (session.dimension() == null) {
            session.setDimension(player.level().dimension());
        }
        if (!BoxEdits.inWorld(pos)) {
            Messages.actionBar(player, "message.wrights.too_big", WrightsConfig.MAX_AXIS.getAsInt(), WrightsConfig.MAX_BLOCKS.getAsInt());
            deny(player);
            return;
        }
        session.setCornerA(pos);
        session.setCornerB(null);
        session.bump();
        chime(player, 1.5F);
        Messages.actionBar(player, "message.wrights.corner1", pos.getX(), pos.getY(), pos.getZ());
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
            Messages.actionBar(player, "message.wrights.different_dimension");
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
        Messages.actionBar(player, "message.wrights.corner2",
                BoxEdits.span(session.cornerA(), pos, 0),
                BoxEdits.span(session.cornerA(), pos, 1),
                BoxEdits.span(session.cornerA(), pos, 2),
                BoxEdits.volume(session.cornerA(), pos),
                BoxEdits.solid(player.level(), session.cornerA(), pos));
        BlueprintNetwork.syncCapture(player);
    }

    public static void updateCapture(ServerPlayer player, int generation, int seq, boolean hasA, BlockPos cornerA, boolean hasB, BlockPos cornerB, String name) {
        if (!requireTool(player)) {
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
            Messages.actionBar(player, "message.wrights.different_dimension");
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

    public static void confirm(ServerPlayer player, String name, boolean copyFluids, boolean copyNbtBlocks, boolean copyNbtSettings, boolean copyNbtAll, boolean buildAfter, byte[] turns) {
        if (!requireTool(player) || WrightsConfig.refuse(player, WrightsConfig.allowCapture())) {
            return;
        }
        copyFluids = copyFluids && WrightsConfig.allowCopyFluids();
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.CAPTURE) {
            return;
        }
        if (session.cornerA() == null || session.cornerB() == null) {
            Messages.actionBar(player, "message.wrights.need_corners");
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (session.dimension() == null || !session.dimension().equals(player.level().dimension())) {
            Messages.actionBar(player, "message.wrights.different_dimension");
            BlueprintNetwork.syncCapture(player);
            return;
        }
        if (!acceptBox(player, session.cornerA(), session.cornerB())) {
            BlueprintNetwork.syncCapture(player);
            return;
        }
        String clean = uniqueName(name, libraryNames(player, session.editingId()));
        session.setName(clean);
        session.setBuildAfterSave(buildAfter);
        session.setMode(BlueprintSessions.Mode.SAVING);
        BlueprintNetwork.syncCapture(player);
        boolean settings = copyNbtBlocks && copyNbtSettings && WrightsConfig.copySettings();
        boolean all = copyNbtBlocks && copyNbtAll && WrightsConfig.copyAll() && player.getAbilities().instabuild && !player.isSpectator();
        if (all) {
            settings = false;
        }
        boolean started = JobManager.startBlueprintSave(
                player,
                clean,
                session.editingId(),
                session.dimension(),
                session.cornerA(),
                session.cornerB(),
                copyFluids,
                copyNbtBlocks,
                settings,
                all,
                turns);
        if (!started) {
            session.setBuildAfterSave(false);
            session.setMode(BlueprintSessions.Mode.CAPTURE);
            BlueprintNetwork.syncCapture(player);
        }
    }

    public static void cancel(ServerPlayer player) {
        if (!requireTool(player)) {
            return;
        }
        BlueprintSessions.get(player).clear();
        BlueprintNetwork.syncCapture(player);
    }

    public static void finishSave(ServerPlayer player, java.util.UUID id, boolean saved) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        boolean buildNext = session.consumeBuildAfterSave();
        if (session.mode() != BlueprintSessions.Mode.SAVING) {
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        if (saved && id != null) {
            session.setEditingId(id);
            if (session.libraryGlobal()) {
                keepGlobal(player, id);
            }
        }
        session.setMode(BlueprintSessions.Mode.CAPTURE);
        BlueprintNetwork.syncLibrary(player);
        BlueprintNetwork.syncCapture(player);
        BlueprintNetwork.finished(player, saved && id != null ? id : net.minecraft.Util.NIL_UUID, saved, true);
        if (buildNext && saved && id != null) {
            beginBuild(player, id);
        }
    }

    public static void delete(ServerPlayer player, java.util.UUID id) {
        if (global(player)) {
            GlobalBlueprintStore store = GlobalBlueprintStore.get();
            Schematic schematic = store.schematic(id).orElse(null);
            if (schematic == null) {
                Messages.actionBar(player, "message.wrights.blueprint_missing");
                BlueprintNetwork.syncLibrary(player);
                return;
            }
            store.removeSchematic(id);
            Messages.actionBar(player, "message.wrights.blueprint_deleted", schematic.name());
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        library.detachJobs(id);
        library.removeSchematic(id);
        Messages.actionBar(player, "message.wrights.blueprint_deleted", schematic.name());
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (id.equals(session.buildId())) {
            session.clear();
            BlueprintNetwork.syncCapture(player);
        }
        BlueprintNetwork.syncLibrary(player);
    }

    public static void rename(ServerPlayer player, java.util.UUID id, String name) {
        if (global(player)) {
            GlobalBlueprintStore store = GlobalBlueprintStore.get();
            Schematic schematic = store.schematic(id).orElse(null);
            if (schematic == null) {
                Messages.actionBar(player, "message.wrights.blueprint_missing");
                BlueprintNetwork.syncLibrary(player);
                return;
            }
            String clean = cleanName(name);
            String unique = uniqueName(clean, globalBlueprintNames(store, id));
            if (!unique.equals(clean)) {
                Messages.actionBar(player, "message.wrights.name_taken");
                BlueprintNetwork.syncLibrary(player);
                return;
            }
            store.putSchematic(schematic.withName(clean));
            Messages.actionBar(player, "message.wrights.blueprint_renamed", clean);
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        String clean = cleanName(name);
        String unique = uniqueName(clean, blueprintNames(library, id));
        if (!unique.equals(clean)) {
            Messages.actionBar(player, "message.wrights.name_taken");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        library.putSchematic(schematic.withName(clean));
        Messages.actionBar(player, "message.wrights.blueprint_renamed", clean);
        BlueprintNetwork.syncLibrary(player);
    }

    public static void duplicate(ServerPlayer player, UUID id, UUID copyId) {
        if (id == null || copyId == null || id.equals(copyId)) {
            return;
        }
        if (global(player)) {
            duplicateGlobal(player, id, copyId);
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BlueprintShelf shelf = library.shelf();
        if (shelf.isFolder(copyId) || library.schematic(copyId).isPresent()) {
            return;
        }
        Map<UUID, String> names = schematicNames(library);
        if (shelf.isFolder(id)) {
            BlueprintShelf.Folder source = shelf.folder(id);
            if (source == null || !shelf.createFolder(copyId, source.parent(), source.name(), names)) {
                BlueprintNetwork.syncLibrary(player);
                return;
            }
            shelf.placeAfter(copyId, true, source.parent(), id, names);
            copyContents(library, shelf, id, copyId, names, 0);
            reveal(shelf, player.getUUID(), copyId);
            Messages.actionBar(player, "message.wrights.folder_duplicated", source.name());
        } else {
            Schematic schematic = library.schematic(id).orElse(null);
            if (schematic == null || schematic.ephemeral()) {
                Messages.actionBar(player, "message.wrights.blueprint_missing");
                BlueprintNetwork.syncLibrary(player);
                return;
            }
            String name = copyName(schematic.name(), blueprintNames(library, null));
            library.putSchematic(schematic.duplicate(copyId, name));
            names.put(copyId, name);
            UUID parent = shelf.parentOf(id);
            shelf.placeAfter(copyId, false, parent, id, names);
            if (parent != null) {
                reveal(shelf, player.getUUID(), parent);
            }
            Messages.actionBar(player, "message.wrights.blueprint_duplicated", name);
        }
        library.setDirty();
        BlueprintNetwork.syncLibrary(player);
    }

    private static void duplicateGlobal(ServerPlayer player, UUID id, UUID copyId) {
        GlobalBlueprintStore store = GlobalBlueprintStore.get();
        BlueprintShelf shelf = store.shelf();
        if (shelf.isFolder(copyId) || store.schematic(copyId).isPresent()) {
            return;
        }
        Map<UUID, String> names = globalSchematicNames(store);
        if (shelf.isFolder(id)) {
            Messages.actionBar(player, "message.wrights.global_folder_dup");
            return;
        }
        Schematic schematic = store.schematic(id).orElse(null);
        if (schematic == null || schematic.ephemeral()) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        String name = copyName(schematic.name(), globalBlueprintNames(store, null));
        store.putSchematic(schematic.duplicate(copyId, name));
        names.put(copyId, name);
        UUID parent = shelf.parentOf(id);
        shelf.placeAfter(copyId, false, parent, id, names);
        if (parent != null) {
            reveal(shelf, player.getUUID(), parent);
        }
        store.markShelfChanged();
        Messages.actionBar(player, "message.wrights.blueprint_duplicated", name);
        BlueprintNetwork.syncLibrary(player);
    }

    /** Copy a blueprint from world↔global. The source is removed after a successful copy. */
    public static void moveLibrary(ServerPlayer player, UUID id, boolean toGlobal) {
        if (id == null || WrightsConfig.refuse(player, WrightsConfig.allowGlobal())) {
            return;
        }
        if (toGlobal) {
            SchematicLibrary library = SchematicLibrary.get(player.getServer());
            Schematic schematic = library.schematic(id).orElse(null);
            if (schematic == null || schematic.ephemeral()) {
                Messages.actionBar(player, "message.wrights.blueprint_missing");
                return;
            }
            GlobalBlueprintStore store = GlobalBlueprintStore.get();
            UUID newId = UUID.randomUUID();
            String name = uniqueName(schematic.name(), globalBlueprintNames(store, null));
            Schematic copy = schematic.duplicate(newId, name);
            store.putSchematic(copy);
            Map<UUID, String> names = globalSchematicNames(store);
            names.put(newId, name);
            store.shelf().placeAfter(newId, false, null, null, names);
            store.markShelfChanged();
            library.detachJobs(id);
            library.removeSchematic(id);
            BlueprintSessions.get(player).setLibraryGlobal(true);
            Messages.actionBar(player, "message.wrights.moved_to_global", name);
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        GlobalBlueprintStore store = GlobalBlueprintStore.get();
        Schematic schematic = store.schematic(id).orElse(null);
        if (schematic == null || schematic.ephemeral()) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        UUID newId = UUID.randomUUID();
        String name = uniqueName(schematic.name(), blueprintNames(library, null));
        Schematic copy = schematic.duplicate(newId, name);
        library.putSchematic(copy);
        Map<UUID, String> names = schematicNames(library);
        names.put(newId, name);
        library.shelf().placeAfter(newId, false, null, null, names);
        library.setDirty();
        store.removeSchematic(id);
        BlueprintSessions.get(player).setLibraryGlobal(false);
        Messages.actionBar(player, "message.wrights.moved_to_world", name);
        BlueprintNetwork.syncLibrary(player);
    }

    private static void copyContents(SchematicLibrary library, BlueprintShelf shelf, UUID from, UUID to, Map<UUID, String> names, int depth) {
        if (depth > 32) {
            return;
        }
        for (UUID child : List.copyOf(shelf.contents(from, names))) {
            if (shelf.isFolder(child)) {
                BlueprintShelf.Folder folder = shelf.folder(child);
                if (folder == null) {
                    continue;
                }
                UUID copyId = UUID.randomUUID();
                if (!shelf.createFolder(copyId, to, folder.name(), names)) {
                    continue;
                }
                copyContents(library, shelf, child, copyId, names, depth + 1);
                continue;
            }
            Schematic schematic = library.schematic(child).orElse(null);
            if (schematic == null || schematic.ephemeral()) {
                continue;
            }
            String name = copyName(schematic.name(), blueprintNames(library, null));
            UUID copyId = UUID.randomUUID();
            library.putSchematic(schematic.duplicate(copyId, name));
            names.put(copyId, name);
            shelf.placeAfter(copyId, false, to, null, names);
        }
    }

    private static void reveal(BlueprintShelf shelf, UUID player, UUID folder) {
        UUID cursor = folder;
        for (int guard = 0; cursor != null && guard < 64; guard++) {
            shelf.setOpen(player, cursor, true);
            BlueprintShelf.Folder node = shelf.folder(cursor);
            cursor = node == null ? null : node.parent();
        }
    }

    public static void beginEdit(ServerPlayer player, java.util.UUID id) {
        if (WrightsConfig.refuse(player, WrightsConfig.allowEdit())) {
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
        finishEdit(player, keep, true, true, true, true);
    }

    public static void finishEdit(ServerPlayer player, boolean keep, boolean placeFluids, boolean placeNbt, boolean placeSettings, boolean placeAll) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        Schematic baseline = session.editBaseline();
        boolean wasDraft = session.editWasDraft();
        boolean baselineDirty = session.editBaselineDirty();
        Schematic draft = session.draft();
        session.clearEdit();
        if (baseline == null) {
            return;
        }
        if (wasDraft && session.mode() == BlueprintSessions.Mode.BUILD && baseline.id().equals(session.buildId())) {
            if (!keep) {
                session.setDraft(baseline);
                session.setDraftDirty(baselineDirty);
                BlueprintNetwork.preview(player, baseline, false, false);
                Messages.actionBar(player, "message.wrights.edit_discarded");
                return;
            }
            Schematic saved = (draft != null ? draft : baseline).withStored(placeFluids, placeNbt, placeSettings, placeAll).withPaste(placeFluids, baseline.voidBlocks(), baseline.voidFluids(), baseline.voidNbt());
            storeSaved(player, saved);
            session.setDraft(saved);
            session.setDraftDirty(false);
            BlueprintNetwork.syncLibrary(player);
            BlueprintNetwork.preview(player, saved, false, false);
            if (!saved.sameBlocks(baseline) || saved.placeFluids() != baseline.placeFluids()) {
                Messages.actionBar(player, "message.wrights.session_saved", saved.name());
            } else {
                Messages.actionBar(player, "message.wrights.session_clean");
            }
            return;
        }
        Schematic current = held(player, baseline.id());
        if (keep) {
            if (current != null) {
                Schematic saved = current.withStored(placeFluids, placeNbt, placeSettings, placeAll).withPaste(placeFluids, current.voidBlocks(), current.voidFluids(), current.voidNbt());
                if (!saved.sameBlocks(current) || saved.placeFluids() != current.placeFluids()) {
                    storeSaved(player, saved);
                    current = saved;
                }
                BlueprintNetwork.syncLibrary(player);
                BlueprintNetwork.preview(player, current, false, false);
            }
            if (current != null && !current.sameBlocks(baseline)) {
                Messages.actionBar(player, "message.wrights.session_saved", current.name());
            } else {
                Messages.actionBar(player, "message.wrights.session_clean");
            }
            return;
        }
        if (current != baseline) {
            storeSaved(player, baseline);
            BlueprintNetwork.syncLibrary(player);
            BlueprintNetwork.preview(player, baseline, false, false);
            Messages.actionBar(player, "message.wrights.edit_discarded");
        }
    }

    public static void recapture(ServerPlayer player, java.util.UUID id) {
        if (!requireTool(player) || WrightsConfig.refuse(player, WrightsConfig.allowEdit())) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = stored(player, id);
        if (schematic == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return;
        }
        if (library.schematic(id).isPresent() && library.isInUse(id)) {
            Messages.actionBar(player, "message.wrights.blueprint_in_use");
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        session.startCapture(player.level().dimension(), schematic.name());
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
                Messages.actionBar(player, "message.wrights.too_big", WrightsConfig.MAX_AXIS.getAsInt(), WrightsConfig.MAX_BLOCKS.getAsInt());
            }
        } else if (schematic.hasSource()) {
            Messages.actionBar(player, "message.wrights.edit_other_dimension");
        } else {
            Messages.actionBar(player, "message.wrights.capture_first");
        }
        BlueprintNetwork.syncCapture(player);
    }

    public static void setAnchor(ServerPlayer player, java.util.UUID id, int x, int y, int z) {
        Schematic schematic = editable(player, id, true);
        if (schematic == null) {
            return;
        }
        remember(player, id, schematic.withAnchor(x, y, z));
    }

    public static void editCell(ServerPlayer player, java.util.UUID id, int x, int y, int z, int stateId, boolean remove) {
        if (WrightsConfig.refuse(player, WrightsConfig.allowEdit())) {
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
            if (!com.wrights.util.BlockFilters.canEdit(state)) {
                Messages.actionBar(player, "message.wrights.cannot_store");
                return;
            }
            if (next.size() >= WrightsConfig.MAX_BLOCKS.getAsInt()) {
                Messages.actionBar(player, "message.wrights.too_big", WrightsConfig.MAX_AXIS.getAsInt(), WrightsConfig.MAX_BLOCKS.getAsInt());
                return;
            }
            next.add(new Schematic.Entry((short) x, (short) y, (short) z, state, com.wrights.util.BlockFilters.isSecondary(state)));
            next.sort(Schematic.BUILD_ORDER);
        }
        remember(player, id, schematic.withEntries(next));
    }

    public static void editLayer(ServerPlayer player, java.util.UUID id, int operation, int from, int to, int axis) {
        if (WrightsConfig.refuse(player, WrightsConfig.allowEdit())) {
            return;
        }
        Schematic schematic = editable(player, id, true);
        if (schematic == null) {
            return;
        }
        axis = LayerAxis.clamp(axis);
        int length = Math.max(1, LayerAxis.size(schematic, axis));
        int source = Math.max(0, Math.min(length - 1, from));
        java.util.ArrayList<Schematic.Entry> entries = new java.util.ArrayList<>();
        int anchor = LayerAxis.anchor(schematic, axis);
        if (operation == BlueprintPayloads.EditLayer.REMOVE) {
            if (length == 1) {
                for (Schematic.Entry entry : schematic.entries()) {
                    if (LayerAxis.get(entry, axis) != source) {
                        entries.add(entry);
                    }
                }
                storeLayout(player, id, schematic, axis, 1, entries, 0);
                return;
            }
            for (Schematic.Entry entry : schematic.entries()) {
                int coord = LayerAxis.get(entry, axis);
                if (coord == source) {
                    continue;
                }
                int next = coord > source ? coord - 1 : coord;
                entries.add(LayerAxis.set(entry, axis, next));
            }
            if (anchor == source) {
                anchor = Math.max(0, source - 1);
            } else if (anchor > source) {
                anchor--;
            }
            storeLayout(player, id, schematic, axis, length - 1, entries, anchor);
            return;
        }
        if (operation == BlueprintPayloads.EditLayer.INSERT) {
            if (length >= WrightsConfig.MAX_AXIS.getAsInt()) {
                Messages.actionBar(player, "message.wrights.too_big", WrightsConfig.MAX_AXIS.getAsInt(), WrightsConfig.MAX_BLOCKS.getAsInt());
                return;
            }
            int at = Math.max(0, Math.min(length, to));
            shiftAlong(schematic, entries, axis, at);
            if (anchor >= at) {
                anchor++;
            }
            storeLayout(player, id, schematic, axis, length + 1, entries, anchor);
            return;
        }
        if (operation == BlueprintPayloads.EditLayer.MOVE) {
            int dest = Math.max(0, Math.min(length - 1, to));
            if (dest == source) {
                return;
            }
            for (Schematic.Entry entry : schematic.entries()) {
                int next = movedAlong(LayerAxis.get(entry, axis), source, dest);
                entries.add(LayerAxis.set(entry, axis, next));
            }
            storeLayout(player, id, schematic, axis, length, entries, movedAlong(anchor, source, dest));
            return;
        }
        if (operation == BlueprintPayloads.EditLayer.COPY) {
            int dest = Math.max(0, Math.min(length, to));
            int added = 0;
            for (Schematic.Entry entry : schematic.entries()) {
                if (LayerAxis.get(entry, axis) == source) {
                    added++;
                }
            }
            if (length >= WrightsConfig.MAX_AXIS.getAsInt() || schematic.blockCount() + added > WrightsConfig.MAX_BLOCKS.getAsInt()) {
                Messages.actionBar(player, "message.wrights.too_big", WrightsConfig.MAX_AXIS.getAsInt(), WrightsConfig.MAX_BLOCKS.getAsInt());
                return;
            }
            shiftAlong(schematic, entries, axis, dest);
            for (Schematic.Entry entry : schematic.entries()) {
                if (LayerAxis.get(entry, axis) != source) {
                    continue;
                }
                entries.add(LayerAxis.set(entry, axis, dest));
            }
            entries.sort(Schematic.BUILD_ORDER);
            if (anchor >= dest) {
                anchor++;
            }
            storeLayout(player, id, schematic, axis, length + 1, entries, anchor);
        }
    }

    private static void shiftAlong(Schematic schematic, java.util.List<Schematic.Entry> into, int axis, int at) {
        for (Schematic.Entry entry : schematic.entries()) {
            int coord = LayerAxis.get(entry, axis);
            into.add(LayerAxis.set(entry, axis, coord >= at ? coord + 1 : coord));
        }
    }

    private static int movedAlong(int coord, int from, int dest) {
        if (coord == from) {
            return dest;
        }
        if (from < dest && coord > from && coord <= dest) {
            return coord - 1;
        }
        if (dest < from && coord >= dest && coord < from) {
            return coord + 1;
        }
        return coord;
    }

    private static void storeLayout(ServerPlayer player, java.util.UUID id, Schematic schematic, int axis, int size, java.util.List<Schematic.Entry> entries, int anchor) {
        remember(player, id, schematic.withLayout(axis, size, entries, anchor));
    }

    public static void transform(ServerPlayer player, java.util.UUID id, int operation) {
        if (WrightsConfig.refuse(player, WrightsConfig.allowEdit())) {
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
        Schematic schematic = stored(player, id);
        if (schematic == null) {
            return;
        }
        BlueprintNetwork.preview(player, schematic, false, false);
    }

    public static void sendMaterials(ServerPlayer player, java.util.UUID id, int clipX, int clipY, int clipZ) {
        sendMaterials(player, id, clipX, clipY, clipZ, true);
    }

    public static void sendMaterials(ServerPlayer player, java.util.UUID id, int clipX, int clipY, int clipZ, boolean placeFluids) {
        Schematic schematic = workingCopy(player, id);
        if (schematic == null) {
            schematic = stored(player, id);
        }
        if (schematic == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return;
        }
        BlueprintSessions.BlueprintSession supplySession = BlueprintSessions.get(player);
        BlueprintNetwork.materials(player, id, false, Requirements.stocked(player, Requirements.of(schematic, clipX, clipY, clipZ, placeFluids), supplySession.supplyPos(), supplySession.supplyDimension()));
    }

    public static void saveSession(ServerPlayer player, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean placeNbt, boolean placeSettings, boolean placeAll) {
        if (!requireTool(player)) {
            return;
        }
        placeFluids = placeFluids && WrightsConfig.allowPlaceFluids();
        voidBlocks = voidBlocks && WrightsConfig.allowVoidBlocks();
        voidFluids = voidFluids && WrightsConfig.allowVoidFluids();
        voidNbt = voidNbt && WrightsConfig.allowVoidNbt();
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        Schematic draft = session.draft();
        if (session.mode() != BlueprintSessions.Mode.BUILD || draft == null || session.buildId() == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (library.isInUse(session.buildId())) {
            Messages.actionBar(player, "message.wrights.blueprint_in_use");
            return;
        }
        Schematic next = draft.withStored(placeFluids, placeNbt, placeSettings, placeAll).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
        Schematic stored = held(player, session.buildId());
        if (!session.draftDirty() && stored != null && stored.sameBlocks(next) && stored.samePaste(next)) {
            Messages.actionBar(player, "message.wrights.session_clean");
            return;
        }
        storeSaved(player, next);
        session.setDraft(next);
        session.setDraftDirty(false);
        BlueprintNetwork.syncLibrary(player);
        Messages.actionBar(player, "message.wrights.session_saved", draft.name());
    }

    public static void beginBuild(ServerPlayer player, java.util.UUID id) {
        if (!requireTool(player) || WrightsConfig.refuse(player, WrightsConfig.allowPlace())) {
            return;
        }
        if (id == null || id.equals(Util.NIL_UUID)) {
            BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
            session.clear();
            session.setMode(BlueprintSessions.Mode.BUILD);
            BlueprintNetwork.syncCapture(player);
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        boolean fromGlobal = false;
        if (schematic == null) {
            schematic = GlobalBlueprintStore.get().schematic(id).orElse(null);
            fromGlobal = schematic != null;
        }
        if (schematic == null || schematic.blockCount() == 0) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
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
        if (fromGlobal) {
            GlobalBlueprintStore store = GlobalBlueprintStore.get();
            store.shelf().noteUse(player.getUUID(), id);
            store.markShelfChanged();
        } else {
            library.shelf().noteUse(player.getUUID(), id);
            library.setDirty();
        }
        BlueprintNetwork.preview(player, schematic, true, false);
        BlueprintNetwork.syncSupply(player);
    }

    public static void stop(ServerPlayer player) {
        if (!requireTool(player)) {
            return;
        }
        JobManager.cancel(player, true);
        BlueprintNetwork.syncLibrary(player);
    }

    public static void launch(ServerPlayer player, java.util.UUID id, BlockPos anchorPoint, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ) {
        launch(player, id, anchorPoint, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ, 0);
    }

    public static void launch(ServerPlayer player, java.util.UUID id, BlockPos anchorPoint, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ, int drones) {
        launch(player, id, anchorPoint, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ, drones, true, false, false, false, false, true, false);
    }

    public static void launch(ServerPlayer player, java.util.UUID id, BlockPos anchorPoint, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) {
        launch(player, id, anchorPoint, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, false, true, false);
    }

    public static void launch(ServerPlayer player, java.util.UUID id, BlockPos anchorPoint, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean force, boolean playerFirst, boolean queue) {
        launch(player, id, anchorPoint, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ, drones, placeFluids, voidBlocks, voidFluids, voidNbt, force, playerFirst, queue, true, true, true, false, BlockPos.ZERO, 0, 0, true, 0);
    }

    public static void launch(ServerPlayer player, java.util.UUID id, BlockPos anchorPoint, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean force, boolean playerFirst, boolean queue, boolean placeNbt, boolean placeSettings, boolean placeAll, boolean mirror, BlockPos mirrorAnchor, int mirrorFace, int mirrorPattern, boolean mirrorShapes, int mirrorAxes) {
        if (!requireTool(player) || WrightsConfig.refuse(player, WrightsConfig.allowPlace())) {
            return;
        }
        placeFluids = placeFluids && WrightsConfig.allowPlaceFluids();
        voidBlocks = voidBlocks && WrightsConfig.allowVoidBlocks();
        voidFluids = voidFluids && WrightsConfig.allowVoidFluids();
        voidNbt = voidNbt && WrightsConfig.allowVoidNbt();
        if (mirror && !WrightsConfig.allowMirrorBlueprints()) {
            mirror = false;
        }
        mirrorPattern = WrightsConfig.allowedPattern(mirrorPattern);
        mirrorAxes = WrightsConfig.allowedAxes(mirrorAxes);
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() != BlueprintSessions.Mode.BUILD || !id.equals(session.buildId())) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = session.draft() != null ? session.draft() : library.schematic(id).orElse(null);
        if (schematic == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
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
            Messages.actionBar(player, "message.wrights.place_out_of_range");
            return;
        }
        if (!force && !player.getAbilities().instabuild && !player.isSpectator()) {
            java.util.List<Requirements.Cost> costs = Requirements.of(schematic, sx, sy, sz, placeFluids);
            if (mirror) {
                int times = Mirror.copyCount(mirrorPattern, mirrorAxes);
                if (times > 1) {
                    java.util.List<Requirements.Cost> scaled = new ArrayList<>(costs.size());
                    for (Requirements.Cost cost : costs) {
                        scaled.add(new Requirements.Cost(cost.itemId(), cost.count() * times));
                    }
                    costs = scaled;
                }
            }
            java.util.List<Requirements.Cost> listed = Requirements.ordered(player, costs, session.supplyPos(), session.supplyDimension());
            if (Requirements.anyShort(listed)) {
                BlueprintNetwork.materials(player, id, true, listed);
                return;
            }
        }
        java.util.UUID buildSchematicId = schematic.id();
        Schematic snapshot = null;
        if (session.draftDirty() || library.schematic(schematic.id()).isEmpty()) {
            snapshot = schematic.ephemeralCopy();
            library.putSchematic(snapshot);
            buildSchematicId = snapshot.id();
        }
        boolean deferSupplies = force && queue && !player.getAbilities().instabuild && !player.isSpectator();
        boolean started = JobManager.startBlueprintBuild(player, buildSchematicId, origin, sx, sy, sz, drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll);
        if (!started && snapshot != null) {
            library.removeSchematic(snapshot.id());
        }
        if (started && mirror && mirrorAnchor != null && player.distanceToSqr(mirrorAnchor.getX() + 0.5D, mirrorAnchor.getY() + 0.5D, mirrorAnchor.getZ() + 0.5D) <= rangeSqr()) {
            MirrorLaunch.Result mirrored = MirrorLaunch.spawn(player, schematic, origin, sx, sy, sz, drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll, mirrorAnchor, mirrorFace, mirrorPattern, mirrorShapes, mirrorAxes);
            if (mirrored.started() > 0) {
                Messages.actionBar(player, "message.wrights.mirror_started", mirrored.started());
            } else if (mirrored.nowhere()) {
                Messages.actionBar(player, "message.wrights.mirror_same");
            }
        } else if (started && mirror && mirrorAnchor != null) {
            Messages.actionBar(player, "message.wrights.mirror_far");
        }
        BlueprintNetwork.syncLibrary(player);
    }

    public static List<BlueprintPayloads.Summary> summaries(ServerPlayer player) {
        if (global(player)) {
            return globalSummaries(player);
        }
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
                    source,
                    library.shelf().parentOf(schematic.id()) == null ? Util.NIL_UUID : library.shelf().parentOf(schematic.id()),
                    library.shelf().orderOf(schematic.id()),
                    library.shelf().favorite(player.getUUID(), schematic.id()),
                    library.shelf().uses(player.getUUID(), schematic.id()),
                    library.shelf().lastUsed(player.getUUID(), schematic.id())));
        }
        return summaries;
    }

    private static List<BlueprintPayloads.Summary> globalSummaries(ServerPlayer player) {
        GlobalBlueprintStore store = GlobalBlueprintStore.get();
        List<Schematic> schematics = new ArrayList<>(store.schematics());
        schematics.sort(Comparator.comparing(Schematic::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Schematic::id));
        List<BlueprintPayloads.Summary> summaries = new ArrayList<>(schematics.size());
        for (Schematic schematic : schematics) {
            if (schematic.ephemeral()) {
                continue;
            }
            summaries.add(new BlueprintPayloads.Summary(
                    schematic.id(),
                    schematic.name(),
                    schematic.sizeX(),
                    schematic.sizeY(),
                    schematic.sizeZ(),
                    schematic.blockCount(),
                    "",
                    store.shelf().parentOf(schematic.id()) == null ? Util.NIL_UUID : store.shelf().parentOf(schematic.id()),
                    store.shelf().orderOf(schematic.id()),
                    store.shelf().favorite(player.getUUID(), schematic.id()),
                    store.shelf().uses(player.getUUID(), schematic.id()),
                    store.shelf().lastUsed(player.getUUID(), schematic.id())));
        }
        return summaries;
    }

    public static List<BlueprintPayloads.FolderLine> folders(ServerPlayer player) {
        BlueprintShelf shelf = global(player) ? GlobalBlueprintStore.get().shelf() : SchematicLibrary.get(player.getServer()).shelf();
        if (!global(player) && shelf.dropFolderFavorites()) {
            SchematicLibrary.get(player.getServer()).setDirty();
        }
        List<BlueprintPayloads.FolderLine> lines = new ArrayList<>();
        for (BlueprintShelf.Folder folder : shelf.folders()) {
            lines.add(new BlueprintPayloads.FolderLine(
                    folder.id(),
                    folder.name(),
                    folder.parent() == null ? Util.NIL_UUID : folder.parent(),
                    shelf.orderOf(folder.id()),
                    false));
        }
        return lines;
    }

    public static List<UUID> openFolders(ServerPlayer player) {
        BlueprintShelf shelf = global(player) ? GlobalBlueprintStore.get().shelf() : SchematicLibrary.get(player.getServer()).shelf();
        return new ArrayList<>(shelf.openFolders(player.getUUID()));
    }

    public static void shelf(ServerPlayer player, BlueprintPayloads.ShelfEdit edit) {
        if (global(player)) {
            shelfGlobal(player, edit);
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BlueprintShelf shelf = library.shelf();
        Map<UUID, String> names = schematicNames(library);
        UUID parent = edit.parent() == null || edit.parent().equals(Util.NIL_UUID) ? null : edit.parent();
        switch (edit.action()) {
            case BlueprintPayloads.ShelfEdit.CREATE -> {
                if (shelf.createFolder(edit.id(), parent, edit.name(), names)) {
                    if (parent != null) {
                        shelf.setOpen(player.getUUID(), parent, true);
                    }
                    Messages.actionBar(player, "message.wrights.folder_created", shelf.folders().stream()
                            .filter(folder -> folder.id().equals(edit.id()))
                            .map(BlueprintShelf.Folder::name)
                            .findFirst()
                            .orElse("Folder"));
                }
            }
            case BlueprintPayloads.ShelfEdit.RENAME -> shelf.rename(edit.id(), edit.name());
            case BlueprintPayloads.ShelfEdit.DELETE -> shelf.deleteFolder(edit.id(), names);
            case BlueprintPayloads.ShelfEdit.MOVE -> shelf.move(edit.id(), edit.folder(), parent, edit.index(), names);
            case BlueprintPayloads.ShelfEdit.FAVORITE -> {
                if (edit.folder() || shelf.isFolder(edit.id())) {
                    if (shelf.dropFolderFavorites()) {
                        library.setDirty();
                        BlueprintNetwork.syncLibrary(player);
                    }
                    return;
                }
                if (library.schematic(edit.id()).filter(schematic -> !schematic.ephemeral()).isEmpty()) {
                    return;
                }
                shelf.toggleFavorite(player.getUUID(), edit.id());
            }
            case BlueprintPayloads.ShelfEdit.OPEN -> shelf.setOpen(player.getUUID(), edit.id(), edit.index() != 0);
            default -> {
                return;
            }
        }
        library.setDirty();
        BlueprintNetwork.syncLibrary(player);
    }

    private static void shelfGlobal(ServerPlayer player, BlueprintPayloads.ShelfEdit edit) {
        GlobalBlueprintStore store = GlobalBlueprintStore.get();
        BlueprintShelf shelf = store.shelf();
        Map<UUID, String> names = globalSchematicNames(store);
        UUID parent = edit.parent() == null || edit.parent().equals(Util.NIL_UUID) ? null : edit.parent();
        switch (edit.action()) {
            case BlueprintPayloads.ShelfEdit.CREATE -> {
                if (shelf.createFolder(edit.id(), parent, edit.name(), names)) {
                    if (parent != null) {
                        shelf.setOpen(player.getUUID(), parent, true);
                    }
                    Messages.actionBar(player, "message.wrights.folder_created", edit.name());
                }
            }
            case BlueprintPayloads.ShelfEdit.RENAME -> shelf.rename(edit.id(), edit.name());
            case BlueprintPayloads.ShelfEdit.DELETE -> shelf.deleteFolder(edit.id(), names);
            case BlueprintPayloads.ShelfEdit.MOVE -> shelf.move(edit.id(), edit.folder(), parent, edit.index(), names);
            case BlueprintPayloads.ShelfEdit.FAVORITE -> {
                if (edit.folder() || shelf.isFolder(edit.id()) || store.schematic(edit.id()).isEmpty()) {
                    return;
                }
                shelf.toggleFavorite(player.getUUID(), edit.id());
            }
            case BlueprintPayloads.ShelfEdit.OPEN -> shelf.setOpen(player.getUUID(), edit.id(), edit.index() != 0);
            default -> {
                return;
            }
        }
        store.markShelfChanged();
        BlueprintNetwork.syncLibrary(player);
    }

    private static Map<UUID, String> schematicNames(SchematicLibrary library) {
        Map<UUID, String> names = new HashMap<>();
        for (Schematic schematic : library.schematics()) {
            if (!schematic.ephemeral()) {
                names.put(schematic.id(), schematic.name());
            }
        }
        return names;
    }

    private static Map<UUID, String> globalSchematicNames(GlobalBlueprintStore store) {
        Map<UUID, String> names = new HashMap<>();
        for (Schematic schematic : store.schematics()) {
            if (!schematic.ephemeral()) {
                names.put(schematic.id(), schematic.name());
            }
        }
        return names;
    }

    public static boolean busy(ServerPlayer player) {
        return SchematicLibrary.get(player.getServer()).jobFor(player.getUUID()).isPresent();
    }

    public static BlueprintPayloads.Preview previewOf(Schematic schematic, boolean forBuild, boolean openEditor) {
        List<Schematic.Entry> entries = schematic.entries();
        boolean concealed = entries.size() > WrightsConfig.maxPreviewBlocks();
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
                openEditor,
                schematic.placeFluids(),
                schematic.voidBlocks(),
                schematic.voidFluids(),
                schematic.voidNbt(),
                schematic.hasSettings(),
                schematic.hasAll());
    }

    private static boolean held(ServerPlayer player) {
        return ControllerItem.isHeld(player);
    }

    /** World build / capture / drone work needs the controller in hand. Library browsing does not. */
    private static boolean requireTool(ServerPlayer player) {
        if (held(player)) {
            return true;
        }
        Messages.actionBar(player, "message.wrights.need_controller");
        return false;
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(WrightsConfig.MAX_AXIS.getAsInt(), size));
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

    /** World library first, then the shared global library. */
    private static Schematic stored(ServerPlayer player, java.util.UUID id) {
        if (id == null) {
            return null;
        }
        Schematic world = SchematicLibrary.get(player.getServer()).schematic(id).orElse(null);
        if (world != null) {
            return world;
        }
        return GlobalBlueprintStore.get().schematic(id).orElse(null);
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
        storeSaved(player, updated);
        BlueprintNetwork.syncLibrary(player);
        BlueprintNetwork.preview(player, updated, false, false);
    }

    private static Schematic editable(ServerPlayer player, java.util.UUID id, boolean changing) {
        Schematic draft = workingCopy(player, id);
        if (draft != null) {
            return draft;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = stored(player, id);
        if (schematic == null) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            BlueprintNetwork.syncLibrary(player);
            return null;
        }
        if (changing && library.schematic(id).isPresent() && library.isInUse(id)) {
            Messages.actionBar(player, "message.wrights.blueprint_in_use");
            return null;
        }
        return schematic;
    }

    private static List<String> libraryNames(ServerPlayer player, UUID except) {
        if (global(player)) {
            return globalBlueprintNames(GlobalBlueprintStore.get(), except);
        }
        return blueprintNames(SchematicLibrary.get(player.getServer()), except);
    }

    private static Schematic held(ServerPlayer player, UUID id) {
        return stored(player, id);
    }

    /** Writes a blueprint back to the library that already holds it. */
    private static void storeSaved(ServerPlayer player, Schematic schematic) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        if (library.schematic(schematic.id()).isPresent()) {
            library.putSchematic(schematic);
            return;
        }
        GlobalBlueprintStore store = GlobalBlueprintStore.get();
        if (store.schematic(schematic.id()).isPresent() || global(player)) {
            store.putSchematic(schematic);
            return;
        }
        library.putSchematic(schematic);
    }

    /** A finished capture was written into the world library. Keep it in the global library instead. */
    private static void keepGlobal(ServerPlayer player, UUID id) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(id).orElse(null);
        if (schematic == null || schematic.ephemeral()) {
            return;
        }
        GlobalBlueprintStore store = GlobalBlueprintStore.get();
        boolean fresh = store.schematic(id).isEmpty();
        store.putSchematic(schematic);
        if (fresh) {
            Map<UUID, String> names = globalSchematicNames(store);
            names.put(id, schematic.name());
            store.shelf().placeAfter(id, false, null, null, names);
            store.markShelfChanged();
        }
        library.removeSchematic(id);
    }

    private static double rangeSqr() {
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        return range * range;
    }

    private static boolean acceptBox(ServerPlayer player, BlockPos a, BlockPos b) {
        if (!BoxEdits.inWorld(a) || !BoxEdits.inWorld(b) || !BoxEdits.fits(a, b)) {
            Messages.actionBar(player, "message.wrights.too_big", WrightsConfig.MAX_AXIS.getAsInt(), WrightsConfig.MAX_BLOCKS.getAsInt());
            deny(player);
            return false;
        }
        if (player.level().dimension() != null && !BoxEdits.near(player, a, b)) {
            Messages.actionBar(player, "message.wrights.too_far");
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
