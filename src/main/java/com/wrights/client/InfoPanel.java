package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import com.wrights.network.BlueprintPayloads;

/** The Info page: network and supply cards, stock panel and setup steps. */
final class InfoPanel {
    private final WrightsMenuScreen s;

    InfoPanel(WrightsMenuScreen s) {
        this.s = s;
    }

    void renderInfo(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = s.listX;
        int y = s.panelY + 40;
        int width = s.panelX + s.panelW - 16 - x;
        int gap = 8;
        int cardW = Math.max(80, (width - gap) / 2);
        int cardH = 108;
        drawNetworkCard(graphics, x, y, cardW, cardH);
        int supplyX = x + cardW + gap;
        int supplyW = width - cardW - gap;
        drawSupplyCard(graphics, supplyX, y, supplyW, cardH);
        if (s.bindInventory != null && s.bindInventory.visible) {
            // Sit on the supply card so Bind belongs to that section.
            int btnW = Math.min(supplyW - 16, Math.max(72, s.font().width(Component.translatable("menu.wrights.info_bind")) + 16));
            s.bindInventory.setX(supplyX + 8);
            s.bindInventory.setY(y + cardH - 24);
            s.bindInventory.setWidth(btnW);
            s.bindInventory.setHeight(16);
        }
        int stockY = y + cardH + 8;
        int stockH = s.panelY + s.panelH - 54 - stockY;
        if (stockH < 36) {
            return;
        }
        drawStockPanel(graphics, x, stockY, width, stockH, mouseX, mouseY);
    }

