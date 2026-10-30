package com.wrights.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.wrights.drone.WrightHomes;

/** Which placed wrightports are drawing their reach, and the cube that reach makes. */
public final class WrightportBounds {
    private static final Set<BlockPos> shown = new HashSet<>();
    @Nullable
    private static ResourceKey<Level> dimension;

    private WrightportBounds() {
    }

    public static boolean showing(BlockPos pos) {
        return shown.contains(pos);
    }

    public static void toggle(ResourceKey<Level> level, BlockPos pos) {
        if (dimension != null && !dimension.equals(level)) {
            shown.clear();
        }
        dimension = level;
        BlockPos key = pos.immutable();
        if (!shown.add(key)) {
            shown.remove(key);
        }
    }

    public static List<BlockPos> visible(ResourceKey<Level> level) {
        if (dimension == null || !dimension.equals(level) || shown.isEmpty()) {
            return List.of();
        }
        return List.copyOf(shown);
    }

    public static void forget(BlockPos pos) {
        shown.remove(pos);
    }

    /**
     * Seven chunk sections on each axis, with this port's section in the middle.
     * Across the ground that is the same 7 by 7 reach a placed port already covers.
     */
    public static AABB cube(BlockPos port) {
        int reach = WrightHomes.PORT_CHUNKS;
        int sectionX = SectionPos.blockToSectionCoord(port.getX());
        int sectionY = SectionPos.blockToSectionCoord(port.getY());
        int sectionZ = SectionPos.blockToSectionCoord(port.getZ());
        return new AABB(
                SectionPos.sectionToBlockCoord(sectionX - reach),
                SectionPos.sectionToBlockCoord(sectionY - reach),
                SectionPos.sectionToBlockCoord(sectionZ - reach),
                SectionPos.sectionToBlockCoord(sectionX + reach + 1),
                SectionPos.sectionToBlockCoord(sectionY + reach + 1),
                SectionPos.sectionToBlockCoord(sectionZ + reach + 1));
    }
}
