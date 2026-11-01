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
 * Optional mods (Framed Blocks camo, …) register through a soft-loaded collector.
 */
public final class BlockExtras {
    private static final String FRAMED_ID = "framedblocks";
    private static final String FRAMED_CLASS = "com.wrights.compat.framedblocks.FramedBlockExtras";

    private static boolean booted;
    @Nullable
    private static Method framedCollect;

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
        if (framedCollect == null) {
            return List.of();
        }
        List<Item> out = new ArrayList<>(4);
        try {
            framedCollect.invoke(null, state, nbt, (Consumer<Item>) item -> {
                if (item != null && item != Items.AIR) {
                    out.add(item);
                }
            });
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional API mismatch — treat as no extras.
        }
        return out;
    }

    private static void boot() {
        if (booted) {
            return;
        }
        booted = true;
        if (!ModList.get().isLoaded(FRAMED_ID)) {
            return;
        }
        try {
            framedCollect = Class.forName(FRAMED_CLASS).getMethod("collect", BlockState.class, CompoundTag.class, Consumer.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            framedCollect = null;
        }
    }
}
