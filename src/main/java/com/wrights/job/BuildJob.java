package com.wrights.job;

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

import com.wrights.schematic.Schematic;

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
    private final List<Hand> hands = new ArrayList<>();
    private final List<UUID> crewUnits = new ArrayList<>();
    private int spanX = 1;
    private int spanY = 1;
    private int spanZ = 1;
    private BlockPos lastDronePos;
    private Phase phase = Phase.RUNNING;
    private int messageDelay;
    private String blueprintName;
    private UUID replaceSchematicId;
    private int clipX = Integer.MAX_VALUE;
    private int clipY = Integer.MAX_VALUE;
    private int clipZ = Integer.MAX_VALUE;
    private BlockPos anchor;
    private int workedTicks;

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

    public boolean hasCrew() {
        return !hands.isEmpty();
    }

    public List<Hand> hands() {
        return hands;
    }

    public int spanX() {
        return spanX;
    }

    public int spanY() {
        return spanY;
    }

    public int spanZ() {
        return spanZ;
    }

    public List<UUID> crewUnits() {
        return List.copyOf(crewUnits);
    }

    public void setCrewUnits(List<UUID> ids) {
        crewUnits.clear();
        if (ids != null) {
            crewUnits.addAll(ids);
        }
    }

    public void setCrew(List<Integer> speeds, int sizeX, int sizeY, int sizeZ) {
        hands.clear();
        crewUnits.clear();
        awaitDrones = false;
        spanX = Math.max(1, sizeX);
        spanY = Math.max(1, sizeY);
        spanZ = Math.max(1, sizeZ);
        int lanes = speeds.size();
        for (int i = 0; i < lanes; i++) {
            Hand hand = new Hand();
            hand.lane = i;
            hand.lanes = lanes;
            hand.speed = Math.max(1, Math.min(4, speeds.get(i)));
            hands.add(hand);
        }
    }

    public boolean awaitingDrones() {
        return awaitDrones;
    }

    public void clearAssigned() {
        hands.clear();
        crewUnits.clear();
        awaitDrones = true;
    }

    public List<UUID> resizeCrew(List<Integer> speeds, int sizeX, int sizeY, int sizeZ, boolean rescan) {
        if (hands.isEmpty()) {
            spanX = Math.max(1, sizeX);
            spanY = Math.max(1, sizeY);
            spanZ = Math.max(1, sizeZ);
        }
        int lanes = speeds.size();
        boolean scan = hands.size() != lanes || (hands.isEmpty() && rescan);
        List<UUID> dropped = new ArrayList<>();
        List<Hand> next = new ArrayList<>();
        for (int i = 0; i < lanes; i++) {
            Hand hand = i < hands.size() ? hands.get(i) : new Hand();
            hand.lane = i;
            hand.lanes = lanes;
            hand.speed = Math.max(1, Math.min(4, speeds.get(i)));
            if (scan) {
                hand.cursor = 0;
                hand.rescan = true;
            }
            next.add(hand);
        }
        for (int i = lanes; i < hands.size(); i++) {
            if (hands.get(i).entityId != null) {
                dropped.add(hands.get(i).entityId);
            }
        }
        hands.clear();
        hands.addAll(next);
        awaitDrones = false;
        return dropped;
    }

    public void resetCrew() {
        for (Hand hand : hands) {
            hand.cursor = 0;
            hand.entityId = null;
            hand.lastPos = null;
            hand.rescan = false;
            hand.idle = false;
            for (Slice slice : hand.slices) {
                slice.cursor = 0;
            }
        }
    }

    public boolean ownsDrone(UUID id) {
        if (id == null) {
            return false;
        }
        if (id.equals(droneId)) {
            return true;
        }
        for (Hand hand : hands) {
            if (id.equals(hand.entityId)) {
                return true;
            }
        }
        return false;
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

    public void setAnchor(BlockPos anchor) {
        this.anchor = anchor == null ? null : anchor.immutable();
    }

    public BlockPos anchor() {
        return anchor != null ? anchor : min;
    }

    public int boxSizeX() {
        if (kind == Kind.COPY) {
            return Math.max(1, max.getX() - min.getX() + 1);
        }
        return clipX == Integer.MAX_VALUE ? 1 : clipX;
    }

    public int boxSizeY() {
        if (kind == Kind.COPY) {
            return Math.max(1, max.getY() - min.getY() + 1);
        }
        return clipY == Integer.MAX_VALUE ? 1 : clipY;
    }

    public int boxSizeZ() {
        if (kind == Kind.COPY) {
            return Math.max(1, max.getZ() - min.getZ() + 1);
        }
        return clipZ == Integer.MAX_VALUE ? 1 : clipZ;
    }

    private boolean paused;
    private boolean awaitDrones;
    private boolean finished;
    private boolean stopped;
    private boolean undone;
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

    public void workedTick() {
        if (workedTicks < Integer.MAX_VALUE) {
            workedTicks++;
        }
    }

    public int elapsedSeconds() {
        return workedTicks / 20;
    }

    public boolean finished() {
        return finished;
    }

    public boolean stopped() {
        return stopped;
    }

    public boolean undone() {
        return undone;
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

    public Refund takeRefund(int index) {
        return refunds.remove(index);
    }

    public void restoreRefund(Refund refund) {
        refunds.add(refund);
    }

    public void restoreRefund(int index, Refund refund) {
        refunds.add(Math.min(Math.max(0, index), refunds.size()), refund);
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
        stopped = false;
        undone = false;
        paused = false;
        undoTotal = refunds.size();
        returned = 0;
        skipped = 0;
        cursor = 0;
        return true;
    }

    public void markFinished(int order, boolean stopped) {
        finished = true;
        this.stopped = stopped;
        if (stopped) {
            undone = false;
        }
        undoing = false;
        historyOrder = order;
        paused = false;
        droneId = null;
    }

    public void markUndone() {
        undone = true;
        stopped = false;
    }

    public void restart() {
        finished = false;
        stopped = false;
        undone = false;
        undoing = false;
        paused = false;
        awaitDrones = false;
        cursor = 0;
        placed = 0;
        skipped = 0;
        skippedSpecial = 0;
        droneId = null;
        lastDronePos = null;
        workedTicks = 0;
        resetCrew();
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
        if (!hands.isEmpty()) {
            ListTag crew = new ListTag();
            for (Hand hand : hands) {
                CompoundTag handTag = new CompoundTag();
                handTag.putInt("lane", hand.lane);
                handTag.putInt("lanes", hand.lanes);
                handTag.putInt("speed", hand.speed);
                handTag.putInt("cursor", hand.cursor);
                handTag.putBoolean("rescan", hand.rescan);
                handTag.putBoolean("idle", hand.idle);
                if (!hand.slices.isEmpty()) {
                    ListTag slices = new ListTag();
                    for (Slice slice : hand.slices) {
                        CompoundTag sliceTag = new CompoundTag();
                        sliceTag.putInt("x0", slice.x0);
                        sliceTag.putInt("y0", slice.y0);
                        sliceTag.putInt("z0", slice.z0);
                        sliceTag.putInt("x1", slice.x1);
                        sliceTag.putInt("y1", slice.y1);
                        sliceTag.putInt("z1", slice.z1);
                        sliceTag.putInt("cursor", slice.cursor);
                        slices.add(sliceTag);
                    }
                    handTag.put("slices", slices);
                }
                if (hand.entityId != null) {
                    handTag.putUUID("entity", hand.entityId);
                }
                if (hand.lastPos != null) {
                    handTag.put("pos", NbtUtils.writeBlockPos(hand.lastPos));
                }
                crew.add(handTag);
            }
            tag.put("hands", crew);
            tag.putInt("spanX", spanX);
            tag.putInt("spanY", spanY);
            tag.putInt("spanZ", spanZ);
        }
        if (!crewUnits.isEmpty()) {
            ListTag units = new ListTag();
            for (UUID unit : crewUnits) {
                CompoundTag unitTag = new CompoundTag();
                unitTag.putUUID("id", unit);
                units.add(unitTag);
            }
            tag.put("crewUnits", units);
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
        if (anchor != null) {
            tag.put("anchor", NbtUtils.writeBlockPos(anchor));
        }
        tag.putInt("workedTicks", workedTicks);
        tag.putBoolean("paused", paused);
        tag.putBoolean("awaitDrones", awaitDrones);
        tag.putBoolean("finished", finished);
        tag.putBoolean("stopped", stopped);
        tag.putBoolean("undone", undone);
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
        if (tag.contains("hands")) {
            job.spanX = Math.max(1, tag.getInt("spanX"));
            job.spanY = Math.max(1, tag.getInt("spanY"));
            job.spanZ = Math.max(1, tag.getInt("spanZ"));
            ListTag crew = tag.getList("hands", Tag.TAG_COMPOUND);
            for (int i = 0; i < crew.size(); i++) {
                CompoundTag handTag = crew.getCompound(i);
                Hand hand = new Hand();
                hand.lane = handTag.getInt("lane");
                hand.lanes = handTag.getInt("lanes");
                hand.speed = handTag.getInt("speed");
                hand.cursor = handTag.getInt("cursor");
                hand.rescan = handTag.getBoolean("rescan");
                hand.idle = handTag.getBoolean("idle");
                ListTag slices = handTag.getList("slices", Tag.TAG_COMPOUND);
                for (int sliceIndex = 0; sliceIndex < slices.size(); sliceIndex++) {
                    CompoundTag sliceTag = slices.getCompound(sliceIndex);
                    Slice slice = new Slice();
                    slice.x0 = sliceTag.getInt("x0");
                    slice.y0 = sliceTag.getInt("y0");
                    slice.z0 = sliceTag.getInt("z0");
                    slice.x1 = sliceTag.getInt("x1");
                    slice.y1 = sliceTag.getInt("y1");
                    slice.z1 = sliceTag.getInt("z1");
                    slice.cursor = sliceTag.getInt("cursor");
                    hand.slices.add(slice);
                }
                if (handTag.hasUUID("entity")) {
                    hand.entityId = handTag.getUUID("entity");
                }
                if (handTag.contains("pos")) {
                    hand.lastPos = NbtUtils.readBlockPos(handTag, "pos").orElse(null);
                }
                job.hands.add(hand);
            }
        }
        if (tag.contains("crewUnits")) {
            ListTag units = tag.getList("crewUnits", Tag.TAG_COMPOUND);
            for (int i = 0; i < units.size(); i++) {
                CompoundTag unitTag = units.getCompound(i);
                if (unitTag.hasUUID("id")) {
                    job.crewUnits.add(unitTag.getUUID("id"));
                }
            }
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
        if (tag.contains("anchor")) {
            job.anchor = NbtUtils.readBlockPos(tag, "anchor").orElse(null);
        }
        if (tag.contains("clipX")) {
            job.clipX = tag.getInt("clipX");
            job.clipY = tag.getInt("clipY");
            job.clipZ = tag.getInt("clipZ");
        }
        job.workedTicks = tag.getInt("workedTicks");
        job.paused = tag.getBoolean("paused");
        job.awaitDrones = tag.getBoolean("awaitDrones");
        job.finished = tag.getBoolean("finished");
        job.stopped = tag.getBoolean("stopped");
        job.undone = tag.getBoolean("undone");
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

    public static final class Slice {
        public int x0;
        public int y0;
        public int z0;
        public int x1;
        public int y1;
        public int z1;
        public int cursor;

        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    public static final class Hand {
        public int lane;
        public int lanes;
        public int speed = 1;
        public int cursor;
        public boolean rescan;
        public boolean idle;
        public UUID entityId;
        public BlockPos lastPos;
        public final List<Slice> slices = new ArrayList<>();
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
