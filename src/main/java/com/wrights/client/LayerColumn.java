package com.wrights.client;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

/**
 * Left column shared by the build editor and the blueprint editor: layer grid,
 * selection, anchor, and layer operations.
 */
public final class LayerColumn {
    public interface View {
        UUID id();

        List<WrightsClientState.Ghost> ghosts();

        int sizeX();

        int sizeY();

        int sizeZ();

        int pivotX();

        int pivotY();

        int pivotZ();

        boolean concealed();
    }

    @FunctionalInterface
    public interface Add {
        void widget(AbstractWidget widget);
    }

    public static final View BUILD = new View() {
        @Override
        public UUID id() {
            return WrightsClientState.buildId;
        }

        @Override
        public List<WrightsClientState.Ghost> ghosts() {
            return WrightsClientState.ghosts;
        }

        @Override
        public int sizeX() {
            return WrightsClientState.fullX;
        }

        @Override
        public int sizeY() {
            return WrightsClientState.fullY;
        }

        @Override
        public int sizeZ() {
            return WrightsClientState.fullZ;
        }

        @Override
        public int pivotX() {
            return WrightsClientState.pivotX;
        }

        @Override
        public int pivotY() {
            return WrightsClientState.pivotY;
        }

        @Override
        public int pivotZ() {
            return WrightsClientState.pivotZ;
        }

        @Override
        public boolean concealed() {
            return WrightsClientState.concealed;
        }
    };

    public static final View EDIT = new View() {
        @Override
        public UUID id() {
            return WrightsClientState.editId;
        }

        @Override
        public List<WrightsClientState.Ghost> ghosts() {
            return WrightsClientState.editGhosts;
        }

        @Override
        public int sizeX() {
            return WrightsClientState.editX;
        }

        @Override
        public int sizeY() {
            return WrightsClientState.editY;
        }

        @Override
        public int sizeZ() {
            return WrightsClientState.editZ;
        }

        @Override
        public int pivotX() {
            return WrightsClientState.editPivotX;
        }

        @Override
        public int pivotY() {
            return WrightsClientState.editPivotY;
        }

        @Override
        public int pivotZ() {
            return WrightsClientState.editPivotZ;
        }

        @Override
        public boolean concealed() {
            return WrightsClientState.previewConcealed;
        }
    };

    private final StructureGrid grid = new StructureGrid();
    private final View view;
    private Screen screen;
    private Font font;
    private Consumer<GuiEventListener> focus;
    private int modelX;
    private int modelY;
    private int gridY;
    private int modelSize;
    private int layer;
    private int layerY;
    private int selectionY;
    private int layersY;
    private int selX = -1;
    private int selY = -1;
    private int selZ = -1;
    private WrightsButton anchorButton;
    private WrightsButton removeButton;
    private WrightsButton changeButton;
    private int promptY;
    private EditBox promptBox;
    private WrightsButton promptOk;
    private WrightsButton promptCancel;
    private boolean promptOpen;
    private boolean movePrompt;
    private boolean awaitTaller;
    private boolean frameSelection;
    private boolean started;

    public LayerColumn(View view) {
        this.view = view;
    }

