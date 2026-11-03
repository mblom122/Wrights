package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import com.wrights.WrightsConfig;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightportPlaces;
import com.wrights.item.WrightportBlockItem;
import com.wrights.network.BlueprintPayloads;

/** The Jobs page: working and finished job lists with crew and pause/remove controls. */
final class JobsPanel {
    private final WrightsMenuScreen s;

    JobsPanel(WrightsMenuScreen s) {
        this.s = s;
    }

    private UUID leadJob;

    private int workHeaderY;

    private int doneHeaderY;

    private int jobsOuterGutter;

    private int jobsPanelW;

    private int jobX;

    private int jobW;

    private int workPauseRight;

    private int workRemoveRight;

    private int doneRemoveRight;

    private int workPauseWidth;

    private int workRemoveWidth;

    private int doneRemoveWidth;

    private int manageAllWidth;

    private int manageAllRight;

    private int manageAllY;

    private int historyWidth;

    private int historyRight;

    private boolean clearJobs;

    private int clearJobWidth;

    private int clearJobRight;

    private double jobBarGrab;

    private boolean workAllPaused;

    /** Exact job-control hitboxes from the last Jobs render — clicks use these, not a second layout pass. */
    private final List<JobHit> jobHits = new ArrayList<>();

    private static final int JOB_HEADER = 20;

    private static final int JOB_GROUP_MIN = 46;

    /** Name (section style), size/blocks, source, anchor, and progress. */
    private static final int JOB_BODY = 58;

    private static final int JOB_CREW = 18;

    private static final int JOB_BUTTON = 18;

    private static final int ACT_MANAGE = -1;

    private static final int ACT_MARK = -2;

    private static final int ACT_MANAGE_ALL = -3;

    private static final int ACT_CLEAR_MARKS = -4;

    private static final int ACT_CREW_MINUS = -5;

    private static final int ACT_CREW_PLUS = -6;

    private static final int ACT_HISTORY = -7;

    private static final int CREW_STEP = 14;

    /** Working rows keep a crew line; finished rows skip it so action buttons fit. */
    private int workStride = JOB_BODY + JOB_CREW + JOB_BUTTON + 6;

    private int doneStride = JOB_BODY + JOB_BUTTON + 6;

    int jobsInsetY() {
        return s.panelY + 38;
    }

    private int jobsInsetH() {
        return s.panelH - 78;
    }

