package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsConfig;
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

    /** A placed port can send wrights only for the player who owns it. Ports from before owners existed do not. */
    private static boolean ownedBy(WrightportBlockEntity port, Player player) {
        return player != null && ownedBy(port, player.getUUID());
    }

    private static int chunkStart(BlockPos min, BlockPos max, boolean x) {
        BlockPos high = max == null ? min : max;
        int first = SectionPos.blockToSectionCoord(x ? min.getX() : min.getZ());
        int second = SectionPos.blockToSectionCoord(x ? high.getX() : high.getZ());
        return Math.min(first, second) - PORT_CHUNKS;
    }

    private static int chunkEnd(BlockPos min, BlockPos max, boolean x) {
        BlockPos high = max == null ? min : max;
        int first = SectionPos.blockToSectionCoord(x ? min.getX() : min.getZ());
        int second = SectionPos.blockToSectionCoord(x ? high.getX() : high.getZ());
        return Math.max(first, second) + PORT_CHUNKS;
    }

    private static boolean ownedBy(WrightportBlockEntity port, UUID owner) {
        return owner != null && owner.equals(port.owner());
    }

    /** Chunks of reach around a placed wrightport, so the area is 7 by 7 chunks with the port in the middle. */
    public static final int PORT_CHUNKS = 3;

    public static boolean playerCovers(ServerPlayer player, ResourceKey<Level> dimension, BlockPos site) {
        return playerCovers(player, dimension, site, site);
    }

    /** True when the player is within range of the closest point on the build, not only one corner. */
    public static boolean playerCovers(ServerPlayer player, ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        if (player == null || min == null || dimension == null || !player.level().dimension().equals(dimension)) {
            return false;
        }
        BlockPos high = max == null ? min : max;
        double x0 = Math.min(min.getX(), high.getX());
        double y0 = Math.min(min.getY(), high.getY());
        double z0 = Math.min(min.getZ(), high.getZ());
        double x1 = Math.max(min.getX(), high.getX()) + 1.0D;
        double y1 = Math.max(min.getY(), high.getY()) + 1.0D;
        double z1 = Math.max(min.getZ(), high.getZ()) + 1.0D;
        double x = Math.max(x0, Math.min(player.getX(), x1));
        double y = Math.max(y0, Math.min(player.getY() + 0.5D, y1));
        double z = Math.max(z0, Math.min(player.getZ(), z1));
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        return player.distanceToSqr(x, y, z) <= range * range;
    }

    /** A placed port covers a build when that build sits in its 7 by 7 chunk square. */
    public static boolean portCovers(ResourceKey<Level> portDimension, BlockPos port, ResourceKey<Level> jobDimension, BlockPos site) {
        if (port == null || site == null || portDimension == null || !portDimension.equals(jobDimension)) {
            return false;
        }
        int dx = Math.abs(SectionPos.blockToSectionCoord(port.getX()) - SectionPos.blockToSectionCoord(site.getX()));
        int dz = Math.abs(SectionPos.blockToSectionCoord(port.getZ()) - SectionPos.blockToSectionCoord(site.getZ()));
        return dx <= PORT_CHUNKS && dz <= PORT_CHUNKS;
    }

    /** A placed port covers a build when its chunk square meets any part of that build. */
    public static boolean portCoversArea(ResourceKey<Level> portDimension, BlockPos port, ResourceKey<Level> jobDimension, BlockPos min, BlockPos max) {
        if (port == null || min == null || portDimension == null || !portDimension.equals(jobDimension)) {
            return false;
        }
        BlockPos high = max == null ? min : max;
        int portX = SectionPos.blockToSectionCoord(port.getX());
        int portZ = SectionPos.blockToSectionCoord(port.getZ());
        int minX = SectionPos.blockToSectionCoord(Math.min(min.getX(), high.getX()));
        int maxX = SectionPos.blockToSectionCoord(Math.max(min.getX(), high.getX()));
        int minZ = SectionPos.blockToSectionCoord(Math.min(min.getZ(), high.getZ()));
        int maxZ = SectionPos.blockToSectionCoord(Math.max(min.getZ(), high.getZ()));
        int dx = portX < minX ? minX - portX : portX > maxX ? portX - maxX : 0;
        int dz = portZ < minZ ? minZ - portZ : portZ > maxZ ? portZ - maxZ : 0;
        return dx <= PORT_CHUNKS && dz <= PORT_CHUNKS;
    }

    /**
     * What can reach a build. Personal wrights only count as reachable while the player is near the site.
     * A placed port counts while its 7 by 7 chunk square contains the site.
     */
    public record Reach(boolean playerNear, int personal, int personalFree, int ports, int portWrights, int portFree) {
    }

    public static Reach reach(ServerPlayer player, ServerLevel level, BlockPos site, Predicate<UUID> free) {
        return reach(player, level, site, site, playerCovers(player, level == null ? null : level.dimension(), site), free);
    }

    public static Reach reach(ServerPlayer player, ServerLevel level, BlockPos min, BlockPos max, boolean playerNear, Predicate<UUID> free) {
        boolean near = playerNear;
        BlockPos site = min;
        int personal = 0;
        int personalFree = 0;
        if (player != null) {
            for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
                personal++;
                if (free.test(unit.id()) && !ManageLock.blocks(player, unit.id())) {
                    personalFree++;
                }
            }
        }
        int ports = 0;
        int portWrights = 0;
        int portFree = 0;
        if (level != null && site != null) {
            int fromX = chunkStart(min, max, true);
            int toX = chunkEnd(min, max, true);
            int fromZ = chunkStart(min, max, false);
            int toZ = chunkEnd(min, max, false);
            for (int chunkX = fromX; chunkX <= toX; chunkX++) {
                for (int chunkZ = fromZ; chunkZ <= toZ; chunkZ++) {
                    if (!level.hasChunk(chunkX, chunkZ)) {
                        continue;
                    }
                    for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                        if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, player)) {
                            continue;
                        }
                        if (!portCoversArea(level.dimension(), entity.getBlockPos(), level.dimension(), min, max)) {
                            continue;
                        }
                        ports++;
                        for (Swarm.Unit unit : port.swarm().units()) {
                            portWrights++;
                            if (free.test(unit.id())) {
                                portFree++;
                            }
                        }
                    }
                }
            }
        }
        return new Reach(near, personal, personalFree, ports, portWrights, portFree);
    }

    public static int coveringPortCount(ServerLevel level, BlockPos site, UUID owner) {
        if (level == null || site == null) {
            return 0;
        }
        int count = 0;
        int centerX = SectionPos.blockToSectionCoord(site.getX());
        int centerZ = SectionPos.blockToSectionCoord(site.getZ());
        for (int chunkX = centerX - PORT_CHUNKS; chunkX <= centerX + PORT_CHUNKS; chunkX++) {
            for (int chunkZ = centerZ - PORT_CHUNKS; chunkZ <= centerZ + PORT_CHUNKS; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (entity instanceof WrightportBlockEntity port && ownedBy(port, owner)
                            && portCovers(level.dimension(), entity.getBlockPos(), level.dimension(), site)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * The wrightport nearest the build. Looks a few chunks past the port's reach so one
     * sitting just outside still gets pointed out.
     */
    @Nullable
    public static BlockPos closestPort(ServerLevel level, BlockPos site, UUID owner, boolean inRangeOnly) {
        if (level == null || site == null) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 from = Vec3.atCenterOf(site);
        int centerX = SectionPos.blockToSectionCoord(site.getX());
        int centerZ = SectionPos.blockToSectionCoord(site.getZ());
        int reach = inRangeOnly ? PORT_CHUNKS : PORT_CHUNKS + 6;
        for (int chunkX = centerX - reach; chunkX <= centerX + reach; chunkX++) {
            for (int chunkZ = centerZ - reach; chunkZ <= centerZ + reach; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, owner)) {
                        continue;
                    }
                    BlockPos pos = entity.getBlockPos();
                    if (inRangeOnly && !portCovers(level.dimension(), pos, level.dimension(), site)) {
                        continue;
                    }
                    double distance = from.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    /**
     * The dock this wright left is no longer a place it can return to.
     * A personal wright counts once it is no longer in the worn port.
     * A block wright counts once that block is loaded and is not a wrightport.
     */
    public static boolean dockGone(ServerLevel level, ServerPlayer player, @Nullable BlockPos home, UUID unit) {
        if (unit == null) {
            return false;
        }
        if (home == null) {
            if (player == null) {
                return false;
            }
            return !Wrightports.swarm(player).contains(unit);
        }
        if (level == null || !level.isLoaded(home)) {
            return false;
        }
        return !(level.getBlockEntity(home) instanceof WrightportBlockEntity);
    }

    /**
     * Nearest owned wrightport that covers {@code site} and still has a free slot.
     * {@code reserved} counts slots already promised to other wrights this pass.
     */
    @Nullable
    public static BlockPos openPort(ServerLevel level, BlockPos site, @Nullable UUID owner, @Nullable Map<BlockPos, Integer> reserved) {
        if (level == null || site == null || owner == null) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 from = Vec3.atCenterOf(site);
        int centerX = SectionPos.blockToSectionCoord(site.getX());
        int centerZ = SectionPos.blockToSectionCoord(site.getZ());
        for (int chunkX = centerX - PORT_CHUNKS; chunkX <= centerX + PORT_CHUNKS; chunkX++) {
            for (int chunkZ = centerZ - PORT_CHUNKS; chunkZ <= centerZ + PORT_CHUNKS; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, owner)) {
                        continue;
                    }
                    BlockPos pos = entity.getBlockPos();
                    if (!portCovers(level.dimension(), pos, level.dimension(), site)) {
                        continue;
                    }
                    int held = reserved == null ? 0 : reserved.getOrDefault(pos, 0);
                    if (port.swarm().units().size() + held >= Swarm.capacity()) {
                        continue;
                    }
                    double distance = from.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    /**
     * Free wrights that can reach {@code site}. Personal ones count only while you are in range.
     * Block ones count while their port's 7 by 7 chunk square contains the site.
     */
    public static List<FreeWright> freeFor(ServerPlayer player, ServerLevel level, BlockPos site, boolean playerFirst, Predicate<UUID> free, int limit) {
        boolean near = player != null && level != null && playerCovers(player, level.dimension(), site);
        return freeFor(player, level, site, site, near, playerFirst, free, limit);
    }

    public static List<FreeWright> freeFor(ServerPlayer player, ServerLevel level, BlockPos min, BlockPos max, boolean playerNear, boolean playerFirst, Predicate<UUID> free, int limit) {
        List<FreeWright> personal = new ArrayList<>();
        List<FreeWright> blocks = new ArrayList<>();
        BlockPos site = min;
        if (limit <= 0 || player == null || level == null || site == null) {
            return personal;
        }
        if (playerNear) {
            for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
                if (!free.test(unit.id()) || ManageLock.blocks(player, unit.id())) {
                    continue;
                }
                personal.add(new FreeWright(unit, null));
            }
        }
        BlockPos high = max == null ? site : max;
        int fromX = chunkStart(site, high, true);
        int toX = chunkEnd(site, high, true);
        int fromZ = chunkStart(site, high, false);
        int toZ = chunkEnd(site, high, false);
        for (int chunkX = fromX; chunkX <= toX; chunkX++) {
            for (int chunkZ = fromZ; chunkZ <= toZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, player)) {
                        continue;
                    }
                    BlockPos home = entity.getBlockPos();
                    if (!portCoversArea(level.dimension(), home, level.dimension(), site, high)) {
                        continue;
                    }
                    for (Swarm.Unit unit : port.swarm().units()) {
                        if (!free.test(unit.id())) {
                            continue;
                        }
                        blocks.add(new FreeWright(unit, home.immutable()));
                    }
                }
            }
        }
        List<FreeWright> ordered = new ArrayList<>();
        if (playerFirst) {
            ordered.addAll(personal);
            ordered.addAll(blocks);
        } else {
            ordered.addAll(blocks);
            ordered.addAll(personal);
        }
        if (ordered.size() > limit) {
            return new ArrayList<>(ordered.subList(0, limit));
        }
        return ordered;
    }

    /** True when that wright is still stored in the home it left from. An unloaded port chunk counts as still there. */
    public static boolean stillThere(ServerLevel level, ServerPlayer player, @Nullable BlockPos home, UUID id) {
        if (id == null) {
            return false;
        }
        if (home == null) {
            for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
                if (unit.id().equals(id)) {
                    return true;
                }
            }
            return false;
        }
        if (level == null || !level.isLoaded(home)) {
            return true;
        }
        BlockEntity entity = level.getBlockEntity(home);
        if (!(entity instanceof WrightportBlockEntity port)) {
            // The dock was removed while this wright is still out. Keep it on the job until it comes back.
            return true;
        }
        if (!ownedBy(port, player)) {
            return false;
        }
        for (Swarm.Unit unit : port.swarm().units()) {
            if (unit.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static FreeWright locate(ServerLevel level, ServerPlayer player, UUID id, BlockPos site) {
        if (id == null || player == null) {
            return null;
        }
        for (Swarm.Unit unit : Wrightports.swarm(player).units()) {
            if (unit.id().equals(id)) {
                return new FreeWright(unit, null);
            }
        }
        if (level != null && site != null) {
            List<FreeWright> found = freeFor(player, level, site, false, id::equals, 1);
            if (!found.isEmpty() && !found.get(0).personal()) {
                return found.get(0);
            }
        }
        Swarm.Unit near = findUnit(player, id);
        if (near == null) {
            return null;
        }
        return new FreeWright(near, homeOf(player, id));
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
                    if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, player)) {
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
            if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, player)) {
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
            if (entity instanceof WrightportBlockEntity port && ownedBy(port, player) && !port.swarm().units().isEmpty()) {
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
            if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, player)) {
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
            if (!(entity instanceof WrightportBlockEntity port) || !ownedBy(port, player)) {
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
