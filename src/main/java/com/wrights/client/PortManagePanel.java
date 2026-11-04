package com.wrights.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.Loadout;
import com.wrights.drone.PartSlot;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;
import com.wrights.drone.WrightportPlaces;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.network.BlueprintPayloads;

/**
 * Manage page: personal / placed wrightports as lists, with per-wright status and send-home.
 * Hangar upgrades and inventory edits happen only on the hangar screens.
 */
final class PortManagePanel {
    private static final int ROW = 18;
    private static final int WRIGHT_ROW = 28;
    private static final UUID HOME = new UUID(0L, 0L);

    private final WrightsMenuScreen s;
    private String portRenameDim = "";
    private int wrightScroll;

    PortManagePanel(WrightsMenuScreen s) {
        this.s = s;
    }

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

    void renderManage(GuiGraphics graphics, int mouseX, int mouseY) {
        drawManageTabs(graphics, mouseX, mouseY);
        if (s.manageTab == 0) {
            renderPersonal(graphics, mouseX, mouseY);
        } else {
            renderPlaced(graphics, mouseX, mouseY);
        }
    }

    private void renderPersonal(GuiGraphics graphics, int mouseX, int mouseY) {
        List<WrightportPlaces.Spot> spots = s.ports();
        int top = s.placedListTop();
        int x = portBarX();
        int w = portBarW();
        if (spots.isEmpty()) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_none"), x, top + 4, WrightsUi.MUTED, false);
            graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_hangar_hint"), x, top + 18, WrightsUi.MUTED, false);
            return;
        }
        ensurePersonalPick(spots);
        int shown = Math.min(spots.size(), personalShown(spots.size()));
        s.portScroll = Mth.clamp(s.portScroll, 0, Math.max(0, spots.size() - shown));
        int h = shown * ROW;
        graphics.fill(x, top, x + w, top + h, 0xFF121A22);
        WrightsUi.border(graphics, x, top, w, h, 0xFF3D4E5E);
        for (int i = 0; i < shown; i++) {
            WrightportPlaces.Spot spot = spots.get(s.portScroll + i);
            int rowY = top + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW;
            boolean picked = spot.kind() == s.portKind && spot.index() == s.portIndex;
            if (hover || picked) {
                graphics.fill(x + 1, rowY + 1, x + w - 1, rowY + ROW - 1, picked ? 0xFF2A4054 : 0xFF243244);
            }
            String text = s.font().plainSubstrByWidth(s.portLabel(spots, spot).getString(), Math.max(0, w - 12));
            graphics.drawString(s.font(), text, x + 6, rowY + 5, WrightsUi.TEXT, false);
        }
        WrightportPlaces.Spot spot = s.currentSpot();
        if (spot == null) {
            return;
        }
        int detailTop = top + h + 8;
        drawPersonalEnergy(graphics, detailTop, mouseX, mouseY);
        Swarm swarm = personalSwarm(spot);
        drawWrightList(graphics, swarm, detailTop + 14, mouseX, mouseY);
    }

    private void drawPersonalEnergy(GuiGraphics graphics, int y, int mouseX, int mouseY) {
        if (s.minecraft() == null || s.minecraft().player == null) {
            return;
        }
        WrightportPlaces.Spot spot = s.currentSpot();
        if (spot == null) {
            return;
        }
        ItemStack stack = WrightportPlaces.stack(s.minecraft().player, spot.kind(), spot.index());
        long stored = com.wrights.energy.PortEnergy.itemStored(stack);
        long capacity = com.wrights.WrightsConfig.portEnergyCapacity();
        int barX = portBarX();
        int barW = portBarW();
        boolean on = WrightportBounds.personalShowing();
        String key = on ? "menu.wrights.boundary_on" : "menu.wrights.boundary";
        int btnW = s.jobButtonWidth(key);
        WrightsUi.energyBar(graphics, s.font(), barX, y, Math.max(40, barW - btnW - 6), stored, capacity);
        s.drawJobButton(graphics, barX + barW, y - 1, key, mouseX, mouseY, btnW, on ? 0xFFF2D48A : 0);
    }

    private Swarm personalSwarm(WrightportPlaces.Spot spot) {
        if (s.minecraft() == null || s.minecraft().player == null) {
            return Swarm.EMPTY;
        }
        ItemStack stack = WrightportPlaces.stack(s.minecraft().player, spot.kind(), spot.index());
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            return Swarm.EMPTY;
        }
        return Swarm.from(stack);
    }

    private void ensurePersonalPick(List<WrightportPlaces.Spot> spots) {
        for (WrightportPlaces.Spot spot : spots) {
            if (spot.kind() == s.portKind && spot.index() == s.portIndex) {
                return;
            }
        }
        WrightportPlaces.Spot first = spots.get(0);
        s.portKind = first.kind();
        s.portIndex = first.index();
        wrightScroll = 0;
    }

    private int personalShown(int count) {
        int room = s.manageY() + s.manageH() - s.placedListTop() - 8;
        // Leave room for energy + at least a couple wright rows when a port is selected.
        int fit = Math.max(1, (room - 14 - WRIGHT_ROW * 2) / ROW);
        return Math.min(count, Math.min(4, fit));
    }

    private void renderPlaced(GuiGraphics graphics, int mouseX, int mouseY) {
        List<BlueprintPayloads.PlacedPort> list = WrightsClientState.placedPorts;
        int top = s.placedListTop();
        int x = portBarX();
        int w = portBarW();
        if (list.isEmpty()) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.placed_none"), x, top + 4, WrightsUi.MUTED, false);
            return;
        }
        int shown = s.placedShown();
        s.placedScroll = Mth.clamp(s.placedScroll, 0, Math.max(0, list.size() - shown));
        int h = shown * ROW;
        graphics.fill(x, top, x + w, top + h, 0xFF121A22);
        WrightsUi.border(graphics, x, top, w, h, 0xFF3D4E5E);
        for (int i = 0; i < shown; i++) {
            BlueprintPayloads.PlacedPort port = list.get(s.placedScroll + i);
            int rowY = top + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW;
            boolean picked = samePort(port, s.placedPick);
            if (hover || picked) {
                graphics.fill(x + 1, rowY + 1, x + w - 1, rowY + ROW - 1, picked ? 0xFF2A4054 : 0xFF243244);
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
            return;
        }
        if (s.placedReady()) {
            drawWrightList(graphics, WrightsClientState.placedSwarm, top + h + 8, mouseX, mouseY);
        } else if (s.placedPick == null) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_pick_port"), x, top + h + 8, WrightsUi.MUTED, false);
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
            return withPortName(port, Component.translatable("menu.wrights.placed_line", port.pos().getX(), port.pos().getY(), port.pos().getZ(), units));
        }
        String dim = port.dimension();
        int colon = dim.indexOf(':');
        if (colon >= 0 && colon + 1 < dim.length()) {
            dim = dim.substring(colon + 1);
        }
        return withPortName(port, Component.translatable("menu.wrights.placed_line_dim", port.pos().getX(), port.pos().getY(), port.pos().getZ(), units, dim));
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
                wrightScroll = 0;
                s.refresh();
            }
            return true;
        }
        int placedX = x + personalW + 4;
        int placedW = manageTabWidth(1);
        if (mouseX >= placedX && mouseX < placedX + placedW) {
            if (s.manageTab != 1) {
                s.manageTab = 1;
                wrightScroll = 0;
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
        if (mouseX < x || mouseX >= x + w || mouseY < top || mouseY >= top + shown * ROW) {
            return clickWrightList(mouseX, mouseY);
        }
        int row = (int) ((mouseY - top) / ROW);
        int index = s.placedScroll + row;
        if (index < 0 || index >= list.size()) {
            return true;
        }
        BlueprintPayloads.PlacedPort port = list.get(index);
        if (s.portRename != null && s.portRename.visible && s.portRename.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int right = x + w - 4;
        int buttonY = top + row * ROW + 1;
        if (s.creativeVisit()) {
            int goW = s.jobButtonWidth("menu.wrights.visit");
            if (hitWide(mouseX, mouseY, right, buttonY, goW)) {
                if (s.requireTool()) {
                    openPlaced(port);
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
        togglePlaced(port);
        return true;
    }

    private void togglePlaced(BlueprintPayloads.PlacedPort port) {
        if (samePort(port, s.placedPick)) {
            finishPortRename(true);
            s.placedPick = null;
            wrightScroll = 0;
            s.refresh();
            return;
        }
        openPlaced(port);
    }

    private void openPlaced(BlueprintPayloads.PlacedPort port) {
        finishPortRename(true);
        s.placedPick = port;
        wrightScroll = 0;
        WrightsClientState.placedKnown = false;
        PacketDistributor.sendToServer(new BlueprintPayloads.PlacedSwarmAsk(port.dimension(), port.pos()));
        s.refresh();
    }

    boolean clickPortList(double mouseX, double mouseY) {
        List<WrightportPlaces.Spot> spots = s.ports();
        if (spots.isEmpty()) {
            return false;
        }
        int x = portBarX();
        int w = portBarW();
        int top = s.placedListTop();
        int shown = Math.min(spots.size(), personalShown(spots.size()));
        if (mouseX >= x && mouseX < x + w && mouseY >= top && mouseY < top + shown * ROW) {
            int row = (int) ((mouseY - top) / ROW);
            int index = s.portScroll + row;
            if (index >= 0 && index < spots.size()) {
                WrightportPlaces.Spot spot = spots.get(index);
                s.portKind = spot.kind();
                s.portIndex = spot.index();
                wrightScroll = 0;
            }
            return true;
        }
        if (clickPersonalBoundary(mouseX, mouseY, top, shown)) {
            return true;
        }
        return clickWrightList(mouseX, mouseY);
    }

    private boolean clickPersonalBoundary(double mouseX, double mouseY, int listTop, int shown) {
        if (shown <= 0 || s.currentSpot() == null) {
            return false;
        }
        int y = listTop + shown * ROW + 8 - 1;
        boolean on = WrightportBounds.personalShowing();
        String key = on ? "menu.wrights.boundary_on" : "menu.wrights.boundary";
        int btnW = s.jobButtonWidth(key);
        if (!hitWide(mouseX, mouseY, portBarX() + portBarW(), y, btnW)) {
            return false;
        }
        WrightportBounds.togglePersonal();
        return true;
    }

    private int wrightListTopPersonal() {
        List<WrightportPlaces.Spot> spots = s.ports();
        int shown = Math.min(spots.size(), personalShown(spots.size()));
        return s.placedListTop() + shown * ROW + 8 + 14;
    }

    private int wrightListTopPlaced() {
        return s.placedListTop() + s.placedShown() * ROW + 8;
    }

    private int currentWrightListTop() {
        return s.manageTab == 0 ? wrightListTopPersonal() : wrightListTopPlaced();
    }

    private Swarm currentSwarm() {
        if (s.manageTab == 1) {
            return s.placedReady() ? WrightsClientState.placedSwarm : Swarm.EMPTY;
        }
        WrightportPlaces.Spot spot = s.currentSpot();
        return spot == null ? Swarm.EMPTY : personalSwarm(spot);
    }

    private void drawWrightList(GuiGraphics graphics, Swarm swarm, int top, int mouseX, int mouseY) {
        int x = portBarX();
        int w = portBarW();
        int bottom = s.manageY() + s.manageH() - 4;
        int header = top;
        graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_wrights", swarm.units().size()), x, header, WrightsUi.COPPER, false);
        int listTop = header + 12;
        int listH = Math.max(0, bottom - listTop);
        if (swarm.units().isEmpty()) {
            graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_wrights_empty"), x, listTop + 2, WrightsUi.MUTED, false);
            graphics.drawString(s.font(), Component.translatable("menu.wrights.manage_hangar_hint"), x, listTop + 14, WrightsUi.MUTED, false);
            return;
        }
        int shown = Math.max(1, listH / WRIGHT_ROW);
        int maxScroll = Math.max(0, swarm.units().size() - shown);
        wrightScroll = Mth.clamp(wrightScroll, 0, maxScroll);
        int homeBtnW = s.jobButtonWidth("menu.wrights.wright_send_home");
        for (int i = 0; i < shown && wrightScroll + i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(wrightScroll + i);
            int rowY = listTop + i * WRIGHT_ROW;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + WRIGHT_ROW;
            if (hover) {
                graphics.fill(x, rowY, x + w, rowY + WRIGHT_ROW - 1, 0xFF1C2C3A);
            } else if ((i & 1) == 0) {
                graphics.fill(x, rowY, x + w, rowY + WRIGHT_ROW - 1, 0xFF101820);
            }
            BlueprintPayloads.JobLine job = jobOf(unit.id());
            boolean busy = job != null;
            int right = x + w - 4;
            if (busy && s.toolHeld()) {
                right = s.drawJobButton(graphics, right, rowY + 6, "menu.wrights.wright_send_home", mouseX, mouseY, homeBtnW, 0xFFE07A7A, true);
            }
            String name = unit.title(wrightScroll + i + 1).getString();
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(name, Math.max(40, right - x - 10)), x + 4, rowY + 3, WrightsUi.TEXT, false);
            drawParts(graphics, unit.parts(), x + 4, rowY + 14, Math.max(40, right - x - 10));
            Component status = statusOf(unit.id(), job);
            int statusW = Math.min(s.font().width(status), Math.max(40, right - x - 80));
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(status.getString(), statusW), right - statusW, rowY + 3, busy ? WrightsUi.COPPER : WrightsUi.GREEN, false);
        }
    }

    private void drawParts(GuiGraphics graphics, Loadout parts, int x, int y, int maxW) {
        int cursor = x;
        PartSlot[] slots = PartSlot.values();
        for (int i = 0; i < slots.length; i++) {
            int tier = parts.tier(slots[i]);
            String label = Component.translatable(WrightTier.nameKey(tier)).getString();
            if (i > 0) {
                String sep = " · ";
                if (cursor + s.font().width(sep) > x + maxW) {
                    break;
                }
                graphics.drawString(s.font(), sep, cursor, y, WrightsUi.MUTED, false);
                cursor += s.font().width(sep);
            }
            int tw = s.font().width(label);
            if (cursor + tw > x + maxW) {
                break;
            }
            graphics.drawString(s.font(), label, cursor, y, WrightTier.textColor(tier), false);
            cursor += tw;
        }
    }

    private Component statusOf(UUID id, BlueprintPayloads.JobLine job) {
        if (job != null) {
            String name = job.name() == null || job.name().isBlank()
                    ? Component.translatable("menu.wrights.job").getString()
                    : job.name();
            return Component.translatable("menu.wrights.wright_on_job", name);
        }
        BlueprintPayloads.WrightSpot spot = WrightsClientState.wrightSpot(id);
        if (spot != null && spot.state() == 1) {
            return Component.translatable("menu.wrights.wright_flying");
        }
        return Component.translatable("menu.wrights.wright_home");
    }

    private BlueprintPayloads.JobLine jobOf(UUID id) {
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!job.finished() && job.crew().contains(id)) {
                return job;
            }
        }
        return null;
    }

    private boolean clickWrightList(double mouseX, double mouseY) {
        Swarm swarm = currentSwarm();
        if (swarm.units().isEmpty()) {
            return false;
        }
        int x = portBarX();
        int w = portBarW();
        int listTop = currentWrightListTop() + 12;
        int bottom = s.manageY() + s.manageH() - 4;
        int listH = Math.max(0, bottom - listTop);
        int shown = Math.max(1, listH / WRIGHT_ROW);
        if (mouseX < x || mouseX >= x + w || mouseY < listTop || mouseY >= listTop + shown * WRIGHT_ROW) {
            return false;
        }
        int row = (int) ((mouseY - listTop) / WRIGHT_ROW);
        int index = wrightScroll + row;
        if (index < 0 || index >= swarm.units().size()) {
            return true;
        }
        Swarm.Unit unit = swarm.units().get(index);
        BlueprintPayloads.JobLine job = jobOf(unit.id());
        if (job == null || !s.requireTool()) {
            return true;
        }
        int homeBtnW = s.jobButtonWidth("menu.wrights.wright_send_home");
        int rowY = listTop + row * WRIGHT_ROW;
        if (hitWide(mouseX, mouseY, x + w - 4, rowY + 6, homeBtnW)) {
            PacketDistributor.sendToServer(new BlueprintPayloads.MoveDrone(unit.id(), HOME));
            return true;
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int x = portBarX();
        int w = portBarW();
        if (mouseX >= x && mouseX < x + w) {
            if (s.manageTab == 0) {
                List<WrightportPlaces.Spot> spots = s.ports();
                int shown = Math.min(spots.size(), personalShown(spots.size()));
                int top = s.placedListTop();
                if (shown > 0 && mouseY >= top && mouseY < top + shown * ROW) {
                    int max = Math.max(0, spots.size() - shown);
                    s.portScroll = Mth.clamp(s.portScroll - (int) Math.signum(scrollY), 0, max);
                    return true;
                }
            } else {
                int shown = s.placedShown();
                int top = s.placedListTop();
                if (shown > 0 && mouseY >= top && mouseY < top + shown * ROW) {
                    finishPortRename(true);
                    int max = Math.max(0, WrightsClientState.placedPorts.size() - shown);
                    s.placedScroll = Mth.clamp(s.placedScroll - (int) Math.signum(scrollY), 0, max);
                    return true;
                }
            }
        }
        Swarm swarm = currentSwarm();
        if (swarm.units().isEmpty()) {
            return false;
        }
        int listTop = currentWrightListTop();
        int bottom = s.manageY() + s.manageH() - 4;
        if (mouseX < x || mouseX >= x + w || mouseY < listTop || mouseY >= bottom) {
            return false;
        }
        int shown = Math.max(1, (bottom - (listTop + 12)) / WRIGHT_ROW);
        int max = Math.max(0, swarm.units().size() - shown);
        wrightScroll = Mth.clamp(wrightScroll - (int) Math.signum(scrollY), 0, max);
        return true;
    }
}
