package com.autobots.drone;

import com.autobots.job.BuildJob;

/**
 * Splits a build along its long horizontal side so each drone owns a slice
 * the others do not fly into. The first and last slice also keep blocks that
 * fall outside the measured box, matching {@link #lane}.
 */
public final class CrewSplit {
    private CrewSplit() {
    }

    public static BuildJob.Slice slab(int lanes, int sizeX, int sizeY, int sizeZ, int lane) {
        BuildJob.Slice slice = new BuildJob.Slice();
        slice.x0 = -32768;
        slice.y0 = -32768;
        slice.z0 = -32768;
        slice.x1 = 32767;
        slice.y1 = 32767;
        slice.z1 = 32767;
        if (lanes <= 1) {
            return slice;
        }
        int laneIndex = Math.min(lanes - 1, Math.max(0, lane));
        int size;
        int axis;
        if (sizeX >= sizeZ && sizeX > 1) {
            size = Math.max(1, sizeX);
            axis = 0;
        } else if (sizeZ > 1) {
            size = Math.max(1, sizeZ);
            axis = 2;
        } else {
            size = Math.max(1, sizeY);
            axis = 1;
        }
        int span = Math.max(1, (size + lanes - 1) / lanes);
        int low = laneIndex == 0 ? -32768 : laneIndex * span;
        int high;
        if (laneIndex == lanes - 1) {
            high = 32767;
        } else if (laneIndex == 0) {
            high = Math.min(size - 1, span - 1);
        } else {
            high = Math.min(size - 1, laneIndex * span + span - 1);
        }
        if (axis == 0) {
            slice.x0 = low;
            slice.x1 = high;
        } else if (axis == 1) {
            slice.y0 = low;
            slice.y1 = high;
        } else {
            slice.z0 = low;
            slice.z1 = high;
        }
        return slice;
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
