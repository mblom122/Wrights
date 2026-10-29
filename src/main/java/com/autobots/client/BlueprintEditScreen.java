package com.autobots.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.blueprint.BlueprintTransforms;
import com.autobots.blueprint.BoxEdits;
import com.autobots.network.BlueprintPayloads;

public class BlueprintEditScreen extends Screen {
    private final StructureGrid grid = new StructureGrid();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int modelX;
    private int modelY;
    private int modelSize;
    private int layerY;
    private int selectionY;
    private int rotateY;
    private int flipY;
    private int sideX;
    private int sideW;
    private int layer;
    private int selX = -1;
    private int selY = -1;
    private int selZ = -1;
    private AutobotsButton anchorButton;
    private AutobotsButton removeButton;
    private AutobotsButton changeButton;
    private EditBox layerTo;

    public BlueprintEditScreen() {
        super(Component.translatable("screen.autobots.adjust_edit"));
        layer = AutobotsClientState.editPivotY;
    }

    @Override
    protected void init() {
        sideW = 188;
        int gap = 14;
        int extra = 172;
        modelSize = Math.min(140, Math.max(72, this.height - extra - 24));
        if (modelSize + gap + sideW > this.width - 16) {
            modelSize = Math.max(72, this.width - 16 - gap - sideW);
        }
        panelW = modelSize + gap + sideW + 28;
        panelH = Math.min(this.height - 12, modelSize + extra);
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(6, (this.height - panelH) / 2);
        modelX = panelX + 14;
        modelY = panelY + 28;
        sideX = modelX + modelSize + gap;
        layerY = modelY + modelSize + 2;
        selectionY = layerY + 32;
        rotateY = selectionY + 48;
        flipY = rotateY + 30;
        layer = Mth.clamp(layer, 0, Math.max(0, AutobotsClientState.editY - 1));
        int half = Math.max(36, (modelSize - 4) / 2);
        addRenderableWidget(new AutobotsButton(modelX, layerY, 22, 16, Component.literal("-"), button -> changeLayer(-1)));
        addRenderableWidget(new AutobotsButton(modelX + modelSize - 22, layerY, 22, 16, Component.literal("+"), button -> changeLayer(1)));
        removeButton = new AutobotsButton(modelX, selectionY, half, 16, Component.translatable("screen.autobots.remove_block"), button -> removeBlock());
        changeButton = new AutobotsButton(modelX + half + 4, selectionY, half, 16, Component.translatable("screen.autobots.change_block"), button -> changeBlock());
        addRenderableWidget(removeButton);
        addRenderableWidget(changeButton);
        addRenderableWidget(new AutobotsButton(modelX, rotateY, half, 16, Component.translatable("screen.autobots.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, rotateY, half, 16, Component.translatable("screen.autobots.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new AutobotsButton(modelX, flipY, half, 16, Component.translatable("screen.autobots.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, flipY, half, 16, Component.translatable("screen.autobots.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        int row = (sideW - 4) / 2;
        anchorButton = new AutobotsButton(sideX, panelY + 74, sideW, 16, Component.translatable("screen.autobots.from_selection"), button -> setAnchor());
        addRenderableWidget(anchorButton);
        addRenderableWidget(new AutobotsButton(sideX, panelY + 92, row, 16, Component.translatable("screen.autobots.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new AutobotsButton(sideX + row + 4, panelY + 92, row, 16, Component.translatable("screen.autobots.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        addRenderableWidget(new AutobotsButton(sideX, panelY + 124, row, 16, Component.translatable("screen.autobots.remove_layer"), button -> editLayer(BlueprintPayloads.EditLayer.REMOVE, layer)));
        addRenderableWidget(new AutobotsButton(sideX + row + 4, panelY + 124, row, 16, Component.translatable("screen.autobots.add_layer"), button -> editLayer(BlueprintPayloads.EditLayer.INSERT, layer)));
        layerTo = new EditBox(this.font, sideX + 58, panelY + 144, 40, 16, Component.translatable("screen.autobots.layer_at"));
        layerTo.setFilter(text -> text.matches("\\d{0,3}"));
        layerTo.setMaxLength(3);
        addRenderableWidget(layerTo);
        addRenderableWidget(new AutobotsButton(sideX, panelY + 164, row, 16, Component.translatable("screen.autobots.move_layer"), button -> editLayer(BlueprintPayloads.EditLayer.MOVE, targetLayer())));
        addRenderableWidget(new AutobotsButton(sideX + row + 4, panelY + 164, row, 16, Component.translatable("screen.autobots.copy_layer"), button -> editLayer(BlueprintPayloads.EditLayer.COPY, targetLayer())));
        addRenderableWidget(new AutobotsButton(sideX, panelY + panelH - 56, sideW, 16, Component.translatable("menu.autobots.materials"), button -> MaterialsScreen.request(AutobotsClientState.editId, 0, 0, 0, this)));
        addRenderableWidget(new AutobotsButton(sideX, panelY + panelH - 34, sideW, 16, Component.translatable("menu.autobots.close"), button -> onClose()));
        syncSelection();
    }

    public void refresh() {
        layer = Mth.clamp(layer, 0, Math.max(0, AutobotsClientState.editY - 1));
        if (!inside(selX, selY, selZ)) {
            clearSelection();
        }
        syncSelection();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        AutobotsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncSelection();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("screen.autobots.adjust_edit"), panelX + 14, panelY + 10, AutobotsUi.COPPER, false);
        String name = AutobotsClientState.editName == null ? "" : AutobotsClientState.editName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, panelW - 140), panelX + 120, panelY + 10, AutobotsUi.TEXT, false);
        if (AutobotsClientState.previewConcealed) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.preview_hidden"), modelX + modelSize / 2, modelY + modelSize / 2, AutobotsUi.MUTED);
        } else {
            grid.layout(modelX + 12, modelY + 14, Math.max(32, modelSize - 28), AutobotsClientState.editX, AutobotsClientState.editZ);
            grid.render(graphics, AutobotsClientState.editGhosts, AutobotsClientState.editX, AutobotsClientState.editZ, layer, AutobotsClientState.editPivotX, AutobotsClientState.editPivotY, AutobotsClientState.editPivotZ, selX, selY, selZ);
        }
        graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.layer", layer + 1, Math.max(1, AutobotsClientState.editY)), modelX + modelSize / 2, layerY + 4, AutobotsUi.MUTED);
        graphics.drawString(this.font, Component.translatable("screen.autobots.selection"), modelX, selectionY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.rotate"), modelX, rotateY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.flip"), modelX, flipY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("menu.autobots.blocks", BoxEdits.volume(AutobotsClientState.editX, AutobotsClientState.editY, AutobotsClientState.editZ), AutobotsClientState.editBlocks), sideX, panelY + 32, AutobotsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.anchor_section"), sideX, panelY + 48, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.anchor", AutobotsClientState.editPivotX, AutobotsClientState.editPivotY, AutobotsClientState.editPivotZ), sideX, panelY + 60, AutobotsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.layers"), sideX, panelY + 112, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.layer_at"), sideX, panelY + 148, AutobotsUi.MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && AutobotsClientState.editId != null && !AutobotsClientState.previewConcealed) {
            grid.layout(modelX + 12, modelY + 14, Math.max(32, modelSize - 28), AutobotsClientState.editX, AutobotsClientState.editZ);
            int[] hit = grid.hit(mouseX, mouseY, AutobotsClientState.editX, AutobotsClientState.editZ, layer);
            if (hit != null) {
                selX = hit[0];
                selY = hit[1];
                selZ = hit[2];
                syncSelection();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= modelX && mouseX < modelX + modelSize && mouseY >= modelY && mouseY < modelY + modelSize) {
            changeLayer(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setAnchor() {
        if (AutobotsClientState.editId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAnchor(AutobotsClientState.editId, selX, selY, selZ));
    }

    private void removeBlock() {
        if (AutobotsClientState.editId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(AutobotsClientState.editId, selX, selY, selZ, 0, true));
    }

    private void changeBlock() {
        if (!inside(selX, selY, selZ)) {
            return;
        }
        BlockPickerScreen.open(this, AutobotsClientState.editId, selX, selY, selZ);
    }

    private void editLayer(int operation, int target) {
        if (AutobotsClientState.editId == null || target < 0) {
            return;
        }
        int from = layer;
        if (operation == BlueprintPayloads.EditLayer.INSERT) {
            layer = Math.min(layer, AutobotsClientState.editY);
        } else if (operation == BlueprintPayloads.EditLayer.MOVE || operation == BlueprintPayloads.EditLayer.COPY) {
            layer = target;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.EditLayer(AutobotsClientState.editId, operation, from, operation == BlueprintPayloads.EditLayer.INSERT ? from : target));
    }

    private int targetLayer() {
        if (layerTo == null || layerTo.getValue().isBlank()) {
            return -1;
        }
        try {
            int number = Integer.parseInt(layerTo.getValue());
            return Math.max(0, number - 1);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void transform(BlueprintTransforms.Op operation) {
        if (AutobotsClientState.editId == null) {
            return;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(AutobotsClientState.editId, operation.ordinal()));
    }

    private void changeLayer(int delta) {
        layer = Mth.clamp(layer + delta, 0, Math.max(0, AutobotsClientState.editY - 1));
    }

    private void clearSelection() {
        selX = -1;
        selY = -1;
        selZ = -1;
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < AutobotsClientState.editX && y < AutobotsClientState.editY && z < AutobotsClientState.editZ;
    }

    private void syncSelection() {
        boolean selected = inside(selX, selY, selZ);
        if (anchorButton != null) {
            anchorButton.active = selected;
            removeButton.active = selected;
            changeButton.active = selected;
        }
    }
}
