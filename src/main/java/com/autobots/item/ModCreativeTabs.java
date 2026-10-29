package com.autobots.item;

import com.autobots.AutobotsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AutobotsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AUTOBOTS = TABS.register("autobots", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.autobots"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.CONTROLLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(ModItems.CONTROLLER.get()))
            .build());

    private ModCreativeTabs() {
    }
}
