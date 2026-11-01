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
        /** Flat plate: Width × Height (full block counts). */
        SQUARE(false, true),
        /** Rectangular prism: Width × Depth × Height (full block counts). */
        BOX(true, true),
        SPHERE(true, false),
        /** Equal edge on all sides — only Size is used. */
        CUBE(true, false),
        CYLINDER(true, true),
        DOME(true, true),
        PYRAMID(true, true),
        /** Flat equal sides — only Size is used (like Cube, but 2D). */
        RECTANGLE(false, false);

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
     * Exchange / Place form presets. Each picks a Kind and Axis so Wall/Floor/Pillar/Beam
     * are oriented without the Shape orientation controls.
     * Floor / Wall are plates: equal-sides uses a single Size; otherwise Width × Height.
     * Pillar = vertical 1×1 post; Beam = horizontal 1-thick line across your look.
     * Stairs (Place Look) toggles rising/descending steps — not shown on the Exchange page.
     * Legacy id 6 (square) maps to {@link #FLOOR}. Id 5 is {@link #STAIRS}.
     */
    public enum ExchangeForm {
        FLOOR,
        WALL,
        PILLAR,
        BEAM,
        CIRCLE,
        STAIRS;

        public Kind kind() {
            return kind(false);
        }

        /** {@code equalSides} only affects Floor/Wall plates. */
        public Kind kind(boolean equalSides) {
            return switch (this) {
                case FLOOR, WALL, STAIRS -> equalSides ? Kind.RECTANGLE : Kind.SQUARE;
                case PILLAR, BEAM -> Kind.BOX;
                case CIRCLE -> Kind.CIRCLE;
            };
        }

        /** True for Floor / Wall — plates that can lock Width = Height. */
        public boolean plate() {
            return this == FLOOR || this == WALL;
        }

        /** True for the 1-block-thick line forms (vertical pillar / horizontal beam). */
        public boolean line() {
            return this == PILLAR || this == BEAM;
        }

        /** Place Look path form — continuous stairs under the player. */
        public boolean stairsPath() {
            return this == STAIRS;
        }

        public static ExchangeForm byId(int id) {
            // Old square (6) preset is Floor now. Id 5 is Stairs.
            if (id == 6) {
                return FLOOR;
            }
            ExchangeForm[] all = values();
            return all[Mth.clamp(id, 0, all.length - 1)];
        }

        public String langKey() {
            return "menu.wrights.exchange_form_" + name().toLowerCase();
        }
    }

    /** Resolve Exchange's configured form to a Kind (plates default to Width×Height). */
    public static Kind exchangeKind(int formId) {
        return ExchangeForm.byId(formId).kind(false);
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

    /** Inclusive AABB for a shape centered on {@code center}, without allocating every cell. */
    public record Bounds(BlockPos min, BlockPos max) {
        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }
    }

    /** World positions for one shape centered on {@code center}. */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, boolean hollow, int axis) {
        return blocks(kind, center, size, height, 1, hollow, axis, 0, Integer.MAX_VALUE);
    }

    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int axis) {
        return blocks(kind, center, size, height, thickness, hollow, axis, 0, Integer.MAX_VALUE);
    }

    /**
     * @param height box / cylinder / pyramid / dome height (box height 1 = flat plate); for rings, tube diameter (1 = flat)
     * @param thickness ring band width; line beam width; cube full depth in blocks on local Z
     * @param axis height axis: {@link #AXIS_Y}, {@link #AXIS_Z}, or {@link #AXIS_X}
     * @param rotation quarter-turns around world Y after axis remap (0–3), Ctrl+scroll in the world
     */
    public static List<BlockPos> blocks(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int axis, int rotation) {
        return blocks(kind, center, size, height, thickness, hollow, axis, rotation, Integer.MAX_VALUE);
    }

    /**
     * Same as {@link #blocks(Kind, BlockPos, int, int, int, boolean, int, int)} but stops once
     * {@code maxCells} positions are collected (never allocate a multi-million list).
     */
    public static List<BlockPos> blocks(
            Kind kind,
            BlockPos center,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int maxCells) {
        int limit = maxCells <= 0 ? Integer.MAX_VALUE : maxCells;
        List<BlockPos> out = new ArrayList<>(Math.min(1024, limit < Integer.MAX_VALUE ? limit : 1024));
        if (center == null || kind == null || limit == 0) {
            return out;
        }
        int r = clampSize(size);
        int h = clampHeight(height);
        int band = clampHeight(thickness);
        // Always build Y-up / XZ cross-section, then orient with axis + rotation.
        switch (kind) {
            // Size is diameter (full width in blocks), not radius.
            case CIRCLE -> diskDiameter(out, center, r, hollow, AXIS_Y, limit);
            case RING -> ring(out, center, r, band, h, AXIS_Y, hollow, limit);
            // Width × Height flat plate (local X × local Z, one block thick).
            case SQUARE -> box(out, center, r, Math.max(1, h), 1, hollow, limit);
            // Size / thickness / height are full block counts: Width × Depth × Height.
            case BOX -> box(out, center, r, band, Math.max(1, h), hollow, limit);
            case SPHERE -> sphere(out, center, r, hollow, limit);
            // Equal edge — Size alone sets Width = Depth = Height.
            case CUBE -> box(out, center, r, r, r, hollow, limit);
            case CYLINDER -> cylinder(out, center, r, h, hollow, limit);
            case DOME -> dome(out, center, r, h, hollow, limit);
            case PYRAMID -> pyramid(out, center, r, h, hollow, limit);
            // Equal edge flat — Size alone sets Width = Height.
            case RECTANGLE -> box(out, center, r, r, 1, hollow, limit);
        }
        orient(out, center, clampPlane(axis), clampYaw(rotation));
        return out;
    }

    /**
     * Upper bound on nested-loop visits for this shape (AABB volume). Used to skip materializing
     * huge lists for preview — skip walking millions of cells just to draw a cue.
     */
    public static long scanVolume(Kind kind, int size, int height, int thickness) {
        if (kind == null) {
            return 0L;
        }
        int r = clampSize(size);
        int h = Math.max(1, clampHeight(height));
        int band = Math.max(1, clampHeight(thickness));
        long diam = 2L * r + 1L;
        return switch (kind) {
            case CIRCLE -> (long) r * (long) r;
            case SQUARE -> (long) r * (long) h;
            case RECTANGLE -> (long) r * (long) r;
            case RING -> {
                int tall = Math.max(1, h);
                long along = tall <= 1 ? 1L : (long) tall;
                yield diam * diam * along;
            }
            case BOX -> (long) r * (long) band * (long) h;
            case SPHERE -> diam * diam * diam;
            case CUBE -> (long) r * (long) r * (long) r;
            case CYLINDER, DOME -> diam * diam * (long) h;
            case PYRAMID -> diam * diam * (long) h;
        };
    }

    /** O(1) AABB for the shape at {@code center} after axis/yaw, without listing cells. */
    public static Bounds bounds(Kind kind, BlockPos center, int size, int height, int thickness, boolean hollow, int axis, int rotation) {
        if (center == null || kind == null) {
            return new Bounds(BlockPos.ZERO, BlockPos.ZERO);
        }
        int r = clampSize(size);
        int h = Math.max(1, clampHeight(height));
        int band = Math.max(1, clampHeight(thickness));
        int minX;
        int minY;
        int minZ;
        int maxX;
        int maxY;
        int maxZ;
        switch (kind) {
            case CIRCLE -> {
                // Size is diameter (full block width).
                int a0 = -(r - 1) / 2;
                minX = a0;
                maxX = a0 + r - 1;
                minY = 0;
                maxY = 0;
                minZ = a0;
                maxZ = a0 + r - 1;
            }
            case SQUARE -> {
                int x0 = -(r - 1) / 2;
                int z0 = -(h - 1) / 2;
                minX = x0;
                maxX = x0 + r - 1;
                minY = 0;
                maxY = 0;
                minZ = z0;
                maxZ = z0 + h - 1;
            }
            case RECTANGLE -> {
                int x0 = -(r - 1) / 2;
                minX = x0;
                maxX = x0 + r - 1;
                minY = 0;
                maxY = 0;
                minZ = x0;
                maxZ = x0 + r - 1;
            }
            case RING -> {
                minX = -r;
                maxX = r;
                minZ = -r;
                maxZ = r;
                if (h <= 1) {
                    minY = 0;
                    maxY = 0;
                } else {
                    minY = -(h / 2);
                    maxY = (h - 1) / 2;
                }
            }
            case BOX -> {
                int x0 = -(r - 1) / 2;
                int z0 = -(band - 1) / 2;
                minX = x0;
                maxX = x0 + r - 1;
                minY = 0;
                maxY = h - 1;
                minZ = z0;
                maxZ = z0 + band - 1;
            }
            case SPHERE -> {
                minX = -r;
                maxX = r;
                minY = -r;
                maxY = r;
                minZ = -r;
                maxZ = r;
            }
            case CUBE -> {
                int x0 = -(r - 1) / 2;
                minX = x0;
                maxX = x0 + r - 1;
                minY = 0;
                maxY = r - 1;
                minZ = x0;
                maxZ = x0 + r - 1;
            }
            case CYLINDER, PYRAMID -> {
                minX = -r;
                maxX = r;
                minY = 0;
                maxY = h - 1;
                minZ = -r;
                maxZ = r;
            }
            case DOME -> {
                minX = -r;
                maxX = r;
                minY = 0;
                maxY = h;
                minZ = -r;
                maxZ = r;
            }
            default -> {
                minX = maxX = minY = maxY = minZ = maxZ = 0;
            }
        }
        int ax = clampPlane(axis);
        int yaw = clampYaw(rotation);
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int outMinX = Integer.MAX_VALUE;
        int outMinY = Integer.MAX_VALUE;
        int outMinZ = Integer.MAX_VALUE;
        int outMaxX = Integer.MIN_VALUE;
        int outMaxY = Integer.MIN_VALUE;
        int outMaxZ = Integer.MIN_VALUE;
        int[] xs = {minX, maxX};
        int[] ys = {minY, maxY};
        int[] zs = {minZ, maxZ};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    int wx = x;
                    int wy = y;
                    int wz = z;
                    switch (ax) {
                        case AXIS_Z -> {
                            wx = x;
                            wy = z;
                            wz = y;
                        }
                        case AXIS_X -> {
                            wx = y;
                            wy = x;
                            wz = z;
                        }
                        default -> {
                        }
                    }
                    for (int t = 0; t < yaw; t++) {
                        int nextX = wz;
                        int nextZ = -wx;
                        wx = nextX;
                        wz = nextZ;
                    }
                    int px = cx + wx;
                    int py = cy + wy;
                    int pz = cz + wz;
                    outMinX = Math.min(outMinX, px);
                    outMinY = Math.min(outMinY, py);
                    outMinZ = Math.min(outMinZ, pz);
                    outMaxX = Math.max(outMaxX, px);
                    outMaxY = Math.max(outMaxY, py);
                    outMaxZ = Math.max(outMaxZ, pz);
                }
            }
        }
        return new Bounds(new BlockPos(outMinX, outMinY, outMinZ), new BlockPos(outMaxX, outMaxY, outMaxZ));
    }

    private static boolean take(List<BlockPos> out, BlockPos pos, int limit) {
        if (out.size() >= limit) {
            return false;
        }
        out.add(pos);
        return true;
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

    /**
     * Collapse a stored 3×3 slot to a line index 0/1/2 (negative end, middle, positive end).
     * Line forms only have three distinct pin cells.
     */
    public static int exchangeLineIndex(int slot) {
        int s = Mth.clamp(slot, 0, 8);
        if (s <= 2) {
            return s;
        }
        if (s == 4) {
            return 1;
        }
        if (s == 3 || s == 6) {
            return 0;
        }
        if (s == 5 || s == 7 || s == 8) {
            return 2;
        }
        return 1;
    }

    /**
     * Map Exchange’s pivot control onto the form’s pin cell.
     * Floor / Circle: full bottom 3×3. Wall: broad vertical face (UI top = wall top).
     * Pillar / Beam: three points along the length (bottom/mid/top or min/mid/max).
     */
    public static int exchangePivotId(ExchangeForm form, int plane, int slot, int size, int thickness) {
        int s = Mth.clamp(slot, 0, 8);
        ExchangeForm kind = form == null ? ExchangeForm.FLOOR : form;
        return switch (kind) {
            case WALL -> {
                int face = clampPlane(plane) == AXIS_X ? 5 /* EAST */ : 2 /* NORTH */;
                int col = s % 3;
                int row = s / 3;
                yield pivotId(face, (2 - row) * 3 + col);
            }
            case PILLAR -> switch (exchangeLineIndex(s)) {
                case 0 -> pivotId(0, 4); // bottom
                case 2 -> pivotId(1, 4); // top
                default -> PIVOT_CENTER; // middle
            };
            case BEAM -> {
                int i = exchangeLineIndex(s);
                // Length sits on X or Z after {@code applyExchangeForm}; thin axis stays 1.
                boolean lengthOnX = clampSize(size) >= clampHeight(thickness);
                yield switch (i) {
                    case 0 -> pivotId(0, lengthOnX ? 3 : 1); // W or N end
                    case 2 -> pivotId(0, lengthOnX ? 5 : 7); // E or S end
                    default -> pivotId(0, 4); // center
                };
            }
            default -> pivotId(0, s); // FLOOR, CIRCLE, STAIRS — bottom footprint
        };
    }

    /** @deprecated prefer {@link #exchangePivotId(ExchangeForm, int, int, int, int)} */
    @Deprecated
    public static int exchangePivotId(ExchangeForm form, int plane, int slot) {
        return exchangePivotId(form, plane, slot, 1, 1);
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
     * Axis tips the shape first; {@code rotation} then yaws the whole footprint around the pin
     * (Ctrl+scroll), so the bounding box orbits that cell like a blueprint anchor.
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
        return blocksPinned(kind, worldTarget, size, height, thickness, hollow, axis, rotation, pivot, Integer.MAX_VALUE);
    }

    public static List<BlockPos> blocksPinned(
            Kind kind,
            BlockPos worldTarget,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int pivot,
            int maxCells) {
        // Build axis-only (yaw 0), pin, then orbit yaw around the pin — not around the center.
        List<BlockPos> local = blocks(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0, maxCells);
        if (local.isEmpty()) {
            return local;
        }
        Bounds box = bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0);
        BlockPos pinLocal = pivotBlock(box.min(), box.max(), pivot);
        int yaw = clampYaw(rotation);
        int px = worldTarget == null ? 0 : worldTarget.getX();
        int py = worldTarget == null ? 0 : worldTarget.getY();
        int pz = worldTarget == null ? 0 : worldTarget.getZ();
        for (int i = 0; i < local.size(); i++) {
            BlockPos cell = local.get(i);
            int rx = cell.getX() - pinLocal.getX();
            int ry = cell.getY() - pinLocal.getY();
            int rz = cell.getZ() - pinLocal.getZ();
            long turned = yawAroundY(rx, rz, yaw);
            local.set(i, new BlockPos(px + turnedX(turned), py + ry, pz + turnedZ(turned)));
        }
        return local;
    }

    /** Pack xz after {@link #yawAroundY} — high 32 = x, low 32 = z. */
    private static int turnedX(long packed) {
        return (int) (packed >> 32);
    }

    private static int turnedZ(long packed) {
        return (int) packed;
    }

    /** Quarter-turns of (x,z) around Y. Positive yaw matches {@link #orient} (x,z)→(z,-x). */
    private static long yawAroundY(int x, int z, int yaw) {
        int wx = x;
        int wz = z;
        int turns = clampYaw(yaw);
        for (int t = 0; t < turns; t++) {
            int nextX = wz;
            int nextZ = -wx;
            wx = nextX;
            wz = nextZ;
        }
        return (((long) wx) << 32) | (wz & 0xFFFFFFFFL);
    }

    /** Inverse of {@link #yawAroundY}. */
    private static long yawAroundYInverse(int x, int z, int yaw) {
        int wx = x;
        int wz = z;
        int turns = clampYaw(yaw);
        for (int t = 0; t < turns; t++) {
            int nextX = -wz;
            int nextZ = wx;
            wx = nextX;
            wz = nextZ;
        }
        return (((long) wx) << 32) | (wz & 0xFFFFFFFFL);
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

    /**
     * Flat disk whose {@code diameter} is the full width in blocks (not radius).
     * Works for odd and even sizes; center sits on a block (odd) or between blocks (even).
     */
    private static void diskDiameter(List<BlockPos> out, BlockPos center, int diameter, boolean hollow, int plane, int limit) {
        int d = Math.max(1, diameter);
        int a0 = -(d - 1) / 2;
        int b0 = -(d - 1) / 2;
        double mid = (d - 1) * 0.5D;
        double maxR2 = (d * 0.5D) * (d * 0.5D);
        double innerR2 = hollow && d > 2 ? ((d - 2) * 0.5D) * ((d - 2) * 0.5D) : -1.0D;
        for (int ia = 0; ia < d; ia++) {
            for (int ib = 0; ib < d; ib++) {
                double da = ia - mid;
                double db = ib - mid;
                double dist2 = da * da + db * db;
                if (dist2 > maxR2 + 1.0E-6D) {
                    continue;
                }
                if (innerR2 >= 0.0D && dist2 < innerR2 - 1.0E-6D) {
                    continue;
                }
                if (!take(out, flat(center, a0 + ia, b0 + ib, plane), limit)) {
                    return;
                }
            }
        }
    }

    /**
     * Flat annulus when {@code height} is 1; otherwise a torus (true donut) with elliptical tube
     * cross-section. {@code thickness} is the tube width in the ring plane; {@code height} is the
     * tube diameter along the plane normal.
     */
    private static void ring(List<BlockPos> out, BlockPos center, int radius, int thickness, int height, int plane, boolean hollow, int limit) {
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
                    if (!take(out, ringCell(center, a, b, 0, plane), limit)) {
                        return;
                    }
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
                    if (!take(out, ringCell(center, a, b, along, plane), limit)) {
                        return;
                    }
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

    private static void sphere(List<BlockPos> out, BlockPos center, int radius, boolean hollow, int limit) {
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
                    if (!take(out, center.offset(x, y, z), limit)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Rectangular box: {@code width}/{@code depth}/{@code height} are full block counts.
     * Centered on X/Z; grows upward on Y from the base (height 1 = flat plate).
     */
    private static void box(List<BlockPos> out, BlockPos center, int width, int depth, int height, boolean hollow, int limit) {
        int w = Math.max(1, width);
        int d = Math.max(1, depth);
        int h = Math.max(1, height);
        int x0 = -(w - 1) / 2;
        int x1 = x0 + w - 1;
        int z0 = -(d - 1) / 2;
        int z1 = z0 + d - 1;
        int top = h - 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= top; y++) {
                for (int z = z0; z <= z1; z++) {
                    boolean shell = x == x0 || x == x1 || z == z0 || z == z1 || y == 0 || y == top;
                    if (hollow && !shell) {
                        continue;
                    }
                    if (!take(out, center.offset(x, y, z), limit)) {
                        return;
                    }
                }
            }
        }
    }

    private static void cylinder(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow, int limit) {
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
                    if (!take(out, center.offset(x, y, z), limit)) {
                        return;
                    }
                }
            }
        }
    }

    /** Hemisphere ellipsoid: Size is the floor radius, Height is how tall the dome rises. */
    private static void dome(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow, int limit) {
        int tall = Math.max(1, height);
        long rr = (long) radius * radius;
        long hh = (long) tall * tall;
        long ellipse = rr * hh;
        int innerR = hollow ? Math.max(0, radius - 1) : -1;
        int innerH = hollow ? Math.max(0, tall - 1) : -1;
        long innerLimit = hollow ? (long) innerR * innerR * innerH * innerH : -1L;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= tall; y++) {
                for (int z = -radius; z <= radius; z++) {
                    long d = (long) x * x * hh + (long) z * z * hh + (long) y * y * rr;
                    if (d > ellipse) {
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
                    if (!take(out, center.offset(x, y, z), limit)) {
                        return;
                    }
                }
            }
        }
    }

    private static void pyramid(List<BlockPos> out, BlockPos center, int radius, int height, boolean hollow, int limit) {
        int top = Math.max(1, height);
        for (int y = 0; y < top; y++) {
            int layer = Math.max(0, radius - (radius * y) / top);
            for (int x = -layer; x <= layer; x++) {
                for (int z = -layer; z <= layer; z++) {
                    boolean edge = Math.abs(x) == layer || Math.abs(z) == layer || y == 0 || y == top - 1;
                    if (hollow && !edge) {
                        continue;
                    }
                    if (!take(out, center.offset(x, y, z), limit)) {
                        return;
                    }
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
        // Same pin-orbit as placement: yaw swings the box around the anchor, then we solve for
        // the anchor that puts the finished top-center under the player's feet.
        Bounds local = bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0);
        BlockPos pinLocal = pivotBlock(local.min(), local.max(), pivot);
        int yaw = clampYaw(rotation);
        int outMinX = Integer.MAX_VALUE;
        int outMinY = Integer.MAX_VALUE;
        int outMinZ = Integer.MAX_VALUE;
        int outMaxX = Integer.MIN_VALUE;
        int outMaxY = Integer.MIN_VALUE;
        int outMaxZ = Integer.MIN_VALUE;
        int[] xs = {local.min().getX(), local.max().getX()};
        int[] ys = {local.min().getY(), local.max().getY()};
        int[] zs = {local.min().getZ(), local.max().getZ()};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    long turned = yawAroundY(x - pinLocal.getX(), z - pinLocal.getZ(), yaw);
                    int rx = turnedX(turned);
                    int ry = y - pinLocal.getY();
                    int rz = turnedZ(turned);
                    outMinX = Math.min(outMinX, rx);
                    outMinY = Math.min(outMinY, ry);
                    outMinZ = Math.min(outMinZ, rz);
                    outMaxX = Math.max(outMaxX, rx);
                    outMaxY = Math.max(outMaxY, ry);
                    outMaxZ = Math.max(outMaxZ, rz);
                }
            }
        }
        int topCenterX = (outMinX + outMaxX) >> 1;
        int topCenterZ = (outMinZ + outMaxZ) >> 1;
        return new BlockPos(
                feet.getX() - topCenterX,
                feet.getY() - 1 - outMaxY,
                feet.getZ() - topCenterZ);
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

    /**
     * World-space AABB after pinning {@code worldTarget} to the chosen pivot, then yawing
     * around that pin. No cell list — streaming placement uses this + {@link #containsWorld}.
     */
    public static Bounds pinnedBounds(
            Kind kind,
            BlockPos worldTarget,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int pivot) {
        Bounds local = bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0);
        BlockPos pinLocal = pivotBlock(local.min(), local.max(), pivot);
        int yaw = clampYaw(rotation);
        int px = worldTarget == null ? 0 : worldTarget.getX();
        int py = worldTarget == null ? 0 : worldTarget.getY();
        int pz = worldTarget == null ? 0 : worldTarget.getZ();
        int outMinX = Integer.MAX_VALUE;
        int outMinY = Integer.MAX_VALUE;
        int outMinZ = Integer.MAX_VALUE;
        int outMaxX = Integer.MIN_VALUE;
        int outMaxY = Integer.MIN_VALUE;
        int outMaxZ = Integer.MIN_VALUE;
        int[] xs = {local.min().getX(), local.max().getX()};
        int[] ys = {local.min().getY(), local.max().getY()};
        int[] zs = {local.min().getZ(), local.max().getZ()};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    long turned = yawAroundY(x - pinLocal.getX(), z - pinLocal.getZ(), yaw);
                    int wx = px + turnedX(turned);
                    int wy = py + (y - pinLocal.getY());
                    int wz = pz + turnedZ(turned);
                    outMinX = Math.min(outMinX, wx);
                    outMinY = Math.min(outMinY, wy);
                    outMinZ = Math.min(outMinZ, wz);
                    outMaxX = Math.max(outMaxX, wx);
                    outMaxY = Math.max(outMaxY, wy);
                    outMaxZ = Math.max(outMaxZ, wz);
                }
            }
        }
        return new Bounds(new BlockPos(outMinX, outMinY, outMinZ), new BlockPos(outMaxX, outMaxY, outMaxZ));
    }

    /**
     * World position of the local formula origin after pin (yaw 0). Membership applies yaw
     * around the pin ({@code center + pinLocal}) via {@link #containsWorld}.
     */
    public static BlockPos pinnedCenter(
            Kind kind,
            BlockPos worldTarget,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int pivot) {
        Bounds local = bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0);
        BlockPos pinLocal = pivotBlock(local.min(), local.max(), pivot);
        int dx = worldTarget == null ? 0 : worldTarget.getX() - pinLocal.getX();
        int dy = worldTarget == null ? 0 : worldTarget.getY() - pinLocal.getY();
        int dz = worldTarget == null ? 0 : worldTarget.getZ() - pinLocal.getZ();
        return new BlockPos(dx, dy, dz);
    }

    /**
     * True when {@code world} is a cell of the pinned shape (no allocation).
     * {@code center} is {@link #pinnedCenter}; yaw orbits the pin, not the formula origin.
     */
    public static boolean containsWorld(
            Kind kind,
            BlockPos world,
            BlockPos center,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation,
            int pivot) {
        if (kind == null || world == null || center == null) {
            return false;
        }
        Bounds local = bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0);
        return containsWorld(kind, world, center, pivotBlock(local.min(), local.max(), pivot), size, height, thickness, hollow, axis, rotation);
    }

    /**
     * Hot-path membership when {@code pinLocal} is already known (avoid recomputing bounds each cell).
     */
    public static boolean containsWorld(
            Kind kind,
            BlockPos world,
            BlockPos center,
            BlockPos pinLocal,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation) {
        if (kind == null || world == null || center == null || pinLocal == null) {
            return false;
        }
        int pinX = center.getX() + pinLocal.getX();
        int pinY = center.getY() + pinLocal.getY();
        int pinZ = center.getZ() + pinLocal.getZ();
        long unyaw = yawAroundYInverse(world.getX() - pinX, world.getZ() - pinZ, rotation);
        int ax = turnedX(unyaw) + pinLocal.getX();
        int ay = (world.getY() - pinY) + pinLocal.getY();
        int az = turnedZ(unyaw) + pinLocal.getZ();
        int lx;
        int ly;
        int lz;
        switch (clampPlane(axis)) {
            case AXIS_Z -> { // forward (x,y,z)->(x,z,y)
                lx = ax;
                ly = az;
                lz = ay;
            }
            case AXIS_X -> { // forward (x,y,z)->(y,x,z)
                lx = ay;
                ly = ax;
                lz = az;
            }
            default -> {
                lx = ax;
                ly = ay;
                lz = az;
            }
        }
        return containsLocal(kind, lx, ly, lz, size, height, thickness, hollow);
    }

    /** Local pivot cell for axis-only (yaw 0) bounds — cache this for streaming membership. */
    public static BlockPos pinLocal(
            Kind kind,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int pivot) {
        Bounds local = bounds(kind, BlockPos.ZERO, size, height, thickness, hollow, axis, 0);
        return pivotBlock(local.min(), local.max(), pivot);
    }

    /** @deprecated prefer {@link #containsWorld} with pivot */
    @Deprecated
    public static boolean containsWorld(
            Kind kind,
            BlockPos world,
            BlockPos center,
            int size,
            int height,
            int thickness,
            boolean hollow,
            int axis,
            int rotation) {
        return containsWorld(kind, world, center, size, height, thickness, hollow, axis, rotation, PIVOT_CENTER);
    }

    /** Membership in local Y-up space (same space as the private generators, before orient). */
    static boolean containsLocal(Kind kind, int x, int y, int z, int size, int height, int thickness, boolean hollow) {
        int r = clampSize(size);
        int h = Math.max(1, clampHeight(height));
        int band = Math.max(1, clampHeight(thickness));
        return switch (kind) {
            case CIRCLE -> inDiskDiameter(x, y, z, r, hollow);
            case RING -> inRing(x, y, z, r, band, h, hollow);
            case SQUARE -> inBox(x, y, z, r, Math.max(1, h), 1, hollow);
            case BOX -> inBox(x, y, z, r, band, Math.max(1, h), hollow);
            case SPHERE -> inSphere(x, y, z, r, hollow);
            case CUBE -> inBox(x, y, z, r, r, r, hollow);
            case CYLINDER -> inCylinder(x, y, z, r, h, hollow);
            case DOME -> inDome(x, y, z, r, h, hollow);
            case PYRAMID -> inPyramid(x, y, z, r, h, hollow);
            case RECTANGLE -> inBox(x, y, z, r, r, 1, hollow);
        };
    }

    private static boolean inDiskDiameter(int x, int y, int z, int diameter, boolean hollow) {
        if (y != 0) {
            return false;
        }
        int d = Math.max(1, diameter);
        int a0 = -(d - 1) / 2;
        int ia = x - a0;
        int ib = z - a0;
        if (ia < 0 || ia >= d || ib < 0 || ib >= d) {
            return false;
        }
        double mid = (d - 1) * 0.5D;
        double maxR2 = (d * 0.5D) * (d * 0.5D);
        double dist2 = (ia - mid) * (ia - mid) + (ib - mid) * (ib - mid);
        if (dist2 > maxR2 + 1.0E-6D) {
            return false;
        }
        if (hollow && d > 2) {
            double innerR2 = ((d - 2) * 0.5D) * ((d - 2) * 0.5D);
            return !(dist2 < innerR2 - 1.0E-6D);
        }
        return true;
    }

    private static boolean inRing(int x, int y, int z, int radius, int thickness, int height, boolean hollow) {
        int band = Mth.clamp(thickness, 1, radius);
        int tall = Math.max(1, height);
        if (tall <= 1) {
            if (y != 0) {
                return false;
            }
            int inner = Math.max(0, radius - band);
            int d2 = x * x + z * z;
            return d2 <= radius * radius && d2 >= inner * inner;
        }
        double rx = band * 0.5D;
        double ry = tall * 0.5D;
        double major = Math.max(rx, radius - rx);
        int alongMax = (tall - 1) / 2;
        int alongMin = -(tall / 2);
        if (y < alongMin || y > alongMax) {
            return false;
        }
        if (x < -radius || x > radius || z < -radius || z > radius) {
            return false;
        }
        double dist = Math.sqrt((double) x * x + (double) z * z);
        double da = dist - major;
        double nx = da / rx;
        double ny = y / ry;
        double q = nx * nx + ny * ny;
        if (q > 1.0D) {
            return false;
        }
        if (hollow) {
            double shell = Math.max(0.35D, 1.0D / Math.max(rx, ry));
            return !(q < (1.0D - shell) * (1.0D - shell));
        }
        return true;
    }

    private static boolean inSphere(int x, int y, int z, int radius, boolean hollow) {
        int d2 = x * x + y * y + z * z;
        if (d2 > radius * radius) {
            return false;
        }
        if (hollow) {
            int inner = Math.max(0, radius - 1);
            return d2 >= inner * inner;
        }
        return true;
    }

    private static boolean inBox(int x, int y, int z, int width, int depth, int height, boolean hollow) {
        int w = Math.max(1, width);
        int d = Math.max(1, depth);
        int h = Math.max(1, height);
        int x0 = -(w - 1) / 2;
        int x1 = x0 + w - 1;
        int z0 = -(d - 1) / 2;
        int z1 = z0 + d - 1;
        int top = h - 1;
        if (x < x0 || x > x1 || y < 0 || y > top || z < z0 || z > z1) {
            return false;
        }
        if (!hollow) {
            return true;
        }
        return x == x0 || x == x1 || z == z0 || z == z1 || y == 0 || y == top;
    }

    private static boolean inCylinder(int x, int y, int z, int radius, int height, boolean hollow) {
        int top = Math.max(0, height - 1);
        if (y < 0 || y > top) {
            return false;
        }
        int d2 = x * x + z * z;
        if (d2 > radius * radius) {
            return false;
        }
        if (!hollow) {
            return true;
        }
        int inner = Math.max(0, radius - 1);
        boolean shell = d2 >= inner * inner;
        boolean cap = y == 0 || y == top;
        if (shell) {
            return true;
        }
        // Hollow keeps the rim on caps, not a filled lid.
        return false;
    }

    private static boolean inDome(int x, int y, int z, int radius, int height, boolean hollow) {
        int tall = Math.max(1, height);
        if (y < 0 || y > tall) {
            return false;
        }
        long rr = (long) radius * radius;
        long hh = (long) tall * tall;
        long ellipse = rr * hh;
        long d = (long) x * x * hh + (long) z * z * hh + (long) y * y * rr;
        if (d > ellipse) {
            return false;
        }
        if (!hollow) {
            return true;
        }
        int innerR = Math.max(0, radius - 1);
        int innerH = Math.max(0, tall - 1);
        if (innerR <= 0 || innerH <= 0) {
            return true;
        }
        long innerLimit = (long) innerR * innerR * innerH * innerH;
        long inner = (long) x * x * (long) innerH * innerH
                + (long) z * z * (long) innerH * innerH
                + (long) y * y * (long) innerR * innerR;
        return inner >= innerLimit;
    }

    private static boolean inPyramid(int x, int y, int z, int radius, int height, boolean hollow) {
        int top = Math.max(1, height);
        if (y < 0 || y >= top) {
            return false;
        }
        int layer = Math.max(0, radius - (radius * y) / top);
        if (x < -layer || x > layer || z < -layer || z > layer) {
            return false;
        }
        if (!hollow) {
            return true;
        }
        return Math.abs(x) == layer || Math.abs(z) == layer || y == 0 || y == top - 1;
    }
}
