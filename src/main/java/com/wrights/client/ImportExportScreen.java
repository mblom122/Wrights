package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.blueprint.BlueprintActions;
import com.wrights.item.BlueprintSlips;
import com.wrights.network.BlueprintPayloads;

public class ImportExportScreen extends Screen {
    private final Screen parent;
    private final UUID selectedId;
    private final boolean selectedFolder;
    private BlueprintPayloads.ClipboardStatus status = BlueprintPayloads.ClipboardStatus.EMPTY;
    private boolean asked;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int leftX;
    private int rightX;
    private int columnW;
    private int bodyY;
    private EditBox nameBox;
    private WrightsButton copyButton;
    private WrightsButton pasteButton;
    private WrightsButton replaceButton;
    private WrightsButton anotherButton;
    private WrightsButton nextButton;
    private WrightsButton useNameButton;
    private WrightsButton saveButton;

    public ImportExportScreen(Screen parent, UUID selectedId, boolean selectedFolder) {
        super(Component.translatable("screen.wrights.transfer_title"));
        this.parent = parent;
        this.selectedId = selectedId;
        this.selectedFolder = selectedFolder;
    }

    public void apply(BlueprintPayloads.ClipboardStatus status) {
        this.status = status == null ? BlueprintPayloads.ClipboardStatus.EMPTY : status;
        asked = true;
        showActions();
    }

