package com.autobots.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.network.BlueprintPayloads;

public class UndoConfirmScreen extends Screen {
    private final UUID id;
    private final int extra;
    private final int drop;
    private final Screen parent;
    private int panelX;
    private int panelY;

    public UndoConfirmScreen(UUID id, int extra, int drop, Screen parent) {
        super(Component.translatable("screen.autobots.undo_title"));
        this.id = id;
        this.extra = extra;
        this.drop = drop;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelW = 280;
        int panelH = 132;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        addRenderableWidget(new AutobotsButton(panelX + 16, panelY + 96, 120, 20, Component.translatable("screen.autobots.undo_confirm"), button -> confirm()));
        addRenderableWidget(new AutobotsButton(panelX + 144, panelY + 96, 120, 20, Component.translatable("menu.autobots.cancel_preview"), button -> onClose()));
    }

    private void confirm() {
        PacketDistributor.sendToServer(new BlueprintPayloads.JobControl(id, BlueprintPayloads.JobControl.UNDO_FORCE));
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        AutobotsUi.panel(graphics, panelX, panelY, 280, 132);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 12, AutobotsUi.COPPER, false);
        String key = drop == BlueprintPayloads.UndoPrompt.AT_CHEST
                ? "screen.autobots.undo_chest"
                : drop == BlueprintPayloads.UndoPrompt.CHEST_GONE
                        ? "screen.autobots.undo_missing"
                        : "screen.autobots.undo_inventory";
        int y = panelY + 32;
        for (var line : this.font.split(Component.translatable(key, extra), 248)) {
            graphics.drawString(this.font, line, panelX + 16, y, AutobotsUi.TEXT, false);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
