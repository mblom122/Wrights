package com.autobots.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.blueprint.BoxEdits;
import com.autobots.network.BlueprintPayloads;

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
    private AutobotsButton saveButton;
    private AutobotsButton resetButton;
    private boolean suppress;

    public BlueprintAdjustScreen() {
        super(Component.translatable("screen.autobots.adjust"));
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
        resetButton = new AutobotsButton(panelX + 12, panelY + 208, 190, 16, Component.translatable(resetKey()), button -> reset());
        addRenderableWidget(resetButton);
        saveButton = new AutobotsButton(panelX + 12, panelY + 230, 118, 18, Component.translatable("screen.autobots.save"), button -> save());
        addRenderableWidget(saveButton);
        addRenderableWidget(new AutobotsButton(panelX + 134, panelY + 230, 68, 18, Component.translatable("screen.autobots.cancel"), button -> cancel(), true));
        refresh();
    }

    public void refresh() {
        if (nameBox == null) {
            return;
        }
        pushFields();
        saveButton.active = AutobotsClientState.cornerA != null && AutobotsClientState.cornerB != null;
        if (resetButton != null) {
            resetButton.setMessage(Component.translatable(resetKey()));
        }
    }

    private static String resetKey() {
        return AutobotsClientState.captureStyle == AutobotsClientState.CaptureStyle.CORNERS
                ? "screen.autobots.reset"
                : "screen.autobots.reset_box";
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AutobotsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component title = Component.translatable(AutobotsClientState.editingId == null ? "screen.autobots.adjust" : "screen.autobots.adjust_edit");
        graphics.drawString(this.font, title, panelX + 14, panelY + 8, AutobotsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.location"), panelX + 12, panelY + 52, AutobotsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.autobots.border"), panelX + 12, panelY + 124, AutobotsUi.BLUE, false);
        axis(graphics, panelY + 66, "X");
        axis(graphics, panelY + 84, "Y");
        axis(graphics, panelY + 102, "Z");
        axis(graphics, panelY + 138, "X");
        axis(graphics, panelY + 156, "Y");
        axis(graphics, panelY + 174, "Z");
        if (AutobotsClientState.cornerA != null && AutobotsClientState.cornerB != null) {
            graphics.drawString(this.font, Component.translatable(
                    "screen.autobots.volume",
                    BoxEdits.span(AutobotsClientState.cornerA, AutobotsClientState.cornerB, 0),
                    BoxEdits.span(AutobotsClientState.cornerA, AutobotsClientState.cornerB, 1),
                    BoxEdits.span(AutobotsClientState.cornerA, AutobotsClientState.cornerB, 2),
                    BoxEdits.volume(AutobotsClientState.cornerA, AutobotsClientState.cornerB),
                    AutobotsClientState.solidSelection()), panelX + 12, panelY + 192, AutobotsUi.MUTED, false);
        } else {
            graphics.drawString(this.font, Component.translatable("hud.autobots.capture"), panelX + 12, panelY + 192, AutobotsUi.MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void nudge(int dx, int dy, int dz) {
        if (AutobotsClientState.cornerA == null || AutobotsClientState.cornerB == null) {
            return;
        }
        BlockPos[] next = BoxEdits.move(AutobotsClientState.cornerA, AutobotsClientState.cornerB, dx, dy, dz);
        apply(next[0], next[1]);
    }

    private void grow(int axis, int delta) {
        if (AutobotsClientState.cornerA == null || AutobotsClientState.cornerB == null) {
            return;
        }
        BlockPos[] next = BoxEdits.resize(AutobotsClientState.cornerA, AutobotsClientState.cornerB, axis, delta);
        apply(next[0], next[1]);
    }

    private void apply(BlockPos first, BlockPos second) {
        if (!BoxEdits.inWorld(first) || !BoxEdits.inWorld(second) || !BoxEdits.fits(first, second)) {
            return;
        }
        AutobotsClientState.name = nameBox.getValue();
        AutobotsClientState.cornerA = first;
        AutobotsClientState.cornerB = second;
        pushFields();
        AutobotsClientState.sendUpdate();
    }

    private void onFields() {
        if (suppress) {
            return;
        }
        AutobotsClientState.name = nameBox.getValue();
        if (AutobotsClientState.cornerA == null || AutobotsClientState.cornerB == null) {
            AutobotsClientState.sendUpdate();
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
        if (box[0].equals(AutobotsClientState.cornerA) && box[1].equals(AutobotsClientState.cornerB)) {
            AutobotsClientState.sendUpdate();
            return;
        }
        AutobotsClientState.cornerA = box[0];
        AutobotsClientState.cornerB = box[1];
        AutobotsClientState.sendUpdate();
    }

    private void pushFields() {
        suppress = true;
        if (!nameBox.isFocused()) {
            nameBox.setValue(AutobotsClientState.name == null ? "Blueprint" : AutobotsClientState.name);
        }
        BlockPos first = AutobotsClientState.cornerA;
        BlockPos second = AutobotsClientState.cornerB;
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
        if (AutobotsClientState.cornerA == null || AutobotsClientState.cornerB == null) {
            return;
        }
        AutobotsClientState.name = nameBox.getValue();
        AutobotsClientState.markSaving();
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureConfirm(nameBox.getValue()));
        this.minecraft.setScreen(null);
    }

    private void cancel() {
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureCancel());
        AutobotsClientState.clearLocal();
        this.minecraft.setScreen(null);
    }

    private void reset() {
        AutobotsClientState.name = nameBox.getValue();
        if (AutobotsClientState.captureStyle == AutobotsClientState.CaptureStyle.MOVE) {
            AutobotsClientState.captureStyle = AutobotsClientState.CaptureStyle.BOX;
        }
        AutobotsClientState.cornerA = null;
        AutobotsClientState.cornerB = null;
        AutobotsClientState.sendUpdate();
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
        addRenderableWidget(new AutobotsButton(x, y, 16, 14, Component.literal(label), button -> action.run()));
    }

    private void axis(GuiGraphics graphics, int y, String label) {
        graphics.drawString(this.font, label, panelX + 134, y, AutobotsUi.MUTED, false);
    }
}
