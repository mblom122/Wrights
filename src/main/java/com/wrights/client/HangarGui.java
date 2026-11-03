package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;

/**
 * Textured hangar panel (wrightport + Manage). Coordinates are pixels on {@link #TEXTURE}
 * ({@value #WIDTH}×{@value #HEIGHT}).
 */
final class HangarGui {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/gui/hangar.png");
    static final ResourceLocation BUTTONS = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/gui/hangar_buttons.png");

    static final int WIDTH = 512;
    static final int HEIGHT = 286;
    /** Extra strip under the art for Add / Remove / Parts / Back. */
    static final int CHROME = 22;
    static final int PANEL_H = HEIGHT + CHROME;

    /** STORAGE BAY 5×2 wright slots. */
    static final int BAY_X = 174;
    static final int BAY_Y = 207;
    static final int BAY_SLOT = 28;
    static final int BAY_GAP = 6;

    /** DRONE HARDWARE part slots (Rotor, Fabricator, Core). */
    static final int PART_X = 147;
    static final int PART_Y = 122;
    static final int PART_SLOT = 34;
    static final int PART_GAP = 57;

    /** Right-side Boundary / Bind / Highlight icon stack. */
    static final int BTN_X = 448;
    static final int BTN_Y = 68;
    static final int BTN_SLOT = 20;
    static final int BTN_GAP = 6;
    static final int BTN_ATLAS = 32;

    static final int TITLE_X = 170;
    static final int TITLE_Y = 8;
    static final int TITLE_W = 170;
    static final int TITLE_H = 20;

    static final int GAUGE_BAR_X = 95;
    static final int GAUGE_BAR_Y = 230;
    static final int GAUGE_BAR_W = 20;
    static final int GAUGE_BAR_H = 40;

    static final int BTN_BOUNDARY = 0;
    static final int BTN_BIND = 1;
    static final int BTN_HIGHLIGHT = 2;

    static final int STATE_DISABLED = 0;
    static final int STATE_IDLE = 1;
    static final int STATE_HOVER = 2;
    static final int STATE_ACTIVE = 3;
    static final int STATE_ACTIVE_HOVER = 4;

    private HangarGui() {
    }

    static void blitPanel(GuiGraphics graphics, int left, int top) {
        graphics.blit(TEXTURE, left, top, 0.0F, 0.0F, WIDTH, HEIGHT, WIDTH, HEIGHT);
    }

    static int partSlotX(int left, int index) {
        return left + PART_X + index * (PART_SLOT + PART_GAP);
    }

    static int partSlotY(int top) {
        return top + PART_Y;
    }

    static int buttonX(int left) {
        return left + BTN_X;
    }

    static int buttonY(int top, int index) {
        return top + BTN_Y + index * (BTN_SLOT + BTN_GAP);
    }

    static int buttonHit(int left, int top, double mouseX, double mouseY) {
        for (int i = 0; i < 3; i++) {
            int x = buttonX(left);
            int y = buttonY(top, i);
            if (mouseX >= x && mouseX < x + BTN_SLOT && mouseY >= y && mouseY < y + BTN_SLOT) {
                return i;
            }
        }
        return -1;
    }

    static int partHit(int left, int top, double mouseX, double mouseY) {
        for (int i = 0; i < 3; i++) {
            int x = partSlotX(left, i);
            int y = partSlotY(top);
            if (mouseX >= x && mouseX < x + PART_SLOT && mouseY >= y && mouseY < y + PART_SLOT) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Atlas column = button id; row = state.
     * States: disabled, idle, hover, active, active+hover.
     */
    static void blitButton(GuiGraphics graphics, int left, int top, int button, int state) {
        int x = buttonX(left);
        int y = buttonY(top, button);
        float u = button * BTN_ATLAS;
        float v = state * BTN_ATLAS;
        // Scale the 32×32 atlas cell into the painted 20×20 well.
        graphics.blit(BUTTONS, x, y, BTN_SLOT, BTN_SLOT, u, v, BTN_ATLAS, BTN_ATLAS, BTN_ATLAS * 3, BTN_ATLAS * 5);
    }

    static int buttonState(boolean enabled, boolean active, boolean hover) {
        if (!enabled) {
            return STATE_DISABLED;
        }
        if (active && hover) {
            return STATE_ACTIVE_HOVER;
        }
        if (active) {
            return STATE_ACTIVE;
        }
        if (hover) {
            return STATE_HOVER;
        }
        return STATE_IDLE;
    }

    /** Vertical energy fill in the painted gauge well. */
    static void drawEnergyGauge(GuiGraphics graphics, int left, int top, int stored, int capacity) {
        int x = left + GAUGE_BAR_X;
        int y = top + GAUGE_BAR_Y;
        int w = GAUGE_BAR_W;
        int h = GAUGE_BAR_H;
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF0A1018);
        if (capacity <= 0) {
            return;
        }
        float fill = Math.min(1.0F, Math.max(0.0F, stored / (float) capacity));
        int filled = Math.max(0, Math.round((h - 4) * fill));
        if (filled <= 0) {
            return;
        }
        int color = fill < 0.2F ? 0xFFE05050 : fill < 0.5F ? 0xFFE0B040 : 0xFF3CFE9A;
        graphics.fill(x + 3, y + h - 2 - filled, x + w - 3, y + h - 2, color);
    }
}
