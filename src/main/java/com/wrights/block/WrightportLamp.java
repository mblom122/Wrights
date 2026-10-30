package com.wrights.block;

import java.util.UUID;
import java.util.function.Predicate;

import com.wrights.drone.Swarm;

/** Corner-lamp colors for a wrightport. Same meaning as the worn pack's bulb. */
public final class WrightportLamp {
    public static final int RED = 0xFF3B3B;
    public static final int YELLOW = 0xFFC030;
    public static final int BLUE = 0x3D8BFF;

    private WrightportLamp() {
    }

    /** Red when empty, yellow while any stored wright is on a job, blue when all are idle. */
    public static int color(Swarm swarm, Predicate<UUID> onJob) {
        if (swarm.units().isEmpty()) {
            return RED;
        }
        for (Swarm.Unit unit : swarm.units()) {
            if (onJob.test(unit.id())) {
                return YELLOW;
            }
        }
        return BLUE;
    }
}
