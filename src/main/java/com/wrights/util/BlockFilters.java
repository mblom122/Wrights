package com.wrights.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
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
     * Block entities are refused unless {@code nbtBlocks} is on (contents are stripped separately).
     */
    public static boolean canCopy(BlockGetter level, BlockPos pos, BlockState state) {
        return canCopy(level, pos, state, false, false);
    }

    /** {@code fluids} also keeps water and lava. A source is paid for with one bucket. */
    public static boolean canCopy(BlockGetter level, BlockPos pos, BlockState state, boolean fluids) {
        return canCopy(level, pos, state, fluids, false);
    }

    /**
     * {@code fluids} also keeps water and lava. {@code nbtBlocks} also keeps chests and machines
     * (as empty blocks, or with settings NBT when the save job asked for that).
     */
    public static boolean canCopy(BlockGetter level, BlockPos pos, BlockState state, boolean fluids, boolean nbtBlocks) {
        if (state.isAir()) {
            return false;
        }
        if (state.hasBlockEntity() && !nbtBlocks) {
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

    /** A full source, not a flowing level. One source costs one bucket. */
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

    /**
     * The item a build spends for this block. A source fluid spends its bucket.
     * Flowing fluid is free, and a block with no item is free.
     */
    public static Item payment(BlockState state) {
        if (state == null) {
            return null;
        }
        if (isSourceFluid(state)) {
            Item bucket = state.getFluidState().getType().getBucket();
            return bucket == Items.AIR ? null : bucket;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
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
        if (state == null || state.isAir() || isNbtBlock(state)) {
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

    /**
     * Chests, machines, and other block-entity blocks. Shape materials and the plain block picker
     * refuse these — shapes only place default states with no NBT.
     */
    public static boolean isNbtBlock(BlockState state) {
        return state != null && (state.hasBlockEntity() || state.getBlock() instanceof EntityBlock);
    }

    /** Sand, gravel, anvils, and the other blocks that fall when the block under them is missing. */
    public static boolean falls(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    /**
     * Plants that break unless the block under them (and sometimes beside them) is right.
     * Cactus and sugar cane are not {@link BushBlock}s, but they pop the same way.
     */
    public static boolean needsGround(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BushBlock
                || block instanceof CactusBlock
                || block instanceof SugarCaneBlock;
    }

    public static boolean isCactus(BlockState state) {
        return state.getBlock() instanceof CactusBlock;
    }

    /** Sand / red sand — what a cactus tower must sit on. */
    public static boolean cactusSoil(BlockState state) {
        return state != null && state.is(BlockTags.SAND);
    }

    public static boolean isSecondary(BlockState state) {
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.getBlock() instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }
}
