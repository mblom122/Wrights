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
import com.wrights.blueprint.ShapeMaterials;
import com.wrights.blueprint.Shapes;
import com.wrights.util.BlockFilters;

/**
 * Compact Shift+right-click tweak for Place: Build mode (Look / To me / Bridge), form or path dims,
 * path forward offset (1–12), stairs up/down, and materials.
 */
public class PlaceTweakScreen extends Screen {
    private static final int PAD = 8;
    private static final int ROW = 14;
    private static final int GAP = 4;
    private static final int STEP = 14;
    private static final int ICON = 16;
    private static final int MAX_ICONS = 6;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int sizeY = -1;
    private int alongY = -1;
    private int styleLabelY = -1;
    private int styleChoicesY = -1;
        private int offsetY = -1;
    private int widthY = -1;
    private int toMeHeightY = -1;
    private int circleMountY = -1;
    private int rotateY = -1;
    private int matsLabelY = -1;
    private int matsRowY = -1;
    private int valueX;
    private int valueW;
    private Component tip;

    public PlaceTweakScreen() {
        super(Component.translatable("screen.wrights.place_tweak_title"));
    }

    @Override
    protected void init() {
        if (WrightsClientConfig.exchangeForm().line()) {
            WrightsClientConfig.enforceExchangeLineFootprint();
        }
        if (WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
        }

        int stepW = STEP;
        int fieldW = 40;
        int labelW = Math.max(52, this.font.width(Component.translatable("screen.wrights.shape_tweak_size")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("screen.wrights.shape_tweak_height")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("screen.wrights.shape_tweak_length")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.place_aim")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.place_bridge_offset")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.place_bridge_width")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.place_to_me_height")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.place_circle_mount")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.shape_rotation")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.shape_materials")));
        int rowInner = labelW + 4 + stepW + 2 + fieldW + 2 + stepW;
        Component done = Component.translatable("screen.wrights.done");
        Component change = Component.translatable("screen.wrights.shape_tweak_change");
        int changeW = Math.max(48, this.font.width(change) + 12);
        int btnW = this.font.width(done) + 14;
        int matsNeed = ICON * MAX_ICONS + GAP * (MAX_ICONS - 1) + GAP + changeW;
        panelW = WrightsUi.fitW(PAD * 2 + Math.max(Math.max(rowInner, btnW), matsNeed), this.width);

        int style = WrightsClientConfig.placeStyle();
        boolean look = style == 0;
        boolean toMe = style == 1;
        boolean bridge = WrightsClientConfig.placeBridgeStyle();
        boolean line = look && WrightsClientConfig.exchangeForm().line();
        boolean showFormDims = look;
        boolean pathDims = bridge;
        boolean circleMount = WrightsClientConfig.placeCircleForm();
        boolean yawRotate = WrightsClientConfig.placeFormYawRotatable();
        int rows = 1 // title
                + 1 // build mode label
                + 1 // build mode choices (Look / To me / Bridge)
                + (showFormDims && !line ? 1 : 0) // size
                + (showFormDims && (line || WrightsClientConfig.exchangeKind().usesHeight()) ? 1 : 0)
                + (pathDims ? 2 : 0) // offset + width
                + (toMe ? 2 : 0) // width + height
                + (circleMount ? 1 : 0) // floor / wall
                + (yawRotate ? 1 : 0) // rotate left/right
                + 1 // materials label
                + 1 // icons
                + 1; // done
        panelH = PAD + 12 + GAP + rows * (ROW + GAP) + PAD;
        panelH = WrightsUi.fitH(panelH, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);

        valueW = fieldW;
        valueX = panelX + PAD + labelW + 4 + stepW + 2;
        int minusX = panelX + PAD + labelW + 4;
        int plusX = valueX + fieldW + 2;
        int y = panelY + PAD + 12 + GAP;

