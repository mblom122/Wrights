package com.wrights.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import com.wrights.util.Messages;

import com.wrights.drone.WrightTier;

/**
 * Soft gate for AE2 / Refined Storage. Core code only talks to this class; adapters are
 * constructed reflectively so a missing mod never classloads.
 */
public final class DigitalNetworks {
    public static final String AE2_ID = "ae2";
    public static final String RS_ID = "refinedstorage";

    private static final List<DigitalAccess> ACCESS = new ArrayList<>();
    private static final ThreadLocal<Boolean> CRAFT_FAILURE = ThreadLocal.withInitial(() -> false);
    private static boolean booted;

    private DigitalNetworks() {
    }

    private static void boot() {
        if (booted) {
            return;
        }
        booted = true;
        if (ModList.get().isLoaded(AE2_ID)) {
            load("com.wrights.compat.ae2.Ae2DigitalAccess");
        }
        if (ModList.get().isLoaded(RS_ID)) {
            load("com.wrights.compat.rs.RsDigitalAccess");
        }
    }

    private static void load(String className) {
        try {
            Object instance = Class.forName(className).getConstructor().newInstance();
            if (instance instanceof DigitalAccess access) {
                ACCESS.add(access);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional mod present but API mismatch — leave that backend offline.
        }
    }

    public static boolean ae2Loaded() {
        return ModList.get().isLoaded(AE2_ID);
    }

    public static boolean rsLoaded() {
        return ModList.get().isLoaded(RS_ID);
    }

    public static DigitalLink.Kind bindKindAt(Level level, BlockPos pos) {
        boot();
        for (DigitalAccess access : ACCESS) {
            if (access.isBindTarget(level, pos)) {
                return access.kind();
            }
        }
        return DigitalLink.Kind.NONE;
    }

    public static DigitalAccess access(DigitalLink.Kind kind) {
        boot();
        for (DigitalAccess access : ACCESS) {
            if (access.kind() == kind) {
                return access;
            }
        }
        return null;
    }

    public static boolean online(ServerLevel level, DigitalLink link) {
        if (link == null || !link.bound()) {
            return false;
        }
        DigitalAccess access = access(link.kind());
        return access != null && access.online(level, link.pos());
    }

    public static long count(ServerLevel level, DigitalLink link, Item item) {
        if (link == null || !link.bound()) {
            return 0L;
        }
        DigitalAccess access = access(link.kind());
        if (access == null || !access.online(level, link.pos())) {
            return 0L;
        }
        return Math.max(0L, access.count(level, link.pos(), item));
    }

    public static long insert(ServerPlayer player, ServerLevel level, DigitalLink link, net.minecraft.world.item.ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || link == null || !link.bound()) {
            return 0L;
        }
        DigitalAccess access = access(link.kind());
        if (access == null || !access.online(level, link.pos())) {
            return 0L;
        }
        return Math.max(0L, access.insert(player, level, link.pos(), stack));
    }

    public static long extract(ServerPlayer player, ServerLevel level, DigitalLink link, Item item, long amount) {
        if (amount <= 0L || link == null || !link.bound()) {
            return 0L;
        }
        DigitalAccess access = access(link.kind());
        if (access == null || !access.online(level, link.pos())) {
            return 0L;
        }
        return Math.max(0L, access.extract(player, level, link.pos(), item, amount));
    }

    public static boolean craftable(ServerLevel level, DigitalLink link, Item item) {
        if (link == null || !link.bound()) {
            return false;
        }
        DigitalAccess access = access(link.kind());
        return access != null && access.online(level, link.pos()) && access.craftable(level, link.pos(), item);
    }

    /** Clears the "a craft failure was already told to the player" flag for this action. */
    public static void beginCraftFeedback() {
        CRAFT_FAILURE.set(false);
    }

    /** Action-bar a craft failure and remember it so a generic "you need the item" line does not replace it. */
    public static void explainCraftFailure(ServerPlayer player, String key, Object... args) {
        CRAFT_FAILURE.set(true);
        if (player != null) {
            Messages.actionBar(player, key, args);
        }
    }

    /** The failure was already shown. Keep that line on the action bar. */
    public static void keepCraftFailure() {
        CRAFT_FAILURE.set(true);
    }

    public static boolean craftFailureExplained() {
        return Boolean.TRUE.equals(CRAFT_FAILURE.get());
    }

    public static void tickCrafts(MinecraftServer server) {
        boot();
        for (DigitalAccess access : ACCESS) {
            access.tickCrafts(server);
        }
    }

    public static boolean requestCraft(ServerPlayer player, ServerLevel level, DigitalLink link, Item item, long amount) {
        if (amount <= 0L || link == null || !link.bound()) {
            return false;
        }
        DigitalAccess access = access(link.kind());
        return access != null
                && access.online(level, link.pos())
                && access.requestCraft(player, level, link.pos(), item, amount);
    }

    public static List<Integer> extractWrights(ServerPlayer player, ServerLevel level, DigitalLink link, int count) {
        if (count <= 0 || link == null || !link.bound()) {
            return List.of();
        }
        DigitalAccess access = access(link.kind());
        if (access == null || !access.online(level, link.pos())) {
            return List.of();
        }
        List<Integer> tiers = access.extractWrights(player, level, link.pos(), count);
        List<Integer> clamped = new ArrayList<>(tiers.size());
        for (int tier : tiers) {
            clamped.add(WrightTier.clamp(tier));
        }
        return clamped;
    }

    public static ServerLevel levelOf(ServerPlayer player, GlobalPos pos) {
        if (pos == null) {
            return player.level() instanceof ServerLevel server ? server : null;
        }
        ServerLevel level = player.server.getLevel(pos.dimension());
        return level == null && player.level() instanceof ServerLevel current ? current : level;
    }
}
