package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.blueprint.BlueprintTransforms;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintPayloads;

public class BlueprintEditScreen extends Screen {
    private final StructureGrid grid = new StructureGrid();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int modelX;
    private int modelY;
    private int modelSize;
    private int gridX;
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
    private WrightsButton anchorButton;
    private WrightsButton removeButton;
    private WrightsButton changeButton;
    private EditBox layerTo;
    private boolean frameSelection;
    private boolean leaving;

    public BlueprintEditScreen() {
        super(Component.translatable("screen.wrights.adjust_edit"));
        layer = WrightsClientState.editPivotY;
    }

    @Override
    protected void init() {
        boolean map = !WrightsClientState.previewConcealed && StructureGrid.oversized(WrightsClientState.editX, WrightsClientState.editZ);
        int pair = 12 + Math.max(
                Math.max(this.font.width(Component.translatable("screen.wrights.remove_block")), this.font.width(Component.translatable("screen.wrights.change_block"))),
                Math.max(
                        Math.max(this.font.width(Component.translatable("screen.wrights.rotate_left")), this.font.width(Component.translatable("screen.wrights.rotate_right"))),
                        Math.max(this.font.width(Component.translatable("screen.wrights.flip_horizontal")), this.font.width(Component.translatable("screen.wrights.flip_vertical")))));
        int fromW = this.font.width(Component.translatable("screen.wrights.from_selection")) + 12;
        int sideLabel = 12 + Math.max(
                this.font.width(Component.translatable("screen.wrights.remove_layer")),
                Math.max(this.font.width(Component.translatable("screen.wrights.add_layer")), Math.max(this.font.width(Component.translatable("screen.wrights.move_layer")), this.font.width(Component.translatable("screen.wrights.copy_layer")))));
        int sideNeed = Math.max(fromW, sideLabel * 2 + 4);
        sideW = Math.max(sideNeed, 188);
        int gap = 14;
        int extra = (map ? 192 : 172) + 32;
        modelSize = Math.min(140, Math.max(72, this.height - extra - 24));
        int columnW = Math.max(modelSize, pair * 2 + 4);
        if (columnW + gap + sideW > this.width - 16) {
            int room = Math.max(sideNeed, this.width - 16 - gap - columnW);
            sideW = Math.min(sideW, room);
        }
        panelW = columnW + gap + sideW + 28;
        panelH = Math.min(this.height - 12, modelSize + extra);
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(6, (this.height - panelH) / 2);
        modelX = panelX + 14;
        modelY = panelY + 28;
        gridX = modelX + Math.max(0, (columnW - modelSize) / 2);
        sideX = modelX + columnW + gap;
        layerY = modelY + modelSize + 14;
        int mapShift = map ? 20 : 0;
        selectionY = layerY + 32 + mapShift;
        rotateY = selectionY + 48;
        flipY = rotateY + 30;
        layer = Mth.clamp(layer, 0, Math.max(0, WrightsClientState.editY - 1));
        int half = Math.max(pair, (columnW - 4) / 2);
        addRenderableWidget(new WrightsButton(gridX, layerY, 22, 16, Component.literal("-"), button -> changeLayer(-1)));
        addRenderableWidget(new WrightsButton(gridX + modelSize - 22, layerY, 22, 16, Component.literal("+"), button -> changeLayer(1)));
        if (map) {
            int openW = Math.min(columnW, Math.max(modelSize, this.font.width(Component.translatable("screen.wrights.open_map")) + 12));
            addRenderableWidget(new WrightsButton(modelX + (columnW - openW) / 2, layerY + 18, openW, 16, Component.translatable("screen.wrights.open_map"), button -> openMap()));
        }
        removeButton = new WrightsButton(modelX, selectionY, half, 16, Component.translatable("screen.wrights.remove_block"), button -> removeBlock());
        changeButton = new WrightsButton(modelX + half + 4, selectionY, half, 16, Component.translatable("screen.wrights.change_block"), button -> changeBlock());
        addRenderableWidget(removeButton);
        addRenderableWidget(changeButton);
        addRenderableWidget(new WrightsButton(modelX, rotateY, half, 16, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(modelX + half + 4, rotateY, half, 16, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(modelX, flipY, half, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(modelX + half + 4, flipY, half, 16, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        int row = (sideW - 4) / 2;
        int anchorW = Math.min(sideW, Math.max(row, fromW));
        anchorButton = new WrightsButton(sideX, panelY + 74, anchorW, 16, Component.translatable("screen.wrights.from_selection"), button -> setAnchor());
        addRenderableWidget(anchorButton);
        addRenderableWidget(new WrightsButton(sideX, panelY + 92, row, 16, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(sideX + row + 4, panelY + 92, row, 16, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        addRenderableWidget(new WrightsButton(sideX, panelY + 124, row, 16, Component.translatable("screen.wrights.remove_layer"), button -> editLayer(BlueprintPayloads.EditLayer.REMOVE, layer)));
        addRenderableWidget(new WrightsButton(sideX + row + 4, panelY + 124, row, 16, Component.translatable("screen.wrights.add_layer"), button -> editLayer(BlueprintPayloads.EditLayer.INSERT, layer)));
        layerTo = new EditBox(this.font, sideX + 58, panelY + 144, 40, 16, Component.translatable("screen.wrights.layer_at"));
        layerTo.setFilter(text -> text.matches("\\d{0,3}"));
        layerTo.setMaxLength(3);
        addRenderableWidget(layerTo);
        addRenderableWidget(new WrightsButton(sideX, panelY + 164, row, 16, Component.translatable("screen.wrights.move_layer"), button -> editLayer(BlueprintPayloads.EditLayer.MOVE, targetLayer())));
        addRenderableWidget(new WrightsButton(sideX + row + 4, panelY + 164, row, 16, Component.translatable("screen.wrights.copy_layer"), button -> editLayer(BlueprintPayloads.EditLayer.COPY, targetLayer())));
        int footY = panelY + panelH - 34;
        addRenderableWidget(new WrightsButton(sideX, footY - 20, sideW, 16, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(WrightsClientState.editId, 0, 0, 0, this)));
        addRenderableWidget(new WrightsButton(sideX, footY, row, 16, Component.translatable("screen.wrights.cancel"), button -> onClose()));
        addRenderableWidget(new WrightsButton(sideX + row + 4, footY, row, 16, Component.translatable("screen.wrights.save"), button -> finish(true)));
        syncSelection();
    }

    @Override
    public void onClose() {
        finish(false);
    }

    private void finish(boolean keep) {
        if (leaving || this.minecraft == null) {
            return;
        }
        leaving = true;
        if (WrightsClientState.editId != null) {
            PacketDistributor.sendToServer(new BlueprintPayloads.FinishEdit(keep));
        }
        this.minecraft.setScreen(new WrightsMenuScreen());
    }

    public void refresh() {
        layer = Mth.clamp(layer, 0, Math.max(0, WrightsClientState.editY - 1));
        if (!inside(selX, selY, selZ)) {
            clearSelection();
        }
        syncSelection();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncSelection();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("screen.wrights.adjust_edit"), panelX + 14, panelY + 10, WrightsUi.COPPER, false);
        String name = WrightsClientState.editName == null ? "" : WrightsClientState.editName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, panelW - 140), panelX + 120, panelY + 10, WrightsUi.TEXT, false);
        if (WrightsClientState.previewConcealed) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.preview_hidden"), modelX + modelSize / 2, modelY + modelSize / 2, WrightsUi.MUTED);
        } else {
            layoutGrid();
            grid.render(graphics, WrightsClientState.editGhosts, WrightsClientState.editX, WrightsClientState.editZ, layer, WrightsClientState.editPivotX, WrightsClientState.editPivotY, WrightsClientState.editPivotZ, selX, selY, selZ);
        }
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.layer", layer + 1, Math.max(1, WrightsClientState.editY)), gridX + modelSize / 2, layerY - 12, WrightsUi.MUTED);
        graphics.drawString(this.font, Component.translatable("screen.wrights.selection"), modelX, selectionY - 12, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.rotate"), modelX, rotateY - 12, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.flip"), modelX, flipY - 12, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.blocks", BoxEdits.volume(WrightsClientState.editX, WrightsClientState.editY, WrightsClientState.editZ), WrightsClientState.editBlocks), sideX, panelY + 32, WrightsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.anchor_section"), sideX, panelY + 48, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.anchor", WrightsClientState.editPivotX, WrightsClientState.editPivotY, WrightsClientState.editPivotZ), sideX, panelY + 60, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.layers"), sideX, panelY + 112, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.layer_at"), sideX, panelY + 148, WrightsUi.MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && WrightsClientState.editId != null && !WrightsClientState.previewConcealed) {
            layoutGrid();
            int[] hit = grid.hit(mouseX, mouseY, WrightsClientState.editX, WrightsClientState.editZ, layer);
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
        if (mouseX >= gridX && mouseX < gridX + modelSize && mouseY >= modelY && mouseY < modelY + modelSize) {
            changeLayer(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void selectCell(int x, int y, int z) {
        selX = x;
        selY = y;
        selZ = z;
        layer = Mth.clamp(y, 0, Math.max(0, WrightsClientState.editY - 1));
        frameSelection = true;
        syncSelection();
    }

    private void openMap() {
        if (this.minecraft == null || WrightsClientState.previewConcealed || WrightsClientState.editId == null) {
            return;
        }
        this.minecraft.setScreen(new BlueprintMapScreen(
                this,
                this::selectCell,
                WrightsClientState.editGhosts,
                WrightsClientState.editX,
                WrightsClientState.editY,
                WrightsClientState.editZ,
                layer,
                WrightsClientState.editPivotX,
                WrightsClientState.editPivotY,
                WrightsClientState.editPivotZ,
                selX,
                selY,
                selZ));
    }

    private void layoutGrid() {
        grid.layout(gridX + 12, modelY + 14, Math.max(32, modelSize - 28), WrightsClientState.editX, WrightsClientState.editZ, WrightsClientState.editPivotX, WrightsClientState.editPivotZ, StructureGrid.INLINE);
        if (selY == layer && selX >= 0 && selZ >= 0) {
            if (frameSelection) {
                grid.centerOn(selX, selZ, WrightsClientState.editX, WrightsClientState.editZ);
                frameSelection = false;
            } else {
                grid.ensureVisible(selX, selZ, WrightsClientState.editX, WrightsClientState.editZ);
            }
        }
    }

    private void setAnchor() {
        if (WrightsClientState.editId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAnchor(WrightsClientState.editId, selX, selY, selZ));
    }

    private void removeBlock() {
        if (WrightsClientState.editId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(WrightsClientState.editId, selX, selY, selZ, 0, true));
    }

    private void changeBlock() {
        if (!inside(selX, selY, selZ)) {
            return;
        }
        BlockPickerScreen.open(this, WrightsClientState.editId, selX, selY, selZ);
    }

    private void editLayer(int operation, int target) {
        if (WrightsClientState.editId == null || target < 0) {
            return;
        }
        int from = layer;
        if (operation == BlueprintPayloads.EditLayer.INSERT) {
            layer = Math.min(layer, WrightsClientState.editY);
        } else if (operation == BlueprintPayloads.EditLayer.MOVE || operation == BlueprintPayloads.EditLayer.COPY) {
            layer = target;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.EditLayer(WrightsClientState.editId, operation, from, operation == BlueprintPayloads.EditLayer.INSERT ? from : target));
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
        if (WrightsClientState.editId == null) {
            return;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(WrightsClientState.editId, operation.ordinal()));
    }

    private void changeLayer(int delta) {
        layer = Mth.clamp(layer + delta, 0, Math.max(0, WrightsClientState.editY - 1));
    }

    private void clearSelection() {
        selX = -1;
        selY = -1;
        selZ = -1;
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < WrightsClientState.editX && y < WrightsClientState.editY && z < WrightsClientState.editZ;
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
