package com.autobots.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlueprintMapScreen extends Screen {
    public interface Pick {
        void selectCell(int x, int y, int z);
    }

    private final Screen parent;
    private final Pick pick;
    private final List<AutobotsClientState.Ghost> ghosts;
    private final StructureGrid grid = new StructureGrid();
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int layer;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private int gridX;
    private int gridY;
    private int pixel;
    private int window;
    private int pendingX;
    private int pendingY;
    private int pendingZ;
    private boolean chosen;
    private AutobotsButton okButton;

    public BlueprintMapScreen(Screen parent, Pick pick, List<AutobotsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, int layer, int anchorX, int anchorY, int anchorZ, int selectedX, int selectedY, int selectedZ) {
        super(Component.translatable("screen.autobots.map"));
        this.parent = parent;
        this.pick = pick;
        this.ghosts = ghosts == null ? List.of() : ghosts;
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        this.layer = layer;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        if (selectedY == layer && selectedX >= 0 && selectedZ >= 0 && selectedX < this.sizeX && selectedZ < this.sizeZ) {
            pendingX = selectedX;
            pendingY = selectedY;
            pendingZ = selectedZ;
            chosen = true;
        } else {
            pendingX = -1;
            pendingY = -1;
            pendingZ = -1;
        }
    }

    @Override
    protected void init() {
        int margin = 28;
        int chrome = 78;
        pixel = Math.max(48, Math.min(this.width - margin * 2, this.height - chrome));
        window = Math.max(1, pixel / 8);
        gridX = (this.width - pixel) / 2;
        gridY = 28;
        int focusX = chosen ? pendingX : anchorX;
        int focusZ = chosen ? pendingZ : anchorZ;
        grid.layout(gridX, gridY, pixel, sizeX, sizeZ, focusX, focusZ, window);
        int sliderY = gridY + pixel + 8;
        int sliderW = Math.max(80, (pixel - 8) / 2);
        if (sizeX > grid.spanX()) {
            addRenderableWidget(new AxisSlider(gridX, sliderY, sliderW, sizeX - grid.spanX(), grid.fromX(), true));
        }
        if (sizeZ > grid.spanZ()) {
            addRenderableWidget(new AxisSlider(gridX + pixel - sliderW, sliderY, sliderW, sizeZ - grid.spanZ(), grid.fromZ(), false));
        }
        int buttonsY = this.height - 28;
        okButton = new AutobotsButton(this.width / 2 - 104, buttonsY, 100, 18, Component.translatable("screen.autobots.map_ok"), button -> confirm());
        okButton.active = chosen;
        addRenderableWidget(okButton);
        addRenderableWidget(new AutobotsButton(this.width / 2 + 4, buttonsY, 100, 18, Component.translatable("screen.autobots.cancel"), button -> close()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0060C14);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, gridX, 10, AutobotsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.layer", layer + 1, sizeY), gridX + pixel - 80, 10, AutobotsUi.MUTED, false);
        grid.render(graphics, ghosts, sizeX, sizeZ, layer, anchorX, anchorY, anchorZ, pendingX, pendingY, pendingZ);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int[] hit = grid.hit(mouseX, mouseY, sizeX, sizeZ, layer);
        if (hit == null) {
            return false;
        }
        pendingX = hit[0];
        pendingY = hit[1];
        pendingZ = hit[2];
        chosen = true;
        if (okButton != null) {
            okButton.active = true;
        }
        return true;
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void confirm() {
        if (!chosen || pick == null) {
            close();
            return;
        }
        pick.selectCell(pendingX, pendingY, pendingZ);
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private final class AxisSlider extends AbstractSliderButton {
        private final int max;
        private final boolean horizontal;

        private AxisSlider(int x, int y, int width, int max, int start, boolean horizontal) {
            super(x, y, width, 16, Component.empty(), max <= 0 ? 0.0D : start / (double) max);
            this.max = Math.max(0, max);
            this.horizontal = horizontal;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int shown = position() + 1;
            setMessage(Component.translatable(horizontal ? "screen.autobots.map_x" : "screen.autobots.map_z", shown));
        }

        @Override
        protected void applyValue() {
            if (horizontal) {
                grid.pan(position(), grid.fromZ(), sizeX, sizeZ);
            } else {
                grid.pan(grid.fromX(), position(), sizeX, sizeZ);
            }
        }

        private int position() {
            if (max <= 0) {
                return 0;
            }
            return (int) Math.round(this.value * max);
        }
    }
}
