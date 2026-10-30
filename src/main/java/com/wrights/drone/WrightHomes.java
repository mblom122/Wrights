package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.wrights.block.WrightportBlockEntity;

/** Where a wright lives: worn personal port, or a placed wrightport block. */
public final class WrightHomes {
    public static final int BLOCK_RANGE = 48;

    private WrightHomes() {
    }

    public record FreeWright(Swarm.Unit unit, @Nullable BlockPos homePort) {
        public boolean personal() {
            return homePort == null;
        }
    }

    /** Behind the player's shoulders, slightly up. */
    public static Vec3 behindPlayer(ServerPlayer player) {
        Vec3 back = player.getLookAngle();
        back = new Vec3(back.x, 0.0D, back.z);
        if (back.lengthSqr() < 1.0E-4D) {
            back = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            back = back.normalize();
        }
        return player.position().add(back.scale(-0.85D)).add(0.0D, 0.95D, 0.0D);
    }

    /** Inside the bay, under the lid, where a returning wright disappears. */
    public static Vec3 hatch(BlockPos port) {
        return Vec3.atBottomCenterOf(port).add(0.0D, 0.42D, 0.0D);
    }

    /** Just above the lid, where a departing wright clears the top. */
    public static Vec3 lip(BlockPos port) {
        return Vec3.atBottomCenterOf(port).add(0.0D, 1.05D, 0.0D);
    }

    /** High ring a returning wright dives into the hatch from. */
    public static Vec3 approach(BlockPos port, int slot, int count) {
        int n = Math.max(1, count);
        double angle = (Math.PI * 2.0D * slot) / n;
        double radius = n <= 1 ? 0.0D : 0.6D;
        return lip(port).add(Math.cos(angle) * radius, 5.0D, Math.sin(angle) * radius);
    }

    /** Wrights in the worn port and in placed wrightports within {@link #BLOCK_RANGE}, kept when {@code include} says so. */
    public static int countUnits(Player player, Predicate<UUID> include) {
        int count = 0;
        if (player == null) {
            return 0;
        }
        for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
            if (include.test(unit.id())) {
                count++;
            }
        }
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int minX = center.getX() - BLOCK_RANGE;
        int maxX = center.getX() + BLOCK_RANGE;
        int minY = center.getY() - 8;
        int maxY = center.getY() + 8;
        int minZ = center.getZ() - BLOCK_RANGE;
        int maxZ = center.getZ() + BLOCK_RANGE;
        int minChunkX = SectionPos.blockToSectionCoord(minX);
        int maxChunkX = SectionPos.blockToSectionCoord(maxX);
        int minChunkZ = SectionPos.blockToSectionCoord(minZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof WrightportBlockEntity port)) {
                        continue;
                    }
                    BlockPos pos = entity.getBlockPos();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY || pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }
                    for (Swarm.Unit unit : port.swarm().units()) {
                        if (include.test(unit.id())) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public static List<FreeWright> freeNear(ServerPlayer player, Predicate<UUID> free, int limit) {
        List<FreeWright> out = new ArrayList<>();
        if (limit <= 0) {
            return out;
        }
        for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
            if (!free.test(unit.id()) || ManageLock.blocks(player, unit.id())) {
                continue;
            }
            out.add(new FreeWright(unit, null));
            if (out.size() >= limit) {
                return out;
            }
        }
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int r = BLOCK_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -8, -r), center.offset(r, 8, r))) {
            if (out.size() >= limit) {
                break;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof WrightportBlockEntity port)) {
                continue;
            }
            BlockPos home = pos.immutable();
            for (Swarm.Unit unit : port.swarm().units()) {
                if (out.size() >= limit) {
                    break;
                }
                if (!free.test(unit.id())) {
                    continue;
                }
                out.add(new FreeWright(unit, home));
            }
        }
        return out;
    }

    public static boolean hasAny(ServerPlayer player) {
        if (!Wrightports.swarm(player).units().isEmpty()) {
            return true;
        }
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int r = BLOCK_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -8, -r), center.offset(r, 8, r))) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof WrightportBlockEntity port && !port.swarm().units().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Swarm.Unit findUnit(ServerPlayer player, UUID unitId) {
        for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
            if (unit.id().equals(unitId)) {
                return unit;
            }
        }
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int r = BLOCK_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -8, -r), center.offset(r, 8, r))) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof WrightportBlockEntity port)) {
                continue;
            }
            for (Swarm.Unit unit : port.swarm().units()) {
                if (unit.id().equals(unitId)) {
                    return unit;
                }
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos homeOf(ServerPlayer player, UUID unitId) {
        for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
            if (unit.id().equals(unitId)) {
                return null;
            }
        }
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int r = BLOCK_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -8, -r), center.offset(r, 8, r))) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof WrightportBlockEntity port)) {
                continue;
            }
            for (Swarm.Unit unit : port.swarm().units()) {
                if (unit.id().equals(unitId)) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }
}
