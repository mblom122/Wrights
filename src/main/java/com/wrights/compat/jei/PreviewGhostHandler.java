package com.wrights.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.wrights.client.JeiPreview;
import com.wrights.client.StructureGrid;
import com.wrights.util.BlockFilters;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/** A JEI ingredient dropped on a preview cell replaces that block or source fluid. */
public final class PreviewGhostHandler<T extends Screen & JeiPreview> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        BlockState state = dragged(ingredient);
        if (state == null || screen.blueprintId() == null) {
            return List.of();
        }
        List<StructureGrid.Spot> spots = screen.dropSpots();
        if (spots.isEmpty()) {
            return List.of();
        }
        List<Target<I>> targets = new ArrayList<>(spots.size());
        for (StructureGrid.Spot spot : spots) {
            targets.add(new CellTarget<>(screen, spot, state));
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    private static BlockState dragged(ITypedIngredient<?> ingredient) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isPresent()) {
            BlockState state = BlockFilters.stateForItem(stack.get());
            if (state != null) {
                return state;
            }
        }
        Optional<FluidStack> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            return BlockFilters.stateForFluid(fluid.get().getFluid());
        }
        return null;
    }

    private static final class CellTarget<T extends Screen & JeiPreview, I> implements Target<I> {
        private final T screen;
        private final StructureGrid.Spot spot;
        private final BlockState state;

        private CellTarget(T screen, StructureGrid.Spot spot, BlockState state) {
            this.screen = screen;
            this.spot = spot;
            this.state = state;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(spot.pixelX(), spot.pixelY(), Math.max(1, spot.size()), Math.max(1, spot.size()));
        }

        @Override
        public void accept(I ingredient) {
            screen.acceptDrop(spot.x(), spot.y(), spot.z(), state);
        }
    }
}
