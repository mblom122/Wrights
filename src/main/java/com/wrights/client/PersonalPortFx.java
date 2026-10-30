package com.wrights.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.wrights.WrightsMod;

/** Client-only door hold pulse when personal wrights leave or return. */
@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class PersonalPortFx {
    private static int doorHold;

    private PersonalPortFx() {
    }

    public static void pulse() {
        doorHold = Math.max(doorHold, 35);
    }

    public static int doorHold() {
        return doorHold;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (doorHold > 0) {
            doorHold--;
        }
    }
}
