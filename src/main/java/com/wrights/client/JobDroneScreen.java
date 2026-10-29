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
import com.wrights.network.BlueprintPayloads;

public class JobDroneScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int ROW = 16;

    private final UUID id;
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelH;
    private WrightsButton back;

    public JobDroneScreen(UUID id, Screen parent) {
        super(Component.translatable("screen.wrights.manage_drones"));
        this.id = id;
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        rebuild();
        back = new WrightsButton(panelX + 16, panelY + panelH - 28, PANEL_W - 32, 16, Component.translatable("screen.wrights.back"), button -> onClose());
        addRenderableWidget(back);
    }

    @Override
    public void tick() {
        super.tick();
        BlueprintPayloads.JobLine job = live();
        if (job == null || job.finished() || job.saving()) {
            onClose();
            return;
        }
        int previous = panelH;
        rebuild();
        if (previous != panelH && back != null) {
            back.setY(panelY + panelH - 28);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, PANEL_W, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BlueprintPayloads.JobLine job = live();
        if (job == null) {
            return;
        }
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(job.name(), PANEL_W - 32), panelX + 16, panelY + 24, WrightsUi.TEXT, false);
        int[] pool = pool();
        graphics.drawString(this.font, Component.translatable("menu.wrights.drones_pool", pool[0], pool[1]), panelX + 16, panelY + 38, WrightsUi.MUTED, false);
        for (Row row : rows) {
            if (row.header) {
                graphics.drawString(this.font, Component.translatable(row.label), panelX + 16, row.y, WrightsUi.TEXT, false);
                continue;
            }
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.label, 150), panelX + 16, row.y, WrightsUi.TEXT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.stat, 90), panelX + 150, row.y, WrightsUi.MUTED, false);
            if (row.action != null) {
                int x = panelX + PANEL_W - 68;
                boolean hover = mouseX >= x && mouseX < x + 52 && mouseY >= row.y - 3 && mouseY < row.y + 13;
                graphics.fill(x, row.y - 3, x + 52, row.y + 13, hover ? 0xFF3A5164 : 0xFF24384A);
                String action = Component.translatable(row.action).getString();
                graphics.drawString(this.font, action, x + (52 - this.font.width(action)) / 2, row.y, WrightsUi.TEXT, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Row row : rows) {
            if (row.header || row.unit == null || row.action == null) {
                continue;
            }
            int x = panelX + PANEL_W - 68;
            if (mouseX >= x && mouseX < x + 52 && mouseY >= row.y - 3 && mouseY < row.y + 13) {
                boolean add = "screen.wrights.drone_add".equals(row.action);
                PacketDistributor.sendToServer(new BlueprintPayloads.SetJobDrones(id, row.unit, add));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void rebuild() {
        rows.clear();
        BlueprintPayloads.JobLine job = live();
        Swarm swarm = swarm();
        int y = 56;
        rows.add(Row.header(y, "screen.wrights.drones_on_job"));
        y += 14;
        int shown = 0;
        if (job != null) {
            for (UUID unit : job.crew()) {
                Swarm.Unit found = find(swarm, unit);
                int number = numberOf(swarm, unit);
                String label = found == null
                        ? Component.translatable("screen.wrights.drone_missing").getString()
                        : Component.translatable("screen.wrights.drone_name", number).getString();
                String stat = found == null ? "" : stat(found);
                rows.add(new Row(unit, y, label, stat, "screen.wrights.drone_remove"));
                y += ROW;
                shown++;
            }
        }
        if (shown == 0) {
            rows.add(new Row(null, y, Component.translatable("screen.wrights.drones_none_on").getString(), "", null));
            y += ROW;
        }
        y += 6;
        rows.add(Row.header(y, "screen.wrights.drones_available"));
        y += 14;
        int free = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            if (job != null && job.crew().contains(unit.id())) {
                continue;
            }
            String other = otherJob(unit.id());
            String label = Component.translatable("screen.wrights.drone_name", i + 1).getString();
            String stat = other == null ? stat(unit) : Component.translatable("screen.wrights.drone_on_other", other).getString();
            rows.add(new Row(unit.id(), y, label, stat, other == null ? "screen.wrights.drone_add" : null));
            y += ROW;
            free++;
        }
        if (free == 0) {
            rows.add(new Row(null, y, Component.translatable("screen.wrights.drones_none_free").getString(), "", null));
            y += ROW;
        }
        panelH = y + 36;
        panelY = Math.max(8, (this.height - panelH) / 2);
        for (Row row : rows) {
            row.y += panelY;
        }
    }

    private int[] pool() {
        int total = swarm().units().size();
        int used = 0;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && !job.saving()) {
                used += job.crew().size();
            }
        }
        return new int[] {used, total};
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

    private static String stat(Swarm.Unit unit) {
        String speed = Component.translatable(speedKey(unit.speed())).getString();
        String line = Component.translatable("screen.wrights.drone_stat", speed, Mth.clamp(unit.speed(), 1, Swarm.MAX_SPEED)).getString();
        if (unit.power() > 0) {
            line = line + "  " + Component.translatable("screen.wrights.drone_power", unit.power()).getString();
        }
        return line;
    }

    private static String speedKey(int speed) {
        return "drone.wrights.speed." + Mth.clamp(speed, 1, Swarm.MAX_SPEED);
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

    private static final class Row {
        private final UUID unit;
        private int y;
        private final String label;
        private final String stat;
        private final String action;
        private final boolean header;

        private Row(UUID unit, int y, String label, String stat, String action) {
            this.unit = unit;
            this.y = y;
            this.label = label;
            this.stat = stat;
            this.action = action;
            this.header = false;
        }

        private static Row header(int y, String key) {
            return new Row(y, key);
        }

        private Row(int y, String key) {
            this.unit = null;
            this.y = y;
            this.label = key;
            this.stat = "";
            this.action = null;
            this.header = true;
        }
    }
}
