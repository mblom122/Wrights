package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.block.ModBlockEntities;
import com.wrights.block.ModBlocks;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.block.WrightportLamp;
import com.wrights.drone.Swarm;
import com.wrights.entity.ModEntities;
import com.wrights.item.BlueprintFolderItem;
import com.wrights.item.BlueprintItem;
import com.wrights.item.ModItems;
import com.wrights.menu.ModMenus;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod(value = WrightsMod.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public class WrightsClient {
    public WrightsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        PartModels.ALL.forEach(event::register);
    }

    @SubscribeEvent
    public static void registerItemRenderers(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private WrightItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new WrightItemRenderer();
                }
                return renderer;
            }
        }, ModItems.WRIGHT.get());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WRIGHTPORT.get(), WrightportRenderer::new);
        // Curios copies this map at the end of AddLayers. Registering there is too late, so the back model never draws.
        CuriosRendererRegistry.register(ModItems.PERSONAL_WRIGHTPORT.get(), PersonalWrightportRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.BLUEPRINT.get(),
                ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "filled"),
                (stack, level, entity, seed) -> BlueprintItem.hasData(stack) ? 1.0F : 0.0F));
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.BLUEPRINT_FOLDER.get(),
                ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "filled"),
                (stack, level, entity, seed) -> BlueprintFolderItem.hasData(stack) ? 1.0F : 0.0F));
        ResourceLocation energy = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "energy");
        for (var item : java.util.List.of(ModItems.PERSONAL_WRIGHTPORT.get(), ModBlocks.WRIGHTPORT_ITEM.get())) {
            event.enqueueWork(() -> ItemProperties.register(item, energy,
                    (stack, level, entity, seed) -> com.wrights.energy.PortEnergy.itemBars(stack) / (float) com.wrights.energy.EnergyBars.BARS));
        }
    }

    @SubscribeEvent
    public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BlueprintTip.class, BlueprintTip.Renderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WRIGHTPORT.get(), WrightportScreen::new);
        event.register(ModMenus.PERSONAL_HANGAR.get(), PersonalHangarScreen::new);
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) {
                return WrightportLamp.RED;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof WrightportBlockEntity port) {
                return WrightportRenderer.colorOf(port);
            }
            return WrightportLamp.RED;
        }, ModBlocks.WRIGHTPORT.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> WrightportLamp.color(Swarm.from(stack), id -> {
            for (var job : WrightsClientState.jobs) {
                if (job.finished() || job.saving()) {
                    continue;
                }
                if (job.crew().contains(id)) {
                    return true;
                }
            }
            return false;
        }), ModBlocks.WRIGHTPORT_ITEM.get());
    }
}
