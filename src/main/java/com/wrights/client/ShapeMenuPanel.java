package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.ShapeMaterials;
import com.wrights.blueprint.ShapeSupply;
import com.wrights.blueprint.Shapes;

/** Shape page body drawn inside the Building section of the controller menu. */
final class ShapeMenuPanel {
    private static final int CHIP = 20;
    private static final int SLIDER_SIZE = 1;
    private static final int SLIDER_HEIGHT = 2;
    private static final int SLIDER_THICKNESS = 3;
    private static final int SLIDER_BRIDGE = 4;
    private static final int SLIDER_PATH_WIDTH = 5;
    private static final int SLIDER_PATH_HEIGHT = 6;

    private final Font font;
    private Screen host;
    private int bodyX;
    private int bodyW;
    private int viewTop;
    private int viewH;
    private int contentH;
    private int scroll;
    private int previewX;
    private int previewW;
    /** Actual 3D preview box (below the size/blocks line). */
    private int previewBoxY;
    private int previewBoxH;
    private int sizeY = -1;
    private int heightY = -1;
    private int thicknessY = -1;
    private int bridgeY = -1;
    private int pathWidthY = -1;
    private int pathHeightY = -1;
    /** Left edge of the slider track (between − and +). */
    private int sizeTrackX;
    private int sizeW;
    private int sizeValueX;
    private int sizeValueW;
    private int heightValueX;
    private int heightValueW;
    private int thicknessValueX;
    private int thicknessValueW;
    private int bridgeValueX;
    private int bridgeValueW;
    private int pathWidthValueX;
    private int pathWidthValueW;
    private int pathHeightValueX;
    private int pathHeightValueW;
    /** 0 = none, otherwise {@link #SLIDER_SIZE} / {@link #SLIDER_HEIGHT} / {@link #SLIDER_THICKNESS}. */
    private int draggingSlider;
    private int editingSlider;
    private String editText = "";
    private int selectedMaterial;
    private final List<Hit> hits = new ArrayList<>();
    private Component tip;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    /** Menu preview mesh — kept as one list so {@link BlueprintModelView} can reuse the bake. */
    private List<WrightsClientState.Ghost> previewGhosts = List.of();
    /** Real shape AABB / block count shown in the info lines. */
    private int previewSizeX = 1;
    private int previewSizeY = 1;
    private int previewSizeZ = 1;
    private int previewBlocks;
    /** Solid miniature AABB used only for the spinning model (may be scaled down). */
    private int meshSizeX = 1;
    private int meshSizeY = 1;
    private int meshSizeZ = 1;
    private long previewKey = Long.MIN_VALUE;
    private float previewYaw = 35.0F;
    private float previewPitch = 28.0F;
    private boolean previewSpinning;
    /** When true, title/materials UI are Exchange; geometry still uses Shape config. */
    private boolean exchangeMode;
    /** Place tool page — Exchange forms that build instead of swap. */
    private boolean placeMode;

    ShapeMenuPanel(Font font) {
        this.font = font;
    }

    void setExchangeMode(boolean exchange) {
        this.exchangeMode = exchange;
        if (exchange) {
            this.placeMode = false;
        }
    }

    void setPlaceMode(boolean place) {
        this.placeMode = place;
        if (place) {
            this.exchangeMode = false;
        }
    }

    private boolean formPresets() {
        return exchangeMode || placeMode;
    }

    Component tip() {
        return tip;
    }

