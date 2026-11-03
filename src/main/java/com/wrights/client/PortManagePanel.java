package com.wrights.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.WrightsConfig;
import com.wrights.drone.Wrightports;
import com.wrights.drone.WrightportPlaces;
import com.wrights.network.BlueprintPayloads;

/** The Manage page: placed wrightports, per-port controls, tabs and the personal port bar. */
final class PortManagePanel {
    private final WrightsMenuScreen s;

    PortManagePanel(WrightsMenuScreen s) {
        this.s = s;
    }

    private String portRenameDim = "";

    private boolean hitWide(double mouseX, double mouseY, int right, int y, int width) {
        return mouseX >= right - width && mouseX < right && mouseY >= y && mouseY < y + 16;
    }

    private int portBarX() {
        return s.manageX() + 8;
    }

    private int portBarW() {
        return Math.max(40, s.manageW() - 16);
    }

    void reconcilePlaced() {
        if (s.placedPick == null) {
            return;
        }
        for (BlueprintPayloads.PlacedPort port : WrightsClientState.placedPorts) {
            if (samePort(port, s.placedPick)) {
                s.placedPick = port;
                return;
            }
        }
        s.placedPick = null;
        WrightsClientState.placedKnown = false;
    }

    private boolean samePort(BlueprintPayloads.PlacedPort port, BlueprintPayloads.PlacedPort other) {
        return port != null && other != null && port.dimension().equals(other.dimension()) && port.pos().equals(other.pos());
    }

    private int chooserY() {
        return s.portBarY() + 20;
    }

    private void drawPersonalEnergy(GuiGraphics graphics) {
        if (s.minecraft() == null || s.minecraft().player == null) {
            return;
        }
        net.minecraft.world.item.ItemStack worn = com.wrights.drone.Wrightports.worn(s.minecraft().player);
        long stored = com.wrights.energy.PortEnergy.itemStored(worn);
        long capacity = com.wrights.WrightsConfig.portEnergyCapacity();
        WrightsUi.energyBar(graphics, s.font(), portBarX(), s.personalEnergyY(), portBarW(), stored, capacity);
    }

    void renderManage(GuiGraphics graphics, int mouseX, int mouseY) {
        drawManageTabs(graphics, mouseX, mouseY);
        if (s.manageTab == 0) {
            renderPersonal(graphics, mouseX, mouseY);
            return;
        }
        renderPlaced(graphics, mouseX, mouseY);
    }

