package com.wrights.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Folders, order, favorites, and use counts for the shared blueprint library.
 * Fold state, stars, and use counts are remembered per player.
 */
public final class BlueprintShelf {
    public static final class Folder {
        private final UUID id;
        private String name;
        private UUID parent;

        private Folder(UUID id, String name, UUID parent) {
            this.id = id;
            this.name = name;
            this.parent = parent;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public UUID parent() {
            return parent;
        }
    }

    private static final class Person {
        private final Set<UUID> favorites = new HashSet<>();
        private final Map<UUID, Integer> uses = new HashMap<>();
        private final Map<UUID, Long> lastUsed = new HashMap<>();
        private final Set<UUID> open = new HashSet<>();

        private boolean empty() {
            return favorites.isEmpty() && uses.isEmpty() && lastUsed.isEmpty() && open.isEmpty();
        }
    }

    private final Map<UUID, Folder> folders = new HashMap<>();
    private final Map<UUID, UUID> schematicParent = new HashMap<>();
    private final Map<UUID, Integer> order = new HashMap<>();
    private final Map<UUID, Person> people = new HashMap<>();

    public List<Folder> folders() {
        return List.copyOf(folders.values());
    }

    public UUID parentOf(UUID schematicId) {
        return schematicParent.get(schematicId);
    }

    public int orderOf(UUID id) {
        return order.getOrDefault(id, 0);
    }

    public boolean favorite(UUID player, UUID id) {
        Person person = people.get(player);
        return person != null && person.favorites.contains(id);
    }

    public int uses(UUID player, UUID id) {
        Person person = people.get(player);
        return person == null ? 0 : person.uses.getOrDefault(id, 0);
    }

    public long lastUsed(UUID player, UUID id) {
        Person person = people.get(player);
        return person == null ? 0L : person.lastUsed.getOrDefault(id, 0L);
    }

    public Set<UUID> openFolders(UUID player) {
        Person person = people.get(player);
        return person == null ? Set.of() : Set.copyOf(person.open);
    }

    public void noteUse(UUID player, UUID schematicId) {
        Person person = person(player);
        person.uses.merge(schematicId, 1, Integer::sum);
        person.lastUsed.put(schematicId, System.currentTimeMillis());
    }

    public boolean isFolder(UUID id) {
        return folders.containsKey(id);
    }

    public Folder folder(UUID id) {
        return folders.get(id);
    }

    public List<UUID> contents(UUID parent, Map<UUID, String> schematicNames) {
        return children(parent, schematicNames);
    }

    /** Puts {@code id} in {@code parent}, directly after {@code after}. A null {@code after} appends it. */
    public void placeAfter(UUID id, boolean folder, UUID parent, UUID after, Map<UUID, String> schematicNames) {
        if (folder) {
            if (!folders.containsKey(id)) {
                return;
            }
            if (parent != null && (!folders.containsKey(parent) || id.equals(parent) || holds(id, parent))) {
                parent = null;
            }
        } else if (parent != null && !folders.containsKey(parent)) {
            parent = null;
        }
        setParent(id, folder, parent);
        List<UUID> destination = children(parent, schematicNames);
        destination.remove(id);
        int at = destination.size();
        if (after != null) {
            int found = destination.indexOf(after);
            if (found >= 0) {
                at = found + 1;
            }
        }
        destination.add(Math.min(at, destination.size()), id);
        writeOrder(destination);
    }

    public void toggleFavorite(UUID player, UUID id) {
        if (folders.containsKey(id)) {
            return;
        }
        Person person = person(player);
        if (!person.favorites.remove(id)) {
            person.favorites.add(id);
        }
    }

    /** Folders are not favorites. Returns whether a saved mark was removed. */
    public boolean dropFolderFavorites() {
        boolean changed = false;
        for (Person person : people.values()) {
            changed |= person.favorites.removeIf(folders::containsKey);
        }
        return changed;
    }

    public void setOpen(UUID player, UUID folder, boolean opened) {
        if (!folders.containsKey(folder)) {
            return;
        }
        Person person = person(player);
        if (opened) {
            person.open.add(folder);
        } else {
            person.open.remove(folder);
        }
    }

    public boolean createFolder(UUID id, UUID parent, String requested, Map<UUID, String> schematicNames) {
        if (id == null || folders.containsKey(id) || schematicNames.containsKey(id)) {
            return false;
        }
        if (parent != null && !folders.containsKey(parent)) {
            parent = null;
        }
        String name = clean(requested, "Folder");
        folders.put(id, new Folder(id, name, parent));
        order.put(id, Integer.MAX_VALUE);
        writeOrder(children(parent, schematicNames));
        return true;
    }

