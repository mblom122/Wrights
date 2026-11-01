package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.Mirror;

/**
 * Compact Shift+right-click tweak for Mirror: Blocks / Blueprints mode, Axis or Anchored face,
 * axes or copy count, and the options you flip most often while building.
 */
public class MirrorTweakScreen extends Screen {
    private static final int PAD = 8;
    private static final int ROW = 14;
    private static final int GAP = 4;
    private static final int STEP = 14;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int modeLabelY = -1;
    private int modeChoicesY = -1;
    private int styleLabelY = -1;
    private int styleChoicesY = -1;
    private int axesLabelY = -1;
    private int axesChoicesY = -1;
    private int copiesY = -1;
    private int shapesY = -1;
    private int ghostY = -1;
    private int replaceY = -1;
    private int mineY = -1;
    private int mineModeY = -1;
    private int mineVoidY = -1;
    private int valueX;
    private int valueW;
    private Component tip;

    public MirrorTweakScreen() {
        super(Component.translatable("screen.wrights.mirror_tweak_title"));
    }

    @Override
    protected void init() {
        int stepW = STEP;
        int fieldW = 40;
        int labelW = Math.max(52, this.font.width(Component.translatable("menu.wrights.mirror_mode")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_style")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_axis")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_pattern")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_shapes")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_on")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_replace")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_mine")));
        labelW = Math.max(labelW, this.font.width(Component.translatable("menu.wrights.mirror_mine_void")));
        int rowInner = labelW + 4 + stepW + 2 + fieldW + 2 + stepW;
        Component done = Component.translatable("screen.wrights.done");
        int btnW = this.font.width(done) + 14;
        panelW = WrightsUi.fitW(PAD * 2 + Math.max(rowInner, btnW), this.width);

        boolean blocksAllowed = WrightsConfig.allowMirrorBlocks();
        boolean plansAllowed = WrightsConfig.allowMirrorBlueprints();
        boolean showMode = blocksAllowed && plansAllowed;
        boolean blocks = WrightsClientConfig.mirrorBlocks();
        boolean axisMode = WrightsConfig.allowMirrorAxes() && (WrightsClientConfig.MIRROR_AXES.get() & 7) != 0;
        boolean showStyle = WrightsConfig.allowMirrorAxes();
        boolean showReplace = blocks && WrightsConfig.allowMirrorReplace();
        boolean showMine = blocks && WrightsConfig.allowMirrorMine();
        boolean showMineDetails = showMine && WrightsClientConfig.mirrorMine();
        boolean showVoid = showMineDetails && WrightsConfig.allowVoidBlocks();

        int rows = 1 // title
                + (showMode ? 2 : 0) // mode label + choices
                + (showStyle ? 2 : 0) // style label + choices
                + (axisMode ? 2 : 1) // axes label+choices, or copies value row
                + 2 // flip + ghost
                + (showReplace ? 1 : 0)
                + (showMine ? 1 : 0)
                + (showMineDetails ? 1 : 0)
                + (showVoid ? 1 : 0)
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

        modeLabelY = modeChoicesY = styleLabelY = styleChoicesY = axesLabelY = axesChoicesY = copiesY =
                shapesY = ghostY = replaceY = mineY = mineModeY = mineVoidY = -1;

        if (showMode) {
            modeLabelY = y;
            y += ROW + GAP;
            modeChoicesY = y;
            y += ROW + GAP;
        }
        if (showStyle) {
            styleLabelY = y;
            y += ROW + GAP;
            styleChoicesY = y;
            y += ROW + GAP;
        }
        if (axisMode) {
            axesLabelY = y;
            y += ROW + GAP;
            axesChoicesY = y;
            y += ROW + GAP;
        } else {
            copiesY = y;
            step(minusX, y, "-", () -> nudgeCopies(-1));
            step(plusX, y, "+", () -> nudgeCopies(1));
            y += ROW + GAP;
        }

