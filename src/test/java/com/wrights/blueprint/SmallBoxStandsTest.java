package com.wrights.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Stand planning is closed-form arithmetic, so it runs without Minecraft on the classpath. */
class SmallBoxStandsTest {
    private static final int REACH = 2;
    private static final int SQUARE = Shapes.Kind.SQUARE.ordinal();
    private static final int SPHERE = Shapes.Kind.SPHERE.ordinal();
    private static final int CUBE = Shapes.Kind.CUBE.ordinal();

    @Test
    void floorOfThreeStandsOnTheMiddle() {
        int[][] stands = SmallBoxStands.stands(3, 1, 3, false, SQUARE, REACH);
        assertEquals(1, stands.length);
        assertArrayEquals(new int[] {1, 0, 1}, stands[0]);
    }

    @Test
    void diameterThreeSphereStandsOnItsCenter() {
        int[][] stands = SmallBoxStands.stands(3, 3, 3, false, SPHERE, REACH);
        assertEquals(1, stands.length);
        assertArrayEquals(new int[] {1, 1, 1}, stands[0]);
    }

    @Test
    void solidThreeCubeStartsAtTheCenterAndNeedsThreeStands() {
        int[][] stands = SmallBoxStands.stands(3, 3, 3, false, CUBE, REACH);
        assertEquals(3, stands.length);
        assertArrayEquals(new int[] {1, 1, 1}, stands[0]);
    }

    @Test
    void sixBySixFloorStaysOnTheNearestFirstCrawler() {
        assertEquals(0, SmallBoxStands.stands(6, 1, 6, false, SQUARE, REACH).length);
    }

    @Test
    void longBeamStaysOnTheNearestFirstCrawler() {
        assertEquals(0, SmallBoxStands.stands(12, 1, 1, false, SQUARE, REACH).length);
    }

    @Test
    void largeBoxesStayOnTheOldPlanner() {
        assertEquals(0, SmallBoxStands.stands(8, 8, 8, false, CUBE, REACH).length);
    }

    @Test
    void longFloorStaysOnTheOldPlanner() {
        assertEquals(0, SmallBoxStands.stands(20, 1, 20, false, SQUARE, REACH).length);
    }

    @Test
    void appliesRejectsEmptyBoxesAndTinyReach() {
        assertFalse(SmallBoxStands.applies(0, 1, 1, REACH));
        assertFalse(SmallBoxStands.applies(3, 1, 3, 1));
        assertTrue(SmallBoxStands.applies(3, 1, 3, REACH));
    }

    @Test
    void appliesStopsAtMaxSide() {
        assertTrue(SmallBoxStands.applies(3, 3, 3, REACH));
        assertFalse(SmallBoxStands.applies(4, 1, 3, REACH));
    }

    @Test
    void oneWideSlabOfABiggerShapeStandsOnItsMiddle() {
        // A wright whose share of a larger job is a 1x3x3 slab, planned as a plain solid box.
        int[][] stands = SmallBoxStands.stands(1, 3, 3, false, CUBE, REACH);
        assertEquals(1, stands.length);
        assertArrayEquals(new int[] {0, 1, 1}, stands[0]);
    }

    @Test
    void slabWiderThanThreeKeepsTheCrawler() {
        assertFalse(SmallBoxStands.applies(1, 6, 6, REACH));
        assertEquals(0, SmallBoxStands.stands(1, 6, 6, false, CUBE, REACH).length);
    }

    @Test
    void repeatedCallsGiveTheSameStands() {
        int[][] first = SmallBoxStands.stands(3, 3, 3, false, CUBE, REACH);
        int[][] second = SmallBoxStands.stands(3, 3, 3, false, CUBE, REACH);
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertArrayEquals(first[i], second[i]);
        }
    }
}