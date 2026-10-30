package com.wrights.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.wrights.drone.HangarOps;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;
import com.wrights.item.WrightItem;

/**
 * The wright list opened from the Wright Manager. A tier row sets one wright,
 * and the checkboxes feed the marked upgrade and remove actions.
 */
final class WrightManagePanel {
    private static final int PANEL_W = 360;
    private static final int ROW = 16;
    private static final int EXPAND = 64;
    private static final int UPGRADE_X = 18;
    private static final int UPGRADE_W = 160;
    private static final int DOWNGRADE_X = 186;
    private static final int DOWNGRADE_W = 156;
    private static final int NONE = 0;
    private static final int ADD = 1;
    private static final int MARKED = 2;

    private WrightsButton addButton;
    private WrightsButton removeButton;
    private WrightsButton upgradeButton;
    private WrightsButton backButton;
    private EditBox countBox;

    private boolean open;
    private int scroll;
    private int expanded = -1;
    private int dialog;
    private final Set<UUID> checked = new HashSet<>();
    private int panelX;
    private int panelY;
    private int panelH;
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

    void toggle(HangarScreen screen) {
        open = !open;
        scroll = 0;
        expanded = -1;
        dialog = NONE;
        checked.clear();
        screen.layoutHangar();
    }

    void close(HangarScreen screen) {
        if (!open) {
            return;
        }
        open = false;
        dialog = NONE;
        screen.layoutHangar();
    }

    void addWidgets(HangarScreen screen) {
        place(screen.width, screen.height);
        int y = panelY + panelH - 54;
        upgradeButton = new WrightsButton(panelX + 12, y, PANEL_W - 24, 16, Component.translatable("screen.wrights.manage_upgrade_marked"), button -> openMarked());
        upgradeButton.active = false;
        screen.addButton(upgradeButton);
        y = panelY + panelH - 32;
        addButton = new WrightsButton(panelX + 12, y, 100, 16, Component.translatable("screen.wrights.manage_add"), button -> openAdd(screen));
        screen.addButton(addButton);
        removeButton = new WrightsButton(panelX + 120, y, 128, 16, Component.translatable("screen.wrights.manage_remove"), button -> removeSelected(screen), true);
        removeButton.active = false;
        screen.addButton(removeButton);
        backButton = new WrightsButton(panelX + 256, y, 92, 16, Component.translatable("screen.wrights.back"), button -> close(screen));
        screen.addButton(backButton);
        countBox = new EditBox(screen.font(), 0, 0, 80, 16, Component.translatable("screen.wrights.manage_add_count"));
        countBox.setMaxLength(4);
        countBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        countBox.setVisible(false);
        screen.addField(countBox);
    }

    void render(HangarScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        place(screen.width, screen.height);
        graphics.fill(0, 0, screen.width, screen.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, PANEL_W, panelH);
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_wrights"), panelX + 12, panelY + 8, WrightsUi.COPPER, false);
        Swarm swarm = swarm(screen);
        if (expanded >= swarm.units().size()) {
            expanded = -1;
        }
        int marked = markedCount(swarm);
        boolean chrome = dialog == NONE;
        if (upgradeButton != null) {
            upgradeButton.setShown(chrome);
            upgradeButton.active = chrome && marked > 0;
        }
        if (addButton != null) {
            addButton.setShown(chrome);
        }
        if (backButton != null) {
            backButton.setShown(chrome);
        }
        if (removeButton != null) {
            removeButton.setShown(chrome);
            removeButton.active = chrome && (marked > 0 || expanded >= 0);
            removeButton.setMessage(Component.translatable(marked > 0 ? "screen.wrights.manage_remove_marked" : "screen.wrights.manage_remove"));
        }
        if (countBox != null) {
            countBox.setVisible(dialog == ADD);
        }
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_count", swarm.units().size(), Swarm.capacity(), held(screen)),
                panelX + 12, panelY + 22, WrightsUi.MUTED, false);
        boolean all = !swarm.units().isEmpty() && marked == swarm.units().size();
        drawCheck(graphics, panelX + 12, panelY + 36, all, mouseX, mouseY);
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_all"), panelX + 26, panelY + 37, WrightsUi.TEXT, false);

