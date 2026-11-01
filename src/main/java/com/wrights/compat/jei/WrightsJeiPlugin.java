package com.wrights.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.wrights.WrightsMod;
import com.wrights.client.BlockPickerScreen;
import com.wrights.client.BlueprintEditScreen;
import com.wrights.client.BlueprintMapScreen;
import com.wrights.client.PlacementScreen;
import com.wrights.item.ModItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class WrightsJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        List<ItemStack> stacks = new ArrayList<>();
        ModItems.ITEMS.getEntries().forEach(entry -> stacks.add(entry.get().getDefaultInstance()));
        registration.addExtraItemStacks(stacks);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(BlueprintEditScreen.class, WrightsJeiScreens::area);
        registration.addGuiScreenHandler(PlacementScreen.class, WrightsJeiScreens::area);
        registration.addGuiScreenHandler(BlueprintMapScreen.class, WrightsJeiScreens::area);
        registration.addGuiScreenHandler(BlockPickerScreen.class, WrightsJeiScreens::area);
        registration.addGhostIngredientHandler(BlueprintEditScreen.class, new PreviewGhostHandler<>());
        registration.addGhostIngredientHandler(PlacementScreen.class, new PreviewGhostHandler<>());
        registration.addGhostIngredientHandler(BlueprintMapScreen.class, new PreviewGhostHandler<>());
        registration.addGhostIngredientHandler(BlockPickerScreen.class, new PickerGhostHandler());
    }
}
