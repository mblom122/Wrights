package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.wrights.WrightsClientConfig;

/**
 * Edit the shape anchor coordinates and preview the nearby world blocks so the
 * point can be nudged into place without leaving the menu.
 */
public class ShapeAnchorScreen extends Screen {
    private static final int RADIUS = 3;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private boolean suppress;
    private float yaw = 35.0F;
    private List<WrightsClientState.Ghost> preview = List.of();
    private int previewSize = RADIUS * 2 + 1;
    private long previewKey = Long.MIN_VALUE;
    private String dimension = "";
    private int previewLeft;
    private int previewTop;
    private int previewWidth;
    private int previewHeight;
    private boolean dragging;
    private double dragLastX;

    public ShapeAnchorScreen(Screen parent) {
        super(Component.translatable("screen.wrights.shape_anchor_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(360, this.width);
        panelH = WrightsUi.fitH(280, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        if (WrightsClientConfig.SHAPE_SET.get()) {
            dimension = WrightsClientConfig.SHAPE_DIM.get();
        } else if (this.minecraft != null && this.minecraft.level != null) {
            dimension = this.minecraft.level.dimension().location().toString();
        }
        BlockPos start = WrightsClientConfig.SHAPE_SET.get()
                ? WrightsClientConfig.shapeAnchor()
                : playerOrZero();
        xBox = number(panelX + 48, panelY + 52);
        yBox = number(panelX + 48, panelY + 70);
        zBox = number(panelX + 48, panelY + 88);
        step(panelX + 28, panelY + 52, "-", () -> nudge(-1, 0, 0));
        step(panelX + 112, panelY + 52, "+", () -> nudge(1, 0, 0));
        step(panelX + 28, panelY + 70, "-", () -> nudge(0, -1, 0));
        step(panelX + 112, panelY + 70, "+", () -> nudge(0, 1, 0));
        step(panelX + 28, panelY + 88, "-", () -> nudge(0, 0, -1));
        step(panelX + 112, panelY + 88, "+", () -> nudge(0, 0, 1));
        suppress = true;
        set(xBox, start.getX());
        set(yBox, start.getY());
        set(zBox, start.getZ());
        suppress = false;
        // Stack Under me / Look in the left column so Look is not drawn under the preview.
        int leftW = Math.max(96, panelW / 2 - 24);
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + 116, leftW, 18,
                Component.translatable("screen.wrights.shape_anchor_here"), button -> usePlayer()));
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + 138, leftW, 18,
                Component.translatable("screen.wrights.shape_anchor_look"), button -> useLook()));
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + panelH - 36, panelW - 100, 20,
                Component.translatable("screen.wrights.done"), button -> saveAndClose()));
        addRenderableWidget(new WrightsButton(panelX + panelW - 76, panelY + panelH - 36, 60, 20,
                Component.translatable("screen.wrights.cancel"), button -> onClose(), true));
        refreshPreview();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
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
        for (var line : this.font.split(Component.translatable("screen.wrights.shape_anchor_hint"), panelW - 32)) {
            graphics.drawString(this.font, line, panelX + 16, panelY + 26, WrightsUi.MUTED, false);
            break;
        }
        graphics.drawString(this.font, "X", panelX + 16, panelY + 55, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Y", panelX + 16, panelY + 73, WrightsUi.MUTED, false);
        graphics.drawString(this.font, "Z", panelX + 16, panelY + 91, WrightsUi.MUTED, false);
        String dim = dimension == null || dimension.isBlank() ? "-" : shortDim(dimension);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(
                Component.translatable("screen.wrights.shape_anchor_dim", dim).getString(), panelW / 2 - 24),
                panelX + 16, panelY + 162, WrightsUi.MUTED, false);
        previewLeft = panelX + panelW / 2 + 8;
        previewTop = panelY + 48;
        previewWidth = panelW / 2 - 24;
        previewHeight = Math.max(72, panelH - 100);
        graphics.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, 0xFF101820);
        WrightsUi.border(graphics, previewLeft, previewTop, previewWidth, previewHeight, 0xFF31404E);
        if (!sameWorld()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.shape_anchor_missing_world"),
                    previewLeft + previewWidth / 2, previewTop + previewHeight / 2 - 4, WrightsUi.MUTED);
        } else if (preview.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.preview_hidden"),
                    previewLeft + previewWidth / 2, previewTop + previewHeight / 2 - 4, WrightsUi.MUTED);
        } else {
            if (!dragging) {
                yaw += 0.25F;
            }
            BlueprintModelView.render(graphics, previewLeft + 1, previewTop + 1, previewWidth - 2, previewHeight - 2,
                    preview, previewSize, previewSize, previewSize, yaw, 28.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getGameTime() % 20L == 0L) {
            previewKey = Long.MIN_VALUE;
            refreshPreview();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= previewLeft && mouseX < previewLeft + previewWidth
                && mouseY >= previewTop && mouseY < previewTop + previewHeight) {
            dragging = true;
            dragLastX = mouseX;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            yaw += (float) (mouseX - dragLastX) * 0.8F;
            dragLastX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void saveAndClose() {
        BlockPos pos = readPos();
        if (pos != null) {
            String dim = dimension;
            if (dim == null || dim.isBlank()) {
                dim = this.minecraft != null && this.minecraft.level != null
                        ? this.minecraft.level.dimension().location().toString()
                        : "";
            }
            WrightsClientConfig.setShapeAnchor(pos, dim);
            WrightsClientConfig.setShapeUseAnchor(true);
        }
        onClose();
    }

    private void nudge(int dx, int dy, int dz) {
        BlockPos pos = readPos();
        if (pos == null) {
            return;
        }
        int step = WrightsUi.nudgeStep();
        apply(pos.offset(dx * step, dy * step, dz * step));
    }

    private void usePlayer() {
        if (this.minecraft != null && this.minecraft.level != null) {
            dimension = this.minecraft.level.dimension().location().toString();
        }
        // Same as “Set under me”: stand on the center of the shape’s top when it finishes.
        BlockPos feet = playerOrZero();
        BlockPos pos = com.wrights.blueprint.Shapes.anchorStandingOnTop(
                com.wrights.blueprint.Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get()),
                feet,
                WrightsClientConfig.SHAPE_SIZE.get(),
                WrightsClientConfig.SHAPE_HEIGHT.get(),
                WrightsClientConfig.SHAPE_THICKNESS.get(),
                WrightsClientConfig.SHAPE_HOLLOW.get(),
                WrightsClientConfig.SHAPE_PLANE.get(),
                WrightsClientConfig.SHAPE_YAW.get(),
                WrightsClientConfig.SHAPE_PIVOT.get());
        apply(pos);
    }

    private void useLook() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        dimension = minecraft.level.dimension().location().toString();
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            apply(hit.getBlockPos());
            return;
        }
        apply(playerOrZero());
    }

    private void apply(BlockPos pos) {
        suppress = true;
        set(xBox, pos.getX());
        set(yBox, pos.getY());
        set(zBox, pos.getZ());
        suppress = false;
        refreshPreview();
    }

    private void onFields() {
        if (suppress) {
            return;
        }
        refreshPreview();
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

    private void refreshPreview() {
        BlockPos center = readPos();
        if (center == null || !sameWorld()) {
            preview = List.of();
            previewKey = Long.MIN_VALUE;
            return;
        }
        Level level = this.minecraft.level;
        long key = center.asLong();
        if (key == previewKey) {
            return;
        }
        previewKey = key;
        int span = RADIUS * 2 + 1;
        previewSize = span;
        List<WrightsClientState.Ghost> ghosts = new ArrayList<>();
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(at);
                    boolean anchor = dx == 0 && dy == 0 && dz == 0;
                    if (state.isAir()) {
                        if (!anchor) {
                            continue;
                        }
                        // Blue stand-in so an empty anchor cell still matches world/menu markers.
                        state = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
                    }
                    if (anchor) {
                        // Keep the real block, but tint blue so the center matches the anchor marker.
                        ghosts.add(new WrightsClientState.Ghost(
                                dx + RADIUS, dy + RADIUS, dz + RADIUS,
                                WrightsUi.anchorR(), WrightsUi.anchorG(), WrightsUi.anchorB(),
                                Block.getId(state),
                                1.0F));
                        continue;
                    }
                    // Fade neighbors so the anchor cell reads clearly in the spinning preview.
                    ghosts.add(new WrightsClientState.Ghost(
                            dx + RADIUS, dy + RADIUS, dz + RADIUS,
                            1.0F, 1.0F, 1.0F,
                            Block.getId(state),
                            0.38F));
                }
            }
        }
        preview = List.copyOf(ghosts);
    }

    private boolean sameWorld() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return false;
        }
        if (dimension == null || dimension.isBlank()) {
            return true;
        }
        return dimension.equals(this.minecraft.level.dimension().location().toString());
    }

    private BlockPos playerOrZero() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.blockPosition();
        }
        return BlockPos.ZERO;
    }

    private static String shortDim(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        return loc == null ? id : loc.getPath();
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
