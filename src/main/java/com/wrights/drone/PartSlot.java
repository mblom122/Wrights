package com.wrights.drone;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredItem;

import com.wrights.item.ModItems;
import com.wrights.item.WrightPartItem;

/** A place on the wright chassis that holds one part. Each slot takes parts of tier Scout through Apex. */
public enum PartSlot implements StringRepresentable {
    ROTOR,
    FABRICATOR,
    CORE;

    public static final Codec<PartSlot> CODEC = StringRepresentable.fromEnum(PartSlot::values);

    private final String id = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The part item of this slot at {@code tier}, or null for Basic. */
    public Item item(int tier) {
        int mark = WrightTier.clamp(tier);
        if (mark <= WrightTier.BASIC) {
            return null;
        }
        return parts().get(mark - 1).get();
    }

    /** This slot's part items, Scout first. */
    public List<DeferredItem<WrightPartItem>> parts() {
        return switch (this) {
            case ROTOR -> ModItems.ROTORS;
            case FABRICATOR -> ModItems.FABRICATORS;
            case CORE -> ModItems.CORES;
        };
    }

    /** "Rotor Assembly", "Fabricator Head", "Power Core". */
    public String nameKey() {
        return "part.wrights." + id;
    }

    /** "Scout Rotor Assembly" for {@code tier}, or "Basic" for an empty slot. */
    public String fittedKey(int tier) {
        int mark = WrightTier.clamp(tier);
        return mark <= WrightTier.BASIC ? "part.wrights." + id + ".none" : "item.wrights." + id + "_mk" + mark;
    }
}
