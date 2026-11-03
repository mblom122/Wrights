package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;
import com.wrights.hangar.HangarLayout;

/**
 * Textured hangar panel (wrightport + Manage). Blits the high-res
 * {@link HangarLayout#TEX_W}×{@link HangarLayout#TEX_H} art into the smaller on-screen
 * {@link HangarLayout#WIDTH}×{@link HangarLayout#HEIGHT} panel (layout coords × {@link HangarLayout#SCALE}).
 */
final class HangarGui {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/gui/hangar.png");
    static final ResourceLocation BUTTONS = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/gui/hangar_buttons.png");

    static final int TEX_W = HangarLayout.TEX_W;
    static final int TEX_H = HangarLayout.TEX_H;
    static final float SCALE = HangarLayout.SCALE;

    static final int WIDTH = HangarLayout.WIDTH;
    static final int HEIGHT = HangarLayout.HEIGHT;
    static final int CHROME = HangarLayout.CHROME;
    static final int PANEL_H = HangarLayout.PANEL_H;
    static final int INV_W = HangarLayout.INV_W;
    static final int INV_H = HangarLayout.INV_H;
    static final int INV_GAP = HangarLayout.INV_GAP;
    static final int SCREEN_H = HangarLayout.SCREEN_H;

    static final int BAY_X = HangarLayout.sx(174);
    static final int BAY_Y = HangarLayout.sx(207);
    static final int BAY_SLOT = HangarLayout.sx(28);
    static final int BAY_GAP = Math.max(1, HangarLayout.sx(6));

    static final int PART_X = HangarLayout.sx(147);
    static final int PART_Y = HangarLayout.sx(122);
    static final int PART_SLOT = HangarLayout.sx(34);
    static final int PART_GAP = HangarLayout.sx(57);

    /** Defaults — live values come from {@link #EDIT}. */
    static final int BTN_X = HangarLayout.sx(457);
    static final int BTN_Y = HangarLayout.sx(50);
    static final int BTN_SLOT = HangarLayout.sx(30);
    static final int BTN_GAP = HangarLayout.sx(8);
    static final int BTN_ATLAS = 32;

    static final int GAUGE_BAR_X = HangarLayout.sx(18);
    static final int GAUGE_BAR_Y = HangarLayout.sx(48);
    static final int GAUGE_BAR_W = Math.max(8, HangarLayout.sx(12));
    static final int GAUGE_BAR_H = HangarLayout.sx(90);

    static final int ENERGY_LINE_X = HangarLayout.sx(18);
    static final int ENERGY_LINE_Y = HangarLayout.sx(148);

    /** Live layout tweaks from the in-GUI editor (Copy → clipboard). After defaults above. */
    static final HangarLayoutEdit EDIT = new HangarLayoutEdit();

    static int btnX() {
        return EDIT.btnX;
    }

    static int btnY() {
        return EDIT.btnY;
    }

    static int btnSlot() {
        return EDIT.btnSlot;
    }

    static int btnGap() {
        return EDIT.btnGap;
    }

    static int gaugeX() {
        return EDIT.gaugeX;
    }

    static int gaugeY() {
        return EDIT.gaugeY;
    }

    static int gaugeW() {
        return EDIT.gaugeW;
    }

    static int gaugeH() {
        return EDIT.gaugeH;
    }

    static int invSlotLeftLive() {
        return EDIT.invSlotLeft;
    }

    static int invSlotTopLive() {
        return EDIT.invSlotTop;
    }

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

    static int invSlotLeft() {
        return invSlotLeftLive();
    }

    static int invSlotTop() {
        return invSlotTopLive();
    }

    static void blitPanel(GuiGraphics graphics, int left, int top) {
        graphics.blit(TEXTURE, left, top, WIDTH, HEIGHT, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    static void drawInventoryPanel(GuiGraphics graphics, int left, int top) {
        int sx0 = left + invSlotLeft();
        int sy0 = top + invSlotTop();
        int panelX = sx0 - 8;
        int panelY = sy0 - 8;
        WrightsUi.panel(graphics, panelX, panelY, INV_W, INV_H);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                WrightsUi.slot(graphics, sx0 + col * 18, sy0 + row * 18, WrightsUi.MUTED & 0xFFFFFF | 0xFF000000);
            }
        }
        int hotY = sy0 + 58;
        for (int col = 0; col < 9; col++) {
            WrightsUi.slot(graphics, sx0 + col * 18, hotY, WrightsUi.MUTED & 0xFFFFFF | 0xFF000000);
        }
    }

    static int partSlotX(int left, int index) {
        return left + PART_X + index * (PART_SLOT + PART_GAP);
    }

    static int partSlotY(int top) {
        return top + PART_Y;
    }

    static int buttonX(int left) {
        return left + btnX();
    }

    static int buttonY(int top, int index) {
        return top + btnY() + index * (btnSlot() + btnGap());
    }

    static int buttonHit(int left, int top, double mouseX, double mouseY) {
        int size = btnSlot();
        for (int i = 0; i < 3; i++) {
            int x = buttonX(left);
            int y = buttonY(top, i);
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
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

    static void blitButton(GuiGraphics graphics, int left, int top, int button, int state) {
        int x = buttonX(left);
        int y = buttonY(top, button);
        int size = btnSlot();
        float u = button * BTN_ATLAS;
        float v = state * BTN_ATLAS;
        graphics.blit(BUTTONS, x, y, size, size, u, v, BTN_ATLAS, BTN_ATLAS, BTN_ATLAS * 3, BTN_ATLAS * 5);
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

    static void drawEnergyGauge(GuiGraphics graphics, int left, int top, int stored, int capacity) {
        int x = left + gaugeX();
        int y = top + gaugeY();
        int w = gaugeW();
        int h = gaugeH();
        graphics.fill(x, y, x + w, y + h, 0xFF0A1018);
        WrightsUi.border(graphics, x, y, w, h, 0xFF3D4E5E);
        if (capacity <= 0) {
            return;
        }
        float fill = Math.min(1.0F, Math.max(0.0F, stored / (float) capacity));
        int filled = Math.max(0, Math.round((h - 4) * fill));
        if (filled <= 0) {
            return;
        }
        int color = fill < 0.2F ? 0xFFE05050 : fill < 0.5F ? 0xFFE0B040 : 0xFF3CFE9A;
        graphics.fill(x + 2, y + h - 2 - filled, x + w - 2, y + h - 2, color);
    }

    /** Tip strip under the hangar art (replaces Add / Remove). */
    static void drawTipStrip(GuiGraphics graphics, net.minecraft.client.gui.Font font, int left, int top, net.minecraft.network.chat.Component tip) {
        int x = left + 8;
        int y = top + HEIGHT + 4;
        int w = WIDTH - 16;
        if (tip == null || tip.getString().isEmpty()) {
            return;
        }
        String text = font.plainSubstrByWidth(tip.getString(), Math.max(8, w));
        graphics.drawString(font, text, x, y, WrightsUi.TEXT, false);
    }
}
