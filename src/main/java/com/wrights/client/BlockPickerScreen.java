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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;
import com.wrights.util.BlockFilters;

public class BlockPickerScreen extends Screen implements JeiPreview {
    private enum Tab {
        RECENT,
        INVENTORY,
        ALL
    }

    private static final int COLS = 8;
    private static final int CELL = 18;
    private static List<Item> allBlocks;
    private static List<Block> sourceFluids;

    private final Screen parent;
    private final UUID id;
    private final int cellX;
    private final int cellY;
    private final int cellZ;
    private final List<Choice> shown = new ArrayList<>();
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

    /** Screen that opened this picker — library edit does not need the controller held. */
    public Screen parent() {
        return parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(220, this.width);
        panelH = WrightsUi.fitH(248, this.height);
        panelX = JeiPreview.left(this.width, panelW);
        panelY = WrightsUi.centerY(panelH, this.height);
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
        WrightsUi.scrim(graphics, this.width, this.height);
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
        Choice hovered = null;
        for (int i = 0; i < rows * COLS && start + i < shown.size(); i++) {
            int column = i % COLS;
            int row = i / COLS;
            int x = listX + column * CELL;
            int y = listY + row * CELL;
            Choice choice = shown.get(start + i);
            boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            if (over) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x66F2D48A);
                hovered = choice;
            }
            if (choice.fluid() != null) {
                FluidIcons.draw(graphics, choice.fluid(), x + 1, y + 1, 16);
            } else if (!choice.icon().isEmpty()) {
                graphics.renderItem(choice.icon(), x + 1, y + 1);
            }
        }
        if (hovered != null) {
            if (hovered.fluid() != null) {
                graphics.renderTooltip(this.font, hovered.name(), (int) mouseX, (int) mouseY);
            } else if (!hovered.icon().isEmpty()) {
                graphics.renderTooltip(this.font, hovered.icon(), (int) mouseX, (int) mouseY);
            }
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
                if (index >= 0 && index < shown.size()) {
                    choose(shown.get(index));
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
        for (Choice choice : source()) {
            if (!query.isEmpty() && !choice.name().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            shown.add(choice);
        }
    }

    private List<Choice> source() {
        List<Choice> choices = new ArrayList<>();
        if (tab == Tab.RECENT) {
            for (RecentBlocks.Pick pick : RecentBlocks.picks()) {
                if (pick.fluid() != null) {
                    addFluid(choices, pick.fluid());
                } else if (pick.item() instanceof BlockItem blockItem && BlockFilters.canStore(blockItem.getBlock().defaultBlockState())) {
                    choices.add(Choice.ofBlock(blockItem));
                }
            }
            return choices;
        }
        if (tab == Tab.INVENTORY) {
            Set<Item> seen = new HashSet<>();
            Set<Block> seenFluids = new HashSet<>();
            if (this.minecraft != null && this.minecraft.player != null) {
                var inventory = this.minecraft.player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    Block fluid = fluidForBucket(stack.getItem());
                    if (fluid != null && seenFluids.add(fluid)) {
                        addFluid(choices, fluid);
                    } else if (stack.getItem() instanceof BlockItem blockItem
                            && BlockFilters.canStore(blockItem.getBlock().defaultBlockState())
                            && seen.add(stack.getItem())) {
                        choices.add(Choice.ofBlock(blockItem));
                    }
                }
            }
            return choices;
        }
        for (Block block : sourceFluids()) {
            addFluid(choices, block);
        }
        for (Item item : allBlocks()) {
            if (item instanceof BlockItem blockItem) {
                choices.add(Choice.ofBlock(blockItem));
            }
        }
        return choices;
    }

    private static void addFluid(List<Choice> choices, Block block) {
        BlockState state = block.defaultBlockState();
        if (BlockFilters.isSourceFluid(state)) {
            choices.add(Choice.ofFluid(state));
        }
    }

    private static Block fluidForBucket(Item item) {
        for (Block block : sourceFluids()) {
            if (block.defaultBlockState().getFluidState().getType().getBucket() == item) {
                return block;
            }
        }
        return null;
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

    private static List<Block> sourceFluids() {
        if (sourceFluids != null) {
            return sourceFluids;
        }
        sourceFluids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (BlockFilters.isSourceFluid(block.defaultBlockState())) {
                sourceFluids.add(block);
            }
        }
        return sourceFluids;
    }

    private void choose(Choice choice) {
        BlockState state = Block.stateById(choice.stateId());
        if (BlockFilters.isSourceFluid(state)) {
            RecentBlocks.rememberFluid(state.getBlock());
        } else if (state.getBlock().asItem() instanceof BlockItem blockItem) {
            RecentBlocks.remember(blockItem);
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(id, cellX, cellY, cellZ, choice.stateId(), false));
        this.minecraft.setScreen(parent);
    }

    @Override
    public int jeiLeft() {
        return panelX;
    }

    @Override
    public int jeiTop() {
        return panelY;
    }

    @Override
    public int jeiWidth() {
        return panelW;
    }

    @Override
    public int jeiHeight() {
        return panelH;
    }

    @Override
    public int jeiScreenWidth() {
        return this.width;
    }

    @Override
    public int jeiScreenHeight() {
        return this.height;
    }

    private record Choice(ItemStack icon, BlockState fluid, int stateId) {
        private static Choice ofBlock(BlockItem item) {
            return new Choice(item.getDefaultInstance(), null, Block.getId(item.getBlock().defaultBlockState()));
        }

        private static Choice ofFluid(BlockState state) {
            return new Choice(ItemStack.EMPTY, state, Block.getId(state));
        }

        private Component name() {
            return fluid != null ? fluid.getBlock().getName() : icon.getHoverName();
        }
    }
}
