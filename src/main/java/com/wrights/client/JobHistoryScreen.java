package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

/**
 * Scrollable finished-job history opened from Jobs. Each row shows outcome, size, blocks,
 * time, source, and place, with the same undo / redo / mark / visit / remove actions.
 */
public class JobHistoryScreen extends Screen {
    private static final int ROW = 78;
    private static final int BTN_H = 16;

    private final Screen parent;
    private final List<BlueprintPayloads.JobLine> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int scroll;
    private int dragGrab = -1;
    private WrightsButton back;

    public JobHistoryScreen(Screen parent) {
        super(Component.translatable("screen.wrights.job_history"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        place();
        rebuild();
        back = addRenderableWidget(new WrightsButton(
                panelX + 12, panelY + panelH - 26, panelW - 24, 16,
                Component.translatable("screen.wrights.back"), button -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        rebuild();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        Component summary = rows.isEmpty()
                ? Component.translatable("screen.wrights.job_history_empty")
                : Component.translatable("screen.wrights.job_history_count", rows.size());
        graphics.drawString(this.font, summary, panelX + 16, panelY + 24, WrightsUi.MUTED, false);

        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
        boolean bar = maxScroll() > 0;
        int contentW = listW - (bar ? 10 : 0);
        graphics.enableScissor(listX, listY, listX + contentW, listY + listH);
        int y = listY - scroll;
        for (BlueprintPayloads.JobLine job : rows) {
            if (y + ROW >= listY && y < listY + listH) {
                drawRow(graphics, mouseX, mouseY, listX, y, contentW, job, listY, listY + listH);
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (bar) {
            drawBar(graphics, listX + listW - 6, listY, listH, mouseX, mouseY);
        }
        if (rows.isEmpty()) {
            String empty = Component.translatable("menu.wrights.jobs_finished_empty").getString();
            graphics.drawString(this.font, empty, listX + (contentW - this.font.width(empty)) / 2, listY + listH / 2 - 4, WrightsUi.MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (maxScroll() > 0 && mouseX >= listX + listW - 8 && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int max = maxScroll();
                int thumb = thumbH();
                int track = Math.max(1, listH - thumb);
                int thumbY = listY + track * scroll / Math.max(1, max);
                if (mouseY >= thumbY && mouseY < thumbY + thumb) {
                    dragGrab = (int) mouseY - thumbY;
                } else {
                    scroll = Mth.clamp(mouseY < thumbY ? scroll - listH : scroll + listH, 0, max);
                }
                return true;
            }
            for (Hit hit : hits) {
                if (mouseX >= hit.x && mouseX < hit.x + hit.w && mouseY >= hit.y && mouseY < hit.y + hit.h) {
                    return activate(hit);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragGrab >= 0 && maxScroll() > 0) {
            int thumb = thumbH();
            int track = Math.max(1, listH - thumb);
            int next = (int) Math.round((mouseY - listY - dragGrab) * maxScroll() / (double) track);
            scroll = Mth.clamp(next, 0, maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragGrab = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * ROW, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void place() {
        panelW = WrightsUi.fitW(420, this.width);
        panelH = WrightsUi.fitH(280, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        listX = panelX + 14;
        listY = panelY + 42;
        listW = panelW - 28;
        listH = panelH - 78;
        if (back != null) {
            back.setX(panelX + 12);
            back.setY(panelY + panelH - 26);
            back.setWidth(panelW - 24);
        }
    }

    private void rebuild() {
        rows.clear();
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.finished()) {
                rows.add(job);
            }
        }
    }

    private void drawRow(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            BlueprintPayloads.JobLine job,
            int clipTop,
            int clipBottom) {
        int rowH = ROW - 4;
        boolean hover = mouseX >= x && mouseX < x + width
                && mouseY >= Math.max(y, clipTop)
                && mouseY < Math.min(y + rowH, clipBottom);
        graphics.fill(x, y, x + width, y + rowH, hover ? WrightsUi.CARD_HOVER : WrightsUi.SHADE);
        if (hover) {
            graphics.fill(x, y, x + 2, y + rowH, statusColor(job));
        }

        Component status = statusText(job);
        int statusW = this.font.width(status);
        int statusX = x + width - 8 - statusW;
        int lineEnd = Math.max(x + 6, statusX - this.font.width("   "));
        String name = this.font.plainSubstrByWidth(job.name(), Math.max(24, lineEnd - (x + 6) - 8));
        WrightsUi.section(graphics, this.font, x + 6, y + 4, Component.literal(name), lineEnd);
        graphics.drawString(this.font, status, statusX, y + 4, statusColor(job), false);

        Component size = Component.translatable("menu.wrights.size", job.sizeX(), job.sizeY(), job.sizeZ());
        Component blocks = Component.translatable("menu.wrights.job_preview_blocks", WrightsUi.count(job.total()));
        graphics.drawString(this.font, size, x + 6, y + 16, WrightsUi.SIZE, false);
        int dashX = x + 6 + this.font.width(size);
        graphics.drawString(this.font, " - ", dashX, y + 16, WrightsUi.MUTED, false);
        graphics.drawString(this.font, blocks, dashX + this.font.width(" - "), y + 16, WrightsUi.MUTED, false);

        String result = resultLine(job);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(result, width - 12), x + 6, y + 27, WrightsUi.TEXT, false);

        String meta = metaLine(job);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(meta, width - 12), x + 6, y + 38, WrightsUi.MUTED, false);

        BlockPos at = job.anchor();
        String place = Component.translatable("menu.wrights.job_anchor", at.getX(), at.getY(), at.getZ()).getString();
        if (job.dimension() != null && !job.dimension().isBlank()) {
            String dim = job.dimension();
            int slash = dim.lastIndexOf(':');
            if (slash >= 0 && slash + 1 < dim.length()) {
                dim = dim.substring(slash + 1);
            }
            place = place + " · " + dim;
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(place, width - 12), x + 6, y + 49, WrightsUi.MUTED, false);

        int btnY = y + rowH - BTN_H - 4;
        if (btnY + BTN_H > clipTop && btnY < clipBottom) {
            drawActions(graphics, mouseX, mouseY, x, btnY, width, job);
        }
    }

    private void drawActions(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, BlueprintPayloads.JobLine job) {
        boolean tool = toolHeld();
        boolean creative = this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild
                && !this.minecraft.player.isSpectator();
        int right = x + width - 6;
        right = drawAction(graphics, mouseX, mouseY, right, y, "menu.wrights.remove", job.id(), BlueprintPayloads.JobControl.REMOVE, tool);
        if (creative) {
            right = drawAction(graphics, mouseX, mouseY, right, y, "menu.wrights.visit", job.id(), BlueprintPayloads.JobControl.VISIT, tool);
        }
        boolean marked = WrightsClientState.markedJobs.contains(job.id());
        right = drawAction(
                graphics,
                mouseX,
                mouseY,
                right,
                y,
                marked ? "menu.wrights.mark_on" : "menu.wrights.mark",
                job.id(),
                Hit.MARK,
                true,
                marked ? 0xFFF2D48A : 0);
        if (job.canUndo() && !job.undone() && !job.undoing() && com.wrights.WrightsConfig.allowUndo()) {
            right = drawAction(graphics, mouseX, mouseY, right, y, "menu.wrights.undo", job.id(), BlueprintPayloads.JobControl.UNDO, tool);
        }
        drawAction(graphics, mouseX, mouseY, right, y, "menu.wrights.redo", job.id(), BlueprintPayloads.JobControl.REDO, tool);
    }

    private int drawAction(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int right,
            int y,
            String key,
            UUID jobId,
            int action,
            boolean enabled) {
        return drawAction(graphics, mouseX, mouseY, right, y, key, jobId, action, enabled, 0);
    }

    private int drawAction(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int right,
            int y,
            String key,
            UUID jobId,
            int action,
            boolean enabled,
            int border) {
        int width = Math.max(52, this.font.width(Component.translatable(key)) + 10);
        int x = right - width;
        boolean hover = enabled && mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + BTN_H;
        WrightsUi.choice(graphics, x, y, width, BTN_H, false, hover, enabled);
        if (border != 0) {
            WrightsUi.border(graphics, x, y, width, BTN_H, border);
        }
        Component label = Component.translatable(key);
        graphics.drawString(
                this.font,
                label,
                x + (width - this.font.width(label)) / 2,
                y + 4,
                enabled ? WrightsUi.TEXT : 0xFF6E7A86,
                false);
        hits.add(new Hit(x, y, width, BTN_H, jobId, action));
        return x - 4;
    }

    private boolean activate(Hit hit) {
        if (hit.action == Hit.MARK) {
            WrightsClientState.toggleMark(hit.jobId);
            return true;
        }
        if (hit.action != Hit.MARK && !toolHeld()) {
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.wrights.hold_controller"), true);
            }
            return true;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(hit.jobId, hit.action));
        return true;
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int h, int mouseX, int mouseY) {
        int max = maxScroll();
        int thumb = thumbH();
        int track = Math.max(1, h - thumb);
        int thumbY = y + track * scroll / Math.max(1, max);
        graphics.fill(x, y, x + 4, y + h, 0xFF0A1016);
        boolean hover = mouseX >= x - 2 && mouseX < x + 6 && mouseY >= thumbY && mouseY < thumbY + thumb;
        graphics.fill(x, thumbY, x + 4, thumbY + thumb, hover || dragGrab >= 0 ? WrightsUi.COPPER : 0xFF3D4E5E);
    }

    private int thumbH() {
        int content = Math.max(listH, rows.size() * ROW);
        return Math.max(16, listH * listH / Math.max(1, content));
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW - listH);
    }

    private static Component statusText(BlueprintPayloads.JobLine job) {
        return Component.translatable(WrightsClientState.jobStatusKey(job));
    }

    private static int statusColor(BlueprintPayloads.JobLine job) {
        if (job.stopped()) {
            return WrightsUi.RED;
        }
        if (job.undone() || job.undoing()) {
            return WrightsUi.MUTED;
        }
        return WrightsUi.GREEN;
    }

    private String resultLine(BlueprintPayloads.JobLine job) {
        int placed = Math.max(0, job.done());
        int skipped = Math.max(0, job.skipped());
        StringBuilder line = new StringBuilder();
        line.append(Component.translatable("screen.wrights.job_history_placed", WrightsUi.count(placed)).getString());
        if (skipped > 0) {
            line.append(" · ");
            line.append(Component.translatable("screen.wrights.job_history_skipped", WrightsUi.count(skipped)).getString());
        }
        int seconds = Math.max(0, job.workedTicks() / 20);
        line.append(" · ");
        if (seconds <= 0) {
            line.append(Component.translatable("hud.wrights.job_time_under").getString());
        } else {
            line.append(Component.translatable("hud.wrights.job_time", duration(seconds)).getString());
        }
        return line.toString();
    }

    private String metaLine(BlueprintPayloads.JobLine job) {
        StringBuilder line = new StringBuilder();
        int wrights = Math.max(job.drones(), Math.max(job.quota(), job.crew().size()));
        if (wrights > 0) {
            line.append(Component.translatable("screen.wrights.job_history_wrights", wrights).getString());
        }
        Component source = sourceText(job);
        if (!source.getString().isEmpty()) {
            if (!line.isEmpty()) {
                line.append(" · ");
            }
            line.append(source.getString());
        }
        return line.toString();
    }

    private static Component sourceText(BlueprintPayloads.JobLine job) {
        String named = job.sourceName();
        BlockPos at = job.sourcePort();
        boolean hasPort = at != null && !at.equals(BlockPos.ZERO);
        return switch (job.source()) {
            case 1 -> Component.translatable("menu.wrights.job_from_you");
            case 2 -> named != null && !named.isBlank()
                    ? (hasPort
                            ? Component.translatable("menu.wrights.job_from_port_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port_named", named))
                    : (hasPort
                            ? Component.translatable("menu.wrights.job_from_port_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port"));
            case 3 -> named != null && !named.isBlank()
                    ? (hasPort
                            ? Component.translatable("menu.wrights.job_from_both_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both_named", named))
                    : (hasPort
                            ? Component.translatable("menu.wrights.job_from_both_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both"));
            default -> Component.translatable("menu.wrights.job_from_you");
        };
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

    private boolean toolHeld() {
        return this.minecraft.player != null && com.wrights.item.ControllerItem.isHeld(this.minecraft.player);
    }

    private record Hit(int x, int y, int w, int h, UUID jobId, int action) {
        private static final int MARK = -2;
    }
}
