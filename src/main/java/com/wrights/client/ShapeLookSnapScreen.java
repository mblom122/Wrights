package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Edit the locked look-mode snap cell: nudge axes with modifier step sizes,
 * type coordinates, confirm, cancel, or clear so the preview follows the mouse again.
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

    public ShapeLookSnapScreen() {
        super(Component.translatable("screen.wrights.shape_look_snap_title"));
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(320, this.width);
        panelH = WrightsUi.fitH(210, this.height);
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

        xBox = number(panelX + 48, panelY + 56);
        yBox = number(panelX + 48, panelY + 74);
        zBox = number(panelX + 48, panelY + 92);
        step(panelX + 28, panelY + 56, "-", () -> nudge(-1, 0, 0));
        step(panelX + 112, panelY + 56, "+", () -> nudge(1, 0, 0));
        step(panelX + 28, panelY + 74, "-", () -> nudge(0, -1, 0));
        step(panelX + 112, panelY + 74, "+", () -> nudge(0, 1, 0));
        step(panelX + 28, panelY + 92, "-", () -> nudge(0, 0, -1));
        step(panelX + 112, panelY + 92, "+", () -> nudge(0, 0, 1));
        suppress = true;
        set(xBox, original.getX());
        set(yBox, original.getY());
        set(zBox, original.getZ());
        suppress = false;

        int gap = 6;
        int btnW = (panelW - 32 - gap * 2) / 3;
        addRenderableWidget(new WrightsButton(
                panelX + 16,
                panelY + panelH - 36,
                btnW,
                20,
                Component.translatable("screen.wrights.confirm"),
                button -> confirm()));
        addRenderableWidget(new WrightsButton(
                panelX + 16 + btnW + gap,
                panelY + panelH - 36,
                btnW,
                20,
                Component.translatable("screen.wrights.cancel"),
                button -> cancel(),
                true));
        addRenderableWidget(new WrightsButton(
                panelX + 16 + (btnW + gap) * 2,
                panelY + panelH - 36,
                btnW,
                20,
                Component.translatable("screen.wrights.shape_look_snap_clear"),
                button -> clearSnap(),
                true));
    }

    @Override
    public void onClose() {
        if (!cleared && original != null && WrightsClientState.shapeLookSnapActive()) {
            // Escape acts like cancel: restore the position from when the menu opened.
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        int y = panelY + 26;
        for (var line : this.font.split(Component.translatable("screen.wrights.shape_look_snap_hint"), panelW - 32)) {
            graphics.drawString(this.font, line, panelX + 16, y, WrightsUi.MUTED, false);
            y += 10;
        }
        graphics.drawString(this.font, "X", panelX + 16, panelY + 59, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Y", panelX + 16, panelY + 77, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Z", panelX + 16, panelY + 95, WrightsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.shape_look_snap_steps"),
                panelX + 16, panelY + 116, WrightsUi.MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
