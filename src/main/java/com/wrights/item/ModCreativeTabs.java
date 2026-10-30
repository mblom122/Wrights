package com.wrights.item;

import com.wrights.WrightsMod;
import com.wrights.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WrightsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WRIGHTS = TABS.register("wrights", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wrights"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.CONTROLLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.CONTROLLER.get());
                output.accept(ModItems.BLUEPRINT.get());
                output.accept(ModItems.BLUEPRINT_FOLDER.get());
                output.accept(ModItems.WRIGHT.get());
                output.accept(ModItems.UPGRADE_COPPER.get());
                output.accept(ModItems.UPGRADE_IRON.get());
                output.accept(ModItems.UPGRADE_GOLD.get());
                output.accept(ModItems.UPGRADE_DIAMOND.get());
                output.accept(ModItems.UPGRADE_NETHERITE.get());
                output.accept(ModItems.PERSONAL_WRIGHTPORT.get());
                output.accept(ModBlocks.WRIGHTPORT_ITEM.get());
                output.accept(ModBlocks.HANGAR_ITEM.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