    private void renderPersonal(GuiGraphics graphics, int mouseX, int mouseY) {
        List<WrightportPlaces.Spot> spots = s.ports();
        WrightportPlaces.Spot spot = s.currentSpot();
        if (spot == null) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_none"), portBarX(), chooserY() + 4, WrightsUi.MUTED, false);
            return;
        }
        if (spots.size() > 1) {
            drawPortBar(graphics, spots, spot, mouseX, mouseY);
        }
        drawPersonalEnergy(graphics);
        if (spots.size() > 1 && s.portListOpen) {
            drawPortList(graphics, spots, mouseX, mouseY);
        }
        if (s.manage.isOpen()) {
            s.manage.render(s.manageHost, graphics, mouseX, mouseY);
        }
    }

    private void renderPlaced(GuiGraphics graphics, int mouseX, int mouseY) {
        List<BlueprintPayloads.PlacedPort> list = WrightsClientState.placedPorts;
        int top = s.placedListTop();
        if (list.isEmpty()) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.placed_none"), portBarX(), top + 4, WrightsUi.MUTED, false);
            return;
        }
        int shown = s.placedShown();
        s.placedScroll = Mth.clamp(s.placedScroll, 0, Math.max(0, list.size() - shown));
        int x = portBarX();
        int w = portBarW();
        int h = shown * 18;
        graphics.fill(x, top, x + w, top + h, 0xFF121A22);
        WrightsUi.border(graphics, x, top, w, h, 0xFF3D4E5E);
        for (int i = 0; i < shown; i++) {
            BlueprintPayloads.PlacedPort port = list.get(s.placedScroll + i);
            int rowY = top + i * 18;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + 18;
            boolean picked = samePort(port, s.placedPick);
            if (hover || picked) {
                graphics.fill(x + 1, rowY + 1, x + w - 1, rowY + 17, picked ? 0xFF2A4054 : 0xFF243244);
            }
            int right = drawPlacedButtons(graphics, port, x + w - 4, rowY + 1, mouseX, mouseY);
            int units = port.units();
            if (picked && s.placedReady()) {
                units = WrightsClientState.placedSwarm.units().size();
            }
            if (renamingPort(port) && s.portRename != null) {
                s.portRename.setX(x + 4);
                s.portRename.setY(rowY + 2);
                s.portRename.setWidth(Math.max(40, right - x - 10));
            } else {
                String text = s.font().plainSubstrByWidth(placedLine(port, units).getString(), Math.max(0, right - x - 8));
                graphics.drawString(s.font(), text, x + 6, rowY + 5, WrightsUi.TEXT, false);
            }
        }
        if (s.placedPick != null && !s.placedReady()) {
            int noteY = top + h + 8;
            String key = placedMissingHere() ? "menu.wrights.placed_missing" : "menu.wrights.placed_loading";
            graphics.drawString(s.font(), Component.translatable(key), x, noteY, WrightsUi.MUTED, false);
        }
        if (s.manage.isOpen()) {
            s.manage.render(s.manageHost, graphics, mouseX, mouseY);
        }
    }

    private boolean placedMissingHere() {
        return WrightsClientState.placedKnown
                && WrightsClientState.placedMissing
                && s.placedPick != null
                && s.placedPick.dimension().equals(WrightsClientState.placedDim)
                && s.placedPick.pos().equals(WrightsClientState.placedPos);
    }

    private int drawPlacedButtons(GuiGraphics graphics, BlueprintPayloads.PlacedPort port, int right, int y, int mouseX, int mouseY) {
        boolean tool = s.toolHeld();
        if (s.creativeVisit()) {
            right = s.drawJobButton(graphics, right, y, "menu.wrights.visit", mouseX, mouseY, s.jobButtonWidth("menu.wrights.visit"), 0, tool);
        }
        right = s.drawJobButton(graphics, right, y, "menu.wrights.rename", mouseX, mouseY, s.jobButtonWidth("menu.wrights.rename"));
        boolean marked = WrightsClientState.portMarked(port.dimension(), port.pos());
        String markKey = marked ? "menu.wrights.mark_on" : "menu.wrights.mark";
        return s.drawJobButton(graphics, right, y, markKey, mouseX, mouseY, s.jobButtonWidth(markKey), marked ? 0xFFF2D48A : 0);
    }

    private Component placedLine(BlueprintPayloads.PlacedPort port, int units) {
        String here = s.minecraft().level == null ? "" : s.minecraft().level.dimension().location().toString();
        if (here.equals(port.dimension())) {
            Component line = Component.translatable("menu.wrights.placed_line", port.pos().getX(), port.pos().getY(), port.pos().getZ(), units);
            return withPortName(port, line);
        }
        String dim = port.dimension();
        int colon = dim.indexOf(':');
        if (colon >= 0 && colon + 1 < dim.length()) {
            dim = dim.substring(colon + 1);
        }
        Component line = Component.translatable("menu.wrights.placed_line_dim", port.pos().getX(), port.pos().getY(), port.pos().getZ(), units, dim);
        return withPortName(port, line);
    }

    private Component withPortName(BlueprintPayloads.PlacedPort port, Component line) {
        String name = port.name();
        Component shown = name == null || name.isBlank() ? line : WrightportBlockEntity.titled(name).copy().append(" · ").append(line);
        if (port.unowned()) {
            shown = shown.copy().append(" · ").append(Component.translatable("block.wrights.wrightport.no_owner"));
        }
        return shown;
    }

    private boolean renamingPort(BlueprintPayloads.PlacedPort port) {
        return s.portRenamePos != null && port.dimension().equals(portRenameDim) && port.pos().equals(s.portRenamePos);
    }

    private void beginPortRename(BlueprintPayloads.PlacedPort port) {
        if (s.portRename == null || port == null) {
            return;
        }
        if (renamingPort(port)) {
            s.portRename.setFocused(true);
            s.setFocused(s.portRename);
            return;
        }
        finishPortRename(true);
        portRenameDim = port.dimension();
        s.portRenamePos = port.pos();
        s.portRename.setValue(port.name() == null ? "" : port.name());
        s.portRename.setCursorPosition(s.portRename.getValue().length());
        s.portRename.setHighlightPos(0);
        s.portRename.setVisible(true);
        s.portRename.setFocused(true);
        s.setFocused(s.portRename);
    }

    void finishPortRename(boolean send) {
        if (s.portRenamePos == null || s.portRename == null) {
            return;
        }
        String dim = portRenameDim;
        BlockPos pos = s.portRenamePos;
        String value = s.portRename.getValue();
        s.portRenamePos = null;
        portRenameDim = "";
        s.portRename.setFocused(false);
        s.portRename.setVisible(false);
        if (s.getFocused() == s.portRename) {
            s.setFocused(null);
        }
        if (send) {
            PacketDistributor.sendToServer(new BlueprintPayloads.RenameWrightport(dim, pos, value));
        }
    }

    private void drawManageTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = s.portBarY();
        int x = portBarX();
        int personalW = manageTabWidth(0);
        drawManageTab(graphics, mouseX, mouseY, x, y, personalW, 0);
        drawManageTab(graphics, mouseX, mouseY, x + personalW + 4, y, manageTabWidth(1), 1);
        if (s.manageTab == 1 && !WrightsClientState.markedPorts.isEmpty()) {
            int width = s.jobButtonWidth("menu.wrights.clear_highlights");
            s.drawJobButton(graphics, x + portBarW(), y, "menu.wrights.clear_highlights", mouseX, mouseY, width, 0xFFF2D48A);
        }
    }

    boolean clickClearPortHighlights(double mouseX, double mouseY) {
        if (s.manageTab != 1 || WrightsClientState.markedPorts.isEmpty()) {
            return false;
        }
        int width = s.jobButtonWidth("menu.wrights.clear_highlights");
        if (!hitWide(mouseX, mouseY, portBarX() + portBarW(), s.portBarY(), width)) {
            return false;
        }
        WrightsClientState.markedPorts.clear();
        return true;
    }

    private void drawManageTab(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int index) {
        boolean on = s.manageTab == index;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        WrightsUi.choice(graphics, x, y, w, 16, on, hover);
        String label = s.font().plainSubstrByWidth(Component.translatable(manageTabKey(index)).getString(), w - 8);
        graphics.drawString(s.font(), label, x + (w - s.font().width(label)) / 2, y + 4, on ? WrightsUi.TEXT : WrightsUi.MUTED, false);
    }

    private int manageTabWidth(int index) {
        return s.font().width(Component.translatable(manageTabKey(index))) + 16;
    }

    private String manageTabKey(int index) {
        return index == 0 ? "menu.wrights.tab_personal" : "menu.wrights.tab_wrightports";
    }

    boolean clickManageTabs(double mouseX, double mouseY) {
        int y = s.portBarY();
        if (mouseY < y || mouseY >= y + 16) {
            return false;
        }
        int personalW = manageTabWidth(0);
        int x = portBarX();
        if (mouseX >= x && mouseX < x + personalW) {
            if (s.manageTab != 0) {
                finishPortRename(true);
                s.manageTab = 0;
                s.portListOpen = false;
                s.manage.resetView();
                s.sendHold(true);
                s.refresh();
            }
            return true;
        }
        int placedX = x + personalW + 4;
        int placedW = manageTabWidth(1);
        if (mouseX >= placedX && mouseX < placedX + placedW) {
            if (s.manageTab != 1) {
                s.manageTab = 1;
                s.portListOpen = false;
                s.manage.resetView();
                s.sendHold(false);
                PacketDistributor.sendToServer(new BlueprintPayloads.PlacedPortsAsk());
                if (s.placedPick != null) {
                    WrightsClientState.placedKnown = false;
                    PacketDistributor.sendToServer(new BlueprintPayloads.PlacedSwarmAsk(s.placedPick.dimension(), s.placedPick.pos()));
                }
                s.refresh();
            }
            return true;
        }
        return false;
    }

    boolean clickPlacedList(double mouseX, double mouseY) {
        List<BlueprintPayloads.PlacedPort> list = WrightsClientState.placedPorts;
        int shown = s.placedShown();
        if (shown <= 0) {
            return false;
        }
        int x = portBarX();
        int w = portBarW();
        int top = s.placedListTop();
        if (mouseX < x || mouseX >= x + w || mouseY < top || mouseY >= top + shown * 18) {
            return false;
        }
        int row = (int) ((mouseY - top) / 18);
        int index = s.placedScroll + row;
        if (index < 0 || index >= list.size()) {
            return true;
        }
        BlueprintPayloads.PlacedPort port = list.get(index);
        if (s.portRename != null && s.portRename.visible && s.portRename.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int right = x + w - 4;
        int buttonY = top + row * 18 + 1;
        if (s.creativeVisit()) {
            int goW = s.jobButtonWidth("menu.wrights.visit");
            if (hitWide(mouseX, mouseY, right, buttonY, goW)) {
                if (s.requireTool()) {
                    pickPlaced(port);
                    PacketDistributor.sendToServer(new BlueprintPayloads.PlacedVisit(port.dimension(), port.pos()));
                }
                return true;
            }
            right -= goW + 4;
        }
        int renameW = s.jobButtonWidth("menu.wrights.rename");
        if (hitWide(mouseX, mouseY, right, buttonY, renameW)) {
            beginPortRename(port);
            return true;
        }
        right -= renameW + 4;
        boolean marked = WrightsClientState.portMarked(port.dimension(), port.pos());
        String markKey = marked ? "menu.wrights.mark_on" : "menu.wrights.mark";
        int markW = s.jobButtonWidth(markKey);
        if (hitWide(mouseX, mouseY, right, buttonY, markW)) {
            WrightsClientState.togglePortMark(port.dimension(), port.pos());
            return true;
        }
        pickPlaced(port);
        return true;
    }

    private void pickPlaced(BlueprintPayloads.PlacedPort port) {
        finishPortRename(true);
        if (!samePort(port, s.placedPick)) {
            s.manage.resetView();
        }
        s.placedPick = port;
        WrightsClientState.placedKnown = false;
        PacketDistributor.sendToServer(new BlueprintPayloads.PlacedSwarmAsk(port.dimension(), port.pos()));
        s.refresh();
    }

    private void drawPortBar(GuiGraphics graphics, List<WrightportPlaces.Spot> spots, WrightportPlaces.Spot spot, int mouseX, int mouseY) {
        int x = portBarX();
        int y = chooserY();
        int w = portBarW();
        boolean open = s.portListOpen;
        boolean hover = spots.size() > 1 && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        WrightsUi.choice(graphics, x, y, w, 16, open, hover);
        String text = s.font().plainSubstrByWidth(s.portLabel(spots, spot).getString(), Math.max(0, w - (spots.size() > 1 ? 18 : 8)));
        graphics.drawString(s.font(), text, x + 6, y + 4, WrightsUi.TEXT, false);
        if (spots.size() > 1) {
            graphics.drawString(s.font(), open ? "^" : "v", x + w - 12, y + 4, WrightsUi.MUTED, false);
        }
    }

    private void drawPortList(GuiGraphics graphics, List<WrightportPlaces.Spot> spots, int mouseX, int mouseY) {
        int x = portBarX();
        int y = chooserY() + 16;
        int w = portBarW();
        int rows = Math.min(WrightsMenuScreen.PORT_ROWS, spots.size());
        int h = rows * 16;
        graphics.fill(x, y, x + w, y + h, 0xFF121A22);
        WrightsUi.border(graphics, x, y, w, h, WrightsUi.COPPER);
        int start = Mth.clamp(s.portScroll, 0, Math.max(0, spots.size() - rows));
        for (int i = 0; i < rows; i++) {
            WrightportPlaces.Spot spot = spots.get(start + i);
            int rowY = y + i * 16;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + 16;
            boolean picked = spot.kind() == s.portKind && spot.index() == s.portIndex;
            if (hover || picked) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + 16, picked ? 0xFF2A4054 : 0xFF243244);
            }
            String text = s.font().plainSubstrByWidth(s.portLabel(spots, spot).getString(), Math.max(0, w - 12));
            graphics.drawString(s.font(), text, x + 6, rowY + 4, WrightsUi.TEXT, false);
        }
    }

    boolean clickPortList(double mouseX, double mouseY) {
        List<WrightportPlaces.Spot> spots = s.ports();
        if (spots.isEmpty()) {
            s.portListOpen = false;
            return false;
        }
        if (spots.size() < 2) {
            s.portListOpen = false;
            return false;
        }
        int x = portBarX();
        int y = chooserY();
        int w = portBarW();
        if (!s.portListOpen && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16) {
            s.portListOpen = true;
            s.portScroll = 0;
            return true;
        }
        if (!s.portListOpen) {
            return false;
        }
        int rows = Math.min(WrightsMenuScreen.PORT_ROWS, spots.size());
        int listY = y + 16;
        if (mouseX >= x && mouseX < x + w && mouseY >= listY && mouseY < listY + rows * 16) {
            int row = (int) ((mouseY - listY) / 16);
            int index = s.portScroll + row;
            if (index >= 0 && index < spots.size()) {
                WrightportPlaces.Spot spot = spots.get(index);
                if (spot.kind() != s.portKind || spot.index() != s.portIndex) {
                    s.portKind = spot.kind();
                    s.portIndex = spot.index();
                    s.manage.resetView();
                    s.sendHold(true);
                }
            }
            s.portListOpen = false;
            return true;
        }
        s.portListOpen = false;
        return true;
    }
}
