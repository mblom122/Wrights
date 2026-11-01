package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintTransforms;
import com.wrights.network.BlueprintPayloads;

public class BlueprintEditScreen extends Screen implements JeiPreview {
    private final LayerColumn column = new LayerColumn(LayerColumn.EDIT);
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int controlsX;
    private int controlsW;
    private int detailY;
    private int rotateY;
    private int flipY;
    private int pasteY;
    private int footY;
    private WrightsButton placeFluidsButton;
    private WrightsButton placeNbtButton;
    private WrightsButton placeSettingsButton;
    private WrightsButton placeAllButton;
    private WrightsButton materialsButton;
    private WrightsButton saveButton;
    private WrightsButton cancelButton;
    private boolean leaving;

    public BlueprintEditScreen() {
        super(Component.translatable("screen.wrights.adjust_edit"));
    }

    @Override
    protected void init() {
        boolean map = !WrightsClientState.previewConcealed && (StructureGrid.oversized(WrightsClientState.editX, WrightsClientState.editZ)
                || StructureGrid.oversized(WrightsClientState.editX, WrightsClientState.editY)
                || StructureGrid.oversized(WrightsClientState.editZ, WrightsClientState.editY));
        controlsW = 240;
        int gap = 14;
        int pad = 12;
        int side = 12;
        int openW = this.font.width(Component.translatable("screen.wrights.open_map")) + 12;
        int leftChrome = 148 + (map ? 18 : 0);
        int modelSize = Math.min(140, Math.max(map ? openW : 88, this.height - leftChrome - 36));
        int leftW = modelSize;
        if (pad + leftW + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 88, this.width - 8 - pad * 2 - gap - controlsW);
            leftW = modelSize;
        }
        panelW = pad + leftW + gap + controlsW + pad;
        int extraRows = (WrightsClientState.showStoredSettings() ? 1 : 0) + (WrightsClientState.showStoredAll() ? 1 : 0);
        int pasteRows = 2 + extraRows;
        int leftNeed = 28 + modelSize + 16 + 18 + 32 + (map ? 18 : 0) + 52 + 40 + 8;
        // Size/anchor row, then rotate, flip, paste, materials, save.
        int rightNeed = 28 + 32 + 36 + 48 + 12 + pasteRows * 18 + 14 + 16 + 18 + 12;
        panelH = Math.min(this.height - 8, Math.max(leftNeed, rightNeed));
        panelX = JeiPreview.left(this.width, panelW);
        panelY = Math.max(4, (this.height - panelH) / 2);
        int modelX = panelX + pad;
        int modelY = panelY + 28;
        controlsX = modelX + leftW + gap;
        column.place(this, this.font, modelX, modelY, modelSize, widget -> addRenderableWidget(widget), this::setFocused);

