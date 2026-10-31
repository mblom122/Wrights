package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightportPlaces;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.network.BlueprintPayloads;

public class WrightsMenuScreen extends Screen {
    private static final int ROW = 18;
    private static final int PAGE_BUILDING = 0;
    private static final int PAGE_JOBS = 1;
    private static final int PAGE_OPTIONS = 2;
    private static final int PAGE_INFO = 3;
    private static final int PAGE_MANAGE = 4;
    private static final int PORT_ROWS = 8;
    private static int savedPage = PAGE_BUILDING;
    private static int savedScroll;
    private static int savedHistoryScroll;
    private static int savedOptionScroll;
    private static String savedSearch = "";
    private static int savedTab;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int detailH;
    private int headingY;
    private int searchY;
    private int detailX;
    private int detailY;
    private int detailW;
    private int scroll;
    private UUID confirmDelete;
    private final List<UUID> marked = new ArrayList<>();
    private UUID markAnchor;
    private List<UUID> confirmSet = List.of();
    private long lastClick;
    private EditBox search;
    private EditBox rename;
    private EditBox portRename;
    private String portRenameDim = "";
    private BlockPos portRenamePos;
    private WrightsButton newButton;
    private WrightsButton folderButton;
    private WrightsButton sortButton;
    private boolean sortOpen;
    private int sortMenuX;
    private int sortMenuY;
    private int sortMenuW;
    private int sortMenuH;
    private static final String[] SORT_LABELS = {
            "menu.wrights.sort_manual",
            "menu.wrights.sort_name",
            "menu.wrights.sort_size",
            "menu.wrights.sort_blocks",
            "menu.wrights.sort_used",
            "menu.wrights.sort_last"
    };
    private WrightsButton editButton;
    private WrightsButton deleteButton;
    private WrightsButton duplicateButton;
    private WrightsButton importButton;
    private WrightsButton moveButton;
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
    private final List<BlueprintList.Row> rows = new ArrayList<>();
    private UUID pendingSelect;
    private boolean suggestRename;
    private UUID shelfPress;
    private boolean shelfAdditive;
    private boolean shelfRange;
    private boolean shelfPressFolder;
    private UUID shelfPressParent = Util.NIL_UUID;
    private int shelfPressIndex;
    private double shelfPressX;
    private double shelfPressY;
    private boolean shelfDragging;
    private UUID dropParent = Util.NIL_UUID;
    private int dropIndex = -1;
    private boolean dropInto;
    private UUID dropHighlight;
    private int dropLineY = -1;
    private boolean options;
    private boolean infoPage;
    private WrightsButton unbindDigital;
    private int infoScroll;
    private static int savedInfoScroll;
    private boolean jobsPage;
    private boolean managePage;
    private final WrightManagePanel manage = new WrightManagePanel();
    private final ManageHost manageHost = new MenuManageHost();
    private int portKind = -1;
    private int portIndex = -1;
    private boolean portListOpen;
    private int portScroll;
    private int manageTab;
    private int placedScroll;
    private BlueprintPayloads.PlacedPort placedPick;
    private boolean holding;
    private int holdTicker;
    private int jobScroll;
    private UUID leadJob;
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
    private int swatchX;
    private int wallSwatchY;
    private int edgeSwatchY;
    private int queueEdgeSwatchY;
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
    private int hologramHeadY;
    private int buildHeadY;
    private int supplyHeadY;
    private int messageHeadY;
    private int optionPreviewX;
    private int optionPreviewY;
    private int droneY;
    private int droneX;
    private int droneW;
    private int droneMinusX;
    private int dronePlusX;
    private int queueX;
    private int queueY;
    private int queueW;
    private int buildMenuX;
    private int buildMenuY;
    private int buildMenuW;
    private int portOrderX;
    private int portOrderY;
    private int portOrderW;
    private int supplyOrderX;
    private int supplyOrderY;
    private int supplyOrderW;
    private int optionPlaceY;
    private int optionOffsetY;
    private int hudPlaceX;
    private int hudPlaceY;
    private int hudPlaceW;
    private final List<HelpSpot> optionHelps = new ArrayList<>();
    private Component optionTip;
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
            infoPage = false;
            managePage = false;
        } else if (WrightsClientState.mode == WrightsClientState.Mode.MANAGE) {
            managePage = true;
            jobsPage = false;
            options = false;
            infoPage = false;
        } else if (WrightsClientState.mode == WrightsClientState.Mode.INFO) {
            infoPage = true;
            managePage = false;
            jobsPage = false;
            options = false;
        } else if (WrightsClientState.mode == WrightsClientState.Mode.BUILD
                || WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                || WrightsClientState.mode == WrightsClientState.Mode.SAVING) {
            jobsPage = false;
            options = false;
            infoPage = false;
            managePage = false;
        } else {
            jobsPage = savedPage == PAGE_JOBS;
            options = savedPage == PAGE_OPTIONS;
            infoPage = savedPage == PAGE_INFO;
            managePage = savedPage == PAGE_MANAGE;
        }
        scroll = savedScroll;
        jobScroll = 0;
        historyScroll = savedHistoryScroll;
        optionScroll = savedOptionScroll;
        infoScroll = savedInfoScroll;
        searchText = savedSearch;
    }

    @Override
    protected void init() {
        layout();
        search = new EditBox(this.font, listX, searchY, listW, 16, Component.translatable("menu.wrights.search"));
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
        portRename = new EditBox(this.font, 0, 0, 80, 14, Component.translatable("screen.wrights.name"));
        portRename.setMaxLength(32);
        portRename.setHint(Component.translatable("screen.wrights.name"));
        portRename.visible = false;
        addRenderableWidget(portRename);

        int y = panelY + panelH - 48;
        int gap = 4;
        int sortW = Math.max(40, this.font.width(Component.translatable("menu.wrights.sort").getString()) + 12);
        sortButton = add(new WrightsButton(listX + listW - sortW, searchY, sortW, 16, Component.translatable("menu.wrights.sort"), button -> {
            sortOpen = !sortOpen;
            if (sortButton != null) {
                sortButton.setPicked(sortOpen);
            }
        }));
        int folderW = this.font.width(Component.translatable("menu.wrights.new_folder").getString()) + 12;
        int room = listW - gap * 3;
        int minOther = 30;
        if (folderW > room - minOther * 3) {
            folderW = Math.max(minOther, room - minOther * 3);
        }
        int otherW = Math.max(minOther, (room - folderW) / 3);
        int slack = listW - (folderW + otherW * 3 + gap * 3);
        int x = listX;
        folderButton = add(new WrightsButton(x, y, folderW, 18, Component.translatable("menu.wrights.new_folder"), button -> newFolder()));
        x += folderW + gap;
        duplicateButton = add(new WrightsButton(x, y, otherW, 18, Component.translatable("menu.wrights.duplicate"), button -> duplicate()));
        x += otherW + gap;
        editButton = add(new WrightsButton(x, y, otherW, 18, Component.translatable("menu.wrights.edit"), button -> edit()));
        x += otherW + gap;
        deleteButton = add(new WrightsButton(x, y, otherW + Math.max(0, slack), 18, Component.translatable("menu.wrights.delete"), button -> delete(), true));
        int bottom = panelY + panelH - 26;
        int moveW = Math.max(48, this.font.width(Component.translatable("menu.wrights.to_global").getString()) + 10);
        int importW = Math.max(40, listW - gap * 2 - otherW - moveW);
        importButton = add(new WrightsButton(listX, bottom, importW, 18, Component.translatable("menu.wrights.transfer"), button -> openImport()));
        moveButton = add(new WrightsButton(listX + importW + gap, bottom, moveW, 18, Component.translatable("menu.wrights.to_global"), button -> moveSelected()));
        newButton = add(new WrightsButton(listX + importW + gap + moveW + gap, bottom, otherW, 18, Component.translatable("menu.wrights.new"), button -> newBlueprint()));
        int detailButtonW = 58;
        buildButton = add(new WrightsButton(detailX + detailW - 120, bottom, detailButtonW, 18, Component.translatable("menu.wrights.build"), button -> build()));
        addRenderableWidget(new WrightsButton(detailX + detailW - detailButtonW, bottom, detailButtonW, 18, Component.translatable("menu.wrights.close"), button -> onClose()));
        renameButton = add(new WrightsButton(detailX + Math.max(48, detailW - 78), detailSplit + 52, 74, 14, Component.translatable("menu.wrights.rename"), button -> rename()));
        materialsButton = add(new WrightsButton(detailX + 4, detailSplit + 72, Math.max(40, detailW - 8), 16, Component.translatable("menu.wrights.materials"), button -> openMaterials()));
        stopButton = add(new WrightsButton(detailX + 4, detailSplit + 92, Math.max(40, detailW - 8), 16, Component.translatable("menu.wrights.stop"), button -> {
            if (requireTool()) {
                PacketDistributor.sendToServer(new BlueprintPayloads.StopJob());
            }
        }));
        discardButton = add(new WrightsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.wrights.discard"), button -> discard()));
        resumeButton = add(new WrightsButton(panelX + 14, panelY + panelH - 68, 92, 16, Component.translatable("menu.wrights.resume"), button -> closeKeepingMode()));
        previewButton = add(new WrightsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.wrights.cancel_preview"), button -> cancelPreview()));
        wallPercent = percentBox(value -> WrightsClientConfig.setWallTransparency(value));
        edgePercent = percentBox(value -> WrightsClientConfig.setEdgeOpacity(value));
        ghostPercent = percentBox(value -> WrightsClientConfig.setGhostTransparency(value));
        placeX = offsetBox(0);
        placeY = offsetBox(1);
        placeZ = offsetBox(2);
        unbindDigital = add(new WrightsButton(panelX + 140, panelY + panelH - 48, 120, 18,
                Component.translatable("menu.wrights.digital_unbind"), button -> clearDigitalLink()));
        manage.addWidgets(manageHost);
        refresh();
        if (infoPage) {
            requestDigitalInfo();
        }
        if (managePage) {
            sendHold(true);
        }
    }

    @Override
    public void removed() {
        savedPage = managePage ? PAGE_MANAGE : jobsPage ? PAGE_JOBS : options ? PAGE_OPTIONS : infoPage ? PAGE_INFO : PAGE_BUILDING;
        sendHold(false);
        savedScroll = scroll;
        savedHistoryScroll = historyScroll;
        savedOptionScroll = optionScroll;
        savedInfoScroll = infoScroll;
        savedSearch = searchText == null ? "" : searchText;
        finishPortRename(true);
        super.removed();
    }

    private boolean toolHeld() {
        return this.minecraft != null && this.minecraft.player != null
                && com.wrights.item.ControllerItem.isHeld(this.minecraft.player);
    }

    /** World build / capture / job actions need the controller; library browsing does not. */
    private boolean requireTool() {
        if (toolHeld()) {
            return true;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("message.wrights.need_controller"), true);
        }
        return false;
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
        headingY = panelY + 36;
        searchY = panelY + 68;
        listY = panelY + 90;
        listW = Math.min(220, panelW - 300);
        if (listW < 120) {
            listW = Math.max(100, (panelW - 150) / 2);
        }
        detailH = Math.max(ROW, panelH - 128);
        listH = Math.max(ROW, detailH - 16);
        detailX = listX + listW + 8;
        detailY = listY;
        detailW = panelX + panelW - 12 - detailX;
        if (detailW < 150 && listW > 100) {
            listW = Math.max(100, listW - (150 - detailW));
            detailX = listX + listW + 8;
            detailW = panelX + panelW - 12 - detailX;
        }
        detailSplit = detailY + detailH / 2;
    }

    public void refresh() {
        if (search == null) {
            return;
        }
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        BlueprintList.Tab tab = BlueprintList.Tab.values()[Mth.clamp(savedTab, 0, BlueprintList.Tab.values().length - 1)];
        rows.clear();
        rows.addAll(BlueprintList.rows(tab, query));
        if (pendingSelect != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).id().equals(pendingSelect)) {
                    continue;
                }
                WrightsClientState.selected = pendingSelect;
                marked.clear();
                marked.add(pendingSelect);
                markAnchor = pendingSelect;
                confirmSet = List.of();
                pendingSelect = null;
                int visible = visibleRows();
                if (i < scroll || i >= scroll + visible) {
                    scroll = Math.max(0, i - Math.max(0, visible - 1));
                }
                suggestRename = !rows.get(i).folder();
                if (rename != null) {
                    rename.setFocused(false);
                }
                break;
            }
        }
        if (WrightsClientState.selected != null
                && WrightsClientState.library.stream().noneMatch(entry -> entry.id().equals(WrightsClientState.selected))
                && WrightsClientState.folders.stream().noneMatch(folder -> folder.id().equals(WrightsClientState.selected))) {
            WrightsClientState.selected = null;
            confirmDelete = null;
            confirmSet = List.of();
        }
        syncMarks();
        int maxScroll = Math.max(0, rows.size() - visibleRows());
        scroll = Mth.clamp(scroll, 0, maxScroll);
        BlueprintList.Row row = selectedRow();
        BlueprintPayloads.Summary selected = row == null || row.folder() ? null : row.summary();
        boolean blueprint = selected != null;
        boolean any = row != null;
        boolean many = marked.size() > 1;
        boolean tool = toolHeld();
        boolean world = !WrightsClientState.libraryGlobal;
        editButton.active = blueprint && !many;
        deleteButton.active = any;
        buildButton.active = blueprint && tool && !many;
        renameButton.active = any && !many;
        deleteButton.setMessage(Component.translatable(any && confirming() ? "menu.wrights.delete_confirm" : "menu.wrights.delete"));
        if (any && !rename.isFocused()) {
            rename.setValue(row.name());
        }
        if (suggestRename && any && !row.folder()) {
            suggestRename = false;
            rename.setValue(row.name());
            rename.setFocused(true);
            rename.setCursorPosition(rename.getValue().length());
            rename.setHighlightPos(0);
        } else {
            suggestRename = false;
        }
        boolean capture = WrightsClientState.mode == WrightsClientState.Mode.CAPTURE;
        boolean building = !options && !jobsPage && !infoPage && !managePage;
        search.setX(listX);
        search.setY(searchY);
        int sortW = sortButton == null ? 0 : sortButton.getWidth();
        search.setWidth(Math.max(40, listW - sortW - 4));
        search.visible = building;
        if (sortButton != null) {
            sortButton.setX(listX + listW - sortW);
            sortButton.setY(searchY);
            sortButton.visible = building;
            sortButton.setPicked(sortOpen && building);
        }
        if (!building) {
            sortOpen = false;
        }
        rename.visible = building;
        if (portRename != null) {
            portRename.visible = managePage && manageTab == 1 && portRenamePos != null;
        }
        newButton.visible = building;
        if (folderButton != null) {
            folderButton.visible = building;
        }
        editButton.visible = building;
        deleteButton.visible = building;
        placeShelfButtons(building, any);
        buildButton.visible = building;
        renameButton.visible = building;
        discardButton.visible = building && (capture || WrightsClientState.mode == WrightsClientState.Mode.SAVING);
        resumeButton.visible = building && capture;
        if (previewButton != null) {
            previewButton.visible = building && WrightsClientState.mode == WrightsClientState.Mode.BUILD;
        }
        if (stopButton != null) {
            stopButton.visible = building && WrightsClientState.busy;
            stopButton.active = WrightsClientState.busy && tool;
            stopButton.setX(detailX + 4);
            stopButton.setY(detailSplit + 92);
            stopButton.setWidth(Math.max(40, detailW - 8));
        }
        if (newButton != null) {
            newButton.active = tool;
        }
        if (moveButton != null) {
            moveButton.active = marked.stream().anyMatch(this::isBlueprint);
            moveButton.setMessage(Component.translatable(world ? "menu.wrights.to_global" : "menu.wrights.to_world"));
        }
        if (discardButton != null) {
            discardButton.active = tool;
        }
        if (resumeButton != null) {
            resumeButton.active = tool;
        }
        if (previewButton != null) {
            previewButton.active = tool;
        }
        if (materialsButton != null) {
            materialsButton.visible = building;
            materialsButton.active = blueprint && marked.size() <= 1;
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
        if (unbindDigital != null) {
            unbindDigital.visible = infoPage;
            unbindDigital.active = infoPage && WrightsClientState.digitalBound;
            unbindDigital.setX(panelX + 140);
            unbindDigital.setY(panelY + panelH - 48);
        }
        reconcilePlaced();
        boolean showManage = manageOpen();
        manage.setActive(showManage);
        if (showManage) {
            manage.layoutChrome(manageHost);
        }
    }

    public void refreshDigital() {
        if (unbindDigital != null) {
            unbindDigital.visible = infoPage;
            unbindDigital.active = infoPage && WrightsClientState.digitalBound;
        }
    }

    private void requestDigitalInfo() {
        PacketDistributor.sendToServer(new BlueprintPayloads.DigitalInfoRequest());
    }

    private void clearDigitalLink() {
        PacketDistributor.sendToServer(new BlueprintPayloads.BindDigital(BlockPos.ZERO, true));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        if (options || jobsPage || infoPage || managePage) {
            int x = listX - 2;
            int width = panelX + panelW - 12 - x;
            int top = jobsPage ? jobsInsetY() : panelY + 34;
            int height = jobsPage ? jobsInsetH() : panelH - 68;
            WrightsUi.inset(graphics, x, top, width, height);
            return;
        }
        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
        WrightsUi.inset(graphics, detailX - 2, detailY - 2, detailW + 4, detailH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (options) {
            layoutOptions();
        }
        if (managePage) {
            reconcilePlaced();
            boolean open = manageOpen();
            if (manage.isOpen() != open) {
                manage.setActive(open);
            }
            if (open) {
                manage.layoutChrome(manageHost);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("menu.wrights.title"), panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.subtitle"), panelX + 16, panelY + 22, WrightsUi.MUTED, false);
        if (!toolHeld()) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.need_controller"), panelX + 150, panelY + 10, WrightsUi.MUTED, false);
        }
        drawTask(graphics, Component.translatable("menu.wrights.building"), !options && !jobsPage && !infoPage && !managePage, false, panelY + 46);
        drawTask(graphics, Component.translatable("menu.wrights.jobs"), jobsPage, false, panelY + 72);
        drawTask(graphics, Component.translatable("menu.wrights.manage"), managePage, false, panelY + 98);
        drawTask(graphics, Component.translatable("menu.wrights.mining"), false, true, panelY + 124);
        drawTask(graphics, Component.translatable("menu.wrights.info"), infoPage, false, panelY + 150);
        drawTask(graphics, Component.translatable("menu.wrights.options"), options, false, panelY + 176);
        if (options) {
            renderOptions(graphics, mouseX, mouseY);
            return;
        }
        if (infoPage) {
            if (System.currentTimeMillis() - WrightsClientState.digitalInfoAt > 2000L) {
                requestDigitalInfo();
                WrightsClientState.digitalInfoAt = System.currentTimeMillis();
            }
            renderInfo(graphics);
            return;
        }
        if (jobsPage) {
            renderJobs(graphics, mouseX, mouseY);
            return;
        }
        if (managePage) {
            renderManage(graphics, mouseX, mouseY);
            return;
        }

        drawScope(graphics, mouseX, mouseY);
        drawTabs(graphics, mouseX, mouseY);
        if (WrightsClientState.busy && !WrightsClientState.libraryGlobal) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.busy"), detailX, headingY, WrightsUi.COPPER, false);
        }
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
        if (sortOpen) {
            renderSortMenu(graphics, mouseX, mouseY);
        }
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

    private void renderInfo(GuiGraphics graphics) {
        int x = listX;
        int y = panelY + 42;
        int width = panelX + panelW - 16 - x;
        graphics.drawString(this.font, Component.translatable("menu.wrights.info_title"), x, y, WrightsUi.TEXT, false);
        y += 14;
        if (!WrightsClientState.digitalBound) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.digital_none"), x, y, WrightsUi.MUTED, false);
            y += 14;
            graphics.drawString(this.font, Component.translatable("menu.wrights.digital_bind_rs"), x, y, WrightsUi.MUTED, false);
            y += 12;
            graphics.drawString(this.font, Component.translatable("menu.wrights.digital_bind_ae2"), x, y, WrightsUi.MUTED, false);
        } else {
            Component kind = Component.translatable(WrightsClientState.digitalKind == com.wrights.storage.DigitalLink.Kind.AE2.ordinal()
                    ? "menu.wrights.digital_ae2"
                    : "menu.wrights.digital_rs");
            Component status = Component.translatable(WrightsClientState.digitalOnline
                    ? "menu.wrights.digital_online"
                    : "menu.wrights.digital_offline");
            graphics.drawString(this.font, Component.translatable("menu.wrights.digital_bound", kind, status), x, y, WrightsUi.TEXT, false);
            y += 12;
            BlockPos pos = WrightsClientState.digitalPos;
            if (pos != null) {
                String dim = WrightsClientState.digitalDim;
                if (dim == null || dim.isEmpty()) {
                    dim = "?";
                }
                graphics.drawString(this.font, Component.translatable("menu.wrights.digital_pos", pos.getX(), pos.getY(), pos.getZ(), dim), x, y, WrightsUi.MUTED, false);
                y += 12;
            }
        }
        y += 6;
        if (WrightsClientState.supplyBound && WrightsClientState.supplyPos != null) {
            BlockPos chest = WrightsClientState.supplyPos;
            graphics.drawString(this.font, Component.translatable("menu.wrights.chest_bound", chest.getX(), chest.getY(), chest.getZ()), x, y, WrightsUi.BLUE, false);
        } else {
            graphics.drawString(this.font, Component.translatable("menu.wrights.chest_none"), x, y, WrightsUi.MUTED, false);
        }
        y += 16;
        graphics.drawString(this.font, Component.translatable("menu.wrights.digital_items"), x, y, WrightsUi.TEXT, false);
        y += 12;
        if (!WrightsClientState.digitalBound || !WrightsClientState.digitalOnline) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.digital_items_offline"), x, y, WrightsUi.MUTED, false);
            return;
        }
        if (WrightsClientState.digitalLines.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.digital_items_empty"), x, y, WrightsUi.MUTED, false);
            return;
        }
        int bottom = panelY + panelH - 56;
        for (BlueprintPayloads.DigitalInfoLine line : WrightsClientState.digitalLines) {
            if (y + 12 > bottom) {
                break;
            }
            net.minecraft.world.item.Item item = net.minecraft.world.item.Item.byId(line.itemId());
            Component name = item.getDefaultInstance().getHoverName();
            Component craft = Component.translatable(line.craftable() ? "menu.wrights.digital_craftable" : "menu.wrights.digital_stock_only");
            String text = name.getString() + "  x" + line.stock() + "  " + craft.getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(text, width), x, y, WrightsUi.MUTED, false);
            y += 12;
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
        int controls = optionControlsX(x);
        int local = 2;
        hologramHeadY = optionY(local);
        optionPreviewX = x + 220;
        optionPreviewY = optionY(local);
        local = 22;
        wallSwatchY = optionY(local - 2);
        local += 20;
        int sliderW = Mth.clamp(optionViewX + optionViewW - 18 - controls - 42, 48, 120);
        wallSliderX = controls;
        wallSliderY = optionY(local);
        wallSliderW = sliderW;
        placeField(wallPercent, wallSliderX + wallSliderW + 8, wallSliderY - 4);
        local += 22;
        edgeSwatchY = optionY(local - 2);
        local += 20;
        edgeSliderX = controls;
        edgeSliderY = optionY(local);
        edgeSliderW = sliderW;
        placeField(edgePercent, edgeSliderX + edgeSliderW + 8, edgeSliderY - 4);
        local += 22;
        queueEdgeSwatchY = optionY(local - 2);
        local += 20;
        ghostSliderX = controls;
        ghostSliderY = optionY(local);
        ghostSliderW = sliderW;
        placeField(ghostPercent, ghostSliderX + ghostSliderW + 8, ghostSliderY - 4);
        local += 24;
        boolean marks = WrightsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.wrights.on" : "menu.wrights.off");
        markerW = Math.max(36, this.font.width(state) + 16);
        markerX = controls;
        markerY = optionY(local - 3);
        local += 28;
        buildHeadY = optionY(local);
        local += 18;
        boolean paceChoice = creativeVisit();
        boolean instant = paceChoice && WrightsClientConfig.INSTANT_JOBS.get();
        Component pace = Component.translatable(instant ? "menu.wrights.instant" : "menu.wrights.drone_pace");
        instantW = Math.max(52, this.font.width(pace) + 16);
        instantX = controls;
        instantY = optionY(local - 3);
        local += 24;
        droneY = optionY(local - 3);
        droneMinusX = controls;
        dronePlusX = droneMinusX + 36;
        Component maxLabel = Component.translatable("menu.wrights.drone_max", WrightsConfig.maxWrightsPerJob());
        droneW = this.font.width(maxLabel) + 12;
        droneX = dronePlusX + 22;
        local += 24;
        Component queueState = Component.translatable(WrightsClientConfig.QUEUE_JOBS.get() ? "menu.wrights.on" : "menu.wrights.off");
        queueW = Math.max(36, this.font.width(queueState) + 16);
        queueX = controls;
        queueY = optionY(local - 3);
        local += 24;
        Component youFirst = Component.translatable("menu.wrights.port_player_first");
        Component blockFirst = Component.translatable("menu.wrights.port_block_first");
        portOrderW = Math.max(this.font.width(youFirst), this.font.width(blockFirst)) + 16;
        portOrderX = controls;
        portOrderY = optionY(local - 3);
        local += 24;
        Component menuState = Component.translatable(WrightsClientConfig.BUILD_MENU.get() ? "menu.wrights.on" : "menu.wrights.off");
        buildMenuW = Math.max(36, this.font.width(menuState) + 16);
        buildMenuX = controls;
        buildMenuY = optionY(local - 3);
        local += 24;
        optionPlaceY = optionY(local);
        local += 16;
        optionOffsetY = optionY(local);
        placeField(placeX, x + 12, optionOffsetY);
        placeField(placeY, x + 82, optionOffsetY);
        placeField(placeZ, x + 152, optionOffsetY);
        local += 36;
        supplyHeadY = optionY(local);
        local += 18;
        Component playerFirst = Component.translatable("menu.wrights.supply_player_first");
        Component chestFirst = Component.translatable("menu.wrights.supply_chest_first");
        supplyOrderW = Math.max(this.font.width(playerFirst), this.font.width(chestFirst)) + 16;
        supplyOrderX = controls;
        supplyOrderY = optionY(local - 3);
        local += 28;
        messageHeadY = optionY(local);
        local += 18;
        Component moveMessages = Component.translatable("menu.wrights.hud_place");
        hudPlaceW = Math.max(120, this.font.width(moveMessages) + 16);
        hudPlaceX = x;
        hudPlaceY = optionY(local);
        swatchX = controls;
        optionContentH = local + 28;
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

    /** Shared left edge for the option controls, past the longest label and its help mark. */
    private int optionControlsX(int labelX) {
        String[] keys = {
                "menu.wrights.wall_color",
                "menu.wrights.wall_transparency",
                "menu.wrights.edge_color",
                "menu.wrights.queue_edge",
                "menu.wrights.edge_opacity",
                "menu.wrights.ghost_transparency",
                "menu.wrights.corner_markers",
                "menu.wrights.build_speed",
                "menu.wrights.drone_deploy",
                "menu.wrights.queue_jobs",
                "menu.wrights.port_order",
                "menu.wrights.open_build_menu",
                "menu.wrights.supply_order"
        };
        int widest = 0;
        for (String key : keys) {
            widest = Math.max(widest, this.font.width(Component.translatable(key)));
        }
        int wanted = labelX + widest + 22;
        int limit = optionViewX + optionViewW - 16 - 168;
        return Math.max(labelX + 108, Math.min(wanted, Math.max(labelX + 108, limit)));
    }

    private void showOptionBox(EditBox box) {
        int top = box.getY();
        int bottom = top + box.getHeight();
        box.visible = options && top >= optionViewY && bottom <= optionViewY + optionViewH;
    }

    private void renderOptions(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = optionViewX + 10;
        optionHelps.clear();
        optionTip = null;
        graphics.enableScissor(optionViewX, optionViewY, optionViewX + optionViewW - 6, optionViewY + optionViewH);
        int lineEnd = optionViewX + optionViewW - 16;
        drawOptionSection(graphics, x, hologramHeadY, "menu.wrights.section_hologram", optionPreviewX - 8);
        int preview = (Math.round(WrightsClientConfig.wallAlpha() * 255.0F) << 24) | WrightsClientConfig.wallRgb();
        graphics.fill(optionPreviewX, optionPreviewY, optionPreviewX + 48, optionPreviewY + 28, preview);
        WrightsUi.border(graphics, optionPreviewX, optionPreviewY, 48, 28, 0xFF000000 | WrightsClientConfig.edgeRgb());
        graphics.drawString(this.font, Component.translatable("menu.wrights.wall_color"), x, wallSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, wallSwatchY, WrightsClientConfig.wallRgb());
        drawSlider(graphics, x, wallSliderY, wallSliderX, wallSliderW, WrightsClientConfig.WALL_TRANSPARENCY.get(), 100, Component.translatable("menu.wrights.wall_transparency"));
        graphics.drawString(this.font, "%", wallPercent.getX() + 34, wallSliderY, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.edge_color"), x, edgeSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, edgeSwatchY, WrightsClientConfig.edgeRgb());
        graphics.drawString(this.font, Component.translatable("menu.wrights.queue_edge"), x, queueEdgeSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, queueEdgeSwatchY, WrightsClientConfig.queueEdgeRgb());
        drawSlider(graphics, x, edgeSliderY, edgeSliderX, edgeSliderW, WrightsClientConfig.EDGE_OPACITY.get(), 100, Component.translatable("menu.wrights.edge_opacity"));
        graphics.drawString(this.font, "%", edgePercent.getX() + 34, edgeSliderY, WrightsUi.TEXT, false);
        Component ghostLabel = Component.translatable("menu.wrights.ghost_transparency");
        drawSlider(graphics, x, ghostSliderY, ghostSliderX, ghostSliderW, WrightsClientConfig.GHOST_TRANSPARENCY.get(), 100, ghostLabel);
        graphics.drawString(this.font, "%", ghostPercent.getX() + 34, ghostSliderY, WrightsUi.TEXT, false);
        drawOptionHelp(graphics, mouseX, mouseY, x, ghostSliderY, ghostLabel, ghostSliderX, ghostPercent.getX() + 42, Component.translatable("menu.wrights.ghost_hint"));
        graphics.drawString(this.font, Component.translatable("menu.wrights.corner_markers"), x, markerY + 3, WrightsUi.MUTED, false);
        boolean marks = WrightsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.wrights.on" : "menu.wrights.off");
        graphics.fill(markerX, markerY, markerX + markerW, markerY + 16, marks ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, markerX, markerY, markerW, 16, marks ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, state, markerX + markerW / 2, markerY + 4, WrightsUi.TEXT);
        drawOptionSection(graphics, x, buildHeadY, "menu.wrights.section_building", lineEnd);
        boolean paceChoice = creativeVisit();
        boolean instant = paceChoice && WrightsClientConfig.INSTANT_JOBS.get();
        Component speedLabel = Component.translatable("menu.wrights.build_speed");
        graphics.drawString(this.font, speedLabel, x, instantY + 3, paceChoice ? WrightsUi.MUTED : 0xFF667480, false);
        Component pace = Component.translatable(instant ? "menu.wrights.instant" : "menu.wrights.drone_pace");
        int fill = paceChoice ? (instant ? 0xFF24384A : 0xFF18222C) : 0xFF141C24;
        int edge = paceChoice ? (instant ? WrightsUi.COPPER : 0xFF3D4E5E) : 0xFF3A4652;
        int text = paceChoice ? WrightsUi.TEXT : 0xFF667480;
        graphics.fill(instantX, instantY, instantX + instantW, instantY + 16, fill);
        WrightsUi.border(graphics, instantX, instantY, instantW, 16, edge);
        graphics.drawCenteredString(this.font, pace, instantX + instantW / 2, instantY + 4, text);
        drawOptionHelp(graphics, mouseX, mouseY, x, instantY + 3, speedLabel, instantX, instantX + instantW, Component.translatable(paceChoice ? "menu.wrights.build_speed_hint" : "menu.wrights.build_speed_locked"));
        Component droneLabel = Component.translatable("menu.wrights.drone_deploy");
        graphics.drawString(this.font, droneLabel, x, droneY + 3, WrightsUi.MUTED, false);
        graphics.fill(droneMinusX, droneY, droneMinusX + 16, droneY + 16, 0xFF18222C);
        WrightsUi.border(graphics, droneMinusX, droneY, 16, 16, 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, "-", droneMinusX + 8, droneY + 4, WrightsUi.TEXT);
        String count = Integer.toString(WrightsClientConfig.droneCount());
        graphics.drawCenteredString(this.font, count, droneMinusX + 26, droneY + 4, WrightsUi.TEXT);
        graphics.fill(dronePlusX, droneY, dronePlusX + 16, droneY + 16, 0xFF18222C);
        WrightsUi.border(graphics, dronePlusX, droneY, 16, 16, 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, "+", dronePlusX + 8, droneY + 4, WrightsUi.TEXT);
        boolean atMax = WrightsClientConfig.droneCount() >= WrightsConfig.maxWrightsPerJob();
        graphics.fill(droneX, droneY, droneX + droneW, droneY + 16, atMax ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, droneX, droneY, droneW, 16, atMax ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, Component.translatable("menu.wrights.drone_max", WrightsConfig.maxWrightsPerJob()), droneX + droneW / 2, droneY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, droneY + 3, droneLabel, droneMinusX, droneX + droneW, Component.translatable("menu.wrights.drone_fixed_hint"));
        boolean queueJobs = WrightsClientConfig.QUEUE_JOBS.get();
        Component queueLabel = Component.translatable("menu.wrights.queue_jobs");
        graphics.drawString(this.font, queueLabel, x, queueY + 3, WrightsUi.MUTED, false);
        Component queueState = Component.translatable(queueJobs ? "menu.wrights.on" : "menu.wrights.off");
        graphics.fill(queueX, queueY, queueX + queueW, queueY + 16, queueJobs ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, queueX, queueY, queueW, 16, queueJobs ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, queueState, queueX + queueW / 2, queueY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, queueY + 3, queueLabel, queueX, queueX + queueW, Component.translatable("menu.wrights.queue_jobs_hint"));
        boolean youFirst = WrightsClientConfig.PORT_PLAYER_FIRST.get();
        Component portLabel = Component.translatable("menu.wrights.port_order");
        graphics.drawString(this.font, portLabel, x, portOrderY + 3, WrightsUi.MUTED, false);
        Component portOrder = Component.translatable(youFirst ? "menu.wrights.port_player_first" : "menu.wrights.port_block_first");
        graphics.fill(portOrderX, portOrderY, portOrderX + portOrderW, portOrderY + 16, youFirst ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, portOrderX, portOrderY, portOrderW, 16, youFirst ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, portOrder, portOrderX + portOrderW / 2, portOrderY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, portOrderY + 3, portLabel, portOrderX, portOrderX + portOrderW, Component.translatable(youFirst ? "menu.wrights.port_order_player_hint" : "menu.wrights.port_order_block_hint"));
        boolean openMenu = WrightsClientConfig.BUILD_MENU.get();
        Component menuLabel = Component.translatable("menu.wrights.open_build_menu");
        graphics.drawString(this.font, menuLabel, x, buildMenuY + 3, WrightsUi.MUTED, false);
        Component menuState = Component.translatable(openMenu ? "menu.wrights.on" : "menu.wrights.off");
        graphics.fill(buildMenuX, buildMenuY, buildMenuX + buildMenuW, buildMenuY + 16, openMenu ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, buildMenuX, buildMenuY, buildMenuW, 16, openMenu ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, menuState, buildMenuX + buildMenuW / 2, buildMenuY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, buildMenuY + 3, menuLabel, buildMenuX, buildMenuX + buildMenuW, Component.translatable("menu.wrights.open_build_menu_hint"));
        Component placeLabel = Component.translatable("menu.wrights.place_offset");
        graphics.drawString(this.font, placeLabel, x, optionPlaceY, WrightsUi.TEXT, false);
        drawOptionHelp(graphics, mouseX, mouseY, x, optionPlaceY, placeLabel, 0, 0, Component.translatable("menu.wrights.place_hint").append("\n").append(Component.translatable("menu.wrights.place_axes")));
        graphics.drawString(this.font, "X", x, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Y", x + 70, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Z", x + 140, optionOffsetY + 3, WrightsUi.MUTED, false);
        drawOptionSection(graphics, x, supplyHeadY, "menu.wrights.section_supply", lineEnd);
        boolean playerFirst = WrightsClientConfig.SUPPLY_PLAYER_FIRST.get();
        Component supplyLabel = Component.translatable("menu.wrights.supply_order");
        graphics.drawString(this.font, supplyLabel, x, supplyOrderY + 3, WrightsUi.MUTED, false);
        Component order = Component.translatable(playerFirst ? "menu.wrights.supply_player_first" : "menu.wrights.supply_chest_first");
        graphics.fill(supplyOrderX, supplyOrderY, supplyOrderX + supplyOrderW, supplyOrderY + 16, playerFirst ? 0xFF24384A : 0xFF18222C);
        WrightsUi.border(graphics, supplyOrderX, supplyOrderY, supplyOrderW, 16, playerFirst ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, order, supplyOrderX + supplyOrderW / 2, supplyOrderY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, supplyOrderY + 3, supplyLabel, supplyOrderX, supplyOrderX + supplyOrderW, Component.translatable(playerFirst ? "menu.wrights.supply_order_player_hint" : "menu.wrights.supply_order_chest_hint"));
        drawOptionSection(graphics, x, messageHeadY, "menu.wrights.section_messages", lineEnd);
        graphics.fill(hudPlaceX, hudPlaceY, hudPlaceX + hudPlaceW, hudPlaceY + 16, 0xFF24384A);
        WrightsUi.border(graphics, hudPlaceX, hudPlaceY, hudPlaceW, 16, WrightsUi.COPPER);
        graphics.drawCenteredString(this.font, Component.translatable("menu.wrights.hud_place"), hudPlaceX + hudPlaceW / 2, hudPlaceY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, hudPlaceX + hudPlaceW + 2, hudPlaceY + 4, Component.empty(), 0, 0, Component.translatable("menu.wrights.hud_place_hint"));
        graphics.disableScissor();
        if (optionTip != null) {
            graphics.renderTooltip(this.font, this.font.split(optionTip, 240), mouseX, mouseY);
        }
        if (optionContentH > optionViewH) {
            int barX = optionViewX + optionViewW - 5;
            graphics.fill(barX, optionViewY, barX + 3, optionViewY + optionViewH, 0xFF24303A);
            graphics.fill(barX, optionThumbY, barX + 3, optionThumbY + optionThumbH, WrightsUi.COPPER);
        }
    }

    private void drawOptionHelp(GuiGraphics graphics, int mouseX, int mouseY, int labelX, int labelY, Component label, int controlX, int controlRight, Component tip) {
        int helpX = labelX + this.font.width(label) + 4;
        if (controlX > labelX && helpX + 12 > controlX - 2) {
            helpX = controlRight + 6;
        }
        int helpY = labelY - 2;
        if (helpY + 12 <= optionViewY || helpY >= optionViewY + optionViewH || helpX + 12 > optionViewX + optionViewW - 6) {
            return;
        }
        boolean over = mouseX >= helpX && mouseX < helpX + 12 && mouseY >= helpY && mouseY < helpY + 12;
        graphics.fill(helpX, helpY, helpX + 12, helpY + 12, 0xFF18222C);
        WrightsUi.border(graphics, helpX, helpY, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, "?", helpX + 6, helpY + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        optionHelps.add(new HelpSpot(helpX, helpY));
        if (over) {
            optionTip = tip;
        }
    }

    private boolean overOptionHelp(double mouseX, double mouseY) {
        for (HelpSpot spot : optionHelps) {
            if (mouseX >= spot.x() && mouseX < spot.x() + 12 && mouseY >= spot.y() && mouseY < spot.y() + 12) {
                return true;
            }
        }
        return false;
    }

    private record HelpSpot(int x, int y) {
    }

    private void drawOptionSection(GuiGraphics graphics, int x, int y, String key, int lineEnd) {
        Component title = Component.translatable(key);
        graphics.drawString(this.font, title, x, y, WrightsUi.COPPER, false);
        int lineX = x + this.font.width(title) + 8;
        int rule = y + 4;
        if (lineEnd > lineX) {
            graphics.fill(lineX, rule, lineEnd, rule + 1, 0xFF5C4632);
        }
        if (y != hologramHeadY) {
            int divider = y - 8;
            graphics.fill(x, divider, optionViewX + optionViewW - 16, divider + 1, 0xFF2A3844);
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

    private void drawScope(GuiGraphics graphics, int mouseX, int mouseY) {
        int half = listW / 2;
        drawScopeChip(graphics, mouseX, mouseY, listX, headingY, half - 2, false, "menu.wrights.scope_world");
        drawScopeChip(graphics, mouseX, mouseY, listX + half, headingY, listW - half, true, "menu.wrights.scope_global");
    }

    private void drawScopeChip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, boolean global, String key) {
        boolean on = WrightsClientState.libraryGlobal == global;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + w, y + 14, on ? 0xFF24384A : (hover ? 0xFF223445 : 0xFF1C2A37));
        if (on) {
            graphics.fill(x, y, x + w, y + 2, WrightsUi.COPPER);
        }
        String label = this.font.plainSubstrByWidth(Component.translatable(key).getString(), w - 4);
        graphics.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] keys = {"menu.wrights.tab_all", "menu.wrights.tab_last", "menu.wrights.tab_most", "menu.wrights.tab_fav"};
        int width = listW / keys.length;
        int tabY = headingY + 16;
        for (int i = 0; i < keys.length; i++) {
            int x = listX + i * width;
            int tabW = i == keys.length - 1 ? listX + listW - x : width;
            boolean on = savedTab == i;
            boolean hover = mouseX >= x && mouseX < x + tabW && mouseY >= tabY && mouseY < tabY + 14;
            graphics.fill(x, tabY, x + tabW - 1, tabY + 13, on ? 0xFF24384A : (hover ? 0xFF223445 : 0xFF1C2A37));
            if (on) {
                graphics.fill(x, tabY, x + tabW - 1, tabY + 2, WrightsUi.COPPER);
            }
            String label = this.font.plainSubstrByWidth(Component.translatable(keys[i]).getString(), tabW - 4);
            graphics.drawString(this.font, label, x + (tabW - this.font.width(label)) / 2, tabY + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!WrightsClientState.libraryKnown) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.loading"), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        if (rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable(emptyKey()), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int shown = 0;
        for (int i = scroll; i < rows.size() && shown < visibleRows(); i++) {
            BlueprintList.Row entry = rows.get(i);
            int y = listY + shown * ROW;
            boolean selected = marked.contains(entry.id());
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW;
            boolean into = shelfDragging && dropInto && entry.id().equals(dropHighlight);
            if (selected || hover || into) {
                graphics.fill(listX, y, listX + listW - 4, y + ROW - 1, into ? 0x665C3E22 : (selected ? (entry.folder() ? 0x665C3E22 : 0x664A78B0) : 0x332A3C4C));
            } else if (entry.folder()) {
                graphics.fill(listX, y, listX + listW - 4, y + ROW - 1, 0x221C140C);
            }
            if (selected) {
                graphics.fill(listX, y, listX + 2, y + ROW - 1, entry.folder() ? WrightsUi.COPPER : WrightsUi.BLUE);
            }
            if (!entry.folder()) {
                drawStar(graphics, listX + 4, y + 4, entry.favorite());
            }
            int x = listX + (entry.folder() ? 4 : 16) + entry.depth() * 8;
            if (entry.folder()) {
                drawDisclosure(graphics, x, y + 6, entry.open());
                drawFolderGlyph(graphics, x + 8, y + 3, entry.open());
                x += 22;
            } else {
                int swatch = 0xFF000000 | (0x5A6E82 + (entry.name().hashCode() & 0x3F4F5F));
                graphics.fill(x, y + 5, x + 6, y + 11, swatch);
                x += 10;
            }
            int nameRoom = Math.max(8, listX + listW - 8 - x);
            String extra = entry.folder() ? Integer.toString(BlueprintList.childCount(entry.id())) : entry.summary().sizeX() + "×" + entry.summary().sizeY() + "×" + entry.summary().sizeZ();
            int extraW = this.font.width(extra) + 6;
            if (nameRoom - extraW < 24) {
                extra = "";
                extraW = 0;
            }
            String name = this.font.plainSubstrByWidth(entry.name(), Math.max(8, nameRoom - extraW));
            graphics.drawString(this.font, name, x, y + 5, entry.folder() ? WrightsUi.COPPER : WrightsUi.TEXT, false);
            if (!extra.isEmpty()) {
                graphics.drawString(this.font, extra, listX + listW - 8 - this.font.width(extra), y + 5, WrightsUi.MUTED, false);
            }
            shown++;
        }
        graphics.disableScissor();
        if (shelfDragging && !dropInto && dropLineY >= listY && dropLineY <= listY + listH) {
            graphics.fill(listX + 4, dropLineY, listX + listW - 6, dropLineY + 2, WrightsUi.COPPER);
        }
        if (shelfDragging && shelfPress != null) {
            int draggingCount = dragIds().size();
            String ghost = "";
            if (draggingCount > 1) {
                ghost = Component.translatable("menu.wrights.drag_many", draggingCount).getString();
            } else {
                for (BlueprintList.Row entry : rows) {
                    if (entry.id().equals(shelfPress)) {
                        ghost = entry.name();
                        break;
                    }
                }
            }
            if (!ghost.isEmpty()) {
                String label = this.font.plainSubstrByWidth(ghost, 120);
                int gx = Mth.clamp(mouseX + 8, listX, listX + listW - this.font.width(label) - 8);
                int gy = Mth.clamp(mouseY - 8, listY, listY + listH - 12);
                graphics.fill(gx - 2, gy - 2, gx + this.font.width(label) + 2, gy + 10, 0xEE1A2633);
                graphics.drawString(this.font, label, gx, gy, WrightsUi.TEXT, false);
            }
        }
        int maxScroll = Math.max(0, rows.size() - visibleRows());
        if (maxScroll > 0) {
            int track = listH - 4;
            int thumb = Math.max(12, track * visibleRows() / rows.size());
            int thumbY = listY + 2 + (track - thumb) * scroll / maxScroll;
            graphics.fill(listX + listW - 3, thumbY, listX + listW - 1, thumbY + thumb, WrightsUi.COPPER);
        }
    }

    private String emptyKey() {
        if (search != null && !search.getValue().trim().isEmpty()) {
            return "menu.wrights.no_match";
        }
        if (savedTab == BlueprintList.Tab.LAST.ordinal() || savedTab == BlueprintList.Tab.MOST.ordinal()) {
            return "menu.wrights.no_used";
        }
        if (savedTab == BlueprintList.Tab.FAVORITES.ordinal()) {
            return "menu.wrights.no_favorites";
        }
        return "menu.wrights.empty";
    }

    private void drawStar(GuiGraphics graphics, int x, int y, boolean on) {
        int color = on ? 0xFFFFC44D : 0xFF4C5964;
        graphics.fill(x + 3, y, x + 5, y + 8, color);
        graphics.fill(x, y + 3, x + 8, y + 5, color);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
        graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
        graphics.fill(x + 1, y + 5, x + 3, y + 7, color);
        graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
    }

    private void drawDisclosure(GuiGraphics graphics, int x, int y, boolean open) {
        int color = WrightsUi.COPPER;
        if (open) {
            graphics.fill(x, y + 2, x + 5, y + 3, color);
            graphics.fill(x + 1, y + 3, x + 4, y + 4, color);
            graphics.fill(x + 2, y + 4, x + 3, y + 5, color);
            return;
        }
        graphics.fill(x, y, x + 1, y + 5, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 4, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
    }

    private void drawFolderGlyph(GuiGraphics graphics, int x, int y, boolean open) {
        graphics.fill(x, y + 1, x + 5, y + 3, open ? 0xFFE0A15A : WrightsUi.COPPER);
        graphics.fill(x, y + 3, x + 11, y + 10, open ? 0xFF8C5A2A : 0xFF5C3E22);
        if (open) {
            graphics.fill(x + 1, y + 4, x + 10, y + 6, 0xFF3D2A18);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        BlueprintList.Row picked = selectedRow();
        modelX = detailX + 4;
        modelY = detailY + 4;
        modelW = Math.max(8, detailW - 8);
        modelH = Math.max(8, detailSplit - detailY - 10);
        if (picked != null && picked.folder()) {
            renderFolder(graphics, picked);
            return;
        }
        BlueprintPayloads.Summary summary = picked == null ? null : picked.summary();
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
        if (summary.id().equals(modelAsked)) {
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
            leaveManage();
            options = false;
            jobsPage = false;
            infoPage = false;
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 72)) {
            leaveManage();
            options = false;
            jobsPage = true;
            infoPage = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 98)) {
            options = false;
            jobsPage = false;
            infoPage = false;
            if (!managePage) {
                manageTab = 0;
            }
            managePage = true;
            portListOpen = false;
            setFocused(null);
            refresh();
            PacketDistributor.sendToServer(new BlueprintPayloads.PlacedPortsAsk());
            sendHold(manageTab == 0);
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 150)) {
            leaveManage();
            options = false;
            jobsPage = false;
            infoPage = true;
            setFocused(null);
            requestDigitalInfo();
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, panelY + 176)) {
            leaveManage();
            options = true;
            jobsPage = false;
            infoPage = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (managePage) {
            if (button == 0 && clickManageTabs(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && manageTab == 0 && clickPortList(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && manageTab == 1 && clickPlacedList(mouseX, mouseY)) {
                return true;
            }
            if (manage.isOpen() && manage.mouseClicked(manageHost, mouseX, mouseY, button)) {
                return true;
            }
            super.mouseClicked(mouseX, mouseY, button);
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
                if (overOptionHelp(mouseX, mouseY)) {
                    return true;
                }
                if (pickSwatch(mouseX, mouseY, wallSwatchY, 0) || pickSwatch(mouseX, mouseY, edgeSwatchY, 1) || pickSwatch(mouseX, mouseY, queueEdgeSwatchY, 2)) {
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
                if (mouseY >= droneY && mouseY < droneY + 16) {
                    if (mouseX >= droneX && mouseX < droneX + droneW) {
                        WrightsClientState.deployChosen = false;
                        WrightsClientConfig.setDroneCount(WrightsConfig.maxWrightsPerJob());
                        WrightsClientState.applyDeploy();
                        return true;
                    }
                    int step = 0;
                    if (mouseX >= droneMinusX && mouseX < droneMinusX + 16) {
                        step = -1;
                    } else if (mouseX >= dronePlusX && mouseX < dronePlusX + 16) {
                        step = 1;
                    }
                    if (step != 0) {
                        WrightsClientState.deployChosen = false;
                        WrightsClientConfig.setDroneCount(WrightsClientState.stepped(WrightsClientConfig.droneCount(), step, 1, WrightsConfig.maxWrightsPerJob()));
                        WrightsClientState.applyDeploy();
                        return true;
                    }
                }
                if (mouseX >= queueX && mouseX < queueX + queueW && mouseY >= queueY && mouseY < queueY + 16) {
                    WrightsClientConfig.setQueueJobs(!WrightsClientConfig.QUEUE_JOBS.get());
                    return true;
                }
                if (mouseX >= portOrderX && mouseX < portOrderX + portOrderW && mouseY >= portOrderY && mouseY < portOrderY + 16) {
                    boolean next = !WrightsClientConfig.PORT_PLAYER_FIRST.get();
                    WrightsClientConfig.setPortPlayerFirst(next);
                    PacketDistributor.sendToServer(new BlueprintPayloads.SetPortOrder(next));
                    return true;
                }
                if (mouseX >= buildMenuX && mouseX < buildMenuX + buildMenuW && mouseY >= buildMenuY && mouseY < buildMenuY + 16) {
                    WrightsClientConfig.setBuildMenu(!WrightsClientConfig.BUILD_MENU.get());
                    return true;
                }
                if (mouseX >= supplyOrderX && mouseX < supplyOrderX + supplyOrderW && mouseY >= supplyOrderY && mouseY < supplyOrderY + 16) {
                    WrightsClientConfig.setSupplyPlayerFirst(!WrightsClientConfig.SUPPLY_PLAYER_FIRST.get());
                    return true;
                }
                if (mouseX >= hudPlaceX && mouseX < hudPlaceX + hudPlaceW && mouseY >= hudPlaceY && mouseY < hudPlaceY + 16) {
                    this.minecraft.setScreen(new HudPlaceScreen());
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
        if (button == 0 && !options && !jobsPage && !infoPage && sortOpen && pickSort(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !options && !jobsPage && !infoPage && clickScope(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !options && !jobsPage && !infoPage && clickTab(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && !options && !jobsPage && !infoPage && sortOpen && (sortButton == null || !sortButton.isMouseOver(mouseX, mouseY))) {
            sortOpen = false;
            if (sortButton != null) {
                sortButton.setPicked(false);
            }
            return true;
        }
        if (button == 0 && !options && !jobsPage && !infoPage && insideModel(mouseX, mouseY)) {
            spinning = true;
            return true;
        }
        if (button == 0 && !options && !jobsPage && !infoPage && insideList(mouseX, mouseY) && !rows.isEmpty() && !overWidget(mouseX, mouseY)) {
            int shown = (int) ((mouseY - listY) / ROW);
            int index = scroll + shown;
            if (shown >= 0 && shown < visibleRows() && index < rows.size()) {
                BlueprintList.Row entry = rows.get(index);
                if (!entry.folder() && mouseX < listX + 14) {
                    WrightsClientState.toggleFavoriteLocal(entry.id());
                    PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.FAVORITE, entry.id(), false, Util.NIL_UUID, 0, ""));
                    refresh();
                    return true;
                }
                shelfPress = entry.id();
                shelfAdditive = hasControlDown();
                shelfRange = hasShiftDown();
                shelfPressFolder = entry.folder();
                shelfPressParent = entry.parent();
                shelfPressIndex = entry.index();
                shelfPressX = mouseX;
                shelfPressY = mouseY;
                shelfDragging = false;
                dropIndex = -1;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && shelfPress != null) {
            if (!shelfDragging && (Math.abs(mouseX - shelfPressX) > 4 || Math.abs(mouseY - shelfPressY) > 4) && canArrange()) {
                shelfDragging = true;
                if (!marked.contains(shelfPress)) {
                    marked.clear();
                    marked.add(shelfPress);
                    markAnchor = shelfPress;
                    WrightsClientState.selected = shelfPress;
                    confirmDelete = null;
                    confirmSet = List.of();
                }
            }
            if (shelfDragging) {
                updateDrop(mouseX, mouseY);
                return true;
            }
        }
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
        if (button == 0 && shelfPress != null) {
            UUID pressed = shelfPress;
            boolean dragging = shelfDragging;
            int index = dropIndex;
            UUID parent = dropParent;
            boolean folder = shelfPressFolder;
            boolean into = dropInto;
            List<UUID> moving = dragIds();
            shelfPress = null;
            shelfDragging = false;
            dropIndex = -1;
            dropInto = false;
            dropHighlight = null;
            dropLineY = -1;
            if (dragging && index >= 0) {
                if (savedTab == BlueprintList.Tab.ALL.ordinal() && into && moving.size() > 1) {
                    dropMarkedInto(parent, moving);
                } else if (savedTab == BlueprintList.Tab.ALL.ordinal()) {
                    PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.MOVE, pressed, folder, parent, index, ""));
                } else {
                    rememberManual(pressed, index);
                }
            } else if (!dragging) {
                selectRow(pressed);
            }
            return true;
        }
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (managePage && manage.keyPressed(manageHost, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (portRename != null && portRename.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                finishPortRename(true);
                return true;
            }
            if (keyCode == 256) {
                finishPortRename(false);
                return true;
            }
            portRename.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (managePage && keyCode == 256) {
            if (portRenamePos != null) {
                finishPortRename(false);
                return true;
            }
            if (manage.closeTop()) {
                return true;
            }
            if (portListOpen) {
                portListOpen = false;
                return true;
            }
        }
        if (sortOpen && keyCode == 256) {
            sortOpen = false;
            if (sortButton != null) {
                sortButton.setPicked(false);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private boolean pickSwatch(double mouseX, double mouseY, int y, int which) {
        if (mouseY < y || mouseY >= y + 12) {
            return false;
        }
        for (int i = 0; i < WrightsClientConfig.COLORS.length; i++) {
            int x = swatchX + i * 16;
            if (mouseX >= x && mouseX < x + 12) {
                int color = WrightsClientConfig.COLORS[i];
                if (which == 0) {
                    WrightsClientConfig.setWallRgb(color);
                } else if (which == 1) {
                    WrightsClientConfig.setEdgeRgb(color);
                } else {
                    WrightsClientConfig.setQueueEdgeRgb(color);
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

    private static final int JOB_ROW = 70;
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
                workingJobs.add(0, job);
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
        if (!workingJobs.isEmpty()) {
            UUID lead = workingJobs.get(0).id();
            if (leadJob != null && !leadJob.equals(lead)) {
                jobScroll = 0;
            }
            leadJob = lead;
        } else {
            leadJob = null;
        }
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
        hoveredJob = null;
        prepareJobs();
        int[] pool = dronePool();
        String drones = this.font.plainSubstrByWidth(Component.translatable("menu.wrights.drones_pool", pool[0], pool[1]).getString(), Math.max(40, jobW - manageAllWidth - 8));
        graphics.drawString(this.font, drones, jobX, manageAllY + 4, WrightsUi.TEXT, false);
        drawJobButton(graphics, manageAllRight, manageAllY, "menu.wrights.manage_all", mouseX, mouseY, manageAllWidth);
        int rowX = jobX + 4;
        int rowW = jobW - 8;
        Component workTitle = workingJobs.size() > workRows
                ? Component.translatable("menu.wrights.jobs_working_count", workingJobs.size())
                : Component.translatable("menu.wrights.jobs_working");
        drawJobGroup(graphics, jobX, workGroupY, jobW, workGroupH, workTitle, workingJobs.isEmpty() ? Component.translatable("menu.wrights.jobs_working_empty") : null, workingJobs.isEmpty() ? 0 : workPauseWidth + workRemoveWidth + 18);
        if (!workingJobs.isEmpty()) {
            drawJobButton(graphics, workRemoveRight, workHeaderY, "menu.wrights.remove_all", mouseX, mouseY, workRemoveWidth);
            drawJobButton(graphics, workPauseRight, workHeaderY, workAllPaused ? "menu.wrights.resume_all" : "menu.wrights.pause_all", mouseX, mouseY, workPauseWidth);
            for (int row = 0; row < workRows && jobScroll + row < workingJobs.size(); row++) {
                drawJobRow(graphics, mouseX, mouseY, rowX, rowW, workListY + row * JOB_ROW, workingJobs.get(jobScroll + row));
            }
        }
        Component doneTitle = finishedJobs.size() > doneRows
                ? Component.translatable("menu.wrights.jobs_finished_count", finishedJobs.size())
                : Component.translatable("menu.wrights.jobs_finished");
        drawJobGroup(graphics, jobX, doneGroupY, jobW, doneGroupH, doneTitle, finishedJobs.isEmpty() ? Component.translatable("menu.wrights.jobs_finished_empty") : null, finishedJobs.isEmpty() ? 0 : doneRemoveWidth + 14);
        if (!finishedJobs.isEmpty()) {
            drawJobButton(graphics, doneRemoveRight, doneHeaderY, "menu.wrights.remove_all", mouseX, mouseY, doneRemoveWidth);
            for (int row = 0; row < doneRows && historyScroll + row < finishedJobs.size(); row++) {
                drawJobRow(graphics, mouseX, mouseY, rowX, rowW, doneListY + row * JOB_ROW, finishedJobs.get(historyScroll + row));
            }
        }
        if (hoveredJob != null) {
            drawJobTooltip(graphics, mouseX, mouseY, hoveredJob);
        }
    }

    private void drawJobGroup(GuiGraphics graphics, int x, int y, int width, int height, Component title, Component empty, int reserved) {
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF3A4C5C);
        graphics.fill(x + 1, y + 1, x + width - 1, y + JOB_HEADER, 0xFF1C2A36);
        graphics.fill(x + 1, y + 1, x + 3, y + JOB_HEADER, WrightsUi.COPPER);
        graphics.fill(x + 1, y + JOB_HEADER - 1, x + width - 1, y + JOB_HEADER, 0xFF31404E);
        String heading = this.font.plainSubstrByWidth(title.getString(), Math.max(40, width - reserved - 12));
        graphics.drawString(this.font, heading, x + 10, y + 6, WrightsUi.TEXT, false);
        if (empty != null) {
            graphics.drawString(this.font, empty, x + 10, y + JOB_HEADER + 6, WrightsUi.MUTED, false);
        }
    }

    private void drawJobRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int width, int y, BlueprintPayloads.JobLine job) {
        boolean working = !job.finished() && !job.saving();
        int buttonY = y + 48;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 66;
        hoveredJob = hover ? job : hoveredJob;
        graphics.fill(x, y, x + width, y + 66, hover ? 0xFF24303C : 0xFF18222C);
        String name = this.font.plainSubstrByWidth(job.name(), Math.max(40, width / 2));
        graphics.drawString(this.font, name, x + 6, y + 4, WrightsUi.TEXT, false);
        Component statusText = jobStatusText(job);
        graphics.drawString(this.font, statusText, x + width - 8 - this.font.width(statusText), y + 4, jobColor(job), false);
        Component source = jobSource(job);
        if (!source.getString().isEmpty()) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(source.getString(), Math.max(40, width - 12)), x + 6, y + 15, WrightsUi.MUTED, false);
        }
        BlockPos anchor = job.anchor();
        graphics.drawString(this.font, Component.translatable("menu.wrights.job_anchor", anchor.getX(), anchor.getY(), anchor.getZ()), x + 6, y + 26, WrightsUi.MUTED, false);
        drawProgress(graphics, x + 6, y + 37, Math.max(40, width - 12), job);
        int right = x + width - 4;
        if (working) {
            String count = crewCountText(job);
            graphics.drawString(this.font, count, x + 6, buttonY + 4, WrightsUi.MUTED, false);
            int manageW = jobButtonWidth("menu.wrights.manage_drones");
            drawJobButton(graphics, x + 6 + this.font.width(count) + 6 + manageW, buttonY, "menu.wrights.manage_drones", mouseX, mouseY, manageW);
        }
        right = drawJobButton(graphics, right, buttonY, "menu.wrights.remove", mouseX, mouseY, 52);
        if (creativeVisit()) {
            right = drawJobButton(graphics, right, buttonY, "menu.wrights.visit", mouseX, mouseY, 52);
        }
        boolean highlighted = WrightsClientState.markedJobs.contains(job.id());
        String markKey = highlighted ? "menu.wrights.mark_on" : "menu.wrights.mark";
        right = drawJobButton(graphics, right, buttonY, markKey, mouseX, mouseY, jobButtonWidth(markKey), highlighted ? 0xFFF2D48A : 0);
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

    private BlueprintPayloads.JobLine hoveredJob;

    private void drawJobTooltip(GuiGraphics graphics, int mouseX, int mouseY, BlueprintPayloads.JobLine job) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(job.name()).getVisualOrderText());
        lines.add(jobStatusText(job).getVisualOrderText());
        lines.add(progressLine(job).getVisualOrderText());
        Component wait = WrightsClientState.jobWait(job);
        if (wait != null) {
            lines.add(wait.getVisualOrderText());
        }
        String skipped = skippedLine(job);
        if (!skipped.isEmpty()) {
            lines.add(Component.literal(skipped).getVisualOrderText());
        }
        graphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    private String crewCountText(BlueprintPayloads.JobLine job) {
        if (job.queued()) {
            return Component.translatable("menu.wrights.job_drone_queued", job.crew().size(), Math.max(job.crew().size(), job.quota())).getString();
        }
        return Component.translatable("menu.wrights.job_drone_count", job.crew().size()).getString();
    }

    private Component jobStatusText(BlueprintPayloads.JobLine job) {
        String named = job.sourceName();
        if (named != null && !named.isBlank() && job.hold() == com.wrights.job.BuildJob.HOLD_PORT_RANGE && !job.finished() && !job.saving() && !job.undoing()) {
            return Component.translatable("menu.wrights.hold_port_named", named);
        }
        return Component.translatable(jobStatus(job));
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
            return holdStatus(job.hold(), true);
        }
        if (job.queued()) {
            return "menu.wrights.queued";
        }
        if (job.hold() != com.wrights.job.BuildJob.HOLD_NONE && job.hold() != com.wrights.job.BuildJob.HOLD_MANUAL) {
            return holdStatus(job.hold(), false);
        }
        return "menu.wrights.running";
    }

    private static String holdStatus(int hold, boolean paused) {
        return switch (hold) {
            case com.wrights.job.BuildJob.HOLD_NO_WRIGHTS -> "menu.wrights.hold_no_wrights";
            case com.wrights.job.BuildJob.HOLD_PLAYER_RANGE -> paused ? "menu.wrights.hold_player" : "menu.wrights.hold_waiting_player";
            case com.wrights.job.BuildJob.HOLD_PORT_RANGE -> "menu.wrights.hold_port";
            case com.wrights.job.BuildJob.HOLD_DIMENSION -> "menu.wrights.hold_dimension";
            case com.wrights.job.BuildJob.HOLD_CHUNK -> "menu.wrights.hold_chunk";
            default -> "menu.wrights.paused";
        };
    }

    private Component jobSource(BlueprintPayloads.JobLine job) {
        BlockPos at = job.sourcePort();
        boolean coords = job.labelPort() && at != null && !at.equals(BlockPos.ZERO);
        String named = job.sourceName();
        boolean hasName = named != null && !named.isBlank();
        return switch (job.source()) {
            case 1 -> Component.translatable("menu.wrights.job_from_you");
            case 2 -> hasName
                    ? (coords
                            ? Component.translatable("menu.wrights.job_from_port_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port_named", named))
                    : (coords
                            ? Component.translatable("menu.wrights.job_from_port_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port"));
            case 3 -> hasName
                    ? (coords
                            ? Component.translatable("menu.wrights.job_from_both_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both_named", named))
                    : (coords
                            ? Component.translatable("menu.wrights.job_from_both_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both"));
            default -> Component.empty();
        };
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
        if (job.queued()) {
            return 0xFF7EB6FF;
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
        if (job.finished() && job.skipped() <= 0 && !job.stopped() && !job.undone() && !job.undoing()) {
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
        return drawJobButton(graphics, right, y, key, mouseX, mouseY, width, 0);
    }

    private int drawJobButton(GuiGraphics graphics, int right, int y, String key, int mouseX, int mouseY, int width, int border) {
        int left = right - width;
        boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 16;
        graphics.fill(left, y, right, y + 16, hover ? 0xFF3A5164 : 0xFF24384A);
        if (border != 0) {
            WrightsUi.border(graphics, left, y, width, 16, border);
        }
        Component label = Component.translatable(key);
        String text = this.font.plainSubstrByWidth(label.getString(), width - 4);
        graphics.drawString(this.font, text, left + (width - this.font.width(text)) / 2, y + 4, WrightsUi.TEXT, false);
        return left - 4;
    }

    private boolean clickJob(double mouseX, double mouseY) {
        prepareJobs();
        if (hitWide(mouseX, mouseY, manageAllRight, manageAllY, manageAllWidth)) {
            if (!requireTool()) {
                return true;
            }
            this.minecraft.setScreen(new DroneRosterScreen(null));
            return true;
        }
        if (!workingJobs.isEmpty()) {
            if (hitWide(mouseX, mouseY, workRemoveRight, workHeaderY, workRemoveWidth)) {
                if (!requireTool()) {
                    return true;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(new UUID(0L, 0L), BlueprintPayloads.JobControl.REMOVE_ACTIVE));
                return true;
            }
            if (hitWide(mouseX, mouseY, workPauseRight, workHeaderY, workPauseWidth)) {
                if (!requireTool()) {
                    return true;
                }
                int action = workAllPaused ? BlueprintPayloads.JobControl.RESUME_ALL : BlueprintPayloads.JobControl.PAUSE_ALL;
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(new UUID(0L, 0L), action));
                return true;
            }
        }
        if (!finishedJobs.isEmpty() && hitWide(mouseX, mouseY, doneRemoveRight, doneHeaderY, doneRemoveWidth)) {
            if (!requireTool()) {
                return true;
            }
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
        int buttonY = y + 48;
        if (mouseY < buttonY || mouseY >= buttonY + 16) {
            return false;
        }
        int rowX = jobX + 4;
        int rowW = jobW - 8;
        if (working) {
            String count = crewCountText(job);
            int manageW = jobButtonWidth("menu.wrights.manage_drones");
            int manageRight = rowX + 6 + this.font.width(count) + 6 + manageW;
            if (mouseX >= manageRight - manageW && mouseX < manageRight) {
                if (!requireTool()) {
                    return true;
                }
                this.minecraft.setScreen(new JobDroneScreen(job.id(), null));
                return true;
            }
        }
        int right = rowX + rowW - 4;
        if (hitJobButton(mouseX, right, buttonY)) {
            if (!requireTool()) {
                return true;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.REMOVE));
            return true;
        }
        right -= 56;
        if (creativeVisit()) {
            if (hitJobButton(mouseX, right, buttonY)) {
                if (!requireTool()) {
                    return true;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.VISIT));
                return true;
            }
            right -= 56;
        }
        String markKey = WrightsClientState.markedJobs.contains(job.id()) ? "menu.wrights.mark_on" : "menu.wrights.mark";
        int markW = jobButtonWidth(markKey);
        if (hitWide(mouseX, mouseY, right, buttonY, markW)) {
            WrightsClientState.toggleMark(job.id());
            return true;
        }
        right -= markW + 4;
        if (job.finished()) {
            if (hitJobButton(mouseX, right, buttonY)) {
                if (!requireTool()) {
                    return true;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.REDO));
                return true;
            }
            right -= 56;
            if (job.canUndo() && hitJobButton(mouseX, right, buttonY)) {
                if (!requireTool()) {
                    return true;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.UNDO));
                return true;
            }
            return false;
        }
        if (hitJobButton(mouseX, right, buttonY)) {
            if (!requireTool()) {
                return true;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), BlueprintPayloads.JobControl.STOP));
            return true;
        }
        right -= 56;
        if (hitJobButton(mouseX, right, buttonY)) {
            if (!requireTool()) {
                return true;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(job.id(), job.paused() ? BlueprintPayloads.JobControl.RESUME : BlueprintPayloads.JobControl.PAUSE));
            return true;
        }
        return false;
    }

    private int[] dronePool() {
        int total = this.minecraft.player == null ? 0 : com.wrights.drone.WrightHomes.countUnits(this.minecraft.player, id -> true);
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
        if (managePage) {
            if (manageTab == 1 && mouseY >= placedListTop() && mouseY < placedListTop() + placedShown() * 18) {
                finishPortRename(true);
                int max = Math.max(0, WrightsClientState.placedPorts.size() - placedShown());
                placedScroll = Mth.clamp(placedScroll - (int) Math.signum(scrollY), 0, max);
                return true;
            }
            if (portListOpen) {
                List<WrightportPlaces.Spot> spots = ports();
                int max = Math.max(0, spots.size() - PORT_ROWS);
                portScroll = Mth.clamp(portScroll - (int) Math.signum(scrollY), 0, max);
                return true;
            }
            if (manage.isOpen()) {
                manage.mouseScrolled(manageHost, mouseX, mouseY, scrollY);
            }
            return true;
        }
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
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, rows.size() - visibleRows()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!managePage) {
            return;
        }
        if (holdTicker++ % 10 == 0) {
            sendHold(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeKeepingMode() {
        if (!requireTool()) {
            return;
        }
        this.minecraft.setScreen(null);
    }

    private void placeShelfButtons(boolean building, boolean any) {
        int gap = 4;
        int y = panelY + panelH - 48;
        int folderW = this.font.width(Component.translatable("menu.wrights.new_folder").getString()) + 12;
        int room = listW - gap * 3;
        int minOther = 30;
        if (folderW > room - minOther * 3) {
            folderW = Math.max(minOther, room - minOther * 3);
        }
        int otherW = Math.max(minOther, (room - folderW) / 3);
        int slack = listW - (folderW + otherW * 3 + gap * 3);
        int x = listX;
        if (folderButton != null) {
            folderButton.setX(x);
            folderButton.setY(y);
            folderButton.setWidth(folderW);
        }
        x += folderW + gap;
        if (duplicateButton != null) {
            duplicateButton.visible = building;
            duplicateButton.active = any && marked.size() <= 1;
            duplicateButton.setX(x);
            duplicateButton.setY(y);
            duplicateButton.setWidth(otherW);
        }
        x += otherW + gap;
        if (editButton != null) {
            editButton.setX(x);
            editButton.setY(y);
            editButton.setWidth(otherW);
        }
        x += otherW + gap;
        if (deleteButton != null) {
            deleteButton.setX(x);
            deleteButton.setY(y);
            deleteButton.setWidth(otherW + Math.max(0, slack));
        }
        int bottom = panelY + panelH - 26;
        int moveW = moveButton == null ? 0 : Math.max(48, moveButton.getWidth());
        int importW = Math.max(40, listW - gap * 2 - otherW - moveW);
        if (importButton != null) {
            importButton.visible = building;
            importButton.active = marked.size() <= 1;
            importButton.setX(listX);
            importButton.setY(bottom);
            importButton.setWidth(importW);
        }
        if (moveButton != null) {
            moveButton.visible = building;
            moveButton.setX(listX + importW + gap);
            moveButton.setY(bottom);
            moveButton.setWidth(moveW);
        }
        if (newButton != null) {
            newButton.setX(listX + importW + gap + (moveButton == null ? 0 : moveW + gap));
            newButton.setY(bottom);
            newButton.setWidth(otherW);
        }
    }

    private void openImport() {
        BlueprintList.Row row = selectedRow();
        this.minecraft.setScreen(new ImportExportScreen(this, row == null ? null : row.id(), row != null && row.folder()));
    }

    private void newBlueprint() {
        if (!requireTool()) {
            return;
        }
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
        if (summary == null || !requireTool()) {
            return;
        }
        WrightsClientState.armBuild(summary);
        PacketDistributor.sendToServer(new BlueprintPayloads.BeginBuild(summary.id()));
        this.minecraft.setScreen(null);
    }

    private void duplicate() {
        BlueprintList.Row row = selectedRow();
        if (row == null) {
            return;
        }
        UUID copyId = UUID.randomUUID();
        savedTab = BlueprintList.Tab.ALL.ordinal();
        pendingSelect = copyId;
        confirmDelete = null;
        confirmSet = List.of();
        searchText = "";
        if (search != null) {
            applyingSearch = true;
            search.setValue("");
            applyingSearch = false;
        }
        for (UUID folder : BlueprintList.chain(row.parent())) {
            WrightsClientState.setOpenLocal(folder, true);
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.OPEN, folder, true, Util.NIL_UUID, 1, ""));
        }
        if (row.folder()) {
            WrightsClientState.setOpenLocal(copyId, true);
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.DuplicateBlueprint(row.id(), copyId));
        refresh();
    }

    /** The first visible row that is not part of the delete, after the set, or before it. */
    private UUID neighborAfterDeletes(List<UUID> ids) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (!ids.contains(rows.get(i).id())) {
                continue;
            }
            if (first < 0) {
                first = i;
            }
            last = i;
        }
        for (int i = last + 1; i < rows.size(); i++) {
            if (!ids.contains(rows.get(i).id())) {
                return rows.get(i).id();
            }
        }
        for (int i = first - 1; i >= 0; i--) {
            if (!ids.contains(rows.get(i).id())) {
                scroll = Math.min(scroll, i);
                return rows.get(i).id();
            }
        }
        return null;
    }

    private void delete() {
        List<BlueprintList.Row> targets = markedRows();
        if (targets.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        for (BlueprintList.Row row : targets) {
            ids.add(row.id());
        }
        if (confirming()) {
            for (BlueprintList.Row row : targets) {
                if (row.folder()) {
                    PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.DELETE, row.id(), true, Util.NIL_UUID, 0, ""));
                } else {
                    PacketDistributor.sendToServer(new BlueprintPayloads.DeleteBlueprint(row.id()));
                }
            }
            UUID next = neighborAfterDeletes(ids);
            confirmDelete = null;
            confirmSet = List.of();
            marked.clear();
            if (next != null) {
                marked.add(next);
            }
            markAnchor = next;
            WrightsClientState.selected = next;
        } else {
            confirmDelete = ids.get(0);
            confirmSet = List.copyOf(ids);
        }
        refresh();
    }

    private void rename() {
        BlueprintList.Row row = selectedRow();
        if (row == null) {
            return;
        }
        if (row.folder()) {
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.RENAME, row.id(), true, Util.NIL_UUID, 0, rename.getValue()));
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.RenameBlueprint(row.id(), rename.getValue()));
    }

    private void discard() {
        if (!requireTool()) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        WrightsClientState.clearLocal();
        WrightsClientState.forgetMode();
        refresh();
    }

    private void cancelPreview() {
        if (!requireTool()) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        WrightsClientState.clearLocal();
        WrightsClientState.forgetMode();
        refresh();
    }

    private BlueprintPayloads.Summary selected() {
        BlueprintList.Row row = selectedRow();
        return row == null || row.folder() ? null : row.summary();
    }

    private BlueprintList.Row selectedRow() {
        if (WrightsClientState.selected == null) {
            return null;
        }
        for (BlueprintList.Row row : rows) {
            if (row.id().equals(WrightsClientState.selected)) {
                return row;
            }
        }
        return null;
    }

    private void renderFolder(GuiGraphics graphics, BlueprintList.Row row) {
        int cx = modelX + Math.max(0, modelW / 2 - 16);
        int cy = modelY + Math.max(8, modelH / 2 - 18);
        graphics.fill(cx, cy, cx + 14, cy + 6, WrightsUi.COPPER);
        graphics.fill(cx, cy + 6, cx + 32, cy + 26, 0xFF8C5A2A);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.folder_keep").getString(), modelW), modelX, cy + 32, WrightsUi.MUTED, false);
        graphics.fill(detailX + 4, detailSplit, detailX + detailW - 4, detailSplit + 1, 0xFF31404E);
        int info = detailSplit + 4;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(row.name(), detailW - 8), detailX + 4, info, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.folder"), detailX + 4, info + 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.folder_count", BlueprintList.childCount(row.id())), detailX + 4, info + 24, WrightsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.name"), detailX + 4, detailSplit + 42, WrightsUi.MUTED, false);
    }

    private boolean clickScope(double mouseX, double mouseY) {
        if (mouseY < headingY || mouseY >= headingY + 14 || mouseX < listX || mouseX >= listX + listW) {
            return false;
        }
        boolean global = mouseX >= listX + listW / 2.0D;
        WrightsClientState.setLibraryScope(global);
        refresh();
        return true;
    }

    private boolean clickTab(double mouseX, double mouseY) {
        int tabY = headingY + 16;
        if (mouseY < tabY || mouseY >= tabY + 14 || mouseX < listX || mouseX >= listX + listW) {
            return false;
        }
        int tab = (int) ((mouseX - listX) * 4 / Math.max(1, listW));
        tab = Mth.clamp(tab, 0, 3);
        if (tab != savedTab) {
            savedTab = tab;
            scroll = 0;
            shelfPress = null;
            shelfDragging = false;
            refresh();
        }
        return true;
    }

    private void moveSelected() {
        boolean toGlobal = !WrightsClientState.libraryGlobal;
        for (BlueprintList.Row row : markedRows()) {
            if (!row.folder() && row.summary() != null) {
                PacketDistributor.sendToServer(new BlueprintPayloads.LibraryMove(row.id(), toGlobal));
            }
        }
    }

    private void placeSortMenu() {
        int widest = 128;
        for (String key : SORT_LABELS) {
            widest = Math.max(widest, this.font.width(Component.translatable(key).getString()) + 72);
        }
        sortMenuW = widest;
        sortMenuH = 8 + SORT_LABELS.length * 16;
        sortMenuX = sortButton.getX() + sortButton.getWidth() - sortMenuW;
        sortMenuY = sortButton.getY() + sortButton.getHeight() + 2;
        if (sortMenuX < panelX + 8) {
            sortMenuX = panelX + 8;
        }
        if (sortMenuY + sortMenuH > panelY + panelH - 8) {
            sortMenuY = sortButton.getY() - sortMenuH - 2;
        }
    }

    private void renderSortMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sortButton == null) {
            return;
        }
        placeSortMenu();
        WrightsUi.panel(graphics, sortMenuX, sortMenuY, sortMenuW, sortMenuH);
        int selected = WrightsClientConfig.sortKey(savedTab);
        boolean ascending = WrightsClientConfig.sortAscending(savedTab);
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int y = sortMenuY + 4 + i * 16;
            boolean on = selected == i;
            boolean hover = mouseX >= sortMenuX && mouseX < sortMenuX + sortMenuW && mouseY >= y && mouseY < y + 16;
            if (on || hover) {
                graphics.fill(sortMenuX + 3, y, sortMenuX + sortMenuW - 2, y + 15, on ? 0xFF24384A : 0xFF1C2A37);
            }
            String label = Component.translatable(SORT_LABELS[i]).getString();
            graphics.drawString(this.font, label, sortMenuX + 8, y + 4, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
            if (on && i != BlueprintList.SortKey.MANUAL.ordinal()) {
                String direction = sortDirection(i, ascending);
                graphics.drawString(this.font, direction, sortMenuX + sortMenuW - 8 - this.font.width(direction), y + 4, WrightsUi.COPPER, false);
            }
        }
    }

    private String sortDirection(int key, boolean ascending) {
        String id = switch (key) {
            case 1 -> ascending ? "menu.wrights.sort_az" : "menu.wrights.sort_za";
            case 2 -> ascending ? "menu.wrights.sort_small" : "menu.wrights.sort_large";
            case 3 -> ascending ? "menu.wrights.sort_few" : "menu.wrights.sort_many";
            case 4 -> ascending ? "menu.wrights.sort_least" : "menu.wrights.sort_most";
            case 5 -> ascending ? "menu.wrights.sort_oldest" : "menu.wrights.sort_newest";
            default -> "";
        };
        return id.isEmpty() ? "" : Component.translatable(id).getString();
    }

    private boolean pickSort(double mouseX, double mouseY) {
        if (sortButton == null) {
            return false;
        }
        placeSortMenu();
        if (mouseX < sortMenuX || mouseX >= sortMenuX + sortMenuW || mouseY < sortMenuY || mouseY >= sortMenuY + sortMenuH) {
            return false;
        }
        int row = (int) ((mouseY - sortMenuY - 4) / 16);
        if (row >= 0 && row < SORT_LABELS.length) {
            chooseSort(row);
        }
        return true;
    }

    private void chooseSort(int key) {
        int current = WrightsClientConfig.sortKey(savedTab);
        boolean ascending = WrightsClientConfig.sortAscending(savedTab);
        if (key == BlueprintList.SortKey.MANUAL.ordinal()) {
            if (current == key) {
                return;
            }
            if (savedTab != BlueprintList.Tab.ALL.ordinal() && WrightsClientConfig.manualOrder(savedTab).isEmpty()) {
                List<UUID> ids = new ArrayList<>();
                for (BlueprintList.Row row : BlueprintList.rows(BlueprintList.Tab.values()[savedTab], "")) {
                    ids.add(row.id());
                }
                WrightsClientConfig.setManualOrder(savedTab, ids);
            }
            WrightsClientConfig.setListSort(savedTab, key, true);
        } else {
            boolean next = key == current ? !ascending : key == BlueprintList.SortKey.NAME.ordinal();
            WrightsClientConfig.setListSort(savedTab, key, next);
        }
        scroll = 0;
        refresh();
    }

    private void rememberManual(UUID id, int toIndex) {
        List<UUID> visible = new ArrayList<>();
        for (BlueprintList.Row row : rows) {
            visible.add(row.id());
        }
        int from = visible.indexOf(id);
        if (from < 0) {
            return;
        }
        visible.remove(from);
        visible.add(Mth.clamp(toIndex, 0, visible.size()), id);
        List<UUID> merged = new ArrayList<>();
        int cursor = 0;
        for (UUID old : WrightsClientConfig.manualOrder(savedTab)) {
            if (visible.contains(old)) {
                if (cursor < visible.size()) {
                    UUID next = visible.get(cursor++);
                    if (!merged.contains(next)) {
                        merged.add(next);
                    }
                }
            } else if (inLibrary(old) && !merged.contains(old)) {
                merged.add(old);
            }
        }
        while (cursor < visible.size()) {
            UUID next = visible.get(cursor++);
            if (!merged.contains(next)) {
                merged.add(next);
            }
        }
        WrightsClientConfig.setManualOrder(savedTab, merged);
        refresh();
    }

    private static boolean inLibrary(UUID id) {
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (summary.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean canArrange() {
        return WrightsClientConfig.sortKey(savedTab) == BlueprintList.SortKey.MANUAL.ordinal()
                && (search == null || search.getValue().trim().isEmpty());
    }

    private void newFolder() {
        UUID id = UUID.randomUUID();
        BlueprintList.Row row = selectedRow();
        UUID parent = row == null ? Util.NIL_UUID : (row.folder() ? row.id() : row.parent());
        savedTab = BlueprintList.Tab.ALL.ordinal();
        for (UUID folder : BlueprintList.chain(parent)) {
            WrightsClientState.setOpenLocal(folder, true);
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.OPEN, folder, true, Util.NIL_UUID, 1, ""));
        }
        pendingSelect = id;
        PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(
                BlueprintPayloads.ShelfEdit.CREATE,
                id,
                true,
                parent,
                0,
                Component.translatable("menu.wrights.new_folder").getString()));
        refresh();
    }

    private void selectRow(UUID id) {
        int index = indexOf(id);
        if (index < 0) {
            return;
        }
        if (shelfRange && markAnchor != null && indexOf(markAnchor) >= 0) {
            int anchor = indexOf(markAnchor);
            int start = Math.min(anchor, index);
            int end = Math.max(anchor, index);
            if (!shelfAdditive) {
                marked.clear();
            }
            for (int i = start; i <= end; i++) {
                UUID rowId = rows.get(i).id();
                if (!marked.contains(rowId)) {
                    marked.add(rowId);
                }
            }
            WrightsClientState.selected = id;
            confirmDelete = null;
            confirmSet = List.of();
            refresh();
            return;
        }
        if (shelfAdditive) {
            if (marked.isEmpty() && WrightsClientState.selected != null && indexOf(WrightsClientState.selected) >= 0) {
                marked.add(WrightsClientState.selected);
            }
            if (marked.contains(id)) {
                marked.remove(id);
                if (id.equals(WrightsClientState.selected)) {
                    WrightsClientState.selected = marked.isEmpty() ? null : marked.get(marked.size() - 1);
                }
            } else {
                marked.add(id);
                WrightsClientState.selected = id;
                if (markAnchor == null) {
                    markAnchor = id;
                }
            }
            confirmDelete = null;
            confirmSet = List.of();
            refresh();
            return;
        }
        marked.clear();
        marked.add(id);
        markAnchor = id;
        activateRow(id);
    }

    private void syncMarks() {
        marked.removeIf(id -> indexOf(id) < 0);
        if (markAnchor != null && indexOf(markAnchor) < 0) {
            markAnchor = null;
        }
        if (WrightsClientState.selected != null && indexOf(WrightsClientState.selected) < 0) {
            WrightsClientState.selected = marked.isEmpty() ? null : marked.get(marked.size() - 1);
        }
        if (marked.isEmpty() && WrightsClientState.selected != null && indexOf(WrightsClientState.selected) >= 0) {
            marked.add(WrightsClientState.selected);
        }
        if (markAnchor == null) {
            markAnchor = WrightsClientState.selected;
        }
        if (!marked.isEmpty() && (WrightsClientState.selected == null || !marked.contains(WrightsClientState.selected))) {
            WrightsClientState.selected = marked.get(marked.size() - 1);
        }
        if (!confirming()) {
            confirmSet = List.of();
            confirmDelete = null;
        }
    }

    private boolean confirming() {
        return !confirmSet.isEmpty() && confirmSet.size() == marked.size() && confirmSet.containsAll(marked);
    }

    private boolean isBlueprint(UUID id) {
        return WrightsClientState.library.stream().anyMatch(entry -> entry.id().equals(id));
    }

    private List<BlueprintList.Row> markedRows() {
        List<BlueprintList.Row> picked = new ArrayList<>();
        for (BlueprintList.Row row : rows) {
            if (marked.contains(row.id())) {
                picked.add(row);
            }
        }
        if (picked.isEmpty()) {
            BlueprintList.Row row = selectedRow();
            if (row != null) {
                picked.add(row);
            }
        }
        return picked;
    }

    private List<UUID> dragIds() {
        if (shelfPress == null) {
            return List.of();
        }
        if (marked.contains(shelfPress) && marked.size() > 1) {
            List<UUID> ids = new ArrayList<>();
            for (BlueprintList.Row row : rows) {
                if (marked.contains(row.id())) {
                    ids.add(row.id());
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        return List.of(shelfPress);
    }

    private boolean dragHits(UUID target) {
        for (UUID id : dragIds()) {
            if (id.equals(target)) {
                return true;
            }
            BlueprintList.Row row = rowOf(id);
            if (row != null && row.folder() && BlueprintList.holds(id, target)) {
                return true;
            }
        }
        return false;
    }

    private void dropMarkedInto(UUID parent, List<UUID> moving) {
        if (parent != null && !parent.equals(Util.NIL_UUID)) {
            WrightsClientState.setOpenLocal(parent, true);
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(
                    BlueprintPayloads.ShelfEdit.OPEN, parent, true, Util.NIL_UUID, 1, ""));
        }
        for (UUID id : moving) {
            BlueprintList.Row row = rowOf(id);
            if (row == null || nestedInDrag(id, moving)) {
                continue;
            }
            if (parent != null && (id.equals(parent) || (row.folder() && BlueprintList.holds(id, parent)))) {
                continue;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(
                    BlueprintPayloads.ShelfEdit.MOVE, id, row.folder(), parent, 1_000_000, ""));
        }
    }

    private boolean nestedInDrag(UUID id, List<UUID> moving) {
        BlueprintList.Row row = rowOf(id);
        if (row == null) {
            return false;
        }
        UUID parent = row.parent();
        for (int guard = 0; parent != null && !parent.equals(Util.NIL_UUID) && guard < 64; guard++) {
            if (moving.contains(parent)) {
                return true;
            }
            BlueprintList.Row folder = rowOf(parent);
            parent = folder == null ? null : folder.parent();
        }
        return false;
    }

    private int indexOf(UUID id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private BlueprintList.Row rowOf(UUID id) {
        int index = indexOf(id);
        return index < 0 ? null : rows.get(index);
    }

    private void activateRow(UUID id) {
        BlueprintList.Row row = null;
        for (BlueprintList.Row candidate : rows) {
            if (candidate.id().equals(id)) {
                row = candidate;
                break;
            }
        }
        if (row == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean again = id.equals(WrightsClientState.selected) && now - lastClick < 350;
        WrightsClientState.selected = id;
        if (!id.equals(confirmDelete)) {
            confirmDelete = null;
            confirmSet = List.of();
        }
        lastClick = now;
        if (row.folder()) {
            if (savedTab == BlueprintList.Tab.ALL.ordinal()) {
                boolean open = !WrightsClientState.openFolders.contains(id);
                WrightsClientState.setOpenLocal(id, open);
                PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.OPEN, id, true, Util.NIL_UUID, open ? 1 : 0, ""));
            }
            refresh();
            return;
        }
        if (again) {
            refresh();
            build();
            return;
        }
        refresh();
    }

    private void updateDrop(double mouseX, double mouseY) {
        dropInto = false;
        dropHighlight = null;
        dropLineY = -1;
        dropIndex = -1;
        if (!insideList(mouseX, mouseY)) {
            dropParent = Util.NIL_UUID;
            dropIndex = 1_000_000;
            dropLineY = listY + Math.min(visibleRows(), Math.max(0, rows.size() - scroll)) * ROW;
            return;
        }
        int shown = Math.min(visibleRows(), Math.max(0, rows.size() - scroll));
        int contentBottom = listY + shown * ROW;
        if (rows.isEmpty() || mouseY >= contentBottom) {
            dropParent = Util.NIL_UUID;
            dropIndex = adjustedIndex(Util.NIL_UUID, 1_000_000);
            dropLineY = contentBottom;
            return;
        }
        int index = Mth.clamp(scroll + (int) ((mouseY - listY) / ROW), 0, rows.size() - 1);
        BlueprintList.Row row = rows.get(index);
        int y = listY + (index - scroll) * ROW;
        int local = (int) mouseY - y;
        if (row.folder() && local >= 4 && local < ROW - 4) {
            if (dragHits(row.id())) {
                return;
            }
            dropInto = true;
            dropHighlight = row.id();
            dropParent = row.id();
            dropIndex = 1_000_000;
            return;
        }
        if (shelfPressFolder && BlueprintList.holds(shelfPress, row.parent())) {
            return;
        }
        boolean before = local < ROW / 2;
        if (!before && row.folder() && index + 1 < rows.size() && row.id().equals(rows.get(index + 1).parent())) {
            if (shelfPress.equals(row.id()) || (shelfPressFolder && BlueprintList.holds(shelfPress, row.id()))) {
                return;
            }
            dropParent = row.id();
            dropIndex = adjustedIndex(row.id(), 0);
            dropLineY = y + ROW - 1;
            return;
        }
        dropParent = row.parent();
        dropIndex = adjustedIndex(row.parent(), before ? row.index() : row.index() + 1);
        dropLineY = before ? y : y + ROW - 1;
    }

    private int adjustedIndex(UUID parent, int index) {
        if (shelfPressParent.equals(parent) && shelfPressIndex < index) {
            return index - 1;
        }
        return index;
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW);
    }

    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;
    }

    /** Shelf buttons sit on the bottom edge of the list, so a click there belongs to the button. */
    private boolean overWidget(double mouseX, double mouseY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof WrightsButton button && button.visible && button.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void leaveManage() {
        finishPortRename(true);
        if (!managePage && !holding) {
            return;
        }
        managePage = false;
        portListOpen = false;
        manage.setActive(false);
        sendHold(false);
    }

    private int manageX() {
        return listX - 2;
    }

    private int manageY() {
        return panelY + 34;
    }

    private int manageW() {
        return panelX + panelW - 12 - manageX();
    }

    private int manageH() {
        return panelH - 68;
    }

    private int portBarX() {
        return manageX() + 8;
    }

    private int portBarY() {
        return manageY() + 6;
    }

    private int portBarW() {
        return Math.max(40, manageW() - 16);
    }

    private List<WrightportPlaces.Spot> ports() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return List.of();
        }
        return WrightportPlaces.list(this.minecraft.player);
    }

    private WrightportPlaces.Spot currentSpot() {
        List<WrightportPlaces.Spot> spots = ports();
        if (spots.isEmpty()) {
            return null;
        }
        for (WrightportPlaces.Spot spot : spots) {
            if (spot.kind() == portKind && spot.index() == portIndex) {
                return spot;
            }
        }
        WrightportPlaces.Spot first = spots.get(0);
        if (portKind != first.kind() || portIndex != first.index()) {
            portKind = first.kind();
            portIndex = first.index();
            manage.resetView();
        }
        return first;
    }

    private void sendHold(boolean active) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (!active || !managePage) {
            if (holding) {
                holding = false;
                PacketDistributor.sendToServer(new BlueprintPayloads.PortHold(0, 0, false));
            }
            return;
        }
        WrightportPlaces.Spot spot = currentSpot();
        if (spot == null) {
            if (holding) {
                holding = false;
                PacketDistributor.sendToServer(new BlueprintPayloads.PortHold(0, 0, false));
            }
            return;
        }
        holding = true;
        PacketDistributor.sendToServer(new BlueprintPayloads.PortHold(spot.kind(), spot.index(), true));
    }

    private boolean manageOpen() {
        if (!managePage) {
            return false;
        }
        if (manageTab == 0) {
            return currentSpot() != null;
        }
        return placedReady();
    }

    private boolean placedReady() {
        return placedPick != null
                && WrightsClientState.placedKnown
                && !WrightsClientState.placedMissing
                && placedPick.dimension().equals(WrightsClientState.placedDim)
                && placedPick.pos().equals(WrightsClientState.placedPos);
    }

    private void reconcilePlaced() {
        if (placedPick == null) {
            return;
        }
        for (BlueprintPayloads.PlacedPort port : WrightsClientState.placedPorts) {
            if (samePort(port, placedPick)) {
                placedPick = port;
                return;
            }
        }
        placedPick = null;
        WrightsClientState.placedKnown = false;
    }

    private boolean samePort(BlueprintPayloads.PlacedPort port, BlueprintPayloads.PlacedPort other) {
        return port != null && other != null && port.dimension().equals(other.dimension()) && port.pos().equals(other.pos());
    }

    private int chooserY() {
        return portBarY() + 20;
    }

    private int placedListTop() {
        return portBarY() + 20;
    }

    private int placedFit() {
        int room = manageY() + manageH() - placedListTop() - 8;
        int fit = Math.max(1, room / 18);
        if (placedPick != null) {
            fit = Math.min(3, fit);
        }
        return fit;
    }

    private int placedShown() {
        int count = WrightsClientState.placedPorts.size();
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, placedFit());
    }

    private void renderManage(GuiGraphics graphics, int mouseX, int mouseY) {
        drawManageTabs(graphics, mouseX, mouseY);
        if (manageTab == 0) {
            renderPersonal(graphics, mouseX, mouseY);
            return;
        }
        renderPlaced(graphics, mouseX, mouseY);
    }

    private void renderPersonal(GuiGraphics graphics, int mouseX, int mouseY) {
        List<WrightportPlaces.Spot> spots = ports();
        WrightportPlaces.Spot spot = currentSpot();
        if (spot == null) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.manage_none"), portBarX(), chooserY() + 4, WrightsUi.MUTED, false);
            return;
        }
        if (spots.size() > 1) {
            drawPortBar(graphics, spots, spot, mouseX, mouseY);
            if (portListOpen) {
                drawPortList(graphics, spots, mouseX, mouseY);
            }
        }
        if (manage.isOpen()) {
            manage.render(manageHost, graphics, mouseX, mouseY);
        }
    }

    private void renderPlaced(GuiGraphics graphics, int mouseX, int mouseY) {
        List<BlueprintPayloads.PlacedPort> list = WrightsClientState.placedPorts;
        int top = placedListTop();
        if (list.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.placed_none"), portBarX(), top + 4, WrightsUi.MUTED, false);
            return;
        }
        int shown = placedShown();
        placedScroll = Mth.clamp(placedScroll, 0, Math.max(0, list.size() - shown));
        int x = portBarX();
        int w = portBarW();
        int h = shown * 18;
        graphics.fill(x, top, x + w, top + h, 0xFF121A22);
        WrightsUi.border(graphics, x, top, w, h, 0xFF3D4E5E);
        for (int i = 0; i < shown; i++) {
            BlueprintPayloads.PlacedPort port = list.get(placedScroll + i);
            int rowY = top + i * 18;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + 18;
            boolean picked = samePort(port, placedPick);
            if (hover || picked) {
                graphics.fill(x + 1, rowY + 1, x + w - 1, rowY + 17, picked ? 0xFF2A4054 : 0xFF243244);
            }
            int right = drawPlacedButtons(graphics, port, x + w - 4, rowY + 1, mouseX, mouseY);
            int units = port.units();
            if (picked && placedReady()) {
                units = WrightsClientState.placedSwarm.units().size();
            }
            if (renamingPort(port) && portRename != null) {
                portRename.setX(x + 4);
                portRename.setY(rowY + 2);
                portRename.setWidth(Math.max(40, right - x - 10));
            } else {
                String text = this.font.plainSubstrByWidth(placedLine(port, units).getString(), Math.max(0, right - x - 8));
                graphics.drawString(this.font, text, x + 6, rowY + 5, WrightsUi.TEXT, false);
            }
        }
        if (placedPick != null && !placedReady()) {
            int noteY = top + h + 8;
            String key = placedMissingHere() ? "menu.wrights.placed_missing" : "menu.wrights.placed_loading";
            graphics.drawString(this.font, Component.translatable(key), x, noteY, WrightsUi.MUTED, false);
        }
        if (manage.isOpen()) {
            manage.render(manageHost, graphics, mouseX, mouseY);
        }
    }

    private boolean placedMissingHere() {
        return WrightsClientState.placedKnown
                && WrightsClientState.placedMissing
                && placedPick != null
                && placedPick.dimension().equals(WrightsClientState.placedDim)
                && placedPick.pos().equals(WrightsClientState.placedPos);
    }

    private int drawPlacedButtons(GuiGraphics graphics, BlueprintPayloads.PlacedPort port, int right, int y, int mouseX, int mouseY) {
        if (creativeVisit()) {
            right = drawJobButton(graphics, right, y, "menu.wrights.visit", mouseX, mouseY, jobButtonWidth("menu.wrights.visit"));
        }
        right = drawJobButton(graphics, right, y, "menu.wrights.rename", mouseX, mouseY, jobButtonWidth("menu.wrights.rename"));
        boolean marked = WrightsClientState.portMarked(port.dimension(), port.pos());
        String markKey = marked ? "menu.wrights.mark_on" : "menu.wrights.mark";
        return drawJobButton(graphics, right, y, markKey, mouseX, mouseY, jobButtonWidth(markKey), marked ? 0xFFF2D48A : 0);
    }

    private Component placedLine(BlueprintPayloads.PlacedPort port, int units) {
        String here = this.minecraft.level == null ? "" : this.minecraft.level.dimension().location().toString();
        if (here.equals(port.dimension())) {
            Component line = Component.translatable("menu.wrights.placed_line", port.pos().getX(), port.pos().getY(), port.pos().getZ(), units);
            return withPortName(port, line);
        }
        String dim = port.dimension();
        int colon = dim.indexOf(':');
        if (colon >= 0 && colon + 1 < dim.length()) {
            dim = dim.substring(colon + 1);
        }
        Component line = Component.translatable("menu.wrights.placed_line_dim", port.pos().getX(), port.pos().getY(), port.pos().getZ(), units, dim);
        return withPortName(port, line);
    }

    private Component withPortName(BlueprintPayloads.PlacedPort port, Component line) {
        String name = port.name();
        Component shown = name == null || name.isBlank() ? line : WrightportBlockEntity.titled(name).copy().append(" · ").append(line);
        if (port.unowned()) {
            shown = shown.copy().append(" · ").append(Component.translatable("block.wrights.wrightport.no_owner"));
        }
        return shown;
    }

    private boolean renamingPort(BlueprintPayloads.PlacedPort port) {
        return portRenamePos != null && port.dimension().equals(portRenameDim) && port.pos().equals(portRenamePos);
    }

    private void beginPortRename(BlueprintPayloads.PlacedPort port) {
        if (portRename == null || port == null) {
            return;
        }
        if (renamingPort(port)) {
            portRename.setFocused(true);
            setFocused(portRename);
            return;
        }
        finishPortRename(true);
        portRenameDim = port.dimension();
        portRenamePos = port.pos();
        portRename.setValue(port.name() == null ? "" : port.name());
        portRename.setCursorPosition(portRename.getValue().length());
        portRename.setHighlightPos(0);
        portRename.setVisible(true);
        portRename.setFocused(true);
        setFocused(portRename);
    }

    private void finishPortRename(boolean send) {
        if (portRenamePos == null || portRename == null) {
            return;
        }
        String dim = portRenameDim;
        BlockPos pos = portRenamePos;
        String value = portRename.getValue();
        portRenamePos = null;
        portRenameDim = "";
        portRename.setFocused(false);
        portRename.setVisible(false);
        if (getFocused() == portRename) {
            setFocused(null);
        }
        if (send) {
            PacketDistributor.sendToServer(new BlueprintPayloads.RenameWrightport(dim, pos, value));
        }
    }

    private void drawManageTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = portBarY();
        int x = portBarX();
        int personalW = manageTabWidth(0);
        drawManageTab(graphics, mouseX, mouseY, x, y, personalW, 0);
        drawManageTab(graphics, mouseX, mouseY, x + personalW + 4, y, manageTabWidth(1), 1);
    }

    private void drawManageTab(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int index) {
        boolean on = manageTab == index;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        graphics.fill(x, y, x + w, y + 16, on ? 0xFF24384A : (hover ? 0xFF223445 : 0xFF1C2A37));
        if (on) {
            graphics.fill(x, y, x + w, y + 2, WrightsUi.COPPER);
        }
        WrightsUi.border(graphics, x, y, w, 16, on || hover ? WrightsUi.COPPER : 0xFF3D4E5E);
        String label = this.font.plainSubstrByWidth(Component.translatable(manageTabKey(index)).getString(), w - 8);
        graphics.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 4, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
    }

    private int manageTabWidth(int index) {
        return this.font.width(Component.translatable(manageTabKey(index))) + 16;
    }

    private String manageTabKey(int index) {
        return index == 0 ? "menu.wrights.tab_personal" : "menu.wrights.tab_wrightports";
    }

    private boolean clickManageTabs(double mouseX, double mouseY) {
        int y = portBarY();
        if (mouseY < y || mouseY >= y + 16) {
            return false;
        }
        int personalW = manageTabWidth(0);
        int x = portBarX();
        if (mouseX >= x && mouseX < x + personalW) {
            if (manageTab != 0) {
                finishPortRename(true);
                manageTab = 0;
                portListOpen = false;
                manage.resetView();
                sendHold(true);
                refresh();
            }
            return true;
        }
        int placedX = x + personalW + 4;
        int placedW = manageTabWidth(1);
        if (mouseX >= placedX && mouseX < placedX + placedW) {
            if (manageTab != 1) {
                manageTab = 1;
                portListOpen = false;
                manage.resetView();
                sendHold(false);
                PacketDistributor.sendToServer(new BlueprintPayloads.PlacedPortsAsk());
                if (placedPick != null) {
                    WrightsClientState.placedKnown = false;
                    PacketDistributor.sendToServer(new BlueprintPayloads.PlacedSwarmAsk(placedPick.dimension(), placedPick.pos()));
                }
                refresh();
            }
            return true;
        }
        return false;
    }

    private boolean clickPlacedList(double mouseX, double mouseY) {
        List<BlueprintPayloads.PlacedPort> list = WrightsClientState.placedPorts;
        int shown = placedShown();
        if (shown <= 0) {
            return false;
        }
        int x = portBarX();
        int w = portBarW();
        int top = placedListTop();
        if (mouseX < x || mouseX >= x + w || mouseY < top || mouseY >= top + shown * 18) {
            return false;
        }
        int row = (int) ((mouseY - top) / 18);
        int index = placedScroll + row;
        if (index < 0 || index >= list.size()) {
            return true;
        }
        BlueprintPayloads.PlacedPort port = list.get(index);
        if (portRename != null && portRename.visible && portRename.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int right = x + w - 4;
        int buttonY = top + row * 18 + 1;
        if (creativeVisit()) {
            int goW = jobButtonWidth("menu.wrights.visit");
            if (hitWide(mouseX, mouseY, right, buttonY, goW)) {
                pickPlaced(port);
                PacketDistributor.sendToServer(new BlueprintPayloads.PlacedVisit(port.dimension(), port.pos()));
                return true;
            }
            right -= goW + 4;
        }
        int renameW = jobButtonWidth("menu.wrights.rename");
        if (hitWide(mouseX, mouseY, right, buttonY, renameW)) {
            beginPortRename(port);
            return true;
        }
        right -= renameW + 4;
        boolean marked = WrightsClientState.portMarked(port.dimension(), port.pos());
        String markKey = marked ? "menu.wrights.mark_on" : "menu.wrights.mark";
        int markW = jobButtonWidth(markKey);
        if (hitWide(mouseX, mouseY, right, buttonY, markW)) {
            WrightsClientState.togglePortMark(port.dimension(), port.pos());
            return true;
        }
        pickPlaced(port);
        return true;
    }

    private void pickPlaced(BlueprintPayloads.PlacedPort port) {
        finishPortRename(true);
        if (!samePort(port, placedPick)) {
            manage.resetView();
        }
        placedPick = port;
        WrightsClientState.placedKnown = false;
        PacketDistributor.sendToServer(new BlueprintPayloads.PlacedSwarmAsk(port.dimension(), port.pos()));
        refresh();
    }

    private void drawPortBar(GuiGraphics graphics, List<WrightportPlaces.Spot> spots, WrightportPlaces.Spot spot, int mouseX, int mouseY) {
        int x = portBarX();
        int y = chooserY();
        int w = portBarW();
        boolean open = portListOpen;
        boolean hover = spots.size() > 1 && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        graphics.fill(x, y, x + w, y + 16, open || hover ? 0xFF2C3E52 : 0xFF1A2633);
        WrightsUi.border(graphics, x, y, w, 16, open || hover ? WrightsUi.COPPER : 0xFF3D4E5E);
        String text = this.font.plainSubstrByWidth(portLabel(spots, spot).getString(), Math.max(0, w - (spots.size() > 1 ? 18 : 8)));
        graphics.drawString(this.font, text, x + 6, y + 4, WrightsUi.TEXT, false);
        if (spots.size() > 1) {
            graphics.drawString(this.font, open ? "^" : "v", x + w - 12, y + 4, WrightsUi.MUTED, false);
        }
    }

    private void drawPortList(GuiGraphics graphics, List<WrightportPlaces.Spot> spots, int mouseX, int mouseY) {
        int x = portBarX();
        int y = chooserY() + 16;
        int w = portBarW();
        int rows = Math.min(PORT_ROWS, spots.size());
        int h = rows * 16;
        graphics.fill(x, y, x + w, y + h, 0xFF121A22);
        WrightsUi.border(graphics, x, y, w, h, WrightsUi.COPPER);
        int start = Mth.clamp(portScroll, 0, Math.max(0, spots.size() - rows));
        for (int i = 0; i < rows; i++) {
            WrightportPlaces.Spot spot = spots.get(start + i);
            int rowY = y + i * 16;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + 16;
            boolean picked = spot.kind() == portKind && spot.index() == portIndex;
            if (hover || picked) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + 16, picked ? 0xFF2A4054 : 0xFF243244);
            }
            String text = this.font.plainSubstrByWidth(portLabel(spots, spot).getString(), Math.max(0, w - 12));
            graphics.drawString(this.font, text, x + 6, rowY + 4, WrightsUi.TEXT, false);
        }
    }

    private Component portLabel(List<WrightportPlaces.Spot> spots, WrightportPlaces.Spot spot) {
        ItemStack stack = ItemStack.EMPTY;
        if (this.minecraft != null && this.minecraft.player != null) {
            stack = WrightportPlaces.stack(this.minecraft.player, spot.kind(), spot.index());
        }
        Component name = stack.isEmpty() ? Component.translatable("item.wrights.personal_wrightport") : stack.getHoverName();
        if (spots.size() < 2) {
            return name;
        }
        return Component.empty().append(name).append(Component.literal(" — ")).append(WrightportPlaces.where(spot));
    }

    private boolean clickPortList(double mouseX, double mouseY) {
        List<WrightportPlaces.Spot> spots = ports();
        if (spots.isEmpty()) {
            portListOpen = false;
            return false;
        }
        if (spots.size() < 2) {
            portListOpen = false;
            return false;
        }
        int x = portBarX();
        int y = chooserY();
        int w = portBarW();
        if (!portListOpen && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16) {
            portListOpen = true;
            portScroll = 0;
            return true;
        }
        if (!portListOpen) {
            return false;
        }
        int rows = Math.min(PORT_ROWS, spots.size());
        int listY = y + 16;
        if (mouseX >= x && mouseX < x + w && mouseY >= listY && mouseY < listY + rows * 16) {
            int row = (int) ((mouseY - listY) / 16);
            int index = portScroll + row;
            if (index >= 0 && index < spots.size()) {
                WrightportPlaces.Spot spot = spots.get(index);
                if (spot.kind() != portKind || spot.index() != portIndex) {
                    portKind = spot.kind();
                    portIndex = spot.index();
                    manage.resetView();
                    sendHold(true);
                }
            }
            portListOpen = false;
            return true;
        }
        portListOpen = false;
        return true;
    }

    private final class MenuManageHost implements ManageHost {
        @Override
        public int screenWidth() {
            return WrightsMenuScreen.this.width;
        }

        @Override
        public int screenHeight() {
            return WrightsMenuScreen.this.height;
        }

        @Override
        public Font font() {
            return WrightsMenuScreen.this.font;
        }

        @Override
        public Minecraft minecraft() {
            return WrightsMenuScreen.this.minecraft;
        }

        @Override
        public Swarm swarm() {
            if (manageTab == 1) {
                return placedReady() ? WrightsClientState.placedSwarm : Swarm.EMPTY;
            }
            WrightportPlaces.Spot spot = currentSpot();
            if (spot == null || WrightsMenuScreen.this.minecraft == null || WrightsMenuScreen.this.minecraft.player == null) {
                return Swarm.from(ItemStack.EMPTY);
            }
            ItemStack stack = WrightportPlaces.stack(WrightsMenuScreen.this.minecraft.player, spot.kind(), spot.index());
            if (!(stack.getItem() instanceof PersonalWrightportItem)) {
                return Swarm.from(ItemStack.EMPTY);
            }
            return Swarm.from(stack);
        }

        @Override
        public boolean away(UUID id) {
            for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
                if (!job.finished() && job.crew().contains(id)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void edit(int action, int index) {
            if (manageTab == 1) {
                if (placedPick == null) {
                    return;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.PlacedEdit(placedPick.dimension(), placedPick.pos(), action, index));
                return;
            }
            WrightportPlaces.Spot spot = currentSpot();
            if (spot == null) {
                return;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.PortEdit(spot.kind(), spot.index(), action, index));
            sendHold(true);
        }

        @Override
        public void rename(int unit, String name) {
            if (manageTab == 1) {
                if (placedPick == null) {
                    return;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.RenameWright(2, placedPick.dimension(), placedPick.pos(), 0, 0, unit, name));
                return;
            }
            WrightportPlaces.Spot spot = currentSpot();
            if (spot == null) {
                return;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.RenameWright(1, "", BlockPos.ZERO, spot.kind(), spot.index(), unit, name));
            sendHold(true);
        }

        @Override
        public void batch(int action, int value, int[] units) {
            if (manageTab == 1) {
                if (placedPick == null) {
                    return;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.PlacedBatch(placedPick.dimension(), placedPick.pos(), action, value, units));
                return;
            }
            WrightportPlaces.Spot spot = currentSpot();
            if (spot == null) {
                return;
            }
            PacketDistributor.sendToServer(new BlueprintPayloads.PortBatch(spot.kind(), spot.index(), action, value, units));
            sendHold(true);
        }

        @Override
        public void addButton(WrightsButton button) {
            addRenderableWidget(button);
        }

        @Override
        public void addField(EditBox box) {
            addRenderableWidget(box);
        }

        @Override
        public void focus(GuiEventListener listener) {
            setFocused(listener);
        }

        @Override
        public void relayout() {
            refresh();
        }

        @Override
        public boolean embedded() {
            return true;
        }

        @Override
        public int areaX() {
            return manageX() + 4;
        }

        @Override
        public int areaY() {
            if (manageTab == 1) {
                return placedListTop() + placedShown() * 18 + 6;
            }
            int y = portBarY() + 22;
            if (ports().size() > 1) {
                y += 22;
            }
            return y;
        }

        @Override
        public int areaW() {
            return Math.max(160, manageW() - 8);
        }

        @Override
        public int areaH() {
            return Math.max(120, manageY() + manageH() - areaY() - 4);
        }
    }
}
