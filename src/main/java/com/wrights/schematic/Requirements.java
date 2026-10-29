package com.wrights.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import com.wrights.util.BlockFilters;

public final class Requirements {
    public record Cost(int itemId, int count) {
    }

    private Requirements() {
    }

    public static List<Cost> of(Schematic schematic, int clipX, int clipY, int clipZ) {
        int limitX = clipX <= 0 ? schematic.sizeX() : clipX;
        int limitY = clipY <= 0 ? schematic.sizeY() : clipY;
        int limitZ = clipZ <= 0 ? schematic.sizeZ() : clipZ;
        Map<Item, Integer> counts = new HashMap<>();
        for (Schematic.Entry entry : schematic.entries()) {
            if (entry.secondary() || entry.x() >= limitX || entry.y() >= limitY || entry.z() >= limitZ) {
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

    public static List<Cost> missing(ServerPlayer player, List<Cost> costs) {
        if (player.getAbilities().instabuild || player.isSpectator()) {
            return List.of();
        }
        List<Cost> missing = new ArrayList<>();
        for (Cost cost : costs) {
            Item item = Item.byId(cost.itemId());
            if (player.getInventory().countItem(item) < cost.count()) {
                missing.add(cost);
            }
        }
        return missing;
    }
}
