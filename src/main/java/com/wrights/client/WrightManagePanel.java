package com.wrights.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.wrights.drone.HangarOps;
import com.wrights.drone.Loadout;
import com.wrights.drone.PartSlot;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;
import com.wrights.item.ModItems;
import com.wrights.item.WrightItem;
import com.wrights.item.WrightPartItem;
import com.wrights.network.BlueprintPayloads;

/**
 * Hangar bay for one wrightport: textured 5×2 STORAGE BAY plus three DRONE HARDWARE part slots.
 * Wrights out on a job stay in their slot but are locked until they return.
 */
final class WrightManagePanel {
    /** Marked dialog: first part row, and the height of each. */
    private static final int MARKED_TOP = 70;
    private static final int MARKED_ROW = 30;
    private static final int NONE = 0;
    private static final int ADD = 1;
    private static final int MARKED = 2;

    private WrightsButton partsButton;
    private WrightsButton backButton;
    private EditBox countBox;
    private Component tipLine = Component.empty();

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

    /** Tip text for the chrome strip under the hangar art. */
    Component tipLine() {
        return tipLine == null ? Component.empty() : tipLine;
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
        // Tip strip owns most of the chrome; Upgrade marked (when checked) and Back sit right-aligned.
        int chromeY = panelY + HangarGui.HEIGHT + 2;
        partsButton = new WrightsButton(0, chromeY, UPGRADE_MARKED_W, 14,
                Component.translatable("screen.wrights.manage_parts_marked"), button -> openMarked(screen));
        partsButton.active = false;
        screen.addButton(partsButton);
        backButton = new WrightsButton(0, chromeY, BACK_W, 14,
                Component.translatable("screen.wrights.back"), button -> close(screen));
        screen.addButton(backButton);
        countBox = new EditBox(screen.font(), 0, 0, 80, 16, Component.translatable("screen.wrights.manage_add_count"));
        countBox.setMaxLength(4);
        countBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        countBox.setVisible(false);
        screen.addField(countBox);
        layoutChrome(screen);
    }

    private static final int UPGRADE_MARKED_W = 88;
    private static final int BACK_W = 52;
    private static final int CHROME_RIGHT = 8;
    private static final int CHROME_GAP = 4;

    void layoutChrome(ManageHost screen) {
        place(screen);
        int y = panelY + HangarGui.HEIGHT + 2;
        int right = panelX + HangarGui.WIDTH - CHROME_RIGHT;
        if (backButton != null) {
            backButton.setWidth(BACK_W);
            backButton.setX(right - BACK_W);
            backButton.setY(y);
        }
        if (partsButton != null) {
            partsButton.setWidth(UPGRADE_MARKED_W);
            boolean showBack = !screen.embedded();
            int upgradeRight = showBack ? right - BACK_W - CHROME_GAP : right;
            partsButton.setX(upgradeRight - UPGRADE_MARKED_W);
            partsButton.setY(y);
        }
    }

