package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.wrights.WrightsClientConfig;

/** Drag the Ctrl/Shift mode menu, or snap it to a side. */
public class ModePlaceScreen extends Screen {
    private int dragging;
    private int grabX;
    private int grabY;
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    public ModePlaceScreen() {
        super(Component.translatable("screen.wrights.mode_place_title"));
    }

    @Override
    protected void init() {
        int x = 8;
        int y = 8;
        x = addSpot(x, y, "screen.wrights.hud_top", HudAnchor.TOP);
        x = addSpot(x, y, "screen.wrights.hud_bottom", HudAnchor.BOTTOM);
        x = addSpot(x, y, "screen.wrights.hud_left", HudAnchor.LEFT);
        x = addSpot(x, y, "screen.wrights.hud_right", HudAnchor.RIGHT);
        addSpot(x, y, "screen.wrights.hud_center", HudAnchor.CENTER);
        int doneW = this.font.width(Component.translatable("screen.wrights.hud_done")) + 16;
        addRenderableWidget(new WrightsButton(this.width - doneW - 8, 8, doneW, 18, Component.translatable("screen.wrights.hud_done"), button -> onClose()));
    }

    private int addSpot(int x, int y, String key, int spot) {
        Component label = Component.translatable(key);
        int width = this.font.width(label) + 14;
        addRenderableWidget(new WrightsButton(x, y, width, 18, label, button -> {
            dragging = 0;
            WrightsClientConfig.setHudModeSpot(spot);
        }));
        return x + width + 4;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88060C14);
        int barW = Math.min(182, this.width - 16);
        int barX = (this.width - barW) / 2;
        int barY = this.height - 22;
        graphics.fill(barX, barY, barX + barW, barY + 20, 0xCC101820);
        WrightsUi.border(graphics, barX, barY, barW, 20, 0xFF3D4E5E);
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.hud_hotbar"), this.width / 2, barY + 6, WrightsUi.MUTED);
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.hud_action"), this.width / 2, this.height - 68, WrightsUi.MUTED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        place();
        WrightsHud.drawSampleModeMenu(graphics, this.minecraft, boxX, boxY, boxW, boxH);
        WrightsUi.border(graphics, boxX - 2, boxY - 2, boxW + 4, boxH + 4, WrightsUi.COPPER);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.mode_place_moving"), 8, 30, WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.mode_place_hint"), 8, 42, WrightsUi.MUTED, false);
    }

    private void place() {
        boxW = WrightsHud.sampleModeMenuWidth(this.minecraft);
        boxH = WrightsHud.sampleModeMenuHeight(this.minecraft);
        boxX = HudAnchor.modeX(this.width, boxW);
        boxY = HudAnchor.modeY(this.height, boxH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        place();
        if (inside(mouseX, mouseY, boxX, boxY, boxW, boxH)) {
            dragging = 1;
            grabX = (int) mouseX - boxX;
            grabY = (int) mouseY - boxY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging != 0) {
            int left = (int) mouseX - grabX;
            int top = (int) mouseY - grabY;
            WrightsClientConfig.setHudModeCustom(
                    HudAnchor.fraction(left + boxW / 2, this.width),
                    HudAnchor.fraction(top + boxH / 2, this.height));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging != 0) {
            dragging = 0;
            WrightsClientConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        WrightsClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new WrightsMenuScreen());
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
