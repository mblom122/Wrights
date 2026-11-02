package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.drone.Swarm;
import com.wrights.drone.Wrightports;
import com.wrights.network.BlueprintPayloads;

public class JobActionScreen extends Screen {
    private static final int CREW_STEP = 14;

    private final UUID id;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private WrightsButton paceButton;
    private int minusX;
    private int plusX;
    private int stepY;

    public JobActionScreen(UUID id) {
        super(Component.translatable("screen.wrights.job_controls"));
        this.id = id;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(240, this.width);
        panelH = WrightsUi.fitH(188, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        int inner = panelW - 32;
        int half = (inner - 8) / 2;
        BlueprintPayloads.JobLine job = live();
        boolean paused = job != null && job.paused();
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + panelH - 110, inner, 18, Component.translatable("screen.wrights.manage_drones"), button -> manage()));
        paceButton = new WrightsButton(panelX + 16, panelY + panelH - 88, inner, 18, Component.translatable(paused ? "menu.wrights.resume" : "menu.wrights.pause"), button -> togglePace());
        addRenderableWidget(paceButton);
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + panelH - 66, half, 18, Component.translatable("menu.wrights.stop"), button -> stop(), true));
        addRenderableWidget(new WrightsButton(panelX + 16 + half + 8, panelY + panelH - 66, panelW - 32 - half - 8, 18, Component.translatable("menu.wrights.close"), button -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        BlueprintPayloads.JobLine job = live();
        if (job == null || job.finished()) {
            onClose();
            return;
        }
        if (paceButton != null) {
            paceButton.setMessage(Component.translatable(job.paused() ? "menu.wrights.resume" : "menu.wrights.pause"));
        }
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
        int inner = panelW - 32;
        String name = this.font.plainSubstrByWidth(job.name(), inner);
        graphics.drawString(this.font, name, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        boolean stalled = !job.paused() && job.waitKind() == com.wrights.job.BuildJob.WAIT_ITEMS;
        String status = job.undoing()
                ? "menu.wrights.undoing"
                : job.paused() ? "menu.wrights.paused" : stalled ? "menu.wrights.stalled" : "menu.wrights.running";
        int statusColor = job.paused() ? WrightsUi.GOLD : stalled ? WrightsUi.TEAL : WrightsUi.COPPER;
        WrightsUi.status(graphics, panelX + 16, panelY + 24, inner, Math.min(48, panelH - 140), statusColor);
        graphics.drawString(this.font, Component.translatable(status), panelX + 26, panelY + 30, statusColor, false);
        int percent = job.finished() ? 100 : WrightsClientState.percent(job);
        graphics.drawString(this.font, Component.translatable("menu.wrights.job_percent", percent, WrightsUi.count(job.done()), WrightsUi.count(job.total())), panelX + 26, panelY + 42, WrightsUi.MUTED, false);
        Component crew = Component.translatable("menu.wrights.job_drone_count", job.crew().size());
        graphics.drawString(this.font, crew, panelX + 26, panelY + 54, WrightsUi.TEXT, false);
        boolean tool = requireToolSilent();
        minusX = panelX + 26 + this.font.width(crew) + 6;
        plusX = minusX + CREW_STEP + 2;
        stepY = panelY + 51;
        drawStep(graphics, mouseX, mouseY, minusX, stepY, "-", tool && !job.crew().isEmpty());
        drawStep(graphics, mouseX, mouseY, plusX, stepY, "+", tool && firstFreeWright(job) != null);
        if (mouseX >= minusX && mouseX < minusX + CREW_STEP && mouseY >= stepY && mouseY < stepY + CREW_STEP) {
            graphics.renderTooltip(this.font, Component.translatable("menu.wrights.job_crew_remove"), mouseX, mouseY);
        } else if (mouseX >= plusX && mouseX < plusX + CREW_STEP && mouseY >= stepY && mouseY < stepY + CREW_STEP) {
            graphics.renderTooltip(this.font, Component.translatable("menu.wrights.job_crew_add"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            BlueprintPayloads.JobLine job = live();
            if (job != null && !job.finished() && !job.saving()) {
                if (mouseX >= minusX && mouseX < minusX + CREW_STEP && mouseY >= stepY && mouseY < stepY + CREW_STEP) {
                    adjustCrew(false);
                    return true;
                }
                if (mouseX >= plusX && mouseX < plusX + CREW_STEP && mouseY >= stepY && mouseY < stepY + CREW_STEP) {
                    adjustCrew(true);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawStep(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label, boolean enabled) {
        boolean hover = enabled && mouseX >= x && mouseX < x + CREW_STEP && mouseY >= y && mouseY < y + CREW_STEP;
        WrightsUi.choice(graphics, x, y, CREW_STEP, CREW_STEP, false, hover, enabled);
        graphics.drawString(this.font, label, x + (CREW_STEP - this.font.width(label)) / 2, y + 3, enabled ? WrightsUi.TEXT : 0xFF6E7A86, false);
    }

    private void adjustCrew(boolean add) {
        if (!requireTool()) {
            return;
        }
        BlueprintPayloads.JobLine job = live();
        if (job == null) {
            return;
        }
        UUID unit = add ? firstFreeWright(job) : lastCrewWright(job);
        if (unit == null) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable(add
                        ? "screen.wrights.drones_none_free"
                        : "screen.wrights.drones_none_on"), true);
            }
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetJobDrones(id, unit, add));
    }

    private UUID firstFreeWright(BlueprintPayloads.JobLine job) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return null;
        }
        Swarm swarm = Wrightports.swarm(this.minecraft.player);
        for (Swarm.Unit unit : swarm.units()) {
            if (job.crew().contains(unit.id()) || busyElsewhere(unit.id())) {
                continue;
            }
            return unit.id();
        }
        return null;
    }

    private static UUID lastCrewWright(BlueprintPayloads.JobLine job) {
        List<UUID> crew = job.crew();
        return crew.isEmpty() ? null : crew.get(crew.size() - 1);
    }

    private boolean busyElsewhere(UUID unit) {
        for (BlueprintPayloads.JobLine other : WrightsClientState.jobs) {
            if (other.id().equals(id) || other.finished() || other.saving()) {
                continue;
            }
            if (other.crew().contains(unit)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void manage() {
        if (!requireTool()) {
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new JobDroneScreen(id, this));
        }
    }

    private void togglePace() {
        BlueprintPayloads.JobLine job = live();
        if (job == null || job.finished() || !requireTool()) {
            return;
        }
        int action = job.paused() ? BlueprintPayloads.JobControl.RESUME : BlueprintPayloads.JobControl.PAUSE;
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(id, action));
    }

    private void stop() {
        if (!requireTool()) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(id, BlueprintPayloads.JobControl.STOP));
        onClose();
    }

    private boolean requireToolSilent() {
        return this.minecraft != null && this.minecraft.player != null
                && com.wrights.item.ControllerItem.isHeld(this.minecraft.player);
    }

    private boolean requireTool() {
        if (requireToolSilent()) {
            return true;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("message.wrights.need_controller"), true);
        }
        return false;
    }

    private BlueprintPayloads.JobLine live() {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.id().equals(id)) {
                return job;
            }
        }
        return null;
    }
}
