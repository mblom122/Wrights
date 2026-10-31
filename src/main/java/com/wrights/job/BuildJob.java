package com.wrights.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.drone.Swarm;
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
    /** Local cells whose ghost is done. Placed cells also live in {@link #refunds}. */
    private final Set<Long> settledGhosts = new HashSet<>();

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
    private boolean mining;
    private boolean mineAny;
    private boolean mineVoid;
    private ItemStack mineTool = ItemStack.EMPTY;
    private boolean copyFluids;
    private boolean copyNbtBlocks;
    private boolean copyNbtSettings;
    private boolean copyNbtAll;
    private byte[] copyTurns = new byte[0];
    private boolean placeFluids = true;
    private boolean voidBlocks;
    private boolean voidFluids;
    private boolean voidNbt;
    /** Place chests and machines. Off skips those blocks for this build. */
    private boolean placeNbtBlocks = true;
    /** Apply saved machine settings. Off leaves a stored block freshly placed. */
    private boolean placeSettings = true;
    /** Apply a full NBT copy. Creative only, and only when the blueprint still has one. */
    private boolean placeAll = true;
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

    /** Scan point for a single wright that has to step past a falling block and come back. Not saved. */
    private int pasteProbe;

    public int pasteProbe() {
        return pasteProbe;
    }

    public void setPasteProbe(int pasteProbe) {
        this.pasteProbe = Math.max(0, pasteProbe);
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

    /** Schematic indexes already counted as skipped. Not saved; a reload may count one of them once more. */
    private final Set<Integer> retired = new HashSet<>();

    /** True the first time this block is given up on. Later passes must not count it again. */
    public boolean retire(int index) {
        return retired.add(index);
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
            hand.speed = com.wrights.drone.WrightTier.clamp(speeds.get(i));
            hands.add(hand);
        }
    }

    public boolean awaitingDrones() {
        return awaitDrones;
    }

    public int crewQuota() {
        return crewQuota;
    }

    public void setCrewQuota(int crewQuota) {
        this.crewQuota = Math.max(0, crewQuota);
    }

    public boolean queued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }

    /** Missing blocks wait for items instead of being skipped. The build stays queued while this is on. */
    public boolean deferSupplies() {
        return deferSupplies;
    }

    public void setDeferSupplies(boolean deferSupplies) {
        this.deferSupplies = deferSupplies;
    }

    /** Keeps the largest crew this job was given, so a later resume can refill it. */
    public boolean rememberCrewQuota(int count) {
        if (count > crewQuota) {
            crewQuota = count;
            return true;
        }
        return false;
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
            hand.speed = com.wrights.drone.WrightTier.clamp(speeds.get(i));
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
            hand.probe = 0;
            hand.entityId = null;
            hand.lastPos = null;
            hand.rescan = false;
            hand.idle = false;
            for (Slice slice : hand.slices) {
                slice.cursor = 0;
                slice.probe = 0;
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

    public boolean mining() {
        return mining;
    }

    public void setMining(boolean mining) {
        this.mining = mining;
    }

    public boolean mineAny() {
        return mineAny;
    }

    public void setMineAny(boolean mineAny) {
        this.mineAny = mineAny;
    }

    public boolean mineVoid() {
        return mineVoid;
    }

    public void setMineVoid(boolean mineVoid) {
        this.mineVoid = mineVoid;
    }

    public ItemStack mineTool() {
        return mineTool;
    }

    public void setMineTool(ItemStack mineTool) {
        this.mineTool = mineTool == null || mineTool.isEmpty() ? ItemStack.EMPTY : mineTool.copy();
    }

    public boolean copyFluids() {
        return copyFluids;
    }

    public void setCopyFluids(boolean copyFluids) {
        this.copyFluids = copyFluids;
    }

    public boolean copyNbtBlocks() {
        return copyNbtBlocks;
    }

    public boolean copyNbtSettings() {
        return copyNbtSettings;
    }

    public boolean copyNbtAll() {
        return copyNbtAll;
    }

    public void setCopyNbt(boolean copyNbtBlocks, boolean copyNbtSettings, boolean copyNbtAll) {
        this.copyNbtBlocks = copyNbtBlocks;
        this.copyNbtSettings = copyNbtBlocks && copyNbtSettings && !copyNbtAll;
        this.copyNbtAll = copyNbtBlocks && copyNbtAll;
    }

    public byte[] copyTurns() {
        return copyTurns;
    }

    public void setCopyTurns(byte[] copyTurns) {
        if (copyTurns == null || copyTurns.length == 0) {
            this.copyTurns = new byte[0];
            return;
        }
        int count = Math.min(copyTurns.length, 24);
        this.copyTurns = new byte[count];
        System.arraycopy(copyTurns, 0, this.copyTurns, 0, count);
    }

    public boolean placeFluids() {
        return placeFluids;
    }

    public boolean voidBlocks() {
        return voidBlocks;
    }

    public boolean voidFluids() {
        return voidFluids;
    }

    public boolean voidNbt() {
        return voidNbt;
    }

    public boolean placeNbtBlocks() {
        return placeNbtBlocks;
    }

    public boolean placeSettings() {
        return placeSettings;
    }

    public boolean placeAll() {
        return placeAll;
    }

    public void setPasteOptions(boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) {
        setPasteOptions(placeFluids, voidBlocks, voidFluids, voidNbt, true, true, true);
    }

    public void setPasteOptions(boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean placeNbt, boolean placeSettings, boolean placeAll) {
        this.placeFluids = placeFluids;
        this.voidBlocks = voidBlocks;
        this.voidFluids = voidFluids;
        this.voidNbt = voidNbt;
        this.placeNbtBlocks = placeNbt;
        this.placeSettings = placeSettings;
        this.placeAll = placeAll;
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

    public static final int HOLD_NONE = 0;
    public static final int HOLD_MANUAL = 1;
    public static final int HOLD_NO_WRIGHTS = 2;
    public static final int HOLD_PLAYER_RANGE = 3;
    public static final int HOLD_PORT_RANGE = 4;
    public static final int HOLD_DIMENSION = 5;
    public static final int HOLD_CHUNK = 6;

    public static final int WAIT_NONE = 0;
    public static final int WAIT_FLOOR = 1;
    public static final int WAIT_CHUNK = 2;
    public static final int WAIT_ITEMS = 3;

    private boolean paused;
    private int hold = HOLD_NONE;
    private int waitKind = WAIT_NONE;
    private BlockPos holdPort;
    private boolean awaitDrones;
    private int crewQuota;
    /** Waiting for wrights. It only accepts homes that can reach the build. */
    private boolean queued;
    /** Survival queue: place blocks as items arrive, and keep the queued ghost until the job ends. */
    private boolean deferSupplies;
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

    public int hold() {
        return hold;
    }

    public int waitKind() {
        return waitKind;
    }

    public void setWaitKind(int waitKind) {
        this.waitKind = waitKind;
    }

    public BlockPos holdPort() {
        return holdPort;
    }

    public void noteHold(int reason, BlockPos port) {
        this.hold = reason;
        this.holdPort = port == null ? null : port.immutable();
    }

    public void clearHold() {
        hold = HOLD_NONE;
        holdPort = null;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void workedTick() {
        if (workedTicks < Integer.MAX_VALUE) {
            workedTicks++;
        }
    }

    public int workedTicks() {
        return workedTicks;
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

    private String supplyDimension;

    public void setSupply(BlockPos supplyPos, ResourceKey<Level> dimension) {
        this.supplyPos = supplyPos == null ? null : supplyPos.immutable();
        this.supplyDimension = supplyPos == null || dimension == null ? null : dimension.location().toString();
    }

    public String supplyDimension() {
        return supplyDimension;
    }

    private boolean supplyPlayerFirst = true;

    public boolean supplyPlayerFirst() {
        return supplyPlayerFirst;
    }

    public void setSupplyPlayerFirst(boolean supplyPlayerFirst) {
        this.supplyPlayerFirst = supplyPlayerFirst;
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
        settleGhost(x, y, z);
    }

    /** The real block has this cell, so its ghost should not come back if the block is broken. */
    public void settleGhost(short x, short y, short z) {
        settledGhosts.add(BlockPos.asLong(Short.toUnsignedInt(x), Short.toUnsignedInt(y), Short.toUnsignedInt(z)));
    }

    public void collectSettledGhosts(Set<Long> into) {
        into.addAll(settledGhosts);
        for (Refund refund : refunds) {
            into.add(BlockPos.asLong(Short.toUnsignedInt(refund.x()), Short.toUnsignedInt(refund.y()), Short.toUnsignedInt(refund.z())));
        }
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
        queued = false;
        deferSupplies = false;
        cursor = 0;
        pasteProbe = 0;
        placed = 0;
        settledGhosts.clear();
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

    public CompoundTag save(HolderLookup.Provider registries) {
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
                handTag.putInt("placeCredit", hand.placeCredit);
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
                if (hand.homePort != null) {
                    handTag.put("home", NbtUtils.writeBlockPos(hand.homePort));
                }
                if (hand.stranded) {
                    handTag.putBoolean("stranded", true);
                }
                if (hand.sheltering) {
                    handTag.putBoolean("sheltering", true);
                }
                if (hand.wrightName != null && !hand.wrightName.isBlank()) {
                    handTag.putString("wrightName", hand.wrightName);
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
        if (mining) {
            tag.putBoolean("mining", true);
            tag.putBoolean("mineAny", mineAny);
            tag.putBoolean("mineVoid", mineVoid);
            if (mineTool != null && !mineTool.isEmpty() && registries != null) {
                tag.put("mineTool", mineTool.save(registries));
            }
        }
        tag.putBoolean("copyFluids", copyFluids);
        tag.putBoolean("copyNbtBlocks", copyNbtBlocks);
        tag.putBoolean("copyNbtSettings", copyNbtSettings);
        tag.putBoolean("copyNbtAll", copyNbtAll);
        if (copyTurns.length > 0) {
            tag.putByteArray("copyTurns", copyTurns);
        }
        tag.putBoolean("placeFluids", placeFluids);
        tag.putBoolean("voidBlocks", voidBlocks);
        tag.putBoolean("voidFluids", voidFluids);
        tag.putBoolean("voidNbt", voidNbt);
        tag.putBoolean("placeNbtBlocks", placeNbtBlocks);
        tag.putBoolean("placeSettings", placeSettings);
        tag.putBoolean("placeAll", placeAll);
        if (replaceSchematicId != null) {
            tag.putUUID("replace", replaceSchematicId);
        }
        if (supplyPos != null) {
            tag.put("supply", NbtUtils.writeBlockPos(supplyPos));
            if (supplyDimension != null) {
                tag.putString("supplyDim", supplyDimension);
            }
        }
        tag.putBoolean("supplyPlayerFirst", supplyPlayerFirst);
        if (anchor != null) {
            tag.put("anchor", NbtUtils.writeBlockPos(anchor));
        }
        tag.putInt("workedTicks", workedTicks);
        tag.putBoolean("paused", paused);
        tag.putInt("hold", hold);
        if (holdPort != null) {
            tag.put("holdPort", NbtUtils.writeBlockPos(holdPort));
        }
        tag.putBoolean("awaitDrones", awaitDrones);
        tag.putInt("crewQuota", crewQuota);
        tag.putBoolean("queued", queued);
        tag.putBoolean("deferSupplies", deferSupplies);
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
        if (!settledGhosts.isEmpty()) {
            long[] packed = new long[settledGhosts.size()];
            int i = 0;
            for (long key : settledGhosts) {
                packed[i++] = key;
            }
            tag.putLongArray("settledGhosts", packed);
        }
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

    public static BuildJob load(CompoundTag tag, HolderGetter<Block> blocks, HolderLookup.Provider registries) {
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
        if (tag.contains("settledGhosts")) {
            for (long key : tag.getLongArray("settledGhosts")) {
                job.settledGhosts.add(key);
            }
        }
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
                hand.placeCredit = handTag.getInt("placeCredit");
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
                if (handTag.contains("home")) {
                    hand.homePort = NbtUtils.readBlockPos(handTag, "home").orElse(null);
                }
                hand.stranded = handTag.getBoolean("stranded");
                hand.sheltering = handTag.getBoolean("sheltering");
                hand.wrightName = handTag.getString("wrightName");
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
        job.mining = tag.getBoolean("mining");
        job.mineAny = tag.getBoolean("mineAny");
        job.mineVoid = tag.getBoolean("mineVoid");
        if (registries != null && tag.contains("mineTool")) {
            job.mineTool = ItemStack.parseOptional(registries, tag.getCompound("mineTool"));
        }
        job.copyFluids = tag.getBoolean("copyFluids");
        job.copyNbtBlocks = tag.getBoolean("copyNbtBlocks");
        job.copyNbtAll = job.copyNbtBlocks && tag.getBoolean("copyNbtAll");
        if (tag.contains("copyTurns")) {
            job.setCopyTurns(tag.getByteArray("copyTurns"));
        }
        job.copyNbtSettings = job.copyNbtBlocks && !job.copyNbtAll && tag.getBoolean("copyNbtSettings");
        job.placeFluids = !tag.contains("placeFluids") || tag.getBoolean("placeFluids");
        job.voidBlocks = tag.getBoolean("voidBlocks");
        job.voidFluids = tag.getBoolean("voidFluids");
        job.voidNbt = tag.getBoolean("voidNbt");
        job.placeNbtBlocks = !tag.contains("placeNbtBlocks") || tag.getBoolean("placeNbtBlocks");
        job.placeSettings = !tag.contains("placeSettings") || tag.getBoolean("placeSettings");
        job.placeAll = !tag.contains("placeAll") || tag.getBoolean("placeAll");
        if (tag.hasUUID("replace")) {
            job.replaceSchematicId = tag.getUUID("replace");
        }
        if (tag.contains("supply")) {
            job.supplyPos = NbtUtils.readBlockPos(tag, "supply").orElse(null);
            if (tag.contains("supplyDim")) {
                job.supplyDimension = tag.getString("supplyDim");
            }
        }
        job.supplyPlayerFirst = !tag.contains("supplyPlayerFirst") || tag.getBoolean("supplyPlayerFirst");
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
        job.hold = tag.getInt("hold");
        if (tag.contains("holdPort")) {
            job.holdPort = NbtUtils.readBlockPos(tag, "holdPort").orElse(null);
        }
        job.awaitDrones = tag.getBoolean("awaitDrones");
        job.crewQuota = Math.max(0, tag.getInt("crewQuota"));
        job.queued = tag.getBoolean("queued");
        job.deferSupplies = tag.getBoolean("deferSupplies");
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
        /** Next schematic index to inspect. Not saved; a reload starts again at {@link #cursor}. */
        public int probe;

        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    public static final class Hand {
        public int lane;
        public int lanes;
        public int speed = 1;
        /** Leftover eighths of a block, so a fractional place rate stays exact across ticks. */
        public int placeCredit;
        public int cursor;
        /** Next schematic index to inspect when this hand has no slices. Not saved. */
        public int probe;
        public boolean rescan;
        public boolean idle;
        /** This tick the only work left is a block another wright has to place first. Not saved. */
        public boolean waitingOnOthers;
        /** Home because every block it can reach needs an item its supply does not have. Not saved. */
        public boolean awaitingItems;
        public UUID entityId;
        public BlockPos lastPos;
        /** Null = personal wrightport (player back). Set = placed wrightport block. */
        public BlockPos homePort;
        /** The dock this wright left is gone. Recover it on the way home. */
        public boolean stranded;
        /** Flying to another wrightport because the old dock is gone. Not dropped from the crew yet. */
        public boolean sheltering;
        /** Name to keep when this wright has to leave a dock that is gone. */
        public String wrightName = "";
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
