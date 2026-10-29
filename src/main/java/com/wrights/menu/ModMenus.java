package com.wrights.menu;

import com.wrights.WrightsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, WrightsMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<HangarMenu>> HANGAR = MENUS.register("hangar",
            () -> IMenuTypeExtension.create(HangarMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WrightportMenu>> WRIGHTPORT = MENUS.register("wrightport",
            () -> IMenuTypeExtension.create(WrightportMenu::new));

    private ModMenus() {
    }
}
