package com.wrights.job;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import com.wrights.util.BlockFilters;

/** Clears leftover fluid when an undo or removal exposes it and it starts to spread. */
public final class FluidClearing {
    private FluidClearing() {
    }

    private record FluidWalk(BlockPos pos, int across, int fallen, int travel) {
    }

    /**
     * Placement lets a source flow, and undo removes only the recorded cell.
     * Flowing blocks never get a fluid tick, so they stay. Two sources can also
     * turn the blocks between them into new sources. Clear that spill, and leave
     * a body of water that continues outside the build.
     */
    static void clearFluidSpread(ServerLevel level, BlockPos origin, Fluid fluid, BuildJob job) {
        int reach = fluid.isSame(Fluids.LAVA) && !level.dimensionType().ultraWarm() ? 4 : 8;
        Set<Long> kept = new HashSet<>();
        BlockPos base = job.min();
        for (BuildJob.Refund refund : job.refunds()) {
            FluidState state = refund.state().getFluidState();
            if (!state.isEmpty() && state.getType().isSame(fluid)) {
                kept.add(BlockPos.asLong(base.getX() + refund.x(), base.getY() + refund.y(), base.getZ() + refund.z()));
            }
        }
        BlockPos boxMin = new BlockPos(base.getX() - reach, base.getY() - 64, base.getZ() - reach);
        BlockPos boxMax = new BlockPos(
                base.getX() + job.boxSizeX() - 1 + reach,
                base.getY() + job.boxSizeY() - 1 + 16,
                base.getZ() + job.boxSizeZ() - 1 + reach);
        ArrayDeque<FluidWalk> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> seenSources = new HashSet<>();
        List<BlockPos> removed = new ArrayList<>();
        queue.add(new FluidWalk(origin, 0, 0, 0));
        seen.add(origin.asLong());
        while (!queue.isEmpty() && removed.size() < 2048) {
            FluidWalk step = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = step.pos().relative(direction);
                int across = step.across();
                int fallen = step.fallen();
                int travel = step.travel() + 1;
                if (direction == Direction.DOWN) {
                    fallen++;
                    across = 0;
                } else if (direction != Direction.UP) {
                    across++;
                }
                if (across > reach || fallen > 64 || travel > 128 || next.getY() > origin.getY() + 16) {
                    continue;
                }
                long key = next.asLong();
                if (!seen.add(key) || kept.contains(key) || level.isOutsideBuildHeight(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!isFreeFluid(state, fluid)) {
                    continue;
                }
                if (state.getFluidState().isSource()) {
                    List<BlockPos> pool = new ArrayList<>();
                    if (!enclosedSourcePool(level, next, fluid, kept, seenSources, boxMin, boxMax, pool)) {
                        continue;
                    }
                    for (BlockPos source : pool) {
                        seen.add(source.asLong());
                        level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        removed.add(source.immutable());
                        queue.add(new FluidWalk(source, 0, 0, 0));
                    }
                    continue;
                }
                level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                removed.add(next.immutable());
                queue.add(new FluidWalk(next.immutable(), across, fallen, travel));
            }
        }
        level.updateNeighborsAt(origin, Blocks.AIR);
        for (BlockPos pos : removed) {
            level.updateNeighborsAt(pos, Blocks.AIR);
        }
    }

    private static boolean isFreeFluid(BlockState state, Fluid fluid) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty() || !fluidState.getType().isSame(fluid)) {
            return false;
        }
        return BlockFilters.isFluid(state) || state.is(Blocks.BUBBLE_COLUMN);
    }

    /** True when every connected source stays inside the build's spread, so it is not a lake. */
    private static boolean enclosedSourcePool(ServerLevel level, BlockPos seed, Fluid fluid, Set<Long> kept, Set<Long> seenSources, BlockPos boxMin, BlockPos boxMax, List<BlockPos> pool) {
        if (!seenSources.add(seed.asLong())) {
            return false;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            if (pool.size() > 256) {
                return false;
            }
            BlockPos at = queue.removeFirst();
            if (at.getX() < boxMin.getX() || at.getY() < boxMin.getY() || at.getZ() < boxMin.getZ()
                    || at.getX() > boxMax.getX() || at.getY() > boxMax.getY() || at.getZ() > boxMax.getZ()) {
                return false;
            }
            pool.add(at.immutable());
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (level.isOutsideBuildHeight(next)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    return false;
                }
                long key = next.asLong();
                if (kept.contains(key) || seenSources.contains(key)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!isFreeFluid(state, fluid) || !state.getFluidState().isSource()) {
                    continue;
                }
                seenSources.add(key);
                queue.add(next.immutable());
            }
        }
        return pool.size() <= 256;
    }
}
