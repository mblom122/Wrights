package com.wrights.client;

import com.wrights.client.WrightsMenuScreen.HelpSpot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;

/** The Mirror page: mirror and mine mode settings, previews and their click handling. */
final class MirrorPanel {
    private final WrightsMenuScreen s;

    MirrorPanel(WrightsMenuScreen s) {
        this.s = s;
    }

    private int mirrorViewTop;

    private int mirrorModeY;

    private int mirrorCardY;

    private int mirrorStyleY = -1;

    private int mirrorStyleW;

    private int mirrorAxisY;

    private int mirrorAxisW;

    private int mirrorNbtX;

    private int mirrorNbtY;

    private int mirrorSettingsX;

    private int mirrorSettingsY;

    private int mirrorAllX;

    private int mirrorAllY;

    private int mirrorShapesX;

    private int mirrorShapesY;

    private int mirrorReplaceX;

    private int mirrorReplaceY = -1;

    private int mirrorOnX;

    private int mirrorOnY;

    private int mirrorToggleW;

    private int mirrorMineY = -1;

    private int mirrorMineModeY = -1;

    private int mirrorMineVoidY = -1;

    private int mirrorFaceY = -1;

    private int mirrorHighlightX;

    private int mirrorHighlightY = -1;

    private int mirrorHighlightW;

    private int mirrorClearX;

    private int mirrorClearW;

    private int mirrorClearY;

    private int mirrorPriorityY = -1;

    private int mirrorBodyW;

    private final List<HelpSpot> mirrorHelps = new ArrayList<>();

