package com.autobots.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.autobots.AutobotsMod;
import com.autobots.blueprint.BoxEdits;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID, value = Dist.CLIENT)
public final class AutobotsHud {
    private AutobotsHud() {
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui
                || !com.autobots.item.ControllerItem.isHeld(minecraft.player)) {
            return;
        }
        Component line = null;
        Component sub = null;
        Component task = taskLine();
        if (AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE) {
            boolean box = AutobotsClientState.captureStyle == AutobotsClientState.CaptureStyle.BOX;
            boolean move = AutobotsClientState.captureStyle == AutobotsClientState.CaptureStyle.MOVE;
            if (move && AutobotsClientState.moveGrabbed && AutobotsClientState.hasBox()) {
                line = volumeLine();
                sub = Component.translatable("hud.autobots.move_sub");
            } else if (move) {
                line = Component.translatable(AutobotsClientState.hasBox() ? "hud.autobots.move_aim" : "hud.autobots.mode_move_need");
                sub = Component.translatable("hud.autobots.mode_how");
            } else if (box && AutobotsClientState.boxDragging && AutobotsClientState.hasBox()) {
                line = volumeLine();
                sub = Component.translatable("hud.autobots.box_sub");
            } else if (box && AutobotsClientState.cornerA != null) {
                line = Component.translatable("hud.autobots.box_idle");
                sub = Component.translatable("hud.autobots.mode_how");
            } else if (AutobotsClientState.cornerA == null) {
                line = Component.translatable("hud.autobots.capture");
                sub = Component.translatable("hud.autobots.mode_how");
            } else if (AutobotsClientState.cornerB == null) {
                line = Component.translatable("hud.autobots.capture_second");
                sub = Component.translatable("hud.autobots.mode_how");
            } else {
                line = volumeLine();
                sub = Component.translatable("hud.autobots.mode_how");
            }
        } else if (AutobotsClientState.mode == AutobotsClientState.Mode.SAVING) {
            line = Component.translatable("hud.autobots.saving", AutobotsClientState.name);
        } else if (AutobotsClientState.mode == AutobotsClientState.Mode.BUILD) {
            if (AutobotsClientState.bindSupply) {
                line = supplyChoice(minecraft);
                sub = Component.translatable("hud.autobots.bind_explain");
            } else {
                line = Component.translatable("hud.autobots.build", AutobotsClientState.buildName);
                sub = Component.translatable(AutobotsClientState.supplyBound ? "hud.autobots.build_chest" : "hud.autobots.build_inventory");
            }
        } else if (AutobotsClientState.mode == AutobotsClientState.Mode.NONE) {
            line = Component.translatable("hud.autobots.task_ready_sub");
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int taskWidth = minecraft.font.width(task);
        int taskX = (graphics.guiWidth() - taskWidth) / 2;
        graphics.fill(taskX - 8, 4, taskX + taskWidth + 8, 18, 0xC0101822);
        graphics.fill(taskX - 8, 4, taskX - 6, 18, AutobotsUi.COPPER);
        graphics.drawString(minecraft.font, task, taskX, 7, AutobotsUi.COPPER, false);
        if (line == null && !showModes(minecraft)) {
            return;
        }
        if (line != null) {
            int width = minecraft.font.width(line);
            if (sub != null) {
                width = Math.max(width, minecraft.font.width(sub));
            }
            int x = (graphics.guiWidth() - width) / 2;
            int height = sub == null ? 18 : 30;
            int top = 22;
            graphics.fill(x - 8, top, x + width + 8, top + height, 0xC0101822);
            graphics.fill(x - 8, top, x - 6, top + height, AutobotsUi.COPPER);
            graphics.drawString(minecraft.font, line, x, top + 4, AutobotsUi.TEXT, false);
            if (sub != null) {
                graphics.drawString(minecraft.font, sub, x, top + 16, AutobotsUi.MUTED, false);
            }
        }
        if (showModes(minecraft)) {
            if (AutobotsClientState.mode == AutobotsClientState.Mode.BUILD) {
                drawBuildModes(graphics, minecraft);
            } else {
                drawModes(graphics, minecraft);
            }
        }
    }

    private static Component taskLine() {
        if (AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE) {
            return Component.translatable("hud.autobots.task_new", Component.translatable(styleKey(AutobotsClientState.captureStyle)));
        }
        if (AutobotsClientState.mode == AutobotsClientState.Mode.BUILD) {
            return Component.translatable("hud.autobots.task_build_tool", Component.translatable(AutobotsClientState.bindSupply ? "hud.autobots.tool_bind" : "hud.autobots.tool_build"));
        }
        if (AutobotsClientState.mode == AutobotsClientState.Mode.SAVING) {
            return Component.translatable("hud.autobots.task_saving");
        }
        return Component.translatable("hud.autobots.task_ready");
    }

    private static boolean showModes(Minecraft minecraft) {
        return AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE
                && (minecraft.player.isShiftKeyDown() || AutobotsClientState.styleHudTicks > 0)
                || AutobotsClientState.mode == AutobotsClientState.Mode.BUILD
                && (minecraft.player.isShiftKeyDown() || AutobotsClientState.styleHudTicks > 0);
    }

