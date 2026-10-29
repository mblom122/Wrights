package com.autobots.menu;

import com.autobots.AutobotsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AutobotsMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<HangarMenu>> HANGAR = MENUS.register("hangar",
            () -> IMenuTypeExtension.create(HangarMenu::new));

    private ModMenus() {
    }
}
