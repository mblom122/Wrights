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

public class DroneRosterScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final UUID FREE = new UUID(0L, 0L);

    private final Screen parent;
    private final List<DroneRow> drones = new ArrayList<>();
    private final List<Choice> choices = new ArrayList<>();
    private int selected;
    private int panelX;
    private int panelY;
    private int panelH;
    private WrightsButton back;

    public DroneRosterScreen(Screen parent) {
        super(Component.translatable("screen.wrights.manage_all"));
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
        int previous = panelH;
        rebuild();
        if (back != null && previous != panelH) {
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
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        int[] pool = pool();
        graphics.drawString(this.font, Component.translatable("menu.wrights.drones_pool", pool[0], pool[1]), panelX + 16, panelY + 24, WrightsUi.MUTED, false);
        if (drones.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.hangar_empty"), panelX + 16, panelY + 48, WrightsUi.MUTED, false);
            return;
        }
        for (int i = 0; i < drones.size(); i++) {
            DroneRow row = drones.get(i);
            if (i == selected) {
                graphics.fill(panelX + 10, row.y - 3, panelX + PANEL_W - 10, row.y + 12, 0xA0C47A3A);
            }
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.name, 88), panelX + 16, row.y, WrightsUi.TEXT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.stat, 150), panelX + 108, row.y, WrightsUi.MUTED, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row.where, 80), panelX + PANEL_W - 96, row.y, WrightsUi.TEXT, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.move_to"), panelX + 16, moveY() - 14, WrightsUi.TEXT, false);
        for (Choice choice : choices) {
            boolean hover = mouseX >= choice.x && mouseX < choice.x + choice.w && mouseY >= choice.y && mouseY < choice.y + 16;
            int fill = choice.current ? 0xFF2A4054 : (hover ? 0xFF3A5164 : 0xFF24384A);
            graphics.fill(choice.x, choice.y, choice.x + choice.w, choice.y + 16, fill);
            String label = this.font.plainSubstrByWidth(choice.label, choice.w - 6);
            graphics.drawString(this.font, label, choice.x + (choice.w - this.font.width(label)) / 2, choice.y + 4, WrightsUi.TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < drones.size(); i++) {
            DroneRow row = drones.get(i);
            if (mouseX >= panelX + 10 && mouseX < panelX + PANEL_W - 10 && mouseY >= row.y - 3 && mouseY < row.y + 12) {
                selected = i;
                rebuild();
                return true;
            }
        }
        for (Choice choice : choices) {
            if (mouseX >= choice.x && mouseX < choice.x + choice.w && mouseY >= choice.y && mouseY < choice.y + 16) {
                if (!choice.current && selected >= 0 && selected < drones.size()) {
                    PacketDistributor.sendToServer(new BlueprintPayloads.MoveDrone(drones.get(selected).unit, choice.job));
                }
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
        Swarm swarm = swarm();
        UUID keep = selected >= 0 && selected < drones.size() ? drones.get(selected).unit : null;
        drones.clear();
        choices.clear();
        int y = panelY + 44;
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            String where = assignment(unit.id());
            drones.add(new DroneRow(unit.id(), y, Component.translatable("screen.wrights.drone_name", i + 1).getString(), stat(unit), where == null ? Component.translatable("screen.wrights.drone_free").getString() : where));
            y += 16;
            if (keep != null && unit.id().equals(keep)) {
                selected = drones.size() - 1;
            }
        }
        if (drones.isEmpty()) {
            selected = 0;
            panelH = 96;
            panelY = Math.max(8, (this.height - panelH) / 2);
            return;
        }
        if (keep == null) {
            selected = 0;
        }
        if (selected >= drones.size()) {
            selected = drones.size() - 1;
        }
        y += 22;
        UUID currentJob = jobOf(drones.get(selected).unit);
        int x = panelX + 16;
        int rowY = y;
        choices.add(place(x, rowY, Component.translatable("screen.wrights.drone_make_free").getString(), FREE, currentJob == null));
        x += choices.get(0).w + 4;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.finished() || job.saving()) {
                continue;
            }
            String label = job.name();
            int w = Mth.clamp(this.font.width(label) + 12, 52, 120);
            if (x + w > panelX + PANEL_W - 16) {
                x = panelX + 16;
                rowY += 20;
            }
            choices.add(place(x, rowY, label, job.id(), job.id().equals(currentJob)));
            x += w + 4;
        }
        panelH = rowY - panelY + 52;
        int nextY = Math.max(8, (this.height - panelH) / 2);
        int shift = nextY - panelY;
        if (shift != 0) {
            panelY = nextY;
            for (DroneRow row : drones) {
                row.y += shift;
            }
            for (Choice choice : choices) {
                choice.y += shift;
            }
        }
    }

    private int moveY() {
        return choices.isEmpty() ? panelY + 48 : choices.get(0).y;
    }

    private Choice place(int x, int y, String label, UUID job, boolean current) {
        int w = Mth.clamp(this.font.width(label) + 12, 52, 120);
        return new Choice(job, x, y, w, label, current);
    }

    private int[] pool() {
        int total = swarm().units().size();
        int working = 0;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && !job.saving()) {
                working += job.crew().size();
            }
        }
        return new int[] {working, total};
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
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && !job.saving() && job.crew().contains(unit)) {
                return job.id();
            }
        }
        return null;
    }

    private Swarm swarm() {
        return this.minecraft == null || this.minecraft.player == null ? Swarm.EMPTY : Wrightports.swarm(this.minecraft.player);
    }

    private static String stat(Swarm.Unit unit) {
        int speed = Mth.clamp(unit.speed(), 1, Swarm.MAX_SPEED);
        String line = Component.translatable("screen.wrights.drone_stat", Component.translatable("drone.wrights.speed." + speed), speed).getString();
        if (unit.power() > 0) {
            line = line + "  " + Component.translatable("screen.wrights.drone_power", unit.power()).getString();
        }
        return line;
    }

    private static final class DroneRow {
        private final UUID unit;
        private int y;
        private final String name;
        private final String stat;
        private final String where;

        private DroneRow(UUID unit, int y, String name, String stat, String where) {
            this.unit = unit;
            this.y = y;
            this.name = name;
            this.stat = stat;
            this.where = where;
        }
    }

    private static final class Choice {
        private final UUID job;
        private final int x;
        private int y;
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
