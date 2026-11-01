package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import com.wrights.WrightsConfig;

/**
 * Block positions for Shape mode. Offsets are relative to the center block.
 */
public final class Shapes {
    public enum Kind {
        CIRCLE(false, false),
        RING(true, true),
        SQUARE(false, false),
        LINE(true, true),
        SPHERE(true, false),
        CUBE(true, true),
        CYLINDER(true, true),
        DOME(true, true),
        PYRAMID(true, true);

        private final boolean volume;
        private final boolean usesHeight;

        Kind(boolean volume, boolean usesHeight) {
            this.volume = volume;
            this.usesHeight = usesHeight;
        }

        public boolean volume() {
            return volume;
        }

        /** True when the Height slider changes this shape. */
        public boolean usesHeight() {
            return usesHeight;
        }

        public static Kind byId(int id) {
            Kind[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }
    }

    /**
     * Height / extrusion axis (cross-section is the other two).
     * 0 = Y upright (XZ), 1 = Z (XY), 2 = X (YZ). Same ids as the old plane control.
     */
    public static final int PLANE_XZ = 0;
    public static final int PLANE_XY = 1;
    public static final int PLANE_YZ = 2;
    public static final int AXIS_Y = PLANE_XZ;
    public static final int AXIS_Z = PLANE_XY;
    public static final int AXIS_X = PLANE_YZ;

    private Shapes() {
    }

    public static int clampSize(int size) {
        return Mth.clamp(size, 1, WrightsConfig.maxShapeAxis());
    }

    public static int clampHeight(int height) {
        return clampSize(height);
    }

    public static int clampPlane(int plane) {
        return Mth.clamp(plane, 0, 2);
    }

    /** Quarter-turns around Y: 0–3. */
    public static int clampYaw(int yaw) {
        return Math.floorMod(yaw, 4);
    }

    /**
     * Quarter-turns so the shape’s default forward (local +Z / south) aims along {@code facing}.
     * Vertical facing counts as south. Matches Ctrl+scroll dialing the four sides around the player.
     */
    public static int facingYaw(Direction facing) {
        if (facing == null || facing.getAxis() == Direction.Axis.Y) {
            return 0;
        }
        // get2DDataValue: S=0 W=1 N=2 E=3; we need S=0 E=1 N=2 W=3.
        return Math.floorMod(-facing.get2DDataValue(), 4);
    }

    /**
     * World yaw from a player-relative offset: 0 = toward facing, then +90° CW per step
     * (the four sides around the player).
     */
    public static int worldYaw(Direction facing, int relative) {
        return clampYaw(facingYaw(facing) + relative);
    }