    public void place(Screen screen, Font font, int x, int y, int size, Add add, Consumer<GuiEventListener> focus) {
        this.screen = screen;
        this.font = font;
        this.focus = focus;
        this.modelX = x;
        this.modelY = y;
        this.gridY = y;
        this.modelSize = size;
        boolean map = !view.concealed() && StructureGrid.oversized(view.sizeX(), view.sizeZ());
        this.layerY = y + size + 16;
        int mapShift = map ? 18 : 0;
        this.selectionY = layerY + 36 + mapShift;
        this.layersY = selectionY + 80;
        if (!started) {
            this.layer = Mth.clamp(view.pivotY(), 0, Math.max(0, view.sizeY() - 1));
            started = true;
        }
        int half = Math.max(40, (size - 4) / 2);
        add.widget(new WrightsButton(x, layersY, half, 16, Component.translatable("screen.wrights.move_layer"), button -> showPrompt(true)));
        add.widget(new WrightsButton(x + half + 4, layersY, half, 16, Component.translatable("screen.wrights.duplicate_layer"), button -> editLayer(BlueprintPayloads.EditLayer.COPY, layer + 1)));
        add.widget(new WrightsButton(x, layersY + 18, half, 16, Component.translatable("screen.wrights.remove_layer"), button -> editLayer(BlueprintPayloads.EditLayer.REMOVE, layer)));
        add.widget(new WrightsButton(x + half + 4, layersY + 18, half, 16, Component.translatable("screen.wrights.add_layer"), button -> showPrompt(false)));
        promptY = layersY + 40;
        int boxW = Math.min(42, Math.max(28, size / 3));
        int actionW = Math.max(24, (size - boxW - 8) / 2);
        promptBox = new EditBox(font, x, promptY + 14, boxW, 16, Component.translatable("screen.wrights.layer_at"));
        promptBox.setFilter(text -> text.matches("\\d{0,3}"));
        promptBox.setMaxLength(3);
        add.widget(promptBox);
        promptOk = new WrightsButton(x + boxW + 4, promptY + 14, actionW, 16, Component.translatable("screen.wrights.layer_ok"), button -> confirmPrompt());
        promptCancel = new WrightsButton(x + boxW + 8 + actionW, promptY + 14, actionW, 16, Component.translatable("screen.wrights.layer_cancel"), button -> hidePrompt());
        add.widget(promptOk);
        add.widget(promptCancel);
        hidePrompt();
        add.widget(new WrightsButton(x, layerY, 22, 16, Component.literal("-"), button -> changeLayer(-1)));
        add.widget(new WrightsButton(x + size - 22, layerY, 22, 16, Component.literal("+"), button -> changeLayer(1)));
        if (map) {
            add.widget(new WrightsButton(x, layerY + 18, size, 16, Component.translatable("screen.wrights.open_map"), button -> openMap()));
        }
        anchorButton = new WrightsButton(x, selectionY, size, 16, Component.translatable("screen.wrights.set_anchor"), button -> setSelectedAnchor());
        removeButton = new WrightsButton(x, selectionY + 18, half, 16, Component.translatable("screen.wrights.remove_block"), button -> removeSelected());
        changeButton = new WrightsButton(x + half + 4, selectionY + 18, half, 16, Component.translatable("screen.wrights.change_block"), button -> changeSelected());
        add.widget(anchorButton);
        add.widget(removeButton);
        add.widget(changeButton);
    }

    public void refresh() {
        int limit = Math.max(0, view.sizeY() - 1);
        if (awaitTaller && layer > limit) {
            limit = layer;
        } else {
            awaitTaller = false;
        }
        layer = Mth.clamp(layer, 0, limit);
        if (!inside(selX, selY, selZ)) {
            selX = -1;
            selY = -1;
            selZ = -1;
        }
        syncSelection();
    }

