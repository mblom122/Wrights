package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.drone.WrightportPlaces;
import com.wrights.network.BlueprintPayloads;

public class WrightsMenuScreen extends WrightsScaledScreen implements JeiPreview {
    static final int ROW = 18;
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
    static int savedTab;

    int panelX;
    int panelY;
    int panelW;
    int panelH;
    int listX;
    int listY;
    int listW;
    int listH;
    private int detailH;
    int headingY;
    private int searchY;
    int detailX;
    private int detailY;
    int detailW;
    private int detailScroll;
    private int detailBodyH;
    private int detailContentH;
    private int detailFooterH;
    int scroll;
    UUID confirmDelete;
    final List<UUID> marked = new ArrayList<>();
    UUID markAnchor;
    List<UUID> confirmSet = List.of();
    EditBox search;
    private EditBox rename;
    EditBox portRename;
    BlockPos portRenamePos;
    private WrightsButton newButton;
    private WrightsButton folderButton;
    WrightsButton sortButton;
    boolean sortOpen;
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
    int detailSplit;
    private UUID modelAsked;
    private UUID spinId;
    boolean spinning;
    private float yaw = 35.0F;
    private float pitch = 28.0F;
    int modelX;
    int modelY;
    int modelW;
    int modelH;
    final List<BlueprintList.Row> rows = new ArrayList<>();
    UUID pendingSelect;
    private boolean suggestRename;
    UUID shelfPress;
    boolean shelfAdditive;
    boolean shelfRange;
    boolean shelfPressFolder;
    UUID shelfPressParent = Util.NIL_UUID;
    int shelfPressIndex;
    double shelfPressX;
    double shelfPressY;
    boolean shelfDragging;
    UUID dropParent = Util.NIL_UUID;
    int dropIndex = -1;
    boolean dropInto;
    UUID dropHighlight;
    int dropLineY = -1;
    boolean options;
    boolean infoPage;
    private boolean helpPage;
    private final HelpPanel helpPanel = new HelpPanel();
    WrightsButton bindInventory;
    private WrightsButton unbindDigital;
    int infoScroll;
    int infoMaxScroll;
    private static int savedInfoScroll;
    boolean jobsPage;
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
    int portKind = -1;
    int portIndex = -1;
    boolean portListOpen;
    int portScroll;
    int manageTab;
    int placedScroll;
    BlueprintPayloads.PlacedPort placedPick;
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
    boolean optionDragging;
    int optionDragGrab;
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

    private final InfoPanel infoPanel = new InfoPanel(this);

