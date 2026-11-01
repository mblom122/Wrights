package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public class FolderSaveScreen extends Screen {
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

    public FolderSaveScreen(int slot, Screen parent) {
        super(Component.translatable("screen.wrights.folder_save_title"));
        this.slot = slot;
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(360, this.width);
        panelH = WrightsUi.fitH(280, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        listX = panelX + 12;
        listY = panelY + 52;
        listW = panelW - 24;
        listH = panelH - 84;
        addRenderableWidget(new WrightsButton(panelX + panelW - 78, panelY + panelH - 24, 66, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
    }

    private List<BlueprintPayloads.FolderLine> folders() {
        List<BlueprintPayloads.FolderLine> found = new ArrayList<>(WrightsClientState.folders);
        found.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.name(), right.name()));
        return found;
    }

    private int blueprints(UUID folder) {
        int count = 0;
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            if (folder.equals(summary.folder())) {
                count++;
            }
        }
        for (BlueprintPayloads.FolderLine child : WrightsClientState.folders) {
            if (folder.equals(child.parent())) {
                count += blueprints(child.id());
            }
        }
        return count;
    }

    private int nested(UUID folder) {
        int count = 1;
        for (BlueprintPayloads.FolderLine child : WrightsClientState.folders) {
            if (folder.equals(child.parent())) {
                count += nested(child.id());
            }
        }
        return count;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            List<BlueprintPayloads.FolderLine> found = folders();
            int index = ((int) mouseY - listY + scroll) / ROW;
            if (index >= 0 && index < found.size()) {
                PacketDistributor.sendToServer(new BlueprintPayloads.WriteFolderItem(slot, found.get(index).id()));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * ROW, 0, Math.max(0, folders().size() * ROW - listH));
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
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 14, panelY + 12, WrightsUi.COPPER, false);
        int hintY = panelY + 26;
        for (var line : this.font.split(Component.translatable("screen.wrights.folder_save_hint"), panelW - 28)) {
            graphics.drawString(this.font, line, panelX + 14, hintY, WrightsUi.MUTED, false);
            hintY += 10;
        }
        List<BlueprintPayloads.FolderLine> found = folders();
        if (found.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.folder_save_empty"), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - scroll;
        for (BlueprintPayloads.FolderLine folder : found) {
            if (y + ROW >= listY && y <= listY + listH) {
                boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW && mouseY >= listY && mouseY < listY + listH;
                if (hover) {
                    graphics.fill(listX + 2, y, listX + listW - 2, y + ROW - 1, WrightsUi.CARD_HOVER);
                    graphics.fill(listX + 2, y, listX + 4, y + ROW - 1, WrightsUi.COPPER);
                }
                graphics.drawString(this.font, this.font.plainSubstrByWidth(folder.name(), listW - 16), listX + 6, y + 2, WrightsUi.TEXT, false);
                graphics.drawString(this.font, Component.translatable("screen.wrights.folder_row", nested(folder.id()), blueprints(folder.id())), listX + 6, y + 12, WrightsUi.MUTED, false);
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
