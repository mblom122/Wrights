package com.wrights.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
    private static final int TRAY_MIN_ROWS = 2;
    private static final int TRAY_MAX_ROWS = 4;
    private static List<Item> allBlocks;
    private static List<Block> sourceFluids;

    private final Screen parent;
    private final UUID id;
    private final int cellX;
    private final int cellY;
    private final int cellZ;
    private final Consumer<BlockState> pick;
    private final Consumer<List<BlockState>> pickMany;
    private final boolean multi;
    private final boolean blocksOnly;
    private final List<Choice> shown = new ArrayList<>();
    private final List<Choice> selected = new ArrayList<>();
    private EditBox search;
    private WrightsButton recentButton;
    private WrightsButton inventoryButton;
    private WrightsButton allButton;
    private WrightsButton addButton;
    private WrightsButton clearButton;
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
    private int trayX;
    private int trayY;
    private int scroll;
    private int dragIndex = -1;
    private int dragHover = -1;

    public BlockPickerScreen(Screen parent, UUID id, int cellX, int cellY, int cellZ) {
        this(parent, id, cellX, cellY, cellZ, null, null, false);
    }

    private BlockPickerScreen(Screen parent, UUID id, int cellX, int cellY, int cellZ, Consumer<BlockState> pick, Consumer<List<BlockState>> pickMany, boolean blocksOnly) {
        super(Component.translatable(blocksOnly ? "screen.wrights.picker_blocks" : "screen.wrights.picker"));
        this.parent = parent;
        this.id = id;
        this.cellX = cellX;
        this.cellY = cellY;
        this.cellZ = cellZ;
        this.pick = pick;
        this.pickMany = pickMany;
        this.multi = pickMany != null;
        this.blocksOnly = blocksOnly;
    }

    public static void open(Screen parent, UUID id, int x, int y, int z) {
        if (id == null) {
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new BlockPickerScreen(parent, id, x, y, z));
    }

    /** Shape materials picker: collect several blocks, then confirm. */
    public static void openBlocks(Screen parent, Consumer<List<BlockState>> onDone) {
        openBlocks(parent, List.of(), onDone);
    }

    /** Shape materials picker, prefilled with the blocks already chosen so reopening does not start over. */
    public static void openBlocks(Screen parent, List<BlockState> initial, Consumer<List<BlockState>> onDone) {
        if (onDone == null) {
            return;
        }
        BlockPickerScreen screen = new BlockPickerScreen(parent, null, 0, 0, 0, null, onDone, true);
        screen.seedSelected(initial);
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    private void seedSelected(List<BlockState> initial) {
        selected.clear();
        if (initial == null) {
            return;
        }
        for (BlockState state : initial) {
            if (state == null || state.isAir()) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (!(item instanceof BlockItem blockItem)) {
                continue;
            }
            Choice choice = Choice.ofBlock(blockItem);
            if (indexInSelected(choice) >= 0) {
                continue;
            }
            if (selected.size() >= TRAY_MAX_ROWS * COLS) {
                break;
            }
            selected.add(choice);
        }
    }

    /** Screen that opened this picker — library edit does not need the controller held. */
    public Screen parent() {
        return parent;
    }

    @Override
    protected void init() {
        panelW = WrightsUi.fitW(220, this.width);
        panelH = WrightsUi.fitH(multi ? 300 : 248, this.height);
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
        trayX = listX;
        listY = tabY + 22;
        layoutLists();
        if (multi) {
            int gap = 4;
            int third = (panelW - 24 - gap * 2) / 3;
            int bx = panelX + 12;
            addRenderableWidget(new WrightsButton(bx, panelY + panelH - 28, third, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
            bx += third + gap;
            clearButton = new WrightsButton(bx, panelY + panelH - 28, third, 16, Component.translatable("screen.wrights.picker_clear"), button -> clearSelected(), true);
            addRenderableWidget(clearButton);
            bx += third + gap;
            addButton = new WrightsButton(bx, panelY + panelH - 28, panelX + panelW - 12 - bx, 16, Component.translatable("screen.wrights.picker_add"), button -> confirmSelected());
            addRenderableWidget(addButton);
            markAdd();
        } else {
            addRenderableWidget(new WrightsButton(panelX + 12, panelY + panelH - 28, panelW - 24, 16, Component.translatable("menu.wrights.close"), button -> onClose()));
        }
        markTabs();
        rebuild(true);
    }

    private void layoutLists() {
        int footer = 36;
        if (multi) {
            int trayRows = trayRows();
            int trayBlock = 16 + trayRows * CELL + 6;
            trayY = panelY + panelH - footer - trayBlock;
            listH = Math.max(CELL + 4, trayY - listY - 8);
        } else {
            trayY = panelY + panelH - footer;
            listH = panelH - 108;
        }
    }

    private int trayRows() {
        int need = Math.max(TRAY_MIN_ROWS, (selected.size() + COLS) / COLS);
        return Math.min(TRAY_MAX_ROWS, need);
    }

    private int traySlots() {
        return trayRows() * COLS;
    }

    private void markAdd() {
        if (addButton != null) {
            addButton.active = !selected.isEmpty();
        }
        if (clearButton != null) {
            clearButton.active = !selected.isEmpty();
        }
    }

    private void clearSelected() {
        selected.clear();
        dragIndex = -1;
        dragHover = -1;
        markAdd();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WrightsUi.scrim(graphics, this.width, this.height);
        WrightsUi.panel(graphics, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layoutLists();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, panelX + 12, panelY + 10, WrightsUi.COPPER, false);
        rebuild(false);
        int rows = Math.max(1, (listH - 4) / CELL);
        int maxScroll = Math.max(0, (shown.size() + COLS - 1) / COLS - rows);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        Choice hovered = null;
        if (shown.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.wrights.picker_empty"), panelX + panelW / 2, listY + 8, WrightsUi.MUTED);
        } else {
            int start = scroll * COLS;
            for (int i = 0; i < rows * COLS && start + i < shown.size(); i++) {
                int column = i % COLS;
                int row = i / COLS;
                int x = listX + column * CELL;
                int y = listY + row * CELL;
                Choice choice = shown.get(start + i);
                boolean inTray = indexInSelected(choice) >= 0;
                boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                if (inTray) {
                    graphics.fill(x, y, x + CELL, y + CELL, 0x4431A06A);
                }
                if (over) {
                    graphics.fill(x, y, x + CELL, y + CELL, 0x66F2D48A);
                    hovered = choice;
                }
                drawChoice(graphics, choice, x, y);
            }
        }
        if (multi) {
            hovered = renderTray(graphics, mouseX, mouseY, hovered);
        }
        if (hovered != null && dragIndex < 0) {
            if (hovered.fluid() != null) {
                graphics.renderTooltip(this.font, hovered.name(), (int) mouseX, (int) mouseY);
            } else if (!hovered.icon().isEmpty()) {
                graphics.renderTooltip(this.font, hovered.icon(), (int) mouseX, (int) mouseY);
            }
        }
        if (dragIndex >= 0 && dragIndex < selected.size()) {
            Choice dragging = selected.get(dragIndex);
            drawChoice(graphics, dragging, (int) mouseX - CELL / 2, (int) mouseY - CELL / 2);
        }
    }

    private Choice renderTray(GuiGraphics graphics, int mouseX, int mouseY, Choice hovered) {
        graphics.drawString(this.font, Component.translatable("screen.wrights.picker_selected"), trayX, trayY, WrightsUi.MUTED, false);
        int gridY = trayY + 12;
        int slots = traySlots();
        for (int i = 0; i < slots; i++) {
            int column = i % COLS;
            int row = i / COLS;
            int x = trayX + column * CELL;
            int y = gridY + row * CELL;
            boolean drop = dragIndex >= 0 && dragHover == i;
            boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            WrightsUi.slot(graphics, x, y, drop ? WrightsUi.COPPER : over ? 0xFF4A5C6C : 0xFF31404E);
            if (i < selected.size() && i != dragIndex) {
                Choice choice = selected.get(i);
                drawChoice(graphics, choice, x, y);
                if (over && dragIndex < 0) {
                    hovered = choice;
                }
            }
        }
        return hovered;
    }

    private static void drawChoice(GuiGraphics graphics, Choice choice, int x, int y) {
        if (choice.fluid() != null) {
            FluidIcons.draw(graphics, choice.fluid(), x + 1, y + 1, 16);
        } else if (!choice.icon().isEmpty()) {
            graphics.renderItem(choice.icon(), x + 1, y + 1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (multi && button == 1) {
            int trayIndex = trayIndexAt(mouseX, mouseY);
            if (trayIndex >= 0 && trayIndex < selected.size()) {
                selected.remove(trayIndex);
                markAdd();
                return true;
            }
        }
        if (button == 0) {
            if (multi) {
                int trayIndex = trayIndexAt(mouseX, mouseY);
                if (trayIndex >= 0 && trayIndex < selected.size()) {
                    dragIndex = trayIndex;
                    dragHover = trayIndex;
                    return true;
                }
            }
            if (!shown.isEmpty()) {
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (multi && button == 0 && dragIndex >= 0) {
            int hover = trayIndexAt(mouseX, mouseY);
            dragHover = hover >= 0 ? Math.min(hover, Math.max(0, selected.size() - 1)) : -1;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (multi && button == 0 && dragIndex >= 0) {
            int from = dragIndex;
            int to = dragHover;
            dragIndex = -1;
            dragHover = -1;
            if (to >= 0 && to < selected.size() && to != from) {
                Choice moved = selected.remove(from);
                selected.add(to, moved);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int trayIndexAt(double mouseX, double mouseY) {
        int gridY = trayY + 12;
        int column = (int) ((mouseX - trayX) / CELL);
        int row = (int) ((mouseY - gridY) / CELL);
        int rows = trayRows();
        if (column < 0 || column >= COLS || row < 0 || row >= rows) {
            return -1;
        }
        return row * COLS + column;
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
            if (blocksOnly && choice.fluid() != null) {
                continue;
            }
            if (!query.isEmpty() && !choice.name().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            shown.add(choice);
        }
    }

    private List<Choice> source() {
        List<Choice> choices = new ArrayList<>();
        if (tab == Tab.RECENT) {
            for (RecentBlocks.Pick recent : RecentBlocks.picks()) {
                if (!blocksOnly && recent.fluid() != null) {
                    addFluid(choices, recent.fluid());
                } else if (recent.item() instanceof BlockItem blockItem && BlockFilters.canStore(blockItem.getBlock().defaultBlockState())) {
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
                    Block fluid = blocksOnly ? null : fluidForBucket(stack.getItem());
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
        if (!blocksOnly) {
            for (Block block : sourceFluids()) {
                addFluid(choices, block);
            }
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
        if (multi) {
            int existing = indexInSelected(choice);
            if (existing >= 0) {
                selected.remove(existing);
            } else if (selected.size() < TRAY_MAX_ROWS * COLS) {
                selected.add(choice);
                remember(choice);
            }
            markAdd();
            return;
        }
        remember(choice);
        BlockState state = Block.stateById(choice.stateId());
        if (pick != null) {
            pick.accept(state);
            this.minecraft.setScreen(parent);
            return;
        }
        if (id == null) {
            this.minecraft.setScreen(parent);
            return;
        }
        PacketDistributor.sendToServer(new BlueprintPayloads.EditCell(id, cellX, cellY, cellZ, choice.stateId(), false));
        this.minecraft.setScreen(parent);
    }

    private void remember(Choice choice) {
        BlockState state = Block.stateById(choice.stateId());
        if (BlockFilters.isSourceFluid(state)) {
            if (!blocksOnly) {
                RecentBlocks.rememberFluid(state.getBlock());
            }
        } else if (state.getBlock().asItem() instanceof BlockItem blockItem) {
            RecentBlocks.remember(blockItem);
        }
    }

    private int indexInSelected(Choice choice) {
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i).stateId() == choice.stateId()) {
                return i;
            }
        }
        return -1;
    }

    private void confirmSelected() {
        if (pickMany == null || selected.isEmpty()) {
            return;
        }
        List<BlockState> states = new ArrayList<>(selected.size());
        for (Choice choice : selected) {
            remember(choice);
            BlockState state = Block.stateById(choice.stateId());
            if (state != null && !state.isAir()) {
                states.add(state);
            }
        }
        pickMany.accept(states);
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
