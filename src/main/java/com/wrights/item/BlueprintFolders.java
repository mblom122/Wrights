package com.wrights.item;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.wrights.blueprint.BlueprintActions;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.BlueprintShelf;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

public final class BlueprintFolders {
    private static final int MAX_BYTES = 384 * 1024;

    private BlueprintFolders() {
    }

    public static void sendList(ServerPlayer player) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        List<String> folderNames = folderNames(library);
        List<BlueprintPayloads.FolderItemLine> lines = new ArrayList<>();
        for (int slot : slots(player.getInventory())) {
            Node stored = read(player.getInventory().getItem(slot), player);
            if (stored == null) {
                continue;
            }
            Match match = match(library, stored);
            String next = BlueprintActions.uniqueName(stored.name, folderNames);
            lines.add(new BlueprintPayloads.FolderItemLine(
                    slot,
                    stored.name,
                    countFolders(stored),
                    countBlueprints(stored),
                    match.kind,
                    next,
                    match.libraryBlueprints));
        }
        BlueprintNetwork.syncLibrary(player);
        BlueprintNetwork.sendFolderItems(player, lines);
    }

    public static void write(ServerPlayer player, int slot, UUID folderId) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BlueprintShelf.Folder folder = library.shelf().folder(folderId);
        if (folder == null) {
            Messages.actionBar(player, "message.wrights.slip_folder_missing");
            return;
        }
        Node tree = fromLibrary(library, folderId);
        CompoundTag tag = save(tree);
        if (!fits(tag)) {
            Messages.actionBar(player, "message.wrights.slip_too_big");
            return;
        }
        Inventory inventory = player.getInventory();
        int target = slot >= 0 ? slot : findBlank(inventory);
        if (target < 0 || !blank(inventory.getItem(target))) {
            Messages.actionBar(player, "message.wrights.slip_folder_need");
            return;
        }
        ItemStack stack = inventory.getItem(target);
        if (stack.getCount() == 1) {
            stack.set(ModDataComponents.FOLDER.get(), tag);
        } else {
            stack.shrink(1);
            ItemStack one = new ItemStack(ModItems.BLUEPRINT_FOLDER.get());
            one.set(ModDataComponents.FOLDER.get(), tag);
            if (!inventory.add(one)) {
                player.drop(one, false);
            }
        }
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        Messages.actionBar(player, "message.wrights.slip_folder_saved", folder.name());
    }

    public static void load(ServerPlayer player, int slot, int mode, String requestedName) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Node stored = read(player.getInventory().getItem(slot), player);
        if (stored == null) {
            Messages.actionBar(player, "message.wrights.slip_folder_empty");
            sendList(player);
            return;
        }
        Match match = match(library, stored);
        if (mode == BlueprintSlips.REPLACE) {
            if (match.replaceId == null) {
                mode = BlueprintSlips.LOAD;
            } else if (inUse(library, match.replaceId)) {
                Messages.actionBar(player, "message.wrights.slip_folder_in_use");
                sendList(player);
                return;
            } else if (!clearChildren(library, match.replaceId)) {
                Messages.actionBar(player, "message.wrights.slip_folder_in_use");
                sendList(player);
                return;
            } else {
                int renamed = importChildren(library, stored, match.replaceId, takenNames(library));
                library.shelf().setOpen(player.getUUID(), match.replaceId, true);
                library.setDirty();
                finish(player, renamed == 0 ? "message.wrights.slip_folder_replaced" : "message.wrights.slip_folder_replaced_renamed", library.shelf().folder(match.replaceId).name(), renamed);
                return;
            }
        }
        String name = stored.name;
        if (mode == BlueprintSlips.NEXT_NUMBER) {
            name = BlueprintActions.uniqueName(stored.name, folderNames(library));
        } else if (mode == BlueprintSlips.NAMED) {
            if (requestedName == null || requestedName.isBlank()) {
                Messages.actionBar(player, "message.wrights.name_taken");
                sendList(player);
                return;
            }
            name = BlueprintActions.cleanName(requestedName);
        } else if (mode != BlueprintSlips.LOAD) {
            sendList(player);
            return;
        }
        Map<UUID, String> names = names(library);
        UUID root = UUID.randomUUID();
        if (!library.shelf().createFolder(root, null, name, names)) {
            Messages.actionBar(player, "message.wrights.slip_folder_missing");
            sendList(player);
            return;
        }
        int renamed = importChildren(library, stored, root, takenNames(library));
        library.shelf().setOpen(player.getUUID(), root, true);
        library.setDirty();
        BlueprintShelf.Folder created = library.shelf().folder(root);
        finish(player, renamed == 0 ? "message.wrights.slip_folder_loaded" : "message.wrights.slip_folder_loaded_renamed", created == null ? name : created.name(), renamed);
    }

    private static void finish(ServerPlayer player, String key, String name, int renamed) {
        if (renamed > 0) {
            Messages.actionBar(player, key, name, renamed);
        } else {
            Messages.actionBar(player, key, name);
        }
        sendList(player);
    }

    private static int importChildren(SchematicLibrary library, Node node, UUID parent, List<String> taken) {
        int renamed = 0;
        Map<UUID, String> names = names(library);
        BlueprintShelf shelf = library.shelf();
        for (Node child : node.children) {
            if (child.folder()) {
                UUID id = UUID.randomUUID();
                if (!shelf.createFolder(id, parent, child.name, names)) {
                    continue;
                }
                renamed += importChildren(library, child, id, taken);
                continue;
            }
            if (child.schematic == null) {
                continue;
            }
            String wanted = BlueprintActions.cleanName(child.schematic.name());
            String unique = BlueprintActions.uniqueName(wanted, taken);
            if (!unique.equals(wanted)) {
                renamed++;
            }
            taken.add(unique);
            UUID id = UUID.randomUUID();
            library.putSchematic(child.schematic.duplicate(id, unique));
            names.put(id, unique);
            shelf.move(id, false, parent, Integer.MAX_VALUE, names);
        }
        return renamed;
    }

    private static boolean clearChildren(SchematicLibrary library, UUID root) {
        List<UUID> schematics = new ArrayList<>();
        List<Depth> folders = new ArrayList<>();
        collect(library, root, schematics, folders, 0);
        for (UUID id : schematics) {
            if (library.isInUse(id)) {
                return false;
            }
        }
        Map<UUID, String> names = names(library);
        for (UUID id : schematics) {
            names.remove(id);
            library.removeSchematic(id);
        }
        folders.sort(Comparator.comparingInt(Depth::depth).reversed());
        for (Depth depth : folders) {
            library.shelf().removeFolder(depth.id);
        }
        return true;
    }

    private static void collect(SchematicLibrary library, UUID folder, List<UUID> schematics, List<Depth> folders, int depth) {
        if (depth > 32) {
            return;
        }
        for (UUID child : library.shelf().contents(folder, names(library))) {
            if (library.shelf().isFolder(child)) {
                collect(library, child, schematics, folders, depth + 1);
                folders.add(new Depth(child, depth + 1));
            } else {
                schematics.add(child);
            }
        }
    }

    private static boolean inUse(SchematicLibrary library, UUID folder) {
        List<UUID> schematics = new ArrayList<>();
        collect(library, folder, schematics, new ArrayList<>(), 0);
        for (UUID id : schematics) {
            if (library.isInUse(id)) {
                return true;
            }
        }
        return false;
    }

    private static Match match(SchematicLibrary library, Node stored) {
        boolean exact = false;
        UUID replace = null;
        int libraryBlueprints = -1;
        List<BlueprintShelf.Folder> folders = new ArrayList<>(library.shelf().folders());
        folders.sort(Comparator.comparing(BlueprintShelf.Folder::name, String.CASE_INSENSITIVE_ORDER).thenComparing(folder -> folder.id().toString()));
        for (BlueprintShelf.Folder folder : folders) {
            if (!folder.name().equalsIgnoreCase(stored.name)) {
                continue;
            }
            Node tree = fromLibrary(library, folder.id());
            if (same(tree, stored)) {
                exact = true;
                if (libraryBlueprints < 0) {
                    libraryBlueprints = countBlueprints(tree);
                }
                continue;
            }
            if (replace == null) {
                replace = folder.id();
                libraryBlueprints = countBlueprints(tree);
            }
        }
        int kind = exact ? BlueprintSlips.EXACT : replace != null ? BlueprintSlips.DIFFERENT : BlueprintSlips.NEW;
        if (kind != BlueprintSlips.DIFFERENT) {
            replace = null;
        }
        return new Match(kind, replace, libraryBlueprints);
    }

    private static boolean same(Node left, Node right) {
        if (left.folder() != right.folder() || !left.name.equalsIgnoreCase(right.name)) {
            return false;
        }
        if (!left.folder()) {
            return left.schematic != null && right.schematic != null && left.schematic.sameBlocks(right.schematic);
        }
        if (left.children.size() != right.children.size()) {
            return false;
        }
        for (int i = 0; i < left.children.size(); i++) {
            if (!same(left.children.get(i), right.children.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Node fromLibrary(SchematicLibrary library, UUID folderId) {
        BlueprintShelf.Folder folder = library.shelf().folder(folderId);
        Node node = new Node(folder == null ? "Folder" : folder.name(), null, true);
        if (folder == null) {
            return node;
        }
        for (UUID child : library.shelf().contents(folderId, names(library))) {
            if (library.shelf().isFolder(child)) {
                node.children.add(fromLibrary(library, child));
                continue;
            }
            Schematic schematic = library.schematic(child).orElse(null);
            if (schematic == null || schematic.ephemeral()) {
                continue;
            }
            node.children.add(new Node(schematic.name(), schematic, false));
        }
        return node;
    }

    private static CompoundTag save(Node root) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", root.name);
        ListTag entries = new ListTag();
        write(root, -1, entries);
        tag.put("entries", entries);
        return tag;
    }

    private static void write(Node node, int parent, ListTag entries) {
        int index = entries.size();
        CompoundTag entry = new CompoundTag();
        entry.putBoolean("folder", node.folder());
        entry.putString("name", node.name);
        entry.putInt("parent", parent);
        if (!node.folder() && node.schematic != null) {
            entry.put("data", node.schematic.save());
        }
        entries.add(entry);
        for (Node child : node.children) {
            write(child, index, entries);
        }
    }

    private static Node read(ItemStack stack, ServerPlayer player) {
        if (!BlueprintFolderItem.hasData(stack)) {
            return null;
        }
        try {
            CompoundTag tag = BlueprintFolderItem.data(stack);
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            if (entries.isEmpty()) {
                return null;
            }
            List<Node> nodes = new ArrayList<>();
            int[] parents = new int[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                Schematic schematic = null;
                if (!entry.getBoolean("folder") && entry.contains("data")) {
                    schematic = Schematic.load(entry.getCompound("data"), Schematic.lookup(player.registryAccess()));
                }
                String name = entry.getString("name");
                boolean folder = entry.getBoolean("folder");
                nodes.add(new Node(name == null || name.isBlank() ? "Folder" : name, folder ? null : schematic, folder));
                parents[i] = entry.getInt("parent");
            }
            for (int i = 0; i < nodes.size(); i++) {
                int parent = parents[i];
                if (parent >= 0 && parent < nodes.size() && parent != i) {
                    nodes.get(parent).children.add(nodes.get(i));
                }
            }
            Node root = nodes.get(0);
            if (tag.contains("name") && !tag.getString("name").isBlank()) {
                root.name = tag.getString("name");
            }
            return root.folder() ? root : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int countFolders(Node node) {
        int count = node.folder() ? 1 : 0;
        for (Node child : node.children) {
            count += countFolders(child);
        }
        return count;
    }

    private static int countBlueprints(Node node) {
        int count = node.folder() ? 0 : 1;
        for (Node child : node.children) {
            count += countBlueprints(child);
        }
        return count;
    }

    private static List<String> folderNames(SchematicLibrary library) {
        List<String> names = new ArrayList<>();
        for (BlueprintShelf.Folder folder : library.shelf().folders()) {
            names.add(folder.name());
        }
        return names;
    }

    private static List<String> takenNames(SchematicLibrary library) {
        return new ArrayList<>(BlueprintActions.takenBlueprintNames(library));
    }

    private static Map<UUID, String> names(SchematicLibrary library) {
        Map<UUID, String> names = new HashMap<>();
        for (Schematic schematic : library.schematics()) {
            if (!schematic.ephemeral()) {
                names.put(schematic.id(), schematic.name());
            }
        }
        return names;
    }

    private static boolean blank(ItemStack stack) {
        return stack.getItem() instanceof BlueprintFolderItem && !BlueprintFolderItem.hasData(stack);
    }

    private static int findBlank(Inventory inventory) {
        for (int slot : slots(inventory)) {
            if (blank(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static List<Integer> slots(Inventory inventory) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            slots.add(slot);
        }
        slots.add(Inventory.SLOT_OFFHAND);
        return slots;
    }

    private static boolean fits(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(out));
            return out.size() <= MAX_BYTES;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class Node {
        private String name;
        private final Schematic schematic;
        private final boolean folder;
        private final List<Node> children = new ArrayList<>();

        private Node(String name, Schematic schematic, boolean folder) {
            this.name = name;
            this.schematic = schematic;
            this.folder = folder;
        }

        private boolean folder() {
            return folder;
        }
    }

    private record Match(int kind, UUID replaceId, int libraryBlueprints) {
    }

    private record Depth(UUID id, int depth) {
    }
}
