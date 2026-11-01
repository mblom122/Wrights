package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintTransforms;
import com.wrights.blueprint.BoxEdits;
import com.wrights.schematic.Schematic;
import com.wrights.util.BlockFilters;

/** Blocks inside a new-blueprint box, in the same shape the blueprint model uses. */
public final class SelectionPreview {
    public record Shot(List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, boolean sampled) {
        public static Shot empty() {
            return new Shot(List.of(), 1, 1, 1, false);
        }

        /** Previews sample instead of concealing; always false. */
        @Deprecated
        public boolean concealed() {
            return false;
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
        int budget = WrightsConfig.previewSampleBudget();
        List<WrightsClientState.Ghost> ghosts = new ArrayList<>(Math.min(budget, 1024));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Random random = new Random(low.asLong() ^ high.asLong() ^ ((long) sizeX << 20) ^ ((long) sizeZ << 10));
        int seen = 0;
        boolean sampled = false;
        for (int y = low.getY(); y <= high.getY(); y++) {
            for (int z = low.getZ(); z <= high.getZ(); z++) {
                for (int x = low.getX(); x <= high.getX(); x++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!BlockFilters.canCopy(level, cursor, state, fluids, nbtBlocks)) {
                        continue;
                    }
                    WrightsClientState.Ghost ghost = new WrightsClientState.Ghost(
                            x - low.getX(),
                            y - low.getY(),
                            z - low.getZ(),
                            1.0F,
                            1.0F,
                            1.0F,
                            Block.getId(state));
                    seen++;
                    if (ghosts.size() < budget) {
                        ghosts.add(ghost);
                    } else {
                        sampled = true;
                        int slot = random.nextInt(seen);
                        if (slot < budget) {
                            ghosts.set(slot, ghost);
                        }
                    }
                }
            }
        }
        return new Shot(List.copyOf(ghosts), sizeX, sizeY, sizeZ, sampled);
    }

    /** Same turns the save applies, so the new-blueprint preview matches the template. */
    public static Shot orient(Shot shot, List<BlueprintTransforms.Op> turns) {
        if (turns == null || turns.isEmpty() || shot.ghosts().isEmpty()) {
            return shot;
        }
        List<Schematic.Entry> entries = new ArrayList<>(shot.ghosts().size());
        for (WrightsClientState.Ghost ghost : shot.ghosts()) {
            entries.add(new Schematic.Entry((short) ghost.x(), (short) ghost.y(), (short) ghost.z(), Block.stateById(ghost.stateId()), false));
        }
        Schematic schematic = new Schematic(UUID.randomUUID(), shot.sizeX(), shot.sizeY(), shot.sizeZ(), entries);
        for (BlueprintTransforms.Op turn : turns) {
            schematic = BlueprintTransforms.apply(schematic, turn);
        }
        List<WrightsClientState.Ghost> ghosts = new ArrayList<>(schematic.blockCount());
        for (Schematic.Entry entry : schematic.entries()) {
            ghosts.add(new WrightsClientState.Ghost(entry.x(), entry.y(), entry.z(), 1.0F, 1.0F, 1.0F, Block.getId(entry.state())));
        }
        return new Shot(List.copyOf(ghosts), schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), shot.sampled());
    }
}