    void render(ManageHost screen, GuiGraphics graphics, int mouseX, int mouseY) {
        place(screen);
        layoutChrome(screen);
        if (!screen.embedded()) {
            graphics.fill(0, 0, screen.screenWidth(), screen.screenHeight(), 0xC0060C14);
            HangarGui.blitPanel(graphics, panelX, panelY);
            graphics.fill(panelX, panelY + HangarGui.HEIGHT, panelX + HangarGui.WIDTH, panelY + HangarGui.PANEL_H, 0xFF0C141C);
        } else {
            graphics.fill(panelX, panelY + HangarGui.HEIGHT, panelX + HangarGui.WIDTH, panelY + HangarGui.PANEL_H, 0xFF0C141C);
        }
        Swarm swarm = screen.swarm();
        if (expanded >= 0 && (expanded >= swarm.units().size() || away(screen, swarm, expanded))) {
            expanded = -1;
        }
        int away = awayCount(screen, swarm);
        int home = swarm.units().size() - away;
        headExtra = 0;
        int marked = markedCount(screen, swarm);
        boolean chrome = dialog == NONE;
        boolean tool = screen.toolHeld();
        if (partsButton != null) {
            partsButton.setShown(open && chrome && marked > 0);
            partsButton.active = open && chrome && tool && marked > 0;
        }
        if (backButton != null) {
            backButton.setShown(open && chrome && !screen.embedded());
        }
        if (countBox != null) {
            countBox.setVisible(open && dialog == ADD);
        }

        int bayX = bayX();
        int bayY = bayY();
        for (int i = 0; i < HangarBay.SLOTS; i++) {
            int sx = HangarBay.hangarSlotX(bayX, i, HangarGui.BAY_SLOT, HangarGui.BAY_GAP);
            int sy = HangarBay.hangarSlotY(bayY, i, HangarGui.BAY_SLOT, HangarGui.BAY_GAP_Y);
            boolean hovered = mouseX >= sx && mouseX < sx + HangarGui.BAY_SLOT
                    && mouseY >= sy && mouseY < sy + HangarGui.BAY_SLOT;
            if (i < swarm.units().size()) {
                Swarm.Unit unit = swarm.units().get(i);
                boolean locked = away(screen, swarm, i);
                if (locked) {
                    checked.remove(unit.id());
                }
                HangarBay.drawSlot(graphics, screen.font(), sx, sy, HangarGui.BAY_SLOT, WrightItem.stack(unit),
                        checked.contains(unit.id()), i == expanded, locked, hovered, false);
            } else {
                HangarBay.drawSlot(graphics, screen.font(), sx, sy, HangarGui.BAY_SLOT, ItemStack.EMPTY,
                        false, false, false, hovered, false);
            }
        }

        drawHardware(graphics, screen, swarm, mouseX, mouseY);
        drawPartTooltip(graphics, screen, swarm, mouseX, mouseY);
        // Ephemeral notices only — never the always-on hangar_hint (that stacked with port tips).
        tipLine = noticeTip();
        if (!tipLine.getString().isEmpty()) {
            HangarGui.drawTipStrip(graphics, screen.font(), panelX, panelY, tipLine);
        }

        if (dialog != NONE) {
            renderDialog(screen, graphics, swarm, mouseX, mouseY);
        }
    }

    private void drawHardware(GuiGraphics graphics, ManageHost screen, Swarm swarm, int mouseX, int mouseY) {
        PartSlot[] slots = PartSlot.values();
        boolean selected = expanded >= 0 && expanded < swarm.units().size();
        boolean locked = selected && away(screen, swarm, expanded);
        Loadout parts = selected ? swarm.units().get(expanded).parts() : null;
        PartSlot dropHint = dropPartSlot(screen);
        for (int i = 0; i < slots.length; i++) {
            int x = HangarGui.partSlotX(panelX, i);
            int y = HangarGui.partSlotY(panelY);
            boolean hover = mouseX >= x && mouseX < x + HangarGui.PART_SLOT
                    && mouseY >= y && mouseY < y + HangarGui.PART_SLOT;
            ItemStack stack = ItemStack.EMPTY;
            if (parts != null) {
                int tier = parts.tier(slots[i]);
                Item part = slots[i].item(tier);
                if (part != null) {
                    stack = new ItemStack(part);
                }
            }
            if (!stack.isEmpty()) {
                WrightsUi.slotItem(graphics, screen.font(), stack, x, y, HangarGui.PART_SLOT, 1.28F);
                if (locked) {
                    graphics.fill(x + 1, y + 1, x + HangarGui.PART_SLOT - 1, y + HangarGui.PART_SLOT - 1, 0x88060C14);
                }
            }
            boolean validDrop = selected && !locked && dropHint == slots[i];
            if ((hover && selected && !locked) || validDrop) {
                HangarBay.hangarHoverTint(graphics, x, y, HangarGui.PART_SLOT);
            }
        }
    }

