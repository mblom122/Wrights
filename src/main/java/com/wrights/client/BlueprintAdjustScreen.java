package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintTransforms;
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
    private WrightsButton rotateLeftButton;
    private WrightsButton rotateRightButton;
    private WrightsButton flipHorizontalButton;
    private WrightsButton flipVerticalButton;
    private final List<BlueprintTransforms.Op> turns = new ArrayList<>();
    private boolean suppress;
    private String savedKey;
    private int modelX;
    private int modelY;
    private int modelW;
    private int modelH;
    private int fluidX;
    private int fluidY;
    private int nbtX;
    private int nbtY;
    private int settingsX;
    private int settingsY;
    private int allX;
    private int allY;
    private boolean spinning;
    private float yaw = 35.0F;
    private float pitch = 28.0F;
    private BlockPos previewA;
    private BlockPos previewB;
    private boolean previewFluids;
    private boolean previewNbt;
    private boolean previewReady;
    private String previewTurns = "";
    private SelectionPreview.Shot preview = SelectionPreview.Shot.empty();
    private Component helpTip;

    public BlueprintAdjustScreen() {
        super(Component.translatable("screen.wrights.adjust"));
    }

    @Override
    protected void init() {
        panelW = 392;
        panelH = 340;
        panelX = Math.max(8, (this.width - panelW) / 2);
        panelY = Math.max(4, (this.height - panelH) / 2);
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
        int turnRow = 93;
        int turnLeft = panelX + 12;
        int rotateY = panelY + 220;
        int flipY = panelY + 256;
        rotateLeftButton = new WrightsButton(turnLeft, rotateY, turnRow, 16, Component.translatable("screen.wrights.rotate_left"), button -> turn(BlueprintTransforms.Op.ROTATE_CCW));
        rotateRightButton = new WrightsButton(turnLeft + turnRow + 4, rotateY, turnRow, 16, Component.translatable("screen.wrights.rotate_right"), button -> turn(BlueprintTransforms.Op.ROTATE));
        flipHorizontalButton = new WrightsButton(turnLeft, flipY, turnRow, 16, Component.translatable("screen.wrights.flip_horizontal"), button -> turn(BlueprintTransforms.Op.FLIP_X));
        flipVerticalButton = new WrightsButton(turnLeft + turnRow + 4, flipY, turnRow, 16, Component.translatable("screen.wrights.flip_vertical"), button -> turn(BlueprintTransforms.Op.FLIP_Y));
        addRenderableWidget(rotateLeftButton);
        addRenderableWidget(rotateRightButton);
        addRenderableWidget(flipHorizontalButton);
        addRenderableWidget(flipVerticalButton);
        resetButton = new WrightsButton(panelX + 12, panelY + 280, 190, 16, Component.translatable(resetKey()), button -> reset());
        addRenderableWidget(resetButton);
        saveButton = new WrightsButton(panelX + 12, panelY + 306, 60, 18, Component.translatable("screen.wrights.save"), button -> save(false));
        addRenderableWidget(saveButton);
        buildButton = new WrightsButton(panelX + 76, panelY + 306, 60, 18, Component.translatable("screen.wrights.send"), button -> build());
        addRenderableWidget(buildButton);
        addRenderableWidget(new WrightsButton(panelX + 140, panelY + 306, 66, 18, Component.translatable("screen.wrights.cancel"), button -> cancel(), true));
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
        graphics.drawString(this.font, Component.translatable("screen.wrights.rotate"), panelX + 12, panelY + 208, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.flip"), panelX + 12, panelY + 244, WrightsUi.BLUE, false);
        axis(graphics, panelY + 66, "X");
        axis(graphics, panelY + 84, "Y");
        axis(graphics, panelY + 102, "Z");
        axis(graphics, panelY + 138, "X");
        axis(graphics, panelY + 156, "Y");
        axis(graphics, panelY + 174, "Z");
        helpTip = null;
        renderModel(graphics, mouseX, mouseY);
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
        if (helpTip != null) {
            graphics.renderTooltip(this.font, helpTip, mouseX, mouseY);
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
            nameBox.setValue(WrightsClientState.name == null ? "Blueprint 1" : WrightsClientState.name);
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
        PacketDistributor.sendToServer(new BlueprintPayloads.CaptureConfirm(
                nameBox.getValue(),
                WrightsClientConfig.COPY_FLUIDS.get(),
                WrightsClientConfig.COPY_NBT_BLOCKS.get(),
                settingsChosen(),
                allChosen(),
                buildAfter,
                turnBytes()));
    }

    private void turn(BlueprintTransforms.Op op) {
        if (saving() || WrightsClientState.cornerA == null || WrightsClientState.cornerB == null) {
            return;
        }
        BlueprintTransforms.Op undo = switch (op) {
            case ROTATE -> BlueprintTransforms.Op.ROTATE_CCW;
            case ROTATE_CCW -> BlueprintTransforms.Op.ROTATE;
            case FLIP_X, FLIP_Y, FLIP_Z -> op;
            default -> null;
        };
        if (undo != null && !turns.isEmpty() && turns.get(turns.size() - 1) == undo) {
            turns.remove(turns.size() - 1);
        } else if (turns.size() < 24) {
            turns.add(op);
        }
        ensurePreview();
        updateSaveButton();
    }

    private byte[] turnBytes() {
        byte[] bytes = new byte[turns.size()];
        for (int i = 0; i < turns.size(); i++) {
            bytes[i] = (byte) turns.get(i).ordinal();
        }
        return bytes;
    }

    private String turnKey() {
        StringBuilder key = new StringBuilder();
        for (BlueprintTransforms.Op op : turns) {
            key.append(op.ordinal()).append(',');
        }
        return key.toString();
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
        return label + "\n" + WrightsClientConfig.COPY_FLUIDS.get()
                + "\n" + WrightsClientConfig.COPY_NBT_BLOCKS.get()
                + "\n" + settingsChosen()
                + "\n" + allChosen()
                + "\n" + WrightsClientState.cornerA + "\n" + WrightsClientState.cornerB
                + "\n" + turnKey();
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
        boolean canTurn = corners && !saving();
        if (rotateLeftButton != null) {
            rotateLeftButton.active = canTurn;
            rotateRightButton.active = canTurn;
            flipHorizontalButton.active = canTurn;
            flipVerticalButton.active = canTurn;
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
        turns.clear();
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

    private void renderModel(GuiGraphics graphics, int mouseX, int mouseY) {
        modelX = panelX + 222;
        modelY = panelY + 28;
        modelW = panelW - 234;
        modelH = 176;
        fluidX = modelX;
        fluidY = modelY + modelH + 8;
        nbtX = modelX;
        nbtY = fluidY + 18;
        settingsX = modelX;
        settingsY = nbtY + 18;
        allX = modelX;
        allY = settingsY + 18;
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
        drawToggle(graphics, mouseX, mouseY, fluidX, fluidY, WrightsClientConfig.COPY_FLUIDS.get(), "screen.wrights.copy_fluids", "screen.wrights.copy_fluids_hint", true);
        drawToggle(graphics, mouseX, mouseY, nbtX, nbtY, WrightsClientConfig.COPY_NBT_BLOCKS.get(), "screen.wrights.copy_nbt_blocks", "screen.wrights.copy_nbt_blocks_hint", true);
        boolean settingsOn = WrightsConfig.copySettings();
        String settingsHint = settingsOn
                ? (WrightsConfig.copySettingsWhitelist() ? "screen.wrights.copy_nbt_settings_whitelist_hint" : "screen.wrights.copy_nbt_settings_hint")
                : "screen.wrights.copy_off_hint";
        drawToggle(graphics, mouseX, mouseY, settingsX, settingsY, settingsChosen(), "screen.wrights.copy_nbt_settings", settingsHint, WrightsClientConfig.COPY_NBT_BLOCKS.get() && settingsOn);
        String allHint = WrightsConfig.copyAll() ? "screen.wrights.copy_nbt_all_hint" : "screen.wrights.copy_off_hint";
        drawToggle(graphics, mouseX, mouseY, allX, allY, allChosen(), "screen.wrights.copy_nbt_all", allHint, WrightsClientConfig.COPY_NBT_BLOCKS.get() && copyAllUsable());
    }

    private void drawToggle(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, boolean on, String label, String hint, boolean enabled) {
        int box = enabled ? 0xFF18222C : 0xFF12171C;
        int border = !enabled ? 0xFF2A343C : on ? WrightsUi.COPPER : 0xFF3D4E5E;
        graphics.fill(x, y, x + 12, y + 12, box);
        WrightsUi.border(graphics, x, y, 12, 12, border);
        if (on && enabled) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, WrightsUi.COPPER);
        }
        int text = enabled ? WrightsUi.TEXT : WrightsUi.MUTED;
        Component title = Component.translatable(label);
        graphics.drawString(this.font, title, x + 16, y + 2, text, false);
        int helpX = x + 16 + this.font.width(title) + 4;
        boolean over = mouseX >= helpX && mouseX < helpX + 12 && mouseY >= y && mouseY < y + 12;
        graphics.fill(helpX, y, helpX + 12, y + 12, 0xFF18222C);
        WrightsUi.border(graphics, helpX, y, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, "?", helpX + 6, y + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        if (over) {
            helpTip = Component.translatable(hint);
        }
    }

    private void ensurePreview() {
        BlockPos first = WrightsClientState.cornerA;
        BlockPos second = WrightsClientState.cornerB;
        boolean fluids = WrightsClientConfig.COPY_FLUIDS.get();
        boolean nbt = WrightsClientConfig.COPY_NBT_BLOCKS.get();
        String turnsNow = turnKey();
        if (previewReady && same(previewA, first) && same(previewB, second) && previewFluids == fluids && previewNbt == nbt && previewTurns.equals(turnsNow)) {
            return;
        }
        previewA = first;
        previewB = second;
        previewFluids = fluids;
        previewNbt = nbt;
        previewTurns = turnsNow;
        previewReady = true;
        preview = this.minecraft == null
                ? SelectionPreview.Shot.empty()
                : SelectionPreview.orient(SelectionPreview.capture(this.minecraft.level, first, second, fluids, nbt), turns);
        WrightsClientState.showCapturePreview(preview.ghosts(), preview.concealed());
    }

    private static boolean same(BlockPos left, BlockPos right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean settingsChosen() {
        return WrightsConfig.copySettings() && WrightsClientConfig.COPY_NBT_SETTINGS.get() && !allChosen();
    }

    private boolean allChosen() {
        return copyAllUsable() && WrightsClientConfig.COPY_NBT_ALL.get();
    }

    private boolean copyAllUsable() {
        return WrightsConfig.copyAll() && this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator();
    }

    private boolean insideModel(double mouseX, double mouseY) {
        return mouseX >= modelX && mouseX < modelX + modelW && mouseY >= modelY && mouseY < modelY + modelH;
    }

    private boolean hitToggle(double mouseX, double mouseY, int x, int y, String label) {
        if (hitHelp(mouseX, mouseY, x, y, label)) {
            return false;
        }
        int width = 16 + this.font.width(Component.translatable(label));
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12;
    }

    private boolean hitHelp(double mouseX, double mouseY, int x, int y, String label) {
        int helpX = x + 16 + this.font.width(Component.translatable(label)) + 4;
        return mouseX >= helpX && mouseX < helpX + 12 && mouseY >= y && mouseY < y + 12;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && (hitHelp(mouseX, mouseY, fluidX, fluidY, "screen.wrights.copy_fluids")
                || hitHelp(mouseX, mouseY, nbtX, nbtY, "screen.wrights.copy_nbt_blocks")
                || hitHelp(mouseX, mouseY, settingsX, settingsY, "screen.wrights.copy_nbt_settings")
                || hitHelp(mouseX, mouseY, allX, allY, "screen.wrights.copy_nbt_all"))) {
            return true;
        }
        if (button == 0 && hitToggle(mouseX, mouseY, fluidX, fluidY, "screen.wrights.copy_fluids")) {
            if (saving()) {
                return true;
            }
            WrightsClientConfig.setCopyFluids(!WrightsClientConfig.COPY_FLUIDS.get());
            previewReady = false;
            ensurePreview();
            updateSaveButton();
            return true;
        }
        if (button == 0 && hitToggle(mouseX, mouseY, nbtX, nbtY, "screen.wrights.copy_nbt_blocks")) {
            if (saving()) {
                return true;
            }
            WrightsClientConfig.setCopyNbtBlocks(!WrightsClientConfig.COPY_NBT_BLOCKS.get());
            previewReady = false;
            ensurePreview();
            updateSaveButton();
            return true;
        }
        if (button == 0 && hitToggle(mouseX, mouseY, settingsX, settingsY, "screen.wrights.copy_nbt_settings")) {
            if (saving() || !WrightsClientConfig.COPY_NBT_BLOCKS.get() || !WrightsConfig.copySettings()) {
                return true;
            }
            WrightsClientConfig.setCopyNbtSettings(!WrightsClientConfig.COPY_NBT_SETTINGS.get());
            updateSaveButton();
            return true;
        }
        if (button == 0 && hitToggle(mouseX, mouseY, allX, allY, "screen.wrights.copy_nbt_all")) {
            if (saving() || !WrightsClientConfig.COPY_NBT_BLOCKS.get() || !copyAllUsable()) {
                return true;
            }
            WrightsClientConfig.setCopyNbtAll(!WrightsClientConfig.COPY_NBT_ALL.get());
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
