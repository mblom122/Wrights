package com.wrights.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintTransforms;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintPayloads;

public class PlacementScreen extends Screen {
    private final StructureGrid grid = new StructureGrid();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int modelX;
    private int modelY;
    private int gridY;
    private int modelSize;
    private int layer;
    private int layerY;
    private int selectionY;
    private int rotateY;
    private int flipY;
    private int layersY;
    private int selX = -1;
    private int selY = -1;
    private int selZ = -1;
    private WrightsButton anchorButton;
    private WrightsButton removeButton;
    private WrightsButton changeButton;
    private int controlsX;
    private int promptY;
    private EditBox promptBox;
    private WrightsButton promptOk;
    private WrightsButton promptCancel;
    private boolean promptOpen;
    private boolean movePrompt;
    private boolean awaitTaller;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox sxBox;
    private EditBox syBox;
    private EditBox szBox;
    private WrightsButton lockButton;
    private WrightsButton followButton;
    private WrightsButton inventoryButton;
    private WrightsButton chestButton;
    private WrightsButton clearSupplyButton;
    private WrightsButton highlightSupplyButton;
    private boolean suppress;
    private boolean edited;
    private boolean frameSelection;

    public PlacementScreen() {
        super(Component.translatable("screen.wrights.place"));
    }

