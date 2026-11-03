package com.wrights.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.wrights.hangar.HangarLayout;
import com.wrights.menu.WrightportMenu;

/**
 * In-GUI hangar layout tweaker: toggle top-left, drag/resize regions, copy texture-space
 * constants to the clipboard for pasting back into chat / HangarGui.
 */
final class HangarLayoutEdit {
    enum Region {
        NONE,
        BUTTONS,
        ENERGY,
        INV
    }

    boolean editing;
    Region selected = Region.NONE;

    int btnX;
    int btnY;
    int btnSlot;
    int btnGap;

    int gaugeX;
    int gaugeY;
    int gaugeW;
    int gaugeH;

    /** First player-inventory slot, relative to panel left/top (same as Slot.x / Slot.y). */
    int invSlotLeft;
    int invSlotTop;

    HangarLayoutEdit() {
        reset();
    }

    private boolean dragging;
    private boolean resizing;
    private int grabX;
    private int grabY;
    private int originX;
    private int originY;
    private int originW;
    private int originH;

    private static final int TOGGLE_W = 36;
    private static final int TOGGLE_H = 12;
    private static final int HANDLE = 6;

    void reset() {
        btnX = HangarLayout.sx(463);
        btnY = HangarLayout.sx(60);
        btnSlot = HangarLayout.sx(31);
        btnGap = HangarLayout.sx(8);
        gaugeX = HangarLayout.sx(36);
        gaugeY = HangarLayout.sx(92);
        gaugeW = Math.max(1, HangarLayout.sx(12));
        gaugeH = HangarLayout.sx(91);
        invSlotLeft = HangarLayout.invSlotLeft();
        invSlotTop = HangarLayout.invSlotTop();
        selected = Region.NONE;
        dragging = false;
        resizing = false;
    }

    int toggleX(int left) {
        return left + 4;
    }

    int toggleY(int top) {
        return top + 4;
    }

    int copyX(int left) {
        return left + 4 + TOGGLE_W + 4;
    }

    int resetX(int left) {
        return left + 4 + TOGGLE_W + 4 + TOGGLE_W + 4;
    }

    boolean hitToggle(int left, int top, double mx, double my) {
        return hit(toggleX(left), toggleY(top), TOGGLE_W, TOGGLE_H, mx, my);
    }

    boolean hitCopy(int left, int top, double mx, double my) {
        return editing && hit(copyX(left), toggleY(top), TOGGLE_W, TOGGLE_H, mx, my);
    }

    boolean hitReset(int left, int top, double mx, double my) {
        return editing && hit(resetX(left), toggleY(top), TOGGLE_W, TOGGLE_H, mx, my);
    }

    void drawChrome(GuiGraphics graphics, Font font, int left, int top) {
        drawChip(graphics, font, toggleX(left), toggleY(top), editing ? "Done" : "Edit", editing);
        if (editing) {
            drawChip(graphics, font, copyX(left), toggleY(top), "Copy", false);
            drawChip(graphics, font, resetX(left), toggleY(top), "Reset", false);
            String sel = selected == Region.NONE ? "drag a box" : selected.name().toLowerCase();
            graphics.drawString(font, "Layout: " + sel + " · drag move · corner resize", left + 4, top + 18, WrightsUi.MUTED, false);
        }
    }

    void drawOverlays(GuiGraphics graphics, int left, int top) {
        if (!editing) {
            return;
        }
        outline(graphics, left + btnX, top + btnY, btnSlot, btnStackH(), selected == Region.BUTTONS);
        outline(graphics, left + gaugeX, top + gaugeY, gaugeW, gaugeH, selected == Region.ENERGY);
        outline(graphics, left + invSlotLeft - 1, top + invSlotTop - 1, 9 * 18 + 2, 3 * 18 + 14 + 18 + 2, selected == Region.INV);
        if (selected == Region.BUTTONS) {
            handle(graphics, left + btnX + btnSlot - HANDLE, top + btnY + btnStackH() - HANDLE);
        } else if (selected == Region.ENERGY) {
            handle(graphics, left + gaugeX + gaugeW - HANDLE, top + gaugeY + gaugeH - HANDLE);
        }
    }

    private int btnStackH() {
        return 3 * btnSlot + 2 * btnGap;
    }

    /**
     * Slot.x/y are final in 1.21 — inventory click hitboxes stay at open-time positions.
     * The drawn wells follow {@link #invSlotLeft}/{@link #invSlotTop}; Copy the values and
     * we bake them into code so clicks match on the next open.
     */
    void syncMenuSlots(WrightportMenu menu) {
        // visual-only while editing
    }

