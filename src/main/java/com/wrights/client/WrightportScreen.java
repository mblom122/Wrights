package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.drone.HangarOps;
import com.wrights.drone.Swarm;
import com.wrights.network.BlueprintPayloads;

public class WrightportScreen extends AbstractContainerScreen<com.wrights.menu.WrightportMenu> {
    private static final int ROW = 12;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 22;
    private static final int VISIBLE = 8;
    private int selected;
    private int scroll;

    public WrightportScreen(com.wrights.menu.WrightportMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 164;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + 122;
        addRenderableWidget(new WrightsButton(leftPos + 8, y, 32, 16, Component.translatable("screen.wrights.hangar_build"), button -> edit(HangarOps.BUILD, selected)));
        addRenderableWidget(new WrightsButton(leftPos + 42, y, 32, 16, Component.translatable("screen.wrights.hangar_remove"), button -> edit(HangarOps.REMOVE, selected)));
        addRenderableWidget(new WrightsButton(leftPos + 76, y, 32, 16, Component.translatable("screen.wrights.hangar_speed"), button -> edit(HangarOps.SPEED, selected)));
        addRenderableWidget(new WrightsButton(leftPos + 110, y, 28, 16, Component.translatable("screen.wrights.hangar_up"), button -> shift(-1)));
        addRenderableWidget(new WrightsButton(leftPos + 140, y, 28, 16, Component.translatable("screen.wrights.hangar_down"), button -> shift(1)));
    }

    private void shift(int delta) {
        Swarm swarm = swarm();
        if (selected < 0 || selected >= swarm.units().size()) {
            return;
        }
        edit(delta < 0 ? HangarOps.UP : HangarOps.DOWN, selected);
        int next = selected + delta;
        if (next >= 0 && next < swarm.units().size()) {
            selected = next;
        }
    }

    private void edit(int action, int index) {
        PacketDistributor.sendToServer(new BlueprintPayloads.HangarEdit(menu.pos(), action, index));
    }

    private Swarm swarm() {
        return Swarm.from(menu.getSlot(0).getItem());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, WrightsUi.COPPER, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        WrightsUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Swarm swarm = swarm();
        if (selected >= swarm.units().size()) {
            selected = Math.max(0, swarm.units().size() - 1);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, swarm.units().size() - VISIBLE)));
        for (int i = scroll; i < swarm.units().size() && i < scroll + VISIBLE; i++) {
            Swarm.Unit unit = swarm.units().get(i);
            int y = topPos + LIST_Y + (i - scroll) * ROW;
            if (i == selected) {
                graphics.fill(leftPos + LIST_X - 2, y - 1, leftPos + 168, y + 11, 0x553C2A1A);
            }
            int speed = com.wrights.drone.WrightTier.clamp(unit.speed());
            String duty = Component.translatable("drone.wrights.speed." + speed).getString();
            String line = (i + 1) + "  " + duty;
            graphics.drawString(this.font, line, leftPos + LIST_X, y + 1, WrightsUi.TEXT, false);
        }
        if (swarm.units().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.hangar_empty"), leftPos + LIST_X, topPos + LIST_Y + 2, WrightsUi.MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.wrights.wrightport_hint"), leftPos + 8, topPos + 146, WrightsUi.MUTED, false);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Swarm swarm = swarm();
        int row = (int) ((mouseY - topPos - LIST_Y) / ROW);
        int index = row + scroll;
        if (mouseX >= leftPos + LIST_X && mouseX <= leftPos + 168 && row >= 0 && row < VISIBLE && index >= 0 && index < swarm.units().size()) {
            selected = index;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, swarm().units().size() - VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }
}
