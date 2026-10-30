package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.entity.DroneEntity;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Starts the looping flight hum when a wright appears on the client. */
@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class DroneSounds {
    private DroneSounds() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof DroneEntity drone)) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(new DroneFlightSoundInstance(drone));
    }
}
