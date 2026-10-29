package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.blueprint.BoxEdits;
import com.wrights.network.BlueprintPayloads;

public class BlueprintAdjustScreen extends Screen {
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox sxBox;
    private EditBox syBox;
    private EditBox szBox;
    private WrightsButton saveButton;
    private WrightsButton resetButton;
    private boolean suppress;

    public BlueprintAdjustScreen() {
        super(Component.translatable("screen.wrights.adjust"));
    }

    @Override
    protected void init() {
        panelW = 214;
        panelH = 268;
        panelX = Math.max(8, (this.width - panelW) / 2);
        panelY = Math.max(6, (this.height - panelH) / 2);
        nameBox = box(panelX + 12, panelY + 28, 190, 16, 48, false);
        xBox = number(panelX + 32, panelY + 64);
        yBox = number(panelX + 32, panelY + 82);
        zBox = number(panelX + 32, panelY + 100);
        sxBox = number(panelX + 32, panelY + 136);
        syBox = number(panelX + 32, panelY + 154);
        szBox = number(panelX + 32, panelY + 172);
        step(panelX + 12, panelY + 64, "-", () -> nudge(-1, 0, 0));
        step(panelX + 114, panelY + 64, "+", () -> nudge(1, 0, 0));
        step(panelX + 12, panelY + 82, "-", () -> nudge(0, -1, 0));
        step(panelX + 114, panelY + 82, "+", () -> nudge(0, 1, 0));
        step(panelX + 12, panelY + 100, "-", () -> nudge(0, 0, -1));
        step(panelX + 114, panelY + 100, "+", () -> nudge(0, 0, 1));
        step(panelX + 12, panelY + 136, "-", () -> grow(0, -1));
        step(panelX + 114, panelY + 136, "+", () -> grow(0, 1));
        step(panelX + 12, panelY + 154, "-", () -> grow(1, -1));
        step(panelX + 114, panelY + 154, "+", () -> grow(1, 1));
        step(panelX + 12, panelY + 172, "-", () -> grow(2, -1));
        step(panelX + 114, panelY + 172, "+", () -> grow(2, 1));
        resetButton = new WrightsButton(panelX + 12, panelY + 208, 190, 16, Component.translatable(resetKey()), button -> reset());
        addRenderableWidget(resetButton);
        saveButton = new WrightsButton(panelX + 12, panelY + 230, 118, 18, Component.translatable("screen.wrights.save"), button -> save());
        addRenderableWidget(saveButton);
        addRenderableWidget(new WrightsButton(panelX + 134, panelY + 230, 68, 18, Component.translatable("screen.wrights.cancel"), button -> cancel(), true));
        refresh();
    }

    public void refresh() {
        if (nameBox == null) {
            return;
        }
        pushFields();
        saveButton.active = WrightsClientState.cornerA != null && WrightsClientState.cornerB != null;
        if (resetButton != null) {
            resetButton.setMessage(Component.translatable(resetKey()));
        }
    }

