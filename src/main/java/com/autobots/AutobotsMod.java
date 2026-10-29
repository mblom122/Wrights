package com.autobots;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import com.autobots.item.ModCreativeTabs;
import com.autobots.item.ModDataComponents;
import com.autobots.item.ModItems;
import com.autobots.entity.ModEntities;

@Mod(AutobotsMod.MOD_ID)
public class AutobotsMod {
    public static final String MOD_ID = "autobots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutobotsMod(IEventBus modBus, ModContainer container) {
        ModDataComponents.DATA_COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, AutobotsConfig.SPEC);
        LOGGER.info("Autobots loaded for an isolated NeoForge 1.21.1 test");
    }
}
