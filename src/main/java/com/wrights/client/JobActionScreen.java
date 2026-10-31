package com.wrights.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public class JobActionScreen extends Screen {
    private final UUID id;
    private int panelX;
    private int panelY;
    private WrightsButton paceButton;

    public JobActionScreen(UUID id) {
        super(Component.translatable("screen.wrights.job_controls"));
        this.id = id;
    }

    @Override
    protected void init() {
        int panelW = 240;
        int panelH = 188;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        BlueprintPayloads.JobLine job = live();
        boolean paused = job != null && job.paused();
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + 78, 208, 18, Component.translatable("screen.wrights.manage_drones"), button -> manage()));
        paceButton = new WrightsButton(panelX + 16, panelY + 100, 208, 18, Component.translatable(paused ? "menu.wrights.resume" : "menu.wrights.pause"), button -> togglePace());
        addRenderableWidget(paceButton);
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + 122, 100, 18, Component.translatable("menu.wrights.stop"), button -> stop(), true));
        addRenderableWidget(new WrightsButton(panelX + 124, panelY + 122, 100, 18, Component.translatable("menu.wrights.close"), button -> onClose()));
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
        WrightsUi.panel(graphics, panelX, panelY, 240, 188);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BlueprintPayloads.JobLine job = live();
        if (job == null) {
            return;
        }
        String name = this.font.plainSubstrByWidth(job.name(), 208);
        graphics.drawString(this.font, name, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        boolean stalled = !job.paused() && job.waitKind() == com.wrights.job.BuildJob.WAIT_ITEMS;
        String status = job.undoing()
                ? "menu.wrights.undoing"
                : job.paused() ? "menu.wrights.paused" : stalled ? "menu.wrights.stalled" : "menu.wrights.running";
        int statusColor = job.paused() ? WrightsUi.GOLD : stalled ? WrightsUi.TEAL : WrightsUi.COPPER;
        WrightsUi.status(graphics, panelX + 16, panelY + 24, 208, 48, statusColor);
        graphics.drawString(this.font, Component.translatable(status), panelX + 26, panelY + 30, statusColor, false);
        int percent = job.finished() ? 100 : WrightsClientState.percent(job);
        graphics.drawString(this.font, Component.translatable("menu.wrights.job_percent", percent, job.done(), job.total()), panelX + 26, panelY + 42, WrightsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("menu.wrights.job_drone_count", job.crew().size()), panelX + 26, panelY + 54, WrightsUi.TEXT, false);
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

    private boolean requireTool() {
        if (this.minecraft != null && this.minecraft.player != null
                && com.wrights.item.ControllerItem.isHeld(this.minecraft.player)) {
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
