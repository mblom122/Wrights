package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintTransforms;
import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintPayloads;

public class PlacementScreen extends Screen implements JeiPreview {
    private final LayerColumn column = new LayerColumn(LayerColumn.BUILD);
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rotateY;
    private int flipY;
    private int pasteY;
    private int deployY;
    private WrightsButton placeFluidsButton;
    private WrightsButton voidBlocksButton;
    private WrightsButton voidFluidsButton;
    private WrightsButton voidNbtButton;
    private int controlsX;
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
    private WrightsButton buildButton;
    private boolean suppress;
    private boolean edited;
    private int poolTick;

    public PlacementScreen() {
        super(Component.translatable("screen.wrights.place"));
    }

    @Override
    protected void init() {
        boolean map = !WrightsClientState.concealed && (StructureGrid.oversized(WrightsClientState.fullX, WrightsClientState.fullZ)
                || StructureGrid.oversized(WrightsClientState.fullX, WrightsClientState.fullY)
                || StructureGrid.oversized(WrightsClientState.fullZ, WrightsClientState.fullY));
        int controlsW = 240;
        int gap = 14;
        int pad = 12;
        int openW = this.font.width(Component.translatable("screen.wrights.open_map")) + 12;
        int leftChrome = 148 + (map ? 18 : 0);
        int modelSize = Math.min(140, Math.max(map ? openW : 88, this.height - leftChrome - 36));
        if (pad + modelSize + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 88, this.width - 8 - pad * 2 - gap - controlsW);
        }
        panelW = pad + modelSize + gap + controlsW + pad;
        panelH = 476;
        panelX = JeiPreview.left(this.width, panelW);
        panelY = Math.max(4, (this.height - panelH) / 2);
        int modelX = panelX + pad;
        int modelY = panelY + 28;
        controlsX = modelX + modelSize + gap;
        rotateY = panelY + 128;
        flipY = rotateY + 36;
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
        deployY = foot - 156;
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && WrightsClientConfig.INSTANT_JOBS.get();
        if (!paceInstant) {
            WrightsClientState.clampDeploy();
            step(controlsX + 12, deployY, "-", () -> changeAssign(-1));
            step(controlsX + controlsW - 28, deployY, "+", () -> changeAssign(1));
        }
        addRenderableWidget(new WrightsButton(controlsX + 12, rotateY, row, 16, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, rotateY, row, 16, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY, row, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY, row, 16, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY + 18, row, 16, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY + 18, row, 16, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        pasteY = flipY + 48;
        placeFluidsButton = new WrightsButton(controlsX + 12, pasteY, controlsW - 24, 16, Component.translatable("screen.wrights.place_fluids"), button -> togglePlaceFluids());
        voidBlocksButton = new WrightsButton(controlsX + 12, pasteY + 18, row, 16, Component.translatable("screen.wrights.void_blocks"), button -> toggleVoidBlocks());
        voidFluidsButton = new WrightsButton(controlsX + 16 + row, pasteY + 18, row, 16, Component.translatable("screen.wrights.void_fluids"), button -> toggleVoidFluids());
        voidNbtButton = new WrightsButton(controlsX + 12, pasteY + 36, controlsW - 24, 16, Component.translatable("screen.wrights.void_nbt"), button -> toggleVoidNbt());
        addRenderableWidget(placeFluidsButton);
        addRenderableWidget(voidBlocksButton);
        addRenderableWidget(voidFluidsButton);
        addRenderableWidget(voidNbtButton);
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 74, controlsW - 24, 16, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(WrightsClientState.buildId, WrightsClientState.viewX, WrightsClientState.viewY, WrightsClientState.viewZ, WrightsClientConfig.PLACE_FLUIDS.get(), this)));
        int backW = 68;
        int backX = controlsW - 12 - backW;
        addRenderableWidget(new WrightsButton(controlsX + 12, foot - 48, backX - 16, 18, Component.translatable("screen.wrights.save_blueprint"), button -> saveBlueprint()));
        addRenderableWidget(new WrightsButton(controlsX + backX, foot - 48, backW, 18, Component.translatable("screen.wrights.back"), button -> onClose()));
        buildButton = new WrightsButton(controlsX + 12, foot - 26, controlsW - 24, 18, Component.translatable("menu.wrights.build"), button -> send());
        addRenderableWidget(buildButton);
        column.place(this, this.font, modelX, modelY, modelSize, widget -> addRenderableWidget(widget), this::setFocused);
        refresh();
    }

    public void refresh() {
        column.refresh();
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
        if (placeFluidsButton != null) {
            placeFluidsButton.setPicked(WrightsClientConfig.PLACE_FLUIDS.get());
            voidBlocksButton.setPicked(WrightsClientConfig.VOID_BLOCKS.get());
            voidFluidsButton.setPicked(WrightsClientConfig.VOID_FLUIDS.get());
            boolean voiding = WrightsClientConfig.VOID_BLOCKS.get() || WrightsClientConfig.VOID_FLUIDS.get();
            voidNbtButton.setShown(voiding);
            voidNbtButton.setPicked(voiding && WrightsClientConfig.VOID_NBT.get());
        }
        if (buildButton != null) {
            boolean queue = WrightsClientConfig.QUEUE_JOBS.get() && WrightsClientState.queueInstead();
            buildButton.setMessage(Component.translatable(queue ? "menu.wrights.queue" : "menu.wrights.build"));
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
        graphics.drawString(this.font, Component.translatable("screen.wrights.deploy_on_job"), controlsX + 12, deployY - 14, WrightsUi.TEXT, false);
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
            graphics.drawString(this.font, Component.translatable("screen.wrights.drones_instant"), controlsX + 12, deployY + 4, WrightsUi.MUTED, false);
        } else if (this.minecraft.player != null) {
            Component line;
            int color;
            if (WrightsClientState.poolStored < 0) {
                line = Component.literal("...");
                color = WrightsUi.MUTED;
            } else if (WrightsClientConfig.QUEUE_JOBS.get() && WrightsClientState.queueInstead()) {
                int cap = WrightsClientState.requestCap();
                line = Component.translatable("screen.wrights.drones_queue", Math.min(WrightsClientState.deployCount, cap), cap);
                color = WrightsUi.BLUE;
            } else {
                int cap = WrightsClientState.deployCap();
                line = cap <= 0
                        ? Component.translatable(WrightsClientState.poolStored <= 0 ? "screen.wrights.need_wrightport" : "screen.wrights.need_drone")
                        : Component.translatable("screen.wrights.drones_send", Math.min(WrightsClientState.deployCount, cap), cap);
                color = cap <= 0 ? 0xFFE07A7A : WrightsUi.BLUE;
            }
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), 168), controlsX + 32, deployY + 4, color, false);
        }
        column.render(graphics);
        graphics.drawString(this.font, Component.translatable("screen.wrights.rotate"), controlsX + 12, rotateY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.flip"), controlsX + 12, flipY - 12, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.paste_options"), controlsX + 12, pasteY - 12, WrightsUi.TEXT, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (column.keyPressed(keyCode)) {
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
        if (poolTick++ % 20 == 0) {
            PacketDistributor.sendToServer(new BlueprintPayloads.DeployAsk());
        }
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
        WrightsClientState.styleHudTicks = WrightsClientState.STYLE_HUD_TICKS;
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
        column.selectCell(x, y, z);
    }

    private void saveBlueprint() {
        PacketDistributor.sendToServer(new BlueprintPayloads.SaveSession(
                WrightsClientConfig.PLACE_FLUIDS.get(),
                WrightsClientConfig.VOID_BLOCKS.get(),
                WrightsClientConfig.VOID_FLUIDS.get(),
                WrightsClientConfig.VOID_NBT.get()));
    }

    private void transform(BlueprintTransforms.Op operation) {
        if (WrightsClientState.buildId == null) {
            return;
        }
        column.clearSelection();
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(WrightsClientState.buildId, operation.ordinal()));
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

    private void togglePlaceFluids() {
        WrightsClientConfig.setPlaceFluids(!WrightsClientConfig.PLACE_FLUIDS.get());
        refresh();
    }

    private void toggleVoidBlocks() {
        WrightsClientConfig.setVoidBlocks(!WrightsClientConfig.VOID_BLOCKS.get());
        refresh();
    }

    private void toggleVoidFluids() {
        WrightsClientConfig.setVoidFluids(!WrightsClientConfig.VOID_FLUIDS.get());
        refresh();
    }

    private void toggleVoidNbt() {
        WrightsClientConfig.setVoidNbt(!WrightsClientConfig.VOID_NBT.get());
        refresh();
    }

    private void changeAssign(int delta) {
        int cap = WrightsClientState.requestCap();
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
        return WrightsClientState.buildId;
    }

    @Override
    public void acceptDrop(int x, int y, int z, BlockState state) {
        column.selectCell(x, y, z);
        CellEdits.apply(WrightsClientState.buildId, x, y, z, state);
    }
}