    void prepareJobs() {
        s.workingJobs.clear();
        s.finishedJobs.clear();
        workAllPaused = true;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.finished()) {
                s.finishedJobs.add(job);
            } else {
                s.workingJobs.add(0, job);
                if (!job.paused()) {
                    workAllPaused = false;
                }
            }
        }
        int insetX = s.listX - 2;
        int insetW = s.panelX + s.panelW - 12 - insetX;
        jobX = insetX + 6;
        jobW = insetW - 12;
        manageAllWidth = s.jobButtonWidth("menu.wrights.manage_all");
        historyWidth = s.jobButtonWidth("menu.wrights.history");
        manageAllRight = jobX + jobW;
        manageAllY = jobsInsetY() + 6;
        historyRight = manageAllRight - manageAllWidth - 4;
        s.jobsViewTop = jobsInsetY() + 28;
        s.jobsViewH = Math.max(JOB_GROUP_MIN, jobsInsetY() + jobsInsetH() - 6 - s.jobsViewTop);
        int gap = 8;
        boolean workOpen = !s.workingJobs.isEmpty();
        boolean doneOpen = !s.finishedJobs.isEmpty();
        // Probe with full width, then reserve an outer scrollbar gutter when both groups need room.
        int probeW = Math.max(80, jobW - 8);
        workStride = JOB_BODY + JOB_CREW + s.buttonLines(s.workingKeys(), probeW) * JOB_BUTTON + 6;
        doneStride = JOB_BODY + s.buttonLines(s.finishedKeys(), probeW) * JOB_BUTTON + 6;
        int workCap = Math.max(1, Math.min(3, Math.max(1, (s.jobsViewH - gap - JOB_GROUP_MIN) / Math.max(1, workStride))));
        int doneCap = workOpen ? workCap : Math.max(1, Math.min(4, Math.max(1, (s.jobsViewH - gap - JOB_GROUP_MIN) / Math.max(1, doneStride))));
        s.workRows = workOpen ? Math.min(s.workingJobs.size(), workCap) : 0;
        s.doneRows = doneOpen ? Math.min(s.finishedJobs.size(), doneCap) : 0;
        int workBlock = jobGroupHeight(s.workRows, workStride);
        int doneBlock = jobGroupHeight(s.doneRows, doneStride);
        s.jobsContentH = workBlock + gap + doneBlock;
        jobsOuterGutter = s.jobsContentH > s.jobsViewH ? 8 : 0;
        jobsPanelW = jobW - jobsOuterGutter;
        if (jobsOuterGutter > 0) {
            int tightW = Math.max(80, jobsPanelW - 8);
            workStride = JOB_BODY + JOB_CREW + s.buttonLines(s.workingKeys(), tightW) * JOB_BUTTON + 6;
            doneStride = JOB_BODY + s.buttonLines(s.finishedKeys(), tightW) * JOB_BUTTON + 6;
            workBlock = jobGroupHeight(s.workRows, workStride);
            doneBlock = jobGroupHeight(s.doneRows, doneStride);
            s.jobsContentH = workBlock + gap + doneBlock;
        }
        s.jobsBodyScroll = Mth.clamp(s.jobsBodyScroll, 0, Math.max(0, s.jobsContentH - s.jobsViewH));
        s.workGroupY = s.jobsViewTop - s.jobsBodyScroll;
        s.workGroupH = workBlock;
        workHeaderY = s.workGroupY + 2;
        s.workListY = s.workGroupY + JOB_HEADER + 4;
        s.doneGroupY = s.workGroupY + workBlock + gap;
        s.doneGroupH = doneBlock;
        doneHeaderY = s.doneGroupY + 2;
        s.doneListY = s.doneGroupY + JOB_HEADER + 4;
        if (!s.workingJobs.isEmpty()) {
            UUID lead = s.workingJobs.get(0).id();
            if (leadJob != null && !leadJob.equals(lead)) {
                s.jobScroll = 0;
            }
            leadJob = lead;
        } else {
            leadJob = null;
        }
        s.jobScroll = Mth.clamp(s.jobScroll, 0, Math.max(0, s.workingJobs.size() - s.workRows));
        s.historyScroll = Mth.clamp(s.historyScroll, 0, Math.max(0, s.finishedJobs.size() - s.doneRows));
        int right = jobX + jobsPanelW - 6;
        workRemoveWidth = s.jobButtonWidth("menu.wrights.remove_all");
        workPauseWidth = s.jobButtonWidth(workAllPaused ? "menu.wrights.resume_all" : "menu.wrights.pause_all");
        workRemoveRight = right;
        workPauseRight = right - workRemoveWidth - 4;
        doneRemoveWidth = workRemoveWidth;
        doneRemoveRight = right;
        clearJobs = !WrightsClientState.markedJobs.isEmpty() || !WrightsClientState.markedWrights.isEmpty();
        clearJobWidth = s.jobButtonWidth("menu.wrights.clear_highlights");
        clearJobRight = historyRight - historyWidth - 4;
    }

    private int jobGroupHeight(int rows, int stride) {
        if (rows <= 0) {
            return JOB_GROUP_MIN;
        }
        return JOB_HEADER + 8 + rows * stride;
    }

    private int jobStride(BlueprintPayloads.JobLine job) {
        return job != null && job.finished() ? doneStride : workStride;
    }

    void renderJobs(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredJob = null;
        jobHits.clear();
        prepareJobs();
        int[] pool = dronePool();
        int reserved = manageAllWidth + historyWidth + 12 + (clearJobs ? clearJobWidth + 4 : 0);
        String drones = s.font().plainSubstrByWidth(Component.translatable("menu.wrights.wrights_available", pool[0], pool[1]).getString(), Math.max(40, jobW - reserved));
        graphics.drawString(s.font(), drones, jobX, manageAllY + 4, WrightsUi.TEXT, false);
        boolean tool = s.toolHeld();
        trackJobButton(manageAllRight, manageAllY, manageAllWidth, null, ACT_MANAGE_ALL);
        s.drawJobButton(graphics, manageAllRight, manageAllY, "menu.wrights.manage_all", mouseX, mouseY, manageAllWidth, 0, tool);
        trackJobButton(historyRight, manageAllY, historyWidth, null, ACT_HISTORY);
        s.drawJobButton(graphics, historyRight, manageAllY, "menu.wrights.history", mouseX, mouseY, historyWidth, 0, true);
        if (clearJobs) {
            trackJobButton(clearJobRight, manageAllY, clearJobWidth, null, ACT_CLEAR_MARKS);
            s.drawJobButton(graphics, clearJobRight, manageAllY, "menu.wrights.clear_highlights", mouseX, mouseY, clearJobWidth, 0xFFF2D48A, true);
        }
        int rowX = jobX + 4;
        int workGutter = s.workingJobs.size() > s.workRows ? 16 : 0;
        int doneGutter = s.finishedJobs.size() > s.doneRows ? 16 : 0;
        int activeJobs = 0;
        for (BlueprintPayloads.JobLine job : s.workingJobs) {
            if (!job.paused()) {
                activeJobs++;
            }
        }
        graphics.enableScissor(jobX, s.jobsViewTop, jobX + jobsPanelW, s.jobsViewTop + s.jobsViewH);
        Component workTitle = s.workingJobs.isEmpty()
                ? Component.translatable("menu.wrights.jobs_working")
                : Component.translatable("menu.wrights.jobs_working_ratio", activeJobs, s.workingJobs.size());
        drawJobGroup(graphics, jobX, s.workGroupY, jobsPanelW, s.workGroupH, workTitle, s.workingJobs.isEmpty() ? Component.translatable("menu.wrights.jobs_working_empty") : null, s.workingJobs.isEmpty() ? 0 : workPauseWidth + workRemoveWidth + 18);
        if (!s.workingJobs.isEmpty()) {
            trackJobButton(workRemoveRight, workHeaderY, workRemoveWidth, null, BlueprintPayloads.JobControl.REMOVE_ACTIVE);
            s.drawJobButton(graphics, workRemoveRight, workHeaderY, "menu.wrights.remove_all", mouseX, mouseY, workRemoveWidth, 0, tool);
            trackJobButton(workPauseRight, workHeaderY, workPauseWidth, null,
                    workAllPaused ? BlueprintPayloads.JobControl.RESUME_ALL : BlueprintPayloads.JobControl.PAUSE_ALL);
            s.drawJobButton(graphics, workPauseRight, workHeaderY, workAllPaused ? "menu.wrights.resume_all" : "menu.wrights.pause_all", mouseX, mouseY, workPauseWidth, 0, tool);
            int workClipTop = Math.max(s.workListY, s.jobsViewTop);
            int workClipBottom = Math.min(s.workGroupY + s.workGroupH - 2, s.jobsViewTop + s.jobsViewH);
            if (workClipBottom > workClipTop) {
                graphics.enableScissor(jobX + 2, workClipTop, jobX + jobsPanelW - 2 - workGutter, workClipBottom);
                for (int row = 0; row < s.workRows && s.jobScroll + row < s.workingJobs.size(); row++) {
                    drawJobRow(graphics, mouseX, mouseY, rowX, jobsPanelW - 8 - workGutter, s.workListY + row * workStride, s.workingJobs.get(s.jobScroll + row));
                }
                graphics.disableScissor();
                int workBarH = Math.max(12, Math.min(s.workRows * workStride, workClipBottom - Math.max(s.workListY, s.jobsViewTop)));
                if (s.workListY < s.jobsViewTop + s.jobsViewH && workClipBottom > s.jobsViewTop) {
                    drawJobBar(graphics, s.workListY, Math.max(workBarH, s.workRows * workStride), s.jobScroll, s.workingJobs.size(), Math.max(1, s.workRows), mouseX, mouseY);
                }
            }
        }
        Component doneTitle = finishedTitle();
        drawJobGroup(graphics, jobX, s.doneGroupY, jobsPanelW, s.doneGroupH, doneTitle, s.finishedJobs.isEmpty() ? Component.translatable("menu.wrights.jobs_finished_empty") : null, s.finishedJobs.isEmpty() ? 0 : doneRemoveWidth + 14);
        if (!s.finishedJobs.isEmpty()) {
            trackJobButton(doneRemoveRight, doneHeaderY, doneRemoveWidth, null, BlueprintPayloads.JobControl.REMOVE_DONE);
            s.drawJobButton(graphics, doneRemoveRight, doneHeaderY, "menu.wrights.remove_all", mouseX, mouseY, doneRemoveWidth, 0, tool);
            int doneClipTop = Math.max(s.doneListY, s.jobsViewTop);
            int doneClipBottom = Math.min(s.doneGroupY + s.doneGroupH - 2, s.jobsViewTop + s.jobsViewH);
            if (doneClipBottom > doneClipTop) {
                graphics.enableScissor(jobX + 2, doneClipTop, jobX + jobsPanelW - 2 - doneGutter, doneClipBottom);
                for (int row = 0; row < s.doneRows && s.historyScroll + row < s.finishedJobs.size(); row++) {
                    drawJobRow(graphics, mouseX, mouseY, rowX, jobsPanelW - 8 - doneGutter, s.doneListY + row * doneStride, s.finishedJobs.get(s.historyScroll + row));
                }
                graphics.disableScissor();
                int doneBarH = Math.max(12, Math.min(s.doneRows * doneStride, doneClipBottom - Math.max(s.doneListY, s.jobsViewTop)));
                if (s.doneListY < s.jobsViewTop + s.jobsViewH && doneClipBottom > s.jobsViewTop) {
                    drawJobBar(graphics, s.doneListY, Math.max(doneBarH, s.doneRows * doneStride), s.historyScroll, s.finishedJobs.size(), Math.max(1, s.doneRows), mouseX, mouseY);
                }
            }
        }
        graphics.disableScissor();
        WrightsUi.scrollBar(graphics, jobX + jobW - 4, s.jobsViewTop, s.jobsViewH, s.jobsBodyScroll, s.jobsContentH);
        if (hoveredJob != null) {
            drawJobTooltip(graphics, mouseX, mouseY, hoveredJob);
        }
    }

    private void drawJobGroup(GuiGraphics graphics, int x, int y, int width, int height, Component title, Component empty, int reserved) {
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF3A4C5C);
        graphics.fill(x + 1, y + 1, x + width - 1, y + JOB_HEADER, 0xFF1C2A36);
        graphics.fill(x + 1, y + 1, x + 3, y + JOB_HEADER, WrightsUi.COPPER);
        graphics.fill(x + 1, y + JOB_HEADER - 1, x + width - 1, y + JOB_HEADER, 0xFF31404E);
        String heading = s.font().plainSubstrByWidth(title.getString(), Math.max(40, width - reserved - 12));
        graphics.drawString(s.font(), heading, x + 10, y + 6, WrightsUi.TEXT, false);
        if (empty != null) {
            graphics.drawString(s.font(), empty, x + 10, y + JOB_HEADER + 6, WrightsUi.MUTED, false);
        }
    }

    /** Up arrow, scrollbar, and down arrow for one job list. Hidden when every row already fits. */
    private void drawJobBar(GuiGraphics graphics, int top, int height, int scroll, int total, int visible, int mouseX, int mouseY) {
        JobBar bar = jobBar(top, height, scroll, total, visible);
        if (bar == null) {
            return;
        }
        int x = bar.x();
        boolean upOn = scroll > 0;
        boolean upHover = mouseX >= x && mouseX < x + 12 && mouseY >= top && mouseY < top + 12;
        graphics.fill(x, top, x + 12, top + 12, upHover && upOn ? 0xFF3A5164 : 0xFF18222C);
        WrightsUi.border(graphics, x, top, 12, 12, upOn ? WrightsUi.COPPER : 0xFF3A4C5C);
        String up = "^";
        graphics.drawString(s.font(), up, x + (12 - s.font().width(up)) / 2, top + 2, upOn ? WrightsUi.COPPER : WrightsUi.MUTED, false);
        int downTop = top + height - 12;
        boolean downOn = scroll < bar.max();
        boolean downHover = mouseX >= x && mouseX < x + 12 && mouseY >= downTop && mouseY < downTop + 12;
        graphics.fill(x, downTop, x + 12, downTop + 12, downHover && downOn ? 0xFF3A5164 : 0xFF18222C);
        WrightsUi.border(graphics, x, downTop, 12, 12, downOn ? WrightsUi.COPPER : 0xFF3A4C5C);
        String down = "v";
        graphics.drawString(s.font(), down, x + (12 - s.font().width(down)) / 2, downTop + 2, downOn ? WrightsUi.COPPER : WrightsUi.MUTED, false);
        graphics.fill(x + 4, bar.trackY(), x + 8, bar.trackY() + bar.trackH(), 0xFF0C141C);
        boolean thumbHover = mouseX >= x && mouseX < x + 12 && mouseY >= bar.thumbY() && mouseY < bar.thumbY() + bar.thumbH();
        graphics.fill(x + 1, bar.thumbY(), x + 11, bar.thumbY() + bar.thumbH(), thumbHover ? 0xFFFFC56A : WrightsUi.COPPER);
    }

    private boolean clickJobBar(double mouseX, double mouseY, boolean working) {
        int total = working ? s.workingJobs.size() : s.finishedJobs.size();
        int visible = working ? s.workRows : s.doneRows;
        int scroll = working ? s.jobScroll : s.historyScroll;
        int top = working ? s.workListY : s.doneListY;
        int stride = working ? workStride : doneStride;
        JobBar bar = jobBar(top, visible * stride, scroll, total, visible);
        if (bar == null || mouseX < bar.x() || mouseX >= bar.x() + 12 || mouseY < top || mouseY >= top + visible * stride) {
            return false;
        }
        if (mouseY < top + 12) {
            setJobScroll(working, scroll - 1);
            return true;
        }
        int downTop = top + visible * stride - 12;
        if (mouseY >= downTop) {
            setJobScroll(working, scroll + 1);
            return true;
        }
        if (mouseY >= bar.thumbY() && mouseY < bar.thumbY() + bar.thumbH()) {
            s.jobBarDrag = working ? 1 : 2;
            jobBarGrab = mouseY - bar.thumbY();
            return true;
        }
        setJobScroll(working, mouseY < bar.thumbY() ? scroll - visible : scroll + visible);
        return true;
    }

    void dragJobBar(double mouseY) {
        prepareJobs();
        boolean working = s.jobBarDrag == 1;
        int total = working ? s.workingJobs.size() : s.finishedJobs.size();
        int visible = working ? s.workRows : s.doneRows;
        int scroll = working ? s.jobScroll : s.historyScroll;
        int top = working ? s.workListY : s.doneListY;
        int stride = working ? workStride : doneStride;
        JobBar bar = jobBar(top, visible * stride, scroll, total, visible);
        if (bar == null) {
            return;
        }
        int travel = Math.max(1, bar.trackH() - bar.thumbH());
        int next = (int) Math.round((mouseY - jobBarGrab - bar.trackY()) * bar.max() / travel);
        setJobScroll(working, next);
    }

    private void setJobScroll(boolean working, int next) {
        if (working) {
            s.jobScroll = Mth.clamp(next, 0, Math.max(0, s.workingJobs.size() - s.workRows));
        } else {
            s.historyScroll = Mth.clamp(next, 0, Math.max(0, s.finishedJobs.size() - s.doneRows));
        }
    }

    private JobBar jobBar(int top, int height, int scroll, int total, int visible) {
        int max = Math.max(0, total - visible);
        if (max <= 0 || height < 36 || visible <= 0) {
            return null;
        }
        int trackY = top + 14;
        int trackH = Math.max(8, height - 12 - 14 - 2);
        int thumbH = Math.max(10, Math.min(trackH, trackH * visible / total));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = trackY + travel * Mth.clamp(scroll, 0, max) / max;
        return new JobBar(jobX + jobsPanelW - 15, trackY, trackH, thumbY, thumbH, max);
    }

    private record JobBar(int x, int trackY, int trackH, int thumbY, int thumbH, int max) {
    }

    private void drawJobRow(GuiGraphics graphics, int mouseX, int mouseY, int x, int width, int y, BlueprintPayloads.JobLine job) {
        int stride = jobStride(job);
        int rowH = stride - 4;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + rowH;
        hoveredJob = hover ? job : hoveredJob;
        graphics.fill(x, y, x + width, y + rowH, hover ? WrightsUi.CARD_HOVER : WrightsUi.SHADE);
        if (hover) {
            graphics.fill(x, y, x + 2, y + rowH, jobColor(job));
        }
        Component statusText = jobStatusText(job);
        int statusW = s.font().width(statusText);
        int statusX = x + width - 8 - statusW;
        // Section hairline runs to ~3 spaces before the status (Completed / Working / …).
        int lineEnd = Math.max(x + 6, statusX - s.font().width("   "));
        String name = s.font().plainSubstrByWidth(job.name(), Math.max(24, lineEnd - (x + 6) - 8));
        WrightsUi.section(graphics, s.font(), x + 6, y + 4, Component.literal(name), lineEnd);
        graphics.drawString(s.font(), statusText, statusX, y + 4, jobColor(job), false);
        Component size = Component.translatable("menu.wrights.size", job.sizeX(), job.sizeY(), job.sizeZ());
        Component blocks = Component.translatable("menu.wrights.job_preview_blocks", WrightsUi.count(job.total()));
        int sizeX = x + 6;
        graphics.drawString(s.font(), size, sizeX, y + 15, WrightsUi.SIZE, false);
        int dashX = sizeX + s.font().width(size);
        graphics.drawString(s.font(), " - ", dashX, y + 15, WrightsUi.MUTED, false);
        int blocksX = dashX + s.font().width(" - ");
        graphics.drawString(s.font(), blocks, blocksX, y + 15, WrightsUi.MUTED, false);
        // Finished cards show how long the active work took (same clock History uses).
        if (job.finished() && job.workedTicks() >= 20) {
            String took = Component.translatable(
                    "history.wrights.took",
                    WrightsClientState.formatSeconds(job.workedTicks() / 20)).getString();
            int tookX = blocksX + s.font().width(blocks) + s.font().width(" · ");
            graphics.drawString(s.font(), " · ", blocksX + s.font().width(blocks), y + 15, WrightsUi.MUTED, false);
            graphics.drawString(s.font(), took, tookX, y + 15, WrightsUi.MUTED, false);
        }
        Component source = jobSource(job);
        if (!source.getString().isEmpty()) {
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(source.getString(), Math.max(40, width - 12)), x + 6, y + 26, WrightsUi.MUTED, false);
        }
        BlockPos anchor = job.anchor();
        graphics.drawString(s.font(), Component.translatable("menu.wrights.job_anchor", anchor.getX(), anchor.getY(), anchor.getZ()), x + 6, y + 37, WrightsUi.MUTED, false);
        drawProgress(graphics, x + 6, y + 48, Math.max(40, width - 12), job);
        boolean tool = s.toolHeld();
        if (!job.finished() && !job.saving()) {
            drawCrewAdjust(graphics, mouseX, mouseY, x, y, width, job, tool);
        }
        for (RowButton button : rowButtons(job, x, y, width)) {
            jobHits.add(new JobHit(button.x(), button.y(), button.width(), 16, job.id(), button.action()));
            s.drawJobButton(graphics, button.x() + button.width(), button.y(), button.key(), mouseX, mouseY, button.width(), button.border(),
                    !needsController(button.action()) || tool);
        }
    }

    /** Working N / Assigned M on the left; − / + sit just past a fixed “100 / 100” slot so they don’t jump. */
    private void drawCrewAdjust(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, BlueprintPayloads.JobLine job, boolean tool) {
        int by = y + JOB_BODY + 1;
        int bx = crewAdjustX(x, width);
        String text = crewCountText(job);
        int textX = x + 6;
        int textY = y + JOB_BODY + 4;
        int textMax = Math.max(24, bx - 6 - textX);
        graphics.drawString(s.font(), s.font().plainSubstrByWidth(text, textMax), textX, textY, WrightsUi.MUTED, false);
        int assigned = assignedWrights(job);
        int cap = jobCrewCap(job);
        boolean canMinus = tool && assigned > 0;
        boolean canPlus = tool && assigned < cap;
        drawCrewStep(graphics, mouseX, mouseY, bx, by, "-", canMinus);
        jobHits.add(new JobHit(bx, by, CREW_STEP, CREW_STEP, job.id(), ACT_CREW_MINUS));
        drawCrewStep(graphics, mouseX, mouseY, bx + CREW_STEP + 2, by, "+", canPlus);
        jobHits.add(new JobHit(bx + CREW_STEP + 2, by, CREW_STEP, CREW_STEP, job.id(), ACT_CREW_PLUS));
        if (mouseX >= bx && mouseX < bx + CREW_STEP && mouseY >= by && mouseY < by + CREW_STEP) {
            graphics.renderTooltip(s.font(), Component.translatable("menu.wrights.job_crew_remove"), mouseX, mouseY);
        } else if (mouseX >= bx + CREW_STEP + 2 && mouseX < bx + CREW_STEP * 2 + 2 && mouseY >= by && mouseY < by + CREW_STEP) {
            graphics.renderTooltip(s.font(), Component.translatable("menu.wrights.job_crew_add"), mouseX, mouseY);
        }
    }

    private void trackCrewAdjust(int x, int y, int width, BlueprintPayloads.JobLine job) {
        if (job.finished() || job.saving()) {
            return;
        }
        int bx = crewAdjustX(x, width);
        int by = y + JOB_BODY + 1;
        jobHits.add(new JobHit(bx, by, CREW_STEP, CREW_STEP, job.id(), ACT_CREW_MINUS));
        jobHits.add(new JobHit(bx + CREW_STEP + 2, by, CREW_STEP, CREW_STEP, job.id(), ACT_CREW_PLUS));
    }

    /** Just after a reserved “Working 100 / 100” width — close to the label, stable as counts change. */
    private int crewAdjustX(int x, int width) {
        int textX = x + 6;
        int reserved = s.font().width(Component.translatable("menu.wrights.job_drone_queued", 100, 100).getString());
        int bx = textX + reserved + 6;
        int maxBx = x + width - 8 - (CREW_STEP * 2 + 2);
        return Math.min(bx, maxBx);
    }

    private void drawCrewStep(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label, boolean enabled) {
        boolean hover = enabled && mouseX >= x && mouseX < x + CREW_STEP && mouseY >= y && mouseY < y + CREW_STEP;
        WrightsUi.choice(graphics, x, y, CREW_STEP, CREW_STEP, false, hover, enabled);
        graphics.drawString(s.font(), label, x + (CREW_STEP - s.font().width(label)) / 2, y + 3, enabled ? WrightsUi.TEXT : 0xFF6E7A86, false);
    }

    /** Highlight / clear-highlights / History are local; everything else talks to the server with the controller. */
    private static boolean needsController(int action) {
        return action != ACT_MARK && action != ACT_CLEAR_MARKS && action != ACT_HISTORY;
    }

    private void trackJobButton(int right, int y, int width, UUID jobId, int action) {
        jobHits.add(new JobHit(right - width, y, width, 16, jobId, action));
    }

    private record JobHit(int x, int y, int w, int h, UUID jobId, int action) {
    }

    private Component finishedTitle() {
        if (s.finishedJobs.isEmpty()) {
            return Component.translatable("menu.wrights.jobs_finished");
        }
        int done = 0;
        int stopped = 0;
        int undone = 0;
        for (BlueprintPayloads.JobLine job : s.finishedJobs) {
            if (job.stopped()) {
                stopped++;
            } else if (job.undone()) {
                undone++;
            } else {
                done++;
            }
        }
        net.minecraft.network.chat.MutableComponent title = Component.empty();
        if (done > 0) {
            title.append(Component.translatable("menu.wrights.jobs_finished_done", done));
        }
        if (stopped > 0) {
            if (!title.getString().isEmpty()) {
                title.append(Component.literal(" · "));
            }
            title.append(Component.translatable("menu.wrights.jobs_finished_stopped", stopped));
        }
        if (undone > 0) {
            if (!title.getString().isEmpty()) {
                title.append(Component.literal(" · "));
            }
            title.append(Component.translatable("menu.wrights.jobs_finished_undone", undone));
        }
        return title.getString().isEmpty() ? Component.translatable("menu.wrights.jobs_finished_done", s.finishedJobs.size()) : title;
    }

    private List<RowButton> rowButtons(BlueprintPayloads.JobLine job, int x, int y, int width) {
        List<RowButton> buttons = new ArrayList<>();
        boolean highlighted = WrightsClientState.markedJobs.contains(job.id());
        String markKey = highlighted ? "menu.wrights.mark_on" : "menu.wrights.mark";
        int markBorder = highlighted ? 0xFFF2D48A : 0;
        if (job.finished()) {
            buttons.add(new RowButton(0, 0, 0, BlueprintPayloads.JobControl.REDO, "menu.wrights.redo", 0));
            if (job.canUndo() && WrightsConfig.allowUndo()) {
                buttons.add(new RowButton(0, 0, 0, BlueprintPayloads.JobControl.UNDO, "menu.wrights.undo", 0));
            }
        } else {
            buttons.add(new RowButton(0, 0, 0, job.paused() ? BlueprintPayloads.JobControl.RESUME : BlueprintPayloads.JobControl.PAUSE, job.paused() ? "menu.wrights.resume" : "menu.wrights.pause", 0));
            buttons.add(new RowButton(0, 0, 0, BlueprintPayloads.JobControl.STOP, "menu.wrights.stop", 0));
            if (!job.saving()) {
                buttons.add(new RowButton(0, 0, 0, ACT_MANAGE, "menu.wrights.manage_drones", 0));
            }
        }
        buttons.add(new RowButton(0, 0, 0, ACT_MARK, markKey, markBorder));
        if (s.creativeVisit()) {
            buttons.add(new RowButton(0, 0, 0, BlueprintPayloads.JobControl.VISIT, "menu.wrights.visit", 0));
        }
        buttons.add(new RowButton(0, 0, 0, BlueprintPayloads.JobControl.REMOVE, "menu.wrights.remove", 0));
        int limit = x + width - 4;
        int cursor = x + 6;
        // Finished cards skip the crew line so Redo/Undo/Highlight stay on-screen.
        int lineY = y + JOB_BODY + (job.finished() || job.saving() ? 0 : JOB_CREW);
        List<RowButton> placed = new ArrayList<>(buttons.size());
        for (RowButton button : buttons) {
            int buttonW = s.jobButtonWidth(button.key());
            if (cursor > x + 6 && cursor + buttonW > limit) {
                cursor = x + 6;
                lineY += JOB_BUTTON;
            }
            placed.add(new RowButton(cursor, lineY, buttonW, button.action(), button.key(), button.border()));
            cursor += buttonW + 4;
        }
        return placed;
    }

    private record RowButton(int x, int y, int width, int action, String key, int border) {
    }

    private BlueprintPayloads.JobLine hoveredJob;

    private void drawJobTooltip(GuiGraphics graphics, int mouseX, int mouseY, BlueprintPayloads.JobLine job) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(job.name()).getVisualOrderText());
        lines.add(jobStatusText(job).getVisualOrderText());
        lines.add(progressLine(job).getVisualOrderText());
        Component wait = WrightsClientState.jobWait(job);
        if (wait != null && job.waitKind() != com.wrights.job.BuildJob.WAIT_ITEMS) {
            lines.add(wait.getVisualOrderText());
        }
        String skipped = skippedLine(job);
        if (!skipped.isEmpty()) {
            lines.add(Component.literal(skipped).getVisualOrderText());
        }
        graphics.renderTooltip(s.font(), lines, mouseX, mouseY);
    }

    private String crewCountText(BlueprintPayloads.JobLine job) {
        int assigned = assignedWrights(job);
        if (!job.finished() && assigned > 0) {
            return Component.translatable("menu.wrights.job_drone_queued", job.drones(), assigned).getString();
        }
        return Component.translatable("menu.wrights.job_drone_count", job.crew().size()).getString();
    }

    private static int assignedWrights(BlueprintPayloads.JobLine job) {
        return Math.max(job.crew().size(), job.quota());
    }

    /** Config max assigned for this job kind — not how many wrights you own or have free. */
    private static int jobCrewCap(BlueprintPayloads.JobLine job) {
        return job == null ? WrightsConfig.maxWrightsPerJob() : Math.max(1, job.crewCap());
    }

    private Component jobStatusText(BlueprintPayloads.JobLine job) {
        String named = job.sourceName();
        if (named != null && !named.isBlank() && job.hold() == com.wrights.job.BuildJob.HOLD_PORT_RANGE && !job.finished() && !job.saving() && !job.undoing()) {
            return Component.translatable("menu.wrights.hold_port_named", named);
        }
        return Component.translatable(jobStatus(job));
    }

    private String jobStatus(BlueprintPayloads.JobLine job) {
        return WrightsClientState.jobStatusKey(job);
    }

    private Component jobSource(BlueprintPayloads.JobLine job) {
        BlockPos at = job.sourcePort();
        boolean coords = job.labelPort() && at != null && !at.equals(BlockPos.ZERO);
        String named = job.sourceName();
        boolean hasName = named != null && !named.isBlank();
        return switch (job.source()) {
            case 1 -> Component.translatable("menu.wrights.job_from_you");
            case 2 -> hasName
                    ? (coords
                            ? Component.translatable("menu.wrights.job_from_port_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port_named", named))
                    : (coords
                            ? Component.translatable("menu.wrights.job_from_port_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port"));
            case 3 -> hasName
                    ? (coords
                            ? Component.translatable("menu.wrights.job_from_both_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both_named", named))
                    : (coords
                            ? Component.translatable("menu.wrights.job_from_both_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both"));
            // Finished jobs used to drop crew before we snapshoted source — show From you.
            default -> job.finished() ? Component.translatable("menu.wrights.job_from_you") : Component.empty();
        };
    }

    private int jobColor(BlueprintPayloads.JobLine job) {
        if (job.finished() && job.stopped()) {
            return WrightsUi.RED;
        }
        if (job.finished() && !job.undone() && !job.undoing()) {
            return WrightsUi.GREEN;
        }
        if (job.paused()) {
            return WrightsUi.GOLD;
        }
        if (job.waitKind() == com.wrights.job.BuildJob.WAIT_ITEMS) {
            return WrightsUi.TEAL;
        }
        if (job.queued()) {
            return WrightsUi.BLUE;
        }
        if (job.hold() != com.wrights.job.BuildJob.HOLD_NONE && job.hold() != com.wrights.job.BuildJob.HOLD_MANUAL) {
            return WrightsUi.TEAL;
        }
        return job.finished() ? WrightsUi.MUTED : WrightsUi.COPPER;
    }

    private void drawProgress(GuiGraphics graphics, int x, int y, int width, BlueprintPayloads.JobLine job) {
        String progress = progressLine(job).getString();
        String skipped = skippedLine(job);
        int skipWidth = skipped.isEmpty() ? 0 : s.font().width(skipped);
        int gap = skipWidth > 0 ? 10 : 0;
        int progressRoom = Math.max(24, width - skipWidth - gap);
        String shown = s.font().plainSubstrByWidth(progress, progressRoom);
        graphics.drawString(s.font(), shown, x, y, WrightsUi.TEXT, false);
        if (skipWidth > 0) {
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(skipped, Math.max(24, width - s.font().width(shown) - gap)), x + s.font().width(shown) + gap, y, 0xFFF0C14A, false);
        }
    }

    private Component progressLine(BlueprintPayloads.JobLine job) {
        int percent = progressPercent(job);
        String left = WrightsClientState.timeLeft(job);
        String done = WrightsUi.count(job.done());
        String total = WrightsUi.count(job.total());
        if (job.finished() || left.isEmpty()) {
            return Component.translatable("menu.wrights.job_percent", percent, done, total);
        }
        return Component.translatable("menu.wrights.job_eta", percent, done, total, left);
    }

    private int progressPercent(BlueprintPayloads.JobLine job) {
        if (job.finished() && job.skipped() <= 0 && !job.stopped() && !job.undone() && !job.undoing()) {
            return 100;
        }
        return WrightsClientState.percent(job);
    }

    private String skippedLine(BlueprintPayloads.JobLine job) {
        int total = Math.max(1, job.total());
        StringBuilder line = new StringBuilder();
        if (job.skipped() > 0 || (job.finished() && !job.undoing() && !job.undone())) {
            int percent = job.skipped() * 100 / total;
            line.append(Component.translatable("menu.wrights.job_skipped", WrightsUi.count(job.skipped()), percent).getString());
        }
        if (job.held() > 0 && !job.finished()) {
            if (!line.isEmpty()) {
                line.append("  ·  ");
            }
            line.append(Component.translatable("menu.wrights.job_held", WrightsUi.count(job.held())).getString());
        }
        return line.toString();
    }

    private boolean jobsBodyHit(double mouseY) {
        return mouseY >= s.jobsViewTop && mouseY < s.jobsViewTop + s.jobsViewH;
    }

    private boolean insideJobsCard(double mouseX, double mouseY) {
        int x = s.listX - 2;
        int width = s.panelX + s.panelW - 12 - x;
        int top = jobsInsetY();
        int bottom = s.wideCardBottom();
        return mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < bottom;
    }

    boolean clickJob(double mouseX, double mouseY) {
        // Prefer hitboxes captured while drawing so layout and pixels cannot disagree.
        if (activateJobAt(mouseX, mouseY, jobHits)) {
            return true;
        }
        // Rebuild once if the cache was empty (first click before a frame, or resize mid-click).
        if (jobHits.isEmpty()) {
            collectJobHits();
            if (activateJobAt(mouseX, mouseY, jobHits)) {
                return true;
            }
        }
        prepareJobs();
        if (!jobsBodyHit(mouseY)) {
            return false;
        }
        return clickJobBar(mouseX, mouseY, true) || clickJobBar(mouseX, mouseY, false);
    }

    private boolean activateJobAt(double mouseX, double mouseY, List<JobHit> hits) {
        // Later entries are row actions drawn on top of earlier header chrome.
        for (int i = hits.size() - 1; i >= 0; i--) {
            JobHit hit = hits.get(i);
            if (!jobHitVisible(hit)) {
                continue;
            }
            if (mouseX < hit.x() || mouseX >= hit.x() + hit.w() || mouseY < hit.y() || mouseY >= hit.y() + hit.h()) {
                continue;
            }
            return activateJobHit(hit);
        }
        return false;
    }

    /** Ignore buttons clipped out of the jobs scissor — their hitboxes must not swallow nearby clicks. */
    private boolean jobHitVisible(JobHit hit) {
        if (hit.action() == ACT_MANAGE_ALL || hit.action() == ACT_CLEAR_MARKS || hit.action() == ACT_HISTORY) {
            return true;
        }
        if (hit.y() + hit.h() <= s.jobsViewTop || hit.y() >= s.jobsViewTop + s.jobsViewH) {
            return false;
        }
        return hit.x() + hit.w() > jobX && hit.x() < jobX + jobsPanelW;
    }

    private void collectJobHits() {
        jobHits.clear();
        prepareJobs();
        trackJobButton(manageAllRight, manageAllY, manageAllWidth, null, ACT_MANAGE_ALL);
        trackJobButton(historyRight, manageAllY, historyWidth, null, ACT_HISTORY);
        if (clearJobs) {
            trackJobButton(clearJobRight, manageAllY, clearJobWidth, null, ACT_CLEAR_MARKS);
        }
        if (!s.workingJobs.isEmpty()) {
            trackJobButton(workRemoveRight, workHeaderY, workRemoveWidth, null, BlueprintPayloads.JobControl.REMOVE_ACTIVE);
            trackJobButton(workPauseRight, workHeaderY, workPauseWidth, null,
                    workAllPaused ? BlueprintPayloads.JobControl.RESUME_ALL : BlueprintPayloads.JobControl.PAUSE_ALL);
            int rowX = jobX + 4;
            int workGutter = s.workingJobs.size() > s.workRows ? 16 : 0;
            int rowW = jobsPanelW - 8 - workGutter;
            for (int row = 0; row < s.workRows && s.jobScroll + row < s.workingJobs.size(); row++) {
                BlueprintPayloads.JobLine job = s.workingJobs.get(s.jobScroll + row);
                int rowY = s.workListY + row * workStride;
                trackCrewAdjust(rowX, rowY, rowW, job);
                for (RowButton button : rowButtons(job, rowX, rowY, rowW)) {
                    jobHits.add(new JobHit(button.x(), button.y(), button.width(), 16, job.id(), button.action()));
                }
            }
        }
        if (!s.finishedJobs.isEmpty()) {
            trackJobButton(doneRemoveRight, doneHeaderY, doneRemoveWidth, null, BlueprintPayloads.JobControl.REMOVE_DONE);
            int rowX = jobX + 4;
            int doneGutter = s.finishedJobs.size() > s.doneRows ? 16 : 0;
            for (int row = 0; row < s.doneRows && s.historyScroll + row < s.finishedJobs.size(); row++) {
                BlueprintPayloads.JobLine job = s.finishedJobs.get(s.historyScroll + row);
                for (RowButton button : rowButtons(job, rowX, s.doneListY + row * doneStride, jobsPanelW - 8 - doneGutter)) {
                    jobHits.add(new JobHit(button.x(), button.y(), button.width(), 16, job.id(), button.action()));
                }
            }
        }
    }

    private boolean activateJobHit(JobHit hit) {
        if (hit.action() == ACT_CLEAR_MARKS) {
            WrightsClientState.clearMarkedJobs();
            WrightsClientState.markedWrights.clear();
            return true;
        }
        if (hit.action() == ACT_MARK) {
            if (hit.jobId() != null) {
                WrightsClientState.toggleMark(hit.jobId());
            }
            return true;
        }
        if (needsController(hit.action()) && !s.requireTool()) {
            return true;
        }
        if (hit.action() == ACT_MANAGE_ALL) {
            s.minecraft().setScreen(new DroneRosterScreen(null));
            return true;
        }
        if (hit.action() == ACT_HISTORY) {
            s.minecraft().setScreen(new JobHistoryScreen(s));
            return true;
        }
        if (hit.action() == ACT_MANAGE) {
            if (hit.jobId() != null) {
                s.minecraft().setScreen(new JobDroneScreen(hit.jobId(), null));
            }
            return true;
        }
        if (hit.action() == ACT_CREW_PLUS || hit.action() == ACT_CREW_MINUS) {
            adjustJobCrew(hit.jobId(), hit.action() == ACT_CREW_PLUS);
            return true;
        }
        UUID id = hit.jobId() == null ? new UUID(0L, 0L) : hit.jobId();
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(id, hit.action()));
        return true;
    }

    private void adjustJobCrew(UUID jobId, boolean add) {
        if (jobId == null || !s.requireTool()) {
            return;
        }
        BlueprintPayloads.JobLine job = null;
        for (BlueprintPayloads.JobLine line : WrightsClientState.jobs) {
            if (line.id().equals(jobId)) {
                job = line;
                break;
            }
        }
        if (job == null || job.finished() || job.saving()) {
            return;
        }
        int assigned = assignedWrights(job);
        int next = WrightsClientState.stepped(assigned, add ? 1 : -1, 0, jobCrewCap(job));
        if (next == assigned) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetJobQuota(jobId, next));
    }

    private int[] dronePool() {
        int owned = 0;
        if (s.minecraft().player != null) {
            for (WrightportPlaces.Spot spot : WrightportPlaces.list(s.minecraft().player)) {
                owned += Swarm.from(WrightportPlaces.stack(s.minecraft().player, spot.kind(), spot.index())).units().size();
            }
            var inventory = s.minecraft().player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (stack.getItem() instanceof WrightportBlockItem) {
                    owned += Swarm.from(stack).units().size() * stack.getCount();
                }
            }
        }
        for (BlueprintPayloads.PlacedPort port : WrightsClientState.placedPorts) {
            if (!port.unowned()) {
                owned += port.units();
            }
        }
        int busyOut = 0;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.finished()) {
                continue;
            }
            // Count wrights actually out on jobs, not the whole crew list (docked leftovers are free).
            busyOut += Math.max(0, job.drones());
        }
        return new int[] { Math.max(0, owned - busyOut), owned };
    }
}
