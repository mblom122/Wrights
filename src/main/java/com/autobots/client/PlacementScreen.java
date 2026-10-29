package com.autobots.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.AutobotsClientConfig;
import com.autobots.AutobotsConfig;
import com.autobots.blueprint.BlueprintTransforms;
import com.autobots.blueprint.BoxEdits;
import com.autobots.network.BlueprintPayloads;

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
    private AutobotsButton anchorButton;
    private AutobotsButton removeButton;
    private AutobotsButton changeButton;
    private int controlsX;
    private int promptY;
    private EditBox promptBox;
    private AutobotsButton promptOk;
    private AutobotsButton promptCancel;
    private boolean promptOpen;
    private boolean movePrompt;
    private boolean awaitTaller;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox sxBox;
    private EditBox syBox;
    private EditBox szBox;
    private AutobotsButton lockButton;
    private AutobotsButton followButton;
    private AutobotsButton inventoryButton;
    private AutobotsButton chestButton;
    private AutobotsButton clearSupplyButton;
    private boolean suppress;
    private boolean edited;
    private boolean frameSelection;

    public PlacementScreen() {
        super(Component.translatable("screen.autobots.place"));
    }

    @Override
    protected void init() {
        boolean map = !AutobotsClientState.concealed && StructureGrid.oversized(AutobotsClientState.fullX, AutobotsClientState.fullZ);
        int controlsW = 240;
        int gap = 14;
        int pad = 12;
        int openW = this.font.width(Component.translatable("screen.autobots.open_map")) + 12;
        int leftChrome = 130 + (map ? 18 : 0);
        modelSize = Math.min(140, Math.max(map ? openW : 88, this.height - leftChrome - 36));
        if (pad + modelSize + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 88, this.width - 8 - pad * 2 - gap - controlsW);
        }
        panelW = pad + modelSize + gap + controlsW + pad;
        panelH = 392;
        panelX = Math.max(4, (this.width - panelW) / 2);
        panelY = Math.max(4, (this.height - panelH) / 2);
        modelX = panelX + pad;
        modelY = panelY + 28;
        gridY = modelY;
        controlsX = modelX + modelSize + gap;
        layerY = modelY + modelSize + 16;
        int mapShift = map ? 18 : 0;
        selectionY = layerY + 36 + mapShift;
        rotateY = panelY + 258;
        flipY = rotateY + 30;
        layersY = panelY + 190;
        layer = AutobotsClientState.pivotY;
        if (AutobotsClientState.anchor == null) {
            AutobotsClientState.anchor = AutobotsClientState.lookTarget();
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
        clearSupplyButton = new AutobotsButton(controlsX + controlsW - 64, panelY + 120, 52, 16, Component.translatable("screen.autobots.supply_clear"), button -> useInventory());
        addRenderableWidget(clearSupplyButton);
        lockButton = new AutobotsButton(controlsX + 12, panelY + 140, row, 16, Component.translatable("screen.autobots.lock"), button -> setAim(false));
        followButton = new AutobotsButton(controlsX + 16 + row, panelY + 140, row, 16, Component.translatable("screen.autobots.follow"), button -> setAim(true));
        addRenderableWidget(lockButton);
        addRenderableWidget(followButton);
        inventoryButton = new AutobotsButton(controlsX + 12, panelY + 100, row, 16, Component.translatable("screen.autobots.supply_inventory"), button -> useInventory());
        chestButton = new AutobotsButton(controlsX + 16 + row, panelY + 100, row, 16, Component.translatable("screen.autobots.supply_chest"), button -> bindChest());
        addRenderableWidget(inventoryButton);
        addRenderableWidget(chestButton);
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && AutobotsClientConfig.INSTANT_JOBS.get();
        if (!paceInstant) {
            step(controlsX + 12, panelY + 160, "-", () -> changeAssign(-1));
            step(controlsX + controlsW - 28, panelY + 160, "+", () -> changeAssign(1));
        }
        addRenderableWidget(new AutobotsButton(controlsX + 12, layersY, row, 16, Component.translatable("screen.autobots.move_layer"), button -> showPrompt(true)));
        addRenderableWidget(new AutobotsButton(controlsX + 16 + row, layersY, row, 16, Component.translatable("screen.autobots.duplicate_layer"), button -> editLayer(BlueprintPayloads.EditLayer.COPY, layer + 1)));
        addRenderableWidget(new AutobotsButton(controlsX + 12, layersY + 18, row, 16, Component.translatable("screen.autobots.remove_layer"), button -> editLayer(BlueprintPayloads.EditLayer.REMOVE, layer)));
        addRenderableWidget(new AutobotsButton(controlsX + 16 + row, layersY + 18, row, 16, Component.translatable("screen.autobots.add_layer"), button -> showPrompt(false)));
        promptY = layersY + 38;
        promptBox = new EditBox(this.font, controlsX + 72, promptY, 36, 16, Component.translatable("screen.autobots.layer_at"));
        promptBox.setFilter(text -> text.matches("\\d{0,3}"));
        promptBox.setMaxLength(3);
        addRenderableWidget(promptBox);
        promptOk = new AutobotsButton(controlsX + 112, promptY, 40, 16, Component.translatable("screen.autobots.layer_ok"), button -> confirmPrompt());
        promptCancel = new AutobotsButton(controlsX + 156, promptY, 52, 16, Component.translatable("screen.autobots.layer_cancel"), button -> hidePrompt());
        addRenderableWidget(promptOk);
        addRenderableWidget(promptCancel);
        hidePrompt();
        addRenderableWidget(new AutobotsButton(controlsX + 12, rotateY, row, 16, Component.translatable("screen.autobots.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new AutobotsButton(controlsX + 16 + row, rotateY, row, 16, Component.translatable("screen.autobots.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new AutobotsButton(controlsX + 12, flipY, row, 16, Component.translatable("screen.autobots.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new AutobotsButton(controlsX + 16 + row, flipY, row, 16, Component.translatable("screen.autobots.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new AutobotsButton(controlsX + 12, flipY + 18, row, 16, Component.translatable("screen.autobots.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new AutobotsButton(controlsX + 16 + row, flipY + 18, row, 16, Component.translatable("screen.autobots.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        addRenderableWidget(new AutobotsButton(controlsX + 12, flipY + 38, controlsW - 24, 16, Component.translatable("menu.autobots.materials"), button -> MaterialsScreen.request(AutobotsClientState.buildId, AutobotsClientState.viewX, AutobotsClientState.viewY, AutobotsClientState.viewZ, this)));
        int backW = 68;
        int backX = controlsW - 12 - backW;
        addRenderableWidget(new AutobotsButton(controlsX + 12, panelY + 348, backX - 16, 18, Component.translatable("menu.autobots.build"), button -> send()));
        addRenderableWidget(new AutobotsButton(controlsX + backX, panelY + 348, backW, 18, Component.translatable("screen.autobots.back"), button -> onClose()));
        addRenderableWidget(new AutobotsButton(controlsX + 12, panelY + 370, controlsW - 24, 18, Component.translatable("screen.autobots.save_blueprint"), button -> saveBlueprint()));
        addRenderableWidget(new AutobotsButton(modelX, layerY, 22, 16, Component.literal("-"), button -> changeLayer(-1)));
        addRenderableWidget(new AutobotsButton(modelX + modelSize - 22, layerY, 22, 16, Component.literal("+"), button -> changeLayer(1)));
        if (map) {
            addRenderableWidget(new AutobotsButton(modelX, layerY + 18, modelSize, 16, Component.translatable("screen.autobots.open_map"), button -> openMap()));
        }
        anchorButton = new AutobotsButton(modelX, selectionY, modelSize, 16, Component.translatable("screen.autobots.set_anchor"), button -> setSelectedAnchor());
        removeButton = new AutobotsButton(modelX, selectionY + 18, half, 16, Component.translatable("screen.autobots.remove_block"), button -> removeSelected());
        changeButton = new AutobotsButton(modelX + half + 4, selectionY + 18, half, 16, Component.translatable("screen.autobots.change_block"), button -> changeSelected());
        addRenderableWidget(anchorButton);
        addRenderableWidget(removeButton);
        addRenderableWidget(changeButton);
        refresh();
    }

    public void refresh() {
        int limit = Math.max(0, AutobotsClientState.fullY - 1);
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
        BlockPos anchor = AutobotsClientState.anchor == null ? BlockPos.ZERO : AutobotsClientState.anchor;
        set(xBox, anchor.getX());
        set(yBox, anchor.getY());
        set(zBox, anchor.getZ());
        set(sxBox, AutobotsClientState.viewX);
        set(syBox, AutobotsClientState.viewY);
        set(szBox, AutobotsClientState.viewZ);
        if (lockButton != null) {
            lockButton.setPicked(!AutobotsClientState.follow);
            followButton.setPicked(AutobotsClientState.follow);
        }
        if (inventoryButton != null) {
            inventoryButton.setPicked(!AutobotsClientState.supplyBound && !AutobotsClientState.bindSupply);
            chestButton.setPicked(AutobotsClientState.supplyBound || AutobotsClientState.bindSupply);
        }
        if (clearSupplyButton != null) {
            clearSupplyButton.active = AutobotsClientState.supplyBound;
        }
        suppress = false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AutobotsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        String title = AutobotsClientState.buildName == null || AutobotsClientState.buildName.isEmpty()
                ? Component.translatable("screen.autobots.place").getString()
                : AutobotsClientState.buildName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(title, panelW - 24), panelX + 14, panelY + 8, AutobotsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.location"), controlsX + 12, panelY + 28, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.size_label"), controlsX + 126, panelY + 28, AutobotsUi.BLUE, false);
        axis(graphics, controlsX + 108, panelY + 40, "X");
        axis(graphics, controlsX + 108, panelY + 56, "Y");
        axis(graphics, controlsX + 108, panelY + 72, "Z");
        axis(graphics, controlsX + 222, panelY + 40, "X");
        axis(graphics, controlsX + 222, panelY + 56, "Y");
        axis(graphics, controlsX + 222, panelY + 72, "Z");
        graphics.drawString(this.font, Component.translatable("screen.autobots.items_from"), controlsX + 12, panelY + 90, AutobotsUi.BLUE, false);
        BlockPos supply = AutobotsClientState.supplyBound ? AutobotsClientState.supplyPos : null;
        Component supplyLine = supply == null
                ? Component.translatable("screen.autobots.supply_none")
                : Component.translatable("screen.autobots.supply_at", supply.getX(), supply.getY(), supply.getZ());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(supplyLine.getString(), 150), controlsX + 12, panelY + 124, supply == null ? AutobotsUi.MUTED : AutobotsUi.BLUE, false);
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && AutobotsClientConfig.INSTANT_JOBS.get();
        if (paceInstant) {
            graphics.drawString(this.font, Component.translatable("screen.autobots.drones_instant"), controlsX + 12, panelY + 164, AutobotsUi.MUTED, false);
        } else if (this.minecraft.player != null) {
            com.autobots.drone.Swarm swarm = com.autobots.drone.Cradles.swarm(this.minecraft.player);
            Component line = swarm.units().isEmpty()
                    ? Component.translatable(com.autobots.drone.Cradles.worn(this.minecraft.player).isEmpty() ? "screen.autobots.need_cradle" : "screen.autobots.need_drone")
                    : Component.translatable("screen.autobots.drones_send", swarm.assignCount(), swarm.units().size());
            int color = swarm.units().isEmpty() ? 0xFFE07A7A : AutobotsUi.BLUE;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), 168), controlsX + 32, panelY + 164, color, false);
        }
        syncSelection();
        if (AutobotsClientState.concealed) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.preview_hidden"), modelX + modelSize / 2, gridY + modelSize / 2, AutobotsUi.MUTED);
        } else {
            layoutGrid();
            grid.render(graphics, AutobotsClientState.ghosts, AutobotsClientState.fullX, AutobotsClientState.fullZ, layer, AutobotsClientState.pivotX, AutobotsClientState.pivotY, AutobotsClientState.pivotZ, selX, selY, selZ);
        }
        graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.layer", layer + 1, AutobotsClientState.fullY), modelX + modelSize / 2, layerY - 12, AutobotsUi.MUTED);
        graphics.drawString(this.font, Component.translatable("screen.autobots.selection"), modelX, selectionY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.autobots.mark_hint").getString(), modelSize), modelX, selectionY + 36, AutobotsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.layers"), controlsX + 12, layersY - 12, AutobotsUi.BLUE, false);
        if (promptOpen) {
            graphics.drawString(this.font, Component.translatable(movePrompt ? "screen.autobots.move_prompt" : "screen.autobots.add_prompt"), controlsX + 12, promptY + 4, AutobotsUi.MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.autobots.rotate"), controlsX + 12, rotateY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.flip"), controlsX + 12, flipY - 12, AutobotsUi.BLUE, false);
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
        if (!AutobotsClientState.follow || xBox == null || xBox.isFocused() || yBox.isFocused() || zBox.isFocused()) {
            return;
        }
        BlockPos look = AutobotsClientState.lookTarget();
        if (AutobotsClientState.anchor == null || !AutobotsClientState.anchor.equals(look)) {
            AutobotsClientState.anchor = look;
            refresh();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && AutobotsClientState.buildId != null && !AutobotsClientState.concealed) {
            layoutGrid();
            int[] hit = grid.hit(mouseX, mouseY, AutobotsClientState.fullX, AutobotsClientState.fullZ, layer);
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
        if (AutobotsClientState.concealed) {
            return Component.translatable("screen.autobots.preview_hidden");
        }
        if (AutobotsClientState.sampled) {
            return Component.translatable("screen.autobots.sampled");
        }
        int inside = 0;
        for (AutobotsClientState.Ghost ghost : AutobotsClientState.ghosts) {
            if (ghost.x() < AutobotsClientState.viewX && ghost.y() < AutobotsClientState.viewY && ghost.z() < AutobotsClientState.viewZ) {
                inside++;
            }
        }
        boolean larger = AutobotsClientState.viewX > AutobotsClientState.fullX
                || AutobotsClientState.viewY > AutobotsClientState.fullY
                || AutobotsClientState.viewZ > AutobotsClientState.fullZ;
        boolean smaller = AutobotsClientState.viewX < AutobotsClientState.fullX
                || AutobotsClientState.viewY < AutobotsClientState.fullY
                || AutobotsClientState.viewZ < AutobotsClientState.fullZ;
        if (larger) {
            return Component.translatable("screen.autobots.extra_empty");
        }
        if (smaller) {
            return Component.translatable("screen.autobots.in_view", inside, AutobotsClientState.blockCount);
        }
        long box = BoxEdits.volume(AutobotsClientState.fullX, AutobotsClientState.fullY, AutobotsClientState.fullZ);
        return Component.translatable("menu.autobots.blocks", box, AutobotsClientState.blockCount);
    }

    private void move(int dx, int dy, int dz) {
        BlockPos anchor = AutobotsClientState.anchor == null ? BlockPos.ZERO : AutobotsClientState.anchor;
        setAnchor(anchor.offset(dx, dy, dz));
    }

    private void resize(int axis, int delta) {
        int x = AutobotsClientState.viewX;
        int y = AutobotsClientState.viewY;
        int z = AutobotsClientState.viewZ;
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
        AutobotsClientState.follow = false;
        AutobotsClientState.anchor = new BlockPos(x, y, z);
        AutobotsClientState.viewX = clamp(sx);
        AutobotsClientState.viewY = clamp(sy);
        AutobotsClientState.viewZ = clamp(sz);
    }

    private void setAnchor(BlockPos anchor) {
        edited = true;
        AutobotsClientState.follow = false;
        AutobotsClientState.anchor = anchor;
        refresh();
    }

    private void setSize(int x, int y, int z) {
        edited = true;
        AutobotsClientState.follow = false;
        AutobotsClientState.viewX = clamp(x);
        AutobotsClientState.viewY = clamp(y);
        AutobotsClientState.viewZ = clamp(z);
        refresh();
    }

    private void setAim(boolean follow) {
        AutobotsClientState.follow = follow;
        if (follow) {
            AutobotsClientState.anchor = AutobotsClientState.lookTarget();
            this.minecraft.setScreen(null);
            return;
        }
        edited = true;
        refresh();
    }

    private void useInventory() {
        AutobotsClientState.bindSupply = false;
        AutobotsClientState.supplyBound = false;
        AutobotsClientState.supplyPos = null;
        PacketDistributor.sendToServer(new BlueprintPayloads.BindSupply(BlockPos.ZERO, true));
        refresh();
    }

    private void bindChest() {
        AutobotsClientState.bindSupply = true;
        AutobotsClientState.styleHudTicks = 70;
        AutobotsClientState.styleAnnounce = true;
        this.minecraft.setScreen(null);
    }

    private void send() {
        if (AutobotsClientState.tryBuild()) {
            AutobotsClientState.stepAnchor();
            refresh();
        }
    }

    public void selectCell(int x, int y, int z) {
        selX = x;
        selY = y;
        selZ = z;
        layer = Mth.clamp(y, 0, Math.max(0, AutobotsClientState.fullY - 1));
        frameSelection = true;
        syncSelection();
    }

    private void openMap() {
        if (this.minecraft == null || AutobotsClientState.concealed || AutobotsClientState.buildId == null) {
            return;
        }
        this.minecraft.setScreen(new BlueprintMapScreen(
                this,
                this::selectCell,
                AutobotsClientState.ghosts,
                AutobotsClientState.fullX,
                AutobotsClientState.fullY,
                AutobotsClientState.fullZ,
                layer,
                AutobotsClientState.pivotX,
                AutobotsClientState.pivotY,
                AutobotsClientState.pivotZ,
                selX,
                selY,
                selZ));
    }

    private void layoutGrid() {
        grid.layout(modelX + 12, gridY + 14, Math.max(32, modelSize - 28), AutobotsClientState.fullX, AutobotsClientState.fullZ, AutobotsClientState.pivotX, AutobotsClientState.pivotZ, StructureGrid.INLINE);
        if (selY == layer && selX >= 0 && selZ >= 0) {
            if (frameSelection) {
                grid.centerOn(selX, selZ, AutobotsClientState.fullX, AutobotsClientState.fullZ);
                frameSelection = false;
            } else {
                grid.ensureVisible(selX, selZ, AutobotsClientState.fullX, AutobotsClientState.fullZ);
            }
        }
    }

    private void saveBlueprint() {
        PacketDistributor.sendToServer(new BlueprintPayloads.SaveSession());
    }

    private void editLayer(int operation, int target) {
        if (AutobotsClientState.buildId == null || target < 0) {
            return;
        }
        int from = layer;
        int height = Math.max(1, AutobotsClientState.fullY);
        if (operation == BlueprintPayloads.EditLayer.REMOVE) {
            awaitTaller = false;
            layer = Math.min(from, Math.max(0, height - 2));
        } else {
            awaitTaller = operation != BlueprintPayloads.EditLayer.MOVE;
            layer = target;
        }
        clearSelection();
        int to = operation == BlueprintPayloads.EditLayer.REMOVE ? from : target;
        PacketDistributor.sendToServer(new BlueprintPayloads.EditLayer(AutobotsClientState.buildId, operation, from, to));
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
        int height = Math.max(1, AutobotsClientState.fullY);
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
                this.minecraft.player.displayClientMessage(Component.translatable("message.autobots.layer_range", max), true);
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
        if (AutobotsClientState.buildId == null) {
            return;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(AutobotsClientState.buildId, operation.ordinal()));
    }

    private void setSelectedAnchor() {
        if (AutobotsClientState.buildId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAnchor(AutobotsClientState.buildId, selX, selY, selZ));
    }

    private void removeSelected() {
        if (AutobotsClientState.buildId == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(AutobotsClientState.buildId, selX, selY, selZ, 0, true));
    }

    private void changeSelected() {
        if (!inside(selX, selY, selZ)) {
            return;
        }
        BlockPickerScreen.open(this, AutobotsClientState.buildId, selX, selY, selZ);
    }

    private void clearSelection() {
        selX = -1;
        selY = -1;
        selZ = -1;
        syncSelection();
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < AutobotsClientState.fullX && y < AutobotsClientState.fullY && z < AutobotsClientState.fullZ;
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
        layer = Mth.clamp(layer + delta, 0, Math.max(0, AutobotsClientState.fullY - 1));
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(AutobotsConfig.MAX_AXIS.getAsInt(), size));
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
        addRenderableWidget(new AutobotsButton(x, y, 16, 14, Component.literal(label), button -> action.run()));
    }

    private void changeAssign(int delta) {
        if (this.minecraft.player == null) {
            return;
        }
        com.autobots.drone.Swarm swarm = com.autobots.drone.Cradles.swarm(this.minecraft.player);
        if (swarm.units().isEmpty()) {
            return;
        }
        int next = net.minecraft.util.Mth.clamp(swarm.assignCount() + delta, 1, swarm.units().size());
        com.autobots.drone.Cradles.replaceWorn(this.minecraft.player, swarm.withAssign(next));
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAssign(next));
    }

    private void axis(GuiGraphics graphics, int x, int y, String label) {
        graphics.drawString(this.font, label, x, y + 3, AutobotsUi.MUTED, false);
    }
}
