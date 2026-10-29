package com.wrights.schematic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.wrights.job.BuildJob;

public final class SchematicLibrary extends SavedData {
    public static final String DATA_ID = "wrights";
    public static final SavedData.Factory<SchematicLibrary> FACTORY = new SavedData.Factory<>(
            SchematicLibrary::new,
            SchematicLibrary::load,
            null);

    private final Map<UUID, Schematic> schematics = new HashMap<>();
    private final Map<UUID, BuildJob> jobs = new HashMap<>();
    private final Map<UUID, List<UUID>> jobsByPlayer = new HashMap<>();
    private int historyClock;

    public static SchematicLibrary get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    public static SchematicLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
        SchematicLibrary library = new SchematicLibrary();
        var blocks = Schematic.lookup(registries);
        ListTag schematics = tag.getList("schematics", Tag.TAG_COMPOUND);
        for (int i = 0; i < schematics.size(); i++) {
            Schematic schematic = Schematic.load(schematics.getCompound(i), blocks);
            library.schematics.put(schematic.id(), schematic);
        }
        ListTag jobs = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            BuildJob job = BuildJob.load(jobs.getCompound(i), blocks);
            library.jobs.put(job.id(), job);
            library.jobsByPlayer.computeIfAbsent(job.owner(), id -> new ArrayList<>()).add(job.id());
        }
        library.historyClock = tag.getInt("historyClock");
        return library;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag schematics = new ListTag();
        for (Schematic schematic : this.schematics.values()) {
            schematics.add(schematic.save());
        }
        tag.put("schematics", schematics);
        ListTag jobs = new ListTag();
        for (BuildJob job : this.jobs.values()) {
            jobs.add(job.save());
        }
        tag.put("jobs", jobs);
        tag.putInt("historyClock", historyClock);
        return tag;
    }

    public Optional<Schematic> schematic(UUID id) {
        return Optional.ofNullable(schematics.get(id));
    }

    public Collection<Schematic> schematics() {
        return schematics.values();
    }

    public void putSchematic(Schematic schematic) {
        schematics.put(schematic.id(), schematic);
        setDirty();
    }

    public void removeSchematic(UUID id) {
        if (schematics.remove(id) != null) {
            setDirty();
        }
    }

    public boolean isInUse(UUID schematicId) {
        for (BuildJob job : jobs.values()) {
            if (job.finished()) {
                continue;
            }
            if (schematicId.equals(job.schematicId()) || schematicId.equals(job.resultSchematicId())) {
                return true;
            }
        }
        return false;
    }

    public Optional<BuildJob> job(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Optional<BuildJob> jobFor(UUID playerId) {
        for (BuildJob job : jobsFor(playerId)) {
            if (!job.finished()) {
                return Optional.of(job);
            }
        }
        return Optional.empty();
    }

    public int nextHistoryOrder() {
        historyClock++;
        setDirty();
        return historyClock;
    }

    public void pruneHistory(UUID playerId) {
        List<BuildJob> done = new ArrayList<>();
        for (BuildJob job : jobsFor(playerId)) {
            if (job.finished()) {
                done.add(job);
            }
        }
        done.sort(Comparator.comparingInt(BuildJob::historyOrder));
        while (done.size() > 32) {
            removeJob(done.remove(0));
        }
    }

    public List<BuildJob> jobsFor(UUID playerId) {
        List<UUID> ids = jobsByPlayer.get(playerId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<BuildJob> found = new ArrayList<>();
        ids.removeIf(id -> {
            BuildJob job = jobs.get(id);
            if (job == null) {
                return true;
            }
            found.add(job);
            return false;
        });
        if (ids.isEmpty()) {
            jobsByPlayer.remove(playerId);
        }
        return found;
    }

    public Collection<BuildJob> jobs() {
        return jobs.values();
    }

    public void addJob(BuildJob job) {
        jobs.put(job.id(), job);
        List<UUID> ids = jobsByPlayer.computeIfAbsent(job.owner(), id -> new ArrayList<>());
        if (!ids.contains(job.id())) {
            ids.add(job.id());
        }
        setDirty();
    }

    public void removeJob(BuildJob job) {
        if (job.schematicId() != null) {
            Schematic schematic = schematics.get(job.schematicId());
            if (schematic != null && schematic.ephemeral()) {
                schematics.remove(schematic.id());
            }
        }
        jobs.remove(job.id());
        List<UUID> ids = jobsByPlayer.get(job.owner());
        if (ids != null) {
            ids.remove(job.id());
            if (ids.isEmpty()) {
                jobsByPlayer.remove(job.owner());
            }
        }
        setDirty();
    }
}
