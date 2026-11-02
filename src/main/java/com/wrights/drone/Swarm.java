package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;
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
 * The drones stored in one wrightport. {@code speed} is flight. {@code power} is how many
 * blocks one operation can touch. {@code opsMigrated} is false on swarms saved before the
 * split, so the old speed tier is copied into power once.
 */
public record Swarm(List<Unit> units, int assign) {
    public static final int MAX_SPEED = 5;

    /** How many wrights one wrightport may hold. Comes from the server config. */
    public static int capacity() {
        return WrightsConfig.maxWrightsCarried();
    }
    public static final Swarm EMPTY = new Swarm(List.of(), 1);

    public record Unit(UUID id, int speed, int power, String name, boolean opsMigrated) {
        public Unit {
            name = cleanName(name);
            power = Mth.clamp(power, WrightTier.BASE, WrightTier.NETHERITE);
        }

        /** A wright created in code. Operations start where the caller set them, and are not migrated again. */
        public Unit(UUID id, int speed, int power, String name) {
            this(id, speed, power, name, true);
        }

        public Unit(UUID id, int speed) {
            this(id, speed, WrightTier.BASE, "");
        }

        public Unit(UUID id, int speed, int power) {
            this(id, speed, power, "");
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

    public static final Codec<Unit> UNIT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Unit::id),
            Codec.INT.optionalFieldOf("speed", 1).forGetter(Unit::speed),
            Codec.INT.optionalFieldOf("power", 0).forGetter(Unit::power),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Unit::name),
            Codec.BOOL.optionalFieldOf("ops", false).forGetter(Unit::opsMigrated)
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
        if (swarm == null) {
            return EMPTY;
        }
        Swarm migrated = swarm.migrated();
        if (migrated != swarm) {
            write(stack, migrated);
        }
        return migrated;
    }

    /** Copies each wright's speed tier into operations the first time an old swarm is read. */
    public Swarm migrated() {
        boolean dirty = false;
        List<Unit> next = new ArrayList<>(units.size());
        for (Unit unit : units) {
            if (unit.opsMigrated()) {
                next.add(unit);
                continue;
            }
            dirty = true;
            next.add(new Unit(unit.id(), unit.speed(), WrightTier.clamp(unit.speed()), unit.name(), true));
        }
        return dirty ? new Swarm(List.copyOf(next), assign) : this;
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
        return addUnit(speed, "");
    }

    public Swarm addUnit(int speed, String name) {
        if (units.size() >= capacity()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.add(new Unit(UUID.randomUUID(), Mth.clamp(speed, WrightTier.BASE, MAX_SPEED), WrightTier.BASE, name, true));
        int keep = assign <= 0 ? 1 : assign;
        return new Swarm(List.copyOf(next), keep);
    }

    /** Puts this exact wright into the port when there is a free slot. Same swarm if it is full. */
    public Swarm adopt(Unit unit) {
        if (unit == null || unit.id() == null || units.size() >= capacity() || contains(unit.id())) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        next.add(new Unit(unit.id(), Mth.clamp(unit.speed(), WrightTier.BASE, MAX_SPEED), unit.power(), unit.name(), unit.opsMigrated()));
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

    public Swarm withSpeed(int index, int speed) {
        if (index < 0 || index >= units.size()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        Unit unit = next.get(index);
        next.set(index, new Unit(unit.id(), Mth.clamp(speed, WrightTier.BASE, MAX_SPEED), unit.power(), unit.name(), unit.opsMigrated()));
        return new Swarm(List.copyOf(next), assign);
    }

    public Swarm withPower(int index, int power) {
        if (index < 0 || index >= units.size()) {
            return this;
        }
        List<Unit> next = new ArrayList<>(units);
        Unit unit = next.get(index);
        int nextPower = WrightTier.clamp(power);
        if (unit.power() == nextPower && unit.opsMigrated()) {
            return this;
        }
        next.set(index, new Unit(unit.id(), unit.speed(), nextPower, unit.name(), true));
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
        next.set(index, new Unit(unit.id(), unit.speed(), unit.power(), nextName, unit.opsMigrated()));
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
