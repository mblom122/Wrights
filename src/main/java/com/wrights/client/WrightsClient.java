package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.entity.ModEntities;
import com.wrights.menu.ModMenus;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = WrightsMod.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public class WrightsClient {
    public static final ModelLayerLocation DRONE_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "drone"), "main");

    public WrightsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DRONE_LAYER, DroneModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.HANGAR.get(), HangarScreen::new);
        event.register(ModMenus.WRIGHTPORT.get(), WrightportScreen::new);
    }
}
