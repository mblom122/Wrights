package com.wrights.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.wrights.WrightsConfig;
import com.wrights.drone.WrightHomes;

/** Which wrightports are drawing their reach, and the boxes that reach makes. */
public final class WrightportBounds {
    private static final Set<BlockPos> shown = new HashSet<>();
    @Nullable
    private static ResourceKey<Level> dimension;
    /** Personal / worn port reach around the player (maxRange sphere drawn as a cube). */
    private static boolean personalShown;

    private WrightportBounds() {
    }

    public static boolean showing(BlockPos pos) {
        return shown.contains(pos);
    }

    public static boolean personalShowing() {
        return personalShown;
    }

    public static void togglePersonal() {
        personalShown = !personalShown;
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

    /** Axis-aligned box of {@link WrightsConfig#MAX_RANGE} around the player (personal wright reach). */
    public static AABB personalCube(Player player) {
        double r = WrightsConfig.MAX_RANGE.getAsInt();
        return new AABB(
                player.getX() - r,
                player.getY() - r,
                player.getZ() - r,
                player.getX() + r,
                player.getY() + r,
                player.getZ() + r);
    }
}
