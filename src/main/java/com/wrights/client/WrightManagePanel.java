package com.wrights.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.wrights.drone.HangarOps;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;
import com.wrights.item.ModItems;
import com.wrights.item.WrightItem;
import com.wrights.network.BlueprintPayloads;

/**
 * Wright list for one personal wrightport. Only wrights still in the port are
 * shown; ones out on a job stay off this list.
 */
final class WrightManagePanel {
    private static final int ROW = 16;
    private static final int EXPAND = 82;
    private static final int NONE = 0;
    private static final int ADD = 1;
    private static final int MARKED = 2;

    private WrightsButton addButton;
    private WrightsButton removeButton;
    private WrightsButton upgradeButton;
    private WrightsButton backButton;
    private EditBox countBox;
    private EditBox nameBox;
    private String sentWrightName = "";
    private int nameFor = -1;

    private boolean open;
    private int scroll;
    private int expanded = -1;
    private int dialog;
    private final Set<UUID> checked = new HashSet<>();
    private int panelX;
    private int panelY;
    private int panelW = 360;
    private int panelH;
    private int headExtra;
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    boolean isOpen() {
        return open;
    }

    boolean closeTop() {
        if (dialog == NONE) {
            return false;
        }
        dialog = NONE;
        if (countBox != null) {
            countBox.setVisible(false);
            countBox.setFocused(false);
        }
        return true;
    }

    void setActive(boolean active) {
        if (active) {
            if (!open) {
                open = true;
                scroll = 0;
                expanded = -1;
                dialog = NONE;
                checked.clear();
            }
            return;
        }
        open = false;
        dialog = NONE;
        if (countBox != null) {
            countBox.setVisible(false);
            countBox.setFocused(false);
        }
        hideChrome();
    }

    void resetView() {
        scroll = 0;
        expanded = -1;
        dialog = NONE;
        checked.clear();
        if (countBox != null) {
            countBox.setVisible(false);
            countBox.setFocused(false);
        }
    }

    void toggle(ManageHost screen) {
        open = !open;
        scroll = 0;
        expanded = -1;
        dialog = NONE;
        checked.clear();
        screen.relayout();
    }

    void close(ManageHost screen) {
        if (!open) {
            return;
        }
        open = false;
        dialog = NONE;
        screen.relayout();
    }

