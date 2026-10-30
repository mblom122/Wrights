package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    /** Hatch above a placed wrightport. */
    public static Vec3 abovePort(BlockPos port) {
        return Vec3.atCenterOf(port).add(0.0D, 1.05D, 0.0D);
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
