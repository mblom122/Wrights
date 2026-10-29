package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public class BlueprintSaveScreen extends Screen {
    private static final int ROW = 22;

    private final int slot;
    private final Screen parent;
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public BlueprintSaveScreen(int slot, Screen parent) {
        super(Component.translatable("screen.wrights.slip_save_title"));
        this.slot = slot;
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, this.width - 16);
        panelH = Math.min(280, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX = panelX + 12;
        listY = panelY + 48;
        listW = panelW - 24;
        listH = panelH - 80;
        addRenderableWidget(new WrightsButton(panelX + panelW - 78, panelY + panelH - 24, 66, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
    }

    private List<BlueprintPayloads.Summary> blueprints() {
        List<BlueprintPayloads.Summary> found = new ArrayList<>();
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            found.add(summary);
        }
        found.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.name(), right.name()));
        return found;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            List<BlueprintPayloads.Summary> found = blueprints();
            int index = ((int) mouseY - listY + scroll) / ROW;
            if (index >= 0 && index < found.size()) {
                PacketDistributor.sendToServer(new BlueprintPayloads.WriteBlueprintItem(slot, found.get(index).id()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * ROW, 0, Math.max(0, blueprints().size() * ROW - listH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 14, panelY + 12, WrightsUi.COPPER, false);
        int hintY = panelY + 26;
        for (var line : this.font.split(Component.translatable("screen.wrights.slip_save_hint"), panelW - 28)) {
            graphics.drawString(this.font, line, panelX + 14, hintY, WrightsUi.MUTED, false);
            hintY += 10;
        }
        List<BlueprintPayloads.Summary> found = blueprints();
        if (found.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.slip_save_empty"), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - scroll;
        for (BlueprintPayloads.Summary summary : found) {
            if (y + ROW >= listY && y <= listY + listH) {
                boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW && mouseY >= listY && mouseY < listY + listH;
                if (hover) {
                    graphics.fill(listX + 2, y, listX + listW - 2, y + ROW - 1, 0xFF1C2A36);
                }
                graphics.drawString(this.font, this.font.plainSubstrByWidth(summary.name(), listW - 16), listX + 6, y + 2, WrightsUi.TEXT, false);
                graphics.drawString(this.font, Component.translatable("screen.wrights.slip_row", summary.sizeX(), summary.sizeY(), summary.sizeZ(), summary.blocks()), listX + 6, y + 12, WrightsUi.MUTED, false);
            }
            y += ROW;
        }
        graphics.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