    ItemStack hoveredStack() {
        return hoveredStack;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, int x, int top, int width, int bottom, Screen host) {
        hits.clear();
        tip = null;
        hoveredStack = ItemStack.EMPTY;
        this.host = host;
        viewTop = top;
        viewH = Math.max(40, bottom - top);
        // Fixed preview on the right; only the left options column scrolls.
        int gap = 8;
        previewW = Mth.clamp((width - gap) * 2 / 5, 110, Math.max(110, width / 2));
        int leftW = Math.max(140, width - gap - previewW);
        previewW = Math.max(96, width - gap - leftW);
        previewX = x + leftW + gap;
        bodyX = x;
        bodyW = leftW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - viewH));
        graphics.enableScissor(x - 4, viewTop, x + leftW + 2, viewTop + viewH);
        int y = viewTop - scroll;
        int lineEnd = x + leftW;
        String titleKey = placeMode
                ? "menu.wrights.place_title"
                : exchangeMode ? "menu.wrights.exchange_title" : "menu.wrights.shape_title";
        graphics.drawString(font, Component.translatable(titleKey), x, y, WrightsUi.COPPER, false);
        y += 14;
        sizeY = -1;
        heightY = -1;
        thicknessY = -1;
        bridgeY = -1;
        pathWidthY = -1;
        pathHeightY = -1;
        heightValueX = heightValueW = 0;
        thicknessValueX = thicknessValueW = 0;
        bridgeValueX = bridgeValueW = 0;
        pathWidthValueX = pathWidthValueW = 0;
        pathHeightValueX = pathHeightValueW = 0;
        boolean useAnchor = false;
        if (placeMode) {
            y = renderPlaceBody(graphics, mouseX, mouseY, x, y, leftW, lineEnd);
        } else {
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    formPresets() ? "menu.wrights.exchange_form" : "menu.wrights.shape_kind",
                    formPresets() ? "menu.wrights.exchange_form_hint" : "menu.wrights.shape_kind_hint",
                    !formPresets());
            y = drawKinds(graphics, mouseX, mouseY, x, y, leftW);
            Shapes.Kind kind = formPresets()
                    ? WrightsClientConfig.exchangeKind()
                    : Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
            Shapes.ExchangeForm exchangeForm = formPresets() ? WrightsClientConfig.exchangeForm() : null;
            boolean exchangeLine = exchangeForm != null && exchangeForm.line();
            boolean exchangePlate = exchangeForm != null && exchangeForm.plate();
            if (exchangeLine) {
                WrightsClientConfig.enforceExchangeLineFootprint();
            }
            if (exchangePlate) {
                WrightsClientConfig.syncExchangeEqualDims();
                kind = WrightsClientConfig.exchangeKind();
            }
            int axis = WrightsClientConfig.SHAPE_PLANE.get();
            boolean ring = kind == Shapes.Kind.RING;
            boolean box = kind == Shapes.Kind.BOX;
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_section_dimensions", "menu.wrights.shape_section_dimensions_hint", false);
            y = drawFormDimensions(graphics, mouseX, mouseY, x, y, leftW, kind, exchangeForm, exchangeLine, exchangePlate, ring, box, axis);
            if (!formPresets()) {
                y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                        "menu.wrights.shape_section_orientation", "menu.wrights.shape_section_orientation_hint", false);
                drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_axis", "menu.wrights.shape_axis_hint");
                y += 12;
                y = drawTriple(graphics, mouseX, mouseY, x, y, leftW,
                        WrightsClientConfig.SHAPE_PLANE.get(),
                        "menu.wrights.shape_axis_y",
                        "menu.wrights.shape_axis_z",
                        "menu.wrights.shape_axis_x",
                        "plane");
                y += 4;
                drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_rotation", "menu.wrights.shape_rotation_hint");
                y += 12;
                y = drawQuad(graphics, mouseX, mouseY, x, y, leftW,
                        Shapes.clampYaw(WrightsClientConfig.SHAPE_YAW.get()),
                        "menu.wrights.shape_rot_0",
                        "menu.wrights.shape_rot_90",
                        "menu.wrights.shape_rot_180",
                        "menu.wrights.shape_rot_270",
                        "rotation");
            }
            String matsKey = exchangeMode ? "menu.wrights.exchange_to" : "menu.wrights.shape_materials";
            String matsHint = exchangeMode ? "menu.wrights.exchange_to_hint" : "menu.wrights.shape_materials_hint";
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd, matsKey, matsHint, false);
            y = drawMaterials(graphics, mouseX, mouseY, x, y, leftW);
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_priority", "menu.wrights.shape_priority_hint", false);
            y = drawPriority(graphics, mouseX, mouseY, x, y, leftW);
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_section_build", "menu.wrights.shape_section_build_hint", false);
            drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_place", "menu.wrights.shape_place_hint");
            y += 12;
            useAnchor = WrightsConfig.allowShapeAnchor() && WrightsClientConfig.SHAPE_USE_ANCHOR.get();
            if (WrightsConfig.allowShapeAnchor()) {
                y = drawPair(graphics, mouseX, mouseY, x, y, leftW,
                        WrightsClientConfig.SHAPE_USE_ANCHOR.get(),
                        "menu.wrights.shape_place_look",
                        "menu.wrights.shape_place_anchor",
                        "place");
                y += 4;
                if (useAnchor) {
                    drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_under_me", "menu.wrights.shape_under_me_hint");
                    y += 12;
                    boolean underHover = mouseX >= x && mouseX < x + leftW && mouseY >= y && mouseY < y + 18;
                    WrightsUi.choice(graphics, x, y, leftW, 18, false, underHover);
                    graphics.drawCenteredString(font, Component.translatable("menu.wrights.shape_under_me_btn"), x + leftW / 2, y + 5, WrightsUi.TEXT);
                    hits.add(new Hit(x, y, leftW, 18, "under_me", 0));
                    y += 22;
                } else if (!exchangeMode) {
                    y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_LOOK_LOCK.get(),
                            "menu.wrights.shape_look_lock", "menu.wrights.shape_look_lock_hint", "look_lock");
                }
            } else if (!exchangeMode) {
                y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_LOOK_LOCK.get(),
                        "menu.wrights.shape_look_lock", "menu.wrights.shape_look_lock_hint", "look_lock");
            }
            if (formPresets()) {
                drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.exchange_pivot", "menu.wrights.exchange_pivot_hint");
                y += 12;
                y = drawExchangePivot(graphics, mouseX, mouseY, x, y, leftW);
            } else {
                drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_pivot", "menu.wrights.shape_pivot_hint");
                y += 12;
                graphics.drawString(font,
                        font.plainSubstrByWidth(ShapePivotScreen.pivotSummary(WrightsClientConfig.SHAPE_PIVOT.get()).getString(), Math.max(24, leftW - 8)),
                        x, y, WrightsUi.MUTED, false);
                y += 12;
                boolean pivotHover = mouseX >= x && mouseX < x + leftW && mouseY >= y && mouseY < y + 18;
                WrightsUi.choice(graphics, x, y, leftW, 18, false, pivotHover);
                graphics.drawCenteredString(font, Component.translatable("menu.wrights.shape_pivot_btn"), x + leftW / 2, y + 5, WrightsUi.TEXT);
                hits.add(new Hit(x, y, leftW, 18, "pivot", 0));
                y += 22;
            }
            if (!formPresets() && WrightsConfig.allowShapeHollow()) {
                y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_HOLLOW.get(), "menu.wrights.shape_hollow", "menu.wrights.shape_hollow_hint", "hollow");
            }
            if (exchangeMode) {
                drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.exchange_match", "menu.wrights.exchange_match_hint");
                y += 12;
                y = drawTriple(graphics, mouseX, mouseY, x, y, leftW,
                        WrightsClientConfig.exchangeMatch().ordinal(),
                        "menu.wrights.exchange_match_same",
                        "menu.wrights.exchange_match_similar",
                        "menu.wrights.exchange_match_any",
                        "exchange_match");
                y += 4;
                y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.exchangeSilk(),
                        "menu.wrights.exchange_silk", "menu.wrights.exchange_silk_hint", "exchange_silk");
            }
            if (WrightsConfig.allowShapeVoid()) {
                String voidHint = exchangeMode
                        ? "menu.wrights.exchange_void_hint"
                        : "menu.wrights.shape_void_hint";
                y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_VOID.get(),
                        "menu.wrights.shape_void", voidHint, "void");
            }
            if (useAnchor) {
                y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                        "menu.wrights.shape_anchor_section", "menu.wrights.shape_anchor_section_hint", false);
                y = drawStatus(graphics, mouseX, mouseY, x, y, leftW);
            }
        }

        contentH = y - (viewTop - scroll) + 8;
        graphics.disableScissor();
        if (contentH > viewH) {
            WrightsUi.scrollBar(graphics, x + leftW - 2, viewTop, viewH, scroll, contentH);
        }
        drawShapePreview(graphics, previewX, viewTop, previewW, viewH);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Scroll only over the left options column — preview stays fixed.
        if (mouseX < bodyX - 4 || mouseX > bodyX + bodyW + 4 || mouseY < viewTop || mouseY > viewTop + viewH) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 24, 0, Math.max(0, contentH - viewH));
        return true;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (editingSlider != 0) {
            if (hitValue(editingSlider, mouseX, mouseY)) {
                return true;
            }
            commitEdit();
        }
        if (insidePreview(mouseX, mouseY)) {
            previewSpinning = true;
            return true;
        }
        if (mouseX < bodyX - 4 || mouseX > bodyX + bodyW + 4 || mouseY < viewTop || mouseY > viewTop + viewH) {
            return false;
        }
        if (hitValue(SLIDER_SIZE, mouseX, mouseY)) {
            beginEdit(SLIDER_SIZE);
            return true;
        }
        if (hitValue(SLIDER_THICKNESS, mouseX, mouseY)) {
            beginEdit(SLIDER_THICKNESS);
            return true;
        }
        if (hitValue(SLIDER_HEIGHT, mouseX, mouseY)) {
            beginEdit(SLIDER_HEIGHT);
            return true;
        }
        if (hitValue(SLIDER_BRIDGE, mouseX, mouseY)) {
            beginEdit(SLIDER_BRIDGE);
            return true;
        }
        if (hitValue(SLIDER_PATH_WIDTH, mouseX, mouseY)) {
            beginEdit(SLIDER_PATH_WIDTH);
            return true;
        }
        if (hitValue(SLIDER_PATH_HEIGHT, mouseX, mouseY)) {
            beginEdit(SLIDER_PATH_HEIGHT);
            return true;
        }
        if (sizeY >= 0 && mouseY >= sizeY && mouseY < sizeY + 12 && mouseX >= sizeTrackX && mouseX < sizeTrackX + sizeW) {
            draggingSlider = SLIDER_SIZE;
            WrightsClientConfig.setShapeSize(sliderAt(mouseX, axisMax()));
            return true;
        }
        if (thicknessY >= 0 && mouseY >= thicknessY && mouseY < thicknessY + 12 && mouseX >= sizeTrackX && mouseX < sizeTrackX + sizeW) {
            draggingSlider = SLIDER_THICKNESS;
            WrightsClientConfig.setShapeThickness(sliderAt(mouseX, axisMax()));
            return true;
        }
        if (heightY >= 0 && mouseY >= heightY && mouseY < heightY + 12 && mouseX >= sizeTrackX && mouseX < sizeTrackX + sizeW) {
            draggingSlider = SLIDER_HEIGHT;
            setHeightDim(sliderAt(mouseX, axisMax()));
            return true;
        }
        if (bridgeY >= 0 && mouseY >= bridgeY && mouseY < bridgeY + 12 && mouseX >= sizeTrackX && mouseX < sizeTrackX + sizeW) {
            draggingSlider = SLIDER_BRIDGE;
            WrightsClientConfig.setPlaceBridgeOffset(sliderAt(mouseX, 12));
            return true;
        }
        if (pathWidthY >= 0 && mouseY >= pathWidthY && mouseY < pathWidthY + 12 && mouseX >= sizeTrackX && mouseX < sizeTrackX + sizeW) {
            draggingSlider = SLIDER_PATH_WIDTH;
            WrightsClientConfig.setPlaceBridgeWidth(sliderAt(mouseX, 12));
            return true;
        }
        if (pathHeightY >= 0 && mouseY >= pathHeightY && mouseY < pathHeightY + 12 && mouseX >= sizeTrackX && mouseX < sizeTrackX + sizeW) {
            draggingSlider = SLIDER_PATH_HEIGHT;
            WrightsClientConfig.setPlaceToMeHeight(sliderAt(mouseX, 12));
            return true;
        }
        for (Hit hit : hits) {
            if (mouseX < hit.x || mouseX >= hit.x + hit.w || mouseY < hit.y || mouseY >= hit.y + hit.h) {
                continue;
            }
            return apply(hit.id, hit.value);
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, double dragX, double dragY) {
        if (draggingSlider == SLIDER_SIZE) {
            WrightsClientConfig.setShapeSize(sliderAt(mouseX, axisMax()));
            return true;
        }
        if (draggingSlider == SLIDER_THICKNESS) {
            WrightsClientConfig.setShapeThickness(sliderAt(mouseX, axisMax()));
            return true;
        }
        if (draggingSlider == SLIDER_HEIGHT) {
            setHeightDim(sliderAt(mouseX, axisMax()));
            return true;
        }
        if (draggingSlider == SLIDER_BRIDGE) {
            WrightsClientConfig.setPlaceBridgeOffset(sliderAt(mouseX, 12));
            return true;
        }
        if (draggingSlider == SLIDER_PATH_WIDTH) {
            WrightsClientConfig.setPlaceBridgeWidth(sliderAt(mouseX, 12));
            return true;
        }
        if (draggingSlider == SLIDER_PATH_HEIGHT) {
            WrightsClientConfig.setPlaceToMeHeight(sliderAt(mouseX, 12));
            return true;
        }
        if (!previewSpinning) {
            return false;
        }
        previewYaw += (float) dragX * 0.75F;
        previewPitch = Mth.clamp(previewPitch + (float) dragY * 0.45F, -70.0F, 70.0F);
        return true;
    }

    boolean mouseReleased() {
        boolean handled = draggingSlider != 0 || previewSpinning;
        draggingSlider = 0;
        previewSpinning = false;
        return handled;
    }

    boolean keyPressed(int keyCode) {
        if (editingSlider == 0) {
            return false;
        }
        if (keyCode == 256) {
            cancelEdit();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            commitEdit();
            return true;
        }
        if (keyCode == 259) {
            if (!editText.isEmpty()) {
                editText = editText.substring(0, editText.length() - 1);
            }
            return true;
        }
        return true;
    }

    boolean charTyped(char code) {
        if (editingSlider == 0) {
            return false;
        }
        if (code >= '0' && code <= '9' && editText.length() < 4) {
            editText = editText + code;
            return true;
        }
        return true;
    }

    boolean editingValue() {
        return editingSlider != 0;
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        return previewW > 0
                && previewBoxH > 0
                && mouseX >= previewX
                && mouseX < previewX + previewW
                && mouseY >= previewBoxY
                && mouseY < previewBoxY + previewBoxH;
    }

    private void beginEdit(int slider) {
        editingSlider = slider;
        editText = Integer.toString(sliderValue(slider));
        draggingSlider = 0;
    }

    private void cancelEdit() {
        editingSlider = 0;
        editText = "";
    }

    private void commitEdit() {
        if (editingSlider == 0) {
            return;
        }
        int slider = editingSlider;
        int parsed;
        try {
            parsed = editText.isEmpty() ? sliderValue(slider) : Integer.parseInt(editText);
        } catch (NumberFormatException ignored) {
            cancelEdit();
            return;
        }
        int max = (slider == SLIDER_BRIDGE || slider == SLIDER_PATH_WIDTH || slider == SLIDER_PATH_HEIGHT) ? 12 : axisMax();
        parsed = Mth.clamp(parsed, 1, max);
        switch (slider) {
            case SLIDER_SIZE -> WrightsClientConfig.setShapeSize(parsed);
            case SLIDER_HEIGHT -> setHeightDim(parsed);
            case SLIDER_THICKNESS -> WrightsClientConfig.setShapeThickness(parsed);
            case SLIDER_BRIDGE -> WrightsClientConfig.setPlaceBridgeOffset(parsed);
            case SLIDER_PATH_WIDTH -> WrightsClientConfig.setPlaceBridgeWidth(parsed);
            case SLIDER_PATH_HEIGHT -> WrightsClientConfig.setPlaceToMeHeight(parsed);
            default -> {
            }
        }
        cancelEdit();
    }

    private int sliderValue(int slider) {
        return switch (slider) {
            case SLIDER_HEIGHT -> heightDim();
            case SLIDER_THICKNESS -> WrightsClientConfig.SHAPE_THICKNESS.get();
            case SLIDER_BRIDGE -> WrightsClientConfig.placeBridgeOffset();
            case SLIDER_PATH_WIDTH -> WrightsClientConfig.placeBridgeWidth();
            case SLIDER_PATH_HEIGHT -> WrightsClientConfig.placeToMeHeight();
            default -> WrightsClientConfig.SHAPE_SIZE.get();
        };
    }

    private boolean exchangeLineForm() {
        return formPresets() && WrightsClientConfig.exchangeForm().line();
    }

    private int heightDim() {
        return exchangeLineForm()
                ? WrightsClientConfig.exchangeLineLength()
                : WrightsClientConfig.SHAPE_HEIGHT.get();
    }

    private void setHeightDim(int value) {
        if (exchangeLineForm()) {
            WrightsClientConfig.setExchangeLineLength(value);
            WrightsClientConfig.enforceExchangeLineFootprint();
        } else {
            WrightsClientConfig.setShapeHeight(value);
        }
    }

    private boolean hitValue(int slider, double mouseX, double mouseY) {
        int x;
        int y;
        int w;
        switch (slider) {
            case SLIDER_SIZE -> {
                if (sizeY < 0 || sizeValueW <= 0) {
                    return false;
                }
                x = sizeValueX;
                y = sizeY;
                w = sizeValueW;
            }
            case SLIDER_HEIGHT -> {
                if (heightY < 0 || heightValueW <= 0) {
                    return false;
                }
                x = heightValueX;
                y = heightY;
                w = heightValueW;
            }
            case SLIDER_THICKNESS -> {
                if (thicknessY < 0 || thicknessValueW <= 0) {
                    return false;
                }
                x = thicknessValueX;
                y = thicknessY;
                w = thicknessValueW;
            }
            case SLIDER_BRIDGE -> {
                if (bridgeY < 0 || bridgeValueW <= 0) {
                    return false;
                }
                x = bridgeValueX;
                y = bridgeY;
                w = bridgeValueW;
            }
            case SLIDER_PATH_WIDTH -> {
                if (pathWidthY < 0 || pathWidthValueW <= 0) {
                    return false;
                }
                x = pathWidthValueX;
                y = pathWidthY;
                w = pathWidthValueW;
            }
            case SLIDER_PATH_HEIGHT -> {
                if (pathHeightY < 0 || pathHeightValueW <= 0) {
                    return false;
                }
                x = pathHeightValueX;
                y = pathHeightY;
                w = pathHeightValueW;
            }
            default -> {
                return false;
            }
        }
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
    }

    private static int axisMax() {
        return WrightsConfig.maxShapeAxis();
    }

    /** Primary size slider label — what SHAPE_SIZE actually builds for this kind. */
    private static String sizeLabel(Shapes.Kind kind, int axis) {
        return switch (kind) {
            case BOX, SQUARE -> "menu.wrights.shape_width";
            case CUBE, RECTANGLE -> "menu.wrights.shape_size";
            case CIRCLE -> "menu.wrights.shape_diameter";
            case CYLINDER, SPHERE, DOME -> "menu.wrights.shape_radius";
            default -> "menu.wrights.shape_size";
        };
    }

    private static String sizeHint(Shapes.Kind kind, int axis) {
        return switch (kind) {
            case BOX -> "menu.wrights.shape_width_hint";
            case SQUARE -> "menu.wrights.shape_square_width_hint";
            case CUBE -> "menu.wrights.shape_cube_size_hint";
            case RECTANGLE -> "menu.wrights.shape_rectangle_size_hint";
            case CIRCLE -> "menu.wrights.shape_circle_diameter_hint";
            case CYLINDER -> "menu.wrights.shape_cylinder_radius_hint";
            case SPHERE -> "menu.wrights.shape_sphere_radius_hint";
            case DOME -> "menu.wrights.shape_dome_radius_hint";
            case PYRAMID -> "menu.wrights.shape_pyramid_size_hint";
            case RING -> "menu.wrights.shape_ring_size_hint";
            default -> "menu.wrights.shape_size_hint";
        };
    }

    /**
     * Along-axis extent (SHAPE_HEIGHT). Box/Square always say Height (W×H / W×D×H); other shapes
     * rename for the chosen Axis so Width/Height are not swapped when tipped onto X or Z.
     */
    private static String alongLabel(Shapes.Kind kind, int axis) {
        if (kind == Shapes.Kind.BOX || kind == Shapes.Kind.SQUARE) {
            return "menu.wrights.shape_height";
        }
        return switch (Shapes.clampPlane(axis)) {
            case Shapes.AXIS_X -> "menu.wrights.shape_along_x";
            case Shapes.AXIS_Z -> "menu.wrights.shape_along_z";
            default -> "menu.wrights.shape_height";
        };
    }

    private static String alongHint(Shapes.Kind kind, int axis) {
        int a = Shapes.clampPlane(axis);
        if (kind == Shapes.Kind.RING) {
            return a == Shapes.AXIS_Y ? "menu.wrights.shape_ring_height_hint" : "menu.wrights.shape_ring_along_hint";
        }
        if (kind == Shapes.Kind.BOX) {
            return "menu.wrights.shape_box_height_hint";
        }
        if (kind == Shapes.Kind.SQUARE) {
            return "menu.wrights.shape_square_height_hint";
        }
        if (kind == Shapes.Kind.CYLINDER) {
            return switch (a) {
                case Shapes.AXIS_X -> "menu.wrights.shape_cylinder_along_x_hint";
                case Shapes.AXIS_Z -> "menu.wrights.shape_cylinder_along_z_hint";
                default -> "menu.wrights.shape_cylinder_height_hint";
            };
        }
        return switch (a) {
            case Shapes.AXIS_X -> "menu.wrights.shape_along_x_hint";
            case Shapes.AXIS_Z -> "menu.wrights.shape_along_z_hint";
            default -> "menu.wrights.shape_height_hint";
        };
    }

    /** Map a click on the shared slider track to 1..maxInclusive. */
    private int sliderAt(double mouseX, int maxInclusive) {
        int span = Math.max(1, maxInclusive - 1);
        int value = 1 + (int) Math.round((mouseX - sizeTrackX) * (double) span / Math.max(1, sizeW));
        return Mth.clamp(value, 1, maxInclusive);
    }

    private boolean apply(String id, int value) {
        return switch (id) {
            case "kind" -> {
                WrightsClientConfig.setShapeKind(value);
                yield true;
            }
            case "exchange_form" -> {
                if (formPresets()) {
                    WrightsClientConfig.applyExchangeForm(value, net.minecraft.client.Minecraft.getInstance().player);
                }
                yield true;
            }
            case "place_style" -> {
                if (placeMode) {
                    WrightsClientConfig.setPlaceStyle(value);
                }
                yield true;
            }
            case "place_stairs_dir" -> {
                if (placeMode) {
                    // drawPair: 0 = left (Up), 1 = right (Down)
                    WrightsClientConfig.setPlaceStairsUp(value == 0);
                }
                yield true;
            }
            case "place_yaw" -> {
                // Action pair: 0 = Left (CCW), 1 = Right (CW) — Floor / Wall / Beam only.
                if (placeMode && WrightsClientConfig.placeFormYawRotatable()) {
                    WrightsClientConfig.nudgeShapeYaw(value == 0 ? 1 : -1);
                }
                yield true;
            }
            case "plane" -> {
                WrightsClientConfig.setShapePlane(value);
                yield true;
            }
            case "rotation" -> {
                WrightsClientConfig.setShapeYaw(value);
                yield true;
            }
            case "place" -> {
                if (!WrightsConfig.allowShapeAnchor()) {
                    WrightsClientConfig.setShapeUseAnchor(false);
                    WrightsClientState.clearShapeLookSnap();
                    yield true;
                }
                WrightsClientConfig.setShapeUseAnchor(value != 0);
                // Look mode must follow the crosshair; drop any leftover snap pin.
                WrightsClientState.clearShapeLookSnap();
                if (exchangeMode && value == 0) {
                    WrightsClientConfig.setShapeLookLock(false);
                }
                yield true;
            }
            case "look_lock" -> {
                WrightsClientConfig.setShapeLookLock(!WrightsClientConfig.SHAPE_LOOK_LOCK.get());
                if (!WrightsClientConfig.SHAPE_LOOK_LOCK.get()) {
                    WrightsClientState.clearShapeLookSnap();
                }
                yield true;
            }
            case "under_me" -> {
                if (WrightsConfig.allowShapeAnchor()) {
                    setAnchorUnderPlayer();
                }
                yield true;
            }
            case "pivot" -> {
                if (host != null) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new ShapePivotScreen(host));
                }
                yield true;
            }
            case "exchange_pivot" -> {
                if (formPresets()) {
                    WrightsClientConfig.setExchangePivot(Shapes.pivotId(0, value));
                }
                yield true;
            }
            case "exchange_equal" -> {
                if (formPresets()) {
                    Shapes.ExchangeForm form = WrightsClientConfig.exchangeForm();
                    if (form == Shapes.ExchangeForm.FLOOR) {
                        WrightsClientConfig.setExchangeFloorEqual(!WrightsClientConfig.exchangeFloorEqual());
                    } else if (form == Shapes.ExchangeForm.WALL) {
                        WrightsClientConfig.setExchangeWallEqual(!WrightsClientConfig.exchangeWallEqual());
                    }
                }
                yield true;
            }
            case "hollow" -> {
                if (WrightsConfig.allowShapeHollow()) {
                    WrightsClientConfig.setShapeHollow(!WrightsClientConfig.SHAPE_HOLLOW.get());
                }
                yield true;
            }
            case "void" -> {
                if (WrightsConfig.allowShapeVoid()) {
                    WrightsClientConfig.setShapeVoid(!WrightsClientConfig.SHAPE_VOID.get());
                }
                yield true;
            }
            case "exchange_match" -> {
                if (exchangeMode) {
                    WrightsClientConfig.setExchangeMatch(com.wrights.util.ExchangeFamilies.Match.byId(value));
                }
                yield true;
            }
            case "exchange_silk" -> {
                if (exchangeMode) {
                    WrightsClientConfig.setExchangeSilk(!WrightsClientConfig.exchangeSilk());
                }
                yield true;
            }
            case "clear" -> {
                WrightsClientConfig.clearShapeAnchor();
                yield true;
            }
            case "highlight" -> {
                if (WrightsClientConfig.SHAPE_SET.get()) {
                    WrightsClientState.glowShapeAnchor(WrightsClientConfig.SHAPE_DIM.get(), WrightsClientConfig.shapeAnchor());
                }
                yield true;
            }
            case "change" -> {
                if (host != null) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new ShapeAnchorScreen(host));
                }
                yield true;
            }
            case "priority" -> {
                if (host != null) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new ShapePriorityScreen(host));
                }
                yield true;
            }
            case "mat_select" -> {
                selectedMaterial = value;
                yield true;
            }
            case "mat_pick" -> {
                if (host != null) {
                    List<BlockState> initial = new ArrayList<>();
                    for (ShapeMaterials.Entry entry : WrightsClientConfig.shapeMaterials()) {
                        BlockState state = entry.state();
                        if (state != null && !state.isAir()) {
                            initial.add(state);
                        }
                    }
                    BlockPickerScreen.openBlocks(host, initial, this::setMaterials);
                }
                yield true;
            }
            case "mat_remove" -> {
                WrightsClientConfig.shapeMaterialRemove(value);
                selectedMaterial = Math.min(selectedMaterial, Math.max(0, WrightsClientConfig.shapeMaterials().size() - 1));
                yield true;
            }
            case "mat_weight_up" -> {
                List<ShapeMaterials.Entry> entries = WrightsClientConfig.shapeMaterials();
                if (value >= 0 && value < entries.size()) {
                    int step = WrightsUi.nudgeStep();
                    WrightsClientConfig.shapeMaterialWeight(value, entries.get(value).weight() + step);
                }
                yield true;
            }
            case "mat_weight_down" -> {
                List<ShapeMaterials.Entry> entries = WrightsClientConfig.shapeMaterials();
                if (value >= 0 && value < entries.size()) {
                    int step = WrightsUi.nudgeStep();
                    WrightsClientConfig.shapeMaterialWeight(value, entries.get(value).weight() - step);
                }
                yield true;
            }
            case "mat_move_left" -> {
                WrightsClientConfig.shapeMaterialMove(value, -1);
                selectedMaterial = Math.max(0, value - 1);
                yield true;
            }
            case "mat_move_right" -> {
                WrightsClientConfig.shapeMaterialMove(value, 1);
                selectedMaterial = value + 1;
                yield true;
            }
            case "fill" -> {
                WrightsClientConfig.setShapeFillMode(value);
                yield true;
            }
            case "batch_up" -> {
                int step = WrightsUi.nudgeStep();
                WrightsClientConfig.setShapeOrderBatch(WrightsClientConfig.SHAPE_ORDER_BATCH.get() + step);
                yield true;
            }
            case "batch_down" -> {
                int step = WrightsUi.nudgeStep();
                WrightsClientConfig.setShapeOrderBatch(WrightsClientConfig.SHAPE_ORDER_BATCH.get() - step);
                yield true;
            }
            case "size_up" -> {
                nudgeDim(SLIDER_SIZE, 1);
                yield true;
            }
            case "size_down" -> {
                nudgeDim(SLIDER_SIZE, -1);
                yield true;
            }
            case "bridge_up" -> {
                nudgeDim(SLIDER_BRIDGE, 1);
                yield true;
            }
            case "bridge_down" -> {
                nudgeDim(SLIDER_BRIDGE, -1);
                yield true;
            }
            case "path_width_up" -> {
                nudgeDim(SLIDER_PATH_WIDTH, 1);
                yield true;
            }
            case "path_width_down" -> {
                nudgeDim(SLIDER_PATH_WIDTH, -1);
                yield true;
            }
            case "path_height_up" -> {
                nudgeDim(SLIDER_PATH_HEIGHT, 1);
                yield true;
            }
            case "path_height_down" -> {
                nudgeDim(SLIDER_PATH_HEIGHT, -1);
                yield true;
            }
            case "height_up" -> {
                nudgeDim(SLIDER_HEIGHT, 1);
                yield true;
            }
            case "height_down" -> {
                nudgeDim(SLIDER_HEIGHT, -1);
                yield true;
            }
            case "thickness_up" -> {
                nudgeDim(SLIDER_THICKNESS, 1);
                yield true;
            }
            case "thickness_down" -> {
                nudgeDim(SLIDER_THICKNESS, -1);
                yield true;
            }
            default -> false;
        };
    }

    /** ± with the shared Wrights modifiers: 1 · Ctrl 5 · Shift 10 · Alt 50 · Ctrl+Shift 100. */
    private void nudgeDim(int slider, int sign) {
        int step = WrightsUi.nudgeStep() * (sign < 0 ? -1 : 1);
        switch (slider) {
            case SLIDER_HEIGHT -> setHeightDim(heightDim() + step);
            case SLIDER_THICKNESS -> WrightsClientConfig.setShapeThickness(WrightsClientConfig.SHAPE_THICKNESS.get() + step);
            case SLIDER_BRIDGE -> WrightsClientConfig.setPlaceBridgeOffset(WrightsClientConfig.placeBridgeOffset() + step);
            case SLIDER_PATH_WIDTH -> WrightsClientConfig.setPlaceBridgeWidth(WrightsClientConfig.placeBridgeWidth() + step);
            case SLIDER_PATH_HEIGHT -> WrightsClientConfig.setPlaceToMeHeight(WrightsClientConfig.placeToMeHeight() + step);
            default -> WrightsClientConfig.setShapeSize(WrightsClientConfig.SHAPE_SIZE.get() + step);
        }
    }

    /** Anchor at the block the player is standing in — no shape-size / hologram offset. */
    private void setAnchorUnderPlayer() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        BlockPos anchor = minecraft.player.blockPosition();
        WrightsClientState.setShapeAnchor(anchor);
        WrightsClientConfig.setShapeUseAnchor(true);
        minecraft.player.displayClientMessage(
                Component.translatable("message.wrights.shape_under_me", anchor.getX(), anchor.getY(), anchor.getZ()),
                true);
    }

    /** Replace the materials list with the picker tray, keeping weights for blocks that stay. */
    private void setMaterials(List<BlockState> states) {
        if (states == null || states.isEmpty()) {
            return;
        }
        java.util.Map<net.minecraft.world.item.Item, Integer> weights = new java.util.HashMap<>();
        for (ShapeMaterials.Entry entry : WrightsClientConfig.shapeMaterials()) {
            weights.put(entry.item(), entry.weight());
        }
        List<ShapeMaterials.Entry> next = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (BlockState state : states) {
            if (!com.wrights.util.BlockFilters.canShapeMaterial(state)) {
                continue;
            }
            net.minecraft.world.item.Item item = state.getBlock().asItem();
            if (!(item instanceof net.minecraft.world.item.BlockItem) || !seen.add(item)) {
                continue;
            }
            next.add(new ShapeMaterials.Entry(item, weights.getOrDefault(item, 1)));
        }
        if (next.isEmpty()) {
            return;
        }
        WrightsClientConfig.setShapeMaterials(next);
        selectedMaterial = Math.min(selectedMaterial, next.size() - 1);
    }

    private int drawMaterials(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        ShapeMaterials.FillMode mode = WrightsClientConfig.shapeFillMode();
        y = drawTriple(graphics, mouseX, mouseY, x, y, width, mode.ordinal(),
                "menu.wrights.shape_fill_random",
                "menu.wrights.shape_fill_priority",
                "menu.wrights.shape_fill_order",
                "fill");
        if (mode == ShapeMaterials.FillMode.ORDER) {
            graphics.drawString(font, Component.translatable("menu.wrights.shape_order_batch"), x, y + 4, WrightsUi.MUTED, false);
            int controls = x + width;
            controls = drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "+", "batch_up", 0, WrightsClientConfig.SHAPE_ORDER_BATCH.get() < 64) - 2;
            String batch = Integer.toString(WrightsClientConfig.SHAPE_ORDER_BATCH.get());
            int batchW = Math.max(18, font.width(batch) + 6);
            graphics.drawCenteredString(font, batch, controls - batchW / 2, y + 4, WrightsUi.COPPER);
            controls -= batchW + 2;
            drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "-", "batch_down", 0, WrightsClientConfig.SHAPE_ORDER_BATCH.get() > 1);
            y += 20;
        }
        List<ShapeMaterials.Entry> entries = WrightsClientConfig.shapeMaterials();
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("menu.wrights.shape_materials_empty"), x, y + 4, WrightsUi.MUTED, false);
            y += 16;
        } else {
            selectedMaterial = Mth.clamp(selectedMaterial, 0, entries.size() - 1);
            int gap = 4;
            int cols = Math.max(1, (width + gap) / (CHIP + gap));
            for (int i = 0; i < entries.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = x + col * (CHIP + gap);
                int cy = y + row * (CHIP + gap);
                boolean on = i == selectedMaterial;
                boolean hover = mouseX >= cx && mouseX < cx + CHIP && mouseY >= cy && mouseY < cy + CHIP;
                WrightsUi.slot(graphics, cx, cy, on || hover ? WrightsUi.COPPER : 0xFF31404E);
                ItemStack stack = entries.get(i).item().getDefaultInstance();
                graphics.renderItem(stack, cx + 1, cy + 1);
                String badge = mode == ShapeMaterials.FillMode.RANDOM
                        ? Integer.toString(entries.get(i).weight())
                        : Integer.toString(i + 1);
                int wx = cx + 17 - font.width(badge);
                int by = cy + 10;
                // Item icons draw above normal text; lift the count like inventory stack sizes.
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.fill(wx - 1, by - 1, cx + 17, by + 8, 0xC0101820);
                graphics.drawString(font, badge, wx, by, WrightsUi.COPPER, false);
                graphics.pose().popPose();
                hits.add(new Hit(cx, cy, CHIP, CHIP, "mat_select", i));
                if (hover) {
                    hoveredStack = stack;
                }
            }
            int rows = (entries.size() + cols - 1) / cols;
            y += rows * (CHIP + gap) + 2;
            ShapeMaterials.Entry selected = entries.get(selectedMaterial);
            String name = font.plainSubstrByWidth(selected.item().getDefaultInstance().getHoverName().getString(), Math.max(40, width - 130));
            graphics.drawString(font, name, x, y + 4, WrightsUi.TEXT, false);
            int controls = x + width;
            controls = drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "x", "mat_remove", selectedMaterial, entries.size() > 1) - 2;
            if (mode == ShapeMaterials.FillMode.RANDOM) {
                controls = drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "+", "mat_weight_up", selectedMaterial, true) - 2;
                String weight = Integer.toString(selected.weight());
                int weightW = Math.max(18, font.width(weight) + 6);
                graphics.drawCenteredString(font, weight, controls - weightW / 2, y + 4, WrightsUi.COPPER);
                controls -= weightW + 2;
                drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "-", "mat_weight_down", selectedMaterial, selected.weight() > 1);
            } else {
                controls = drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, ">", "mat_move_right", selectedMaterial, selectedMaterial < entries.size() - 1) - 2;
                drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "<", "mat_move_left", selectedMaterial, selectedMaterial > 0);
            }
            y += 20;
        }
        boolean pickHover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18;
        WrightsUi.choice(graphics, x, y, width, 18, false, pickHover);
        graphics.drawCenteredString(font, Component.translatable("menu.wrights.shape_materials_pick"), x + width / 2, y + 5, WrightsUi.TEXT);
        hits.add(new Hit(x, y, width, 18, "mat_pick", 0));
        return y + 22;
    }

    /** Place page: Build mode first; form / dims only for Look. */
    private int renderPlaceBody(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int leftW, int lineEnd) {
        y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                "menu.wrights.place_aim", "menu.wrights.place_aim_hint", false);
        drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.place_aim", "menu.wrights.place_aim_hint");
        y += 12;
        y = drawTriple(graphics, mouseX, mouseY, x, y, leftW,
                WrightsClientConfig.placeStyle(),
                "menu.wrights.place_aim_look",
                "menu.wrights.place_aim_me",
                "menu.wrights.place_aim_bridge",
                "place_style");
        y += 4;

        int style = WrightsClientConfig.placeStyle();
        boolean look = style == 0;
        boolean toMe = style == 1;
        boolean bridge = WrightsClientConfig.placeBridgeStyle();
        Shapes.ExchangeForm form = WrightsClientConfig.exchangeForm();
        boolean stairsForm = look && form.stairsPath();

        if (bridge) {
            y = drawPathOffsetWidth(graphics, mouseX, mouseY, x, y, leftW, false);
        }

        if (toMe) {
            drawField(graphics, mouseX, mouseY, x, y,
                    "menu.wrights.place_bridge_width", "menu.wrights.place_bridge_width_hint");
            y += 12;
            pathWidthY = y;
            drawDimSlider(graphics, mouseX, mouseY, x, y, leftW,
                    WrightsClientConfig.placeBridgeWidth(), SLIDER_PATH_WIDTH);
            y += 18;
            drawField(graphics, mouseX, mouseY, x, y,
                    "menu.wrights.place_to_me_height", "menu.wrights.place_to_me_height_hint");
            y += 12;
            pathHeightY = y;
            drawDimSlider(graphics, mouseX, mouseY, x, y, leftW,
                    WrightsClientConfig.placeToMeHeight(), SLIDER_PATH_HEIGHT);
            y += 18;
        }

        if (look) {
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.exchange_form", "menu.wrights.exchange_form_hint", false);
            y = drawKinds(graphics, mouseX, mouseY, x, y, leftW);
            form = WrightsClientConfig.exchangeForm();
            stairsForm = form.stairsPath();
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_section_dimensions", "menu.wrights.shape_section_dimensions_hint", false);
            if (stairsForm) {
                y = drawPathOffsetWidth(graphics, mouseX, mouseY, x, y, leftW, true);
                drawField(graphics, mouseX, mouseY, x, y,
                        "menu.wrights.place_stairs_dir", "menu.wrights.place_stairs_dir_hint");
                y += 12;
                y = drawPair(graphics, mouseX, mouseY, x, y, leftW,
                        !WrightsClientConfig.placeStairsUp(),
                        "menu.wrights.place_stairs_up",
                        "menu.wrights.place_stairs_down",
                        "place_stairs_dir");
                y += 4;
            } else {
                boolean exchangeLine = form.line();
                boolean exchangePlate = form.plate();
                if (exchangeLine) {
                    WrightsClientConfig.enforceExchangeLineFootprint();
                }
                if (exchangePlate) {
                    WrightsClientConfig.syncExchangeEqualDims();
                }
                Shapes.Kind kind = WrightsClientConfig.exchangeKind();
                int axis = WrightsClientConfig.SHAPE_PLANE.get();
                y = drawFormDimensions(graphics, mouseX, mouseY, x, y, leftW, kind, form, exchangeLine, exchangePlate,
                        kind == Shapes.Kind.RING, kind == Shapes.Kind.BOX, axis);
                if (WrightsClientConfig.placeFormYawRotatable()) {
                    drawField(graphics, mouseX, mouseY, x, y,
                            "menu.wrights.shape_rotation", "menu.wrights.place_form_rotation_hint");
                    y += 12;
                    y = drawActionPair(graphics, mouseX, mouseY, x, y, leftW,
                            "screen.wrights.rotate_left",
                            "screen.wrights.rotate_right",
                            "place_yaw");
                    y += 4;
                }
            }
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_materials", "menu.wrights.shape_materials_hint", false);
            y = drawMaterials(graphics, mouseX, mouseY, x, y, leftW);
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_priority", "menu.wrights.shape_priority_hint", false);
            y = drawPriority(graphics, mouseX, mouseY, x, y, leftW);
            drawField(graphics, mouseX, mouseY, x, y, "menu.wrights.exchange_pivot", "menu.wrights.exchange_pivot_hint");
            y += 12;
            y = drawExchangePivot(graphics, mouseX, mouseY, x, y, leftW);
        } else {
            y = beginSection(graphics, mouseX, mouseY, x, y, lineEnd,
                    "menu.wrights.shape_materials", "menu.wrights.shape_materials_hint", false);
            y = drawMaterials(graphics, mouseX, mouseY, x, y, leftW);
        }

        if (WrightsConfig.allowShapeVoid()) {
            y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_VOID.get(),
                    "menu.wrights.shape_void", "menu.wrights.place_void_hint", "void");
        }
        return y;
    }

    private int drawPathOffsetWidth(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int leftW, boolean stairs) {
        drawField(graphics, mouseX, mouseY, x, y,
                stairs ? "menu.wrights.place_stairs_height" : "menu.wrights.place_bridge_offset",
                stairs ? "menu.wrights.place_stairs_height_hint" : "menu.wrights.place_bridge_offset_hint");
        y += 12;
        bridgeY = y;
        drawDimSlider(graphics, mouseX, mouseY, x, y, leftW,
                WrightsClientConfig.placeBridgeOffset(), SLIDER_BRIDGE);
        y += 18;
        drawField(graphics, mouseX, mouseY, x, y,
                stairs ? "menu.wrights.place_stairs_width" : "menu.wrights.place_bridge_width",
                stairs ? "menu.wrights.place_stairs_width_hint" : "menu.wrights.place_bridge_width_hint");
        y += 12;
        pathWidthY = y;
        drawDimSlider(graphics, mouseX, mouseY, x, y, leftW,
                WrightsClientConfig.placeBridgeWidth(), SLIDER_PATH_WIDTH);
        return y + 18;
    }

    private int drawFormDimensions(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int leftW,
            Shapes.Kind kind,
            Shapes.ExchangeForm exchangeForm,
            boolean exchangeLine,
            boolean exchangePlate,
            boolean ring,
            boolean box,
            int axis) {
        if (exchangeLine) {
            boolean pillar = exchangeForm == Shapes.ExchangeForm.PILLAR;
            drawField(graphics, mouseX, mouseY, x, y,
                    pillar ? "menu.wrights.shape_height" : "menu.wrights.exchange_beam_length",
                    pillar ? "menu.wrights.exchange_pillar_height_hint" : "menu.wrights.exchange_beam_length_hint");
            y += 12;
            heightY = y;
            drawDimSlider(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.exchangeLineLength(), SLIDER_HEIGHT);
            return y + 18;
        }
        if (exchangePlate) {
            boolean equal = WrightsClientConfig.exchangeEqualSides();
            y = drawToggle(graphics, mouseX, mouseY, x, y, leftW, equal,
                    "menu.wrights.exchange_equal", "menu.wrights.exchange_equal_hint", "exchange_equal");
        }
        drawField(graphics, mouseX, mouseY, x, y, sizeLabel(kind, axis), sizeHint(kind, axis));
        y += 12;
        sizeY = y;
        drawDimSlider(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_SIZE.get(), SLIDER_SIZE);
        y += 18;
        if (ring || box) {
            drawField(graphics, mouseX, mouseY, x, y,
                    box ? "menu.wrights.shape_depth" : "menu.wrights.shape_thickness",
                    box ? "menu.wrights.shape_depth_hint" : "menu.wrights.shape_thickness_hint");
            y += 12;
            thicknessY = y;
            int thickness = Mth.clamp(WrightsClientConfig.SHAPE_THICKNESS.get(), 1, axisMax());
            drawDimSlider(graphics, mouseX, mouseY, x, y, leftW, thickness, SLIDER_THICKNESS);
            y += 18;
        }
        if (kind.usesHeight()) {
            drawField(graphics, mouseX, mouseY, x, y, alongLabel(kind, axis), alongHint(kind, axis));
            y += 12;
            heightY = y;
            drawDimSlider(graphics, mouseX, mouseY, x, y, leftW, WrightsClientConfig.SHAPE_HEIGHT.get(), SLIDER_HEIGHT);
            y += 18;
        }
        return y;
    }

    private int drawKinds(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        int gap = 4;
        int cols = 3;
        int rowH = 26;
        int labelH = 12;
        int sectionGap = 6;
        int cardW = Math.max(36, (width - gap * (cols - 1)) / cols);
        if (formPresets()) {
            java.util.List<Shapes.ExchangeForm> forms = placeMode
                    ? java.util.List.of(
                            Shapes.ExchangeForm.FLOOR,
                            Shapes.ExchangeForm.WALL,
                            Shapes.ExchangeForm.PILLAR,
                            Shapes.ExchangeForm.BEAM,
                            Shapes.ExchangeForm.CIRCLE,
                            Shapes.ExchangeForm.STAIRS)
                    : java.util.List.of(
                            Shapes.ExchangeForm.FLOOR,
                            Shapes.ExchangeForm.WALL,
                            Shapes.ExchangeForm.PILLAR,
                            Shapes.ExchangeForm.BEAM,
                            Shapes.ExchangeForm.CIRCLE);
            int rows = Math.max(1, (forms.size() + cols - 1) / cols);
            int cursor = y;
            graphics.drawString(font, Component.translatable("menu.wrights.exchange_form_presets"), x, cursor, WrightsUi.MUTED, false);
            cursor += labelH;
            drawExchangeFormGrid(graphics, mouseX, mouseY, x, cursor, width, cardW, gap, cols, rowH, forms);
            return y + labelH + rows * rowH;
        }
        java.util.List<Shapes.Kind> flat = new java.util.ArrayList<>();
        java.util.List<Shapes.Kind> volume = new java.util.ArrayList<>();
        for (Shapes.Kind entry : Shapes.Kind.values()) {
            if (entry.volume()) {
                volume.add(entry);
            } else {
                flat.add(entry);
            }
        }
        int flatRows = Math.max(1, (flat.size() + cols - 1) / cols);
        int volumeRows = Math.max(1, (volume.size() + cols - 1) / cols);
        int gridH = labelH + flatRows * rowH + sectionGap + labelH + volumeRows * rowH;
        int cursor = y;
        graphics.drawString(font, Component.translatable("menu.wrights.shape_kind_2d"), x, cursor, WrightsUi.MUTED, false);
        cursor += labelH;
        cursor = drawKindGrid(graphics, mouseX, mouseY, x, cursor, width, cardW, gap, cols, rowH, flat);
        cursor += sectionGap;
        graphics.drawString(font, Component.translatable("menu.wrights.shape_kind_3d"), x, cursor, WrightsUi.MUTED, false);
        cursor += labelH;
        drawKindGrid(graphics, mouseX, mouseY, x, cursor, width, cardW, gap, cols, rowH, volume);
        return y + gridH;
    }

    private void drawExchangeFormGrid(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int gridW,
            int cardW,
            int gap,
            int cols,
            int rowH,
            java.util.List<Shapes.ExchangeForm> forms) {
        int selected = WrightsClientConfig.EXCHANGE_FORM.get();
        for (int i = 0; i < forms.size(); i++) {
            Shapes.ExchangeForm entry = forms.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = y + row * rowH;
            int cw = col == cols - 1 ? x + gridW - cx : cardW;
            boolean on = selected == entry.ordinal();
            boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + 22;
            WrightsUi.choice(graphics, cx, cy, cw, 22, on, hover);
            String label = font.plainSubstrByWidth(
                    Component.translatable(entry.langKey()).getString(),
                    Math.max(8, cw - 10));
            graphics.drawString(font, label, cx + 5, cy + 7, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
            hits.add(new Hit(cx, cy, cw, 22, "exchange_form", entry.ordinal()));
        }
    }

    private int drawKindGrid(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int gridW, int cardW, int gap, int cols, int rowH, java.util.List<Shapes.Kind> kinds) {
        int selected = WrightsClientConfig.SHAPE_KIND.get();
        for (int i = 0; i < kinds.size(); i++) {
            Shapes.Kind entry = kinds.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = y + row * rowH;
            int cw = col == cols - 1 ? x + gridW - cx : cardW;
            boolean on = selected == entry.ordinal();
            boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + 22;
            WrightsUi.choice(graphics, cx, cy, cw, 22, on, hover);
            String label = font.plainSubstrByWidth(
                    Component.translatable("menu.wrights.shape_kind_" + entry.name().toLowerCase()).getString(),
                    Math.max(8, cw - 10));
            graphics.drawString(font, label, cx + 5, cy + 7, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
            hits.add(new Hit(cx, cy, cw, 22, "kind", entry.ordinal()));
        }
        int rows = Math.max(1, (kinds.size() + cols - 1) / cols);
        return y + rows * rowH;
    }

    private void drawShapePreview(GuiGraphics graphics, int x, int y, int width, int height) {
        refreshShapePreview();
        // Preview header, size (blue), then block count — same language as Jobs rows.
        int infoH = 34;
        WrightsUi.section(graphics, font, x, y, "menu.wrights.job_preview", x + width);
        Component size = Component.translatable("menu.wrights.size", previewSizeX, previewSizeY, previewSizeZ);
        graphics.drawString(font, font.plainSubstrByWidth(size.getString(), width), x, y + 12, WrightsUi.SIZE, false);
        Component blocks = previewBlocks < 0
                ? Component.translatable("menu.wrights.shape_preview_wire")
                : Component.translatable("menu.wrights.job_preview_blocks", WrightsUi.count(previewBlocks));
        graphics.drawString(font, font.plainSubstrByWidth(blocks.getString(), width), x, y + 22, WrightsUi.MUTED, false);
        previewBoxY = y + infoH;
        previewBoxH = Math.max(24, height - infoH);
        graphics.fill(x, previewBoxY, x + width, previewBoxY + previewBoxH, 0xFF101820);
        WrightsUi.border(graphics, x, previewBoxY, width, previewBoxH, 0xFF31404E);
        if (previewGhosts.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.wrights.preview_hidden"), x + width / 2, previewBoxY + previewBoxH / 2 - 4, WrightsUi.MUTED);
            return;
        }
        if (!previewSpinning) {
            previewYaw += 0.35F;
        }
        net.minecraft.core.BlockPos pin = Shapes.pivotBlock(
                BlockPos.ZERO,
                new BlockPos(Math.max(0, meshSizeX - 1), Math.max(0, meshSizeY - 1), Math.max(0, meshSizeZ - 1)),
                formPresets() ? WrightsClientConfig.exchangePivot() : WrightsClientConfig.SHAPE_PIVOT.get());
        BlueprintModelView.render(
                graphics,
                x + 1,
                previewBoxY + 1,
                width - 2,
                previewBoxH - 2,
                previewGhosts,
                meshSizeX,
                meshSizeY,
                meshSizeZ,
                previewYaw,
                previewPitch,
                true,
                pin.getX(),
                pin.getY(),
                pin.getZ());
        graphics.drawCenteredString(font, Component.translatable("menu.wrights.spin"), x + width / 2, previewBoxY + previewBoxH - 12, WrightsUi.MUTED);
    }

    private void refreshShapePreview() {
        List<ShapeMaterials.Entry> materials = WrightsClientConfig.shapeMaterials();
        ShapeMaterials.FillMode mode = WrightsClientConfig.shapeFillMode();
        int batch = WrightsClientConfig.SHAPE_ORDER_BATCH.get();
        // Bridge / Stairs use path cells — not the Exchange form footprint (world ghosts already do).
        if (placeMode && WrightsClientConfig.placePathStyle()) {
            refreshPlacePathPreview(materials, mode, batch);
            return;
        }
        Shapes.Kind kind = formPresets()
                ? WrightsClientConfig.exchangeKind()
                : Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
        if (formPresets() && WrightsClientConfig.exchangeForm().line()) {
            WrightsClientConfig.enforceExchangeLineFootprint();
        }
        if (formPresets() && WrightsClientConfig.exchangeForm().plate()) {
            WrightsClientConfig.syncExchangeEqualDims();
            kind = WrightsClientConfig.exchangeKind();
        }
        int size = Shapes.clampSize(WrightsClientConfig.SHAPE_SIZE.get());
        int height = Shapes.clampHeight(WrightsClientConfig.SHAPE_HEIGHT.get());
        int thickness = Shapes.clampHeight(WrightsClientConfig.SHAPE_THICKNESS.get());
        boolean hollow = WrightsClientConfig.SHAPE_HOLLOW.get() && WrightsConfig.allowShapeHollow();
        int plane = WrightsClientConfig.SHAPE_PLANE.get();
        // Same absolute yaw as the in-world preview so we reuse SelectionRenderer's cache.
        int yaw = Shapes.clampYaw(WrightsClientConfig.SHAPE_YAW.get());
        long key = previewMaterialsKey(kind, size, height, thickness, plane, yaw, hollow, materials, mode, batch);
        if (key == previewKey) {
            return;
        }
        previewKey = key;
        Shapes.Bounds fullBox = Shapes.bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, plane, yaw);
        previewSizeX = fullBox.sizeX();
        previewSizeY = fullBox.sizeY();
        previewSizeZ = fullBox.sizeZ();
        long scan = Shapes.scanVolume(kind, size, height, thickness);
        int soft = WrightsConfig.shapePreviewListCap();
        // Only materialize the full cell list when it stays cheap; otherwise show AABB size + a scaled mesh.
        if (scan <= soft) {
            SelectionRenderer.ensureShapeCache(kind, size, height, thickness, hollow, plane, yaw);
            List<BlockPos> full = SelectionRenderer.shapeCacheCells();
            previewBlocks = full.size();
        } else {
            // Exact count would allocate / walk millions of cells — skip that for the menu.
            previewBlocks = -1;
        }
        // Solid miniature: scale dims down to fit the bake budget instead of striding
        // (stride left growing gaps that looked worse the bigger the shape).
        int budget = WrightsConfig.previewSampleBudget();
        List<BlockPos> mesh;
        BlockPos origin;
        if (scan <= budget) {
            SelectionRenderer.ensureShapeCache(kind, size, height, thickness, hollow, plane, yaw);
            mesh = SelectionRenderer.shapeCacheCells();
            origin = SelectionRenderer.shapeCacheMin();
        } else {
            double scale = Math.cbrt((double) budget / (double) Math.max(1L, scan));
            int meshSize = Math.max(1, (int) Math.floor(size * scale));
            int meshHeight = Math.max(1, (int) Math.floor(height * scale));
            int meshThickness = Math.max(1, (int) Math.floor(thickness * scale));
            mesh = Shapes.blocks(kind, BlockPos.ZERO, meshSize, meshHeight, meshThickness, hollow, plane, yaw, budget);
            int guard = 0;
            while (mesh.size() >= budget && guard++ < 512 && (meshSize > 1 || meshHeight > 1 || meshThickness > 1)) {
                if (meshSize >= meshHeight && meshSize >= meshThickness && meshSize > 1) {
                    meshSize--;
                } else if (meshHeight >= meshThickness && meshHeight > 1) {
                    meshHeight--;
                } else if (meshThickness > 1) {
                    meshThickness--;
                } else {
                    break;
                }
                mesh = Shapes.blocks(kind, BlockPos.ZERO, meshSize, meshHeight, meshThickness, hollow, plane, yaw, budget);
            }
            origin = mesh.isEmpty() ? BlockPos.ZERO : Shapes.origin(mesh);
        }
        bakePreviewMesh(mesh, origin, materials, mode, batch, key);
    }

    /** Menu miniature for Bridge / Stairs — same cells as the in-world path ghosts. */
    private void refreshPlacePathPreview(
            List<ShapeMaterials.Entry> materials,
            ShapeMaterials.FillMode mode,
            int batch) {
        int offset = Math.max(1, WrightsClientConfig.placeBridgeOffset());
        int width = Math.max(1, WrightsClientConfig.placeBridgeWidth());
        long key = previewPathKey(offset, width, false, true, materials, mode, batch);
        if (key == previewKey) {
            return;
        }
        previewKey = key;
        List<BlockPos> mesh = WrightsClientState.placePathCells(
                BlockPos.ZERO,
                net.minecraft.core.Direction.SOUTH,
                offset,
                width,
                false,
                true);
        if (mesh.isEmpty()) {
            previewGhosts = List.of();
            previewBlocks = 0;
            previewSizeX = previewSizeY = previewSizeZ = 1;
            meshSizeX = meshSizeY = meshSizeZ = 1;
            return;
        }
        BlockPos origin = Shapes.origin(mesh);
        BlockPos extent = Shapes.extent(mesh);
        previewSizeX = extent.getX() - origin.getX() + 1;
        previewSizeY = extent.getY() - origin.getY() + 1;
        previewSizeZ = extent.getZ() - origin.getZ() + 1;
        previewBlocks = mesh.size();
        bakePreviewMesh(mesh, origin, materials, mode, batch, key);
    }

    private void bakePreviewMesh(
            List<BlockPos> mesh,
            BlockPos origin,
            List<ShapeMaterials.Entry> materials,
            ShapeMaterials.FillMode mode,
            int batch,
            long key) {
        if (mesh.isEmpty()) {
            previewGhosts = List.of();
            meshSizeX = meshSizeY = meshSizeZ = 1;
            return;
        }
        BlockPos extent = Shapes.extent(mesh);
        meshSizeX = extent.getX() - origin.getX() + 1;
        meshSizeY = extent.getY() - origin.getY() + 1;
        meshSizeZ = extent.getZ() - origin.getZ() + 1;
        List<BlockState> planned = ShapeMaterials.assign(
                materials,
                mesh.size(),
                mode,
                batch,
                new Random(key),
                true,
                item -> Long.MAX_VALUE / 4L,
                true);
        BlockState fallback = Blocks.STONE.defaultBlockState();
        if (planned.isEmpty()) {
            planned = new ArrayList<>(mesh.size());
            for (int i = 0; i < mesh.size(); i++) {
                planned.add(fallback);
            }
        }
        List<WrightsClientState.Ghost> ghosts = new ArrayList<>(mesh.size());
        int gMinX = Integer.MAX_VALUE;
        int gMinY = Integer.MAX_VALUE;
        int gMinZ = Integer.MAX_VALUE;
        int gMaxX = Integer.MIN_VALUE;
        int gMaxY = Integer.MIN_VALUE;
        int gMaxZ = Integer.MIN_VALUE;
        for (int i = 0; i < mesh.size(); i++) {
            BlockPos pos = mesh.get(i);
            BlockState state = i < planned.size() ? planned.get(i) : planned.get(planned.size() - 1);
            if (state == null || state.isAir()) {
                state = fallback;
            }
            int gx = pos.getX() - origin.getX();
            int gy = pos.getY() - origin.getY();
            int gz = pos.getZ() - origin.getZ();
            gMinX = Math.min(gMinX, gx);
            gMinY = Math.min(gMinY, gy);
            gMinZ = Math.min(gMinZ, gz);
            gMaxX = Math.max(gMaxX, gx);
            gMaxY = Math.max(gMaxY, gy);
            gMaxZ = Math.max(gMaxZ, gz);
            ghosts.add(new WrightsClientState.Ghost(gx, gy, gz, 1.0F, 1.0F, 1.0F, Block.getId(state)));
        }
        // Keep mesh AABB in lockstep with the baked ghosts (avoids frame/model drift).
        if (gMinX <= gMaxX) {
            meshSizeX = gMaxX - gMinX + 1;
            meshSizeY = gMaxY - gMinY + 1;
            meshSizeZ = gMaxZ - gMinZ + 1;
        }
        previewGhosts = List.copyOf(ghosts);
    }

    /** Fingerprint for Bridge / Stairs path miniature (distinct salt from shape keys). */
    private static long previewPathKey(
            int offset,
            int width,
            boolean stairs,
            boolean up,
            List<ShapeMaterials.Entry> materials,
            ShapeMaterials.FillMode mode,
            int batch) {
        long key = 0xB71D1C4E5A97F301L;
        key = key * 31L + offset;
        key = key * 31L + width;
        key = key * 31L + (stairs ? 1 : 0);
        key = key * 31L + (up ? 1 : 0);
        key = key * 31L + (mode == null ? 0 : mode.ordinal());
        key = key * 31L + batch;
        if (materials != null) {
            for (ShapeMaterials.Entry entry : materials) {
                if (entry == null) {
                    continue;
                }
                Item item = entry.item();
                key = key * 31L + (item == null ? 0 : Item.getId(item));
                key = key * 31L + entry.weight();
            }
        }
        return key;
    }

    /** Fingerprint so the sample rebuilds when materials, weights, or fill mode change. */
    private static long previewMaterialsKey(
            Shapes.Kind kind,
            int size,
            int height,
            int thickness,
            int plane,
            int yaw,
            boolean hollow,
            List<ShapeMaterials.Entry> materials,
            ShapeMaterials.FillMode mode,
            int batch) {
        long key = 0x9E3779B97F4A7C15L;
        key = key * 31L + kind.ordinal();
        key = key * 31L + size;
        key = key * 31L + height;
        key = key * 31L + thickness;
        key = key * 31L + plane;
        key = key * 31L + yaw;
        key = key * 31L + (hollow ? 1 : 0);
        key = key * 31L + (mode == null ? 0 : mode.ordinal());
        key = key * 31L + batch;
        if (materials != null) {
            for (ShapeMaterials.Entry entry : materials) {
                if (entry == null) {
                    continue;
                }
                Item item = entry.item();
                key = key * 31L + (item == null ? 0 : Item.getId(item));
                key = key * 31L + entry.weight();
            }
        }
        return key;
    }

    /**
     * Exchange pin control: 3×3 for Floor/Wall/Circle; three points along the length for Pillar/Beam.
     */
    private int drawExchangePivot(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        int selected = WrightsClientConfig.exchangePivotSlot();
        int gap = 4;
        if (WrightsClientConfig.exchangeForm().line()) {
            boolean pillar = WrightsClientConfig.exchangeForm() == Shapes.ExchangeForm.PILLAR;
            int cell = Math.min(32, Math.max(20, pillar ? Math.min(width, 72) : (width - gap * 2) / 3));
            // Pillar: top → mid → bottom. Beam: min end → mid → max end.
            int[] order = pillar ? new int[] {2, 1, 0} : new int[] {0, 1, 2};
            String[] keys = pillar
                    ? new String[] {
                            "menu.wrights.exchange_pivot_bottom",
                            "menu.wrights.exchange_pivot_middle",
                            "menu.wrights.exchange_pivot_top"
                    }
                    : new String[] {
                            "menu.wrights.exchange_pivot_end_a",
                            "menu.wrights.exchange_pivot_middle",
                            "menu.wrights.exchange_pivot_end_b"
                    };
            if (pillar) {
                int gridX = x + Math.max(0, (width - cell) / 2);
                for (int i = 0; i < 3; i++) {
                    int line = order[i];
                    int cy = y + i * (cell + gap);
                    boolean on = line == selected;
                    boolean hover = mouseX >= gridX && mouseX < gridX + cell && mouseY >= cy && mouseY < cy + cell;
                    WrightsUi.choice(graphics, gridX, cy, cell, cell, on, hover);
                    String mark = line == 1 ? "●" : "·";
                    graphics.drawCenteredString(font, mark, gridX + cell / 2, cy + cell / 2 - 4, on ? WrightsUi.TEXT : WrightsUi.MUTED);
                    hits.add(new Hit(gridX, cy, cell, cell, "exchange_pivot", line));
                    if (hover) {
                        tip = Component.translatable(keys[line]);
                    }
                }
                return y + cell * 3 + gap * 2 + 8;
            }
            int gridW = cell * 3 + gap * 2;
            int gridX = x + Math.max(0, (width - gridW) / 2);
            for (int i = 0; i < 3; i++) {
                int line = order[i];
                int cx = gridX + i * (cell + gap);
                boolean on = line == selected;
                boolean hover = mouseX >= cx && mouseX < cx + cell && mouseY >= y && mouseY < y + cell;
                WrightsUi.choice(graphics, cx, y, cell, cell, on, hover);
                String mark = line == 1 ? "●" : "─";
                graphics.drawCenteredString(font, mark, cx + cell / 2, y + cell / 2 - 4, on ? WrightsUi.TEXT : WrightsUi.MUTED);
                hits.add(new Hit(cx, y, cell, cell, "exchange_pivot", line));
                if (hover) {
                    tip = Component.translatable(keys[line]);
                }
            }
            return y + cell + 8;
        }
        int cell = Math.min(28, Math.max(18, (width - gap * 2) / 3));
        int gridW = cell * 3 + gap * 2;
        int gridX = x + Math.max(0, (width - gridW) / 2);
        String[] keys = {
                "screen.wrights.shape_pivot_slot_nw",
                "screen.wrights.shape_pivot_slot_n",
                "screen.wrights.shape_pivot_slot_ne",
                "screen.wrights.shape_pivot_slot_w",
                "screen.wrights.shape_pivot_slot_c",
                "screen.wrights.shape_pivot_slot_e",
                "screen.wrights.shape_pivot_slot_sw",
                "screen.wrights.shape_pivot_slot_s",
                "screen.wrights.shape_pivot_slot_se"
        };
        for (int slot = 0; slot < 9; slot++) {
            int col = slot % 3;
            int row = slot / 3;
            int cx = gridX + col * (cell + gap);
            int cy = y + row * (cell + gap);
            boolean on = slot == selected;
            boolean hover = mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + cell;
            WrightsUi.choice(graphics, cx, cy, cell, cell, on, hover);
            String mark = switch (slot) {
                case 4 -> "●";
                case 1, 7 -> "─";
                case 3, 5 -> "|";
                default -> "·";
            };
            graphics.drawCenteredString(font, mark, cx + cell / 2, cy + cell / 2 - 4, on ? WrightsUi.TEXT : WrightsUi.MUTED);
            hits.add(new Hit(cx, cy, cell, cell, "exchange_pivot", slot));
            if (hover) {
                tip = Component.translatable(keys[slot]);
            }
        }
        return y + cell * 3 + gap * 2 + 8;
    }

    private int drawPriority(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        String line = font.plainSubstrByWidth(WrightsClientConfig.supplyOrderSummary(), Math.max(8, width));
        graphics.drawString(font, line, x, y, WrightsUi.MUTED, false);
        y += 12;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18;
        WrightsUi.choice(graphics, x, y, width, 18, false, hover);
        graphics.drawCenteredString(font, Component.translatable("menu.wrights.shape_priority_edit"), x + width / 2, y + 5, WrightsUi.TEXT);
        hits.add(new Hit(x, y, width, 18, "priority", 0));
        return y + 22;
    }

    private int drawMini(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String text, String id, int value, boolean on) {
        boolean hover = on && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + w, y + 14, on ? (hover ? 0xFF3A5164 : 0xFF1C2A37) : 0xFF141A22);
        WrightsUi.border(graphics, x, y, w, 14, on ? WrightsUi.COPPER_DIM : 0xFF2A343C);
        graphics.drawCenteredString(font, text, x + w / 2, y + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED);
        if (on) {
            hits.add(new Hit(x, y, w, 14, id, value));
        }
        return x;
    }

    private int drawStatus(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        boolean placed = WrightsClientConfig.SHAPE_SET.get();
        int boxH = placed ? 54 : 42;
        WrightsUi.status(graphics, x, y, width, boxH, placed ? WrightsUi.TEAL : WrightsUi.COPPER_DIM);
        if (!placed) {
            graphics.drawString(font, Component.translatable("menu.wrights.shape_none"), x + 10, y + 8, WrightsUi.TEXT, false);
            int btnY = y + 24;
            int btnW = Math.min(88, width - 20);
            drawAction(graphics, mouseX, mouseY, x + 8, btnY, btnW, "menu.wrights.shape_change", "change", 0xFF1C2A37, 0xFF3A5164, WrightsUi.TEXT);
        } else {
            var anchor = WrightsClientConfig.shapeAnchor();
            graphics.drawString(font, Component.translatable("menu.wrights.shape_anchor",
                    anchor.getX(), anchor.getY(), anchor.getZ()), x + 10, y + 6, WrightsUi.TEXT, false);
            String dim = WrightsClientConfig.SHAPE_DIM.get();
            String dimLabel = dim == null || dim.isBlank() ? "-" : dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
            graphics.drawString(font, font.plainSubstrByWidth(
                    Component.translatable("menu.wrights.shape_anchor_dim", dimLabel).getString(), width - 24),
                    x + 10, y + 18, WrightsUi.MUTED, false);
            int btnY = y + 34;
            int gap = 4;
            int btnW = Math.max(48, (width - 16 - gap * 2) / 3);
            int bx = x + 8;
            drawAction(graphics, mouseX, mouseY, bx, btnY, btnW, "menu.wrights.shape_highlight", "highlight", 0xFF1A3A38, 0xFF245850, WrightsUi.TEAL);
            bx += btnW + gap;
            drawAction(graphics, mouseX, mouseY, bx, btnY, btnW, "menu.wrights.shape_clear", "clear", 0xFF3A2428, 0xFF5A3030, WrightsUi.RED);
            bx += btnW + gap;
            int changeW = Math.max(48, x + width - 8 - bx);
            drawAction(graphics, mouseX, mouseY, bx, btnY, changeW, "menu.wrights.shape_change", "change", 0xFF1C2A37, 0xFF3A5164, WrightsUi.TEXT);
        }
        return y + boxH + 4;
    }

    private void drawAction(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String label, String id, int fill, int hoverFill, int ink) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + w, y + 14, hover ? hoverFill : fill);
        graphics.drawCenteredString(font, Component.translatable(label), x + w / 2, y + 3, ink);
        hits.add(new Hit(x, y, w, 14, id, 0));
    }

    private int drawPair(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, boolean second, String left, String right, String id) {
        int gap = 6;
        int half = (width - gap) / 2;
        drawChoice(graphics, mouseX, mouseY, x, y, half, 24, !second, left, id, 0);
        drawChoice(graphics, mouseX, mouseY, x + half + gap, y, width - half - gap, 24, second, right, id, 1);
        return y + 28;
    }

    /** Two side-by-side action buttons (neither stays selected). */
    private int drawActionPair(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, String left, String right, String id) {
        int gap = 6;
        int half = (width - gap) / 2;
        drawChoice(graphics, mouseX, mouseY, x, y, half, 24, false, left, id, 0);
        drawChoice(graphics, mouseX, mouseY, x + half + gap, y, width - half - gap, 24, false, right, id, 1);
        return y + 28;
    }

    private int drawTriple(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int selected, String a, String b, String c, String id) {
        int gap = 4;
        int w = Math.max(36, (width - gap * 2) / 3);
        drawChoice(graphics, mouseX, mouseY, x, y, w, 22, selected == 0, a, id, 0);
        drawChoice(graphics, mouseX, mouseY, x + w + gap, y, w, 22, selected == 1, b, id, 1);
        drawChoice(graphics, mouseX, mouseY, x + (w + gap) * 2, y, x + width - (x + (w + gap) * 2), 22, selected == 2, c, id, 2);
        return y + 26;
    }

    private int drawQuad(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int selected, String a, String b, String c, String d, String id) {
        int gap = 4;
        int w = Math.max(28, (width - gap * 3) / 4);
        drawChoice(graphics, mouseX, mouseY, x, y, w, 22, selected == 0, a, id, 0);
        drawChoice(graphics, mouseX, mouseY, x + w + gap, y, w, 22, selected == 1, b, id, 1);
        drawChoice(graphics, mouseX, mouseY, x + (w + gap) * 2, y, w, 22, selected == 2, c, id, 2);
        drawChoice(graphics, mouseX, mouseY, x + (w + gap) * 3, y, x + width - (x + (w + gap) * 3), 22, selected == 3, d, id, 3);
        return y + 26;
    }

    private int drawToggle(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int columnW, boolean on, String label, String hint, String id) {
        hits.add(new Hit(x, y, Math.max(24, columnW), 14, id, on ? 1 : 0));
        graphics.fill(x, y, x + 12, y + 12, 0xFF18222C);
        WrightsUi.border(graphics, x, y, 12, 12, on ? WrightsUi.COPPER : 0xFF3D4E5E);
        if (on) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, WrightsUi.COPPER);
        }
        String title = font.plainSubstrByWidth(Component.translatable(label).getString(), Math.max(8, columnW - 32));
        graphics.drawString(font, title, x + 16, y + 2, WrightsUi.TEXT, false);
        int hx = x + 16 + font.width(title) + 4;
        boolean over = mouseX >= hx && mouseX < hx + 12 && mouseY >= y && mouseY < y + 12;
        graphics.fill(hx, y, hx + 12, y + 12, 0xFF18222C);
        WrightsUi.border(graphics, hx, y, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(font, "?", hx + 6, y + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        if (over) {
            tip = Component.translatable(hint);
        }
        return y + 16;
    }

    private void drawChoice(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, boolean on, String title, String id, int value) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        WrightsUi.choice(graphics, x, y, w, h, on, hover);
        String label = font.plainSubstrByWidth(Component.translatable(title).getString(), Math.max(8, w - 12));
        graphics.drawString(font, label, x + 6, y + (h - 8) / 2, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        hits.add(new Hit(x, y, w, h, id, value));
    }

    /** Copper section header with trailing rule; returns Y under the title. */
    private int beginSection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int lineEnd, String title, String hint, boolean first) {
        if (!first) {
            WrightsUi.sectionBreak(graphics, x, y, Math.max(0, lineEnd - x));
            y += 8;
        }
        int titleW = WrightsUi.section(graphics, font, x, y, title, lineEnd);
        drawHelpBox(graphics, mouseX, mouseY, x + titleW + 4, y - 2, hint);
        return y + 12;
    }

    /** Muted field label under a section (optional ? tip). */
    private void drawField(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label, String hint) {
        int titleW = WrightsUi.fieldLabel(graphics, font, x, y, label);
        if (hint != null && !hint.isEmpty()) {
            drawHelpBox(graphics, mouseX, mouseY, x + titleW + 4, y - 2, hint);
        }
    }

    private void drawHelpBox(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String hint) {
        boolean over = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        graphics.fill(x, y, x + 12, y + 12, 0xFF18222C);
        WrightsUi.border(graphics, x, y, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(font, "?", x + 6, y + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        if (over) {
            tip = Component.translatable(hint);
        }
    }

    private void drawSlider(GuiGraphics graphics, int x, int y, int width, int value, int max) {
        graphics.fill(x, y + 4, x + width, y + 8, 0xFF1A2836);
        int thumb = max <= 0 ? 0 : width * Mth.clamp(value, 0, max) / max;
        graphics.fill(x, y + 4, x + Math.max(2, thumb), y + 8, WrightsUi.COPPER);
        graphics.fill(x + Math.max(0, thumb - 2), y + 1, x + Math.max(4, thumb + 2), y + 11, WrightsUi.COPPER);
    }

    /**
     * Dimension row: {@code −} track {@code +} value.
     * Buttons use {@link WrightsUi#nudgeStep()} (Ctrl/Shift/Alt).
     */
    private void drawDimSlider(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int leftW, int value, int slider) {
        int stepW = 14;
        int gap = 2;
        int valueReserve = 36;
        sizeW = Math.max(28, leftW - stepW * 2 - gap * 2 - valueReserve);
        sizeTrackX = x + stepW + gap;
        int max = (slider == SLIDER_BRIDGE || slider == SLIDER_PATH_WIDTH || slider == SLIDER_PATH_HEIGHT) ? 12 : axisMax();
        int clamped = Mth.clamp(value, 1, max);
        String downId = switch (slider) {
            case SLIDER_HEIGHT -> "height_down";
            case SLIDER_THICKNESS -> "thickness_down";
            case SLIDER_BRIDGE -> "bridge_down";
            case SLIDER_PATH_WIDTH -> "path_width_down";
            case SLIDER_PATH_HEIGHT -> "path_height_down";
            default -> "size_down";
        };
        String upId = switch (slider) {
            case SLIDER_HEIGHT -> "height_up";
            case SLIDER_THICKNESS -> "thickness_up";
            case SLIDER_BRIDGE -> "bridge_up";
            case SLIDER_PATH_WIDTH -> "path_width_up";
            case SLIDER_PATH_HEIGHT -> "path_height_up";
            default -> "size_up";
        };
        drawMini(graphics, mouseX, mouseY, x, y - 1, stepW, "-", downId, 0, clamped > 1);
        drawSlider(graphics, sizeTrackX, y, sizeW, clamped - 1, Math.max(1, max - 1));
        drawMini(graphics, mouseX, mouseY, sizeTrackX + sizeW + gap, y - 1, stepW, "+", upId, 0, clamped < max);
        drawSliderValue(graphics, mouseX, mouseY, sizeTrackX + sizeW + gap + stepW + 4, y, clamped, slider);
    }

    /** Number to the right of a slider — click to type; copper outline while editing. */
    private void drawSliderValue(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int value, int slider) {
        boolean editing = editingSlider == slider;
        String shown = editing ? editText : Integer.toString(value);
        int textW = font.width(shown.isEmpty() ? "0" : shown);
        int w = Math.max(28, textW + 8);
        boolean hover = !editing && mouseX >= x - 2 && mouseX < x - 2 + w + 2 && mouseY >= y - 1 && mouseY < y + 11;
        if (editing || hover) {
            graphics.fill(x - 2, y - 1, x - 2 + w + 2, y + 11, editing ? 0xFF1C2A37 : 0xFF18222C);
            WrightsUi.border(graphics, x - 2, y - 1, w + 2, 12, editing ? WrightsUi.COPPER : 0xFF3D4E5E);
        }
        graphics.drawString(font, shown.isEmpty() ? "_" : shown, x, y, editing ? WrightsUi.COPPER : WrightsUi.TEXT, false);
        switch (slider) {
            case SLIDER_SIZE -> {
                sizeValueX = x - 2;
                sizeValueW = w + 2;
            }
            case SLIDER_HEIGHT -> {
                heightValueX = x - 2;
                heightValueW = w + 2;
            }
            case SLIDER_THICKNESS -> {
                thicknessValueX = x - 2;
                thicknessValueW = w + 2;
            }
            case SLIDER_BRIDGE -> {
                bridgeValueX = x - 2;
                bridgeValueW = w + 2;
            }
            case SLIDER_PATH_WIDTH -> {
                pathWidthValueX = x - 2;
                pathWidthValueW = w + 2;
            }
            case SLIDER_PATH_HEIGHT -> {
                pathHeightValueX = x - 2;
                pathHeightValueW = w + 2;
            }
            default -> {
            }
        }
    }

    private record Hit(int x, int y, int w, int h, String id, int value) {
    }
}
