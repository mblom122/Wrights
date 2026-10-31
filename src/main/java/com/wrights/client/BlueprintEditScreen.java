package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
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
    private int rotateY;
    private int flipY;
    private int pasteY;
    private int detailY;
    private WrightsButton placeFluidsButton;
    private WrightsButton placeNbtButton;
    private WrightsButton placeSettingsButton;
    private WrightsButton placeAllButton;
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
        int openW = this.font.width(Component.translatable("screen.wrights.open_map")) + 12;
        int leftChrome = 148 + (map ? 18 : 0);
        int modelSize = Math.min(140, Math.max(map ? openW : 88, this.height - leftChrome - 36));
        if (pad + modelSize + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 88, this.width - 8 - pad * 2 - gap - controlsW);
        }
        panelW = pad + modelSize + gap + controlsW + pad;
        int extraRows = (WrightsClientState.showStoredSettings() ? 1 : 0) + (WrightsClientState.showStoredAll() ? 1 : 0);
        int belowGuess = 28 + modelSize + 16 + 18 + 32 + (map ? 18 : 0) + 52 + 40;
        int pasteGuess = 40 + 36 + 48 + (2 + extraRows) * 18 + 8;
        panelH = Math.max(belowGuess, pasteGuess) + 160;
        panelX = JeiPreview.left(this.width, panelW);
        panelY = Math.max(4, (this.height - panelH) / 2);
        int modelX = panelX + pad;
        int modelY = panelY + 28;
        controlsX = modelX + modelSize + gap;
        column.place(this, this.font, modelX, modelY, modelSize, widget -> addRenderableWidget(widget), this::setFocused);
        rotateY = panelY + 40;
        flipY = rotateY + 36;
        pasteY = flipY + 48;
        detailY = Math.max(column.belowSelection(), pasteY + (2 + extraRows) * 18 + 8);
        int row = (controlsW - 28) / 2;
        int foot = panelY + panelH;
        addRenderableWidget(new WrightsButton(controlsX + 12, rotateY, row, 16, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, rotateY, row, 16, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY, row, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY, row, 16, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY + 18, row, 16, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY + 18, row, 16, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        placeFluidsButton = new WrightsButton(controlsX + 12, pasteY, controlsW - 24, 16, Component.translatable("screen.wrights.place_fluids"), button -> togglePlaceFluids());
        placeNbtButton = new WrightsButton(controlsX + 12, pasteY + 18, controlsW - 24, 16, Component.translatable("screen.wrights.place_nbt_blocks"), button -> togglePlaceNbt());
        placeSettingsButton = new WrightsButton(controlsX + 12, pasteY + 36, controlsW - 24, 16, Component.translatable("screen.wrights.place_nbt_settings"), button -> togglePlaceSettings());
        placeAllButton = new WrightsButton(controlsX + 12, pasteY + 54, controlsW - 24, 16, Component.translatable("screen.wrights.place_nbt_all"), button -> togglePlaceAll());
        addRenderableWidget(placeFluidsButton);
        addRenderableWidget(placeNbtButton);
        addRenderableWidget(placeSettingsButton);
        addRenderableWidget(placeAllButton);
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 74, controlsW - 24, 16, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(WrightsClientState.editId, 0, 0, 0, this)));
        int backW = 68;
        int backX = controlsW - 12 - backW;
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 48, backX - 16, 18, Component.translatable("screen.wrights.save"), button -> finish(true)));
        addRenderableWidget(new WrightsButton(controlsX + backX, foot - 48, backW, 18, Component.translatable("screen.wrights.cancel"), button -> onClose()));
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
        }
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
        graphics.drawString(this.font, Component.translatable("screen.wrights.rotate"), controlsX + 12, rotateY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.flip"), controlsX + 12, flipY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.paste_options"), controlsX + 12, pasteY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.size_label"), controlsX + 12, detailY, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.literal(WrightsClientState.editX + " × " + WrightsClientState.editY + " × " + WrightsClientState.editZ), controlsX + 12, detailY + 14, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.anchor_section"), controlsX + 126, detailY, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.anchor", WrightsClientState.editPivotX, WrightsClientState.editPivotY, WrightsClientState.editPivotZ), controlsX + 126, detailY + 14, WrightsUi.TEXT, false);
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
