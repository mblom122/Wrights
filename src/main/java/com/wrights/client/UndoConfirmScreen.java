package com.wrights.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public class UndoConfirmScreen extends Screen {
    private final UUID id;
    private final int extra;
    private final int drop;
    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public UndoConfirmScreen(UUID id, int extra, int drop, Screen parent) {
        super(Component.translatable("screen.wrights.undo_title"));
        this.id = id;
        this.extra = extra;
        this.drop = drop;
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(280, this.width);
        panelH = WrightsUi.fitH(132, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        int half = (panelW - 40) / 2;
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + panelH - 36, half, 20, Component.translatable("screen.wrights.undo_confirm"), button -> confirm()));
        addRenderableWidget(new WrightsButton(panelX + 24 + half, panelY + panelH - 36, panelW - 40 - half, 20, Component.translatable("menu.wrights.cancel_preview"), button -> onClose()));
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
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        String key = drop == BlueprintPayloads.UndoPrompt.AT_CHEST
                ? "screen.wrights.undo_chest"
                : drop == BlueprintPayloads.UndoPrompt.CHEST_GONE
                        ? "screen.wrights.undo_missing"
                        : "screen.wrights.undo_inventory";
        int statusH = Math.max(40, panelH - 70);
        WrightsUi.status(graphics, panelX + 16, panelY + 26, panelW - 32, statusH, WrightsUi.GOLD);
        int y = panelY + 34;
        int wrap = panelW - 52;
        for (var line : this.font.split(Component.translatable(key, extra), wrap)) {
            if (y > panelY + 26 + statusH - 12) {
                break;
            }
            graphics.drawString(this.font, line, panelX + 26, y, WrightsUi.TEXT, false);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
