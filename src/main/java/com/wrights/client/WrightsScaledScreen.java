package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Wrights {@link Screen} with uniform high-GUI-scale shrink via {@link WrightsGuiScale}.
 * Subclasses override {@code *Scaled} methods; mouse coords are already remapped.
 */
public abstract class WrightsScaledScreen extends Screen {
    protected WrightsScaledScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep screen margins darkened when content is shrunk.
        if (WrightsGuiScale.active()) {
            graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        }
        int localX = WrightsGuiScale.localX(mouseX, this.width);
        int localY = WrightsGuiScale.localY(mouseY, this.height);
        WrightsGuiScale.begin(graphics, this.width, this.height);
        try {
            renderScaled(graphics, localX, localY, partialTick);
        } finally {
            WrightsGuiScale.end(graphics);
        }
    }

    /**
     * Draw the screen in logical (unscaled) coordinates. Default matches vanilla
     * {@link Screen#render}: background, then widgets.
     */
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedScaled(
                WrightsGuiScale.localX(mouseX, this.width),
                WrightsGuiScale.localY(mouseY, this.height),
                button);
    }

    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return mouseReleasedScaled(
                WrightsGuiScale.localX(mouseX, this.width),
                WrightsGuiScale.localY(mouseY, this.height),
                button);
    }

    protected boolean mouseReleasedScaled(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return mouseDraggedScaled(
                WrightsGuiScale.localX(mouseX, this.width),
                WrightsGuiScale.localY(mouseY, this.height),
                button,
                WrightsGuiScale.localDelta(dragX),
                WrightsGuiScale.localDelta(dragY));
    }

    protected boolean mouseDraggedScaled(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return mouseScrolledScaled(
                WrightsGuiScale.localX(mouseX, this.width),
                WrightsGuiScale.localY(mouseY, this.height),
                scrollX,
                scrollY);
    }

    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
