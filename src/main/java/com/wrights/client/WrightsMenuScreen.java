package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;

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
    private static final int PAGE_HELP = 5;
    static final int PORT_ROWS = 8;
    private static int savedPage = PAGE_BUILDING;
    private static int savedScroll;
    private static int savedHistoryScroll;
    private static int savedOptionScroll;
    private static String savedSearch = "";
    private static int savedTab;

    int panelX;
    int panelY;
    int panelW;
    int panelH;
    int listX;
    private int listY;
    private int listW;
    private int listH;
    private int detailH;
    private int headingY;
    private int searchY;
    private int detailX;
    private int detailY;
    private int detailW;
    private int detailScroll;
    private int detailBodyH;
    private int detailContentH;
    private int detailFooterH;
    private int scroll;
    private UUID confirmDelete;
    private final List<UUID> marked = new ArrayList<>();
    private UUID markAnchor;
    private List<UUID> confirmSet = List.of();
    private long lastClick;
    private EditBox search;
    private EditBox rename;
    EditBox portRename;
    BlockPos portRenamePos;
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
    WrightsButton closeButton;
    private WrightsButton renameButton;
    private WrightsButton discardButton;
    private WrightsButton resumeButton;
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
    boolean options;
    private boolean infoPage;
    private boolean helpPage;
    private final HelpPanel helpPanel = new HelpPanel();
    private WrightsButton bindInventory;
    private WrightsButton unbindDigital;
    private int infoScroll;
    private int infoMaxScroll;
    private static int savedInfoScroll;
    private boolean jobsPage;
    private boolean managePage;
    boolean mirrorPage;
    private boolean shapePage;
    private boolean exchangePage;
    private boolean placePage;
    private static boolean savedMirror;
    private static boolean savedShape;
    private static boolean savedExchange;
    private static boolean savedPlace;
    Component mirrorTip;
    private ShapeMenuPanel shapePanel;
    final WrightManagePanel manage = new WrightManagePanel();
    final ManageHost manageHost = new MenuManageHost();
    int portKind = -1;
    int portIndex = -1;
    boolean portListOpen;
    int portScroll;
    int manageTab;
    int placedScroll;
    BlueprintPayloads.PlacedPort placedPick;
    private boolean holding;
    private int holdTicker;
    private int portStockTick;
    int jobScroll;
    int historyScroll;
    final List<BlueprintPayloads.JobLine> workingJobs = new ArrayList<>();
    final List<BlueprintPayloads.JobLine> finishedJobs = new ArrayList<>();
    int workListY;
    int workRows;
    int workGroupY;
    int workGroupH;
    int doneListY;
    int doneRows;
    int doneGroupY;
    int doneGroupH;
    int jobsBodyScroll;
    int jobsViewTop;
    int jobsViewH;
    int jobsContentH;
    /** 1 = working list, 2 = finished list. */
    int jobBarDrag;
    /** Until this millis, draw the controller banner brighter after a blocked click. */
    private long needControllerFlashUntil;
    private boolean lastToolHeld;
    int drag;
    int wallSliderX;
    int wallSliderY;
    int wallSliderW;
    int edgeSliderX;
    int edgeSliderY;
    int edgeSliderW;
    int ghostSliderX;
    int ghostSliderY;
    int ghostSliderW;
    int wallSwatchY;
    int edgeSwatchY;
    int queueEdgeSwatchY;
    int placeEdgeSwatchY;
    int markerX;
    int markerY;
    int markerW;
    int instantX;
    int instantY;
    int instantW;
    int optionScroll;
    int optionViewY;
    int optionViewH;
    int optionContentH;
    int mirrorScroll;
    int mirrorViewH;
    int mirrorContentH;
    /** Left task strip scroll (Building + subs + Jobs…). */
    private int navScroll;
    int optionThumbY;
    int optionThumbH;
    private boolean optionDragging;
    private int optionDragGrab;
    int droneY;
    int droneX;
    int droneW;
    int droneMinusX;
    int dronePlusX;
    int queueX;
    int queueY;
    int queueW;
    int buildMenuX;
    int buildMenuY;
    int buildMenuW;
    int dockWatchX;
    int dockWatchY;
    int dockWatchW;
    int portOrderX;
    int portOrderY;
    int portOrderW;
    int supplyOrderX;
    int supplyOrderY;
    int supplyOrderW;
    int jobDoneMinY;
    int jobDoneMinusX;
    int jobDonePlusX;
    int hudPlaceX;
    int hudPlaceY;
    int hudPlaceW;
    int modePlaceX;
    int modePlaceY;
    int modePlaceW;
    EditBox wallPercent;
    EditBox edgePercent;
    EditBox ghostPercent;
    EditBox placeX;
    EditBox placeY;
    EditBox placeZ;
    EditBox shapeAirOffset;
    boolean suppressOptions;
    private String searchText = "";
    private boolean applyingSearch;

    private final JobsPanel jobsPanel = new JobsPanel(this);

    net.minecraft.client.Minecraft minecraft() {
        return this.minecraft;
    }

    net.minecraft.client.gui.Font font() {
        return this.font;
    }

    private final PortManagePanel portManagePanel = new PortManagePanel(this);

    private final MirrorPanel mirrorPanel = new MirrorPanel(this);

    private final OptionsPanel optionsPanel = new OptionsPanel(this);

    public WrightsMenuScreen() {
        super(Component.translatable("menu.wrights.title"));
        if (WrightsClientState.mode == WrightsClientState.Mode.JOBS) {
            jobsPage = true;
            options = false;
            infoPage = false;
            managePage = false;
            helpPage = false;
        } else if (WrightsClientState.mode == WrightsClientState.Mode.MANAGE) {
            if (WrightsClientState.dockWaiting()) {
                managePage = false;
                jobsPage = WrightsConfig.allowJobs();
                options = !jobsPage;
                infoPage = false;
                helpPage = false;
            } else {
                managePage = true;
                jobsPage = false;
                options = false;
                infoPage = false;
                helpPage = false;
            }
        } else if (WrightsClientState.mode == WrightsClientState.Mode.INFO
                || WrightsClientState.mode == WrightsClientState.Mode.LINK) {
            infoPage = true;
            managePage = false;
            jobsPage = false;
            options = false;
            helpPage = false;
        } else if (WrightsClientState.mode == WrightsClientState.Mode.BUILD
                || WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                || WrightsClientState.mode == WrightsClientState.Mode.SAVING) {
            jobsPage = false;
            options = false;
            infoPage = false;
            managePage = false;
            helpPage = false;
            if (WrightsClientState.placeAim && WrightsConfig.allowPlaceTool()) {
                placePage = true;
                exchangePage = false;
                shapePage = false;
                mirrorPage = false;
            } else if (WrightsClientState.exchangeAim && WrightsConfig.allowExchange()) {
                exchangePage = true;
                placePage = false;
                shapePage = false;
                mirrorPage = false;
            } else if (WrightsClientState.shapeAim && WrightsConfig.allowShape()) {
                shapePage = true;
                placePage = false;
                mirrorPage = false;
                exchangePage = false;
            } else if (WrightsClientState.mirrorAim && WrightsConfig.allowMirror()) {
                mirrorPage = true;
                placePage = false;
                shapePage = false;
                exchangePage = false;
            } else if (savedPlace && WrightsConfig.allowPlaceTool()) {
                placePage = true;
                exchangePage = false;
                shapePage = false;
                mirrorPage = false;
            } else if (savedExchange && WrightsConfig.allowExchange()) {
                exchangePage = true;
                placePage = false;
                shapePage = false;
                mirrorPage = false;
            } else if (savedShape && WrightsConfig.allowShape()) {
                shapePage = true;
                placePage = false;
                mirrorPage = false;
                exchangePage = false;
            } else {
                mirrorPage = savedMirror && WrightsConfig.allowMirror();
                placePage = false;
                shapePage = false;
                exchangePage = false;
            }
        } else {
            jobsPage = savedPage == PAGE_JOBS;
            options = savedPage == PAGE_OPTIONS;
            infoPage = savedPage == PAGE_INFO;
            managePage = savedPage == PAGE_MANAGE;
            helpPage = savedPage == PAGE_HELP;
            boolean building = !jobsPage && !options && !infoPage && !managePage && !helpPage;
            placePage = building && savedPlace && WrightsConfig.allowPlaceTool();
            exchangePage = building && !placePage && savedExchange && WrightsConfig.allowExchange();
            shapePage = building && !placePage && !exchangePage && savedShape && WrightsConfig.allowShape();
            mirrorPage = building && !placePage && !exchangePage && !shapePage && savedMirror && WrightsConfig.allowMirror();
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
        shapePanel = new ShapeMenuPanel(this.font);
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
        closeButton = add(new WrightsButton(detailX + detailW - detailButtonW, bottom, detailButtonW, 18, Component.translatable("menu.wrights.close"), button -> onClose()));
        renameButton = add(new WrightsButton(detailX + Math.max(48, detailW - 78), detailSplit + 52, 74, 14, Component.translatable("menu.wrights.rename"), button -> rename()));
        materialsButton = add(new WrightsButton(detailX + 4, detailSplit + 72, Math.max(40, detailW - 8), 16, Component.translatable("menu.wrights.materials"), button -> openMaterials()));
        discardButton = add(new WrightsButton(panelX + 14, panelY + panelH - 48, 92, 16, Component.translatable("menu.wrights.discard"), button -> discard()));
        resumeButton = add(new WrightsButton(panelX + 14, panelY + panelH - 68, 92, 16, Component.translatable("menu.wrights.resume"), button -> closeKeepingMode()));
        wallPercent = percentBox(value -> WrightsClientConfig.setWallTransparency(value));
        edgePercent = percentBox(value -> WrightsClientConfig.setEdgeOpacity(value));
        ghostPercent = percentBox(value -> WrightsClientConfig.setGhostTransparency(value));
        placeX = offsetBox(0);
        placeY = offsetBox(1);
        placeZ = offsetBox(2);
        shapeAirOffset = intOptionBox(0, 32, value -> WrightsClientConfig.setShapeAirOffset(value));
        bindInventory = add(new WrightsButton(panelX + 14, panelY + panelH - 48, 72, 16,
                Component.translatable("menu.wrights.info_bind"), button -> startBindInventory()));
        unbindDigital = add(new WrightsButton(panelX + 140, panelY + panelH - 48, 120, 18,
                Component.translatable("menu.wrights.digital_unbind"), button -> clearDigitalLink()));
        manage.addWidgets(manageHost);
        lastToolHeld = toolHeld();
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
        savedPage = helpPage ? PAGE_HELP : managePage ? PAGE_MANAGE : jobsPage ? PAGE_JOBS : options ? PAGE_OPTIONS : infoPage ? PAGE_INFO : PAGE_BUILDING;
        if (savedPage == PAGE_BUILDING) {
            savedMirror = mirrorPage;
            savedShape = shapePage;
            savedExchange = exchangePage;
            savedPlace = placePage;
        }
        sendHold(false);
        savedScroll = scroll;
        savedHistoryScroll = historyScroll;
        savedOptionScroll = optionScroll;
        savedInfoScroll = infoScroll;
        savedSearch = searchText == null ? "" : searchText;
        portManagePanel.finishPortRename(true);
        super.removed();
    }

    boolean toolHeld() {
        return this.minecraft != null && this.minecraft.player != null
                && com.wrights.item.ControllerItem.isHeld(this.minecraft.player);
    }

    /** World build / capture / job actions need the controller; library browsing does not. */
    boolean requireTool() {
        if (toolHeld()) {
            return true;
        }
        // Action-bar toasts sit under this menu — flash the in-panel banner instead.
        needControllerFlashUntil = System.currentTimeMillis() + 2500L;
        return false;
    }

    private <T extends WrightsButton> T add(T button) {
        addRenderableWidget(button);
        return button;
    }

    private void layout() {
        panelW = WrightsUi.fitW(540, this.width);
        panelH = WrightsUi.fitH(mirrorPage || shapePage || exchangePage || placePage ? 480 : 360, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        listX = panelX + 126;
        headingY = panelY + 36;
        searchY = panelY + 68;
        listY = panelY + 90;
        listW = Math.min(220, panelW - 300);
        if (listW < 120) {
            listW = Math.max(100, (panelW - 150) / 2);
        }
        // Detail runs to the panel foot so Build/Close stay inside the card.
        detailFooterH = 24;
        detailH = Math.max(ROW + detailFooterH + 8, panelY + panelH - 8 - listY);
        listH = Math.max(ROW, detailH - detailFooterH - 20);
        detailX = listX + listW + 8;
        detailY = listY;
        detailW = panelX + panelW - 12 - detailX;
        if (detailW < 150 && listW > 100) {
            listW = Math.max(100, listW - (150 - detailW));
            detailX = listX + listW + 8;
            detailW = panelX + panelW - 12 - detailX;
        }
        detailBodyH = Math.max(40, detailH - detailFooterH - 4);
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, detailContentH - detailBodyH));
        int modelPrefer = Math.min(Math.max(56, detailBodyH / 2), Math.max(56, detailBodyH - 110));
        detailSplit = detailY + 4 - detailScroll + modelPrefer + 6;
    }

    public void refresh() {
        if (search == null) {
            return;
        }
        honorFeatures();
        layout();
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
        boolean building = !options && !jobsPage && !infoPage && !managePage && !helpPage && !mirrorPage && !shapePage && !exchangePage && !placePage;
        placeClose(building);
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
        newButton.visible = building && WrightsConfig.allowCapture();
        if (folderButton != null) {
            folderButton.visible = building;
        }
        editButton.visible = building && WrightsConfig.allowEdit();
        deleteButton.visible = building;
        placeShelfButtons(building, any);
        buildButton.visible = building;
        renameButton.visible = building;
        discardButton.visible = building && (capture || WrightsClientState.mode == WrightsClientState.Mode.SAVING);
        resumeButton.visible = building && capture;
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
        syncDetailScrollLayout(building, blueprint && marked.size() <= 1);
        placeModeButtons(building);
        boolean showOptions = options;
        wallPercent.visible = showOptions;
        edgePercent.visible = showOptions;
        ghostPercent.visible = showOptions;
        placeX.visible = showOptions;
        placeY.visible = showOptions;
        placeZ.visible = showOptions;
        shapeAirOffset.visible = showOptions;
        if (showOptions) {
            optionsPanel.showPercent(wallPercent, WrightsClientConfig.WALL_TRANSPARENCY.get());
            optionsPanel.showPercent(edgePercent, WrightsClientConfig.EDGE_OPACITY.get());
            optionsPanel.showPercent(ghostPercent, WrightsClientConfig.GHOST_TRANSPARENCY.get());
            optionsPanel.showOffset(placeX, WrightsClientConfig.PLACE_X.get());
            optionsPanel.showOffset(placeY, WrightsClientConfig.PLACE_Y.get());
            optionsPanel.showOffset(placeZ, WrightsClientConfig.PLACE_Z.get());
            optionsPanel.showOffset(shapeAirOffset, WrightsClientConfig.SHAPE_AIR_OFFSET.get());
            optionsPanel.layoutOptions();
        }
        if (bindInventory != null) {
            bindInventory.visible = infoPage;
            bindInventory.active = infoPage && toolHeld();
        }
        if (unbindDigital != null) {
            unbindDigital.visible = infoPage && WrightsConfig.allowLink();
            unbindDigital.active = infoPage && WrightsConfig.allowLink() && WrightsClientState.digitalBound;
            unbindDigital.setX(panelX + 140);
            unbindDigital.setY(panelY + panelH - 48);
        }
        if (jobsPage) {
            // Jobs draws its own buttons; keep real widgets out of the card so they cannot steal hits.
            search.visible = false;
            if (sortButton != null) {
                sortButton.visible = false;
            }
            rename.visible = false;
            if (portRename != null) {
                portRename.visible = false;
            }
            if (folderButton != null) {
                folderButton.visible = false;
            }
            if (duplicateButton != null) {
                duplicateButton.visible = false;
            }
            editButton.visible = false;
            deleteButton.visible = false;
            if (importButton != null) {
                importButton.visible = false;
            }
            if (moveButton != null) {
                moveButton.visible = false;
            }
            newButton.visible = false;
            buildButton.visible = false;
            renameButton.visible = false;
            if (materialsButton != null) {
                materialsButton.visible = false;
            }
            discardButton.visible = false;
            resumeButton.visible = false;
            wallPercent.visible = false;
            edgePercent.visible = false;
            ghostPercent.visible = false;
            placeX.visible = false;
            placeY.visible = false;
            placeZ.visible = false;
            shapeAirOffset.visible = false;
            if (bindInventory != null) {
                bindInventory.visible = false;
            }
            if (unbindDigital != null) {
                unbindDigital.visible = false;
            }
            setFocused(null);
        }
        portManagePanel.reconcilePlaced();
        boolean showManage = manageOpen();
        manage.setActive(showManage);
        if (showManage) {
            manage.layoutChrome(manageHost);
        }
    }

    public void refreshDigital() {
        if (bindInventory != null) {
            bindInventory.visible = infoPage;
            bindInventory.active = infoPage && toolHeld();
        }
        if (unbindDigital != null) {
            unbindDigital.visible = infoPage && WrightsConfig.allowLink();
            unbindDigital.active = infoPage && WrightsConfig.allowLink() && WrightsClientState.digitalBound;
        }
    }

    private void requestDigitalInfo() {
        PacketDistributor.sendToServer(new BlueprintPayloads.DigitalInfoRequest());
    }

    private void startBindInventory() {
        if (!requireTool()) {
            return;
        }
        WrightsClientState.selectBindTool();
        WrightsClientState.styleHudTicks = WrightsClientState.STYLE_HUD_TICKS;
        WrightsClientState.styleAnnounce = true;
        this.minecraft.setScreen(null);
    }

    private void clearDigitalLink() {
        PacketDistributor.sendToServer(new BlueprintPayloads.BindDigital(BlockPos.ZERO, true));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        if ((mirrorPage || shapePage || exchangePage || placePage) && !options && !jobsPage && !infoPage && !managePage && !helpPage) {
            int x = listX - 2;
            int width = panelX + panelW - 12 - x;
            int top = panelY + 34;
            WrightsUi.inset(graphics, x, top, width, wideCardBottom() - top);
            return;
        }
        if (options || jobsPage || infoPage || managePage || helpPage) {
            int x = listX - 2;
            int width = panelX + panelW - 12 - x;
            int top = jobsPage ? jobsPanel.jobsInsetY() : panelY + 34;
            WrightsUi.inset(graphics, x, top, width, wideCardBottom() - top);
            return;
        }
        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
        WrightsUi.inset(graphics, detailX - 2, detailY - 2, detailW + 4, detailH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (options) {
            optionsPanel.layoutOptions();
        }
        if (managePage) {
            portManagePanel.reconcilePlaced();
            boolean open = manageOpen();
            if (manage.isOpen() != open) {
                manage.setActive(open);
            }
            if (open) {
                manage.layoutChrome(manageHost);
            }
        }
        boolean blueprints = !options && !jobsPage && !infoPage && !managePage && !helpPage && !mirrorPage && !shapePage && !exchangePage && !placePage;
        if (blueprints) {
            BlueprintList.Row row = selectedRow();
            syncDetailScrollLayout(true, row != null && !row.folder() && marked.size() <= 1);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("menu.wrights.title"), panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.subtitle"), panelX + 16, panelY + 22, WrightsUi.MUTED, false);
        clampNavScroll();
        int navTop = navViewTop();
        int navH = navViewH();
        graphics.enableScissor(panelX + 8, navTop, panelX + 122, navTop + navH);
        if (taskBuildingY() >= 0) {
            drawTask(graphics, Component.translatable("menu.wrights.building"), buildingPage(), false, taskBuildingY());
            if (buildingPage()) {
                drawBuildSubs(graphics);
            }
        }
        if (taskJobsY() >= 0) {
            drawTask(graphics, Component.translatable("menu.wrights.jobs"), jobsPage, false, taskJobsY());
        }
        if (taskManageY() >= 0) {
            drawTask(graphics, Component.translatable("menu.wrights.manage"), managePage, false, taskManageY());
        }
        drawTask(graphics, Component.translatable("menu.wrights.mining"), false, true, taskMiningY());
        if (taskInfoY() >= 0) {
            drawTask(graphics, Component.translatable("menu.wrights.info"), infoPage, false, taskInfoY());
        }
        drawTask(graphics, Component.translatable("menu.wrights.options"), options, false, taskOptionsY());
        drawTask(graphics, Component.translatable("menu.wrights.help"), helpPage, false, taskHelpY());
        graphics.disableScissor();
        WrightsUi.scrollBar(graphics, panelX + 118, navTop, navH, navScroll, navContentH());
        if (helpPage) {
            int hx = listX - 2;
            int hy = panelY + 38;
            int hw = panelX + panelW - 16 - hx;
            int hh = panelH - 76;
            helpPanel.layout(hx, hy, hw, hh, this.font);
            helpPanel.render(graphics, this.font, mouseX, mouseY);
        } else if (options) {
            optionsPanel.renderOptions(graphics, mouseX, mouseY);
        } else if (infoPage) {
            if (System.currentTimeMillis() - WrightsClientState.digitalInfoAt > 2000L) {
                requestDigitalInfo();
                WrightsClientState.digitalInfoAt = System.currentTimeMillis();
            }
            renderInfo(graphics, mouseX, mouseY);
        } else if (jobsPage) {
            jobsPanel.renderJobs(graphics, mouseX, mouseY);
        } else if (managePage) {
            if (System.currentTimeMillis() - WrightsClientState.digitalInfoAt > 2000L) {
                requestDigitalInfo();
                WrightsClientState.digitalInfoAt = System.currentTimeMillis();
            }
            portManagePanel.renderManage(graphics, mouseX, mouseY);
        } else if (mirrorPage) {
            mirrorPanel.renderMirror(graphics, mouseX, mouseY);
            if (mirrorTip != null) {
                graphics.renderTooltip(this.font, this.font.split(mirrorTip, 240), mouseX, mouseY);
                mirrorTip = null;
            }
        } else if (shapePage || exchangePage || placePage) {
            if (shapePanel == null) {
                shapePanel = new ShapeMenuPanel(this.font);
            }
            shapePanel.setExchangeMode(exchangePage);
            shapePanel.setPlaceMode(placePage);
            int x = listX + 8;
            int w = Math.max(120, panelX + panelW - 28 - listX);
            int closeTop = closeButton != null && closeButton.visible ? closeButton.getY() - 6 : panelY + panelH - 28;
            shapePanel.render(graphics, mouseX, mouseY, x, panelY + 40, w, closeTop, this);
            if (shapePanel.tip() != null) {
                graphics.renderTooltip(this.font, this.font.split(shapePanel.tip(), 240), mouseX, mouseY);
            } else if (!shapePanel.hoveredStack().isEmpty()) {
                graphics.renderTooltip(this.font, shapePanel.hoveredStack(), mouseX, mouseY);
            }
        } else {
            drawScope(graphics, mouseX, mouseY);
            drawTabs(graphics, mouseX, mouseY);
            renderList(graphics, mouseX, mouseY);
            renderDetail(graphics);
            if (!toolHeld() && buildButton != null && buildButton.visible) {
                Component needed = Component.translatable("menu.wrights.controller_needed");
                int textW = this.font.width(needed);
                int labelX = buildButton.getX() + buildButton.getWidth() / 2 - textW / 2;
                int minX = detailX + 4;
                int maxX = detailX + detailW - 4 - textW;
                labelX = Mth.clamp(labelX, minX, Math.max(minX, maxX));
                graphics.drawString(this.font, needed, labelX, buildButton.getY() - 11, 0xFFFF5555, false);
            }
            if (sortOpen) {
                renderSortMenu(graphics, mouseX, mouseY);
            }
        }
        // Always last so Jobs/Manage cards cannot cover the warning.
        if (!toolHeld()) {
            drawNeedController(graphics);
        }
    }

    private void drawNeedController(GuiGraphics graphics) {
        Component label = Component.translatable("menu.wrights.need_controller");
        boolean flash = System.currentTimeMillis() < needControllerFlashUntil;
        float scale = flash ? 2.2F : 1.85F;
        int textW = this.font.width(label);
        int cardX = listX - 2;
        int cardW = panelX + panelW - 12 - cardX;
        int room = Math.max(80, cardW - 24);
        if (textW > 0 && textW * scale > room) {
            scale = Math.max(1.35F, room / (float) textW);
        }
        int bannerW = Math.min(cardW - 8, Math.round(textW * scale) + 24);
        int bannerH = Math.round(10 * scale) + 14;
        int bannerX = cardX + (cardW - bannerW) / 2;
        int bannerY = jobsPage || managePage || options || infoPage || helpPage || mirrorPage || shapePage || exchangePage || placePage
                ? jobsPanel.jobsInsetY() + 4
                : panelY + 8;
        int fill = flash ? 0xF0481018 : 0xE0321018;
        int edge = flash ? 0xFFFF6666 : 0xFFCC4444;
        graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, fill);
        WrightsUi.border(graphics, bannerX, bannerY, bannerW, bannerH, edge);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(bannerX + bannerW / 2.0F, bannerY + bannerH / 2.0F - 4.0F * scale, 200.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(this.font, label, -textW / 2, 0, flash ? 0xFFFF8888 : 0xFFFF5555, true);
        pose.popPose();
    }

    private void drawTask(GuiGraphics graphics, Component label, boolean active, boolean soon, int contentY) {
        int y = navScreenY(contentY);
        if (y < 0) {
            return;
        }
        int x = panelX + 14;
        WrightsUi.rail(graphics, x, y, 100, 22, active, WrightsUi.COPPER);
        graphics.drawString(this.font, label, x + 8, y + 7, active ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        if (soon) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.soon"), x + 68, y + 7, 0xFF667480, false);
        }
    }

    private void renderInfo(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listX;
        int y = panelY + 40;
        int width = panelX + panelW - 16 - x;
        int gap = 8;
        int cardW = Math.max(80, (width - gap) / 2);
        int cardH = 108;
        drawNetworkCard(graphics, x, y, cardW, cardH);
        int supplyX = x + cardW + gap;
        int supplyW = width - cardW - gap;
        drawSupplyCard(graphics, supplyX, y, supplyW, cardH);
        if (bindInventory != null && bindInventory.visible) {
            // Sit on the supply card so Bind belongs to that section.
            int btnW = Math.min(supplyW - 16, Math.max(72, this.font.width(Component.translatable("menu.wrights.info_bind")) + 16));
            bindInventory.setX(supplyX + 8);
            bindInventory.setY(y + cardH - 24);
            bindInventory.setWidth(btnW);
            bindInventory.setHeight(16);
        }
        int stockY = y + cardH + 8;
        int stockH = panelY + panelH - 54 - stockY;
        if (stockH < 36) {
            return;
        }
        drawStockPanel(graphics, x, stockY, width, stockH, mouseX, mouseY);
    }

    private void drawInfoCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF31404E);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, WrightsUi.COPPER_DIM);
    }

    private void drawStatusPill(GuiGraphics graphics, int right, int y, String text, int fill, int ink) {
        int width = this.font.width(text) + 14;
        int left = right - width;
        graphics.fill(left, y, right, y + 11, fill);
        WrightsUi.border(graphics, left, y, width, 11, ink);
        graphics.fill(left + 4, y + 4, left + 7, y + 7, ink);
        graphics.drawString(this.font, text, left + 10, y + 2, ink, false);
    }

    private void drawNetworkIcon(GuiGraphics graphics, int x, int y) {
        int color = WrightsUi.COPPER;
        graphics.fill(x, y + 4, x + 3, y + 7, color);
        graphics.fill(x + 8, y, x + 11, y + 3, color);
        graphics.fill(x + 8, y + 8, x + 11, y + 11, color);
        graphics.fill(x + 3, y + 5, x + 8, y + 6, color);
        graphics.fill(x + 8, y + 3, x + 9, y + 8, color);
    }

    private void drawChestIcon(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 3, x + 12, y + 11, WrightsUi.COPPER);
        graphics.fill(x + 1, y + 1, x + 11, y + 4, 0xFFC47A3A);
        graphics.fill(x + 5, y + 6, x + 7, y + 8, 0xFF101820);
    }

    private void drawNetworkCard(GuiGraphics graphics, int x, int y, int width, int height) {
        drawInfoCard(graphics, x, y, width, height);
        boolean bound = WrightsClientState.digitalBound;
        boolean online = bound && WrightsClientState.digitalOnline;
        String pill = Component.translatable(online ? "menu.wrights.info_online" : bound ? "menu.wrights.info_offline" : "menu.wrights.info_unbound").getString();
        int pillInk = online ? WrightsUi.GREEN : bound ? WrightsUi.GOLD : WrightsUi.MUTED;
        int pillFill = online ? 0xFF143028 : bound ? 0xFF3A2A14 : 0xFF1A222A;
        drawStatusPill(graphics, x + width - 8, y + 8, pill, pillFill, pillInk);
        drawNetworkIcon(graphics, x + 8, y + 8);
        int titleRoom = width - 28 - (this.font.width(pill) + 22);
        String title = this.font.plainSubstrByWidth(Component.translatable("menu.wrights.info_network").getString(), Math.max(16, titleRoom));
        graphics.drawString(this.font, title, x + 22, y + 9, WrightsUi.TEXT, false);
        if (!bound) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.info_hold").getString(), width - 16), x + 8, y + 26, WrightsUi.MUTED, false);
            drawInfoStep(graphics, 1, "menu.wrights.info_step_rs", x + 8, y + 42, width - 16);
            drawInfoStep(graphics, 2, "menu.wrights.info_step_ae2", x + 8, y + 60, width - 16);
            return;
        }
        boolean ae2 = WrightsClientState.digitalKind == com.wrights.storage.DigitalLink.Kind.AE2.ordinal();
        String kind = Component.translatable(ae2 ? "menu.wrights.digital_ae2" : "menu.wrights.digital_rs").getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(kind, width - 16), x + 8, y + 28, WrightsUi.TEXT, false);
        BlockPos pos = WrightsClientState.digitalPos;
        if (pos != null) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(pos.getX() + ",  " + pos.getY() + ",  " + pos.getZ(), width - 16), x + 8, y + 44, WrightsUi.TEXT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(shortDimension(WrightsClientState.digitalDim), width - 16), x + 8, y + 58, WrightsUi.COPPER, false);
        }
    }

    private void drawSupplyCard(GuiGraphics graphics, int x, int y, int width, int height) {
        drawInfoCard(graphics, x, y, width, height);
        boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
        String pill = Component.translatable(bound ? "menu.wrights.info_linked" : "menu.wrights.info_unbound").getString();
        int pillInk = bound ? WrightsUi.GREEN : WrightsUi.MUTED;
        int pillFill = bound ? 0xFF143028 : 0xFF1A222A;
        drawStatusPill(graphics, x + width - 8, y + 8, pill, pillFill, pillInk);
        drawChestIcon(graphics, x + 8, y + 8);
        int titleRoom = width - 28 - (this.font.width(pill) + 22);
        String title = this.font.plainSubstrByWidth(Component.translatable("menu.wrights.info_supply").getString(), Math.max(16, titleRoom));
        graphics.drawString(this.font, title, x + 24, y + 9, WrightsUi.TEXT, false);
        if (!bound) {
            List<FormattedCharSequence> lines = this.font.split(Component.translatable("menu.wrights.info_chest_how"), width - 16);
            int lineY = y + 30;
            for (int i = 0; i < lines.size() && lineY < y + height - 8; i++) {
                graphics.drawString(this.font, lines.get(i), x + 8, lineY, WrightsUi.MUTED, false);
                lineY += 12;
            }
            return;
        }
        BlockPos chest = WrightsClientState.supplyPos;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(chest.getX() + ",  " + chest.getY() + ",  " + chest.getZ(), width - 16), x + 8, y + 32, WrightsUi.TEXT, false);
        int inside = chestItemCount();
        if (inside < 0) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.info_chest_far").getString(), width - 16), x + 8, y + 50, WrightsUi.GOLD, false);
        } else {
            String count = Component.translatable("menu.wrights.info_chest_count", compactCount(inside)).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(count, width - 16), x + 8, y + 50, inside > 0 ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        }
    }

    private void drawInfoStep(GuiGraphics graphics, int number, String key, int x, int y, int width) {
        graphics.fill(x, y - 1, x + 10, y + 9, 0xFF24384A);
        String label = Integer.toString(number);
        graphics.drawString(this.font, label, x + (10 - this.font.width(label)) / 2, y, WrightsUi.COPPER, false);
        String text = this.font.plainSubstrByWidth(Component.translatable(key).getString(), Math.max(8, width - 14));
        graphics.drawString(this.font, text, x + 14, y, WrightsUi.MUTED, false);
    }

    private void drawStockPanel(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        WrightsUi.inset(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("menu.wrights.info_stock"), x + 8, y + 7, WrightsUi.TEXT, false);
        boolean live = WrightsClientState.digitalBound && WrightsClientState.digitalOnline;
        if (!live || WrightsClientState.digitalLines.isEmpty()) {
            String key = !WrightsClientState.digitalBound ? "menu.wrights.info_unbound_note"
                    : !WrightsClientState.digitalOnline ? "menu.wrights.info_offline_note" : "menu.wrights.digital_items_empty";
            List<FormattedCharSequence> note = this.font.split(Component.translatable(key), width - 24);
            int noteY = y + 28;
            for (FormattedCharSequence line : note) {
                if (noteY > y + height - 12) {
                    break;
                }
                graphics.drawString(this.font, line, x + 12, noteY, WrightsUi.MUTED, false);
                noteY += 12;
            }
            infoMaxScroll = 0;
            return;
        }
        List<BlueprintPayloads.DigitalInfoLine> lines = new ArrayList<>(WrightsClientState.digitalLines);
        lines.sort((left, right) -> {
            int leftRank = left.stock() > 0 ? 0 : left.craftable() ? 1 : 2;
            int rightRank = right.stock() > 0 ? 0 : right.craftable() ? 1 : 2;
            if (leftRank != rightRank) {
                return leftRank - rightRank;
            }
            return Long.compare(right.stock(), left.stock());
        });
        int kinds = 0;
        int craftable = 0;
        long stored = 0L;
        for (BlueprintPayloads.DigitalInfoLine line : lines) {
            if (line.stock() > 0) {
                kinds++;
                stored += line.stock();
            }
            if (line.craftable()) {
                craftable++;
            }
        }
        String summary = Component.translatable("menu.wrights.info_summary", kinds, compactCount(stored), craftable).getString();
        int summaryW = this.font.width(summary);
        if (summaryW < width - 16) {
            graphics.drawString(this.font, summary, x + width - 8 - summaryW, y + 7, WrightsUi.MUTED, false);
        }
        boolean showYou = width >= 250;
        int pillW = 52;
        int netW = 52;
        int youW = showYou ? 40 : 0;
        int right = x + width - 12;
        int pillX = right - pillW;
        int netX = pillX - 8 - netW;
        int youX = showYou ? netX - 6 - youW : netX;
        drawRightText(graphics, Component.translatable("menu.wrights.info_net").getString(), netX + netW, y + 22, WrightsUi.MUTED);
        if (showYou) {
            drawRightText(graphics, Component.translatable("menu.wrights.info_you").getString(), youX + youW, y + 22, WrightsUi.MUTED);
        }
        int rowH = 20;
        int bodyY = y + 34;
        int bodyH = height - 40;
        int visible = Math.max(1, bodyH / rowH);
        infoMaxScroll = Math.max(0, lines.size() - visible);
        infoScroll = Mth.clamp(infoScroll, 0, infoMaxScroll);
        graphics.enableScissor(x + 2, bodyY, x + width - 2, y + height - 4);
        boolean creative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;
        for (int i = infoScroll; i < lines.size() && i - infoScroll < visible; i++) {
            BlueprintPayloads.DigitalInfoLine line = lines.get(i);
            net.minecraft.world.item.Item item = net.minecraft.world.item.Item.byId(line.itemId());
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            int rowY = bodyY + (i - infoScroll) * rowH;
            if ((i - infoScroll) % 2 == 0) {
                graphics.fill(x + 4, rowY, x + width - 6, rowY + rowH - 1, 0xFF0C141C);
            }
            ItemStack stack = item.getDefaultInstance();
            int edge = line.stock() > 0 ? WrightsUi.COPPER : line.craftable() ? WrightsUi.TEAL : 0xFF31404E;
            WrightsUi.slot(graphics, x + 6, rowY, edge);
            graphics.renderItem(stack, x + 7, rowY + 1);
            int nameW = Math.max(16, youX - (x + 28) - 4);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(stack.getHoverName().getString(), nameW), x + 28, rowY + 5, WrightsUi.TEXT, false);
            if (showYou) {
                String you = creative ? "\u221e" : compactCount(carried(item));
                drawRightText(graphics, you, youX + youW, rowY + 5, carried(item) > 0 || creative ? WrightsUi.TEXT : WrightsUi.MUTED);
            }
            int countColor = line.stock() > 0 ? WrightsUi.TEXT : line.craftable() ? WrightsUi.TEAL : WrightsUi.MUTED;
            drawRightText(graphics, compactCount(line.stock()), netX + netW, rowY + 5, countColor);
            String chip = Component.translatable(line.craftable() ? "menu.wrights.info_crafts" : line.stock() > 0 ? "menu.wrights.info_stored" : "menu.wrights.info_empty_chip").getString();
            int chipInk = line.craftable() ? WrightsUi.GREEN : line.stock() > 0 ? WrightsUi.BLUE : WrightsUi.MUTED;
            int chipFill = line.craftable() ? 0xFF143028 : line.stock() > 0 ? 0xFF152433 : 0xFF1A222A;
            drawStatusPill(graphics, pillX + pillW, rowY + 4, chip, chipFill, chipInk);
        }
        graphics.disableScissor();
        if (infoMaxScroll > 0) {
            int trackX = x + width - 6;
            int thumb = Math.max(10, bodyH * visible / lines.size());
            int travel = Math.max(1, bodyH - thumb);
            int thumbY = bodyY + infoScroll * travel / infoMaxScroll;
            graphics.fill(trackX, bodyY, trackX + 2, bodyY + bodyH, 0xFF1A222A);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumb, WrightsUi.COPPER);
        }
    }

    private void drawRightText(GuiGraphics graphics, String text, int right, int y, int color) {
        graphics.drawString(this.font, text, right - this.font.width(text), y, color, false);
    }

    private int carried(net.minecraft.world.item.Item item) {
        if (this.minecraft.player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : this.minecraft.player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = this.minecraft.player.getOffhandItem();
        if (offhand.is(item)) {
            count += offhand.getCount();
        }
        return count;
    }

    private int chestItemCount() {
        if (!WrightsClientState.supplyBound || WrightsClientState.supplyPos == null || this.minecraft.level == null) {
            return -1;
        }
        net.minecraft.world.level.block.entity.BlockEntity block = this.minecraft.level.getBlockEntity(WrightsClientState.supplyPos);
        if (!(block instanceof net.minecraft.world.Container container)) {
            return -1;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }

    private static String shortDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "?";
        }
        int colon = dimension.indexOf(':');
        String name = colon >= 0 ? dimension.substring(colon + 1) : dimension;
        return name.replace('_', ' ');
    }

    private static String compactCount(long count) {
        long value = Math.max(0L, count);
        if (value < 1000L) {
            return Long.toString(value);
        }
        if (value < 10_000L) {
            return (value / 1000L) + "." + ((value % 1000L) / 100L) + "k";
        }
        if (value < 1_000_000L) {
            return (value / 1000L) + "k";
        }
        return (value / 1_000_000L) + "m";
    }

    record HelpSpot(int x, int y) {
    }

    void drawSlider(GuiGraphics graphics, int labelX, int y, int x, int width, int along, int span, Component label) {
        graphics.drawString(this.font, label, labelX, y, WrightsUi.MUTED, false);
        int trackY = y + 4;
        graphics.fill(x, trackY, x + width, trackY + 4, 0xFF101820);
        int filled = (int) Math.round(width * (along / (double) Math.max(1, span)));
        graphics.fill(x, trackY, x + Math.max(0, Math.min(width, filled)), trackY + 4, WrightsUi.COPPER);
        int knob = x + (int) ((width - 6) * (along / (double) Math.max(1, span)));
        graphics.fill(knob, trackY - 3, knob + 6, trackY + 7, WrightsUi.TEXT);
    }

    private boolean buildingPage() {
        return !options && !jobsPage && !infoPage && !managePage && !helpPage;
    }

    private void rememberStrip() {
        if (buildingPage()) {
            savedMirror = mirrorPage;
            savedShape = shapePage;
            savedExchange = exchangePage;
            savedPlace = placePage;
        }
    }

    /** After Edit Save/Cancel, land on Blueprints instead of a sticky Shape/Mirror strip. */
    static void preferBlueprintsStrip() {
        savedShape = false;
        savedMirror = false;
        savedExchange = false;
        savedPlace = false;
        WrightsClientState.clearBuildAims();
    }

    private void honorFeatures() {
        if (!WrightsConfig.allowGlobal() && WrightsClientState.libraryGlobal) {
            WrightsClientState.setLibraryScope(false);
        }
        if (!WrightsConfig.allowMirrorAxes() && (WrightsClientConfig.MIRROR_AXES.get() & 7) != 0) {
            WrightsClientConfig.setMirrorStyleAnchored();
        }
        int pattern = WrightsClientConfig.MIRROR_PATTERN.get();
        int allowed = WrightsConfig.allowedPattern(pattern);
        if (allowed != pattern) {
            WrightsClientConfig.setMirrorPattern(allowed);
        }
        boolean blocks = WrightsConfig.allowMirrorBlocks();
        boolean plans = WrightsConfig.allowMirrorBlueprints();
        if (blocks && !plans && !WrightsClientConfig.mirrorBlocks()) {
            WrightsClientConfig.setMirrorBlocks(true);
        }
        if (plans && !blocks && WrightsClientConfig.mirrorBlocks()) {
            WrightsClientConfig.setMirrorBlocks(false);
        }
        if (!WrightsConfig.allowMirrorReplace() && WrightsClientConfig.mirrorReplace()) {
            WrightsClientConfig.setMirrorReplace(false);
        }
        if (!WrightsConfig.allowMirrorMine() && WrightsClientConfig.mirrorMine()) {
            WrightsClientConfig.setMirrorMine(false);
        }
        if (!WrightsConfig.allowPlaceFluids() && WrightsClientConfig.PLACE_FLUIDS.get()) {
            WrightsClientConfig.setPlaceFluids(false);
        }
        if (!WrightsConfig.allowVoidBlocks() && WrightsClientConfig.VOID_BLOCKS.get()) {
            WrightsClientConfig.setVoidBlocks(false);
        }
        if (!WrightsConfig.allowVoidBlocks() && WrightsClientConfig.mirrorMineVoid()) {
            WrightsClientConfig.setMirrorMineVoid(false);
        }
        if (!WrightsConfig.allowVoidFluids() && WrightsClientConfig.VOID_FLUIDS.get()) {
            WrightsClientConfig.setVoidFluids(false);
        }
        if (!WrightsConfig.allowVoidNbt() && WrightsClientConfig.VOID_NBT.get()) {
            WrightsClientConfig.setVoidNbt(false);
        }
        if (!WrightsConfig.allowCopyFluids() && WrightsClientConfig.COPY_FLUIDS.get()) {
            WrightsClientConfig.setCopyFluids(false);
        }
        if (jobsPage && !WrightsConfig.allowJobs()) {
            jobsPage = false;
        }
        if (managePage && !WrightsConfig.allowManage()) {
            leaveManage();
        }
        if (infoPage && !WrightsConfig.allowInfo()) {
            infoPage = false;
        }
        if (!WrightsConfig.showBuilding() && buildingPage()) {
            if (WrightsConfig.allowJobs()) {
                jobsPage = true;
            } else if (WrightsConfig.allowManage()) {
                managePage = true;
            } else if (WrightsConfig.allowInfo()) {
                infoPage = true;
            } else {
                options = true;
            }
        } else if (buildingPage()) {
            if (!WrightsConfig.allowBlueprints() && !mirrorPage && !shapePage && !exchangePage && !placePage) {
                if (WrightsConfig.allowPlaceTool()) {
                    placePage = true;
                } else if (WrightsConfig.allowMirror()) {
                    mirrorPage = true;
                } else if (WrightsConfig.allowExchange()) {
                    exchangePage = true;
                } else if (WrightsConfig.allowShape()) {
                    shapePage = true;
                }
            }
            if (!WrightsConfig.allowPlaceTool()) {
                placePage = false;
            }
            if (!WrightsConfig.allowMirror()) {
                mirrorPage = false;
            }
            if (!WrightsConfig.allowShape()) {
                shapePage = false;
            }
            if (!WrightsConfig.allowExchange()) {
                exchangePage = false;
            }
        }
    }

    /** Content Y for left-nav rows (0 at top of the strip). Screen Y = {@link #navScreenY(int)}. */
    private int taskBuildingY() {
        return WrightsConfig.showBuilding() ? 0 : -1;
    }

    private int taskBlueprintsY() {
        if (!buildingPage() || !WrightsConfig.allowBlueprints()) {
            return -1;
        }
        return 24;
    }

    private int taskPlaceY() {
        if (!buildingPage() || !WrightsConfig.allowPlaceTool()) {
            return -1;
        }
        return 24 + (WrightsConfig.allowBlueprints() ? 20 : 0);
    }

    private int taskMirrorY() {
        if (!buildingPage() || !WrightsConfig.allowMirror()) {
            return -1;
        }
        int y = 24;
        if (WrightsConfig.allowBlueprints()) {
            y += 20;
        }
        if (WrightsConfig.allowPlaceTool()) {
            y += 20;
        }
        return y;
    }

    private int taskExchangeY() {
        if (!buildingPage() || !WrightsConfig.allowExchange()) {
            return -1;
        }
        int y = 24;
        if (WrightsConfig.allowBlueprints()) {
            y += 20;
        }
        if (WrightsConfig.allowPlaceTool()) {
            y += 20;
        }
        if (WrightsConfig.allowMirror()) {
            y += 20;
        }
        return y;
    }

    private int taskShapeY() {
        if (!buildingPage() || !WrightsConfig.allowShape()) {
            return -1;
        }
        int y = 24;
        if (WrightsConfig.allowBlueprints()) {
            y += 20;
        }
        if (WrightsConfig.allowPlaceTool()) {
            y += 20;
        }
        if (WrightsConfig.allowMirror()) {
            y += 20;
        }
        if (WrightsConfig.allowExchange()) {
            y += 20;
        }
        return y;
    }

    private int taskJobsY() {
        return mainTaskY(0);
    }

    private int taskManageY() {
        return mainTaskY(1);
    }

    private int taskMiningY() {
        return mainTaskY(2);
    }

    private int taskInfoY() {
        return mainTaskY(3);
    }

    private int taskOptionsY() {
        return mainTaskY(4);
    }

    private int taskHelpY() {
        return mainTaskY(5);
    }

    /** Jobs, manage, mining, info, options, help — packed under whatever building left behind. */
    private int mainTaskY(int slot) {
        int y = WrightsConfig.showBuilding() ? (buildingPage() ? subsBottom() : 26) : 0;
        boolean[] show = {
                WrightsConfig.allowJobs(),
                WrightsConfig.allowManage(),
                true,
                WrightsConfig.allowInfo(),
                true,
                true
        };
        for (int i = 0; i < show.length; i++) {
            if (!show[i]) {
                continue;
            }
            if (i == slot) {
                return y;
            }
            y += 26;
        }
        return -1;
    }

    private int subsBottom() {
        int count = (WrightsConfig.allowBlueprints() ? 1 : 0)
                + (WrightsConfig.allowPlaceTool() ? 1 : 0)
                + (WrightsConfig.allowMirror() ? 1 : 0)
                + (WrightsConfig.allowExchange() ? 1 : 0)
                + (WrightsConfig.allowShape() ? 1 : 0);
        if (count == 0) {
            return 26;
        }
        return 24 + 20 * count + 4;
    }

    private int navViewTop() {
        return panelY + 40;
    }

    private int navViewH() {
        return Math.max(48, panelY + panelH - 10 - navViewTop());
    }

    private int navContentH() {
        int last = taskHelpY();
        if (last < 0) {
            last = taskOptionsY();
        }
        if (last < 0) {
            last = taskInfoY();
        }
        if (last < 0) {
            last = taskMiningY();
        }
        return Math.max(22, last + 24);
    }

    private int navScreenY(int contentY) {
        if (contentY < 0) {
            return -1;
        }
        return navViewTop() + contentY - navScroll;
    }

    private void clampNavScroll() {
        navScroll = Mth.clamp(navScroll, 0, Math.max(0, navContentH() - navViewH()));
    }

    private boolean insideNav(double mouseX, double mouseY) {
        return mouseX >= panelX + 8 && mouseX < panelX + 122
                && mouseY >= navViewTop() && mouseY < navViewTop() + navViewH();
    }

    private void drawBuildSubs(GuiGraphics graphics) {
        int last = taskShapeY() >= 0 ? taskShapeY()
                : taskExchangeY() >= 0 ? taskExchangeY()
                : taskMirrorY() >= 0 ? taskMirrorY()
                : taskPlaceY() >= 0 ? taskPlaceY()
                : taskBlueprintsY();
        if (last < 0) {
            return;
        }
        int railTop = navScreenY(taskBuildingY()) + 22;
        int railBot = navScreenY(last) + 18;
        if (railBot > railTop) {
            graphics.fill(panelX + 22, railTop, panelX + 24, railBot, WrightsUi.COPPER_DIM);
        }
        if (taskBlueprintsY() >= 0) {
            drawSubTask(graphics, Component.translatable("menu.wrights.sub_blueprints"), !mirrorPage && !shapePage && !exchangePage && !placePage, taskBlueprintsY());
        }
        if (taskPlaceY() >= 0) {
            drawSubTask(graphics, Component.translatable("menu.wrights.sub_place"), placePage, taskPlaceY());
        }
        if (taskMirrorY() >= 0) {
            drawSubTask(graphics, Component.translatable("menu.wrights.sub_mirror"), mirrorPage, taskMirrorY());
        }
        if (taskExchangeY() >= 0) {
            drawSubTask(graphics, Component.translatable("menu.wrights.sub_exchange"), exchangePage, taskExchangeY());
        }
        if (taskShapeY() >= 0) {
            drawSubTask(graphics, Component.translatable("menu.wrights.sub_shape"), shapePage, taskShapeY());
        }
    }

    private void drawSubTask(GuiGraphics graphics, Component label, boolean active, int contentY) {
        int y = navScreenY(contentY);
        if (y < 0) {
            return;
        }
        int x = panelX + 30;
        WrightsUi.rail(graphics, x, y, 84, 18, active, WrightsUi.TEAL);
        String text = this.font.plainSubstrByWidth(label.getString(), 74);
        graphics.drawString(this.font, text, x + 8, y + 5, active ? WrightsUi.TEXT : WrightsUi.MUTED, false);
    }

    private boolean hitSub(double mouseX, double mouseY, int contentY) {
        if (contentY < 0 || !insideNav(mouseX, mouseY)) {
            return false;
        }
        int y = navScreenY(contentY);
        return y >= 0 && mouseX >= panelX + 30 && mouseX < panelX + 114 && mouseY >= y && mouseY < y + 18;
    }

    int mirrorCardW;
    int mirrorBodyX;

    private void drawScope(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!WrightsConfig.allowGlobal()) {
            drawScopeChip(graphics, mouseX, mouseY, listX, headingY, listW, false, "menu.wrights.scope_world");
            return;
        }
        int half = listW / 2;
        drawScopeChip(graphics, mouseX, mouseY, listX, headingY, half - 2, false, "menu.wrights.scope_world");
        drawScopeChip(graphics, mouseX, mouseY, listX + half, headingY, listW - half, true, "menu.wrights.scope_global");
    }

    private void drawScopeChip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, boolean global, String key) {
        boolean on = WrightsClientState.libraryGlobal == global;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        WrightsUi.choice(graphics, x, y, w, 14, on, hover);
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
            WrightsUi.choice(graphics, x, tabY, tabW - 1, 14, on, hover);
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
                graphics.drawString(this.font, extra, listX + listW - 8 - this.font.width(extra), y + 5,
                        entry.folder() ? WrightsUi.MUTED : WrightsUi.SIZE, false);
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

    /** Short GUI heights put Materials on the Build/Close row. */
    private boolean compactDetailFooter() {
        return this.height < 340 || detailBodyH < 150;
    }

    /** Keeps the preview/info stack and Materials button aligned with detail scroll. */
    private void syncDetailScrollLayout(boolean buildingPage, boolean materialsActive) {
        boolean compact = compactDetailFooter();
        int bar = detailContentH > detailBodyH ? 8 : 0;
        int modelPrefer = Math.min(Math.max(56, detailBodyH / 2), Math.max(56, detailBodyH - (compact ? 90 : 110)));
        modelX = detailX + 4;
        modelW = Math.max(8, detailW - 8 - bar);
        modelH = modelPrefer;
        // Compact footers keep Materials off the scroll stack so it cannot cover Build/Close.
        detailContentH = modelPrefer + 6 + (compact ? 52 + 16 : 72 + 18);
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, detailContentH - detailBodyH));
        modelY = detailY + 4 - detailScroll;
        detailSplit = modelY + modelH + 6;
        int nameY = detailSplit + 52;
        int materialsY = detailSplit + 72;
        boolean nameIn = nameY + 14 > detailY && nameY < detailY + detailBodyH;
        boolean materialsIn = !compact && materialsY + 16 > detailY && materialsY < detailY + detailBodyH;
        if (rename != null) {
            rename.setX(detailX + 4);
            rename.setY(nameY);
            rename.setWidth(Math.max(40, detailW - 86 - bar));
            rename.visible = buildingPage && nameIn;
        }
        if (renameButton != null) {
            renameButton.setX(detailX + Math.max(48, detailW - 78 - bar));
            renameButton.setY(nameY);
            renameButton.visible = buildingPage && nameIn;
        }
        if (materialsButton != null) {
            materialsButton.active = materialsActive;
            if (compact) {
                placeDetailFooter(buildingPage);
            } else {
                materialsButton.setMessage(Component.translatable("menu.wrights.materials"));
                materialsButton.setX(detailX + 4);
                materialsButton.setY(materialsY);
                materialsButton.setWidth(Math.max(40, detailW - 8 - bar));
                materialsButton.visible = buildingPage && materialsIn;
            }
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        BlueprintList.Row picked = selectedRow();
        int bar = detailContentH > detailBodyH ? 8 : 0;
        syncDetailScrollLayout(true, picked != null && !picked.folder() && marked.size() <= 1);
        graphics.enableScissor(detailX, detailY, detailX + detailW, detailY + detailBodyH);
        if (picked != null && picked.folder()) {
            renderFolder(graphics, picked);
            graphics.disableScissor();
            WrightsUi.scrollBar(graphics, detailX + detailW - 4, detailY + 2, detailBodyH - 4, detailScroll, detailContentH);
            return;
        }
        BlueprintPayloads.Summary summary = picked == null ? null : picked.summary();
        if (summary == null) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.detail_empty"), detailX + 4, detailY + 8, WrightsUi.MUTED, false);
            graphics.disableScissor();
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
        graphics.fill(detailX + 4, detailSplit, detailX + detailW - 4 - bar, detailSplit + 1, 0xFF31404E);
        int info = detailSplit + 4;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary.name(), modelW), detailX + 4, info, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.size", summary.sizeX(), summary.sizeY(), summary.sizeZ()), detailX + 4, info + 10, WrightsUi.SIZE, false);
        long box = (long) summary.sizeX() * summary.sizeY() * summary.sizeZ();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.blocks", WrightsUi.count(box), WrightsUi.count(summary.blocks())).getString(), modelW), detailX + 4, info + 20, WrightsUi.MUTED, false);
        if (!summary.source().isEmpty()) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("menu.wrights.source", summary.source()).getString(), modelW), detailX + 4, info + 30, WrightsUi.MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.name"), detailX + 4, detailSplit + 42, WrightsUi.MUTED, false);
        graphics.disableScissor();
        WrightsUi.scrollBar(graphics, detailX + detailW - 4, detailY + 2, detailBodyH - 4, detailScroll, detailContentH);
    }

    private boolean insideDetailBody(double mouseX, double mouseY) {
        return mouseX >= detailX && mouseX < detailX + detailW && mouseY >= detailY && mouseY < detailY + detailBodyH;
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
        if (button == 0 && buildingPage() && hitSub(mouseX, mouseY, taskBlueprintsY())) {
            mirrorPage = false;
            shapePage = false;
            exchangePage = false;
            placePage = false;
            savedMirror = false;
            savedShape = false;
            savedExchange = false;
            savedPlace = false;
            // Drop Shape/Mirror/Exchange/Place aim so reopening (e.g. after Edit → Cancel) stays on Blueprints.
            if (requireTool()) {
                WrightsClientState.selectBlueprintTool();
            } else {
                WrightsClientState.clearBuildAims();
            }
            sortOpen = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && buildingPage() && hitSub(mouseX, mouseY, taskPlaceY())) {
            placePage = true;
            mirrorPage = false;
            shapePage = false;
            exchangePage = false;
            savedPlace = true;
            savedMirror = false;
            savedShape = false;
            savedExchange = false;
            if (requireTool()) {
                WrightsClientState.selectPlaceTool();
            } else {
                WrightsClientState.clearBuildAims();
            }
            sortOpen = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && buildingPage() && hitSub(mouseX, mouseY, taskMirrorY())) {
            mirrorPage = true;
            placePage = false;
            shapePage = false;
            exchangePage = false;
            savedMirror = true;
            savedPlace = false;
            savedShape = false;
            savedExchange = false;
            if (requireTool()) {
                WrightsClientState.selectMirrorTool();
            } else {
                WrightsClientState.clearBuildAims();
            }
            sortOpen = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && buildingPage() && hitSub(mouseX, mouseY, taskShapeY())) {
            shapePage = true;
            placePage = false;
            mirrorPage = false;
            exchangePage = false;
            savedShape = true;
            savedPlace = false;
            savedMirror = false;
            savedExchange = false;
            if (requireTool()) {
                WrightsClientState.selectShapeTool();
            } else {
                WrightsClientState.clearBuildAims();
            }
            sortOpen = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && buildingPage() && hitSub(mouseX, mouseY, taskExchangeY())) {
            exchangePage = true;
            placePage = false;
            shapePage = false;
            mirrorPage = false;
            savedExchange = true;
            savedPlace = false;
            savedShape = false;
            savedMirror = false;
            if (requireTool()) {
                WrightsClientState.selectExchangeTool();
            } else {
                WrightsClientState.clearBuildAims();
            }
            sortOpen = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, taskBuildingY())) {
            boolean stay = buildingPage();
            leaveManage();
            options = false;
            jobsPage = false;
            infoPage = false;
            helpPage = false;
            if (!stay) {
                placePage = savedPlace && WrightsConfig.allowPlaceTool();
                exchangePage = !placePage && savedExchange && WrightsConfig.allowExchange();
                shapePage = !placePage && !exchangePage && savedShape && WrightsConfig.allowShape();
                mirrorPage = !placePage && !exchangePage && !shapePage && savedMirror && WrightsConfig.allowMirror();
            }
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, taskJobsY())) {
            rememberStrip();
            leaveManage();
            options = false;
            jobsPage = true;
            infoPage = false;
            helpPage = false;
            setFocused(null);
            refresh();
            PacketDistributor.sendToServer(new BlueprintPayloads.PlacedPortsAsk());
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, taskManageY())) {
            if (WrightsClientState.dockWaiting()) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(Component.translatable("message.wrights.dock_wait_busy"), true);
                }
                return true;
            }
            rememberStrip();
            options = false;
            jobsPage = false;
            infoPage = false;
            helpPage = false;
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
        if (button == 0 && hitTask(mouseX, mouseY, taskInfoY())) {
            rememberStrip();
            leaveManage();
            options = false;
            jobsPage = false;
            infoPage = true;
            helpPage = false;
            setFocused(null);
            requestDigitalInfo();
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, taskOptionsY())) {
            rememberStrip();
            leaveManage();
            options = true;
            jobsPage = false;
            infoPage = false;
            helpPage = false;
            setFocused(null);
            refresh();
            return true;
        }
        if (button == 0 && hitTask(mouseX, mouseY, taskHelpY())) {
            rememberStrip();
            leaveManage();
            options = false;
            jobsPage = false;
            infoPage = false;
            helpPage = true;
            setFocused(null);
            refresh();
            return true;
        }
        if (helpPage) {
            if (button == 0 && closeButton != null && closeButton.visible && closeButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (helpPanel.mouseClicked(mouseX, mouseY, button, this.font)) {
                return true;
            }
            return true;
        }
        if (managePage) {
            if (button == 0 && portManagePanel.clickClearPortHighlights(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && portManagePanel.clickManageTabs(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && manageTab == 0 && portManagePanel.clickPortList(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && manageTab == 1 && portManagePanel.clickPlacedList(mouseX, mouseY)) {
                return true;
            }
            if (manage.isOpen() && manage.mouseClicked(manageHost, mouseX, mouseY, button)) {
                return true;
            }
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (jobsPage) {
            if (button == 0 && jobsPanel.clickJob(mouseX, mouseY)) {
                return true;
            }
            // Close still works; everything else is consumed so shelf/manage widgets cannot steal hits.
            if (button == 0 && closeButton != null && closeButton.visible && closeButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (options && button == 0) {
            optionsPanel.layoutOptions();
            if (optionsPanel.hitOptionBar(mouseX, mouseY)) {
                optionDragging = true;
                optionDragGrab = (int) mouseY - optionThumbY;
                return true;
            }
            if (optionsPanel.insideOptionView(mouseX, mouseY)) {
                if (optionsPanel.overOptionHelp(mouseX, mouseY)) {
                    return true;
                }
                if (optionsPanel.pickSwatch(mouseX, mouseY, wallSwatchY, 0) || optionsPanel.pickSwatch(mouseX, mouseY, edgeSwatchY, 1) || optionsPanel.pickSwatch(mouseX, mouseY, queueEdgeSwatchY, 2) || optionsPanel.pickSwatch(mouseX, mouseY, placeEdgeSwatchY, 3)) {
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
                        WrightsClientConfig.setDroneMax(true);
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
                        int droneCap = WrightsConfig.maxWrightsPerJob(true);
                        int current = WrightsClientConfig.DRONE_MAX.get()
                                ? WrightsConfig.maxWrightsPerJob()
                                : WrightsClientConfig.droneCount();
                        WrightsClientConfig.setDroneCount(WrightsClientState.stepped(current, step, 1, droneCap));
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
                if (mouseX >= dockWatchX && mouseX < dockWatchX + dockWatchW && mouseY >= dockWatchY && mouseY < dockWatchY + 16) {
                    WrightsClientConfig.setDockWatch(!WrightsClientConfig.DOCK_WATCH.get());
                    return true;
                }
                if (mouseX >= supplyOrderX && mouseX < supplyOrderX + supplyOrderW && mouseY >= supplyOrderY && mouseY < supplyOrderY + 16) {
                    this.minecraft.setScreen(new ShapePriorityScreen(this));
                    return true;
                }
                if (mouseY >= jobDoneMinY && mouseY < jobDoneMinY + 16) {
                    int step = 0;
                    if (mouseX >= jobDoneMinusX && mouseX < jobDoneMinusX + 16) {
                        step = -1;
                    } else if (mouseX >= jobDonePlusX && mouseX < jobDonePlusX + 16) {
                        step = 1;
                    }
                    if (step != 0) {
                        int current = WrightsClientConfig.jobDoneMinSeconds();
                        WrightsClientConfig.setJobDoneMinSeconds(WrightsClientState.stepped(current, step, 0, 120));
                        return true;
                    }
                }
                if (mouseX >= hudPlaceX && mouseX < hudPlaceX + hudPlaceW && mouseY >= hudPlaceY && mouseY < hudPlaceY + 16) {
                    this.minecraft.setScreen(new HudPlaceScreen());
                    return true;
                }
                if (mouseX >= modePlaceX && mouseX < modePlaceX + modePlaceW && mouseY >= modePlaceY && mouseY < modePlaceY + 16) {
                    this.minecraft.setScreen(new ModePlaceScreen());
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
        if (mirrorPage && buildingPage()) {
            if (overWidget(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (button == 0) {
                mirrorPanel.clickMirror(mouseX, mouseY);
            }
            return true;
        }
        if ((shapePage || exchangePage || placePage) && buildingPage()) {
            if (overWidget(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (button == 0 && shapePanel != null && shapePanel.mouseClicked(mouseX, mouseY)) {
                return true;
            }
            return true;
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
        if (helpPage && helpPanel.mouseDragged(mouseY, button)) {
            return true;
        }
        if (optionDragging && button == 0) {
            int max = Math.max(0, optionContentH - optionViewH);
            int travel = Math.max(1, optionViewH - optionThumbH);
            optionScroll = Mth.clamp((int) ((mouseY - optionDragGrab - optionViewY) * max / travel), 0, max);
            return true;
        }
        if (jobBarDrag != 0 && button == 0 && jobsPage) {
            jobsPanel.dragJobBar(mouseY);
            return true;
        }
        if (drag != 0 && button == 0) {
            applyDrag(mouseX);
            return true;
        }
        if ((shapePage || exchangePage || placePage) && shapePanel != null && button == 0 && shapePanel.mouseDragged(mouseX, mouseY, dragX, dragY)) {
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
        if (helpPage && helpPanel.mouseReleased(button)) {
            return true;
        }
        if (button == 0 && (shapePage || exchangePage || placePage) && shapePanel != null && shapePanel.mouseReleased()) {
            return true;
        }
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
            jobBarDrag = 0;
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
    public boolean charTyped(char code, int modifiers) {
        if ((shapePage || exchangePage || placePage) && shapePanel != null && shapePanel.editingValue() && shapePanel.charTyped(code)) {
            return true;
        }
        return super.charTyped(code, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((shapePage || exchangePage || placePage) && shapePanel != null && shapePanel.editingValue() && shapePanel.keyPressed(keyCode)) {
            return true;
        }
        if (managePage && manage.keyPressed(manageHost, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (portRename != null && portRename.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                portManagePanel.finishPortRename(true);
                return true;
            }
            if (keyCode == 256) {
                portManagePanel.finishPortRename(false);
                return true;
            }
            portRename.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (managePage && keyCode == 256) {
            if (portRenamePos != null) {
                portManagePanel.finishPortRename(false);
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

    private boolean hitTask(double mouseX, double mouseY, int contentY) {
        if (contentY < 0 || !insideNav(mouseX, mouseY)) {
            return false;
        }
        int y = navScreenY(contentY);
        return y >= 0 && mouseX >= panelX + 14 && mouseX < panelX + 114 && mouseY >= y && mouseY < y + 22;
    }

    boolean hitSlider(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x - 4 && mouseX < x + width + 4 && mouseY >= y - 4 && mouseY < y + 16;
    }

    void applyDrag(double mouseX) {
        if (drag == 1) {
            WrightsClientConfig.setWallTransparency(along(mouseX, wallSliderX, wallSliderW, 0, 100));
            optionsPanel.showPercent(wallPercent, WrightsClientConfig.WALL_TRANSPARENCY.get());
        } else if (drag == 2) {
            WrightsClientConfig.setEdgeOpacity(along(mouseX, edgeSliderX, edgeSliderW, 0, 100));
            optionsPanel.showPercent(edgePercent, WrightsClientConfig.EDGE_OPACITY.get());
        } else if (drag == 3) {
            WrightsClientConfig.setGhostTransparency(along(mouseX, ghostSliderX, ghostSliderW, 0, 100));
            optionsPanel.showPercent(ghostPercent, WrightsClientConfig.GHOST_TRANSPARENCY.get());
        } else if (drag == 4) {
            int maxCopies = Math.max(1, WrightsConfig.maxMirrorCopies());
            WrightsClientConfig.MIRROR_PATTERN.set(along(mouseX, mirrorBodyX, mirrorCardW, 1, maxCopies));
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
        box.setResponder(value -> optionsPanel.applyOffset(axis, value));
        addRenderableWidget(box);
        return box;
    }

    private EditBox intOptionBox(int min, int max, java.util.function.IntConsumer apply) {
        EditBox box = new EditBox(this.font, 0, 0, 36, 14, Component.empty());
        box.setMaxLength(2);
        box.setFilter(text -> text.matches("\\d{0,2}"));
        box.setResponder(value -> {
            if (suppressOptions || value.isEmpty()) {
                return;
            }
            try {
                apply.accept(Mth.clamp(Integer.parseInt(value), min, max));
            } catch (NumberFormatException ignored) {
                return;
            }
        });
        addRenderableWidget(box);
        return box;
    }

    private static int along(double mouseX, int x, int width, int min, int max) {
        double t = Mth.clamp((mouseX - x) / width, 0.0D, 1.0D);
        return min + (int) Math.round(t * (max - min));
    }

    /** Bottom of the full-width page card, with room for Close inside it. */
    int wideCardBottom() {
        return panelY + panelH - 6;
    }

    private void placeClose(boolean blueprintPage) {
        if (closeButton == null) {
            return;
        }
        closeButton.visible = true;
        if (blueprintPage) {
            placeDetailFooter(true);
            return;
        }
        int right = panelX + panelW - 12;
        closeButton.setX(right - 10 - closeButton.getWidth());
        closeButton.setY(wideCardBottom() - 8 - closeButton.getHeight());
    }

    /** Build/Close always; Materials joins that row when the detail card is short. */
    private void placeDetailFooter(boolean buildingPage) {
        int footY = detailY + detailH - detailFooterH + 2;
        int closeW = closeButton.getWidth();
        int buildW = buildButton == null ? 58 : buildButton.getWidth();
        closeButton.setX(detailX + detailW - closeW);
        closeButton.setY(footY);
        if (buildButton != null) {
            buildButton.setX(detailX + detailW - closeW - 4 - buildW);
            buildButton.setY(footY);
        }
        if (materialsButton == null) {
            return;
        }
        if (!compactDetailFooter()) {
            return;
        }
        materialsButton.setMessage(Component.translatable("menu.wrights.materials_short"));
        int materialsW = Math.min(96, Math.max(56, this.font.width(materialsButton.getMessage()) + 12));
        int buildX = buildButton == null ? detailX + detailW - closeW : buildButton.getX();
        materialsButton.setWidth(Math.min(materialsW, Math.max(40, buildX - detailX - 8)));
        materialsButton.setX(detailX + 4);
        materialsButton.setY(footY);
        materialsButton.visible = buildingPage;
    }

    /**
     * Capture Resume / Discard sit in the detail footer — never on the left task strip,
     * where a short panel (large GUI scale) would overlap Info/Options.
     */
    private void placeModeButtons(boolean building) {
        if (!building) {
            return;
        }
        if ((resumeButton == null || !resumeButton.visible) && (discardButton == null || !discardButton.visible)) {
            return;
        }
        int footY = detailY + detailH - detailFooterH + 2;
        int h = closeButton != null ? closeButton.getHeight() : 16;
        int left = detailX + 4;
        if (materialsButton != null && materialsButton.visible && compactDetailFooter()) {
            left = materialsButton.getX() + materialsButton.getWidth() + 4;
        }
        int rightLimit = buildButton != null && buildButton.visible
                ? buildButton.getX() - 4
                : closeButton != null ? closeButton.getX() - 4 : detailX + detailW;
        if (resumeButton != null && resumeButton.visible) {
            int w = Math.max(52, this.font.width(resumeButton.getMessage()) + 12);
            resumeButton.setWidth(Math.min(w, Math.max(40, rightLimit - left)));
            resumeButton.setHeight(h);
            resumeButton.setX(left);
            resumeButton.setY(footY);
            left += resumeButton.getWidth() + 4;
        }
        if (discardButton != null && discardButton.visible) {
            int w = Math.max(52, this.font.width(discardButton.getMessage()) + 12);
            if (left + w > rightLimit) {
                left = Math.max(detailX + 4, rightLimit - w);
            }
            discardButton.setWidth(Math.min(w, Math.max(40, rightLimit - left)));
            discardButton.setHeight(h);
            discardButton.setX(left);
            discardButton.setY(footY);
        }
    }

    int jobButtonWidth(String key) {
        return Math.max(52, this.font.width(Component.translatable(key)) + 10);
    }

    private int actionLines(int width) {
        return Math.max(buttonLines(workingKeys(), width), buttonLines(finishedKeys(), width));
    }

    int buttonLines(List<String> keys, int width) {
        int limit = Math.max(40, width - 10);
        int lines = 1;
        int cursor = 0;
        for (String key : keys) {
            int button = jobButtonWidth(key);
            if (cursor > 0 && cursor + button > limit) {
                lines++;
                cursor = 0;
            }
            cursor += button + 4;
        }
        return lines;
    }

    List<String> workingKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("menu.wrights.resume");
        keys.add("menu.wrights.stop");
        keys.add("menu.wrights.manage_drones");
        keys.add("menu.wrights.mark_on");
        if (creativeVisit()) {
            keys.add("menu.wrights.visit");
        }
        keys.add("menu.wrights.remove");
        return keys;
    }

    List<String> finishedKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("menu.wrights.redo");
        keys.add("menu.wrights.undo");
        keys.add("menu.wrights.mark_on");
        if (creativeVisit()) {
            keys.add("menu.wrights.visit");
        }
        keys.add("menu.wrights.remove");
        return keys;
    }

    boolean creativeVisit() {
        return this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator();
    }

    int drawJobButton(GuiGraphics graphics, int right, int y, String key, int mouseX, int mouseY, int width) {
        return drawJobButton(graphics, right, y, key, mouseX, mouseY, width, 0, true);
    }

    int drawJobButton(GuiGraphics graphics, int right, int y, String key, int mouseX, int mouseY, int width, int border) {
        return drawJobButton(graphics, right, y, key, mouseX, mouseY, width, border, true);
    }

    int drawJobButton(GuiGraphics graphics, int right, int y, String key, int mouseX, int mouseY, int width, int border, boolean enabled) {
        int left = right - width;
        boolean hover = enabled && mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 16;
        WrightsUi.choice(graphics, left, y, width, 16, false, hover, enabled);
        if (border != 0 && enabled) {
            graphics.fill(left, y, right, y + 2, border);
        }
        Component label = Component.translatable(key);
        String text = this.font.plainSubstrByWidth(label.getString(), width - 4);
        graphics.drawString(this.font, text, left + (width - this.font.width(text)) / 2, y + 4, enabled ? WrightsUi.TEXT : 0xFF6E7A86, false);
        return left - 4;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideNav(mouseX, mouseY) && navContentH() > navViewH()) {
            navScroll = Mth.clamp(navScroll - (int) Math.signum(scrollY) * 22, 0, Math.max(0, navContentH() - navViewH()));
            return true;
        }
        if (helpPage && helpPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (infoPage) {
            infoScroll = Mth.clamp(infoScroll - (int) Math.signum(scrollY), 0, infoMaxScroll);
            savedInfoScroll = infoScroll;
            return true;
        }
        if (managePage) {
            if (manageTab == 1 && mouseY >= placedListTop() && mouseY < placedListTop() + placedShown() * 18) {
                portManagePanel.finishPortRename(true);
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
            jobsPanel.prepareJobs();
            int delta = (int) Math.signum(scrollY);
            boolean inView = mouseY >= jobsViewTop && mouseY < jobsViewTop + jobsViewH;
            if (inView && mouseY >= workListY && mouseY < workGroupY + workGroupH - 2 && workingJobs.size() > workRows) {
                jobScroll = Mth.clamp(jobScroll - delta, 0, Math.max(0, workingJobs.size() - workRows));
                return true;
            }
            if (inView && mouseY >= doneListY && mouseY < doneGroupY + doneGroupH - 2 && finishedJobs.size() > doneRows) {
                historyScroll = Mth.clamp(historyScroll - delta, 0, Math.max(0, finishedJobs.size() - doneRows));
                return true;
            }
            if (inView && jobsContentH > jobsViewH) {
                jobsBodyScroll = Mth.clamp(jobsBodyScroll - delta * 24, 0, Math.max(0, jobsContentH - jobsViewH));
                return true;
            }
            return true;
        }
        if (options && optionsPanel.insideOptionView(mouseX, mouseY)) {
            int max = Math.max(0, optionContentH - optionViewH);
            optionScroll = Mth.clamp(optionScroll - (int) Math.signum(scrollY) * 16, 0, max);
            return true;
        }
        if (mirrorPanel.insideMirrorView(mouseX, mouseY)) {
            int max = Math.max(0, mirrorContentH - mirrorViewH);
            mirrorScroll = Mth.clamp(mirrorScroll - (int) Math.signum(scrollY) * 16, 0, max);
            return true;
        }
        if ((shapePage || exchangePage || placePage) && shapePanel != null && shapePanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (!options && !jobsPage && !infoPage && !managePage && !helpPage && !mirrorPage && !shapePage && !exchangePage && !placePage && insideDetailBody(mouseX, mouseY)) {
            int max = Math.max(0, detailContentH - detailBodyH);
            int next = Mth.clamp(detailScroll - (int) Math.signum(scrollY) * 16, 0, max);
            if (next != detailScroll) {
                detailScroll = next;
                return true;
            }
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
        boolean tool = toolHeld();
        if (tool != lastToolHeld) {
            lastToolHeld = tool;
            refresh();
        }
        if (jobsPage && portStockTick++ % 20 == 0) {
            PacketDistributor.sendToServer(new BlueprintPayloads.PlacedPortsAsk());
        }
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
            importButton.visible = building && WrightsConfig.allowTransfer();
            importButton.active = marked.size() <= 1;
            importButton.setX(listX);
            importButton.setY(bottom);
            importButton.setWidth(importW);
        }
        if (moveButton != null) {
            moveButton.visible = building && WrightsConfig.allowGlobal();
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
        this.minecraft.setScreen(new ImportExportScreen(this, row));
    }

    private void newBlueprint() {
        if (!requireTool()) {
            return;
        }
        if (!WrightsConfig.allowCapture()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.wrights.feature_off"), true);
            }
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
        WrightsClientState.selectBlueprintTool();
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
        boolean global = WrightsConfig.allowGlobal() && mouseX >= listX + listW / 2.0D;
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
        portManagePanel.finishPortRename(true);
        if (!managePage && !holding) {
            return;
        }
        managePage = false;
        portListOpen = false;
        manage.setActive(false);
        sendHold(false);
    }

    int manageX() {
        return listX - 2;
    }

    private int manageY() {
        return panelY + 34;
    }

    int manageW() {
        return panelX + panelW - 12 - manageX();
    }

    private int manageH() {
        return Math.max(80, wideCardBottom() - 8 - 18 - 8 - manageY());
    }

    int portBarY() {
        return manageY() + 6;
    }

    List<WrightportPlaces.Spot> ports() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return List.of();
        }
        return WrightportPlaces.list(this.minecraft.player);
    }

    WrightportPlaces.Spot currentSpot() {
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

    void sendHold(boolean active) {
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

    boolean placedReady() {
        return placedPick != null
                && WrightsClientState.placedKnown
                && !WrightsClientState.placedMissing
                && placedPick.dimension().equals(WrightsClientState.placedDim)
                && placedPick.pos().equals(WrightsClientState.placedPos);
    }

    int personalEnergyY() {
        return ports().size() > 1 ? portBarY() + 38 : portBarY() + 20;
    }

    int placedListTop() {
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

    int placedShown() {
        int count = WrightsClientState.placedPorts.size();
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, placedFit());
    }

    Component portLabel(List<WrightportPlaces.Spot> spots, WrightportPlaces.Spot spot) {
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

    final class MenuManageHost implements ManageHost {
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
            return personalEnergyY() + 16;
        }

        @Override
        public int areaW() {
            return Math.max(160, manageW() - 8);
        }

        @Override
        public int areaH() {
            return Math.max(120, manageY() + manageH() - areaY() - 4);
        }

        @Override
        public boolean toolHeld() {
            return WrightsMenuScreen.this.toolHeld();
        }

        @Override
        public boolean requireTool() {
            return WrightsMenuScreen.this.requireTool();
        }
    }
}