    void addWidgets(ManageHost screen) {
        place(screen);
        upgradeButton = new WrightsButton(panelX + 12, panelY + panelH - 54, panelW - 24, 16, Component.translatable("screen.wrights.manage_upgrade_marked"), button -> openMarked());
        upgradeButton.active = false;
        screen.addButton(upgradeButton);
        addButton = new WrightsButton(panelX + 12, panelY + panelH - 32, 100, 16, Component.translatable("screen.wrights.manage_add"), button -> openAdd(screen));
        screen.addButton(addButton);
        removeButton = new WrightsButton(panelX + 120, panelY + panelH - 32, 128, 16, Component.translatable("screen.wrights.manage_remove"), button -> removeSelected(screen), true);
        removeButton.active = false;
        screen.addButton(removeButton);
        backButton = new WrightsButton(panelX + 256, panelY + panelH - 32, 92, 16, Component.translatable("screen.wrights.back"), button -> close(screen));
        screen.addButton(backButton);
        countBox = new EditBox(screen.font(), 0, 0, 80, 16, Component.translatable("screen.wrights.manage_add_count"));
        countBox.setMaxLength(4);
        countBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        countBox.setVisible(false);
        screen.addField(countBox);
        nameBox = new EditBox(screen.font(), 0, 0, 120, 14, Component.translatable("screen.wrights.name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("screen.wrights.name"));
        nameBox.setVisible(false);
        screen.addField(nameBox);
        layoutChrome(screen);
    }

    void layoutChrome(ManageHost screen) {
        place(screen);
        if (upgradeButton != null) {
            upgradeButton.setX(panelX + 12);
            upgradeButton.setY(panelY + panelH - 54);
            upgradeButton.setWidth(Math.max(40, panelW - 24));
        }
        int y = panelY + panelH - 32;
        if (addButton != null) {
            addButton.setX(panelX + 12);
            addButton.setY(y);
        }
        if (removeButton != null) {
            removeButton.setX(panelX + 120);
            removeButton.setY(y);
            removeButton.setWidth(screen.embedded() ? Math.max(80, panelW - 140) : 128);
        }
        if (backButton != null) {
            backButton.setX(panelX + Math.max(256, panelW - 104));
            backButton.setY(y);
        }
    }

    void render(ManageHost screen, GuiGraphics graphics, int mouseX, int mouseY) {
        place(screen);
        layoutChrome(screen);
        if (!screen.embedded()) {
            graphics.fill(0, 0, screen.screenWidth(), screen.screenHeight(), 0xC0060C14);
            WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
        }
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_wrights"), panelX + 12, panelY + 8, WrightsUi.COPPER, false);
        Swarm swarm = screen.swarm();
        if (expanded >= 0 && (expanded >= swarm.units().size() || away(screen, swarm, expanded))) {
            expanded = -1;
        }
        int away = awayCount(screen, swarm);
        int home = swarm.units().size() - away;
        headExtra = away > 0 ? 12 : 0;
        int marked = markedCount(screen, swarm);
        boolean chrome = dialog == NONE;
        if (upgradeButton != null) {
            upgradeButton.setShown(open && chrome);
            upgradeButton.active = open && chrome && marked > 0;
        }
        if (addButton != null) {
            addButton.setShown(open && chrome);
            addButton.active = open && chrome && roomLeft(swarm) > 0;
        }
        if (backButton != null) {
            backButton.setShown(open && chrome && !screen.embedded());
        }
        if (removeButton != null) {
            removeButton.setShown(open && chrome);
            removeButton.active = open && chrome && (marked > 0 || (expanded >= 0 && !away(screen, swarm, expanded)));
            removeButton.setMessage(Component.translatable(marked > 0 ? "screen.wrights.manage_remove_marked" : "screen.wrights.manage_remove"));
        }
        if (countBox != null) {
            countBox.setVisible(open && dialog == ADD);
        }
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_count", home, Swarm.capacity(), readyWrights(screen)),
                panelX + 12, panelY + 22, WrightsUi.MUTED, false);
        if (away > 0) {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_away", away), panelX + 12, panelY + 34, WrightsUi.MUTED, false);
        }
        boolean all = home > 0 && marked == home;
        int allY = panelY + 36 + headExtra;
        drawCheck(graphics, panelX + 12, allY, all, home > 0, mouseX, mouseY);
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_all"), panelX + 26, allY + 1, home > 0 ? WrightsUi.TEXT : WrightsUi.MUTED, false);

        int listTop = listTop();
        int listBottom = panelY + panelH - 62;
        int view = Math.max(1, listBottom - listTop);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight(screen, swarm) - view));
        graphics.enableScissor(panelX + 8, listTop, panelX + panelW - 8, listBottom);
        if (home == 0) {
            graphics.drawString(screen.font(), Component.translatable(swarm.units().isEmpty() ? "screen.wrights.hangar_empty" : "screen.wrights.manage_home_empty"),
                    panelX + 12, listTop + 4, WrightsUi.MUTED, false);
        }
        int y = listTop - scroll;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (away(screen, swarm, i)) {
                checked.remove(swarm.units().get(i).id());
                continue;
            }
            Swarm.Unit unit = swarm.units().get(i);
            int tier = WrightTier.clamp(unit.speed());
            if (y + ROW >= listTop && y < listBottom) {
                boolean mark = checked.contains(unit.id());
                if (mark || i == expanded) {
                    graphics.fill(panelX + 10, y - 1, panelX + panelW - 10, y + ROW - 1, i == expanded ? 0x553C2A1A : 0x33243340);
                }
                drawCheck(graphics, panelX + 12, y + 2, mark, true, mouseX, mouseY);
                Component tierName = Component.translatable(WrightTier.nameKey(tier));
                int tierWidth = screen.font().width(tierName);
                String name = screen.font().plainSubstrByWidth(unit.title(i + 1).getString(), Math.max(24, panelW - 28 - 18 - tierWidth));
                graphics.drawString(screen.font(), name, panelX + 28, y + 3, WrightsUi.TEXT, false);
                graphics.drawString(screen.font(), tierName, panelX + panelW - 14 - tierWidth, y + 3, WrightTier.textColor(tier), false);
            }
            y += ROW;
            if (i == expanded) {
                placeNameBox(screen, swarm, i, y, listTop, listBottom);
                if (y + EXPAND > listTop && y < listBottom) {
                    graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_pace"), panelX + 18, y + 20, WrightsUi.TEXT, false);
                    boolean canDowngrade = tier > WrightTier.BASE;
                    if (tier < WrightTier.NETHERITE) {
                        Component next = Component.translatable(WrightTier.nameKey(tier + 1));
                        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_next", next), panelX + 18, y + 32, WrightsUi.MUTED, false);
                        boolean have = canAfford(screen, tier, tier + 1);
                        drawMini(graphics, screen, upX(), y + 46, upW(), Component.translatable("screen.wrights.manage_upgrade", next), have, false, mouseX, mouseY);
                        drawMini(graphics, screen, downX(), y + 46, downW(), Component.translatable("screen.wrights.manage_remove_upgrade"), canDowngrade, false, mouseX, mouseY);
                    } else {
                        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_best"), panelX + 18, y + 32, WrightsUi.MUTED, false);
                        drawMini(graphics, screen, upX(), y + 46, upW(), Component.translatable("screen.wrights.manage_remove_upgrade"), canDowngrade, false, mouseX, mouseY);
                    }
                    drawTiers(graphics, screen, y + 64, tier, mouseX, mouseY);
                }
                y += EXPAND;
            }
        }
        graphics.disableScissor();
        if (nameBox != null && expanded < 0) {
            nameBox.setVisible(false);
        }
        if (dialog != NONE) {
            renderDialog(screen, graphics, swarm, mouseX, mouseY);
        }
        drawDigitalTip(screen, graphics, mouseX, mouseY);
    }

    void drawDigitalTip(ManageHost screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (WrightsClientState.digitalUseTip.isEmpty() || System.currentTimeMillis() >= WrightsClientState.digitalUseUntil) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : WrightsClientState.digitalUseTip) {
            lines.add(line.getVisualOrderText());
        }
        graphics.renderTooltip(screen.font(), lines, mouseX, mouseY);
    }

    boolean mouseClicked(ManageHost screen, double mouseX, double mouseY, int button) {
        if (button != 0 || !open) {
            return false;
        }
        if (nameBox != null && nameBox.isFocused() && !nameBox.isMouseOver(mouseX, mouseY)) {
            commitName(screen);
        }
        if (nameBox != null && nameBox.visible && nameBox.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        place(screen);
        if (dialog != NONE) {
            return dialogClick(screen, mouseX, mouseY);
        }
        Swarm swarm = screen.swarm();
        headExtra = awayCount(screen, swarm) > 0 ? 12 : 0;
        int allY = panelY + 36 + headExtra;
        if (hit(mouseX, mouseY, panelX + 12, allY, 10, 10) || hit(mouseX, mouseY, panelX + 26, allY, 40, 12)) {
            if (swarm.units().size() - awayCount(screen, swarm) > 0) {
                toggleAll(screen, swarm);
            }
            return true;
        }
        int listTop = listTop();
        int listBottom = panelY + panelH - 62;
        if (mouseX < panelX + 8 || mouseX > panelX + panelW - 8 || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int local = (int) mouseY - listTop + scroll;
        int y = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (away(screen, swarm, i)) {
                continue;
            }
            if (local >= y && local < y + ROW) {
                if (mouseX >= panelX + 12 && mouseX < panelX + 24) {
                    toggle(swarm.units().get(i).id());
                } else {
                    commitName(screen);
                    if (expanded == i) {
                        nameFor = -1;
                    }
                    expanded = expanded == i ? -1 : i;
                }
                return true;
            }
            y += ROW;
            if (expanded == i) {
                int tier = WrightTier.clamp(swarm.units().get(i).speed());
                if (local >= y + 46 && local < y + 60) {
                    if (tier < WrightTier.NETHERITE && mouseX >= upX() && mouseX < upX() + upW()) {
                        screen.edit(HangarOps.UPGRADE, i);
                        return true;
                    }
                    int downX = tier < WrightTier.NETHERITE ? downX() : upX();
                    int downW = tier < WrightTier.NETHERITE ? downW() : upW();
                    if (tier > WrightTier.BASE && mouseX >= downX && mouseX < downX + downW) {
                        screen.edit(HangarOps.DOWNGRADE, i);
                        return true;
                    }
                }
                if (local >= y + 64 && local < y + 78) {
                    int picked = tierAt(mouseX);
                    if (picked >= WrightTier.BASE && picked != tier && (picked < tier || (picked == tier + 1 && canAfford(screen, tier, picked)))) {
                        screen.batch(HangarOps.TO_TIER, picked, new int[] { i });
                    }
                    return true;
                }
                y += EXPAND;
            }
        }
        return true;
    }

    boolean mouseScrolled(ManageHost screen, double mouseX, double mouseY, double delta) {
        if (!open) {
            return false;
        }
        if (dialog != NONE) {
            return true;
        }
        int listTop = listTop();
        int listBottom = panelY + panelH - 62;
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        int max = Math.max(0, contentHeight(screen, screen.swarm()) - (listBottom - listTop));
        commitName(screen);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * ROW, 0, max);
        return true;
    }

    private void openAdd(ManageHost screen) {
        dialog = ADD;
        if (countBox != null) {
            countBox.setValue("1");
            countBox.setVisible(true);
            countBox.setFocused(true);
            screen.focus(countBox);
        }
    }

    private void openMarked() {
        dialog = MARKED;
    }

    private void removeSelected(ManageHost screen) {
        Swarm swarm = screen.swarm();
        int[] units = marked(screen, swarm);
        if (units.length > 0) {
            screen.batch(HangarOps.TAKE_MARKED, 0, units);
            checked.clear();
            expanded = -1;
            return;
        }
        if (expanded < 0 || away(screen, swarm, expanded)) {
            return;
        }
        screen.edit(HangarOps.TAKE, expanded);
        expanded = -1;
    }

    private boolean dialogClick(ManageHost screen, double mouseX, double mouseY) {
        int dw = Math.min(312, Math.max(220, screen.screenWidth() - 24));
        int dh = dialog == ADD ? 176 : 196;
        int dx = (screen.screenWidth() - dw) / 2;
        int dy = (screen.screenHeight() - dh) / 2;
        if (mouseX < dx || mouseY < dy || mouseX >= dx + dw || mouseY >= dy + dh) {
            closeTop();
            return true;
        }
        if (hit(mouseX, mouseY, dx + dw - 78, dy + dh - 26, 66, 16)) {
            closeTop();
            return true;
        }
        if (dialog == ADD) {
            if (countBox != null && hit(mouseX, mouseY, boxX, boxY, boxW, boxH)) {
                return false;
            }
            int[] quick = { 1, 8, 16, 64 };
            int gap = 4;
            int width = (dw - 24 - gap * 3) / 4;
            int fit = allThatFit(screen);
            for (int i = 0; i < quick.length; i++) {
                int x = dx + 12 + i * (width + gap);
                if (hit(mouseX, mouseY, x, dy + 48, width, 16)) {
                    if (quick[i] <= fit) {
                        screen.batch(HangarOps.ADD_COUNT, quick[i], new int[0]);
                        closeTop();
                    }
                    return true;
                }
            }
            if (hit(mouseX, mouseY, dx + 12, dy + 70, dw - 24, 16)) {
                if (fit > 0) {
                    screen.batch(HangarOps.ADD_COUNT, fit, new int[0]);
                    closeTop();
                }
                return true;
            }
            if (hit(mouseX, mouseY, dx + dw - 108, dy + 98, 96, 16)) {
                int count = typedCount();
                if (count > 0 && count <= fit) {
                    screen.batch(HangarOps.ADD_COUNT, count, new int[0]);
                    closeTop();
                }
                return true;
            }
            return true;
        }
        int tier = tierAt(mouseX, dx + 12, dw - 24);
        if (tier >= WrightTier.BASE && mouseY >= dy + 78 && mouseY < dy + 94) {
            screen.batch(HangarOps.TO_TIER, tier, marked(screen, screen.swarm()));
            closeTop();
            return true;
        }
        if (hit(mouseX, mouseY, dx + 12, dy + 102, dw - 24, 16)) {
            screen.batch(HangarOps.MAX_AFFORD, 0, marked(screen, screen.swarm()));
            closeTop();
            return true;
        }
        if (hit(mouseX, mouseY, dx + 12, dy + 124, dw - 24, 16)) {
            screen.batch(HangarOps.DOWN_ONE, 0, marked(screen, screen.swarm()));
            closeTop();
            return true;
        }
        return true;
    }

    private void renderDialog(ManageHost screen, GuiGraphics graphics, Swarm swarm, int mouseX, int mouseY) {
        int dw = Math.min(312, Math.max(220, screen.screenWidth() - 24));
        int dh = dialog == ADD ? 176 : 196;
        int dx = (screen.screenWidth() - dw) / 2;
        int dy = (screen.screenHeight() - dh) / 2;
        graphics.fill(0, 0, screen.screenWidth(), screen.screenHeight(), 0x88060C14);
        WrightsUi.panel(graphics, dx, dy, dw, dh);
        if (dialog == ADD) {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_add_title"), dx + 12, dy + 12, WrightsUi.COPPER, false);
            int room = roomLeft(swarm);
            int fit = allThatFit(screen);
            if (digitalCraftable(ModItems.WRIGHT.get())) {
                graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_add_craft", room), dx + 12, dy + 28, WrightsUi.MUTED, false);
            } else {
                graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_add_room", room, readyWrights(screen)), dx + 12, dy + 28, WrightsUi.MUTED, false);
            }
            int[] quick = { 1, 8, 16, 64 };
            int gap = 4;
            int width = (dw - 24 - gap * 3) / 4;
            for (int i = 0; i < quick.length; i++) {
                drawMini(graphics, screen, dx + 12 + i * (width + gap), dy + 48, width, Component.literal(Integer.toString(quick[i])), quick[i] <= fit, false, mouseX, mouseY);
            }
            drawMini(graphics, screen, dx + 12, dy + 70, dw - 24, Component.translatable("screen.wrights.manage_add_all"), fit > 0, false, mouseX, mouseY);
            boxX = dx + 12;
            boxY = dy + 98;
            boxW = dw - 128;
            boxH = 16;
            if (countBox != null) {
                countBox.setX(boxX);
                countBox.setY(boxY);
                countBox.setWidth(boxW);
            }
            int typed = typedCount();
            drawMini(graphics, screen, dx + dw - 108, dy + 98, 96, Component.translatable("screen.wrights.manage_add_count"), typed > 0 && typed <= fit, false, mouseX, mouseY);
        } else {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_marked_title"), dx + 12, dy + 12, WrightsUi.COPPER, false);
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_marked_count", markedCount(screen, swarm)), dx + 12, dy + 28, WrightsUi.TEXT, false);
            int hintY = dy + 44;
            for (var line : screen.font().split(Component.translatable("screen.wrights.manage_marked_hint"), dw - 24)) {
                graphics.drawString(screen.font(), line, dx + 12, hintY, WrightsUi.MUTED, false);
                hintY += 10;
            }
            drawTierChoices(graphics, screen, dx + 12, dy + 78, dw - 24, -1, mouseX, mouseY);
            drawMini(graphics, screen, dx + 12, dy + 102, dw - 24, Component.translatable("screen.wrights.manage_max"), true, false, mouseX, mouseY);
            drawMini(graphics, screen, dx + 12, dy + 124, dw - 24, Component.translatable("screen.wrights.manage_down_one"), true, false, mouseX, mouseY);
        }
        drawMini(graphics, screen, dx + dw - 78, dy + dh - 26, 66, Component.translatable("screen.wrights.manage_cancel"), true, false, mouseX, mouseY);
    }

    private void drawTiers(GuiGraphics graphics, ManageHost screen, int y, int current, int mouseX, int mouseY) {
        drawTierChoices(graphics, screen, panelX + 18, y, panelW - 36, current, mouseX, mouseY);
    }

    private void drawTierChoices(GuiGraphics graphics, ManageHost screen, int x, int y, int width, int current, int mouseX, int mouseY) {
        int gap = 4;
        int steps = WrightTier.NETHERITE - WrightTier.BASE;
        int chip = (width - gap * steps) / (steps + 1);
        for (int tier = WrightTier.BASE; tier <= WrightTier.NETHERITE; tier++) {
            int chipX = x + (tier - WrightTier.BASE) * (chip + gap);
            boolean enabled = current < WrightTier.BASE || tier != current;
            if (current >= WrightTier.BASE && tier > current) {
                enabled = tier == current + 1 && canAfford(screen, current, tier);
            }
            drawMini(graphics, screen, chipX, y, chip, Component.translatable(WrightTier.nameKey(tier)), enabled || tier == current, tier == current, mouseX, mouseY);
        }
    }

    private int tierAt(double mouseX) {
        return tierAt(mouseX, panelX + 18, panelW - 36);
    }

    private int tierAt(double mouseX, int x, int width) {
        int gap = 4;
        int steps = WrightTier.NETHERITE - WrightTier.BASE;
        int chip = (width - gap * steps) / (steps + 1);
        if (chip <= 0 || mouseX < x) {
            return -1;
        }
        int index = (int) (mouseX - x) / (chip + gap);
        if (index < 0 || index > steps) {
            return -1;
        }
        int chipX = x + index * (chip + gap);
        if (mouseX >= chipX + chip) {
            return -1;
        }
        return WrightTier.BASE + index;
    }

    private void place(ManageHost screen) {
        if (screen.embedded()) {
            panelX = screen.areaX();
            panelY = screen.areaY();
            panelW = Math.max(160, screen.areaW());
            panelH = Math.max(120, screen.areaH());
            return;
        }
        panelW = 360;
        panelH = Math.min(screen.screenHeight() - 24, 340);
        panelX = (screen.screenWidth() - panelW) / 2;
        panelY = Math.max(8, (screen.screenHeight() - panelH) / 2);
    }

    boolean keyPressed(ManageHost screen, int keyCode, int scanCode, int modifiers) {
        if (nameBox == null || !nameBox.isFocused()) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) {
            commitName(screen);
            nameBox.setFocused(false);
            screen.focus(null);
            return true;
        }
        if (keyCode == 256) {
            nameBox.setValue(sentWrightName);
            nameBox.setFocused(false);
            screen.focus(null);
            return true;
        }
        nameBox.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    private void placeNameBox(ManageHost screen, Swarm swarm, int index, int y, int listTop, int listBottom) {
        if (nameBox == null) {
            return;
        }
        boolean shown = y + 16 > listTop && y + 2 < listBottom;
        nameBox.setVisible(shown);
        if (!shown) {
            return;
        }
        nameBox.setX(panelX + 18);
        nameBox.setY(y + 2);
        nameBox.setWidth(Math.max(60, panelW - 36));
        if (nameFor != index && index >= 0 && index < swarm.units().size()) {
            String current = swarm.units().get(index).name();
            if (current == null) {
                current = "";
            }
            nameBox.setValue(current);
            sentWrightName = current;
            nameFor = index;
        }
    }

    private void commitName(ManageHost screen) {
        if (nameBox == null || expanded < 0) {
            return;
        }
        Swarm swarm = screen.swarm();
        if (expanded >= swarm.units().size()) {
            return;
        }
        String next = Swarm.cleanName(nameBox.getValue());
        String current = swarm.units().get(expanded).name();
        if (current == null) {
            current = "";
        }
        if (!nameBox.getValue().equals(next)) {
            nameBox.setValue(next);
        }
        sentWrightName = next;
        if (next.equals(current)) {
            return;
        }
        screen.rename(expanded, next);
    }

    private int listTop() {
        return panelY + 52 + headExtra;
    }

    private int upX() {
        return panelX + 18;
    }

    private int upW() {
        return Math.min(160, Math.max(40, (panelW - 44) / 2));
    }

    private int downX() {
        return panelX + panelW - 18 - downW();
    }

    private int downW() {
        return Math.min(156, Math.max(40, panelW - 44 - upW() - 8));
    }

    private int contentHeight(ManageHost screen, Swarm swarm) {
        int height = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (away(screen, swarm, i)) {
                continue;
            }
            height += ROW;
            if (i == expanded) {
                height += EXPAND;
            }
        }
        return height;
    }

    private int awayCount(ManageHost screen, Swarm swarm) {
        int count = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (away(screen, swarm, i)) {
                count++;
            }
        }
        return count;
    }

    private static boolean away(ManageHost screen, Swarm swarm, int index) {
        return index >= 0 && index < swarm.units().size() && screen.away(swarm.units().get(index).id());
    }

    private int markedCount(ManageHost screen, Swarm swarm) {
        int count = 0;
        for (Swarm.Unit unit : swarm.units()) {
            if (checked.contains(unit.id()) && !screen.away(unit.id())) {
                count++;
            }
        }
        return count;
    }

    private int[] marked(ManageHost screen, Swarm swarm) {
        int[] units = new int[markedCount(screen, swarm)];
        int cursor = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            if (checked.contains(unit.id()) && !screen.away(unit.id())) {
                units[cursor++] = i;
            }
        }
        return units;
    }

    private void toggleAll(ManageHost screen, Swarm swarm) {
        int home = swarm.units().size() - awayCount(screen, swarm);
        if (home > 0 && markedCount(screen, swarm) == home) {
            checked.clear();
            return;
        }
        for (Swarm.Unit unit : swarm.units()) {
            if (!screen.away(unit.id())) {
                checked.add(unit.id());
            }
        }
    }

    private void toggle(UUID id) {
        if (!checked.add(id)) {
            checked.remove(id);
        }
    }

    private int typedCount() {
        if (countBox == null || countBox.getValue().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(countBox.getValue());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int roomLeft(Swarm swarm) {
        return Math.max(0, Swarm.capacity() - swarm.units().size());
    }

    private int allThatFit(ManageHost screen) {
        int room = roomLeft(screen.swarm());
        if (screen.minecraft().player != null && screen.minecraft().player.getAbilities().instabuild) {
            return room;
        }
        if (digitalCraftable(ModItems.WRIGHT.get())) {
            return room;
        }
        return (int) Math.min(room, readyWrights(screen));
    }

    private static int readyWrights(ManageHost screen) {
        long ready = held(screen) + digitalStock(ModItems.WRIGHT.get());
        return ready > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ready;
    }

    private static int held(ManageHost screen) {
        if (screen.minecraft().player == null) {
            return 0;
        }
        int count = 0;
        Inventory inventory = screen.minecraft().player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof WrightItem) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int count(ManageHost screen, Item item) {
        if (item == null || screen.minecraft().player == null) {
            return 0;
        }
        return screen.minecraft().player.getInventory().countItem(item);
    }

    private static boolean canAfford(ManageHost screen, int tier, int target) {
        if (screen.minecraft().player != null && screen.minecraft().player.getAbilities().instabuild) {
            return true;
        }
        for (int step = tier + 1; step <= target; step++) {
            Item upgrade = WrightTier.upgradeItem(step);
            if (count(screen, upgrade) <= 0 && digitalStock(upgrade) <= 0L && !digitalCraftable(upgrade)) {
                return false;
            }
        }
        return true;
    }

    private static BlueprintPayloads.DigitalInfoLine digitalLine(Item item) {
        if (item == null || !WrightsClientState.digitalOnline) {
            return null;
        }
        int id = Item.getId(item);
        for (BlueprintPayloads.DigitalInfoLine line : WrightsClientState.digitalLines) {
            if (line.itemId() == id) {
                return line;
            }
        }
        return null;
    }

    private static long digitalStock(Item item) {
        BlueprintPayloads.DigitalInfoLine line = digitalLine(item);
        return line == null ? 0L : Math.max(0L, line.stock());
    }

    private static boolean digitalCraftable(Item item) {
        BlueprintPayloads.DigitalInfoLine line = digitalLine(item);
        return line != null && line.craftable();
    }

    private void hideChrome() {
        if (upgradeButton != null) {
            upgradeButton.setShown(false);
        }
        if (addButton != null) {
            addButton.setShown(false);
        }
        if (removeButton != null) {
            removeButton.setShown(false);
        }
        if (backButton != null) {
            backButton.setShown(false);
        }
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawCheck(GuiGraphics graphics, int x, int y, boolean checked, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10;
        graphics.fill(x, y, x + 10, y + 10, !enabled ? 0xFF141A22 : (hover ? 0xFF2C3E52 : 0xFF1A2633));
        WrightsUi.border(graphics, x, y, 10, 10, !enabled ? 0xFF2A3440 : (checked ? WrightsUi.COPPER : (hover ? WrightsUi.COPPER : 0xFF3D4E5E)));
        if (checked && enabled) {
            graphics.fill(x + 2, y + 2, x + 8, y + 8, WrightsUi.COPPER);
        }
    }

    private static void drawMini(GuiGraphics graphics, ManageHost screen, int x, int y, int width, Component label, boolean enabled, boolean selected, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        int fill = !enabled && !selected ? 0xFF141A22 : (selected ? 0xFF2A4054 : (hover ? 0xFF2C3E52 : 0xFF1A2633));
        graphics.fill(x, y, x + width, y + 14, fill);
        int border = selected ? WrightsUi.COPPER : (enabled ? (hover ? WrightsUi.COPPER : 0xFF3D4E5E) : 0xFF2A3440);
        WrightsUi.border(graphics, x, y, width, 14, border);
        String text = screen.font().plainSubstrByWidth(label.getString(), Math.max(0, width - 6));
        graphics.drawString(screen.font(), text, x + (width - screen.font().width(text)) / 2, y + 3, enabled || selected ? WrightsUi.TEXT : 0xFF6E7A86, false);
    }
}
