package com.wrights.schematic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import com.wrights.job.BuildJob;
import com.wrights.storage.DigitalLink;

public final class SchematicLibrary extends SavedData {
    public static final String DATA_ID = "wrights";
    public static final SavedData.Factory<SchematicLibrary> FACTORY = new SavedData.Factory<>(
            SchematicLibrary::new,
            SchematicLibrary::load,
            null);

    private final Map<UUID, Schematic> schematics = new HashMap<>();
    private final BlueprintShelf shelf = new BlueprintShelf();
    private final Map<UUID, BuildJob> jobs = new HashMap<>();
    private final Map<UUID, List<UUID>> jobsByPlayer = new HashMap<>();
    private final Map<UUID, GlobalPos> supplies = new HashMap<>();
    private final Map<UUID, DigitalLink> digitalLinks = new HashMap<>();
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
            BuildJob job = BuildJob.load(jobs.getCompound(i), blocks, registries);
            library.jobs.put(job.id(), job);
            library.jobsByPlayer.computeIfAbsent(job.owner(), id -> new ArrayList<>()).add(job.id());
        }
        library.historyClock = tag.getInt("historyClock");
        if (tag.contains("shelf", Tag.TAG_COMPOUND)) {
            library.shelf.load(tag.getCompound("shelf"));
        }
        if (tag.contains("supplies", Tag.TAG_LIST)) {
            ListTag supplies = tag.getList("supplies", Tag.TAG_COMPOUND);
            for (int i = 0; i < supplies.size(); i++) {
                CompoundTag supply = supplies.getCompound(i);
                if (!supply.hasUUID("player")) {
                    continue;
                }
                BlockPos pos = NbtUtils.readBlockPos(supply, "pos").orElse(null);
                ResourceLocation dimension = ResourceLocation.tryParse(supply.getString("dim"));
                if (pos == null || dimension == null) {
                    continue;
                }
                library.supplies.put(supply.getUUID("player"), GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), pos));
            }
        }
        if (tag.contains("digitalLinks", Tag.TAG_LIST)) {
            ListTag links = tag.getList("digitalLinks", Tag.TAG_COMPOUND);
            for (int i = 0; i < links.size(); i++) {
                CompoundTag entry = links.getCompound(i);
                if (!entry.hasUUID("player") || !entry.contains("link", Tag.TAG_COMPOUND)) {
                    continue;
                }
                DigitalLink link = DigitalLink.load(entry.getCompound("link"));
                if (link.bound()) {
                    library.digitalLinks.put(entry.getUUID("player"), link);
                }
            }
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
            jobs.add(job.save(registries));
        }
        tag.put("jobs", jobs);
        tag.putInt("historyClock", historyClock);
        tag.put("shelf", shelf.save());
        ListTag supplies = new ListTag();
        for (Map.Entry<UUID, GlobalPos> entry : this.supplies.entrySet()) {
            CompoundTag supply = new CompoundTag();
            supply.putUUID("player", entry.getKey());
            supply.putString("dim", entry.getValue().dimension().location().toString());
            supply.put("pos", NbtUtils.writeBlockPos(entry.getValue().pos()));
            supplies.add(supply);
        }
        tag.put("supplies", supplies);
        ListTag digitalLinks = new ListTag();
        for (Map.Entry<UUID, DigitalLink> entry : this.digitalLinks.entrySet()) {
            if (!entry.getValue().bound()) {
                continue;
            }
            CompoundTag link = new CompoundTag();
            link.putUUID("player", entry.getKey());
            link.put("link", entry.getValue().save());
            digitalLinks.add(link);
        }
        tag.put("digitalLinks", digitalLinks);
        return tag;
    }

    /** The chest every blueprint for this player draws from, until it is cleared. */
    public GlobalPos supply(UUID playerId) {
        return supplies.get(playerId);
    }

    public void setSupply(UUID playerId, GlobalPos pos) {
        if (pos == null) {
            if (supplies.remove(playerId) != null) {
                setDirty();
            }
            return;
        }
        supplies.put(playerId, pos);
        setDirty();
    }

    public DigitalLink digital(UUID playerId) {
        DigitalLink link = digitalLinks.get(playerId);
        return link == null ? DigitalLink.NONE : link;
    }

    public void setDigital(UUID playerId, DigitalLink link) {
        if (link == null || !link.bound()) {
            if (digitalLinks.remove(playerId) != null) {
                setDirty();
            }
            return;
        }
        digitalLinks.put(playerId, link);
        setDirty();
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

    public BlueprintShelf shelf() {
        return shelf;
    }

    public void removeSchematic(UUID id) {
        if (schematics.remove(id) != null) {
            shelf.forget(id);
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

    /**
     * Keep active jobs running when a library blueprint is deleted or moved by
     * pointing them at an ephemeral snapshot of the same blocks.
     */
    public void detachJobs(UUID schematicId) {
        if (schematicId == null || !isInUse(schematicId)) {
            return;
        }
        Schematic schematic = schematics.get(schematicId);
        if (schematic == null) {
            return;
        }
        Schematic snap = schematic.ephemeralCopy();
        schematics.put(snap.id(), snap);
        for (BuildJob job : jobs.values()) {
            if (job.finished()) {
                continue;
            }
            if (schematicId.equals(job.schematicId())) {
                job.setSchematicId(snap.id());
            }
            if (schematicId.equals(job.resultSchematicId())) {
                job.setResultSchematicId(snap.id());
            }
        }
        setDirty();
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
                shelf.forget(schematic.id());
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
