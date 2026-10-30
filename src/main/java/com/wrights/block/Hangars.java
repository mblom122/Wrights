package com.wrights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Hangars {
    private Hangars() {
    }

    /** Opens nearby hangar doors for drone traffic around a point. */
    public static void pulseTraffic(Level level, Vec3 around, double radius) {
        if (level == null || around == null) {
            return;
        }
        BlockPos center = BlockPos.containing(around);
        int r = Math.max(1, (int) Math.ceil(radius));
        BlockPos.betweenClosedStream(center.offset(-r, -2, -r), center.offset(r, 4, r)).forEach(pos -> {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof HangarBlockEntity hangar) {
                hangar.requestDoorOpen(40);
            }
        });
    }

    public static void pulseTraffic(Level level, BlockPos around, double radius) {
        if (around == null) {
            return;
        }
        pulseTraffic(level, Vec3.atCenterOf(around), radius);
    }

    public static boolean hasDroneTraffic(Level level, BlockPos hangarPos, double radius) {
        if (level == null || hangarPos == null) {
            return false;
        }
        AABB box = new AABB(hangarPos).inflate(radius, radius + 2.0D, radius).move(0.0D, 1.0D, 0.0D);
        return !level.getEntitiesOfClass(com.wrights.entity.DroneEntity.class, box, drone -> drone.isAlive()).isEmpty();
    }
}
