package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.drone.Wrightports;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;
import com.wrights.network.BlueprintPayloads;

/** Assigns wrights to one job. The window stays put; each list scrolls on its own. */
public class JobDroneScreen extends Screen {
    private static final int PREFERRED_W = 372;
    private static final int ROW = 20;
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
        Component summary = busy == 0
                ? Component.translatable("screen.wrights.drone_split", assigned.size(), free)
                : Component.translatable("screen.wrights.drone_split_busy", assigned.size(), free, busy);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary.getString(), panelW - 32), panelX + 16, panelY + 32, WrightsUi.MUTED, false);

        Well onJob = onJobWell();
        Well available = availableWell();
        drawSection(graphics, onJob, "screen.wrights.drones_on_job", crewCount(assigned), WrightsUi.COPPER, assigned, onScroll, mouseX, mouseY);
        drawSection(graphics, available, "screen.wrights.drones_available", free, WrightsUi.GREEN, others, otherScroll, mouseX, mouseY);
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
        panelH = WrightsUi.fitH(420, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
    }

    private Well onJobWell() {
        int top = panelY + HEADER;
        int body = panelH - HEADER - FOOTER;
        int height = Math.max(ROW + SECTION_HEAD + 4, (body - 8) / 2);
        return new Well(panelX + 12, top, panelW - 24, height);
    }

    private Well availableWell() {
        Well onJob = onJobWell();
        return new Well(onJob.x, onJob.y + onJob.h + 8, onJob.w, onJob.h);
    }

    private void drawSection(GuiGraphics graphics, Well well, String title, int count, int accent, List<Entry> rows, int scroll, int mouseX, int mouseY) {
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
        boolean bar = rows.size() * ROW > listH;
        int contentRight = listX + listW - (bar ? 8 : 0);
        graphics.enableScissor(listX, listY, contentRight, listY + listH);
        int y = listY - scroll;
        for (int i = 0; i < rows.size(); i++) {
            if (y + ROW >= listY && y < listY + listH) {
                drawEntry(graphics, rows.get(i), i, listX, y, contentRight - listX, mouseX, mouseY, listY, listY + listH);
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (bar) {
            drawBar(graphics, listX + listW - 6, listY, listH, scroll, maxScroll(rows, well), mouseX, mouseY, rows == assigned ? drag == 1 : drag == 2);
        }
    }

    private void drawEntry(GuiGraphics graphics, Entry entry, int index, int x, int y, int width, int mouseX, int mouseY, int clipTop, int clipBottom) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + ROW, clipBottom);
        if (entry.divider) {
            int labelW = this.font.width(entry.name);
            graphics.drawString(this.font, entry.name, x + 8, y + 6, WrightsUi.MUTED, false);
            graphics.fill(x + 14 + labelW, y + 9, x + width - 8, y + 10, 0xFF31404E);
            return;
        }
        if (hover && entry.unit != null) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + ROW - 1, 0xFF1C2C3A);
        } else if ((index & 1) == 0 && entry.unit != null) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + ROW - 1, 0xFF101820);
        }
        int buttonX = x + width - 6 - BUTTON_W;
        int textRight = entry.action == null ? x + width - 8 : buttonX - 6;
        int cursor = textRight;
        if (!entry.detail.isEmpty()) {
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
        if (entry.action != null) {
            boolean add = "screen.wrights.drone_add".equals(entry.action);
            int by = y + 3;
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
        boolean bar = rows.size() * ROW > listH;
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
        int index = ((int) mouseY - listY + scroll) / ROW;
        if (index < 0 || index >= rows.size()) {
            return true;
        }
        Entry entry = rows.get(index);
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

    private static int maxScroll(List<Entry> rows, Well well) {
        int listH = well.h - SECTION_HEAD - 4;
        return Math.max(0, rows.size() * ROW - listH);
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
                assigned.add(Entry.wright(unit, label, found == null ? 0 : WrightTier.clamp(found.speed()), "", "screen.wrights.drone_remove"));
            }
        }
        if (assigned.isEmpty()) {
            assigned.add(Entry.note(Component.translatable("screen.wrights.drones_none_on").getString()));
        }
        int free = 0;
        List<Entry> busy = new ArrayList<>();
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            if (job != null && job.crew().contains(unit.id())) {
                continue;
            }
            String other = otherJob(unit.id());
            String label = unit.title(i + 1).getString();
            int tier = WrightTier.clamp(unit.speed());
            if (other == null) {
                others.add(Entry.wright(unit.id(), label, tier, "", "screen.wrights.drone_add"));
                free++;
            } else {
                busy.add(Entry.wright(unit.id(), label, tier, Component.translatable("screen.wrights.drone_on_other", other).getString(), null));
            }
        }
        if (free == 0) {
            others.add(0, Entry.note(Component.translatable("screen.wrights.drones_none_free").getString()));
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

        private Entry(UUID unit, String name, int tier, String tierName, String detail, String action, boolean divider) {
            this.unit = unit;
            this.name = name;
            this.tier = tier;
            this.tierName = tierName == null ? "" : tierName;
            this.detail = detail == null ? "" : detail;
            this.action = action;
            this.divider = divider;
        }

        private static Entry wright(UUID unit, String name, int tier, String detail, String action) {
            String tierName = tier <= 0 ? "" : Component.translatable(WrightTier.nameKey(tier)).getString();
            return new Entry(unit, name, tier, tierName, detail, action, false);
        }

        private static Entry note(String name) {
            return new Entry(null, name, 0, "", "", null, false);
        }

        private static Entry divider(String name) {
            return new Entry(null, name, 0, "", "", null, true);
        }
    }
}
