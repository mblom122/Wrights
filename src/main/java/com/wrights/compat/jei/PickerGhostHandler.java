package com.wrights.compat.jei;

import java.util.List;
import java.util.Optional;

import com.wrights.client.BlockPickerScreen;
import com.wrights.util.BlockFilters;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drag a block item from JEI onto the Pick blocks panel to add it to the materials tray.
 * Only plain (non-NBT) blocks are accepted.
 */
public final class PickerGhostHandler implements IGhostIngredientHandler<BlockPickerScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(BlockPickerScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        if (!screen.acceptsMaterials()) {
            return List.of();
        }
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty() || stack.get().isEmpty()) {
            return List.of();
        }
        BlockState state = BlockFilters.stateForItem(stack.get());
        if (!BlockFilters.canStore(state)) {
            return List.of();
        }
        // Whole panel is a drop target so the block can be released anywhere on Pick blocks.
        return List.of(new PanelTarget<>(screen, state));
    }

    @Override
    public void onComplete() {
    }

    private static final class PanelTarget<I> implements Target<I> {
        private final BlockPickerScreen screen;
        private final BlockState state;

        private PanelTarget(BlockPickerScreen screen, BlockState state) {
            this.screen = screen;
            this.state = state;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(screen.jeiLeft(), screen.jeiTop(), screen.jeiWidth(), screen.jeiHeight());
        }

        @Override
        public void accept(I ingredient) {
            screen.acceptMaterial(state);
        }
    }
}
