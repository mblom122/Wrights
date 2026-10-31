package com.wrights.compat.ae2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.WrightsMod;
import com.wrights.drone.CraftDeliveries;
import com.wrights.drone.WrightTier;
import com.wrights.network.BlueprintPayloads;
import com.wrights.item.ModItems;
import com.wrights.item.WrightItem;
import com.wrights.storage.DigitalAccess;
import com.wrights.storage.DigitalLink;
import com.wrights.storage.DigitalNetworks;

public final class Ae2DigitalAccess implements DigitalAccess {
    private static final ResourceLocation WAP = ResourceLocation.fromNamespaceAndPath("ae2", "wireless_access_point");
    private static final ResourceLocation CONTROLLER = ResourceLocation.fromNamespaceAndPath("ae2", "controller");
    /** How long a failed plan stays quiet so a job retry does not ask again every tick. */
    private static final int QUIET_TICKS = 100;
    private static final Map<CraftKey, PendingCraft> PENDING = new HashMap<>();
    private static final Map<CraftKey, Long> QUIET_UNTIL = new HashMap<>();
    private static final Map<CraftKey, Long> ACCEPTED_UNTIL = new HashMap<>();

    @Override
    public DigitalLink.Kind kind() {
        return DigitalLink.Kind.AE2;
    }

    @Override
    public boolean isBindTarget(Level level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (WAP.equals(id) || CONTROLLER.equals(id)) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof IWirelessAccessPoint;
    }

    @Override
    public boolean online(ServerLevel level, GlobalPos pos) {
        return grid(level, pos) != null;
    }

    @Override
    public long count(ServerLevel level, GlobalPos pos, Item item) {
        IGrid grid = grid(level, pos);
        if (grid == null) {
            return 0L;
        }
        long total = 0L;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEItemKey itemKey && itemKey.getItem() == item) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    @Override
    public long extract(ServerPlayer player, ServerLevel level, GlobalPos pos, Item item, long amount) {
        IGrid grid = grid(level, pos);
        if (grid == null || amount <= 0L) {
            return 0L;
        }
        IActionSource src = IActionSource.ofPlayer(player);
        MEStorage inventory = grid.getStorageService().getInventory();
        long left = amount;
        AEItemKey plain = AEItemKey.of(item);
        if (plain != null) {
            left -= inventory.extract(plain, left, Actionable.MODULATE, src);
        }
        if (left > 0L) {
            List<AEItemKey> matches = new ArrayList<>();
            for (var entry : grid.getStorageService().getCachedInventory()) {
                if (entry.getKey() instanceof AEItemKey itemKey && itemKey.getItem() == item) {
                    matches.add(itemKey);
                }
            }
            for (AEItemKey itemKey : matches) {
                left -= inventory.extract(itemKey, left, Actionable.MODULATE, src);
                if (left <= 0L) {
                    break;
                }
            }
        }
        return amount - Math.max(0L, left);
    }

