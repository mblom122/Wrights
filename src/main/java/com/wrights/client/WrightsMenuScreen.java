package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.drone.Wrightports;
import com.wrights.network.BlueprintPayloads;

public class WrightsMenuScreen extends Screen {
    private static final int ROW = 18;
    private static final int PAGE_BUILDING = 0;
    private static final int PAGE_JOBS = 1;
    private static final int PAGE_OPTIONS = 2;
    private static int savedPage = PAGE_BUILDING;
    private static int savedScroll;
    private static int savedJobScroll;
    private static int savedHistoryScroll;
    private static int savedOptionScroll;
    private static String savedSearch = "";

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
    private WrightsButton newButton;
    private WrightsButton editButton;
    private WrightsButton deleteButton;
    private WrightsButton buildButton;
    private WrightsButton renameButton;
    private WrightsButton discardButton;
    private WrightsButton resumeButton;
    private WrightsButton previewButton;
    private WrightsButton stopButton;
    private WrightsButton materialsButton;
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
    private boolean jobsPage;
    private int jobScroll;
    private int historyScroll;
    private final List<BlueprintPayloads.JobLine> workingJobs = new ArrayList<>();
    private final List<BlueprintPayloads.JobLine> finishedJobs = new ArrayList<>();
    private int workHeaderY;
    private int workListY;
    private int workRows;
    private int workGroupY;
    private int workGroupH;
    private int doneHeaderY;
    private int doneListY;
    private int doneRows;
    private int doneGroupY;
    private int doneGroupH;
    private int jobX;
    private int jobW;
    private int workPauseRight;
    private int workRemoveRight;
    private int doneRemoveRight;
    private int workPauseWidth;
    private int workRemoveWidth;
    private int doneRemoveWidth;
    private int manageAllWidth;
    private int manageAllRight;
    private int manageAllY;
    private boolean workAllPaused;
    private int drag;
    private int wallSliderX;
    private int wallSliderY;
    private int wallSliderW;
    private int edgeSliderX;
    private int edgeSliderY;
    private int edgeSliderW;
    private int ghostSliderX;
    private int ghostSliderY;
    private int ghostSliderW;
    private int ghostHintY;
    private int swatchX;
    private int wallSwatchY;
    private int edgeSwatchY;
    private int markerX;
    private int markerY;
    private int markerW;
    private int instantX;
    private int instantY;
    private int instantW;
    private int optionScroll;
    private int optionViewX;
    private int optionViewY;
    private int optionViewW;
    private int optionViewH;
    private int optionContentH;
    private int optionThumbY;
    private int optionThumbH;
    private boolean optionDragging;
    private int optionDragGrab;
    private int optionTitleY;
    private int optionPreviewX;
    private int optionPreviewY;
    private int optionHintY;
    private int droneY;
    private int droneX;
    private int droneW;
    private int droneMinusX;
    private int dronePlusX;
    private int droneHintY;
    private int buildMenuX;
    private int buildMenuY;
    private int buildMenuW;
    private int buildMenuHintY;
    private int optionPlaceY;
    private int optionPlaceHintY;
    private int optionOffsetY;
    private EditBox wallPercent;
    private EditBox edgePercent;
    private EditBox ghostPercent;
    private EditBox placeX;
    private EditBox placeY;
    private EditBox placeZ;
    private boolean suppressOptions;
    private String searchText = "";
    private boolean applyingSearch;