    public void rename(UUID id, String requested) {
        Folder folder = folders.get(id);
        if (folder == null) {
            return;
        }
        folder.name = clean(requested, "Folder");
    }

    public void deleteFolder(UUID id, Map<UUID, String> schematicNames) {
        Folder folder = folders.get(id);
        if (folder == null) {
            return;
        }
        UUID parent = folder.parent;
        List<UUID> moving = children(id, schematicNames);
        int rank = 1_000_000;
        for (UUID child : moving) {
            order.put(child, rank++);
            setParent(child, folders.containsKey(child), parent);
        }
        folders.remove(id);
        order.remove(id);
        for (Person person : people.values()) {
            person.favorites.remove(id);
            person.open.remove(id);
        }
        writeOrder(children(parent, schematicNames));
    }

    public void move(UUID id, boolean folder, UUID parent, int index, Map<UUID, String> schematicNames) {
        if (folder) {
            if (!folders.containsKey(id)) {
                return;
            }
            if (parent != null && (!folders.containsKey(parent) || id.equals(parent) || holds(id, parent))) {
                return;
            }
        } else if (!schematicNames.containsKey(id)) {
            return;
        } else if (parent != null && !folders.containsKey(parent)) {
            return;
        }
        UUID previous = folder ? folders.get(id).parent : schematicParent.get(id);
        setParent(id, folder, parent);
        List<UUID> destination = children(parent, schematicNames);
        destination.remove(id);
        int at = Math.max(0, Math.min(index, destination.size()));
        destination.add(at, id);
        writeOrder(destination);
        if ((previous == null) != (parent == null) || (previous != null && !previous.equals(parent))) {
            writeOrder(children(previous, schematicNames));
        }
    }

    /** Removes a folder record. Children should already have been moved or removed. */
    public void removeFolder(UUID id) {
        if (folders.remove(id) == null) {
            return;
        }
        order.remove(id);
        schematicParent.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        for (Folder folder : folders.values()) {
            if (id.equals(folder.parent)) {
                folder.parent = null;
            }
        }
        for (Person person : people.values()) {
            person.favorites.remove(id);
            person.open.remove(id);
            person.uses.remove(id);
            person.lastUsed.remove(id);
        }
    }

    public void forget(UUID schematicId) {
        schematicParent.remove(schematicId);
        order.remove(schematicId);
        for (Person person : people.values()) {
            person.favorites.remove(schematicId);
            person.uses.remove(schematicId);
            person.lastUsed.remove(schematicId);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag folderList = new ListTag();
        for (Folder folder : folders.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", folder.id);
            entry.putString("name", folder.name);
            if (folder.parent != null) {
                entry.putUUID("parent", folder.parent);
            }
            entry.putInt("order", order.getOrDefault(folder.id, 0));
            folderList.add(entry);
        }
        tag.put("folders", folderList);
        ListTag placed = new ListTag();
        Set<UUID> schematics = new HashSet<>(schematicParent.keySet());
        for (Map.Entry<UUID, Integer> entry : order.entrySet()) {
            if (!folders.containsKey(entry.getKey())) {
                schematics.add(entry.getKey());
            }
        }
        for (UUID id : schematics) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            UUID parent = schematicParent.get(id);
            if (parent != null) {
                entry.putUUID("parent", parent);
            }
            entry.putInt("order", order.getOrDefault(id, 0));
            placed.add(entry);
        }
        tag.put("placed", placed);
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Person> entry : people.entrySet()) {
            if (entry.getValue().empty()) {
                continue;
            }
            Person person = entry.getValue();
            CompoundTag node = new CompoundTag();
            node.putUUID("player", entry.getKey());
            node.put("favorites", uuids(person.favorites));
            node.put("open", uuids(person.open));
            ListTag used = new ListTag();
            for (Map.Entry<UUID, Integer> use : person.uses.entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putUUID("id", use.getKey());
                item.putInt("n", use.getValue());
                Long last = person.lastUsed.get(use.getKey());
                if (last != null) {
                    item.putLong("last", last);
                }
                used.add(item);
            }
            for (Map.Entry<UUID, Long> last : person.lastUsed.entrySet()) {
                if (person.uses.containsKey(last.getKey())) {
                    continue;
                }
                CompoundTag item = new CompoundTag();
                item.putUUID("id", last.getKey());
                item.putLong("last", last.getValue());
                used.add(item);
            }
            node.put("used", used);
            players.add(node);
        }
        tag.put("players", players);
        return tag;
    }

