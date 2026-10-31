package com.wrights.compat.rs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternRepository;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CraftingCalculatorImpl;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskPlanCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.autocrafting.PatternProvider;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemTargetBlockEntity;
import com.refinedmods.refinedstorage.common.controller.ControllerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wrights.drone.WrightTier;
import com.wrights.item.ModItems;
import com.wrights.item.WrightItem;
import com.wrights.storage.DigitalAccess;
import com.wrights.storage.DigitalLink;
import com.wrights.storage.DigitalNetworks;

public final class RsDigitalAccess implements DigitalAccess {
    private static final ResourceLocation CONTROLLER = ResourceLocation.fromNamespaceAndPath("refinedstorage", "controller");
    private static final ResourceLocation CREATIVE = ResourceLocation.fromNamespaceAndPath("refinedstorage", "creative_controller");
    private static final int QUIET_TICKS = 100;
    private static final Map<CraftKey, Long> QUIET_UNTIL = new HashMap<>();

    @Override
    public DigitalLink.Kind kind() {
        return DigitalLink.Kind.RS;
    }

    @Override
    public boolean isBindTarget(Level level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (CONTROLLER.equals(id) || CREATIVE.equals(id)) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof ControllerBlockEntity;
    }

    @Override
    public boolean online(ServerLevel level, GlobalPos pos) {
        return network(level, pos) != null;
    }

    @Override
    public long count(ServerLevel level, GlobalPos pos, Item item) {
        Network network = network(level, pos);
        if (network == null) {
            return 0L;
        }
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        long total = 0L;
        for (ResourceAmount amount : storage.getAll()) {
            if (amount.resource() instanceof ItemResource resource && resource.item() == item) {
                total += amount.amount();
            }
        }
        return total;
    }

    @Override
    public long extract(ServerPlayer player, ServerLevel level, GlobalPos pos, Item item, long amount) {
        Network network = network(level, pos);
        if (network == null || amount <= 0L) {
            return 0L;
        }
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        Actor actor = new PlayerActor(player);
        long left = amount;
        left -= storage.extract(new ItemResource(item), left, Action.EXECUTE, actor);
        if (left > 0L) {
            for (ResourceAmount entry : List.copyOf(storage.getAll())) {
                if (!(entry.resource() instanceof ItemResource resource) || resource.item() != item) {
                    continue;
                }
                left -= storage.extract(resource, left, Action.EXECUTE, actor);
                if (left <= 0L) {
                    break;
                }
            }
        }
        return amount - Math.max(0L, left);
    }

    @Override
    public long insert(ServerPlayer player, ServerLevel level, GlobalPos pos, ItemStack stack) {
        Network network = network(level, pos);
        if (network == null || stack.isEmpty()) {
            return 0L;
        }
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        ItemResource resource = ItemResource.ofItemStack(stack);
        Actor actor = new PlayerActor(player);
        long before = stored(storage, resource);
        storage.insert(resource, stack.getCount(), Action.EXECUTE, actor);
        return Math.max(0L, stored(storage, resource) - before);
    }

    private static long stored(StorageNetworkComponent storage, ItemResource resource) {
        long total = 0L;
        for (ResourceAmount entry : storage.getAll()) {
            if (resource.equals(entry.resource())) {
                total += entry.amount();
            }
        }
        return total;
    }

