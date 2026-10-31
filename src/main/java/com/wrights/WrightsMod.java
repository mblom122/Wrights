package com.wrights;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import com.wrights.block.ModBlockEntities;
import com.wrights.block.ModBlocks;
import com.wrights.item.ModCreativeTabs;
import com.wrights.item.ModDataComponents;
import com.wrights.item.ModItems;
import com.wrights.entity.ModEntities;
import com.wrights.menu.ModMenus;
import com.wrights.network.BlueprintNetwork;
import com.wrights.sound.ModSounds;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import top.theillusivec4.curios.api.CuriosApi;

@Mod(WrightsMod.MOD_ID)
public class WrightsMod {
    public static final String MOD_ID = "wrights";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WrightsMod(IEventBus modBus, ModContainer container) {
        ModDataComponents.DATA_COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(BlueprintNetwork::register);
        modBus.addListener(WrightsMod::commonSetup);
        container.registerConfig(ModConfig.Type.SERVER, WrightsConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, WrightsClientConfig.SPEC);
        LOGGER.info("Wrights loaded for an isolated NeoForge 1.21.1 test");
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CuriosApi.registerCurioPredicate(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "wrightport"),
                result -> result.stack().is(ModItems.PERSONAL_WRIGHTPORT.get())));
        event.enqueueWork(() -> com.wrights.schematic.GlobalBlueprintStore.get());
        event.enqueueWork(WrightsMod::registerAe2Link);
    }

    /** Loaded by name so a pack without AE2 never touches its classes. */
    private static void registerAe2Link() {
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2")) {
            return;
        }
        try {
            Class.forName("com.wrights.compat.ae2.Ae2ControllerLink").getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("AE2 is loaded, but the controller could not be registered for the wireless access point", exception);
        }
    }
}
