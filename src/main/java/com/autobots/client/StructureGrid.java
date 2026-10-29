package com.autobots.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class StructureGrid {
    private int originX;
    private int originY;
    private int cell = 4;

    public void layout(int x, int y, int pixelSize, int sizeX, int sizeZ) {
        int span = Math.max(1, Math.max(sizeX, sizeZ));
        cell = Math.max(4, Math.min(16, pixelSize / span));
        int gridW = Math.max(1, sizeX) * cell;
        int gridH = Math.max(1, sizeZ) * cell;
        originX = x + Math.max(0, (pixelSize - gridW) / 2);
        originY = y + Math.max(0, (pixelSize - gridH) / 2);
    }

    public void render(GuiGraphics graphics, List<AutobotsClientState.Ghost> ghosts, int sizeX, int sizeZ, int layer, int anchorX, int anchorY, int anchorZ, int selectedX, int selectedY, int selectedZ) {
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
                int cellX = originX + ghost.x() * cell;
                int cellY = originY + ghost.z() * cell;
                BlockState state = Block.stateById(ghost.stateId());
                ItemStack stack = new ItemStack(state.getBlock());
                if (!stack.isEmpty()) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(cellX, cellY, 50);
                    float scale = Math.max(0.25F, (cell - 1) / 16.0F);
                    pose.scale(scale, scale, 1.0F);
                    graphics.renderItem(stack, 0, 0);
                    pose.popPose();
                }
            }
        }
        markCell(graphics, filled, sizeX, anchorX, anchorZ, anchorY == layer ? AutobotsUi.BLUE : 0xFF3A5A8A, 0x667EB6FF);
        boolean selectedHere = selectedX >= 0 && selectedY == layer;
        boolean same = selectedHere && selectedX == anchorX && selectedY == anchorY && selectedZ == anchorZ;
        if (selectedHere && !same) {
            markCell(graphics, filled, sizeX, selectedX, selectedZ, 0xFFF2D48A, 0x66F0C420);
        } else if (same) {
            int cellX = originX + anchorX * cell;
            int cellY = originY + anchorZ * cell;
            int inset = Math.max(1, cell / 4);
            AutobotsUi.border(graphics, cellX + inset, cellY + inset, Math.max(2, cell - inset * 2), Math.max(2, cell - inset * 2), 0xFFF2D48A);
        }
        var font = Minecraft.getInstance().font;
        int north = originX + width / 2;
        int middle = originY + height / 2 - 4;
        graphics.drawCenteredString(font, "N", north, originY - 12, AutobotsUi.TEXT);
        graphics.drawCenteredString(font, "S", north, originY + height + 3, AutobotsUi.TEXT);
        graphics.drawString(font, "W", originX - 10, middle, AutobotsUi.TEXT, false);
        graphics.drawString(font, "E", originX + width + 4, middle, AutobotsUi.TEXT, false);
    }

    private void markCell(GuiGraphics graphics, boolean[] filled, int sizeX, int x, int z, int border, int empty) {
        if (x < 0 || z < 0 || x >= sizeX || z >= filled.length / Math.max(1, sizeX)) {
            return;
        }
        int cellX = originX + x * cell;
        int cellY = originY + z * cell;
        AutobotsUi.border(graphics, cellX - 1, cellY - 1, cell + 1, cell + 1, border);
        if (!filled[x + z * sizeX]) {
            graphics.fill(cellX, cellY, cellX + Math.max(1, cell - 1), cellY + Math.max(1, cell - 1), empty);
        }
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
