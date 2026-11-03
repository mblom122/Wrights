package com.wrights.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnergyBarsTest {
    @Test
    void emptyIsDarkAndFullFillsEveryBar() {
        assertEquals(0, EnergyBars.lit(0, 600));
        assertEquals(6, EnergyBars.lit(600, 600));
        assertEquals(0, EnergyBars.lit(50, 0));
    }

    @Test
    void anyChargeLightsOneBarAndEachSixthAddsOne() {
        assertEquals(1, EnergyBars.lit(1, 600));
        assertEquals(1, EnergyBars.lit(100, 600));
        assertEquals(2, EnergyBars.lit(101, 600));
        assertEquals(5, EnergyBars.lit(500, 600));
        assertEquals(6, EnergyBars.lit(501, 600));
        assertEquals(6, EnergyBars.lit(900, 600));
    }
}
