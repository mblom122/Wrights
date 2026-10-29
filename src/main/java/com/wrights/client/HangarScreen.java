package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.drone.HangarOps;
import com.wrights.drone.Swarm;
import com.wrights.network.BlueprintPayloads;

public class HangarScreen extends AbstractContainerScreen<com.wrights.menu.HangarMenu> {
    private static final int ROW = 12;
    private static final int LIST_X = 80;
    private static final int LIST_Y = 18;
    private int selected;

    public HangarScreen(com.wrights.menu.HangarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 240;
        this.titleLabelY = 6;
        this.inventoryLabelY = 147;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + 116;
        addRenderableWidget(new WrightsButton(leftPos + 8, y, 32, 16, Component.translatable("screen.wrights.hangar_build"), button -> edit(HangarOps.BUILD, selected)));
        addRenderableWidget(new WrightsButton(leftPos + 42, y, 32, 16, Component.translatable("screen.wrights.hangar_remove"), button -> edit(HangarOps.REMOVE, selected)));
        addRenderableWidget(new WrightsButton(leftPos + 76, y, 32, 16, Component.translatable("screen.wrights.hangar_speed"), button -> edit(HangarOps.SPEED, selected)));
        addRenderableWidget(new WrightsButton(leftPos + 110, y, 28, 16, Component.translatable("screen.wrights.hangar_up"), button -> shift(-1)));
        addRenderableWidget(new WrightsButton(leftPos + 140, y, 28, 16, Component.translatable("screen.wrights.hangar_down"), button -> shift(1)));
        addRenderableWidget(new WrightsButton(leftPos + 8, topPos + 134, 16, 14, Component.literal("-"), button -> assign(-1)));
        addRenderableWidget(new WrightsButton(leftPos + 152, topPos + 134, 16, 14, Component.literal("+"), button -> assign(1)));
    }

    private void edit(int action, int index) {
        PacketDistributor.sendToServer(new BlueprintPayloads.HangarEdit(menu.pos(), action, index));
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

    private void assign(int delta) {
        Swarm swarm = swarm();
        if (swarm.units().isEmpty()) {
            return;
        }
        edit(HangarOps.ASSIGN, swarm.assignCount() + delta);
    }

    private Swarm swarm() {
        ItemStack stack = menu.getSlot(0).getItem();
        return Swarm.from(stack);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.wrightport_dock"), 8, 18, WrightsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.wrightport_worn"), 46, 18, WrightsUi.MUTED, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        WrightsUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            int edge = slot.index <= 1 ? WrightsUi.COPPER : 0xFFC5D2DE;
            WrightsUi.slot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, edge);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Swarm swarm = swarm();
        if (selected >= swarm.units().size()) {
            selected = Math.max(0, swarm.units().size() - 1);
        }
        for (int i = 0; i < swarm.units().size() && i < Swarm.MAX; i++) {
            Swarm.Unit unit = swarm.units().get(i);
            int y = topPos + LIST_Y + i * ROW;
            if (i == selected) {
                graphics.fill(leftPos + LIST_X - 2, y - 1, leftPos + 168, y + 11, 0x553C2A1A);
            }
            int speed = Math.max(1, Math.min(Swarm.MAX_SPEED, unit.speed()));
            String duty = i < swarm.assignCount()
                    ? Component.translatable("screen.wrights.hangar_duty").getString()
                    : Component.translatable("screen.wrights.hangar_standby").getString();
            String line = (i + 1) + "  " + Component.translatable("drone.wrights.speed." + speed).getString() + "  " + duty;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line, 84), leftPos + LIST_X, y + 1, i < swarm.assignCount() ? WrightsUi.COPPER : WrightsUi.MUTED, false);
        }
        if (swarm.units().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.hangar_empty"), leftPos + LIST_X, topPos + LIST_Y + 2, WrightsUi.MUTED, false);
        }
        String send = swarm.units().isEmpty()
                ? Component.translatable("screen.wrights.hangar_none").getString()
                : Component.translatable("screen.wrights.hangar_send", swarm.assignCount(), swarm.units().size()).getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(send, 120), leftPos + 28, topPos + 136, WrightsUi.BLUE, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.hangar_power"), leftPos + 8, topPos + 149, WrightsUi.MUTED, false);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Swarm swarm = swarm();
        int row = (int) ((mouseY - topPos - LIST_Y) / ROW);
        if (mouseX >= leftPos + LIST_X && mouseX <= leftPos + 168 && row >= 0 && row < swarm.units().size()) {
            selected = row;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
