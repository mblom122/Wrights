package com.wrights.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** One digital-storage backend (AE2 or RS). Implementations live under compat packages. */
public interface DigitalAccess {
    DigitalLink.Kind kind();

    /** True when this block can be bound with the Wrights controller. */
    boolean isBindTarget(Level level, BlockPos pos);

    boolean online(ServerLevel level, GlobalPos pos);

    long count(ServerLevel level, GlobalPos pos, Item item);

    /** Removes up to {@code amount} from the network. Returns how many were taken. */
    long extract(ServerPlayer player, ServerLevel level, GlobalPos pos, Item item, long amount);

    /** Inserts as many of {@code stack} as the network can hold. Returns how many were stored. */
    default long insert(ServerPlayer player, ServerLevel level, GlobalPos pos, net.minecraft.world.item.ItemStack stack) {
        return 0L;
    }

    boolean craftable(ServerLevel level, GlobalPos pos, Item item);

    /** Asks the network to autocraft {@code amount} of the item into storage. */
    boolean requestCraft(ServerPlayer player, ServerLevel level, GlobalPos pos, Item item, long amount);

    /** Finishes craft plans that were started on an earlier tick. Must not block. */
    default void tickCrafts(net.minecraft.server.MinecraftServer server) {
    }

    /**
     * Pulls wright item stacks of any tier. Returns the tier of each extracted wright.
     * Default drains via {@link #extract} and treats them as stock wrights.
     */
    default List<Integer> extractWrights(ServerPlayer player, ServerLevel level, GlobalPos pos, int count) {
        List<Integer> tiers = new ArrayList<>();
        long taken = extract(player, level, pos, com.wrights.item.ModItems.WRIGHT.get(), count);
        for (int i = 0; i < taken; i++) {
            tiers.add(com.wrights.drone.WrightTier.BASE);
        }
        return tiers;
    }
}