    public WrightsMenuScreen() {
        super(Component.translatable("menu.wrights.title"));
        if (WrightsClientState.mode == WrightsClientState.Mode.JOBS) {
            jobsPage = true;
            options = false;
        } else if (WrightsClientState.mode == WrightsClientState.Mode.BUILD
                || WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                || WrightsClientState.mode == WrightsClientState.Mode.SAVING) {
            jobsPage = false;
            options = false;
        } else {
            jobsPage = savedPage == PAGE_JOBS;
            options = savedPage == PAGE_OPTIONS;
        }
        scroll = savedScroll;
        jobScroll = savedJobScroll;
        historyScroll = savedHistoryScroll;
        optionScroll = savedOptionScroll;
        searchText = savedSearch;
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(this.font, listX, panelY + 36, listW, 16, Component.translatable("menu.wrights.search"));
        search.setHint(Component.translatable("menu.wrights.search"));
        search.setMaxLength(48);
        search.setResponder(value -> {
            searchText = value;
            if (applyingSearch) {
                return;
            }
            scroll = 0;
            refresh();
        });
        applyingSearch = true;
        search.setValue(searchText);
        applyingSearch = false;
        addRenderableWidget(search);

        rename = new EditBox(this.font, detailX + 4, detailSplit + 52, Math.max(40, detailW - 86), 14, Component.translatable("screen.wrights.name"));
        rename.setMaxLength(48);
        addRenderableWidget(rename);

        int y = panelY + panelH - 26;
        int buttonW = 58;
        newButton = add(new WrightsButton(listX, y, buttonW, 18, Component.translatable("menu.wrights.new"), button -> newBlueprint()));
        editButton = add(new WrightsButton(listX + 62, y, buttonW, 18, Component.translatable("menu.wrights.edit"), button -> edit()));
        deleteButton = add(new WrightsButton(listX + 124, y, buttonW, 18, Component.translatable("menu.wrights.delete"), button -> delete(), true));
        buildButton = add(new WrightsButton(detailX + detailW - 120, y, buttonW, 18, Component.translatable("menu.wrights.build"), button -> build()));
        addRenderableWidget(new WrightsButton(detailX + detailW - buttonW, y, buttonW, 18, Component.translatable("menu.wrights.close"), button -> onClose()));
        renameButton = add(new WrightsButton(detailX + Math.max(48, detailW - 78), detailSplit + 52, 74, 14, Component.translatable("menu.wrights.rename"), button -> rename()));
        materialsButton = add(new WrightsButton(detailX + 4, detailSplit + 72, Math.max(40, detailW - 8), 16, Component.translatable("menu.wrights.materials"), button -> openMaterials()));
        stopButton = add(new WrightsButton(detailX + 4, detailSplit + 92, Math.max(40, detailW - 8), 16, Component.translatable("menu.wrights.stop"), button -> PacketDistributor.sendToServer(new BlueprintPayloads.StopJob())));
        discardButton = add(new WrightsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.wrights.discard"), button -> discard()));
        resumeButton = add(new WrightsButton(panelX + 14, panelY + panelH - 68, 92, 16, Component.translatable("menu.wrights.resume"), button -> closeKeepingMode()));
        previewButton = add(new WrightsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.wrights.cancel_preview"), button -> cancelPreview()));
        wallPercent = percentBox(value -> WrightsClientConfig.setWallTransparency(value));
        edgePercent = percentBox(value -> WrightsClientConfig.setEdgeOpacity(value));
        ghostPercent = percentBox(value -> WrightsClientConfig.setGhostTransparency(value));
        placeX = offsetBox(0);
        placeY = offsetBox(1);
        placeZ = offsetBox(2);
        refresh();
    }

    private boolean preserveMode;

    @Override
    public void removed() {
        savedPage = jobsPage ? PAGE_JOBS : options ? PAGE_OPTIONS : PAGE_BUILDING;
        savedScroll = scroll;
        savedJobScroll = jobScroll;
        savedHistoryScroll = historyScroll;
        savedOptionScroll = optionScroll;
        savedSearch = searchText == null ? "" : searchText;
        if (!preserveMode) {
            if (jobsPage) {
                WrightsClientState.enterJobs();
            } else if (!options) {
                WrightsClientState.enterBuildMode();
            }
        }
        super.removed();
    }

    private <T extends WrightsButton> T add(T button) {
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
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (query.isEmpty() || summary.name().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(summary);
            }
        }
        if (WrightsClientState.selected != null && filtered.stream().noneMatch(entry -> entry.id().equals(WrightsClientState.selected))) {
            WrightsClientState.selected = null;
            confirmDelete = null;
        }
        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        scroll = Mth.clamp(scroll, 0, maxScroll);
        BlueprintPayloads.Summary selected = selected();
        boolean has = selected != null;
        editButton.active = has;
        deleteButton.active = has;
        buildButton.active = has;
        renameButton.active = has;
        deleteButton.setMessage(Component.translatable(has && selected.id().equals(confirmDelete) ? "menu.wrights.delete_confirm" : "menu.wrights.delete"));
        if (has && !rename.isFocused()) {
            rename.setValue(selected.name());
        }
        boolean capture = WrightsClientState.mode == WrightsClientState.Mode.CAPTURE;
        boolean building = !options && !jobsPage;
        search.visible = building;
        rename.visible = building;
        newButton.visible = building;
        editButton.visible = building;
        deleteButton.visible = building;
        buildButton.visible = building;
        renameButton.visible = building;
        discardButton.visible = building && (capture || WrightsClientState.mode == WrightsClientState.Mode.SAVING);
        resumeButton.visible = building && capture;
        if (previewButton != null) {
            previewButton.visible = building && WrightsClientState.mode == WrightsClientState.Mode.BUILD;
        }
        if (stopButton != null) {
            stopButton.visible = building && WrightsClientState.busy;
            stopButton.active = WrightsClientState.busy;
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
        ghostPercent.visible = showOptions;
        placeX.visible = showOptions;
        placeY.visible = showOptions;
        placeZ.visible = showOptions;
        if (showOptions) {
            showPercent(wallPercent, WrightsClientConfig.WALL_TRANSPARENCY.get());
            showPercent(edgePercent, WrightsClientConfig.EDGE_OPACITY.get());
            showPercent(ghostPercent, WrightsClientConfig.GHOST_TRANSPARENCY.get());
            showOffset(placeX, WrightsClientConfig.PLACE_X.get());
            showOffset(placeY, WrightsClientConfig.PLACE_Y.get());
            showOffset(placeZ, WrightsClientConfig.PLACE_Z.get());
            layoutOptions();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        if (options || jobsPage) {
            int x = listX - 2;
            int width = panelX + panelW - 12 - x;
            int top = jobsPage ? jobsInsetY() : panelY + 34;
            int height = jobsPage ? jobsInsetH() : panelH - 68;
            WrightsUi.inset(graphics, x, top, width, height);
            return;
        }
        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
        WrightsUi.inset(graphics, detailX - 2, detailY - 2, detailW + 4, listH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (options) {
            layoutOptions();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("menu.wrights.title"), panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.subtitle"), panelX + 16, panelY + 22, WrightsUi.MUTED, false);
        drawTask(graphics, Component.translatable("menu.wrights.building"), !options && !jobsPage, false, panelY + 46);
        drawTask(graphics, Component.translatable("menu.wrights.jobs"), jobsPage, false, panelY + 72);
        drawTask(graphics, Component.translatable("menu.wrights.mining"), false, true, panelY + 98);
        drawTask(graphics, Component.translatable("menu.wrights.hauling"), false, true, panelY + 124);
        drawTask(graphics, Component.translatable("menu.wrights.options"), options, false, panelY + 150);
        if (options) {
            renderOptions(graphics);
            return;
        }
        if (jobsPage) {
            renderJobs(graphics, mouseX, mouseY);
            return;
        }

        graphics.drawString(this.font, Component.translatable("menu.wrights.blueprints"), listX, panelY + 24, WrightsUi.TEXT, false);
        if (WrightsClientState.busy) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.busy"), detailX, panelY + 24, WrightsUi.COPPER, false);
        }
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
    }

    private void drawTask(GuiGraphics graphics, Component label, boolean active, boolean soon, int y) {
        int x = panelX + 14;
        int color = active ? 0xFF24384A : 0xFF18222C;
        graphics.fill(x, y, x + 100, y + 22, color);
        if (active) {
            graphics.fill(x, y, x + 2, y + 22, WrightsUi.COPPER);
        }
        graphics.drawString(this.font, label, x + 8, y + 7, active ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        if (soon) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.soon"), x + 68, y + 7, 0xFF667480, false);
        }
    }

    private int optionY(int local) {
        return optionViewY + local - optionScroll;
    }

    private void layoutOptions() {
        optionViewX = listX - 2;
        optionViewY = panelY + 38;
        optionViewW = panelX + panelW - 16 - optionViewX;
        optionViewH = panelH - 76;
        int x = optionViewX + 10;
        int local = 6;
        optionTitleY = optionY(local);
        optionPreviewX = x + 220;
        optionPreviewY = optionY(local);
        local = 30;
        wallSwatchY = optionY(local - 2);
        local += 20;
        wallSliderX = x + 108;
        wallSliderY = optionY(local);
        wallSliderW = 120;
        placeField(wallPercent, wallSliderX + wallSliderW + 8, wallSliderY - 4);
        local += 22;
        edgeSwatchY = optionY(local - 2);
        local += 20;
        edgeSliderX = x + 108;
        edgeSliderY = optionY(local);
        edgeSliderW = 120;
        placeField(edgePercent, edgeSliderX + edgeSliderW + 8, edgeSliderY - 4);
        local += 22;
        ghostSliderX = x + 108;
        ghostSliderY = optionY(local);
        ghostSliderW = 120;
        placeField(ghostPercent, ghostSliderX + ghostSliderW + 8, ghostSliderY - 4);
        local += 14;
        ghostHintY = optionY(local);
        local += 20;
        boolean marks = WrightsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.wrights.on" : "menu.wrights.off");
        markerW = Math.max(36, this.font.width(state) + 16);
        markerX = x + 108;
        markerY = optionY(local - 3);
        local += 22;
        boolean paceChoice = creativeVisit();
        boolean instant = paceChoice && WrightsClientConfig.INSTANT_JOBS.get();
        Component pace = Component.translatable(instant ? "menu.wrights.instant" : "menu.wrights.drone_pace");
        instantW = Math.max(52, this.font.width(pace) + 16);
        instantX = x + 108;
        instantY = optionY(local - 3);
        local += 14;
        optionHintY = optionY(local);
        local += 20;
        boolean droneMax = WrightsClientConfig.DRONE_MAX.get();
        Component droneMode = Component.translatable(droneMax ? "menu.wrights.drone_max" : "menu.wrights.drone_fixed");
        droneW = Math.max(52, this.font.width(droneMode) + 16);
        droneX = x + 108;
        droneY = optionY(local - 3);
        droneMinusX = droneX + droneW + 8;
        dronePlusX = droneMinusX + 36;
        local += 16;
        droneHintY = optionY(local);
        local += 16;
        Component menuState = Component.translatable(WrightsClientConfig.BUILD_MENU.get() ? "menu.wrights.on" : "menu.wrights.off");
        buildMenuW = Math.max(36, this.font.width(menuState) + 16);
        buildMenuX = x + 108;
        buildMenuY = optionY(local - 3);
        local += 14;
        buildMenuHintY = optionY(local);
        local += 16;
        optionPlaceY = optionY(local);
        local += 12;
        optionPlaceHintY = optionY(local);
        local += 22;
        optionOffsetY = optionY(local);
        placeField(placeX, x + 12, optionOffsetY);
        placeField(placeY, x + 82, optionOffsetY);
        placeField(placeZ, x + 152, optionOffsetY);
        swatchX = x + 110;
        optionContentH = local + 22;
        int maxScroll = Math.max(0, optionContentH - optionViewH);
        int clamped = Mth.clamp(optionScroll, 0, maxScroll);
        if (clamped != optionScroll) {
            optionScroll = clamped;
            layoutOptions();
            return;
        }
        showOptionBox(wallPercent);
        showOptionBox(edgePercent);
        showOptionBox(ghostPercent);
        showOptionBox(placeX);
        showOptionBox(placeY);
        showOptionBox(placeZ);
        if (maxScroll > 0) {
            optionThumbH = Math.max(16, optionViewH * optionViewH / optionContentH);
            optionThumbY = optionViewY + (optionViewH - optionThumbH) * optionScroll / maxScroll;
        } else {
            optionThumbH = optionViewH;
            optionThumbY = optionViewY;
        }
    }

    private void showOptionBox(EditBox box) {
        int top = box.getY();
        int bottom = top + box.getHeight();
        box.visible = options && top >= optionViewY && bottom <= optionViewY + optionViewH;
    }

    private void renderOptions(GuiGraphics graphics) {
        int x = optionViewX + 10;
        graphics.enableScissor(optionViewX, optionViewY, optionViewX + optionViewW - 6, optionViewY + optionViewH);
        graphics.drawString(this.font, Component.translatable("menu.wrights.options_title"), x, optionTitleY, WrightsUi.TEXT, false);
        int preview = (Math.round(WrightsClientConfig.wallAlpha() * 255.0F) << 24) | WrightsClientConfig.wallRgb();
        graphics.fill(optionPreviewX, optionPreviewY, optionPreviewX + 48, optionPreviewY + 28, preview);
        WrightsUi.border(graphics, optionPreviewX, optionPreviewY, 48, 28, 0xFF000000 | WrightsClientConfig.edgeRgb());
        graphics.drawString(this.font, Component.translatable("menu.wrights.wall_color"), x, wallSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, wallSwatchY, WrightsClientConfig.wallRgb());
        drawSlider(graphics, x, wallSliderY, wallSliderX, wallSliderW, WrightsClientConfig.WALL_TRANSPARENCY.get(), 100, Component.translatable("menu.wrights.wall_transparency"));
        graphics.drawString(this.font, "%", wallPercent.getX() + 34, wallSliderY, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.edge_color"), x, edgeSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, edgeSwatchY, WrightsClientConfig.edgeRgb());
        drawSlider(graphics, x, edgeSliderY, edgeSliderX, edgeSliderW, WrightsClientConfig.EDGE_OPACITY.get(), 100, Component.translatable("menu.wrights.edge_opacity"));
        graphics.drawString(this.font, "%", edgePercent.getX() + 34, edgeSliderY, WrightsUi.TEXT, false);
        drawSlider(graphics, x, ghostSliderY, ghostSliderX, ghostSliderW, WrightsClientConfig.GHOST_TRANSPARENCY.get(), 100, Component.translatable("menu.wrights.ghost_transparency"));
        graphics.drawString(this.font, "%", ghostPercent.getX() + 34, ghostSliderY, WrightsUi.TEXT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.ghost_hint").getString(), optionViewW - 28), x, ghostHintY, 0xFF667480, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.corner_markers"), x, markerY + 3, WrightsUi.MUTED, false);
        boolean marks = WrightsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.wrights.on" : "menu.wrights.off");
        graphics.fill(markerX, markerY, markerX + markerW, markerY + 16, marks ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, markerX, markerY, markerW, 16, marks ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, state, markerX + markerW / 2, markerY + 4, WrightsUi.TEXT);
        boolean paceChoice = creativeVisit();
        boolean instant = paceChoice && WrightsClientConfig.INSTANT_JOBS.get();
        graphics.drawString(this.font, Component.translatable("menu.wrights.build_speed"), x, instantY + 3, paceChoice ? WrightsUi.MUTED : 0xFF667480, false);
        Component pace = Component.translatable(instant ? "menu.wrights.instant" : "menu.wrights.drone_pace");
        int fill = paceChoice ? (instant ? 0xFF24384A : 0xFF18222C) : 0xFF141C24;
        int edge = paceChoice ? (instant ? WrightsUi.COPPER : 0xFF3D4E5E) : 0xFF3A4652;
        int text = paceChoice ? WrightsUi.TEXT : 0xFF667480;
        graphics.fill(instantX, instantY, instantX + instantW, instantY + 16, fill);
        WrightsUi.border(graphics, instantX, instantY, instantW, 16, edge);
        graphics.drawCenteredString(this.font, pace, instantX + instantW / 2, instantY + 4, text);
        Component hint = Component.translatable(paceChoice ? "menu.wrights.build_speed_hint" : "menu.wrights.build_speed_locked");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(hint.getString(), optionViewW - 28), x, optionHintY, 0xFF667480, false);
        boolean droneMax = WrightsClientConfig.DRONE_MAX.get();
        graphics.drawString(this.font, Component.translatable("menu.wrights.drone_deploy"), x, droneY + 3, WrightsUi.MUTED, false);
        Component droneMode = Component.translatable(droneMax ? "menu.wrights.drone_max" : "menu.wrights.drone_fixed");
        graphics.fill(droneX, droneY, droneX + droneW, droneY + 16, droneMax ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, droneX, droneY, droneW, 16, droneMax ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, droneMode, droneX + droneW / 2, droneY + 4, WrightsUi.TEXT);
        if (!droneMax) {
            graphics.fill(droneMinusX, droneY, droneMinusX + 16, droneY + 16, 0xFF18222C);
            WrightsUi.border(graphics, droneMinusX, droneY, 16, 16, 0xFF3D4E5E);
            graphics.drawCenteredString(this.font, "-", droneMinusX + 8, droneY + 4, WrightsUi.TEXT);
            String count = Integer.toString(WrightsClientConfig.DRONE_COUNT.get());
            graphics.drawCenteredString(this.font, count, droneMinusX + 26, droneY + 4, WrightsUi.TEXT);
            graphics.fill(dronePlusX, droneY, dronePlusX + 16, droneY + 16, 0xFF18222C);
            WrightsUi.border(graphics, dronePlusX, droneY, 16, 16, 0xFF3D4E5E);
            graphics.drawCenteredString(this.font, "+", dronePlusX + 8, droneY + 4, WrightsUi.TEXT);
        }
        Component droneHint = Component.translatable(droneMax ? "menu.wrights.drone_max_hint" : "menu.wrights.drone_fixed_hint");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(droneHint.getString(), optionViewW - 28), x, droneHintY, 0xFF667480, false);
        boolean openMenu = WrightsClientConfig.BUILD_MENU.get();
        graphics.drawString(this.font, Component.translatable("menu.wrights.open_build_menu"), x, buildMenuY + 3, WrightsUi.MUTED, false);
        Component menuState = Component.translatable(openMenu ? "menu.wrights.on" : "menu.wrights.off");
        graphics.fill(buildMenuX, buildMenuY, buildMenuX + buildMenuW, buildMenuY + 16, openMenu ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, buildMenuX, buildMenuY, buildMenuW, 16, openMenu ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, menuState, buildMenuX + buildMenuW / 2, buildMenuY + 4, WrightsUi.TEXT);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.open_build_menu_hint").getString(), optionViewW - 28), x, buildMenuHintY, 0xFF667480, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.place_offset"), x, optionPlaceY, WrightsUi.TEXT, false);
        int hintW = Math.max(40, optionViewW - 16);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.place_hint").getString(), hintW), x, optionPlaceHintY, WrightsUi.MUTED, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.place_axes").getString(), hintW), x, optionPlaceHintY + 10, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "X", x, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Y", x + 70, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Z", x + 140, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.disableScissor();
        if (optionContentH > optionViewH) {
            int barX = optionViewX + optionViewW - 5;
            graphics.fill(barX, optionViewY, barX + 3, optionViewY + optionViewH, 0xFF24303A);
            graphics.fill(barX, optionThumbY, barX + 3, optionThumbY + optionThumbH, WrightsUi.COPPER);
        }
    }

    private void drawSwatches(GuiGraphics graphics, int x, int y, int selected) {
        for (int i = 0; i < WrightsClientConfig.COLORS.length; i++) {
            int color = WrightsClientConfig.COLORS[i];
            int sx = x + i * 16;
            graphics.fill(sx, y, sx + 12, y + 12, 0xFF000000 | color);
            if (color == selected) {
                WrightsUi.border(graphics, sx - 1, y - 1, 14, 14, 0xFFFFFFFF);
            }
        }
    }

    private void drawSlider(GuiGraphics graphics, int labelX, int y, int x, int width, int along, int span, Component label) {
        graphics.drawString(this.font, label, labelX, y, WrightsUi.MUTED, false);
        int trackY = y + 2;
        graphics.fill(x - 1, trackY - 1, x + width + 1, trackY + 9, 0xFF8EA0B0);
        graphics.fill(x, trackY, x + width, trackY + 8, 0xFF101820);
        int filled = (int) Math.round(width * (along / (double) Math.max(1, span)));
        graphics.fill(x, trackY, x + Math.max(0, Math.min(width, filled)), trackY + 8, 0xFFC47A3A);
        int knob = x + (int) ((width - 8) * (along / (double) Math.max(1, span)));
        graphics.fill(knob, trackY - 2, knob + 8, trackY + 10, 0xFFF2D48A);
        WrightsUi.border(graphics, knob, trackY - 2, 8, 12, 0xFFFFFFFF);
    }

    private void placeField(EditBox box, int x, int y) {
        box.setX(x);
        box.setY(y);
        box.setWidth(28);
        box.setHeight(14);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!WrightsClientState.libraryKnown) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.loading"), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        if (filtered.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.empty"), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int row = 0;
        for (int i = scroll; i < filtered.size() && row < visibleRows(); i++) {
            BlueprintPayloads.Summary summary = filtered.get(i);
            int y = listY + row * ROW;
            boolean selected = summary.id().equals(WrightsClientState.selected);
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW;
            if (selected || hover) {
                graphics.fill(listX, y, listX + listW - 4, y + ROW - 1, selected ? 0x664A78B0 : 0x332A3C4C);
            }
            if (selected) {
                graphics.fill(listX, y, listX + 2, y + ROW - 1, WrightsUi.BLUE);
            }
            int swatch = 0xFF000000 | (0x5A6E82 + (summary.name().hashCode() & 0x3F4F5F));
            graphics.fill(listX + 6, y + 5, listX + 12, y + 11, swatch);
            String name = this.font.plainSubstrByWidth(summary.name(), listW - 58);
            graphics.drawString(this.font, name, listX + 16, y + 5, WrightsUi.TEXT, false);
            String size = summary.sizeX() + "×" + summary.sizeY() + "×" + summary.sizeZ();
            graphics.drawString(this.font, size, listX + listW - 8 - this.font.width(size), y + 5, WrightsUi.MUTED, false);
            row++;
        }
        graphics.disableScissor();
        int maxScroll = Math.max(0, filtered.size() - visibleRows());
        if (maxScroll > 0) {
            int track = listH - 4;
            int thumb = Math.max(12, track * visibleRows() / filtered.size());
            int thumbY = listY + 2 + (track - thumb) * scroll / maxScroll;
            graphics.fill(listX + listW - 3, thumbY, listX + listW - 1, thumbY + thumb, WrightsUi.COPPER);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        BlueprintPayloads.Summary summary = selected();
        modelX = detailX + 4;
        modelY = detailY + 4;
        modelW = Math.max(8, detailW - 8);
        modelH = Math.max(8, detailSplit - detailY - 10);
        if (summary == null) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.detail_empty"), detailX + 4, detailY + 8, WrightsUi.MUTED, false);
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
        boolean ready = summary.id().equals(WrightsClientState.editId);
        if (ready && !WrightsClientState.editGhosts.isEmpty()) {
            BlueprintModelView.render(graphics, modelX, modelY, modelW, modelH, WrightsClientState.editGhosts, WrightsClientState.editX, WrightsClientState.editY, WrightsClientState.editZ, yaw, pitch);
        } else if (!ready) {
            graphics.drawCenteredString(this.font, Component.translatable("menu.wrights.model_loading"), modelX + modelW / 2, modelY + modelH / 2, WrightsUi.MUTED);
        } else if (WrightsClientState.previewConcealed) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.preview_hidden"), modelX + modelW / 2, modelY + modelH / 2, WrightsUi.MUTED);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("menu.wrights.materials_empty"), modelX + modelW / 2, modelY + modelH / 2, WrightsUi.MUTED);
        }
        graphics.drawCenteredString(this.font, Component.translatable("menu.wrights.spin"), modelX + modelW / 2, modelY + modelH - 10, WrightsUi.MUTED);
        if (ready && WrightsClientState.previewSampled) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.wrights.sampled").getString(), modelW), modelX, modelY + 2, WrightsUi.MUTED, false);
        }
        graphics.fill(detailX + 4, detailSplit, detailX + detailW - 4, detailSplit + 1, 0xFF31404E);
        int info = detailSplit + 4;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary.name(), detailW - 8), detailX + 4, info, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.size", summary.sizeX(), summary.sizeY(), summary.sizeZ()), detailX + 4, info + 10, WrightsUi.BLUE, false);
        long box = (long) summary.sizeX() * summary.sizeY() * summary.sizeZ();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.blocks", box, summary.blocks()).getString(), detailW - 8), detailX + 4, info + 20, WrightsUi.MUTED, false);
        if (!summary.source().isEmpty()) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.source", summary.source()).getString(), detailW - 8), detailX + 4, info + 30, WrightsUi.MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.name"), detailX + 4, detailSplit + 42, WrightsUi.MUTED, false);
    }

    private void ensureModel(BlueprintPayloads.Summary summary) {
        if (summary.id().equals(WrightsClientState.editId) || summary.id().equals(modelAsked)) {
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
            jobsPage = false;
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 72)) {
            options = false;
            jobsPage = true;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 150)) {
            options = true;
            jobsPage = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (jobsPage && button == 0 && clickJob(mouseX, mouseY)) {
            return true;
        }
        if (options && button == 0) {
            layoutOptions();
            if (hitOptionBar(mouseX, mouseY)) {
                optionDragging = true;
                optionDragGrab = (int) mouseY - optionThumbY;
                return true;
            }
            if (insideOptionView(mouseX, mouseY)) {
                if (pickSwatch(mouseX, mouseY, wallSwatchY, true) || pickSwatch(mouseX, mouseY, edgeSwatchY, false)) {
                    return true;
                }
                if (mouseX >= markerX && mouseX < markerX + markerW && mouseY >= markerY && mouseY < markerY + 16) {
                    WrightsClientConfig.setCornerMarkers(!WrightsClientConfig.CORNER_MARKERS.get());
                    return true;
                }
                if (mouseX >= instantX && mouseX < instantX + instantW && mouseY >= instantY && mouseY < instantY + 16) {
                    if (creativeVisit()) {
                        boolean next = !WrightsClientConfig.INSTANT_JOBS.get();
                        WrightsClientConfig.setInstantJobs(next);
                        PacketDistributor.sendToServer(new BlueprintPayloads.SetPace(next));
                    }
                    return true;
                }
                if (mouseX >= droneX && mouseX < droneX + droneW && mouseY >= droneY && mouseY < droneY + 16) {
                    WrightsClientState.deployChosen = false;
                    WrightsClientConfig.setDroneMax(!WrightsClientConfig.DRONE_MAX.get());
                    WrightsClientState.applyDeploy();
                    return true;
                }
                if (!WrightsClientConfig.DRONE_MAX.get() && mouseY >= droneY && mouseY < droneY + 16) {
                    int step = 0;
                    if (mouseX >= droneMinusX && mouseX < droneMinusX + 16) {
                        step = -1;
                    } else if (mouseX >= dronePlusX && mouseX < dronePlusX + 16) {
                        step = 1;
                    }
                    if (step != 0) {
                        WrightsClientState.deployChosen = false;
                        WrightsClientConfig.setDroneCount(WrightsClientState.stepped(WrightsClientConfig.DRONE_COUNT.get(), step, 1, 64));
                        WrightsClientState.applyDeploy();
                        return true;
                    }
                }
                if (mouseX >= buildMenuX && mouseX < buildMenuX + buildMenuW && mouseY >= buildMenuY && mouseY < buildMenuY + 16) {
                    WrightsClientConfig.setBuildMenu(!WrightsClientConfig.BUILD_MENU.get());
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
                if (hitSlider(mouseX, mouseY, ghostSliderX, ghostSliderY, ghostSliderW)) {
                    drag = 3;
                    applyDrag(mouseX);
                    return true;
                }
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
                if (id.equals(WrightsClientState.selected) && now - lastClick < 350) {
                    build();
                }
                WrightsClientState.selected = id;
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
        if (optionDragging && button == 0) {
            int max = Math.max(0, optionContentH - optionViewH);
            int travel = Math.max(1, optionViewH - optionThumbH);
            optionScroll = Mth.clamp((int) ((mouseY - optionDragGrab - optionViewY) * max / travel), 0, max);
            return true;
        }
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
            optionDragging = false;
        }
        if (drag != 0) {
            applyDrag(mouseX);
            drag = 0;
            WrightsClientConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insideModel(double mouseX, double mouseY) {
        return !options && selected() != null && mouseX >= modelX && mouseX < modelX + modelW && mouseY >= modelY && mouseY < modelY + modelH;
    }

    private boolean insideOptionView(double mouseX, double mouseY) {
        return mouseX >= optionViewX && mouseX < optionViewX + optionViewW && mouseY >= optionViewY && mouseY < optionViewY + optionViewH;
    }

    private boolean hitOptionBar(double mouseX, double mouseY) {
        if (optionContentH <= optionViewH) {
            return false;
        }
        int barX = optionViewX + optionViewW - 8;
        return mouseX >= barX && mouseX < optionViewX + optionViewW && mouseY >= optionViewY && mouseY < optionViewY + optionViewH;
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
        for (int i = 0; i < WrightsClientConfig.COLORS.length; i++) {
            int x = swatchX + i * 16;
            if (mouseX >= x && mouseX < x + 12) {
                if (wall) {
                    WrightsClientConfig.setWallRgb(WrightsClientConfig.COLORS[i]);
                } else {
                    WrightsClientConfig.setEdgeRgb(WrightsClientConfig.COLORS[i]);
                }
                return true;
            }
        }
        return false;
    }

    private void applyDrag(double mouseX) {
        if (drag == 1) {
            WrightsClientConfig.setWallTransparency(along(mouseX, wallSliderX, wallSliderW, 0, 100));
            showPercent(wallPercent, WrightsClientConfig.WALL_TRANSPARENCY.get());
        } else if (drag == 2) {
            WrightsClientConfig.setEdgeOpacity(along(mouseX, edgeSliderX, edgeSliderW, 0, 100));
            showPercent(edgePercent, WrightsClientConfig.EDGE_OPACITY.get());
        } else if (drag == 3) {
            WrightsClientConfig.setGhostTransparency(along(mouseX, ghostSliderX, ghostSliderW, 0, 100));
            showPercent(ghostPercent, WrightsClientConfig.GHOST_TRANSPARENCY.get());
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
                WrightsClientConfig.save();
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
        int x = axis == 0 ? parsed : WrightsClientConfig.PLACE_X.get();
        int y = axis == 1 ? parsed : WrightsClientConfig.PLACE_Y.get();
        int z = axis == 2 ? parsed : WrightsClientConfig.PLACE_Z.get();
        WrightsClientConfig.setPlace(x, y, z);
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

    private static final int JOB_ROW = 58;
    private static final int JOB_HEADER = 20;
    private static final int JOB_GROUP_MIN = 46;

    private int jobsInsetY() {
        return panelY + 38;
    }

    private int jobsInsetH() {
        return panelH - 78;
    }

    private void prepareJobs() {
        workingJobs.clear();
        finishedJobs.clear();
        workAllPaused = true;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.finished()) {
                finishedJobs.add(job);
            } else {
                workingJobs.add(job);
                if (!job.paused()) {
                    workAllPaused = false;
                }
            }
        }
        int insetX = listX - 2;
        int insetW = panelX + panelW - 12 - insetX;
        jobX = insetX + 6;
        jobW = insetW - 12;
        manageAllWidth = jobButtonWidth("menu.wrights.manage_all");
        manageAllRight = jobX + jobW;
        manageAllY = jobsInsetY() + 6;
        int groupsTop = jobsInsetY() + 28;
        int groupsBottom = jobsInsetY() + jobsInsetH() - 6;
        int gap = 8;
        int room = Math.max(JOB_GROUP_MIN * 2 + gap, groupsBottom - groupsTop);
        boolean workOpen = !workingJobs.isEmpty();
        boolean doneOpen = !finishedJobs.isEmpty();
        int workWant = jobGroupHeight(workOpen ? workingJobs.size() : 0);
        int doneWant = jobGroupHeight(doneOpen ? finishedJobs.size() : 0);
        int workBlock;
        int doneBlock;
        if (workWant + doneWant + gap <= room) {
            workBlock = workWant;
            doneBlock = doneWant;
        } else {
            int workMin = jobGroupHeight(workOpen ? 1 : 0);
            int doneMin = jobGroupHeight(doneOpen ? 1 : 0);
            int extra = Math.max(0, room - gap - workMin - doneMin);
            int workDemand = Math.max(0, workWant - workMin);
            int doneDemand = Math.max(0, doneWant - doneMin);
            int demand = Math.max(1, workDemand + doneDemand);
            workBlock = workMin + extra * workDemand / demand;
            doneBlock = room - gap - workBlock;
        }
        workGroupY = groupsTop;
        workGroupH = workBlock;
        workHeaderY = workGroupY + 2;
        workListY = workGroupY + JOB_HEADER + 4;
        workRows = workOpen ? Math.max(1, (workBlock - JOB_HEADER - 8) / JOB_ROW) : 0;
        doneGroupY = workGroupY + workBlock + gap;
        doneGroupH = doneBlock;
        doneHeaderY = doneGroupY + 2;
        doneListY = doneGroupY + JOB_HEADER + 4;
        doneRows = doneOpen ? Math.max(1, (doneBlock - JOB_HEADER - 8) / JOB_ROW) : 0;
        jobScroll = Mth.clamp(jobScroll, 0, Math.max(0, workingJobs.size() - workRows));
        historyScroll = Mth.clamp(historyScroll, 0, Math.max(0, finishedJobs.size() - doneRows));
        int right = jobX + jobW - 6;
        workRemoveWidth = jobButtonWidth("menu.wrights.remove_all");
        workPauseWidth = jobButtonWidth(workAllPaused ? "menu.wrights.resume_all" : "menu.wrights.pause_all");
        workRemoveRight = right;
        workPauseRight = right - workRemoveWidth - 4;
        doneRemoveWidth = workRemoveWidth;
        doneRemoveRight = right;
    }

    private static int jobGroupHeight(int rows) {
        if (rows <= 0) {
            return JOB_GROUP_MIN;
        }
        return JOB_HEADER + 8 + rows * JOB_ROW;
    }

    private int jobButtonWidth(String key) {
        return Math.max(52, this.font.width(Component.translatable(key)) + 10);
    }

    private void renderJobs(GuiGraphics graphics, int mouseX, int mouseY) {
        prepareJobs();
        int[] pool = dronePool();
        String drones = this.font.plainSubstrByWidth(Component.translatable("menu.wrights.drones_pool", pool[0], pool[1]).getString(), Math.max(40, jobW - manageAllWidth - 8));
        graphics.drawString(this.font, drones, jobX, manageAllY + 4, WrightsUi.TEXT, false);
        drawJobButton(graphics, manageAllRight, manageAllY, "menu.wrights.manage_all", mouseX, mouseY, manageAllWidth);
        int rowX = jobX + 4;
        int rowW = jobW - 8;
        drawJobGroup(graphics, jobX, workGroupY, jobW, workGroupH, "menu.wrights.jobs_working", workingJobs.isEmpty() ? "menu.wrights.jobs_working_empty" : null, workingJobs.isEmpty() ? 0 : workPauseWidth + workRemoveWidth + 18);
        if (!workingJobs.isEmpty()) {
            drawJobButton(graphics, workRemoveRight, workHeaderY, "menu.wrights.remove_all", mouseX, mouseY, workRemoveWidth);
            drawJobButton(graphics, workPauseRight, workHeaderY, workAllPaused ? "menu.wrights.resume_all" : "menu.wrights.pause_all", mouseX, mouseY, workPauseWidth);
            for (int row = 0; row < workRows && jobScroll + row < workingJobs.size(); row++) {
                drawJobRow(graphics, mouseX, mouseY, rowX, rowW, workListY + row * JOB_ROW, workingJobs.get(jobScroll + row));
            }
        }
        drawJobGroup(graphics, jobX, doneGroupY, jobW, doneGroupH, "menu.wrights.jobs_finished", finishedJobs.isEmpty() ? "menu.wrights.jobs_finished_empty" : null, finishedJobs.isEmpty() ? 0 : doneRemoveWidth + 14);
        if (!finishedJobs.isEmpty()) {
            drawJobButton(graphics, doneRemoveRight, doneHeaderY, "menu.wrights.remove_all", mouseX, mouseY, doneRemoveWidth);
            for (int row = 0; row < doneRows && historyScroll + row < finishedJobs.size(); row++) {
                drawJobRow(graphics, mouseX, mouseY, rowX, rowW, doneListY + row * JOB_ROW, finishedJobs.get(historyScroll + row));
            }
        }
    }

    private void drawJobGroup(GuiGraphics graphics, int x, int y, int width, int height, String title, String empty, int reserved) {
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF3A4C5C);
        graphics.fill(x + 1, y + 1, x + width - 1, y + JOB_HEADER, 0xFF1C2A36);
        graphics.fill(x + 1, y + 1, x + 3, y + JOB_HEADER, WrightsUi.COPPER);
        graphics.fill(x + 1, y + JOB_HEADER - 1, x + width - 1, y + JOB_HEADER, 0xFF31404E);
        String heading = this.font.plainSubstrByWidth(Component.translatable(title).getString(), Math.max(40, width - reserved - 12));
        graphics.drawString(this.font, heading, x + 10, y + 6, WrightsUi.TEXT, false);
        if (empty != null) {
            graphics.drawString(this.font, Component.translatable(empty), x + 10, y + JOB_HEADER + 6, WrightsUi.MUTED, false);
        }
    }

    private void drawJobRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int width, int y, BlueprintPayloads.JobLine job) {
        boolean working = !job.finished() && !job.saving();
        int buttonY = y + 36;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 54;
        graphics.fill(x, y, x + width, y + 54, hover ? 0xFF24303C : 0xFF18222C);
        String name = this.font.plainSubstrByWidth(job.name(), Math.max(40, width / 2));
        graphics.drawString(this.font, name, x + 6, y + 4, WrightsUi.TEXT, false);
        Component statusText = Component.translatable(jobStatus(job));
        graphics.drawString(this.font, statusText, x + width - 8 - this.font.width(statusText), y + 4, jobColor(job), false);
        BlockPos anchor = job.anchor();
        graphics.drawString(this.font, Component.translatable("menu.wrights.job_anchor", anchor.getX(), anchor.getY(), anchor.getZ()), x + 6, y + 15, WrightsUi.MUTED, false);
        drawProgress(graphics, x + 6, y + 26, Math.max(40, width - 12), job);
        int right = x + width - 4;
        if (working) {
            String count = Component.translatable("menu.wrights.job_drone_count", job.crew().size()).getString();
            graphics.drawString(this.font, count, x + 6, buttonY + 4, WrightsUi.MUTED, false);
            int manageW = jobButtonWidth("menu.wrights.manage_drones");
            drawJobButton(graphics, x + 6 + this.font.width(count) + 6 + manageW, buttonY, "menu.wrights.manage_drones", mouseX, mouseY, manageW);
        }
        right = drawJobButton(graphics, right, buttonY, "menu.wrights.remove", mouseX, mouseY, 52);
        if (creativeVisit()) {
            right = drawJobButton(graphics, right, buttonY, "menu.wrights.visit", mouseX, mouseY, 52);
        }
        right = drawJobButton(graphics, right, buttonY, WrightsClientState.markedJobs.contains(job.id()) ? "menu.wrights.mark_on" : "menu.wrights.mark", mouseX, mouseY, 52);
        if (job.finished()) {
            right = drawJobButton(graphics, right, buttonY, "menu.wrights.redo", mouseX, mouseY, 52);
            if (job.canUndo()) {
                drawJobButton(graphics, right, buttonY, "menu.wrights.undo", mouseX, mouseY, 52);
            }
        } else {
            right = drawJobButton(graphics, right, buttonY, "menu.wrights.stop", mouseX, mouseY, 52);
            drawJobButton(graphics, right, buttonY, job.paused() ? "menu.wrights.resume" : "menu.wrights.pause", mouseX, mouseY, 52);
        }
    }

    private String jobStatus(BlueprintPayloads.JobLine job) {
        if (job.undoing()) {
            return "menu.wrights.undoing";
        }
        if (job.finished()) {
            if (job.stopped()) {
                return "menu.wrights.stopped";
            }
            if (job.undone()) {
                return "menu.wrights.undone";
            }
            return "menu.wrights.completed";
        }
        if (job.saving()) {
            return "menu.wrights.saving";
        }
        if (job.paused()) {
            return "menu.wrights.paused";
        }
        return "menu.wrights.running";
    }

    private int jobColor(BlueprintPayloads.JobLine job) {
        if (job.finished() && job.stopped()) {
            return 0xFFE07A7A;
        }
        if (job.finished() && !job.undone() && !job.undoing()) {
            return 0xFF6FCF97;
        }
        if (job.paused()) {
            return 0xFFE0B060;
        }
        return job.finished() ? WrightsUi.MUTED : WrightsUi.COPPER;
    }

    private void drawProgress(GuiGraphics graphics, int x, int y, int width, BlueprintPayloads.JobLine job) {
        String progress = progressLine(job).getString();
        String skipped = skippedLine(job);
        int skipWidth = skipped.isEmpty() ? 0 : this.font.width(skipped);
        int gap = skipWidth > 0 ? 10 : 0;
        int progressRoom = Math.max(24, width - skipWidth - gap);
        String shown = this.font.plainSubstrByWidth(progress, progressRoom);
        graphics.drawString(this.font, shown, x, y, WrightsUi.TEXT, false);
        if (skipWidth > 0) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(skipped, Math.max(24, width - this.font.width(shown) - gap)), x + this.font.width(shown) + gap, y, 0xFFF0C14A, false);
        }
    }

    private Component progressLine(BlueprintPayloads.JobLine job) {
        int percent = progressPercent(job);
        String left = WrightsClientState.timeLeft(job);
        if (job.finished() || left.isEmpty()) {
            return Component.translatable("menu.wrights.job_percent", percent, job.done(), job.total());
        }
        return Component.translatable("menu.wrights.job_eta", percent, job.done(), job.total(), left);
    }

    private int progressPercent(BlueprintPayloads.JobLine job) {
        if (job.finished() && !job.stopped() && !job.undone() && !job.undoing()) {
            return 100;
        }
        return WrightsClientState.percent(job);
    }

    private String skippedLine(BlueprintPayloads.JobLine job) {
        if (job.skipped() <= 0 && !(job.finished() && !job.undoing() && !job.undone())) {
            return "";
        }
        int total = Math.max(1, job.total());
        int percent = job.skipped() * 100 / total;
        return Component.translatable("menu.wrights.job_skipped", job.skipped(), percent).getString();
    }

    private boolean creativeVisit() {
        return this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator();
    }

    private int drawJobButton(GuiGraphics graphics, int right, int y, String key, int mouseX, int mouseY, int width) {
        int left = right - width;
        boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 16;
        graphics.fill(left, y, right, y + 16, hover ? 0xFF3A5164 : 0xFF24384A);
        Component label = Component.translatable(key);
        String text = this.font.plainSubstrByWidth(label.getString(), width - 4);
        graphics.drawString(this.font, text, left + (width - this.font.width(text)) / 2, y + 4, WrightsUi.TEXT, false);
        return left - 4;
    }

    private boolean clickJob(double mouseX, double mouseY) {
        prepareJobs();
        if (hitWide(mouseX, mouseY, manageAllRight, manageAllY, manageAllWidth)) {
            this.minecraft.setScreen(new DroneRosterScreen(null));
            return true;
        }
        if (!workingJobs.isEmpty()) {
            if (hitWide(mouseX, mouseY, workRemoveRight, workHeaderY, workRemoveWidth)) {
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(new UUID(0L, 0L), BlueprintPayloads.JobControl.REMOVE_ACTIVE));
                return true;
            }
            if (hitWide(mouseX, mouseY, workPauseRight, workHeaderY, workPauseWidth)) {
                int action = workAllPaused ? BlueprintPayloads.JobControl.RESUME_ALL : BlueprintPayloads.JobControl.PAUSE_ALL;
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(new UUID(0L, 0L), action));
                return true;
            }
        }
        if (!finishedJobs.isEmpty() && hitWide(mouseX, mouseY, doneRemoveRight, doneHeaderY, doneRemoveWidth)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(new UUID(0L, 0L), BlueprintPayloads.JobControl.REMOVE_DONE));
            return true;
        }
        if (mouseY >= workListY && mouseY < workListY + workRows * JOB_ROW) {
            int row = (int) ((mouseY - workListY) / JOB_ROW);
            if (row >= 0 && row < workRows && jobScroll + row < workingJobs.size()) {
                return clickJobRow(mouseX, mouseY, workListY + row * JOB_ROW, workingJobs.get(jobScroll + row));
            }
        }
        if (mouseY >= doneListY && mouseY < doneListY + doneRows * JOB_ROW) {
            int row = (int) ((mouseY - doneListY) / JOB_ROW);
            if (row >= 0 && row < doneRows && historyScroll + row < finishedJobs.size()) {
                return clickJobRow(mouseX, mouseY, doneListY + row * JOB_ROW, finishedJobs.get(historyScroll + row));
            }
        }
        return false;
    }

    private boolean clickJobRow(double mouseX, double mouseY, int y, BlueprintPayloads.JobLine job) {
        boolean working = !job.finished() && !job.saving();
        int buttonY = y + 36;
        if (mouseY < buttonY || mouseY >= buttonY + 16) {
            return false;
        }
        int rowX = jobX + 4;
        int rowW = jobW - 8;
        if (working) {
            String count = Component.translatable("menu.wrights.job_drone_count", job.crew().size()).getString();
            int manageW = jobButtonWidth("menu.wrights.manage_drones");
            int manageRight = rowX + 6 + this.font.width(count) + 6 + manageW;
            if (mouseX >= manageRight - manageW && mouseX < manageRight) {
                this.minecraft.setScreen(new JobDroneScreen(job.id(), null));
                return true;
            }
        }
        int right = rowX + rowW - 4;
        if (hitJobButton(mouseX, right, buttonY)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.REMOVE));
            return true;
        }
        right -= 56;
        if (creativeVisit()) {
            if (hitJobButton(mouseX, right, buttonY)) {
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.VISIT));
                return true;
            }
            right -= 56;
        }
        if (hitJobButton(mouseX, right, buttonY)) {
            WrightsClientState.toggleMark(job.id());
            return true;
        }
        right -= 56;
        if (job.finished()) {
            if (hitJobButton(mouseX, right, buttonY)) {
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.REDO));
                return true;
            }
            right -= 56;
            if (job.canUndo() && hitJobButton(mouseX, right, buttonY)) {
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.UNDO));
                return true;
            }
            return false;
        }
        if (hitJobButton(mouseX, right, buttonY)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.STOP));
            return true;
        }
        right -= 56;
        if (hitJobButton(mouseX, right, buttonY)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), job.paused() ? BlueprintPayloads.JobControl.RESUME : BlueprintPayloads.JobControl.PAUSE));
            return true;
        }
        return false;
    }

    private int[] dronePool() {
        int total = this.minecraft.player == null ? 0 : Wrightports.swarm(this.minecraft.player).units().size();
        int working = 0;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && !job.saving()) {
                working += job.crew().size();
            }
        }
        return new int[] {working, total};
    }

    private boolean hitJobButton(double mouseX, int right, int y) {
        return hitWide(mouseX, y, right, y, 52);
    }

    private boolean hitWide(double mouseX, double mouseY, int right, int y, int width) {
        return mouseX >= right - width && mouseX < right && mouseY >= y && mouseY < y + 16;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (jobsPage) {
            prepareJobs();
            int delta = (int) Math.signum(scrollY);
            if (mouseY >= doneGroupY) {
                historyScroll = Mth.clamp(historyScroll - delta, 0, Math.max(0, finishedJobs.size() - doneRows));
            } else {
                jobScroll = Mth.clamp(jobScroll - delta, 0, Math.max(0, workingJobs.size() - workRows));
            }
            return true;
        }
        if (options && insideOptionView(mouseX, mouseY)) {
            int max = Math.max(0, optionContentH - optionViewH);
            optionScroll = Mth.clamp(optionScroll - (int) Math.signum(scrollY) * 16, 0, max);
            return true;
        }
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

    private void closeKeepingMode() {
        preserveMode = true;
        this.minecraft.setScreen(null);
    }

    private void newBlueprint() {
        preserveMode = true;
        WrightsClientState.beginCaptureLocal();
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
        if (summary == null) {
            return;
        }
        WrightsClientState.armBuild(summary);
        PacketDistributor.sendToServer(new BlueprintPayloads.BeginBuild(summary.id()));
        preserveMode = true;
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
            if (summary.id().equals(WrightsClientState.selected)) {
                WrightsClientState.selected = null;
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
        WrightsClientState.clearLocal();
        WrightsClientState.forgetMode();
        refresh();
    }

    private void cancelPreview() {
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        WrightsClientState.clearLocal();
        WrightsClientState.forgetMode();
        refresh();
    }

    private BlueprintPayloads.Summary selected() {
        if (WrightsClientState.selected == null) {
            return null;
        }
        for (BlueprintPayloads.Summary summary : filtered) {
            if (summary.id().equals(WrightsClientState.selected)) {
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
