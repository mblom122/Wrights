package com.wrights.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.wrights.drone.Swarm;

/**
 * Fixed 5×2 hangar grid. Slots are twice a normal inventory cell; each holds one wright.
 */
final class HangarBay {
    static final int COLS = 5;
    static final int ROWS = 2;
    static final int SLOTS = Swarm.HANGAR_SLOTS;
    /** Twice a vanilla 18px inventory slot. */
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
        return COLS * SLOT + (COLS - 1) * GAP;
    }

    static int height() {
        return ROWS * SLOT + (ROWS - 1) * GAP;
    }

    static int slotX(int originX, int index) {
        return originX + (index % COLS) * (SLOT + GAP);
    }

    static int slotY(int originY, int index) {
        return originY + (index / COLS) * (SLOT + GAP);
    }

    /** Hangar index under the cursor, or -1. */
    static int hit(int originX, int originY, double mouseX, double mouseY) {
        for (int i = 0; i < SLOTS; i++) {
            int x = slotX(originX, i);
            int y = slotY(originY, i);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                return i;
            }
        }
        return -1;
    }

    static void drawSlot(GuiGraphics graphics, Font font, int x, int y, ItemStack stack, boolean marked,
            boolean selected, boolean locked, boolean hovered) {
        int edge = selected ? WrightsUi.COPPER : hovered ? WrightsUi.BLUE : WrightsUi.MUTED;
        WrightsUi.slot(graphics, x, y, SLOT, edge & 0xFFFFFF | 0xFF000000);
        if (marked) {
            graphics.fill(x + 2, y + 2, x + 8, y + 8, 0xFF3CFE9A);
            graphics.fill(x + 3, y + 3, x + 7, y + 7, 0xFF0C141C);
            graphics.fill(x + 4, y + 4, x + 6, y + 6, 0xFF3CFE9A);
        }
        if (!stack.isEmpty()) {
            WrightsUi.slotItem(graphics, font, stack, x, y, SLOT);
            if (locked) {
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x88060C14);
            }
        }
        if (selected) {
            graphics.fill(x, y, x + SLOT, y + 1, 0xFFC47A3A);
            graphics.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, 0xFFC47A3A);
            graphics.fill(x, y, x + 1, y + SLOT, 0xFFC47A3A);
            graphics.fill(x + SLOT - 1, y, x + SLOT, y + SLOT, 0xFFC47A3A);
        }
    }
}
