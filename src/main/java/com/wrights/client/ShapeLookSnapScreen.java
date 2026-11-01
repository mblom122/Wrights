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
        panelW = WrightsUi.fitW(220, this.width);
        panelH = WrightsUi.fitH(128, this.height);
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

        int row0 = panelY + 28;
        int row1 = panelY + 44;
        int row2 = panelY + 60;
        xBox = number(panelX + 40, row0);
        yBox = number(panelX + 40, row1);
        zBox = number(panelX + 40, row2);
        step(panelX + 22, row0, "-", () -> nudge(-1, 0, 0));
        step(panelX + 100, row0, "+", () -> nudge(1, 0, 0));
        step(panelX + 22, row1, "-", () -> nudge(0, -1, 0));
        step(panelX + 100, row1, "+", () -> nudge(0, 1, 0));
        step(panelX + 22, row2, "-", () -> nudge(0, 0, -1));
        step(panelX + 100, row2, "+", () -> nudge(0, 0, 1));
        suppress = true;
        set(xBox, original.getX());
        set(yBox, original.getY());
        set(zBox, original.getZ());
        suppress = false;

        int gap = 4;
        int btnW = (panelW - 24 - gap * 2) / 3;
        int btnY = panelY + panelH - 28;
        addRenderableWidget(new WrightsButton(
                panelX + 12,
                btnY,
                btnW,
                16,
                Component.translatable("screen.wrights.confirm"),
                button -> confirm()));
        addRenderableWidget(new WrightsButton(
                panelX + 12 + btnW + gap,
                btnY,
                btnW,
                16,
                Component.translatable("screen.wrights.cancel"),
                button -> cancel(),
                true));
        addRenderableWidget(new WrightsButton(
                panelX + 12 + (btnW + gap) * 2,
                btnY,
                btnW,
                16,
                Component.translatable("screen.wrights.shape_look_snap_clear"),
                button -> clearSnap(),
                true));
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
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tip = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 12, panelY + 8, WrightsUi.COPPER, false);
        drawHelp(graphics, mouseX, mouseY, panelX + 12 + this.font.width(this.title) + 4, panelY + 6,
                "screen.wrights.shape_look_snap_hint");
        graphics.drawString(this.font, "X", panelX + 12, panelY + 31, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Y", panelX + 12, panelY + 47, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Z", panelX + 12, panelY + 63, WrightsUi.MUTED, false);
        drawHelp(graphics, mouseX, mouseY, panelX + 120, panelY + 44,
                "screen.wrights.shape_look_snap_steps");
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

    private EditBox number(int x, int y) {
        EditBox box = new EditBox(this.font, x, y, 52, 12, Component.empty());
        box.setMaxLength(8);
        box.setFilter(text -> text.matches("-?\\d{0,7}"));
        box.setResponder(value -> onFields());
        addRenderableWidget(box);
        return box;
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, 14, 12, Component.literal(label), button -> action.run()));
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
