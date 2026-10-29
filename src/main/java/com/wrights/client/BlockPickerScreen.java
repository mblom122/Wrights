package com.wrights.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;
import com.wrights.util.BlockFilters;

public class BlockPickerScreen extends Screen {
    private enum Tab {
        RECENT,
        INVENTORY,
        ALL
    }

    private static final int COLS = 8;
    private static final int CELL = 18;
    private static List<Item> allBlocks;

    private final Screen parent;
    private final UUID id;
    private final int cellX;
    private final int cellY;
    private final int cellZ;
    private final List<ItemStack> shown = new ArrayList<>();
    private EditBox search;
    private WrightsButton recentButton;
    private WrightsButton inventoryButton;
    private WrightsButton allButton;
    private Tab tab = Tab.RECENT;
    private String builtQuery = "\0";
    private Tab builtTab;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listH;
    private int scroll;

    public BlockPickerScreen(Screen parent, UUID id, int cellX, int cellY, int cellZ) {
        super(Component.translatable("screen.wrights.picker"));
        this.parent = parent;
        this.id = id;
        this.cellX = cellX;
        this.cellY = cellY;
        this.cellZ = cellZ;
    }

    public static void open(Screen parent, UUID id, int x, int y, int z) {
        if (id == null) {
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new BlockPickerScreen(parent, id, x, y, z));
    }

    @Override
    protected void init() {
        panelW = Math.min(220, this.width - 16);
        panelH = Math.min(248, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        search = new EditBox(this.font, panelX + 12, panelY + 28, panelW - 24, 16, Component.translatable("menu.wrights.search"));
        search.setHint(Component.translatable("menu.wrights.search"));
        search.setMaxLength(48);
        search.setResponder(value -> rebuild(true));
        addRenderableWidget(search);
        int tabY = panelY + 48;
        int tabW = (panelW - 32) / 3;
        recentButton = new WrightsButton(panelX + 12, tabY, tabW, 16, Component.translatable("screen.wrights.picker_recent"), button -> setTab(Tab.RECENT));
        inventoryButton = new WrightsButton(panelX + 16 + tabW, tabY, tabW, 16, Component.translatable("screen.wrights.picker_inventory"), button -> setTab(Tab.INVENTORY));
        allButton = new WrightsButton(panelX + 20 + tabW * 2, tabY, tabW, 16, Component.translatable("screen.wrights.picker_all"), button -> setTab(Tab.ALL));
        addRenderableWidget(recentButton);
        addRenderableWidget(inventoryButton);
        addRenderableWidget(allButton);
        listX = panelX + (panelW - COLS * CELL) / 2;
        listY = tabY + 22;
        listH = panelH - 108;
        addRenderableWidget(new WrightsButton(panelX + 12, panelY + panelH - 28, panelW - 24, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
        markTabs();
        rebuild(true);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0060C14);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 12, panelY + 10, WrightsUi.COPPER, false);
        rebuild(false);
        int rows = Math.max(1, (listH - 4) / CELL);
        int maxScroll = Math.max(0, (shown.size() + COLS - 1) / COLS - rows);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        if (shown.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.picker_empty"), panelX + panelW / 2, listY + 8, WrightsUi.MUTED);
            return;
        }
        int start = scroll * COLS;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < rows * COLS && start + i < shown.size(); i++) {
            int column = i % COLS;
            int row = i / COLS;
            int x = listX + column * CELL;
            int y = listY + row * CELL;
            ItemStack stack = shown.get(start + i);
            boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            if (over) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x66F2D48A);
                hovered = stack;
            }
            graphics.renderItem(stack, x + 1, y + 1);
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(this.font, hovered, (int) mouseX, (int) mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !shown.isEmpty()) {
            int column = (int) ((mouseX - listX) / CELL);
            int row = (int) ((mouseY - listY) / CELL);
            int rows = Math.max(1, (listH - 4) / CELL);
            if (column >= 0 && column < COLS && row >= 0 && row < rows) {
                int index = (scroll + row) * COLS + column;
                if (index >= 0 && index < shown.size() && shown.get(index).getItem() instanceof BlockItem blockItem) {
                    choose(blockItem);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = Math.max(1, (listH - 4) / CELL);
        int maxScroll = Math.max(0, (shown.size() + COLS - 1) / COLS - rows);
        scroll = Mth.clamp(scroll + (scrollY > 0 ? -1 : 1), 0, maxScroll);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setTab(Tab next) {
        tab = next;
        markTabs();
        rebuild(true);
    }

    private void markTabs() {
        recentButton.setPicked(tab == Tab.RECENT);
        inventoryButton.setPicked(tab == Tab.INVENTORY);
        allButton.setPicked(tab == Tab.ALL);
    }

    private void rebuild(boolean resetScroll) {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.equals(builtQuery) && tab == builtTab) {
            return;
        }
        builtQuery = query;
        builtTab = tab;
        if (resetScroll) {
            scroll = 0;
        }
        shown.clear();
        for (Item item : source()) {
            ItemStack stack = new ItemStack(item);
            if (!query.isEmpty() && !stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            shown.add(stack);
        }
    }

    private List<Item> source() {
        if (tab == Tab.RECENT) {
            return RecentBlocks.items();
        }
        if (tab == Tab.INVENTORY) {
            List<Item> items = new ArrayList<>();
            Set<Item> seen = new HashSet<>();
            if (this.minecraft != null && this.minecraft.player != null) {
                var inventory = this.minecraft.player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack.getItem() instanceof BlockItem blockItem
                            && BlockFilters.canStore(blockItem.getBlock().defaultBlockState())
                            && seen.add(stack.getItem())) {
                        items.add(stack.getItem());
                    }
                }
            }
            return items;
        }
        return allBlocks();
    }

    private static List<Item> allBlocks() {
        if (allBlocks != null) {
            return allBlocks;
        }
        allBlocks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem blockItem && BlockFilters.canStore(blockItem.getBlock().defaultBlockState())) {
                allBlocks.add(item);
            }
        }
        return allBlocks;
    }

    private void choose(BlockItem item) {
        RecentBlocks.remember(item);
        int stateId = Block.getId(item.getBlock().defaultBlockState());
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(id, cellX, cellY, cellZ, stateId, false));
        this.minecraft.setScreen(parent);
    }
}
