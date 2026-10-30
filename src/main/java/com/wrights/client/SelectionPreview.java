package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsConfig;
import com.wrights.blueprint.BoxEdits;
import com.wrights.util.BlockFilters;

/** Blocks inside a new-blueprint box, in the same shape the blueprint model uses. */
public final class SelectionPreview {
    public record Shot(List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, boolean concealed) {
        public static Shot empty() {
            return new Shot(List.of(), 1, 1, 1, false);
        }
    }

    private SelectionPreview() {
    }

    public static Shot capture(BlockGetter level, BlockPos first, BlockPos second, boolean fluids) {
        return capture(level, first, second, fluids, false);
    }

    public static Shot capture(BlockGetter level, BlockPos first, BlockPos second, boolean fluids, boolean nbtBlocks) {
        if (level == null || first == null || second == null) {
            return Shot.empty();
        }
        BlockPos low = BoxEdits.min(first, second);
        BlockPos high = BoxEdits.max(first, second);
        int sizeX = high.getX() - low.getX() + 1;
        int sizeY = high.getY() - low.getY() + 1;
        int sizeZ = high.getZ() - low.getZ() + 1;
        List<WrightsClientState.Ghost> ghosts = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int limit = WrightsConfig.maxPreviewBlocks();
        for (int y = low.getY(); y <= high.getY(); y++) {
            for (int z = low.getZ(); z <= high.getZ(); z++) {
                for (int x = low.getX(); x <= high.getX(); x++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!BlockFilters.canCopy(level, cursor, state, fluids, nbtBlocks)) {
                        continue;
                    }
                    ghosts.add(new WrightsClientState.Ghost(
                            x - low.getX(),
                            y - low.getY(),
                            z - low.getZ(),
                            1.0F,
                            1.0F,
                            1.0F,
                            Block.getId(state)));
                    if (ghosts.size() > limit) {
                        return new Shot(List.of(), sizeX, sizeY, sizeZ, true);
                    }
                }
            }
        }
        return new Shot(List.copyOf(ghosts), sizeX, sizeY, sizeZ, false);
    }
}
