package com.wrights.storage;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Per-player binding to an AE2 or Refined Storage network block. */
public record DigitalLink(Kind kind, GlobalPos pos) {
    public enum Kind {
        NONE,
        AE2,
        RS;

        public static Kind byId(int id) {
            Kind[] values = values();
            if (id < 0 || id >= values.length) {
                return NONE;
            }
            return values[id];
        }
    }

    public static final DigitalLink NONE = new DigitalLink(Kind.NONE, null);

    public boolean bound() {
        return kind != Kind.NONE && pos != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("kind", (byte) kind.ordinal());
        if (pos != null) {
            tag.putString("dim", pos.dimension().location().toString());
            tag.put("pos", NbtUtils.writeBlockPos(pos.pos()));
        }
        return tag;
    }

    public static DigitalLink load(CompoundTag tag) {
        Kind kind = Kind.byId(tag.getByte("kind"));
        if (kind == Kind.NONE || !tag.contains("pos") || !tag.contains("dim")) {
            return NONE;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dim"));
        var block = NbtUtils.readBlockPos(tag, "pos").orElse(null);
        if (dimension == null || block == null) {
            return NONE;
        }
        return new DigitalLink(kind, GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), block));
    }
}
