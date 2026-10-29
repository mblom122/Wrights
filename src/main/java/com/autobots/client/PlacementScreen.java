package com.autobots.client;

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
    private int centerY;
    private int layersY;
    private int selX = -1;
    private int selY = -1;
    private int selZ = -1;
    private AutobotsButton anchorButton;
    private AutobotsButton removeButton;
    private AutobotsButton changeButton;
    private EditBox layerTo;
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
    private boolean suppress;
    private boolean edited;

    public PlacementScreen() {
        super(Component.translatable("screen.autobots.place"));
    }

    @Override
    protected void init() {
        boolean map = !AutobotsClientState.concealed && StructureGrid.oversized(AutobotsClientState.fullX, AutobotsClientState.fullZ);
        panelW = 214;
        panelH = 318;
        int extra = map ? 314 : 296;
        int openW = this.font.width(Component.translatable("screen.autobots.open_map")) + 12;
        modelSize = Math.min(120, Math.max(map ? openW : 64, this.height - extra - 12));
        int gap = 12;
        if (modelSize + gap + panelW > this.width - 16) {
            modelSize = Math.max(map ? openW : 88, this.width - 16 - gap - panelW);
        }
        int columnH = extra + modelSize;
        int blockH = Math.max(panelH, columnH);
        int groupX = Math.max(8, (this.width - modelSize - gap - panelW) / 2);
        panelX = groupX + modelSize + gap;
        panelY = Math.max(6, (this.height - blockH) / 2);
        modelX = groupX;
        modelY = panelY;
        gridY = modelY;
        layerY = gridY + modelSize + 4;
        int mapShift = map ? 18 : 0;
        selectionY = layerY + 34 + mapShift;
        rotateY = selectionY + 64;
        flipY = rotateY + 34;
        centerY = flipY + 20;
        layersY = centerY + 32;
        layer = AutobotsClientState.pivotY;
        if (AutobotsClientState.anchor == null) {
            AutobotsClientState.anchor = AutobotsClientState.lookTarget();
        }
        xBox = number(panelX + 32, panelY + 48);
        yBox = number(panelX + 32, panelY + 66);
        zBox = number(panelX + 32, panelY + 84);
        sxBox = number(panelX + 32, panelY + 120);
        syBox = number(panelX + 32, panelY + 138);
        szBox = number(panelX + 32, panelY + 156);
        step(panelX + 12, panelY + 48, "-", () -> move(-1, 0, 0));
        step(panelX + 114, panelY + 48, "+", () -> move(1, 0, 0));
        step(panelX + 12, panelY + 66, "-", () -> move(0, -1, 0));
        step(panelX + 114, panelY + 66, "+", () -> move(0, 1, 0));
        step(panelX + 12, panelY + 84, "-", () -> move(0, 0, -1));
        step(panelX + 114, panelY + 84, "+", () -> move(0, 0, 1));
        step(panelX + 12, panelY + 120, "-", () -> resize(0, -1));
        step(panelX + 114, panelY + 120, "+", () -> resize(0, 1));
        step(panelX + 12, panelY + 138, "-", () -> resize(1, -1));
        step(panelX + 114, panelY + 138, "+", () -> resize(1, 1));
        step(panelX + 12, panelY + 156, "-", () -> resize(2, -1));
        step(panelX + 114, panelY + 156, "+", () -> resize(2, 1));
        int half = Math.max(40, (modelSize - 4) / 2);
        int aimW = (panelW - 28) / 2;
        lockButton = new AutobotsButton(panelX + 12, panelY + 208, aimW, 16, Component.translatable("screen.autobots.lock"), button -> setAim(false));
        followButton = new AutobotsButton(panelX + 16 + aimW, panelY + 208, aimW, 16, Component.translatable("screen.autobots.follow"), button -> setAim(true));
        addRenderableWidget(lockButton);
        addRenderableWidget(followButton);
        inventoryButton = new AutobotsButton(panelX + 12, panelY + 188, aimW, 16, Component.translatable("screen.autobots.supply_inventory"), button -> useInventory());
        chestButton = new AutobotsButton(panelX + 16 + aimW, panelY + 188, aimW, 16, Component.translatable("screen.autobots.supply_chest"), button -> bindChest());
        addRenderableWidget(inventoryButton);
        addRenderableWidget(chestButton);
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && AutobotsClientConfig.INSTANT_JOBS.get();
        if (!paceInstant) {
            step(panelX + 12, panelY + 230, "-", () -> changeAssign(-1));
            step(panelX + 186, panelY + 230, "+", () -> changeAssign(1));
        }
        addRenderableWidget(new AutobotsButton(panelX + 12, panelY + 252, 118, 18, Component.translatable("menu.autobots.build"), button -> send()));
        addRenderableWidget(new AutobotsButton(panelX + 134, panelY + 252, 68, 18, Component.translatable("screen.autobots.back"), button -> onClose()));
        addRenderableWidget(new AutobotsButton(panelX + 12, panelY + 274, panelW - 24, 18, Component.translatable("screen.autobots.save_blueprint"), button -> saveBlueprint()));
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
        addRenderableWidget(new AutobotsButton(modelX, rotateY, half, 16, Component.translatable("screen.autobots.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, rotateY, half, 16, Component.translatable("screen.autobots.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new AutobotsButton(modelX, flipY, half, 16, Component.translatable("screen.autobots.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, flipY, half, 16, Component.translatable("screen.autobots.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new AutobotsButton(modelX, centerY, half, 16, Component.translatable("screen.autobots.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, centerY, half, 16, Component.translatable("screen.autobots.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        addRenderableWidget(new AutobotsButton(modelX, layersY, half, 16, Component.translatable("screen.autobots.remove_layer"), button -> editLayer(BlueprintPayloads.EditLayer.REMOVE, layer)));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, layersY, half, 16, Component.translatable("screen.autobots.add_layer"), button -> editLayer(BlueprintPayloads.EditLayer.INSERT, layer)));
        layerTo = new EditBox(this.font, modelX + modelSize - 42, layersY + 18, 42, 16, Component.translatable("screen.autobots.layer_at"));
        layerTo.setFilter(text -> text.matches("\\d{0,3}"));
        layerTo.setMaxLength(3);
        addRenderableWidget(layerTo);
        addRenderableWidget(new AutobotsButton(modelX, layersY + 36, half, 16, Component.translatable("screen.autobots.move_layer"), button -> editLayer(BlueprintPayloads.EditLayer.MOVE, targetLayer())));
        addRenderableWidget(new AutobotsButton(modelX + half + 4, layersY + 36, half, 16, Component.translatable("screen.autobots.copy_layer"), button -> editLayer(BlueprintPayloads.EditLayer.COPY, targetLayer())));
        addRenderableWidget(new AutobotsButton(modelX, layersY + 56, modelSize, 16, Component.translatable("menu.autobots.materials"), button -> MaterialsScreen.request(AutobotsClientState.buildId, AutobotsClientState.viewX, AutobotsClientState.viewY, AutobotsClientState.viewZ, this)));
        refresh();
    }

    public void refresh() {
        layer = Mth.clamp(layer, 0, Math.max(0, AutobotsClientState.fullY - 1));
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
        graphics.drawString(this.font, Component.translatable("screen.autobots.location"), panelX + 12, panelY + 36, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.size_label"), panelX + 12, panelY + 108, AutobotsUi.BLUE, false);
        axis(graphics, panelY + 50, "X");
        axis(graphics, panelY + 68, "Y");
        axis(graphics, panelY + 86, "Z");
        axis(graphics, panelY + 122, "X");
        axis(graphics, panelY + 140, "Y");
        axis(graphics, panelY + 158, "Z");
        graphics.drawString(this.font, Component.translatable("screen.autobots.items_from"), panelX + 12, panelY + 176, AutobotsUi.BLUE, false);
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && AutobotsClientConfig.INSTANT_JOBS.get();
        if (paceInstant) {
            graphics.drawString(this.font, Component.translatable("screen.autobots.drones_instant"), panelX + 12, panelY + 234, AutobotsUi.MUTED, false);
        } else if (this.minecraft.player != null) {
            com.autobots.drone.Swarm swarm = com.autobots.drone.Cradles.swarm(this.minecraft.player);
            Component line = swarm.units().isEmpty()
                    ? Component.translatable(com.autobots.drone.Cradles.worn(this.minecraft.player).isEmpty() ? "screen.autobots.need_cradle" : "screen.autobots.need_drone")
                    : Component.translatable("screen.autobots.drones_send", swarm.assignCount(), swarm.units().size());
            int color = swarm.units().isEmpty() ? 0xFFE07A7A : AutobotsUi.BLUE;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), 150), panelX + 32, panelY + 234, color, false);
        }
        syncSelection();
        if (AutobotsClientState.concealed) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.preview_hidden"), modelX + modelSize / 2, gridY + modelSize / 2, AutobotsUi.MUTED);
        } else {
            layoutGrid();
            grid.render(graphics, AutobotsClientState.ghosts, AutobotsClientState.fullX, AutobotsClientState.fullZ, layer, AutobotsClientState.pivotX, AutobotsClientState.pivotY, AutobotsClientState.pivotZ, selX, selY, selZ);
        }
        graphics.drawCenteredString(this.font, Component.translatable("screen.autobots.layer", layer + 1, AutobotsClientState.fullY), modelX + modelSize / 2, layerY + 4, AutobotsUi.MUTED);
        graphics.drawString(this.font, Component.translatable("screen.autobots.selection"), modelX, selectionY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.autobots.mark_hint").getString(), modelSize), modelX, selectionY + 36, AutobotsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.rotate"), modelX, rotateY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.flip"), modelX, flipY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.layers"), modelX, layersY - 12, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.autobots.layer_at").getString(), Math.max(8, modelSize - 46)), modelX, layersY + 22, AutobotsUi.MUTED, false);
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
            grid.ensureVisible(selX, selZ, AutobotsClientState.fullX, AutobotsClientState.fullZ);
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
        if (operation == BlueprintPayloads.EditLayer.INSERT) {
            layer = Math.min(layer, AutobotsClientState.fullY);
        } else if (operation == BlueprintPayloads.EditLayer.MOVE || operation == BlueprintPayloads.EditLayer.COPY) {
            layer = target;
        }
        clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.EditLayer(AutobotsClientState.buildId, operation, from, operation == BlueprintPayloads.EditLayer.INSERT ? from : target));
    }

    private int targetLayer() {
        if (layerTo == null || layerTo.getValue().isBlank()) {
            return -1;
        }
        try {
            int number = Integer.parseInt(layerTo.getValue());
            return Math.max(0, number - 1);
        } catch (NumberFormatException exception) {
            return -1;
        }
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
        EditBox box = new EditBox(this.font, x, y, 78, 14, Component.empty());
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

    private void axis(GuiGraphics graphics, int y, String label) {
        graphics.drawString(this.font, label, panelX + 134, y, AutobotsUi.MUTED, false);
    }
}
