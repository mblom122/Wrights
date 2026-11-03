package com.wrights.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WrightEnergyTest {
    @Test
    void matchedRotorAndCorePayTheBaseCost() {
        for (int tier = WrightTier.BASIC; tier <= WrightTier.APEX; tier++) {
            assertEquals(100, WrightTier.energyPercent(tier, tier));
            assertEquals(75, WrightTier.energyCost(75, tier, tier));
        }
    }

    @Test
    void fasterRotorsDrawMore() {
        for (int tier = WrightTier.SCOUT; tier <= WrightTier.APEX; tier++) {
            assertTrue(WrightTier.rotorDraw(tier) > WrightTier.rotorDraw(tier - 1));
            assertTrue(WrightTier.energyCost(75, tier, WrightTier.BASIC) > WrightTier.energyCost(75, tier - 1, WrightTier.BASIC));
        }
        assertEquals(150, WrightTier.energyCost(75, WrightTier.APEX, WrightTier.BASIC));
    }

    @Test
    void strongerCoresSave() {
        for (int core = WrightTier.SCOUT; core <= WrightTier.APEX; core++) {
            assertTrue(WrightTier.energyCost(250, WrightTier.VECTOR, core) < WrightTier.energyCost(250, WrightTier.VECTOR, core - 1));
        }
        assertEquals(38, WrightTier.energyCost(75, WrightTier.BASIC, WrightTier.APEX));
    }

    @Test
    void costRoundsUpAndNeverReachesZero() {
        assertEquals(1, WrightTier.energyCost(1, WrightTier.BASIC, WrightTier.APEX));
        assertEquals(0, WrightTier.energyCost(0, WrightTier.APEX, WrightTier.BASIC));
    }
}
