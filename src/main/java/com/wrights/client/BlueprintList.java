package com.wrights.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.Util;

import com.wrights.network.BlueprintPayloads;

/** Visible rows of the blueprint menu: a folder tree, or a flat tab. */
public final class BlueprintList {
    public enum Tab {
        ALL,
        LAST,
        MOST,
        FAVORITES
    }

    public record Row(UUID id, boolean folder, int depth, int index, UUID parent, String name, boolean open, boolean favorite, BlueprintPayloads.Summary summary) {
    }

    private record Item(UUID id, boolean folder, int order, String name) {
    }

    private BlueprintList() {
    }

    public static List<Row> rows(Tab tab, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        if (tab == Tab.ALL) {
            walk(Util.NIL_UUID, 0, needle, rows);
            return rows;
        }
        if (tab == Tab.FAVORITES) {
            List<Row> found = new ArrayList<>();
            for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
                if (summary.favorite() && (needle.isEmpty() || summary.name().toLowerCase(Locale.ROOT).contains(needle))) {
                    found.add(new Row(summary.id(), false, 0, 0, Util.NIL_UUID, summary.name(), false, true, summary));
                }
            }
            found.sort(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER).thenComparing(row -> row.id().toString()));
            return found;
        }
        List<BlueprintPayloads.Summary> summaries = new ArrayList<>();
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (!needle.isEmpty() && !summary.name().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (tab == Tab.LAST && summary.lastUsed() <= 0L) {
                continue;
            }
            if (tab == Tab.MOST && summary.uses() <= 0) {
                continue;
            }
            summaries.add(summary);
        }
        if (tab == Tab.LAST) {
            summaries.sort(Comparator.comparingLong(BlueprintPayloads.Summary::lastUsed).reversed()
                    .thenComparing(BlueprintPayloads.Summary::name, String.CASE_INSENSITIVE_ORDER));
        } else {
            summaries.sort(Comparator.comparingInt(BlueprintPayloads.Summary::uses).reversed()
                    .thenComparing(Comparator.comparingLong(BlueprintPayloads.Summary::lastUsed).reversed())
                    .thenComparing(BlueprintPayloads.Summary::name, String.CASE_INSENSITIVE_ORDER));
        }
        for (BlueprintPayloads.Summary summary : summaries) {
            rows.add(new Row(summary.id(), false, 0, 0, Util.NIL_UUID, summary.name(), false, summary.favorite(), summary));
        }
        return rows;
    }

    public static int childCount(UUID folder) {
        return children(folder).size();
    }

    public static boolean holds(UUID ancestor, UUID node) {
        if (root(ancestor) || root(node)) {
            return false;
        }
        UUID cursor = node;
        for (int guard = 0; cursor != null && !root(cursor) && guard < 64; guard++) {
            if (cursor.equals(ancestor)) {
                return true;
            }
            BlueprintPayloads.FolderLine folder = folder(cursor);
            if (folder == null) {
                return false;
            }
            cursor = folder.parent();
        }
        return false;
    }

    public static List<UUID> chain(UUID folder) {
        List<UUID> ids = new ArrayList<>();
        UUID cursor = folder;
        for (int guard = 0; cursor != null && !root(cursor) && guard < 64; guard++) {
            ids.add(cursor);
            BlueprintPayloads.FolderLine line = folder(cursor);
            cursor = line == null ? null : line.parent();
        }
        return ids;
    }

    private static void walk(UUID parent, int depth, String query, List<Row> out) {
        if (depth > 16) {
            return;
        }
        List<Item> kids = children(parent);
        for (int index = 0; index < kids.size(); index++) {
            Item child = kids.get(index);
            UUID parentId = root(parent) ? Util.NIL_UUID : parent;
            if (child.folder) {
                boolean self = query.isEmpty() || child.name.toLowerCase(Locale.ROOT).contains(query);
                boolean below = !query.isEmpty() && subtreeMatches(child.id, query);
                if (!query.isEmpty() && !self && !below) {
                    continue;
                }
                boolean open = WrightsClientState.openFolders.contains(child.id) || below;
                out.add(new Row(child.id, true, depth, index, parentId, child.name, open, false, null));
                if (open) {
                    walk(child.id, depth + 1, query, out);
                }
            } else if (query.isEmpty() || child.name.toLowerCase(Locale.ROOT).contains(query)) {
                BlueprintPayloads.Summary summary = summary(child.id);
                if (summary != null) {
                    out.add(new Row(child.id, false, depth, index, parentId, child.name, false, summary.favorite(), summary));
                }
            }
        }
    }

    private static boolean subtreeMatches(UUID folder, String query) {
        for (Item child : children(folder)) {
            if (child.name.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            if (child.folder && subtreeMatches(child.id, query)) {
                return true;
            }
        }
        return false;
    }

    private static List<Item> children(UUID parent) {
        List<Item> items = new ArrayList<>();
        for (BlueprintPayloads.FolderLine folder : WrightsClientState.folders) {
            if (same(folder.parent(), parent)) {
                items.add(new Item(folder.id(), true, folder.order(), folder.name()));
            }
        }
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (same(summary.folder(), parent)) {
                items.add(new Item(summary.id(), false, summary.order(), summary.name()));
            }
        }
        items.sort(Comparator.comparingInt(Item::order).thenComparing(Item::name, String.CASE_INSENSITIVE_ORDER).thenComparing(item -> item.id.toString()));
        return items;
    }

    private static boolean same(UUID parent, UUID wanted) {
        return root(parent) == root(wanted) && (root(wanted) || parent.equals(wanted));
    }

    private static boolean root(UUID id) {
        return id == null || id.equals(Util.NIL_UUID);
    }

    private static BlueprintPayloads.FolderLine folder(UUID id) {
        for (BlueprintPayloads.FolderLine folder : WrightsClientState.folders) {
            if (folder.id().equals(id)) {
                return folder;
            }
        }
        return null;
    }

    private static BlueprintPayloads.Summary summary(UUID id) {
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (summary.id().equals(id)) {
                return summary;
            }
        }
        return null;
    }
}
