package com.wrights.client;

import com.wrights.network.BlueprintPayloads;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import com.wrights.client.WrightsMenuScreen.HelpSpot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;

/** The Options page: colours, sliders, offsets and message settings. */
final class OptionsPanel {
    private final WrightsMenuScreen s;

    OptionsPanel(WrightsMenuScreen s) {
        this.s = s;
    }

    private int swatchX;

    private int optionViewX;

    private int optionViewW;

    private int hologramHeadY;

    private int buildHeadY;

    private int supplyHeadY;

    private int messageHeadY;

    private int optionPreviewX;

    private int optionPreviewY;

    private int optionPlaceY;

    private int optionOffsetY;

    private int optionShapeAirY;

    private final List<HelpSpot> optionHelps = new ArrayList<>();

    private Component optionTip;

    private int optionY(int local) {
        return s.optionViewY + local - s.optionScroll;
    }

    void layoutOptions() {
        optionViewX = s.listX - 2;
        s.optionViewY = s.panelY + 38;
        optionViewW = s.panelX + s.panelW - 16 - optionViewX;
        s.optionViewH = s.panelH - 76;
        int x = optionViewX + 10;
        int controls = optionControlsX(x);
        int local = 2;
        hologramHeadY = optionY(local);
        // Keep the hologram sample clear of the color swatches on the left.
        optionPreviewX = optionViewX + optionViewW - 16 - 48;
        optionPreviewY = optionY(local);
        local = 34;
        s.wallSwatchY = optionY(local - 2);
        local += 20;
        int sliderW = Mth.clamp(optionViewX + optionViewW - 18 - controls - 42, 48, 120);
        s.wallSliderX = controls;
        s.wallSliderY = optionY(local);
        s.wallSliderW = sliderW;
        placeField(s.wallPercent, s.wallSliderX + s.wallSliderW + 8, s.wallSliderY - 4);
        local += 22;
        s.edgeSwatchY = optionY(local - 2);
        local += 20;
        s.edgeSliderX = controls;
        s.edgeSliderY = optionY(local);
        s.edgeSliderW = sliderW;
        placeField(s.edgePercent, s.edgeSliderX + s.edgeSliderW + 8, s.edgeSliderY - 4);
        local += 22;
        s.queueEdgeSwatchY = optionY(local - 2);
        local += 22;
        s.placeEdgeSwatchY = optionY(local - 2);
        local += 20;
        s.ghostSliderX = controls;
        s.ghostSliderY = optionY(local);
        s.ghostSliderW = sliderW;
        placeField(s.ghostPercent, s.ghostSliderX + s.ghostSliderW + 8, s.ghostSliderY - 4);
        local += 24;
        boolean marks = WrightsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.wrights.on" : "menu.wrights.off");
        s.markerW = Math.max(36, s.font().width(state) + 16);
        s.markerX = controls;
        s.markerY = optionY(local - 3);
        local += 28;
        buildHeadY = optionY(local);
        local += 18;
        boolean paceChoice = s.creativeVisit();
        boolean instant = paceChoice && WrightsClientConfig.INSTANT_JOBS.get();
        Component pace = Component.translatable(instant ? "menu.wrights.instant" : "menu.wrights.drone_pace");
        s.instantW = Math.max(52, s.font().width(pace) + 16);
        s.instantX = controls;
        s.instantY = optionY(local - 3);
        local += 24;
        s.droneY = optionY(local - 3);
        s.droneMinusX = controls;
        s.dronePlusX = s.droneMinusX + 36;
        Component maxLabel = Component.translatable("menu.wrights.drone_max", WrightsConfig.maxWrightsPerJob(true));
        s.droneW = s.font().width(maxLabel) + 12;
        s.droneX = s.dronePlusX + 22;
        local += 24;
        Component queueState = Component.translatable(WrightsClientConfig.QUEUE_JOBS.get() ? "menu.wrights.on" : "menu.wrights.off");
        s.queueW = Math.max(36, s.font().width(queueState) + 16);
        s.queueX = controls;
        s.queueY = optionY(local - 3);
        local += 24;
        Component youFirst = Component.translatable("menu.wrights.port_player_first");
        Component blockFirst = Component.translatable("menu.wrights.port_block_first");
        s.portOrderW = Math.max(s.font().width(youFirst), s.font().width(blockFirst)) + 16;
        s.portOrderX = controls;
        s.portOrderY = optionY(local - 3);
        local += 24;
        Component menuState = Component.translatable(WrightsClientConfig.BUILD_MENU.get() ? "menu.wrights.on" : "menu.wrights.off");
        s.buildMenuW = Math.max(36, s.font().width(menuState) + 16);
        s.buildMenuX = controls;
        s.buildMenuY = optionY(local - 3);
        local += 24;
        Component dockWatchState = Component.translatable(WrightsClientConfig.DOCK_WATCH.get() ? "menu.wrights.on" : "menu.wrights.off");
        s.dockWatchW = Math.max(36, s.font().width(dockWatchState) + 16);
        s.dockWatchX = controls;
        s.dockWatchY = optionY(local - 3);
        local += 24;
        optionPlaceY = optionY(local);
        local += 16;
        optionOffsetY = optionY(local);
        placeField(s.placeX, x + 12, optionOffsetY);
        placeField(s.placeY, x + 82, optionOffsetY);
        placeField(s.placeZ, x + 152, optionOffsetY);
        local += 22;
        optionShapeAirY = optionY(local - 3);
        placeField(s.shapeAirOffset, controls, optionShapeAirY);
        local += 28;
        supplyHeadY = optionY(local);
        local += 18;
        Component editPriority = Component.translatable("menu.wrights.shape_priority_edit");
        s.supplyOrderW = Math.max(88, s.font().width(editPriority) + 16);
        s.supplyOrderX = controls;
        s.supplyOrderY = optionY(local - 3);
        local += 34;
        messageHeadY = optionY(local);
        local += 18;
        s.jobDoneMinY = optionY(local - 3);
        s.jobDoneMinusX = controls;
        s.jobDonePlusX = s.jobDoneMinusX + 36;
        local += 24;
        Component moveMessages = Component.translatable("menu.wrights.hud_place");
        Component moveModes = Component.translatable("menu.wrights.mode_place");
        s.hudPlaceW = Math.max(120, s.font().width(moveMessages) + 16);
        s.modePlaceW = Math.max(120, s.font().width(moveModes) + 16);
        s.hudPlaceX = x;
        s.hudPlaceY = optionY(local);
        // Leave room for the ? help between the two buttons when they share a row.
        s.modePlaceX = x + s.hudPlaceW + 18;
        if (s.modePlaceX + s.modePlaceW > optionViewX + optionViewW - 16) {
            s.modePlaceX = x;
            s.modePlaceY = optionY(local + 20);
            local += 20;
        } else {
            s.modePlaceY = s.hudPlaceY;
        }
        swatchX = controls;
        s.optionContentH = local + 28;
        int maxScroll = Math.max(0, s.optionContentH - s.optionViewH);
        int clamped = Mth.clamp(s.optionScroll, 0, maxScroll);
        if (clamped != s.optionScroll) {
            s.optionScroll = clamped;
            layoutOptions();
            return;
        }
        showOptionBox(s.wallPercent);
        showOptionBox(s.edgePercent);
        showOptionBox(s.ghostPercent);
        showOptionBox(s.placeX);
        showOptionBox(s.placeY);
        showOptionBox(s.placeZ);
        showOptionBox(s.shapeAirOffset);
        if (maxScroll > 0) {
            s.optionThumbH = Math.max(16, s.optionViewH * s.optionViewH / s.optionContentH);
            s.optionThumbY = s.optionViewY + (s.optionViewH - s.optionThumbH) * s.optionScroll / maxScroll;
        } else {
            s.optionThumbH = s.optionViewH;
            s.optionThumbY = s.optionViewY;
        }
    }

