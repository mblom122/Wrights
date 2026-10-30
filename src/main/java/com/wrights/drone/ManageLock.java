package com.wrights.drone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.wrights.item.PersonalWrightportItem;
import com.wrights.job.BuildJob;
import com.wrights.schematic.SchematicLibrary;

/** Wrights held on the Manage screen stay home until the screen lets them go. */
public final class ManageLock {
    private static final int LIFE = 40;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private record Hold(int kind, int index, long tick) {
    }

    private ManageLock() {
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

    /** On a job, including flying back, so the wright is not in the port. */
    public static boolean away(ServerPlayer player, UUID unitId) {
        SchematicLibrary library = SchematicLibrary.get(player.server);
        for (BuildJob job : library.jobsFor(player.getUUID())) {
            if (!job.finished() && job.crewUnits().contains(unitId)) {
                return true;
            }
        }
        return false;
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
