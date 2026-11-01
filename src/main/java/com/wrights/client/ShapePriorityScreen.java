package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.wrights.WrightsClientConfig;
import com.wrights.blueprint.ShapeSupply;

/** Shared material-source order for Shape, place, and Options. */
public class ShapePriorityScreen extends Screen {
    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private final List<Hit> hits = new ArrayList<>();

    public ShapePriorityScreen(Screen parent) {
        super(Component.translatable("screen.wrights.supply_priority_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(320, this.width);
        panelH = WrightsUi.fitH(280, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        addRenderableWidget(new WrightsButton(
                panelX + 16,
                panelY + panelH - 36,
                panelW - 32,
                20,
                Component.translatable("screen.wrights.done"),
                button -> onClose()));
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
        hits.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 16, panelY + 10, WrightsUi.COPPER, false);
        int y = panelY + 28;
        int inner = panelW - 32;
        for (var line : this.font.split(Component.translatable("menu.wrights.supply_priority_hint"), inner)) {
            graphics.drawString(this.font, line, panelX + 16, y, WrightsUi.MUTED, false);
            y += 10;
        }
        y += 6;
        List<Integer> order = WrightsClientConfig.shapeSupplyOrder();
        int listBottom = panelY + panelH - 48;
        for (int i = 0; i < order.size(); i++) {
            if (y + 18 > listBottom) {
                break;
            }
            int source = order.get(i);
            boolean hover = mouseX >= panelX + 16 && mouseX < panelX + 16 + inner && mouseY >= y && mouseY < y + 18;
            WrightsUi.choice(graphics, panelX + 16, y, inner, 18, false, hover);
            graphics.drawString(this.font,
                    (i + 1) + ". " + Component.translatable(ShapeSupply.labelKey(source)).getString(),
                    panelX + 22, y + 5, WrightsUi.TEXT, false);
            int btn = 16;
            int right = panelX + 16 + inner - 4;
            right = drawMini(graphics, mouseX, mouseY, right - btn, y + 2, btn, "↑", "up", i, i > 0);
            right = drawMini(graphics, mouseX, mouseY, right - btn - 2, y + 2, btn, "↓", "down", i, i < order.size() - 1);
            drawMini(graphics, mouseX, mouseY, right - btn - 2, y + 2, btn, "x", "remove", i, order.size() > 1);
            y += 20;
        }
        List<Integer> missing = ShapeSupply.missing(order);
        if (!missing.isEmpty() && y + 12 < listBottom) {
            graphics.drawString(this.font, Component.translatable("menu.wrights.shape_priority_add"), panelX + 16, y, WrightsUi.MUTED, false);
            y += 12;
            for (int source : missing) {
                if (y + 16 > listBottom) {
                    break;
                }
                boolean hover = mouseX >= panelX + 16 && mouseX < panelX + 16 + inner && mouseY >= y && mouseY < y + 16;
                WrightsUi.choice(graphics, panelX + 16, y, inner, 16, false, hover);
                graphics.drawString(this.font,
                        "+ " + Component.translatable(ShapeSupply.labelKey(source)).getString(),
                        panelX + 22, y + 4, WrightsUi.COPPER, false);
                hits.add(new Hit(panelX + 16, y, inner, 16, "add", source));
                y += 18;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (mouseX >= hit.x && mouseX < hit.x + hit.w && mouseY >= hit.y && mouseY < hit.y + hit.h) {
                    apply(hit.id, hit.value);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void apply(String id, int value) {
        switch (id) {
            case "up" -> WrightsClientConfig.shapeSupplyMove(value, -1);
            case "down" -> WrightsClientConfig.shapeSupplyMove(value, 1);
            case "remove" -> WrightsClientConfig.shapeSupplyRemove(value);
            case "add" -> WrightsClientConfig.shapeSupplyAdd(value);
            default -> {
            }
        }
    }

    private int drawMini(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String text, String id, int value, boolean on) {
        boolean hover = on && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + w, y + 14, on ? (hover ? 0xFF3A5164 : 0xFF1C2A37) : 0xFF141A22);
        WrightsUi.border(graphics, x, y, w, 14, on ? WrightsUi.COPPER_DIM : 0xFF2A343C);
        graphics.drawCenteredString(this.font, text, x + w / 2, y + 3, on ? WrightsUi.TEXT : WrightsUi.MUTED);
        if (on) {
            hits.add(new Hit(x, y, w, 14, id, value));
        }
        return x;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Hit(int x, int y, int w, int h, String id, int value) {
    }
}
