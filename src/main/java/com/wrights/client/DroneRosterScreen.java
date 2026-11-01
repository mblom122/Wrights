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

/** Moves wrights between jobs. The window stays put, and each list scrolls on its own. */
public class DroneRosterScreen extends Screen {
    private static final int PREFERRED_W = 372;
    private static final int ROW = 20;
    private static final int HEADER = 46;
    private static final int MOVE_H = 78;
    private static final int FOOTER = 32;
    private static final int SECTION_HEAD = 18;
    private static final UUID FREE = new UUID(0L, 0L);
    private static final UUID MARK = new UUID(0L, 1L);
    private static final UUID GO = new UUID(0L, 2L);

    private final Screen parent;
    private final List<WrightRow> freeRows = new ArrayList<>();
    private final List<WrightRow> busyRows = new ArrayList<>();
    private final List<Choice> choices = new ArrayList<>();
    private UUID selected = FREE;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int freeScroll;
    private int busyScroll;
    private int moveScroll;
    private int drag;
    private int dragGrab;
    private WrightsButton back;

    public DroneRosterScreen(Screen parent) {
        super(Component.translatable("screen.wrights.manage_roster"));
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
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 8, WrightsUi.COPPER, false);
        Component summary = Component.translatable("screen.wrights.roster_split", freeCount(), busyCount());
        graphics.drawString(this.font, summary, panelX + 16, panelY + 22, WrightsUi.MUTED, false);
        drawList(graphics, freeWell(), "screen.wrights.drones_available", freeCount(), WrightsUi.GREEN, freeRows, freeScroll, 1, mouseX, mouseY);
        drawList(graphics, busyWell(), "screen.wrights.roster_on_job", busyCount(), WrightsUi.COPPER, busyRows, busyScroll, 2, mouseX, mouseY);
        drawMoves(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clickList(freeWell(), freeRows, true, mouseX, mouseY)) {
                return true;
            }
            if (clickList(busyWell(), busyRows, false, mouseX, mouseY)) {
                return true;
            }
            if (clickMove(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drag != 0 && button == 0) {
            if (drag == 3) {
                moveScroll = Mth.clamp((int) (mouseY - dragGrab - moveListY()), 0, moveMax());
            } else {
                boolean free = drag == 1;
                Well well = free ? freeWell() : busyWell();
                List<WrightRow> rows = free ? freeRows : busyRows;
                int listY = well.y + SECTION_HEAD;
                int listH = listH(well);
                int max = maxScroll(rows, listH);
                int thumb = Math.max(12, listH * listH / (listH + Math.max(1, max)));
                int travel = Math.max(1, listH - thumb);
                int next = (int) ((mouseY - dragGrab - listY) * max / travel);
                if (free) {
                    freeScroll = Mth.clamp(next, 0, max);
                } else {
                    busyScroll = Mth.clamp(next, 0, max);
                }
            }
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
        if (inside(mouseX, mouseY, freeWell())) {
            freeScroll = Mth.clamp(freeScroll - delta, 0, maxScroll(freeRows, listH(freeWell())));
            return true;
        }
        if (inside(mouseX, mouseY, busyWell())) {
            busyScroll = Mth.clamp(busyScroll - delta, 0, maxScroll(busyRows, listH(busyWell())));
            return true;
        }
        Well move = moveWell();
        if (inside(mouseX, mouseY, move)) {
            moveScroll = Mth.clamp(moveScroll - delta, 0, moveMax());
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

    private int bodyTop() {
        return panelY + HEADER;
    }

    private int bodyHeight() {
        return panelH - HEADER - MOVE_H - FOOTER;
    }

    private Well freeWell() {
        int height = Math.max(ROW + SECTION_HEAD + 4, (bodyHeight() - 8) / 2);
        return new Well(panelX + 12, bodyTop(), panelW - 24, height);
    }

    private Well busyWell() {
        Well free = freeWell();
        return new Well(free.x, free.y + free.h + 8, free.w, free.h);
    }

    private Well moveWell() {
        return new Well(panelX + 12, panelY + panelH - FOOTER - MOVE_H, panelW - 24, MOVE_H);
    }

    private static int listH(Well well) {
        return well.h - SECTION_HEAD - 4;
    }

    private void drawList(GuiGraphics graphics, Well well, String title, int count, int accent, List<WrightRow> rows, int scroll, int dragId, int mouseX, int mouseY) {
        WrightsUi.inset(graphics, well.x, well.y, well.w, well.h);
        graphics.fill(well.x + 1, well.y + 1, well.x + well.w - 1, well.y + SECTION_HEAD, 0xFF15202B);
        graphics.fill(well.x + 1, well.y + 1, well.x + 3, well.y + SECTION_HEAD, accent);
        graphics.drawString(this.font, Component.translatable(title), well.x + 10, well.y + 5, WrightsUi.TEXT, false);
        String countText = Integer.toString(count);
        graphics.drawString(this.font, countText, well.x + well.w - 8 - this.font.width(countText), well.y + 5, accent, false);
        int listX = well.x + 4;
        int listY = well.y + SECTION_HEAD;
        int listW = well.w - 8;
        int view = listH(well);
        boolean bar = rows.size() * ROW > view;
        int contentRight = listX + listW - (bar ? 8 : 0);
        graphics.enableScissor(listX, listY, contentRight, listY + view);
        int y = listY - scroll;
        for (WrightRow row : rows) {
            if (y + ROW >= listY && y < listY + view) {
                drawWright(graphics, row, listX, y, contentRight - listX, mouseX, mouseY, listY, listY + view);
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (bar) {
            drawBar(graphics, listX + listW - 6, listY, view, scroll, Math.max(0, rows.size() * ROW - view), mouseX, mouseY, drag == dragId);
        }
    }

    private void drawWright(GuiGraphics graphics, WrightRow row, int x, int y, int width, int mouseX, int mouseY, int clipTop, int clipBottom) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, clipTop) && mouseY < Math.min(y + ROW, clipBottom);
        if (row.unit == null) {
            graphics.drawString(this.font, row.name, x + 8, y + 6, WrightsUi.MUTED, false);
            return;
        }
        boolean picked = row.unit.equals(selected);
        if (picked) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + ROW - 1, 0xFF24384A);
            graphics.fill(x + 2, y + 1, x + 4, y + ROW - 1, WrightsUi.COPPER);
        } else if (hover) {
            graphics.fill(x + 2, y + 1, x + width - 2, y + ROW - 1, 0xFF1C2C3A);
        }
        int textRight = x + width - 8;
        int cursor = textRight;
        if (!row.where.isEmpty()) {
            int whereW = Math.min(this.font.width(row.where), Math.max(24, width / 2));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.where, whereW), textRight - whereW, y + 6, row.whereColor, false);
            cursor = textRight - whereW - 6;
        }
        if (!row.tierName.isEmpty()) {
            int tierW = this.font.width(row.tierName);
            graphics.drawString(this.font, row.tierName, cursor - tierW, y + 6, WrightTier.textColor(row.tier), false);
            cursor -= tierW + 6;
        }
        int nameW = Math.max(24, cursor - (x + 8));
        graphics.drawString(this.font, this.font.plainSubstrByWidth(row.name, nameW), x + 8, y + 6, WrightsUi.TEXT, false);
    }

    private void drawMoves(GuiGraphics graphics, int mouseX, int mouseY) {
        Well well = moveWell();
        WrightsUi.inset(graphics, well.x, well.y, well.w, well.h);
        graphics.fill(well.x + 1, well.y + 1, well.x + well.w - 1, well.y + SECTION_HEAD, 0xFF15202B);
        graphics.fill(well.x + 1, well.y + 1, well.x + 3, well.y + SECTION_HEAD, WrightsUi.GOLD);
        String head = Component.translatable("screen.wrights.move_to").getString();
        String detail = selectedDetail();
        if (detail != null) {
            head = detail;
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(head, well.w - 20), well.x + 10, well.y + 5, detail == null ? WrightsUi.TEXT : WrightsUi.GOLD, false);
        int listX = well.x + 6;
        int listY = well.y + SECTION_HEAD + 4;
        int listW = well.w - 12;
        int listH = well.h - SECTION_HEAD - 8;
        if (selected.equals(FREE) || findRow(selected) == null) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.roster_pick"), listX, listY + 4, WrightsUi.MUTED, false);
            return;
        }
        int content = moveContent();
        boolean bar = content > listH;
        int right = listX + listW - (bar ? 8 : 0);
        graphics.enableScissor(listX, listY, right, listY + listH);
        for (Choice choice : choices) {
            int y = listY + choice.y - moveScroll;
            if (y + 16 < listY || y > listY + listH) {
                continue;
            }
            boolean hover = mouseX >= listX + choice.x && mouseX < listX + choice.x + choice.w && mouseY >= y && mouseY < y + 16;
            int fill = choice.current ? 0xFF2A4054 : (hover ? 0xFF2C3E52 : 0xFF1A2633);
            graphics.fill(listX + choice.x, y, listX + choice.x + choice.w, y + 16, fill);
            int edge = choice.current ? WrightsUi.COPPER : (hover ? WrightsUi.GOLD : 0xFF3D4E5E);
            WrightsUi.border(graphics, listX + choice.x, y, choice.w, 16, edge);
            String label = this.font.plainSubstrByWidth(choice.label, choice.w - 8);
            graphics.drawString(this.font, label, listX + choice.x + (choice.w - this.font.width(label)) / 2, y + 4, WrightsUi.TEXT, false);
        }
        graphics.disableScissor();
        if (bar) {
            drawBar(graphics, listX + listW - 6, listY, listH, moveScroll, moveMax(), mouseX, mouseY, drag == 3);
        }
    }

    private boolean clickList(Well well, List<WrightRow> rows, boolean free, double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, well)) {
            return false;
        }
        int listX = well.x + 4;
        int listY = well.y + SECTION_HEAD;
        int listW = well.w - 8;
        int view = listH(well);
        int scroll = free ? freeScroll : busyScroll;
        boolean bar = rows.size() * ROW > view;
        if (bar && mouseX >= listX + listW - 8) {
            int max = maxScroll(rows, view);
            int thumb = Math.max(12, view * view / (view + Math.max(1, max)));
            int thumbY = listY + Math.max(1, view - thumb) * scroll / Math.max(1, max);
            if (mouseY >= thumbY && mouseY < thumbY + thumb) {
                drag = free ? 1 : 2;
                dragGrab = (int) mouseY - thumbY;
            } else {
                int next = mouseY < thumbY ? scroll - view : scroll + view;
                if (free) {
                    freeScroll = Mth.clamp(next, 0, max);
                } else {
                    busyScroll = Mth.clamp(next, 0, max);
                }
            }
            return true;
        }
        int index = ((int) mouseY - listY + scroll) / ROW;
        if (index >= 0 && index < rows.size() && rows.get(index).unit != null) {
            selected = rows.get(index).unit;
            moveScroll = 0;
            rebuild();
        }
        return true;
    }

    private boolean clickMove(double mouseX, double mouseY) {
        Well well = moveWell();
        if (!inside(mouseX, mouseY, well) || selected.equals(FREE) || findRow(selected) == null) {
            return false;
        }
        int listX = well.x + 6;
        int listY = well.y + SECTION_HEAD + 4;
        int listW = well.w - 12;
        int view = well.h - SECTION_HEAD - 8;
        if (moveContent() > view && mouseX >= listX + listW - 8) {
            int max = moveMax();
            int thumb = Math.max(12, view * view / (view + Math.max(1, max)));
            int thumbY = listY + Math.max(1, view - thumb) * moveScroll / Math.max(1, max);
            if (mouseY >= thumbY && mouseY < thumbY + thumb) {
                drag = 3;
                dragGrab = (int) mouseY - thumbY;
            } else {
                moveScroll = Mth.clamp(mouseY < thumbY ? moveScroll - view : moveScroll + view, 0, max);
            }
            return true;
        }
        int localY = (int) mouseY - listY + moveScroll;
        for (Choice choice : choices) {
            if (mouseX >= listX + choice.x && mouseX < listX + choice.x + choice.w && localY >= choice.y && localY < choice.y + 16) {
                if (choice.job.equals(MARK)) {
                    WrightsClientState.toggleWright(selected);
                } else if (choice.job.equals(GO)) {
                    PacketDistributor.sendToServer(new BlueprintPayloads.VisitWright(selected));
                    onClose();
                } else if (!choice.current) {
                    PacketDistributor.sendToServer(new BlueprintPayloads.MoveDrone(selected, choice.job));
                }
                return true;
            }
        }
        return true;
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

    private void rebuild() {
        UUID keep = selected;
        freeRows.clear();
        busyRows.clear();
        choices.clear();
        Swarm swarm = swarm();
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            String where = assignment(unit.id());
            String statusKey = statusKey(unit.id());
            if (where != null && statusKey != null && !"menu.wrights.running".equals(statusKey)) {
                where = where + " · " + Component.translatable(statusKey).getString();
            }
            int whereColor = "screen.wrights.roster_flying".equals(statusKey) ? WrightsUi.TEAL : (statusKey == null || "menu.wrights.running".equals(statusKey) ? WrightsUi.MUTED : WrightsUi.GOLD);
            WrightRow row = new WrightRow(unit.id(), unit.title(i + 1).getString(), WrightTier.clamp(unit.speed()), where == null ? "" : where, whereColor);
            if (where == null) {
                freeRows.add(row);
            } else {
                busyRows.add(row);
            }
        }
        if (freeRows.isEmpty()) {
            freeRows.add(WrightRow.note(Component.translatable("screen.wrights.drones_none_free").getString()));
        }
        if (busyRows.isEmpty()) {
            busyRows.add(WrightRow.note(Component.translatable("screen.wrights.roster_none_busy").getString()));
        }
        if (findRow(keep) == null) {
            selected = FREE;
        }
        layoutChoices();
        freeScroll = Mth.clamp(freeScroll, 0, maxScroll(freeRows, listH(freeWell())));
        busyScroll = Mth.clamp(busyScroll, 0, maxScroll(busyRows, listH(busyWell())));
        moveScroll = Mth.clamp(moveScroll, 0, moveMax());
    }

    private void layoutChoices() {
        if (selected.equals(FREE) || findRow(selected) == null) {
            return;
        }
        UUID current = jobOf(selected);
        int x = 0;
        int y = 0;
        int limit = panelW - 24 - 18;
        if (current != null) {
            boolean marked = WrightsClientState.wrightMarked(selected);
            choices.add(choice(x, y, Component.translatable(marked ? "menu.wrights.mark_on" : "menu.wrights.mark").getString(), MARK, marked));
            x += choices.get(0).w + 4;
            if (creative()) {
                int goW = Mth.clamp(this.font.width(Component.translatable("menu.wrights.visit").getString()) + 12, 52, 140);
                if (x + goW > limit) {
                    x = 0;
                    y += 20;
                }
                choices.add(choice(x, y, Component.translatable("menu.wrights.visit").getString(), GO, false));
                x += goW + 4;
            }
        }
        if (x > 0 && x + 52 > limit) {
            x = 0;
            y += 20;
        }
        choices.add(choice(x, y, Component.translatable("screen.wrights.drone_make_free").getString(), FREE, current == null));
        x += choices.get(choices.size() - 1).w + 4;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.finished() || job.saving()) {
                continue;
            }
            int w = Mth.clamp(this.font.width(job.name()) + 12, 52, 140);
            if (x > 0 && x + w > limit) {
                x = 0;
                y += 20;
            }
            choices.add(choice(x, y, job.name(), job.id(), job.id().equals(current)));
            x += w + 4;
        }
    }

    private Choice choice(int x, int y, String label, UUID job, boolean current) {
        int w = Mth.clamp(this.font.width(label) + 12, 52, 140);
        return new Choice(job, x, y, w, label, current);
    }

    private int moveContent() {
        int bottom = 0;
        for (Choice choice : choices) {
            bottom = Math.max(bottom, choice.y + 16);
        }
        return bottom;
    }

    private int moveMax() {
        return Math.max(0, moveContent() - (MOVE_H - SECTION_HEAD - 8));
    }

    private int moveListY() {
        return moveWell().y + SECTION_HEAD + 4;
    }

    private int busyCount() {
        int count = 0;
        for (WrightRow row : busyRows) {
            if (row.unit != null) {
                count++;
            }
        }
        return count;
    }

    private int freeCount() {
        int count = 0;
        for (WrightRow row : freeRows) {
            if (row.unit != null) {
                count++;
            }
        }
        return count;
    }

    private WrightRow findRow(UUID unit) {
        if (unit == null || unit.equals(FREE)) {
            return null;
        }
        for (WrightRow row : freeRows) {
            if (unit.equals(row.unit)) {
                return row;
            }
        }
        for (WrightRow row : busyRows) {
            if (unit.equals(row.unit)) {
                return row;
            }
        }
        return null;
    }

    private static int maxScroll(List<WrightRow> rows, int view) {
        return Math.max(0, rows.size() * ROW - view);
    }

    private static boolean inside(double mouseX, double mouseY, Well well) {
        return mouseX >= well.x && mouseX < well.x + well.w && mouseY >= well.y && mouseY < well.y + well.h;
    }

    private String assignment(UUID unit) {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && !job.saving() && job.crew().contains(unit)) {
                return job.name();
            }
        }
        return null;
    }

    private UUID jobOf(UUID unit) {
        BlueprintPayloads.JobLine job = jobLine(unit);
        return job == null ? null : job.id();
    }

    private BlueprintPayloads.JobLine jobLine(UUID unit) {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && !job.saving() && job.crew().contains(unit)) {
                return job;
            }
        }
        return null;
    }

    /** Why this wright is not placing blocks, or the running key when the job is moving. */
    private String statusKey(UUID unit) {
        BlueprintPayloads.JobLine job = jobLine(unit);
        if (job == null) {
            return null;
        }
        BlueprintPayloads.WrightSpot spot = WrightsClientState.wrightSpot(unit);
        if (spot != null && spot.state() == 1) {
            return "screen.wrights.roster_flying";
        }
        String key = WrightsClientState.jobStatusKey(job);
        if (!"menu.wrights.running".equals(key)) {
            return key;
        }
        if (spot != null && spot.state() == 2) {
            return "screen.wrights.roster_missing";
        }
        return key;
    }

    private String selectedDetail() {
        if (selected.equals(FREE)) {
            return null;
        }
        BlueprintPayloads.JobLine job = jobLine(selected);
        String key = statusKey(selected);
        if (job == null || key == null) {
            return null;
        }
        String status = Component.translatable(key).getString();
        BlueprintPayloads.WrightSpot spot = WrightsClientState.wrightSpot(selected);
        if (spot != null && spot.pos() != null) {
            return Component.translatable("screen.wrights.roster_where", job.name(), status, spot.pos().getX(), spot.pos().getY(), spot.pos().getZ()).getString();
        }
        return Component.translatable("screen.wrights.roster_where_plain", job.name(), status).getString();
    }

    private boolean creative() {
        return this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild
                && !this.minecraft.player.isSpectator();
    }

    private Swarm swarm() {
        return this.minecraft == null || this.minecraft.player == null ? Swarm.EMPTY : Wrightports.swarm(this.minecraft.player);
    }

    private record Well(int x, int y, int w, int h) {
    }

    private static final class WrightRow {
        private final UUID unit;
        private final String name;
        private final String tierName;
        private final String where;
        private final int whereColor;
        private final int tier;

        private WrightRow(UUID unit, String name, int tier, String where, int whereColor) {
            this.unit = unit;
            this.name = name;
            this.tier = tier;
            this.tierName = unit == null || tier <= 0 ? "" : Component.translatable(WrightTier.nameKey(tier)).getString();
            this.where = where == null ? "" : where;
            this.whereColor = whereColor;
        }

        private static WrightRow note(String name) {
            return new WrightRow(null, name, 0, "", WrightsUi.MUTED);
        }
    }

    private static final class Choice {
        private final UUID job;
        private final int x;
        private final int y;
        private final int w;
        private final String label;
        private final boolean current;

        private Choice(UUID job, int x, int y, int w, String label, boolean current) {
            this.job = job;
            this.x = x;
            this.y = y;
            this.w = w;
            this.label = label;
            this.current = current;
        }
    }
}
