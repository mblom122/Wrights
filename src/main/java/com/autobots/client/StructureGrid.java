package com.autobots.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class StructureGrid {
    private int originX;
    private int originY;
    private int cell = 4;

    public void layout(int x, int y, int pixelSize, int sizeX, int sizeZ) {
        int span = Math.max(1, Math.max(sizeX, sizeZ));
        cell = Math.max(4, Math.min(10, pixelSize / span));
        int gridW = Math.max(1, sizeX) * cell;
        int gridH = Math.max(1, sizeZ) * cell;
        originX = x + Math.max(0, (pixelSize - gridW) / 2);
        originY = y + Math.max(0, (pixelSize - gridH) / 2);
    }

    public void render(GuiGraphics graphics, List<AutobotsClientState.Ghost> ghosts, int sizeX, int sizeZ, int layer, int anchorX, int anchorY, int anchorZ) {
        int width = Math.max(1, sizeX) * cell;
        int height = Math.max(1, sizeZ) * cell;
        graphics.fill(originX - 4, originY - 4, originX + width + 4, originY + height + 4, 0xFF0C141C);
        AutobotsUi.border(graphics, originX - 4, originY - 4, width + 8, height + 8, 0xFF31404E);
        boolean[] filled = new boolean[Math.max(1, sizeX * sizeZ)];
        if (ghosts != null) {
            for (AutobotsClientState.Ghost ghost : ghosts) {
                if (ghost.y() != layer || ghost.x() < 0 || ghost.z() < 0 || ghost.x() >= sizeX || ghost.z() >= sizeZ) {
                    continue;
                }
                int index = ghost.x() + ghost.z() * sizeX;
                filled[index] = true;
                int color = 0xFF000000
                        | (Math.min(255, (int) (ghost.r() * 255)) << 16)
                        | (Math.min(255, (int) (ghost.g() * 255)) << 8)
                        | Math.min(255, (int) (ghost.b() * 255));
                int cellX = originX + ghost.x() * cell;
                int cellY = originY + ghost.z() * cell;
                graphics.fill(cellX, cellY, cellX + cell - 1, cellY + cell - 1, color);
            }
        }
        if (anchorX >= 0 && anchorZ >= 0 && anchorX < sizeX && anchorZ < sizeZ) {
            int cellX = originX + anchorX * cell;
            int cellY = originY + anchorZ * cell;
            int mark = anchorY == layer ? AutobotsUi.COPPER : 0xFF6E5430;
            AutobotsUi.border(graphics, cellX - 1, cellY - 1, cell + 1, cell + 1, mark);
            if (!filled[anchorX + anchorZ * sizeX]) {
                graphics.fill(cellX, cellY, cellX + Math.max(1, cell - 1), cellY + Math.max(1, cell - 1), 0x66C47A3A);
            }
        }
        var font = Minecraft.getInstance().font;
        int north = originX + width / 2;
        int middle = originY + height / 2 - 4;
        graphics.drawCenteredString(font, "N", north, originY - 12, AutobotsUi.TEXT);
        graphics.drawCenteredString(font, "S", north, originY + height + 3, AutobotsUi.TEXT);
        graphics.drawString(font, "W", originX - 10, middle, AutobotsUi.TEXT, false);
        graphics.drawString(font, "E", originX + width + 4, middle, AutobotsUi.TEXT, false);
    }

    public int[] hit(double mouseX, double mouseY, int sizeX, int sizeZ, int layer) {
        int localX = (int) ((mouseX - originX) / cell);
        int localZ = (int) ((mouseY - originY) / cell);
        if (localX < 0 || localZ < 0 || localX >= sizeX || localZ >= sizeZ) {
            return null;
        }
        return new int[] {localX, layer, localZ};
    }
}
