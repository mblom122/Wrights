package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
    private int bodyScroll;
    private int bodyTop;
    private int bodyViewH;
    private int contentH;
    private int rowH;
    private int btnH;
    private WrightsButton placeFluidsButton;
    private WrightsButton placeNbtButton;
    private WrightsButton placeSettingsButton;
    private WrightsButton placeAllButton;
    private WrightsButton materialsButton;
    private WrightsButton saveButton;
    private WrightsButton cancelButton;
    private Component helpTip;
    private boolean leaving;

    public BlueprintEditScreen() {
        super(Component.translatable("screen.wrights.adjust_edit"));
    }

    @Override
    protected void init() {
        boolean map = !WrightsClientState.previewConcealed && (StructureGrid.oversized(WrightsClientState.editX, WrightsClientState.editZ)
                || StructureGrid.oversized(WrightsClientState.editX, WrightsClientState.editY)
                || StructureGrid.oversized(WrightsClientState.editZ, WrightsClientState.editY));
        float density = Mth.clamp(this.height / 380.0F, 0.62F, 1.0F);
        rowH = density < 0.85F ? 14 : 16;
        btnH = density < 0.85F ? 14 : 16;
        int section = density < 0.85F ? 10 : 12;
        controlsW = density < 0.85F ? 210 : 240;
        int gap = density < 0.85F ? 10 : 14;
        int pad = 12;
        int side = 12;
        int openW = this.font.width(Component.translatable("screen.wrights.open_map")) + 12;
        int modelCap = Math.max(72, Math.round(140 * density));
        int modelSize = Math.min(modelCap, Math.max(map ? openW : 72, Math.round(88 * density)));
        int leftW = modelSize;
        if (pad + leftW + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 72, this.width - 8 - pad * 2 - gap - controlsW);
            leftW = modelSize;
        }
        panelW = pad + leftW + gap + controlsW + pad;
        int extraRows = (WrightsClientState.showStoredSettings() ? 1 : 0) + (WrightsClientState.showStoredAll() ? 1 : 0);
        int pasteRows = 2 + extraRows;
        int leftNeed = 28 + modelSize + 16 + 18 + 32 + (map ? 18 : 0) + 52 + 40 + 8;
        int rightNeed = 28 + 32 + 36 + 48 + 12 + pasteRows * rowH + 14 + 16 + 18 + 12;
        panelH = WrightsUi.fitH(Math.max(leftNeed, rightNeed), this.height);
        panelX = JeiPreview.left(this.width, panelW);
        panelY = WrightsUi.centerY(panelH, this.height);
        bodyTop = panelY + 22;
        bodyViewH = Math.max(40, panelH - 28);
        bodyScroll = Mth.clamp(bodyScroll, 0, Math.max(0, contentH - bodyViewH));
        int origin = bodyTop - bodyScroll;
        int modelX = panelX + pad;
        int modelY = origin;
        controlsX = modelX + leftW + gap;
        column.place(this, this.font, modelX, modelY, modelSize, widget -> addRenderableWidget(widget), this::setFocused);

        detailY = origin;
        rotateY = detailY + 28 + section;
        flipY = rotateY + rowH + section + 8;
        pasteY = flipY + rowH * 2 + section + 8;
        int inner = controlsW - side;
        int row = (inner - 4) / 2;
        addRenderableWidget(new WrightsButton(controlsX + side, rotateY, row, btnH, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(controlsX + side + row + 4, rotateY, row, btnH, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(controlsX + side, flipY, row, btnH, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(controlsX + side + row + 4, flipY, row, btnH, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new WrightsButton(controlsX + side, flipY + rowH, row, btnH, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(controlsX + side + row + 4, flipY + rowH, row, btnH, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        placeFluidsButton = new WrightsButton(controlsX + side, pasteY, inner, btnH, Component.translatable("screen.wrights.place_fluids"), button -> togglePlaceFluids());
        placeFluidsButton.setTooltip(Tooltip.create(Component.translatable("screen.wrights.place_fluids_hint")));
        placeNbtButton = new WrightsButton(controlsX + side, pasteY + rowH, inner, btnH, Component.translatable("screen.wrights.place_nbt_blocks"), button -> togglePlaceNbt());
        placeNbtButton.setTooltip(Tooltip.create(Component.translatable("screen.wrights.place_nbt_blocks_hint")));
        placeSettingsButton = new WrightsButton(controlsX + side, pasteY + rowH * 2, inner, btnH, Component.translatable("screen.wrights.place_nbt_settings"), button -> togglePlaceSettings());
        placeSettingsButton.setTooltip(Tooltip.create(Component.translatable("screen.wrights.place_nbt_settings_hint")));
        placeAllButton = new WrightsButton(controlsX + side, pasteY + rowH * 3, inner, btnH, Component.translatable("screen.wrights.place_nbt_all"), button -> togglePlaceAll());
        placeAllButton.setTooltip(Tooltip.create(Component.translatable("screen.wrights.place_nbt_all_hint")));
        addRenderableWidget(placeFluidsButton);
        addRenderableWidget(placeNbtButton);
        addRenderableWidget(placeSettingsButton);
        addRenderableWidget(placeAllButton);

        footY = pasteY + rowH * 2 + extraRows * rowH + 12;
        materialsButton = new WrightsButton(controlsX + side, footY, inner, btnH, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(
                WrightsClientState.editId,
                0,
                0,
                0,
                WrightsClientConfig.PLACE_FLUIDS.get(),
                WrightsClientConfig.PLACE_NBT_BLOCKS.get(),
                WrightsClientState.placeSettingsChosen(),
                WrightsClientState.placeAllChosen(),
                this));
        int backW = 68;
        int backX = controlsW - backW;
        int footBtn = Math.max(16, btnH + 2);
        saveButton = new WrightsButton(controlsX + side, footY + rowH + 2, backX - side - 4, footBtn, Component.translatable("screen.wrights.save"), button -> finish(true));
        cancelButton = new WrightsButton(controlsX + backX, footY + rowH + 2, backW, footBtn, Component.translatable("screen.wrights.cancel"), button -> onClose());
        addRenderableWidget(materialsButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(cancelButton);

        int contentBottom = Math.max(column.belowSelection(), footY + rowH + 2 + footBtn + 8);
        contentH = contentBottom - (bodyTop - bodyScroll);
        bodyScroll = Mth.clamp(bodyScroll, 0, Math.max(0, contentH - bodyViewH));
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
        WrightsMenuScreen.preferBlueprintsStrip();
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
        int pitch = Math.max(14, rowH);
        int y = pasteY + pitch * 2;
        if (showSettings) {
            placeSettingsButton.setY(y);
            y += pitch;
        }
        if (showAll) {
            placeAllButton.setY(y);
            y += pitch;
        }
        footY = y + 12;
        int footBtn = Math.max(16, btnH + 2);
        if (materialsButton != null) {
            materialsButton.setY(footY);
            saveButton.setY(footY + pitch + 2);
            cancelButton.setY(footY + pitch + 2);
        }
        int contentBottom = Math.max(column.belowSelection(), footY + pitch + 2 + footBtn + 8);
        contentH = contentBottom - (bodyTop - bodyScroll);
        bodyScroll = Mth.clamp(bodyScroll, 0, Math.max(0, contentH - bodyViewH));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.enableScissor(panelX + 1, bodyTop, panelX + panelW - 1, bodyTop + bodyViewH);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        column.render(graphics);
        int side = 12;
        int half = (controlsW - side) / 2;
        int sizeX = controlsX + side;
        int anchorX = controlsX + side + half;
        graphics.drawString(this.font, Component.translatable("screen.wrights.size_label"), sizeX, detailY, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.literal(WrightsClientState.editX + " × " + WrightsClientState.editY + " × " + WrightsClientState.editZ), sizeX, detailY + 12, WrightsUi.SIZE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.anchor_section"), anchorX, detailY, WrightsUi.BLUE, false);
        String pivot = WrightsClientState.editPivotX + ", " + WrightsClientState.editPivotY + ", " + WrightsClientState.editPivotZ;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(pivot, Math.max(40, controlsW - side - half)), anchorX, detailY + 12, WrightsUi.TEXT, false);
        helpTip = null;
        int labelX = controlsX + 12;
        drawHeaderHelp(graphics, mouseX, mouseY, labelX, rotateY - 12, "screen.wrights.rotate", "screen.wrights.rotate_hint");
        drawHeaderHelp(graphics, mouseX, mouseY, labelX, flipY - 12, "screen.wrights.flip", "screen.wrights.flip_hint");
        drawHeaderHelp(graphics, mouseX, mouseY, labelX, pasteY - 12, "screen.wrights.paste_options", "screen.wrights.paste_options_hint");
        graphics.disableScissor();
        String title = WrightsClientState.editName == null || WrightsClientState.editName.isEmpty()
                ? Component.translatable("screen.wrights.adjust_edit").getString()
                : WrightsClientState.editName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(title, panelW - 24), panelX + 14, panelY + 8, WrightsUi.COPPER, false);
        WrightsUi.scrollBar(graphics, panelX + panelW - 5, bodyTop, bodyViewH, bodyScroll, contentH);
        if (helpTip != null) {
            graphics.renderTooltip(this.font, this.font.split(helpTip, 240), mouseX, mouseY);
        }
    }

    private void drawHeaderHelp(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String titleKey, String hintKey) {
        Component title = Component.translatable(titleKey);
        graphics.drawString(this.font, title, x, y, WrightsUi.BLUE, false);
        if (WrightsUi.helpMark(graphics, this.font, mouseX, mouseY, x + this.font.width(title) + 4, y - 2, hintKey)) {
            helpTip = Component.translatable(hintKey);
        }
    }

    private boolean insideBody(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + panelW && mouseY >= bodyTop && mouseY < bodyTop + bodyViewH;
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
        if (!insideBody(mouseX, mouseY) && mouseY >= bodyTop) {
            return false;
        }
        if (insideBody(mouseX, mouseY) && column.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!insideBody(mouseX, mouseY)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideBody(mouseX, mouseY) && column.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (insideBody(mouseX, mouseY)) {
            int max = Math.max(0, contentH - bodyViewH);
            int next = Mth.clamp(bodyScroll - (int) Math.signum(scrollY) * 18, 0, max);
            if (next != bodyScroll) {
                bodyScroll = next;
                this.rebuildWidgets();
                return true;
            }
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