    @Override
    protected void init() {
        boolean map = !WrightsClientState.concealed && StructureGrid.oversized(WrightsClientState.fullX, WrightsClientState.fullZ);
        int controlsW = 240;
        int gap = 14;
        int pad = 12;
        int openW = this.font.width(Component.translatable("screen.wrights.open_map")) + 12;
        int leftChrome = 130 + (map ? 18 : 0);
        modelSize = Math.min(140, Math.max(map ? openW : 88, this.height - leftChrome - 36));
        if (pad + modelSize + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 88, this.width - 8 - pad * 2 - gap - controlsW);
        }
        panelW = pad + modelSize + gap + controlsW + pad;
        panelH = 420;
        panelX = Math.max(4, (this.width - panelW) / 2);
        panelY = Math.max(4, (this.height - panelH) / 2);
        modelX = panelX + pad;
        modelY = panelY + 28;
        gridY = modelY;
        controlsX = modelX + modelSize + gap;
        layerY = modelY + modelSize + 16;
        int mapShift = map ? 18 : 0;
        selectionY = layerY + 36 + mapShift;
        layersY = selectionY + 80;
        rotateY = panelY + 154;
        flipY = rotateY + 36;
        layer = WrightsClientState.pivotY;
        if (WrightsClientState.anchor == null) {
            WrightsClientState.anchor = WrightsClientState.lookTarget();
        }
        int left = controlsX + 12;
        int right = controlsX + 126;
        xBox = number(left + 18, panelY + 40);
        yBox = number(left + 18, panelY + 56);
        zBox = number(left + 18, panelY + 72);
        sxBox = number(right + 18, panelY + 40);
        syBox = number(right + 18, panelY + 56);
        szBox = number(right + 18, panelY + 72);
        step(left, panelY + 40, "-", () -> move(-1, 0, 0));
        step(left + 76, panelY + 40, "+", () -> move(1, 0, 0));
        step(left, panelY + 56, "-", () -> move(0, -1, 0));
        step(left + 76, panelY + 56, "+", () -> move(0, 1, 0));
        step(left, panelY + 72, "-", () -> move(0, 0, -1));
        step(left + 76, panelY + 72, "+", () -> move(0, 0, 1));
        step(right, panelY + 40, "-", () -> resize(0, -1));
        step(right + 76, panelY + 40, "+", () -> resize(0, 1));
        step(right, panelY + 56, "-", () -> resize(1, -1));
        step(right + 76, panelY + 56, "+", () -> resize(1, 1));
        step(right, panelY + 72, "-", () -> resize(2, -1));
        step(right + 76, panelY + 72, "+", () -> resize(2, 1));
        int half = Math.max(40, (modelSize - 4) / 2);
        int row = (controlsW - 28) / 2;
        int foot = panelY + panelH;
        lockButton = new WrightsButton(controlsX + 12, panelY + 96, row, 16, Component.translatable("screen.wrights.lock"), button -> setAim(false));
        followButton = new WrightsButton(controlsX + 16 + row, panelY + 96, row, 16, Component.translatable("screen.wrights.follow"), button -> setAim(true));
        addRenderableWidget(lockButton);
        addRenderableWidget(followButton);
        inventoryButton = new WrightsButton(controlsX + 12, foot - 118, row, 16, Component.translatable("screen.wrights.supply_player"), button -> useInventory());
        chestButton = new WrightsButton(controlsX + 16 + row, foot - 118, row, 16, Component.translatable("screen.wrights.supply_bind"), button -> bindChest());
        addRenderableWidget(inventoryButton);
        addRenderableWidget(chestButton);
        int clearW = 52;
        int highlightW = 68;
        highlightSupplyButton = new WrightsButton(controlsX + controlsW - 16 - clearW - highlightW, foot - 96, highlightW, 16, Component.translatable("screen.wrights.supply_highlight"), button -> highlightSupply());
        clearSupplyButton = new WrightsButton(controlsX + controlsW - 12 - clearW, foot - 96, clearW, 16, Component.translatable("screen.wrights.supply_clear"), button -> useInventory());
        addRenderableWidget(highlightSupplyButton);
        addRenderableWidget(clearSupplyButton);
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && WrightsClientConfig.INSTANT_JOBS.get();
        if (!paceInstant) {
            WrightsClientState.clampDeploy();
            step(controlsX + 12, panelY + 118, "-", () -> changeAssign(-1));
            step(controlsX + controlsW - 28, panelY + 118, "+", () -> changeAssign(1));
        }
        addRenderableWidget(new WrightsButton(modelX, layersY, half, 16, Component.translatable("screen.wrights.move_layer"), button -> showPrompt(true)));
        addRenderableWidget(new WrightsButton(modelX + half + 4, layersY, half, 16, Component.translatable("screen.wrights.duplicate_layer"), button -> editLayer(BlueprintPayloads.EditLayer.COPY, layer + 1)));
        addRenderableWidget(new WrightsButton(modelX, layersY + 18, half, 16, Component.translatable("screen.wrights.remove_layer"), button -> editLayer(BlueprintPayloads.EditLayer.REMOVE, layer)));
        addRenderableWidget(new WrightsButton(modelX + half + 4, layersY + 18, half, 16, Component.translatable("screen.wrights.add_layer"), button -> showPrompt(false)));
        promptY = layersY + 40;
        int boxW = Math.min(42, Math.max(28, modelSize / 3));
        int actionW = Math.max(24, (modelSize - boxW - 8) / 2);
        promptBox = new EditBox(this.font, modelX, promptY + 14, boxW, 16, Component.translatable("screen.wrights.layer_at"));
        promptBox.setFilter(text -> text.matches("\\d{0,3}"));
        promptBox.setMaxLength(3);
        addRenderableWidget(promptBox);
        promptOk = new WrightsButton(modelX + boxW + 4, promptY + 14, actionW, 16, Component.translatable("screen.wrights.layer_ok"), button -> confirmPrompt());
        promptCancel = new WrightsButton(modelX + boxW + 8 + actionW, promptY + 14, actionW, 16, Component.translatable("screen.wrights.layer_cancel"), button -> hidePrompt());
        addRenderableWidget(promptOk);
        addRenderableWidget(promptCancel);
        hidePrompt();
        addRenderableWidget(new WrightsButton(controlsX + 12, rotateY, row, 16, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, rotateY, row, 16, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY, row, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY, row, 16, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY + 18, row, 16, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY + 18, row, 16, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 74, controlsW - 24, 16, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(WrightsClientState.buildId, WrightsClientState.viewX, WrightsClientState.viewY, WrightsClientState.viewZ, this)));
        int backW = 68;
        int backX = controlsW - 12 - backW;
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 48, backX - 16, 18, Component.translatable("screen.wrights.save_blueprint"), button -> saveBlueprint()));
        addRenderableWidget(new WrightsButton(controlsX + backX, foot - 48, backW, 18, Component.translatable("screen.wrights.back"), button -> onClose()));
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 26, controlsW - 24, 18, Component.translatable("menu.wrights.build"), button -> send()));
        addRenderableWidget(new WrightsButton(modelX, layerY, 22, 16, Component.literal("-"), button -> changeLayer(-1)));
        addRenderableWidget(new WrightsButton(modelX + modelSize - 22, layerY, 22, 16, Component.literal("+"), button -> changeLayer(1)));
        if (map) {
            addRenderableWidget(new WrightsButton(modelX, layerY + 18, modelSize, 16, Component.translatable("screen.wrights.open_map"), button -> openMap()));
        }
        anchorButton = new WrightsButton(modelX, selectionY, modelSize, 16, Component.translatable("screen.wrights.set_anchor"), button -> setSelectedAnchor());
        removeButton = new WrightsButton(modelX, selectionY + 18, half, 16, Component.translatable("screen.wrights.remove_block"), button -> removeSelected());
        changeButton = new WrightsButton(modelX + half + 4, selectionY + 18, half, 16, Component.translatable("screen.wrights.change_block"), button -> changeSelected());
        addRenderableWidget(anchorButton);
        addRenderableWidget(removeButton);
        addRenderableWidget(changeButton);
        refresh();
    }

    public void refresh() {
        int limit = Math.max(0, WrightsClientState.fullY - 1);
        if (awaitTaller && layer > limit) {
            limit = layer;
        } else {
            awaitTaller = false;
        }
        layer = Mth.clamp(layer, 0, limit);
        if (!inside(selX, selY, selZ)) {
            selX = -1;
            selY = -1;
            selZ = -1;
        }
        if (xBox == null) {
            return;
        }
        suppress = true;
        BlockPos anchor = WrightsClientState.anchor == null ? BlockPos.ZERO : WrightsClientState.anchor;
        set(xBox, anchor.getX());
        set(yBox, anchor.getY());
        set(zBox, anchor.getZ());
        set(sxBox, WrightsClientState.viewX);
        set(syBox, WrightsClientState.viewY);
        set(szBox, WrightsClientState.viewZ);
        if (lockButton != null) {
            lockButton.setPicked(!WrightsClientState.follow);
            followButton.setPicked(WrightsClientState.follow);
        }
        WrightsClientState.clampDeploy();
        if (inventoryButton != null) {
            boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
            inventoryButton.setPicked(!bound && !WrightsClientState.bindSupply);
            chestButton.setPicked(bound || WrightsClientState.bindSupply);
            clearSupplyButton.visible = bound;
            highlightSupplyButton.visible = bound;
        }
        suppress = false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        String title = WrightsClientState.buildName == null || WrightsClientState.buildName.isEmpty()
                ? Component.translatable("screen.wrights.place").getString()
                : WrightsClientState.buildName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(title, panelW - 24), panelX + 14, panelY + 8, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.location"), controlsX + 12, panelY + 28, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.size_label"), controlsX + 126, panelY + 28, WrightsUi.TEXT, false);
        axis(graphics, controlsX + 108, panelY + 40, "X");
        axis(graphics, controlsX + 108, panelY + 56, "Y");
        axis(graphics, controlsX + 108, panelY + 72, "Z");
        axis(graphics, controlsX + 222, panelY + 40, "X");
        axis(graphics, controlsX + 222, panelY + 56, "Y");
        axis(graphics, controlsX + 222, panelY + 72, "Z");
        int foot = panelY + panelH;
        graphics.drawString(this.font, Component.translatable("screen.wrights.use_items_from"), controlsX + 12, foot - 132, WrightsUi.MUTED, false);
        boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
        if (bound) {
            Component supplyLine = Component.translatable("screen.wrights.supply_at", WrightsClientState.supplyPos.getX(), WrightsClientState.supplyPos.getY(), WrightsClientState.supplyPos.getZ());
            int room = Math.max(40, highlightSupplyButton.getX() - (controlsX + 12) - 6);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(supplyLine.getString(), room), controlsX + 12, foot - 92, WrightsUi.TEXT, false);
        } else if (WrightsClientState.bindSupply) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.wrights.supply_aim").getString(), panelX + panelW - 24 - controlsX), controlsX + 12, foot - 92, WrightsUi.MUTED, false);
        }
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && WrightsClientConfig.INSTANT_JOBS.get();
        if (paceInstant) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.drones_instant"), controlsX + 12, panelY + 122, WrightsUi.MUTED, false);
        } else if (this.minecraft.player != null) {
            com.wrights.drone.Swarm swarm = com.wrights.drone.Wrightports.swarm(this.minecraft.player);
            int cap = WrightsClientState.deployCap();
            Component line = swarm.units().isEmpty()
                    ? Component.translatable(com.wrights.drone.Wrightports.worn(this.minecraft.player).isEmpty() ? "screen.wrights.need_wrightport" : "screen.wrights.need_drone")
                    : Component.translatable("screen.wrights.drones_send", Math.min(WrightsClientState.deployCount, Math.max(1, cap)), Math.max(1, cap));
            int color = swarm.units().isEmpty() ? 0xFFE07A7A : WrightsUi.BLUE;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), 168), controlsX + 32, panelY + 122, color, false);
        }
        syncSelection();
        if (WrightsClientState.concealed) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.preview_hidden"), modelX + modelSize / 2, gridY + modelSize / 2, WrightsUi.MUTED);
        } else {
            layoutGrid();
            grid.render(graphics, WrightsClientState.ghosts, WrightsClientState.fullX, WrightsClientState.fullZ, layer, WrightsClientState.pivotX, WrightsClientState.pivotY, WrightsClientState.pivotZ, selX, selY, selZ);
        }
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.layer", layer + 1, WrightsClientState.fullY), modelX + modelSize / 2, layerY - 12, WrightsUi.MUTED);
        graphics.drawString(this.font, Component.translatable("screen.wrights.selection"), modelX, selectionY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.wrights.hint_selected").getString(), modelSize), modelX, selectionY + 40, 0xFFF2D48A, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.wrights.hint_anchor").getString(), modelSize), modelX, selectionY + 52, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.layers"), modelX, layersY - 12, WrightsUi.TEXT, false);
        if (promptOpen) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(movePrompt ? "screen.wrights.move_prompt" : "screen.wrights.add_prompt").getString(), modelSize), modelX, promptY + 2, WrightsUi.MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.rotate"), controlsX + 12, rotateY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.flip"), controlsX + 12, flipY - 12, WrightsUi.TEXT, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (promptOpen && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            confirmPrompt();
            return true;
        }
        if (promptOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            hidePrompt();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!WrightsClientState.follow || xBox == null || xBox.isFocused() || yBox.isFocused() || zBox.isFocused()) {
            return;
        }
        BlockPos look = WrightsClientState.lookTarget();
        if (WrightsClientState.anchor == null || !WrightsClientState.anchor.equals(look)) {
            WrightsClientState.anchor = look;
            refresh();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && WrightsClientState.buildId != null && !WrightsClientState.concealed) {
            layoutGrid();
            int[] hit = grid.hit(mouseX, mouseY, WrightsClientState.fullX, WrightsClientState.fullZ, layer);
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
        if (mouseX >= modelX && mouseX < modelX + modelSize && mouseY >= gridY && mouseY < gridY + modelSize) {
            changeLayer(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Component note() {
        if (WrightsClientState.concealed) {
            return Component.translatable("screen.wrights.preview_hidden");
        }
        if (WrightsClientState.sampled) {
            return Component.translatable("screen.wrights.sampled");
        }
        int inside = 0;
        for (WrightsClientState.Ghost ghost : WrightsClientState.ghosts) {
            if (ghost.x() < WrightsClientState.viewX && ghost.y() < WrightsClientState.viewY && ghost.z() < WrightsClientState.viewZ) {
                inside++;
            }
        }
        boolean larger = WrightsClientState.viewX > WrightsClientState.fullX
                || WrightsClientState.viewY > WrightsClientState.fullY
                || WrightsClientState.viewZ > WrightsClientState.fullZ;
        boolean smaller = WrightsClientState.viewX < WrightsClientState.fullX
                || WrightsClientState.viewY < WrightsClientState.fullY
                || WrightsClientState.viewZ < WrightsClientState.fullZ;
        if (larger) {
            return Component.translatable("screen.wrights.extra_empty");
        }
        if (smaller) {
            return Component.translatable("screen.wrights.in_view", inside, WrightsClientState.blockCount);
        }
        long box = BoxEdits.volume(WrightsClientState.fullX, WrightsClientState.fullY, WrightsClientState.fullZ);
        return Component.translatable("menu.wrights.blocks", box, WrightsClientState.blockCount);
    }

    private void move(int dx, int dy, int dz) {
        BlockPos anchor = WrightsClientState.anchor == null ? BlockPos.ZERO : WrightsClientState.anchor;
        setAnchor(anchor.offset(dx, dy, dz));
    }

    private void resize(int axis, int delta) {
        int x = WrightsClientState.viewX;
        int y = WrightsClientState.viewY;
        int z = WrightsClientState.viewZ;
        if (axis == 0) {
            x += delta;
        } else if (axis == 1) {
            y += delta;
        } else {
            z += delta;
        }
        setSize(x, y, z);
    }

    private void onFields() {
        if (suppress) {
            return;
        }
        Integer x = parse(xBox);
        Integer y = parse(yBox);
        Integer z = parse(zBox);
        Integer sx = parse(sxBox);
        Integer sy = parse(syBox);
        Integer sz = parse(szBox);
        if (x == null || y == null || z == null || sx == null || sy == null || sz == null) {
            return;
        }
        edited = true;
        WrightsClientState.follow = false;
        WrightsClientState.anchor = new BlockPos(x, y, z);
        WrightsClientState.viewX = clamp(sx);
        WrightsClientState.viewY = clamp(sy);
        WrightsClientState.viewZ = clamp(sz);
        WrightsClientState.clampDeploy();
    }

    private void setAnchor(BlockPos anchor) {
        edited = true;
        WrightsClientState.follow = false;
        WrightsClientState.anchor = anchor;
        refresh();
    }

    private void setSize(int x, int y, int z) {
        edited = true;
        WrightsClientState.follow = false;
        WrightsClientState.viewX = clamp(x);
        WrightsClientState.viewY = clamp(y);
        WrightsClientState.viewZ = clamp(z);
        WrightsClientState.clampDeploy();
        refresh();
    }

    private void setAim(boolean follow) {
        WrightsClientState.follow = follow;
        if (follow) {
            WrightsClientState.anchor = WrightsClientState.lookTarget();
            this.minecraft.setScreen(null);
            return;
        }
        edited = true;
        refresh();
    }

    private void useInventory() {
        WrightsClientState.bindSupply = false;
        WrightsClientState.supplyBound = false;
        WrightsClientState.supplyPos = null;
        WrightsClientState.supplyHighlightUntil = 0L;
        PacketDistributor.sendToServer(new BlueprintPayloads.BindSupply(BlockPos.ZERO, true));
        refresh();
    }

    private void highlightSupply() {
        if (WrightsClientState.supplyPos == null) {
            return;
        }
        WrightsClientState.supplyHighlightUntil = System.currentTimeMillis() + 5000L;
        this.minecraft.setScreen(null);
    }

    private void bindChest() {
        WrightsClientState.bindFromMenu = true;
        WrightsClientState.bindSupply = true;
        WrightsClientState.styleHudTicks = 70;
        WrightsClientState.styleAnnounce = true;
        this.minecraft.setScreen(null);
    }

    private void send() {
        if (WrightsClientState.tryBuild()) {
            WrightsClientState.stepAnchor();
            refresh();
        }
    }

    public void selectCell(int x, int y, int z) {
        selX = x;
        selY = y;
        selZ = z;
        layer = Mth.clamp(y, 0, Math.max(0, WrightsClientState.fullY - 1));
        frameSelection = true;
        syncSelection();
    }

    private void openMap() {
        if (this.minecraft == null || WrightsClientState.concealed || WrightsClientState.buildId == null) {
            return;
        }
        this.minecraft.setScreen(new BlueprintMapScreen(
                this,
                this::selectCell,
                WrightsClientState.ghosts,
                WrightsClientState.fullX,
                WrightsClientState.fullY,
                WrightsClientState.fullZ,
                layer,
                WrightsClientState.pivotX,
                WrightsClientState.pivotY,
                WrightsClientState.pivotZ,
                selX,
                selY,
                selZ));
    }

    private void layoutGrid() {
        grid.layout(modelX + 12, gridY + 14, Math.max(32, modelSize - 28), WrightsClientState.fullX, WrightsClientState.fullZ, WrightsClientState.pivotX, WrightsClientState.pivotZ, StructureGrid.INLINE);
        if (selY == layer && selX >= 0 && selZ >= 0) {
            if (frameSelection) {
                grid.centerOn(selX, selZ, WrightsClientState.fullX, WrightsClientState.fullZ);
                frameSelection = false;
            } else {
                grid.ensureVisible(selX, selZ, WrightsClientState.fullX, WrightsClientState.fullZ);
            }
        }
    }

    private void saveBlueprint() {
        PacketDistributor.sendToServer(new BlueprintPayloads.SaveSession());
    }

    private void editLayer(int operation, int target) {
        if (WrightsClientState.buildId == null || target < 0) {
            return;
        }
        int from = layer;
        int height = Math.max(1, WrightsClientState.fullY);
        if (operation == BlueprintPayloads.EditLayer.REMOVE) {
            awaitTaller = false;
            layer = Math.min(from, Math.max(0, height - 2));
        } else {
            awaitTaller = operation != BlueprintPayloads.EditLayer.MOVE;
            layer = target;
        }
        clearSelection();
        int to = operation == BlueprintPayloads.EditLayer.REMOVE ? from : target;
        PacketDistributor.sendToServer(new BlueprintPayloads.EditLayer(WrightsClientState.buildId, operation, from, to));
    }

    private void showPrompt(boolean move) {
        movePrompt = move;
        promptOpen = true;
        promptBox.setValue(Integer.toString(layer + 1));
        promptBox.setVisible(true);
        promptOk.setShown(true);
        promptCancel.setShown(true);
        setFocused(promptBox);
    }

    private void hidePrompt() {
        promptOpen = false;
        if (promptBox == null) {
            return;
        }
        promptBox.setFocused(false);
        promptBox.setVisible(false);
        promptOk.setShown(false);
        promptCancel.setShown(false);
    }

    private void confirmPrompt() {
        if (!promptOpen || promptBox == null) {
            return;
        }
        int height = Math.max(1, WrightsClientState.fullY);
        int max = movePrompt ? height : height + 1;
        int number = -1;
        try {
            if (!promptBox.getValue().isBlank()) {
                number = Integer.parseInt(promptBox.getValue());
            }
        } catch (NumberFormatException ignored) {
            number = -1;
        }
        if (number < 1 || number > max) {
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.wrights.layer_range", max), true);
            }
            return;
        }
        int index = number - 1;
        if (!movePrompt || index != layer) {
            editLayer(movePrompt ? BlueprintPayloads.EditLayer.MOVE : BlueprintPayloads.EditLayer.INSERT, index);
        }
        hidePrompt();
    }

    private void transform(BlueprintTransforms.Op operation) {
        if (WrightsClientState.buildId == null) {
            return;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(WrightsClientState.buildId, operation.ordinal()));
    }

    private void setSelectedAnchor() {
        if (WrightsClientState.buildId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAnchor(WrightsClientState.buildId, selX, selY, selZ));
    }

    private void removeSelected() {
        if (WrightsClientState.buildId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(WrightsClientState.buildId, selX, selY, selZ, 0, true));
    }

    private void changeSelected() {
        if (!inside(selX, selY, selZ)) {
            return;
        }
        BlockPickerScreen.open(this, WrightsClientState.buildId, selX, selY, selZ);
    }

    private void clearSelection() {
        selX = -1;
        selY = -1;
        selZ = -1;
        syncSelection();
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < WrightsClientState.fullX && y < WrightsClientState.fullY && z < WrightsClientState.fullZ;
    }

    private void syncSelection() {
        boolean selected = inside(selX, selY, selZ);
        if (anchorButton != null) {
            anchorButton.active = selected;
            removeButton.active = selected;
            changeButton.active = selected;
        }
    }

    private void changeLayer(int delta) {
        layer = Mth.clamp(layer + delta, 0, Math.max(0, WrightsClientState.fullY - 1));
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(WrightsConfig.MAX_AXIS.getAsInt(), size));
    }

    private void set(EditBox box, int value) {
        if (!box.isFocused()) {
            box.setValue(Integer.toString(value));
        }
    }

    private Integer parse(EditBox box) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private EditBox number(int x, int y) {
        EditBox box = new EditBox(this.font, x, y, 56, 14, Component.empty());
        box.setMaxLength(8);
        box.setFilter(text -> text.matches("-?\\d{0,7}"));
        box.setResponder(value -> onFields());
        addRenderableWidget(box);
        return box;
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, 16, 14, Component.literal(label), button -> action.run()));
    }

    private void changeAssign(int delta) {
        int cap = WrightsClientState.deployCap();
        if (cap <= 0) {
            return;
        }
        WrightsClientState.clampDeploy();
        int next = WrightsClientState.stepped(WrightsClientState.deployCount, delta, 1, cap);
        if (next == WrightsClientState.deployCount) {
            return;
        }
        WrightsClientState.deployChosen = true;
        WrightsClientState.deployCount = next;
    }

    private void axis(GuiGraphics graphics, int x, int y, String label) {
        graphics.drawString(this.font, label, x, y + 3, WrightsUi.MUTED, false);
    }
}
