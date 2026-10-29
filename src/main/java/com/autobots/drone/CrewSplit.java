package com.autobots.drone;

/**
 * Splits a build along its long horizontal side so each drone owns a slice
 * the others do not fly into.
 */
public final class CrewSplit {
    private CrewSplit() {
    }

    public static int lane(int x, int y, int z, int lanes, int sizeX, int sizeY, int sizeZ) {
        if (lanes <= 1) {
            return 0;
        }
        if (sizeX >= sizeZ && sizeX > 1) {
            int span = Math.max(1, (sizeX + lanes - 1) / lanes);
            return Math.min(lanes - 1, Math.max(0, x) / span);
        }
        if (sizeZ > 1) {
            int span = Math.max(1, (sizeZ + lanes - 1) / lanes);
            return Math.min(lanes - 1, Math.max(0, z) / span);
        }
        int span = Math.max(1, (Math.max(1, sizeY) + lanes - 1) / lanes);
        return Math.min(lanes - 1, Math.max(0, y) / span);
    }

    public static boolean splitOnX(int lanes, int sizeX, int sizeZ) {
        return lanes > 1 && sizeX >= sizeZ && sizeX > 1;
    }
}
