package com.autobots.schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Entry> entries;

    public Schematic(UUID id, int sizeX, int sizeY, int sizeZ, List<Entry> entries) {
        this.id = id;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.entries = List.copyOf(entries);
    }

    public UUID id() {
        return id;
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
        tag.putInt("sx", sizeX);
        tag.putInt("sy", sizeY);
        tag.putInt("sz", sizeZ);
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
        return new Schematic(id, tag.getInt("sx"), tag.getInt("sy"), tag.getInt("sz"), entries);
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
