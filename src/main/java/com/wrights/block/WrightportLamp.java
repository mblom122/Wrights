package com.wrights.block;

import java.util.UUID;
import java.util.function.Predicate;

import com.wrights.drone.Swarm;

/** Corner-lamp colors for a wrightport. Same meaning as the worn pack's bulb. */
public final class WrightportLamp {
    public static final int RED = 0xFF3B3B;
    public static final int YELLOW = 0xFFC030;
    public static final int GREEN = 0x3DDB5C;
    /** Buffer is empty while wrights are stored. */
    public static final int AMBER = 0xFF8C1A;

    private WrightportLamp() {
    }

    /** Red when empty, yellow when every wright is out, green when one is free. */
    public static int color(Swarm swarm, Predicate<UUID> onJob) {
        return color(swarm, onJob, false);
    }

    /** Amber when the port has wrights but no Forge Energy left. */
    public static int color(Swarm swarm, Predicate<UUID> onJob, boolean energyStalled) {
        if (swarm.units().isEmpty()) {
            return RED;
        }
        if (energyStalled) {
            return AMBER;
        }
        for (Swarm.Unit unit : swarm.units()) {
            if (!onJob.test(unit.id())) {
                return GREEN;
            }
        }
        return YELLOW;
    }
}