    /** Shared left edge for the option controls, past the longest label and its help mark. */
    private int optionControlsX(int labelX) {
        String[] keys = {
                "menu.wrights.wall_color",
                "menu.wrights.wall_transparency",
                "menu.wrights.edge_color",
                "menu.wrights.queue_edge",
                "menu.wrights.place_edge",
                "menu.wrights.edge_opacity",
                "menu.wrights.ghost_transparency",
                "menu.wrights.corner_markers",
                "menu.wrights.build_speed",
                "menu.wrights.drone_deploy",
                "menu.wrights.queue_jobs",
                "menu.wrights.port_order",
                "menu.wrights.open_build_menu",
                "menu.wrights.dock_watch",
                "menu.wrights.shape_air_offset",
                "menu.wrights.supply_order",
                "menu.wrights.job_done_min"
        };
        int widest = 0;
        for (String key : keys) {
            widest = Math.max(widest, s.font().width(Component.translatable(key)));
        }
        int wanted = labelX + widest + 22;
        int limit = optionViewX + optionViewW - 16 - 168;
        return Math.max(labelX + 108, Math.min(wanted, Math.max(labelX + 108, limit)));
    }

    private void showOptionBox(EditBox box) {
        int top = box.getY();
        int bottom = top + box.getHeight();
        box.visible = s.options && top >= s.optionViewY && bottom <= s.optionViewY + s.optionViewH;
    }

