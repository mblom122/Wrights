package com.wrights.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.network.BlueprintPayloads;
import com.wrights.util.Messages;

/**
 * Manage a placed wrightport from the controller, without standing at the block.
 * Only the player who placed it can see or change it.
 */
public final class PlacedPortManage {
    private PlacedPortManage() {
    }

    public static void sync(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        WrightLedger ledger = WrightLedger.get(player.getServer());
        ledger.sweep(player.getServer());
        var owned = ledger.listed(player.getUUID());
        var ports = new java.util.ArrayList<BlueprintPayloads.PlacedPort>(Math.min(owned.size(), 512));
        for (int i = 0; i < owned.size() && ports.size() < 512; i++) {
            WrightLedger.Placed placed = owned.get(i);
            ports.add(new BlueprintPayloads.PlacedPort(placed.dimension(), placed.pos(), placed.units(), placed.unowned()));
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.PlacedPorts(ports));
    }

    public static void swarm(ServerPlayer player, String dimension, BlockPos pos) {
        WrightportBlockEntity port = ownedPort(player, dimension, pos);
        if (port == null) {
            sendSwarm(player, dimension, pos, true, Swarm.EMPTY);
            sync(player);
            return;
        }
        sendSwarm(player, dimension, pos, false, port.swarm());
    }

    public static void edit(ServerPlayer player, String dimension, BlockPos pos, int action, int unit) {
        WrightportBlockEntity port = ownedPort(player, dimension, pos);
        if (port == null) {
            sendSwarm(player, dimension, pos, true, Swarm.EMPTY);
            sync(player);
            return;
        }
        if (port.owner() == null) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        Swarm next = HangarOps.edit(player, port.swarm(), action, unit);
        port.setSwarm(next);
        sendSwarm(player, dimension, pos, false, next);
        sync(player);
    }

    public static void batch(ServerPlayer player, String dimension, BlockPos pos, int action, int value, int[] units) {
        WrightportBlockEntity port = ownedPort(player, dimension, pos);
        if (port == null) {
            sendSwarm(player, dimension, pos, true, Swarm.EMPTY);
            sync(player);
            return;
        }
        if (port.owner() == null) {
            port.assignOwner(player.getUUID(), player.getGameProfile().getName());
        }
        Swarm next = HangarOps.applyBatch(player, port.swarm(), action, value, units);
        port.setSwarm(next);
        sendSwarm(player, dimension, pos, false, next);
        sync(player);
    }

    public static void visit(ServerPlayer player, String dimension, BlockPos pos) {
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            Messages.actionBar(player, "message.wrights.visit_survival");
            return;
        }
        ServerLevel level = level(player, dimension, pos);
        if (level == null) {
            Messages.actionBar(player, "message.wrights.visit_blocked");
            return;
        }
        WrightportBlockEntity port = ownedPort(player, dimension, pos);
        if (port == null) {
            Messages.actionBar(player, "message.wrights.placed_missing");
            sync(player);
            return;
        }
        BlockPos spot = standNear(level, pos);
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, player.getYRot(), player.getXRot());
        Messages.actionBar(player, "message.wrights.visited", spot.getX(), spot.getY(), spot.getZ());
    }

    private static void sendSwarm(ServerPlayer player, String dimension, BlockPos pos, boolean missing, Swarm swarm) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.PlacedSwarm(dimension, pos, missing, swarm));
    }

    private static WrightportBlockEntity ownedPort(ServerPlayer player, String dimension, BlockPos pos) {
        ServerLevel level = level(player, dimension, pos);
        if (level == null || player.getServer() == null) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof WrightportBlockEntity port)) {
            WrightLedger.get(player.getServer()).forget(level.dimension(), pos);
            return null;
        }
        if (port.owner() != null && !port.owner().equals(player.getUUID())) {
            return null;
        }
        return port;
    }

    private static ServerLevel level(ServerPlayer player, String dimension, BlockPos pos) {
        if (player.getServer() == null || pos == null || dimension == null || dimension.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            return null;
        }
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return level;
    }

    private static BlockPos standNear(ServerLevel level, BlockPos port) {
        BlockPos[] tries = {
                port.above(),
                port.north(), port.south(), port.east(), port.west(),
                port.north().above(), port.south().above(), port.east().above(), port.west().above()
        };
        for (BlockPos spot : tries) {
            if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()) {
                return spot;
            }
        }
        return port.above();
    }
}
