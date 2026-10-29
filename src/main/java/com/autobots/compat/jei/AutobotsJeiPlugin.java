package com.autobots.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.autobots.AutobotsMod;
import com.autobots.item.ModItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class AutobotsJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AutobotsMod.MOD_ID, "jei");

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
}
