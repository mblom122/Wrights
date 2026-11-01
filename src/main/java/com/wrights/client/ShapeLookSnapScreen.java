package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Compact editor for the locked look-mode snap cell: nudge axes with modifier
 * step sizes, type coordinates, confirm, cancel, or clear so the preview follows the mouse again.
 */
public class ShapeLookSnapScreen extends Screen {
    private static final int PAD = 8;
    private static final int ROW = 14;
    private static final int GAP = 4;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private boolean suppress;
    private BlockPos original;
    private boolean cleared;
    private Component tip;

    public ShapeLookSnapScreen() {
        super(Component.translatable("screen.wrights.shape_look_snap_title"));
    }

    @Override
    protected void init() {
        // Left: X/Y/Z label, − value +, gap, then Clear / Cancel / Confirm (text-sized).
        int stepW = 14;
        int fieldW = 48;
        int labelW = 10;
        int colGap = 8;
        Component clear = Component.translatable("screen.wrights.shape_look_snap_clear");
        Component cancel = Component.translatable("screen.wrights.cancel");
        Component confirm = Component.translatable("screen.wrights.confirm");
        int actionW = Math.max(this.font.width(clear), Math.max(this.font.width(cancel), this.font.width(confirm))) + 12;
        int contentW = PAD + labelW + 2 + stepW + 2 + fieldW + 2 + stepW + colGap + actionW + PAD;
        panelW = WrightsUi.fitW(contentW, this.width);
        panelH = PAD + 12 + GAP + ROW * 3 + GAP * 2 + PAD;
        panelH = WrightsUi.fitH(panelH, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        BlockPos start = WrightsClientState.shapeLookSnapPos;
        if (start == null) {
            start = WrightsClientState.shapeLookTarget();
        }
        if (start == null) {
            start = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.blockPosition()
                    : BlockPos.ZERO;
        }
        original = start.immutable();
        WrightsClientState.setShapeLookSnapTarget(original);
        cleared = false;

        int row0 = panelY + PAD + 12 + GAP;
        int row1 = row0 + ROW + GAP;
        int row2 = row1 + ROW + GAP;
        int minusX = panelX + PAD + labelW + 2;
        int fieldX = minusX + stepW + 2;
        int plusX = fieldX + fieldW + 2;
        int actionX = plusX + stepW + colGap;

        xBox = number(fieldX, row0, fieldW);
        yBox = number(fieldX, row1, fieldW);
        zBox = number(fieldX, row2, fieldW);
        step(minusX, row0, "-", () -> nudge(-1, 0, 0));
        step(plusX, row0, "+", () -> nudge(1, 0, 0));
        step(minusX, row1, "-", () -> nudge(0, -1, 0));
        step(plusX, row1, "+", () -> nudge(0, 1, 0));
        step(minusX, row2, "-", () -> nudge(0, 0, -1));
        step(plusX, row2, "+", () -> nudge(0, 0, 1));

        addRenderableWidget(new WrightsButton(actionX, row0, actionW, ROW, clear, button -> clearSnap(), true));
        addRenderableWidget(new WrightsButton(actionX, row1, actionW, ROW, cancel, button -> cancel(), true));
        addRenderableWidget(new WrightsButton(actionX, row2, actionW, ROW, confirm, button -> confirm()));

        suppress = true;
        set(xBox, original.getX());
        set(yBox, original.getY());
        set(zBox, original.getZ());
        suppress = false;
    }

    @Override
    public void onClose() {
        if (!cleared && original != null && WrightsClientState.shapeLookSnapActive()) {
            WrightsClientState.setShapeLookSnapTarget(original);
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Light veil + translucent panel so the live shape preview stays readable underneath.
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
        int helpX = panelX + PAD + this.font.width(this.title) + 4;
        drawHelp(graphics, mouseX, mouseY, helpX, titleY - 2, "screen.wrights.shape_look_snap_hint");
        drawHelp(graphics, mouseX, mouseY, helpX + 14, titleY - 2, "screen.wrights.shape_look_snap_steps");

        int row0 = panelY + PAD + 12 + GAP;
        int row1 = row0 + ROW + GAP;
        int row2 = row1 + ROW + GAP;
        graphics.drawString(this.font, "X", panelX + PAD, row0 + 3, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Y", panelX + PAD, row1 + 3, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Z", panelX + PAD, row2 + 3, WrightsUi.MUTED, false);
        if (tip != null) {
            graphics.renderTooltip(this.font, this.font.split(tip, 220), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    private void confirm() {
        BlockPos pos = readPos();
        if (pos != null) {
            WrightsClientState.setShapeLookSnapTarget(pos);
            original = pos.immutable();
        }
        cleared = false;
        this.minecraft.setScreen(null);
    }

    private void cancel() {
        if (original != null) {
            WrightsClientState.setShapeLookSnapTarget(original);
        }
        cleared = false;
        this.minecraft.setScreen(null);
    }

    private void clearSnap() {
        WrightsClientState.clearShapeLookSnap();
        cleared = true;
        this.minecraft.setScreen(null);
    }

    private void nudge(int dx, int dy, int dz) {
        BlockPos pos = readPos();
        if (pos == null) {
            return;
        }
        int step = nudgeStep();
        apply(pos.offset(dx * step, dy * step, dz * step));
    }

    /** Plain 1, Ctrl 5, Shift 10, Ctrl+Shift 50, Alt 100. */
    private static int nudgeStep() {
        boolean control = hasControlDown();
        boolean shift = hasShiftDown();
        boolean alt = hasAltDown();
        if (control && shift) {
            return 50;
        }
        if (alt) {
            return 100;
        }
        if (control) {
            return 5;
        }
        if (shift) {
            return 10;
        }
        return 1;
    }

    private void apply(BlockPos pos) {
        suppress = true;
        set(xBox, pos.getX());
        set(yBox, pos.getY());
        set(zBox, pos.getZ());
        suppress = false;
        WrightsClientState.setShapeLookSnapTarget(pos);
    }

    private void onFields() {
        if (suppress) {
            return;
        }
        BlockPos pos = readPos();
        if (pos != null) {
            WrightsClientState.setShapeLookSnapTarget(pos);
        }
    }

    private BlockPos readPos() {
        Integer x = parse(xBox);
        Integer y = parse(yBox);
        Integer z = parse(zBox);
        if (x == null || y == null || z == null) {
            return null;
        }
        return new BlockPos(x, y, z);
    }

    private EditBox number(int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, ROW, Component.empty());
        box.setMaxLength(8);
        box.setFilter(text -> text.matches("-?\\d{0,7}"));
        box.setResponder(value -> onFields());
        addRenderableWidget(box);
        return box;
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, 14, ROW, Component.literal(label), button -> action.run()));
    }

    private void set(EditBox box, int value) {
        box.setValue(Integer.toString(value));
    }

    private Integer parse(EditBox box) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
