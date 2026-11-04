package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Hangar screens replace {@link AbstractContainerScreen#render} and never call {@code super.render()}.
 * JEI (and other mods) only draw beside container GUIs on
 * {@link ContainerScreenEvent.Render.Background} / {@link ContainerScreenEvent.Render.Foreground},
 * which vanilla posts from that {@code super.render()} path. {@link net.neoforged.neoforge.client.event.ScreenEvent.Render.Post}
 * deliberately skips {@link AbstractContainerScreen}. Post the container events ourselves so JEI shows.
 */
final class HangarScreenEvents {
    private HangarScreenEvents() {
    }

    static void postRender(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        NeoForge.EVENT_BUS.post(new ContainerScreenEvent.Render.Background(screen, graphics, mouseX, mouseY));
        NeoForge.EVENT_BUS.post(new ContainerScreenEvent.Render.Foreground(screen, graphics, mouseX, mouseY));
    }
}