    @Override
    protected void init() {
        panelW = Math.min(520, this.width - 16);
        panelH = Math.min(300, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        leftX = panelX + 14;
        columnW = (panelW - 36) / 2;
        rightX = leftX + columnW + 8;
        bodyY = panelY + 58;
        int buttonY = panelY + panelH - 78;
        copyButton = addRenderableWidget(new WrightsButton(leftX, buttonY, columnW, 18, Component.translatable("screen.wrights.transfer_copy"), button -> copy()));
        pasteButton = addRenderableWidget(new WrightsButton(leftX, buttonY + 22, columnW, 18, Component.translatable("screen.wrights.transfer_paste"), button -> paste(BlueprintSlips.LOAD)));
        int half = Math.max(36, (columnW - 4) / 2);
        replaceButton = addRenderableWidget(new WrightsButton(leftX, buttonY, half, 18, Component.translatable("screen.wrights.slip_replace"), button -> paste(BlueprintSlips.REPLACE), true));
        anotherButton = addRenderableWidget(new WrightsButton(leftX + half + 4, buttonY, columnW - half - 4, 18, Component.translatable("screen.wrights.folder_another"), button -> paste(BlueprintSlips.LOAD)));
        nextButton = addRenderableWidget(new WrightsButton(leftX, buttonY + 22, columnW, 18, Component.translatable("screen.wrights.slip_as", ""), button -> paste(BlueprintSlips.NEXT_NUMBER)));
        nameBox = new EditBox(this.font, leftX, buttonY + 44, Math.max(40, columnW - 78), 18, Component.translatable("screen.wrights.slip_name_hint"));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.translatable("screen.wrights.slip_name_hint"));
        nameBox.setResponder(value -> refreshName());
        addRenderableWidget(nameBox);
        useNameButton = addRenderableWidget(new WrightsButton(leftX + columnW - 74, buttonY + 44, 74, 18, Component.translatable("screen.wrights.slip_use_name"), button -> paste(BlueprintSlips.NAMED)));
        saveButton = addRenderableWidget(new WrightsButton(rightX, bodyY + 28, columnW, 18, Component.translatable("screen.wrights.transfer_save_blueprint"), button -> saveItem()));
        addRenderableWidget(new WrightsButton(rightX, bodyY + 52, columnW, 18, Component.translatable("screen.wrights.transfer_load_blueprints"), button -> {
            this.minecraft.setScreen(new BlueprintLoadScreen(this));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestBlueprintItems());
        }));
        addRenderableWidget(new WrightsButton(rightX, bodyY + 76, columnW, 18, Component.translatable("screen.wrights.transfer_load_folders"), button -> {
            this.minecraft.setScreen(new FolderLoadScreen(this));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestFolderItems());
        }));
        addRenderableWidget(new WrightsButton(panelX + panelW - 78, panelY + panelH - 24, 66, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
        copyButton.active = selectedId != null;
        saveButton.active = selectedId != null;
        saveButton.setMessage(Component.translatable(selectedFolder ? "screen.wrights.transfer_save_folder" : "screen.wrights.transfer_save_blueprint"));
        BlueprintClipboard.load();
        if (BlueprintClipboard.has()) {
            PacketDistributor.sendToServer(new BlueprintPayloads.ClipboardPreview(BlueprintClipboard.data()));
        } else {
            asked = true;
        }
        showActions();
    }

    private void showActions() {
        if (pasteButton == null) {
            return;
        }
        boolean ready = asked && status.present() && BlueprintClipboard.has();
        boolean conflict = ready && status.kind() != BlueprintSlips.NEW;
        boolean different = ready && status.kind() == BlueprintSlips.DIFFERENT;
        pasteButton.visible = !conflict;
        pasteButton.active = ready && status.kind() == BlueprintSlips.NEW;
        replaceButton.visible = different;
        anotherButton.visible = different && status.folder();
        if (different && !status.folder()) {
            replaceButton.setWidth(columnW);
        } else if (different) {
            int half = Math.max(36, (columnW - 4) / 2);
            replaceButton.setWidth(half);
            anotherButton.setX(leftX + half + 4);
            anotherButton.setWidth(columnW - half - 4);
        }
        nextButton.visible = conflict;
        nameBox.visible = conflict;
        useNameButton.visible = conflict;
        if (conflict) {
            nextButton.setMessage(Component.translatable("screen.wrights.slip_as", status.nextName()));
        }
        refreshName();
    }

    private void refreshName() {
        if (useNameButton == null || nameBox == null) {
            return;
        }
        String typed = nameBox.getValue().trim();
        if (typed.isEmpty() || status == null || !status.present()) {
            useNameButton.active = false;
            return;
        }
        if (status.folder()) {
            useNameButton.active = true;
            return;
        }
        String clean = BlueprintActions.cleanName(typed);
        List<String> taken = new ArrayList<>();
        for (BlueprintPayloads.Summary summary : WrightsClientState.library) {
            taken.add(summary.name());
        }
        useNameButton.active = BlueprintActions.uniqueName(clean, taken).equals(clean);
    }

    private void copy() {
        if (selectedId == null) {
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.CopyClipboard(selectedId, selectedFolder));
    }

    private void paste(int mode) {
        if (!BlueprintClipboard.has()) {
            return;
        }
        String name = mode == BlueprintSlips.NAMED && nameBox != null ? nameBox.getValue() : "";
        PacketDistributor.sendToServer(new BlueprintPayloads.ClipboardImport(mode, name, BlueprintClipboard.data()));
    }

    private void saveItem() {
        if (selectedId == null) {
            return;
        }
        if (selectedFolder) {
            PacketDistributor.sendToServer(new BlueprintPayloads.WriteFolderItem(-1, selectedId));
        } else {
            PacketDistributor.sendToServer(new BlueprintPayloads.WriteBlueprintItem(-1, selectedId));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        WrightsUi.inset(graphics, leftX - 4, bodyY - 16, columnW + 8, panelH - 78);
        WrightsUi.inset(graphics, rightX - 4, bodyY - 16, columnW + 8, panelH - 78);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 14, panelY + 12, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.transfer_sub"), panelX + 14, panelY + 26, WrightsUi.MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.transfer_clip"), leftX, bodyY - 12, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.transfer_items"), rightX, bodyY - 12, WrightsUi.COPPER, false);
        int y = bodyY + 4;
        if (asked && status.present() && !status.folder() && BlueprintClipboard.has()) {
            SchematicViews.Shot shot = SchematicViews.fromClipboard();
            int buttonTop = panelY + panelH - 82;
            int modelH = Math.min(96, buttonTop - y - 28);
            if (shot.visible() && modelH >= 36) {
                SchematicViews.draw(graphics, this.font, leftX, y, columnW, modelH, shot);
                y += modelH + 6;
            }
        }
        if (!BlueprintClipboard.has()) {
            for (var line : this.font.split(Component.translatable("screen.wrights.transfer_empty"), columnW - 8)) {
                graphics.drawString(this.font, line, leftX, y, WrightsUi.MUTED, false);
                y += 10;
            }
        } else if (!asked || !status.present()) {
            graphics.drawString(this.font, Component.translatable(asked ? "screen.wrights.transfer_bad" : "screen.wrights.slip_loading"), leftX, y, WrightsUi.MUTED, false);
        } else {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(status.name(), columnW - 8), leftX, y, WrightsUi.TEXT, false);
            y += 12;
            Component detail = status.folder()
                    ? Component.translatable("screen.wrights.folder_row", status.a(), status.b())
                    : Component.translatable("screen.wrights.slip_row", status.a(), status.b(), status.c(), status.count());
            graphics.drawString(this.font, detail, leftX, y, WrightsUi.MUTED, false);
            y += 14;
            Component body = switch (status.kind()) {
                case BlueprintSlips.DIFFERENT -> Component.translatable(status.folder() ? "screen.wrights.folder_different" : "screen.wrights.slip_different", status.name(), status.libraryStat(), status.folder() ? status.b() : status.count());
                case BlueprintSlips.EXACT -> Component.translatable(status.folder() ? "screen.wrights.folder_exact" : "screen.wrights.slip_exact", status.name());
                default -> Component.translatable(status.folder() ? "screen.wrights.folder_new" : "screen.wrights.slip_new");
            };
            int color = status.kind() == BlueprintSlips.DIFFERENT ? 0xFFE7C07A : status.kind() == BlueprintSlips.EXACT ? 0xFF8FCB8F : WrightsUi.TEXT;
            for (var line : this.font.split(body, columnW - 8)) {
                if (y > panelY + panelH - 100) {
                    break;
                }
                graphics.drawString(this.font, line, leftX, y, color, false);
                y += 10;
            }
        }
        int hint = bodyY + 8;
        for (var line : this.font.split(Component.translatable(selectedId == null ? "screen.wrights.transfer_none" : selectedFolder ? "screen.wrights.transfer_save_folder_hint" : "screen.wrights.transfer_save_blueprint_hint"), columnW - 8)) {
            graphics.drawString(this.font, line, rightX, hint, WrightsUi.MUTED, false);
            hint += 10;
            if (hint > bodyY + 24) {
                break;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