    boolean mouseClicked(int left, int top, double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (hitToggle(left, top, mx, my)) {
            editing = !editing;
            selected = Region.NONE;
            dragging = false;
            resizing = false;
            return true;
        }
        if (hitCopy(left, top, mx, my)) {
            copyToClipboard();
            return true;
        }
        if (hitReset(left, top, mx, my)) {
            reset();
            return true;
        }
        if (!editing) {
            return false;
        }
        // Prefer resize handle of current selection.
        if (selected == Region.BUTTONS && hitHandle(left + btnX + btnSlot, top + btnY + btnStackH(), mx, my)) {
            resizing = true;
            grabX = (int) mx;
            grabY = (int) my;
            originW = btnSlot;
            originH = btnStackH();
            return true;
        }
        if (selected == Region.ENERGY && hitHandle(left + gaugeX + gaugeW, top + gaugeY + gaugeH, mx, my)) {
            resizing = true;
            grabX = (int) mx;
            grabY = (int) my;
            originW = gaugeW;
            originH = gaugeH;
            return true;
        }
        Region hit = hitRegion(left, top, mx, my);
        if (hit != Region.NONE) {
            selected = hit;
            dragging = true;
            grabX = (int) mx;
            grabY = (int) my;
            if (hit == Region.BUTTONS) {
                originX = btnX;
                originY = btnY;
            } else if (hit == Region.ENERGY) {
                originX = gaugeX;
                originY = gaugeY;
            } else {
                originX = invSlotLeft;
                originY = invSlotTop;
            }
            return true;
        }
        selected = Region.NONE;
        return true; // swallow clicks while editing so tools don't fire
    }

    boolean mouseDragged(int left, int top, double mx, double my) {
        if (!editing) {
            return false;
        }
        if (resizing) {
            int dx = (int) mx - grabX;
            int dy = (int) my - grabY;
            if (selected == Region.BUTTONS) {
                btnSlot = Mth.clamp(originW + Math.max(dx, dy) / 1, 10, 48);
            } else if (selected == Region.ENERGY) {
                gaugeW = Mth.clamp(originW + dx, 6, 40);
                gaugeH = Mth.clamp(originH + dy, 20, 160);
            }
            return true;
        }
        if (dragging) {
            int dx = (int) mx - grabX;
            int dy = (int) my - grabY;
            if (selected == Region.BUTTONS) {
                btnX = Mth.clamp(originX + dx, 0, HangarGui.WIDTH - btnSlot);
                btnY = Mth.clamp(originY + dy, 0, HangarGui.HEIGHT - btnStackH());
            } else if (selected == Region.ENERGY) {
                gaugeX = Mth.clamp(originX + dx, 0, HangarGui.WIDTH - gaugeW);
                gaugeY = Mth.clamp(originY + dy, 0, HangarGui.HEIGHT - gaugeH);
            } else if (selected == Region.INV) {
                invSlotLeft = Mth.clamp(originX + dx, 0, HangarGui.WIDTH - 9 * 18);
                invSlotTop = Mth.clamp(originY + dy, HangarGui.HEIGHT, HangarGui.SCREEN_H - 76);
            }
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        if (!editing) {
            return false;
        }
        boolean used = dragging || resizing;
        dragging = false;
        resizing = false;
        return used;
    }

    void copyToClipboard() {
        float s = HangarLayout.SCALE;
        String text = String.join("\n",
                "hangar_layout (texture px @ scale " + s + " — paste to agent)",
                "BTN_X=" + tex(btnX) + " BTN_Y=" + tex(btnY) + " BTN_SLOT=" + tex(btnSlot) + " BTN_GAP=" + tex(btnGap),
                "GAUGE_X=" + tex(gaugeX) + " GAUGE_Y=" + tex(gaugeY) + " GAUGE_W=" + tex(gaugeW) + " GAUGE_H=" + tex(gaugeH),
                "INV_SLOT_LEFT_SCREEN=" + invSlotLeft + " INV_SLOT_TOP_SCREEN=" + invSlotTop,
                "INV_SLOT_LEFT_TEX=" + tex(invSlotLeft) + " INV_SLOT_TOP_TEX=" + tex(invSlotTop),
                "// screen px: btn=" + btnX + "," + btnY + " slot=" + btnSlot + " gap=" + btnGap
                        + " gauge=" + gaugeX + "," + gaugeY + " " + gaugeW + "x" + gaugeH
                        + " inv=" + invSlotLeft + "," + invSlotTop);
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("Hangar layout copied to clipboard"), true);
        }
    }

    private int tex(int screenPx) {
        return Math.round(screenPx / HangarLayout.SCALE);
    }

    private Region hitRegion(int left, int top, double mx, double my) {
        if (hit(left + btnX, top + btnY, btnSlot, btnStackH(), mx, my)) {
            return Region.BUTTONS;
        }
        if (hit(left + gaugeX, top + gaugeY, gaugeW, gaugeH, mx, my)) {
            return Region.ENERGY;
        }
        if (hit(left + invSlotLeft, top + invSlotTop, 9 * 18, 3 * 18 + 14 + 18, mx, my)) {
            return Region.INV;
        }
        return Region.NONE;
    }

    private static boolean hit(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean hitHandle(int right, int bottom, double mx, double my) {
        return mx >= right - HANDLE && mx < right + 2 && my >= bottom - HANDLE && my < bottom + 2;
    }

    private static void drawChip(GuiGraphics graphics, Font font, int x, int y, String label, boolean on) {
        int w = TOGGLE_W;
        int h = TOGGLE_H;
        graphics.fill(x, y, x + w, y + h, on ? 0xFF2A4054 : 0xFF1A2633);
        WrightsUi.border(graphics, x, y, w, h, on ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(font, label, x + w / 2, y + 2, WrightsUi.TEXT);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, boolean selected) {
        int color = selected ? 0xFF3CFE9A : 0x88E0B060;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void handle(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + HANDLE, y + HANDLE, 0xFF3CFE9A);
    }
}