    private static String resetKey() {
        return WrightsClientState.captureStyle == WrightsClientState.CaptureStyle.CORNERS
                ? "screen.wrights.reset"
                : "screen.wrights.reset_box";
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component title = Component.translatable(WrightsClientState.editingId == null ? "screen.wrights.adjust" : "screen.wrights.adjust_edit");
        graphics.drawString(this.font, title, panelX + 14, panelY + 8, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.location"), panelX + 12, panelY + 52, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.border"), panelX + 12, panelY + 124, WrightsUi.BLUE, false);
        axis(graphics, panelY + 66, "X");
        axis(graphics, panelY + 84, "Y");
        axis(graphics, panelY + 102, "Z");
        axis(graphics, panelY + 138, "X");
        axis(graphics, panelY + 156, "Y");
        axis(graphics, panelY + 174, "Z");
        if (WrightsClientState.cornerA != null && WrightsClientState.cornerB != null) {
            graphics.drawString(this.font, Component.translatable(
                    "screen.wrights.volume",
                    BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 0),
                    BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 1),
                    BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 2),
                    BoxEdits.volume(WrightsClientState.cornerA, WrightsClientState.cornerB),
                    WrightsClientState.solidSelection()), panelX + 12, panelY + 192, WrightsUi.MUTED, false);
        } else {
            graphics.drawString(this.font, Component.translatable("hud.wrights.capture"), panelX + 12, panelY + 192, WrightsUi.MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void nudge(int dx, int dy, int dz) {
        if (WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        BlockPos[] next = BoxEdits.move(WrightsClientState.cornerA, WrightsClientState.cornerB, dx, dy, dz);
        apply(next[0], next[1]);
    }

    private void grow(int axis, int delta) {
        if (WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        BlockPos[] next = BoxEdits.resize(WrightsClientState.cornerA, WrightsClientState.cornerB, axis, delta);
        apply(next[0], next[1]);
    }

    private void apply(BlockPos first, BlockPos second) {
        if (!BoxEdits.inWorld(first) || !BoxEdits.inWorld(second) || !BoxEdits.fits(first, second)) {
            return;
        }
        WrightsClientState.name = nameBox.getValue();
        WrightsClientState.cornerA = first;
        WrightsClientState.cornerB = second;
        pushFields();
        WrightsClientState.sendUpdate();
    }

    private void onFields() {
        if (suppress) {
            return;
        }
        WrightsClientState.name = nameBox.getValue();
        if (WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            WrightsClientState.sendUpdate();
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
        BlockPos[] box = BoxEdits.fromMinAndSize(x, y, z, sx, sy, sz);
        if (!BoxEdits.inWorld(box[0]) || !BoxEdits.inWorld(box[1]) || !BoxEdits.fits(box[0], box[1])) {
            return;
        }
        if (box[0].equals(WrightsClientState.cornerA) && box[1].equals(WrightsClientState.cornerB)) {
            WrightsClientState.sendUpdate();
            return;
        }
        WrightsClientState.cornerA = box[0];
        WrightsClientState.cornerB = box[1];
        WrightsClientState.sendUpdate();
    }

    private void pushFields() {
        suppress = true;
        if (!nameBox.isFocused()) {
            nameBox.setValue(WrightsClientState.name == null ? "Blueprint" : WrightsClientState.name);
        }
        BlockPos first = WrightsClientState.cornerA;
        BlockPos second = WrightsClientState.cornerB;
        if (first != null) {
            BlockPos low = second == null ? first : BoxEdits.min(first, second);
            set(xBox, low.getX());
            set(yBox, low.getY());
            set(zBox, low.getZ());
            set(sxBox, second == null ? 1 : BoxEdits.span(first, second, 0));
            set(syBox, second == null ? 1 : BoxEdits.span(first, second, 1));
            set(szBox, second == null ? 1 : BoxEdits.span(first, second, 2));
        }
        suppress = false;
    }

    private void save() {
        if (WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        WrightsClientState.name = nameBox.getValue();
        WrightsClientState.markSaving();
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureConfirm(nameBox.getValue()));
        this.minecraft.setScreen(null);
    }

    private void cancel() {
        WrightsClientState.releaseSelection();
        WrightsClientState.sendUpdate();
        this.minecraft.setScreen(null);
    }

    private void reset() {
        WrightsClientState.name = nameBox.getValue();
        if (WrightsClientState.captureStyle == WrightsClientState.CaptureStyle.MOVE) {
            WrightsClientState.captureStyle = WrightsClientState.CaptureStyle.BOX;
        }
        WrightsClientState.cornerA = null;
        WrightsClientState.cornerB = null;
        WrightsClientState.sendUpdate();
        this.minecraft.setScreen(null);
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

    private EditBox box(int x, int y, int width, int height, int max, boolean numeric) {
        EditBox box = new EditBox(this.font, x, y, width, height, Component.empty());
        box.setMaxLength(max);
        if (numeric) {
            box.setFilter(text -> text.matches("-?\\d{0,7}"));
            box.setResponder(value -> onFields());
        } else {
            box.setResponder(value -> onFields());
        }
        addRenderableWidget(box);
        return box;
    }

    private EditBox number(int x, int y) {
        return box(x, y, 78, 14, 8, true);
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, 16, 14, Component.literal(label), button -> action.run()));
    }

    private void axis(GuiGraphics graphics, int y, String label) {
        graphics.drawString(this.font, label, panelX + 134, y, WrightsUi.MUTED, false);
    }
}
