package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public class MaterialsScreen extends Screen {
    private static final int ROW = 16;
    private static final int GREEN = 0xFF6FCF97;
    private static final int RED = 0xFFE07A7A;

    private final UUID id;
    private final Screen parent;
    private final List<BlueprintPayloads.MaterialLine> lines = new ArrayList<>();
    private boolean shortage;
    private boolean loaded;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listY;
    private int listH;
    private int scroll;

    public MaterialsScreen(UUID id, Screen parent) {
        super(Component.translatable("menu.wrights.materials_title"));
        this.id = id;
        this.parent = parent;
    }

    public static void request(UUID id, int clipX, int clipY, int clipZ, Screen parent) {
        if (id == null) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.setScreen(new MaterialsScreen(id, parent));
        PacketDistributor.sendToServer(new BlueprintPayloads.MaterialsRequest(id, clipX, clipY, clipZ));
    }

    public static void show(BlueprintPayloads.Materials payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen instanceof MaterialsScreen screen && screen.id.equals(payload.id())) {
            screen.apply(payload);
            return;
        }
        Screen parent = payload.shortage() ? null : minecraft.screen;
        MaterialsScreen screen = new MaterialsScreen(payload.id(), parent);
        screen.apply(payload);
        minecraft.setScreen(screen);
    }

    public void apply(BlueprintPayloads.Materials payload) {
        lines.clear();
        lines.addAll(payload.lines());
        shortage = payload.shortage();
        loaded = true;
        scroll = 0;
    }

    @Override
    protected void init() {
        panelW = Math.min(320, this.width - 16);
        panelH = Math.min(240, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listY = panelY + 36;
        listH = panelH - 78;
        addRenderableWidget(new WrightsButton(panelX + 12, panelY + panelH - 28, panelW - 24, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        WrightsUi.inset(graphics, panelX + 10, listY - 4, panelW - 20, listH + 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component title = Component.translatable(shortage ? "menu.wrights.need_title" : "menu.wrights.materials_title");
        graphics.drawString(this.font, title, panelX + 14, panelY + 8, WrightsUi.COPPER, false);
        boolean creative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator();
        Component hint = Component.translatable(creative ? "menu.wrights.materials_creative" : "menu.wrights.materials_hint");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(hint.getString(), panelW - 28), panelX + 14, panelY + 20, WrightsUi.MUTED, false);
        if (!loaded) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.materials_loading"), panelX + 18, listY + 6, WrightsUi.MUTED, false);
            return;
        }
        if (lines.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.materials_empty"), panelX + 18, listY + 6, WrightsUi.MUTED, false);
            return;
        }
        int rows = Math.max(1, listH / ROW);
        graphics.enableScissor(panelX + 12, listY, panelX + panelW - 12, listY + listH);
        for (int i = scroll; i < lines.size() && i - scroll < rows; i++) {
            BlueprintPayloads.MaterialLine line = lines.get(i);
            Item item = BuiltInRegistries.ITEM.byId(line.itemId());
            if (item == Items.AIR) {
                continue;
            }
            int y = listY + (i - scroll) * ROW;
            int have = this.minecraft.player == null ? 0 : this.minecraft.player.getInventory().countItem(item);
            int color = creative ? WrightsUi.BLUE : (have >= line.count() ? GREEN : RED);
            String name = this.font.plainSubstrByWidth(item.getName(item.getDefaultInstance()).getString(), panelW - 110);
            graphics.drawString(this.font, name, panelX + 18, y + 3, WrightsUi.TEXT, false);
            String amount = creative ? Integer.toString(line.count()) : have + " / " + line.count();
            graphics.drawString(this.font, amount, panelX + panelW - 18 - this.font.width(amount), y + 3, color, false);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = Math.max(1, listH / ROW);
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, lines.size() - rows));
        return true;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
