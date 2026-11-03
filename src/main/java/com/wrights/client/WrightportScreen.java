package com.wrights.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
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
    private WrightsButton highlightChestButton;
    private EditBox nameBox;
    private String sentName = "";
    private boolean energyKnown;
    private int energyStored;
    private int energyCapacity;

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
                if (!controllerHeld()) {
                    getMinecraft().player.displayClientMessage(
                            Component.translatable("message.wrights.need_controller"), true);
                    return;
                }
                PacketDistributor.sendToServer(new BlueprintPayloads.BindPortChest(menu.pos(), net.minecraft.core.BlockPos.ZERO, true));
                return;
            }
            // Close the port UI and put the held controller into Bind Chest aim for this wrightport.
            if (WrightsClientState.beginPortChest(menu.pos())) {
                getMinecraft().setScreen(null);
            }
        });
        addRenderableWidget(chestButton);
        highlightChestButton = new WrightsButton(0, 0, 88, 16, Component.translatable("screen.wrights.supply_highlight"), button -> {
            BlockPos chest = boundChest();
            if (chest != null) {
                WrightsClientState.highlightPortChest(chest);
                getMinecraft().setScreen(null);
            }
        });
        addRenderableWidget(highlightChestButton);
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
        int chestReserve = 0;
        if (chestButton != null && chestButton.visible) {
            chestReserve = chestButton.getWidth() + 8;
            if (!controllerHeld()) {
                chestReserve += this.font.width(Component.translatable("screen.wrights.port_chest_need_controller")) + 8;
            }
        }
        int limit = imageWidth - 24 - chestReserve;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(owner.getString(), Math.max(24, limit)), leftPos + 12, topPos + 28, WrightsUi.MUTED, false);
    }

    private BlockPos boundChest() {
        if (getMinecraft().level == null
                || !(getMinecraft().level.getBlockEntity(menu.pos()) instanceof WrightportBlockEntity port)) {
            return null;
        }
        return port.supplyChest();
    }

    private void placeBoundary() {
        if (boundaryButton == null) {
            return;
        }
        boundaryButton.setX(leftPos + imageWidth - 16 - boundaryButton.getWidth());
        boundaryButton.setY(topPos + 8);
        // Same choice on/off language as Bind Chest (teal when active feature).
        boundaryButton.setPicked(WrightportBounds.showing(menu.pos()));
        if (chestButton != null) {
            boolean mine = canRename();
            boolean held = controllerHeld();
            boolean bound = boundChest() != null;
            chestButton.setShown(mine);
            // setShown forces active; Bind Chest stays disabled until a controller is held.
            chestButton.active = mine && held;
            chestButton.setX(boundaryButton.getX());
            chestButton.setY(boundaryButton.getY() + 18);
            // Off = copper idle (no chest); on = teal (chest bound) — same as Boundary.
            chestButton.setPicked(bound);
            if (!chestButton.active) {
                chestButton.setFocused(false);
            }
            if (highlightChestButton != null) {
                highlightChestButton.setShown(mine && bound);
                highlightChestButton.active = mine && bound;
                highlightChestButton.setX(chestButton.getX());
                highlightChestButton.setY(chestButton.getY() + chestButton.getHeight() + 3 + 9 + 4);
                highlightChestButton.setPicked(WrightsClientState.portChestHighlighting()
                        && boundChest() != null
                        && boundChest().equals(WrightsClientState.portChestHighlightPos));
            }
        } else if (highlightChestButton != null) {
            highlightChestButton.setShown(false);
        }
    }

    private void drawChestNeedController(GuiGraphics graphics) {
        if (chestButton == null || !chestButton.visible || controllerHeld()) {
            return;
        }
        Component tip = Component.translatable("screen.wrights.port_chest_need_controller");
        int maxW = Math.max(24, chestButton.getX() - (leftPos + 12) - 6);
        String text = this.font.plainSubstrByWidth(tip.getString(), maxW);
        int x = chestButton.getX() - 6 - this.font.width(text);
        int y = chestButton.getY() + (chestButton.getHeight() - 8) / 2;
        graphics.drawString(this.font, text, x, y, WrightsUi.RED, false);
    }

    private void drawChestCoords(GuiGraphics graphics) {
        BlockPos chest = boundChest();
        if (chest == null || chestButton == null || !chestButton.visible) {
            return;
        }
        Component line = Component.translatable("screen.wrights.supply_at", chest.getX(), chest.getY(), chest.getZ());
        String text = this.font.plainSubstrByWidth(line.getString(), chestButton.getWidth() + 24);
        int x = chestButton.getX() + chestButton.getWidth() - this.font.width(text);
        int y = chestButton.getY() + chestButton.getHeight() + 3;
        graphics.drawString(this.font, text, x, y, WrightsUi.MUTED, false);
    }

    /** Bottom of Boundary / Bind Chest / coords / Highlight stack — energy sits under this. */
    private int headerStackBottom() {
        int y = topPos + 8 + 16;
        if (canRename()) {
            y = topPos + 8 + 18 + 16;
            if (boundChest() != null) {
                y += 3 + 9 + 4 + 16;
            }
        }
        return y;
    }

    private int energyBarY() {
        return headerStackBottom() + 6;
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
        return Math.max(topPos + 60, energyBarY() + 16);
    }

    @Override
    public int areaW() {
        return Math.max(160, imageWidth - 8);
    }

    @Override
    public int areaH() {
        return Math.max(100, topPos + imageHeight - 8 - areaY());
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
        WrightsUi.energyBar(graphics, this.font, leftPos + 12, energyBarY(), Math.max(40, imageWidth - 24), stored, capacity);
    }

    @Override
    public boolean toolHeld() {
        // Hangar edits stay available from the block; Chest bind still needs a controller.
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
        placeBoundary();
        placeName();
        drawOwner(graphics);
        drawEnergy(graphics);
        drawChestNeedController(graphics);
        drawChestCoords(graphics);
        if (canRename() && !portHasSupply()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("message.wrights.port_need_supply"),
                    leftPos + 12,
                    topPos + imageHeight - 14,
                    WrightsUi.RED,
                    false);
        }
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
