package com.autobots.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class AutobotsButton extends Button {
    private final boolean danger;
    private boolean picked;

    public void setPicked(boolean picked) {
        this.picked = picked;
    }

    public void setShown(boolean shown) {
        this.visible = shown;
        this.active = shown;
    }

    public AutobotsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    public AutobotsButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean danger) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.danger = danger;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int background = this.picked ? 0xFF2A4054 : (!this.active ? 0xFF141A22 : (this.isHovered() ? 0xFF2C3E52 : 0xFF1A2633));
        graphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, background);
        int border = this.picked ? AutobotsUi.COPPER : (!this.active ? 0xFF2A3440 : (this.danger ? (this.isHovered() ? 0xFFE07A7A : 0xFF8E4048) : (this.isHovered() ? AutobotsUi.COPPER : 0xFF3D4E5E)));
        AutobotsUi.border(graphics, getX(), getY(), this.width, this.height, border);
        int color = !this.active ? 0xFF6E7A86 : AutobotsUi.TEXT;
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + this.width / 2, getY() + (this.height - 8) / 2, color);
    }
}
