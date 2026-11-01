package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Bind-mode menu: teleport (creative), highlight, or clear the bound supply chest. */
public class SupplyBindScreen extends Screen {
    private static final int PAD = 12;
    private static final int ROW = 18;
    private static final int GAP = 4;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public SupplyBindScreen() {
        super(Component.translatable("screen.wrights.supply_bind_title"));
    }

    @Override
    protected void init() {
        boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
        boolean creative = this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator();
        int btnW = Math.max(120, this.font.width(Component.translatable("screen.wrights.supply_teleport")) + 16);
        btnW = Math.max(btnW, this.font.width(Component.translatable("screen.wrights.supply_highlight")) + 16);
        btnW = Math.max(btnW, this.font.width(Component.translatable("screen.wrights.supply_clear")) + 16);
        btnW = Math.max(btnW, this.font.width(Component.translatable("screen.wrights.done")) + 16);
        panelW = WrightsUi.fitW(PAD * 2 + btnW, this.width);
        int actionRows = 1 + (bound ? (creative ? 3 : 2) : 0);
        panelH = WrightsUi.fitH(PAD + 12 + 12 + GAP + actionRows * (ROW + GAP) + PAD, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        int x = panelX + PAD;
        int y = panelY + PAD + 12 + 12 + GAP;
        int w = panelW - PAD * 2;
        if (bound) {
            if (creative) {
                addRenderableWidget(new WrightsButton(x, y, w, ROW, Component.translatable("screen.wrights.supply_teleport"), button -> {
                    WrightsClientState.visitSupply();
                    this.minecraft.setScreen(null);
                }));
                y += ROW + GAP;
            }
            addRenderableWidget(new WrightsButton(x, y, w, ROW, Component.translatable("screen.wrights.supply_highlight"), button -> {
                WrightsClientState.highlightSupply();
                this.minecraft.setScreen(null);
            }));
            y += ROW + GAP;
            addRenderableWidget(new WrightsButton(x, y, w, ROW, Component.translatable("screen.wrights.supply_clear"), button -> {
                WrightsClientState.clearSupplyBind();
                this.minecraft.setScreen(null);
            }, true));
            y += ROW + GAP;
        }
        addRenderableWidget(new WrightsButton(x, y, w, ROW, Component.translatable("screen.wrights.done"), button -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + PAD, panelY + PAD, WrightsUi.COPPER, false);
        boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
        String status;
        if (bound) {
            BlockPos pos = WrightsClientState.supplyPos;
            status = Component.translatable("screen.wrights.supply_at", pos.getX(), pos.getY(), pos.getZ()).getString();
        } else {
            status = Component.translatable("screen.wrights.supply_none").getString();
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(status, panelW - PAD * 2),
                panelX + PAD, panelY + PAD + 12, WrightsUi.MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