    void renderMirror(GuiGraphics graphics, int mouseX, int mouseY) {
        mirrorHelps.clear();
        s.mirrorTip = null;
        mirrorPriorityY = -1;
        int x = s.listX + 8;
        int w = Math.max(120, s.panelX + s.panelW - 28 - s.listX);
        s.mirrorBodyX = x;
        mirrorBodyW = w;
        mirrorViewTop = s.panelY + 40;
        // Keep Clear anchor and the rest above the shared Close button.
        int closeTop = s.closeButton != null && s.closeButton.visible ? s.closeButton.getY() - 6 : s.panelY + s.panelH - 28;
        s.mirrorViewH = Math.max(40, closeTop - mirrorViewTop);
        s.mirrorScroll = Mth.clamp(s.mirrorScroll, 0, Math.max(0, s.mirrorContentH - s.mirrorViewH));
        graphics.enableScissor(x - 4, mirrorViewTop, x + w + 4, mirrorViewTop + s.mirrorViewH);
        int y = mirrorViewTop - s.mirrorScroll;
        int lineEnd = x + w;
        graphics.drawString(s.font(), Component.translatable("menu.wrights.mirror_title"), x, y, WrightsUi.COPPER, false);
        y += 14;
        int gap = 6;
        boolean firstSection = true;
        y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.mirror_mode", "menu.wrights.mirror_mode_hint", firstSection);
        firstSection = false;
        boolean blocksOn = WrightsConfig.allowMirrorBlocks();
        boolean plansOn = WrightsConfig.allowMirrorBlueprints();
        int modeCount = (blocksOn ? 1 : 0) + (plansOn ? 1 : 0);
        int modeW = modeCount <= 1 ? w : (w - gap) / 2;
        mirrorModeY = y;
        int modeX = x;
        if (blocksOn) {
            int width = plansOn ? modeW : w;
            drawChoice(graphics, mouseX, mouseY, modeX, y, width, 28, WrightsClientConfig.mirrorBlocks(), "menu.wrights.mirror_blocks");
            modeX += width + gap;
        }
        if (plansOn) {
            drawChoice(graphics, mouseX, mouseY, modeX, y, x + w - modeX, 28, !blocksOn || !WrightsClientConfig.mirrorBlocks(), "menu.wrights.mirror_blueprints");
        }
        y += 36;
        int axes = WrightsClientConfig.MIRROR_AXES.get() & 7;
        boolean axisMode = WrightsConfig.allowMirrorAxes() && axes != 0;
        mirrorStyleY = -1;
        if (WrightsConfig.allowMirrorAxes()) {
            y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.mirror_style", "menu.wrights.mirror_style_hint", firstSection);
            firstSection = false;
            int styleW = (w - gap) / 2;
            mirrorStyleY = y;
            mirrorStyleW = styleW;
            drawChoice(graphics, mouseX, mouseY, x, y, styleW, 28, axisMode, "menu.wrights.mirror_style_axis");
            drawChoice(graphics, mouseX, mouseY, x + styleW + gap, y, w - styleW - gap, 28, !axisMode, "menu.wrights.mirror_style_face");
            y += 36;
        }
        mirrorCardY = -1;
        mirrorAxisY = -1;
        if (axisMode) {
            y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.mirror_axis", "menu.wrights.mirror_axis_hint", firstSection);
            firstSection = false;
            int axisW = Math.max(36, (w - gap * 2) / 3);
            mirrorAxisY = y;
            mirrorAxisW = axisW;
            s.mirrorCardW = axisW;
            String[] axisNames = {"menu.wrights.mirror_axis_x", "menu.wrights.mirror_axis_y", "menu.wrights.mirror_axis_z"};
            int[] axisBits = {com.wrights.blueprint.Mirror.AXIS_X, com.wrights.blueprint.Mirror.AXIS_Y, com.wrights.blueprint.Mirror.AXIS_Z};
            for (int i = 0; i < axisNames.length; i++) {
                int cardX = x + (axisW + gap) * i;
                int width = i == axisNames.length - 1 ? x + w - cardX : axisW;
                drawChoice(graphics, mouseX, mouseY, cardX, y, width, 28, (axes & axisBits[i]) != 0, axisNames[i]);
            }
            y += 36;
        } else {
            y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.mirror_pattern", "menu.wrights.mirror_pattern_hint", firstSection);
            firstSection = false;
            int copies = WrightsConfig.allowedPattern(WrightsClientConfig.MIRROR_PATTERN.get());
            int maxCopies = Math.max(1, WrightsConfig.maxMirrorCopies());
            String count = Integer.toString(copies);
            int numberW = Math.max(16, s.font().width("32"));
            int sliderW = Math.max(40, w - numberW - 8);
            mirrorCardY = y;
            s.mirrorCardW = sliderW;
            int span = Math.max(1, maxCopies - 1);
            s.drawSlider(graphics, x, y, x, sliderW, copies - 1, span, Component.empty());
            graphics.drawString(s.font(), count, x + sliderW + 8, y, WrightsUi.TEXT, false);
            y += 18;
        }
        y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.mirror_section_options", "menu.wrights.mirror_section_options_hint", firstSection);
        firstSection = false;
        int col = (w - gap) / 2;
        int right = x + col + gap;
        mirrorToggleW = col;
        mirrorNbtX = x;
        mirrorNbtY = y;
        drawMirrorToggle(graphics, mouseX, mouseY, x, y, WrightsClientConfig.COPY_NBT_BLOCKS.get(), "screen.wrights.copy_nbt_blocks", "screen.wrights.copy_nbt_blocks_hint", true, col);
        mirrorSettingsX = right;
        mirrorSettingsY = y;
        boolean settingsOn = WrightsConfig.copySettings();
        String settingsHint = settingsOn
                ? (WrightsConfig.copySettingsWhitelist() ? "screen.wrights.copy_nbt_settings_whitelist_hint" : "screen.wrights.copy_nbt_settings_hint")
                : "screen.wrights.copy_off_hint";
        drawMirrorToggle(graphics, mouseX, mouseY, right, y, mirrorSettingsChosen(), "screen.wrights.copy_nbt_settings", settingsHint, WrightsClientConfig.COPY_NBT_BLOCKS.get() && settingsOn, col);
        y += 16;
        mirrorAllX = x;
        mirrorAllY = y;
        String allHint = WrightsConfig.copyAll() ? "screen.wrights.copy_nbt_all_hint" : "screen.wrights.copy_off_hint";
        drawMirrorToggle(graphics, mouseX, mouseY, x, y, mirrorAllChosen(), "screen.wrights.copy_nbt_all", allHint, WrightsClientConfig.COPY_NBT_BLOCKS.get() && mirrorAllUsable(), col);
        mirrorShapesX = right;
        mirrorShapesY = y;
        drawMirrorToggle(graphics, mouseX, mouseY, right, y, WrightsClientConfig.MIRROR_SHAPES.get(), "menu.wrights.mirror_shapes", "menu.wrights.mirror_shapes_hint", true, col);
        y += 16;
        mirrorReplaceY = -1;
        boolean replaceOn = WrightsClientConfig.mirrorBlocks() && WrightsConfig.allowMirrorReplace();
        mirrorOnY = y;
        if (replaceOn) {
            mirrorReplaceX = x;
            mirrorReplaceY = y;
            drawMirrorToggle(graphics, mouseX, mouseY, x, y, WrightsClientConfig.mirrorReplace(), "menu.wrights.mirror_replace", "menu.wrights.mirror_replace_hint", true, col);
            mirrorOnX = right;
            drawMirrorToggle(graphics, mouseX, mouseY, right, y, WrightsClientConfig.MIRROR_ON.get(), "menu.wrights.mirror_on", "menu.wrights.mirror_on_hint", true, col);
        } else {
            mirrorOnX = x;
            drawMirrorToggle(graphics, mouseX, mouseY, x, y, WrightsClientConfig.MIRROR_ON.get(), "menu.wrights.mirror_on", "menu.wrights.mirror_on_hint", true, col);
        }
        y += 16;
        mirrorMineY = -1;
        mirrorMineModeY = -1;
        mirrorMineVoidY = -1;
        if (WrightsClientConfig.mirrorBlocks() && WrightsConfig.allowMirrorMine()) {
            mirrorMineY = y;
            drawMirrorToggle(graphics, mouseX, mouseY, x, y, WrightsClientConfig.mirrorMine(), "menu.wrights.mirror_mine", "menu.wrights.mirror_mine_hint", true, w);
            y += 16;
            if (WrightsClientConfig.mirrorMine()) {
                mirrorMineModeY = y;
                int half = (w - gap) / 2;
                boolean any = WrightsClientConfig.mirrorMineAny();
                drawChoice(graphics, mouseX, mouseY, x + 16, y, half, 22, !any, "menu.wrights.mirror_mine_same");
                drawChoice(graphics, mouseX, mouseY, x + 16 + half + gap, y, w - 16 - half - gap, 22, any, "menu.wrights.mirror_mine_any");
                y += 28;
                if (WrightsConfig.allowVoidBlocks()) {
                    mirrorMineVoidY = y;
                    drawMirrorToggle(graphics, mouseX, mouseY, x + 16, y, WrightsClientConfig.mirrorMineVoid(), "menu.wrights.mirror_mine_void", "menu.wrights.mirror_mine_void_hint", true, w - 16);
                    y += 16;
                }
            }
        }
        y += 6;
        y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.mirror_section_anchor", "menu.wrights.mirror_section_anchor_hint", false);
        int statusH = drawMirrorStatus(graphics, mouseX, mouseY, x, y, w);
        y += statusH + 6;
        mirrorHighlightY = y;
        int buttonGap = 8;
        int clearW = Math.min(108, Math.max(72, (w - buttonGap) / 2));
        int highlightW = Math.max(72, w - buttonGap - clearW);
        mirrorHighlightX = x;
        mirrorHighlightW = highlightW;
        mirrorClearX = x + highlightW + buttonGap;
        mirrorClearW = clearW;
        String dimension = s.minecraft() != null && s.minecraft().level != null ? s.minecraft().level.dimension().location().toString() : "";
        boolean here = WrightsClientConfig.mirrorPlaced(dimension);
        boolean highlightHover = here && mouseX >= x && mouseX < x + highlightW && mouseY >= y && mouseY < y + 18 && !overMirrorHelp(mouseX, mouseY);
        graphics.fill(x, y, x + highlightW, y + 18, here ? (highlightHover ? 0xFF245850 : 0xFF1A3A38) : 0xFF1C2A37);
        String highlight = s.font().plainSubstrByWidth(Component.translatable("menu.wrights.mirror_highlight").getString(), Math.max(8, highlightW - 28));
        graphics.drawString(s.font(), highlight, x + 8, y + 5, here ? WrightsUi.TEAL : WrightsUi.MUTED, false);
        drawHelpBox(graphics, mouseX, mouseY, x + highlightW - 16, y + 3, "menu.wrights.mirror_highlight_hint");
        boolean clearOn = WrightsClientConfig.MIRROR_SET.get();
        boolean clearHover = clearOn && mouseX >= mirrorClearX && mouseX < mirrorClearX + clearW && mouseY >= y && mouseY < y + 18 && !overMirrorHelp(mouseX, mouseY);
        graphics.fill(mirrorClearX, y, mirrorClearX + clearW, y + 18, clearOn ? (clearHover ? 0xFF5A3030 : 0xFF3A2428) : 0xFF1C2A37);
        String clear = s.font().plainSubstrByWidth(Component.translatable("menu.wrights.mirror_clear").getString(), Math.max(8, clearW - 28));
        graphics.drawString(s.font(), clear, mirrorClearX + 8, y + 5, clearOn ? WrightsUi.RED : WrightsUi.MUTED, false);
        drawHelpBox(graphics, mouseX, mouseY, mirrorClearX + clearW - 16, y + 3, "menu.wrights.mirror_clear_hint");
        mirrorClearY = y;
        y += 26;
        y = beginMirrorSection(graphics, mouseX, mouseY, x, y, lineEnd, "menu.wrights.shape_priority", "menu.wrights.shape_priority_hint", false);
        String orderLine = s.font().plainSubstrByWidth(WrightsClientConfig.supplyOrderSummary(), Math.max(8, w));
        graphics.drawString(s.font(), orderLine, x, y, WrightsUi.MUTED, false);
        y += 12;
        mirrorPriorityY = y;
        boolean priorityHover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18 && !overMirrorHelp(mouseX, mouseY);
        WrightsUi.choice(graphics, x, y, w, 18, false, priorityHover);
        graphics.drawCenteredString(s.font(), Component.translatable("menu.wrights.shape_priority_edit"), x + w / 2, y + 5, WrightsUi.TEXT);
        s.mirrorContentH = y + 18 - (mirrorViewTop - s.mirrorScroll);
        graphics.disableScissor();
        int maxScroll = Math.max(0, s.mirrorContentH - s.mirrorViewH);
        s.mirrorScroll = Mth.clamp(s.mirrorScroll, 0, maxScroll);
        if (maxScroll > 0) {
            int track = s.mirrorViewH - 4;
            int thumb = Math.max(16, track * s.mirrorViewH / s.mirrorContentH);
            int thumbY = mirrorViewTop + 2 + (track - thumb) * s.mirrorScroll / maxScroll;
            graphics.fill(x + w - 2, mirrorViewTop + 2, x + w, mirrorViewTop + s.mirrorViewH - 2, 0xFF1A2836);
            graphics.fill(x + w - 2, thumbY, x + w, thumbY + thumb, WrightsUi.COPPER_DIM);
        }
    }

