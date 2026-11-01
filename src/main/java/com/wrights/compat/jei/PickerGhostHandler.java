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

/** Drag a plain block from JEI onto the Pick blocks tray. NBT blocks are refused. */
public final class PickerGhostHandler implements IGhostIngredientHandler<BlockPickerScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(BlockPickerScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty()) {
            return List.of();
        }
        BlockState state = BlockFilters.stateForItem(stack.get());
        if (!screen.acceptsMaterials() || !BlockFilters.canStore(state)) {
            return List.of();
        }
        return List.of(new TrayTarget<>(screen, state));
    }

    @Override
    public void onComplete() {
    }

    private static final class TrayTarget<I> implements Target<I> {
        private final BlockPickerScreen screen;
        private final BlockState state;

        private TrayTarget(BlockPickerScreen screen, BlockState state) {
            this.screen = screen;
            this.state = state;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(screen.trayPixelX(), screen.trayPixelY(), screen.trayPixelW(), screen.trayPixelH());
        }

        @Override
        public void accept(I ingredient) {
            screen.acceptMaterial(state);
        }
    }
}