    /** World positions for one shape centered on {@code center}. */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, boolean hollow, int axis) {
        return blocks(kind, center, size, height, 1, hollow, axis, 0);
    }

    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int axis) {
        return blocks(kind, center, size, height, thickness, hollow, axis, 0);
    }

    /**
     * @param height box / cylinder / pyramid / dome height (box height 1 = flat plate); for rings, tube diameter (1 = flat)
     * @param thickness ring band width; line beam width; cube half-depth on local Z
     * @param axis height axis: {@link #AXIS_Y}, {@link #AXIS_Z}, or {@link #AXIS_X}
     * @param rotation quarter-turns around world Y after axis remap (0–3), Ctrl+scroll in the world
     */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int axis, int rotation) {
        List<BlockPos> out = new ArrayList<>();
        if (center == null || kind == null) {
            return out;
        }
        int r = clampSize(size);
        int h = clampHeight(height);
        int band = clampHeight(thickness);
        // Always build Y-up / XZ cross-section, then orient with axis + rotation.
        switch (kind) {
            case CIRCLE -> disk(out, center, r, hollow, AXIS_Y, false);
            case RING -> ring(out, center, r, band, h, AXIS_Y, hollow);
            case SQUARE -> square(out, center, r, hollow, AXIS_Y);
            case LINE -> squareLine(out, center, r, band, h, hollow, AXIS_Y);
            case SPHERE -> sphere(out, center, r, hollow);
            // Size = half-width (X), thickness = half-depth (Z), height = full tallness (Y).
            case CUBE -> box(out, center, r, band, Math.max(1, h), hollow);
            case CYLINDER -> cylinder(out, center, r, h, hollow);
            case DOME -> dome(out, center, r, h, hollow);
            case PYRAMID -> pyramid(out, center, r, h, hollow);
        }
        orient(out, center, clampPlane(axis), clampYaw(rotation));
        return out;
    }

    /**
     * Map local Y (height) onto the chosen world axis, then yaw around world Y.
     * That way a tipped pyramid’s tip swings N/E/S/W instead of rolling around the tip.
     * Axis ids: Y={@link #AXIS_Y}, Z={@link #AXIS_Z}, X={@link #AXIS_X}.
     */
    private static void orient(List<BlockPos> out, BlockPos center, int axis, int rotation) {
        if (out.isEmpty() || (axis == AXIS_Y && rotation == 0)) {
            return;
        }
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        for (int i = 0; i < out.size(); i++) {
            BlockPos pos = out.get(i);
            int x = pos.getX() - cx;
            int y = pos.getY() - cy;
            int z = pos.getZ() - cz;
            int wx;
            int wy;
            int wz;
            switch (axis) {
                case AXIS_Z -> { // local Y -> world Z  (x,y,z) -> (x,z,y)
                    wx = x;
                    wy = z;
                    wz = y;
                }
                case AXIS_X -> { // local Y -> world X  (x,y,z) -> (y,x,z)
                    wx = y;
                    wy = x;
                    wz = z;
                }
                default -> {
                    wx = x;
                    wy = y;
                    wz = z;
                }
            }
            // Yaw around world up so sideways tips turn through the cardinals.
            for (int t = 0; t < rotation; t++) {
                int nextX = wz;
                int nextZ = -wx;
                wx = nextX;
                wz = nextZ;
            }
            out.set(i, new BlockPos(cx + wx, cy + wy, cz + wz));
        }
    }

    /**
     * Pivot on the shape AABB: {@code face * 9 + slot} for a face’s 3×3 (corners, edge mids, face center),
     * or {@link #PIVOT_CENTER} for the middle of the whole box. Faces match {@link Direction#get3DDataValue()}.
     */
    public static final int PIVOT_CENTER = 54;
    public static final int PIVOT_COUNT = 55;

    public static int clampPivot(int pivot) {
        return Mth.clamp(pivot, 0, PIVOT_CENTER);
    }

    public static int pivotFace(int pivot) {
        int id = clampPivot(pivot);
        return id >= PIVOT_CENTER ? -1 : id / 9;
    }

    public static int pivotSlot(int pivot) {
        int id = clampPivot(pivot);
        return id >= PIVOT_CENTER ? -1 : id % 9;
    }

    public static int pivotId(int face, int slot) {
        return Mth.clamp(face, 0, 5) * 9 + Mth.clamp(slot, 0, 8);
    }

    /** Inclusive AABB max for a non-empty block list. */
    public static BlockPos extent(List<BlockPos> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return BlockPos.ZERO;
        }
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : worlds) {
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    /**
     * Block on the shape AABB that pins to the world target.
     * Slot UV: u = (slot % 3) / 2, v = (slot / 3) / 2 → corners, edge mids, and face center.
     */
    public static BlockPos pivotBlock(BlockPos min, BlockPos max, int pivot) {
        if (min == null || max == null) {
            return BlockPos.ZERO;
        }
        int id = clampPivot(pivot);
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());
        if (id >= PIVOT_CENTER) {
            return new BlockPos((minX + maxX) >> 1, (minY + maxY) >> 1, (minZ + maxZ) >> 1);
        }
        int face = id / 9;
        int slot = id % 9;
        float u = (slot % 3) * 0.5F;
        float v = (slot / 3) * 0.5F;
        int alongU = Math.round(u * (maxX - minX));
        int alongV = Math.round(v * (maxZ - minZ));
        int alongY = Math.round(v * (maxY - minY));
        int alongZU = Math.round(u * (maxZ - minZ));
        return switch (face) {
            case 1 -> new BlockPos(minX + alongU, maxY, minZ + alongV); // UP
            case 2 -> new BlockPos(minX + alongU, minY + alongY, minZ); // NORTH
            case 3 -> new BlockPos(minX + alongU, minY + alongY, maxZ); // SOUTH
            case 4 -> new BlockPos(minX, minY + alongY, minZ + alongZU); // WEST
            case 5 -> new BlockPos(maxX, minY + alongY, minZ + alongZU); // EAST
            default -> new BlockPos(minX + alongU, minY, minZ + alongV); // DOWN
        };
    }

    /**
     * World positions with the chosen AABB pivot pinned to {@code worldTarget}.
     * Used by both look-at and on-anchor placement.
     */
    public static List<BlockPos> blocksPinned(
            Kind kind,
            BlockPos worldTarget,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int pivot) {
        List<BlockPos> local = blocks(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, rotation);
        if (local.isEmpty() || worldTarget == null) {
            return local;
        }
        BlockPos min = origin(local);
        BlockPos max = extent(local);
        BlockPos pin = pivotBlock(min, max, pivot);
        int dx = worldTarget.getX() - pin.getX();
        int dy = worldTarget.getY() - pin.getY();
        int dz = worldTarget.getZ() - pin.getZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return local;
        }
        List<BlockPos> out = new ArrayList<>(local.size());
        for (BlockPos pos : local) {
            out.add(pos.offset(dx, dy, dz));
        }
        return out;
    }

    /** @deprecated prefer {@link #blocksPinned} with a pivot */
    @Deprecated
    public static List<BlockPos> blocksFromCorner(Kind kind, BlockPos corner, int size, int height, boolean hollow, int axis) {
        return blocksFromCorner(kind, corner, size, height, 1, hollow, axis, 0);
    }

    /** @deprecated prefer {@link #blocksPinned} with a pivot */
    @Deprecated
    public static List<BlockPos> blocksFromCorner(Kind kind, BlockPos corner, int size, int height, int thickness, boolean hollow, int axis) {
        return blocksFromCorner(kind, corner, size, height, thickness, hollow, axis, 0);
    }

    /** @deprecated prefer {@link #blocksPinned} with a pivot */
    @Deprecated
    public static List<BlockPos> blocksFromCorner(Kind kind, BlockPos corner, int size, int height, int thickness, boolean hollow, int axis, int rotation) {
        // Min corner of the AABB = DOWN face, slot 0 (u=0,v=0).
        return blocksPinned(kind, corner, size, height, thickness, hollow, axis, rotation, pivotId(0, 0));
    }

    /** Origin for a wright schematic that covers every offset. */
    public static BlockPos origin(List<BlockPos> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return BlockPos.ZERO;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : worlds) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static void disk(List<BlockPos> out, BlockPos center, int radius, boolean hollow, int plane, boolean filledOnly) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                int d2 = a * a + b * b;
                if (d2 > r2) {
                    continue;
                }
                if (hollow && !filledOnly && d2 < inner2) {
                    continue;
                }
                out.add(flat(center, a, b, plane));
            }
        }
    }

    /**
     * Flat annulus when {@code height} is 1; otherwise a torus (true donut) with elliptical tube
     * cross-section. {@code thickness} is the tube width in the ring plane; {@code height} is the
     * tube diameter along the plane normal.
     */
    private static void ring(List<BlockPos> out, BlockPos center, int radius, int thickness, int height, int plane, boolean hollow) {
        int band = Mth.clamp(thickness, 1, radius);
        int tall = Math.max(1, height);
        if (tall <= 1) {
            int inner = Math.max(0, radius - band);
            int r2 = radius * radius;
            int inner2 = inner * inner;
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    int d2 = a * a + b * b;
                    if (d2 > r2 || d2 < inner2) {
                        continue;
                    }
                    out.add(ringCell(center, a, b, 0, plane));
                }
            }
            return;
        }
        // Major radius to tube centerline; minor radii for elliptical tube section.
        double rx = band * 0.5D;
        double ry = tall * 0.5D;
        double major = Math.max(rx, radius - rx);
        int alongMax = (tall - 1) / 2;
        int alongMin = -(tall / 2);
        // Shell thickness in ellipse-parameter space (~one block of the larger minor axis).
        double shell = hollow ? Math.max(0.35D, 1.0D / Math.max(rx, ry)) : 0.0D;
        for (int along = alongMin; along <= alongMax; along++) {
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    double dist = Math.sqrt((double) a * a + (double) b * b);
                    double da = dist - major;
                    double nx = da / rx;
                    double ny = along / ry;
                    double q = nx * nx + ny * ny;
                    if (q > 1.0D) {
                        continue;
                    }
                    if (hollow && q < (1.0D - shell) * (1.0D - shell)) {
                        continue;
                    }
                    out.add(ringCell(center, a, b, along, plane));
                }
            }
        }
    }

    /** Ring/torus in the chosen plane; {@code along} is offset on the plane normal. */
    private static BlockPos ringCell(BlockPos center, int a, int b, int along, int plane) {
        return switch (plane) {
            case PLANE_XY -> center.offset(a, b, along);
            case PLANE_YZ -> center.offset(along, a, b);
            default -> center.offset(a, along, b);
        };
    }

    private static void square(List<BlockPos> out, BlockPos center, int radius, boolean hollow, int plane) {
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                boolean edge = Math.abs(a) == radius || Math.abs(b) == radius;
                if (hollow && !edge) {
                    continue;
                }
                out.add(flat(center, a, b, plane));
            }
        }
    }

    private static void line(List<BlockPos> out, BlockPos center, int radius, int plane) {
        for (int i = -radius; i <= radius; i++) {
            out.add(flat(center, i, 0, plane));
        }
    }

    /**
     * Rectangular beam: {@code length} is half-length along the plane’s main axis,
     * {@code thickness} is width in the plane, {@code height} is the other cross-section size
     * (on XZ, thickness is Z and height is Y).
     */
    private static void squareLine(List<BlockPos> out, BlockPos center, int length, int thickness, int height, boolean hollow, int plane) {
        int half = Math.max(1, length);
        int wide = Math.max(1, thickness);
        int tall = Math.max(1, height);
        int v0 = -(wide - 1) / 2;
        int v1 = v0 + wide - 1;
        int u0 = -(tall - 1) / 2;
        int u1 = u0 + tall - 1;
        for (int i = -half; i <= half; i++) {
            for (int u = u0; u <= u1; u++) {
                for (int v = v0; v <= v1; v++) {
                    if (hollow && (wide > 2 || tall > 2)) {
                        boolean shell = u == u0 || u == u1 || v == v0 || v == v1;
                        if (!shell) {
                            continue;
                        }
                    }
                    out.add(beamCell(center, i, u, v, plane));
                }
            }
        }
    }

    /** Beam along the plane’s main axis; {@code u}/{@code v} are the rectangular cross-section. */
    private static BlockPos beamCell(BlockPos origin, int along, int u, int v, int plane) {
        return switch (plane) {
            case PLANE_XY -> origin.offset(along, u, v);
            case PLANE_YZ -> origin.offset(v, along, u);
            default -> origin.offset(along, u, v);
        };
    }

    private static void sphere(List<BlockPos> out, BlockPos center, int radius, boolean hollow) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > r2) {
                        continue;
                    }
                    if (hollow && d2 < inner2) {
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    /**
     * Rectangular box: {@code halfX}/{@code halfZ} are half-extents on local X/Z,
     * {@code height} is full block count upward from the base (1 = flat plate).
     */
    private static void box(List<BlockPos> out, BlockPos center, int halfX, int halfZ, int height, boolean hollow) {
        int rx = Math.max(0, halfX);
        int rz = Math.max(0, halfZ);
        int top = Math.max(0, height - 1);
        for (int x = -rx; x <= rx; x++) {
            for (int y = 0; y <= top; y++) {
                for (int z = -rz; z <= rz; z++) {
                    boolean shell = Math.abs(x) == rx || Math.abs(z) == rz || y == 0 || y == top;
                    if (hollow && !shell) {
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    private static void cylinder(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int r2 = radius * radius;
        int inner = hollow ? Math.max(0, radius - 1) : -1;
        int inner2 = inner * inner;
        int top = Math.max(0, height - 1);
        for (int y = 0; y <= top; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + z * z;
                    if (d2 > r2) {
                        continue;
                    }
                    boolean shell = d2 >= inner2;
                    boolean cap = y == 0 || y == top;
                    if (hollow && !shell && !cap) {
                        continue;
                    }
                    if (hollow && !shell && cap) {
                        // Hollow keeps the rim on caps, not a filled lid.
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    /** Hemisphere ellipsoid: Size is the floor radius, Height is how tall the dome rises. */
    private static void dome(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int tall = Math.max(1, height);
        long rr = (long) radius * radius;
        long hh = (long) tall * tall;
        long limit = rr * hh;
        int innerR = hollow ? Math.max(0, radius - 1) : -1;
        int innerH = hollow ? Math.max(0, tall - 1) : -1;
        long innerLimit = hollow ? (long) innerR * innerR * innerH * innerH : -1L;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= tall; y++) {
                for (int z = -radius; z <= radius; z++) {
                    long d = (long) x * x * hh + (long) z * z * hh + (long) y * y * rr;
                    if (d > limit) {
                        continue;
                    }
                    if (hollow && innerR > 0 && innerH > 0) {
                        long inner = (long) x * x * (long) innerH * innerH
                                + (long) z * z * (long) innerH * innerH
                                + (long) y * y * (long) innerR * innerR;
                        if (inner < innerLimit) {
                            continue;
                        }
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    private static void pyramid(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow) {
        int top = Math.max(1, height);
        for (int y = 0; y < top; y++) {
            int layer = Math.max(0, radius - (radius * y) / top);
            for (int x = -layer; x <= layer; x++) {
                for (int z = -layer; z <= layer; z++) {
                    boolean edge = Math.abs(x) == layer || Math.abs(z) == layer || y == 0 || y == top - 1;
                    if (hollow && !edge) {
                        continue;
                    }
                    out.add(center.offset(x, y, z));
                }
            }
        }
    }

    private static BlockPos flat(BlockPos center, int a, int b, int plane) {
        return switch (plane) {
            case PLANE_XY -> center.offset(a, b, 0);
            case PLANE_YZ -> center.offset(0, a, b);
            default -> center.offset(a, 0, b);
        };
    }

    /**
     * Anchor so a player at {@code feet} ends standing on the center of the shape’s top face
     * after the build (feet on the block above that top cell).
     */
    public static BlockPos anchorStandingOnTop(
            Kind kind,
            BlockPos feet,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int pivot) {
        if (feet == null) {
            return BlockPos.ZERO;
        }
        List<BlockPos> local = blocks(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, rotation);
        if (local.isEmpty()) {
            return feet.below();
        }
        BlockPos min = origin(local);
        BlockPos max = extent(local);
        BlockPos pin = pivotBlock(min, max, pivot);
        int topCenterX = (min.getX() + max.getX()) >> 1;
        int topCenterZ = (min.getZ() + max.getZ()) >> 1;
        // Place so the chosen pivot lands on the returned anchor, and the top sits under the player's feet.
        return new BlockPos(
                feet.getX() - (topCenterX - pin.getX()),
                feet.getY() - 1 - (max.getY() - pin.getY()),
                feet.getZ() - (topCenterZ - pin.getZ()));
    }

    /**
     * Aim cell for look-at builds: the block next to the clicked face.
     * That cell is where the shape’s chosen pivot is pinned.
     */
    public static BlockPos lookCorner(BlockPos clicked, Direction face) {
        if (clicked == null) {
            return null;
        }
        return face == null ? clicked.immutable() : clicked.relative(face);
    }

    /** @deprecated use {@link #lookCorner} */
    @Deprecated
    public static BlockPos lookCenter(BlockPos clicked, Direction face) {
        return lookCorner(clicked, face);
    }
}
