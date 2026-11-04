package com.wrights.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.drone.Swarm;
import com.wrights.energy.PortEnergy;
import com.wrights.menu.PersonalHangarMenu;
import com.wrights.network.BlueprintPayloads;

/** Hangar UI for a carried personal wrightport (no placed-port tools). */
public class PersonalHangarScreen extends AbstractContainerScreen<PersonalHangarMenu> implements ManageHost, JeiPreview {
    private final WrightManagePanel manage = new WrightManagePanel();

    public PersonalHangarScreen(PersonalHangarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = HangarGui.WIDTH;
        this.imageHeight = HangarGui.SCREEN_H;
    }

    @Override
    protected void init() {
        this.imageWidth = HangarGui.WIDTH;
        this.imageHeight = HangarGui.SCREEN_H;
        super.init();
        this.leftPos = JeiPreview.left(this.width, this.imageWidth);
        this.topPos = Math.max(4, (this.height - this.imageHeight) / 3);
        manage.setActive(true);
        clearWidgets();
        manage.addWidgets(this);
        PacketDistributor.sendToServer(new BlueprintPayloads.PortHold(menu.kind(), menu.index(), true));
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new BlueprintPayloads.PortHold(menu.kind(), menu.index(), false));
        super.onClose();
    }

    @Override
    public void addButton(WrightsButton button) {
        addRenderableWidget(button);
    }

    @Override
    public void addField(EditBox box) {
        addRenderableWidget(box);
    }

    @Override
    public void focus(GuiEventListener listener) {
        setFocused(listener);
    }

    @Override
    public void edit(int action, int index) {
        PacketDistributor.sendToServer(new BlueprintPayloads.PortEdit(menu.kind(), menu.index(), action, index));
    }

    @Override
    public void rename(int unit, String name) {
        PacketDistributor.sendToServer(new BlueprintPayloads.RenameWright(
                1, "", net.minecraft.core.BlockPos.ZERO, menu.kind(), menu.index(), unit, name));
    }

    @Override
    public void batch(int action, int value, int[] units) {
        PacketDistributor.sendToServer(new BlueprintPayloads.PortBatch(menu.kind(), menu.index(), action, value, units));
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public int screenWidth() {
        return this.width;
    }

    @Override
    public int screenHeight() {
        return this.height;
    }

    @Override
    public Minecraft minecraft() {
        return getMinecraft();
    }

    @Override
    public Swarm swarm() {
        return Swarm.from(menu.getSlot(0).getItem());
    }

    @Override
    public boolean away(UUID id) {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && job.crew().contains(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void relayout() {
        manage.layoutChrome(this);
    }

    @Override
    public boolean embedded() {
        return true;
    }

    @Override
    public int areaX() {
        return leftPos;
    }

    @Override
    public int areaY() {
        return topPos;
    }

    @Override
    public int areaW() {
        return HangarGui.WIDTH;
    }

    @Override
    public int areaH() {
        return HangarGui.PANEL_H;
    }

    @Override
    public int jeiLeft() {
        return leftPos;
    }

    @Override
    public int jeiTop() {
        return topPos;
    }

    @Override
    public int jeiWidth() {
        return imageWidth;
    }

    @Override
    public int jeiHeight() {
        return imageHeight;
    }

    @Override
    public int jeiScreenWidth() {
        return width;
    }

    @Override
    public int jeiScreenHeight() {
        return height;
    }

    @Override
    public boolean toolHeld() {
        return true;
    }

    @Override
    public boolean requireTool() {
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        HangarGui.blitPanel(graphics, leftPos, topPos);
        HangarGui.drawInventoryPanel(graphics, leftPos, topPos);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (WrightsGuiScale.active()) {
            graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        }
        int localX = WrightsGuiScale.localX(mouseX, this.width);
        int localY = WrightsGuiScale.localY(mouseY, this.height);
        WrightsGuiScale.begin(graphics, this.width, this.height);
        try {
            this.renderBackground(graphics, localX, localY, partialTick);
            renderBg(graphics, partialTick, localX, localY);
            manage.render(this, graphics, localX, localY);
            ItemStack port = menu.getSlot(0).getItem();
            HangarGui.drawEnergyGauge(graphics, leftPos, topPos,
                    PortEnergy.itemStored(port), com.wrights.WrightsConfig.portEnergyCapacity());
            boolean modal = manage.dialogOpen();
            this.hoveredSlot = null;
            if (!modal) {
                graphics.pose().pushPose();
                graphics.pose().translate(leftPos, topPos, 0.0F);
                for (var slot : this.menu.slots) {
                    if (!slot.isActive()) {
                        continue;
                    }
                    this.renderSlot(graphics, slot);
                    if (this.isHovering(slot.x, slot.y, 16, 16, localX, localY)) {
                        this.hoveredSlot = slot;
                        AbstractContainerScreen.renderSlotHighlight(graphics, slot.x, slot.y, 0);
                    }
                }
                graphics.pose().popPose();
            }
            for (Renderable widget : this.renderables) {
                widget.render(graphics, localX, localY, partialTick);
            }
            manage.renderOverlay(this, graphics, localX, localY);
            var carried = this.menu.getCarried();
            if (!carried.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 500.0F);
                graphics.renderItem(carried, localX - 8, localY - 8);
                graphics.renderItemDecorations(this.font, carried, localX - 8, localY - 8);
                graphics.pose().popPose();
            }
            this.renderTooltip(graphics, localX, localY);
        } finally {
            WrightsGuiScale.end(graphics);
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (type == ClickType.QUICK_MOVE && slot != null
                && manage.tryQuickFitPart(this, slot.getItem())) {
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = WrightsGuiScale.localX(mouseX, this.width);
        double localY = WrightsGuiScale.localY(mouseY, this.height);
        if (manage.mouseClicked(this, localX, localY, button)) {
            return true;
        }
        for (GuiEventListener child : this.children()) {
            if (child.mouseClicked(localX, localY, button)) {
                setFocused(child);
                return true;
            }
        }
        return super.mouseClicked(localX, localY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(
                WrightsGuiScale.localX(mouseX, this.width),
                WrightsGuiScale.localY(mouseY, this.height),
                button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(
                WrightsGuiScale.localX(mouseX, this.width),
                WrightsGuiScale.localY(mouseY, this.height),
                button,
                WrightsGuiScale.localDelta(dragX),
                WrightsGuiScale.localDelta(dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localX = WrightsGuiScale.localX(mouseX, this.width);
        double localY = WrightsGuiScale.localY(mouseY, this.height);
        if (manage.mouseScrolled(this, localX, localY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(localX, localY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (manage.keyPressed(this, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == 256 && manage.closeTop()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