    @Override
    public boolean craftable(ServerLevel level, GlobalPos pos, Item item) {
        Network network = network(level, pos);
        if (network == null) {
            return false;
        }
        AutocraftingNetworkComponent crafting = network.getComponent(AutocraftingNetworkComponent.class);
        if (matchesCraft(crafting, new ItemResource(item))) {
            return true;
        }
        if (item == ModItems.WRIGHT.get()) {
            for (int tier = WrightTier.BASE; tier <= WrightTier.NETHERITE; tier++) {
                if (matchesCraft(crafting, ItemResource.ofItemStack(WrightItem.stack(tier, 1)))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean requestCraft(ServerPlayer player, ServerLevel level, GlobalPos pos, Item item, long amount) {
        Network network = network(level, pos);
        if (network == null || amount <= 0L) {
            return false;
        }
        AutocraftingNetworkComponent crafting = network.getComponent(AutocraftingNetworkComponent.class);
        ResourceKey key = craftKey(crafting, item);
        if (key == null) {
            return false;
        }
        CraftKey craftKey = new CraftKey(player.getUUID(), pos, item);
        long now = level.getServer().getTickCount();
        QUIET_UNTIL.values().removeIf(until -> now >= until);
        Long quiet = QUIET_UNTIL.get(craftKey);
        if (quiet != null && now < quiet) {
            DigitalNetworks.keepCraftFailure();
            return false;
        }
        if (quiet != null) {
            QUIET_UNTIL.remove(craftKey);
        }
        ItemStack named = new ItemStack(item);
        boolean crafter = crafterActive(crafting, key);
        if (!crafter) {
            boolean missing = !canPlan(network, crafting, key, amount);
            quiet(level, craftKey);
            if (missing) {
                DigitalNetworks.explainCraftFailure(player, "message.wrights.digital_craft_blocked_crafter", named.getHoverName());
            } else {
                DigitalNetworks.explainCraftFailure(player, "message.wrights.digital_craft_crafter", named.getHoverName());
            }
            return false;
        }
        Actor actor = new PlayerActor(player);
        AutocraftingNetworkComponent.EnsureResult result;
        try {
            result = crafting.ensureTask(key, amount, actor, CancellationToken.NONE);
        } catch (RuntimeException ex) {
            quiet(level, craftKey);
            DigitalNetworks.explainCraftFailure(player, "message.wrights.digital_craft_failed");
            return false;
        }
        if (result == AutocraftingNetworkComponent.EnsureResult.TASK_CREATED
                || result == AutocraftingNetworkComponent.EnsureResult.TASK_ALREADY_RUNNING) {
            com.wrights.drone.CraftDeliveries.accepted(player, item, amount, amount);
            return true;
        }
        quiet(level, craftKey);
        com.wrights.drone.CraftDeliveries.cancel(player.getUUID());
        if (result == AutocraftingNetworkComponent.EnsureResult.MISSING_RESOURCES) {
            DigitalNetworks.explainCraftFailure(player, "message.wrights.digital_craft_materials", named.getHoverName());
        } else {
            DigitalNetworks.explainCraftFailure(player, "message.wrights.digital_craft_failed");
        }
        return false;
    }

    /** An autocrafter holds the pattern. It can take the job only while that block is active. */
    private static boolean crafterActive(AutocraftingNetworkComponent crafting, ResourceKey key) {
        List<Pattern> patterns = crafting.getPatternsByOutput(key);
        if (patterns.isEmpty()) {
            return false;
        }
        for (Pattern pattern : patterns) {
            PatternProvider provider = crafting.getProviderByPattern(pattern);
            if (provider instanceof PatternProviderNetworkNode node) {
                if (node.isActive()) {
                    return true;
                }
            } else if (provider != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plans on this thread and does not start a task. An empty plan means the network
     * cannot supply the ingredients.
     */
    private static boolean canPlan(Network network, AutocraftingNetworkComponent crafting, ResourceKey key, long amount) {
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        CraftingCalculatorImpl calculator = new CraftingCalculatorImpl(new PatternView(crafting), storage);
        try {
            return TaskPlanCraftingCalculatorListener.calculatePlan(calculator, key, amount, CancellationToken.NONE).isPresent();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static final class PatternView implements PatternRepository {
        private final AutocraftingNetworkComponent crafting;

        private PatternView(AutocraftingNetworkComponent crafting) {
            this.crafting = crafting;
        }

        @Override
        public void add(Pattern pattern, int priority) {
        }

        @Override
        public void update(Pattern pattern, int priority) {
        }

        @Override
        public void remove(Pattern pattern) {
        }

        @Override
        public Collection<Pattern> getByOutput(ResourceKey resource) {
            return crafting.getPatternsByOutput(resource);
        }

        @Override
        public Set<ResourceKey> getOutputs() {
            return crafting.getOutputs();
        }

        @Override
        public Set<Pattern> getAll() {
            return crafting.getPatterns();
        }
    }

    private static void quiet(ServerLevel level, CraftKey key) {
        QUIET_UNTIL.put(key, level.getServer().getTickCount() + (long) QUIET_TICKS);
    }

    private record CraftKey(UUID player, GlobalPos pos, Item item) {
    }

    @Override
    public List<Integer> extractWrights(ServerPlayer player, ServerLevel level, GlobalPos pos, int count) {
        List<Integer> tiers = new ArrayList<>();
        Network network = network(level, pos);
        if (network == null || count <= 0) {
            return tiers;
        }
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        Actor actor = new PlayerActor(player);
        while (tiers.size() < count) {
            ItemResource found = null;
            for (ResourceAmount entry : storage.getAll()) {
                if (entry.resource() instanceof ItemResource resource
                        && resource.item() == ModItems.WRIGHT.get()
                        && entry.amount() > 0L) {
                    found = resource;
                    break;
                }
            }
            if (found == null) {
                break;
            }
            if (storage.extract(found, 1L, Action.EXECUTE, actor) <= 0L) {
                break;
            }
            ItemStack stack = found.toItemStack();
            tiers.add(WrightItem.tier(stack));
        }
        return tiers;
    }

    private static boolean matchesCraft(AutocraftingNetworkComponent crafting, ResourceKey resource) {
        return !crafting.getPatternsByOutput(resource).isEmpty() || crafting.getOutputs().contains(resource);
    }

    private static ResourceKey craftKey(AutocraftingNetworkComponent crafting, Item item) {
        ItemResource plain = new ItemResource(item);
        if (matchesCraft(crafting, plain)) {
            return plain;
        }
        if (item == ModItems.WRIGHT.get()) {
            for (int tier = WrightTier.BASE; tier <= WrightTier.NETHERITE; tier++) {
                ItemResource keyed = ItemResource.ofItemStack(WrightItem.stack(tier, 1));
                if (matchesCraft(crafting, keyed)) {
                    return keyed;
                }
            }
        }
        return null;
    }

    private static Network network(ServerLevel level, GlobalPos pos) {
        if (pos == null) {
            return null;
        }
        ServerLevel stored = level.getServer().getLevel(pos.dimension());
        if (stored == null) {
            stored = level;
        }
        BlockEntity be = stored.getBlockEntity(pos.pos());
        if (be instanceof NetworkItemTargetBlockEntity target) {
            return target.getNetworkForItem();
        }
        if (be instanceof ControllerBlockEntity controller) {
            return controller.getNetworkForItem();
        }
        return null;
    }
}
