package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.item.BlueprintSlips;
import com.wrights.network.BlueprintPayloads;

public class FolderLoadScreen extends Screen {
    private static final int ROW = 24;
    private static final int HEADER = 16;

    private final Screen parent;
    private final List<BlueprintPayloads.FolderItemLine> lines = new ArrayList<>();
    private final List<View> views = new ArrayList<>();
    private boolean received;
    private int selectedSlot = -1;
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int detailX;
    private int detailW;
    private EditBox nameBox;
    private WrightsButton loadButton;
    private WrightsButton replaceButton;
    private WrightsButton anotherButton;
    private WrightsButton nextButton;
    private WrightsButton useNameButton;

    public FolderLoadScreen(Screen parent) {
        super(Component.translatable("screen.wrights.folder_title"));
        this.parent = parent;
    }

    public void apply(List<BlueprintPayloads.FolderItemLine> incoming) {
        lines.clear();
        lines.addAll(incoming);
        received = true;
        rebuild();
        if (selected() == null) {
            selectedSlot = -1;
        }
        applySelection();
    }

    @Override
    protected void init() {
        panelW = Math.min(500, this.width - 16);
        panelH = Math.min(320, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX = panelX + 12;
        listY = panelY + 32;
        listW = Math.min(220, panelW / 2);
        listH = panelH - 64;
        detailX = listX + listW + 10;
        detailW = panelX + panelW - 12 - detailX;

        int actionY = panelY + panelH - 108;
        int half = Math.max(40, (detailW - 4) / 2);
        loadButton = addRenderableWidget(new WrightsButton(detailX, actionY, detailW, 18, Component.translatable("screen.wrights.slip_load"), button -> send(BlueprintSlips.LOAD)));
        replaceButton = addRenderableWidget(new WrightsButton(detailX, actionY, half, 18, Component.translatable("screen.wrights.slip_replace"), button -> send(BlueprintSlips.REPLACE), true));
        anotherButton = addRenderableWidget(new WrightsButton(detailX + half + 4, actionY, detailW - half - 4, 18, Component.translatable("screen.wrights.folder_another"), button -> send(BlueprintSlips.LOAD)));
        nextButton = addRenderableWidget(new WrightsButton(detailX, actionY + 20, detailW, 18, Component.translatable("screen.wrights.slip_as", ""), button -> send(BlueprintSlips.NEXT_NUMBER)));
        nameBox = new EditBox(this.font, detailX, actionY + 40, Math.max(40, detailW - 78), 18, Component.translatable("screen.wrights.slip_name_hint"));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.translatable("screen.wrights.slip_name_hint"));
        nameBox.setResponder(value -> refreshNameButton());
        addRenderableWidget(nameBox);
        useNameButton = addRenderableWidget(new WrightsButton(detailX + detailW - 74, actionY + 40, 74, 18, Component.translatable("screen.wrights.slip_use_name"), button -> send(BlueprintSlips.NAMED)));
        addRenderableWidget(new WrightsButton(panelX + panelW - 78, panelY + panelH - 24, 66, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
        rebuild();
        applySelection();
    }

    private void rebuild() {
        views.clear();
        views.add(new View(Component.translatable("screen.wrights.slip_section_new").getString()));
        boolean anyNew = false;
        for (BlueprintPayloads.FolderItemLine line : lines) {
            if (line.kind() != BlueprintSlips.EXACT) {
                views.add(new View(line));
                anyNew = true;
            }
        }
        if (!anyNew) {
            views.add(View.none());
        }
        views.add(new View(Component.translatable("screen.wrights.slip_section_exact").getString()));
        boolean anyExact = false;
        for (BlueprintPayloads.FolderItemLine line : lines) {
            if (line.kind() == BlueprintSlips.EXACT) {
                views.add(new View(line));
                anyExact = true;
            }
        }
        if (!anyExact) {
            views.add(View.none());
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight() - listH));
    }

    private void applySelection() {
        BlueprintPayloads.FolderItemLine line = selected();
        if (loadButton == null) {
            return;
        }
        boolean different = line != null && line.kind() == BlueprintSlips.DIFFERENT;
        boolean conflict = line != null && line.kind() != BlueprintSlips.NEW;
        loadButton.visible = line != null && line.kind() == BlueprintSlips.NEW;
        replaceButton.visible = different;
        anotherButton.visible = different;
        nextButton.visible = conflict;
        nameBox.visible = conflict;
        useNameButton.visible = conflict;
        if (conflict) {
            nextButton.setMessage(Component.translatable("screen.wrights.slip_as", line.nextName()));
        }
        refreshNameButton();
    }

    private void refreshNameButton() {
        if (useNameButton != null) {
            useNameButton.active = nameBox != null && !nameBox.getValue().isBlank();
        }
    }

    private BlueprintPayloads.FolderItemLine selected() {
        for (BlueprintPayloads.FolderItemLine line : lines) {
            if (line.slot() == selectedSlot) {
                return line;
            }
        }
        return null;
    }

    private void send(int mode) {
        BlueprintPayloads.FolderItemLine line = selected();
        if (line == null) {
            return;
        }
        String name = mode == BlueprintSlips.NAMED && nameBox != null ? nameBox.getValue() : "";
        PacketDistributor.sendToServer(new BlueprintPayloads.LoadFolderItem(line.slot(), mode, name));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideList(mouseX, mouseY)) {
            int y = listY - scroll;
            for (View view : views) {
                int height = view.height();
                if (mouseY >= y && mouseY < y + height && view.line != null) {
                    selectedSlot = view.line.slot();
                    if (nameBox != null) {
                        nameBox.setValue("");
                    }
                    applySelection();
                    return true;
                }
                y += height;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideList(mouseX, mouseY)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 16, 0, Math.max(0, contentHeight() - listH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        WrightsUi.inset(graphics, listX - 2, listY - 2, listW + 4, listH + 4);
        WrightsUi.inset(graphics, detailX - 2, listY - 2, detailW + 4, listH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 14, panelY + 12, WrightsUi.COPPER, false);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!received) {
            graphics.drawString(this.font, Component.translatable("screen.wrights.slip_loading"), listX + 6, listY + 8, WrightsUi.MUTED, false);
            return;
        }
        if (lines.isEmpty()) {
            for (var line : this.font.split(Component.translatable("screen.wrights.folder_empty"), listW - 12)) {
                graphics.drawString(this.font, line, listX + 6, listY + 8, WrightsUi.MUTED, false);
            }
            return;
        }
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - scroll;
        for (View view : views) {
            int height = view.height();
            if (y + height >= listY && y <= listY + listH) {
                if (view.header != null) {
                    graphics.drawString(this.font, view.header, listX + 4, y + 4, WrightsUi.COPPER, false);
                } else if (view.line == null) {
                    graphics.drawString(this.font, Component.translatable("screen.wrights.slip_none"), listX + 8, y + 2, WrightsUi.MUTED, false);
                } else {
                    boolean on = view.line.slot() == selectedSlot;
                    boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + height;
                    if (on || hover) {
                        graphics.fill(listX + 2, y, listX + listW - 2, y + height - 1, on ? 0xFF24384A : 0xFF1C2A36);
                    }
                    int color = view.line.kind() == BlueprintSlips.EXACT ? WrightsUi.MUTED : WrightsUi.TEXT;
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(view.line.name(), listW - 70), listX + 6, y + 3, color, false);
                    Component tag = Component.translatable(tagKey(view.line.kind()));
                    graphics.drawString(this.font, tag, listX + listW - 8 - this.font.width(tag), y + 3, tagColor(view.line.kind()), false);
                    graphics.drawString(this.font, Component.translatable("screen.wrights.folder_row", view.line.folders(), view.line.blueprints()), listX + 6, y + 13, WrightsUi.MUTED, false);
                }
            }
            y += height;
        }
        graphics.disableScissor();
    }

    private void renderDetail(GuiGraphics graphics) {
        BlueprintPayloads.FolderItemLine line = selected();
        int y = listY + 8;
        if (!received || line == null) {
            Component text = Component.translatable(received ? "screen.wrights.folder_pick" : "screen.wrights.slip_loading");
            for (var split : this.font.split(text, detailW - 12)) {
                graphics.drawString(this.font, split, detailX + 6, y, WrightsUi.MUTED, false);
                y += 10;
            }
            return;
        }
        graphics.drawString(this.font, this.font.plainSubstrByWidth(line.name(), detailW - 12), detailX + 6, y, line.kind() == BlueprintSlips.EXACT ? WrightsUi.MUTED : WrightsUi.TEXT, false);
        y += 14;
        Component body = switch (line.kind()) {
            case BlueprintSlips.DIFFERENT -> Component.translatable("screen.wrights.folder_different", line.name(), line.libraryBlueprints(), line.blueprints());
            case BlueprintSlips.EXACT -> Component.translatable("screen.wrights.folder_exact", line.name());
            default -> Component.translatable("screen.wrights.folder_new");
        };
        int color = line.kind() == BlueprintSlips.DIFFERENT ? 0xFFE7C07A : line.kind() == BlueprintSlips.EXACT ? 0xFF8FCB8F : WrightsUi.TEXT;
        for (var split : this.font.split(body, detailW - 12)) {
            if (y > panelY + panelH - 120) {
                break;
            }
            graphics.drawString(this.font, split, detailX + 6, y, color, false);
            y += 10;
        }
    }

    private int contentHeight() {
        int height = 0;
        for (View view : views) {
            height += view.height();
        }
        return height;
    }

    private static String tagKey(int kind) {
        return switch (kind) {
            case BlueprintSlips.DIFFERENT -> "screen.wrights.slip_tag_different";
            case BlueprintSlips.EXACT -> "screen.wrights.slip_tag_exact";
            default -> "screen.wrights.slip_tag_new";
        };
    }

    private static int tagColor(int kind) {
        return switch (kind) {
            case BlueprintSlips.DIFFERENT -> 0xFFE7C07A;
            case BlueprintSlips.EXACT -> 0xFF8FCB8F;
            default -> WrightsUi.BLUE;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class View {
        private final String header;
        private final BlueprintPayloads.FolderItemLine line;

        private static View none() {
            return new View();
        }

        private View() {
            this.header = null;
            this.line = null;
        }

        private View(String header) {
            this.header = header;
            this.line = null;
        }

        private View(BlueprintPayloads.FolderItemLine line) {
            this.header = null;
            this.line = line;
        }

        private int height() {
            if (header != null) {
                return HEADER;
            }
            return line == null ? 14 : ROW;
        }
    }
}
