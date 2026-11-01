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
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.Swarm;
import com.wrights.menu.WrightportMenu;
import com.wrights.network.BlueprintPayloads;

/** The placed wrightport. Same wright list as Manage, for this block alone. */
public class WrightportScreen extends AbstractContainerScreen<WrightportMenu> implements ManageHost {
    private final WrightManagePanel manage = new WrightManagePanel();
    private WrightsButton boundaryButton;
    private WrightsButton chestButton;
    private EditBox nameBox;
    private String sentName = "";

    public WrightportScreen(WrightportMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 368;
        this.imageHeight = 348;
    }

    @Override
    protected void init() {
        this.imageWidth = WrightsUi.fitW(368, this.width);
        this.imageHeight = WrightsUi.fitH(348, this.height);
        super.init();
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
        boundaryButton = new WrightsButton(0, 0, 88, 16, Component.translatable("screen.wrights.boundary"), button -> {
            if (getMinecraft().level != null) {
                WrightportBounds.toggle(getMinecraft().level.dimension(), menu.pos());
            }
        });
        addRenderableWidget(boundaryButton);
        chestButton = new WrightsButton(0, 0, 88, 16, Component.translatable("screen.wrights.port_chest"), button -> {
            if (getMinecraft().player != null && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                PacketDistributor.sendToServer(new BlueprintPayloads.BindPortChest(menu.pos(), net.minecraft.core.BlockPos.ZERO, true));
                return;
            }
            WrightsClientState.beginPortChest(menu.pos());
            getMinecraft().setScreen(null);
        });
        addRenderableWidget(chestButton);
        nameBox = new EditBox(this.font, 0, 0, 140, 16, Component.translatable("screen.wrights.name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("screen.wrights.name"));
        if (getMinecraft().level != null && getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port) {
            sentName = port.label();
            nameBox.setValue(sentName);
        }
        addRenderableWidget(nameBox);
        placeBoundary();
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

    private void commitName() {
        if (nameBox == null || !canRename() || getMinecraft().level == null) {
            return;
        }
        String next = WrightportBlockEntity.cleanLabel(nameBox.getValue());
        if (next.equals(sentName)) {
            if (!nameBox.getValue().equals(next)) {
                nameBox.setValue(next);
            }
            return;
        }
        sentName = next;
        nameBox.setValue(next);
        PacketDistributor.sendToServer(new BlueprintPayloads.RenameWrightport(
                getMinecraft().level.dimension().location().toString(), menu.pos(), next));
    }

    private void placeName() {
        if (nameBox == null || boundaryButton == null) {
            return;
        }
        boolean mine = canRename();
        nameBox.visible = mine;
        if (!mine) {
            return;
        }
        int right = boundaryButton.getX() - 6;
        nameBox.setX(leftPos + 12);
        nameBox.setY(topPos + 8);
        nameBox.setWidth(Math.max(60, right - nameBox.getX()));
    }

    private void drawOwner(GuiGraphics graphics) {
        if (getMinecraft().level == null || !(getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return;
        }
        if (!canRename()) {
            String title = this.font.plainSubstrByWidth(port.title().getString(), Math.max(40, imageWidth - 120));
            graphics.drawString(this.font, title, leftPos + 12, topPos + 12, WrightsUi.TEXT, false);
        }
        Component owner = port.ownerLabel();
        int limit = imageWidth - 24 - (chestButton != null && chestButton.visible ? chestButton.getWidth() + 8 : 0);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(owner.getString(), Math.max(24, limit)), leftPos + 12, topPos + 28, WrightsUi.MUTED, false);
    }

    private void placeBoundary() {
        if (boundaryButton == null) {
            return;
        }
        boundaryButton.setX(leftPos + imageWidth - 16 - boundaryButton.getWidth());
        boundaryButton.setY(topPos + 8);
        boundaryButton.setPicked(WrightportBounds.showing(menu.pos()));
        if (chestButton != null) {
            boolean mine = canRename();
            chestButton.setShown(mine);
            chestButton.setX(boundaryButton.getX());
            chestButton.setY(boundaryButton.getY() + 18);
            boolean bound = getMinecraft().level != null
                    && getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port
                    && port.supplyChest() != null;
            chestButton.setPicked(bound);
        }
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
        return leftPos + 4;
    }

    @Override
    public int areaY() {
        return topPos + 42;
    }

    @Override
    public int areaW() {
        return Math.max(160, imageWidth - 8);
    }

    @Override
    public int areaH() {
        return Math.max(120, imageHeight - 46);
    }

    @Override
    public boolean toolHeld() {
        // Block UI is opened from the wrightport itself — no controller gate.
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
        WrightsUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderBg(graphics, partialTick, mouseX, mouseY);
        manage.render(this, graphics, mouseX, mouseY);
        drawOwner(graphics);
        placeBoundary();
        placeName();
        for (Renderable widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        manage.drawDigitalTip(this, graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (nameBox != null && nameBox.isFocused() && !nameBox.isMouseOver(mouseX, mouseY)) {
            commitName();
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
        if (mouseX >= leftPos && mouseX < leftPos + imageWidth && mouseY >= topPos && mouseY < topPos + imageHeight) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (GuiEventListener child : this.children()) {
            if (child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return manage.mouseScrolled(this, mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (manage.keyPressed(this, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (nameBox != null && nameBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                commitName();
                setFocused(null);
                nameBox.setFocused(false);
                return true;
            }
            if (keyCode == 256) {
                nameBox.setValue(sentName);
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            // Letters arrive in charTyped. Swallow the key so E does not close this screen.
            nameBox.keyPressed(keyCode, scanCode, modifiers);
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

    @Override
    public void removed() {
        commitName();
        super.removed();
    }
}
