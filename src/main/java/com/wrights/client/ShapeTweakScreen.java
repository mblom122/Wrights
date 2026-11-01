package com.wrights.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.ShapeMaterials;
import com.wrights.blueprint.Shapes;
import com.wrights.util.BlockFilters;

/**
 * Compact translucent Shift+right-click tweak for Shape / Exchange:
 * size, Look vs Anchor, materials / Replace with icons + Change, and Edit point.
 * Same light-veil panel style as {@link ShapeLookSnapScreen}.
 */
public class ShapeTweakScreen extends Screen {
    private static final int PAD = 8;
    private static final int ROW = 14;
    private static final int GAP = 4;
    private static final int STEP = 14;
    private static final int ICON = 16;
    private static final int MAX_ICONS = 6;

    private final boolean exchange;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int sizeY = -1;
    private int alongY = -1;
    private int thicknessY = -1;
    private int placeLabelY = -1;
    private int placeChoicesY = -1;
    private int matchLabelY = -1;
    private int matchChoicesY = -1;
    private int matsLabelY = -1;
    private int matsRowY = -1;
    private int valueX;
    private int valueW;
    private Component tip;

    public ShapeTweakScreen(boolean exchange) {
        super(Component.translatable(exchange ? "screen.wrights.exchange_tweak_title" : "screen.wrights.shape_tweak_title"));
        this.exchange = exchange;
    }

