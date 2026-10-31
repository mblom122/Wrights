package com.wrights.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class WrightsButton extends Button {
    private final boolean danger;
    private boolean picked;

    public void setPicked(boolean picked) {
        this.picked = picked;
    }

    public void setShown(boolean shown) {
        this.visible = shown;
        this.active = shown;
    }

    public WrightsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    public WrightsButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean danger) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.danger = danger;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        if (!this.active) {
            graphics.fill(x, y, x + this.width, y + this.height, 0xFF141A22);
            graphics.fill(x, y, x + this.width, y + Math.min(2, this.height), 0xFF2A3440);
        } else if (this.danger) {
            graphics.fill(x, y, x + this.width, y + this.height, this.isHovered() ? 0xFF3A2428 : 0xFF2A1C20);
            graphics.fill(x, y, x + this.width, y + Math.min(2, this.height), this.isHovered() ? WrightsUi.RED : 0xFF8E4048);
        } else {
            WrightsUi.choice(graphics, x, y, this.width, this.height, this.picked, this.isHovered());
        }
        int color = !this.active ? 0xFF6E7A86 : WrightsUi.TEXT;
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + this.width / 2, getY() + (this.height - 8) / 2, color);
    }
}
