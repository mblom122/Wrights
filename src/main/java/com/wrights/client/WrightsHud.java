package com.wrights.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.wrights.WrightsMod;
import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.blueprint.BoxEdits;
import com.wrights.blueprint.Shapes;

import net.minecraft.core.Direction;

@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class WrightsHud {
    /** Short help under the mode title — only for the first seconds after a mode/submode change. */
    private static final long MODE_HELP_MS = 5000L;
    private static String modeHelpKey = "";
    private static long modeHelpUntilMs;

    private WrightsHud() {
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        drawDockWait(event.getGuiGraphics(), minecraft);
        drawJobNotices(event.getGuiGraphics(), minecraft);
        if (minecraft.screen != null || !com.wrights.item.ControllerItem.isHeld(minecraft.player)) {
            return;
        }
        Component line = null;
        Component sub = null;
        Component note = null;
        Component offset = mirrorOffset(minecraft);
        List<Component> look = List.of();
        Component task = taskLine();
        boolean showHelp = refreshModeHelp();
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
            if (WrightsClientState.exchangeToolActive()) {
                line = exchangeStatusLine();
                sub = Component.translatable("hud.wrights.exchange_explain");
            } else if (WrightsClientState.shapeToolActive()) {
                line = shapeStatusLine();
                sub = Component.translatable(shapeFooter(minecraft));
                if (WrightsClientConfig.shapeMaterials().isEmpty()) {
                    note = Component.translatable("hud.wrights.shape_pick_material");
                }
            } else if (WrightsClientState.mirrorToolActive()) {
                line = mirrorStatusLine();
                sub = Component.translatable(mirrorFooter(minecraft));
            } else if (WrightsClientState.buildId == null) {
                line = Component.translatable("hud.wrights.pick_blueprint");
            } else if (WrightsClientState.bindSupply) {
                line = Component.translatable("hud.wrights.bind_explain");
                sub = supplyChoice(minecraft);
            } else {
                line = Component.translatable("hud.wrights.build", WrightsClientState.buildName);
                sub = Component.translatable(WrightsClientState.supplyBound ? "hud.wrights.build_chest" : "hud.wrights.build_inventory");
                note = Component.translatable("hud.wrights.rotate_scroll");
            }
        } else if (WrightsClientState.mode == WrightsClientState.Mode.MANAGE) {
            line = Component.translatable("hud.wrights.manage_explain");
        } else if (WrightsClientState.mode == WrightsClientState.Mode.INFO) {
            look = InfoLook.describe(minecraft);
            if (look.isEmpty()) {
                line = Component.translatable("hud.wrights.info_explain");
            } else {
                offset = null;
            }
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
                        ? (aimed.held() > 0
                                ? Component.translatable("hud.wrights.job_detail_held", aimed.done(), aimed.total(), percent, aimed.skipped(), aimed.held())
                                : Component.translatable("hud.wrights.job_detail", aimed.done(), aimed.total(), percent, aimed.skipped()))
                        : (aimed.held() > 0
                                ? Component.translatable("hud.wrights.job_detail_held_time", aimed.done(), aimed.total(), percent, aimed.skipped(), aimed.held(), time)
                                : Component.translatable("hud.wrights.job_detail_time", aimed.done(), aimed.total(), percent, aimed.skipped(), time));
                note = WrightsClientState.jobWait(aimed);
            }
        } else if (WrightsClientState.mode == WrightsClientState.Mode.NONE) {
            line = Component.translatable("hud.wrights.task_ready_sub");
        }
        // Timed help under the mode title. Live Info look / Jobs aim / bind / saving stay visible.
        boolean keepDetail = persistentModeDetail(look);
        if (!showHelp && !keepDetail) {
            line = null;
            sub = null;
            note = null;
            offset = null;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int taskWidth = minecraft.font.width(task) + 16;
        int detailWidth = 0;
        int detailHeight = 0;
        if (!look.isEmpty()) {
            for (Component row : look) {
                detailWidth = Math.max(detailWidth, minecraft.font.width(row));
            }
            detailWidth += 16;
            detailHeight = 6 + look.size() * 12;
        } else if (line != null) {
            boolean hideNote = note != null && showCtrlHelp(minecraft);
            detailWidth = minecraft.font.width(line);
            if (sub != null) {
                detailWidth = Math.max(detailWidth, minecraft.font.width(sub));
            }
            if (note != null && !hideNote) {
                detailWidth = Math.max(detailWidth, minecraft.font.width(note));
            }
            if (offset != null) {
                detailWidth = Math.max(detailWidth, minecraft.font.width(offset));
            }
            detailWidth += 16;
            int rows = 1 + (sub == null ? 0 : 1) + (note == null || hideNote ? 0 : 1) + (offset == null ? 0 : 1);
            detailHeight = 6 + rows * 12;
        }
        // Mode title stays centered just above the hotbar; timed help stacks above it.
        int taskH = 14;
        int taskX = HudAnchor.aboveHotbarX(graphics.guiWidth(), taskWidth);
        int taskY = HudAnchor.aboveHotbarY(graphics.guiHeight(), taskH);
        graphics.fill(taskX, taskY, taskX + taskWidth, taskY + taskH, 0xC0101822);
        graphics.fill(taskX, taskY, taskX + 2, taskY + taskH, WrightsUi.COPPER);
        graphics.drawString(minecraft.font, task, taskX + 8, taskY + 3, WrightsUi.COPPER, false);
        if (line == null && look.isEmpty() && !showModes(minecraft) && !showMainModes(minecraft) && !showCtrlHelp(minecraft)) {
            return;
        }
        if (!look.isEmpty() || line != null) {
            int top = taskY - 4 - detailHeight;
            int x = HudAnchor.aboveHotbarX(graphics.guiWidth(), detailWidth);
            if (top < 2) {
                top = 2;
            }
            graphics.fill(x, top, x + detailWidth, top + detailHeight, 0xC0101822);
            graphics.fill(x, top, x + 2, top + detailHeight, WrightsUi.COPPER);
            int textY = top + 4;
            if (!look.isEmpty()) {
                for (int i = 0; i < look.size(); i++) {
                    graphics.drawString(minecraft.font, look.get(i), x + 8, textY, i == 0 ? WrightsUi.TEXT : WrightsUi.MUTED, false);
                    textY += 12;
                }
            } else {
                graphics.drawString(minecraft.font, line, x + 8, textY, WrightsUi.TEXT, false);
                if (sub != null) {
                    textY += 12;
                    graphics.drawString(minecraft.font, sub, x + 8, textY, WrightsUi.MUTED, false);
                }
                // Hide the one-line Ctrl tip while the full Ctrl hold panel is open.
                if (note != null && !showCtrlHelp(minecraft)) {
                    textY += 12;
                    graphics.drawString(minecraft.font, note, x + 8, textY, 0xFFF0C14A, false);
                }
                if (offset != null) {
                    textY += 12;
                    graphics.drawString(minecraft.font, offset, x + 8, textY, WrightsUi.TEAL, false);
                }
            }
        }
        if (showMainModes(minecraft)) {
            drawMainModes(graphics, minecraft);
        } else if (showCtrlHelp(minecraft)) {
            drawCtrlHelp(graphics, minecraft);
        } else if (showModes(minecraft)) {
            drawBuildModes(graphics, minecraft);
        }
    }

    private static boolean controlDown(Minecraft minecraft) {
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** Ctrl alone — shortcuts for the current capture/build tool. */
    private static boolean showCtrlHelp(Minecraft minecraft) {
        return controlDown(minecraft) && !minecraft.player.isShiftKeyDown() && ctrlHelpKind() != CtrlHelp.NONE;
    }

    private enum CtrlHelp {
        NONE,
        RESIZE,
        ROTATE,
        SIZE
    }

    private static CtrlHelp ctrlHelpKind() {
        if (WrightsClientState.mode == WrightsClientState.Mode.CAPTURE && WrightsClientState.hasBox()) {
            return CtrlHelp.RESIZE;
        }
        if (WrightsClientState.exchangeToolActive() && WrightsConfig.allowExchange()) {
            return CtrlHelp.SIZE;
        }
        if (WrightsClientState.shapeToolActive() && WrightsConfig.allowShape()) {
            return CtrlHelp.ROTATE;
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.BUILD
                && WrightsClientState.buildId != null
                && !WrightsClientState.bindSupply
                && !(WrightsClientState.mirrorToolActive() && WrightsClientConfig.mirrorBlocks())) {
            return CtrlHelp.ROTATE;
        }
        return CtrlHelp.NONE;
    }

    private static void drawCtrlHelp(GuiGraphics graphics, Minecraft minecraft) {
        CtrlHelp kind = ctrlHelpKind();
        String titleKey = switch (kind) {
            case SIZE -> "hud.wrights.ctrl_size_title";
            case ROTATE -> "hud.wrights.ctrl_rotate_title";
            default -> "hud.wrights.ctrl_resize_title";
        };
        String upKey = switch (kind) {
            case SIZE -> "hud.wrights.ctrl_size_up";
            case ROTATE -> "hud.wrights.ctrl_rotate_up";
            default -> "hud.wrights.ctrl_resize_up";
        };
        String downKey = switch (kind) {
            case SIZE -> "hud.wrights.ctrl_size_down";
            case ROTATE -> "hud.wrights.ctrl_rotate_down";
            default -> "hud.wrights.ctrl_resize_down";
        };
        String tipKey = switch (kind) {
            case SIZE -> "hud.wrights.ctrl_size_tip";
            case ROTATE -> WrightsClientState.shapeToolActive()
                    ? "hud.wrights.ctrl_rotate_shape_tip"
                    : "hud.wrights.ctrl_rotate_tip";
            default -> "hud.wrights.ctrl_resize_tip";
        };
        Component title = Component.translatable(titleKey);
        Component up = Component.translatable(upKey);
        Component down = Component.translatable(downKey);
        Component tip = Component.translatable(tipKey);
        int width = 180;
        width = Math.max(width, minecraft.font.width(title) + 28);
        width = Math.max(width, minecraft.font.width(up) + 28);
        width = Math.max(width, minecraft.font.width(down) + 28);
        width = Math.max(width, minecraft.font.width(tip) + 28);
        int height = 28 + 18 * 2 + 16;
        int x = HudAnchor.modeX(graphics.guiWidth(), width);
        int y = HudAnchor.modeY(graphics.guiHeight(), height);
        WrightsUi.panel(graphics, x, y, width, height);
        graphics.drawString(minecraft.font, title, x + 12, y + 8, WrightsUi.TEXT, false);
        graphics.fill(x + 8, y + 21, x + width - 8, y + 35, 0xA0C47A3A);
        graphics.drawString(minecraft.font, up, x + 14, y + 24, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, down, x + 14, y + 42, WrightsUi.MUTED, false);
        graphics.drawString(minecraft.font, tip, x + 12, y + 62, 0xFFF0C14A, false);
    }

    /**
     * Restart the 5s help window when mode or submode changes.
     * @return true while the under-mode help tooltip should still show
     */
    private static boolean refreshModeHelp() {
        String key = modeHelpFingerprint();
        long now = System.currentTimeMillis();
        if (!key.equals(modeHelpKey)) {
            modeHelpKey = key;
            modeHelpUntilMs = now + MODE_HELP_MS;
        }
        return now < modeHelpUntilMs;
    }

    private static String modeHelpFingerprint() {
        WrightsClientState.Mode mode = WrightsClientState.mode;
        StringBuilder key = new StringBuilder(mode.name());
        if (mode == WrightsClientState.Mode.CAPTURE || mode == WrightsClientState.Mode.SAVING) {
            key.append('|').append(WrightsClientState.captureStyle.name());
        } else if (mode == WrightsClientState.Mode.BUILD) {
            if (WrightsClientState.exchangeToolActive()) {
                key.append("|exchange|").append(WrightsClientConfig.exchangeForm().name());
            } else if (WrightsClientState.shapeToolActive()) {
                key.append("|shape|").append(WrightsClientConfig.SHAPE_KIND.get());
            } else if (WrightsClientState.mirrorToolActive()) {
                key.append("|mirror|").append(WrightsClientConfig.mirrorBlocks());
            } else if (WrightsClientState.bindSupply) {
                key.append("|bind");
            } else {
                key.append("|blueprint|").append(WrightsClientState.buildId == null ? "" : WrightsClientState.buildId.toString());
            }
        }
        return key.toString();
    }

    /** Details that should stay after the timed help fades (live status, not mode tips). */
    private static boolean persistentModeDetail(List<Component> look) {
        if (!look.isEmpty()) {
            return true;
        }
        if (WrightsClientState.bindPort != null || WrightsClientState.bindSupply) {
            return true;
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.SAVING) {
            return true;
        }
        return WrightsClientState.mode == WrightsClientState.Mode.JOBS && WrightsClientState.jobAimed() != null;
    }

    private static Component taskLine() {
        if (WrightsClientState.mode == WrightsClientState.Mode.CAPTURE) {
            return Component.translatable("hud.wrights.task_new", Component.translatable(styleKey(WrightsClientState.captureStyle)));
        }
        if (WrightsClientState.mode == WrightsClientState.Mode.BUILD) {
            String tool;
            if (WrightsClientState.exchangeToolActive()) {
                tool = "hud.wrights.tool_exchange";
            } else if (WrightsClientState.shapeToolActive()) {
                tool = "hud.wrights.tool_shape";
            } else if (WrightsClientState.mirrorToolActive()) {
                tool = "hud.wrights.tool_mirror";
            } else if (WrightsClientState.bindSupply) {
                tool = "hud.wrights.tool_bind";
            } else {
                tool = "hud.wrights.tool_build";
            }
            return Component.translatable("hud.wrights.task_build_tool", Component.translatable(tool));
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
        return minecraft.player.isShiftKeyDown() && controlDown(minecraft)
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
        int width = 168;
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
        int x = HudAnchor.modeX(graphics.guiWidth(), width);
        int y = HudAnchor.modeY(graphics.guiHeight(), height);
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
        // Mode strip only lists tools — full keybind catalogs live in Help.
        Component how = WrightsClientState.styleAnnounce
                ? Component.translatable("hud.wrights.mode_now", Component.translatable(
                        making ? styleKey(WrightsClientState.captureStyle) : buildToolKey()))
                : Component.translatable("hud.wrights.mode_how");
        Component use = Component.translatable("hud.wrights.tool_build");
        Component fresh = Component.translatable("hud.wrights.tool_new");
        Component bind = Component.translatable("hud.wrights.tool_bind");
        Component mirror = Component.translatable("hud.wrights.tool_mirror");
        Component shape = Component.translatable("hud.wrights.tool_shape");
        Component exchange = Component.translatable("hud.wrights.tool_exchange");
        Component mirrorMark = Component.translatable(WrightsClientConfig.mirrorBlocks() ? "menu.wrights.mirror_blocks" : "menu.wrights.mirror_blueprints");
        Component shapeMark = Component.translatable("menu.wrights.shape_kind_" + com.wrights.blueprint.Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get()).name().toLowerCase());
        Component exchangeMark = Component.translatable(WrightsClientConfig.exchangeForm().langKey());
        Component selected = Component.translatable("hud.wrights.mode_selected");
        Component corners = Component.translatable("hud.wrights.mode_corners");
        Component box = Component.translatable("hud.wrights.mode_box");
        Component move = Component.translatable("hud.wrights.mode_move");
        Component need = Component.translatable("hud.wrights.mode_move_need");
        boolean canMove = WrightsClientState.hasBox();
        boolean showMirror = WrightsConfig.allowMirror();
        boolean showShape = WrightsConfig.allowShape();
        boolean showExchange = WrightsConfig.allowExchange();
        int width = 168;
        width = Math.max(width, minecraft.font.width(title) + 28);
        width = Math.max(width, minecraft.font.width(use) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(fresh) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(bind) + minecraft.font.width(selected) + 36);
        if (showMirror) {
            width = Math.max(width, minecraft.font.width(mirror) + minecraft.font.width(mirrorMark) + 36);
        }
        if (showShape) {
            width = Math.max(width, minecraft.font.width(shape) + minecraft.font.width(shapeMark) + 36);
        }
        if (showExchange) {
            width = Math.max(width, minecraft.font.width(exchange) + minecraft.font.width(exchangeMark) + 36);
        }
        int nest = 18;
        if (making) {
            width = Math.max(width, minecraft.font.width(corners) + minecraft.font.width(selected) + 36 + nest);
            width = Math.max(width, minecraft.font.width(box) + minecraft.font.width(selected) + 36 + nest);
            width = Math.max(width, minecraft.font.width(move) + minecraft.font.width(canMove ? selected : need) + 36 + nest);
        }
        int row = 18;
        int rows = 3 + (showMirror ? 1 : 0) + (showShape ? 1 : 0) + (showExchange ? 1 : 0) + (making ? 3 : 0);
        int textWidth = width - 24;
        int footer = footerHeight(minecraft, how, textWidth);
        int height = 28 + row * rows + footer;
        int x = HudAnchor.modeX(graphics.guiWidth(), width);
        int y = HudAnchor.modeY(graphics.guiHeight(), height);
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
        int toolY = yAfterNew;
        drawToolRow(graphics, minecraft, x, toolY, width, building && !WrightsClientState.bindSupply && !WrightsClientState.mirrorAim && !WrightsClientState.shapeAim && !WrightsClientState.exchangeAim, use, selected);
        toolY += row;
        if (showMirror) {
            drawToolRow(graphics, minecraft, x, toolY, width, building && WrightsClientState.mirrorAim, mirror, mirrorMark);
            toolY += row;
        }
        if (showExchange) {
            drawToolRow(graphics, minecraft, x, toolY, width, building && WrightsClientState.exchangeAim, exchange, exchangeMark);
            toolY += row;
        }
        if (showShape) {
            drawToolRow(graphics, minecraft, x, toolY, width, building && WrightsClientState.shapeAim, shape, shapeMark);
            toolY += row;
        }
        drawToolRow(graphics, minecraft, x, toolY, width, building && WrightsClientState.bindSupply, bind, selected);
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

    /** Signed distance from the mirror anchor to the block under the crosshair. */
    private static Component mirrorOffset(Minecraft minecraft) {
        if (!WrightsClientState.mirrorToolActive() || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        if (!WrightsClientConfig.mirrorPlaced(minecraft.level.dimension().location().toString())) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos anchor = WrightsClientConfig.mirrorAnchor();
        BlockPos at = hit.getBlockPos();
        return Component.translatable(
                "hud.wrights.mirror_offset",
                signed(at.getX() - anchor.getX()),
                signed(at.getY() - anchor.getY()),
                signed(at.getZ() - anchor.getZ()));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /** Title line: kind + size/height so the HUD shows the current Shape setup. */
    private static Component shapeStatusLine() {
        Shapes.Kind kind = Shapes.Kind.byId(WrightsClientConfig.SHAPE_KIND.get());
        Component name = Component.translatable("menu.wrights.shape_kind_" + kind.name().toLowerCase());
        return Component.translatable("hud.wrights.tool_shape_detail", name, shapeDimText(kind));
    }

    /** Title line: form + size so Exchange shows Floor/Wall/Pillar and its length. */
    private static Component exchangeStatusLine() {
        Shapes.ExchangeForm form = WrightsClientConfig.exchangeForm();
        Component name = Component.translatable(form.langKey());
        return Component.translatable("hud.wrights.tool_exchange_detail", name, exchangeDimText(form));
    }

    /** Title line: Blocks/Blueprints plus copies or axis mix. */
    private static Component mirrorStatusLine() {
        Component mode = Component.translatable(
                WrightsClientConfig.mirrorBlocks() ? "menu.wrights.mirror_blocks" : "menu.wrights.mirror_blueprints");
        int axes = WrightsConfig.allowedAxes(WrightsClientConfig.MIRROR_AXES.get()) & 7;
        if (axes != 0) {
            return Component.translatable("hud.wrights.tool_mirror_axes", mode, mirrorAxesText(axes));
        }
        int copies = WrightsConfig.allowedPattern(WrightsClientConfig.MIRROR_PATTERN.get());
        Direction face = Direction.from3DDataValue(WrightsClientConfig.MIRROR_FACE.get());
        Component faceName = Component.translatable("menu.wrights.face." + face.getSerializedName());
        return Component.translatable("hud.wrights.tool_mirror_copies", mode, copies, faceName);
    }

    private static Component shapeDimText(Shapes.Kind kind) {
        int size = WrightsClientConfig.SHAPE_SIZE.get();
        int height = WrightsClientConfig.SHAPE_HEIGHT.get();
        int thickness = WrightsClientConfig.SHAPE_THICKNESS.get();
        return switch (kind) {
            case SQUARE -> Component.translatable("hud.wrights.dim_wh", size, height);
            case BOX -> Component.translatable("hud.wrights.dim_wdh", size, thickness, height);
            case RING -> Component.translatable("hud.wrights.dim_size_band_along", size, thickness, height);
            case CUBE, RECTANGLE, CIRCLE, SPHERE -> Component.translatable("hud.wrights.dim_size", size);
            default -> kind.usesHeight()
                    ? Component.translatable("hud.wrights.dim_size_height", size, height)
                    : Component.translatable("hud.wrights.dim_size", size);
        };
    }

    private static Component exchangeDimText(Shapes.ExchangeForm form) {
        if (form.line()) {
            int length = WrightsClientConfig.exchangeLineLength();
            return form == Shapes.ExchangeForm.PILLAR
                    ? Component.translatable("hud.wrights.dim_height", length)
                    : Component.translatable("hud.wrights.dim_length", length);
        }
        int size = WrightsClientConfig.SHAPE_SIZE.get();
        if (form.plate() && !WrightsClientConfig.exchangeEqualSides()) {
            return Component.translatable("hud.wrights.dim_wh", size, WrightsClientConfig.SHAPE_HEIGHT.get());
        }
        return Component.translatable("hud.wrights.dim_size", size);
    }

    private static Component mirrorAxesText(int axes) {
        StringBuilder bits = new StringBuilder();
        if ((axes & 1) != 0) {
            bits.append('X');
        }
        if ((axes & 2) != 0) {
            bits.append('Y');
        }
        if ((axes & 4) != 0) {
            bits.append('Z');
        }
        return Component.literal(bits.toString());
    }

    private static String mirrorFooter(Minecraft minecraft) {
        boolean placed = minecraft.level != null && WrightsClientConfig.mirrorPlaced(minecraft.level.dimension().location().toString());
        if (WrightsClientConfig.mirrorBlocks()) {
            return placed ? "hud.wrights.mirror_blocks_ready" : "hud.wrights.mirror_blocks_explain";
        }
        return placed ? "hud.wrights.mirror_blueprint_ready" : "hud.wrights.mirror_blueprint_explain";
    }

    private static String shapeFooter(Minecraft minecraft) {
        if (WrightsConfig.allowShapeAnchor() && WrightsClientConfig.SHAPE_USE_ANCHOR.get()) {
            boolean placed = minecraft.level != null && WrightsClientConfig.shapePlaced(minecraft.level.dimension().location().toString());
            return placed ? "hud.wrights.shape_anchor_ready" : "hud.wrights.shape_anchor_explain";
        }
        if (!WrightsClientConfig.SHAPE_LOOK_LOCK.get()) {
            return WrightsClientState.shapeLookSnapActive()
                    ? "hud.wrights.shape_look_snap_ready"
                    : "hud.wrights.shape_look_explain";
        }
        return WrightsClientState.shapeLookSnapActive()
                ? "hud.wrights.shape_look_locked"
                : "hud.wrights.shape_look_lock_explain";
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

    private static String buildToolKey() {
        if (WrightsClientState.exchangeAim) {
            return "hud.wrights.tool_exchange";
        }
        if (WrightsClientState.shapeAim) {
            return "hud.wrights.tool_shape";
        }
        if (WrightsClientState.mirrorAim) {
            return "hud.wrights.tool_mirror";
        }
        if (WrightsClientState.bindSupply) {
            return "hud.wrights.tool_bind";
        }
        return "hud.wrights.tool_build";
    }

    private static void drawDockWait(GuiGraphics graphics, Minecraft minecraft) {
        int left = WrightsClientState.dockWaitRemaining;
        if (left <= 0) {
            return;
        }
        Component line = left == 1
                ? Component.translatable("hud.wrights.dock_wait_one")
                : Component.translatable("hud.wrights.dock_wait", left);
        int width = minecraft.font.width(line);
        int x = (minecraft.getWindow().getGuiScaledWidth() - width) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() / 2 - 12;
        graphics.fill(x - 8, y - 6, x + width + 8, y + 14, 0xAA060C14);
        graphics.drawString(minecraft.font, line, x, y, 0xFFE8DCC8, false);
    }

    private static void drawJobNotices(GuiGraphics graphics, Minecraft minecraft) {
        WrightsClientState.DockNotice dock = WrightsClientState.dockNotice;
        if (WrightsClientState.jobNotices.isEmpty() && dock == null) {
            return;
        }
        int headerH = 18;
        List<Component> dockLines = dockLines(dock);
        int dockHeight = dockLines.isEmpty() ? 0 : headerH + 8 + dockLines.size() * 11;
        int maxWidth = 0;
        int[] widths = new int[WrightsClientState.jobNotices.size()];
        int[] heights = new int[WrightsClientState.jobNotices.size()];
        for (int i = 0; i < WrightsClientState.jobNotices.size(); i++) {
            WrightsClientState.JobNotice notice = WrightsClientState.jobNotices.get(i);
            Component header = Component.translatable("hud.wrights.job_finished");
            Component time = noticeTime(notice);
            int width = minecraft.font.width(header) + 30;
            width = Math.max(width, minecraft.font.width(notice.name) + 18);
            if (hasDetail(notice)) {
                width = Math.max(width, minecraft.font.width(notice.detail) + 18);
            }
            width = Math.max(width, minecraft.font.width(time) + 18);
            widths[i] = Math.min(280, width);
            heights[i] = noticeHeight(notice);
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
        int total = dockHeight;
        for (int height : heights) {
            total += height;
        }
        total += Math.max(0, cards - 1) * 4;
        int stackX = HudAnchor.toastX(graphics.guiWidth(), maxWidth);
        int y = HudAnchor.toastY(graphics.guiHeight(), total);
        int spot = HudAnchor.toastSpot();
        for (int i = 0; i < WrightsClientState.jobNotices.size(); i++) {
            WrightsClientState.JobNotice notice = WrightsClientState.jobNotices.get(i);
            Component header = Component.translatable("hud.wrights.job_finished");
            Component time = noticeTime(notice);
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(notice.name);
            if (hasDetail(notice)) {
                lines.add(notice.detail);
            }
            lines.add(time);
            int width = widths[i];
            int x = toastX(spot, stackX, maxWidth, width);
            int alpha = notice.ticks > 20 ? 0xE0 : Math.max(0, 0xE0 * notice.ticks / 20);
            paintToast(graphics, minecraft, x, y, width, heights[i], headerH, alpha, header, lines);
            y += heights[i] + 4;
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
        Component name = Component.translatable("hud.wrights.tool_mirror").append(" - ").append(Component.translatable("hud.wrights.task_mine"));
        Component detail = Component.translatable("hud.wrights.done_mined", 4, 0);
        Component time = Component.translatable("hud.wrights.job_time", "12s");
        int height = sampleToastHeight();
        graphics.fill(x, y, x + width, y + height, 0xE0101822);
        WrightsUi.border(graphics, x, y, width, height, 0xFF3A4C5C);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, 0xE01C2A36);
        graphics.fill(x + 1, y + 1, x + 3, y + 18, WrightsUi.COPPER);
        graphics.drawString(minecraft.font, header, x + 8, y + 5, WrightsUi.TEXT, false);
        graphics.drawString(minecraft.font, name, x + 8, y + 23, WrightsUi.MUTED, false);
        graphics.drawString(minecraft.font, detail, x + 8, y + 34, WrightsUi.MUTED, false);
        graphics.drawString(minecraft.font, time, x + 8, y + 45, WrightsUi.MUTED, false);
    }

    static int sampleToastHeight() {
        return 18 + 5 + 11 * 3 + 4;
    }

    static int sampleToastWidth(Minecraft minecraft) {
        Component name = Component.translatable("hud.wrights.tool_mirror").append(" - ").append(Component.translatable("hud.wrights.task_mine"));
        Component detail = Component.translatable("hud.wrights.done_mined", 4, 0);
        return Math.min(280, Math.max(
                minecraft.font.width(Component.translatable("hud.wrights.job_finished")) + 30,
                Math.max(minecraft.font.width(name), Math.max(minecraft.font.width(detail),
                        minecraft.font.width(Component.translatable("hud.wrights.job_time", "12s")))) + 18));
    }

    static int sampleModeMenuWidth(Minecraft minecraft) {
        return Math.max(188, minecraft.font.width(Component.translatable("hud.wrights.build_modes")) + 28);
    }

    static int sampleModeMenuHeight(Minecraft minecraft) {
        return 28 + 18 * 6 + 28;
    }

    static void drawSampleModeMenu(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, int height) {
        Component title = Component.translatable("hud.wrights.build_modes");
        Component use = Component.translatable("hud.wrights.tool_build");
        Component fresh = Component.translatable("hud.wrights.tool_new");
        Component selected = Component.translatable("hud.wrights.mode_selected");
        Component how = Component.translatable("hud.wrights.mode_how");
        WrightsUi.panel(graphics, x, y, width, height);
        graphics.drawString(minecraft.font, title, x + 12, y + 8, WrightsUi.TEXT, false);
        drawToolRow(graphics, minecraft, x, y + 24, width, true, fresh, selected);
        drawToolRow(graphics, minecraft, x, y + 42, width, false, use, selected);
        drawFooter(graphics, minecraft, how, x + 12, y + height - 20, width - 24, WrightsUi.MUTED);
    }

    private static boolean hasDetail(WrightsClientState.JobNotice notice) {
        return notice.detail != null && !notice.detail.getString().isEmpty();
    }

    private static int noticeHeight(WrightsClientState.JobNotice notice) {
        int lines = hasDetail(notice) ? 3 : 2;
        return 18 + 5 + lines * 11 + 4;
    }

    private static Component noticeTime(WrightsClientState.JobNotice notice) {
        return notice.seconds < 1
                ? Component.translatable("hud.wrights.job_time_under")
                : Component.translatable("hud.wrights.job_time", duration(notice.seconds));
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