        shapesY = y;
        y += ROW + GAP;
        ghostY = y;
        y += ROW + GAP;
        if (showReplace) {
            replaceY = y;
            y += ROW + GAP;
        }
        if (showMine) {
            mineY = y;
            y += ROW + GAP;
        }
        if (showMineDetails) {
            mineModeY = y;
            y += ROW + GAP;
        }
        if (showVoid) {
            mineVoidY = y;
            y += ROW + GAP;
        }

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
                "screen.wrights.mirror_tweak_hint");

        if (modeLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_mode"),
                    panelX + PAD, modeLabelY + 3, WrightsUi.MUTED, false);
        }
        if (modeChoicesY >= 0) {
            boolean blocks = WrightsClientConfig.mirrorBlocks();
            drawPair(graphics, mouseX, mouseY, modeChoicesY, blocks,
                    "menu.wrights.mirror_blocks", "menu.wrights.mirror_blueprints");
        }
        if (styleLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_style"),
                    panelX + PAD, styleLabelY + 3, WrightsUi.MUTED, false);
        }
        if (styleChoicesY >= 0) {
            boolean axisMode = (WrightsClientConfig.MIRROR_AXES.get() & 7) != 0;
            drawPair(graphics, mouseX, mouseY, styleChoicesY, axisMode,
                    "menu.wrights.mirror_style_axis", "menu.wrights.mirror_style_face");
        }
        if (axesLabelY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_axis"),
                    panelX + PAD, axesLabelY + 3, WrightsUi.MUTED, false);
        }
        if (axesChoicesY >= 0) {
            int axes = WrightsClientConfig.MIRROR_AXES.get() & 7;
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap * 2) / 3;
            int choiceX = panelX + PAD;
            drawChoice(graphics, mouseX, mouseY, choiceX, axesChoicesY, choiceW,
                    (axes & Mirror.AXIS_X) != 0, "menu.wrights.mirror_axis_x");
            drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + gap, axesChoicesY, choiceW,
                    (axes & Mirror.AXIS_Y) != 0, "menu.wrights.mirror_axis_y");
            drawChoice(graphics, mouseX, mouseY, choiceX + (choiceW + gap) * 2, axesChoicesY,
                    panelW - PAD * 2 - (choiceW + gap) * 2, (axes & Mirror.AXIS_Z) != 0, "menu.wrights.mirror_axis_z");
        }
        if (copiesY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_pattern"),
                    panelX + PAD, copiesY + 3, WrightsUi.MUTED, false);
            drawValue(graphics, copiesY, WrightsConfig.allowedPattern(WrightsClientConfig.MIRROR_PATTERN.get()));
        }
        if (shapesY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_shapes"),
                    panelX + PAD, shapesY + 3, WrightsUi.MUTED, false);
            drawOnOff(graphics, mouseX, mouseY, shapesY, WrightsClientConfig.MIRROR_SHAPES.get());
        }
        if (ghostY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_on"),
                    panelX + PAD, ghostY + 3, WrightsUi.MUTED, false);
            drawOnOff(graphics, mouseX, mouseY, ghostY, WrightsClientConfig.MIRROR_ON.get());
        }
        if (replaceY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_replace"),
                    panelX + PAD, replaceY + 3, WrightsUi.MUTED, false);
            drawOnOff(graphics, mouseX, mouseY, replaceY, WrightsClientConfig.mirrorReplace());
        }
        if (mineY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_mine"),
                    panelX + PAD, mineY + 3, WrightsUi.MUTED, false);
            drawOnOff(graphics, mouseX, mouseY, mineY, WrightsClientConfig.mirrorMine());
        }
        if (mineModeY >= 0) {
            drawPair(graphics, mouseX, mouseY, mineModeY, !WrightsClientConfig.mirrorMineAny(),
                    "menu.wrights.mirror_mine_same", "menu.wrights.mirror_mine_any");
        }
        if (mineVoidY >= 0) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.mirror_mine_void"),
                    panelX + PAD, mineVoidY + 3, WrightsUi.MUTED, false);
            drawOnOff(graphics, mouseX, mouseY, mineVoidY, WrightsClientConfig.mirrorMineVoid());
        }
        if (tip != null) {
            graphics.renderTooltip(this.font, this.font.split(tip, 220), mouseX, mouseY);
        }
    }

    private void drawPair(GuiGraphics graphics, int mouseX, int mouseY, int y, boolean leftOn, String leftKey, String rightKey) {
        int gap = GAP;
        int choiceW = (panelW - PAD * 2 - gap) / 2;
        int choiceX = panelX + PAD;
        drawChoice(graphics, mouseX, mouseY, choiceX, y, choiceW, leftOn, leftKey);
        drawChoice(graphics, mouseX, mouseY, choiceX + choiceW + gap, y,
                panelW - PAD * 2 - choiceW - gap, !leftOn, rightKey);
    }

    private void drawOnOff(GuiGraphics graphics, int mouseX, int mouseY, int y, boolean on) {
        int gap = GAP;
        int choiceW = Math.max(28, Math.min(44, (panelW - PAD * 2 - gap) / 4));
        int onX = panelX + panelW - PAD - choiceW;
        int offX = onX - gap - choiceW;
        drawChoice(graphics, mouseX, mouseY, offX, y, choiceW, !on, "menu.wrights.off");
        drawChoice(graphics, mouseX, mouseY, onX, y, choiceW, on, "menu.wrights.on");
    }

    private void drawValue(GuiGraphics graphics, int y, int value) {
        graphics.drawCenteredString(this.font, Integer.toString(value), valueX + valueW / 2, y + 3, WrightsUi.TEXT);
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
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (modeChoicesY >= 0 && clickPair(mouseX, mouseY, modeChoicesY,
                () -> {
                    WrightsClientConfig.setMirrorBlocks(true);
                    rebuild();
                },
                () -> {
                    WrightsClientConfig.setMirrorBlocks(false);
                    rebuild();
                })) {
            return true;
        }
        if (styleChoicesY >= 0 && clickPair(mouseX, mouseY, styleChoicesY,
                () -> {
                    WrightsClientConfig.setMirrorStyleAxes();
                    rebuild();
                },
                () -> {
                    WrightsClientConfig.setMirrorStyleAnchored();
                    rebuild();
                })) {
            return true;
        }
        if (axesChoicesY >= 0) {
            int gap = GAP;
            int choiceW = (panelW - PAD * 2 - gap * 2) / 3;
            int choiceX = panelX + PAD;
            int rightW = panelW - PAD * 2 - (choiceW + gap) * 2;
            if (hit(mouseX, mouseY, choiceX, axesChoicesY, choiceW, ROW)) {
                WrightsClientConfig.toggleMirrorAxis(Mirror.AXIS_X);
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + choiceW + gap, axesChoicesY, choiceW, ROW)) {
                WrightsClientConfig.toggleMirrorAxis(Mirror.AXIS_Y);
                return true;
            }
            if (hit(mouseX, mouseY, choiceX + (choiceW + gap) * 2, axesChoicesY, rightW, ROW)) {
                WrightsClientConfig.toggleMirrorAxis(Mirror.AXIS_Z);
                return true;
            }
        }
        if (shapesY >= 0 && clickOnOff(mouseX, mouseY, shapesY, WrightsClientConfig.MIRROR_SHAPES.get(),
                WrightsClientConfig::setMirrorShapes)) {
            return true;
        }
        if (ghostY >= 0 && clickOnOff(mouseX, mouseY, ghostY, WrightsClientConfig.MIRROR_ON.get(),
                WrightsClientConfig::setMirrorOn)) {
            return true;
        }
        if (replaceY >= 0 && clickOnOff(mouseX, mouseY, replaceY, WrightsClientConfig.mirrorReplace(),
                WrightsClientConfig::setMirrorReplace)) {
            return true;
        }
        if (mineY >= 0 && clickOnOff(mouseX, mouseY, mineY, WrightsClientConfig.mirrorMine(), on -> {
            WrightsClientConfig.setMirrorMine(on);
            rebuild();
        })) {
            return true;
        }
        if (mineModeY >= 0 && clickPair(mouseX, mouseY, mineModeY,
                () -> WrightsClientConfig.setMirrorMineAny(false),
                () -> WrightsClientConfig.setMirrorMineAny(true))) {
            return true;
        }
        if (mineVoidY >= 0 && clickOnOff(mouseX, mouseY, mineVoidY, WrightsClientConfig.mirrorMineVoid(),
                WrightsClientConfig::setMirrorMineVoid)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickPair(double mouseX, double mouseY, int y, Runnable left, Runnable right) {
        int gap = GAP;
        int choiceW = (panelW - PAD * 2 - gap) / 2;
        int choiceX = panelX + PAD;
        int rightW = panelW - PAD * 2 - choiceW - gap;
        if (hit(mouseX, mouseY, choiceX, y, choiceW, ROW)) {
            left.run();
            return true;
        }
        if (hit(mouseX, mouseY, choiceX + choiceW + gap, y, rightW, ROW)) {
            right.run();
            return true;
        }
        return false;
    }

    private boolean clickOnOff(double mouseX, double mouseY, int y, boolean current, java.util.function.Consumer<Boolean> set) {
        int gap = GAP;
        int choiceW = Math.max(28, Math.min(44, (panelW - PAD * 2 - gap) / 4));
        int onX = panelX + panelW - PAD - choiceW;
        int offX = onX - gap - choiceW;
        if (hit(mouseX, mouseY, offX, y, choiceW, ROW)) {
            if (current) {
                set.accept(false);
            }
            return true;
        }
        if (hit(mouseX, mouseY, onX, y, choiceW, ROW)) {
            if (!current) {
                set.accept(true);
            }
            return true;
        }
        return false;
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void rebuild() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new MirrorTweakScreen());
        }
    }

    private void nudgeCopies(int dir) {
        int step = WrightsUi.nudgeStep() * dir;
        int next = WrightsClientConfig.MIRROR_PATTERN.get() + step;
        int max = Math.max(1, WrightsConfig.maxMirrorCopies());
        WrightsClientConfig.setMirrorPattern(Math.max(1, Math.min(max, next)));
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, STEP, ROW, Component.literal(label), button -> action.run()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