    private static void drawBuildModes(GuiGraphics graphics, Minecraft minecraft) {
        Component title = Component.translatable("hud.autobots.build_modes");
        Component how = Component.translatable(AutobotsClientState.bindSupply ? "hud.autobots.bind_explain" : "hud.autobots.build_explain");
        Component build = Component.translatable("hud.autobots.tool_build");
        Component bind = Component.translatable("hud.autobots.tool_bind");
        Component selected = Component.translatable("hud.autobots.mode_selected");
        int width = 188;
        width = Math.max(width, minecraft.font.width(title) + 28);
        width = Math.max(width, minecraft.font.width(how) + 28);
        int row = 18;
        int height = 28 + row * 2 + 18;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 8;
        AutobotsUi.panel(graphics, x, y, width, height);
        graphics.drawString(minecraft.font, title, x + 12, y + 8, AutobotsUi.TEXT, false);
        drawToolRow(graphics, minecraft, x, y + 24, width, false, build, selected);
        drawToolRow(graphics, minecraft, x, y + 24 + row, width, true, bind, selected);
        int foot = AutobotsClientState.styleAnnounce ? 0xFFF2C14E : AutobotsUi.MUTED;
        graphics.drawString(minecraft.font, how, x + 12, y + height - 14, foot, false);
    }

    private static void drawToolRow(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, boolean bind, Component label, Component selected) {
        boolean on = AutobotsClientState.bindSupply == bind;
        if (on) {
            graphics.fill(x + 8, y - 3, x + width - 8, y + 11, 0xA0C47A3A);
        }
        graphics.drawString(minecraft.font, label, x + 14, y, on ? 0xFFFFFFFF : AutobotsUi.MUTED, false);
        if (on) {
            int markWidth = minecraft.font.width(selected);
            graphics.drawString(minecraft.font, selected, x + width - markWidth - 14, y, 0xFFFFFFFF, false);
        }
    }

    private static void drawModes(GuiGraphics graphics, Minecraft minecraft) {
        Component title = Component.translatable("hud.autobots.mode_title");
        Component how = AutobotsClientState.styleAnnounce
                ? Component.translatable("hud.autobots.mode_now", Component.translatable(styleKey(AutobotsClientState.captureStyle)))
                : Component.translatable("hud.autobots.mode_how");
        Component corners = Component.translatable("hud.autobots.mode_corners");
        Component box = Component.translatable("hud.autobots.mode_box");
        Component move = Component.translatable("hud.autobots.mode_move");
        Component need = Component.translatable("hud.autobots.mode_move_need");
        Component selected = Component.translatable("hud.autobots.mode_selected");
        boolean canMove = AutobotsClientState.hasBox();
        int width = 188;
        width = Math.max(width, minecraft.font.width(title) + 28);
        width = Math.max(width, minecraft.font.width(how) + 28);
        width = Math.max(width, minecraft.font.width(corners) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(box) + minecraft.font.width(selected) + 36);
        width = Math.max(width, minecraft.font.width(move) + minecraft.font.width(canMove ? selected : need) + 36);
        int row = 18;
        int height = 28 + row * 3 + 18;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 8;
        AutobotsUi.panel(graphics, x, y, width, height);
        graphics.drawString(minecraft.font, title, x + 12, y + 8, AutobotsUi.TEXT, false);
        drawModeRow(graphics, minecraft, x, y + 24, width, AutobotsClientState.CaptureStyle.CORNERS, corners, selected, true, null);
        drawModeRow(graphics, minecraft, x, y + 24 + row, width, AutobotsClientState.CaptureStyle.BOX, box, selected, true, null);
        drawModeRow(graphics, minecraft, x, y + 24 + row * 2, width, AutobotsClientState.CaptureStyle.MOVE, move, selected, canMove, need);
        int foot = AutobotsClientState.styleAnnounce ? 0xFFF2C14E : AutobotsUi.MUTED;
        graphics.drawString(minecraft.font, how, x + 12, y + height - 14, foot, false);
    }

    private static Component supplyChoice(Minecraft minecraft) {
        BlockPos aim = SelectionRenderer.supplyTarget(minecraft);
        if (aim == null || minecraft.level == null) {
            return Component.translatable("hud.autobots.supply_yours");
        }
        return minecraft.level.getBlockState(aim).getBlock().getName();
    }

    private static Component volumeLine() {
        return Component.translatable(
                "screen.autobots.volume",
                BoxEdits.span(AutobotsClientState.cornerA, AutobotsClientState.cornerB, 0),
                BoxEdits.span(AutobotsClientState.cornerA, AutobotsClientState.cornerB, 1),
                BoxEdits.span(AutobotsClientState.cornerA, AutobotsClientState.cornerB, 2),
                BoxEdits.volume(AutobotsClientState.cornerA, AutobotsClientState.cornerB),
                AutobotsClientState.solidSelection());
    }

    private static void drawModeRow(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width, AutobotsClientState.CaptureStyle style, Component label, Component selected, boolean enabled, Component locked) {
        boolean on = enabled && AutobotsClientState.captureStyle == style;
        if (on) {
            graphics.fill(x + 8, y - 3, x + width - 8, y + 11, 0xA0C47A3A);
        }
        graphics.drawString(minecraft.font, label, x + 14, y, on ? 0xFFFFFFFF : (enabled ? AutobotsUi.MUTED : 0xFF5C6A74), false);
        Component mark = on ? selected : (!enabled ? locked : null);
        if (mark != null) {
            int markWidth = minecraft.font.width(mark);
            graphics.drawString(minecraft.font, mark, x + width - markWidth - 14, y, on ? 0xFFFFFFFF : 0xFF5C6A74, false);
        }
    }

    private static String styleKey(AutobotsClientState.CaptureStyle style) {
        if (style == AutobotsClientState.CaptureStyle.BOX) {
            return "hud.autobots.mode_box";
        }
        if (style == AutobotsClientState.CaptureStyle.MOVE) {
            return "hud.autobots.mode_move";
        }
        return "hud.autobots.mode_corners";
    }
}
