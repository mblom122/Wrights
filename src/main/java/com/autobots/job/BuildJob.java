package com.autobots.job;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.autobots.schematic.Schematic;

public final class BuildJob {
    public enum Kind {
        COPY,
        PASTE
    }

    public enum Phase {
        RUNNING,
        BINDING
    }

    private final UUID id;
    private final UUID owner;
    private final UUID controllerId;
    private final Kind kind;
    private final ResourceKey<Level> dimension;
    private final BlockPos min;
    private final BlockPos max;
    private final int total;
    private final List<Schematic.Entry> partial = new ArrayList<>();

    private UUID schematicId;
    private UUID resultSchematicId;
    private int cursor;
    private int placed;
    private int skipped;
    private int skippedSpecial;
    private UUID droneId;
    private BlockPos lastDronePos;
    private Phase phase = Phase.RUNNING;
    private int messageDelay;

    private BuildJob(UUID id, UUID owner, UUID controllerId, Kind kind, ResourceKey<Level> dimension, BlockPos min, BlockPos max, int total) {
        this.id = id;
        this.owner = owner;
        this.controllerId = controllerId;
        this.kind = kind;
        this.dimension = dimension;
        this.min = min;
        this.max = max;
        this.total = total;
    }

    public static BuildJob copy(UUID owner, UUID controllerId, ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;
        return new BuildJob(UUID.randomUUID(), owner, controllerId, Kind.COPY, dimension, min, max, width * height * depth);
    }

    public static BuildJob paste(UUID owner, UUID controllerId, ResourceKey<Level> dimension, BlockPos origin, UUID schematicId, int total) {
        BuildJob job = new BuildJob(UUID.randomUUID(), owner, controllerId, Kind.PASTE, dimension, origin, origin, total);
        job.schematicId = schematicId;
        return job;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public UUID controllerId() {
        return controllerId;
    }

    public Kind kind() {
        return kind;
    }

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public int total() {
        return total;
    }

    public int cursor() {
        return cursor;
    }

    public void advance() {
        cursor++;
    }

    public int placed() {
        return placed;
    }

    public void placedOne() {
        placed++;
    }

    public int skipped() {
        return skipped;
    }

    public void skippedOne() {
        skipped++;
    }

    public int skippedSpecial() {
        return skippedSpecial;
    }

    public void skippedSpecialOne() {
        skippedSpecial++;
    }

    public UUID schematicId() {
        return schematicId;
    }

    public UUID resultSchematicId() {
        return resultSchematicId;
    }

    public void setResultSchematicId(UUID resultSchematicId) {
        this.resultSchematicId = resultSchematicId;
    }

    public UUID droneId() {
        return droneId;
    }

    public void setDroneId(UUID droneId) {
        this.droneId = droneId;
    }

    public BlockPos lastDronePos() {
        return lastDronePos;
    }

    public void setLastDronePos(BlockPos lastDronePos) {
        this.lastDronePos = lastDronePos;
    }

    public List<Schematic.Entry> partial() {
        return partial;
    }

    public boolean tickMessage() {
        if (messageDelay > 0) {
            messageDelay--;
            return false;
        }
        messageDelay = 20;
        return true;
    }

    public BlockPos scanPos() {
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int x = min.getX() + (cursor % width);
        int y = min.getY() + ((cursor / width) % height);
        int z = min.getZ() + (cursor / (width * height));
        return new BlockPos(x, y, z);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner", owner);
        tag.putUUID("controller", controllerId);
        tag.putString("kind", kind.name());
        tag.putString("phase", phase.name());
        tag.putString("dimension", dimension.location().toString());
        tag.put("min", NbtUtils.writeBlockPos(min));
        tag.put("max", NbtUtils.writeBlockPos(max));
        tag.putInt("total", total);
        tag.putInt("cursor", cursor);
        tag.putInt("placed", placed);
        tag.putInt("skipped", skipped);
        tag.putInt("skippedSpecial", skippedSpecial);
        if (schematicId != null) {
            tag.putUUID("schematic", schematicId);
        }
        if (resultSchematicId != null) {
            tag.putUUID("result", resultSchematicId);
        }
        if (droneId != null) {
            tag.putUUID("drone", droneId);
        }
        if (lastDronePos != null) {
            tag.put("dronePos", NbtUtils.writeBlockPos(lastDronePos));
        }
        ListTag list = new ListTag();
        for (Schematic.Entry entry : partial) {
            list.add(entry.save());
        }
        tag.put("partial", list);
        return tag;
    }

    public static BuildJob load(CompoundTag tag, HolderGetter<Block> blocks) {
        ResourceLocation dimensionId = ResourceLocation.parse(tag.getString("dimension"));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BuildJob job = new BuildJob(
                tag.getUUID("id"),
                tag.getUUID("owner"),
                tag.getUUID("controller"),
                Kind.valueOf(tag.getString("kind")),
                dimension,
                NbtUtils.readBlockPos(tag, "min").orElse(BlockPos.ZERO),
                NbtUtils.readBlockPos(tag, "max").orElse(BlockPos.ZERO),
                tag.getInt("total"));
        job.phase = Phase.valueOf(tag.getString("phase"));
        job.cursor = tag.getInt("cursor");
        job.placed = tag.getInt("placed");
        job.skipped = tag.getInt("skipped");
        job.skippedSpecial = tag.getInt("skippedSpecial");
        if (tag.hasUUID("schematic")) {
            job.schematicId = tag.getUUID("schematic");
        }
        if (tag.hasUUID("result")) {
            job.resultSchematicId = tag.getUUID("result");
        }
        if (tag.hasUUID("drone")) {
            job.droneId = tag.getUUID("drone");
        }
        if (tag.contains("dronePos")) {
            job.lastDronePos = NbtUtils.readBlockPos(tag, "dronePos").orElse(null);
        }
        ListTag list = tag.getList("partial", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            job.partial.add(Schematic.Entry.load(list.getCompound(i), blocks));
        }
        return job;
    }
}
