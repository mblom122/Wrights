package com.autobots.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.network.BlueprintPayloads;

public class JobActionScreen extends Screen {
    private final UUID id;
    private int panelX;
    private int panelY;
    private AutobotsButton paceButton;

    public JobActionScreen(UUID id) {
        super(Component.translatable("screen.autobots.job_controls"));
        this.id = id;
    }

    @Override
    protected void init() {
        int panelW = 240;
        int panelH = 148;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        BlueprintPayloads.JobLine job = live();
        boolean paused = job != null && job.paused();
        paceButton = new AutobotsButton(panelX + 16, panelY + 78, 208, 18, Component.translatable(paused ? "menu.autobots.resume" : "menu.autobots.pause"), button -> togglePace());
        addRenderableWidget(paceButton);
        addRenderableWidget(new AutobotsButton(panelX + 16, panelY + 100, 100, 18, Component.translatable("menu.autobots.stop"), button -> stop(), true));
        addRenderableWidget(new AutobotsButton(panelX + 124, panelY + 100, 100, 18, Component.translatable("menu.autobots.close"), button -> onClose()));
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
            paceButton.setMessage(Component.translatable(job.paused() ? "menu.autobots.resume" : "menu.autobots.pause"));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        AutobotsUi.panel(graphics, panelX, panelY, 240, 148);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        BlueprintPayloads.JobLine job = live();
        if (job == null) {
            return;
        }
        String name = this.font.plainSubstrByWidth(job.name(), 208);
        graphics.drawString(this.font, name, panelX + 16, panelY + 12, AutobotsUi.COPPER, false);
        String status = job.undoing()
                ? "menu.autobots.undoing"
                : job.paused() ? "menu.autobots.paused" : "menu.autobots.running";
        graphics.drawString(this.font, Component.translatable(status), panelX + 16, panelY + 28, job.paused() ? 0xFFE0B060 : AutobotsUi.TEXT, false);
        int percent = job.finished() ? 100 : AutobotsClientState.percent(job);
        graphics.drawString(this.font, Component.translatable("menu.autobots.job_percent", percent, job.done(), job.total()), panelX + 16, panelY + 44, AutobotsUi.MUTED, false);
        if (job.skipped() > 0) {
            int skipped = job.skipped() * 100 / Math.max(1, job.total());
            graphics.drawString(this.font, Component.translatable("menu.autobots.job_skipped", job.skipped(), skipped), panelX + 16, panelY + 58, 0xFFF0C14A, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void togglePace() {
        BlueprintPayloads.JobLine job = live();
        if (job == null || job.finished()) {
            return;
        }
        int action = job.paused() ? BlueprintPayloads.JobControl.RESUME : BlueprintPayloads.JobControl.PAUSE;
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(id, action));
    }

    private void stop() {
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(id, BlueprintPayloads.JobControl.STOP));
        onClose();
    }

    private BlueprintPayloads.JobLine live() {
        for (BlueprintPayloads.JobLine job : AutobotsClientState.jobs) {
            if (job.id().equals(id)) {
                return job;
            }
        }
        return null;
    }
}
