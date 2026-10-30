package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.blueprint.LayerAxis;
import com.wrights.util.BlockFilters;

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
    private int axis = LayerAxis.Y;
    private boolean flipV;

    public void setAxis(int axis) {
        int next = LayerAxis.clamp(axis);
        if (next != this.axis) {
            this.axis = next;
            sizedX = -1;
            sizedZ = -1;
        }
        flipV = LayerAxis.flipVertical(this.axis);
    }

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

    public void render(GuiGraphics graphics, List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, int layer, int axis, int anchorX, int anchorY, int anchorZ, int selectedX, int selectedY, int selectedZ) {
        setAxis(axis);
        int width = spanX * cell;
        int height = spanZ * cell;
        graphics.fill(originX - 4, originY - 4, originX + width + 4, originY + height + 4, 0xFF0C141C);
        WrightsUi.border(graphics, originX - 4, originY - 4, width + 8, height + 8, 0xFF31404E);
        boolean[] filled = new boolean[Math.max(1, spanX * spanZ)];
        if (ghosts != null) {
            int planeH = LayerAxis.horizontal(this.axis, sizeX, sizeY, sizeZ);
            int planeV = LayerAxis.vertical(this.axis, sizeX, sizeY, sizeZ);
            for (WrightsClientState.Ghost ghost : ghosts) {
                if (LayerAxis.of(ghost.x(), ghost.y(), ghost.z(), this.axis) != layer) {
                    continue;
                }
                int worldH = LayerAxis.horizontal(this.axis, ghost.x(), ghost.y(), ghost.z());
                int worldV = LayerAxis.vertical(this.axis, ghost.x(), ghost.y(), ghost.z());
                if (!inside(worldH, worldV, planeH, planeV)) {
                    continue;
                }
                int localX = worldH - fromX;
                int localZ = localV(worldV);
                filled[localX + localZ * spanX] = true;
                int cellX = originX + localX * cell;
                int cellY = originY + localZ * cell;
                BlockState state = Block.stateById(ghost.stateId());
                int drawn = blockSize();
                if (BlockFilters.isFluid(state)) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(0.0F, 0.0F, 50.0F);
                    FluidIcons.draw(graphics, state, cellX, cellY, drawn);
                    pose.popPose();
                } else {
                    ItemStack stack = BlockFilters.icon(state);
                    if (!stack.isEmpty()) {
                        var pose = graphics.pose();
                        pose.pushPose();
                        pose.translate(cellX, cellY, 50);
                        float scale = drawn / 16.0F;
                        pose.scale(scale, scale, 1.0F);
                        graphics.renderItem(stack, 0, 0);
                        pose.popPose();
                    }
                }
            }
        }
        int anchorH = LayerAxis.horizontal(this.axis, anchorX, anchorY, anchorZ);
        int anchorV = LayerAxis.vertical(this.axis, anchorX, anchorY, anchorZ);
        int planeH = LayerAxis.horizontal(this.axis, sizeX, sizeY, sizeZ);
        int planeV = LayerAxis.vertical(this.axis, sizeX, sizeY, sizeZ);
        markCell(graphics, filled, anchorH, anchorV, LayerAxis.of(anchorX, anchorY, anchorZ, this.axis) == layer ? WrightsUi.BLUE : 0xFF3A5A8A, 0x667EB6FF);
        boolean selectedHere = selectedX >= 0 && LayerAxis.of(selectedX, selectedY, selectedZ, this.axis) == layer;
        boolean same = selectedHere && selectedX == anchorX && selectedY == anchorY && selectedZ == anchorZ;
        if (selectedHere && !same) {
            markCell(graphics, filled, LayerAxis.horizontal(this.axis, selectedX, selectedY, selectedZ), LayerAxis.vertical(this.axis, selectedX, selectedY, selectedZ), 0xFFF2D48A, 0x66F0C420);
        } else if (same && inside(anchorH, anchorV, planeH, planeV)) {
            int cellX = originX + (anchorH - fromX) * cell;
            int cellY = originY + localV(anchorV) * cell;
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
        String top;
        String bottom;
        String left;
        String right;
        if (this.axis == LayerAxis.X) {
            top = "U";
            bottom = "D";
            left = "N";
            right = "S";
        } else if (this.axis == LayerAxis.Z) {
            top = "U";
            bottom = "D";
            left = "W";
            right = "E";
        } else {
            top = "N";
            bottom = "S";
            left = "W";
            right = "E";
        }
        graphics.drawCenteredString(font, top, north, originY - 12, WrightsUi.TEXT);
        graphics.drawCenteredString(font, bottom, north, originY + height + 3, WrightsUi.TEXT);
        graphics.drawString(font, left, originX - 10, middle, WrightsUi.TEXT, false);
        graphics.drawString(font, right, originX + width + 4, middle, WrightsUi.TEXT, false);
    }

    private int localV(int worldV) {
        if (flipV) {
            return fromZ + spanZ - 1 - worldV;
        }
        return worldV - fromZ;
    }

    private boolean inside(int x, int z, int sizeX, int sizeZ) {
        return x >= fromX && z >= fromZ && x < fromX + spanX && z < fromZ + spanZ && x < sizeX && z < sizeZ && x >= 0 && z >= 0;
    }

    private void markCell(GuiGraphics graphics, boolean[] filled, int x, int z, int border, int empty) {
        if (x < fromX || z < fromZ || x >= fromX + spanX || z >= fromZ + spanZ) {
            return;
        }
        int localX = x - fromX;
        int localZ = localV(z);
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

    public record Spot(int x, int y, int z, int pixelX, int pixelY, int size) {
    }

    /** Every visible cell, including empty ones, so a drop can place or replace. */
    public List<Spot> spots(int sizeX, int sizeY, int sizeZ, int layer) {
        List<Spot> spots = new ArrayList<>();
        int drawn = blockSize();
        int planeH = LayerAxis.horizontal(axis, sizeX, sizeY, sizeZ);
        int planeV = LayerAxis.vertical(axis, sizeX, sizeY, sizeZ);
        for (int z = 0; z < spanZ; z++) {
            for (int x = 0; x < spanX; x++) {
                int worldH = fromX + x;
                int worldV = flipV ? fromZ + spanZ - 1 - z : fromZ + z;
                if (!inside(worldH, worldV, planeH, planeV)) {
                    continue;
                }
                int[] cell = LayerAxis.cell(axis, layer, worldH, worldV);
                spots.add(new Spot(cell[0], cell[1], cell[2], originX + x * this.cell, originY + z * this.cell, drawn));
            }
        }
        return spots;
    }

    public int[] hit(double mouseX, double mouseY, int sizeX, int sizeY, int sizeZ, int layer) {
        if (cell <= 0) {
            return null;
        }
        int localX = (int) ((mouseX - originX) / cell);
        int localZ = (int) ((mouseY - originY) / cell);
        if (localX < 0 || localZ < 0 || localX >= spanX || localZ >= spanZ) {
            return null;
        }
        int worldH = fromX + localX;
        int worldV = flipV ? fromZ + spanZ - 1 - localZ : fromZ + localZ;
        int planeH = LayerAxis.horizontal(axis, sizeX, sizeY, sizeZ);
        int planeV = LayerAxis.vertical(axis, sizeX, sizeY, sizeZ);
        if (!inside(worldH, worldV, planeH, planeV)) {
            return null;
        }
        return LayerAxis.cell(axis, layer, worldH, worldV);
    }
}