    public void load(CompoundTag tag) {
        folders.clear();
        schematicParent.clear();
        order.clear();
        people.clear();
        ListTag folderList = tag.getList("folders", Tag.TAG_COMPOUND);
        for (int i = 0; i < folderList.size(); i++) {
            CompoundTag entry = folderList.getCompound(i);
            if (!entry.hasUUID("id")) {
                continue;
            }
            UUID id = entry.getUUID("id");
            UUID parent = entry.hasUUID("parent") ? entry.getUUID("parent") : null;
            folders.put(id, new Folder(id, clean(entry.getString("name"), "Folder"), parent));
            order.put(id, entry.getInt("order"));
        }
        for (Folder folder : folders.values()) {
            if (folder.parent != null && !folders.containsKey(folder.parent)) {
                folder.parent = null;
            }
        }
        ListTag placed = tag.getList("placed", Tag.TAG_COMPOUND);
        for (int i = 0; i < placed.size(); i++) {
            CompoundTag entry = placed.getCompound(i);
            if (!entry.hasUUID("id")) {
                continue;
            }
            UUID id = entry.getUUID("id");
            if (entry.hasUUID("parent") && folders.containsKey(entry.getUUID("parent"))) {
                schematicParent.put(id, entry.getUUID("parent"));
            }
            order.put(id, entry.getInt("order"));
        }
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag node = players.getCompound(i);
            if (!node.hasUUID("player")) {
                continue;
            }
            Person person = person(node.getUUID("player"));
            readIds(node.getList("favorites", Tag.TAG_COMPOUND), person.favorites);
            person.favorites.removeIf(folders::containsKey);
            readIds(node.getList("open", Tag.TAG_COMPOUND), person.open);
            person.open.removeIf(id -> !folders.containsKey(id));
            ListTag used = node.getList("used", Tag.TAG_COMPOUND);
            for (int n = 0; n < used.size(); n++) {
                CompoundTag item = used.getCompound(n);
                if (!item.hasUUID("id")) {
                    continue;
                }
                UUID id = item.getUUID("id");
                if (item.contains("n")) {
                    person.uses.put(id, item.getInt("n"));
                }
                if (item.contains("last")) {
                    person.lastUsed.put(id, item.getLong("last"));
                }
            }
        }
    }

    private boolean holds(UUID ancestor, UUID node) {
        UUID cursor = node;
        for (int guard = 0; cursor != null && guard < 64; guard++) {
            if (cursor.equals(ancestor)) {
                return true;
            }
            Folder folder = folders.get(cursor);
            cursor = folder == null ? null : folder.parent;
        }
        return false;
    }

    private void setParent(UUID id, boolean folder, UUID parent) {
        if (folder) {
            Folder moving = folders.get(id);
            if (moving != null) {
                moving.parent = parent;
            }
            return;
        }
        if (parent == null) {
            schematicParent.remove(id);
        } else {
            schematicParent.put(id, parent);
        }
    }

    private List<UUID> children(UUID parent, Map<UUID, String> schematicNames) {
        record Item(UUID id, int order, String name) {
        }
        List<Item> items = new ArrayList<>();
        for (Folder folder : folders.values()) {
            if (same(folder.parent, parent)) {
                items.add(new Item(folder.id, order.getOrDefault(folder.id, 0), folder.name));
            }
        }
        for (Map.Entry<UUID, String> entry : schematicNames.entrySet()) {
            if (same(schematicParent.get(entry.getKey()), parent)) {
                items.add(new Item(entry.getKey(), order.getOrDefault(entry.getKey(), 0), entry.getValue()));
            }
        }
        items.sort(Comparator.comparingInt(Item::order).thenComparing(Item::name, String.CASE_INSENSITIVE_ORDER).thenComparing(item -> item.id.toString()));
        List<UUID> ids = new ArrayList<>(items.size());
        for (Item item : items) {
            ids.add(item.id);
        }
        return ids;
    }

    private static boolean same(UUID parent, UUID wanted) {
        return parent == null ? wanted == null : parent.equals(wanted);
    }

    private void writeOrder(List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            order.put(ids.get(i), i);
        }
    }

    private static String clean(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim().replace('\r', ' ').replace('\n', ' ');
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.length() > 48 ? trimmed.substring(0, 48) : trimmed;
    }

    private Person person(UUID player) {
        return people.computeIfAbsent(player, id -> new Person());
    }

    private static ListTag uuids(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            list.add(entry);
        }
        return list;
    }

    private static void readIds(ListTag list, Set<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id")) {
                into.add(entry.getUUID("id"));
            }
        }
    }
}
