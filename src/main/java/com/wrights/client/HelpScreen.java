package com.wrights.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Options help book: left topic list, right scrollable guide with headings, body, and key chips.
 */
public class HelpScreen extends Screen {
    private static final int NAV_W = 148;
    private static final int ROW = 20;
    private static final int PAD = 14;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int navX;
    private int navY;
    private int navH;
    private int bodyX;
    private int bodyY;
    private int bodyW;
    private int bodyH;
    private int topic;
    private int navScroll;
    private int bodyScroll;
    private int contentH;
    private int thumbY;
    private int thumbH;
    private boolean dragging;
    private int dragGrab;
    private final List<LaidLine> laid = new ArrayList<>();

    public HelpScreen(Screen parent) {
        super(Component.translatable("help.wrights.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(740, this.width);
        panelH = WrightsUi.fitH(380, this.height);
        panelX = WrightsUi.centerX(panelW, this.width);
        panelY = WrightsUi.centerY(panelH, this.height);
        navX = panelX + PAD;
        navY = panelY + 36;
        navH = panelH - 72;
        bodyX = navX + NAV_W + 10;
        bodyY = navY;
        bodyW = panelX + panelW - PAD - bodyX;
        bodyH = navH;
        topic = Mth.clamp(topic, 0, HelpTopics.TOPICS.length - 1);
        rebuildBody();
        int closeW = Math.max(72, this.font.width(Component.translatable("menu.wrights.close")) + 16);
        addRenderableWidget(new WrightsButton(
                panelX + panelW - PAD - closeW,
                panelY + panelH - 28,
                closeW,
                16,
                Component.translatable("menu.wrights.close"),
                button -> onClose()));
        int backW = Math.max(72, this.font.width(Component.translatable("help.wrights.back")) + 16);
        addRenderableWidget(new WrightsButton(
                panelX + PAD,
                panelY + panelH - 28,
                backW,
                16,
                Component.translatable("help.wrights.back"),
                button -> onClose()));
    }

    private void rebuildBody() {
        laid.clear();
        HelpTopics.Topic current = HelpTopics.TOPICS[topic];
        int y = 0;
        int textW = Math.max(40, bodyW - 18);
        laid.add(LaidLine.key("TITLE", current.navKey(), y, 0));
        y += 16;
        Component lead = Component.translatable("help.wrights." + current.id() + ".lead");
        for (FormattedCharSequence part : this.font.split(lead, textW)) {
            laid.add(LaidLine.seq("LEAD", part, y, 0));
            y += 11;
        }
        y += 8;
        for (HelpTopics.Line line : HelpTopics.lines(current.id())) {
            if ("H".equals(line.kind())) {
                y += 4;
                laid.add(LaidLine.key("H", line.text(), y, 0));
                y += 16;
                continue;
            }
            if ("K".equals(line.kind())) {
                y = layKeys(line.text(), y, textW);
                y += 6;
                continue;
            }
            if ("T".equals(line.kind())) {
                y += 2;
                Component tip = Component.translatable(line.text());
                for (FormattedCharSequence part : this.font.split(tip, Math.max(40, textW - 12))) {
                    laid.add(LaidLine.seq("T", part, y, 0));
                    y += 11;
                }
                y += 8;
                continue;
            }
            Component body = Component.translatable(line.text());
            for (FormattedCharSequence part : this.font.split(body, textW)) {
                laid.add(LaidLine.seq("P", part, y, 0));
                y += 11;
            }
            y += 6;
        }
        contentH = y + 8;
        bodyScroll = Mth.clamp(bodyScroll, 0, maxBodyScroll());
        updateThumb();
    }

    private int layKeys(String raw, int y, int textW) {
        String[] parts = raw.split("\\|");
        int x = 0;
        int rowH = 16;
        for (String part : parts) {
            String label = part.trim();
            if (label.isEmpty()) {
                continue;
            }
            int chipW = this.font.width(label) + 10;
            if (x > 0 && x + chipW > textW) {
                x = 0;
                y += rowH + 4;
            }
            laid.add(LaidLine.key("K", label, y, x));
            x += chipW + 4;
        }
        return y + rowH;
    }

    private int maxBodyScroll() {
        return Math.max(0, contentH - bodyH);
    }

    private int maxNavScroll() {
        return Math.max(0, HelpTopics.TOPICS.length * ROW - navH);
    }

    private void updateThumb() {
        int max = maxBodyScroll();
        if (max <= 0) {
            thumbH = bodyH;
            thumbY = bodyY;
            return;
        }
        thumbH = Math.max(18, bodyH * bodyH / Math.max(bodyH, contentH));
        int travel = Math.max(1, bodyH - thumbH);
        thumbY = bodyY + bodyScroll * travel / max;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        // Soft inset for the guide page.
        WrightsUi.inset(graphics, bodyX - 4, bodyY - 4, bodyW + 8, bodyH + 8);
        WrightsUi.inset(graphics, navX - 2, navY - 2, NAV_W + 4, navH + 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + PAD, panelY + 10, WrightsUi.COPPER, false);
        Component sub = Component.translatable("help.wrights.subtitle");
        graphics.drawString(this.font, this.font.plainSubstrByWidth(sub.getString(), panelW - 28),
                panelX + PAD, panelY + 22, WrightsUi.MUTED, false);

        drawNav(graphics, mouseX, mouseY);
        drawBody(graphics);
        if (maxBodyScroll() > 0) {
            int barX = bodyX + bodyW - 3;
            graphics.fill(barX, bodyY, barX + 2, bodyY + bodyH, 0xFF1A2836);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, WrightsUi.COPPER);
        }
    }

    private void drawNav(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(navX, navY, navX + NAV_W, navY + navH);
        for (int i = 0; i < HelpTopics.TOPICS.length; i++) {
            int y = navY + i * ROW - navScroll;
            if (y + ROW < navY || y > navY + navH) {
                continue;
            }
            boolean on = i == topic;
            boolean hover = mouseX >= navX && mouseX < navX + NAV_W && mouseY >= y && mouseY < y + ROW - 1;
            WrightsUi.rail(graphics, navX, y, NAV_W, ROW - 2, on, on ? WrightsUi.TEAL : WrightsUi.COPPER);
            Component label = Component.translatable(HelpTopics.TOPICS[i].navKey());
            int color = on ? WrightsUi.TEXT : (hover ? WrightsUi.TEXT : WrightsUi.MUTED);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), NAV_W - 14),
                    navX + 8, y + 5, color, false);
        }
        graphics.disableScissor();
        if (maxNavScroll() > 0) {
            WrightsUi.scrollBar(graphics, navX + NAV_W - 3, navY, navH, navScroll, HelpTopics.TOPICS.length * ROW);
        }
    }

    private void drawBody(GuiGraphics graphics) {
        graphics.enableScissor(bodyX, bodyY, bodyX + bodyW - 6, bodyY + bodyH);
        for (LaidLine line : laid) {
            int y = bodyY + line.y() - bodyScroll;
            if (y + 16 < bodyY || y > bodyY + bodyH) {
                continue;
            }
            switch (line.kind()) {
                case "TITLE" -> {
                    Component title = Component.translatable(line.key());
                    graphics.drawString(this.font, title, bodyX, y, WrightsUi.TEXT, false);
                    graphics.fill(bodyX, y + 12, bodyX + Math.min(bodyW - 10, this.font.width(title) + 8), y + 13, WrightsUi.COPPER_DIM);
                }
                case "LEAD" -> graphics.drawString(this.font, line.sequence(), bodyX, y, WrightsUi.MUTED);
                case "H" -> {
                    Component heading = Component.translatable(line.key());
                    WrightsUi.status(graphics, bodyX, y - 1, bodyW - 8, 14, WrightsUi.COPPER);
                    graphics.drawString(this.font, heading, bodyX + 8, y + 2, WrightsUi.COPPER, false);
                }
                case "P" -> graphics.drawString(this.font, line.sequence(), bodyX, y, WrightsUi.TEXT);
                case "T" -> {
                    graphics.fill(bodyX, y - 1, bodyX + 2, y + 10, WrightsUi.BLUE);
                    graphics.drawString(this.font, line.sequence(), bodyX + 6, y, WrightsUi.BLUE);
                }
                case "K" -> {
                    int chipW = this.font.width(line.key()) + 10;
                    int x = bodyX + line.x();
                    graphics.fill(x, y, x + chipW, y + 14, 0xFF152029);
                    WrightsUi.border(graphics, x, y, chipW, 14, 0xFF3A4C5C);
                    graphics.fill(x, y, x + chipW, y + 1, WrightsUi.COPPER_DIM);
                    graphics.drawCenteredString(this.font, line.key(), x + chipW / 2, y + 3, WrightsUi.SIZE);
                }
                default -> {
                }
            }
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= navX && mouseX < navX + NAV_W && mouseY >= navY && mouseY < navY + navH) {
            int index = (int) ((mouseY - navY + navScroll) / ROW);
            if (index >= 0 && index < HelpTopics.TOPICS.length) {
                topic = index;
                bodyScroll = 0;
                rebuildBody();
                return true;
            }
        }
        if (button == 0 && maxBodyScroll() > 0
                && mouseX >= bodyX + bodyW - 5 && mouseX < bodyX + bodyW
                && mouseY >= thumbY && mouseY < thumbY + thumbH) {
            dragging = true;
            dragGrab = (int) mouseY - thumbY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            int max = maxBodyScroll();
            int travel = Math.max(1, bodyH - thumbH);
            int next = (int) mouseY - bodyY - dragGrab;
            bodyScroll = Mth.clamp(next * max / travel, 0, max);
            updateThumb();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY) * -12;
        if (mouseX >= navX && mouseX < navX + NAV_W && mouseY >= navY && mouseY < navY + navH) {
            navScroll = Mth.clamp(navScroll + step, 0, maxNavScroll());
            return true;
        }
        if (mouseX >= bodyX - 4 && mouseX < bodyX + bodyW + 4 && mouseY >= bodyY - 4 && mouseY < bodyY + bodyH + 4) {
            bodyScroll = Mth.clamp(bodyScroll + step, 0, maxBodyScroll());
            updateThumb();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && parent != null) {
            this.minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record LaidLine(String kind, String key, FormattedCharSequence sequence, int y, int x) {
        static LaidLine key(String kind, String key, int y, int x) {
            return new LaidLine(kind, key, FormattedCharSequence.EMPTY, y, x);
        }

        static LaidLine seq(String kind, FormattedCharSequence sequence, int y, int x) {
            return new LaidLine(kind, "", sequence, y, x);
        }
    }
}