    private void drawInfoCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        WrightsUi.border(graphics, x, y, width, height, 0xFF31404E);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, WrightsUi.COPPER_DIM);
    }

    private void drawStatusPill(GuiGraphics graphics, int right, int y, String text, int fill, int ink) {
        int width = s.font().width(text) + 14;
        int left = right - width;
        graphics.fill(left, y, right, y + 11, fill);
        WrightsUi.border(graphics, left, y, width, 11, ink);
        graphics.fill(left + 4, y + 4, left + 7, y + 7, ink);
        graphics.drawString(s.font(), text, left + 10, y + 2, ink, false);
    }

    private void drawNetworkIcon(GuiGraphics graphics, int x, int y) {
        int color = WrightsUi.COPPER;
        graphics.fill(x, y + 4, x + 3, y + 7, color);
        graphics.fill(x + 8, y, x + 11, y + 3, color);
        graphics.fill(x + 8, y + 8, x + 11, y + 11, color);
        graphics.fill(x + 3, y + 5, x + 8, y + 6, color);
        graphics.fill(x + 8, y + 3, x + 9, y + 8, color);
    }

    private void drawChestIcon(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 3, x + 12, y + 11, WrightsUi.COPPER);
        graphics.fill(x + 1, y + 1, x + 11, y + 4, 0xFFC47A3A);
        graphics.fill(x + 5, y + 6, x + 7, y + 8, 0xFF101820);
    }

    private void drawNetworkCard(GuiGraphics graphics, int x, int y, int width, int height) {
        drawInfoCard(graphics, x, y, width, height);
        boolean bound = WrightsClientState.digitalBound;
        boolean online = bound && WrightsClientState.digitalOnline;
        String pill = Component.translatable(online ? "menu.wrights.info_online" : bound ? "menu.wrights.info_offline" : "menu.wrights.info_unbound").getString();
        int pillInk = online ? WrightsUi.GREEN : bound ? WrightsUi.GOLD : WrightsUi.MUTED;
        int pillFill = online ? 0xFF143028 : bound ? 0xFF3A2A14 : 0xFF1A222A;
        drawStatusPill(graphics, x + width - 8, y + 8, pill, pillFill, pillInk);
        drawNetworkIcon(graphics, x + 8, y + 8);
        int titleRoom = width - 28 - (s.font().width(pill) + 22);
        String title = s.font().plainSubstrByWidth(Component.translatable("menu.wrights.info_network").getString(), Math.max(16, titleRoom));
        graphics.drawString(s.font(), title, x + 22, y + 9, WrightsUi.TEXT, false);
        if (!bound) {
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(Component.translatable("menu.wrights.info_hold").getString(), width - 16), x + 8, y + 26, WrightsUi.MUTED, false);
            drawInfoStep(graphics, 1, "menu.wrights.info_step_rs", x + 8, y + 42, width - 16);
            drawInfoStep(graphics, 2, "menu.wrights.info_step_ae2", x + 8, y + 60, width - 16);
            return;
        }
        boolean ae2 = WrightsClientState.digitalKind == com.wrights.storage.DigitalLink.Kind.AE2.ordinal();
        String kind = Component.translatable(ae2 ? "menu.wrights.digital_ae2" : "menu.wrights.digital_rs").getString();
        graphics.drawString(s.font(), s.font().plainSubstrByWidth(kind, width - 16), x + 8, y + 28, WrightsUi.TEXT, false);
        BlockPos pos = WrightsClientState.digitalPos;
        if (pos != null) {
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(pos.getX() + ",  " + pos.getY() + ",  " + pos.getZ(), width - 16), x + 8, y + 44, WrightsUi.TEXT, false);
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(shortDimension(WrightsClientState.digitalDim), width - 16), x + 8, y + 58, WrightsUi.COPPER, false);
        }
    }

    private void drawSupplyCard(GuiGraphics graphics, int x, int y, int width, int height) {
        drawInfoCard(graphics, x, y, width, height);
        boolean bound = WrightsClientState.supplyBound && WrightsClientState.supplyPos != null;
        String pill = Component.translatable(bound ? "menu.wrights.info_linked" : "menu.wrights.info_unbound").getString();
        int pillInk = bound ? WrightsUi.GREEN : WrightsUi.MUTED;
        int pillFill = bound ? 0xFF143028 : 0xFF1A222A;
        drawStatusPill(graphics, x + width - 8, y + 8, pill, pillFill, pillInk);
        drawChestIcon(graphics, x + 8, y + 8);
        int titleRoom = width - 28 - (s.font().width(pill) + 22);
        String title = s.font().plainSubstrByWidth(Component.translatable("menu.wrights.info_supply").getString(), Math.max(16, titleRoom));
        graphics.drawString(s.font(), title, x + 24, y + 9, WrightsUi.TEXT, false);
        if (!bound) {
            List<FormattedCharSequence> lines = s.font().split(Component.translatable("menu.wrights.info_chest_how"), width - 16);
            int lineY = y + 30;
            for (int i = 0; i < lines.size() && lineY < y + height - 8; i++) {
                graphics.drawString(s.font(), lines.get(i), x + 8, lineY, WrightsUi.MUTED, false);
                lineY += 12;
            }
            return;
        }
        BlockPos chest = WrightsClientState.supplyPos;
        graphics.drawString(s.font(), s.font().plainSubstrByWidth(chest.getX() + ",  " + chest.getY() + ",  " + chest.getZ(), width - 16), x + 8, y + 32, WrightsUi.TEXT, false);
        int inside = chestItemCount();
        if (inside < 0) {
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(Component.translatable("menu.wrights.info_chest_far").getString(), width - 16), x + 8, y + 50, WrightsUi.GOLD, false);
        } else {
            String count = Component.translatable("menu.wrights.info_chest_count", compactCount(inside)).getString();
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(count, width - 16), x + 8, y + 50, inside > 0 ? WrightsUi.TEXT : WrightsUi.MUTED, false);
        }
    }

    private void drawInfoStep(GuiGraphics graphics, int number, String key, int x, int y, int width) {
        graphics.fill(x, y - 1, x + 10, y + 9, 0xFF24384A);
        String label = Integer.toString(number);
        graphics.drawString(s.font(), label, x + (10 - s.font().width(label)) / 2, y, WrightsUi.COPPER, false);
        String text = s.font().plainSubstrByWidth(Component.translatable(key).getString(), Math.max(8, width - 14));
        graphics.drawString(s.font(), text, x + 14, y, WrightsUi.MUTED, false);
    }

    private void drawStockPanel(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        WrightsUi.inset(graphics, x, y, width, height);
        graphics.drawString(s.font(), Component.translatable("menu.wrights.info_stock"), x + 8, y + 7, WrightsUi.TEXT, false);
        boolean live = WrightsClientState.digitalBound && WrightsClientState.digitalOnline;
        if (!live || WrightsClientState.digitalLines.isEmpty()) {
            String key = !WrightsClientState.digitalBound ? "menu.wrights.info_unbound_note"
                    : !WrightsClientState.digitalOnline ? "menu.wrights.info_offline_note" : "menu.wrights.digital_items_empty";
            List<FormattedCharSequence> note = s.font().split(Component.translatable(key), width - 24);
            int noteY = y + 28;
            for (FormattedCharSequence line : note) {
                if (noteY > y + height - 12) {
                    break;
                }
                graphics.drawString(s.font(), line, x + 12, noteY, WrightsUi.MUTED, false);
                noteY += 12;
            }
            s.infoMaxScroll = 0;
            return;
        }
        List<BlueprintPayloads.DigitalInfoLine> lines = new ArrayList<>(WrightsClientState.digitalLines);
        lines.sort((left, right) -> {
            int leftRank = left.stock() > 0 ? 0 : left.craftable() ? 1 : 2;
            int rightRank = right.stock() > 0 ? 0 : right.craftable() ? 1 : 2;
            if (leftRank != rightRank) {
                return leftRank - rightRank;
            }
            return Long.compare(right.stock(), left.stock());
        });
        int kinds = 0;
        int craftable = 0;
        long stored = 0L;
        for (BlueprintPayloads.DigitalInfoLine line : lines) {
            if (line.stock() > 0) {
                kinds++;
                stored += line.stock();
            }
            if (line.craftable()) {
                craftable++;
            }
        }
        String summary = Component.translatable("menu.wrights.info_summary", kinds, compactCount(stored), craftable).getString();
        int summaryW = s.font().width(summary);
        if (summaryW < width - 16) {
            graphics.drawString(s.font(), summary, x + width - 8 - summaryW, y + 7, WrightsUi.MUTED, false);
        }
        boolean showYou = width >= 250;
        int pillW = 52;
        int netW = 52;
        int youW = showYou ? 40 : 0;
        int right = x + width - 12;
        int pillX = right - pillW;
        int netX = pillX - 8 - netW;
        int youX = showYou ? netX - 6 - youW : netX;
        drawRightText(graphics, Component.translatable("menu.wrights.info_net").getString(), netX + netW, y + 22, WrightsUi.MUTED);
        if (showYou) {
            drawRightText(graphics, Component.translatable("menu.wrights.info_you").getString(), youX + youW, y + 22, WrightsUi.MUTED);
        }
        int rowH = 20;
        int bodyY = y + 34;
        int bodyH = height - 40;
        int visible = Math.max(1, bodyH / rowH);
        s.infoMaxScroll = Math.max(0, lines.size() - visible);
        s.infoScroll = Mth.clamp(s.infoScroll, 0, s.infoMaxScroll);
        graphics.enableScissor(x + 2, bodyY, x + width - 2, y + height - 4);
        boolean creative = s.minecraft().player != null && s.minecraft().player.getAbilities().instabuild;
        for (int i = s.infoScroll; i < lines.size() && i - s.infoScroll < visible; i++) {
            BlueprintPayloads.DigitalInfoLine line = lines.get(i);
            net.minecraft.world.item.Item item = net.minecraft.world.item.Item.byId(line.itemId());
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            int rowY = bodyY + (i - s.infoScroll) * rowH;
            if ((i - s.infoScroll) % 2 == 0) {
                graphics.fill(x + 4, rowY, x + width - 6, rowY + rowH - 1, 0xFF0C141C);
            }
            ItemStack stack = item.getDefaultInstance();
            int edge = line.stock() > 0 ? WrightsUi.COPPER : line.craftable() ? WrightsUi.TEAL : 0xFF31404E;
            WrightsUi.slot(graphics, x + 6, rowY, edge);
            graphics.renderItem(stack, x + 7, rowY + 1);
            int nameW = Math.max(16, youX - (x + 28) - 4);
            graphics.drawString(s.font(), s.font().plainSubstrByWidth(stack.getHoverName().getString(), nameW), x + 28, rowY + 5, WrightsUi.TEXT, false);
            if (showYou) {
                String you = creative ? "\u221e" : compactCount(carried(item));
                drawRightText(graphics, you, youX + youW, rowY + 5, carried(item) > 0 || creative ? WrightsUi.TEXT : WrightsUi.MUTED);
            }
            int countColor = line.stock() > 0 ? WrightsUi.TEXT : line.craftable() ? WrightsUi.TEAL : WrightsUi.MUTED;
            drawRightText(graphics, compactCount(line.stock()), netX + netW, rowY + 5, countColor);
            String chip = Component.translatable(line.craftable() ? "menu.wrights.info_crafts" : line.stock() > 0 ? "menu.wrights.info_stored" : "menu.wrights.info_empty_chip").getString();
            int chipInk = line.craftable() ? WrightsUi.GREEN : line.stock() > 0 ? WrightsUi.BLUE : WrightsUi.MUTED;
            int chipFill = line.craftable() ? 0xFF143028 : line.stock() > 0 ? 0xFF152433 : 0xFF1A222A;
            drawStatusPill(graphics, pillX + pillW, rowY + 4, chip, chipFill, chipInk);
        }
        graphics.disableScissor();
        if (s.infoMaxScroll > 0) {
            int trackX = x + width - 6;
            int thumb = Math.max(10, bodyH * visible / lines.size());
            int travel = Math.max(1, bodyH - thumb);
            int thumbY = bodyY + s.infoScroll * travel / s.infoMaxScroll;
            graphics.fill(trackX, bodyY, trackX + 2, bodyY + bodyH, 0xFF1A222A);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumb, WrightsUi.COPPER);
        }
    }

    private void drawRightText(GuiGraphics graphics, String text, int right, int y, int color) {
        graphics.drawString(s.font(), text, right - s.font().width(text), y, color, false);
    }

    private int carried(net.minecraft.world.item.Item item) {
        if (s.minecraft().player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : s.minecraft().player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = s.minecraft().player.getOffhandItem();
        if (offhand.is(item)) {
            count += offhand.getCount();
        }
        return count;
    }

    private int chestItemCount() {
        if (!WrightsClientState.supplyBound || WrightsClientState.supplyPos == null || s.minecraft().level == null) {
            return -1;
        }
        net.minecraft.world.level.block.entity.BlockEntity block = s.minecraft().level.getBlockEntity(WrightsClientState.supplyPos);
        if (!(block instanceof net.minecraft.world.Container container)) {
            return -1;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }

    private static String shortDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "?";
        }
        int colon = dimension.indexOf(':');
        String name = colon >= 0 ? dimension.substring(colon + 1) : dimension;
        return name.replace('_', ' ');
    }

    private static String compactCount(long count) {
        long value = Math.max(0L, count);
        if (value < 1000L) {
            return Long.toString(value);
        }
        if (value < 10_000L) {
            return (value / 1000L) + "." + ((value % 1000L) / 100L) + "k";
        }
        if (value < 1_000_000L) {
            return (value / 1000L) + "k";
        }
        return (value / 1_000_000L) + "m";
    }
}
