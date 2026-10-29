package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounds and drop cells for a screen that should keep the JEI ingredient list open.
 * JEI itself is optional; this type does not reference it.
 */
public interface JeiPreview {
    int JEI_LANE = 168;

    int jeiLeft();

    int jeiTop();

    int jeiWidth();

    int jeiHeight();

    int jeiScreenWidth();

    int jeiScreenHeight();

    default List<StructureGrid.Spot> dropSpots() {
        return List.of();
    }

    default UUID blueprintId() {
        return null;
    }

    default void acceptDrop(int x, int y, int z, BlockState state) {
    }

    /** Shift a centered panel left when the right side would otherwise cover JEI. */
    static int left(int screenWidth, int panelWidth) {
        int natural = Math.max(4, (screenWidth - panelWidth) / 2);
        return Math.max(4, Math.min(natural, screenWidth - panelWidth - JEI_LANE));
    }
}
