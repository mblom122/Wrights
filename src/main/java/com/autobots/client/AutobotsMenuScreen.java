package com.autobots.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.AutobotsClientConfig;
import com.autobots.network.BlueprintPayloads;

public class AutobotsMenuScreen extends Screen {
    private static final int ROW = 18;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int detailX;
    private int detailY;
    private int detailW;
    private int scroll;
    private UUID confirmDelete;
    private long lastClick;
    private EditBox search;
    private EditBox rename;
    private AutobotsButton newButton;
    private AutobotsButton editButton;
    private AutobotsButton deleteButton;
    private AutobotsButton buildButton;
    private AutobotsButton renameButton;
    private AutobotsButton discardButton;
    private AutobotsButton resumeButton;
    private AutobotsButton previewButton;
    private AutobotsButton stopButton;
    private AutobotsButton materialsButton;
    private int detailSplit;
    private UUID modelAsked;
    private UUID spinId;
    private boolean spinning;
    private float yaw = 35.0F;
    private float pitch = 28.0F;
    private int modelX;
    private int modelY;
    private int modelW;
    private int modelH;
    private final List<BlueprintPayloads.Summary> filtered = new ArrayList<>();
    private boolean options;
    private int drag;
    private int wallSliderX;
    private int wallSliderY;
    private int wallSliderW;
    private int edgeSliderX;
    private int edgeSliderY;
    private int edgeSliderW;
    private int swatchX;
    private int wallSwatchY;
    private int edgeSwatchY;
    private int markerX;
    private int markerY;
    private int markerW;
    private EditBox wallPercent;
    private EditBox edgePercent;
    private EditBox placeX;
    private EditBox placeY;
    private EditBox placeZ;
    private boolean suppressOptions;

