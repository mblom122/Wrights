package com.wrights.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class Schematic {
    private final UUID id;
    private final String name;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Entry> entries;
    private final BlockPos sourceMin;
    private final BlockPos sourceMax;
    private final String sourceDimension;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    /** Bottom layer first, then upper halves of doors and tall plants at the same height. */
    public static final Comparator<Entry> BUILD_ORDER = Comparator.comparingInt((Entry entry) -> entry.y()).thenComparing(Entry::secondary);

    private final boolean ephemeral;
    private final boolean placeFluids;
    private final boolean voidBlocks;
    private final boolean voidFluids;
    private final boolean voidNbt;
    private transient Map<Long, Integer> positions;

    public Schematic(UUID id, int sizeX, int sizeY, int sizeZ, List<Entry> entries) {
        this(id, "Blueprint", sizeX, sizeY, sizeZ, entries, BlockPos.ZERO, BlockPos.ZERO, "");
    }

    public Schematic(UUID id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> entries, BlockPos sourceMin, BlockPos sourceMax, String sourceDimension) {
        this(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, 0, 0, 0);
    }

    public Schematic(UUID id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> entries, BlockPos sourceMin, BlockPos sourceMax, String sourceDimension, int anchorX, int anchorY, int anchorZ) {
        this(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, false);
    }

    public Schematic(UUID id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> entries, BlockPos sourceMin, BlockPos sourceMax, String sourceDimension, int anchorX, int anchorY, int anchorZ, boolean ephemeral) {
        this(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, ephemeral, true, false, false, false);
    }

    public Schematic(UUID id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> entries, BlockPos sourceMin, BlockPos sourceMax, String sourceDimension, int anchorX, int anchorY, int anchorZ, boolean ephemeral, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) {
        this.id = id;
        this.name = name == null || name.isBlank() ? "Blueprint" : name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.entries = List.copyOf(entries);
        this.sourceMin = sourceMin;
        this.sourceMax = sourceMax;
        this.sourceDimension = sourceDimension == null ? "" : sourceDimension;
        this.anchorX = clampAnchor(anchorX, sizeX);
        this.anchorY = clampAnchor(anchorY, sizeY);
        this.anchorZ = clampAnchor(anchorZ, sizeZ);
        this.ephemeral = ephemeral;
        this.placeFluids = placeFluids;
        this.voidBlocks = voidBlocks;
        this.voidFluids = voidFluids;
        this.voidNbt = voidNbt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean hasSource() {
        return !sourceDimension.isEmpty();
    }

    public BlockPos sourceMin() {
        return sourceMin;
    }

    public BlockPos sourceMax() {
        return sourceMax;
    }

    public String sourceDimension() {
        return sourceDimension;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public boolean ephemeral() {
        return ephemeral;
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

    public boolean samePaste(Schematic other) {
        return other != null
                && placeFluids == other.placeFluids
                && voidBlocks == other.voidBlocks
                && voidFluids == other.voidFluids
                && voidNbt == other.voidNbt;
    }

    public boolean hasSettings() {
        for (Entry entry : entries) {
            if (entry.nbt() != null && !entry.nbtFull()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAll() {
        for (Entry entry : entries) {
            if (entry.nbtFull()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops fluids, stored blocks, settings, or a full copy when that choice is off.
     * A full copy stays intact. Settings-only keeps machine config and clears a full copy down to that.
     */
    public Schematic withStored(boolean fluids, boolean nbtBlocks, boolean settings, boolean all) {
        java.util.List<Entry> next = new java.util.ArrayList<>(entries.size());
        for (Entry entry : entries) {
            boolean fluid = com.wrights.util.BlockFilters.isFluid(entry.state());
            boolean stored = entry.state().hasBlockEntity();
            if (fluid && !fluids) {
                continue;
            }
            if (stored && !nbtBlocks) {
                continue;
            }
            net.minecraft.nbt.CompoundTag nbt = entry.nbt();
            boolean full = entry.nbtFull();
            if (stored && nbt != null) {
                if (all && full) {
                    // Keep the exact copy.
                } else if (settings) {
                    nbt = full ? com.wrights.util.BlockEntityNbt.settings(nbt) : nbt;
                    full = false;
                } else {
                    nbt = null;
                    full = false;
                }
            }
            next.add(new Entry(entry.x(), entry.y(), entry.z(), entry.state(), entry.secondary(), nbt, full && all));
        }
        return withEntries(next);
    }

    public Schematic withPaste(boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, ephemeral, placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    public Schematic ephemeralCopy() {
        return new Schematic(UUID.randomUUID(), name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, true).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    /** Keeps this blueprint's id and name, and takes the blocks stored on {@code source}. */
    public Schematic withContents(Schematic source) {
        return new Schematic(id, name, source.sizeX, source.sizeY, source.sizeZ, source.entries, source.sourceMin, source.sourceMax, source.sourceDimension, source.anchorX, source.anchorY, source.anchorZ, ephemeral).withPaste(source.placeFluids, source.voidBlocks, source.voidFluids, source.voidNbt);
    }

    /** True when both blueprints store the same blocks in the same cells. Name and id are ignored. */
    public boolean sameBlocks(Schematic other) {
        if (other == null || sizeX != other.sizeX || sizeY != other.sizeY || sizeZ != other.sizeZ || entries.size() != other.entries.size()) {
            return false;
        }
        for (Entry entry : entries) {
            int index = other.indexAt(entry.x(), entry.y(), entry.z());
            if (index < 0) {
                return false;
            }
            Entry theirs = other.entry(index);
            if (entry.secondary() != theirs.secondary()
                    || entry.nbtFull() != theirs.nbtFull()
                    || !entry.state().equals(theirs.state())
                    || !java.util.Objects.equals(entry.nbt(), theirs.nbt())) {
                return false;
            }
        }
        return true;
    }

    public Schematic withName(String name) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, ephemeral).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    /** A saved copy with its own id. Use counts and favorites stay with the original. */
    public Schematic duplicate(UUID id, String name) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, false).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    public Schematic withAnchor(int anchorX, int anchorY, int anchorZ) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, ephemeral).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    public Schematic withEntries(List<Entry> entries) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ, ephemeral).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    public Schematic withLayout(int sizeY, List<Entry> entries, int anchorY) {
        return withLayout(com.wrights.blueprint.LayerAxis.Y, sizeY, entries, anchorY);
    }

    public Schematic withLayout(int axis, int size, List<Entry> entries, int anchor) {
        int sx = sizeX;
        int sy = sizeY;
        int sz = sizeZ;
        int ax = anchorX;
        int ay = anchorY;
        int az = anchorZ;
        if (axis == com.wrights.blueprint.LayerAxis.X) {
            sx = size;
            ax = anchor;
        } else if (axis == com.wrights.blueprint.LayerAxis.Z) {
            sz = size;
            az = anchor;
        } else {
            sy = size;
            ay = anchor;
        }
        return new Schematic(id, name, sx, sy, sz, entries, sourceMin, sourceMax, sourceDimension, ax, ay, az, ephemeral).withPaste(placeFluids, voidBlocks, voidFluids, voidNbt);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int blockCount() {
        return entries.size();
    }

    public Entry entry(int index) {
        return entries.get(index);
    }

    /** Index of the saved block at this schematic cell, or -1 when the blueprint has air there. */
    public int indexAt(int x, int y, int z) {
        return positions().getOrDefault(BlockPos.asLong(x, y, z), -1);
    }

    private Map<Long, Integer> positions() {
        if (positions == null) {
            Map<Long, Integer> map = new HashMap<>(entries.size() * 2);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                map.put(BlockPos.asLong(entry.x(), entry.y(), entry.z()), i);
            }
            positions = map;
        }
        return positions;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putInt("sx", sizeX);
        tag.putInt("sy", sizeY);
        tag.putInt("sz", sizeZ);
        tag.putInt("anchorX", anchorX);
        tag.putInt("anchorY", anchorY);
        tag.putInt("anchorZ", anchorZ);
        if (ephemeral) {
            tag.putBoolean("ephemeral", true);
        }
        tag.putBoolean("placeFluids", placeFluids);
        tag.putBoolean("voidBlocks", voidBlocks);
        tag.putBoolean("voidFluids", voidFluids);
        tag.putBoolean("voidNbt", voidNbt);
        if (!sourceDimension.isEmpty()) {
            tag.putString("sourceDim", sourceDimension);
            tag.put("sourceMin", NbtUtils.writeBlockPos(sourceMin));
            tag.put("sourceMax", NbtUtils.writeBlockPos(sourceMax));
        }
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            list.add(entry.save());
        }
        tag.put("blocks", list);
        return tag;
    }

    public static Schematic load(CompoundTag tag, HolderGetter<Block> blocks) {
        UUID id = tag.getUUID("id");
        List<Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Entry entry = Entry.load(list.getCompound(i), blocks);
            if (!entry.state().isAir()) {
                entries.add(entry);
            }
        }
        String name = tag.contains("name") ? tag.getString("name") : "Blueprint";
        BlockPos sourceMin = BlockPos.ZERO;
        BlockPos sourceMax = BlockPos.ZERO;
        String sourceDim = "";
        if (tag.contains("sourceDim")) {
            sourceDim = tag.getString("sourceDim");
            sourceMin = NbtUtils.readBlockPos(tag, "sourceMin").orElse(BlockPos.ZERO);
            sourceMax = NbtUtils.readBlockPos(tag, "sourceMax").orElse(BlockPos.ZERO);
        }
        int anchorX = tag.contains("anchorX") ? tag.getInt("anchorX") : 0;
        int anchorY = tag.contains("anchorY") ? tag.getInt("anchorY") : 0;
        int anchorZ = tag.contains("anchorZ") ? tag.getInt("anchorZ") : 0;
        Schematic schematic = new Schematic(id, name, tag.getInt("sx"), tag.getInt("sy"), tag.getInt("sz"), entries, sourceMin, sourceMax, sourceDim, anchorX, anchorY, anchorZ, tag.getBoolean("ephemeral"));
        if (!tag.contains("placeFluids")) {
            return schematic;
        }
        return schematic.withPaste(tag.getBoolean("placeFluids"), tag.getBoolean("voidBlocks"), tag.getBoolean("voidFluids"), tag.getBoolean("voidNbt"));
    }

    private static int clampAnchor(int value, int size) {
        if (size <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(size - 1, value));
    }

    public static HolderGetter<Block> lookup(net.minecraft.core.HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.BLOCK);
    }

    public record Entry(short x, short y, short z, BlockState state, boolean secondary, @javax.annotation.Nullable CompoundTag nbt, boolean nbtFull) {
        public Entry(short x, short y, short z, BlockState state, boolean secondary) {
            this(x, y, z, state, secondary, null, false);
        }

        public Entry(short x, short y, short z, BlockState state, boolean secondary, @javax.annotation.Nullable CompoundTag nbt) {
            this(x, y, z, state, secondary, nbt, false);
        }

        public Entry {
            if (nbt == null || nbt.isEmpty()) {
                nbt = null;
                nbtFull = false;
            }
        }

        public Entry moved(short nextX, short nextY, short nextZ) {
            return new Entry(nextX, nextY, nextZ, state, secondary, nbt, nbtFull);
        }

        public Entry withState(BlockState next) {
            return new Entry(x, y, z, next, com.wrights.util.BlockFilters.isSecondary(next), nbt, nbtFull);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putShort("x", x);
            tag.putShort("y", y);
            tag.putShort("z", z);
            tag.putBoolean("s", secondary);
            tag.put("state", NbtUtils.writeBlockState(state));
            if (nbt != null) {
                tag.put("nbt", nbt.copy());
                if (nbtFull) {
                    tag.putBoolean("full", true);
                }
            }
            return tag;
        }

        public static Entry load(CompoundTag tag, HolderGetter<Block> blocks) {
            BlockState state = NbtUtils.readBlockState(blocks, tag.getCompound("state"));
            CompoundTag data = tag.contains("nbt") ? tag.getCompound("nbt") : null;
            return new Entry(tag.getShort("x"), tag.getShort("y"), tag.getShort("z"), state, tag.getBoolean("s"), data, tag.getBoolean("full"));
        }
    }
}
