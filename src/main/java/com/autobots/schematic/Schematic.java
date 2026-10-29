package com.autobots.schematic;

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

    public Schematic(UUID id, int sizeX, int sizeY, int sizeZ, List<Entry> entries) {
        this(id, "Blueprint", sizeX, sizeY, sizeZ, entries, BlockPos.ZERO, BlockPos.ZERO, "");
    }

    public Schematic(UUID id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> entries, BlockPos sourceMin, BlockPos sourceMax, String sourceDimension) {
        this(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, 0, 0, 0);
    }

    public Schematic(UUID id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> entries, BlockPos sourceMin, BlockPos sourceMax, String sourceDimension, int anchorX, int anchorY, int anchorZ) {
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

    public Schematic withName(String name) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ);
    }

    public Schematic withAnchor(int anchorX, int anchorY, int anchorZ) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ);
    }

    public Schematic withEntries(List<Entry> entries) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ);
    }

    public Schematic withLayout(int sizeY, List<Entry> entries, int anchorY) {
        return new Schematic(id, name, sizeX, sizeY, sizeZ, entries, sourceMin, sourceMax, sourceDimension, anchorX, anchorY, anchorZ);
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
        return new Schematic(id, name, tag.getInt("sx"), tag.getInt("sy"), tag.getInt("sz"), entries, sourceMin, sourceMax, sourceDim, anchorX, anchorY, anchorZ);
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

    public record Entry(short x, short y, short z, BlockState state, boolean secondary) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putShort("x", x);
            tag.putShort("y", y);
            tag.putShort("z", z);
            tag.putBoolean("s", secondary);
            tag.put("state", NbtUtils.writeBlockState(state));
            return tag;
        }

        public static Entry load(CompoundTag tag, HolderGetter<Block> blocks) {
            BlockState state = NbtUtils.readBlockState(blocks, tag.getCompound("state"));
            return new Entry(tag.getShort("x"), tag.getShort("y"), tag.getShort("z"), state, tag.getBoolean("s"));
        }
    }
}
