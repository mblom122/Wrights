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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.Swarm;
import com.wrights.menu.WrightportMenu;
import com.wrights.network.BlueprintPayloads;

/** Placed wrightport hangar — textured panel with Boundary / Bind / Highlight icons. */
public class WrightportScreen extends AbstractContainerScreen<WrightportMenu> implements ManageHost, JeiPreview {
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
        // Leave a JEI lane on the right; sit a bit higher so hangar + inventory clear the hotbar.
        this.leftPos = JeiPreview.left(this.width, this.imageWidth);
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
        int size = HangarGui.BTN_SLOT;
        for (int i = 0; i < 3; i++) {
            int x = HangarGui.buttonX(leftPos);
            int y = HangarGui.buttonY(topPos, i);
            boolean hover = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
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

    /** Port-tool hover tip (chrome strip). */
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
            drawEnergy(graphics);
            drawPortButtons(graphics, localX, localY);
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
            // Dialog + tip last so hangar/inventory do not show through; tip stays readable.
            manage.renderOverlay(this, graphics, localX, localY);
            var carried = this.menu.getCarried();
            if (!carried.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 500.0F);
                graphics.renderItem(carried, localX - 8, localY - 8);
                graphics.renderItemDecorations(this.font, carried, localX - 8, localY - 8);
                graphics.pose().popPose();
            }
            Component portTip = portButtonTip(localX, localY);
            if (portTip != null) {
                graphics.renderTooltip(this.font, portTip, localX, localY);
            }
            this.renderTooltip(graphics, localX, localY);
        } finally {
            WrightsGuiScale.end(graphics);
        }
        // Screen coords (not localX/Y): JEI lays out beside the hangar in unscaled GUI space.
        HangarScreenEvents.postRender(this, graphics, mouseX, mouseY);
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
        if (button == 0 && clickPortButton(localX, localY)) {
            return true;
        }
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