    @Override
    public long insert(ServerPlayer player, ServerLevel level, GlobalPos pos, ItemStack stack) {
        IGrid grid = grid(level, pos);
        if (grid == null || stack.isEmpty()) {
            return 0L;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0L;
        }
        return Math.max(0L, grid.getStorageService().getInventory().insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.ofPlayer(player)));
    }

    @Override
    public boolean craftable(ServerLevel level, GlobalPos pos, Item item) {
        IGrid grid = grid(level, pos);
        if (grid == null) {
            return false;
        }
        ICraftingService crafting = grid.getCraftingService();
        AEItemKey plain = AEItemKey.of(item);
        if (plain != null && crafting.isCraftable(plain)) {
            return true;
        }
        if (item == ModItems.WRIGHT.get()) {
            for (int tier = WrightTier.BASE; tier <= WrightTier.NETHERITE; tier++) {
                AEItemKey key = AEItemKey.of(WrightItem.stack(tier, 1));
                if (key != null && crafting.isCraftable(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean requestCraft(ServerPlayer player, ServerLevel level, GlobalPos pos, Item item, long amount) {
        IGrid grid = grid(level, pos);
        if (grid == null || amount <= 0L) {
            return false;
        }
        AEItemKey key = craftKey(grid, item);
        if (key == null) {
            return false;
        }
        if (grid.getCraftingService().isRequesting(key)) {
            return true;
        }
        CraftKey craftKey = new CraftKey(player.getUUID(), pos, item);
        long now = level.getServer().getTickCount();
        Long quiet = QUIET_UNTIL.get(craftKey);
        if (quiet != null && now < quiet) {
            DigitalNetworks.keepCraftFailure();
            return false;
        }
        Long accepted = ACCEPTED_UNTIL.get(craftKey);
        if ((accepted != null && now < accepted) || PENDING.containsKey(craftKey)) {
            return true;
        }
        // Patterns are only visited when the request names a machine on the network.
        // A player source has no machine, so the plan used to ignore the pattern provider.
        // An empty source reads the stored stock instead of the listing the terminal is already walking.
        IGridNode node = actionNode(level, pos);
        if (node == null) {
            return false;
        }
        IActionSource src = IActionSource.ofPlayer(player);
        Future<ICraftingPlan> future = grid.getCraftingService().beginCraftingCalculation(
                level,
                new NetworkRequester(node),
                key,
                amount,
                CalculationStrategy.CRAFT_LESS);
        PENDING.put(craftKey, new PendingCraft(craftKey, future, src, amount));
        return true;
    }

    @Override
    public void tickCrafts(MinecraftServer server) {
        if (PENDING.isEmpty() && QUIET_UNTIL.isEmpty() && ACCEPTED_UNTIL.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        QUIET_UNTIL.values().removeIf(until -> now >= until);
        ACCEPTED_UNTIL.values().removeIf(until -> now >= until);
        Iterator<PendingCraft> pending = PENDING.values().iterator();
        List<PendingCraft> ready = new ArrayList<>();
        while (pending.hasNext()) {
            PendingCraft craft = pending.next();
            if (!craft.future.isDone()) {
                continue;
            }
            pending.remove();
            ready.add(craft);
        }
        for (PendingCraft craft : ready) {
            try {
                finish(server, craft);
            } catch (RuntimeException ex) {
                WrightsMod.LOGGER.warn("AE2 craft request failed", ex);
                ServerPlayer player = server.getPlayerList().getPlayer(craft.key.player);
                CraftDeliveries.cancel(craft.key.player);
                tell(server, player, craft, Component.translatable(
                        "message.wrights.digital_craft_failed_reason", Component.literal(shortReason(ex))));
            }
        }
    }

    /**
     * The plan is calculated off the server thread, and that work posts back onto the server
     * thread. Waiting for it here would lock the world, so this only runs once the future is done.
     */
    private static void finish(MinecraftServer server, PendingCraft craft) {
        ServerPlayer player = server.getPlayerList().getPlayer(craft.key.player);
        ICraftingPlan plan;
        try {
            plan = craft.future.get();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            WrightsMod.LOGGER.warn("AE2 craft plan failed", ex);
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            CraftDeliveries.cancel(craft.key.player);
            tell(server, player, craft, Component.translatable(
                    "message.wrights.digital_craft_failed_reason", Component.literal(shortReason(cause))));
            return;
        }
        if (player == null) {
            return;
        }
        if (plan == null) {
            CraftDeliveries.cancel(craft.key.player);
            tell(server, player, craft, Component.translatable("message.wrights.digital_craft_incomplete", new ItemStack(craft.key.item).getHoverName()));
            return;
        }
        ServerLevel level = server.getLevel(craft.key.pos.dimension());
        IGrid grid = level == null ? null : grid(level, craft.key.pos);
        if (grid == null) {
            CraftDeliveries.cancel(craft.key.player);
            tell(server, player, craft, "message.wrights.digital_offline");
            return;
        }
        // A simulated plan is AE2 giving up. It is not proof the drives are empty.
        boolean shortStock = ingredientShort(grid, plan, craft.key.item);
        boolean noCpu = !cpuFree(grid.getCraftingService(), plan);
        ICraftingSubmitResult result = null;
        if (!plan.simulation()) {
            result = grid.getCraftingService().submitJob(plan, null, null, false, craft.source);
            if (result.successful()) {
                ACCEPTED_UNTIL.put(craft.key, server.getTickCount() + 40L);
                long planned = plan.finalOutput() == null ? craft.amount : Math.max(0L, plan.finalOutput().amount());
                if (planned <= 0L) {
                    planned = craft.amount;
                }
                CraftDeliveries.accepted(player, craft.key.item, planned, craft.amount);
                return;
            }
            CraftingSubmitErrorCode code = result.errorCode();
            if (code == CraftingSubmitErrorCode.NO_CPU_FOUND
                    || code == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND
                    || code == CraftingSubmitErrorCode.CPU_BUSY
                    || code == CraftingSubmitErrorCode.CPU_OFFLINE
                    || code == CraftingSubmitErrorCode.CPU_TOO_SMALL) {
                noCpu = true;
            } else if (code == CraftingSubmitErrorCode.MISSING_INGREDIENT) {
                shortStock = true;
            }
        }
        Component name = new ItemStack(craft.key.item).getHoverName();
        Component reason = reason(plan, result, shortStock, noCpu || grid.getCraftingService().getCpus().isEmpty(), craft.key.item, name);
        WrightsMod.LOGGER.info(
                "AE2 craft of {} did not start (simulation={}, bytes={}, missing=[{}], error={}, detail={})",
                BuiltInRegistries.ITEM.getKey(craft.key.item),
                plan.simulation(),
                plan.bytes(),
                missingIds(plan),
                result == null || result.errorCode() == null ? "-" : result.errorCode(),
                result == null || result.errorDetail() == null ? "-" : result.errorDetail());
        CraftDeliveries.cancel(craft.key.player);
        tell(server, player, craft, reason);
    }

    /** A sentence the player can read, naming the thing AE2 actually refused. */
    private static Component reason(ICraftingPlan plan, ICraftingSubmitResult result, boolean shortStock, boolean noCpu, Item requested, Component name) {
        if (plan.simulation() && onlyOutput(plan.missingItems(), requested)) {
            return Component.translatable("message.wrights.digital_craft_no_pattern", name);
        }
        if (shortStock && noCpu) {
            return Component.translatable("message.wrights.digital_craft_blocked", name);
        }
        if (shortStock) {
            Component list = missingDetail(plan, result);
            if (list == null) {
                return Component.translatable("message.wrights.digital_craft_materials", name);
            }
            return Component.translatable("message.wrights.digital_craft_materials_list", name, list);
        }
        if (noCpu) {
            if (result != null && result.errorDetail() instanceof UnsuitableCpus cpus) {
                return Component.translatable("message.wrights.digital_craft_cpu_why", name, Component.translatable(
                        "message.wrights.digital_craft_cpu_bits", cpus.offline(), cpus.busy(), cpus.tooSmall(), cpus.excluded()));
            }
            if (result != null && result.errorCode() == CraftingSubmitErrorCode.CPU_TOO_SMALL) {
                return Component.translatable("message.wrights.digital_craft_cpu_small", name, plan.bytes());
            }
            if (result != null && result.errorCode() == CraftingSubmitErrorCode.CPU_OFFLINE) {
                return Component.translatable("message.wrights.digital_craft_cpu_offline");
            }
            if (result != null && result.errorCode() == CraftingSubmitErrorCode.CPU_BUSY) {
                return Component.translatable("message.wrights.digital_craft_cpu_busy");
            }
            return Component.translatable("message.wrights.digital_craft_cpu", name);
        }
        Component list = missingDetail(plan, result);
        if (list != null) {
            return Component.translatable("message.wrights.digital_craft_failed_reason", list);
        }
        if (result != null && result.errorCode() != null) {
            return Component.translatable("message.wrights.digital_craft_failed_reason", Component.literal(result.errorCode().name()));
        }
        return Component.translatable("message.wrights.digital_craft_incomplete", name);
    }

    private static Component missingDetail(ICraftingPlan plan, ICraftingSubmitResult result) {
        if (result != null && result.errorDetail() instanceof GenericStack missing && missing.what() != null) {
            return missing.what().getDisplayName().copy().append(" x" + missing.amount());
        }
        return missingList(plan);
    }

    private static Component missingList(ICraftingPlan plan) {
        KeyCounter missing = plan.missingItems();
        if (missing == null || missing.isEmpty()) {
            return null;
        }
        Component text = Component.empty();
        int shown = 0;
        for (var entry : missing) {
            long need = entry.getLongValue();
            if (need <= 0L) {
                continue;
            }
            if (shown == 4) {
                text = text.copy().append(", ...");
                break;
            }
            if (shown > 0) {
                text = text.copy().append(", ");
            }
            text = text.copy().append(entry.getKey().getDisplayName()).append(" x" + need);
            shown++;
        }
        return shown == 0 ? null : text;
    }

    /** True when the only thing AE2 could not get is the item we asked it to craft. */
    private static boolean onlyOutput(KeyCounter missing, Item requested) {
        if (missing == null || missing.isEmpty()) {
            return false;
        }
        boolean saw = false;
        for (var entry : missing) {
            if (entry.getLongValue() <= 0L) {
                continue;
            }
            saw = true;
            if (!(entry.getKey() instanceof AEItemKey itemKey) || itemKey.getItem() != requested) {
                return false;
            }
        }
        return saw;
    }

    private static String missingIds(ICraftingPlan plan) {
        KeyCounter missing = plan.missingItems();
        if (missing == null || missing.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (var entry : missing) {
            if (entry.getLongValue() <= 0L) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(entry.getKey().getId()).append(" x").append(entry.getLongValue());
        }
        return text.toString();
    }

    private static String shortReason(Throwable ex) {
        String why = ex.getMessage();
        if (why == null || why.isBlank()) {
            why = ex.getClass().getSimpleName();
        }
        return why.length() > 90 ? why.substring(0, 90) : why;
    }

    /** True when a non-output ingredient in the plan is actually absent from network storage. */
    private static boolean ingredientShort(IGrid grid, ICraftingPlan plan, Item requested) {
        KeyCounter missing = plan.missingItems();
        if (missing == null || missing.isEmpty()) {
            return false;
        }
        KeyCounter stored = grid.getStorageService().getCachedInventory();
        for (var entry : missing) {
            long need = entry.getLongValue();
            if (need <= 0L) {
                continue;
            }
            AEKey key = entry.getKey();
            if (key instanceof AEItemKey itemKey && itemKey.getItem() == requested) {
                continue;
            }
            if (available(stored, key) < need) {
                return true;
            }
        }
        return false;
    }

    private static long available(KeyCounter stored, AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return stored.get(key);
        }
        long total = 0L;
        for (var entry : stored) {
            if (entry.getKey() instanceof AEItemKey storedItem
                    && storedItem.getItem() == itemKey.getItem()
                    && entry.getLongValue() > 0L) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    private static void tell(MinecraftServer server, ServerPlayer player, PendingCraft craft, String key, Object... args) {
        tell(server, player, craft, Component.translatable(key, args));
    }

    private static void tell(MinecraftServer server, ServerPlayer player, PendingCraft craft, Component text) {
        QUIET_UNTIL.put(craft.key, server.getTickCount() + (long) QUIET_TICKS);
        if (player != null && text != null) {
            PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DigitalUse(List.of()));
            DigitalNetworks.explainCraftFailure(player, text);
        }
    }

    private static boolean cpuFree(ICraftingService crafting, ICraftingPlan plan) {
        for (ICraftingCPU cpu : crafting.getCpus()) {
            if (cpu.isBusy() || cpu.getAvailableStorage() < plan.bytes()) {
                continue;
            }
            if (cpu.getSelectionMode() == CpuSelectionMode.MACHINE_ONLY) {
                continue;
            }
            return true;
        }
        return false;
    }

    @Override
    public List<Integer> extractWrights(ServerPlayer player, ServerLevel level, GlobalPos pos, int count) {
        List<Integer> tiers = new ArrayList<>();
        IGrid grid = grid(level, pos);
        if (grid == null || count <= 0) {
            return tiers;
        }
        IActionSource src = IActionSource.ofPlayer(player);
        MEStorage inventory = grid.getStorageService().getInventory();
        while (tiers.size() < count) {
            AEItemKey found = null;
            for (var entry : grid.getStorageService().getCachedInventory()) {
                if (entry.getKey() instanceof AEItemKey itemKey
                        && itemKey.getItem() == ModItems.WRIGHT.get()
                        && entry.getLongValue() > 0L) {
                    found = itemKey;
                    break;
                }
            }
            if (found == null) {
                break;
            }
            if (inventory.extract(found, 1L, Actionable.MODULATE, src) <= 0L) {
                break;
            }
            tiers.add(WrightItem.tier(found.toStack()));
        }
        return tiers;
    }

    private static AEItemKey craftKey(IGrid grid, Item item) {
        ICraftingService crafting = grid.getCraftingService();
        AEItemKey plain = AEItemKey.of(item);
        if (plain != null && crafting.isCraftable(plain)) {
            return plain;
        }
        if (item == ModItems.WRIGHT.get()) {
            for (int tier = WrightTier.BASE; tier <= WrightTier.NETHERITE; tier++) {
                AEItemKey key = AEItemKey.of(WrightItem.stack(tier, 1));
                if (key != null && crafting.isCraftable(key)) {
                    return key;
                }
            }
        }
        return null;
    }

    private static IGrid grid(ServerLevel level, GlobalPos pos) {
        IGridNode node = actionNode(level, pos);
        return node == null ? null : node.getGrid();
    }

    /** The linked controller or access point. AE2 will not read patterns without this node. */
    private static IGridNode actionNode(ServerLevel level, GlobalPos pos) {
        if (pos == null) {
            return null;
        }
        ServerLevel stored = level.getServer().getLevel(pos.dimension());
        if (stored == null) {
            stored = level;
        }
        BlockEntity be = stored.getBlockEntity(pos.pos());
        if (be == null) {
            return null;
        }
        if (be instanceof IActionHost host) {
            IGridNode node = host.getActionableNode();
            if (node != null && node.getGrid() != null) {
                return node;
            }
        }
        if (be instanceof IInWorldGridNodeHost host) {
            for (Direction direction : Direction.values()) {
                IGridNode node = host.getGridNode(direction);
                if (node != null && node.getGrid() != null) {
                    return node;
                }
            }
        }
        return null;
    }

    private record CraftKey(UUID player, GlobalPos pos, Item item) {
    }

    private record PendingCraft(CraftKey key, Future<ICraftingPlan> future, IActionSource source, long amount) {
    }

    /**
     * Names the linked machine so pattern providers are visited, and carries no player so the
     * stock snapshot is the stored cache rather than a listing another screen is already walking.
     */
    private record NetworkRequester(IGridNode node) implements ICraftingSimulationRequester {
        @Override
        public IActionSource getActionSource() {
            return IActionSource.empty();
        }

        @Override
        public IGridNode getGridNode() {
            return node;
        }
    }
}
