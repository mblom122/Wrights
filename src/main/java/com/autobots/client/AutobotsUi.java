package com.autobots.client;

import net.minecraft.client.gui.GuiGraphics;

public final class AutobotsUi {
    public static final int COPPER = 0xFFC47A3A;
    public static final int COPPER_DIM = 0xFF8C5A2A;
    public static final int BLUE = 0xFF7EB6FF;
    public static final int TEXT = 0xFFE7EEF4;
    public static final int MUTED = 0xFF93A1AD;
    public static final int PANEL = 0xF0101822;
    public static final int INSET = 0xFF0C141C;

    private AutobotsUi() {
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 4, y + 5, x + width + 4, y + height + 5, 0x66000000);
        graphics.fill(x, y, x + width, y + height, PANEL);
        border(graphics, x, y, width, height, 0xFF31404E);
        graphics.fill(x, y, x + 3, y + height, COPPER);
        graphics.fill(x + 3, y, x + width, y + 2, COPPER_DIM);
    }

    public static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, INSET);
        border(graphics, x, y, width, height, 0xFF243140);
    }
}
