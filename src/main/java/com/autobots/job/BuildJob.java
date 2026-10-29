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
import net.minecraft.world.level.block.state.BlockState;

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
    private final List<Refund> refunds = new ArrayList<>();

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
    private String blueprintName;
    private UUID replaceSchematicId;
    private int clipX = Integer.MAX_VALUE;
    private int clipY = Integer.MAX_VALUE;
    private int clipZ = Integer.MAX_VALUE;

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

    public String blueprintName() {
        return blueprintName;
    }

    public void setBlueprintName(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public UUID replaceSchematicId() {
        return replaceSchematicId;
    }

    public void setReplaceSchematicId(UUID replaceSchematicId) {
        this.replaceSchematicId = replaceSchematicId;
    }

    public void setClip(int clipX, int clipY, int clipZ) {
        this.clipX = Math.max(1, clipX);
        this.clipY = Math.max(1, clipY);
        this.clipZ = Math.max(1, clipZ);
    }

    public int clipX() {
        return clipX;
    }

    public int clipY() {
        return clipY;
    }

    public int clipZ() {
        return clipZ;
    }

    public boolean outsideClip(Schematic.Entry entry) {
        return entry.x() >= clipX || entry.y() >= clipY || entry.z() >= clipZ;
    }

    private boolean paused;
    private boolean finished;
    private boolean undoing;
    private int historyOrder;
    private int undoTotal;
    private int returned;

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean finished() {
        return finished;
    }

    public boolean undoing() {
        return undoing;
    }

    public int historyOrder() {
        return historyOrder;
    }

    public int undoTotal() {
        return undoTotal;
    }

    public int returned() {
        return returned;
    }

    private BlockPos supplyPos;

    public BlockPos supplyPos() {
        return supplyPos;
    }

    public void setSupply(BlockPos supplyPos) {
        this.supplyPos = supplyPos;
    }

    public List<Refund> refunds() {
        return refunds;
    }

    public int refundCount() {
        return refunds.size();
    }

    public boolean canUndo() {
        return finished && kind == Kind.PASTE && !refunds.isEmpty();
    }

    public void recordPlaced(short x, short y, short z, BlockState state, boolean paid) {
        refunds.add(new Refund(x, y, z, state, paid));
    }

    public Refund pollRefund() {
        if (refunds.isEmpty()) {
            return null;
        }
        return refunds.remove(refunds.size() - 1);
    }

    public void restoreRefund(Refund refund) {
        refunds.add(refund);
    }

    public boolean hasRefunds() {
        return !refunds.isEmpty();
    }

    public BlockPos lastRefundPos() {
        Refund refund = refunds.get(refunds.size() - 1);
        return min.offset(refund.x(), refund.y(), refund.z());
    }

    public void returnedOne() {
        returned++;
    }

    public boolean beginUndo() {
        if (refunds.isEmpty() || kind != Kind.PASTE) {
            return false;
        }
        undoing = true;
        finished = false;
        paused = false;
        undoTotal = refunds.size();
        returned = 0;
        skipped = 0;
        cursor = 0;
        return true;
    }

    public void markFinished(int order) {
        finished = true;
        undoing = false;
        historyOrder = order;
        paused = false;
        droneId = null;
    }

    public void restart() {
        finished = false;
        undoing = false;
        paused = false;
        cursor = 0;
        placed = 0;
        skipped = 0;
        skippedSpecial = 0;
        droneId = null;
        lastDronePos = null;
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
        if (blueprintName != null) {
            tag.putString("blueprintName", blueprintName);
        }
        if (replaceSchematicId != null) {
            tag.putUUID("replace", replaceSchematicId);
        }
        if (supplyPos != null) {
            tag.put("supply", NbtUtils.writeBlockPos(supplyPos));
        }
        tag.putBoolean("paused", paused);
        tag.putBoolean("finished", finished);
        tag.putBoolean("undoing", undoing);
        tag.putInt("historyOrder", historyOrder);
        tag.putInt("undoTotal", undoTotal);
        tag.putInt("returned", returned);
        ListTag refundList = new ListTag();
        for (Refund refund : refunds) {
            refundList.add(refund.save());
        }
        tag.put("refunds", refundList);
        if (clipX != Integer.MAX_VALUE) {
            tag.putInt("clipX", clipX);
            tag.putInt("clipY", clipY);
            tag.putInt("clipZ", clipZ);
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
        if (tag.contains("blueprintName")) {
            job.blueprintName = tag.getString("blueprintName");
        }
        if (tag.hasUUID("replace")) {
            job.replaceSchematicId = tag.getUUID("replace");
        }
        if (tag.contains("supply")) {
            job.supplyPos = NbtUtils.readBlockPos(tag, "supply").orElse(null);
        }
        if (tag.contains("clipX")) {
            job.clipX = tag.getInt("clipX");
            job.clipY = tag.getInt("clipY");
            job.clipZ = tag.getInt("clipZ");
        }
        job.paused = tag.getBoolean("paused");
        job.finished = tag.getBoolean("finished");
        job.undoing = tag.getBoolean("undoing");
        job.historyOrder = tag.getInt("historyOrder");
        job.undoTotal = tag.getInt("undoTotal");
        job.returned = tag.getInt("returned");
        ListTag refundList = tag.getList("refunds", Tag.TAG_COMPOUND);
        for (int i = 0; i < refundList.size(); i++) {
            job.refunds.add(Refund.load(refundList.getCompound(i), blocks));
        }
        ListTag list = tag.getList("partial", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            job.partial.add(Schematic.Entry.load(list.getCompound(i), blocks));
        }
        return job;
    }

    public record Refund(short x, short y, short z, BlockState state, boolean paid) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putShort("x", x);
            tag.putShort("y", y);
            tag.putShort("z", z);
            tag.putBoolean("paid", paid);
            tag.put("state", NbtUtils.writeBlockState(state));
            return tag;
        }

        public static Refund load(CompoundTag tag, HolderGetter<Block> blocks) {
            BlockState state = NbtUtils.readBlockState(blocks, tag.getCompound("state"));
            return new Refund(tag.getShort("x"), tag.getShort("y"), tag.getShort("z"), state, tag.getBoolean("paid"));
        }
    }
}