    public void render(GuiGraphics graphics) {
        syncSelection();
        if (view.concealed()) {
            graphics.drawCenteredString(font, Component.translatable("screen.wrights.preview_hidden"), modelX + modelSize / 2, gridY + modelSize / 2, WrightsUi.MUTED);
        } else {
            layoutGrid();
            grid.render(graphics, view.ghosts(), view.sizeX(), view.sizeZ(), layer, view.pivotX(), view.pivotY(), view.pivotZ(), selX, selY, selZ);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.wrights.layer", layer + 1, view.sizeY()), modelX + modelSize / 2, layerY - 12, WrightsUi.MUTED);
        graphics.drawString(font, Component.translatable("screen.wrights.selection"), modelX, selectionY - 12, WrightsUi.TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.wrights.hint_selected").getString(), modelSize), modelX, selectionY + 40, 0xFFF2D48A, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.wrights.hint_anchor").getString(), modelSize), modelX, selectionY + 52, WrightsUi.BLUE, false);
        graphics.drawString(font, Component.translatable("screen.wrights.layers"), modelX, layersY - 12, WrightsUi.TEXT, false);
        if (promptOpen) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(movePrompt ? "screen.wrights.move_prompt" : "screen.wrights.add_prompt").getString(), modelSize), modelX, promptY + 2, WrightsUi.MUTED, false);
        }
    }

    public boolean keyPressed(int keyCode) {
        if (promptOpen && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            confirmPrompt();
            return true;
        }
        if (promptOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            hidePrompt();
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && view.id() != null && !view.concealed()) {
            layoutGrid();
            int[] hit = grid.hit(mouseX, mouseY, view.sizeX(), view.sizeZ(), layer);
            if (hit != null) {
                selX = hit[0];
                selY = hit[1];
                selZ = hit[2];
                syncSelection();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= modelX && mouseX < modelX + modelSize && mouseY >= gridY && mouseY < gridY + modelSize) {
            changeLayer(scrollY > 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    public void selectCell(int x, int y, int z) {
        selX = x;
        selY = y;
        selZ = z;
        layer = Mth.clamp(y, 0, Math.max(0, view.sizeY() - 1));
        frameSelection = true;
        syncSelection();
    }

    public void clearSelection() {
        selX = -1;
        selY = -1;
        selZ = -1;
        syncSelection();
    }

    private void openMap() {
        if (screen.getMinecraft() == null || view.concealed() || view.id() == null) {
            return;
        }
        screen.getMinecraft().setScreen(new BlueprintMapScreen(
                screen,
                this::selectCell,
                view.ghosts(),
                view.sizeX(),
                view.sizeY(),
                view.sizeZ(),
                layer,
                view.pivotX(),
                view.pivotY(),
                view.pivotZ(),
                selX,
                selY,
                selZ));
    }

    private void layoutGrid() {
        grid.layout(modelX + 12, gridY + 14, Math.max(32, modelSize - 28), view.sizeX(), view.sizeZ(), view.pivotX(), view.pivotZ(), StructureGrid.INLINE);
        if (selY == layer && selX >= 0 && selZ >= 0) {
            if (frameSelection) {
                grid.centerOn(selX, selZ, view.sizeX(), view.sizeZ());
                frameSelection = false;
            } else {
                grid.ensureVisible(selX, selZ, view.sizeX(), view.sizeZ());
            }
        }
    }

    private void editLayer(int operation, int target) {
        if (view.id() == null || target < 0) {
            return;
        }
        int from = layer;
        int height = Math.max(1, view.sizeY());
        if (operation == BlueprintPayloads.EditLayer.REMOVE) {
            awaitTaller = false;
            layer = Math.min(from, Math.max(0, height - 2));
        } else {
            awaitTaller = operation != BlueprintPayloads.EditLayer.MOVE;
            layer = target;
        }
        clearSelection();
        int to = operation == BlueprintPayloads.EditLayer.REMOVE ? from : target;
        PacketDistributor.sendToServer(new BlueprintPayloads.EditLayer(view.id(), operation, from, to));
    }

    private void showPrompt(boolean move) {
        movePrompt = move;
        promptOpen = true;
        promptBox.setValue(Integer.toString(layer + 1));
        promptBox.setVisible(true);
        promptOk.setShown(true);
        promptCancel.setShown(true);
        focus.accept(promptBox);
    }

    private void hidePrompt() {
        promptOpen = false;
        if (promptBox == null) {
            return;
        }
        promptBox.setFocused(false);
        promptBox.setVisible(false);
        promptOk.setShown(false);
        promptCancel.setShown(false);
    }

    private void confirmPrompt() {
        if (!promptOpen || promptBox == null) {
            return;
        }
        int height = Math.max(1, view.sizeY());
        int max = movePrompt ? height : height + 1;
        int number = -1;
        try {
            if (!promptBox.getValue().isBlank()) {
                number = Integer.parseInt(promptBox.getValue());
            }
        } catch (NumberFormatException ignored) {
            number = -1;
        }
        if (number < 1 || number > max) {
            Minecraft minecraft = screen.getMinecraft();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.wrights.layer_range", max), true);
            }
            return;
        }
        int index = number - 1;
        if (!movePrompt || index != layer) {
            editLayer(movePrompt ? BlueprintPayloads.EditLayer.MOVE : BlueprintPayloads.EditLayer.INSERT, index);
        }
        hidePrompt();
    }

    private void setSelectedAnchor() {
        if (view.id() == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.SetAnchor(view.id(), selX, selY, selZ));
    }

    private void removeSelected() {
        if (view.id() == null || !inside(selX, selY, selZ)) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(view.id(), selX, selY, selZ, 0, true));
    }

    private void changeSelected() {
        if (!inside(selX, selY, selZ)) {
            return;
        }
        BlockPickerScreen.open(screen, view.id(), selX, selY, selZ);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < view.sizeX() && y < view.sizeY() && z < view.sizeZ();
    }

    private void syncSelection() {
        boolean selected = inside(selX, selY, selZ);
        if (anchorButton != null) {
            anchorButton.active = selected;
            removeButton.active = selected;
            changeButton.active = selected;
        }
    }

    private void changeLayer(int delta) {
        layer = Mth.clamp(layer + delta, 0, Math.max(0, view.sizeY() - 1));
    }
}
