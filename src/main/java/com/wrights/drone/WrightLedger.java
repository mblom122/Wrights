package com.wrights.drone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import com.wrights.WrightsConfig;
import com.wrights.WrightsMod;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.item.WrightportBlockItem;

/**
 * Wrights a player has stored. Personal ports are counted from what they carry.
 * Placed ports are remembered here so a port in an unloaded chunk still counts.
 */
@EventBusSubscriber(modid = WrightsMod.MOD_ID)
public final class WrightLedger extends SavedData {
    public static final String DATA_ID = "wrights_ports";
    public static final SavedData.Factory<WrightLedger> FACTORY = new SavedData.Factory<>(
            WrightLedger::new,
            WrightLedger::load,
            null);

    private final Map<Key, Port> ports = new HashMap<>();

    public static WrightLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    public static WrightLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        WrightLedger ledger = new WrightLedger();
        ListTag list = tag.getList("ports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            BlockPos pos = NbtUtils.readBlockPos(entry, "pos").orElse(null);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dim"));
            if (pos == null || dimension == null) {
                continue;
            }
            ledger.ports.put(new Key(dimension.toString(), pos.asLong()), new Port(entry.getUUID("owner"), Math.max(0, entry.getInt("units"))));
        }
        return ledger;
    }

    /** How many more wrights this player may store. */
    public static int room(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            return 0;
        }
        int owned = get(server).blockUnits(owner);
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            owned += carried(player);
        }
        return Math.max(0, WrightsConfig.maxWrightsOwned() - owned);
    }

    public void remember(UUID owner, ResourceKey<Level> dimension, BlockPos pos, int units) {
        if (owner == null || dimension == null || pos == null) {
            return;
        }
        Port next = new Port(owner, Math.max(0, units));
        Port previous = ports.put(new Key(dimension.location().toString(), pos.asLong()), next);
        if (!next.equals(previous)) {
            setDirty();
        }
    }

    public void forget(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return;
        }
        if (ports.remove(new Key(dimension.location().toString(), pos.asLong())) != null) {
            setDirty();
        }
    }

    public List<Placed> owned(UUID owner) {
        List<Placed> list = new ArrayList<>();
        if (owner == null) {
            return list;
        }
        for (Map.Entry<Key, Port> entry : ports.entrySet()) {
            if (!owner.equals(entry.getValue().owner)) {
                continue;
            }
            list.add(new Placed(entry.getKey().dimension, BlockPos.of(entry.getKey().pos), entry.getValue().units));
        }
        list.sort((a, b) -> {
            int dim = a.dimension.compareTo(b.dimension);
            if (dim != 0) {
                return dim;
            }
            int y = Integer.compare(a.pos.getY(), b.pos.getY());
            if (y != 0) {
                return y;
            }
            int x = Integer.compare(a.pos.getX(), b.pos.getX());
            return x != 0 ? x : Integer.compare(a.pos.getZ(), b.pos.getZ());
        });
        return list;
    }

    public int blockUnits(UUID owner) {
        int count = 0;
        for (Port port : ports.values()) {
            if (owner.equals(port.owner)) {
                count += port.units;
            }
        }
        return count;
    }

    /** Drops remembered ports in this chunk that are no longer there, and refreshes the ones that are. */
    public void reconcile(ResourceKey<Level> dimension, LevelChunk chunk) {
        String dim = dimension.location().toString();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        Map<Long, Port> live = new HashMap<>();
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (!(entity instanceof WrightportBlockEntity port) || port.owner() == null) {
                continue;
            }
            live.put(entity.getBlockPos().asLong(), new Port(port.owner(), port.swarm().units().size()));
        }
        boolean changed = false;
        Iterator<Map.Entry<Key, Port>> entries = ports.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Key, Port> entry = entries.next();
            Key key = entry.getKey();
            if (!key.dimension.equals(dim)) {
                continue;
            }
            BlockPos pos = BlockPos.of(key.pos);
            if (SectionPos.blockToSectionCoord(pos.getX()) != chunkX || SectionPos.blockToSectionCoord(pos.getZ()) != chunkZ) {
                continue;
            }
            Port found = live.remove(key.pos);
            if (found == null) {
                entries.remove();
                changed = true;
            } else if (!found.equals(entry.getValue())) {
                entry.setValue(found);
                changed = true;
            }
        }
        for (Map.Entry<Long, Port> found : live.entrySet()) {
            ports.put(new Key(dim, found.getKey()), found.getValue());
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    private static int carried(ServerPlayer player) {
        int count = 0;
        for (WrightportPlaces.Spot spot : WrightportPlaces.list(player)) {
            count += Swarm.from(WrightportPlaces.stack(player, spot.kind(), spot.index())).units().size();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof WrightportBlockItem) {
                count += Swarm.from(stack).units().size() * stack.getCount();
            }
        }
        return count;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Key, Port> entry : ports.entrySet()) {
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getKey().dimension);
            if (dimension == null) {
                continue;
            }
            CompoundTag saved = new CompoundTag();
            saved.putUUID("owner", entry.getValue().owner);
            saved.putString("dim", dimension.toString());
            saved.put("pos", NbtUtils.writeBlockPos(BlockPos.of(entry.getKey().pos)));
            saved.putInt("units", entry.getValue().units);
            list.add(saved);
        }
        tag.put("ports", list);
        return tag;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        get(level.getServer()).reconcile(level.dimension(), chunk);
    }

    public record Placed(String dimension, BlockPos pos, int units) {
    }

    private record Key(String dimension, long pos) {
    }

    private record Port(UUID owner, int units) {
    }
}
