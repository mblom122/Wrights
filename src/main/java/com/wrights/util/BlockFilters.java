package com.wrights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class BlockFilters {
    private BlockFilters() {
    }

    /**
     * Blocks we can remember and later pay for with one item.
     * Block entities are refused so chests and machines cannot duplicate their contents.
     */
    public static boolean canCopy(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        var block = state.getBlock();
        if (block instanceof LiquidBlock
                || block instanceof NetherPortalBlock
                || block instanceof EndPortalBlock
                || block instanceof EndGatewayBlock
                || block instanceof PistonHeadBlock
                || block instanceof MovingPistonBlock) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return block.asItem() != Items.AIR;
    }

    public static boolean canStore(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        var block = state.getBlock();
        if (block instanceof LiquidBlock
                || block instanceof NetherPortalBlock
                || block instanceof EndPortalBlock
                || block instanceof EndGatewayBlock
                || block instanceof PistonHeadBlock
                || block instanceof MovingPistonBlock) {
            return false;
        }
        return block.asItem() != Items.AIR;
    }

    /** Sand, gravel, anvils, and the other blocks that fall when the block under them is missing. */
    public static boolean falls(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    public static boolean isSecondary(BlockState state) {
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.getBlock() instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }
}
