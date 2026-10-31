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

    public UndoConfirmScreen(UUID id, int extra, int drop, Screen parent) {
        super(Component.translatable("screen.wrights.undo_title"));
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
        addRenderableWidget(new WrightsButton(panelX + 16, panelY + 96, 120, 20, Component.translatable("screen.wrights.undo_confirm"), button -> confirm()));
        addRenderableWidget(new WrightsButton(panelX + 144, panelY + 96, 120, 20, Component.translatable("menu.wrights.cancel_preview"), button -> onClose()));
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
        WrightsUi.panel(graphics, panelX, panelY, 280, 132);
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
        WrightsUi.status(graphics, panelX + 16, panelY + 26, 248, 62, WrightsUi.GOLD);
        int y = panelY + 34;
        for (var line : this.font.split(Component.translatable(key, extra), 228)) {
            if (y > panelY + 76) {
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
