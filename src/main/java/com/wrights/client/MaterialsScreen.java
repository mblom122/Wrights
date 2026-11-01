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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsClientConfig;
import com.wrights.network.BlueprintPayloads;

public class MaterialsScreen extends Screen {
    private static final int ROW = 20;
    private static final int HEADER = 14;
    private static final int COUNT_W = 44;
    private static final int GREEN = 0xFF6FCF97;
    private static final int RED = 0xFFE07A7A;

    private final UUID id;
    private final Screen parent;
    private final List<BlueprintPayloads.MaterialLine> lines = new ArrayList<>();
    private boolean shortage;
    private boolean supplyBound;
    private boolean digitalBound;
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
        request(
                id,
                clipX,
                clipY,
                clipZ,
                WrightsClientConfig.PLACE_FLUIDS.get(),
                WrightsClientConfig.PLACE_NBT_BLOCKS.get(),
                WrightsClientState.placeSettingsChosen(),
                WrightsClientState.placeAllChosen(),
                parent);
    }

    public static void request(UUID id, int clipX, int clipY, int clipZ, boolean placeFluids, Screen parent) {
        request(
                id,
                clipX,
                clipY,
                clipZ,
                placeFluids,
                WrightsClientConfig.PLACE_NBT_BLOCKS.get(),
                WrightsClientState.placeSettingsChosen(),
                WrightsClientState.placeAllChosen(),
                parent);
    }

    public static void request(
            UUID id,
            int clipX,
            int clipY,
            int clipZ,
            boolean placeFluids,
            boolean placeNbt,
            boolean placeSettings,
            boolean placeAll,
            Screen parent) {
        if (id == null) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.setScreen(new MaterialsScreen(id, parent));
        PacketDistributor.sendToServer(new BlueprintPayloads.MaterialsRequest(
                id, clipX, clipY, clipZ, placeFluids, placeNbt, placeSettings, placeAll));
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
        supplyBound = payload.supply();
        digitalBound = payload.digital();
        loaded = true;
        scroll = 0;
        if (this.minecraft != null && this.minecraft.screen == this) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(560, this.width);
        panelH = WrightsUi.fitH(240, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        listY = panelY + 36;
        listH = panelH - 78;
        int y = panelY + panelH - 28;
        if (shortage) {
            Component anyway = Component.translatable("menu.wrights.build_anyway");
            Component close = Component.translatable("menu.wrights.close");
            int closeW = Math.max(72, this.font.width(close) + 16);
            int buildW = Math.max(96, this.font.width(anyway) + 16);
            int closeX = panelX + panelW - 12 - closeW;
            int buildX = Math.max(panelX + 12, closeX - 8 - buildW);
            addRenderableWidget(new WrightsButton(buildX, y, Math.min(buildW, closeX - 8 - buildX), 16, anyway, button -> buildAnyway()));
            addRenderableWidget(new WrightsButton(closeX, y, closeW, 16, close, button -> onClose()));
            return;
        }
        addRenderableWidget(new WrightsButton(panelX + 12, y, panelW - 24, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
    }

    private void buildAnyway() {
        if (id.equals(com.wrights.blueprint.ShapeLaunch.EXCHANGE_MATERIALS_ID)) {
            if (WrightsClientState.tryExchange(true)) {
                onClose();
            }
            return;
        }
        if (id.equals(com.wrights.blueprint.ShapeLaunch.MATERIALS_ID)) {
            if (WrightsClientState.tryShape(true)) {
                onClose();
            }
            return;
        }
        if (WrightsClientState.tryBuild(true)) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        WrightsUi.inset(graphics, panelX + 10, listY - 4, panelW - 20, listH + 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component title = Component.translatable(shortage ? "menu.wrights.need_title" : "menu.wrights.materials_title");
        graphics.drawString(this.font, title, panelX + 14, panelY + 8, WrightsUi.COPPER, false);
        boolean creative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild && !this.minecraft.player.isSpectator();
        boolean queue = WrightsClientConfig.QUEUE_JOBS.get();
        String hintKey = creative ? "menu.wrights.materials_creative" : shortage ? (queue ? "menu.wrights.need_hint_queue" : "menu.wrights.need_hint") : "menu.wrights.materials_hint";
        Component hint = Component.translatable(hintKey);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(hint.getString(), panelW - 28), panelX + 14, panelY + 20, WrightsUi.MUTED, false);
        if (!loaded) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.materials_loading"), panelX + 18, listY + 6, WrightsUi.MUTED, false);
            return;
        }
        if (lines.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.materials_empty"), panelX + 18, listY + 6, WrightsUi.MUTED, false);
            return;
        }
        int right = panelX + panelW - 18;
        int inventoryW = Math.max(COUNT_W, this.font.width(Component.translatable("menu.wrights.column_inventory").getString()));
        int digitalW = Math.max(COUNT_W, this.font.width(Component.translatable("menu.wrights.column_digital").getString()));
        int missingW = Math.max(COUNT_W, this.font.width(Component.translatable("menu.wrights.column_missing").getString()));
        int totalW = Math.max(COUNT_W, this.font.width(Component.translatable("menu.wrights.column_total").getString()));
        for (BlueprintPayloads.MaterialLine line : lines) {
            long supply = supplyBound ? line.supply() : 0L;
            long digital = digitalBound ? line.digital() : 0L;
            long sum = line.player() + supply + digital;
            long shortfall = Math.max(0L, line.count() - sum);
            if (supplyBound) {
                inventoryW = Math.max(inventoryW, this.font.width(Integer.toString(line.supply())));
            }
            if (digitalBound) {
                digitalW = Math.max(digitalW, this.font.width(Integer.toString(line.digital())));
            }
            totalW = Math.max(totalW, this.font.width(Long.toString(sum)));
            missingW = Math.max(missingW, this.font.width(Long.toString(shortfall)));
        }
        int needX = right - COUNT_W;
        int missingX = needX - 8 - missingW;
        int totalX = missingX - 8 - totalW;
        int digitalX = totalX - 8 - digitalW;
        int inventoryX = digitalX - 8 - inventoryW;
        int playerX = inventoryX - 8 - COUNT_W;
        int iconX = panelX + 16;
        int nameX = iconX + 22;
        int nameW = Math.max(16, playerX - nameX - 6);
        drawColumn(graphics, Component.translatable("menu.wrights.column_material"), iconX, listY + 2, COUNT_W, false);
        drawColumn(graphics, Component.translatable("menu.wrights.column_player"), playerX, listY + 2, COUNT_W, true);
        drawColumn(graphics, Component.translatable("menu.wrights.column_inventory"), inventoryX, listY + 2, inventoryW, true);
        drawColumn(graphics, Component.translatable("menu.wrights.column_digital"), digitalX, listY + 2, digitalW, true);
        drawColumn(graphics, Component.translatable("menu.wrights.column_total"), totalX, listY + 2, totalW, true);
        drawColumn(graphics, Component.translatable("menu.wrights.column_missing"), missingX, listY + 2, missingW, true);
        drawColumn(graphics, Component.translatable("menu.wrights.column_need"), needX, listY + 2, COUNT_W, true);
        graphics.fill(panelX + 16, listY + HEADER - 2, right, listY + HEADER - 1, 0xFF31404E);
        int rows = Math.max(1, (listH - HEADER) / ROW);
        int bodyY = listY + HEADER;
        graphics.enableScissor(panelX + 12, bodyY, panelX + panelW - 12, listY + listH);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = scroll; i < lines.size() && i - scroll < rows; i++) {
            BlueprintPayloads.MaterialLine line = lines.get(i);
            Item item = BuiltInRegistries.ITEM.byId(line.itemId());
            if (item == Items.AIR) {
                continue;
            }
            int y = bodyY + (i - scroll) * ROW;
            ItemStack stack = new ItemStack(item);
            int need = line.count();
            long supply = supplyBound ? line.supply() : 0L;
            long digital = digitalBound ? line.digital() : 0L;
            long total = line.player() + supply + digital;
            long missing = Math.max(0L, need - total);
            WrightsUi.slot(graphics, iconX, y, 0xFF31404E);
            graphics.renderItem(stack, iconX + 1, y + 1);
            String name = this.font.plainSubstrByWidth(stack.getHoverName().getString(), nameW);
            graphics.drawString(this.font, name, nameX, y + 5, WrightsUi.TEXT, false);
            drawCount(graphics, Integer.toString(line.player()), playerX, y + 5, COUNT_W, stockColor(creative, line.player(), need));
            drawCount(graphics, supplyBound ? Integer.toString(line.supply()) : "-", inventoryX, y + 5, inventoryW,
                    supplyBound ? stockColor(creative, line.supply(), need) : WrightsUi.MUTED);
            drawCount(graphics, digitalBound ? Integer.toString(line.digital()) : "-", digitalX, y + 5, digitalW,
                    digitalBound ? stockColor(creative, line.digital(), need) : WrightsUi.MUTED);
            drawCount(graphics, Long.toString(total), totalX, y + 5, totalW, stockColor(creative, total, need));
            drawCount(graphics, Long.toString(missing), missingX, y + 5, missingW, missingColor(creative, missing));
            drawCount(graphics, Integer.toString(need), needX, y + 5, COUNT_W, WrightsUi.TEXT);
            if (mouseX >= iconX && mouseX < right && mouseY >= y && mouseY < y + ROW) {
                hovered = stack;
            }
        }
        graphics.disableScissor();
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    private int stockColor(boolean creative, long held, int need) {
        if (creative) {
            return WrightsUi.BLUE;
        }
        return held >= need ? GREEN : RED;
    }

    private int missingColor(boolean creative, long missing) {
        if (creative) {
            return WrightsUi.BLUE;
        }
        return missing > 0L ? RED : GREEN;
    }

    private void drawColumn(GuiGraphics graphics, Component label, int x, int y, int width, boolean count) {
        String text = label.getString();
        int drawX = count ? x + width - this.font.width(text) : x;
        graphics.drawString(this.font, text, drawX, y, WrightsUi.MUTED, false);
    }

    private void drawCount(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(this.font, text, x + width - this.font.width(text), y, color, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = Math.max(1, (listH - HEADER) / ROW);
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