    @Override
    protected void init() {
        if (exchange && WrightsClientConfig.exchangeForm().line()) {
            WrightsClientConfig.enforceExchangeLineFootprint();
        }
        if (exchange && WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
        }

        int stepW = STEP;
        int fieldW = 40;
        int labelW = Math.max(52, this.font.width(Component.translatable("screen.wrights.shape_tweak_size")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("screen.wrights.shape_tweak_height")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("screen.wrights.shape_tweak_length")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("screen.wrights.shape_tweak_depth")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.shape_place")));
        labelW = Math.max(labelW, this.font.width(Component.translatable(
                exchange ? "menu.wrights.exchange_to" : "menu.wrights.shape_materials")));
        if (exchange) {
            labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.exchange_match")));
        }
        int rowInner = labelW + 4 + stepW + 2 + fieldW + 2 + stepW;
        Component done = Component.translatable("screen.wrights.done");
        Component edit = Component.translatable("screen.wrights.shape_tweak_edit_point");
        Component change = Component.translatable("screen.wrights.shape_tweak_change");
        int changeW = Math.max(48, this.font.width(change) + 12);
        int btnW = Math.max(this.font.width(done), this.font.width(edit)) + 14;
        int matsNeed = ICON * MAX_ICONS + GAP * (MAX_ICONS - 1) + GAP + changeW;
        panelW = WrightsUi.fitW(PAD * 2 + Math.max(Math.max(rowInner, btnW * 2 + GAP), matsNeed), this.width);

        boolean anchors = WrightsConfig.allowShapeAnchor();
        boolean line = exchange && WrightsClientConfig.exchangeForm().line();
        Shapes.Kind kind = exchange ? WrightsClientConfig.exchangeKind() : Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
        boolean showSize = !line;
        boolean showAlong = line || kind.usesHeight();
        boolean showThickness = !line && (kind == Shapes.Kind.BOX || kind == Shapes.Kind.RING);
        boolean showLookLock = !exchange && anchors && !WrightsClientConfig.SHAPE_USE_ANCHOR.get();
        int rows = 1 // title
                + (showSize ? 1 : 0)
                + (showAlong ? 1 : 0)
                + (showThickness ? 1 : 0)
                + 1 // Build at label
                + 1 // Build at choices
                + (exchange ? 2 : 0) // Match label + Match look / Any
                + 1 // materials label
                + 1 // materials icons + Change
                + (showLookLock ? 2 : 0)
                + 1; // Edit / Done
        panelH = PAD + 12 + GAP + rows * (ROW + GAP) + PAD;
        panelH = WrightsUi.fitH(panelH, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);

        valueW = fieldW;
        valueX = panelX + PAD + labelW + 4 + stepW + 2;
        int minusX = panelX + PAD + labelW + 4;
        int plusX = valueX + fieldW + 2;
        int y = panelY + PAD + 12 + GAP;

        sizeY = alongY = thicknessY = placeLabelY = placeChoicesY = matchLabelY = matchChoicesY = matsLabelY = matsRowY = -1;
        if (showSize) {
            sizeY = y;
            step(minusX, y, "-", () -> nudgeSize(-1));
            step(plusX, y, "+", () -> nudgeSize(1));
            y += ROW + GAP;
        }
        if (showAlong) {
            alongY = y;
            step(minusX, y, "-", () -> nudgeAlong(-1));
            step(plusX, y, "+", () -> nudgeAlong(1));
            y += ROW + GAP;
        }
        if (showThickness) {
            thicknessY = y;
            step(minusX, y, "-", () -> nudgeThickness(-1));
            step(plusX, y, "+", () -> nudgeThickness(1));
            y += ROW + GAP;
        }

        placeLabelY = y;
        y += ROW + GAP;
        placeChoicesY = y;
        y += ROW + GAP;
        if (exchange) {
            matchLabelY = y;
            y += ROW + GAP;
            matchChoicesY = y;
            y += ROW + GAP;
        }
        matsLabelY = y;
        y += ROW + GAP;
        matsRowY = y;
        int changeX = panelX + panelW - PAD - changeW;
        addRenderableWidget(new WrightsButton(changeX, matsRowY, changeW, ROW, change, button -> openMaterialsPicker()));
        y += ROW + GAP;
        if (showLookLock) {
            y += ROW + GAP;
            y += ROW + GAP;
        }

        int half = (panelW - PAD * 2 - GAP) / 2;
        addRenderableWidget(new WrightsButton(panelX + PAD, y, half, ROW, edit, button -> openPointEditor()));
        addRenderableWidget(new WrightsButton(panelX + PAD + half + GAP, y, panelW - PAD * 2 - half - GAP, ROW, done,
                button -> this.minecraft.setScreen(null)));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66060C14);
        graphics.fill(panelX + 3, panelY + 4, panelX + panelW + 3, panelY + panelH + 4, 0x33000000);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xB0101822);
        WrightsUi.border(graphics, panelX, panelY, panelW, panelH, 0xFF31404E);
        graphics.fill(panelX, panelY, panelX + 2, panelY + panelH, WrightsUi.COPPER);
        graphics.fill(panelX + 2, panelY, panelX + panelW, panelY + 1, WrightsUi.COPPER_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tip = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleY = panelY + PAD;
        graphics.drawString(this.font, this.title, panelX + PAD, titleY, WrightsUi.COPPER, false);
        drawHelp(graphics, mouseX, mouseY, panelX + PAD + this.font.width(this.title) + 4, titleY - 2,
                "screen.wrights.shape_tweak_hint");

        boolean line = exchange && WrightsClientConfig.exchangeForm().line();
        boolean pillar = exchange && WrightsClientConfig.exchangeForm() == Shapes.ExchangeForm.PILLAR;
        if (sizeY >= 0) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.shape_tweak_size"),
                    panelX + PAD, sizeY + 3, WrightsUi.MUTED, false);
            drawValue(graphics, sizeY, WrightsClientConfig.SHAPE_SIZE.get());
        }
        if (alongY >= 0) {
            String alongKey = line
                    ? (pillar ? "screen.wrights.shape_tweak_height" : "screen.wrights.shape_tweak_length")
                    : "screen.wrights.shape_tweak_height";
            graphics.drawString(this.font, Component.translatable(alongKey),
                    panelX + PAD, alongY + 3, WrightsUi.MUTED, false);
            int along = line ? WrightsClientConfig.exchangeLineLength() : WrightsClientConfig.SHAPE_HEIGHT.get();
            drawValue(graphics, alongY, along);
        }
        if (thicknessY >= 0) {
            Shapes.Kind kind = exchange ? WrightsClientConfig.exchangeKind() : Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
            String thickKey = kind == Shapes.Kind.BOX ? "screen.wrights.shape_tweak_depth" : "screen.wrights.shape_tweak_band";
            graphics.drawString(this.font, Component.translatable(thickKey),
                    panelX + PAD, thicknessY + 3, WrightsUi.MUTED, false);
            drawValue(graphics, thicknessY, WrightsClientConfig.SHAPE_THICKNESS.get());
        }

