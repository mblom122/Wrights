package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.wrights.job.JobEventLog;
import com.wrights.network.BlueprintPayloads;

/**
 * Info-only History log: completed jobs, returns, stalls, pauses, and similar events.
 * No action buttons — Manage keeps those on live / finished job cards.
 */
public class JobHistoryScreen extends Screen {
    private static final int ROW = 60;

    private final Screen parent;
    private final List<BlueprintPayloads.HistoryLine> rows = new ArrayList<>();
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
        for (BlueprintPayloads.HistoryLine event : rows) {
            if (y + ROW >= listY && y < listY + listH) {
                drawRow(graphics, mouseX, mouseY, listX, y, contentW, event, listY, listY + listH);
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (bar) {
            drawBar(graphics, listX + listW - 6, listY, listH, mouseX, mouseY);
        }
        if (rows.isEmpty()) {
            String empty = Component.translatable("screen.wrights.job_history_empty").getString();
            graphics.drawString(this.font, empty, listX + (contentW - this.font.width(empty)) / 2, listY + listH / 2 - 4, WrightsUi.MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= listX + listW - 8 && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
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
        rows.addAll(WrightsClientState.history);
    }

    private void drawRow(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            BlueprintPayloads.HistoryLine event,
            int clipTop,
            int clipBottom) {
        int rowH = ROW - 4;
        boolean hover = mouseX >= x && mouseX < x + width
                && mouseY >= Math.max(y, clipTop)
                && mouseY < Math.min(y + rowH, clipBottom);
        graphics.fill(x, y, x + width, y + rowH, hover ? WrightsUi.CARD_HOVER : WrightsUi.SHADE);
        graphics.fill(x, y, x + 2, y + rowH, kindColor(event.kind()));

        Component title = kindTitle(event.kind());
        String name = event.jobName() == null ? "" : event.jobName();
        int titleW = this.font.width(title);
        graphics.drawString(this.font, title, x + 8, y + 5, kindColor(event.kind()), false);
        if (!name.isBlank()) {
            int nameX = x + 8 + titleW + 8;
            String clipped = this.font.plainSubstrByWidth(name, Math.max(24, width - (nameX - x) - 8));
            graphics.drawString(this.font, clipped, nameX, y + 5, WrightsUi.TEXT, false);
        }

        int lineY = y + 16;
        String stats = statsLine(event);
        if (!stats.isBlank()) {
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(stats, width - 16),
                    x + 8,
                    lineY,
                    WrightsUi.SIZE,
                    false);
            lineY += 11;
        }
        String location = event.location() == null ? "" : event.location();
        if (!location.isBlank()) {
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(location, width - 16),
                    x + 8,
                    lineY,
                    WrightsUi.MUTED,
                    false);
            lineY += 11;
        }
        String detail = event.detail() == null || event.detail().isBlank()
                ? Component.translatable("history.wrights.detail_none").getString()
                : event.detail();
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(detail, width - 16),
                x + 8,
                lineY,
                WrightsUi.MUTED,
                false);
    }

    /** Same size / blocks / duration strip the Jobs cards use. */
    private static String statsLine(BlueprintPayloads.HistoryLine event) {
        boolean hasSize = event.sizeX() > 0 && event.sizeY() > 0 && event.sizeZ() > 0;
        boolean hasBlocks = event.blocks() > 0;
        boolean hasTime = event.elapsedSeconds() > 0;
        if (!hasSize && !hasBlocks && !hasTime) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (hasSize) {
            out.append(Component.translatable(
                    "menu.wrights.size", event.sizeX(), event.sizeY(), event.sizeZ()).getString());
        }
        if (hasBlocks) {
            if (!out.isEmpty()) {
                out.append(" - ");
            }
            out.append(Component.translatable(
                    "menu.wrights.job_preview_blocks", WrightsUi.count(event.blocks())).getString());
        }
        if (hasTime) {
            if (!out.isEmpty()) {
                out.append(" · ");
            }
            out.append(Component.translatable(
                    "history.wrights.took", WrightsClientState.formatSeconds(event.elapsedSeconds())).getString());
        }
        return out.toString();
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

    private static Component kindTitle(int kind) {
        return Component.translatable(switch (kind) {
            case JobEventLog.COMPLETED -> "history.wrights.completed";
            case JobEventLog.STOPPED -> "history.wrights.stopped";
            case JobEventLog.UNDONE -> "history.wrights.undone";
            case JobEventLog.PAUSED -> "history.wrights.paused";
            case JobEventLog.RESUMED -> "history.wrights.resumed_title";
            case JobEventLog.STALLED_ITEMS -> "history.wrights.stalled";
            case JobEventLog.OUT_OF_RANGE -> "history.wrights.out_of_range";
            case JobEventLog.WRONG_DIMENSION -> "history.wrights.wrong_dimension";
            case JobEventLog.REJOINED -> "history.wrights.rejoined";
            case JobEventLog.LAUNCHED -> "history.wrights.launched";
            case JobEventLog.QUEUED -> "history.wrights.queued";
            case JobEventLog.REMOVED -> "history.wrights.removed_title";
            case JobEventLog.COPY_DONE -> "history.wrights.copy_done";
            case JobEventLog.UNDOING -> "history.wrights.undoing_title";
            case JobEventLog.SHARE_SPLIT -> "history.wrights.share_split_title";
            default -> "history.wrights.event";
        });
    }

    private static int kindColor(int kind) {
        return switch (kind) {
            case JobEventLog.COMPLETED, JobEventLog.COPY_DONE, JobEventLog.REJOINED, JobEventLog.RESUMED, JobEventLog.LAUNCHED, JobEventLog.SHARE_SPLIT -> WrightsUi.GREEN;
            case JobEventLog.STOPPED, JobEventLog.OUT_OF_RANGE, JobEventLog.WRONG_DIMENSION, JobEventLog.REMOVED -> WrightsUi.RED;
            case JobEventLog.STALLED_ITEMS, JobEventLog.PAUSED, JobEventLog.QUEUED, JobEventLog.UNDOING -> WrightsUi.COPPER;
            default -> WrightsUi.MUTED;
        };
    }
}
