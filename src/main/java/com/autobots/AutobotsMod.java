package com.autobots;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import com.autobots.block.ModBlockEntities;
import com.autobots.block.ModBlocks;
import com.autobots.item.ModCreativeTabs;
import com.autobots.item.ModDataComponents;
import com.autobots.item.ModItems;
import com.autobots.entity.ModEntities;
import com.autobots.menu.ModMenus;
import com.autobots.network.BlueprintNetwork;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import top.theillusivec4.curios.api.CuriosApi;

@Mod(AutobotsMod.MOD_ID)
public class AutobotsMod {
    public static final String MOD_ID = "autobots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutobotsMod(IEventBus modBus, ModContainer container) {
        ModDataComponents.DATA_COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(BlueprintNetwork::register);
        modBus.addListener(AutobotsMod::commonSetup);
        container.registerConfig(ModConfig.Type.SERVER, AutobotsConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AutobotsClientConfig.SPEC);
        LOGGER.info("Wrights loaded for an isolated NeoForge 1.21.1 test");
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CuriosApi.registerCurioPredicate(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "drone_cradle"),
                result -> result.stack().is(ModItems.CRADLE.get())));
    }
}
