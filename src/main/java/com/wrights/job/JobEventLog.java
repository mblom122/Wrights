package com.wrights.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import com.wrights.WrightsConfig;
import com.wrights.network.BlueprintNetwork;

/**
 * Per-player info log for History: completed jobs, returns, stalls, pauses, and similar events.
 * Newest first. Capped so the window stays readable.
 */
public final class JobEventLog {
    public static final int COMPLETED = 0;
    public static final int STOPPED = 1;
    public static final int UNDONE = 2;
    public static final int PAUSED = 3;
    public static final int RESUMED = 4;
    public static final int STALLED_ITEMS = 5;
    public static final int OUT_OF_RANGE = 6;
    public static final int WRONG_DIMENSION = 7;
    public static final int REJOINED = 8;
    public static final int LAUNCHED = 9;
    public static final int QUEUED = 10;
    public static final int REMOVED = 11;
    public static final int COPY_DONE = 12;
    public static final int UNDOING = 13;
    /** A wright finished its share and took half of a busier share on the same job. */
    public static final int SHARE_SPLIT = 14;

    private static final int MAX_EVENTS = 80;

    public record Event(int kind, String jobName, String detail, String location, long gameTime) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("kind", kind);
            tag.putString("name", jobName == null ? "" : jobName);
            tag.putString("detail", detail == null ? "" : detail);
            tag.putString("loc", location == null ? "" : location);
            tag.putLong("time", gameTime);
            return tag;
        }

        public static Event load(CompoundTag tag) {
            return new Event(
                    tag.getInt("kind"),
                    tag.getString("name"),
                    tag.getString("detail"),
                    tag.contains("loc") ? tag.getString("loc") : "",
                    tag.getLong("time"));
        }
    }

    private final Map<UUID, List<Event>> byPlayer = new HashMap<>();

    public List<Event> events(UUID playerId) {
        List<Event> list = byPlayer.get(playerId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void record(ServerPlayer player, int kind, String jobName, String detail, String location) {
        if (player == null) {
            return;
        }
        long time = player.level().getGameTime();
        List<Event> list = byPlayer.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        // Skip spam: stalls/range stay one line until another event for that job; others debounce briefly.
        if (!list.isEmpty()) {
            Event last = list.get(0);
            if (last.kind() == kind && safe(last.jobName()).equals(safe(jobName))) {
                if (kind == STALLED_ITEMS || kind == OUT_OF_RANGE || kind == WRONG_DIMENSION) {
                    return;
                }
                if (time - last.gameTime() < 40L) {
                    return;
                }
            }
        }
        list.add(0, new Event(kind, safe(jobName), safe(detail), safe(location), time));
        int cap = Math.max(MAX_EVENTS, WrightsConfig.maxJobHistory() * 2);
        while (list.size() > cap) {
            list.remove(list.size() - 1);
        }
        BlueprintNetwork.syncHistory(player, list);
    }

    public void load(CompoundTag tag) {
        byPlayer.clear();
        if (tag == null || !tag.contains("players", Tag.TAG_LIST)) {
            return;
        }
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (!entry.hasUUID("player")) {
                continue;
            }
            ListTag events = entry.getList("events", Tag.TAG_COMPOUND);
            List<Event> list = new ArrayList<>(events.size());
            for (int e = 0; e < events.size(); e++) {
                list.add(Event.load(events.getCompound(e)));
            }
            byPlayer.put(entry.getUUID("player"), list);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<Event>> entry : byPlayer.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("player", entry.getKey());
            ListTag events = new ListTag();
            for (Event event : entry.getValue()) {
                events.add(event.save());
            }
            row.put("events", events);
            players.add(row);
        }
        tag.put("players", players);
        return tag;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
