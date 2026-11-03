package com.wrights.drone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The part fitted in each {@link PartSlot} of one wright. Tier {@link WrightTier#BASIC} means the slot is empty. */
public record Loadout(int rotor, int fabricator) {
    public static final Loadout BASIC = new Loadout(WrightTier.BASIC, WrightTier.BASIC);

    public static final Codec<Loadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rotor", WrightTier.BASIC).forGetter(Loadout::rotor),
            Codec.INT.optionalFieldOf("fabricator", WrightTier.BASIC).forGetter(Loadout::fabricator)
    ).apply(instance, Loadout::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Loadout> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Loadout::rotor,
            ByteBufCodecs.VAR_INT, Loadout::fabricator,
            Loadout::new);

    public Loadout {
        rotor = WrightTier.clamp(rotor);
        fabricator = WrightTier.clamp(fabricator);
    }

    public int tier(PartSlot slot) {
        return switch (slot) {
            case ROTOR -> rotor;
            case FABRICATOR -> fabricator;
        };
    }

    public Loadout with(PartSlot slot, int tier) {
        return switch (slot) {
            case ROTOR -> new Loadout(tier, fabricator);
            case FABRICATOR -> new Loadout(rotor, tier);
        };
    }

    public boolean isBasic() {
        return rotor == WrightTier.BASIC && fabricator == WrightTier.BASIC;
    }

    public boolean isMax() {
        for (PartSlot slot : PartSlot.values()) {
            if (tier(slot) < WrightTier.APEX) {
                return false;
            }
        }
        return true;
    }
}
