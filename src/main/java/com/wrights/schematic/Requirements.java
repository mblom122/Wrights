package com.wrights.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.util.BlockFilters;

public final class Requirements {
    public record Cost(int itemId, int count, int player, int supply) {
        public Cost(int itemId, int count) {
            this(itemId, count, 0, 0);
        }
    }

    private Requirements() {
    }

    public static List<Cost> of(Schematic schematic, int clipX, int clipY, int clipZ) {
        return of(schematic, clipX, clipY, clipZ, true);
    }

    public static List<Cost> of(Schematic schematic, int clipX, int clipY, int clipZ, boolean placeFluids) {
        int limitX = clipX <= 0 ? schematic.sizeX() : clipX;
        int limitY = clipY <= 0 ? schematic.sizeY() : clipY;
        int limitZ = clipZ <= 0 ? schematic.sizeZ() : clipZ;
        Map<Item, Integer> counts = new HashMap<>();
        for (Schematic.Entry entry : schematic.entries()) {
            if (entry.secondary() || entry.x() >= limitX || entry.y() >= limitY || entry.z() >= limitZ) {
                continue;
            }
            if (!placeFluids && BlockFilters.isFluid(entry.state())) {
                continue;
            }
            Item item = BlockFilters.payment(entry.state());
            if (item == null) {
                continue;
            }
            counts.merge(item, 1, Integer::sum);
        }
        List<Cost> costs = new ArrayList<>(counts.size());
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            costs.add(new Cost(Item.getId(entry.getKey()), entry.getValue()));
        }
        costs.sort(Comparator.comparingInt(Cost::count).reversed().thenComparingInt(Cost::itemId));
        return costs;
    }

    /** Fills in how many of each item the player and a bound chest currently hold. */
    public static List<Cost> stocked(ServerPlayer player, List<Cost> costs, BlockPos supply, ResourceKey<Level> dimension) {
        List<Cost> stocked = new ArrayList<>(costs.size());
        for (Cost cost : costs) {
            Item item = Item.byId(cost.itemId());
            stocked.add(new Cost(cost.itemId(), cost.count(), playerCount(player, item), supplyCount(player, supply, dimension, item)));
        }
        return stocked;
    }

    public static List<Cost> missing(ServerPlayer player, List<Cost> costs, BlockPos supply, ResourceKey<Level> dimension) {
        if (player.getAbilities().instabuild || player.isSpectator()) {
            return List.of();
        }
        List<Cost> missing = new ArrayList<>();
        for (Cost cost : stocked(player, costs, supply, dimension)) {
            if (cost.player() + cost.supply() < cost.count()) {
                missing.add(cost);
            }
        }
        return missing;
    }

    public static int playerCount(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    public static int supplyCount(ServerPlayer player, BlockPos supply, ResourceKey<Level> dimension, Item item) {
        long stored = 0L;
        ServerLevel level = null;
        if (dimension != null) {
            level = player.server.getLevel(dimension);
        } else if (player.level() instanceof ServerLevel current) {
            level = current;
        }
        if (level != null && supply != null && level.mayInteract(player, supply)) {
            Container container = containerAt(level, supply);
            if (container != null) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.is(item)) {
                        stored += stack.getCount();
                    }
                }
            }
        }
        stored += com.wrights.storage.ItemSource.digitalCount(player, item);
        return stored > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) stored;
    }

    private static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, false);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container container) {
            return container;
        }
        return null;
    }
}
