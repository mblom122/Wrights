package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.item.ControllerItem;
import com.wrights.storage.ItemSource;

/**
 * Shared ordered material sources for Shape, blueprint place, and Options.
 * Default order is main hand → offhand → action bar → inventory → bound chest → digital storage.
 */
public final class ShapeSupply {
    public static final int MAIN_HAND = 0;
    public static final int OFFHAND = 1;
    public static final int ACTION_BAR = 2;
    public static final int INVENTORY = 3;
    public static final int BOUND_CHEST = 4;
    public static final int DIGITAL = 5;
    public static final int COUNT = 6;

    public static final String DEFAULT_ORDER = "0,1,2,3,4,5";

    private ShapeSupply() {
    }

    public static int clamp(int id) {
        return Math.max(0, Math.min(COUNT - 1, id));
    }

    public static List<Integer> parse(String raw) {
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split("[,;\\s]+")) {
                if (part.isBlank()) {
                    continue;
                }
                try {
                    int id = clamp(Integer.parseInt(part.trim()));
                    seen.add(id);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (seen.isEmpty()) {
            for (int i = 0; i < COUNT; i++) {
                seen.add(i);
            }
        }
        return new ArrayList<>(seen);
    }

    public static String encode(List<Integer> order) {
        if (order == null || order.isEmpty()) {
            return DEFAULT_ORDER;
        }
        StringBuilder out = new StringBuilder();
        Set<Integer> seen = new LinkedHashSet<>();
        for (int id : order) {
            int next = clamp(id);
            if (!seen.add(next)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(next);
        }
        return out.length() == 0 ? DEFAULT_ORDER : out.toString();
    }

    public static List<Integer> missing(List<Integer> order) {
        boolean[] used = new boolean[COUNT];
        for (int id : order) {
            used[clamp(id)] = true;
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            if (!used[i]) {
                out.add(i);
            }
        }
        return out;
    }

    public static String labelKey(int id) {
        return switch (clamp(id)) {
            case OFFHAND -> "menu.wrights.shape_src_offhand";
            case ACTION_BAR -> "menu.wrights.shape_src_actionbar";
            case INVENTORY -> "menu.wrights.shape_src_inventory";
            case BOUND_CHEST -> "menu.wrights.shape_src_chest";
            case DIGITAL -> "menu.wrights.shape_src_digital";
            default -> "menu.wrights.shape_src_main";
        };
    }

    /** Compact names for the shape menu summary line. */
    public static String shortLabelKey(int id) {
        return switch (clamp(id)) {
            case OFFHAND -> "menu.wrights.shape_src_offhand_short";
            case ACTION_BAR -> "menu.wrights.shape_src_actionbar_short";
            case INVENTORY -> "menu.wrights.shape_src_inventory_short";
            case BOUND_CHEST -> "menu.wrights.shape_src_chest_short";
            case DIGITAL -> "menu.wrights.shape_src_digital_short";
            default -> "menu.wrights.shape_src_main_short";
        };
    }

    /** Block states that Shape mode may place, following the priority list. */
    public static List<BlockState> palette(Player player, List<Integer> order, boolean randomizeHotbar) {
        List<BlockState> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        List<Integer> sources = order == null || order.isEmpty() ? parse(DEFAULT_ORDER) : order;
        if (randomizeHotbar && sources.contains(ACTION_BAR)) {
            collectHotbar(player, out);
            if (!out.isEmpty()) {
                return out;
            }
        }
        for (int source : sources) {
            collect(player, source, out, 1);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    public static BlockState pick(Player player, List<Integer> order, boolean randomizeHotbar, Random random) {
        List<BlockState> palette = palette(player, order, randomizeHotbar);
        if (palette.isEmpty()) {
            return null;
        }
        if (palette.size() == 1 || random == null) {
            return palette.get(0);
        }
        return palette.get(random.nextInt(palette.size()));
    }

    public static boolean canAfford(ServerPlayer player, Item item, int count, List<Integer> order) {
        if (player == null || item == null || count <= 0) {
            return count <= 0;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        return available(player, item, order) >= count;
    }

    public static long available(ServerPlayer player, Item item, List<Integer> order) {
        return available(player, item, order, null);
    }

    /** True when any player-hand / inventory source appears before the bound chest in {@code order}. */
    public static boolean playerSourcesFirst(List<Integer> order) {
        for (int source : order == null ? List.<Integer>of() : order) {
            int id = clamp(source);
            if (id == BOUND_CHEST) {
                return false;
            }
            if (id == MAIN_HAND || id == OFFHAND || id == ACTION_BAR || id == INVENTORY) {
                return true;
            }
        }
        return true;
    }

    public static long available(ServerPlayer player, Item item, List<Integer> order, net.minecraft.world.Container chest) {
        if (player == null || item == null) {
            return 0L;
        }
        if (player.getAbilities().instabuild) {
            return Long.MAX_VALUE / 4L;
        }
        long total = 0L;
        boolean countedPlayer = false;
        for (int source : order == null ? parse(DEFAULT_ORDER) : order) {
            switch (clamp(source)) {
                case MAIN_HAND, OFFHAND, ACTION_BAR, INVENTORY -> {
                    if (!countedPlayer) {
                        total += ItemSource.playerCount(player, item);
                        countedPlayer = true;
                    }
                }
                case BOUND_CHEST -> {
                    if (chest != null) {
                        total += countContainer(chest, item);
                    } else {
                        total += ItemSource.chestCount(player, item);
                    }
                }
                case DIGITAL -> total += ItemSource.digitalCount(player, item);
                default -> {
                }
            }
        }
        return total;
    }

    /**
     * Takes one item using the shared priority list. Player slots are searched in order;
     * chest and digital follow when listed.
     */
    public static boolean takeOne(ServerPlayer player, Item item, List<Integer> order) {
        return takeOne(player, item, order, null);
    }

    /** Like {@link #takeOne(ServerPlayer, Item, List)} but uses {@code chest} when bound-chest is listed. */
    public static boolean takeOne(ServerPlayer player, Item item, List<Integer> order, net.minecraft.world.Container chest) {
        if (player == null || item == null) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        Inventory inv = player.getInventory();
        for (int source : order == null ? parse(DEFAULT_ORDER) : order) {
            switch (clamp(source)) {
                case MAIN_HAND -> {
                    if (shrinkSlot(inv, inv.selected, item)) {
                        return true;
                    }
                }
                case OFFHAND -> {
                    if (shrinkSlot(inv, 40, item)) {
                        return true;
                    }
                }
                case ACTION_BAR -> {
                    for (int slot = 0; slot < 9; slot++) {
                        if (shrinkSlot(inv, slot, item)) {
                            return true;
                        }
                    }
                }
                case INVENTORY -> {
                    for (int slot = 9; slot < inv.items.size(); slot++) {
                        if (shrinkSlot(inv, slot, item)) {
                            return true;
                        }
                    }
                    // Also allow remaining player slots if earlier sources were removed.
                    for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                        if (slot == Inventory.SLOT_OFFHAND) {
                            continue;
                        }
                        if (shrinkSlot(inv, slot, item)) {
                            return true;
                        }
                    }
                }
                case BOUND_CHEST -> {
                    if (takeChestOnly(player, item, chest)) {
                        return true;
                    }
                }
                case DIGITAL -> {
                    if (ItemSource.digitalCount(player, item) > 0L) {
                        var link = ItemSource.linkOf(player);
                        var level = com.wrights.storage.DigitalNetworks.levelOf(player, link == null ? null : link.pos());
                        if (level != null && com.wrights.storage.DigitalNetworks.extract(player, level, link, item, 1L) > 0L) {
                            return true;
                        }
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static boolean takeChestOnly(ServerPlayer player, Item item, net.minecraft.world.Container chest) {
        var container = chest != null ? chest : ItemSource.container(player, ItemSource.chestOf(player));
        if (container == null) {
            return false;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            container.setChanged();
            return true;
        }
        return false;
    }

    private static long countContainer(net.minecraft.world.Container container, Item item) {
        long total = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean shrinkSlot(Inventory inv, int slot, Item item) {
        if (slot < 0 || slot >= inv.getContainerSize()) {
            return false;
        }
        ItemStack stack = inv.getItem(slot);
        if (!stack.is(item) || stack.getItem() instanceof ControllerItem) {
            return false;
        }
        stack.shrink(1);
        return true;
    }

    private static void collect(Player player, int source, List<BlockState> out, int limit) {
        Inventory inv = player.getInventory();
        switch (clamp(source)) {
            case MAIN_HAND -> addBlock(inv.getSelected(), out, limit);
            case OFFHAND -> addBlock(inv.offhand.get(0), out, limit);
            case ACTION_BAR -> collectHotbar(player, out);
            case INVENTORY -> {
                for (int slot = 9; slot < inv.items.size() && out.size() < limit; slot++) {
                    addBlock(inv.items.get(slot), out, limit);
                }
            }
            case BOUND_CHEST, DIGITAL -> {
                // These supply matching items for a chosen block; they do not invent a block type.
            }
            default -> {
            }
        }
    }

    private static void collectHotbar(Player player, List<BlockState> out) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            addBlock(inv.getItem(slot), out, Integer.MAX_VALUE);
        }
    }

    private static void addBlock(ItemStack stack, List<BlockState> out, int limit) {
        if (out.size() >= limit || stack == null || stack.isEmpty()) {
            return;
        }
        if (!(stack.getItem() instanceof BlockItem block) || stack.getItem() instanceof ControllerItem) {
            return;
        }
        BlockState state = block.getBlock().defaultBlockState();
        if (state.isAir()) {
            return;
        }
        for (BlockState existing : out) {
            if (existing.getBlock() == state.getBlock()) {
                return;
            }
        }
        out.add(state);
    }

    public static String debugOrder(List<Integer> order) {
        StringBuilder out = new StringBuilder();
        for (int id : order) {
            if (out.length() > 0) {
                out.append('>');
            }
            out.append(labelKey(id).replace("menu.wrights.shape_src_", "").toUpperCase(Locale.ROOT));
        }
        return out.toString();
    }
}
