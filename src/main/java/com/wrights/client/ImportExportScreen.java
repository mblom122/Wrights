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
    private final Component selectionLine;
    private BlueprintPayloads.ClipboardStatus status = BlueprintPayloads.ClipboardStatus.EMPTY;
    private String copiedName = "";
    /** True only after the player asks to paste from the clipboard. */
    private boolean reviewing;
    private boolean waitingPreview;
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
    private int actionY;

    public ImportExportScreen(Screen parent, BlueprintList.Row row) {
        super(Component.translatable("screen.wrights.transfer_title"));
        this.parent = parent;
        if (row == null) {
            this.selectedId = null;
            this.selectedFolder = false;
            this.selectionLine = Component.translatable("screen.wrights.transfer_selected_none");
        } else if (row.folder()) {
            this.selectedId = row.id();
            this.selectedFolder = true;
            this.selectionLine = Component.translatable("screen.wrights.transfer_selected_folder", row.name(), BlueprintList.childCount(row.id()));
        } else {
            this.selectedId = row.id();
            this.selectedFolder = false;
            BlueprintPayloads.Summary summary = row.summary();
            this.selectionLine = Component.translatable(
                    "screen.wrights.transfer_selected_blueprint",
                    row.name(),
                    summary.sizeX(),
                    summary.sizeY(),
                    summary.sizeZ(),
                    summary.blocks());
        }
    }

    /** Server finished a copy-to-clipboard. Do not treat that as an import preview. */
    public void copied(BlueprintPayloads.ClipboardStatus status) {
        this.status = status == null ? BlueprintPayloads.ClipboardStatus.EMPTY : status;
        this.copiedName = this.status.present() ? this.status.name() : "";
        reviewing = false;
        waitingPreview = false;
        showActions();
    }

    /** Server answered a paste-from-clipboard preview. */
    public void apply(BlueprintPayloads.ClipboardStatus status) {
        this.status = status == null ? BlueprintPayloads.ClipboardStatus.EMPTY : status;
        reviewing = true;
        waitingPreview = false;
        showActions();
    }

    @Override
    protected void init() {
        panelW = Math.min(520, this.width - 16);
        // Room for selection line, columns, and a 3-row conflict stack above Close.
        panelH = Math.min(320, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        leftX = panelX + 14;
        columnW = (panelW - 36) / 2;
        rightX = leftX + columnW + 8;
        bodyY = panelY + 70;
        actionY = panelY + panelH - 92;
        copyButton = addRenderableWidget(new WrightsButton(leftX, actionY, columnW, 18, Component.translatable("screen.wrights.transfer_copy"), button -> copy()));
        pasteButton = addRenderableWidget(new WrightsButton(leftX, actionY + 20, columnW, 18, Component.translatable("screen.wrights.transfer_paste"), button -> pasteClicked()));
        replaceButton = addRenderableWidget(new WrightsButton(leftX, actionY, columnW, 18, Component.translatable("screen.wrights.slip_replace"), button -> importClip(BlueprintSlips.REPLACE), true));
        anotherButton = addRenderableWidget(new WrightsButton(leftX, actionY, columnW, 18, Component.translatable("screen.wrights.folder_another"), button -> importClip(BlueprintSlips.LOAD)));
        nextButton = addRenderableWidget(new WrightsButton(leftX, actionY + 20, columnW, 18, Component.translatable("screen.wrights.slip_as", ""), button -> importClip(BlueprintSlips.NEXT_NUMBER)));
        nameBox = new EditBox(this.font, leftX, actionY + 40, Math.max(40, columnW - 78), 18, Component.translatable("screen.wrights.slip_name_hint"));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.translatable("screen.wrights.slip_name_hint"));
        nameBox.setResponder(value -> refreshName());
        addRenderableWidget(nameBox);
        useNameButton = addRenderableWidget(new WrightsButton(leftX + columnW - 74, actionY + 40, 74, 18, Component.translatable("screen.wrights.slip_use_name"), button -> importClip(BlueprintSlips.NAMED)));
        saveButton = addRenderableWidget(new WrightsButton(rightX, bodyY + 28, columnW, 18, Component.translatable("screen.wrights.transfer_save_blueprint"), button -> saveItem()));
        addRenderableWidget(new WrightsButton(rightX, bodyY + 52, columnW, 18, Component.translatable("screen.wrights.transfer_load_blueprints"), button -> {
            this.minecraft.setScreen(new BlueprintLoadScreen(this));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestBlueprintItems());
        }));
        addRenderableWidget(new WrightsButton(rightX, bodyY + 76, columnW, 18, Component.translatable("screen.wrights.transfer_load_folders"), button -> {
            this.minecraft.setScreen(new FolderLoadScreen(this));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestFolderItems());
        }));
        addRenderableWidget(new WrightsButton(panelX + panelW - 78, panelY + panelH - 22, 66, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
        copyButton.active = selectedId != null;
        saveButton.active = selectedId != null;
        saveButton.setMessage(Component.translatable(selectedFolder ? "screen.wrights.transfer_save_folder" : "screen.wrights.transfer_save_blueprint"));
        reviewing = false;
        waitingPreview = false;
        status = BlueprintPayloads.ClipboardStatus.EMPTY;
        showActions();
    }

    private void showActions() {
        if (pasteButton == null) {
            return;
        }
        boolean ready = reviewing && !waitingPreview && status.present() && BlueprintClipboard.has();
        boolean conflict = ready && status.kind() != BlueprintSlips.NEW;
        boolean different = ready && status.kind() == BlueprintSlips.DIFFERENT;
        boolean canImport = ready && status.kind() == BlueprintSlips.NEW;
        boolean folderConflict = different && status.folder();
        copyButton.setShown(!conflict);
        pasteButton.setShown(!conflict);
        if (!conflict) {
            copyButton.active = selectedId != null;
            pasteButton.active = !waitingPreview && (!reviewing || canImport);
            pasteButton.setMessage(Component.translatable(canImport ? "screen.wrights.transfer_paste_confirm" : "screen.wrights.transfer_paste"));
            copyButton.setY(actionY);
            pasteButton.setY(actionY + 20);
        }
        // Conflict stack: replace/another, then save-as, then custom name — never share rows with copy/paste.
        replaceButton.setShown(different);
        anotherButton.setShown(folderConflict);
        if (different) {
            replaceButton.setY(actionY);
            if (folderConflict) {
                int half = Math.max(36, (columnW - 4) / 2);
                replaceButton.setX(leftX);
                replaceButton.setWidth(half);
                anotherButton.setX(leftX + half + 4);
                anotherButton.setY(actionY);
                anotherButton.setWidth(columnW - half - 4);
            } else {
                replaceButton.setX(leftX);
                replaceButton.setWidth(columnW);
            }
        }
        int renameY = conflict ? (different ? actionY + 20 : actionY) : actionY + 20;
        nextButton.setShown(conflict);
        nameBox.visible = conflict;
        useNameButton.setShown(conflict);
        if (conflict) {
            nextButton.setX(leftX);
            nextButton.setY(renameY);
            nextButton.setWidth(columnW);
            nextButton.setMessage(Component.translatable("screen.wrights.slip_as", status.nextName()));
            nameBox.setX(leftX);
            nameBox.setY(renameY + 20);
            nameBox.setWidth(Math.max(40, columnW - 78));
            useNameButton.setX(leftX + columnW - 74);
            useNameButton.setY(renameY + 20);
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

    private void pasteClicked() {
        if (reviewing && status.present() && status.kind() == BlueprintSlips.NEW && BlueprintClipboard.has()) {
            importClip(BlueprintSlips.LOAD);
            return;
        }
        readClipboard();
    }

    private void readClipboard() {
        BlueprintClipboard.load();
        copiedName = "";
        if (!BlueprintClipboard.has()) {
            status = BlueprintPayloads.ClipboardStatus.EMPTY;
            reviewing = true;
            waitingPreview = false;
            showActions();
            return;
        }
        reviewing = true;
        waitingPreview = true;
        showActions();
        PacketDistributor.sendToServer(new BlueprintPayloads.ClipboardPreview(BlueprintClipboard.data()));
    }

    private void importClip(int mode) {
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
        graphics.drawString(this.font, this.font.plainSubstrByWidth(selectionLine.getString(), panelW - 28), panelX + 14, panelY + 40, selectedId == null ? WrightsUi.MUTED : WrightsUi.TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.transfer_clip"), leftX, bodyY - 12, WrightsUi.COPPER, false);
        graphics.drawString(this.font, Component.translatable("screen.wrights.transfer_items"), rightX, bodyY - 12, WrightsUi.COPPER, false);
        int y = bodyY + 4;
        if (reviewing && status.present() && !status.folder() && BlueprintClipboard.has()) {
            SchematicViews.Shot shot = SchematicViews.fromClipboard();
            int modelH = Math.min(96, actionY - y - 10);
            if (shot.visible() && modelH >= 36) {
                SchematicViews.draw(graphics, this.font, leftX, y, columnW, modelH, shot);
                y += modelH + 6;
            }
        }
        if (!reviewing) {
            Component tip = copiedName.isEmpty()
                    ? Component.translatable("screen.wrights.transfer_idle")
                    : Component.translatable("screen.wrights.transfer_copied", copiedName);
            for (var line : this.font.split(tip, columnW - 8)) {
                graphics.drawString(this.font, line, leftX, y, WrightsUi.MUTED, false);
                y += 10;
            }
        } else if (waitingPreview) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.slip_loading"), leftX, y, WrightsUi.MUTED, false);
        } else if (!BlueprintClipboard.has() || !status.present()) {
            for (var line : this.font.split(Component.translatable(BlueprintClipboard.has() ? "screen.wrights.transfer_bad" : "screen.wrights.transfer_empty"), columnW - 8)) {
                graphics.drawString(this.font, line, leftX, y, WrightsUi.MUTED, false);
                y += 10;
            }
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
