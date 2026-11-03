package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.wrights.WrightsConfig;
import com.wrights.item.ModDataComponents;

/**
 * The wrights stored in one wrightport. Each {@link Unit} keeps its fitted parts and its name.
 */
public record Swarm(List<Unit> units, int assign) {
    /** How many wrights one wrightport may hold. Comes from the server config. */
    public static int capacity() {
        return WrightsConfig.maxWrightsCarried();
    }
    public static final Swarm EMPTY = new Swarm(List.of(), 1);

    public record Unit(UUID id, Loadout parts, String name) {
        public Unit {
            name = cleanName(name);
            parts = parts == null ? Loadout.BASIC : parts;
        }

        public int rotor() {
            return parts.rotor();
        }

        public int fabricator() {
            return parts.fabricator();
        }

        public int core() {
            return parts.core();
        }

        public Component title(int number) {
            if (name == null || name.isBlank()) {
                return Component.translatable("screen.wrights.drone_name", number);
            }
            return Component.translatable("screen.wrights.drone_named", name);
        }
    }

    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("§", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (text.length() > 32) {
            text = text.substring(0, 32).trim();
        }
        return text;
    }

    /** Reads {@code speed}/{@code power} from units saved before parts; writes only {@code parts}. */
    public static final Codec<Unit> UNIT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Unit::id),
            Loadout.CODEC.optionalFieldOf("parts").forGetter(unit -> Optional.of(unit.parts())),
            Codec.INT.optionalFieldOf("speed").forGetter(unit -> Optional.empty()),
            Codec.INT.optionalFieldOf("power").forGetter(unit -> Optional.empty()),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Unit::name)
    ).apply(instance, (id, parts, speed, power, name) -> new Unit(id,
            parts.orElseGet(() -> new Loadout(speed.orElse(WrightTier.BASIC), power.orElse(WrightTier.BASIC), WrightTier.BASIC)), name)));

    public static final Codec<Swarm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UNIT_CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(Swarm::units),
            Codec.INT.optionalFieldOf("assign", 1).forGetter(Swarm::assign)
    ).apply(instance, Swarm::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Swarm> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static Swarm from(ItemStack stack) {
        if (stack.isEmpty()) {
            return EMPTY;
        }
        Swarm swarm = stack.get(ModDataComponents.SWARM.get());
        if (swarm == null) {
            return EMPTY;
        }
        return swarm;
    }

    public static void write(ItemStack stack, Swarm swarm) {
        stack.set(ModDataComponents.SWARM.get(), swarm);
    }

    public int assignCount() {
        if (units.isEmpty()) {
            return 0;
        }
        return Mth.clamp(assign, 1, units.size());
    }

    public Swarm addUnit() {
        return addUnit(Loadout.BASIC, "");
    }

    public Swarm addUnit(Loadout parts, String name) {
        if (units.size() >= capacity()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.add(new Unit(UUID.randomUUID(), parts, name));
        int keep = assign <= 0 ? 1 : assign;
        return new Swarm(List.copyOf(next), keep);
    }

    /** Puts this exact wright into the port when there is a free slot. Same swarm if it is full. */
    public Swarm adopt(Unit unit) {
        if (unit == null || unit.id() == null || units.size() >= capacity() || contains(unit.id())) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.add(unit);
        return new Swarm(List.copyOf(next), assign);
    }

    public boolean contains(UUID id) {
        if (id == null) {
            return false;
        }
        for (Unit unit : units) {
            if (id.equals(unit.id())) {
                return true;
            }
        }
        return false;
    }

    public Swarm without(java.util.Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return this;
        }
        List<Unit> next = new ArrayList<>();
        for (Unit unit : units) {
            if (!ids.contains(unit.id())) {
                next.add(unit);
            }
        }
        if (next.size() == units.size()) {
            return this;
        }
        return new Swarm(List.copyOf(next), assign);
    }

    public Swarm removeUnit(int index) {
        if (index < 0 || index >= units.size()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.remove(index);
        return new Swarm(List.copyOf(next), assign);
    }

    public Swarm withParts(int index, Loadout parts) {
        if (index < 0 || index >= units.size() || units.get(index).parts().equals(parts)) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        Unit unit = next.get(index);
        next.set(index, new Unit(unit.id(), parts, unit.name()));
        return new Swarm(List.copyOf(next), assign);
    }

    public Swarm withName(int index, String name) {
        if (index < 0 || index >= units.size()) {
            return this;
        }
        Unit unit = units.get(index);
        String nextName = cleanName(name);
        if (nextName.equals(unit.name())) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.set(index, new Unit(unit.id(), unit.parts(), nextName));
        return new Swarm(List.copyOf(next), assign);
    }

    public Swarm withAssign(int count) {
        if (units.isEmpty()) {
            return new Swarm(units, 1);
        }
        return new Swarm(units, Mth.clamp(count, 1, units.size()));
    }

    public Swarm move(int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= units.size() || to < 0 || to >= units.size()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.add(to, next.remove(index));
        return new Swarm(List.copyOf(next), assign);
    }
}
