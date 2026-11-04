package com.wrights.drone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.wrights.item.PersonalWrightportItem;

/** Wrights held on the Manage screen stay home until the screen lets them go. */
public final class ManageLock {
    private static final int LIFE = 40;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private record Hold(int kind, int index, long tick) {
    }

    private ManageLock() {
    }

    /** Releases every hold when the server stops. */
    public static void reset() {
        HOLDS.clear();
    }

    public static void hold(ServerPlayer player, int kind, int index) {
        HOLDS.put(player.getUUID(), new Hold(kind, index, player.level().getGameTime()));
    }

    public static void clear(ServerPlayer player) {
        HOLDS.remove(player.getUUID());
    }

    /** True when this wright is sitting in the port currently being managed. */
    public static boolean blocks(ServerPlayer player, UUID unitId) {
        Hold hold = fresh(player);
        if (hold == null || away(player, unitId)) {
            return false;
        }
        ItemStack stack = WrightportPlaces.stack(player, hold.kind, hold.index);
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            return false;
        }
        for (Swarm.Unit unit : Swarm.from(stack).units()) {
            if (unit.id().equals(unitId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Physically out of the hangar (on site or flying home). Assigned-but-docked crew
     * leftovers after a stall must stay editable.
     */
    public static boolean away(ServerPlayer player, UUID unitId) {
        return com.wrights.job.CrewRoster.isOutOnJob(player, unitId);
    }

    private static Hold fresh(ServerPlayer player) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null) {
            return null;
        }
        if (player.level().getGameTime() - hold.tick > LIFE) {
            HOLDS.remove(player.getUUID());
            return null;
        }
        return hold;
    }
}
