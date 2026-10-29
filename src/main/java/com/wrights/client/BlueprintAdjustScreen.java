package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
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
    private WrightsButton buildButton;
    private WrightsButton resetButton;
    private boolean suppress;
    private String savedKey;
    private int modelX;
    private int modelY;
    private int modelW;
    private int modelH;
    private int fluidX;
    private int fluidY;
    private boolean spinning;
    private float yaw = 35.0F;
    private float pitch = 28.0F;
    private BlockPos previewA;
    private BlockPos previewB;
    private boolean previewFluids;
    private boolean previewReady;
    private SelectionPreview.Shot preview = SelectionPreview.Shot.empty();

    public BlueprintAdjustScreen() {
        super(Component.translatable("screen.wrights.adjust"));
    }

    @Override
    protected void init() {
        panelW = 392;
        panelH = 268;
        panelX = Math.max(8, (this.width - panelW) / 2);
        panelY = Math.max(6, (this.height - panelH) / 2);
        nameBox = box(panelX + 12, panelY + 28, 190, 16, 48, false);
        xBox = number(panelX + 32, panelY + 64, true);
        yBox = number(panelX + 32, panelY + 82, true);
        zBox = number(panelX + 32, panelY + 100, true);
        sxBox = number(panelX + 32, panelY + 136, false);
        syBox = number(panelX + 32, panelY + 154, false);
        szBox = number(panelX + 32, panelY + 172, false);
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
        saveButton = new WrightsButton(panelX + 12, panelY + 230, 60, 18, Component.translatable("screen.wrights.save"), button -> save(false));
        addRenderableWidget(saveButton);
        buildButton = new WrightsButton(panelX + 76, panelY + 230, 60, 18, Component.translatable("screen.wrights.send"), button -> build());
        addRenderableWidget(buildButton);
        addRenderableWidget(new WrightsButton(panelX + 140, panelY + 230, 66, 18, Component.translatable("screen.wrights.cancel"), button -> cancel(), true));
        refresh();
    }

    public void refresh() {
        if (nameBox == null) {
            return;
        }
        pushFields();
        if (resetButton != null) {
            resetButton.setMessage(Component.translatable(resetKey()));
        }
        updateSaveButton();
    }

    public void onSaveFinished(boolean saved) {
        savedKey = saved ? fingerprint() : null;
        updateSaveButton();
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
        graphics.drawString(this.font, Component.translatable("screen.wrights.size_label"), panelX + 12, panelY + 124, WrightsUi.BLUE, false);
        axis(graphics, panelY + 66, "X");
        axis(graphics, panelY + 84, "Y");
        axis(graphics, panelY + 102, "Z");
        axis(graphics, panelY + 138, "X");
        axis(graphics, panelY + 156, "Y");
        axis(graphics, panelY + 174, "Z");
        renderModel(graphics);
        if (WrightsClientState.cornerA != null && WrightsClientState.cornerB != null) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(
                    "screen.wrights.volume",
                    BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 0),
                    BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 1),
                    BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 2),
                    BoxEdits.volume(WrightsClientState.cornerA, WrightsClientState.cornerB),
                    WrightsClientState.solidSelection()).getString(), 198), panelX + 12, panelY + 192, WrightsUi.MUTED, false);
        } else {
            graphics.drawString(this.font, Component.translatable("hud.wrights.capture"), panelX + 12, panelY + 192, WrightsUi.MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void nudge(int dx, int dy, int dz) {
        if (saving() || WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        BlockPos[] next = BoxEdits.move(WrightsClientState.cornerA, WrightsClientState.cornerB, dx, dy, dz);
        apply(next[0], next[1]);
    }

    private void grow(int axis, int direction) {
        if (saving() || WrightsClientState.cornerA == null || WrightsClientState.cornerB == null || direction == 0) {
            return;
        }
        int current = BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, axis);
        int target = WrightsClientState.stepped(current, direction, 1, WrightsConfig.MAX_AXIS.getAsInt());
        if (target == current) {
            return;
        }
        BlockPos[] next = fitSize(axis, current, target);
        if (next != null) {
            apply(next[0], next[1]);
        }
    }

    private BlockPos[] fitSize(int axis, int current, int target) {
        BlockPos[] exact = BoxEdits.resize(WrightsClientState.cornerA, WrightsClientState.cornerB, axis, target - current);
        if (acceptable(exact[0], exact[1])) {
            return exact;
        }
        if (target < current) {
            return null;
        }
        int low = current + 1;
        int high = target - 1;
        BlockPos[] best = null;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            BlockPos[] trial = BoxEdits.resize(WrightsClientState.cornerA, WrightsClientState.cornerB, axis, mid - current);
            if (acceptable(trial[0], trial[1])) {
                best = trial;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static boolean acceptable(BlockPos first, BlockPos second) {
        return BoxEdits.inWorld(first) && BoxEdits.inWorld(second) && BoxEdits.fits(first, second);
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
        ensurePreview();
        updateSaveButton();
    }

    private void onFields() {
        if (suppress || saving()) {
            return;
        }
        WrightsClientState.name = nameBox.getValue();
        if (WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            WrightsClientState.sendUpdate();
            updateSaveButton();
            return;
        }
        Integer x = parse(xBox);
        Integer y = parse(yBox);
        Integer z = parse(zBox);
        Integer sx = parse(sxBox);
        Integer sy = parse(syBox);
        Integer sz = parse(szBox);
        if (x == null || y == null || z == null || sx == null || sy == null || sz == null) {
            updateSaveButton();
            return;
        }
        sx = Math.max(1, sx);
        sy = Math.max(1, sy);
        sz = Math.max(1, sz);
        BlockPos[] box = BoxEdits.fromMinAndSize(x, y, z, sx, sy, sz);
        if (!BoxEdits.inWorld(box[0]) || !BoxEdits.inWorld(box[1]) || !BoxEdits.fits(box[0], box[1])) {
            updateSaveButton();
            return;
        }
        if (box[0].equals(WrightsClientState.cornerA) && box[1].equals(WrightsClientState.cornerB)) {
            WrightsClientState.sendUpdate();
            updateSaveButton();
            return;
        }
        WrightsClientState.cornerA = box[0];
        WrightsClientState.cornerB = box[1];
        WrightsClientState.sendUpdate();
        ensurePreview();
        updateSaveButton();
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

    private void save(boolean buildAfter) {
        if (saving() || WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        if (!buildAfter && savedKey != null && savedKey.equals(fingerprint())) {
            return;
        }
        WrightsClientState.name = nameBox.getValue();
        WrightsClientState.markSaving();
        updateSaveButton();
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureConfirm(nameBox.getValue(), WrightsClientConfig.COPY_FLUIDS.get(), buildAfter));
    }

    private void build() {
        if (saving() || WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        if (savedKey != null && savedKey.equals(fingerprint()) && WrightsClientState.editingId != null) {
            PacketDistributor.sendToServer(new BlueprintPayloads.BeginBuild(WrightsClientState.editingId));
            return;
        }
        save(true);
    }

    private boolean saving() {
        return WrightsClientState.mode == WrightsClientState.Mode.SAVING;
    }

    private String fingerprint() {
        String label = nameBox == null ? "" : nameBox.getValue();
        return label + "\n" + WrightsClientConfig.COPY_FLUIDS.get() + "\n" + WrightsClientState.cornerA + "\n" + WrightsClientState.cornerB;
    }

    private void updateSaveButton() {
        if (saveButton == null) {
            return;
        }
        boolean corners = WrightsClientState.cornerA != null && WrightsClientState.cornerB != null;
        boolean clean = corners && savedKey != null && savedKey.equals(fingerprint());
        if (saving()) {
            saveButton.active = false;
            saveButton.setPicked(false);
            saveButton.setMessage(Component.translatable("screen.wrights.saving_short"));
        } else if (clean) {
            saveButton.active = false;
            saveButton.setPicked(true);
            saveButton.setMessage(Component.translatable("screen.wrights.saved"));
        } else {
            saveButton.active = corners;
            saveButton.setPicked(false);
            saveButton.setMessage(Component.translatable("screen.wrights.save"));
        }
        if (buildButton != null) {
            buildButton.active = corners && !saving();
        }
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
        return box(x, y, width, height, max, numeric, true);
    }

    private EditBox box(int x, int y, int width, int height, int max, boolean numeric, boolean signed) {
        EditBox box = new EditBox(this.font, x, y, width, height, Component.empty());
        box.setMaxLength(max);
        if (numeric) {
            box.setFilter(text -> text.matches(signed ? "-?\\d{0,7}" : "\\d{0,7}"));
            box.setResponder(value -> onFields());
        } else {
            box.setResponder(value -> {
                onFields();
                updateSaveButton();
            });
        }
        addRenderableWidget(box);
        return box;
    }

    private EditBox number(int x, int y, boolean signed) {
        return box(x, y, 78, 14, 8, true, signed);
    }

    private void step(int x, int y, String label, Runnable action) {
        addRenderableWidget(new WrightsButton(x, y, 16, 14, Component.literal(label), button -> action.run()));
    }

    private void axis(GuiGraphics graphics, int y, String label) {
        graphics.drawString(this.font, label, panelX + 134, y, WrightsUi.MUTED, false);
    }

    private void renderModel(GuiGraphics graphics) {
        modelX = panelX + 222;
        modelY = panelY + 28;
        modelW = panelW - 234;
        modelH = 176;
        fluidX = modelX;
        fluidY = modelY + modelH + 14;
        WrightsUi.inset(graphics, modelX - 4, modelY - 4, modelW + 8, modelH + 8);
        ensurePreview();
        if (!spinning) {
            yaw += 0.35F;
        }
        if (preview.concealed()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.preview_hidden"), modelX + modelW / 2, modelY + modelH / 2, WrightsUi.MUTED);
        } else if (!preview.ghosts().isEmpty()) {
            BlueprintModelView.render(graphics, modelX, modelY, modelW, modelH, preview.ghosts(), preview.sizeX(), preview.sizeY(), preview.sizeZ(), yaw, pitch);
            graphics.drawCenteredString(this.font, Component.translatable("menu.wrights.spin"), modelX + modelW / 2, modelY + modelH - 10, WrightsUi.MUTED);
        } else if (WrightsClientState.cornerA != null && WrightsClientState.cornerB != null) {
            graphics.drawCenteredString(this.font, Component.translatable("message.wrights.copy_empty"), modelX + modelW / 2, modelY + modelH / 2, WrightsUi.MUTED);
        }
        boolean fluids = WrightsClientConfig.COPY_FLUIDS.get();
        graphics.fill(fluidX, fluidY, fluidX + 12, fluidY + 12, 0xFF18222C);
        WrightsUi.border(graphics, fluidX, fluidY, 12, 12, fluids ? WrightsUi.COPPER : 0xFF3D4E5E);
        if (fluids) {
            graphics.fill(fluidX + 3, fluidY + 3, fluidX + 9, fluidY + 9, WrightsUi.COPPER);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.copy_fluids"), fluidX + 16, fluidY + 2, WrightsUi.TEXT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("screen.wrights.copy_fluids_hint").getString(), modelW - 4), fluidX, fluidY + 16, WrightsUi.MUTED, false);
    }

    private void ensurePreview() {
        BlockPos first = WrightsClientState.cornerA;
        BlockPos second = WrightsClientState.cornerB;
        boolean fluids = WrightsClientConfig.COPY_FLUIDS.get();
        if (previewReady && same(previewA, first) && same(previewB, second) && previewFluids == fluids) {
            return;
        }
        previewA = first;
        previewB = second;
        previewFluids = fluids;
        previewReady = true;
        preview = this.minecraft == null
                ? SelectionPreview.Shot.empty()
                : SelectionPreview.capture(this.minecraft.level, first, second, fluids);
        WrightsClientState.showCapturePreview(preview.ghosts(), preview.concealed());
    }

    private static boolean same(BlockPos left, BlockPos right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean insideModel(double mouseX, double mouseY) {
        return mouseX >= modelX && mouseX < modelX + modelW && mouseY >= modelY && mouseY < modelY + modelH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= fluidX && mouseX < fluidX + modelW && mouseY >= fluidY && mouseY < fluidY + 28) {
            if (saving()) {
                return true;
            }
            WrightsClientConfig.setCopyFluids(!WrightsClientConfig.COPY_FLUIDS.get());
            previewReady = false;
            ensurePreview();
            updateSaveButton();
            return true;
        }
        if (button == 0 && insideModel(mouseX, mouseY)) {
            spinning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (spinning && button == 0) {
            yaw += (float) dragX * 0.75F;
            pitch = Mth.clamp(pitch + (float) dragY * 0.45F, -70.0F, 70.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            spinning = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