    private final ShelfPanel shelfPanel = new ShelfPanel(this);

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
        folderButton = add(new WrightsButton(x, y, folderW, 18, Component.translatable("menu.wrights.new_folder"), button -> shelfPanel.newFolder()));
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
        buildButton = add(new WrightsButton(detailX + detailW - 120, bottom, detailButtonW, 18, Component.translatable("menu.wrights.build"), button -> onBuildClick()));
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
        lastToolHeld = toolHeld();
        refresh();
        if (infoPage) {
            requestDigitalInfo();
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
        // Leave a lane on the right so JEI can stay open beside Manage / hangar.
        panelX = JeiPreview.left(this.width, panelW);
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
        shelfPanel.syncMarks();
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
        boolean toolModePage = !options && !jobsPage && !infoPage && !managePage && !helpPage
                && (mirrorPage || shapePage || exchangePage || placePage);
        boolean building = !options && !jobsPage && !infoPage && !managePage && !helpPage && !toolModePage;
        placeClose(building, toolModePage);
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
        buildButton.visible = building || toolModePage;
        buildButton.active = toolModePage ? tool : blueprint && tool && !many;
        // Mirror arms a tool; "Build" reads like starting a paste job.
        buildButton.setMessage(Component.translatable(mirrorPage ? "menu.wrights.use" : "menu.wrights.build"));
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
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (options) {
            optionsPanel.layoutOptions();
        }
        if (managePage) {
            portManagePanel.reconcilePlaced();
        }
        boolean blueprints = !options && !jobsPage && !infoPage && !managePage && !helpPage && !mirrorPage && !shapePage && !exchangePage && !placePage;
        if (blueprints) {
            BlueprintList.Row row = selectedRow();
            syncDetailScrollLayout(true, row != null && !row.folder() && marked.size() <= 1);
        }
        super.renderScaled(graphics, mouseX, mouseY, partialTick);
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
            infoPanel.renderInfo(graphics, mouseX, mouseY);
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
            shelfPanel.drawScope(graphics, mouseX, mouseY);
            shelfPanel.drawTabs(graphics, mouseX, mouseY);
            shelfPanel.renderList(graphics, mouseX, mouseY);
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
                shelfPanel.renderSortMenu(graphics, mouseX, mouseY);
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
            shelfPanel.renderFolder(graphics, picked);
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
    protected boolean mouseClickedScaled(double mouseX, double mouseY, int button) {
        if (button == 0 && buildingPage()) {
            if (hitSub(mouseX, mouseY, taskBlueprintsY())) {
                return chooseBuildMode(BuildMode.BLUEPRINTS);
            }
            if (hitSub(mouseX, mouseY, taskPlaceY())) {
                return chooseBuildMode(BuildMode.PLACE);
            }
            if (hitSub(mouseX, mouseY, taskMirrorY())) {
                return chooseBuildMode(BuildMode.MIRROR);
            }
            if (hitSub(mouseX, mouseY, taskShapeY())) {
                return chooseBuildMode(BuildMode.SHAPE);
            }
            if (hitSub(mouseX, mouseY, taskExchangeY())) {
                return chooseBuildMode(BuildMode.EXCHANGE);
            }
        }
        if (clickNav(mouseX, mouseY, button)) {
            return true;
        }
        if (helpPage) {
            if (button == 0 && closeButton != null && closeButton.visible && closeButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClickedScaled(mouseX, mouseY, button);
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
            super.mouseClickedScaled(mouseX, mouseY, button);
            return true;
        }
        if (jobsPage) {
            if (button == 0 && jobsPanel.clickJob(mouseX, mouseY)) {
                return true;
            }
            // Close still works; everything else is consumed so shelf/manage widgets cannot steal hits.
            if (button == 0 && closeButton != null && closeButton.visible && closeButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClickedScaled(mouseX, mouseY, button);
            }
            return true;
        }
        if (options && button == 0) {
            if (optionsPanel.click(mouseX, mouseY)) {
                return true;
            }
            return super.mouseClickedScaled(mouseX, mouseY, button);
        }
        if (mirrorPage && buildingPage()) {
            if (overWidget(mouseX, mouseY)) {
                return super.mouseClickedScaled(mouseX, mouseY, button);
            }
            if (button == 0) {
                mirrorPanel.clickMirror(mouseX, mouseY);
            }
            return true;
        }
        if ((shapePage || exchangePage || placePage) && buildingPage()) {
            if (overWidget(mouseX, mouseY)) {
                return super.mouseClickedScaled(mouseX, mouseY, button);
            }
            if (button == 0 && shapePanel != null && shapePanel.mouseClicked(mouseX, mouseY)) {
                return true;
            }
            return true;
        }
        if (shelfPanel.clickShelf(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    private enum BuildMode {
        BLUEPRINTS, PLACE, MIRROR, SHAPE, EXCHANGE
    }

    /**
     * Switches the Building page only. Does not arm the controller — browsing Shape / Place /
     * Mirror / Exchange used to change the held mode. {@link #onBuildClick()} arms that mode.
     */
    private boolean chooseBuildMode(BuildMode mode) {
        placePage = mode == BuildMode.PLACE;
        mirrorPage = mode == BuildMode.MIRROR;
        shapePage = mode == BuildMode.SHAPE;
        exchangePage = mode == BuildMode.EXCHANGE;
        savedPlace = placePage;
        savedMirror = mirrorPage;
        savedShape = shapePage;
        savedExchange = exchangePage;
        sortOpen = false;
        setFocused(null);
        refresh();
        return true;
    }

    /** Left-nav task buttons (Building, Jobs, Manage, Info, Options, Help). Returns true when one was hit. */
    private boolean clickNav(double mouseX, double mouseY, int button) {
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
        return false;
    }

    @Override
    protected boolean mouseDraggedScaled(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
                shelfPanel.updateDrop(mouseX, mouseY);
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
        return super.mouseDraggedScaled(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean mouseReleasedScaled(double mouseX, double mouseY, int button) {
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
            List<UUID> moving = shelfPanel.dragIds();
            shelfPress = null;
            shelfDragging = false;
            dropIndex = -1;
            dropInto = false;
            dropHighlight = null;
            dropLineY = -1;
            if (dragging && index >= 0) {
                if (savedTab == BlueprintList.Tab.ALL.ordinal() && into && moving.size() > 1) {
                    shelfPanel.dropMarkedInto(parent, moving);
                } else if (savedTab == BlueprintList.Tab.ALL.ordinal()) {
                    PacketDistributor.sendToServer(new BlueprintPayloads.ShelfEdit(BlueprintPayloads.ShelfEdit.MOVE, pressed, folder, parent, index, ""));
                } else {
                    shelfPanel.rememberManual(pressed, index);
                }
            } else if (!dragging) {
                shelfPanel.selectRow(pressed);
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
        return super.mouseReleasedScaled(mouseX, mouseY, button);
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

    boolean insideModel(double mouseX, double mouseY) {
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

    private void placeClose(boolean blueprintPage, boolean toolModePage) {
        if (closeButton == null) {
            return;
        }
        closeButton.visible = true;
        if (blueprintPage) {
            placeDetailFooter(true);
            return;
        }
        int right = panelX + panelW - 12;
        int footY = wideCardBottom() - 8 - closeButton.getHeight();
        closeButton.setX(right - 10 - closeButton.getWidth());
        closeButton.setY(footY);
        if (toolModePage && buildButton != null) {
            buildButton.setX(closeButton.getX() - 4 - buildButton.getWidth());
            buildButton.setY(footY);
        }
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
    protected boolean mouseScrolledScaled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
            portManagePanel.mouseScrolled(mouseX, mouseY, scrollY);
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
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
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
        if (managePage && manageTab == 1 && placedPick != null && portStockTick++ % 20 == 0) {
            PacketDistributor.sendToServer(new BlueprintPayloads.PlacedSwarmAsk(placedPick.dimension(), placedPick.pos()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    /** Blueprints Build, or arm Place / Mirror / Shape / Exchange from that page. */
    private void onBuildClick() {
        if (shapePage || placePage || mirrorPage || exchangePage) {
            armToolMode();
            return;
        }
        build();
    }

    /** Puts the controller on this page's mode with its current settings, then closes the menu. */
    private void armToolMode() {
        if (!requireTool()) {
            return;
        }
        if (shapePage) {
            WrightsClientState.selectShapeTool();
        } else if (placePage) {
            WrightsClientState.selectPlaceTool();
        } else if (mirrorPage) {
            WrightsClientState.selectMirrorTool();
        } else if (exchangePage) {
            WrightsClientState.selectExchangeTool();
        } else {
            return;
        }
        WrightsClientState.styleHudTicks = WrightsClientState.STYLE_HUD_TICKS;
        WrightsClientState.styleAnnounce = true;
        this.minecraft.setScreen(null);
    }

    void build() {
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
        List<BlueprintList.Row> targets = shelfPanel.markedRows();
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

    BlueprintList.Row selectedRow() {
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

    private void moveSelected() {
        boolean toGlobal = !WrightsClientState.libraryGlobal;
        for (BlueprintList.Row row : shelfPanel.markedRows()) {
            if (!row.folder() && row.summary() != null) {
                PacketDistributor.sendToServer(new BlueprintPayloads.LibraryMove(row.id(), toGlobal));
            }
        }
    }

    private boolean canArrange() {
        return WrightsClientConfig.sortKey(savedTab) == BlueprintList.SortKey.MANUAL.ordinal()
                && (search == null || search.getValue().trim().isEmpty());
    }

    boolean confirming() {
        return !confirmSet.isEmpty() && confirmSet.size() == marked.size() && confirmSet.containsAll(marked);
    }

    private boolean isBlueprint(UUID id) {
        return WrightsClientState.library.stream().anyMatch(entry -> entry.id().equals(id));
    }

    int visibleRows() {
        return Math.max(1, listH / ROW);
    }

    boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;
    }

    /** Shelf buttons sit on the bottom edge of the list, so a click there belongs to the button. */
    boolean overWidget(double mouseX, double mouseY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof WrightsButton button && button.visible && button.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void leaveManage() {
        portManagePanel.finishPortRename(true);
        if (!managePage) {
            return;
        }
        managePage = false;
        portListOpen = false;
    }

    int manageX() {
        return listX - 2;
    }

    int manageY() {
        return panelY + 34;
    }

    int manageW() {
        return panelX + panelW - 12 - manageX();
    }

    int manageH() {
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
        portKind = first.kind();
        portIndex = first.index();
        return first;
    }

    boolean placedReady() {
        return placedPick != null
                && WrightsClientState.placedKnown
                && !WrightsClientState.placedMissing
                && placedPick.dimension().equals(WrightsClientState.placedDim)
                && placedPick.pos().equals(WrightsClientState.placedPos);
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
}
