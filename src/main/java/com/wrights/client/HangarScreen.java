package com.wrights.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.drone.Swarm;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.network.BlueprintPayloads;

public class HangarScreen extends AbstractContainerScreen<com.wrights.menu.HangarMenu> {
    /** Tab that holds the worn wrightport, sticking out of the window's left edge. */
    private static final int EAR_W = 40;
    private static final int EAR_H = 42;
    private static final int EAR_Y = 141;

    private final WrightManagePanel manage = new WrightManagePanel();
    private WrightsButton manageButton;

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
        if (this.leftPos < EAR_W + 4) {
            this.leftPos = EAR_W + 4;
        }
        layoutHangar();
    }

    void layoutHangar() {
        clearWidgets();
        if (manage.isOpen()) {
            manage.addWidgets(this);
            return;
        }
        manageButton = new WrightsButton(leftPos + 8, topPos + 52, 160, 18, Component.translatable("screen.wrights.manage"), button -> manage.toggle(this));
        manageButton.setShown(docked());
        addRenderableWidget(manageButton);
    }

    void addButton(WrightsButton button) {
        addRenderableWidget(button);
    }

    void edit(int action, int index) {
        PacketDistributor.sendToServer(new BlueprintPayloads.HangarEdit(menu.pos(), action, index));
    }

    void batch(int action, int value, int[] units) {
        PacketDistributor.sendToServer(new BlueprintPayloads.HangarBatch(menu.pos(), action, value, units));
    }

    void addField(net.minecraft.client.gui.components.EditBox box) {
        addRenderableWidget(box);
    }

    void focus(net.minecraft.client.gui.components.events.GuiEventListener listener) {
        setFocused(listener);
    }

    net.minecraft.client.gui.Font font() {
        return this.font;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (manage.isOpen() && !docked()) {
            manage.close(this);
        }
        if (manageButton != null) {
            manageButton.setShown(!manage.isOpen() && docked());
        }
    }

    private boolean docked() {
        return !menu.getSlot(0).getItem().isEmpty();
    }

    private Swarm swarm() {
        ItemStack stack = menu.getSlot(0).getItem();
        return Swarm.from(stack);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        super.renderSlotContents(graphics, stack, slot, countString);
        if (stack.isEmpty() || stack.getItem() instanceof PersonalWrightportItem) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xB4000000);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.wrightport_dock"), 8, 18, WrightsUi.MUTED, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int earX = leftPos - EAR_W;
        int earY = topPos + EAR_Y;
        graphics.fill(earX + 4, earY + 5, leftPos, earY + EAR_H + 5, 0x66000000);
        WrightsUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(earX, earY, leftPos + 4, earY + EAR_H, WrightsUi.PANEL);
        graphics.fill(earX, earY, leftPos, earY + 1, 0xFF31404E);
        graphics.fill(earX, earY + EAR_H - 1, leftPos, earY + EAR_H, 0xFF31404E);
        graphics.fill(earX, earY, earX + 1, earY + EAR_H, 0xFF31404E);
        graphics.fill(earX, earY, earX + 3, earY + EAR_H, WrightsUi.COPPER);
        graphics.fill(earX + 3, earY, leftPos, earY + 2, WrightsUi.COPPER_DIM);
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            int edge = slot.index <= 1 ? WrightsUi.COPPER : 0xFFC5D2DE;
            WrightsUi.slot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, edge);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (manage.isOpen()) {
            manage.render(this, graphics, mouseX, mouseY);
            for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!docked()) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.manage_dock"), leftPos + 8, topPos + 56, WrightsUi.MUTED, false);
        } else {
            Swarm swarm = swarm();
            graphics.drawString(this.font, Component.translatable("screen.wrights.manage_ready", swarm.units().size()), leftPos + 80, topPos + 32, WrightsUi.TEXT, false);
        }
        Component worn = Component.translatable("screen.wrights.wrightport_worn");
        graphics.drawString(this.font, worn, leftPos - EAR_W + (EAR_W - this.font.width(worn)) / 2, topPos + EAR_Y + 5, WrightsUi.MUTED, false);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (manage.isOpen()) {
            if (manage.mouseClicked(this, mouseX, mouseY, button)) {
                return true;
            }
            for (GuiEventListener child : this.children()) {
                if (child.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(child);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (manage.isOpen()) {
            for (GuiEventListener child : this.children()) {
                if (child.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (manage.isOpen()) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (manage.isOpen()) {
            return manage.mouseScrolled(this, mouseX, mouseY, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (manage.isOpen() && keyCode == 256) {
            if (manage.closeTop()) {
                return true;
            }
            manage.close(this);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= guiLeft - EAR_W && mouseX < guiLeft && mouseY >= guiTop + EAR_Y && mouseY < guiTop + EAR_Y + EAR_H) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }
}