        detailY = panelY + 28;
        rotateY = detailY + 32;
        flipY = rotateY + 36;
        pasteY = flipY + 48;
        int inner = controlsW - side;
        int row = (inner - 4) / 2;
        addRenderableWidget(new WrightsButton(controlsX + side, rotateY, row, 16, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(controlsX + side + row + 4, rotateY, row, 16, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(controlsX + side, flipY, row, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(controlsX + side + row + 4, flipY, row, 16, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new WrightsButton(controlsX + side, flipY + 18, row, 16, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(controlsX + side + row + 4, flipY + 18, row, 16, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        placeFluidsButton = new WrightsButton(controlsX + side, pasteY, inner, 16, Component.translatable("screen.wrights.place_fluids"), button -> togglePlaceFluids());
        placeNbtButton = new WrightsButton(controlsX + side, pasteY + 18, inner, 16, Component.translatable("screen.wrights.place_nbt_blocks"), button -> togglePlaceNbt());
        placeSettingsButton = new WrightsButton(controlsX + side, pasteY + 36, inner, 16, Component.translatable("screen.wrights.place_nbt_settings"), button -> togglePlaceSettings());
        placeAllButton = new WrightsButton(controlsX + side, pasteY + 54, inner, 16, Component.translatable("screen.wrights.place_nbt_all"), button -> togglePlaceAll());
        addRenderableWidget(placeFluidsButton);
        addRenderableWidget(placeNbtButton);
        addRenderableWidget(placeSettingsButton);
        addRenderableWidget(placeAllButton);

        footY = pasteY + 36 + extraRows * 18 + 14;
        materialsButton = new WrightsButton(controlsX + side, footY, inner, 16, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(WrightsClientState.editId, 0, 0, 0, this));
        int backW = 68;
        int backX = controlsW - backW;
        saveButton = new WrightsButton(controlsX + side, footY + 18, backX - side - 4, 18, Component.translatable("screen.wrights.save"), button -> finish(true));
        cancelButton = new WrightsButton(controlsX + backX, footY + 18, backW, 18, Component.translatable("screen.wrights.cancel"), button -> onClose());
        addRenderableWidget(materialsButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(cancelButton);

        int contentBottom = Math.max(column.belowSelection(), footY + 40);
        panelH = Math.min(this.height - 8, Math.max(panelH, contentBottom - panelY + 8));
        column.refresh();
        refreshOptions();
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
            PacketDistributor.sendToServer(new BlueprintPayloads.FinishEdit(
                    keep,
                    WrightsClientConfig.PLACE_FLUIDS.get(),
                    WrightsClientConfig.PLACE_NBT_BLOCKS.get(),
                    WrightsClientState.keepStoredSettings(),
                    WrightsClientState.keepStoredAll()));
        }
        this.minecraft.setScreen(new WrightsMenuScreen());
    }

    public void refresh() {
        column.refresh();
        refreshOptions();
    }

    private void refreshOptions() {
        if (placeFluidsButton == null) {
            return;
        }
        placeFluidsButton.setShown(WrightsConfig.allowPlaceFluids());
        placeFluidsButton.setPicked(WrightsClientConfig.PLACE_FLUIDS.get());
        placeNbtButton.setPicked(WrightsClientConfig.PLACE_NBT_BLOCKS.get());
        boolean showSettings = WrightsClientState.showStoredSettings();
        boolean showAll = WrightsClientState.showStoredAll();
        placeSettingsButton.setShown(showSettings);
        placeSettingsButton.setPicked(showSettings && WrightsClientState.placeSettingsChosen());
        placeAllButton.setShown(showAll);
        placeAllButton.setPicked(showAll && WrightsClientState.placeAllChosen());
        int y = pasteY + 36;
        if (showSettings) {
            placeSettingsButton.setY(y);
            y += 18;
        }
        if (showAll) {
            placeAllButton.setY(y);
            y += 18;
        }
        footY = y + 14;
        if (materialsButton != null) {
            materialsButton.setY(footY);
            saveButton.setY(footY + 18);
            cancelButton.setY(footY + 18);
        }
        int contentBottom = Math.max(column.belowSelection(), footY + 40);
        panelH = Math.min(this.height - 8, Math.max(panelH, contentBottom - panelY + 8));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        String title = WrightsClientState.editName == null || WrightsClientState.editName.isEmpty()
                ? Component.translatable("screen.wrights.adjust_edit").getString()
                : WrightsClientState.editName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(title, panelW - 24), panelX + 14, panelY + 8, WrightsUi.COPPER, false);
        column.render(graphics);
        int side = 12;
        int half = (controlsW - side) / 2;
        int sizeX = controlsX + side;
        int anchorX = controlsX + side + half;
        graphics.drawString(this.font, Component.translatable("screen.wrights.size_label"), sizeX, detailY, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.literal(WrightsClientState.editX + " × " + WrightsClientState.editY + " × " + WrightsClientState.editZ), sizeX, detailY + 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.anchor_section"), anchorX, detailY, WrightsUi.BLUE, false);
        String pivot = WrightsClientState.editPivotX + ", " + WrightsClientState.editPivotY + ", " + WrightsClientState.editPivotZ;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(pivot, Math.max(40, controlsW - side - half)), anchorX, detailY + 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.rotate"), controlsX + 12, rotateY - 12, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.flip"), controlsX + 12, flipY - 12, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.paste_options"), controlsX + 12, pasteY - 12, WrightsUi.BLUE, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (column.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (column.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (column.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void selectCell(int x, int y, int z) {
        column.selectCell(x, y, z);
    }

    @Override
    public int jeiLeft() {
        return panelX;
    }

    @Override
    public int jeiTop() {
        return panelY;
    }

    @Override
    public int jeiWidth() {
        return panelW;
    }

    @Override
    public int jeiHeight() {
        return panelH;
    }

    @Override
    public int jeiScreenWidth() {
        return this.width;
    }

    @Override
    public int jeiScreenHeight() {
        return this.height;
    }

    @Override
    public List<StructureGrid.Spot> dropSpots() {
        return column.dropSpots();
    }

    @Override
    public UUID blueprintId() {
        return WrightsClientState.editId;
    }

    @Override
    public void acceptDrop(int x, int y, int z, BlockState state) {
        column.selectCell(x, y, z);
        CellEdits.apply(WrightsClientState.editId, x, y, z, state);
    }

    private void transform(BlueprintTransforms.Op operation) {
        if (WrightsClientState.editId == null) {
            return;
        }
        column.clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(WrightsClientState.editId, operation.ordinal()));
    }

    private void togglePlaceFluids() {
        WrightsClientConfig.setPlaceFluids(!WrightsClientConfig.PLACE_FLUIDS.get());
        refreshOptions();
    }

    private void togglePlaceNbt() {
        WrightsClientConfig.setPlaceNbtBlocks(!WrightsClientConfig.PLACE_NBT_BLOCKS.get());
        refreshOptions();
    }

    private void togglePlaceSettings() {
        if (!WrightsClientState.showStoredSettings()) {
            return;
        }
        WrightsClientConfig.setPlaceNbtSettings(!WrightsClientConfig.PLACE_NBT_SETTINGS.get());
        refreshOptions();
    }

    private void togglePlaceAll() {
        if (!WrightsClientState.showStoredAll()) {
            return;
        }
        WrightsClientConfig.setPlaceNbtAll(!WrightsClientConfig.PLACE_NBT_ALL.get());
        refreshOptions();
    }
}
