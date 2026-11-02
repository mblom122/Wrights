package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

import com.wrights.drone.WrightHomes;
import com.wrights.drone.Wrightports;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;
import com.wrights.entity.DroneEntity;
import com.wrights.network.BlueprintPayloads;

/** Assigns wrights to one job. The window stays put; each list scrolls on its own. */
public class JobDroneScreen extends Screen {
    private static final int PREFERRED_W = 420;
    private static final int ROW = 20;
    private static final int DETAIL_ROW = 44;
    private static final int HEADER = 52;
    private static final int FOOTER = 36;
    private static final int SECTION_HEAD = 18;
    private static final int BUTTON_W = 52;

    private final UUID id;
    private final Screen parent;
    private final List<Entry> assigned = new ArrayList<>();
    private final List<Entry> others = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int onScroll;
    private int otherScroll;
    private int drag;
    private int dragGrab;
    private WrightsButton back;

    public JobDroneScreen(UUID id, Screen parent) {
        super(Component.translatable("screen.wrights.manage_drones"));
        this.id = id;
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
        BlueprintPayloads.JobLine job = live();
        if (job == null || job.finished() || job.saving()) {
            onClose();
            return;
        }
        rebuild();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BlueprintPayloads.JobLine job = live();
        if (job == null) {
            return;
        }
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 8, WrightsUi.COPPER, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(job.name(), panelW - 32), panelX + 16, panelY + 20, WrightsUi.TEXT, false);
        int busy = busyCount();
        int free = freeCount();
        int flying = Math.max(0, job.drones());
        int crewNow = crewCount(assigned);
        // Quota stays after out-of-range dock so the job still shows Assigned N while crew is free.
        int assignedWanted = Math.max(crewNow, Math.max(0, job.quota()));
        int docked = Math.max(0, crewNow - flying);
        int open = Math.max(0, assignedWanted - crewNow);
        Component summary;
        if (open > 0) {
            summary = busy == 0
                    ? Component.translatable("screen.wrights.drone_split_open", assignedWanted, flying, open, free)
                    : Component.translatable("screen.wrights.drone_split_open_busy", assignedWanted, flying, open, free, busy);
        } else if (docked > 0) {
            summary = busy == 0
                    ? Component.translatable("screen.wrights.drone_split_docked", assignedWanted, flying, docked, free)
                    : Component.translatable("screen.wrights.drone_split_docked_busy", assignedWanted, flying, docked, free, busy);
        } else {
            summary = busy == 0
                    ? Component.translatable("screen.wrights.drone_split_out", assignedWanted, flying, free)
                    : Component.translatable("screen.wrights.drone_split_out_busy", assignedWanted, flying, free, busy);
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary.getString(), panelW - 32), panelX + 16, panelY + 32, WrightsUi.MUTED, false);

        Well onJobWell = onJobWell();
        Well available = availableWell();
        drawSection(graphics, onJobWell, "screen.wrights.drones_on_job", assignedWanted, WrightsUi.COPPER, assigned, onScroll, mouseX, mouseY, true);
        drawSection(graphics, available, "screen.wrights.drones_available_range", free, WrightsUi.GREEN, others, otherScroll, mouseX, mouseY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clickWell(onJobWell(), assigned, true, mouseX, mouseY)) {
                return true;
            }
            if (clickWell(availableWell(), others, false, mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drag != 0 && button == 0) {
            scrollFromThumb(drag == 1, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            drag = 0;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) Math.signum(scrollY) * ROW;
        Well onJob = onJobWell();
        Well available = availableWell();
        if (inside(mouseX, mouseY, onJob)) {
            onScroll = Mth.clamp(onScroll - delta, 0, maxScroll(assigned, onJob));
            return true;
        }
        if (inside(mouseX, mouseY, available)) {
            otherScroll = Mth.clamp(otherScroll - delta, 0, maxScroll(others, available));
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent == null ? new WrightsMenuScreen() : parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void place() {
        panelW = WrightsUi.fitW(PREFERRED_W, this.width);
        panelH = WrightsUi.fitH(460, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
    }

    private Well onJobWell() {
        int top = panelY + HEADER;
        int body = panelH - HEADER - FOOTER;
        // Give the on-job list more room for the multi-line debug rows.
        int height = Math.max(DETAIL_ROW + SECTION_HEAD + 4, (body * 3) / 5);
        return new Well(panelX + 12, top, panelW - 24, height);
    }

    private Well availableWell() {
        Well onJob = onJobWell();
        int body = panelH - HEADER - FOOTER;
        int height = Math.max(ROW + SECTION_HEAD + 4, body - onJob.h - 8);
        return new Well(onJob.x, onJob.y + onJob.h + 8, onJob.w, height);
    }

    private void drawSection(
            GuiGraphics graphics,
            Well well,
            String title,
            int count,
            int accent,
            List<Entry> rows,
            int scroll,
            int mouseX,
            int mouseY,
            boolean detailed) {
        WrightsUi.inset(graphics, well.x, well.y, well.w, well.h);
        graphics.fill(well.x + 1, well.y + 1, well.x + well.w - 1, well.y + SECTION_HEAD, 0xFF15202B);
        graphics.fill(well.x + 1, well.y + 1, well.x + 3, well.y + SECTION_HEAD, accent);
        Component heading = Component.translatable(title);
        graphics.drawString(this.font, heading, well.x + 10, well.y + 5, WrightsUi.TEXT, false);
        String countText = Integer.toString(count);
        graphics.drawString(this.font, countText, well.x + well.w - 8 - this.font.width(countText), well.y + 5, accent, false);

        int listX = well.x + 4;
        int listY = well.y + SECTION_HEAD;
        int listW = well.w - 8;
        int listH = well.h - SECTION_HEAD - 4;
        boolean bar = contentHeight(rows) > listH;
        int contentRight = listX + listW - (bar ? 8 : 0);
        graphics.enableScissor(listX, listY, contentRight, listY + listH);
        int y = listY - scroll;
        for (int i = 0; i < rows.size(); i++) {
            Entry entry = rows.get(i);
            int rowH = rowHeight(entry);
            if (y + rowH >= listY && y < listY + listH) {
                drawEntry(graphics, entry, i, listX, y, contentRight - listX, rowH, mouseX, mouseY, listY, listY + listH, detailed);
            }
            y += rowH;
        }
        graphics.disableScissor();
        if (bar) {
            drawBar(graphics, listX + listW - 6, listY, listH, scroll, maxScroll(rows, well), mouseX, mouseY, rows == assigned ? drag == 1 : drag == 2);
        }
    }

    private void drawEntry(
            GuiGraphics graphics,
            Entry entry,
            int index,
            int x,
            int y,
            int width,
            int rowH,
            int mouseX,
            int mouseY,
            int clipTop,
            int clipBottom,
            boolean detailed) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + rowH, clipBottom);
        if (entry.divider) {
            int labelW = this.font.width(entry.name);
            graphics.drawString(this.font, entry.name, x + 8, y + 6, WrightsUi.MUTED, false);
            graphics.fill(x + 14 + labelW, y + 9, x + width - 8, y + 10, 0xFF31404E);
            return;
        }
        if (hover && entry.unit != null) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + rowH - 1, 0xFF1C2C3A);
        } else if ((index & 1) == 0 && entry.unit != null) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + rowH - 1, 0xFF101820);
        }
        int buttonX = x + width - 6 - BUTTON_W;
        int textRight = entry.action == null ? x + width - 8 : buttonX - 6;
        int cursor = textRight;
        if (!detailed && !entry.detail.isEmpty()) {
            int detailW = Math.min(this.font.width(entry.detail), Math.max(24, textRight - (x + 8) - 72));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.detail, detailW), textRight - detailW, y + 6, WrightsUi.MUTED, false);
            cursor = textRight - detailW - 6;
        }
        if (!entry.tierName.isEmpty()) {
            int tierW = this.font.width(entry.tierName);
            graphics.drawString(this.font, entry.tierName, cursor - tierW, y + 6, WrightTier.textColor(entry.tier), false);
            cursor -= tierW + 6;
        }
        int nameW = Math.max(24, cursor - (x + 8));
        int nameColor = entry.unit == null ? WrightsUi.MUTED : WrightsUi.TEXT;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.name, nameW), x + 8, y + 6, nameColor, false);
        if (detailed && entry.unit != null) {
            int statusColor = entry.idle || entry.waitReason == DroneEntity.WAIT_IDLE ? WrightsUi.GOLD : WrightsUi.MUTED;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.statusLine, Math.max(24, textRight - (x + 8))), x + 8, y + 17, statusColor, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.whereLine, Math.max(24, textRight - (x + 8))), x + 8, y + 28, WrightsUi.MUTED, false);
        }
        if (entry.action != null) {
            boolean add = "screen.wrights.drone_add".equals(entry.action);
            int by = y + (rowH - 14) / 2;
            boolean onButton = hover && mouseX >= buttonX && mouseX < buttonX + BUTTON_W;
            graphics.fill(buttonX, by, buttonX + BUTTON_W, by + 14, onButton ? 0xFF2C3E52 : 0xFF1A2633);
            int edge = add ? (onButton ? WrightsUi.GREEN : 0xFF2F6A4C) : (onButton ? WrightsUi.RED : 0xFF8E4048);
            WrightsUi.border(graphics, buttonX, by, BUTTON_W, 14, edge);
            String action = Component.translatable(entry.action).getString();
            graphics.drawString(this.font, action, buttonX + (BUTTON_W - this.font.width(action)) / 2, by + 3, add ? WrightsUi.GREEN : WrightsUi.RED, false);
        }
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int height, int scroll, int max, int mouseX, int mouseY, boolean dragging) {
        graphics.fill(x, y, x + 4, y + height, 0xFF0A1218);
        if (max <= 0) {
            return;
        }
        int thumb = Math.max(12, height * height / (height + max));
        int travel = Math.max(1, height - thumb);
        int thumbY = y + travel * Mth.clamp(scroll, 0, max) / max;
        boolean hover = mouseX >= x - 2 && mouseX < x + 8 && mouseY >= thumbY && mouseY < thumbY + thumb;
        graphics.fill(x, thumbY, x + 4, thumbY + thumb, hover || dragging ? WrightsUi.COPPER : 0xFF3D4E5E);
    }

    private boolean clickWell(Well well, List<Entry> rows, boolean assignedList, double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, well)) {
            return false;
        }
        int listX = well.x + 4;
        int listY = well.y + SECTION_HEAD;
        int listW = well.w - 8;
        int listH = well.h - SECTION_HEAD - 4;
        int scroll = assignedList ? onScroll : otherScroll;
        boolean bar = contentHeight(rows) > listH;
        if (bar && mouseX >= listX + listW - 8) {
            int max = maxScroll(rows, well);
            int thumb = Math.max(12, listH * listH / (listH + max));
            int travel = Math.max(1, listH - thumb);
            int thumbY = listY + travel * scroll / max;
            if (mouseY >= thumbY && mouseY < thumbY + thumb) {
                drag = assignedList ? 1 : 2;
                dragGrab = (int) mouseY - thumbY;
            } else {
                int next = mouseY < thumbY ? scroll - listH : scroll + listH;
                setScroll(assignedList, Mth.clamp(next, 0, max));
            }
            return true;
        }
        int y = listY - scroll;
        for (int i = 0; i < rows.size(); i++) {
            Entry entry = rows.get(i);
            int rowH = rowHeight(entry);
            if (mouseY >= y && mouseY < y + rowH) {
                if (entry.unit == null || entry.action == null) {
                    return true;
                }
                int contentRight = listX + listW - (bar ? 8 : 0);
                int buttonX = contentRight - 6 - BUTTON_W;
                if (mouseX < buttonX || mouseX >= buttonX + BUTTON_W) {
                    return true;
                }
                boolean add = "screen.wrights.drone_add".equals(entry.action);
                PacketDistributor.sendToServer(new BlueprintPayloads.SetJobDrones(id, entry.unit, add));
                return true;
            }
            y += rowH;
        }
        return true;
    }

    private void scrollFromThumb(boolean assignedList, double mouseY) {
        Well well = assignedList ? onJobWell() : availableWell();
        List<Entry> rows = assignedList ? assigned : others;
        int listY = well.y + SECTION_HEAD;
        int listH = well.h - SECTION_HEAD - 4;
        int max = maxScroll(rows, well);
        int thumb = Math.max(12, listH * listH / (listH + Math.max(1, max)));
        int travel = Math.max(1, listH - thumb);
        int next = (int) ((mouseY - dragGrab - listY) * max / travel);
        setScroll(assignedList, Mth.clamp(next, 0, max));
    }

    private void setScroll(boolean assignedList, int value) {
        if (assignedList) {
            onScroll = value;
        } else {
            otherScroll = value;
        }
    }

    private static int rowHeight(Entry entry) {
        if (entry.divider || entry.unit == null) {
            return ROW;
        }
        return entry.detailed ? DETAIL_ROW : ROW;
    }

    private static int contentHeight(List<Entry> rows) {
        int height = 0;
        for (Entry entry : rows) {
            height += rowHeight(entry);
        }
        return height;
    }

    private static int maxScroll(List<Entry> rows, Well well) {
        int listH = well.h - SECTION_HEAD - 4;
        return Math.max(0, contentHeight(rows) - listH);
    }

    private static boolean inside(double mouseX, double mouseY, Well well) {
        return mouseX >= well.x && mouseX < well.x + well.w && mouseY >= well.y && mouseY < well.y + well.h;
    }

    private void rebuild() {
        assigned.clear();
        others.clear();
        BlueprintPayloads.JobLine job = live();
        Swarm swarm = swarm();
        if (job != null) {
            for (UUID unit : job.crew()) {
                Swarm.Unit found = find(swarm, unit);
                int number = numberOf(swarm, unit);
                String label = found == null
                        ? Component.translatable("screen.wrights.drone_missing").getString()
                        : found.title(number).getString();
                BlueprintPayloads.CrewHand hand = job.crewHand(unit);
                assigned.add(Entry.assigned(
                        unit,
                        label,
                        found == null ? 0 : WrightTier.clamp(found.speed()),
                        hand,
                        job.hold(),
                        "screen.wrights.drone_remove"));
            }
        }
        if (assigned.isEmpty()) {
            int quota = job == null ? 0 : Math.max(0, job.quota());
            if (quota > 0 && (job.queued() || job.hold() == com.wrights.job.BuildJob.HOLD_PLAYER_RANGE
                    || job.hold() == com.wrights.job.BuildJob.HOLD_DIMENSION)) {
                assigned.add(Entry.note(Component.translatable("screen.wrights.drones_waiting_quota", quota).getString()));
            } else {
                assigned.add(Entry.note(Component.translatable("screen.wrights.drones_none_on").getString()));
            }
        }
        int free = 0;
        int freeElsewhere = 0;
        boolean inRange = job != null && playerCoversJob(job);
        List<Entry> busy = new ArrayList<>();
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            // Only wrights currently linked to this job's crew are reserved here.
            if (job != null && job.crew().contains(unit.id())) {
                continue;
            }
            String other = otherJob(unit.id());
            String label = unit.title(i + 1).getString();
            int tier = WrightTier.clamp(unit.speed());
            if (other == null) {
                // Worn-port wrights reach the job only while you are in range of it.
                if (inRange) {
                    others.add(Entry.wright(unit.id(), label, tier, "", "screen.wrights.drone_add"));
                    free++;
                } else {
                    freeElsewhere++;
                }
            } else {
                busy.add(Entry.wright(unit.id(), label, tier, Component.translatable("screen.wrights.drone_on_other", other).getString(), null));
            }
        }
        if (free == 0) {
            if (freeElsewhere > 0) {
                others.add(0, Entry.note(Component.translatable("screen.wrights.drones_none_free_range", freeElsewhere).getString()));
            } else {
                others.add(0, Entry.note(Component.translatable("screen.wrights.drones_none_free").getString()));
            }
        }
        if (!busy.isEmpty()) {
            others.add(Entry.divider(Component.translatable("screen.wrights.drones_on_other_header").getString()));
            others.addAll(busy);
        }
        onScroll = Mth.clamp(onScroll, 0, maxScroll(assigned, onJobWell()));
        otherScroll = Mth.clamp(otherScroll, 0, maxScroll(others, availableWell()));
    }

    private int freeCount() {
        int count = 0;
        for (Entry entry : others) {
            if ("screen.wrights.drone_add".equals(entry.action)) {
                count++;
            }
        }
        return count;
    }

    private int busyCount() {
        int count = 0;
        for (Entry entry : others) {
            if (entry.unit != null && entry.action == null && !entry.divider) {
                count++;
            }
        }
        return count;
    }

    private static int crewCount(List<Entry> rows) {
        int count = 0;
        for (Entry entry : rows) {
            if (entry.unit != null) {
                count++;
            }
        }
        return count;
    }

    private String otherJob(UUID unit) {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.id().equals(id) || job.finished() || job.saving()) {
                continue;
            }
            if (job.crew().contains(unit)) {
                return job.name();
            }
        }
        return null;
    }

    /** Same reach rule as the server: player within maxRange of the closest point on the job box. */
    private boolean playerCoversJob(BlueprintPayloads.JobLine job) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null || job == null) {
            return false;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(job.dimension());
        if (dimId == null) {
            return false;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos min = job.origin();
        BlockPos far = min.offset(Math.max(0, job.sizeX() - 1), Math.max(0, job.sizeY() - 1), Math.max(0, job.sizeZ() - 1));
        BlockPos anchor = job.anchor() == null ? min : job.anchor();
        BlockPos boxMin = new BlockPos(
                Math.min(min.getX(), Math.min(far.getX(), anchor.getX())),
                Math.min(min.getY(), Math.min(far.getY(), anchor.getY())),
                Math.min(min.getZ(), Math.min(far.getZ(), anchor.getZ())));
        BlockPos boxMax = new BlockPos(
                Math.max(min.getX(), Math.max(far.getX(), anchor.getX())),
                Math.max(min.getY(), Math.max(far.getY(), anchor.getY())),
                Math.max(min.getZ(), Math.max(far.getZ(), anchor.getZ())));
        return WrightHomes.playerCovers(this.minecraft.player, dimension, boxMin, boxMax);
    }

    private BlueprintPayloads.JobLine live() {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.id().equals(id)) {
                return job;
            }
        }
        return null;
    }

    private Swarm swarm() {
        return this.minecraft == null || this.minecraft.player == null ? Swarm.EMPTY : Wrightports.swarm(this.minecraft.player);
    }

    private static Swarm.Unit find(Swarm swarm, UUID id) {
        for (Swarm.Unit unit : swarm.units()) {
            if (unit.id().equals(id)) {
                return unit;
            }
        }
        return null;
    }

    private static int numberOf(Swarm swarm, UUID id) {
        for (int i = 0; i < swarm.units().size(); i++) {
            if (swarm.units().get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String waitLabel(int reason, boolean idle, boolean out, int hold) {
        if (!out) {
            if (idle || reason == DroneEntity.WAIT_IDLE) {
                return Component.translatable("screen.wrights.drone_status_idle_docked").getString();
            }
            return Component.translatable("screen.wrights.drone_status_docked").getString();
        }
        if (hold == com.wrights.job.BuildJob.HOLD_PLAYER_RANGE) {
            return Component.translatable("screen.wrights.drone_status_player_range").getString();
        }
        if (hold == com.wrights.job.BuildJob.HOLD_DIMENSION) {
            return Component.translatable("screen.wrights.drone_status_dimension").getString();
        }
        if (hold == com.wrights.job.BuildJob.HOLD_PORT_RANGE) {
            return Component.translatable("screen.wrights.drone_status_port_range").getString();
        }
        return switch (reason) {
            case DroneEntity.WAIT_FLOOR -> Component.translatable("hud.wrights.info_wright_wait_floor").getString();
            case DroneEntity.WAIT_ITEMS -> Component.translatable("hud.wrights.info_wright_wait_items").getString();
            case DroneEntity.WAIT_CHUNK -> Component.translatable("hud.wrights.info_wright_wait_chunk").getString();
            case DroneEntity.WAIT_IDLE -> Component.translatable("hud.wrights.info_wright_idle").getString();
            case DroneEntity.WAIT_HOME -> Component.translatable("hud.wrights.info_wright_home").getString();
            case DroneEntity.WAIT_SHELTER -> Component.translatable("hud.wrights.info_wright_shelter").getString();
            case DroneEntity.WAIT_PAUSED -> Component.translatable("hud.wrights.info_wright_paused").getString();
            default -> Component.translatable("screen.wrights.drone_status_working").getString();
        };
    }

    private static String coords(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ZERO)) {
            return "-";
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private record Well(int x, int y, int w, int h) {
    }

    private static final class Entry {
        private final UUID unit;
        private final String name;
        private final String tierName;
        private final String detail;
        private final String action;
        private final int tier;
        private final boolean divider;
        private final boolean detailed;
        private final boolean idle;
        private final int waitReason;
        private final String statusLine;
        private final String whereLine;

        private Entry(
                UUID unit,
                String name,
                int tier,
                String tierName,
                String detail,
                String action,
                boolean divider,
                boolean detailed,
                boolean idle,
                int waitReason,
                String statusLine,
                String whereLine) {
            this.unit = unit;
            this.name = name;
            this.tier = tier;
            this.tierName = tierName == null ? "" : tierName;
            this.detail = detail == null ? "" : detail;
            this.action = action;
            this.divider = divider;
            this.detailed = detailed;
            this.idle = idle;
            this.waitReason = waitReason;
            this.statusLine = statusLine == null ? "" : statusLine;
            this.whereLine = whereLine == null ? "" : whereLine;
        }

        private static Entry wright(UUID unit, String name, int tier, String detail, String action) {
            String tierName = tier <= 0 ? "" : Component.translatable(WrightTier.nameKey(tier)).getString();
            return new Entry(unit, name, tier, tierName, detail, action, false, false, false, 0, "", "");
        }

        private static Entry assigned(UUID unit, String name, int tier, BlueprintPayloads.CrewHand hand, int hold, String action) {
            String tierName = tier <= 0 ? "" : Component.translatable(WrightTier.nameKey(tier)).getString();
            if (hand == null) {
                return new Entry(
                        unit,
                        name,
                        tier,
                        tierName,
                        "",
                        action,
                        false,
                        true,
                        false,
                        0,
                        Component.translatable("screen.wrights.drone_status_unknown").getString(),
                        "");
            }
            String status = waitLabel(hand.waitReason(), hand.idle(), hand.out(), hold);
            ItemStack block = hand.block() == null ? ItemStack.EMPTY : hand.block();
            String blockName = block.isEmpty() ? "" : block.getHoverName().getString();
            String target = coords(hand.target());
            String at = coords(hand.at());
            // While held out of range, don't imply they are still placing the last target.
            String statusLine;
            boolean held = hand.out() && (hold == com.wrights.job.BuildJob.HOLD_PLAYER_RANGE
                    || hold == com.wrights.job.BuildJob.HOLD_DIMENSION
                    || hold == com.wrights.job.BuildJob.HOLD_PORT_RANGE
                    || hand.waitReason() == DroneEntity.WAIT_PAUSED);
            if (held) {
                statusLine = status;
            } else if (!blockName.isEmpty() && !target.equals("-")) {
                statusLine = Component.translatable("screen.wrights.drone_status_block_at", status, blockName, target).getString();
            } else if (!blockName.isEmpty()) {
                statusLine = Component.translatable("screen.wrights.drone_status_block", status, blockName).getString();
            } else if (!target.equals("-")) {
                statusLine = Component.translatable("screen.wrights.drone_status_target", status, target).getString();
            } else {
                statusLine = status;
            }
            String whereLine = Component.translatable(
                    "screen.wrights.drone_status_where",
                    hand.out()
                            ? Component.translatable("screen.wrights.drone_status_flying").getString()
                            : Component.translatable("screen.wrights.drone_status_docked").getString(),
                    at,
                    hand.lane() + 1,
                    Math.max(1, hand.lanes())).getString();
            return new Entry(
                    unit,
                    name,
                    tier,
                    tierName,
                    "",
                    action,
                    false,
                    true,
                    hand.idle(),
                    hand.waitReason(),
                    statusLine,
                    whereLine);
        }

        private static Entry note(String name) {
            return new Entry(null, name, 0, "", "", null, false, false, false, 0, "", "");
        }

        private static Entry divider(String name) {
            return new Entry(null, name, 0, "", "", null, true, false, false, 0, "", "");
        }
    }
}
