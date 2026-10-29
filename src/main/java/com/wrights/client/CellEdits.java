package com.wrights.client;

import java.util.UUID;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;
import com.wrights.util.BlockFilters;

public final class CellEdits {
    private CellEdits() {
    }

    public static void apply(UUID id, int x, int y, int z, BlockState state) {
        if (id == null || state == null || !BlockFilters.canEdit(state)) {
            return;
        }
        if (BlockFilters.isSourceFluid(state)) {
            RecentBlocks.rememberFluid(state.getBlock());
        } else {
            Item item = state.getBlock().asItem();
            if (item instanceof BlockItem) {
                RecentBlocks.remember(item);
            }
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(id, x, y, z, Block.getId(state), false));
    }
}