        sizeY = alongY = styleLabelY = styleChoicesY = offsetY = widthY = toMeHeightY = circleMountY = rotateY = matsLabelY = matsRowY = -1;

        styleLabelY = y;
        y += ROW + GAP;
        styleChoicesY = y;
        y += ROW + GAP;

        if (showFormDims && !line) {
            sizeY = y;
            step(minusX, y, "-", () -> nudgeSize(-1));
            step(plusX, y, "+", () -> nudgeSize(1));
            y += ROW + GAP;
        }
        if (showFormDims && (line || WrightsClientConfig.exchangeKind().usesHeight())) {
            alongY = y;
            step(minusX, y, "-", () -> nudgeAlong(-1));
            step(plusX, y, "+", () -> nudgeAlong(1));
            y += ROW + GAP;
        }
        if (pathDims) {
            offsetY = y;
            step(minusX, y, "-", () -> nudgeOffset(-1));
            step(plusX, y, "+", () -> nudgeOffset(1));
            y += ROW + GAP;
            widthY = y;
            step(minusX, y, "-", () -> nudgeWidth(-1));
            step(plusX, y, "+", () -> nudgeWidth(1));
            y += ROW + GAP;
        }
        if (toMe) {
            widthY = y;
            step(minusX, y, "-", () -> nudgeWidth(-1));
            step(plusX, y, "+", () -> nudgeWidth(1));
            y += ROW + GAP;
            toMeHeightY = y;
            step(minusX, y, "-", () -> nudgeToMeHeight(-1));
            step(plusX, y, "+", () -> nudgeToMeHeight(1));
            y += ROW + GAP;
        }
        if (circleMount) {
            circleMountY = y;
            y += ROW + GAP;
        }
        if (yawRotate) {
            rotateY = y;
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap) / 2;
            int leftX = panelX + PAD;
            int rightX = leftX + choiceW + gap;
            int rightW = panelW - PAD * 2 - choiceW - gap;
            addRenderableWidget(new WrightsButton(
                    leftX,
                    rotateY,
                    choiceW,
                    ROW,
                    Component.translatable("screen.wrights.rotate_left"),
                    button -> WrightsClientConfig.nudgeShapeYaw(1)));
            addRenderableWidget(new WrightsButton(
                    rightX,
                    rotateY,
                    rightW,
                    ROW,
                    Component.translatable("screen.wrights.rotate_right"),
                    button -> WrightsClientConfig.nudgeShapeYaw(-1)));
            y += ROW + GAP;
        }
        matsLabelY = y;
        y += ROW + GAP;
        matsRowY = y;
        int changeX = panelX + panelW - PAD - changeW;
        addRenderableWidget(new WrightsButton(changeX, matsRowY, changeW, ROW, change, button -> openMaterialsPicker()));
        y += ROW + GAP;
        addRenderableWidget(new WrightsButton(panelX + PAD, y, panelW - PAD * 2, ROW, done,
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
                "screen.wrights.place_tweak_hint");

        boolean line = WrightsClientConfig.exchangeForm().line();
        boolean pillar = WrightsClientConfig.exchangeForm() == Shapes.ExchangeForm.PILLAR;
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

        if (styleLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.place_aim"),
                    panelX + PAD, styleLabelY + 3, WrightsUi.MUTED, false);
        }
        if (styleChoicesY >= 0) {
            int style = WrightsClientConfig.placeStyle();
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap * 2) / 3;
            int choiceX = panelX + PAD;
            drawChoice(graphics, mouseX, mouseY, choiceX, styleChoicesY, choiceW,
                    style == 0, "menu.wrights.place_aim_look");
            drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + gap, styleChoicesY, choiceW,
                    style == 1, "menu.wrights.place_aim_me");
            drawChoice(graphics, mouseX, mouseY, choiceX + (choiceW + gap) * 2, styleChoicesY,
                    panelW - PAD * 2 - (choiceW + gap) * 2, style == 2, "menu.wrights.place_aim_bridge");
        }
        if (offsetY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.place_bridge_offset"),
                    panelX + PAD, offsetY + 3, WrightsUi.MUTED, false);
            drawValue(graphics, offsetY, WrightsClientConfig.placeBridgeOffset());
        }
        if (widthY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.place_bridge_width"),
                    panelX + PAD, widthY + 3, WrightsUi.MUTED, false);
            drawValue(graphics, widthY, WrightsClientConfig.placeBridgeWidth());
        }
        if (toMeHeightY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.place_to_me_height"),
                    panelX + PAD, toMeHeightY + 3, WrightsUi.MUTED, false);
            drawValue(graphics, toMeHeightY, WrightsClientConfig.placeToMeHeight());
        }
        if (circleMountY >= 0) {
            boolean wall = WrightsClientConfig.placeCircleWall();
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap) / 2;
            int choiceX = panelX + PAD;
            drawChoice(graphics, mouseX, mouseY, choiceX, circleMountY, choiceW,
                    !wall, "menu.wrights.place_circle_floor");
            drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + gap, circleMountY,
                    panelW - PAD * 2 - choiceW - gap, wall, "menu.wrights.place_circle_wall");
        }
        if (matsLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.shape_materials"),
                    panelX + PAD, matsLabelY + 3, WrightsUi.MUTED, false);
        }
        if (matsRowY >= 0) {
            drawMaterialIcons(graphics, mouseX, mouseY);
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
        if (button == 0 && styleChoicesY >= 0) {
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap * 2) / 3;
            int choiceX = panelX + PAD;
            int rightW = panelW - PAD * 2 - (choiceW + gap) * 2;
            if (hit(mouseX, mouseY, choiceX, styleChoicesY, choiceW, ROW)) {
                WrightsClientConfig.setPlaceStyle(0);
                rebuild();
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + choiceW + gap, styleChoicesY, choiceW, ROW)) {
                WrightsClientConfig.setPlaceStyle(1);
                rebuild();
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + (choiceW + gap) * 2, styleChoicesY, rightW, ROW)) {
                WrightsClientConfig.setPlaceStyle(2);
                rebuild();
                return true;
            }
        }
        if (button == 0 && circleMountY >= 0) {
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap) / 2;
            int choiceX = panelX + PAD;
            int rightW = panelW - PAD * 2 - choiceW - gap;
            if (hit(mouseX, mouseY, choiceX, circleMountY, choiceW, ROW)) {
                WrightsClientConfig.setPlaceCircleWall(false, this.minecraft != null ? this.minecraft.player : null);
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + choiceW + gap, circleMountY, rightW, ROW)) {
                WrightsClientConfig.setPlaceCircleWall(true, this.minecraft != null ? this.minecraft.player : null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void rebuild() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new PlaceTweakScreen());
        }
    }

    private void nudgeSize(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        WrightsClientConfig.setShapeSize(WrightsClientConfig.SHAPE_SIZE.get() + step);
        if (WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
        }
    }

    private void nudgeAlong(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        if (WrightsClientConfig.exchangeForm().line()) {
            WrightsClientConfig.setExchangeLineLength(WrightsClientConfig.exchangeLineLength() + step);
            return;
        }
        WrightsClientConfig.setShapeHeight(WrightsClientConfig.SHAPE_HEIGHT.get() + step);
        if (WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
        }
    }

    private void nudgeOffset(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        WrightsClientConfig.setPlaceBridgeOffset(WrightsClientConfig.placeBridgeOffset() + step);
    }

    private void nudgeWidth(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        WrightsClientConfig.setPlaceBridgeWidth(WrightsClientConfig.placeBridgeWidth() + step);
    }

    private void nudgeToMeHeight(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        WrightsClientConfig.setPlaceToMeHeight(WrightsClientConfig.placeToMeHeight() + step);
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

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, STEP, ROW, Component.literal(label), button -> action.run()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