        int listTop = panelY + 52;
        int listBottom = panelY + panelH - 62;
        int view = Math.max(1, listBottom - listTop);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight(swarm) - view));
        graphics.enableScissor(panelX + 8, listTop, panelX + PANEL_W - 8, listBottom);
        if (swarm.units().isEmpty()) {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.hangar_empty"), panelX + 12, listTop + 4, WrightsUi.MUTED, false);
        }
        int y = listTop - scroll;
        for (int i = 0; i < swarm.units().size(); i++) {
            Swarm.Unit unit = swarm.units().get(i);
            int tier = WrightTier.clamp(unit.speed());
            if (y + ROW >= listTop && y < listBottom) {
                boolean mark = checked.contains(unit.id());
                if (mark || i == expanded) {
                    graphics.fill(panelX + 10, y - 1, panelX + PANEL_W - 10, y + ROW - 1, i == expanded ? 0x553C2A1A : 0x33243340);
                }
                drawCheck(graphics, panelX + 12, y + 2, mark, mouseX, mouseY);
                String name = Component.translatable("screen.wrights.drone_name", i + 1).getString();
                graphics.drawString(screen.font(), name, panelX + 28, y + 3, WrightsUi.TEXT, false);
                Component tierName = Component.translatable(WrightTier.nameKey(tier));
                graphics.drawString(screen.font(), tierName, panelX + PANEL_W - 14 - screen.font().width(tierName), y + 3, WrightTier.textColor(tier), false);
            }
            y += ROW;
            if (i == expanded) {
                if (y + EXPAND > listTop && y < listBottom) {
                    graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_pace"), panelX + 18, y + 2, WrightsUi.TEXT, false);
                    boolean canDowngrade = tier > WrightTier.BASE;
                    if (tier < WrightTier.NETHERITE) {
                        Component next = Component.translatable(WrightTier.nameKey(tier + 1));
                        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_next", next), panelX + 18, y + 14, WrightsUi.MUTED, false);
                        boolean have = canAfford(screen, tier, tier + 1);
                        drawMini(graphics, screen, panelX + UPGRADE_X, y + 28, UPGRADE_W, Component.translatable("screen.wrights.manage_upgrade", next), have, false, mouseX, mouseY);
                        drawMini(graphics, screen, panelX + DOWNGRADE_X, y + 28, DOWNGRADE_W, Component.translatable("screen.wrights.manage_remove_upgrade"), canDowngrade, false, mouseX, mouseY);
                    } else {
                        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_best"), panelX + 18, y + 14, WrightsUi.MUTED, false);
                        drawMini(graphics, screen, panelX + UPGRADE_X, y + 28, UPGRADE_W, Component.translatable("screen.wrights.manage_remove_upgrade"), canDowngrade, false, mouseX, mouseY);
                    }
                    drawTiers(graphics, screen, y + 46, tier, mouseX, mouseY);
                }
                y += EXPAND;
            }
        }
        graphics.disableScissor();
        if (dialog != NONE) {
            renderDialog(screen, graphics, swarm, mouseX, mouseY);
        }
    }

    boolean mouseClicked(HangarScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        place(screen.width, screen.height);
        if (dialog != NONE) {
            return dialogClick(screen, mouseX, mouseY);
        }
        Swarm swarm = swarm(screen);
        if (hit(mouseX, mouseY, panelX + 12, panelY + 36, 10, 10) || hit(mouseX, mouseY, panelX + 26, panelY + 36, 40, 12)) {
            toggleAll(swarm);
            return true;
        }
        int listTop = panelY + 52;
        int listBottom = panelY + panelH - 62;
        if (mouseX < panelX + 8 || mouseX > panelX + PANEL_W - 8 || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int local = (int) mouseY - listTop + scroll;
        int y = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (local >= y && local < y + ROW) {
                if (mouseX >= panelX + 12 && mouseX < panelX + 24) {
                    toggle(swarm.units().get(i).id());
                } else {
                    expanded = expanded == i ? -1 : i;
                }
                return true;
            }
            y += ROW;
            if (expanded == i) {
                int tier = WrightTier.clamp(swarm.units().get(i).speed());
                if (local >= y + 28 && local < y + 42) {
                    if (tier < WrightTier.NETHERITE && mouseX >= panelX + UPGRADE_X && mouseX < panelX + UPGRADE_X + UPGRADE_W) {
                        screen.edit(HangarOps.UPGRADE, i);
                        return true;
                    }
                    int downX = tier < WrightTier.NETHERITE ? panelX + DOWNGRADE_X : panelX + UPGRADE_X;
                    int downW = tier < WrightTier.NETHERITE ? DOWNGRADE_W : UPGRADE_W;
                    if (tier > WrightTier.BASE && mouseX >= downX && mouseX < downX + downW) {
                        screen.edit(HangarOps.DOWNGRADE, i);
                        return true;
                    }
                }
                if (local >= y + 46 && local < y + 60) {
                    int picked = tierAt(mouseX);
                    if (picked >= WrightTier.BASE && picked != tier && (picked < tier || canAfford(screen, tier, picked))) {
                        screen.batch(HangarOps.TO_TIER, picked, new int[] { i });
                    }
                    return true;
                }
                y += EXPAND;
            }
        }
        return true;
    }

    boolean mouseScrolled(HangarScreen screen, double mouseX, double mouseY, double delta) {
        if (dialog != NONE) {
            return true;
        }
        int listTop = panelY + 52;
        int listBottom = panelY + panelH - 62;
        if (mouseX < panelX || mouseX > panelX + PANEL_W || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        int max = Math.max(0, contentHeight(swarm(screen)) - (listBottom - listTop));
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * ROW, 0, max);
        return true;
    }

    private void openAdd(HangarScreen screen) {
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

    private void removeSelected(HangarScreen screen) {
        Swarm swarm = swarm(screen);
        int[] units = marked(swarm);
        if (units.length > 0) {
            screen.batch(HangarOps.TAKE_MARKED, 0, units);
            checked.clear();
            expanded = -1;
            return;
        }
        if (expanded < 0) {
            return;
        }
        screen.edit(HangarOps.TAKE, expanded);
        expanded = -1;
    }

    private boolean dialogClick(HangarScreen screen, double mouseX, double mouseY) {
        int dw = 312;
        int dh = dialog == ADD ? 176 : 196;
        int dx = (screen.width - dw) / 2;
        int dy = (screen.height - dh) / 2;
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
            for (int i = 0; i < quick.length; i++) {
                int x = dx + 12 + i * (width + gap);
                if (hit(mouseX, mouseY, x, dy + 48, width, 16)) {
                    screen.batch(HangarOps.ADD_COUNT, quick[i], new int[0]);
                    closeTop();
                    return true;
                }
            }
            if (hit(mouseX, mouseY, dx + 12, dy + 70, dw - 24, 16)) {
                int fit = allThatFit(screen);
                if (fit > 0) {
                    screen.batch(HangarOps.ADD_COUNT, fit, new int[0]);
                    closeTop();
                }
                return true;
            }
            if (hit(mouseX, mouseY, dx + dw - 108, dy + 98, 96, 16)) {
                int count = typedCount();
                if (count > 0) {
                    screen.batch(HangarOps.ADD_COUNT, count, new int[0]);
                    closeTop();
                }
                return true;
            }
            return true;
        }
        int tier = tierAt(mouseX, dx + 12, dy + 78, dw - 24);
        if (tier >= WrightTier.BASE && mouseY >= dy + 78 && mouseY < dy + 94) {
            screen.batch(HangarOps.TO_TIER, tier, marked(swarm(screen)));
            closeTop();
            return true;
        }
        if (hit(mouseX, mouseY, dx + 12, dy + 102, dw - 24, 16)) {
            screen.batch(HangarOps.MAX_AFFORD, 0, marked(swarm(screen)));
            closeTop();
            return true;
        }
        if (hit(mouseX, mouseY, dx + 12, dy + 124, dw - 24, 16)) {
            screen.batch(HangarOps.DOWN_ONE, 0, marked(swarm(screen)));
            closeTop();
            return true;
        }
        return true;
    }

    private void renderDialog(HangarScreen screen, GuiGraphics graphics, Swarm swarm, int mouseX, int mouseY) {
        int dw = 312;
        int dh = dialog == ADD ? 176 : 196;
        int dx = (screen.width - dw) / 2;
        int dy = (screen.height - dh) / 2;
        graphics.fill(0, 0, screen.width, screen.height, 0x88060C14);
        WrightsUi.panel(graphics, dx, dy, dw, dh);
        if (dialog == ADD) {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_add_title"), dx + 12, dy + 12, WrightsUi.COPPER, false);
            int room = Math.max(0, Swarm.capacity() - swarm.units().size());
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_add_room", room, held(screen)), dx + 12, dy + 28, WrightsUi.MUTED, false);
            int[] quick = { 1, 8, 16, 64 };
            int gap = 4;
            int width = (dw - 24 - gap * 3) / 4;
            for (int i = 0; i < quick.length; i++) {
                drawMini(graphics, screen, dx + 12 + i * (width + gap), dy + 48, width, Component.literal(Integer.toString(quick[i])), true, false, mouseX, mouseY);
            }
            drawMini(graphics, screen, dx + 12, dy + 70, dw - 24, Component.translatable("screen.wrights.manage_add_all"), room > 0, false, mouseX, mouseY);
            boxX = dx + 12;
            boxY = dy + 98;
            boxW = dw - 128;
            boxH = 16;
            if (countBox != null) {
                countBox.setX(boxX);
                countBox.setY(boxY);
                countBox.setWidth(boxW);
            }
            drawMini(graphics, screen, dx + dw - 108, dy + 98, 96, Component.translatable("screen.wrights.manage_add_count"), typedCount() > 0, false, mouseX, mouseY);
        } else {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_marked_title"), dx + 12, dy + 12, WrightsUi.COPPER, false);
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_marked_count", markedCount(swarm)), dx + 12, dy + 28, WrightsUi.TEXT, false);
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

    private void drawTiers(GuiGraphics graphics, HangarScreen screen, int y, int current, int mouseX, int mouseY) {
        drawTierChoices(graphics, screen, panelX + 18, y, PANEL_W - 36, current, mouseX, mouseY);
    }

    private void drawTierChoices(GuiGraphics graphics, HangarScreen screen, int x, int y, int width, int current, int mouseX, int mouseY) {
        int gap = 4;
        int steps = WrightTier.NETHERITE - WrightTier.BASE;
        int chip = (width - gap * steps) / (steps + 1);
        for (int tier = WrightTier.BASE; tier <= WrightTier.NETHERITE; tier++) {
            int chipX = x + (tier - WrightTier.BASE) * (chip + gap);
            boolean enabled = current < WrightTier.BASE || tier != current;
            if (current >= WrightTier.BASE && tier > current) {
                enabled = canAfford(screen, current, tier);
            }
            drawMini(graphics, screen, chipX, y, chip, Component.translatable(WrightTier.nameKey(tier)), enabled || tier == current, tier == current, mouseX, mouseY);
        }
    }

    private int tierAt(double mouseX) {
        return tierAt(mouseX, panelX + 18, 0, PANEL_W - 36);
    }

    private int tierAt(double mouseX, int x, int ignoredY, int width) {
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

    private void place(int screenW, int screenH) {
        panelH = Math.min(screenH - 24, 340);
        panelX = (screenW - PANEL_W) / 2;
        panelY = Math.max(8, (screenH - panelH) / 2);
    }

    private int contentHeight(Swarm swarm) {
        return swarm.units().size() * ROW + (expanded >= 0 && expanded < swarm.units().size() ? EXPAND : 0);
    }

    private int markedCount(Swarm swarm) {
        int count = 0;
        for (Swarm.Unit unit : swarm.units()) {
            if (checked.contains(unit.id())) {
                count++;
            }
        }
        return count;
    }

    private int[] marked(Swarm swarm) {
        int[] units = new int[markedCount(swarm)];
        int cursor = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (checked.contains(swarm.units().get(i).id())) {
                units[cursor++] = i;
            }
        }
        return units;
    }

    private void toggleAll(Swarm swarm) {
        if (!swarm.units().isEmpty() && markedCount(swarm) == swarm.units().size()) {
            checked.clear();
            return;
        }
        for (Swarm.Unit unit : swarm.units()) {
            checked.add(unit.id());
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

    private int allThatFit(HangarScreen screen) {
        int room = Math.max(0, Swarm.capacity() - swarm(screen).units().size());
        if (screen.getMinecraft().player != null && screen.getMinecraft().player.getAbilities().instabuild) {
            return room;
        }
        return Math.min(room, held(screen));
    }

    private static Swarm swarm(HangarScreen screen) {
        return Swarm.from(screen.getMenu().getSlot(0).getItem());
    }

    private static int held(HangarScreen screen) {
        if (screen.getMinecraft().player == null) {
            return 0;
        }
        int count = 0;
        Inventory inventory = screen.getMinecraft().player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof WrightItem) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int count(HangarScreen screen, Item item) {
        if (item == null || screen.getMinecraft().player == null) {
            return 0;
        }
        return screen.getMinecraft().player.getInventory().countItem(item);
    }

    private static boolean canAfford(HangarScreen screen, int tier, int target) {
        if (screen.getMinecraft().player != null && screen.getMinecraft().player.getAbilities().instabuild) {
            return true;
        }
        for (int step = tier + 1; step <= target; step++) {
            if (count(screen, WrightTier.upgradeItem(step)) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawCheck(GuiGraphics graphics, int x, int y, boolean checked, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10;
        graphics.fill(x, y, x + 10, y + 10, hover ? 0xFF2C3E52 : 0xFF1A2633);
        WrightsUi.border(graphics, x, y, 10, 10, checked ? WrightsUi.COPPER : (hover ? WrightsUi.COPPER : 0xFF3D4E5E));
        if (checked) {
            graphics.fill(x + 2, y + 2, x + 8, y + 8, WrightsUi.COPPER);
        }
    }

    private static void drawMini(GuiGraphics graphics, HangarScreen screen, int x, int y, int width, Component label, boolean enabled, boolean selected, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        int fill = !enabled && !selected ? 0xFF141A22 : (selected ? 0xFF2A4054 : (hover ? 0xFF2C3E52 : 0xFF1A2633));
        graphics.fill(x, y, x + width, y + 14, fill);
        int border = selected ? WrightsUi.COPPER : (enabled ? (hover ? WrightsUi.COPPER : 0xFF3D4E5E) : 0xFF2A3440);
        WrightsUi.border(graphics, x, y, width, 14, border);
        String text = screen.font().plainSubstrByWidth(label.getString(), Math.max(0, width - 6));
        graphics.drawString(screen.font(), text, x + (width - screen.font().width(text)) / 2, y + 3, enabled || selected ? WrightsUi.TEXT : 0xFF6E7A86, false);
    }
}
