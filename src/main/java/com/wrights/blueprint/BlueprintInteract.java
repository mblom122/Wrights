package com.wrights.blueprint;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.wrights.WrightsMod;
import com.wrights.item.ControllerItem;

@EventBusSubscriber(modid = WrightsMod.MOD_ID)
public final class BlueprintInteract {
    private BlueprintInteract() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ControllerItem.isHeld(player)) {
            return;
        }
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (session.mode() == BlueprintSessions.Mode.CAPTURE) {
            swallow(event);
            return;
        }
        if (session.mode() == BlueprintSessions.Mode.SAVING) {
            swallow(event);
            return;
        }
        if (session.mode() == BlueprintSessions.Mode.BUILD && player.isShiftKeyDown()) {
            swallow(event);
            return;
        }
        if (session.mode() == BlueprintSessions.Mode.BUILD) {
            swallow(event);
            return;
        }
        if (session.mode() == BlueprintSessions.Mode.BUILD && event.getItemStack().getItem() instanceof ControllerItem) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ControllerItem.isHeld(player)) {
            return;
        }
        BlueprintSessions.Mode mode = BlueprintSessions.get(player).mode();
        if (mode == BlueprintSessions.Mode.CAPTURE || mode == BlueprintSessions.Mode.SAVING) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        } else if (mode == BlueprintSessions.Mode.BUILD) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    private static void swallow(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
    }
}
