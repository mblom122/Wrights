package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.wrights.WrightsConfig;
import com.wrights.item.ModDataComponents;

/**
 * The drones stored in one wrightport. {@code power} is reserved for a later upgrade
 * and is not spent yet.
 */
public record Swarm(List<Unit> units, int assign) {
    public static final int MAX_SPEED = 5;

    /** How many wrights one wrightport may hold. Comes from the server config. */
    public static int capacity() {
        return WrightsConfig.maxWrightsCarried();
    }
    public static final Swarm EMPTY = new Swarm(List.of(), 1);

    public record Unit(UUID id, int speed, int power) {
        public Unit(UUID id, int speed) {
            this(id, speed, 0);
        }
    }

    public static final Codec<Unit> UNIT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Unit::id),
            Codec.INT.optionalFieldOf("speed", 1).forGetter(Unit::speed),
            Codec.INT.optionalFieldOf("power", 0).forGetter(Unit::power)
    ).apply(instance, Unit::new));

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
        return swarm == null ? EMPTY : swarm;
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

    public List<Integer> speeds(int count) {
        int n = Math.min(Math.max(0, count), units.size());
        List<Integer> speeds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            speeds.add(Mth.clamp(units.get(i).speed(), WrightTier.BASE, MAX_SPEED));
        }
        return speeds;
    }

    public List<Integer> assignedSpeeds() {
        int count = assignCount();
        List<Integer> speeds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            speeds.add(Mth.clamp(units.get(i).speed(), WrightTier.BASE, MAX_SPEED));
        }
        return speeds;
    }

    public Swarm addUnit() {
        return addUnit(WrightTier.BASE);
    }

    public Swarm addUnit(int speed) {
        if (units.size() >= capacity()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.add(new Unit(UUID.randomUUID(), Mth.clamp(speed, WrightTier.BASE, MAX_SPEED), 0));
        int keep = assign <= 0 ? 1 : assign;
        return new Swarm(List.copyOf(next), keep);
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

    public Swarm withSpeed(int index, int speed) {
        if (index < 0 || index >= units.size()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        Unit unit = next.get(index);
        next.set(index, new Unit(unit.id(), Mth.clamp(speed, WrightTier.BASE, MAX_SPEED), unit.power()));
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
