package com.wrights;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import com.wrights.blueprint.MirrorLaunch;
import com.wrights.drone.CraftDeliveries;
import com.wrights.drone.DockRecovery;
import com.wrights.drone.HangarDock;
import com.wrights.drone.ManageLock;
import com.wrights.job.WrightFlights;
import com.wrights.storage.DigitalNetworks;

/**
 * Server-side caches live in statics. A singleplayer session can close one world and open another
 * without restarting the JVM, so anything keyed by player or unit id has to be dropped at both ends.
 */
@EventBusSubscriber(modid = WrightsMod.MOD_ID)
public final class ServerLifecycle {
    private ServerLifecycle() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        resetCaches();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        resetCaches();
    }

    private static void resetCaches() {
        WrightFlights.reset();
        CraftDeliveries.reset();
        HangarDock.reset();
        ManageLock.reset();
        DockRecovery.reset();
        MirrorLaunch.reset();
        DigitalNetworks.reset();
    }
}
