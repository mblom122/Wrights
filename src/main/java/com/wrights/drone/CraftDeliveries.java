package com.wrights.drone;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.item.ModItems;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.network.BlueprintPayloads;
import com.wrights.storage.DigitalLink;
import com.wrights.storage.DigitalNetworks;
import com.wrights.storage.ItemSource;
import com.wrights.util.Messages;

/**
 * After a manage-page craft starts, finished wrights sit in digital storage until this
 * pulls them into the hangar that asked for them.
 */
public final class CraftDeliveries {
    private static final long LIFE_TICKS = 20L * 60L * 10L;
    private static final ThreadLocal<Hangar> ACTIVE = new ThreadLocal<>();
    private static final Map<UUID, Delivery> WAITING = new HashMap<>();

    private CraftDeliveries() {
    }

    public static void push(Hangar hangar) {
        ACTIVE.set(hangar);
    }

    public static void pop() {
        ACTIVE.remove();
    }

    /** Remember that this hangar is waiting for up to {@code amount} crafted wrights. */
    public static void expect(ServerPlayer player, long amount) {
        Hangar hangar = ACTIVE.get();
        if (player == null || hangar == null || amount <= 0L) {
            return;
        }
        long want = Math.min(Integer.MAX_VALUE, amount);
        WAITING.put(player.getUUID(), new Delivery(player.getUUID(), hangar, want, want, false, player.level().getGameTime() + LIFE_TICKS));
    }

    /** The network accepted a plan. {@code planned} may be less than {@code requested}. */
    public static void accepted(ServerPlayer player, Item item, long planned, long requested) {
        if (player == null || item == null || planned <= 0L) {
            return;
        }
        ItemStack named = new ItemStack(item);
        if (planned < requested) {
            Messages.actionBar(player, "message.wrights.digital_craft_partial", planned, requested, named.getHoverName());
        } else {
            Messages.actionBar(player, "message.wrights.digital_crafted", planned, named.getHoverName());
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DigitalUse(List.of(
                new BlueprintPayloads.DigitalUseLine(Item.getId(item), 0, (int) Math.min(Integer.MAX_VALUE, planned)))));
        if (item != ModItems.WRIGHT.get()) {
            return;
        }
        Delivery prior = WAITING.get(player.getUUID());
        if (prior == null) {
            return;
        }
        long take = Math.min(prior.remaining, planned);
        WAITING.put(player.getUUID(), new Delivery(prior.player, prior.hangar, take, take, true, prior.until));
    }

    public static void cancel(UUID player) {
        if (player != null) {
            WAITING.remove(player);
        }
    }

    public static void tick(MinecraftServer server) {
        if (WAITING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Delivery>> entries = WAITING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Delivery> entry = entries.next();
            Delivery delivery = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(delivery.player);
            if (player == null) {
                continue;
            }
            if (player.level().getGameTime() > delivery.until) {
                entries.remove();
                continue;
            }
            if (!delivery.confirmed) {
                continue;
            }
            int left = (int) Math.min(Integer.MAX_VALUE, delivery.remaining);
            if (left <= 0) {
                entries.remove();
                continue;
            }
            int added = deliver(player, delivery.hangar, left);
            if (added <= 0) {
                continue;
            }
            long rest = delivery.remaining - added;
            if (rest <= 0L) {
                entries.remove();
            } else {
                entry.setValue(new Delivery(delivery.player, delivery.hangar, rest, rest, true, delivery.until));
            }
        }
    }

    private static int deliver(ServerPlayer player, Hangar hangar, int want) {
        if (hangar.placed()) {
            return deliverPlaced(player, hangar, want);
        }
        return deliverPersonal(player, hangar, want);
    }

    private static int deliverPersonal(ServerPlayer player, Hangar hangar, int want) {
        ItemStack stack = WrightportPlaces.stack(player, hangar.kind, hangar.index);
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            cancel(player.getUUID());
            return 0;
        }
        Swarm swarm = Swarm.from(stack);
        int room = room(player, hangar.owner, swarm);
        if (room <= 0) {
            cancel(player.getUUID());
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return 0;
        }
        List<Integer> tiers = pull(player, Math.min(room, want));
        if (tiers.isEmpty()) {
            return 0;
        }
        Swarm next = swarm;
        for (int tier : tiers) {
            next = next.addUnit(tier, "");
        }
        WrightportPlaces.write(player, hangar.kind, hangar.index, next);
        ManageLock.hold(player, hangar.kind, hangar.index);
        Messages.actionBar(player, "message.wrights.wrights_added", tiers.size());
        return tiers.size();
    }

    private static int deliverPlaced(ServerPlayer player, Hangar hangar, int want) {
        ServerLevel level = player.getServer().getLevel(hangar.dimension);
        if (level == null || !(level.getBlockEntity(hangar.pos) instanceof WrightportBlockEntity port)) {
            cancel(player.getUUID());
            return 0;
        }
        Swarm swarm = port.swarm();
        int room = room(player, hangar.owner, swarm);
        if (room <= 0) {
            cancel(player.getUUID());
            Messages.actionBar(player, "message.wrights.drone_full", Swarm.capacity());
            return 0;
        }
        List<Integer> tiers = pull(player, Math.min(room, want));
        if (tiers.isEmpty()) {
            return 0;
        }
        Swarm next = swarm;
        for (int tier : tiers) {
            next = next.addUnit(tier, "");
        }
        port.setSwarm(next);
        PlacedPortManage.swarm(player, hangar.dimension.location().toString(), hangar.pos);
        Messages.actionBar(player, "message.wrights.wrights_added", tiers.size());
        return tiers.size();
    }

    private static int room(ServerPlayer player, UUID owner, Swarm swarm) {
        int portRoom = Swarm.capacity() - swarm.units().size();
        if (portRoom <= 0) {
            return 0;
        }
        int capRoom = WrightLedger.room(player.serverLevel().getServer(), owner);
        if (capRoom <= 0) {
            return 0;
        }
        return Math.min(portRoom, capRoom);
    }

    private static List<Integer> pull(ServerPlayer player, int want) {
        DigitalLink link = ItemSource.linkOf(player);
        ServerLevel level = DigitalNetworks.levelOf(player, link == null ? null : link.pos());
        if (level == null || want <= 0) {
            return List.of();
        }
        return DigitalNetworks.extractWrights(player, level, link, want);
    }

    public record Hangar(UUID owner, int kind, int index, ResourceKey<Level> dimension, BlockPos pos) {
        public static Hangar personal(UUID owner, int kind, int index) {
            return new Hangar(owner, kind, index, null, null);
        }

        public static Hangar placed(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
            return new Hangar(owner, -1, -1, dimension, pos);
        }

        public boolean placed() {
            return pos != null && dimension != null;
        }
    }

    private record Delivery(UUID player, Hangar hangar, long remaining, long expected, boolean confirmed, long until) {
    }
}