    void renderOptions(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = optionViewX + 10;
        optionHelps.clear();
        optionTip = null;
        graphics.enableScissor(optionViewX, s.optionViewY, optionViewX + optionViewW - 6, s.optionViewY + s.optionViewH);
        int lineEnd = optionViewX + optionViewW - 16;
        drawOptionSection(graphics, x, hologramHeadY, "menu.wrights.section_hologram", optionPreviewX - 8);
        int preview = (Math.round(WrightsClientConfig.wallAlpha() * 255.0F) << 24) | WrightsClientConfig.wallRgb();
        graphics.fill(optionPreviewX, optionPreviewY, optionPreviewX + 48, optionPreviewY + 28, preview);
        WrightsUi.border(graphics, optionPreviewX, optionPreviewY, 48, 28, 0xFF000000 | WrightsClientConfig.edgeRgb());
        graphics.drawString(s.font(), Component.translatable("menu.wrights.wall_color"), x, s.wallSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, s.wallSwatchY, WrightsClientConfig.wallRgb());
        s.drawSlider(graphics, x, s.wallSliderY, s.wallSliderX, s.wallSliderW, WrightsClientConfig.WALL_TRANSPARENCY.get(), 100, Component.translatable("menu.wrights.wall_transparency"));
        graphics.drawString(s.font(), "%", s.wallPercent.getX() + 34, s.wallSliderY, WrightsUi.TEXT, false);
        graphics.drawString(s.font(), Component.translatable("menu.wrights.edge_color"), x, s.edgeSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, s.edgeSwatchY, WrightsClientConfig.edgeRgb());
        graphics.drawString(s.font(), Component.translatable("menu.wrights.queue_edge"), x, s.queueEdgeSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, s.queueEdgeSwatchY, WrightsClientConfig.queueEdgeRgb());
        graphics.drawString(s.font(), Component.translatable("menu.wrights.place_edge"), x, s.placeEdgeSwatchY + 2, WrightsUi.MUTED, false);
        drawSwatches(graphics, swatchX, s.placeEdgeSwatchY, WrightsClientConfig.placeEdgeRgb());
        s.drawSlider(graphics, x, s.edgeSliderY, s.edgeSliderX, s.edgeSliderW, WrightsClientConfig.EDGE_OPACITY.get(), 100, Component.translatable("menu.wrights.edge_opacity"));
        graphics.drawString(s.font(), "%", s.edgePercent.getX() + 34, s.edgeSliderY, WrightsUi.TEXT, false);
        Component ghostLabel = Component.translatable("menu.wrights.ghost_transparency");
        s.drawSlider(graphics, x, s.ghostSliderY, s.ghostSliderX, s.ghostSliderW, WrightsClientConfig.GHOST_TRANSPARENCY.get(), 100, ghostLabel);
        graphics.drawString(s.font(), "%", s.ghostPercent.getX() + 34, s.ghostSliderY, WrightsUi.TEXT, false);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.ghostSliderY, ghostLabel, s.ghostSliderX, s.ghostPercent.getX() + 42, Component.translatable("menu.wrights.ghost_hint"));
        graphics.drawString(s.font(), Component.translatable("menu.wrights.corner_markers"), x, s.markerY + 3, WrightsUi.MUTED, false);
        boolean marks = WrightsClientConfig.CORNER_MARKERS.get();
        Component state = Component.translatable(marks ? "menu.wrights.on" : "menu.wrights.off");
        WrightsUi.choice(graphics, s.markerX, s.markerY, s.markerW, 16, marks, false);
        graphics.drawCenteredString(s.font(), state, s.markerX + s.markerW / 2, s.markerY + 4, WrightsUi.TEXT);
        drawOptionSection(graphics, x, buildHeadY, "menu.wrights.section_building", lineEnd);
        boolean paceChoice = s.creativeVisit();
        boolean instant = paceChoice && WrightsClientConfig.INSTANT_JOBS.get();
        Component speedLabel = Component.translatable("menu.wrights.build_speed");
        graphics.drawString(s.font(), speedLabel, x, s.instantY + 3, paceChoice ? WrightsUi.MUTED : 0xFF667480, false);
        Component pace = Component.translatable(instant ? "menu.wrights.instant" : "menu.wrights.drone_pace");
        int text = paceChoice ? WrightsUi.TEXT : 0xFF667480;
        if (paceChoice) {
            WrightsUi.choice(graphics, s.instantX, s.instantY, s.instantW, 16, instant, false);
        } else {
            graphics.fill(s.instantX, s.instantY, s.instantX + s.instantW, s.instantY + 16, 0xFF141C24);
            graphics.fill(s.instantX, s.instantY, s.instantX + s.instantW, s.instantY + 2, 0xFF2A3440);
        }
        graphics.drawCenteredString(s.font(), pace, s.instantX + s.instantW / 2, s.instantY + 4, text);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.instantY + 3, speedLabel, s.instantX, s.instantX + s.instantW, Component.translatable(paceChoice ? "menu.wrights.build_speed_hint" : "menu.wrights.build_speed_locked"));
        Component droneLabel = Component.translatable("menu.wrights.drone_deploy");
        graphics.drawString(s.font(), droneLabel, x, s.droneY + 3, WrightsUi.MUTED, false);
        WrightsUi.choice(graphics, s.droneMinusX, s.droneY, 16, 16, false, false);
        graphics.drawCenteredString(s.font(), "-", s.droneMinusX + 8, s.droneY + 4, WrightsUi.TEXT);
        int droneCap = WrightsConfig.maxWrightsPerJob(true);
        String count = Integer.toString(WrightsClientConfig.DRONE_MAX.get()
                ? WrightsConfig.maxWrightsPerJob()
                : WrightsClientConfig.droneCount());
        graphics.drawCenteredString(s.font(), count, s.droneMinusX + 26, s.droneY + 4, WrightsUi.TEXT);
        WrightsUi.choice(graphics, s.dronePlusX, s.droneY, 16, 16, false, false);
        graphics.drawCenteredString(s.font(), "+", s.dronePlusX + 8, s.droneY + 4, WrightsUi.TEXT);
        boolean atMax = WrightsClientConfig.DRONE_MAX.get();
        WrightsUi.choice(graphics, s.droneX, s.droneY, s.droneW, 16, atMax, false);
        graphics.drawCenteredString(s.font(), Component.translatable("menu.wrights.drone_max", droneCap), s.droneX + s.droneW / 2, s.droneY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.droneY + 3, droneLabel, s.droneMinusX, s.droneX + s.droneW, Component.translatable("menu.wrights.drone_fixed_hint"));
        boolean queueJobs = WrightsClientConfig.QUEUE_JOBS.get();
        Component queueLabel = Component.translatable("menu.wrights.queue_jobs");
        graphics.drawString(s.font(), queueLabel, x, s.queueY + 3, WrightsUi.MUTED, false);
        Component queueState = Component.translatable(queueJobs ? "menu.wrights.on" : "menu.wrights.off");
        WrightsUi.choice(graphics, s.queueX, s.queueY, s.queueW, 16, queueJobs, false);
        graphics.drawCenteredString(s.font(), queueState, s.queueX + s.queueW / 2, s.queueY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.queueY + 3, queueLabel, s.queueX, s.queueX + s.queueW, Component.translatable("menu.wrights.queue_jobs_hint"));
        boolean youFirst = WrightsClientConfig.PORT_PLAYER_FIRST.get();
        Component portLabel = Component.translatable("menu.wrights.port_order");
        graphics.drawString(s.font(), portLabel, x, s.portOrderY + 3, WrightsUi.MUTED, false);
        Component portOrder = Component.translatable(youFirst ? "menu.wrights.port_player_first" : "menu.wrights.port_block_first");
        WrightsUi.choice(graphics, s.portOrderX, s.portOrderY, s.portOrderW, 16, youFirst, false);
        graphics.drawCenteredString(s.font(), portOrder, s.portOrderX + s.portOrderW / 2, s.portOrderY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.portOrderY + 3, portLabel, s.portOrderX, s.portOrderX + s.portOrderW, Component.translatable(youFirst ? "menu.wrights.port_order_player_hint" : "menu.wrights.port_order_block_hint"));
        boolean openMenu = WrightsClientConfig.BUILD_MENU.get();
        Component menuLabel = Component.translatable("menu.wrights.open_build_menu");
        graphics.drawString(s.font(), menuLabel, x, s.buildMenuY + 3, WrightsUi.MUTED, false);
        Component menuState = Component.translatable(openMenu ? "menu.wrights.on" : "menu.wrights.off");
        WrightsUi.choice(graphics, s.buildMenuX, s.buildMenuY, s.buildMenuW, 16, openMenu, false);
        graphics.drawCenteredString(s.font(), menuState, s.buildMenuX + s.buildMenuW / 2, s.buildMenuY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.buildMenuY + 3, menuLabel, s.buildMenuX, s.buildMenuX + s.buildMenuW, Component.translatable("menu.wrights.open_build_menu_hint"));
        boolean dockWatch = WrightsClientConfig.DOCK_WATCH.get();
        Component dockWatchLabel = Component.translatable("menu.wrights.dock_watch");
        graphics.drawString(s.font(), dockWatchLabel, x, s.dockWatchY + 3, WrightsUi.MUTED, false);
        Component dockWatchState = Component.translatable(dockWatch ? "menu.wrights.on" : "menu.wrights.off");
        WrightsUi.choice(graphics, s.dockWatchX, s.dockWatchY, s.dockWatchW, 16, dockWatch, false);
        graphics.drawCenteredString(s.font(), dockWatchState, s.dockWatchX + s.dockWatchW / 2, s.dockWatchY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.dockWatchY + 3, dockWatchLabel, s.dockWatchX, s.dockWatchX + s.dockWatchW, Component.translatable("menu.wrights.dock_watch_hint"));
        Component placeLabel = Component.translatable("menu.wrights.place_offset");
        graphics.drawString(s.font(), placeLabel, x, optionPlaceY, WrightsUi.TEXT, false);
        drawOptionHelp(graphics, mouseX, mouseY, x, optionPlaceY, placeLabel, 0, 0, Component.translatable("menu.wrights.place_hint").append("\n").append(Component.translatable("menu.wrights.place_axes")));
        graphics.drawString(s.font(), "X", x, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.drawString(s.font(), "Y", x + 70, optionOffsetY + 3, WrightsUi.MUTED, false);
        graphics.drawString(s.font(), "Z", x + 140, optionOffsetY + 3, WrightsUi.MUTED, false);
        Component shapeAirLabel = Component.translatable("menu.wrights.shape_air_offset");
        graphics.drawString(s.font(), shapeAirLabel, x, optionShapeAirY + 3, WrightsUi.MUTED, false);
        int shapeAirBoxX = s.shapeAirOffset.getX();
        drawOptionHelp(graphics, mouseX, mouseY, x, optionShapeAirY + 3, shapeAirLabel, shapeAirBoxX, shapeAirBoxX + s.shapeAirOffset.getWidth(),
                Component.translatable("menu.wrights.shape_air_offset_hint"));
        drawOptionSection(graphics, x, supplyHeadY, "menu.wrights.section_supply", lineEnd);
        Component supplyLabel = Component.translatable("menu.wrights.supply_order");
        graphics.drawString(s.font(), supplyLabel, x, s.supplyOrderY + 3, WrightsUi.MUTED, false);
        WrightsUi.choice(graphics, s.supplyOrderX, s.supplyOrderY, s.supplyOrderW, 16, false, false);
        graphics.drawCenteredString(s.font(), Component.translatable("menu.wrights.shape_priority_edit"), s.supplyOrderX + s.supplyOrderW / 2, s.supplyOrderY + 4, WrightsUi.TEXT);
        String summary = WrightsClientConfig.supplyOrderSummary();
        graphics.drawString(s.font(), s.font().plainSubstrByWidth(summary, Math.max(40, optionViewW - 28)),
                x, s.supplyOrderY + 20, WrightsUi.MUTED, false);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.supplyOrderY + 3, supplyLabel, s.supplyOrderX, s.supplyOrderX + s.supplyOrderW, Component.translatable("menu.wrights.supply_order_hint"));
        drawOptionSection(graphics, x, messageHeadY, "menu.wrights.section_messages", lineEnd);
        Component jobDoneLabel = Component.translatable("menu.wrights.job_done_min");
        graphics.drawString(s.font(), jobDoneLabel, x, s.jobDoneMinY + 3, WrightsUi.MUTED, false);
        WrightsUi.choice(graphics, s.jobDoneMinusX, s.jobDoneMinY, 16, 16, false, false);
        graphics.drawCenteredString(s.font(), "-", s.jobDoneMinusX + 8, s.jobDoneMinY + 4, WrightsUi.TEXT);
        String jobDoneCount = Integer.toString(WrightsClientConfig.jobDoneMinSeconds());
        graphics.drawCenteredString(s.font(), jobDoneCount, s.jobDoneMinusX + 26, s.jobDoneMinY + 4, WrightsUi.TEXT);
        WrightsUi.choice(graphics, s.jobDonePlusX, s.jobDoneMinY, 16, 16, false, false);
        graphics.drawCenteredString(s.font(), "+", s.jobDonePlusX + 8, s.jobDoneMinY + 4, WrightsUi.TEXT);
        graphics.drawString(s.font(), Component.translatable("menu.wrights.job_done_min_unit"), s.jobDonePlusX + 22, s.jobDoneMinY + 4, WrightsUi.MUTED, false);
        drawOptionHelp(graphics, mouseX, mouseY, x, s.jobDoneMinY + 3, jobDoneLabel, s.jobDoneMinusX, s.jobDonePlusX + 40, Component.translatable("menu.wrights.job_done_min_hint"));
        WrightsUi.choice(graphics, s.hudPlaceX, s.hudPlaceY, s.hudPlaceW, 16, true, false);
        graphics.drawCenteredString(s.font(), Component.translatable("menu.wrights.hud_place"), s.hudPlaceX + s.hudPlaceW / 2, s.hudPlaceY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, s.hudPlaceX + s.hudPlaceW + 2, s.hudPlaceY + 2, Component.empty(), 0, 0, Component.translatable("menu.wrights.hud_place_hint"));
        WrightsUi.choice(graphics, s.modePlaceX, s.modePlaceY, s.modePlaceW, 16, true, false);
        graphics.drawCenteredString(s.font(), Component.translatable("menu.wrights.mode_place"), s.modePlaceX + s.modePlaceW / 2, s.modePlaceY + 4, WrightsUi.TEXT);
        drawOptionHelp(graphics, mouseX, mouseY, s.modePlaceX + s.modePlaceW + 2, s.modePlaceY + 2, Component.empty(), 0, 0, Component.translatable("menu.wrights.mode_place_hint"));
        graphics.disableScissor();
        if (optionTip != null) {
            graphics.renderTooltip(s.font(), s.font().split(optionTip, 240), mouseX, mouseY);
        }
        if (s.optionContentH > s.optionViewH) {
            int barX = optionViewX + optionViewW - 5;
            graphics.fill(barX, s.optionViewY, barX + 3, s.optionViewY + s.optionViewH, 0xFF24303A);
            graphics.fill(barX, s.optionThumbY, barX + 3, s.optionThumbY + s.optionThumbH, WrightsUi.COPPER);
        }
    }

    private void drawOptionHelp(GuiGraphics graphics, int mouseX, int mouseY, int labelX, int labelY, Component label, int controlX, int controlRight, Component tip) {
        int helpX = labelX + s.font().width(label) + 4;
        if (controlX > labelX && helpX + 12 > controlX - 2) {
            helpX = controlRight + 6;
        }
        int helpY = labelY - 2;
        if (helpY + 12 <= s.optionViewY || helpY >= s.optionViewY + s.optionViewH || helpX + 12 > optionViewX + optionViewW - 6) {
            return;
        }
        boolean over = mouseX >= helpX && mouseX < helpX + 12 && mouseY >= helpY && mouseY < helpY + 12;
        graphics.fill(helpX, helpY, helpX + 12, helpY + 12, 0xFF18222C);
        WrightsUi.border(graphics, helpX, helpY, 12, 12, over ? WrightsUi.COPPER : 0xFF3D4E5E);
        graphics.drawCenteredString(s.font(), "?", helpX + 6, helpY + 2, over ? WrightsUi.COPPER : WrightsUi.MUTED);
        optionHelps.add(new HelpSpot(helpX, helpY));
        if (over) {
            optionTip = tip;
        }
    }

    boolean overOptionHelp(double mouseX, double mouseY) {
        for (HelpSpot spot : optionHelps) {
            if (mouseX >= spot.x() && mouseX < spot.x() + 12 && mouseY >= spot.y() && mouseY < spot.y() + 12) {
                return true;
            }
        }
        return false;
    }

    private void drawOptionSection(GuiGraphics graphics, int x, int y, String key, int lineEnd) {
        if (y != hologramHeadY) {
            WrightsUi.sectionBreak(graphics, x, y - 8, Math.max(0, (optionViewX + optionViewW - 16) - x));
        }
        WrightsUi.section(graphics, s.font(), x, y, key, lineEnd);
    }

    private void drawSwatches(GuiGraphics graphics, int x, int y, int selected) {
        for (int i = 0; i < WrightsClientConfig.COLORS.length; i++) {
            int color = WrightsClientConfig.COLORS[i];
            int sx = x + i * 16;
            graphics.fill(sx, y, sx + 12, y + 12, 0xFF000000 | color);
            if (color == selected) {
                WrightsUi.border(graphics, sx - 1, y - 1, 14, 14, WrightsUi.TEXT);
            }
        }
    }

    private void placeField(EditBox box, int x, int y) {
        box.setX(x);
        box.setY(y);
        box.setWidth(28);
        box.setHeight(14);
    }

    boolean insideOptionView(double mouseX, double mouseY) {
        return mouseX >= optionViewX && mouseX < optionViewX + optionViewW && mouseY >= s.optionViewY && mouseY < s.optionViewY + s.optionViewH;
    }

    boolean hitOptionBar(double mouseX, double mouseY) {
        if (s.optionContentH <= s.optionViewH) {
            return false;
        }
        int barX = optionViewX + optionViewW - 8;
        return mouseX >= barX && mouseX < optionViewX + optionViewW && mouseY >= s.optionViewY && mouseY < s.optionViewY + s.optionViewH;
    }

    boolean pickSwatch(double mouseX, double mouseY, int y, int which) {
        if (mouseY < y || mouseY >= y + 12) {
            return false;
        }
        for (int i = 0; i < WrightsClientConfig.COLORS.length; i++) {
            int x = swatchX + i * 16;
            if (mouseX >= x && mouseX < x + 12) {
                int color = WrightsClientConfig.COLORS[i];
                if (which == 0) {
                    WrightsClientConfig.setWallRgb(color);
                } else if (which == 1) {
                    WrightsClientConfig.setEdgeRgb(color);
                } else if (which == 2) {
                    WrightsClientConfig.setQueueEdgeRgb(color);
                } else {
                    WrightsClientConfig.setPlaceEdgeRgb(color);
                }
                return true;
            }
        }
        return false;
    }

    void applyOffset(int axis, String value) {
        if (s.suppressOptions || value.isEmpty() || value.equals("-")) {
            return;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return;
        }
        int x = axis == 0 ? parsed : WrightsClientConfig.PLACE_X.get();
        int y = axis == 1 ? parsed : WrightsClientConfig.PLACE_Y.get();
        int z = axis == 2 ? parsed : WrightsClientConfig.PLACE_Z.get();
        WrightsClientConfig.setPlace(x, y, z);
    }

    void showPercent(EditBox box, int value) {
        if (box.isFocused()) {
            return;
        }
        s.suppressOptions = true;
        box.setValue(Integer.toString(value));
        s.suppressOptions = false;
    }

    void showOffset(EditBox box, int value) {
        if (box.isFocused()) {
            return;
        }
        s.suppressOptions = true;
        box.setValue(Integer.toString(value));
        s.suppressOptions = false;
    }

    boolean click(double mouseX, double mouseY) {
            layoutOptions();
            if (hitOptionBar(mouseX, mouseY)) {
                s.optionDragging = true;
                s.optionDragGrab = (int) mouseY - s.optionThumbY;
                return true;
            }
            if (insideOptionView(mouseX, mouseY)) {
                if (overOptionHelp(mouseX, mouseY)) {
                    return true;
                }
                if (pickSwatch(mouseX, mouseY, s.wallSwatchY, 0) || pickSwatch(mouseX, mouseY, s.edgeSwatchY, 1) || pickSwatch(mouseX, mouseY, s.queueEdgeSwatchY, 2) || pickSwatch(mouseX, mouseY, s.placeEdgeSwatchY, 3)) {
                    return true;
                }
                if (mouseX >= s.markerX && mouseX < s.markerX + s.markerW && mouseY >= s.markerY && mouseY < s.markerY + 16) {
                    WrightsClientConfig.setCornerMarkers(!WrightsClientConfig.CORNER_MARKERS.get());
                    return true;
                }
                if (mouseX >= s.instantX && mouseX < s.instantX + s.instantW && mouseY >= s.instantY && mouseY < s.instantY + 16) {
                    if (s.creativeVisit()) {
                        boolean next = !WrightsClientConfig.INSTANT_JOBS.get();
                        WrightsClientConfig.setInstantJobs(next);
                        PacketDistributor.sendToServer(new BlueprintPayloads.SetPace(next));
                    }
                    return true;
                }
                if (mouseY >= s.droneY && mouseY < s.droneY + 16) {
                    if (mouseX >= s.droneX && mouseX < s.droneX + s.droneW) {
                        WrightsClientState.deployChosen = false;
                        WrightsClientConfig.setDroneMax(true);
                        WrightsClientState.applyDeploy();
                        return true;
                    }
                    int step = 0;
                    if (mouseX >= s.droneMinusX && mouseX < s.droneMinusX + 16) {
                        step = -1;
                    } else if (mouseX >= s.dronePlusX && mouseX < s.dronePlusX + 16) {
                        step = 1;
                    }
                    if (step != 0) {
                        WrightsClientState.deployChosen = false;
                        int droneCap = WrightsConfig.maxWrightsPerJob(true);
                        int current = WrightsClientConfig.DRONE_MAX.get()
                                ? WrightsConfig.maxWrightsPerJob()
                                : WrightsClientConfig.droneCount();
                        WrightsClientConfig.setDroneCount(WrightsClientState.stepped(current, step, 1, droneCap));
                        WrightsClientState.applyDeploy();
                        return true;
                    }
                }
                if (mouseX >= s.queueX && mouseX < s.queueX + s.queueW && mouseY >= s.queueY && mouseY < s.queueY + 16) {
                    WrightsClientConfig.setQueueJobs(!WrightsClientConfig.QUEUE_JOBS.get());
                    return true;
                }
                if (mouseX >= s.portOrderX && mouseX < s.portOrderX + s.portOrderW && mouseY >= s.portOrderY && mouseY < s.portOrderY + 16) {
                    boolean next = !WrightsClientConfig.PORT_PLAYER_FIRST.get();
                    WrightsClientConfig.setPortPlayerFirst(next);
                    PacketDistributor.sendToServer(new BlueprintPayloads.SetPortOrder(next));
                    return true;
                }
                if (mouseX >= s.buildMenuX && mouseX < s.buildMenuX + s.buildMenuW && mouseY >= s.buildMenuY && mouseY < s.buildMenuY + 16) {
                    WrightsClientConfig.setBuildMenu(!WrightsClientConfig.BUILD_MENU.get());
                    return true;
                }
                if (mouseX >= s.dockWatchX && mouseX < s.dockWatchX + s.dockWatchW && mouseY >= s.dockWatchY && mouseY < s.dockWatchY + 16) {
                    WrightsClientConfig.setDockWatch(!WrightsClientConfig.DOCK_WATCH.get());
                    return true;
                }
                if (mouseX >= s.supplyOrderX && mouseX < s.supplyOrderX + s.supplyOrderW && mouseY >= s.supplyOrderY && mouseY < s.supplyOrderY + 16) {
                    s.minecraft().setScreen(new ShapePriorityScreen(s));
                    return true;
                }
                if (mouseY >= s.jobDoneMinY && mouseY < s.jobDoneMinY + 16) {
                    int step = 0;
                    if (mouseX >= s.jobDoneMinusX && mouseX < s.jobDoneMinusX + 16) {
                        step = -1;
                    } else if (mouseX >= s.jobDonePlusX && mouseX < s.jobDonePlusX + 16) {
                        step = 1;
                    }
                    if (step != 0) {
                        int current = WrightsClientConfig.jobDoneMinSeconds();
                        WrightsClientConfig.setJobDoneMinSeconds(WrightsClientState.stepped(current, step, 0, 120));
                        return true;
                    }
                }
                if (mouseX >= s.hudPlaceX && mouseX < s.hudPlaceX + s.hudPlaceW && mouseY >= s.hudPlaceY && mouseY < s.hudPlaceY + 16) {
                    s.minecraft().setScreen(new HudPlaceScreen());
                    return true;
                }
                if (mouseX >= s.modePlaceX && mouseX < s.modePlaceX + s.modePlaceW && mouseY >= s.modePlaceY && mouseY < s.modePlaceY + 16) {
                    s.minecraft().setScreen(new ModePlaceScreen());
                    return true;
                }
                if (s.hitSlider(mouseX, mouseY, s.wallSliderX, s.wallSliderY, s.wallSliderW)) {
                    s.drag = 1;
                    s.applyDrag(mouseX);
                    return true;
                }
                if (s.hitSlider(mouseX, mouseY, s.edgeSliderX, s.edgeSliderY, s.edgeSliderW)) {
                    s.drag = 2;
                    s.applyDrag(mouseX);
                    return true;
                }
                if (s.hitSlider(mouseX, mouseY, s.ghostSliderX, s.ghostSliderY, s.ghostSliderW)) {
                    s.drag = 3;
                    s.applyDrag(mouseX);
                    return true;
                }
            }
        return false;
    }
}
