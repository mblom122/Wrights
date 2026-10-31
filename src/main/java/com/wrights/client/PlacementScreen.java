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
    private WrightsButton placeNbtButton;
    private WrightsButton placeSettingsButton;
    private WrightsButton placeAllButton;
    private WrightsButton voidBlocksButton;
    private WrightsButton voidFluidsButton;
    private WrightsButton voidNbtButton;
    private int controlsX;
    private int controlsW;
    private int modelX;
    private int locationY;
    private int sizeLeft;
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
        controlsW = 240;
        int gap = 14;
        int pad = 12;
        int openW = this.font.width(Component.translatable("screen.wrights.open_map")) + 12;
        // Location + size sit side by side under selection, with room for the move prompt (~94px).
        int leftChrome = 148 + (map ? 18 : 0) + 94;
        int modelSize = Math.min(140, Math.max(map ? openW : 88, this.height - leftChrome - 36));
        if (pad + modelSize + gap + controlsW + pad > this.width - 8) {
            modelSize = Math.max(map ? openW : 88, this.width - 8 - pad * 2 - gap - controlsW);
        }
        panelW = pad + modelSize + gap + controlsW + pad;
        boolean voiding = WrightsClientConfig.VOID_BLOCKS.get() || WrightsClientConfig.VOID_FLUIDS.get();
        int extraRows = (WrightsClientState.showStoredSettings() ? 1 : 0) + (WrightsClientState.showStoredAll() ? 1 : 0) + (voiding ? 1 : 0);
        int pasteRows = 3 + extraRows;
        int leftNeed = 28 + modelSize + 16 + 18 + 32 + (map ? 18 : 0) + 52 + 40 + 94 + 8;
        int rightNeed = 28 + 46 + 36 + 48 + pasteRows * 18 + 14 + 18 + 14 + 18 + 20 + 16 + 18 + 18 + 12;
        panelH = Math.min(this.height - 8, Math.max(leftNeed, rightNeed));
        panelX = JeiPreview.left(this.width, panelW);
        panelY = Math.max(4, (this.height - panelH) / 2);
        modelX = panelX + pad;
        int modelY = panelY + 28;
        controlsX = modelX + modelSize + gap;
        column.place(this, this.font, modelX, modelY, modelSize, widget -> addRenderableWidget(widget), this::setFocused);
        if (WrightsClientState.anchor == null) {
            WrightsClientState.anchor = WrightsClientState.lookTarget();
        }
        // Leave a strip under Selection for the Move-layer prompt, then Location / Size.
        column.shiftPromptTo(column.belowSelection());
        locationY = column.belowSelection() + 34;
        // Two narrow columns (~40% of the old field width) side by side under Selection.
        int stepW = 16;
        int axisRoom = 8;
        int pairGap = 6;
        int span = modelSize + gap;
        int boxW = 22;
        int colW = stepW + 2 + boxW + 2 + stepW + axisRoom;
        while (boxW > 16 && colW * 2 + pairGap > span) {
            boxW--;
            colW = stepW + 2 + boxW + 2 + stepW + axisRoom;
        }
        sizeLeft = modelX + colW + pairGap;
        int locPlus = modelX + stepW + 2 + boxW + 2;
        int sizePlus = sizeLeft + stepW + 2 + boxW + 2;
        xBox = number(modelX + stepW + 2, locationY + 12, boxW);
        yBox = number(modelX + stepW + 2, locationY + 28, boxW);
        zBox = number(modelX + stepW + 2, locationY + 44, boxW);
        sxBox = number(sizeLeft + stepW + 2, locationY + 12, boxW);
        syBox = number(sizeLeft + stepW + 2, locationY + 28, boxW);
        szBox = number(sizeLeft + stepW + 2, locationY + 44, boxW);
        step(modelX, locationY + 12, "-", () -> move(-1, 0, 0));
        step(locPlus, locationY + 12, "+", () -> move(1, 0, 0));
        step(modelX, locationY + 28, "-", () -> move(0, -1, 0));
        step(locPlus, locationY + 28, "+", () -> move(0, 1, 0));
        step(modelX, locationY + 44, "-", () -> move(0, 0, -1));
        step(locPlus, locationY + 44, "+", () -> move(0, 0, 1));
        step(sizeLeft, locationY + 12, "-", () -> resize(0, -1));
        step(sizePlus, locationY + 12, "+", () -> resize(0, 1));
        step(sizeLeft, locationY + 28, "-", () -> resize(1, -1));
        step(sizePlus, locationY + 28, "+", () -> resize(1, 1));
        step(sizeLeft, locationY + 44, "-", () -> resize(2, -1));
        step(sizePlus, locationY + 44, "+", () -> resize(2, 1));
        int top = panelY + 28;
        rotateY = top + 46;
        flipY = rotateY + 36;
        pasteY = flipY + 48;
        int row = (controlsW - 28) / 2;
        lockButton = new WrightsButton(controlsX + 12, top + 14, row, 16, Component.translatable("screen.wrights.lock"), button -> setAim(false));
        followButton = new WrightsButton(controlsX + 16 + row, top + 14, row, 16, Component.translatable("screen.wrights.follow"), button -> setAim(true));
        addRenderableWidget(lockButton);
        addRenderableWidget(followButton);
        addRenderableWidget(new WrightsButton(controlsX + 12, rotateY, row, 16, Component.translatable("screen.wrights.rotate_left"), button -> transform(BlueprintTransforms.Op.ROTATE_CCW)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, rotateY, row, 16, Component.translatable("screen.wrights.rotate_right"), button -> transform(BlueprintTransforms.Op.ROTATE)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY, row, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> transform(BlueprintTransforms.Op.FLIP_X)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY, row, 16, Component.translatable("screen.wrights.flip_vertical"), button -> transform(BlueprintTransforms.Op.FLIP_Y)));
        addRenderableWidget(new WrightsButton(controlsX + 12, flipY + 18, row, 16, Component.translatable("screen.wrights.center_short"), button -> transform(BlueprintTransforms.Op.CENTER)));
        addRenderableWidget(new WrightsButton(controlsX + 16 + row, flipY + 18, row, 16, Component.translatable("screen.wrights.ground"), button -> transform(BlueprintTransforms.Op.GROUND)));
        placeFluidsButton = new WrightsButton(controlsX + 12, pasteY, controlsW - 24, 16, Component.translatable("screen.wrights.place_fluids"), button -> togglePlaceFluids());
        placeNbtButton = new WrightsButton(controlsX + 12, pasteY + 18, controlsW - 24, 16, Component.translatable("screen.wrights.place_nbt_blocks"), button -> togglePlaceNbt());
        placeSettingsButton = new WrightsButton(controlsX + 12, pasteY + 36, controlsW - 24, 16, Component.translatable("screen.wrights.place_nbt_settings"), button -> togglePlaceSettings());
        placeAllButton = new WrightsButton(controlsX + 12, pasteY + 54, controlsW - 24, 16, Component.translatable("screen.wrights.place_nbt_all"), button -> togglePlaceAll());
        int voidY = pasteY + 36 + (WrightsClientState.showStoredSettings() ? 18 : 0) + (WrightsClientState.showStoredAll() ? 18 : 0);
        voidBlocksButton = new WrightsButton(controlsX + 12, voidY, row, 16, Component.translatable("screen.wrights.void_blocks"), button -> toggleVoidBlocks());
        voidFluidsButton = new WrightsButton(controlsX + 16 + row, voidY, row, 16, Component.translatable("screen.wrights.void_fluids"), button -> toggleVoidFluids());
        voidNbtButton = new WrightsButton(controlsX + 12, voidY + 18, controlsW - 24, 16, Component.translatable("screen.wrights.void_nbt"), button -> toggleVoidNbt());
        addRenderableWidget(placeFluidsButton);
        addRenderableWidget(placeNbtButton);
        addRenderableWidget(placeSettingsButton);
        addRenderableWidget(placeAllButton);
        addRenderableWidget(voidBlocksButton);
        addRenderableWidget(voidFluidsButton);
        addRenderableWidget(voidNbtButton);
        int stackY = voidY + 18 + (voiding ? 18 : 0) + 10;
        deployY = stackY + 14;
        boolean paceInstant = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator() && WrightsClientConfig.INSTANT_JOBS.get();
        if (!paceInstant) {
            WrightsClientState.clampDeploy();
            step(controlsX + 12, deployY, "-", () -> changeAssign(-1));
            step(controlsX + controlsW - 28, deployY, "+", () -> changeAssign(1));
        }
        int supplyY = deployY + 22;
        inventoryButton = new WrightsButton(controlsX + 12, supplyY + 14, row, 16, Component.translatable("screen.wrights.supply_player"), button -> useInventory());
        chestButton = new WrightsButton(controlsX + 16 + row, supplyY + 14, row, 16, Component.translatable("screen.wrights.supply_bind"), button -> bindChest());
        addRenderableWidget(inventoryButton);
        addRenderableWidget(chestButton);
        int clearW = 52;
        int highlightW = 68;
        highlightSupplyButton = new WrightsButton(controlsX + controlsW - 16 - clearW - highlightW, supplyY + 36, highlightW, 16, Component.translatable("screen.wrights.supply_highlight"), button -> highlightSupply());
        clearSupplyButton = new WrightsButton(controlsX + controlsW - 12 - clearW, supplyY + 36, clearW, 16, Component.translatable("screen.wrights.supply_clear"), button -> useInventory());
        addRenderableWidget(highlightSupplyButton);
        addRenderableWidget(clearSupplyButton);
        int foot = supplyY + 58;
        addRenderableWidget(new WrightsButton(controlsX + 12, foot, controlsW - 24, 16, Component.translatable("menu.wrights.materials"), button -> MaterialsScreen.request(WrightsClientState.buildId, WrightsClientState.viewX, WrightsClientState.viewY, WrightsClientState.viewZ, WrightsClientConfig.PLACE_FLUIDS.get(), this)));
        int backW = 68;
        int backX = controlsW - 12 - backW;
        addRenderableWidget(new WrightsButton(controlsX + 12, foot + 18, backX - 16, 18, Component.translatable("screen.wrights.save_blueprint"), button -> saveBlueprint()));
        addRenderableWidget(new WrightsButton(controlsX + backX, foot + 18, backW, 18, Component.translatable("screen.wrights.back"), button -> onClose()));
        buildButton = new WrightsButton(controlsX + 12, foot + 40, controlsW - 24, 18, Component.translatable("menu.wrights.build"), button -> send());
        addRenderableWidget(buildButton);
        int contentBottom = Math.max(locationY + 60, foot + 60);
        panelH = Math.min(this.height - 8, Math.max(panelH, contentBottom - panelY + 8));
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
            placeFluidsButton.setShown(WrightsConfig.allowPlaceFluids());
            placeFluidsButton.setPicked(WrightsClientConfig.PLACE_FLUIDS.get());
            placeNbtButton.setPicked(WrightsClientConfig.PLACE_NBT_BLOCKS.get());
            boolean showSettings = WrightsClientState.showStoredSettings();
            boolean showAll = WrightsClientState.showStoredAll();
            placeSettingsButton.setShown(showSettings);
            placeSettingsButton.setPicked(showSettings && WrightsClientState.placeSettingsChosen());
            placeAllButton.setShown(showAll);
            placeAllButton.setPicked(showAll && WrightsClientState.placeAllChosen());
            int y = pasteY + 36;
            if (showSettings) {
                placeSettingsButton.setY(y);
                y += 18;
            }
            if (showAll) {
                placeAllButton.setY(y);
                y += 18;
            }
            voidBlocksButton.setY(y);
            voidFluidsButton.setY(y);
            voidBlocksButton.setShown(WrightsConfig.allowVoidBlocks());
            voidFluidsButton.setShown(WrightsConfig.allowVoidFluids());
            voidBlocksButton.setPicked(WrightsClientConfig.VOID_BLOCKS.get());
            voidFluidsButton.setPicked(WrightsClientConfig.VOID_FLUIDS.get());
            boolean voiding = WrightsClientConfig.VOID_BLOCKS.get() || WrightsClientConfig.VOID_FLUIDS.get();
            voidNbtButton.setY(y + 18);
            voidNbtButton.setShown(voiding && WrightsConfig.allowVoidNbt());
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
        graphics.drawString(this.font, Component.translatable("screen.wrights.placement"), controlsX + 12, panelY + 28, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.location"), modelX, locationY, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.size_label"), sizeLeft, locationY, WrightsUi.TEXT, false);
        int locAxis = xBox.getX() + xBox.getWidth() + 20;
        int sizeAxis = sxBox.getX() + sxBox.getWidth() + 20;
        axis(graphics, locAxis, locationY + 12, "X");
        axis(graphics, locAxis, locationY + 28, "Y");
        axis(graphics, locAxis, locationY + 44, "Z");
        axis(graphics, sizeAxis, locationY + 12, "X");
        axis(graphics, sizeAxis, locationY + 28, "Y");
        axis(graphics, sizeAxis, locationY + 44, "Z");
        graphics.drawString(this.font, Component.translatable("screen.wrights.deploy_on_job"), controlsX + 12, deployY - 14, WrightsUi.TEXT, false);
        int supplyLabelY = inventoryButton.getY() - 14;
        graphics.drawString(this.font, Component.translatable("screen.wrights.use_items_from"), controlsX + 12, supplyLabelY, WrightsUi.MUTED, false);
        boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
        if (bound) {
            Component supplyLine = Component.translatable("screen.wrights.supply_at", WrightsClientState.supplyPos.getX(), WrightsClientState.supplyPos.getY(), WrightsClientState.supplyPos.getZ());
            int room = Math.max(40, highlightSupplyButton.getX() - (controlsX + 12) - 6);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(supplyLine.getString(), room), controlsX + 12, highlightSupplyButton.getY() + 4, WrightsUi.TEXT, false);
        } else if (WrightsClientState.bindSupply) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.wrights.supply_aim").getString(), panelX + panelW - 24 - controlsX), controlsX + 12, highlightSupplyButton.getY() + 4, WrightsUi.MUTED, false);
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
                WrightsClientConfig.VOID_NBT.get(),
                WrightsClientConfig.PLACE_NBT_BLOCKS.get(),
                WrightsClientState.keepStoredSettings(),
                WrightsClientState.keepStoredAll()));
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

    private EditBox number(int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, 14, Component.empty());
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

    private void togglePlaceNbt() {
        WrightsClientConfig.setPlaceNbtBlocks(!WrightsClientConfig.PLACE_NBT_BLOCKS.get());
        refresh();
    }

    private void togglePlaceSettings() {
        if (!WrightsClientState.showStoredSettings()) {
            return;
        }
        WrightsClientConfig.setPlaceNbtSettings(!WrightsClientConfig.PLACE_NBT_SETTINGS.get());
        refresh();
    }

    private void togglePlaceAll() {
        if (!WrightsClientState.showStoredAll()) {
            return;
        }
        WrightsClientConfig.setPlaceNbtAll(!WrightsClientConfig.PLACE_NBT_ALL.get());
        refresh();
    }

    private void toggleVoidBlocks() {
        boolean before = voidRowOpen();
        WrightsClientConfig.setVoidBlocks(!WrightsClientConfig.VOID_BLOCKS.get());
        relayoutVoids(before);
    }

    private void toggleVoidFluids() {
        boolean before = voidRowOpen();
        WrightsClientConfig.setVoidFluids(!WrightsClientConfig.VOID_FLUIDS.get());
        relayoutVoids(before);
    }

    private static boolean voidRowOpen() {
        return WrightsClientConfig.VOID_BLOCKS.get() || WrightsClientConfig.VOID_FLUIDS.get();
    }

    private void relayoutVoids(boolean before) {
        if (before == voidRowOpen()) {
            refresh();
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.execute(this::rebuildWidgets);
        }
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
