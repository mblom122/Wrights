package com.autobots.schematic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.autobots.job.BuildJob;

public final class SchematicLibrary extends SavedData {
    public static final String DATA_ID = "autobots";
    public static final SavedData.Factory<SchematicLibrary> FACTORY = new SavedData.Factory<>(
            SchematicLibrary::new,
            SchematicLibrary::load,
            null);

    private final Map<UUID, Schematic> schematics = new HashMap<>();
    private final Map<UUID, BuildJob> jobs = new HashMap<>();
    private final Map<UUID, UUID> jobByPlayer = new HashMap<>();

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
            library.jobByPlayer.put(job.owner(), job.id());
        }
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
        UUID jobId = jobByPlayer.get(playerId);
        if (jobId == null) {
            return Optional.empty();
        }
        BuildJob job = jobs.get(jobId);
        if (job == null) {
            jobByPlayer.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(job);
    }

    public Collection<BuildJob> jobs() {
        return jobs.values();
    }

    public void addJob(BuildJob job) {
        jobs.put(job.id(), job);
        jobByPlayer.put(job.owner(), job.id());
        setDirty();
    }

    public void removeJob(BuildJob job) {
        jobs.remove(job.id());
        jobByPlayer.remove(job.owner(), job.id());
        setDirty();
    }
}
