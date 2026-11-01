package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;

public final class WrightsUi {
    public static final int COPPER = 0xFFC47A3A;
    public static final int COPPER_DIM = 0xFF8C5A2A;
    public static final int BLUE = 0xFF7EB6FF;
    public static final int GOLD = 0xFFE0B060;
    public static final int GREEN = 0xFF6FCF97;
    public static final int RED = 0xFFE07A7A;
    /** A job that stopped on its own and will continue when the cause clears. */
    public static final int TEAL = 0xFF5ED0C4;
    public static final int TEXT = 0xFFE7EEF4;
    public static final int MUTED = 0xFF93A1AD;
    public static final int PANEL = 0xF0101822;
    public static final int INSET = 0xFF0C141C;
    public static final int CARD = 0xFF1C2A37;
    public static final int CARD_HOVER = 0xFF223445;
    public static final int CARD_ON = 0xFF24384A;
    public static final int SHADE = 0xFF101820;

    private WrightsUi() {
    }

    /** Preferred panel width, never wider than the window. */
    public static int fitW(int preferred, int screenW) {
        return Math.min(preferred, Math.max(120, screenW - 16));
    }

    /** Preferred panel height, never taller than the window. */
    public static int fitH(int preferred, int screenH) {
        return Math.min(preferred, Math.max(100, screenH - 16));
    }

    public static int centerX(int panelW, int screenW) {
        return Math.max(8, (screenW - panelW) / 2);
    }

    public static int centerY(int panelH, int screenH) {
        return Math.max(4, (screenH - panelH) / 2);
    }

    /** Thumb for a vertical body scroller. Returns false when everything already fits. */
    public static boolean scrollBar(GuiGraphics graphics, int x, int viewTop, int viewH, int scroll, int contentH) {
        int max = Math.max(0, contentH - viewH);
        if (max <= 0 || viewH <= 8) {
            return false;
        }
        int track = viewH - 4;
        int thumb = Math.max(16, track * viewH / Math.max(viewH, contentH));
        int thumbY = viewTop + 2 + (track - thumb) * scroll / max;
        graphics.fill(x, viewTop + 2, x + 2, viewTop + viewH - 2, 0xFF1A2836);
        graphics.fill(x, thumbY, x + 2, thumbY + thumb, COPPER_DIM);
        return true;
    }

    public static void scrim(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xC0060C14);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x55000000);
        graphics.fill(x, y, x + width, y + height, PANEL);
        border(graphics, x, y, width, height, 0xFF31404E);
        graphics.fill(x, y, x + 2, y + height, COPPER);
        graphics.fill(x + 2, y, x + width, y + 1, COPPER_DIM);
    }

    /** A selectable card with a thin top accent, the same language as the mirror page. */
    public static void choice(GuiGraphics graphics, int x, int y, int width, int height, boolean on, boolean hover) {
        choice(graphics, x, y, width, height, on, hover, true);
    }

    public static void choice(GuiGraphics graphics, int x, int y, int width, int height, boolean on, boolean hover, boolean enabled) {
        if (!enabled) {
            graphics.fill(x, y, x + width, y + height, 0xFF141A22);
            graphics.fill(x, y, x + width, y + Math.min(2, height), 0xFF2A3440);
            return;
        }
        graphics.fill(x, y, x + width, y + height, on ? CARD_ON : (hover ? CARD_HOVER : CARD));
        int accent = on ? TEAL : (hover ? COPPER : COPPER_DIM);
        graphics.fill(x, y, x + width, y + Math.min(2, height), accent);
    }

    /** A row or nav item with a thin bar on the left. */
    public static void rail(GuiGraphics graphics, int x, int y, int width, int height, boolean on, int accent) {
        graphics.fill(x, y, x + width, y + height, on ? CARD_ON : 0xFF141C24);
        graphics.fill(x, y, x + 2, y + height, on ? accent : COPPER_DIM);
    }

    /** A short status block with a colored bar on the left. */
    public static void status(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x, y, x + width, y + height, SHADE);
        graphics.fill(x, y, x + 2, y + height, accent);
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

    public static void slot(GuiGraphics graphics, int x, int y, int edge) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF050A10);
        border(graphics, x, y, 18, 18, edge);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF1A2836);
    }
}
