package com.wrights.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class BlockFilters {
    private static Map<Item, BlockState> bucketSources;

    private BlockFilters() {
    }

    /**
     * Blocks we can remember and later pay for with one item.
     * Block entities are refused so chests and machines cannot duplicate their contents.
     */
    public static boolean canCopy(BlockGetter level, BlockPos pos, BlockState state) {
        return canCopy(level, pos, state, false);
    }

    /** {@code fluids} also keeps water and lava. Those blocks have no item to pay with. */
    public static boolean canCopy(BlockGetter level, BlockPos pos, BlockState state, boolean fluids) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        var block = state.getBlock();
        if (block instanceof LiquidBlock) {
            return fluids;
        }
        if (block instanceof NetherPortalBlock
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

    public static boolean isFluid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

    /** A full source, not a flowing level. Editing can place these; they are not paid for with an item. */
    public static boolean isSourceFluid(BlockState state) {
        return isFluid(state) && state.getFluidState().isSource();
    }

    /** A block the editor can write into a blueprint, including source fluids. */
    public static boolean canEdit(BlockState state) {
        return isSourceFluid(state) || canStore(state);
    }

    /**
     * A block or source fluid an editor drop can write.
     * A water bucket becomes the water source, never the bucket item.
     */
    public static BlockState stateForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (isSourceFluid(state) || (canStore(state) && !isFluid(state))) {
                return state;
            }
        }
        return bucketSources().get(item);
    }

    /** The source block for a fluid ingredient. Flowing fluids are refused. */
    public static BlockState stateForFluid(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }
        BlockState state = fluid.defaultFluidState().createLegacyBlock();
        return isSourceFluid(state) ? state : null;
    }

    private static Map<Item, BlockState> bucketSources() {
        if (bucketSources != null) {
            return bucketSources;
        }
        Map<Item, BlockState> found = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (!isSourceFluid(state)) {
                continue;
            }
            Item bucket = state.getFluidState().getType().getBucket();
            if (bucket != Items.AIR) {
                found.putIfAbsent(bucket, state);
            }
        }
        bucketSources = found;
        return found;
    }

    /** The stack shown for a stored block. Source fluids use their bucket, since the fluid block has no item. */
    public static ItemStack icon(BlockState state) {
        Item item = state.getBlock().asItem();
        if (item != Items.AIR) {
            return new ItemStack(item);
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            Item bucket = fluid.getType().getBucket();
            if (bucket != Items.AIR) {
                return new ItemStack(bucket);
            }
        }
        return ItemStack.EMPTY;
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