    /** Part slot that accepts the stack on the cursor (inventory pickup), or null. */
    private static PartSlot dropPartSlot(ManageHost screen) {
        ItemStack carried = carriedStack(screen);
        if (carried.getItem() instanceof WrightPartItem part) {
            return part.slot();
        }
        return null;
    }

    private static ItemStack carriedStack(ManageHost screen) {
        if (screen.minecraft().player == null || screen.minecraft().player.containerMenu == null) {
            return ItemStack.EMPTY;
        }
        return screen.minecraft().player.containerMenu.getCarried();
    }

    private Component noticeTip() {
        long now = System.currentTimeMillis();
        if (WrightsClientState.notice != null && now < WrightsClientState.noticeUntil
                && !WrightsClientState.notice.getString().isEmpty()) {
            return WrightsClientState.notice;
        }
        return Component.empty();
    }

    /** Mouse tooltip for a hardware part — name + stats. */
    private void drawPartTooltip(GuiGraphics graphics, ManageHost screen, Swarm swarm, int mouseX, int mouseY) {
        if (expanded < 0 || expanded >= swarm.units().size()) {
            return;
        }
        int hit = HangarGui.partHit(panelX, panelY, mouseX, mouseY);
        if (hit < 0) {
            return;
        }
        PartSlot slot = PartSlot.values()[hit];
        int tier = swarm.units().get(expanded).parts().tier(slot);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(slot.fittedKey(tier)).withColor(WrightTier.textColor(tier) & 0xFFFFFF));
        lines.addAll(WrightPartItem.stats(slot, tier));
        graphics.renderComponentTooltip(screen.font(), lines, mouseX, mouseY);
    }

    boolean mouseClicked(ManageHost screen, double mouseX, double mouseY, int button) {
        if (!open || (button != 0 && button != 1)) {
            return false;
        }
        place(screen);
        if (dialog != NONE) {
            return button == 0 && dialogClick(screen, mouseX, mouseY);
        }
        Swarm swarm = screen.swarm();
        int bayHit = HangarBay.hit(bayX(), bayY(), mouseX, mouseY, HangarGui.BAY_SLOT, HangarGui.BAY_GAP, HangarGui.BAY_GAP_Y);
        if (button == 1) {
            if (bayHit >= 0 && bayHit < swarm.units().size()) {
                return takeWright(screen, swarm, bayHit);
            }
            return false;
        }
        int partHit = HangarGui.partHit(panelX, panelY, mouseX, mouseY);
        if (partHit >= 0) {
            return hardwareClick(screen, swarm, PartSlot.values()[partHit]);
        }
        if (bayHit >= 0) {
            return hangarClick(screen, swarm, bayHit, mouseX, mouseY);
        }
        return false;
    }

    private boolean takeWright(ManageHost screen, Swarm swarm, int slot) {
        if (slot < 0 || slot >= swarm.units().size() || away(screen, swarm, slot) || !screen.requireTool()) {
            return true;
        }
        UUID id = swarm.units().get(slot).id();
        screen.edit(HangarOps.TAKE, slot);
        if (expanded == slot) {
            expanded = -1;
        }
        checked.remove(id);
        return true;
    }

    /**
     * Shift-click a part in the player inventory onto the selected wright.
     * @return true when the click was handled (do not run normal quick-move)
     */
    boolean tryQuickFitPart(ManageHost screen, ItemStack stack) {
        if (!open || stack == null || stack.isEmpty() || !(stack.getItem() instanceof WrightPartItem part)) {
            return false;
        }
        Swarm swarm = screen.swarm();
        if (expanded < 0 || expanded >= swarm.units().size()) {
            return false;
        }
        if (away(screen, swarm, expanded) || !screen.requireTool()) {
            return true;
        }
        int fitted = swarm.units().get(expanded).parts().tier(part.slot());
        if (part.tier() == fitted || !have(screen, part.slot(), part.tier())) {
            return true;
        }
        int[] targets = markedCount(screen, swarm) > 0 ? marked(screen, swarm) : new int[] { expanded };
        if (targets.length == 0) {
            targets = new int[] { expanded };
        }
        screen.batch(HangarOps.partFit(part.slot()), part.tier(), targets);
        return true;
    }

    private boolean hardwareClick(ManageHost screen, Swarm swarm, PartSlot slot) {
        if (expanded < 0 || expanded >= swarm.units().size()) {
            return true;
        }
        if (away(screen, swarm, expanded) || !screen.requireTool()) {
            return true;
        }
        int fitted = swarm.units().get(expanded).parts().tier(slot);
        ItemStack held = heldPart(screen);
        if (!held.isEmpty() && held.getItem() instanceof WrightPartItem part && part.slot() == slot) {
            if (part.tier() != fitted && have(screen, slot, part.tier())) {
                int[] targets = markedCount(screen, swarm) > 0 ? marked(screen, swarm) : new int[] { expanded };
                if (targets.length == 0) {
                    targets = new int[] { expanded };
                }
                screen.batch(HangarOps.partFit(slot), part.tier(), targets);
            }
            return true;
        }
        if (fitted > WrightTier.BASIC) {
            screen.edit(HangarOps.partRemove(slot), expanded);
            return true;
        }
        if (fitted < WrightTier.APEX && have(screen, slot, fitted + 1)) {
            screen.edit(HangarOps.partNext(slot), expanded);
        }
        return true;
    }

    private static ItemStack heldPart(ManageHost screen) {
        ItemStack carried = carriedStack(screen);
        if (carried.getItem() instanceof WrightPartItem) {
            return carried;
        }
        if (screen.minecraft().player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = screen.minecraft().player.getMainHandItem();
        if (main.getItem() instanceof WrightPartItem) {
            return main;
        }
        ItemStack off = screen.minecraft().player.getOffhandItem();
        if (off.getItem() instanceof WrightPartItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private boolean hangarClick(ManageHost screen, Swarm swarm, int slot, double mouseX, double mouseY) {
        if (slot < swarm.units().size()) {
            Swarm.Unit unit = swarm.units().get(slot);
            boolean locked = away(screen, swarm, slot);
            int sx = HangarBay.hangarSlotX(bayX(), slot, HangarGui.BAY_SLOT, HangarGui.BAY_GAP);
            int sy = HangarBay.hangarSlotY(bayY(), slot, HangarGui.BAY_SLOT, HangarGui.BAY_GAP_Y);
            if (!locked && hit(mouseX, mouseY, sx + 2, sy + 2, 8, 8)) {
                toggle(unit.id());
                return true;
            }
            if (Screen.hasShiftDown()) {
                return takeWright(screen, swarm, slot);
            }
            if (expanded == slot) {
                expanded = -1;
            } else {
                expanded = slot;
                scroll = 0;
            }
            return true;
        }
        if (slot >= Swarm.capacity()) {
            return true;
        }
        tryOpenAdd(screen);
        return true;
    }

    boolean mouseScrolled(ManageHost screen, double mouseX, double mouseY, double delta) {
        if (!open) {
            return false;
        }
        if (dialog != NONE) {
            return true;
        }
        return false;
    }

    private void tryOpenAdd(ManageHost screen) {
        if (!screen.requireTool() || roomLeft(screen.swarm()) <= 0) {
            return;
        }
        boolean stock = canAddFromStock(screen);
        boolean craft = canCraftWright(screen);
        if (!stock && !craft) {
            WrightsClientState.notice = Component.translatable("screen.wrights.manage_no_wrights");
            WrightsClientState.noticeUntil = System.currentTimeMillis() + 2800L;
            return;
        }
        dialog = ADD;
        if (countBox != null) {
            countBox.setVisible(false);
            countBox.setFocused(false);
        }
    }

    private static boolean creative(ManageHost screen) {
        return screen.minecraft().player != null && screen.minecraft().player.getAbilities().instabuild;
    }

    private static boolean canAddFromStock(ManageHost screen) {
        return creative(screen) || readyWrights(screen) > 0;
    }

    private static boolean canCraftWright(ManageHost screen) {
        return creative(screen) || digitalCraftable(ModItems.WRIGHT.get());
    }

    private void openMarked(ManageHost screen) {
        if (!screen.requireTool()) {
            return;
        }
        dialog = MARKED;
    }

    private void removeSelected(ManageHost screen) {
        if (!screen.requireTool()) {
            return;
        }
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
        int dh = dialog == ADD ? 110 : MARKED_TOP + PartSlot.values().length * MARKED_ROW + 84;
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
            if (!screen.requireTool()) {
                closeTop();
                return true;
            }
            boolean stock = canAddFromStock(screen);
            boolean craft = canCraftWright(screen);
            if (hit(mouseX, mouseY, dx + 12, dy + 36, dw - 24, 16)) {
                if (stock) {
                    screen.batch(HangarOps.ADD_STOCK, 1, new int[0]);
                    closeTop();
                }
                return true;
            }
            if (hit(mouseX, mouseY, dx + 12, dy + 58, dw - 24, 16)) {
                if (craft) {
                    screen.batch(HangarOps.ADD_CRAFT, 1, new int[0]);
                    closeTop();
                }
                return true;
            }
            return true;
        }
        if (!screen.requireTool()) {
            closeTop();
            return true;
        }
        int tier = tierAt(mouseX, dx + 12, dw - 24);
        PartSlot[] slots = PartSlot.values();
        for (int k = 0; k < slots.length; k++) {
            int chips = dy + MARKED_TOP + k * MARKED_ROW + 10;
            if (tier >= WrightTier.BASIC && mouseY >= chips && mouseY < chips + 14) {
                screen.batch(HangarOps.partFit(slots[k]), tier, marked(screen, screen.swarm()));
                closeTop();
                return true;
            }
        }
        int buttons = dy + MARKED_TOP + slots.length * MARKED_ROW;
        if (hit(mouseX, mouseY, dx + 12, buttons, dw - 24, 16)) {
            screen.batch(HangarOps.BEST_PARTS, 0, marked(screen, screen.swarm()));
            closeTop();
            return true;
        }
        if (hit(mouseX, mouseY, dx + 12, buttons + 22, dw - 24, 16)) {
            screen.batch(HangarOps.STRIP_MARKED, 0, marked(screen, screen.swarm()));
            closeTop();
            return true;
        }
        return true;
    }

    private void renderDialog(ManageHost screen, GuiGraphics graphics, Swarm swarm, int mouseX, int mouseY) {
        int dw = Math.min(312, Math.max(220, screen.screenWidth() - 24));
        int dh = dialog == ADD ? 110 : MARKED_TOP + PartSlot.values().length * MARKED_ROW + 84;
        int dx = (screen.screenWidth() - dw) / 2;
        int dy = (screen.screenHeight() - dh) / 2;
        graphics.fill(0, 0, screen.screenWidth(), screen.screenHeight(), 0x88060C14);
        WrightsUi.panel(graphics, dx, dy, dw, dh);
        if (dialog == ADD) {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_add_title"), dx + 12, dy + 12, WrightsUi.COPPER, false);
            boolean stock = canAddFromStock(screen);
            boolean craft = canCraftWright(screen);
            int ready = readyWrights(screen);
            drawMini(graphics, screen, dx + 12, dy + 36, dw - 24,
                    Component.translatable("screen.wrights.manage_add_from_stock", ready), stock, false, mouseX, mouseY);
            drawMini(graphics, screen, dx + 12, dy + 58, dw - 24,
                    Component.translatable("screen.wrights.manage_add_craft_one"), craft, false, mouseX, mouseY);
        } else {
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_marked_title"), dx + 12, dy + 12, WrightsUi.COPPER, false);
            graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_marked_count", markedCount(screen, swarm)), dx + 12, dy + 28, WrightsUi.TEXT, false);
            int hintY = dy + 44;
            for (var line : screen.font().split(Component.translatable("screen.wrights.manage_marked_hint"), dw - 24)) {
                graphics.drawString(screen.font(), line, dx + 12, hintY, WrightsUi.MUTED, false);
                hintY += 10;
            }
            boolean tool = screen.toolHeld();
            PartSlot[] slots = PartSlot.values();
            for (int k = 0; k < slots.length; k++) {
                int top = dy + MARKED_TOP + k * MARKED_ROW;
                graphics.drawString(screen.font(), Component.translatable(slots[k].nameKey()), dx + 12, top, WrightsUi.TEXT, false);
                drawTierChoices(graphics, screen, dx + 12, top + 10, dw - 24, slots[k], -1, mouseX, mouseY, tool);
            }
            int buttons = dy + MARKED_TOP + slots.length * MARKED_ROW;
            drawMini(graphics, screen, dx + 12, buttons, dw - 24, Component.translatable("screen.wrights.manage_best_parts"), tool, false, mouseX, mouseY);
            drawMini(graphics, screen, dx + 12, buttons + 22, dw - 24, Component.translatable("screen.wrights.manage_strip"), tool, false, mouseX, mouseY);
        }
        drawMini(graphics, screen, dx + dw - 78, dy + dh - 26, 66, Component.translatable("screen.wrights.manage_cancel"), true, false, mouseX, mouseY);
    }

    private void drawTierChoices(GuiGraphics graphics, ManageHost screen, int x, int y, int width, PartSlot slot, int current,
            int mouseX, int mouseY, boolean tool) {
        int gap = 4;
        int steps = WrightTier.APEX - WrightTier.BASIC;
        int chip = (width - gap * steps) / (steps + 1);
        for (int tier = WrightTier.BASIC; tier <= WrightTier.APEX; tier++) {
            int chipX = x + (tier - WrightTier.BASIC) * (chip + gap);
            boolean enabled = tool && tier != current && (current < WrightTier.BASIC || have(screen, slot, tier));
            drawMini(graphics, screen, chipX, y, chip, Component.translatable(WrightTier.nameKey(tier)), enabled || tier == current, tier == current, mouseX, mouseY);
        }
    }

    private int tierAt(double mouseX) {
        return tierAt(mouseX, panelX + 18, panelW - 36);
    }

    private int tierAt(double mouseX, int x, int width) {
        int gap = 4;
        int steps = WrightTier.APEX - WrightTier.BASIC;
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
        return WrightTier.BASIC + index;
    }

    private void place(ManageHost screen) {
        panelW = HangarGui.WIDTH;
        panelH = HangarGui.PANEL_H;
        if (screen.embedded()) {
            panelX = screen.areaX();
            panelY = screen.areaY();
            return;
        }
        panelX = WrightsUi.centerX(panelW, screen.screenWidth());
        panelY = WrightsUi.centerY(panelH, screen.screenHeight());
    }

    boolean keyPressed(ManageHost screen, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    private int bayX() {
        return panelX + HangarGui.BAY_X;
    }

    private int bayY() {
        return panelY + HangarGui.BAY_Y;
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

    /** True when the {@code tier} part of {@code slot} is held, stored, or craftable. Basic needs nothing. */
    private static boolean have(ManageHost screen, PartSlot slot, int tier) {
        Item part = slot.item(tier);
        if (part == null) {
            return true;
        }
        if (screen.minecraft().player != null && screen.minecraft().player.getAbilities().instabuild) {
            return true;
        }
        return count(screen, part) > 0 || digitalStock(part) > 0L || digitalCraftable(part);
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
        if (partsButton != null) {
            partsButton.setShown(false);
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
