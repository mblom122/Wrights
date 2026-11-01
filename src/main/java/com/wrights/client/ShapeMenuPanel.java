package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
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
    private int sizeW;
    private int selectedMaterial;
    private final List<Hit> hits = new ArrayList<>();
    private Component tip;
    private ItemStack hoveredStack = ItemStack.EMPTY;

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
        boolean volume = Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get()).volume();
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_size", "menu.wrights.shape_size_hint");
        y += 12;
        sizeY = y;
        sizeW = Math.max(40, width - 40);
        int size = WrightsClientConfig.SHAPE_SIZE.get();
        int max = Math.max(1, Math.min(64, WrightsConfig.MAX_AXIS.getAsInt()));
        drawSlider(graphics, x, y, sizeW, size - 1, Math.max(1, max - 1));
        graphics.drawString(font, Integer.toString(size), x + sizeW + 8, y, WrightsUi.TEXT, false);
        y += 18;
        heightY = -1;
        if (volume) {
            drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_height", "menu.wrights.shape_height_hint");
            y += 12;
            heightY = y;
            int height = WrightsClientConfig.SHAPE_HEIGHT.get();
            drawSlider(graphics, x, y, sizeW, height - 1, Math.max(1, max - 1));
            graphics.drawString(font, Integer.toString(height), x + sizeW + 8, y, WrightsUi.TEXT, false);
            y += 18;
        }
        if (!volume) {
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
        if (WrightsConfig.allowShapeReplace()) {
            drawToggle(graphics, mouseX, mouseY, right, y - 16, col, WrightsClientConfig.SHAPE_REPLACE.get(), "menu.wrights.shape_replace", "menu.wrights.shape_replace_hint", "replace");
        }
        if (WrightsConfig.allowVoidBlocks()) {
            y = drawToggle(graphics, mouseX, mouseY, x, y, width, WrightsClientConfig.SHAPE_VOID.get(), "menu.wrights.shape_void", "menu.wrights.shape_void_hint", "void");
        }
        y += 6;
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
            case "up" -> {
                WrightsClientConfig.shapeSupplyMove(value, -1);
                yield true;
            }
            case "down" -> {
                WrightsClientConfig.shapeSupplyMove(value, 1);
                yield true;
            }
            case "remove" -> {
                WrightsClientConfig.shapeSupplyRemove(value);
                yield true;
            }
            case "add" -> {
                WrightsClientConfig.shapeSupplyAdd(value);
                yield true;
            }
            case "mat_select" -> {
                selectedMaterial = value;
                yield true;
            }
            case "mat_pick" -> {
                if (host != null) {
                    BlockPickerScreen.openBlocks(host, this::addMaterial);
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
            default -> false;
        };
    }

    private void addMaterial(BlockState state) {
        if (state == null || state.isAir()) {
            return;
        }
        WrightsClientConfig.shapeMaterialAdd(state.getBlock().asItem());
        selectedMaterial = Math.max(0, WrightsClientConfig.shapeMaterials().size() - 1);
    }

    private int drawMaterials(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        drawSection(graphics, mouseX, mouseY, x, y, "menu.wrights.shape_materials", "menu.wrights.shape_materials_hint");
        y += 12;
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
                graphics.renderItem(stack, cx + 2, cy + 2);
                String weight = Integer.toString(entries.get(i).weight());
                int wx = cx + CHIP - 1 - font.width(weight);
                graphics.fill(wx - 1, cy + CHIP - 9, cx + CHIP - 1, cy + CHIP - 1, 0xAA101820);
                graphics.drawString(font, weight, wx, cy + CHIP - 9, WrightsUi.COPPER, false);
                hits.add(new Hit(cx, cy, CHIP, CHIP, "mat_select", i));
                if (hover) {
                    hoveredStack = stack;
                }
            }
            int rows = (entries.size() + cols - 1) / cols;
            y += rows * (CHIP + gap) + 2;
            ShapeMaterials.Entry selected = entries.get(selectedMaterial);
            String name = font.plainSubstrByWidth(selected.item().getDefaultInstance().getHoverName().getString(), Math.max(40, width - 110));
            graphics.drawString(font, name, x, y + 4, WrightsUi.TEXT, false);
            int controls = x + width;
            controls = drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "x", "mat_remove", selectedMaterial, entries.size() > 1) - 2;
            controls = drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "+", "mat_weight_up", selectedMaterial, true) - 2;
            String weight = Integer.toString(selected.weight());
            int weightW = Math.max(18, font.width(weight) + 6);
            graphics.drawCenteredString(font, weight, controls - weightW / 2, y + 4, WrightsUi.COPPER);
            controls -= weightW + 2;
            drawMini(graphics, mouseX, mouseY, controls - 16, y + 1, 16, "-", "mat_weight_down", selectedMaterial, selected.weight() > 1);
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
        int cardW = Math.max(48, (width - gap * (cols - 1)) / cols);
        int kind = WrightsClientConfig.SHAPE_KIND.get();
        Shapes.Kind[] all = Shapes.Kind.values();
        for (int i = 0; i < all.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = y + row * 26;
            int cw = col == cols - 1 ? x + width - cx : cardW;
            boolean on = kind == i;
            boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + 22;
            WrightsUi.choice(graphics, cx, cy, cw, 22, on, hover);
            String label = font.plainSubstrByWidth(Component.translatable("menu.wrights.shape_kind_" + all[i].name().toLowerCase()).getString(), Math.max(8, cw - 10));
            graphics.drawString(font, label, cx + 5, cy + 7, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
            hits.add(new Hit(cx, cy, cw, 22, "kind", i));
        }
        return y + ((all.length + cols - 1) / cols) * 26;
    }

    private int drawPriority(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        List<Integer> order = WrightsClientConfig.shapeSupplyOrder();
        for (int i = 0; i < order.size(); i++) {
            int source = order.get(i);
            WrightsUi.choice(graphics, x, y, width, 18, false, mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18);
            graphics.drawString(font, (i + 1) + ". " + Component.translatable(ShapeSupply.labelKey(source)).getString(), x + 6, y + 5, WrightsUi.TEXT, false);
            int btn = 16;
            int right = x + width - 4;
            right = drawMini(graphics, mouseX, mouseY, right - btn, y + 1, btn, "↑", "up", i, i > 0);
            right = drawMini(graphics, mouseX, mouseY, right - btn - 2, y + 1, btn, "↓", "down", i, i < order.size() - 1);
            drawMini(graphics, mouseX, mouseY, right - btn - 2, y + 1, btn, "x", "remove", i, order.size() > 1);
            y += 20;
        }
        List<Integer> missing = ShapeSupply.missing(order);
        if (!missing.isEmpty()) {
            graphics.drawString(font, Component.translatable("menu.wrights.shape_priority_add"), x, y, WrightsUi.MUTED, false);
            y += 12;
            for (int source : missing) {
                boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 16;
                WrightsUi.choice(graphics, x, y, width, 16, false, hover);
                graphics.drawString(font, "+ " + Component.translatable(ShapeSupply.labelKey(source)).getString(), x + 6, y + 4, WrightsUi.COPPER, false);
                hits.add(new Hit(x, y, width, 16, "add", source));
                y += 18;
            }
        }
        return y;
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
        WrightsUi.status(graphics, x, y, width, placed ? 40 : 28, placed ? WrightsUi.TEAL : WrightsUi.COPPER_DIM);
        if (!placed) {
            graphics.drawString(font, Component.translatable("menu.wrights.shape_none"), x + 10, y + 10, WrightsUi.TEXT, false);
        } else {
            var anchor = WrightsClientConfig.shapeAnchor();
            graphics.drawString(font, Component.translatable("menu.wrights.shape_anchor", anchor.getX(), anchor.getY(), anchor.getZ()), x + 10, y + 8, WrightsUi.TEXT, false);
            int btnY = y + 22;
            int half = (width - 20) / 2;
            hits.add(new Hit(x + 8, btnY, half, 14, "highlight", 0));
            hits.add(new Hit(x + 12 + half, btnY, half, 14, "clear", 0));
            boolean hHover = mouseX >= x + 8 && mouseX < x + 8 + half && mouseY >= btnY && mouseY < btnY + 14;
            boolean cHover = mouseX >= x + 12 + half && mouseX < x + 12 + half + half && mouseY >= btnY && mouseY < btnY + 14;
            graphics.fill(x + 8, btnY, x + 8 + half, btnY + 14, hHover ? 0xFF245850 : 0xFF1A3A38);
            graphics.fill(x + 12 + half, btnY, x + 12 + half + half, btnY + 14, cHover ? 0xFF5A3030 : 0xFF3A2428);
            graphics.drawString(font, Component.translatable("menu.wrights.shape_highlight"), x + 14, btnY + 3, WrightsUi.TEAL, false);
            graphics.drawString(font, Component.translatable("menu.wrights.shape_clear"), x + 18 + half, btnY + 3, WrightsUi.RED, false);
        }
        return y + (placed ? 44 : 32);
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
