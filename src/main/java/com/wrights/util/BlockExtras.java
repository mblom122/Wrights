package com.wrights.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import com.wrights.schematic.Schematic;

/**
 * Extra items a schematic cell spends beyond {@link BlockFilters#payment(BlockState)}.
 * Optional mods register soft-loaded collectors (Framed Blocks camo, AE2 facades, Create copycats, …).
 */
public final class BlockExtras {
    @FunctionalInterface
    public interface Collector {
        void collect(BlockState state, CompoundTag nbt, Consumer<Item> out);
    }

    private static final List<CollectorSpec> SPECS = List.of(
            new CollectorSpec("framedblocks", "com.wrights.compat.framedblocks.FramedBlockExtras"),
            new CollectorSpec("ae2", "com.wrights.compat.ae2.Ae2CableExtras"),
            // Create / Copycats+ material skins — NBT-only, no Create jar required at compile time.
            new CollectorSpec(null, "com.wrights.compat.create.CopycatExtras"));

    private static boolean booted;
    private static final List<Collector> COLLECTORS = new ArrayList<>();

    private BlockExtras() {
    }

    public static List<Item> of(Schematic.Entry entry) {
        if (entry == null) {
            return List.of();
        }
        return of(entry.state(), entry.nbt());
    }

    public static List<Item> of(BlockState state, @Nullable CompoundTag nbt) {
        if (state == null || nbt == null || nbt.isEmpty()) {
            return List.of();
        }
        boot();
        if (COLLECTORS.isEmpty()) {
            return List.of();
        }
        List<Item> out = new ArrayList<>(4);
        Consumer<Item> sink = item -> {
            if (item != null && item != Items.AIR) {
                out.add(item);
            }
        };
        for (Collector collector : COLLECTORS) {
            try {
                collector.collect(state, nbt, sink);
            } catch (RuntimeException | LinkageError ignored) {
                // Optional API mismatch — skip that collector.
            }
        }
        return out;
    }

    private static void boot() {
        if (booted) {
            return;
        }
        booted = true;
        for (CollectorSpec spec : SPECS) {
            if (spec.modId != null && !ModList.get().isLoaded(spec.modId)) {
                continue;
            }
            try {
                Method method = Class.forName(spec.className)
                        .getMethod("collect", BlockState.class, CompoundTag.class, Consumer.class);
                COLLECTORS.add((state, nbt, out) -> {
                    try {
                        method.invoke(null, state, nbt, out);
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        // Collector unavailable this call.
                    }
                });
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Optional class missing or API mismatch.
            }
        }
    }

    private record CollectorSpec(@Nullable String modId, String className) {
    }
}
