package com.wrights.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.wrights.drone.Swarm;

/**
 * Fixed 5×2 hangar grid. Classic Manage uses large drawn slots; the textured hangar uses
 * {@link HangarGui} cell size over painted frames.
 */
final class HangarBay {
    static final int COLS = 5;
    static final int ROWS = 2;
    static final int SLOTS = Swarm.HANGAR_SLOTS;
    /** Twice a vanilla 18px inventory slot (classic Manage). */
    static final int SLOT = 36;
    static final int GAP = 4;

    static {
        if (SLOTS != COLS * ROWS) {
            throw new IllegalStateException("Hangar bay must be 5×2");
        }
    }

    private HangarBay() {
    }

    static int width() {
        return width(SLOT, GAP);
    }

    static int height() {
        return height(SLOT, GAP);
    }

    static int width(int slot, int gap) {
        return COLS * slot + (COLS - 1) * gap;
    }

    static int height(int slot, int gap) {
        return ROWS * slot + (ROWS - 1) * gap;
    }

    static int slotX(int originX, int index) {
        return slotX(originX, index, SLOT, GAP);
    }

    static int slotY(int originY, int index) {
        return slotY(originY, index, SLOT, GAP);
    }

    static int slotX(int originX, int index, int slot, int gapX) {
        return originX + (index % COLS) * (slot + gapX);
    }

    static int slotY(int originY, int index, int slot, int gapY) {
        return originY + (index / COLS) * (slot + gapY);
    }

    /**
     * Textured hangar bay X: uniform grid plus a left-nudge that lerps from 1px (col 0)
     * to 2px (col 4), so middle columns sit between the corner offsets.
     */
    static int hangarSlotX(int originX, int index, int slot, int gapX) {
        int col = index % COLS;
        int dx = -Math.round(1.0F + col * (1.0F / (COLS - 1)));
        return slotX(originX, index, slot, gapX) + dx;
    }

    /**
     * Textured hangar bay Y: uniform grid plus a down-nudge that lerps from 2px (row 0)
     * to 1px (row 1).
     */
    static int hangarSlotY(int originY, int index, int slot, int gapY) {
        int row = index / COLS;
        int dy = ROWS == 1 ? 2 : Math.round(2.0F - row * (1.0F / (ROWS - 1)));
        return slotY(originY, index, slot, gapY) + dy;
    }

    /** Hangar index under the cursor, or -1 (uniform classic Manage grid). */
    static int hit(int originX, int originY, double mouseX, double mouseY) {
        return hit(originX, originY, mouseX, mouseY, SLOT, GAP, GAP, false);
    }

    static int hit(int originX, int originY, double mouseX, double mouseY, int slot, int gap) {
        return hit(originX, originY, mouseX, mouseY, slot, gap, gap, false);
    }

    static int hit(int originX, int originY, double mouseX, double mouseY, int slot, int gapX, int gapY) {
        return hit(originX, originY, mouseX, mouseY, slot, gapX, gapY, true);
    }

    static int hit(int originX, int originY, double mouseX, double mouseY, int slot, int gapX, int gapY,
            boolean texturedHangar) {
        for (int i = 0; i < SLOTS; i++) {
            int x = texturedHangar ? hangarSlotX(originX, i, slot, gapX) : slotX(originX, i, slot, gapX);
            int y = texturedHangar ? hangarSlotY(originY, i, slot, gapY) : slotY(originY, i, slot, gapY);
            if (mouseX >= x && mouseX < x + slot && mouseY >= y && mouseY < y + slot) {
                return i;
            }
        }
        return -1;
    }

    static void drawSlot(GuiGraphics graphics, Font font, int x, int y, ItemStack stack, boolean marked,
            boolean selected, boolean locked, boolean hovered) {
        drawSlot(graphics, font, x, y, SLOT, stack, marked, selected, locked, hovered, true);
    }

    /**
     * @param drawFrame when false, only the item / mark / selection sit on a painted texture slot
     */
    static void drawSlot(GuiGraphics graphics, Font font, int x, int y, int size, ItemStack stack, boolean marked,
            boolean selected, boolean locked, boolean hovered, boolean drawFrame) {
        if (drawFrame) {
            int edge = selected ? WrightsUi.COPPER : hovered ? WrightsUi.BLUE : WrightsUi.MUTED;
            WrightsUi.slot(graphics, x, y, size, edge & 0xFFFFFF | 0xFF000000);
        } else if (selected) {
            graphics.fill(x, y, x + size, y + 1, 0xFFC47A3A);
            graphics.fill(x, y + size - 1, x + size, y + size, 0xFFC47A3A);
            graphics.fill(x, y, x + 1, y + size, 0xFFC47A3A);
            graphics.fill(x + size - 1, y, x + size, y + size, 0xFFC47A3A);
        }
        if (marked) {
            graphics.fill(x + 2, y + 2, x + 8, y + 8, 0xFF3CFE9A);
            graphics.fill(x + 3, y + 3, x + 7, y + 7, 0xFF0C141C);
            graphics.fill(x + 4, y + 4, x + 6, y + 6, 0xFF3CFE9A);
        }
        if (!stack.isEmpty()) {
            WrightsUi.slotItem(graphics, font, stack, x, y, size);
            if (locked) {
                graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x88060C14);
            }
        }
        if (hovered) {
            hangarHoverTint(graphics, x, y, size);
        }
        if (selected && drawFrame) {
            graphics.fill(x, y, x + size, y + 1, 0xFFC47A3A);
            graphics.fill(x, y + size - 1, x + size, y + size, 0xFFC47A3A);
            graphics.fill(x, y, x + 1, y + size, 0xFFC47A3A);
            graphics.fill(x + size - 1, y, x + size, y + size, 0xFFC47A3A);
        }
    }

    /** Vanilla-style white slot highlight, inset so the painted border stays visible. */
    static void hangarHoverTint(GuiGraphics graphics, int x, int y, int size) {
        if (size <= 2) {
            return;
        }
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x80FFFFFF);
    }
}
