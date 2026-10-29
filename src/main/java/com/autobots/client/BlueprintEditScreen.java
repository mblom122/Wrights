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
    private AutobotsButton anchorButton;
    private AutobotsButton removeButton;
    private AutobotsButton changeButton;
    private EditBox layerTo;
    private boolean frameSelection;
    private boolean leaving;

    public BlueprintEditScreen() {
        super(Component.translatable("screen.autobots.adjust_edit"));
        layer = AutobotsClientState.editPivotY;
    }

    @Override
    protected void init() {
        boolean map = !AutobotsClientState.previewConcealed && StructureGrid.oversized(AutobotsClientState.editX, AutobotsClientState.editZ);
        int pair = 12 + Math.max(
                Math.max(this.font.width(Component.translatable("screen.autobots.remove_block")), this.font.width(Component.translatable("screen.autobots.change_block"))),
                Math.max(
                        Math.max(this.font.width(Component.translatable("screen.autobots.rotate_left")), this.font.width(Component.translatable("screen.autobots.rotate_right"))),
                        Math.max(this.font.width(Component.translatable("screen.autobots.flip_horizontal")), this.font.width(Component.translatable("screen.autobots.flip_vertical")))));
        int fromW = this.font.width(Component.translatable("screen.autobots.from_selection")) + 12;
        int sideLabel = 12 + Math.max(
                this.font.width(Component.translatable("screen.autobots.remove_layer")),
                Math.max(this.font.width(Component.translatable("screen.autobots.add_layer")), Math.max(this.font.width(Component.translatable("screen.autobots.move_layer")), this.font.width(Component.translatable("screen.autobots.copy_layer")))));
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
        layer = Mth.clamp(layer, 0, Math.max(0, AutobotsClientState.editY - 1));
        int half = Math.max(pair, (columnW - 4) / 2);
        addRenderableWidget(new AutobotsButton(gridX, layerY, 22, 16, Component.literal("-"), button -> changeLayer(-1)));
        addRenderableWidget(new AutobotsButton(gridX + modelSize - 22, layerY, 22, 16, Component.literal("+"), button -> changeLayer(1)));
        if (map) {
            int openW = Math.min(columnW, Math.max(modelSize, this.font.width(Component.translatable("screen.autobots.open_map")) + 12));
            addRenderableWidget(new AutobotsButton(modelX + (columnW - openW) / 2, layerY + 18, openW, 16, Component.translatable("screen.autobots.open_map"), button -> openMap()));
        }
        removeButton = new AutobotsButton(modelX, selectionY, half, 16, Component.translatable("screen.autobots.remove_block"), button -> removeBlock());
        changeButton = new AutobotsButton(modelX + half + 4, selectionY, half, 16, Component.translatable("screen.autobots.change_block"), button -> changeBlock());
        addRenderableWidget(removeButton);
        addRenderableWidget(changeButton);
        addRenderableWidget(new AutobotsButton(modelX, rotateY, half, 16, Component.translatable("screen.autobots.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, rotateY, half, 16, Component.translatable("screen.autobots.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new AutobotsButton(modelX, flipY, half, 16, Component.translatable("screen.autobots.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, flipY, half, 16, Component.translatable("screen.autobots.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        int row = (sideW - 4) / 2;
        int anchorW = Math.min(sideW, Math.max(row, fromW));
        anchorButton = new AutobotsButton(sideX, panelY + 74, anchorW, 16, Component.translatable("screen.autobots.from_selection"), button -> setAnchor());
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
        int footY = panelY + panelH - 34;
        addRenderableWidget(new AutobotsButton(sideX, footY - 20, sideW, 16, Component.translatable("menu.autobots.materials"), button -> MaterialsScreen.request(AutobotsClientState.editId, 0, 0, 0, this)));
        addRenderableWidget(new AutobotsButton(sideX, footY, row, 16, Component.translatable("screen.autobots.cancel"), button -> onClose()));
        addRenderableWidget(new AutobotsButton(sideX + row + 4, footY, row, 16, Component.translatable("screen.autobots.save"), button -> finish(true)));
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
        if (AutobotsClientState.editId != null) {
            PacketDistributor.sendToServer(new BlueprintPayloads.FinishEdit(keep));
        }
        this.minecraft.setScreen(new AutobotsMenuScreen());
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
            layoutGrid();
            grid.render(graphics, AutobotsClientState.editGhosts, AutobotsClientState.editX, AutobotsClientState.editZ, layer, AutobotsClientState.editPivotX, AutobotsClientState.editPivotY, AutobotsClientState.editPivotZ, selX, selY, selZ);
        }
        graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.layer", layer + 1, Math.max(1, AutobotsClientState.editY)), gridX + modelSize / 2, layerY - 12, AutobotsUi.MUTED);
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
            layoutGrid();
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
        layer = Mth.clamp(y, 0, Math.max(0, AutobotsClientState.editY - 1));
        frameSelection = true;
        syncSelection();
    }

    private void openMap() {
        if (this.minecraft == null || AutobotsClientState.previewConcealed || AutobotsClientState.editId == null) {
            return;
        }
        this.minecraft.setScreen(new BlueprintMapScreen(
                this,
                this::selectCell,
                AutobotsClientState.editGhosts,
                AutobotsClientState.editX,
                AutobotsClientState.editY,
                AutobotsClientState.editZ,
                layer,
                AutobotsClientState.editPivotX,
                AutobotsClientState.editPivotY,
                AutobotsClientState.editPivotZ,
                selX,
                selY,
                selZ));
    }

    private void layoutGrid() {
        grid.layout(gridX + 12, modelY + 14, Math.max(32, modelSize - 28), AutobotsClientState.editX, AutobotsClientState.editZ, AutobotsClientState.editPivotX, AutobotsClientState.editPivotZ, StructureGrid.INLINE);
        if (selY == layer && selX >= 0 && selZ >= 0) {
            if (frameSelection) {
                grid.centerOn(selX, selZ, AutobotsClientState.editX, AutobotsClientState.editZ);
                frameSelection = false;
            } else {
                grid.ensureVisible(selX, selZ, AutobotsClientState.editX, AutobotsClientState.editZ);
            }
        }
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