    public AutobotsMenuScreen() {
        super(Component.translatable("menu.autobots.title"));
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(this.font, listX, panelY + 36, listW, 16, Component.translatable("menu.autobots.search"));
        search.setHint(Component.translatable("menu.autobots.search"));
        search.setMaxLength(48);
        search.setResponder(value -> {
            scroll = 0;
            refresh();
        });
        addRenderableWidget(search);

        rename = new EditBox(this.font, detailX + 4, detailSplit + 52, Math.max(40, detailW - 86), 14, Component.translatable("screen.autobots.name"));
        rename.setMaxLength(48);
        addRenderableWidget(rename);

        int y = panelY + panelH - 26;
        int buttonW = 58;
        newButton = add(new AutobotsButton(listX, y, buttonW, 18, Component.translatable("menu.autobots.new"), button -> newBlueprint()));
        editButton = add(new AutobotsButton(listX + 62, y, buttonW, 18, Component.translatable("menu.autobots.edit"), button -> edit()));
        deleteButton = add(new AutobotsButton(listX + 124, y, buttonW, 18, Component.translatable("menu.autobots.delete"), button -> delete(), true));
        buildButton = add(new AutobotsButton(detailX, y, detailW - 74, 18, Component.translatable("menu.autobots.build"), button -> build()));
        addRenderableWidget(new AutobotsButton(detailX + detailW - 70, y, 70, 18, Component.translatable("menu.autobots.close"), button -> onClose()));
        renameButton = add(new AutobotsButton(detailX + Math.max(48, detailW - 78), detailSplit + 52, 74, 14, Component.translatable("menu.autobots.rename"), button -> rename()));
        materialsButton = add(new AutobotsButton(detailX + 4, detailSplit + 72, Math.max(40, detailW - 8), 16, Component.translatable("menu.autobots.materials"), button -> openMaterials()));
        stopButton = add(new AutobotsButton(detailX + 4, detailSplit + 92, Math.max(40, detailW - 8), 16, Component.translatable("menu.autobots.stop"), button -> PacketDistributor.sendToServer(new BlueprintPayloads.StopJob())));
        discardButton = add(new AutobotsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.autobots.discard"), button -> discard()));
        resumeButton = add(new AutobotsButton(panelX + 14, panelY + panelH - 68, 92, 16, Component.translatable("menu.autobots.resume"), button -> minecraft.setScreen(null)));
        previewButton = add(new AutobotsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.autobots.cancel_preview"), button -> cancelPreview()));
        wallPercent = percentBox(value -> AutobotsClientConfig.setWallTransparency(value));
        edgePercent = percentBox(value -> AutobotsClientConfig.setEdgeOpacity(value));
        placeX = offsetBox(0);
        placeY = offsetBox(1);
        placeZ = offsetBox(2);
        refresh();
    }

    private <T extends AutobotsButton> T add(T button) {
        addRenderableWidget(button);
        return button;
    }

    private void layout() {
        panelW = Math.min(540, this.width - 16);
        panelH = Math.min(360, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX = panelX + 126;
        listY = panelY + 56;
        listW = Math.min(168, panelW - 300);
        if (listW < 120) {
            listW = Math.max(100, (panelW - 150) / 2);
        }
        listH = panelH - 96;
        detailX = listX + listW + 8;
        detailY = listY;
        detailW = panelX + panelW - 12 - detailX;
        if (detailW < 150 && listW > 100) {
            listW = Math.max(100, listW - (150 - detailW));
            detailX = listX + listW + 8;
            detailW = panelX + panelW - 12 - detailX;
        }
        detailSplit = detailY + listH / 2;
    }

    public void refresh() {
        if (search == null) {
            return;
        }
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (BlueprintPayloads.Summary summary : AutobotsClientState.library) {
            if (query.isEmpty() || summary.name().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(summary);
            }
        }
        if (AutobotsClientState.selected != null && filtered.stream().noneMatch(entry -> entry.id().equals(AutobotsClientState.selected))) {
            AutobotsClientState.selected = null;
            confirmDelete = null;
        }
        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        scroll = Mth.clamp(scroll, 0, maxScroll);
        BlueprintPayloads.Summary selected = selected();
        boolean has = selected != null;
        editButton.active = has;
        deleteButton.active = has;
        buildButton.active = has && !AutobotsClientState.busy;
        renameButton.active = has;
        deleteButton.setMessage(Component.translatable(has && selected.id().equals(confirmDelete) ? "menu.autobots.delete_confirm" : "menu.autobots.delete"));
        if (has && !rename.isFocused()) {
            rename.setValue(selected.name());
        }
        boolean capture = AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE;
        boolean building = !options;
        search.visible = building;
        rename.visible = building;
        newButton.visible = building;
        editButton.visible = building;
        deleteButton.visible = building;
        buildButton.visible = building;
        renameButton.visible = building;
        discardButton.visible = building && (capture || AutobotsClientState.mode == AutobotsClientState.Mode.SAVING);
        resumeButton.visible = building && capture;
        if (previewButton != null) {
            previewButton.visible = building && AutobotsClientState.mode == AutobotsClientState.Mode.BUILD;
        }
        if (stopButton != null) {
            stopButton.visible = building && AutobotsClientState.busy;
            stopButton.active = AutobotsClientState.busy;
            stopButton.setX(detailX + 4);
            stopButton.setY(detailSplit + 92);
            stopButton.setWidth(Math.max(40, detailW - 8));
        }
        if (materialsButton != null) {
            materialsButton.visible = building;
            materialsButton.active = has;
            materialsButton.setX(detailX + 4);
            materialsButton.setY(detailSplit + 72);
            materialsButton.setWidth(Math.max(40, detailW - 8));
        }
        if (rename != null) {
            rename.setX(detailX + 4);
            rename.setY(detailSplit + 52);
            rename.setWidth(Math.max(40, detailW - 86));
        }
        if (renameButton != null) {
            renameButton.setX(detailX + Math.max(48, detailW - 78));
            renameButton.setY(detailSplit + 52);
        }
        if (previewButton.visible) {
            discardButton.visible = false;
        }
        boolean showOptions = options;
        wallPercent.visible = showOptions;
        edgePercent.visible = showOptions;
        placeX.visible = showOptions;
        placeY.visible = showOptions;
        placeZ.visible = showOptions;
        if (showOptions) {
            showPercent(wallPercent, AutobotsClientConfig.WALL_TRANSPARENCY.get());
            showPercent(edgePercent, AutobotsClientConfig.EDGE_OPACITY.get());
            showOffset(placeX, AutobotsClientConfig.PLACE_X.get());
            showOffset(placeY, AutobotsClientConfig.PLACE_Y.get());
            showOffset(placeZ, AutobotsClientConfig.PLACE_Z.get());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        AutobotsUi.panel(graphics, panelX, panelY, panelW, panelH);
        if (options) {
            int x = listX - 2;
            int width = panelX + panelW - 12 - x;
            AutobotsUi.inset(graphics, x, panelY + 34, width, panelH - 68);
            return;
        }
        AutobotsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
        AutobotsUi.inset(graphics, detailX - 2, detailY - 2, detailW + 4, listH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("menu.autobots.title"), panelX + 16, panelY + 10, AutobotsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("menu.autobots.subtitle"), panelX + 16, panelY + 22, AutobotsUi.MUTED, false);
        drawTask(graphics, Component.translatable("menu.autobots.building"), !options, false, panelY + 46);
        drawTask(graphics, Component.translatable("menu.autobots.mining"), false, true, panelY + 72);
        drawTask(graphics, Component.translatable("menu.autobots.hauling"), false, true, panelY + 98);
        drawTask(graphics, Component.translatable("menu.autobots.options"), options, false, panelY + 124);
        if (options) {
            renderOptions(graphics);
            return;
        }

        graphics.drawString(this.font, Component.translatable("menu.autobots.blueprints"), listX, panelY + 24, AutobotsUi.TEXT, false);
        if (AutobotsClientState.busy) {
            graphics.drawString(this.font, Component.translatable("menu.autobots.busy"), detailX, panelY + 24, AutobotsUi.COPPER, false);
        }
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
    }

    private void drawTask(GuiGraphics graphics, Component label, boolean active, boolean soon, int y) {
        int x = panelX + 14;
        int color = active ? 0xFF24384A : 0xFF18222C;
        graphics.fill(x, y, x + 100, y + 22, color);
        if (active) {
            graphics.fill(x, y, x + 2, y + 22, AutobotsUi.COPPER);
        }
        graphics.drawString(this.font, label, x + 8, y + 7, active ? AutobotsUi.TEXT : AutobotsUi.MUTED, false);
        if (soon) {
            graphics.drawString(this.font, Component.translatable("menu.autobots.soon"), x + 68, y + 7, 0xFF667480, false);
        }
    }

    private void renderOptions(GuiGraphics graphics) {
        int x = listX + 8;
        int y = panelY + 46;
        graphics.drawString(this.font, Component.translatable("menu.autobots.options_title"), x, panelY + 42, AutobotsUi.TEXT, false);
        int preview = (Math.round(AutobotsClientConfig.wallAlpha() * 255.0F) << 24) | AutobotsClientConfig.wallRgb();
        graphics.fill(x + 220, panelY + 42, x + 268, panelY + 70, preview);
        AutobotsUi.border(graphics, x + 220, panelY + 42, 48, 28, 0xFF000000 | AutobotsClientConfig.edgeRgb());
        y = panelY + 64;
        graphics.drawString(this.font, Component.translatable("menu.autobots.wall_color"), x, y, AutobotsUi.MUTED, false);
        swatchX = x + 110;
        wallSwatchY = y - 2;
        drawSwatches(graphics, swatchX, wallSwatchY, AutobotsClientConfig.wallRgb());
        y += 20;
        wallSliderX = x + 108;
        wallSliderY = y;
        wallSliderW = 120;
        drawSlider(graphics, x, y, wallSliderX, wallSliderW, AutobotsClientConfig.WALL_TRANSPARENCY.get(), 100, Component.translatable("menu.autobots.wall_transparency"));
        placeField(wallPercent, wallSliderX + wallSliderW + 8, y - 4);
        graphics.drawString(this.font, "%", wallPercent.getX() + 34, y, AutobotsUi.TEXT, false);
        y += 22;
        graphics.drawString(this.font, Component.translatable("menu.autobots.edge_color"), x, y, AutobotsUi.MUTED, false);
        edgeSwatchY = y - 2;
        drawSwatches(graphics, swatchX, edgeSwatchY, AutobotsClientConfig.edgeRgb());
        y += 20;
        edgeSliderX = x + 108;
        edgeSliderY = y;
        edgeSliderW = 120;
        drawSlider(graphics, x, y, edgeSliderX, edgeSliderW, AutobotsClientConfig.EDGE_OPACITY.get(), 100, Component.translatable("menu.autobots.edge_opacity"));
        placeField(edgePercent, edgeSliderX + edgeSliderW + 8, y - 4);
        graphics.drawString(this.font, "%", edgePercent.getX() + 34, y, AutobotsUi.TEXT, false);
        y += 22;
        graphics.drawString(this.font, Component.translatable("menu.autobots.corner_markers"), x, y, AutobotsUi.MUTED, false);
        boolean marks = AutobotsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.autobots.on" : "menu.autobots.off");
        markerW = Math.max(36, this.font.width(state) + 16);
        markerX = x + 108;
        markerY = y - 3;
        graphics.fill(markerX, markerY, markerX + markerW, markerY + 16, marks ? 0xFF24384A : 0xFF18222C);
        AutobotsUi.border(graphics, markerX, markerY, markerW, 16, marks ? AutobotsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, state, markerX + markerW / 2, markerY + 4, AutobotsUi.TEXT);
        y += 22;
        graphics.drawString(this.font, Component.translatable("menu.autobots.place_offset"), x, y, AutobotsUi.TEXT, false);
        y += 12;
        graphics.drawString(this.font, Component.translatable("menu.autobots.place_hint"), x, y, AutobotsUi.MUTED, false);
        y += 16;
        drawOffset(graphics, x, y, "X", placeX);
        drawOffset(graphics, x + 70, y, "Y", placeY);
        drawOffset(graphics, x + 140, y, "Z", placeZ);
    }

    private void drawSwatches(GuiGraphics graphics, int x, int y, int selected) {
        for (int i = 0; i < AutobotsClientConfig.COLORS.length; i++) {
            int color = AutobotsClientConfig.COLORS[i];
            int sx = x + i * 16;
            graphics.fill(sx, y, sx + 12, y + 12, 0xFF000000 | color);
            if (color == selected) {
                AutobotsUi.border(graphics, sx - 1, y - 1, 14, 14, 0xFFFFFFFF);
            }
        }
    }

    private void drawSlider(GuiGraphics graphics, int labelX, int y, int x, int width, int along, int span, Component label) {
        graphics.drawString(this.font, label, labelX, y, AutobotsUi.MUTED, false);
        int trackY = y + 2;
        graphics.fill(x - 1, trackY - 1, x + width + 1, trackY + 9, 0xFF8EA0B0);
        graphics.fill(x, trackY, x + width, trackY + 8, 0xFF101820);
        int filled = (int) Math.round(width * (along / (double) Math.max(1, span)));
        graphics.fill(x, trackY, x + Math.max(0, Math.min(width, filled)), trackY + 8, 0xFFC47A3A);
        int knob = x + (int) ((width - 8) * (along / (double) Math.max(1, span)));
        graphics.fill(knob, trackY - 2, knob + 8, trackY + 10, 0xFFF2D48A);
        AutobotsUi.border(graphics, knob, trackY - 2, 8, 12, 0xFFFFFFFF);
    }

    private void placeField(EditBox box, int x, int y) {
        box.setX(x);
        box.setY(y);
        box.setWidth(28);
        box.setHeight(14);
    }

    private void drawOffset(GuiGraphics graphics, int x, int y, String label, EditBox box) {
        graphics.drawString(this.font, label, x, y + 3, AutobotsUi.MUTED, false);
        placeField(box, x + 12, y);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!AutobotsClientState.libraryKnown) {
            graphics.drawString(this.font, Component.translatable("menu.autobots.loading"), listX + 6, listY + 8, AutobotsUi.MUTED, false);
            return;
        }
        if (filtered.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("menu.autobots.empty"), listX + 6, listY + 8, AutobotsUi.MUTED, false);
            return;
        }
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int row = 0;
        for (int i = scroll; i < filtered.size() && row < visibleRows(); i++) {
            BlueprintPayloads.Summary summary = filtered.get(i);
            int y = listY + row * ROW;
            boolean selected = summary.id().equals(AutobotsClientState.selected);
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW;
            if (selected || hover) {
                graphics.fill(listX, y, listX + listW - 4, y + ROW - 1, selected ? 0x664A78B0 : 0x332A3C4C);
            }
            if (selected) {
                graphics.fill(listX, y, listX + 2, y + ROW - 1, AutobotsUi.BLUE);
            }
            int swatch = 0xFF000000 | (0x5A6E82 + (summary.name().hashCode() & 0x3F4F5F));
            graphics.fill(listX + 6, y + 5, listX + 12, y + 11, swatch);
            String name = this.font.plainSubstrByWidth(summary.name(), listW - 58);
            graphics.drawString(this.font, name, listX + 16, y + 5, AutobotsUi.TEXT, false);
            String size = summary.sizeX() + "×" + summary.sizeY() + "×" + summary.sizeZ();
            graphics.drawString(this.font, size, listX + listW - 8 - this.font.width(size), y + 5, AutobotsUi.MUTED, false);
            row++;
        }
        graphics.disableScissor();
        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        if (maxScroll > 0) {
            int track = listH - 4;
            int thumb = Math.max(12, track * visibleRows() / filtered.size());
            int thumbY = listY + 2 + (track - thumb) * scroll / maxScroll;
            graphics.fill(listX + listW - 3, thumbY, listX + listW - 1, thumbY + thumb, AutobotsUi.COPPER);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        BlueprintPayloads.Summary summary = selected();
        modelX = detailX + 4;
        modelY = detailY + 4;
        modelW = Math.max(8, detailW - 8);
        modelH = Math.max(8, detailSplit - detailY - 10);
        if (summary == null) {
            graphics.drawString(this.font, Component.translatable("menu.autobots.detail_empty"), detailX + 4, detailY + 8, AutobotsUi.MUTED, false);
            return;
        }
        if (!summary.id().equals(spinId)) {
            spinId = summary.id();
            yaw = 35.0F;
            pitch = 28.0F;
        }
        if (!spinning) {
            yaw += 0.35F;
        }
        ensureModel(summary);
        boolean ready = summary.id().equals(AutobotsClientState.editId);
        if (ready && !AutobotsClientState.editGhosts.isEmpty()) {
            BlueprintModelView.render(graphics, modelX, modelY, modelW, modelH, AutobotsClientState.editGhosts, AutobotsClientState.editX, AutobotsClientState.editY, AutobotsClientState.editZ, yaw, pitch);
        } else if (!ready) {
            graphics.drawCenteredString(this.font, Component.translatable("menu.autobots.model_loading"), modelX + modelW / 2, modelY + modelH / 2, AutobotsUi.MUTED);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("menu.autobots.materials_empty"), modelX + modelW / 2, modelY + modelH / 2, AutobotsUi.MUTED);
        }
        graphics.drawCenteredString(this.font, Component.translatable("menu.autobots.spin"), modelX + modelW / 2, modelY + modelH - 10, AutobotsUi.MUTED);
        if (ready && AutobotsClientState.previewSampled) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.autobots.sampled").getString(), modelW), modelX, modelY + 2, AutobotsUi.MUTED, false);
        }
        graphics.fill(detailX + 4, detailSplit, detailX + detailW - 4, detailSplit + 1, 0xFF31404E);
        int info = detailSplit + 4;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary.name(), detailW - 8), detailX + 4, info, AutobotsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("menu.autobots.size", summary.sizeX(), summary.sizeY(), summary.sizeZ()), detailX + 4, info + 10, AutobotsUi.BLUE, false);
        long box = (long) summary.sizeX() * summary.sizeY() * summary.sizeZ();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.autobots.blocks", box, summary.blocks()).getString(), detailW - 8), detailX + 4, info + 20, AutobotsUi.MUTED, false);
        if (!summary.source().isEmpty()) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.autobots.source", summary.source()).getString(), detailW - 8), detailX + 4, info + 30, AutobotsUi.MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.autobots.name"), detailX + 4, detailSplit + 42, AutobotsUi.MUTED, false);
    }

    private void ensureModel(BlueprintPayloads.Summary summary) {
        if (summary.id().equals(AutobotsClientState.editId) || summary.id().equals(modelAsked)) {
            return;
        }
        modelAsked = summary.id();
        PacketDistributor.sendToServer(new BlueprintPayloads.PreviewBlueprint(summary.id()));
    }

    private void openMaterials() {
        BlueprintPayloads.Summary summary = selected();
        if (summary != null) {
            MaterialsScreen.request(summary.id(), 0, 0, 0, this);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 46)) {
            options = false;
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 124)) {
            options = true;
            refresh();
            return true;
        }
        if (options && button == 0) {
            if (pickSwatch(mouseX, mouseY, wallSwatchY, true) || pickSwatch(mouseX, mouseY, edgeSwatchY, false)) {
                return true;
            }
            if (mouseX >= markerX && mouseX < markerX + markerW && mouseY >= markerY && mouseY < markerY + 16) {
                AutobotsClientConfig.setCornerMarkers(!AutobotsClientConfig.CORNER_MARKERS.get());
                return true;
            }
            if (hitSlider(mouseX, mouseY, wallSliderX, wallSliderY, wallSliderW)) {
                drag = 1;
                applyDrag(mouseX);
                return true;
            }
            if (hitSlider(mouseX, mouseY, edgeSliderX, edgeSliderY, edgeSliderW)) {
                drag = 2;
                applyDrag(mouseX);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && insideModel(mouseX, mouseY)) {
            spinning = true;
            return true;
        }
        if (button == 0 && insideList(mouseX, mouseY) && !filtered.isEmpty()) {
            int index = scroll + (int) ((mouseY - listY) / ROW);
            if (index >= 0 && index < filtered.size()) {
                UUID id = filtered.get(index).id();
                long now = System.currentTimeMillis();
                if (id.equals(AutobotsClientState.selected) && now - lastClick < 350) {
                    build();
                }
                AutobotsClientState.selected = id;
                if (!id.equals(confirmDelete)) {
                    confirmDelete = null;
                }
                lastClick = now;
                refresh();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drag != 0 && button == 0) {
            applyDrag(mouseX);
            return true;
        }
        if (spinning && button == 0) {
            yaw += (float) dragX * 0.75F;
            pitch = Mth.clamp(pitch + (float) dragY * 0.45F, -70.0F, 70.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            spinning = false;
        }
        if (drag != 0) {
            applyDrag(mouseX);
            drag = 0;
            AutobotsClientConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insideModel(double mouseX, double mouseY) {
        return !options && selected() != null && mouseX >= modelX && mouseX < modelX + modelW && mouseY >= modelY && mouseY < modelY + modelH;
    }

    private boolean hitTask(double mouseX, double mouseY, int y) {
        return mouseX >= panelX + 14 && mouseX < panelX + 114 && mouseY >= y && mouseY < y + 22;
    }

    private boolean hitSlider(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x - 4 && mouseX < x + width + 4 && mouseY >= y - 4 && mouseY < y + 16;
    }

    private boolean pickSwatch(double mouseX, double mouseY, int y, boolean wall) {
        if (mouseY < y || mouseY >= y + 12) {
            return false;
        }
        for (int i = 0; i < AutobotsClientConfig.COLORS.length; i++) {
            int x = swatchX + i * 16;
            if (mouseX >= x && mouseX < x + 12) {
                if (wall) {
                    AutobotsClientConfig.setWallRgb(AutobotsClientConfig.COLORS[i]);
                } else {
                    AutobotsClientConfig.setEdgeRgb(AutobotsClientConfig.COLORS[i]);
                }
                return true;
            }
        }
        return false;
    }

    private void applyDrag(double mouseX) {
        if (drag == 1) {
            AutobotsClientConfig.setWallTransparency(along(mouseX, wallSliderX, wallSliderW, 0, 100));
            showPercent(wallPercent, AutobotsClientConfig.WALL_TRANSPARENCY.get());
        } else if (drag == 2) {
            AutobotsClientConfig.setEdgeOpacity(along(mouseX, edgeSliderX, edgeSliderW, 0, 100));
            showPercent(edgePercent, AutobotsClientConfig.EDGE_OPACITY.get());
        }
    }

    private EditBox percentBox(java.util.function.IntConsumer apply) {
        EditBox box = new EditBox(this.font, 0, 0, 28, 14, Component.empty());
        box.setMaxLength(3);
        box.setFilter(text -> text.matches("\\d{0,3}"));
        box.setResponder(value -> {
            if (suppressOptions || value.isEmpty()) {
                return;
            }
            try {
                apply.accept(Integer.parseInt(value));
                AutobotsClientConfig.save();
            } catch (NumberFormatException ignored) {
                return;
            }
        });
        addRenderableWidget(box);
        return box;
    }

    private EditBox offsetBox(int axis) {
        EditBox box = new EditBox(this.font, 0, 0, 36, 14, Component.empty());
        box.setMaxLength(3);
        box.setFilter(text -> text.matches("-?\\d{0,2}"));
        box.setResponder(value -> applyOffset(axis, value));
        addRenderableWidget(box);
        return box;
    }

    private void applyOffset(int axis, String value) {
        if (suppressOptions || value.isEmpty() || value.equals("-")) {
            return;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return;
        }
        int x = axis == 0 ? parsed : AutobotsClientConfig.PLACE_X.get();
        int y = axis == 1 ? parsed : AutobotsClientConfig.PLACE_Y.get();
        int z = axis == 2 ? parsed : AutobotsClientConfig.PLACE_Z.get();
        AutobotsClientConfig.setPlace(x, y, z);
    }

    private void showPercent(EditBox box, int value) {
        if (box.isFocused()) {
            return;
        }
        suppressOptions = true;
        box.setValue(Integer.toString(value));
        suppressOptions = false;
    }

    private void showOffset(EditBox box, int value) {
        if (box.isFocused()) {
            return;
        }
        suppressOptions = true;
        box.setValue(Integer.toString(value));
        suppressOptions = false;
    }

    private static int along(double mouseX, int x, int width, int min, int max) {
        double t = Mth.clamp((mouseX - x) / width, 0.0D, 1.0D);
        return min + (int) Math.round(t * (max - min));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideList(mouseX, mouseY)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, filtered.size() - visibleRows()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void newBlueprint() {
        AutobotsClientState.beginCaptureLocal();
        PacketDistributor.sendToServer(new BlueprintPayloads.StartCapture());
        this.minecraft.setScreen(null);
    }

    private void edit() {
        BlueprintPayloads.Summary summary = selected();
        if (summary == null) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.BeginEdit(summary.id()));
    }

    private void build() {
        BlueprintPayloads.Summary summary = selected();
        if (summary == null || AutobotsClientState.busy) {
            return;
        }
        AutobotsClientState.armBuild(summary);
        PacketDistributor.sendToServer(new BlueprintPayloads.BeginBuild(summary.id()));
        this.minecraft.setScreen(null);
    }

    private void delete() {
        BlueprintPayloads.Summary summary = selected();
        if (summary == null) {
            return;
        }
        if (summary.id().equals(confirmDelete)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.DeleteBlueprint(summary.id()));
            confirmDelete = null;
            if (summary.id().equals(AutobotsClientState.selected)) {
                AutobotsClientState.selected = null;
            }
        } else {
            confirmDelete = summary.id();
        }
        refresh();
    }

    private void rename() {
        BlueprintPayloads.Summary summary = selected();
        if (summary == null) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.RenameBlueprint(summary.id(), rename.getValue()));
    }

    private void discard() {
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        AutobotsClientState.clearLocal();
        refresh();
    }

    private void cancelPreview() {
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        AutobotsClientState.clearLocal();
        refresh();
    }

    private BlueprintPayloads.Summary selected() {
        if (AutobotsClientState.selected == null) {
            return null;
        }
        for (BlueprintPayloads.Summary summary : filtered) {
            if (summary.id().equals(AutobotsClientState.selected)) {
                return summary;
            }
        }
        return null;
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW);
    }

    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;
    }
}