    boolean insideMirrorView(double mouseX, double mouseY) {
        return s.mirrorPage && mouseX >= s.mirrorBodyX - 4 && mouseX < s.mirrorBodyX + mirrorBodyW + 4
                && mouseY >= mirrorViewTop && mouseY < mirrorViewTop + s.mirrorViewH;
    }

    private static String mirrorAxisKey(Direction.Axis axis) {
        return switch (axis) {
            case X -> "menu.wrights.mirror_axis_x";
            case Y -> "menu.wrights.mirror_axis_y";
            case Z -> "menu.wrights.mirror_axis_z";
        };
    }

    private int drawMirrorStatus(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        String dimension = s.minecraft() != null && s.minecraft().level != null ? s.minecraft().level.dimension().location().toString() : "";
        boolean placed = WrightsClientConfig.mirrorPlaced(dimension);
        boolean armed = WrightsClientConfig.mirrorArmed(dimension);
        mirrorFaceY = -1;
        int height = placed ? 52 : 34;
        WrightsUi.status(graphics, x, y, w, height, placed ? WrightsUi.TEAL : WrightsUi.COPPER_DIM);
        if (!WrightsClientConfig.MIRROR_SET.get()) {
            drawWrapped(graphics, Component.translatable("menu.wrights.mirror_none"), x + 10, y + 6, w - 16, WrightsUi.TEXT);
        } else if (!placed) {
            drawWrapped(graphics, Component.translatable("menu.wrights.mirror_other_world"), x + 10, y + 6, w - 16, WrightsUi.TEXT);
        } else {
            net.minecraft.core.BlockPos anchor = WrightsClientConfig.mirrorAnchor();
            Direction.Axis axis = Direction.from3DDataValue(WrightsClientConfig.MIRROR_FACE.get()).getAxis();
            graphics.drawString(s.font(), Component.translatable("menu.wrights.mirror_anchor", anchor.getX(), anchor.getY(), anchor.getZ(), Component.translatable(mirrorAxisKey(axis))), x + 10, y + 6, WrightsUi.TEXT, false);
            int copies = com.wrights.blueprint.Mirror.copyCount(WrightsClientConfig.MIRROR_PATTERN.get(), WrightsClientConfig.MIRROR_AXES.get());
            graphics.drawString(s.font(), Component.translatable(armed ? "menu.wrights.mirror_copies" : "menu.wrights.mirror_paused", copies), x + 10, y + 18, armed ? WrightsUi.TEAL : WrightsUi.MUTED, false);
            mirrorFaceY = y + 30;
            int pad = 8;
            int gap = 4;
            int buttonW = (w - pad * 2 - gap * 2) / 3;
            Direction.Axis[] axes = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
            for (int i = 0; i < axes.length; i++) {
                int bx = x + pad + i * (buttonW + gap);
                int bw = i == axes.length - 1 ? x + w - pad - bx : buttonW;
                drawChoice(graphics, mouseX, mouseY, bx, mirrorFaceY, bw, 16, axis == axes[i], mirrorAxisKey(axes[i]));
            }
        }
        return height;
    }

