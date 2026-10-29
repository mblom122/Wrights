package com.autobots.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import com.autobots.blueprint.BlueprintTransforms;
import com.autobots.network.BlueprintPayloads;

public class BlueprintEditScreen extends Screen {
    private final StructureGrid grid = new StructureGrid();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int modelX;
    private int modelY;
    private int modelSize;
    private int layer;

    public BlueprintEditScreen() {
        super(Component.translatable("screen.autobots.adjust_edit"));
        layer = AutobotsClientState.editPivotY;
    }

    @Override
    protected void init() {
        panelW = Math.min(430, this.width - 16);
        panelH = Math.min(268, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        modelSize = Math.min(176, panelH - 56);
        modelX = panelX + 16;
        modelY = panelY + 36;
        int x = panelX + modelSize + 36;
        int y = panelY + 36;
        int width = panelW - (x - panelX) - 14;
        add(x, y, width, "screen.autobots.rotate", BlueprintTransforms.Op.ROTATE);
        add(x, y + 20, width, "screen.autobots.rotate_ccw", BlueprintTransforms.Op.ROTATE_CCW);
        add(x, y + 44, width, "screen.autobots.flip_h", BlueprintTransforms.Op.FLIP_X);
        add(x, y + 64, width, "screen.autobots.flip_v", BlueprintTransforms.Op.FLIP_Y);
        add(x, y + 84, width, "screen.autobots.flip_d", BlueprintTransforms.Op.FLIP_Z);
        add(x, y + 108, width, "screen.autobots.center", BlueprintTransforms.Op.CENTER);
        add(x, y + 128, width, "screen.autobots.ground", BlueprintTransforms.Op.GROUND);
        addRenderableWidget(new AutobotsButton(x, panelY + panelH - 66, width, 16, Component.translatable("menu.autobots.materials"), button -> MaterialsScreen.request(AutobotsClientState.editId, 0, 0, 0, this)));
        addRenderableWidget(new AutobotsButton(x, panelY + panelH - 46, width, 16, Component.translatable("screen.autobots.recapture"), button -> recapture()));
        addRenderableWidget(new AutobotsButton(x, panelY + panelH - 26, width, 16, Component.translatable("menu.autobots.close"), button -> onClose()));
        addRenderableWidget(new AutobotsButton(modelX, modelY + modelSize + 4, 18, 16, Component.literal("-"), button -> changeLayer(-1)));
        addRenderableWidget(new AutobotsButton(modelX + modelSize - 18, modelY + modelSize + 4, 18, 16, Component.literal("+"), button -> changeLayer(1)));
    }

    public void refresh() {
        layer = Mth.clamp(layer, 0, Math.max(0, AutobotsClientState.editY - 1));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        AutobotsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, Component.translatable("screen.autobots.adjust_edit"), panelX + 16, panelY + 10, AutobotsUi.COPPER, false);
        String name = AutobotsClientState.editName == null ? "" : AutobotsClientState.editName;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(name, panelW - 140), panelX + 120, panelY + 10, AutobotsUi.TEXT, false);
        grid.layout(modelX + 12, modelY + 12, Math.max(32, modelSize - 24), AutobotsClientState.editX, AutobotsClientState.editZ);
        grid.render(graphics, AutobotsClientState.editGhosts, AutobotsClientState.editX, AutobotsClientState.editZ, layer, AutobotsClientState.editPivotX, AutobotsClientState.editPivotY, AutobotsClientState.editPivotZ);
        String layerLabel = Component.translatable("screen.autobots.layer", layer + 1, AutobotsClientState.editY).getString();
        graphics.drawCenteredString(this.font, layerLabel, modelX + modelSize / 2, modelY + modelSize + 8, AutobotsUi.MUTED);
        graphics.drawString(this.font, Component.translatable("screen.autobots.edit_hint"), panelX + 16, panelY + panelH - 16, AutobotsUi.MUTED, false);
        Component anchor = Component.translatable("screen.autobots.anchor", AutobotsClientState.editPivotX, AutobotsClientState.editPivotY, AutobotsClientState.editPivotZ);
        int textX = panelX + modelSize + 36;
        graphics.drawString(this.font, Component.translatable("menu.autobots.blocks", com.autobots.blueprint.BoxEdits.volume(AutobotsClientState.editX, AutobotsClientState.editY, AutobotsClientState.editZ), AutobotsClientState.editBlocks), textX, panelY + panelH - 92, AutobotsUi.MUTED, false);
        graphics.drawString(this.font, anchor, textX, panelY + panelH - 78, AutobotsUi.BLUE, false);
        String block = anchorBlock();
        if (!block.isEmpty()) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(block, panelW - (textX - panelX) - 16), textX, panelY + panelH - 66, AutobotsUi.TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && AutobotsClientState.editId != null) {
            int[] hit = grid.hit(mouseX, mouseY, AutobotsClientState.editX, AutobotsClientState.editZ, layer);
            if (hit != null) {
                PacketDistributor.sendToServer(new BlueprintPayloads.SetAnchor(AutobotsClientState.editId, hit[0], hit[1], hit[2]));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= modelX && mouseX < modelX + modelSize && mouseY >= modelY && mouseY < modelY + modelSize) {
            changeLayer(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void add(int x, int y, int width, String key, BlueprintTransforms.Op operation) {
        addRenderableWidget(new AutobotsButton(x, y, width, 16, Component.translatable(key), button -> transform(operation)));
    }

    private void transform(BlueprintTransforms.Op operation) {
        if (AutobotsClientState.editId == null) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.TransformBlueprint(AutobotsClientState.editId, operation.ordinal()));
    }

    private void recapture() {
        if (AutobotsClientState.editId == null) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.RecaptureBlueprint(AutobotsClientState.editId));
    }

    private void changeLayer(int delta) {
        layer = Mth.clamp(layer + delta, 0, Math.max(0, AutobotsClientState.editY - 1));
    }

    private String anchorBlock() {
        for (AutobotsClientState.Ghost ghost : AutobotsClientState.editGhosts) {
            if (ghost.x() == AutobotsClientState.editPivotX && ghost.y() == AutobotsClientState.editPivotY && ghost.z() == AutobotsClientState.editPivotZ) {
                return Block.stateById(ghost.stateId()).getBlock().getName().getString();
            }
        }
        return "";
    }
}
