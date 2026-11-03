package com.wrights.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.Swarm;
import com.wrights.menu.WrightportMenu;
import com.wrights.network.BlueprintPayloads;

/** Placed wrightport hangar — textured panel with Boundary / Bind / Highlight icons. */
public class WrightportScreen extends AbstractContainerScreen<WrightportMenu> implements ManageHost {
    private final WrightManagePanel manage = new WrightManagePanel();
    private boolean energyKnown;
    private int energyStored;
    private int energyCapacity;

    public WrightportScreen(WrightportMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = HangarGui.WIDTH;
        this.imageHeight = HangarGui.SCREEN_H;
    }

    @Override
    protected void init() {
        this.imageWidth = HangarGui.WIDTH;
        this.imageHeight = HangarGui.SCREEN_H;
        super.init();
        // Sit a bit higher than dead-center so hangar + inventory clear the hotbar area.
        this.topPos = Math.max(4, (this.height - this.imageHeight) / 3);
        if (WrightsClientState.dockWaiting()) {
            if (getMinecraft().player != null) {
                getMinecraft().player.displayClientMessage(Component.translatable("message.wrights.dock_wait_busy"), true);
            }
            onClose();
            return;
        }
        manage.setActive(true);
        clearWidgets();
        manage.addWidgets(this);
    }

    private boolean canRename() {
        if (getMinecraft().player == null || getMinecraft().level == null) {
            return false;
        }
        if (!(getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return false;
        }
        return port.owner() == null || port.owner().equals(getMinecraft().player.getUUID());
    }

    private BlockPos boundChest() {
        if (getMinecraft().level == null
                || !(getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return null;
        }
        return port.supplyChest();
    }

    private boolean controllerHeld() {
        return getMinecraft().player != null && com.wrights.item.ControllerItem.isHeld(getMinecraft().player);
    }

    private boolean portHasSupply() {
        if (getMinecraft().level == null
                || !(getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return false;
        }
        if (port.supplyChest() != null) {
            return true;
        }
        return WrightsClientState.digitalBound;
    }

    private void drawPortButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean mine = canRename();
        boolean held = controllerHeld();
        boolean bound = boundChest() != null;
        boolean boundaryOn = WrightportBounds.showing(menu.pos());
        boolean highlightOn = WrightsClientState.portChestHighlighting()
                && boundChest() != null
                && boundChest().equals(WrightsClientState.portChestHighlightPos);

        boolean[] enabled = {
                true,
                mine && held,
                mine && bound
        };
        boolean[] active = {
                boundaryOn,
                bound,
                highlightOn
        };
        for (int i = 0; i < 3; i++) {
            int x = HangarGui.buttonX(leftPos);
            int y = HangarGui.buttonY(topPos, i);
            boolean hover = mouseX >= x && mouseX < x + HangarGui.BTN_SLOT
                    && mouseY >= y && mouseY < y + HangarGui.BTN_SLOT;
            HangarGui.blitButton(graphics, leftPos, topPos, i, HangarGui.buttonState(enabled[i], active[i], hover && enabled[i]));
        }
    }

    private boolean clickPortButton(double mouseX, double mouseY) {
        int hit = HangarGui.buttonHit(leftPos, topPos, mouseX, mouseY);
        if (hit < 0) {
            return false;
        }
        boolean mine = canRename();
        if (hit == HangarGui.BTN_BOUNDARY) {
            if (getMinecraft().level != null) {
                WrightportBounds.toggle(getMinecraft().level.dimension(), menu.pos());
            }
            return true;
        }
        if (hit == HangarGui.BTN_BIND) {
            if (!mine) {
                return true;
            }
            if (getMinecraft().player != null && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                if (!controllerHeld()) {
                    getMinecraft().player.displayClientMessage(
                            Component.translatable("message.wrights.need_controller"), true);
                    return true;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.BindPortChest(menu.pos(), BlockPos.ZERO, true));
                return true;
            }
            if (WrightsClientState.beginPortChest(menu.pos())) {
                getMinecraft().setScreen(null);
            }
            return true;
        }
        if (hit == HangarGui.BTN_HIGHLIGHT) {
            BlockPos chest = boundChest();
            if (mine && chest != null) {
                WrightsClientState.highlightPortChest(chest);
                getMinecraft().setScreen(null);
            }
            return true;
        }
        return true;
    }

    /** Port-tool tip for the chrome strip (overrides hangar tip while hovering). */
    private Component portButtonTip(int mouseX, int mouseY) {
        int hit = HangarGui.buttonHit(leftPos, topPos, mouseX, mouseY);
        if (hit < 0) {
            return null;
        }
        return switch (hit) {
            case HangarGui.BTN_BOUNDARY -> Component.translatable("screen.wrights.boundary");
            case HangarGui.BTN_BIND -> !controllerHeld() && canRename()
                    ? Component.translatable("screen.wrights.port_chest_need_controller")
                    : Component.translatable("screen.wrights.port_chest");
            case HangarGui.BTN_HIGHLIGHT -> Component.translatable("screen.wrights.supply_highlight");
            default -> null;
        };
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
        PacketDistributor.sendToServer(new BlueprintPayloads.HangarEdit(menu.pos(), action, index));
    }

    @Override
    public void rename(int unit, String name) {
        PacketDistributor.sendToServer(new BlueprintPayloads.RenameWright(0, "", menu.pos(), 0, 0, unit, name));
    }

    @Override
    public void batch(int action, int value, int[] units) {
        PacketDistributor.sendToServer(new BlueprintPayloads.HangarBatch(menu.pos(), action, value, units));
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

    public void acceptEnergy(BlockPos pos, int stored, int capacity) {
        if (pos != null && pos.equals(menu.pos())) {
            energyKnown = true;
            energyStored = Math.max(0, stored);
            energyCapacity = Math.max(0, capacity);
        }
    }

    private void drawEnergy(GuiGraphics graphics) {
        int stored = energyStored;
        int capacity = energyCapacity;
        if (!energyKnown && getMinecraft().level != null
                && getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port) {
            stored = port.energyStored();
            capacity = port.energyCapacity();
        }
        HangarGui.drawEnergyGauge(graphics, leftPos, topPos, stored, capacity);
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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderBg(graphics, partialTick, mouseX, mouseY);
        manage.render(this, graphics, mouseX, mouseY);
        drawEnergy(graphics);
        drawPortButtons(graphics, mouseX, mouseY);
        Component portTip = portButtonTip(mouseX, mouseY);
        if (portTip != null) {
            HangarGui.drawTipStrip(graphics, this.font, leftPos, topPos, portTip);
        } else if (canRename() && !portHasSupply()) {
            HangarGui.drawTipStrip(graphics, this.font, leftPos, topPos,
                    Component.translatable("message.wrights.port_need_supply").withStyle(net.minecraft.ChatFormatting.RED));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0.0F);
        this.hoveredSlot = null;
        for (var slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            this.renderSlot(graphics, slot);
            if (this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                this.hoveredSlot = slot;
                AbstractContainerScreen.renderSlotHighlight(graphics, slot.x, slot.y, 0);
            }
        }
        graphics.pose().popPose();
        for (Renderable widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        var carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 232.0F);
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickPortButton(mouseX, mouseY)) {
            return true;
        }
        if (manage.mouseClicked(this, mouseX, mouseY, button)) {
            return true;
        }
        for (GuiEventListener child : this.children()) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                setFocused(child);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (manage.mouseScrolled(this, mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