    private int beginMirrorSection(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int lineEnd, String label, String hint, boolean first) {
        if (!first) {
            WrightsUi.sectionBreak(graphics, x, y, Math.max(0, lineEnd - x));
            y += 8;
        }
        int titleW = WrightsUi.section(graphics, s.font(), x, y, label, lineEnd);
        drawHelpBox(graphics, mouseX, mouseY, x + titleW + 4, y - 2, hint);
        return y + 12;
    }

    private void drawChoice(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h, boolean on, String title) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && !overMirrorHelp(mouseX, mouseY);
        WrightsUi.choice(graphics, x, y, w, h, on, hover);
        String label = s.font().plainSubstrByWidth(Component.translatable(title).getString(), Math.max(8, w - 12));
        graphics.drawString(s.font(), label, x + 6, y + (h - 8) / 2, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
    }

    private void drawMirrorToggle(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, boolean on, String label, String hint, boolean enabled, int columnW) {
        int box = enabled ? 0xFF18222C : 0xFF12171C;
        int border = !enabled ? 0xFF2A343C : on ? WrightsUi.COPPER : 0xFF3D4E5E;
        graphics.fill(x, y, x + 12, y + 12, box);
        WrightsUi.border(graphics, x, y, 12, 12, border);
        if (on && enabled) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, WrightsUi.COPPER);
        }
        int textW = Math.max(8, columnW - 16 - 4 - 12);
        String title = s.font().plainSubstrByWidth(Component.translatable(label).getString(), textW);
        graphics.drawString(s.font(), title, x + 16, y + 2, enabled ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        drawHelpBox(graphics, mouseX, mouseY, x + 16 + s.font().width(title) + 4, y, hint);
    }

    private void drawHelpBox(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String hint) {
        mirrorHelps.add(new HelpSpot(x, y));
        boolean over = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        graphics.fill(x, y, x + 12, y + 12, 0xFF18222C);
        WrightsUi.border(graphics, x, y, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(s.font(), "?", x + 6, y + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        if (over) {
            s.mirrorTip = Component.translatable(hint);
        }
    }

    private boolean overMirrorHelp(double mouseX, double mouseY) {
        for (HelpSpot spot : mirrorHelps) {
            if (mouseX >= spot.x() && mouseX < spot.x() + 12 && mouseY >= spot.y() && mouseY < spot.y() + 12) {
                return true;
            }
        }
        return false;
    }

    private boolean mirrorSettingsChosen() {
        return WrightsConfig.copySettings() && WrightsClientConfig.COPY_NBT_SETTINGS.get() && !mirrorAllChosen();
    }

    private boolean mirrorAllChosen() {
        return mirrorAllUsable() && WrightsClientConfig.COPY_NBT_ALL.get();
    }

    private boolean mirrorAllUsable() {
        return WrightsConfig.copyAll() && s.minecraft() != null && s.minecraft().player != null
                && s.minecraft().player.getAbilities().instabuild && !s.minecraft().player.isSpectator();
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (var line : s.font().split(text, Math.max(20, width))) {
            graphics.drawString(s.font(), line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    boolean clickMirror(double mouseX, double mouseY) {
        if (overMirrorHelp(mouseX, mouseY)) {
            return true;
        }
        if (s.mirrorCardW <= 0) {
            return false;
        }
        int x = s.mirrorBodyX;
        int gap = 6;
        boolean blocksOn = WrightsConfig.allowMirrorBlocks();
        boolean plansOn = WrightsConfig.allowMirrorBlueprints();
        int modeCount = (blocksOn ? 1 : 0) + (plansOn ? 1 : 0);
        int modeW = modeCount <= 1 ? mirrorBodyW : (mirrorBodyW - gap) / 2;
        if (mouseY >= mirrorModeY && mouseY < mirrorModeY + 28) {
            if (blocksOn && mouseX >= x && mouseX < x + modeW) {
                WrightsClientConfig.setMirrorBlocks(true);
                return true;
            }
            if (plansOn && mouseX >= x + (blocksOn ? modeW + gap : 0) && mouseX < x + mirrorBodyW) {
                WrightsClientConfig.setMirrorBlocks(false);
                return true;
            }
        }
        if (mirrorStyleY >= 0 && mouseY >= mirrorStyleY && mouseY < mirrorStyleY + 28) {
            if (mouseX >= x && mouseX < x + mirrorStyleW) {
                WrightsClientConfig.setMirrorStyleAxes();
                return true;
            }
            if (mouseX >= x + mirrorStyleW + gap && mouseX < x + mirrorBodyW) {
                WrightsClientConfig.setMirrorStyleAnchored();
                return true;
            }
        }
        if (mirrorCardY >= 0 && s.hitSlider(mouseX, mouseY, x, mirrorCardY, s.mirrorCardW)) {
            s.drag = 4;
            s.applyDrag(mouseX);
            return true;
        }
        if (mirrorAxisY >= 0 && mirrorAxisW > 0 && mouseY >= mirrorAxisY && mouseY < mirrorAxisY + 28) {
            int index = (int) ((mouseX - x) / (mirrorAxisW + gap));
            int cardX = x + (mirrorAxisW + gap) * index;
            int cardWidth = index == 2 ? x + mirrorBodyW - cardX : mirrorAxisW;
            if (index >= 0 && index < 3 && mouseX >= cardX && mouseX < cardX + cardWidth) {
                WrightsClientConfig.toggleMirrorAxis(1 << index);
                return true;
            }
        }
        if (hitMirrorCell(mouseX, mouseY, mirrorNbtX, mirrorNbtY)) {
            WrightsClientConfig.setCopyNbtBlocks(!WrightsClientConfig.COPY_NBT_BLOCKS.get());
            return true;
        }
        if (hitMirrorCell(mouseX, mouseY, mirrorSettingsX, mirrorSettingsY)) {
            if (WrightsClientConfig.COPY_NBT_BLOCKS.get() && WrightsConfig.copySettings()) {
                WrightsClientConfig.setCopyNbtSettings(!WrightsClientConfig.COPY_NBT_SETTINGS.get());
            }
            return true;
        }
        if (hitMirrorCell(mouseX, mouseY, mirrorAllX, mirrorAllY)) {
            if (WrightsClientConfig.COPY_NBT_BLOCKS.get() && mirrorAllUsable()) {
                WrightsClientConfig.setCopyNbtAll(!WrightsClientConfig.COPY_NBT_ALL.get());
            }
            return true;
        }
        if (hitMirrorCell(mouseX, mouseY, mirrorShapesX, mirrorShapesY)) {
            WrightsClientConfig.setMirrorShapes(!WrightsClientConfig.MIRROR_SHAPES.get());
            return true;
        }
        if (mirrorReplaceY >= 0 && hitMirrorCell(mouseX, mouseY, mirrorReplaceX, mirrorReplaceY)) {
            WrightsClientConfig.setMirrorReplace(!WrightsClientConfig.mirrorReplace());
            return true;
        }
        if (hitMirrorCell(mouseX, mouseY, mirrorOnX, mirrorOnY)) {
            WrightsClientConfig.setMirrorOn(!WrightsClientConfig.MIRROR_ON.get());
            return true;
        }
        if (mirrorMineY >= 0 && hitMirrorToggle(mouseX, mouseY, x, mirrorMineY, "menu.wrights.mirror_mine")) {
            WrightsClientConfig.setMirrorMine(!WrightsClientConfig.mirrorMine());
            return true;
        }
        if (mirrorMineModeY >= 0 && mouseY >= mirrorMineModeY && mouseY < mirrorMineModeY + 22) {
            int half = (mirrorBodyW - 6) / 2;
            int left = x + 16;
            if (mouseX >= left && mouseX < left + half) {
                WrightsClientConfig.setMirrorMineAny(false);
                return true;
            }
            int right = left + half + 6;
            if (mouseX >= right && mouseX < x + mirrorBodyW) {
                WrightsClientConfig.setMirrorMineAny(true);
                return true;
            }
        }
        if (mirrorMineVoidY >= 0 && hitMirrorToggle(mouseX, mouseY, x + 16, mirrorMineVoidY, "menu.wrights.mirror_mine_void")) {
            WrightsClientConfig.setMirrorMineVoid(!WrightsClientConfig.mirrorMineVoid());
            return true;
        }
        if (mirrorFaceY >= 0 && mouseY >= mirrorFaceY && mouseY < mirrorFaceY + 16) {
            int pad = 8;
            int faceGap = 4;
            int buttonW = (mirrorBodyW - pad * 2 - faceGap * 2) / 3;
            Direction.Axis[] axes = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
            for (int i = 0; i < axes.length; i++) {
                int bx = x + pad + i * (buttonW + faceGap);
                int bw = i == axes.length - 1 ? x + mirrorBodyW - pad - bx : buttonW;
                if (mouseX >= bx && mouseX < bx + bw) {
                    WrightsClientConfig.setMirrorFace(Direction.get(Direction.AxisDirection.POSITIVE, axes[i]).get3DDataValue());
                    return true;
                }
            }
        }
        if (mirrorHighlightY >= 0 && mouseX >= mirrorHighlightX && mouseX < mirrorHighlightX + mirrorHighlightW && mouseY >= mirrorHighlightY && mouseY < mirrorHighlightY + 18) {
            String dimension = s.minecraft() != null && s.minecraft().level != null ? s.minecraft().level.dimension().location().toString() : "";
            if (WrightsClientConfig.mirrorPlaced(dimension)) {
                WrightsClientState.glowMirrorAnchor(dimension, WrightsClientConfig.mirrorAnchor());
                if (s.minecraft() != null) {
                    s.minecraft().setScreen(null);
                }
            }
            return true;
        }
        if (WrightsClientConfig.MIRROR_SET.get() && mouseX >= mirrorClearX && mouseX < mirrorClearX + mirrorClearW && mouseY >= mirrorClearY && mouseY < mirrorClearY + 18) {
            WrightsClientConfig.clearMirror();
            return true;
        }
        if (mirrorPriorityY >= 0 && mouseX >= x && mouseX < x + mirrorBodyW && mouseY >= mirrorPriorityY && mouseY < mirrorPriorityY + 18) {
            s.minecraft().setScreen(new ShapePriorityScreen(s));
            return true;
        }
        return false;
    }

    private boolean hitMirrorCell(double mouseX, double mouseY, int x, int y) {
        return y >= 0 && mouseX >= x && mouseX < x + mirrorToggleW && mouseY >= y && mouseY < y + 12;
    }

    private boolean hitMirrorToggle(double mouseX, double mouseY, int x, int y, String label) {
        int width = 16 + s.font().width(Component.translatable(label));
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12;
    }
}
