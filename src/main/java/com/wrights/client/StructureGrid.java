package com.wrights.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class StructureGrid {
    public static final int INLINE = 10;

    private int originX;
    private int originY;
    private int cell = 8;
    private int fromX;
    private int fromZ;
    private int spanX = 1;
    private int spanZ = 1;
    private int sizedX = -1;
    private int sizedZ = -1;

    public void layout(int x, int y, int pixelSize, int sizeX, int sizeZ, int focusX, int focusZ, int window) {
        spanX = Math.max(1, Math.min(Math.max(1, window), Math.max(1, sizeX)));
        spanZ = Math.max(1, Math.min(Math.max(1, window), Math.max(1, sizeZ)));
        if (sizedX != sizeX || sizedZ != sizeZ) {
            fromX = origin(focusX, sizeX, spanX);
            fromZ = origin(focusZ, sizeZ, spanZ);
            sizedX = sizeX;
            sizedZ = sizeZ;
        } else {
            fromX = Math.max(0, Math.min(Math.max(0, sizeX - spanX), fromX));
            fromZ = Math.max(0, Math.min(Math.max(0, sizeZ - spanZ), fromZ));
        }
        int span = Math.max(spanX, spanZ);
        cell = Math.max(1, Math.min(16, Math.max(1, pixelSize) / span));
        int gridW = spanX * cell;
        int gridH = spanZ * cell;
        originX = x + Math.max(0, (pixelSize - gridW) / 2);
        originY = y + Math.max(0, (pixelSize - gridH) / 2);
    }

    public void centerOn(int x, int z, int sizeX, int sizeZ) {
        fromX = origin(x, sizeX, spanX);
        fromZ = origin(z, sizeZ, spanZ);
    }

    public void ensureVisible(int x, int z, int sizeX, int sizeZ) {
        if (x < 0 || z < 0) {
            return;
        }
        if (x < fromX) {
            fromX = x;
        } else if (x >= fromX + spanX) {
            fromX = x - spanX + 1;
        }
        if (z < fromZ) {
            fromZ = z;
        } else if (z >= fromZ + spanZ) {
            fromZ = z - spanZ + 1;
        }
        fromX = Math.max(0, Math.min(Math.max(0, sizeX - spanX), fromX));
        fromZ = Math.max(0, Math.min(Math.max(0, sizeZ - spanZ), fromZ));
    }

    public void pan(int x, int z, int sizeX, int sizeZ) {
        fromX = Math.max(0, Math.min(Math.max(0, sizeX - spanX), x));
        fromZ = Math.max(0, Math.min(Math.max(0, sizeZ - spanZ), z));
    }

    public int fromX() {
        return fromX;
    }

    public int fromZ() {
        return fromZ;
    }

    public int spanX() {
        return spanX;
    }

    public int spanZ() {
        return spanZ;
    }

    public static int origin(int focus, int size, int span) {
        if (size <= span) {
            return 0;
        }
        int start = focus - span / 2;
        return Math.max(0, Math.min(size - span, start));
    }

    public static boolean oversized(int sizeX, int sizeZ) {
        return sizeX > INLINE || sizeZ > INLINE;
    }

    public void render(GuiGraphics graphics, List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeZ, int layer, int anchorX, int anchorY, int anchorZ, int selectedX, int selectedY, int selectedZ) {
        int width = spanX * cell;
        int height = spanZ * cell;
        graphics.fill(originX - 4, originY - 4, originX + width + 4, originY + height + 4, 0xFF0C141C);
        WrightsUi.border(graphics, originX - 4, originY - 4, width + 8, height + 8, 0xFF31404E);
        boolean[] filled = new boolean[Math.max(1, spanX * spanZ)];
        if (ghosts != null) {
            for (WrightsClientState.Ghost ghost : ghosts) {
                if (ghost.y() != layer || !inside(ghost.x(), ghost.z(), sizeX, sizeZ)) {
                    continue;
                }
                int localX = ghost.x() - fromX;
                int localZ = ghost.z() - fromZ;
                filled[localX + localZ * spanX] = true;
                int cellX = originX + localX * cell;
                int cellY = originY + localZ * cell;
                BlockState state = Block.stateById(ghost.stateId());
                ItemStack stack = new ItemStack(state.getBlock());
                if (!stack.isEmpty()) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(cellX, cellY, 50);
                    float scale = blockSize() / 16.0F;
                    pose.scale(scale, scale, 1.0F);
                    graphics.renderItem(stack, 0, 0);
                    pose.popPose();
                }
            }
        }
        markCell(graphics, filled, anchorX, anchorZ, anchorY == layer ? WrightsUi.BLUE : 0xFF3A5A8A, 0x667EB6FF);
        boolean selectedHere = selectedX >= 0 && selectedY == layer;
        boolean same = selectedHere && selectedX == anchorX && selectedY == anchorY && selectedZ == anchorZ;
        if (selectedHere && !same) {
            markCell(graphics, filled, selectedX, selectedZ, 0xFFF2D48A, 0x66F0C420);
        } else if (same && inside(anchorX, anchorZ, sizeX, sizeZ)) {
            int cellX = originX + (anchorX - fromX) * cell;
            int cellY = originY + (anchorZ - fromZ) * cell;
            int inset = cell <= 6 ? 1 : Math.max(1, blockSize() / 5);
            int inner = Math.max(1, blockSize() - inset * 2);
            if (cell <= 6) {
                markCorners(graphics, cellX + inset, cellY + inset, inner, 0xFFF2D48A);
            } else {
                WrightsUi.border(graphics, cellX + inset, cellY + inset, inner, inner, 0xFFF2D48A);
            }
        }
        var font = Minecraft.getInstance().font;
        int north = originX + width / 2;
        int middle = originY + height / 2 - 4;
        graphics.drawCenteredString(font, "N", north, originY - 12, WrightsUi.TEXT);
        graphics.drawCenteredString(font, "S", north, originY + height + 3, WrightsUi.TEXT);
        graphics.drawString(font, "W", originX - 10, middle, WrightsUi.TEXT, false);
        graphics.drawString(font, "E", originX + width + 4, middle, WrightsUi.TEXT, false);
    }

    private boolean inside(int x, int z, int sizeX, int sizeZ) {
        return x >= fromX && z >= fromZ && x < fromX + spanX && z < fromZ + spanZ && x < sizeX && z < sizeZ && x >= 0 && z >= 0;
    }

    private void markCell(GuiGraphics graphics, boolean[] filled, int x, int z, int border, int empty) {
        if (x < fromX || z < fromZ || x >= fromX + spanX || z >= fromZ + spanZ) {
            return;
        }
        int localX = x - fromX;
        int localZ = z - fromZ;
        int cellX = originX + localX * cell;
        int cellY = originY + localZ * cell;
        int block = blockSize();
        if (cell <= 6) {
            markCorners(graphics, cellX, cellY, block, border);
        } else {
            WrightsUi.border(graphics, cellX, cellY, block, block, border);
        }
        int index = localX + localZ * spanX;
        if (index >= 0 && index < filled.length && !filled[index]) {
            graphics.fill(cellX, cellY, cellX + block, cellY + block, empty);
        }
    }

    private int blockSize() {
        return cell <= 6 ? Math.max(1, cell - 1) : cell;
    }

    private void markCorners(GuiGraphics graphics, int x, int y, int size, int color) {
        int arm = 1;
        int end = x + size;
        int bottom = y + size;
        graphics.fill(x, y, x + arm, y + arm, color);
        graphics.fill(end - arm, y, end, y + arm, color);
        graphics.fill(x, bottom - arm, x + arm, bottom, color);
        graphics.fill(end - arm, bottom - arm, end, bottom, color);
    }

    public int[] hit(double mouseX, double mouseY, int sizeX, int sizeZ, int layer) {
        if (cell <= 0) {
            return null;
        }
        int localX = fromX + (int) ((mouseX - originX) / cell);
        int localZ = fromZ + (int) ((mouseY - originY) / cell);
        if (!inside(localX, localZ, sizeX, sizeZ)) {
            return null;
        }
        return new int[] {localX, layer, localZ};
    }
}