        boolean anchors = WrightsConfig.allowShapeAnchor();
        boolean useAnchor = anchors && WrightsClientConfig.SHAPE_USE_ANCHOR.get();
        if (placeLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.shape_place"),
                    panelX + PAD, placeLabelY + 3, WrightsUi.MUTED, false);
        }
        if (placeChoicesY >= 0) {
            int choiceX = panelX + PAD;
            int choiceW = (panelW - PAD * 2 - GAP) / 2;
            drawChoice(graphics, mouseX, mouseY, choiceX, placeChoicesY, choiceW,
                    !useAnchor, "menu.wrights.shape_place_look");
            drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + GAP, placeChoicesY,
                    panelW - PAD * 2 - choiceW - GAP, useAnchor, "menu.wrights.shape_place_anchor");
        }

        if (exchange && matchLabelY >= 0 && matchChoicesY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.exchange_match"),
                    panelX + PAD, matchLabelY + 3, WrightsUi.MUTED, false);
            int choiceX = panelX + PAD;
            int choiceW = (panelW - PAD * 2 - GAP) / 2;
            boolean same = WrightsClientConfig.exchangeSameOnly();
            drawChoice(graphics, mouseX, mouseY, choiceX, matchChoicesY, choiceW,
                    same, "menu.wrights.exchange_match_same");
            drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + GAP, matchChoicesY,
                    panelW - PAD * 2 - choiceW - GAP, !same, "menu.wrights.exchange_match_any");
        }

        if (matsLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable(
                            exchange ? "menu.wrights.exchange_to" : "menu.wrights.shape_materials"),
                    panelX + PAD, matsLabelY + 3, WrightsUi.MUTED, false);
        }
        if (matsRowY >= 0) {
            drawMaterialIcons(graphics, mouseX, mouseY);
        }

        if (!exchange && anchors && !useAnchor && placeChoicesY >= 0) {
            int lockLabelY = matsRowY >= 0 ? matsRowY + ROW + GAP : placeChoicesY + ROW + GAP;
            // Lock sits after materials when present.
            if (matsRowY >= 0) {
                lockLabelY = matsRowY + ROW + GAP;
            } else {
                lockLabelY = placeChoicesY + ROW + GAP;
            }
            int lockChoicesY = lockLabelY + ROW + GAP;
            int choiceX = panelX + PAD;
            int choiceW = (panelW - PAD * 2 - GAP) / 2;
            graphics.drawString(this.font, Component.translatable("menu.wrights.shape_look_lock"),
                    panelX + PAD, lockLabelY + 3, WrightsUi.MUTED, false);
            boolean lock = WrightsClientConfig.SHAPE_LOOK_LOCK.get();
            drawChoice(graphics, mouseX, mouseY, choiceX, lockChoicesY, choiceW, !lock, "menu.wrights.off");
            drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + GAP, lockChoicesY,
                    panelW - PAD * 2 - choiceW - GAP, lock, "menu.wrights.on");
        }

        if (tip != null) {
            graphics.renderTooltip(this.font, this.font.split(tip, 220), mouseX, mouseY);
        }
    }

    private void drawMaterialIcons(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ShapeMaterials.Entry> entries = WrightsClientConfig.shapeMaterials();
        int x = panelX + PAD;
        int y = matsRowY;
        Component change = Component.translatable("screen.wrights.shape_tweak_change");
        int changeW = Math.max(48, this.font.width(change) + 12);
        int iconRight = panelX + panelW - PAD - changeW - GAP;
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.shape_tweak_materials_empty"),
                    x, y + 3, WrightsUi.MUTED, false);
            return;
        }
        int shown = Math.min(MAX_ICONS, entries.size());
        for (int i = 0; i < shown; i++) {
            int cx = x + i * (ICON + 2);
            if (cx + ICON > iconRight) {
                break;
            }
            ItemStack stack = entries.get(i).item().getDefaultInstance();
            graphics.renderItem(stack, cx, y - 1);
            if (mouseX >= cx && mouseX < cx + ICON && mouseY >= y - 1 && mouseY < y - 1 + ICON) {
                tip = stack.getHoverName();
            }
        }
        if (entries.size() > shown) {
            int moreX = x + shown * (ICON + 2);
            if (moreX + 12 <= iconRight) {
                graphics.drawString(this.font, "+" + (entries.size() - shown), moreX, y + 3, WrightsUi.MUTED, false);
            }
        }
    }

    private void drawValue(GuiGraphics graphics, int y, int value) {
        String text = Integer.toString(value);
        graphics.drawCenteredString(this.font, text, valueX + valueW / 2, y + 3, WrightsUi.TEXT);
    }

    private void drawChoice(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, boolean on, String key) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW;
        WrightsUi.choice(graphics, x, y, w, ROW, on, hover);
        String label = this.font.plainSubstrByWidth(Component.translatable(key).getString(), Math.max(8, w - 6));
        graphics.drawCenteredString(this.font, label, x + w / 2, y + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED);
    }

    private void drawHelp(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String hint) {
        boolean over = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        graphics.fill(x, y, x + 12, y + 12, 0xFF18222C);
        WrightsUi.border(graphics, x, y, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, "?", x + 6, y + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        if (over) {
            tip = Component.translatable(hint);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleModeClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleModeClick(double mouseX, double mouseY) {
        boolean anchors = WrightsConfig.allowShapeAnchor();
        boolean useAnchor = anchors && WrightsClientConfig.SHAPE_USE_ANCHOR.get();
        if (placeChoicesY < 0) {
            return false;
        }
        int choiceX = panelX + PAD;
        int choiceW = (panelW - PAD * 2 - GAP) / 2;
        if (hit(mouseX, mouseY, choiceX, placeChoicesY, choiceW, ROW)) {
            WrightsClientConfig.setShapeUseAnchor(false);
            WrightsClientState.clearShapeLookSnap();
            if (exchange) {
                WrightsClientConfig.setShapeLookLock(false);
            }
            rebuild();
            return true;
        }
        if (anchors && hit(mouseX, mouseY, choiceX + choiceW + GAP, placeChoicesY,
                panelW - PAD * 2 - choiceW - GAP, ROW)) {
            WrightsClientConfig.setShapeUseAnchor(true);
            WrightsClientState.clearShapeLookSnap();
            rebuild();
            return true;
        }
        if (exchange && matchChoicesY >= 0) {
            if (hit(mouseX, mouseY, choiceX, matchChoicesY, choiceW, ROW)) {
                WrightsClientConfig.setExchangeSameOnly(true);
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + choiceW + GAP, matchChoicesY,
                    panelW - PAD * 2 - choiceW - GAP, ROW)) {
                WrightsClientConfig.setExchangeSameOnly(false);
                return true;
            }
        }
        if (!exchange && anchors && !useAnchor && matsRowY >= 0) {
            int lockChoicesY = matsRowY + ROW + GAP + ROW + GAP;
            if (hit(mouseX, mouseY, choiceX, lockChoicesY, choiceW, ROW)) {
                WrightsClientConfig.setShapeLookLock(false);
                WrightsClientState.clearShapeLookSnap();
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + choiceW + GAP, lockChoicesY,
                    panelW - PAD * 2 - choiceW - GAP, ROW)) {
                WrightsClientConfig.setShapeLookLock(true);
                return true;
            }
        }
        return false;
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void rebuild() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ShapeTweakScreen(exchange));
        }
    }

    private void nudgeSize(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        WrightsClientConfig.setShapeSize(WrightsClientConfig.SHAPE_SIZE.get() + step);
        if (exchange && WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
        }
    }

    private void nudgeAlong(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        if (exchange && WrightsClientConfig.exchangeForm().line()) {
            WrightsClientConfig.setExchangeLineLength(WrightsClientConfig.exchangeLineLength() + step);
            return;
        }
        WrightsClientConfig.setShapeHeight(WrightsClientConfig.SHAPE_HEIGHT.get() + step);
        if (exchange && WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
        }
    }

    private void nudgeThickness(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        WrightsClientConfig.setShapeThickness(WrightsClientConfig.SHAPE_THICKNESS.get() + step);
    }

    private void openMaterialsPicker() {
        List<BlockState> initial = new ArrayList<>();
        for (ShapeMaterials.Entry entry : WrightsClientConfig.shapeMaterials()) {
            BlockState state = entry.state();
            if (state != null && !state.isAir()) {
                initial.add(state);
            }
        }
        BlockPickerScreen.openBlocks(this, initial, this::applyMaterials);
    }

    private void applyMaterials(List<BlockState> states) {
        if (states == null || states.isEmpty()) {
            return;
        }
        Map<Item, Integer> weights = new HashMap<>();
        for (ShapeMaterials.Entry entry : WrightsClientConfig.shapeMaterials()) {
            weights.put(entry.item(), entry.weight());
        }
        List<ShapeMaterials.Entry> next = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (BlockState state : states) {
            if (!BlockFilters.canShapeMaterial(state)) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (!(item instanceof BlockItem) || !seen.add(item)) {
                continue;
            }
            next.add(new ShapeMaterials.Entry(item, weights.getOrDefault(item, 1)));
        }
        if (!next.isEmpty()) {
            WrightsClientConfig.setShapeMaterials(next);
        }
        rebuild();
    }

    private void openPointEditor() {
        boolean lookMode = !WrightsClientConfig.SHAPE_USE_ANCHOR.get() || !WrightsConfig.allowShapeAnchor();
        if (lookMode) {
            if (!WrightsClientState.shapeLookSnapActive()) {
                var aim = WrightsClientState.shapeLookHit();
                if (aim == null) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                Component.translatable("message.wrights.shape_look"), true);
                    }
                    return;
                }
                WrightsClientState.lockShapeLookSnap(aim.getBlockPos(), aim.getDirection());
            }
            this.minecraft.setScreen(new ShapeLookSnapScreen());
            return;
        }
        this.minecraft.setScreen(new ShapeAnchorScreen(null));
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, STEP, ROW, Component.literal(label), button -> action.run()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
