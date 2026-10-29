package com.wrights.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
 * The wright list opened from the Wright Manager. Adding uses the click modifiers:
 * plain is one, shift is 16, alt is a stack of 64, and alt with shift is everything that fits.
 */
final class WrightManagePanel {
    private static final int PANEL_W = 248;
    private static final int ROW = 14;
    private static final int EXPAND = 46;
    private static final int UPGRADE_X = 18;
    private static final int UPGRADE_W = 108;
    private static final int DOWNGRADE_X = 132;
    private static final int DOWNGRADE_W = 100;

    private WrightsButton removeButton;

    private boolean open;
    private int scroll;
    private int expanded = -1;
    private int panelX;
    private int panelY;
    private int panelH;

    boolean isOpen() {
        return open;
    }

    void toggle(HangarScreen screen) {
        open = !open;
        scroll = 0;
        expanded = -1;
        screen.layoutHangar();
    }

    void close(HangarScreen screen) {
        if (!open) {
            return;
        }
        open = false;
        screen.layoutHangar();
    }

    void addWidgets(HangarScreen screen) {
        place(screen.width, screen.height);
        int y = panelY + panelH - 34;
        screen.addButton(new WrightsButton(panelX + 12, y, 70, 16, Component.translatable("screen.wrights.manage_add"),
                button -> screen.edit(HangarOps.INSERT, mode())));
        removeButton = new WrightsButton(panelX + 88, y, 70, 16, Component.translatable("screen.wrights.manage_remove"),
                button -> removeSelected(screen));
        removeButton.active = false;
        screen.addButton(removeButton);
        screen.addButton(new WrightsButton(panelX + 164, y, 72, 16, Component.translatable("screen.wrights.back"),
                button -> close(screen)));
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
        if (removeButton != null) {
            removeButton.active = expanded >= 0;
        }
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_count", swarm.units().size(), Swarm.capacity(), held(screen)),
                panelX + 12, panelY + 22, WrightsUi.MUTED, false);
        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_bulk"), panelX + 12, panelY + 36, WrightsUi.MUTED, false);

        int listTop = panelY + 52;
        int listBottom = panelY + panelH - 44;
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
                if (i == expanded) {
                    graphics.fill(panelX + 10, y - 1, panelX + PANEL_W - 10, y + ROW - 1, 0x553C2A1A);
                }
                String name = Component.translatable("screen.wrights.drone_name", i + 1).getString();
                graphics.drawString(screen.font(), name, panelX + 14, y, WrightsUi.TEXT, false);
                Component tierName = Component.translatable(WrightTier.nameKey(tier));
                graphics.drawString(screen.font(), tierName, panelX + PANEL_W - 14 - screen.font().width(tierName), y, WrightTier.textColor(tier), false);
            }
            y += ROW;
            if (i == expanded) {
                if (y + EXPAND > listTop && y < listBottom) {
                    graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_pace"), panelX + 18, y + 2, WrightsUi.TEXT, false);
                    boolean canDowngrade = tier > WrightTier.COPPER;
                    if (tier < WrightTier.NETHERITE) {
                        Component next = Component.translatable(WrightTier.nameKey(tier + 1));
                        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_next", next), panelX + 18, y + 14, WrightsUi.MUTED, false);
                        boolean have = screen.getMinecraft().player != null && (screen.getMinecraft().player.getAbilities().instabuild
                                || count(screen, WrightTier.upgradeItem(tier + 1)) > 0);
                        drawMini(graphics, screen, panelX + UPGRADE_X, y + 28, UPGRADE_W, Component.translatable("screen.wrights.manage_upgrade", next), have, mouseX, mouseY);
                        drawMini(graphics, screen, panelX + DOWNGRADE_X, y + 28, DOWNGRADE_W, Component.translatable("screen.wrights.manage_remove_upgrade"), canDowngrade, mouseX, mouseY);
                    } else {
                        graphics.drawString(screen.font(), Component.translatable("screen.wrights.manage_best"), panelX + 18, y + 14, WrightsUi.MUTED, false);
                        drawMini(graphics, screen, panelX + UPGRADE_X, y + 28, UPGRADE_W, Component.translatable("screen.wrights.manage_remove_upgrade"), canDowngrade, mouseX, mouseY);
                    }
                }
                y += EXPAND;
            }
        }
        graphics.disableScissor();
    }

    boolean mouseClicked(HangarScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Swarm swarm = swarm(screen);
        int listTop = panelY + 52;
        int listBottom = panelY + panelH - 44;
        if (mouseX < panelX + 8 || mouseX > panelX + PANEL_W - 8 || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int local = (int) mouseY - listTop + scroll;
        int y = 0;
        for (int i = 0; i < swarm.units().size(); i++) {
            if (local >= y && local < y + ROW) {
                expanded = expanded == i ? -1 : i;
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
                    if (tier > WrightTier.COPPER && mouseX >= downX && mouseX < downX + downW) {
                        screen.edit(HangarOps.DOWNGRADE, i);
                        return true;
                    }
                }
                y += EXPAND;
            }
        }
        return true;
    }

    private void removeSelected(HangarScreen screen) {
        if (expanded < 0) {
            return;
        }
        screen.edit(HangarOps.TAKE, expanded);
        expanded = -1;
    }

    boolean mouseScrolled(HangarScreen screen, double mouseX, double mouseY, double delta) {
        int listTop = panelY + 52;
        int listBottom = panelY + panelH - 44;
        if (mouseX < panelX || mouseX > panelX + PANEL_W || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        int max = Math.max(0, contentHeight(swarm(screen)) - (listBottom - listTop));
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * ROW, 0, max);
        return true;
    }

    private void place(int screenW, int screenH) {
        panelH = Math.min(screenH - 24, 280);
        panelX = (screenW - PANEL_W) / 2;
        panelY = Math.max(8, (screenH - panelH) / 2);
    }

    private int contentHeight(Swarm swarm) {
        return swarm.units().size() * ROW + (expanded >= 0 && expanded < swarm.units().size() ? EXPAND : 0);
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

    private static int mode() {
        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();
        if (shift && alt) {
            return HangarOps.ALL;
        }
        if (alt) {
            return HangarOps.STACK;
        }
        if (shift) {
            return HangarOps.SIXTEEN;
        }
        return HangarOps.ONE;
    }

    private static void drawMini(GuiGraphics graphics, HangarScreen screen, int x, int y, int width, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + width, y + 14, !enabled ? 0xFF141A22 : (hover ? 0xFF2C3E52 : 0xFF1A2633));
        WrightsUi.border(graphics, x, y, width, 14, enabled ? (hover ? WrightsUi.COPPER : 0xFF3D4E5E) : 0xFF2A3440);
        String text = screen.font().plainSubstrByWidth(label.getString(), width - 6);
        graphics.drawString(screen.font(), text, x + (width - screen.font().width(text)) / 2, y + 3, enabled ? WrightsUi.TEXT : 0xFF6E7A86, false);
    }
}
