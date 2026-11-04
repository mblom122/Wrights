package com.wrights.job;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.wrights.network.BlueprintNetwork;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

/** Tells the player what a finished job did, and writes it to the job history. */
public final class JobAnnouncements {
    private JobAnnouncements() {
    }

    /** Appends " · N operations" when the job recorded at least one AOE/place burst. */
    static Component withOperations(Component detail, BuildJob job) {
        if (detail == null || job == null || job.operations() <= 0 || isNoopFinish(detail)) {
            return detail;
        }
        Component ops = job.operations() == 1
                ? Component.translatable("hud.wrights.done_operations_one")
                : Component.translatable("hud.wrights.done_operations", job.operations());
        return detail.copy().append(Component.literal(" · ")).append(ops);
    }

    /** Mode, the sub-modes that were on, and the task. The detail says what the job actually did. */
    static void announce(ServerPlayer player, SchematicLibrary library, BuildJob job, String taskKey, Component detail) {
        if (player == null || job == null) {
            return;
        }
        Component title = JobTitles.jobTitle(player.getServer(), library, job, taskKey);
        Component line = detail == null ? Component.empty() : detail;
        // Nothing changed — short mid-screen tip instead of a Job done toast.
        if (isNoopFinish(line)) {
            Messages.centerTip(player, line);
        } else if (line.getString().isEmpty()) {
            Messages.actionBar(player, title);
            BlueprintNetwork.jobDone(player, title, line, job.elapsedSeconds());
        } else {
            Messages.actionBar(player, title.copy().append(Component.literal(". ")).append(line));
            BlueprintNetwork.jobDone(player, title, line, job.elapsedSeconds());
        }
        int kind = JobEventLog.COMPLETED;
        if ("hud.wrights.task_undo".equals(taskKey)) {
            kind = JobEventLog.UNDONE;
        } else if ("hud.wrights.task_save".equals(taskKey)) {
            kind = JobEventLog.COPY_DONE;
        } else if (job.stopped()) {
            kind = JobEventLog.STOPPED;
        }
        String historyDetail = line.getString();
        int crew = Math.max(job.crewQuota(), CrewRoster.assignedCount(job));
        if (crew > 0 && (kind == JobEventLog.COMPLETED || kind == JobEventLog.UNDONE)) {
            String crewBit = Component.translatable(
                    crew == 1 ? "history.wrights.crew_used_one" : "history.wrights.crew_used",
                    crew).getString();
            historyDetail = historyDetail.isBlank() ? crewBit : historyDetail + " · " + crewBit;
        }
        noteHistory(player, library, job, kind, historyDetail);
    }

    /**
     * Job ran out of items or Forge Energy: toast once per stall (why, and how many wrights flew home)
     * and write History. A job tick that ends with no wait re-arms the toast for the next stall.
     */
    static void stalled(ServerPlayer player, SchematicLibrary library, BuildJob job, boolean energy, int returned) {
        if (player == null || library == null || job == null) {
            return;
        }
        String reasonKey = energy ? "history.wrights.stalled_energy" : "history.wrights.stalled_items";
        noteHistory(player, library, job, JobEventLog.STALLED_ITEMS, Component.translatable(reasonKey).getString());
        if (job.stallNoticed()) {
            return;
        }
        job.setStallNoticed(true);
        Component header = Component.translatable(energy ? "hud.wrights.job_stalled_energy" : "hud.wrights.job_stalled_items");
        Component name = Component.literal(JobTitles.jobCardTitle(job, library));
        String key = energy ? "hud.wrights.stalled_energy_detail" : "hud.wrights.stalled_items_detail";
        Component detail = returned <= 0
                ? Component.translatable(key + "_none")
                : returned == 1
                        ? Component.translatable(key + "_one")
                        : Component.translatable(key, returned);
        BlueprintNetwork.jobDone(player, header, name, detail, -1);
    }

    private static boolean isNoopFinish(Component detail) {
        if (detail == null) {
            return false;
        }
        String text = detail.getString();
        return text.equals(Component.translatable("message.wrights.already_built").getString())
                || text.equals(Component.translatable("message.wrights.all_skipped").getString())
                || text.equals(Component.translatable("message.wrights.shape_nowhere").getString())
                || text.equals(Component.translatable("message.wrights.mine_none").getString())
                || text.equals(Component.translatable("message.wrights.exchange_none").getString());
    }

    static void noteHistory(ServerPlayer player, SchematicLibrary library, BuildJob job, int kind, String detail) {
        if (player == null || library == null || job == null) {
            return;
        }
        int blocks = job.undoing() || kind == JobEventLog.UNDONE || kind == JobEventLog.UNDOING
                ? Math.max(1, job.undoTotal() > 0 ? job.undoTotal() : job.total())
                : Math.max(0, job.total());
        library.eventLog().record(
                player,
                kind,
                JobTitles.jobCardTitle(job, library),
                detail == null ? "" : detail,
                historyLocation(job),
                job.boxSizeX(),
                job.boxSizeY(),
                job.boxSizeZ(),
                blocks,
                job.elapsedSeconds());
        library.setDirty();
    }

    /** Same coords the Jobs card shows — History needs them to find the site later. */
    private static String historyLocation(BuildJob job) {
        BlockPos pos = job.anchor() != null ? job.anchor() : job.min();
        if (pos == null) {
            return "";
        }
        return Component.translatable("menu.wrights.job_anchor", pos.getX(), pos.getY(), pos.getZ()).getString();
    }
}
