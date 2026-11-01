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

    private final Font font;
    private Screen host;
    private int bodyX;
    private int bodyW;
    private int viewTop;
    private int viewH;
    private int contentH;
    private int scroll;
    private int sizeY = -1;
    private int heightY = -1;
    private int thicknessY = -1;
    private int sizeW;
    private int selectedMaterial;
    private final List<Hit> hits = new ArrayList<>();
    private Component tip;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    /** Menu preview mesh — kept as one list so {@link BlueprintModelView} can reuse the bake. */
    private List<WrightsClientState.Ghost> previewGhosts = List.of();
    private int previewSizeX = 1;
    private int previewSizeY = 1;
    private int previewSizeZ = 1;
    private long previewKey = Long.MIN_VALUE;
    private float previewYaw = 35.0F;

    ShapeMenuPanel(Font font) {
        this.font = font;
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
        bodyX = x;
        bodyW = width;
        viewTop = top;
        viewH = Math.max(40, bottom - top);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - viewH));
        graphics.enableScissor(x - 4, viewTop, x + width + 4, viewTop + viewH);
        int y = viewTop - scroll;
        graphics.drawString(font, Component.translatable("menu.wrights.shape_title"), x, y, WrightsUi.COPPER, false);
        y += 14;
        y = drawKinds(graphics, mouseX, mouseY, x, y, width);
        y += 4;
        Shapes.Kind kind = Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
        boolean volume = kind.volume();
        boolean ring = kind == Shapes.Kind.RING;
        boolean box = kind == Shapes.Kind.CUBE;
        boolean cylinder = kind == Shapes.Kind.CYLINDER;
        boolean line = kind == Shapes.Kind.LINE;
        boolean widthShape = box || cylinder;
        drawSection(graphics, mouseX, mouseY, x, y,
                line ? "menu.wrights.shape_length" : widthShape ? "menu.wrights.shape_width" : "menu.wrights.shape_size",
                line ? "menu.wrights.shape_length_hint"
                        : cylinder ? "menu.wrights.shape_cylinder_width_hint"
                        : box ? "menu.wrights.shape_width_hint"
                        : "menu.wrights.shape_size_hint");
        y += 12;
        sizeY = y;
        sizeW = Math.max(40, width - 40);
        int size = WrightsClientConfig.SHAPE_SIZE.get();
        int max = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
        drawSlider(graphics, x, y, sizeW, size - 1, Math.max(1, max - 1));
        graphics.drawString(font, Integer.toString(size), x + sizeW + 8, y, WrightsUi.TEXT, false);
        y += 18;
        heightY = -1;
        thicknessY = -1;
        if (ring || line) {
            drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_thickness",
                    line ? "menu.wrights.shape_line_thickness_hint" : "menu.wrights.shape_thickness_hint");
            y += 12;
            thicknessY = y;
            int thicknessMax = line ? max : Math.max(1, size);
            int thickness = Math.min(WrightsClientConfig.SHAPE_THICKNESS.get(), thicknessMax);
            drawSlider(graphics, x, y, sizeW, thickness - 1, Math.max(1, thicknessMax - 1));
            graphics.drawString(font, Integer.toString(thickness), x + sizeW + 8, y, WrightsUi.TEXT, false);
            y += 18;
        }
        if (kind.usesHeight()) {
            String heightHint = ring ? "menu.wrights.shape_ring_height_hint"
                    : box ? "menu.wrights.shape_box_height_hint"
                    : cylinder ? "menu.wrights.shape_cylinder_height_hint"
                    : "menu.wrights.shape_height_hint";
            drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_height", heightHint);
            y += 12;
            heightY = y;
            int height = WrightsClientConfig.SHAPE_HEIGHT.get();
            drawSlider(graphics, x, y, sizeW, height - 1, Math.max(1, max - 1));
            graphics.drawString(font, Integer.toString(height), x + sizeW + 8, y, WrightsUi.TEXT, false);
            y += 18;
        }
        // Flat shapes tip with Plane. Box at height 1 keeps Plane for the flat-square shortcut.
        boolean showPlane = !volume || (box && WrightsClientConfig.SHAPE_HEIGHT.get() <= 1);
        if (showPlane) {
            drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_plane", "menu.wrights.shape_plane_hint");
            y += 12;
            y = drawTriple(graphics, mouseX, mouseY, x, y, width,
                    WrightsClientConfig.SHAPE_PLANE.get(),
                    "menu.wrights.shape_plane_xz",
                    "menu.wrights.shape_plane_xy",
                    "menu.wrights.shape_plane_yz",
                    "plane");
            y += 4;
        }
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_place", "menu.wrights.shape_place_hint");
        y += 12;
        y = drawPair(graphics, mouseX, mouseY, x, y, width,
                WrightsClientConfig.SHAPE_USE_ANCHOR.get(),
                "menu.wrights.shape_place_look",
                "menu.wrights.shape_place_anchor",
                "place");
        y += 4;
        int col = (width - 6) / 2;
        int right = x + col + 6;
        y = drawToggle(graphics, mouseX, mouseY, x, y, col, WrightsClientConfig.SHAPE_HOLLOW.get(), "menu.wrights.shape_hollow", "menu.wrights.shape_hollow_hint", "hollow");
        drawToggle(graphics, mouseX, mouseY, right, y - 16, col, WrightsClientConfig.SHAPE_FLIP_VERTICAL.get(), "menu.wrights.shape_flip", "menu.wrights.shape_flip_hint", "flip");
        if (WrightsConfig.allowShapeReplace()) {
            y = drawToggle(graphics, mouseX, mouseY, x, y, col, WrightsClientConfig.SHAPE_REPLACE.get(), "menu.wrights.shape_replace", "menu.wrights.shape_replace_hint", "replace");
            if (WrightsConfig.allowVoidBlocks()) {
                drawToggle(graphics, mouseX, mouseY, right, y - 16, col, WrightsClientConfig.SHAPE_VOID.get(), "menu.wrights.shape_void", "menu.wrights.shape_void_hint", "void");
            }
        } else if (WrightsConfig.allowVoidBlocks()) {
            y = drawToggle(graphics, mouseX, mouseY, x, y, width, WrightsClientConfig.SHAPE_VOID.get(), "menu.wrights.shape_void", "menu.wrights.shape_void_hint", "void");
        }
        y += 6;
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_anchor_section", "menu.wrights.shape_anchor_section_hint");
        y += 12;
        y = drawStatus(graphics, mouseX, mouseY, x, y, width);
        y += 8;
        y = drawMaterials(graphics, mouseX, mouseY, x, y, width);
        y += 8;
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_priority", "menu.wrights.shape_priority_hint");
        y += 12;
        y = drawPriority(graphics, mouseX, mouseY, x, y, width);
        contentH = y - (viewTop - scroll) + 8;
        graphics.disableScissor();
        if (contentH > viewH) {
            WrightsUi.scrollBar(graphics, x + width - 2, viewTop, viewH, scroll, contentH);
        }
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < bodyX - 4 || mouseX > bodyX + bodyW + 4 || mouseY < viewTop || mouseY > viewTop + viewH) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 24, 0, Math.max(0, contentH - viewH));
        return true;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (mouseX < bodyX - 4 || mouseX > bodyX + bodyW + 4 || mouseY < viewTop || mouseY > viewTop + viewH) {
            return false;
        }
        if (sizeY >= 0 && mouseY >= sizeY && mouseY < sizeY + 12 && mouseX >= bodyX && mouseX < bodyX + sizeW) {
            int max = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
            int value = 1 + (int) Math.round((mouseX - bodyX) * (max - 1) / Math.max(1, sizeW));
            WrightsClientConfig.setShapeSize(value);
            return true;
        }
        if (thicknessY >= 0 && mouseY >= thicknessY && mouseY < thicknessY + 12 && mouseX >= bodyX && mouseX < bodyX + sizeW) {
            int max = Math.max(1, Math.min(WrightsClientConfig.SHAPE_SIZE.get(), Math.min(64, WrightsConfig.MAX_AXIS.getAsInt())));
            int value = 1 + (int) Math.round((mouseX - bodyX) * (max - 1) / Math.max(1, sizeW));
            WrightsClientConfig.setShapeThickness(value);
            return true;
        }
        if (heightY >= 0 && mouseY >= heightY && mouseY < heightY + 12 && mouseX >= bodyX && mouseX < bodyX + sizeW) {
            int max = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
            int value = 1 + (int) Math.round((mouseX - bodyX) * (max - 1) / Math.max(1, sizeW));
            WrightsClientConfig.setShapeHeight(value);
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

    boolean dragSize(double mouseX) {
        if (sizeY < 0) {
            return false;
        }
        int max = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
        int value = 1 + (int) Math.round((mouseX - bodyX) * (max - 1) / Math.max(1, sizeW));
        WrightsClientConfig.setShapeSize(value);
        return true;
    }

    boolean dragHeight(double mouseX) {
        if (heightY < 0) {
            return false;
        }
        int max = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
        int value = 1 + (int) Math.round((mouseX - bodyX) * (max - 1) / Math.max(1, sizeW));
        WrightsClientConfig.setShapeHeight(value);
        return true;
    }

    boolean dragThickness(double mouseX) {
        if (thicknessY < 0) {
            return false;
        }
        int max = Math.max(1, Math.min(WrightsClientConfig.SHAPE_SIZE.get(), Math.min(64, WrightsConfig.MAX_AXIS.getAsInt())));
        int value = 1 + (int) Math.round((mouseX - bodyX) * (max - 1) / Math.max(1, sizeW));
        WrightsClientConfig.setShapeThickness(value);
        return true;
    }

    private boolean apply(String id, int value) {
        return switch (id) {
            case "kind" -> {
                WrightsClientConfig.setShapeKind(value);
                yield true;
            }
            case "plane" -> {
                WrightsClientConfig.setShapePlane(value);
                yield true;
            }
            case "place" -> {
                WrightsClientConfig.setShapeUseAnchor(value != 0);
                yield true;
            }
            case "hollow" -> {
                WrightsClientConfig.setShapeHollow(!WrightsClientConfig.SHAPE_HOLLOW.get());
                yield true;
            }
            case "flip" -> {
                WrightsClientConfig.setShapeFlipVertical(!WrightsClientConfig.SHAPE_FLIP_VERTICAL.get());
                yield true;
            }
            case "replace" -> {
                WrightsClientConfig.setShapeReplace(!WrightsClientConfig.SHAPE_REPLACE.get());
                yield true;
            }
            case "void" -> {
                WrightsClientConfig.setShapeVoid(!WrightsClientConfig.SHAPE_VOID.get());
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
                    WrightsClientConfig.shapeMaterialWeight(value, entries.get(value).weight() + 1);
                }
                yield true;
            }
            case "mat_weight_down" -> {
                List<ShapeMaterials.Entry> entries = WrightsClientConfig.shapeMaterials();
                if (value >= 0 && value < entries.size()) {
                    WrightsClientConfig.shapeMaterialWeight(value, entries.get(value).weight() - 1);
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
                WrightsClientConfig.setShapeOrderBatch(WrightsClientConfig.SHAPE_ORDER_BATCH.get() + 1);
                yield true;
            }
            case "batch_down" -> {
                WrightsClientConfig.setShapeOrderBatch(WrightsClientConfig.SHAPE_ORDER_BATCH.get() - 1);
                yield true;
            }
            default -> false;
        };
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
            if (!com.wrights.util.BlockFilters.canStore(state)) {
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
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_materials", "menu.wrights.shape_materials_hint");
        y += 12;
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

    private int drawKinds(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_kind", "menu.wrights.shape_kind_hint");
        y += 12;
        int gap = 4;
        int cols = 3;
        int rowH = 26;
        int labelH = 12;
        int sectionGap = 6;
        int previewGap = 8;
        int previewW = Math.max(40, (width - previewGap) * 2 / 5);
        int gridW = Math.max(72, width - previewGap - previewW);
        previewW = Math.max(40, width - previewGap - gridW);
        int cardW = Math.max(36, (gridW - gap * (cols - 1)) / cols);
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
        int previewX = x + gridW + previewGap;
        previewW = Math.min(previewW, Math.max(0, x + width - previewX));
        int cursor = y;
        graphics.drawString(font, Component.translatable("menu.wrights.shape_kind_2d"), x, cursor, WrightsUi.MUTED, false);
        cursor += labelH;
        cursor = drawKindGrid(graphics, mouseX, mouseY, x, cursor, gridW, cardW, gap, cols, rowH, flat);
        cursor += sectionGap;
        graphics.drawString(font, Component.translatable("menu.wrights.shape_kind_3d"), x, cursor, WrightsUi.MUTED, false);
        cursor += labelH;
        drawKindGrid(graphics, mouseX, mouseY, x, cursor, gridW, cardW, gap, cols, rowH, volume);
        drawShapePreview(graphics, previewX, y, previewW, gridH);
        return y + gridH;
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
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF31404E);
        refreshShapePreview();
        if (previewGhosts.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.wrights.preview_hidden"), x + width / 2, y + height / 2 - 4, WrightsUi.MUTED);
            return;
        }
        previewYaw += 0.35F;
        BlueprintModelView.render(graphics, x + 1, y + 1, width - 2, height - 2, previewGhosts, previewSizeX, previewSizeY, previewSizeZ, previewYaw, 28.0F);
    }

    private void refreshShapePreview() {
        Shapes.Kind kind = Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
        int size = Shapes.clampSize(WrightsClientConfig.SHAPE_SIZE.get());
        int height = Shapes.clampHeight(WrightsClientConfig.SHAPE_HEIGHT.get());
        int thickness = Shapes.clampHeight(WrightsClientConfig.SHAPE_THICKNESS.get());
        boolean hollow = WrightsClientConfig.SHAPE_HOLLOW.get();
        boolean flip = WrightsClientConfig.SHAPE_FLIP_VERTICAL.get();
        int plane = WrightsClientConfig.SHAPE_PLANE.get();
        int yaw = Shapes.clampYaw(WrightsClientConfig.SHAPE_YAW.get());
        List<ShapeMaterials.Entry> materials = WrightsClientConfig.shapeMaterials();
        ShapeMaterials.FillMode mode = WrightsClientConfig.shapeFillMode();
        int batch = WrightsClientConfig.SHAPE_ORDER_BATCH.get();
        long key = previewMaterialsKey(kind, size, height, thickness, plane, yaw, hollow, flip, materials, mode, batch);
        if (key == previewKey) {
            return;
        }
        previewKey = key;
        List<BlockPos> worlds = Shapes.blocks(kind, BlockPos.ZERO, size, height, thickness, hollow, plane, flip, yaw);
        if (worlds.isEmpty()) {
            previewGhosts = List.of();
            previewSizeX = previewSizeY = previewSizeZ = 1;
            return;
        }
        BlockPos origin = Shapes.origin(worlds);
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (BlockPos pos : worlds) {
            maxX = Math.max(maxX, pos.getX() - origin.getX());
            maxY = Math.max(maxY, pos.getY() - origin.getY());
            maxZ = Math.max(maxZ, pos.getZ() - origin.getZ());
        }
        previewSizeX = maxX + 1;
        previewSizeY = maxY + 1;
        previewSizeZ = maxZ + 1;
        if (worlds.size() > WrightsConfig.maxPreviewBlocks()) {
            previewGhosts = List.of();
            return;
        }
        List<BlockState> planned = ShapeMaterials.assign(
                materials,
                worlds.size(),
                mode,
                batch,
                new Random(key),
                true,
                item -> Long.MAX_VALUE / 4L,
                true);
        if (planned.isEmpty()) {
            BlockState fallback = Blocks.STONE.defaultBlockState();
            planned = new ArrayList<>(worlds.size());
            for (int i = 0; i < worlds.size(); i++) {
                planned.add(fallback);
            }
        }
        List<WrightsClientState.Ghost> ghosts = new ArrayList<>(worlds.size());
        for (int i = 0; i < worlds.size(); i++) {
            BlockPos pos = worlds.get(i);
            BlockState state = i < planned.size() ? planned.get(i) : planned.get(planned.size() - 1);
            if (state == null || state.isAir()) {
                state = Blocks.STONE.defaultBlockState();
            }
            ghosts.add(new WrightsClientState.Ghost(
                    pos.getX() - origin.getX(),
                    pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ(),
                    1.0F,
                    1.0F,
                    1.0F,
                    Block.getId(state)));
        }
        previewGhosts = List.copyOf(ghosts);
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
            boolean flip,
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
        key = key * 31L + (flip ? 1 : 0);
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

    private int drawPriority(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        List<Integer> order = WrightsClientConfig.shapeSupplyOrder();
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) {
                summary.append(" › ");
            }
            summary.append(Component.translatable(ShapeSupply.shortLabelKey(order.get(i))).getString());
        }
        String line = font.plainSubstrByWidth(summary.toString(), Math.max(8, width));
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

    private int drawTriple(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int selected, String a, String b, String c, String id) {
        int gap = 4;
        int w = Math.max(36, (width - gap * 2) / 3);
        drawChoice(graphics, mouseX, mouseY, x, y, w, 22, selected == 0, a, id, 0);
        drawChoice(graphics, mouseX, mouseY, x + w + gap, y, w, 22, selected == 1, b, id, 1);
        drawChoice(graphics, mouseX, mouseY, x + (w + gap) * 2, y, x + width - (x + (w + gap) * 2), 22, selected == 2, c, id, 2);
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

    private void drawSection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label, String hint) {
        Component text = Component.translatable(label);
        graphics.drawString(font, text, x, y, WrightsUi.MUTED, false);
        int hx = x + font.width(text) + 4;
        boolean over = mouseX >= hx && mouseX < hx + 12 && mouseY >= y - 2 && mouseY < y + 10;
        graphics.fill(hx, y - 2, hx + 12, y + 10, 0xFF18222C);
        WrightsUi.border(graphics, hx, y - 2, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(font, "?", hx + 6, y, over ? WrightsUi.COPPER : WrightsUi.MUTED);
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

    private record Hit(int x, int y, int w, int h, String id, int value) {
    }
}
