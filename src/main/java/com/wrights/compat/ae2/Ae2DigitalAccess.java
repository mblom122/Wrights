package com.wrights.compat.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;

import com.wrights.drone.WrightTier;
import com.wrights.item.ModItems;
import com.wrights.item.WrightItem;
import com.wrights.storage.DigitalAccess;
import com.wrights.storage.DigitalLink;

public final class Ae2DigitalAccess implements DigitalAccess {
    private static final ResourceLocation WAP = ResourceLocation.fromNamespaceAndPath("ae2", "wireless_access_point");
    private static final ResourceLocation CONTROLLER = ResourceLocation.fromNamespaceAndPath("ae2", "controller");

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
        ICraftingService crafting = grid.getCraftingService();
        IActionSource src = IActionSource.ofPlayer(player);
        Future<ICraftingPlan> future = crafting.beginCraftingCalculation(
                level,
                () -> src,
                key,
                amount,
                CalculationStrategy.CRAFT_LESS);
        level.getServer().execute(() -> {
            try {
                ICraftingPlan plan = future.get();
                if (plan != null) {
                    crafting.submitJob(plan, null, null, false, src);
                }
            } catch (Exception ignored) {
            }
        });
        return true;
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
        if (be instanceof IWirelessAccessPoint wap) {
            return wap.getGrid();
        }
        if (be instanceof IInWorldGridNodeHost host) {
            for (Direction direction : Direction.values()) {
                IGridNode node = host.getGridNode(direction);
                if (node != null && node.getGrid() != null) {
                    return node.getGrid();
                }
            }
        }
        return null;
    }
}
