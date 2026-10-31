package com.wrights.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.wrights.WrightsMod;
import com.wrights.blueprint.BoxEdits;

@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class WrightsHud {
    private WrightsHud() {
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        drawJobNotices(event.getGuiGraphics(), minecraft);
        if (minecraft.screen != null || !com.wrights.item.ControllerItem.isHeld(minecraft.player)) {
            return;
        }
        Component line = null;
        Component sub = null;
        Component note = null;
        Component task = taskLine();
        if (WrightsClientState.bindPort != null) {
            line = Component.translatable("hud.wrights.port_chest");
            sub = Component.translatable("hud.wrights.port_chest_sub");
        } else if (WrightsClientState.mode == WrightsClientState.Mode.CAPTURE) {
            boolean box = WrightsClientState.captureStyle == WrightsClientState.CaptureStyle.BOX;
            boolean move = WrightsClientState.captureStyle == WrightsClientState.CaptureStyle.MOVE;
            if (move && WrightsClientState.moveGrabbed && WrightsClientState.hasBox()) {
                line = volumeLine();
                sub = Component.translatable("hud.wrights.move_sub");
                note = Component.translatable("hud.wrights.resize_side");
            } else if (move) {
                line = Component.translatable(WrightsClientState.hasBox() ? "hud.wrights.move_aim" : "hud.wrights.mode_move_need");
                sub = Component.translatable("hud.wrights.mode_how");
                if (WrightsClientState.hasBox()) {
                    note = Component.translatable("hud.wrights.resize_side");
                }
            } else if (box && WrightsClientState.boxDragging && WrightsClientState.hasBox()) {
                line = volumeLine();
                sub = Component.translatable("hud.wrights.box_sub");
            } else if (box && WrightsClientState.cornerA != null) {
                if (WrightsClientState.hasBox()) {
                    line = volumeLine();
                    sub = Component.translatable("hud.wrights.shape_open");
                    note = Component.translatable("hud.wrights.resize_side");
                } else {
                    line = Component.translatable("hud.wrights.box_idle");
                    sub = Component.translatable("hud.wrights.mode_how");
                }
            } else if (WrightsClientState.cornerA == null) {
                line = Component.translatable("hud.wrights.capture");
                sub = Component.translatable("hud.wrights.mode_how");
            } else if (WrightsClientState.cornerB == null) {
                line = Component.translatable("hud.wrights.capture_second");
                sub = Component.translatable("hud.wrights.mode_how");
            } else {
                line = volumeLine();
                sub = Component.translatable("hud.wrights.shape_open");
                note = Component.translatable("hud.wrights.resize_side");
            }
        } else if (WrightsClientState.mode == WrightsClientState.Mode.SAVING) {
            line = Component.translatable("hud.wrights.saving", WrightsClientState.name);
        } else if (WrightsClientState.mode == WrightsClientState.Mode.BUILD) {
            if (WrightsClientState.buildId == null) {
                line = Component.translatable("hud.wrights.pick_blueprint");
            } else if (WrightsClientState.bindSupply) {
                line = Component.translatable("hud.wrights.bind_explain");
                sub = supplyChoice(minecraft);
            } else {
                line = Component.translatable("hud.wrights.build", WrightsClientState.buildName);
                sub = Component.translatable(WrightsClientState.supplyBound ? "hud.wrights.build_chest" : "hud.wrights.build_inventory");
                note = Component.translatable("hud.wrights.resize_side");
            }
        } else if (WrightsClientState.mode == WrightsClientState.Mode.MANAGE) {
            line = Component.translatable("hud.wrights.manage_explain");
        } else if (WrightsClientState.mode == WrightsClientState.Mode.INFO) {
            line = Component.translatable("hud.wrights.info_explain");
        } else if (WrightsClientState.mode == WrightsClientState.Mode.LINK) {
            line = Component.translatable("hud.wrights.link_explain");
            sub = Component.translatable("hud.wrights.link_targets");
        } else if (WrightsClientState.mode == WrightsClientState.Mode.JOBS) {
            com.wrights.network.BlueprintPayloads.JobLine aimed = WrightsClientState.jobAimed();
            if (aimed == null) {
                line = Component.translatable("hud.wrights.jobs_explain");
            } else {
                boolean stalled = aimed.waitKind() == com.wrights.job.BuildJob.WAIT_ITEMS;
                String title = aimed.undoing() ? "hud.wrights.job_undoing" : aimed.paused() ? "hud.wrights.job_paused" : stalled ? "hud.wrights.job_stalled" : "hud.wrights.job_building";
                line = Component.translatable(title, aimed.name());
                String time = WrightsClientState.timeLeft(aimed);
                int percent = WrightsClientState.percent(aimed);
                sub = time.isEmpty()
                        ? Component.translatable("hud.wrights.job_detail", aimed.done(), aimed.total(), percent, aimed.skipped())
                        : Component.translatable("hud.wrights.job_detail_time", aimed.done(), aimed.total(), percent, aimed.skipped(), time);
                note = WrightsClientState.jobWait(aimed);
            }
        } else if (WrightsClientState.mode == WrightsClientState.Mode.NONE) {
            line = Component.translatable("hud.wrights.task_ready_sub");
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int taskWidth = minecraft.font.width(task) + 16;
        int detailWidth = 0;
        int detailHeight = 0;
        if (line != null) {
            detailWidth = minecraft.font.width(line);
            if (sub != null) {
                detailWidth = Math.max(detailWidth, minecraft.font.width(sub));
            }
            if (note != null) {
                detailWidth = Math.max(detailWidth, minecraft.font.width(note));
            }
            detailWidth += 16;
            detailHeight = sub == null ? 18 : note == null ? 30 : 42;
        }
        int stackWidth = Math.max(taskWidth, detailWidth);
        int stackHeight = 14 + (detailHeight == 0 ? 0 : 4 + detailHeight);
        int stackX = HudAnchor.hintX(graphics.guiWidth(), stackWidth);
        int stackY = HudAnchor.hintY(graphics.guiHeight(), stackHeight);
        int center = stackX + stackWidth / 2;
        int taskX = center - taskWidth / 2;
        graphics.fill(taskX, stackY, taskX + taskWidth, stackY + 14, 0xC0101822);
        graphics.fill(taskX, stackY, taskX + 2, stackY + 14, WrightsUi.COPPER);
        graphics.drawString(minecraft.font, task, taskX + 8, stackY + 3, WrightsUi.COPPER, false);
        if (line == null && !showModes(minecraft)) {
            return;
        }
        if (line != null) {
            int top = stackY + 18;
            int x = center - detailWidth / 2;
            graphics.fill(x, top, x + detailWidth, top + detailHeight, 0xC0101822);
            graphics.fill(x, top, x + 2, top + detailHeight, WrightsUi.COPPER);
            graphics.drawString(minecraft.font, line, x + 8, top + 4, WrightsUi.TEXT, false);
            if (sub != null) {
                graphics.drawString(minecraft.font, sub, x + 8, top + 16, WrightsUi.MUTED, false);
            }
            if (note != null) {
                graphics.drawString(minecraft.font, note, x + 8, top + 28, 0xFFF0C14A, false);
            }
        }
        if (showMainModes(minecraft)) {
            drawMainModes(graphics, minecraft);
        } else if (showModes(minecraft)) {
            drawBuildModes(graphics, minecraft);
        }
    }

    private static Component taskLine() {
        if (WrightsClientState.mode == WrightsClientState.Mode.CAPTURE) {
            return Component.translatable("hud.wrights.task_new", Component.translatable(styleKey(WrightsClientState.captureStyle)));
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.BUILD) {
            return Component.translatable("hud.wrights.task_build_tool", Component.translatable(WrightsClientState.bindSupply ? "hud.wrights.tool_bind" : "hud.wrights.tool_build"));
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.JOBS) {
            return Component.translatable("hud.wrights.task_jobs");
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.MANAGE) {
            return Component.translatable("hud.wrights.task_manage");
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.INFO) {
            return Component.translatable("hud.wrights.task_info");
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.LINK) {
            return Component.translatable("hud.wrights.task_link");
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.SAVING) {
            return Component.translatable("hud.wrights.task_saving");
        }
        return Component.translatable("hud.wrights.task_ready");
    }

    private static boolean showMainModes(Minecraft minecraft) {
        boolean control = InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
        return minecraft.player.isShiftKeyDown() && control
                || WrightsClientState.mainPicker && WrightsClientState.styleHudTicks > 0;
    }

    private static void drawMainModes(GuiGraphics graphics, Minecraft minecraft) {
        Component title = Component.translatable("hud.wrights.main_modes");
        Component how = Component.translatable("hud.wrights.main_how");
        Component build = Component.translatable("hud.wrights.main_build");
        Component jobs = Component.translatable("hud.wrights.main_jobs");
        Component manage = Component.translatable("hud.wrights.main_manage");
        Component info = Component.translatable("hud.wrights.main_info");
        Component link = Component.translatable("hud.wrights.main_link");
        Component selected = Component.translatable("hud.wrights.mode_selected");
        int width = 188;
        width = Math.max(width, minecraft.font.width(title) + 28);
        width = Math.max(width, minecraft.font.width(build) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(jobs) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(manage) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(info) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(link) + minecraft.font.width(selected) + 36);
        int row = 18;
        int textWidth = width - 24;
        int footer = footerHeight(minecraft, how, textWidth);
        int height = 28 + row * 5 + footer;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 8;
        WrightsUi.panel(graphics, x, y, width, height);
        graphics.drawString(minecraft.font, title, x + 12, y + 8, WrightsUi.TEXT, false);
        boolean building = WrightsClientState.mode == WrightsClientState.Mode.BUILD
                || WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                || WrightsClientState.mode == WrightsClientState.Mode.SAVING;
        drawMainRow(graphics, minecraft, x, y + 24, width, building, build, selected);
        drawMainRow(graphics, minecraft, x, y + 24 + row, width, WrightsClientState.mode == WrightsClientState.Mode.JOBS, jobs, selected);
        drawMainRow(graphics, minecraft, x, y + 24 + row * 2, width, WrightsClientState.mode == WrightsClientState.Mode.MANAGE, manage, selected);
        drawMainRow(graphics, minecraft, x, y + 24 + row * 3, width, WrightsClientState.mode == WrightsClientState.Mode.INFO, info, selected);
        drawMainRow(graphics, minecraft, x, y + 24 + row * 4, width, WrightsClientState.mode == WrightsClientState.Mode.LINK, link, selected);
        drawFooter(graphics, minecraft, how, x + 12, y + height - footer + 4, textWidth, WrightsUi.MUTED);
    }

    private static void drawMainRow(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, boolean on, Component label, Component selected) {
        if (on) {
            graphics.fill(x + 8, y - 3, x + width - 8, y + 11, 0xA0C47A3A);
        }
        graphics.drawString(minecraft.font, label, x + 14, y, on ? 0xFFFFFFFF : WrightsUi.MUTED, false);
        if (on) {
            int markWidth = minecraft.font.width(selected);
            graphics.drawString(minecraft.font, selected, x + width - markWidth - 14, y, 0xFFFFFFFF, false);
        }
    }

    private static boolean showModes(Minecraft minecraft) {
        return WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                && (minecraft.player.isShiftKeyDown() || WrightsClientState.styleHudTicks > 0)
                || WrightsClientState.mode == WrightsClientState.Mode.BUILD
                && (minecraft.player.isShiftKeyDown() || WrightsClientState.styleHudTicks > 0);
    }

    private static void drawBuildModes(GuiGraphics graphics, Minecraft minecraft) {
        Component title = Component.translatable("hud.wrights.build_modes");
        boolean making = WrightsClientState.mode == WrightsClientState.Mode.CAPTURE
                || WrightsClientState.mode == WrightsClientState.Mode.SAVING;
        Component how = making
                ? (WrightsClientState.styleAnnounce
                        ? Component.translatable("hud.wrights.mode_now", Component.translatable(styleKey(WrightsClientState.captureStyle)))
                        : Component.translatable("hud.wrights.mode_how"))
                : WrightsClientState.bindSupply
                        ? Component.translatable("hud.wrights.bind_explain")
                        : WrightsClientState.buildId == null
                                ? Component.translatable("hud.wrights.pick_blueprint")
                                : Component.translatable("hud.wrights.build_explain");
        Component use = Component.translatable("hud.wrights.tool_build");
        Component fresh = Component.translatable("hud.wrights.tool_new");
        Component bind = Component.translatable("hud.wrights.tool_bind");
        Component selected = Component.translatable("hud.wrights.mode_selected");
        Component corners = Component.translatable("hud.wrights.mode_corners");
        Component box = Component.translatable("hud.wrights.mode_box");
        Component move = Component.translatable("hud.wrights.mode_move");
        Component need = Component.translatable("hud.wrights.mode_move_need");
        boolean canMove = WrightsClientState.hasBox();
        int width = 188;
        width = Math.max(width, minecraft.font.width(title) + 28);
        width = Math.max(width, minecraft.font.width(use) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(fresh) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(bind) + minecraft.font.width(selected) + 36);
        int nest = 18;
        if (making) {
            width = Math.max(width, minecraft.font.width(corners) + minecraft.font.width(selected) + 36 + nest);
            width = Math.max(width, minecraft.font.width(box) + minecraft.font.width(selected) + 36 + nest);
            width = Math.max(width, minecraft.font.width(move) + minecraft.font.width(canMove ? selected : need) + 36 + nest);
        }
        int row = 18;
        int rows = 3 + (making ? 3 : 0);
        int textWidth = width - 24;
        int footer = footerHeight(minecraft, how, textWidth);
        int height = 28 + row * rows + footer;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 8;
        WrightsUi.panel(graphics, x, y, width, height);
        graphics.drawString(minecraft.font, title, x + 12, y + 8, WrightsUi.TEXT, false);
        boolean building = WrightsClientState.mode == WrightsClientState.Mode.BUILD;
        int yRow = y + 24;
        drawToolRow(graphics, minecraft, x, yRow, width, making, fresh, selected);
        int yAfterNew = yRow + row;
        if (making) {
            int subY = yAfterNew;
            int railX = x + 20;
            graphics.fill(x + 14, subY - 4, x + width - 10, subY + row * 3 - 6, 0x55101822);
            graphics.fill(railX, subY - 2, railX + 2, subY + row * 2 + 4, WrightsUi.COPPER_DIM);
            drawModeRow(graphics, minecraft, x, subY, width, nest, WrightsClientState.CaptureStyle.CORNERS, corners, selected, true, null);
            drawModeRow(graphics, minecraft, x, subY + row, width, nest, WrightsClientState.CaptureStyle.BOX, box, selected, true, null);
            drawModeRow(graphics, minecraft, x, subY + row * 2, width, nest, WrightsClientState.CaptureStyle.MOVE, move, selected, canMove, need);
            yAfterNew = subY + row * 3;
        }
        drawToolRow(graphics, minecraft, x, yAfterNew, width, building && !WrightsClientState.bindSupply, use, selected);
        drawToolRow(graphics, minecraft, x, yAfterNew + row, width, building && WrightsClientState.bindSupply, bind, selected);
        int foot = WrightsClientState.styleAnnounce ? 0xFFF2C14E : WrightsUi.MUTED;
        drawFooter(graphics, minecraft, how, x + 12, y + height - footer + 4, textWidth, foot);
    }

    private static int footerHeight(Minecraft minecraft, Component text, int width) {
        return 8 + Math.max(1, minecraft.font.split(text, width).size()) * 10;
    }

    private static void drawFooter(GuiGraphics graphics, Minecraft minecraft, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = minecraft.font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(minecraft.font, lines.get(i), x, y + i * 10, color, false);
        }
    }

    private static void drawToolRow(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, boolean on, Component label, Component selected) {
        if (on) {
            graphics.fill(x + 8, y - 3, x + width - 8, y + 11, 0xA0C47A3A);
        }
        graphics.drawString(minecraft.font, label, x + 14, y, on ? 0xFFFFFFFF : WrightsUi.MUTED, false);
        if (on) {
            int markWidth = minecraft.font.width(selected);
            graphics.drawString(minecraft.font, selected, x + width - markWidth - 14, y, 0xFFFFFFFF, false);
        }
    }

    private static Component supplyChoice(Minecraft minecraft) {
        BlockPos aim = SelectionRenderer.supplyTarget(minecraft);
        if (aim == null || minecraft.level == null) {
            if (WrightsClientState.supplyBound && WrightsClientState.supplyPos != null) {
                BlockPos bound = WrightsClientState.supplyPos;
                return Component.translatable("screen.wrights.supply_at", bound.getX(), bound.getY(), bound.getZ());
            }
            return Component.translatable("screen.wrights.supply_aim");
        }
        return minecraft.level.getBlockState(aim).getBlock().getName();
    }

    private static Component volumeLine() {
        return Component.translatable(
                "screen.wrights.volume",
                BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 0),
                BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 1),
                BoxEdits.span(WrightsClientState.cornerA, WrightsClientState.cornerB, 2),
                BoxEdits.volume(WrightsClientState.cornerA, WrightsClientState.cornerB),
                WrightsClientState.solidSelection());
    }

    private static void drawModeRow(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, int inset, WrightsClientState.CaptureStyle style, Component label, Component selected, boolean enabled, Component locked) {
        boolean on = enabled && WrightsClientState.captureStyle == style;
        int textX = x + 14 + inset;
        graphics.fill(x + 20, y + 3, textX - 4, y + 5, WrightsUi.COPPER_DIM);
        if (on) {
            graphics.fill(textX - 6, y - 3, x + width - 8, y + 11, 0xA0C47A3A);
        }
        graphics.drawString(minecraft.font, label, textX, y, on ? 0xFFFFFFFF : (enabled ? WrightsUi.MUTED : 0xFF5C6A74), false);
        Component mark = on ? selected : (!enabled ? locked : null);
        if (mark != null) {
            int markWidth = minecraft.font.width(mark);
            graphics.drawString(minecraft.font, mark, x + width - markWidth - 14, y, on ? 0xFFFFFFFF : 0xFF5C6A74, false);
        }
    }

    private static String styleKey(WrightsClientState.CaptureStyle style) {
        if (style == WrightsClientState.CaptureStyle.BOX) {
            return "hud.wrights.mode_box";
        }
        if (style == WrightsClientState.CaptureStyle.MOVE) {
            return "hud.wrights.mode_move";
        }
        return "hud.wrights.mode_corners";
    }

    private static void drawJobNotices(GuiGraphics graphics, Minecraft minecraft) {
        WrightsClientState.DockNotice dock = WrightsClientState.dockNotice;
        if (WrightsClientState.jobNotices.isEmpty() && dock == null) {
            return;
        }
        int headerH = 18;
        int bodyH = 28;
        int height = headerH + bodyH;
        List<Component> dockLines = dockLines(dock);
        int dockHeight = dockLines.isEmpty() ? 0 : headerH + 8 + dockLines.size() * 11;
        int maxWidth = 0;
        int[] widths = new int[WrightsClientState.jobNotices.size()];
        for (int i = 0; i < WrightsClientState.jobNotices.size(); i++) {
            WrightsClientState.JobNotice notice = WrightsClientState.jobNotices.get(i);
            Component header = Component.translatable("hud.wrights.job_finished");
            Component name = Component.literal(notice.name);
            Component time = notice.seconds < 1
                    ? Component.translatable("hud.wrights.job_time_under")
                    : Component.translatable("hud.wrights.job_time", duration(notice.seconds));
            widths[i] = Math.min(240, Math.max(
                    minecraft.font.width(header) + 30,
                    Math.max(minecraft.font.width(name), minecraft.font.width(time)) + 18));
            maxWidth = Math.max(maxWidth, widths[i]);
        }
        int dockWidth = 0;
        if (!dockLines.isEmpty()) {
            Component header = Component.translatable("hud.wrights.dock_gone");
            dockWidth = minecraft.font.width(header) + 30;
            for (Component line : dockLines) {
                dockWidth = Math.max(dockWidth, minecraft.font.width(line) + 18);
            }
            dockWidth = Math.min(240, dockWidth);
            maxWidth = Math.max(maxWidth, dockWidth);
        }
        int cards = WrightsClientState.jobNotices.size() + (dockLines.isEmpty() ? 0 : 1);
        int total = WrightsClientState.jobNotices.size() * height + dockHeight + Math.max(0, cards - 1) * 4;
        int stackX = HudAnchor.toastX(graphics.guiWidth(), maxWidth);
        int y = HudAnchor.toastY(graphics.guiHeight(), total);
        int spot = HudAnchor.toastSpot();
        for (int i = 0; i < WrightsClientState.jobNotices.size(); i++) {
            WrightsClientState.JobNotice notice = WrightsClientState.jobNotices.get(i);
            Component header = Component.translatable("hud.wrights.job_finished");
            Component name = Component.literal(notice.name);
            Component time = notice.seconds < 1
                    ? Component.translatable("hud.wrights.job_time_under")
                    : Component.translatable("hud.wrights.job_time", duration(notice.seconds));
            int width = widths[i];
            int x = toastX(spot, stackX, maxWidth, width);
            int alpha = notice.ticks > 20 ? 0xE0 : Math.max(0, 0xE0 * notice.ticks / 20);
            paintToast(graphics, minecraft, x, y, width, height, headerH, alpha, header, List.of(name, time));
            y += height + 4;
        }
        if (!dockLines.isEmpty()) {
            int x = toastX(spot, stackX, maxWidth, dockWidth);
            int alpha = dock.ticks > 20 ? 0xE0 : Math.max(0, 0xE0 * dock.ticks / 20);
            paintToast(graphics, minecraft, x, y, dockWidth, dockHeight, headerH, alpha,
                    Component.translatable("hud.wrights.dock_gone"), dockLines);
        }
    }

    private static List<Component> dockLines(WrightsClientState.DockNotice dock) {
        List<Component> lines = new java.util.ArrayList<>();
        if (dock == null) {
            return lines;
        }
        if (dock.docked > 0) {
            if (dock.port == null || dock.port.isBlank()) {
                lines.add(Component.translatable("hud.wrights.dock_toast_port", dock.docked, dock.dx, dock.dy, dock.dz));
            } else {
                lines.add(Component.translatable("hud.wrights.dock_toast_port_named", dock.docked,
                        Component.translatable("block.wrights.wrightport.named", dock.port), dock.dx, dock.dy, dock.dz));
            }
        }
        if (dock.digital > 0) {
            lines.add(Component.translatable("hud.wrights.dock_toast_digital", dock.digital, dock.sx, dock.sy, dock.sz));
        }
        if (dock.dropped > 0) {
            lines.add(Component.translatable("hud.wrights.dock_toast_ground", dock.dropped, dock.gx, dock.gy, dock.gz));
        }
        return lines;
    }

    private static void paintToast(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, int height, int headerH, int alpha, Component header, List<Component> lines) {
        int panel = (alpha << 24) | 0x101822;
        int toolbar = (alpha << 24) | 0x1C2A36;
        int copper = (alpha << 24) | (WrightsUi.COPPER & 0xFFFFFF);
        int edge = (alpha << 24) | 0x31404E;
        int border = (alpha << 24) | 0x3A4C5C;
        int text = (alpha << 24) | (WrightsUi.TEXT & 0xFFFFFF);
        int muted = (alpha << 24) | (WrightsUi.MUTED & 0xFFFFFF);
        graphics.fill(x, y, x + width, y + height, panel);
        WrightsUi.border(graphics, x, y, width, height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + headerH, toolbar);
        graphics.fill(x + 1, y + 1, x + 3, y + headerH, copper);
        graphics.fill(x + 1, y + headerH - 1, x + width - 1, y + headerH, edge);
        drawInfoBubble(graphics, x + 8, y + 5, alpha);
        graphics.drawString(minecraft.font, header, x + 20, y + 5, text, false);
        int lineY = y + headerH + 5;
        for (Component line : lines) {
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(line.getString(), width - 16), x + 8, lineY, muted, false);
            lineY += 11;
        }
    }

    private static int toastX(int spot, int stackX, int maxWidth, int width) {
        if (spot == HudAnchor.RIGHT) {
            return stackX + maxWidth - width;
        }
        if (spot == HudAnchor.LEFT) {
            return stackX;
        }
        return stackX + (maxWidth - width) / 2;
    }

    static void drawSampleHint(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width) {
        Component task = Component.translatable("hud.wrights.task_ready");
        Component line = Component.translatable("hud.wrights.task_ready_sub");
        graphics.fill(x, y, x + width, y + 14, 0xC0101822);
        graphics.fill(x, y, x + 2, y + 14, WrightsUi.COPPER);
        graphics.drawString(minecraft.font, task, x + 8, y + 3, WrightsUi.COPPER, false);
        graphics.fill(x, y + 18, x + width, y + 36, 0xC0101822);
        graphics.fill(x, y + 18, x + 2, y + 36, WrightsUi.COPPER);
        graphics.drawString(minecraft.font, line, x + 8, y + 22, WrightsUi.TEXT, false);
    }

    static int sampleHintWidth(Minecraft minecraft) {
        return Math.max(minecraft.font.width(Component.translatable("hud.wrights.task_ready")),
                minecraft.font.width(Component.translatable("hud.wrights.task_ready_sub"))) + 16;
    }

    static void drawSampleToast(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width) {
        Component header = Component.translatable("hud.wrights.job_finished");
        Component name = Component.translatable("hud.wrights.sample_toast");
        Component time = Component.translatable("hud.wrights.job_time", "12s");
        graphics.fill(x, y, x + width, y + 46, 0xE0101822);
        WrightsUi.border(graphics, x, y, width, 46, 0xFF3A4C5C);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, 0xE01C2A36);
        graphics.fill(x + 1, y + 1, x + 3, y + 18, WrightsUi.COPPER);
        graphics.drawString(minecraft.font, header, x + 8, y + 5, WrightsUi.TEXT, false);
        graphics.drawString(minecraft.font, name, x + 8, y + 23, WrightsUi.MUTED, false);
        graphics.drawString(minecraft.font, time, x + 8, y + 34, WrightsUi.MUTED, false);
    }

    static int sampleToastWidth(Minecraft minecraft) {
        return Math.min(240, Math.max(
                minecraft.font.width(Component.translatable("hud.wrights.job_finished")) + 30,
                Math.max(minecraft.font.width(Component.translatable("hud.wrights.sample_toast")),
                        minecraft.font.width(Component.translatable("hud.wrights.job_time", "12s"))) + 18));
    }

    private static void drawInfoBubble(GuiGraphics graphics, int x, int y, int alpha) {
        int fill = (alpha << 24) | (WrightsUi.BLUE & 0xFFFFFF);
        int ink = (alpha << 24) | 0x101822;
        graphics.fill(x + 1, y, x + 7, y + 8, fill);
        graphics.fill(x, y + 1, x + 8, y + 7, fill);
        graphics.fill(x + 3, y + 1, x + 5, y + 3, ink);
        graphics.fill(x + 3, y + 4, x + 5, y + 7, ink);
    }

    private static String duration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int rest = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + rest + "s";
        }
        return rest + "s";
    }
}
