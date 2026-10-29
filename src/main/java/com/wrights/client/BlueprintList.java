package com.wrights.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.Util;

import com.wrights.WrightsClientConfig;
import com.wrights.network.BlueprintPayloads;

/** Visible rows of the blueprint menu: a folder tree, or a flat tab. */
public final class BlueprintList {
    public enum Tab {
        ALL,
        LAST,
        MOST,
        FAVORITES
    }

    /** Stored by ordinal in the client config. Do not reorder. */
    public enum SortKey {
        MANUAL,
        NAME,
        SIZE,
        BLOCKS,
        USED,
        LAST
    }

    public record Row(UUID id, boolean folder, int depth, int index, UUID parent, String name, boolean open, boolean favorite, BlueprintPayloads.Summary summary) {
    }

    private record Item(UUID id, boolean folder, int order, String name, long size, int blocks, int uses, long lastUsed) {
    }

    private BlueprintList() {
    }

    public static List<Row> rows(Tab tab, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int key = WrightsClientConfig.sortKey(tab.ordinal());
        boolean ascending = WrightsClientConfig.sortAscending(tab.ordinal());
        List<Row> rows = new ArrayList<>();
        if (tab == Tab.ALL) {
            walk(Util.NIL_UUID, 0, needle, key, ascending, rows);
            return rows;
        }
        List<BlueprintPayloads.Summary> summaries = new ArrayList<>();
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (!needle.isEmpty() && !summary.name().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (tab == Tab.FAVORITES && !summary.favorite()) {
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
        sortSummaries(summaries, tab, key, ascending);
        for (int index = 0; index < summaries.size(); index++) {
            BlueprintPayloads.Summary summary = summaries.get(index);
            rows.add(new Row(summary.id(), false, 0, index, Util.NIL_UUID, summary.name(), false, summary.favorite(), summary));
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

    private static void sortSummaries(List<BlueprintPayloads.Summary> summaries, Tab tab, int key, boolean ascending) {
        Comparator<BlueprintPayloads.Summary> name = Comparator.comparing(BlueprintPayloads.Summary::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<BlueprintPayloads.Summary> id = Comparator.comparing(summary -> summary.id().toString());
        if (key == SortKey.MANUAL.ordinal()) {
            Map<UUID, Integer> rank = manualRank(tab);
            summaries.sort(Comparator.comparingInt((BlueprintPayloads.Summary summary) -> rank.getOrDefault(summary.id(), Integer.MAX_VALUE)).thenComparing(name).thenComparing(id));
            return;
        }
        Comparator<BlueprintPayloads.Summary> primary = switch (key) {
            case 2 -> Comparator.comparingLong(BlueprintList::volume);
            case 3 -> Comparator.comparingInt(BlueprintPayloads.Summary::blocks);
            case 4 -> Comparator.comparingInt(BlueprintPayloads.Summary::uses);
            case 5 -> Comparator.comparingLong(BlueprintPayloads.Summary::lastUsed);
            default -> name;
        };
        if (!ascending) {
            primary = primary.reversed();
        }
        summaries.sort(key == SortKey.NAME.ordinal() ? primary.thenComparing(id) : primary.thenComparing(name).thenComparing(id));
    }

    private static Map<UUID, Integer> manualRank(Tab tab) {
        List<UUID> saved = WrightsClientConfig.manualOrder(tab.ordinal());
        Map<UUID, Integer> rank = new HashMap<>();
        for (int i = 0; i < saved.size(); i++) {
            rank.putIfAbsent(saved.get(i), i);
        }
        return rank;
    }

    private static void walk(UUID parent, int depth, String query, int key, boolean ascending, List<Row> out) {
        if (depth > 16) {
            return;
        }
        List<Item> kids = children(parent, key, ascending);
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
                    walk(child.id, depth + 1, query, key, ascending, out);
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
        return children(parent, SortKey.MANUAL.ordinal(), true);
    }

    private static List<Item> children(UUID parent, int key, boolean ascending) {
        List<Item> items = new ArrayList<>();
        for (BlueprintPayloads.FolderLine folder : WrightsClientState.folders) {
            if (same(folder.parent(), parent)) {
                long[] measured = measure(folder.id(), 0);
                items.add(new Item(folder.id(), true, folder.order(), folder.name(), measured[0], (int) measured[1], (int) measured[2], measured[3]));
            }
        }
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (same(summary.folder(), parent)) {
                items.add(new Item(summary.id(), false, summary.order(), summary.name(), volume(summary), summary.blocks(), summary.uses(), summary.lastUsed()));
            }
        }
        items.sort(itemOrder(key, ascending));
        return items;
    }

    private static Comparator<Item> itemOrder(int key, boolean ascending) {
        Comparator<Item> name = Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<Item> id = Comparator.comparing(item -> item.id.toString());
        if (key == SortKey.MANUAL.ordinal()) {
            return Comparator.comparingInt(Item::order).thenComparing(name).thenComparing(id);
        }
        Comparator<Item> primary = switch (key) {
            case 2 -> Comparator.comparingLong(Item::size);
            case 3 -> Comparator.comparingInt(Item::blocks);
            case 4 -> Comparator.comparingInt(Item::uses);
            case 5 -> Comparator.comparingLong(Item::lastUsed);
            default -> name;
        };
        if (!ascending) {
            primary = primary.reversed();
        }
        return key == SortKey.NAME.ordinal() ? primary.thenComparing(id) : primary.thenComparing(name).thenComparing(id);
    }

    private static long[] measure(UUID folder, int depth) {
        long size = 0L;
        long blocks = 0L;
        long uses = 0L;
        long last = 0L;
        if (depth > 16) {
            return new long[] {0L, 0L, 0L, 0L};
        }
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (!same(summary.folder(), folder)) {
                continue;
            }
            size += volume(summary);
            blocks += summary.blocks();
            uses += summary.uses();
            last = Math.max(last, summary.lastUsed());
        }
        for (BlueprintPayloads.FolderLine child : WrightsClientState.folders) {
            if (!same(child.parent(), folder)) {
                continue;
            }
            long[] nested = measure(child.id(), depth + 1);
            size += nested[0];
            blocks += nested[1];
            uses += nested[2];
            last = Math.max(last, nested[3]);
        }
        return new long[] {size, blocks, uses, last};
    }

    private static long volume(BlueprintPayloads.Summary summary) {
        return (long) Math.max(0, summary.sizeX()) * Math.max(0, summary.sizeY()) * Math.max(0, summary.sizeZ());
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
