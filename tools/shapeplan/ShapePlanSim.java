import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Isolated stand-in for shape place planning. No Minecraft types.
 *
 * <p>Netherite default, matching {@code WrightTier} / {@code JobManager}:
 * one hover covers the 3×3×3 cube without its eight corners (19 cells,
 * squared distance ≤ 2), and at most 19 blocks leave that hover in one tick.
 * Crew default is 10. Flight matches netherite close speed 2.75 blocks/tick,
 * 2.1× past 8 blocks, and a hover is "arrived" inside 1.5 blocks.
 *
 * <p>Run from the repo root:
 * <pre>
 *   javac -d tools/shapeplan/out tools/shapeplan/ShapePlanSim.java
 *   java -cp tools/shapeplan/out ShapePlanSim
 * </pre>
 * The report is also written to {@code tools/shapeplan/results.txt}.
 */
public final class ShapePlanSim {
    static final int CAP = 19;
    static final int CREW = 10;
    static final double ARRIVE = 1.5;
    static final double CLOSE_SPEED = 2.75;
    static final double FAR_SPEED = 2.75 * 2.1;
    static final int PORT_GAP = 8;

    static final int[] BALL_X = new int[19];
    static final int[] BALL_Y = new int[19];
    static final int[] BALL_Z = new int[19];
    static int BALL = 0;

    /**
     * Centers are x + A*y + B*z ≡ 0 (mod M). Each residue picks one sphere offset, so every
     * block belongs to exactly one hover and that hover's blocks sit inside the 19-cell sphere.
     * M = 19 would be a perfect fill. No such form exists; the search keeps the M with the
     * best blocks-per-tick once the shortest hop's fly time is included.
     */
    static int FORM_A = -1;
    static int FORM_B = -1;
    static int FORM_M = -1;
    static int[] REP_X = new int[0];
    static int[] REP_Y = new int[0];
    static int[] REP_Z = new int[0];
    static int STEP_X;
    static int STEP_Y;
    static int STEP_Z;
    static int STEP_D2;

    static final List<Row> ROWS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        java.io.PrintStream file = new java.io.PrintStream(
                java.nio.file.Files.newOutputStream(java.nio.file.Path.of("tools/shapeplan/results.txt")),
                true,
                java.nio.charset.StandardCharsets.UTF_8);
        java.io.PrintStream out = new java.io.PrintStream(new Tee(System.out, file), true, java.nio.charset.StandardCharsets.UTF_8);
        try {
            buildBall(2);
            if (BALL != 19) {
                throw new IllegalStateException("ball " + BALL);
            }
            findForm();
            out.println("Shape place planner — isolated simulation");
            out.println("Sphere: squared distance <= 2, " + BALL + " cells (3x3x3 without corners). Cap " + CAP + ". Crew " + CREW + ".");
            out.println("Lattice: x + " + FORM_A + "*y + " + FORM_B + "*z ≡ 0 (mod " + FORM_M + ").");
            out.println("Shortest hop (" + STEP_X + "," + STEP_Y + "," + STEP_Z + ") dist "
                    + String.format(Locale.US, "%.3f", Math.sqrt(STEP_D2))
                    + ", fly ticks " + flyTicks(0, 0, 0, STEP_X, STEP_Y, STEP_Z) + ".");
            out.println();
            sectionFrontier(out);
            sectionTorus(out);
            sectionLegacy(out);
            List<Shape> zoo = zoo();
            sectionZoo(out, zoo);
            sectionTravel(out);
            sectionTicks(out);
            sectionCrewAoe(out);
            sectionScores(out);
            out.println();
            out.println("Done. Rows " + ROWS.size() + ".");
        } finally {
            out.flush();
            file.close();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ball, lattice, flight
    // ---------------------------------------------------------------------------------------------

    static void buildBall(int reach2) {
        BALL = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= reach2) {
                        BALL_X[BALL] = dx;
                        BALL_Y[BALL] = dy;
                        BALL_Z[BALL] = dz;
                        BALL++;
                    }
                }
            }
        }
    }

    static int ballCount(int reach2) {
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= reach2) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** True when the ball's offsets hit every residue of x + a*y + b*z mod m. */
    static boolean covers(int a, int b, int m) {
        boolean[] seen = new boolean[m];
        int got = 0;
        for (int i = 0; i < BALL; i++) {
            int h = Math.floorMod(BALL_X[i] + a * BALL_Y[i] + b * BALL_Z[i], m);
            if (!seen[h]) {
                seen[h] = true;
                got++;
            }
        }
        return got == m;
    }

    static int[] shortestStep(int a, int b, int m) {
        int best = Integer.MAX_VALUE;
        int bx = m;
        int by = 0;
        int bz = 0;
        for (int y = -m; y <= m; y++) {
            for (int z = -m; z <= m; z++) {
                for (int x = -m; x <= m; x++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (Math.floorMod(x + a * y + b * z, m) != 0) {
                        continue;
                    }
                    int d2 = x * x + y * y + z * z;
                    if (d2 < best) {
                        best = d2;
                        bx = x;
                        by = y;
                        bz = z;
                        if (best == 1) {
                            return new int[] {bx, by, bz, best};
                        }
                    }
                }
            }
        }
        return new int[] {bx, by, bz, best};
    }

    static void findForm() {
        double bestRate = -1;
        int bestM = 1;
        int bestA = 0;
        int bestB = 0;
        int[] bestStep = {1, 0, 0, 1};
        for (int m = 1; m <= 18; m++) {
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    if (!covers(a, b, m)) {
                        continue;
                    }
                    int[] step = shortestStep(a, b, m);
                    int fly = flyTicks(0, 0, 0, step[0], step[1], step[2]);
                    double rate = m / (double) (fly + 1);
                    boolean better = rate > bestRate + 1.0e-9
                            || (Math.abs(rate - bestRate) <= 1.0e-9 && (m > bestM || (m == bestM && step[3] < bestStep[3])));
                    if (better) {
                        bestRate = rate;
                        bestM = m;
                        bestA = a;
                        bestB = b;
                        bestStep = step;
                    }
                }
            }
        }
        FORM_A = bestA;
        FORM_B = bestB;
        FORM_M = bestM;
        STEP_X = bestStep[0];
        STEP_Y = bestStep[1];
        STEP_Z = bestStep[2];
        STEP_D2 = bestStep[3];
        REP_X = new int[FORM_M];
        REP_Y = new int[FORM_M];
        REP_Z = new int[FORM_M];
        boolean[] seen = new boolean[FORM_M];
        int got = 0;
        for (int i = 0; i < BALL; i++) {
            int h = Math.floorMod(BALL_X[i] + FORM_A * BALL_Y[i] + FORM_B * BALL_Z[i], FORM_M);
            if (seen[h]) {
                continue;
            }
            seen[h] = true;
            REP_X[h] = BALL_X[i];
            REP_Y[h] = BALL_Y[i];
            REP_Z[h] = BALL_Z[i];
            got++;
        }
        if (got != FORM_M) {
            throw new IllegalStateException("reps " + got);
        }
    }

    static boolean isCenter(int x, int y, int z) {
        return Math.floorMod(x + FORM_A * y + FORM_B * z, FORM_M) == 0;
    }

    static int overlap(int sx, int sy, int sz) {
        int n = 0;
        for (int i = 0; i < BALL; i++) {
            int px = BALL_X[i] - sx;
            int py = BALL_Y[i] - sy;
            int pz = BALL_Z[i] - sz;
            if (px * px + py * py + pz * pz <= 2) {
                n++;
            }
        }
        return n;
    }

    /** Ticks of flying until the wright is inside 1.5 blocks of the next hover. */
    static int flyTicks(int x0, int y0, int z0, int x1, int y1, int z1) {
        double dx = x0 - x1;
        double dy = y0 - y1;
        double dz = z0 - z1;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double gap = dist - ARRIVE;
        if (gap <= 1.0e-9) {
            return 0;
        }
        if (dist <= 8.0) {
            return (int) Math.ceil(gap / CLOSE_SPEED);
        }
        double fast = dist - 8.0;
        double slow = 8.0 - ARRIVE;
        return (int) Math.ceil(fast / FAR_SPEED) + (int) Math.ceil(slow / CLOSE_SPEED);
    }

    static int usefulCrew(long blocks, int sx, int sy, int sz) {
        int station = 3;
        int nx = (Math.max(1, sx) + station - 1) / station;
        int ny = (Math.max(1, sy) + station - 1) / station;
        int nz = (Math.max(1, sz) + station - 1) / station;
        int stations = Math.max(1, nx * ny * nz);
        int columns = Math.max(1, Math.max(sx, sz));
        int blockCap = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, blocks));
        return Math.max(1, Math.min(blockCap, Math.min(stations, columns)));
    }

    // ---------------------------------------------------------------------------------------------
    // Shapes (same inclusion tests as Shapes.java, translated into a 0-based box when noted)
    // ---------------------------------------------------------------------------------------------

    abstract static class Shape {
        final String name;
        final int x0;
        final int y0;
        final int z0;
        final int x1;
        final int y1;
        final int z1;
        long cells = -1;

        Shape(String name, int x0, int y0, int z0, int x1, int y1, int z1) {
            this.name = name;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        abstract boolean contains(int x, int y, int z);

        int sx() {
            return x1 - x0 + 1;
        }

        int sy() {
            return y1 - y0 + 1;
        }

        int sz() {
            return z1 - z0 + 1;
        }

        long volume() {
            return (long) sx() * sy() * sz();
        }
    }

    static Shape cube(int n, boolean hollow) {
        String name = (hollow ? "hollow cube " : "cube ") + n;
        return new Shape(name, 0, 0, 0, n - 1, n - 1, n - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= n || y >= n || z >= n) {
                    return false;
                }
                if (!hollow || n <= 2) {
                    return true;
                }
                return x == 0 || y == 0 || z == 0 || x == n - 1 || y == n - 1 || z == n - 1;
            }
        };
    }

    static Shape box(String name, int w, int h, int d, boolean hollow) {
        return new Shape(name, 0, 0, 0, w - 1, h - 1, d - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) {
                    return false;
                }
                if (!hollow) {
                    return true;
                }
                return x == 0 || x == w - 1 || y == 0 || y == h - 1 || z == 0 || z == d - 1;
            }
        };
    }

    static Shape sphere(int diameter, boolean hollow) {
        int d = Math.max(1, diameter);
        String name = (hollow ? "hollow sphere " : "sphere ") + d;
        return new Shape(name, 0, 0, 0, d - 1, d - 1, d - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= d || y >= d || z >= d) {
                    return false;
                }
                double mid = (d - 1) * 0.5;
                double dx = x - mid;
                double dy = y - mid;
                double dz = z - mid;
                double dist2 = dx * dx + dy * dy + dz * dz;
                double maxR2 = (d * 0.5) * (d * 0.5);
                if (dist2 > maxR2 + 1.0E-6) {
                    return false;
                }
                if (hollow && d > 2) {
                    double inner = ((d - 2) * 0.5) * ((d - 2) * 0.5);
                    if (dist2 < inner - 1.0E-6) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    static Shape disk(int diameter) {
        int d = Math.max(1, diameter);
        return new Shape("disk " + d, 0, 0, 0, d - 1, 0, d - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (y != 0 || x < 0 || z < 0 || x >= d || z >= d) {
                    return false;
                }
                double mid = (d - 1) * 0.5;
                double dx = x - mid;
                double dz = z - mid;
                double dist2 = dx * dx + dz * dz;
                return dist2 <= (d * 0.5) * (d * 0.5) + 1.0E-6;
            }
        };
    }

    static Shape cylinder(int diameter, int height, boolean hollow) {
        int d = Math.max(1, diameter);
        int h = Math.max(1, height);
        String name = (hollow ? "hollow cyl " : "cyl ") + d + "x" + h;
        return new Shape(name, 0, 0, 0, d - 1, h - 1, d - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (y < 0 || y >= h || x < 0 || z < 0 || x >= d || z >= d) {
                    return false;
                }
                double mid = (d - 1) * 0.5;
                double dx = x - mid;
                double dz = z - mid;
                double dist2 = dx * dx + dz * dz;
                double maxR2 = (d * 0.5) * (d * 0.5);
                if (dist2 > maxR2 + 1.0E-6) {
                    return false;
                }
                if (!hollow) {
                    return true;
                }
                if (d <= 2) {
                    return true;
                }
                double inner = ((d - 2) * 0.5) * ((d - 2) * 0.5);
                return dist2 >= inner - 1.0E-6;
            }
        };
    }

    static Shape pyramid(int diameter, int height, boolean hollow) {
        int base = Math.max(1, diameter);
        int top = Math.max(1, height);
        int o = -(base - 1) / 2;
        String name = (hollow ? "hollow pyr " : "pyramid ") + base + "x" + top;
        return new Shape(name, o, 0, o, o + base - 1, top - 1, o + base - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (y < 0 || y >= top) {
                    return false;
                }
                int layer = Math.max(1, base - (base * y) / top);
                int lo = -(layer - 1) / 2;
                int ix = x - lo;
                int iz = z - lo;
                if (ix < 0 || iz < 0 || ix >= layer || iz >= layer) {
                    return false;
                }
                if (!hollow) {
                    return true;
                }
                return ix == 0 || ix == layer - 1 || iz == 0 || iz == layer - 1 || y == 0 || y == top - 1;
            }
        };
    }

    static Shape dome(int diameter, int height, boolean hollow) {
        int d = Math.max(1, diameter);
        int tall = Math.max(1, height);
        int o = -(d - 1) / 2;
        String name = (hollow ? "hollow dome " : "dome ") + d + "x" + tall;
        return new Shape(name, o, 0, o, o + d - 1, tall, o + d - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                if (y < 0 || y > tall) {
                    return false;
                }
                int ix = x - o;
                int iz = z - o;
                if (ix < 0 || iz < 0 || ix >= d || iz >= d) {
                    return false;
                }
                double mid = (d - 1) * 0.5;
                double rr = (d * 0.5) * (d * 0.5);
                double hh = (double) tall * tall;
                double dx = ix - mid;
                double dz = iz - mid;
                double val = dx * dx * hh + dz * dz * hh + (double) y * y * rr;
                if (val > rr * hh + 1.0E-6) {
                    return false;
                }
                if (!hollow || d <= 2 || tall <= 1) {
                    return true;
                }
                double innerR = (d - 2) * 0.5;
                double innerH = tall - 1;
                double innerLimit = innerR * innerR * innerH * innerH;
                double inner = dx * dx * innerH * innerH + dz * dz * innerH * innerH + (double) y * y * innerR * innerR;
                return !(inner < innerLimit - 1.0E-6);
            }
        };
    }

    static List<Shape> zoo() {
        List<Shape> list = new ArrayList<>();
        int[] cubes = {1, 2, 3, 4, 5, 8, 10, 16, 24, 32, 48, 64, 100};
        for (int n : cubes) {
            list.add(cube(n, false));
        }
        for (int n : new int[] {3, 5, 8, 16, 32, 64, 100}) {
            list.add(cube(n, true));
        }
        for (int n : new int[] {1, 3, 5, 8, 16, 32, 64, 100}) {
            list.add(sphere(n, false));
            if (n >= 5) {
                list.add(sphere(n, true));
            }
        }
        for (int n : new int[] {1, 3, 8, 16, 19, 32, 100}) {
            list.add(box("pillar " + n, 1, n, 1, false));
        }
        for (int n : new int[] {8, 16, 32, 100}) {
            list.add(box("wall " + n, n, n, 1, false));
            list.add(disk(n));
        }
        list.add(box("beam 2x100", 100, 1, 2, false));
        list.add(box("slab 100x1x100", 100, 1, 100, false));
        list.add(box("hall 100x8x40", 100, 8, 40, false));
        list.add(box("shell 100x40x40", 100, 40, 40, true));
        list.add(box("plate 50x50x2", 50, 2, 50, false));
        list.add(cylinder(16, 32, false));
        list.add(cylinder(32, 8, false));
        list.add(cylinder(100, 20, false));
        list.add(cylinder(32, 32, true));
        list.add(cylinder(100, 40, true));
        list.add(pyramid(16, 16, false));
        list.add(pyramid(32, 32, false));
        list.add(pyramid(64, 64, false));
        list.add(pyramid(32, 32, true));
        list.add(dome(16, 16, false));
        list.add(dome(32, 16, false));
        list.add(dome(64, 32, false));
        list.add(dome(32, 16, true));
        list.add(dome(64, 32, true));
        return list;
    }

    // ---------------------------------------------------------------------------------------------
    // Bitset occupancy
    // ---------------------------------------------------------------------------------------------

    static final class Bits {
        final int x0;
        final int y0;
        final int z0;
        final int sx;
        final int sy;
        final int sz;
        final long[] words;
        int remain;

        Bits(Shape shape) {
            x0 = shape.x0;
            y0 = shape.y0;
            z0 = shape.z0;
            sx = shape.sx();
            sy = shape.sy();
            sz = shape.sz();
            long n = (long) sx * sy * sz;
            if (n > 2_000_000L) {
                throw new IllegalArgumentException("bbox " + n);
            }
            words = new long[(int) ((n + 63) >>> 6)];
        }

        Bits copy() {
            Bits b = new Bits(x0, y0, z0, sx, sy, sz, words.length);
            System.arraycopy(words, 0, b.words, 0, words.length);
            b.remain = remain;
            return b;
        }

        private Bits(int x0, int y0, int z0, int sx, int sy, int sz, int words) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.words = new long[words];
        }

        int index(int x, int y, int z) {
            int lx = x - x0;
            int ly = y - y0;
            int lz = z - z0;
            if ((lx | ly | lz) < 0 || lx >= sx || ly >= sy || lz >= sz) {
                return -1;
            }
            return lx + sx * (ly + sy * lz);
        }

        void fill(Shape shape) {
            Arrays.fill(words, 0L);
            remain = 0;
            for (int z = shape.z0; z <= shape.z1; z++) {
                for (int y = shape.y0; y <= shape.y1; y++) {
                    for (int x = shape.x0; x <= shape.x1; x++) {
                        if (!shape.contains(x, y, z)) {
                            continue;
                        }
                        int i = index(x, y, z);
                        words[i >>> 6] |= 1L << (i & 63);
                        remain++;
                    }
                }
            }
        }

        boolean test(int x, int y, int z) {
            int i = index(x, y, z);
            if (i < 0) {
                return false;
            }
            return (words[i >>> 6] & (1L << (i & 63))) != 0;
        }

        boolean take(int x, int y, int z) {
            int i = index(x, y, z);
            if (i < 0) {
                return false;
            }
            int w = i >>> 6;
            long m = 1L << (i & 63);
            if ((words[w] & m) == 0) {
                return false;
            }
            words[w] ^= m;
            remain--;
            return true;
        }

        int peek(int ax, int ay, int az, int cap) {
            int n = 0;
            for (int i = 0; i < BALL && n < cap; i++) {
                if (test(ax + BALL_X[i], ay + BALL_Y[i], az + BALL_Z[i])) {
                    n++;
                }
            }
            return n;
        }

        int claim(int ax, int ay, int az, int cap) {
            int n = 0;
            for (int i = 0; i < BALL && n < cap; i++) {
                if (take(ax + BALL_X[i], ay + BALL_Y[i], az + BALL_Z[i])) {
                    n++;
                }
            }
            return n;
        }
    }

    static final class Stations {
        int[] x = new int[64];
        int[] y = new int[64];
        int[] z = new int[64];
        int[] blocks = new int[64];
        int n;

        void add(int px, int py, int pz, int hit) {
            if (n == x.length) {
                int m = n << 1;
                x = Arrays.copyOf(x, m);
                y = Arrays.copyOf(y, m);
                z = Arrays.copyOf(z, m);
                blocks = Arrays.copyOf(blocks, m);
            }
            x[n] = px;
            y[n] = py;
            z[n] = pz;
            blocks[n] = hit;
            n++;
        }

        long blockSum() {
            long s = 0;
            for (int i = 0; i < n; i++) {
                s += blocks[i];
            }
            return s;
        }
    }

    static final class Row {
        final String shape;
        final String algo;
        final long cells;
        final int sx;
        final int sy;
        final int sz;
        final int ops;
        final int leftover;
        final int maxHit;
        final double planMs;
        final long routeBytes;
        final long scratchBytes;
        final long portTicks;
        final long centerTicks;
        final long cornerTicks;
        final long faceTicks;
        final long edgeTicks;
        final long preTicks;

        Row(String shape, String algo, long cells, int sx, int sy, int sz, int ops, int leftover, int maxHit,
                double planMs, long routeBytes, long scratchBytes, long[] travel) {
            this.shape = shape;
            this.algo = algo;
            this.cells = cells;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.ops = ops;
            this.leftover = leftover;
            this.maxHit = maxHit;
            this.planMs = planMs;
            this.routeBytes = routeBytes;
            this.scratchBytes = scratchBytes;
            this.portTicks = travel[0];
            this.centerTicks = travel[1];
            this.cornerTicks = travel[2];
            this.faceTicks = travel[3];
            this.edgeTicks = travel[4];
            this.preTicks = travel[5];
        }

        boolean ok() {
            return leftover == 0 && ops > 0 && cells > 0;
        }

        double capEff() {
            return ops <= 0 ? 0 : (double) (cells - leftover) / (ops * (double) CAP);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Planners
    // ---------------------------------------------------------------------------------------------

    static Stations latticeStations(Shape shape) {
        Stations s = new Stations();
        int loX = shape.x0 - 1;
        int hiX = shape.x1 + 1;
        int loY = shape.y0 - 1;
        int hiY = shape.y1 + 1;
        int loZ = shape.z0 - 1;
        int hiZ = shape.z1 + 1;
        for (int y = loY; y <= hiY; y++) {
            for (int z = loZ; z <= hiZ; z++) {
                int need = Math.floorMod(-FORM_A * y - FORM_B * z, FORM_M);
                int x = loX + Math.floorMod(need - loX, FORM_M);
                for (; x <= hiX; x += FORM_M) {
                    int hit = 0;
                    int ax = x;
                    int ay = y;
                    int az = z;
                    for (int r = 0; r < FORM_M; r++) {
                        if (shape.contains(ax + REP_X[r], ay + REP_Y[r], az + REP_Z[r])) {
                            hit++;
                        }
                    }
                    if (hit > 0) {
                        s.add(ax, ay, az, hit);
                    }
                }
            }
        }
        return s;
    }

    static void firstFitOrder(Bits bits, int[] order, boolean local, Stations out) {
        int bias = 512;
        for (int packed : order) {
            int x = (packed & 1023) - bias;
            int y = ((packed >>> 10) & 1023) - bias;
            int z = ((packed >>> 20) & 1023) - bias;
            if (!bits.test(x, y, z)) {
                continue;
            }
            int ax = x;
            int ay = y;
            int az = z;
            if (local) {
                int best = bits.peek(x, y, z, CAP);
                for (int i = 0; i < BALL; i++) {
                    int cx = x - BALL_X[i];
                    int cy = y - BALL_Y[i];
                    int cz = z - BALL_Z[i];
                    int hit = bits.peek(cx, cy, cz, CAP);
                    if (hit > best) {
                        best = hit;
                        ax = cx;
                        ay = cy;
                        az = cz;
                    }
                }
            }
            int hit = bits.claim(ax, ay, az, CAP);
            if (hit > 0 && out != null) {
                out.add(ax, ay, az, hit);
            }
        }
    }

    static int[] packScan(Shape shape, Bits bits) {
        int[] cells = new int[bits.remain];
        int p = 0;
        int bias = 512;
        for (int z = shape.z0; z <= shape.z1; z++) {
            for (int y = shape.y0; y <= shape.y1; y++) {
                for (int x = shape.x0; x <= shape.x1; x++) {
                    if (!bits.test(x, y, z)) {
                        continue;
                    }
                    cells[p++] = (x + bias) | ((y + bias) << 10) | ((z + bias) << 20);
                }
            }
        }
        if (p != cells.length) {
            cells = Arrays.copyOf(cells, p);
        }
        return cells;
    }

    static int[] orderBy(Shape shape, int[] cells, int mode) {
        int n = cells.length;
        int bias = 512;
        int[] key = new int[n];
        int max = 0;
        int cx = (shape.x0 + shape.x1) / 2;
        int cy = (shape.y0 + shape.y1) / 2;
        int cz = (shape.z0 + shape.z1) / 2;
        for (int i = 0; i < n; i++) {
            int packed = cells[i];
            int x = (packed & 1023) - bias;
            int y = ((packed >>> 10) & 1023) - bias;
            int z = ((packed >>> 20) & 1023) - bias;
            int k = switch (mode) {
                case 1 -> dist2(x, y, z, cx, cy, cz);
                case 2 -> cornerKey(shape, x, y, z);
                case 3 -> faceKey(shape, x, y, z);
                case 4 -> edgeKey(shape, x, y, z);
                default -> i;
            };
            key[i] = k;
            if (k > max) {
                max = k;
            }
        }
        int[] bucket = new int[max + 1];
        for (int k : key) {
            bucket[k]++;
        }
        int run = 0;
        for (int i = 0; i < bucket.length; i++) {
            int c = bucket[i];
            bucket[i] = run;
            run += c;
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[bucket[key[i]]++] = cells[i];
        }
        return out;
    }

    static int dist2(int x, int y, int z, int px, int py, int pz) {
        int dx = x - px;
        int dy = y - py;
        int dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    static int cornerKey(Shape s, int x, int y, int z) {
        int best = Integer.MAX_VALUE;
        int[] xs = {s.x0, s.x1};
        int[] ys = {s.y0, s.y1};
        int[] zs = {s.z0, s.z1};
        for (int px : xs) {
            for (int py : ys) {
                for (int pz : zs) {
                    best = Math.min(best, dist2(x, y, z, px, py, pz));
                }
            }
        }
        return best;
    }

    static int faceKey(Shape s, int x, int y, int z) {
        int cx = (s.x0 + s.x1) / 2;
        int cy = (s.y0 + s.y1) / 2;
        int cz = (s.z0 + s.z1) / 2;
        int best = dist2(x, y, z, cx, cy, s.z0);
        best = Math.min(best, dist2(x, y, z, cx, cy, s.z1));
        best = Math.min(best, dist2(x, y, z, cx, s.y0, cz));
        best = Math.min(best, dist2(x, y, z, cx, s.y1, cz));
        best = Math.min(best, dist2(x, y, z, s.x0, cy, cz));
        best = Math.min(best, dist2(x, y, z, s.x1, cy, cz));
        return best;
    }

    static int edgeKey(Shape s, int x, int y, int z) {
        int cx = (s.x0 + s.x1) / 2;
        int cy = (s.y0 + s.y1) / 2;
        int cz = (s.z0 + s.z1) / 2;
        int best = Integer.MAX_VALUE;
        int[][] pts = {
                {s.x0, s.y0, cz}, {s.x1, s.y0, cz}, {s.x0, s.y1, cz}, {s.x1, s.y1, cz},
                {s.x0, cy, s.z0}, {s.x1, cy, s.z0}, {s.x0, cy, s.z1}, {s.x1, cy, s.z1},
                {cx, s.y0, s.z0}, {cx, s.y0, s.z1}, {cx, s.y1, s.z0}, {cx, s.y1, s.z1}
        };
        for (int[] p : pts) {
            best = Math.min(best, dist2(x, y, z, p[0], p[1], p[2]));
        }
        return best;
    }

    static void gridStep(Bits bits, Shape shape, int step, Stations out) {
        int ax0 = shape.x0 + Math.min(1, shape.sx() - 1);
        int ay0 = shape.y0 + Math.min(1, shape.sy() - 1);
        int az0 = shape.z0 + Math.min(1, shape.sz() - 1);
        for (int x = ax0; x <= shape.x1 + 1; x += step) {
            for (int y = ay0; y <= shape.y1 + 1; y += step) {
                for (int z = az0; z <= shape.z1 + 1; z += step) {
                    int hit = bits.claim(x, y, z, CAP);
                    if (hit > 0 && out != null) {
                        out.add(x, y, z, hit);
                    }
                }
            }
        }
    }

    static void planeTiles(Bits bits, Shape shape, Stations out) {
        claimPlaneFamily(bits, shape, 0, out);
        claimPlaneFamily(bits, shape, 1, out);
        claimPlaneFamily(bits, shape, 2, out);
    }

    static void claimPlaneFamily(Bits bits, Shape shape, int axis, Stations out) {
        int a0;
        int a1;
        if (axis == 0) {
            a0 = shape.x0;
            a1 = shape.x1;
        } else if (axis == 1) {
            a0 = shape.y0;
            a1 = shape.y1;
        } else {
            a0 = shape.z0;
            a1 = shape.z1;
        }
        for (int face = 0; face < 2; face++) {
            int fixed = face == 0 ? a0 : a1;
            if (face == 1 && a0 == a1) {
                continue;
            }
            int u0 = axis == 0 ? shape.y0 : shape.x0;
            int u1 = axis == 0 ? shape.y1 : shape.x1;
            int v0 = axis == 2 ? shape.y0 : shape.z0;
            int v1 = axis == 2 ? shape.y1 : shape.z1;
            for (int u = u0; u <= u1; u += 3) {
                for (int v = v0; v <= v1; v += 3) {
                    int uu = Math.min(u + 1, u1);
                    int vv = Math.min(v + 1, v1);
                    int x;
                    int y;
                    int z;
                    if (axis == 0) {
                        x = fixed;
                        y = uu;
                        z = vv;
                    } else if (axis == 1) {
                        x = uu;
                        y = fixed;
                        z = vv;
                    } else {
                        x = uu;
                        y = vv;
                        z = fixed;
                    }
                    int hit = bits.claim(x, y, z, CAP);
                    if (hit > 0 && out != null) {
                        out.add(x, y, z, hit);
                    }
                }
            }
        }
    }

    static void chunk16(Bits bits, Shape shape, boolean clip, Stations out) {
        for (int z = shape.z0; z <= shape.z1; z += 16) {
            for (int y = shape.y0; y <= shape.y1; y += 16) {
                for (int x = shape.x0; x <= shape.x1; x += 16) {
                    int x1 = Math.min(shape.x1, x + 15);
                    int y1 = Math.min(shape.y1, y + 15);
                    int z1 = Math.min(shape.z1, z + 15);
                    for (int zz = z; zz <= z1; zz++) {
                        for (int yy = y; yy <= y1; yy++) {
                            for (int xx = x; xx <= x1; xx++) {
                                if (!bits.test(xx, yy, zz)) {
                                    continue;
                                }
                                if (clip) {
                                    int hit = claimClipped(bits, xx, yy, zz, x, y, z, x1, y1, z1);
                                    if (hit > 0 && out != null) {
                                        out.add(xx, yy, zz, hit);
                                    }
                                } else {
                                    int hit = bits.claim(xx, yy, zz, CAP);
                                    if (hit > 0 && out != null) {
                                        out.add(xx, yy, zz, hit);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static int claimClipped(Bits bits, int ax, int ay, int az, int x0, int y0, int z0, int x1, int y1, int z1) {
        int n = 0;
        for (int i = 0; i < BALL && n < CAP; i++) {
            int x = ax + BALL_X[i];
            int y = ay + BALL_Y[i];
            int z = az + BALL_Z[i];
            if (x < x0 || y < y0 || z < z0 || x > x1 || y > y1 || z > z1) {
                continue;
            }
            if (bits.take(x, y, z)) {
                n++;
            }
        }
        return n;
    }

    /** Current small-shape planner: every live cell is tried as a hover, fullest wins. */
    static int legacyPairs(int[] xs, int[] ys, int[] zs, long budgetNs) {
        int n = xs.length;
        boolean[] alive = new boolean[n];
        Arrays.fill(alive, true);
        int left = n;
        int stations = 0;
        long t0 = System.nanoTime();
        while (left > 0) {
            if (System.nanoTime() - t0 > budgetNs) {
                return -stations;
            }
            int bestI = -1;
            int best = 0;
            for (int i = 0; i < n; i++) {
                if (!alive[i]) {
                    continue;
                }
                int covered = 0;
                for (int j = 0; j < n; j++) {
                    if (!alive[j]) {
                        continue;
                    }
                    int dx = xs[j] - xs[i];
                    int dy = ys[j] - ys[i];
                    int dz = zs[j] - zs[i];
                    if (dx * dx + dy * dy + dz * dz <= 2) {
                        covered++;
                        if (covered >= CAP) {
                            break;
                        }
                    }
                }
                if (covered > best) {
                    best = covered;
                    bestI = i;
                    if (best >= CAP) {
                        break;
                    }
                }
            }
            if (bestI < 0 || best == 0) {
                break;
            }
            int ax = xs[bestI];
            int ay = ys[bestI];
            int az = zs[bestI];
            int took = 0;
            for (int j = 0; j < n && took < CAP; j++) {
                if (!alive[j]) {
                    continue;
                }
                int dx = xs[j] - ax;
                int dy = ys[j] - ay;
                int dz = zs[j] - az;
                if (dx * dx + dy * dy + dz * dz <= 2) {
                    alive[j] = false;
                    left--;
                    took++;
                }
            }
            stations++;
        }
        return stations;
    }

    static int greedyWord(Bits bits, long budgetNs) {
        long t0 = System.nanoTime();
        int ops = 0;
        int sx = bits.sx;
        int sy = bits.sy;
        while (bits.remain > 0) {
            if (System.nanoTime() - t0 > budgetNs) {
                return -ops;
            }
            int best = 0;
            int bx = 0;
            int by = 0;
            int bz = 0;
            for (int w = 0; w < bits.words.length && best < CAP; w++) {
                long word = bits.words[w];
                while (word != 0 && best < CAP) {
                    int bit = Long.numberOfTrailingZeros(word);
                    word &= word - 1;
                    int i = (w << 6) + bit;
                    int z = i / (sx * sy);
                    int rem = i - z * sx * sy;
                    int y = rem / sx;
                    int x = rem - y * sx;
                    int hit = bits.peek(x + bits.x0, y + bits.y0, z + bits.z0, CAP);
                    if (hit > best) {
                        best = hit;
                        bx = x + bits.x0;
                        by = y + bits.y0;
                        bz = z + bits.z0;
                    }
                }
            }
            if (best == 0) {
                break;
            }
            bits.claim(bx, by, bz, CAP);
            ops++;
        }
        return ops;
    }

    // ---------------------------------------------------------------------------------------------
    // Travel makespan for 10 wrights
    // ---------------------------------------------------------------------------------------------

    /** port, center, corners, face mids, edge mids, already on the first hover. */
    static long[] travel(Stations s, Shape shape) {
        return travel(s, shape, CREW);
    }

    static long[] travel(Stations s, Shape shape, int crewCap) {
        long[] ticks = new long[6];
        if (s == null || s.n == 0) {
            return ticks;
        }
        int n = s.n;
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        qsort(idx, s, 0, n - 1, 0);
        int crew = Math.min(Math.max(1, crewCap), n);
        int base = n / crew;
        int extra = n % crew;
        int cursor = 0;
        long[][] per = new long[6][crew];
        for (int w = 0; w < crew; w++) {
            int len = base + (w < extra ? 1 : 0);
            int[] slice = new int[len];
            System.arraycopy(idx, cursor, slice, 0, len);
            cursor += len;
            qsort(slice, s, 0, len - 1, 1);
            serpentine(slice, s);
            double ax = 0;
            double ay = 0;
            double az = 0;
            for (int id : slice) {
                ax += s.x[id];
                ay += s.y[id];
                az += s.z[id];
            }
            ax /= len;
            ay /= len;
            az /= len;
            int[][] seeds = seeds(shape, ax, ay, az);
            for (int mode = 0; mode < 5; mode++) {
                per[mode][w] = directed(slice, s, seeds[mode][0], seeds[mode][1], seeds[mode][2]);
            }
            per[5][w] = internal(slice, s);
        }
        for (int mode = 0; mode < 6; mode++) {
            long max = 0;
            for (int w = 0; w < crew; w++) {
                if (per[mode][w] > max) {
                    max = per[mode][w];
                }
            }
            ticks[mode] = max;
        }
        return ticks;
    }

    static int[][] seeds(Shape shape, double ax, double ay, double az) {
        int cx = (shape.x0 + shape.x1) / 2;
        int cy = (shape.y0 + shape.y1) / 2;
        int cz = (shape.z0 + shape.z1) / 2;
        int[] port = {shape.x0 - PORT_GAP, shape.y0, cz};
        int[] center = {cx, cy, cz};
        int[] corner = nearest(ax, ay, az, corners(shape));
        int[] face = nearest(ax, ay, az, faces(shape));
        int[] edge = nearest(ax, ay, az, edges(shape));
        return new int[][] {port, center, corner, face, edge};
    }

    static int[] nearest(double ax, double ay, double az, int[][] pts) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < pts.length; i++) {
            double dx = pts[i][0] - ax;
            double dy = pts[i][1] - ay;
            double dz = pts[i][2] - az;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return pts[best];
    }

    static int[][] corners(Shape s) {
        return new int[][] {
                {s.x0, s.y0, s.z0}, {s.x1, s.y0, s.z0}, {s.x0, s.y1, s.z0}, {s.x1, s.y1, s.z0},
                {s.x0, s.y0, s.z1}, {s.x1, s.y0, s.z1}, {s.x0, s.y1, s.z1}, {s.x1, s.y1, s.z1}
        };
    }

    static int[][] faces(Shape s) {
        int cx = (s.x0 + s.x1) / 2;
        int cy = (s.y0 + s.y1) / 2;
        int cz = (s.z0 + s.z1) / 2;
        return new int[][] {
                {cx, cy, s.z0}, {cx, cy, s.z1}, {cx, s.y0, cz}, {cx, s.y1, cz}, {s.x0, cy, cz}, {s.x1, cy, cz}
        };
    }

    static int[][] edges(Shape s) {
        int cx = (s.x0 + s.x1) / 2;
        int cy = (s.y0 + s.y1) / 2;
        int cz = (s.z0 + s.z1) / 2;
        return new int[][] {
                {s.x0, s.y0, cz}, {s.x1, s.y0, cz}, {s.x0, s.y1, cz}, {s.x1, s.y1, cz},
                {s.x0, cy, s.z0}, {s.x1, cy, s.z0}, {s.x0, cy, s.z1}, {s.x1, cy, s.z1},
                {cx, s.y0, s.z0}, {cx, s.y0, s.z1}, {cx, s.y1, s.z0}, {cx, s.y1, s.z1}
        };
    }

    static void serpentine(int[] slice, Stations s) {
        int i = 0;
        boolean flip = false;
        while (i < slice.length) {
            int y = s.y[slice[i]];
            int j = i + 1;
            while (j < slice.length && s.y[slice[j]] == y) {
                j++;
            }
            if (flip) {
                reverse(slice, i, j - 1);
            }
            flip = !flip;
            i = j;
        }
    }

    static void reverse(int[] a, int lo, int hi) {
        while (lo < hi) {
            int t = a[lo];
            a[lo] = a[hi];
            a[hi] = t;
            lo++;
            hi--;
        }
    }

    static long directed(int[] slice, Stations s, int sx, int sy, int sz) {
        long forward = pathCost(slice, s, sx, sy, sz, false);
        long backward = pathCost(slice, s, sx, sy, sz, true);
        return Math.min(forward, backward);
    }

    static long pathCost(int[] slice, Stations s, int sx, int sy, int sz, boolean reverse) {
        int start = reverse ? slice.length - 1 : 0;
        int step = reverse ? -1 : 1;
        int end = reverse ? -1 : slice.length;
        long ticks = 0;
        int px = sx;
        int py = sy;
        int pz = sz;
        for (int k = start; k != end; k += step) {
            int id = slice[k];
            ticks += flyTicks(px, py, pz, s.x[id], s.y[id], s.z[id]);
            int bursts = Math.max(1, (s.blocks[id] + CAP - 1) / CAP);
            ticks += bursts;
            px = s.x[id];
            py = s.y[id];
            pz = s.z[id];
        }
        return ticks;
    }

    static long internal(int[] slice, Stations s) {
        if (slice.length == 0) {
            return 0;
        }
        int id0 = slice[0];
        long a = pathCost(slice, s, s.x[id0], s.y[id0], s.z[id0], false);
        int id1 = slice[slice.length - 1];
        long b = pathCost(slice, s, s.x[id1], s.y[id1], s.z[id1], true);
        return Math.min(a, b);
    }

    /** axis 0: x,y,z. axis 1: y,z,x. */
    static void qsort(int[] idx, Stations s, int lo, int hi, int axis) {
        if (lo >= hi) {
            return;
        }
        int pivot = idx[lo + ((hi - lo) >>> 1)];
        int i = lo;
        int j = hi;
        while (i <= j) {
            while (cmp(s, idx[i], pivot, axis) < 0) {
                i++;
            }
            while (cmp(s, idx[j], pivot, axis) > 0) {
                j--;
            }
            if (i <= j) {
                int t = idx[i];
                idx[i] = idx[j];
                idx[j] = t;
                i++;
                j--;
            }
        }
        if (lo < j) {
            qsort(idx, s, lo, j, axis);
        }
        if (i < hi) {
            qsort(idx, s, i, hi, axis);
        }
    }

    static int cmp(Stations s, int a, int b, int axis) {
        int c0;
        int c1;
        int c2;
        int d0;
        int d1;
        int d2;
        if (axis == 0) {
            c0 = s.x[a];
            c1 = s.y[a];
            c2 = s.z[a];
            d0 = s.x[b];
            d1 = s.y[b];
            d2 = s.z[b];
        } else {
            c0 = s.y[a];
            c1 = s.z[a];
            c2 = s.x[a];
            d0 = s.y[b];
            d1 = s.z[b];
            d2 = s.x[b];
        }
        int c = Integer.compare(c0, d0);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(c1, d1);
        if (c != 0) {
            return c;
        }
        return Integer.compare(c2, d2);
    }

    // ---------------------------------------------------------------------------------------------
    // Runs
    // ---------------------------------------------------------------------------------------------

    /** Perfect 19-fill on a 19×3×3 torus, or a note that the search proved there is none. */
    static String exactCover19() {
        int sx = 19;
        int sy = 3;
        int sz = 3;
        boolean[] alive = new boolean[sx * sy * sz];
        Arrays.fill(alive, true);
        int[] nodes = {0};
        boolean[] capped = {false};
        boolean found = recExact(alive, sx, sy, sz, nodes, capped);
        if (found) {
            return "FOUND a perfect 19-tiling (" + nodes[0] + " nodes)";
        }
        if (capped[0]) {
            return "stopped at " + nodes[0] + " nodes without a tiling (not a proof)";
        }
        return "proved there is no perfect tiling (" + nodes[0] + " nodes)";
    }

    static boolean recExact(boolean[] alive, int sx, int sy, int sz, int[] nodes, boolean[] capped) {
        if (++nodes[0] > 4_000_000) {
            capped[0] = true;
            return false;
        }
        int n = alive.length;
        int idx = -1;
        for (int i = 0; i < n; i++) {
            if (alive[i]) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return true;
        }
        int x = idx % sx;
        int y = (idx / sx) % sy;
        int z = idx / (sx * sy);
        for (int o = 0; o < BALL; o++) {
            int cx = x - BALL_X[o];
            int cy = y - BALL_Y[o];
            int cz = z - BALL_Z[o];
            if (!exactFits(alive, cx, cy, cz, sx, sy, sz)) {
                continue;
            }
            exactPaint(alive, cx, cy, cz, sx, sy, sz, false);
            if (recExact(alive, sx, sy, sz, nodes, capped)) {
                return true;
            }
            if (capped[0]) {
                exactPaint(alive, cx, cy, cz, sx, sy, sz, true);
                return false;
            }
            exactPaint(alive, cx, cy, cz, sx, sy, sz, true);
        }
        return false;
    }

    static int wrap(int v, int m) {
        int r = v % m;
        return r < 0 ? r + m : r;
    }

    static boolean exactFits(boolean[] alive, int cx, int cy, int cz, int sx, int sy, int sz) {
        for (int i = 0; i < BALL; i++) {
            int x = wrap(cx + BALL_X[i], sx);
            int y = wrap(cy + BALL_Y[i], sy);
            int z = wrap(cz + BALL_Z[i], sz);
            if (!alive[x + sx * (y + sy * z)]) {
                return false;
            }
        }
        return true;
    }

    static void exactPaint(boolean[] alive, int cx, int cy, int cz, int sx, int sy, int sz, boolean on) {
        for (int i = 0; i < BALL; i++) {
            int x = wrap(cx + BALL_X[i], sx);
            int y = wrap(cy + BALL_Y[i], sy);
            int z = wrap(cz + BALL_Z[i], sz);
            alive[x + sx * (y + sy * z)] = on;
        }
    }

    static void sectionFrontier(java.io.PrintStream out) {
        out.println("## 1. What one hover can actually finish");
        out.println();
        out.println("A full 19-cell hover and the next full hover cannot sit on adjacent blocks.");
        out.println("Centers one step apart, or one diagonal step apart, share cells. The closest");
        out.println("integer steps that share nothing are already farther than the 1.5-block arrive");
        out.println("radius, so the wright spends a fly tick between two full hovers.");
        out.println();
        out.println("shift        dist   shared  new  fly  ticks  blocks/tick");
        int[][] shifts = {
                {1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {2, 0, 0}, {2, 1, 0}, {2, 1, 1},
                {2, 2, 0}, {2, 2, 1}, {2, 2, 2}, {3, 0, 0}, {3, 1, 1}
        };
        for (int[] s : shifts) {
            printShift(out, s[0], s[1], s[2]);
        }
        int best = Integer.MAX_VALUE;
        int bx = 0;
        int by = 0;
        int bz = 0;
        for (int x = -30; x <= 30; x++) {
            for (int y = -30; y <= 30; y++) {
                for (int z = -30; z <= 30; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (!isCenter(x, y, z)) {
                        continue;
                    }
                    int d2 = x * x + y * y + z * z;
                    if (d2 < best) {
                        best = d2;
                        bx = x;
                        by = y;
                        bz = z;
                    }
                }
            }
        }
        out.println();
        out.println("Shortest step on the perfect lattice: (" + bx + "," + by + "," + bz + ")  dist "
                + String.format(Locale.US, "%.3f", Math.sqrt(best)) + "  shared " + overlap(bx, by, bz));
        int stepFly = flyTicks(0, 0, 0, STEP_X, STEP_Y, STEP_Z);
        double perWright = FORM_M / (double) (stepFly + 1);
        out.println("No lattice puts a full disjoint 19 on every hover (tried every x+ay+bz mod 19).");
        out.println("Best lattice load is " + FORM_M + " blocks, shortest hop fly ticks " + stepFly + ".");
        out.println(String.format(Locale.US, "  %.2f blocks/tick per wright, %.1f with 10 wrights.", perWright, perWright * CREW));
        out.println("  100^3 = 1,000,000 blocks -> about "
                + String.format(Locale.US, "%.1f", 1_000_000 / (perWright * CREW) / 20.0)
                + " seconds of placing, before the walk to the site.");
        out.println();
        int centerCover = 0;
        for (int i = 0; i < BALL; i++) {
            int x = BALL_X[i] + 1;
            int y = BALL_Y[i] + 1;
            int z = BALL_Z[i] + 1;
            if (x >= 0 && y >= 0 && z >= 0 && x < 3 && y < 3 && z < 3) {
                centerCover++;
            }
        }
        out.println("Solid 3x3x3 is 27 blocks. The center hover covers " + centerCover
                + " and misses the 8 corners (squared distance 3).");
        out.println("Reach-1 ball (copper/iron, faces only) is " + ballCount(1) + " cells. Basic is the occupied cell only.");
        out.println();
    }

    static void printShift(java.io.PrintStream out, int x, int y, int z) {
        int shared = overlap(x, y, z);
        int fresh = CAP - shared;
        double dist = Math.sqrt(x * x + y * y + z * z);
        int fly = flyTicks(0, 0, 0, x, y, z);
        int ticks = fly + 1;
        double rate = fresh / (double) ticks;
        out.println(String.format(Locale.US, "(%d,%d,%d)     %5.2f   %4d   %4d   %2d    %2d     %5.2f",
                x, y, z, dist, shared, fresh, fly, ticks, rate));
    }

    static void sectionTorus(java.io.PrintStream out) {
        out.println("## 2. Easy grids versus the lattice");
        out.println();
        out.println("A perfect fill would be one residue class modulo 19, 19 blocks every hover.");
        out.println("Every linear form collides inside the sphere, and every index-19 lattice is one of");
        out.println("those forms (19 is prime). So a disjoint 19-tiling by translations does not exist.");
        out.println("The lattice below is the best covering that still fits in the sphere:");
        out.println("  modulus " + FORM_M + ", interior hover " + FORM_M + " blocks, cap use "
                + String.format(Locale.US, "%.1f%%", 100.0 * FORM_M / CAP) + ".");
        out.println();
        int side = FORM_M;
        Stations torus = latticeStations(new Shape("mod cube", 0, 0, 0, side - 1, side - 1, side - 1) {
            @Override
            boolean contains(int x, int y, int z) {
                return x >= 0 && y >= 0 && z >= 0 && x < side && y < side && z < side;
            }
        });
        long sum = torus.blockSum();
        long expectCells = (long) side * side * side;
        long expectHovers = expectCells / FORM_M;
        out.println("Checked on a " + side + "^3 cube (" + expectCells + " cells):");
        out.println("  lattice hovers " + torus.n + " (expect " + expectHovers + ")  cells " + sum);
        if (sum != expectCells) {
            out.println("  FAIL: lattice did not cover the cube once.");
        }
        out.println();
        out.println("First-fit from the corner of a 19^3 cube (claim the sphere and move on, no lattice):");
        Bits bits = new Bits(cube(19, false));
        bits.fill(cube(19, false));
        int[] scan = packScan(cube(19, false), bits);
        Stations ff = new Stations();
        firstFitOrder(bits, scan, false, ff);
        out.println("  hovers " + ff.n + "  leftover " + bits.remain + "  cap use "
                + String.format(Locale.US, "%.1f%%", 6859.0 / (ff.n * 19.0) * 100));
        out.println("  lattice on that same 19^3:");
        Stations big = latticeStations(cube(19, false));
        out.println("  hovers " + big.n + "  cells " + big.blockSum() + "  cap use "
                + String.format(Locale.US, "%.1f%%", 100.0 * big.blockSum() / (big.n * (double) CAP)));
        out.println();
        out.println("Exact-cover search for a perfect 19-tiling on the smallest wrap-free torus (19x3x3):");
        long t0 = System.nanoTime();
        String exact = exactCover19();
        out.println("  " + exact + " in " + String.format(Locale.US, "%.2f", (System.nanoTime() - t0) / 1_000_000.0) + " ms");
        out.println();
        out.println("The live estimate usefulCrewFor divides each side by 3. That pretends a hover");
        out.println("covers a 3x3x3. It does not: the corners are outside the sphere, and a 3-step");
        out.println("grid leaves a hole on every (2,2,2) mod 3. Measured leftover is below.");
        out.println();
    }

    static void sectionLegacy(java.io.PrintStream out) {
        out.println("## 3. Cost of the current pair search");
        out.println();
        out.println("planShapeStations tries every remaining block as the next hover. It refuses");
        out.println("shapes above 192 blocks. Times below are that same nested loop, no world calls.");
        out.println("A negative hover count means it was stopped at 0.75s.");
        out.println();
        out.println("cells   kind     hovers    ms");
        int[] counts = {27, 64, 125, 192, 216, 512, 1000, 1728, 4096, 8000};
        for (int count : counts) {
            int side = (int) Math.round(Math.cbrt(count));
            while (side * side * side < count) {
                side++;
            }
            int[] xs = new int[count];
            int[] ys = new int[count];
            int[] zs = new int[count];
            int p = 0;
            for (int z = 0; z < side && p < count; z++) {
                for (int y = 0; y < side && p < count; y++) {
                    for (int x = 0; x < side && p < count; x++) {
                        xs[p] = x;
                        ys[p] = y;
                        zs[p] = z;
                        p++;
                    }
                }
            }
            long t0 = System.nanoTime();
            int ops = legacyPairs(xs, ys, zs, 750_000_000L);
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            out.println(String.format(Locale.US, "%5d   dense    %7d   %8.2f", count, ops, ms));
            if (ms > 700) {
                break;
            }
        }
        out.println();
        out.println("A line never fills a 19-ball, so the inner loop cannot stop early:");
        for (int count : new int[] {64, 192, 400, 800}) {
            int[] xs = new int[count];
            int[] ys = new int[count];
            int[] zs = new int[count];
            for (int i = 0; i < count; i++) {
                xs[i] = i;
            }
            long t0 = System.nanoTime();
            int ops = legacyPairs(xs, ys, zs, 750_000_000L);
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            out.println(String.format(Locale.US, "%5d   line     %7d   %8.2f", count, ops, ms));
            if (ms > 700) {
                break;
            }
        }
        out.println();
        out.println("Word-scan greedy (still picks the fullest hover, but a hover check is 19 bit tests):");
        for (int side : new int[] {8, 12, 16, 20, 24, 32}) {
            Shape shape = cube(side, false);
            Bits bits = new Bits(shape);
            bits.fill(shape);
            long t0 = System.nanoTime();
            int ops = greedyWord(bits, 1_500_000_000L);
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            out.println(String.format(Locale.US, "cube %2d  cells %6d  hovers %7d  leftover %6d  ms %8.2f",
                    side, side * side * side, ops, bits.remain, ms));
            if (ms > 1400) {
                break;
            }
        }
        out.println();
    }

    static void sectionZoo(java.io.PrintStream out, List<Shape> zoo) {
        out.println("## 4. Shapes from 1x1x1 through 100");
        out.println();
        out.println("planMs is the planner only (occupancy copy + hover choice), after JIT warmup on small shapes.");
        out.println("rasterMs is reading the shape once into a bitset. routeKB is the hover list if we stored it.");
        out.println("cap% is blocks per hover divided by 19. lb is ceil(blocks/19). crewNow is usefulCrewFor.");
        out.println("A planner with leftover did not finish the shape.");
        out.println();
        out.printf(Locale.US, "%-22s %-16s %10s %8s %8s %7s %6s %8s %8s %8s%n",
                "shape", "planner", "blocks", "hovers", "left", "cap%", "lb", "crewNow", "planMs", "rasterMs");
        for (Shape shape : zoo) {
            System.err.println("zoo " + shape.name);
            Bits master = new Bits(shape);
            long r0 = System.nanoTime();
            master.fill(shape);
            double rasterMs = (System.nanoTime() - r0) / 1_000_000.0;
            shape.cells = master.remain;
            long scratch = master.words.length * 8L;
            int reps = shape.cells < 2_000 ? 20 : shape.cells < 80_000 ? 3 : 1;
            if (shape.volume() >= 90_000) {
                reps = 1;
            }
            runLattice(out, shape, master, scratch, rasterMs, reps);
            runBits(out, shape, master, scratch, rasterMs, reps, "fit-scan", false, 0, false);
            if (shape.cells <= 120_000) {
                runBits(out, shape, master, scratch, rasterMs, reps, "fit-center", false, 1, false);
                runBits(out, shape, master, scratch, rasterMs, reps, "fit-corner", false, 2, false);
                runBits(out, shape, master, scratch, rasterMs, reps, "fit-face", false, 3, false);
                runBits(out, shape, master, scratch, rasterMs, reps, "fit-edge", false, 4, false);
            }
            if (shape.cells <= 80_000) {
                runBits(out, shape, master, scratch, rasterMs, 1, "local-center", true, 1, false);
            }
            runBits(out, shape, master, scratch, rasterMs, reps, "grid3+rest", false, -1, false);
            runBits(out, shape, master, scratch, rasterMs, reps, "face+rest", false, -2, false);
            runBits(out, shape, master, scratch, rasterMs, reps, "chunk16", false, -3, false);
            runBits(out, shape, master, scratch, rasterMs, reps, "chunk16-clip", false, -4, false);
            if (shape.sx() == 1 && shape.sz() == 1 || shape.sy() == 1 && shape.sz() == 1 || shape.sx() == 1 && shape.sy() == 1) {
                runFormulaLine(out, shape, rasterMs);
            }
            if (shape.sx() == 1 || shape.sy() == 1 || shape.sz() == 1) {
                runFormulaPlane(out, shape, master, scratch, rasterMs);
            }
        }
        out.println();
    }

    static int repsFor(Shape shape) {
        if (shape.volume() >= 90_000 || shape.cells >= 200_000) {
            return 1;
        }
        if (shape.cells < 2_000) {
            return 15;
        }
        return 2;
    }

    static void runLattice(java.io.PrintStream out, Shape shape, Bits master, long scratch, double rasterMs, int reps) {
        Stations last = null;
        long t0 = System.nanoTime();
        for (int r = 0; r < reps; r++) {
            last = latticeStations(shape);
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0 / reps;
        long covered = last.blockSum();
        int leftover = (int) (shape.cells - covered);
        int max = 0;
        for (int i = 0; i < last.n; i++) {
            max = Math.max(max, last.blocks[i]);
        }
        long[] travel = travel(last, shape);
        addRow(out, shape, "lattice", last.n, leftover, max, ms, last.n * 16L, scratch, rasterMs, travel);
        if (leftover != 0) {
            out.println("  ** lattice coverage mismatch on " + shape.name + " covered " + covered + " cells " + shape.cells);
        }
    }

    static void runBits(java.io.PrintStream out, Shape shape, Bits master, long scratch, double rasterMs, int reps,
            String algo, boolean local, int mode, boolean travelWanted) {
        Stations last = null;
        int leftover = 0;
        long t0 = System.nanoTime();
        for (int r = 0; r < reps; r++) {
            Bits bits = master.copy();
            Stations stations = new Stations();
            if ("grid3+rest".equals(algo)) {
                gridStep(bits, shape, 3, stations);
                int[] cells = packScan(shape, bits);
                firstFitOrder(bits, cells, false, stations);
            } else if ("face+rest".equals(algo)) {
                planeTiles(bits, shape, stations);
                int[] cells = packScan(shape, bits);
                firstFitOrder(bits, cells, false, stations);
            } else if ("chunk16".equals(algo)) {
                chunk16(bits, shape, false, stations);
            } else if ("chunk16-clip".equals(algo)) {
                chunk16(bits, shape, true, stations);
            } else {
                int[] cells = packScan(shape, master);
                int[] order = mode == 0 ? cells : orderBy(shape, cells, mode);
                firstFitOrder(bits, order, local, stations);
            }
            leftover = bits.remain;
            last = stations;
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0 / reps;
        int max = 0;
        if (last != null) {
            for (int i = 0; i < last.n; i++) {
                max = Math.max(max, last.blocks[i]);
            }
        }
        boolean wantTravel = travelWanted || "fit-scan".equals(algo) || "fit-center".equals(algo) || "lattice".equals(algo);
        long[] travel = wantTravel && last != null ? travel(last, shape) : new long[6];
        addRow(out, shape, algo, last == null ? 0 : last.n, leftover, max, ms, last == null ? 0 : last.n * 16L, scratch, rasterMs, travel);
    }

    static void runFormulaLine(java.io.PrintStream out, Shape shape, double rasterMs) {
        int n = Math.max(shape.sx(), Math.max(shape.sy(), shape.sz()));
        int ops = (n + 2) / 3;
        long[] none = new long[6];
        addRow(out, shape, "math-line", ops, 0, 3, 0.0001, 0, 0, rasterMs, none);
    }

    static void runFormulaPlane(java.io.PrintStream out, Shape shape, Bits master, long scratch, double rasterMs) {
        int axis = shape.sy() == 1 ? 1 : shape.sz() == 1 ? 2 : 0;
        Bits bits = master.copy();
        Stations stations = new Stations();
        claimPlaneFamily(bits, shape, axis, stations);
        int formulaOps = stations.n;
        int formulaLeft = bits.remain;
        int[] rest = packScan(shape, bits);
        firstFitOrder(bits, rest, false, stations);
        long[] none = new long[6];
        addRow(out, shape, formulaLeft == 0 ? "math-plane" : "math-plane-open", formulaOps, formulaLeft, 9, 0.0002, 0,
                scratch, rasterMs, none);
        if (formulaLeft != 0) {
            addRow(out, shape, "plane+rest", stations.n, bits.remain, 9, 0.0002, stations.n * 16L, scratch, rasterMs, none);
        }
    }

    static void addRow(java.io.PrintStream out, Shape shape, String algo, int ops, int leftover, int maxHit, double ms,
            long routeBytes, long scratch, double rasterMs, long[] travel) {
        Row row = new Row(shape.name, algo, shape.cells, shape.sx(), shape.sy(), shape.sz(), ops, leftover, maxHit, ms,
                routeBytes, scratch, travel);
        ROWS.add(row);
        int lb = (int) ((shape.cells + CAP - 1) / CAP);
        int crew = usefulCrew(shape.cells, shape.sx(), shape.sy(), shape.sz());
        out.printf(Locale.US, "%-22s %-16s %10d %8d %8d %6.1f %6d %8d %8.3f %8.3f%n",
                shape.name, algo, shape.cells, ops, leftover, row.capEff() * 100.0, lb, crew, ms, rasterMs);
    }

    static void sectionTravel(java.io.PrintStream out) {
        out.println("## 5. Where the 10 wrights start");
        out.println();
        out.println("Times are the slowest wright, in seconds (ticks/20). Work is split into 10 x-slabs,");
        out.println("each slab walked in a serpentine, and the walk is flipped if that end is closer to the start.");
        out.println("port = 8 blocks off the west face. pre = already hovering the first block (no walk-in).");
        out.println("Start tax is port minus pre: how much of the job is just arriving.");
        out.println();
        out.printf(Locale.US, "%-22s %-12s %8s %8s %8s %8s %8s %8s %8s%n",
                "shape", "planner", "port", "center", "corner", "face", "edge", "pre", "tax%");
        for (Row row : ROWS) {
            if (row.preTicks <= 0) {
                continue;
            }
            if (!("lattice".equals(row.algo) || "fit-scan".equals(row.algo) || "fit-center".equals(row.algo))) {
                continue;
            }
            if (!travelShape(row.shape)) {
                continue;
            }
            double tax = row.preTicks <= 0 ? 0 : 100.0 * (row.portTicks - row.preTicks) / row.preTicks;
            out.printf(Locale.US, "%-22s %-12s %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %7.1f%n",
                    row.shape, row.algo,
                    row.portTicks / 20.0, row.centerTicks / 20.0, row.cornerTicks / 20.0,
                    row.faceTicks / 20.0, row.edgeTicks / 20.0, row.preTicks / 20.0, tax);
        }
        out.println();
    }

    static boolean travelShape(String name) {
        return name.equals("cube 1") || name.equals("cube 3") || name.equals("cube 8") || name.equals("cube 16")
                || name.equals("cube 32") || name.equals("cube 100")
                || name.equals("sphere 16") || name.equals("sphere 32") || name.equals("sphere 100")
                || name.equals("hollow cube 32") || name.equals("hollow cube 100")
                || name.equals("hollow sphere 32") || name.equals("hollow sphere 100")
                || name.equals("wall 100") || name.equals("pillar 100") || name.equals("disk 100")
                || name.equals("hall 100x8x40") || name.equals("shell 100x40x40")
                || name.equals("pyramid 64x64") || name.equals("dome 64x32")
                || name.equals("cyl 100x20") || name.equals("slab 100x1x100");
    }

    static void sectionTicks(java.io.PrintStream out) {
        out.println("## 6. Server tick cost, now versus a streamed hover");
        out.println();
        int radius = 8;
        int visits = 0;
        int sink = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    visits++;
                    sink += dx * dx + dy * dy + dz * dz;
                }
            }
        }
        int cluster = 0;
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    cluster++;
                }
            }
        }
        long loops = 400;
        long t0 = System.nanoTime();
        for (int n = 0; n < loops; n++) {
            int local = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        local += d2 <= 64 ? 1 : 0;
                        local += (dx * 3 + dy * 5 + dz) & 15;
                    }
                }
            }
            sink += local;
        }
        double nsProbe = (System.nanoTime() - t0) / (double) loops;
        int[] cursor = new int[10];
        t0 = System.nanoTime();
        for (int n = 0; n < loops * 50; n++) {
            for (int w = 0; w < CREW; w++) {
                cursor[w] = cursor[w] + 19;
                sink += cursor[w];
            }
        }
        double nsStream = (System.nanoTime() - t0) / (double) (loops * 50);
        out.println("Probe the live code uses when it has no stored hover (FLY_NEAR_RADIUS = 8):");
        out.println("  cells visited per wright per new hover: " + visits + " (17^3). Near cluster is " + cluster + " (9^3).");
        out.println("  measured arithmetic only: " + String.format(Locale.US, "%.0f ns", nsProbe) + " per probe. World checks are not in that number.");
        out.println("  streamed next hover, 10 wrights: " + String.format(Locale.US, "%.0f ns", nsStream) + " (sink " + (sink & 1) + ").");
        out.println();
        out.println("If each visited cell also does a block lookup + permission + shape test, at an assumed");
        out.println("200 ns each (not measured — a floor for 'maybe cheap' and a ceiling for 'getBlockState plus rules'):");
        double assumed = 200.0;
        double perWrightUs = visits * assumed / 1000.0;
        out.println(String.format(Locale.US, "  one wright: %.2f ms of lookups", perWrightUs / 1000.0));
        for (int jobs : new int[] {1, 5, 10, 20}) {
            double ms = jobs * CREW * visits * assumed / 1_000_000.0;
            out.println(String.format(Locale.US, "  %2d jobs × 10 wrights: %7.2f ms per server tick   %s",
                    jobs, ms, ms > 50 ? "OVER a 50 ms tick" : ms > 10 ? "heavy" : "ok"));
        }
        out.println("A streamed plan checks the ≤19 cells it is about to place, not the 4,913-cell cube around the wright.");
        out.println(String.format(Locale.US, "  20 jobs × 10 × 19 × 200 ns = %.2f ms per tick.",
                20 * 10 * 19 * assumed / 1_000_000.0));
        out.println();
        out.println("Up-front plan, if every job planned on the same tick (lattice planMs is in section 4):");
        out.println("  10 players × 2 jobs = 20 plans. Anything under ~1 ms per plan stays inside a tick even as a herd.");
        out.println("  Pair search is already illegal past 192 blocks because one plan can exceed a tick.");
        out.println();
        // Tier throughput on a solid 32 cube using the lattice groups, extra place ticks when cap < ball.
        out.println("Same lattice groups, lower tiers stay on the hover until the ball is empty (no extra fly):");
        out.println("tier        cap  reach  place-ticks per full ball   blocks/tick/wright");
        int[] caps = {1, 3, 7, 11, 15, 19};
        int[] reach = {0, 1, 1, 2, 2, 2};
        String[] names = {"basic", "copper", "iron", "gold", "diamond", "netherite"};
        for (int i = 0; i < caps.length; i++) {
            int bursts = (reach[i] < 2) ? -1 : (CAP + caps[i] - 1) / caps[i];
            String rate = bursts < 0 ? "different ball" : String.format(Locale.US, "%.2f", CAP / (double) bursts);
            out.println(String.format(Locale.US, "%-10s  %3d   %3d    %s                         %s",
                    names[i], caps[i], reach[i], bursts < 0 ? "n/a (7-cell cross)" : Integer.toString(bursts), rate));
        }
        out.println("Gold and diamond keep the netherite groups: two place ticks clear 19, still no fly inside the ball.");
        out.println("Copper and iron use a smaller cross (squared distance ≤ 1, 7 cells) and need their own tiling.");
        out.println();
    }

    /**
     * Full AOE (Netherite sphere, cap 19) against no AOE upgrade (cap 1, stand on the block).
     * Flight stays at netherite speed so the only change is the sphere. Crew is 1 or 10.
     */
    static void sectionCrewAoe(java.io.PrintStream out) {
        out.println("## 8. One wright versus ten, full AOE versus none");
        out.println();
        out.println("Full AOE is the 19-cell sphere. No AOE upgrade places only the block the wright stands on");
        out.println("(reach squared 0, cap 1). Flight speed is still netherite, so this is the AOE upgrade alone.");
        out.println("pre1 / pre10 are seconds for the slowest wright, already at its first block.");
        out.println("ideal is the same hovers walked with no fly tick (adjacent step, or the diagonal on the lattice).");
        out.println("A reused full-AOE center list with cap 1 places one block per center and leaves the rest.");
        out.println();
        out.printf(Locale.US, "%-18s %10s %8s %8s %8s %8s %8s %8s %8s %8s%n",
                "shape", "blocks", "fullH", "pre10", "pre1", "ratio", "noneH", "none10", "none1", "miss%");
        Shape[] shapes = {
                cube(1, false), cube(3, false), cube(8, false), cube(16, false), cube(32, false), cube(100, false),
                sphere(16, false), sphere(100, false),
                box("wall 100", 100, 100, 1, false),
                box("pillar 100", 1, 100, 1, false),
                cube(32, true), cube(100, true)
        };
        for (Shape shape : shapes) {
            System.err.println("crew/aoe " + shape.name);
            Bits bits = new Bits(shape);
            bits.fill(shape);
            shape.cells = bits.remain;
            Stations full = latticeStations(shape);
            long[] full10 = travel(full, shape, 10);
            long[] full1 = travel(full, shape, 1);
            int hop = flyTicks(0, 0, 0, STEP_X, STEP_Y, STEP_Z);
            long ideal10 = idealTicks(full.n, 10, hop);
            long ideal1 = idealTicks(full.n, 1, hop);
            Stations none = everyCell(shape);
            long[] none10 = travel(none, shape, 10);
            long[] none1 = travel(none, shape, 1);
            long missed = shape.cells - full.n;
            double miss = shape.cells <= 0 ? 0 : 100.0 * missed / shape.cells;
            double ratio = full10[5] <= 0 ? 0 : full1[5] / (double) full10[5];
            out.printf(Locale.US, "%-18s %10d %8d %8.1f %8.1f %8.2f %8d %8.1f %8.1f %7.1f%n",
                    shape.name, shape.cells, full.n,
                    full10[5] / 20.0, full1[5] / 20.0, ratio,
                    none.n, none10[5] / 20.0, none1[5] / 20.0, miss);
            out.printf(Locale.US, "%-18s %10s %8s %8.1f %8.1f %8.2f %8s %8.1f %8.1f%n",
                    "  ideal no-fly", "", "",
                    ideal10 / 20.0, ideal1 / 20.0,
                    ideal10 <= 0 ? 0 : ideal1 / (double) ideal10,
                    "",
                    idealTicks(none.n, 10, 0) / 20.0,
                    idealTicks(none.n, 1, 0) / 20.0);
        }
        out.println();
        out.println("Hover counts do not change with crew. A second wright does not place a bigger sphere.");
        out.println("Ten wrights finish in about 1/10 the time only while there are at least 10 hovers.");
        out.println("A 3x3x3 solid is 3 hovers with full AOE, so a tenth wright has nothing to stand on.");
        out.println("No AOE upgrade makes every block its own hover. The mod-11 centers then skip most of the cube;");
        out.println("miss% is the share left unplaced if those centers are reused and only one block is set down.");
        out.println();
    }

    static long idealTicks(int hovers, int crewCap, int hopFly) {
        if (hovers <= 0) {
            return 0;
        }
        int used = Math.min(Math.max(1, crewCap), hovers);
        int per = (hovers + used - 1) / used;
        return (long) per + (long) Math.max(0, per - 1) * hopFly;
    }

    /** One hover per block. This is the whole plan when the AOE upgrade is absent. */
    static Stations everyCell(Shape shape) {
        Stations stations = new Stations();
        for (int z = shape.z0; z <= shape.z1; z++) {
            for (int y = shape.y0; y <= shape.y1; y++) {
                for (int x = shape.x0; x <= shape.x1; x++) {
                    if (shape.contains(x, y, z)) {
                        stations.add(x, y, z, 1);
                    }
                }
            }
        }
        return stations;
    }

    static void sectionScores(java.io.PrintStream out) {
        out.println("## 7. Scores");
        out.println();
        out.println("Efficiency: 100 means as few hovers as the best finisher on that shape.");
        out.println("Cap use: 100 means every hover placed 19. Shells and lines cannot reach that; physics, not a bug.");
        out.println("Plan speed: 100 under 0.05 ms, 90 under 1 ms, 70 under 5 ms, 40 under 16 ms, 15 under 50 ms, 0 after that.");
        out.println("Scale: 20 plans (10 players, 2 jobs) on one tick. 100 if that herd is under 5 ms, 80 under 20, 50 under 50, 10 if it spills a tick.");
        out.println("Memory: streamed lattice keeps a cursor per wright. A stored route is 16 bytes × hovers. Scratch bitset is bbox/8.");
        out.println("Build: 100 means the pre-placed crew matches 1 place tick + 1 fly tick per hover, split across min(10, hovers).");
        out.println("A planner that leaves blocks out is scored 0 on efficiency.");
        out.println();
        String[] algos = {
                "lattice", "fit-scan", "fit-center", "fit-corner", "fit-face", "fit-edge",
                "local-center", "grid3+rest", "face+rest", "chunk16", "chunk16-clip", "math-line", "math-plane"
        };
        out.printf(Locale.US, "%-16s %8s %8s %8s %8s %8s %8s %8s %10s%n",
                "planner", "shapes", "eff", "capUse", "speed", "scale", "mem", "build", "medianMs");
        for (String algo : algos) {
            Score s = score(algo);
            if (s.n == 0) {
                continue;
            }
            out.printf(Locale.US, "%-16s %8d %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %10.3f%n",
                    algo, s.n, s.eff, s.cap, s.speed, s.scale, s.mem, s.build, s.medianMs);
        }
        out.println();
        out.println("Per size class, lattice cap use and hover count versus ceil(blocks/19):");
        out.printf(Locale.US, "%-22s %10s %8s %8s %8s %8s%n", "shape", "blocks", "lb", "lattice", "cap%", "gap%");
        for (Row row : ROWS) {
            if (!"lattice".equals(row.algo) || !row.ok()) {
                continue;
            }
            if (!row.shape.startsWith("cube ") && !row.shape.startsWith("sphere ") && !row.shape.startsWith("hollow")
                    && !row.shape.startsWith("wall") && !row.shape.startsWith("pillar") && !row.shape.startsWith("hall")
                    && !row.shape.startsWith("shell") && !row.shape.startsWith("pyramid") && !row.shape.startsWith("dome")
                    && !row.shape.startsWith("cyl") && !row.shape.startsWith("disk") && !row.shape.startsWith("slab")
                    && !row.shape.startsWith("plate") && !row.shape.startsWith("beam")) {
                continue;
            }
            int lb = (int) ((row.cells + CAP - 1) / CAP);
            double gap = lb <= 0 ? 0 : 100.0 * (row.ops - lb) / lb;
            boolean interesting = row.shape.endsWith(" 1") || row.shape.endsWith(" 3") || row.shape.endsWith(" 8")
                    || row.shape.endsWith(" 16") || row.shape.endsWith(" 32") || row.shape.endsWith(" 64")
                    || row.shape.endsWith(" 100") || row.shape.contains("100") || row.shape.contains("64")
                    || row.shape.contains("32");
            if (!interesting) {
                continue;
            }
            out.printf(Locale.US, "%-22s %10d %8d %8d %7.1f %7.1f%n",
                    row.shape, row.cells, lb, row.ops, row.capEff() * 100.0, gap);
        }
        out.println();
    }

    static final class Score {
        int n;
        double eff;
        double cap;
        double speed;
        double scale;
        double mem;
        double build;
        double medianMs;
    }

    static Score score(String algo) {
        List<Row> mine = new ArrayList<>();
        for (Row row : ROWS) {
            if (algo.equals(row.algo)) {
                mine.add(row);
            }
        }
        Score s = new Score();
        s.n = mine.size();
        if (mine.isEmpty()) {
            return s;
        }
        double eff = 0;
        double cap = 0;
        double speed = 0;
        double scale = 0;
        double mem = 0;
        double build = 0;
        int buildN = 0;
        double[] times = new double[mine.size()];
        int i = 0;
        for (Row row : mine) {
            times[i++] = row.planMs;
            int best = bestOps(row.shape);
            if (!row.ok() || best <= 0) {
                eff += 0;
            } else {
                eff += 100.0 * best / row.ops;
            }
            cap += row.ok() ? row.capEff() * 100.0 : 0;
            speed += speedScore(row.planMs);
            scale += scaleScore(row.planMs);
            mem += memScore(row);
            if (row.preTicks > 0 && row.ok()) {
                int crew = (int) Math.min(CREW, row.ops);
                int per = (row.ops + crew - 1) / crew;
                int hop = flyTicks(0, 0, 0, STEP_X, STEP_Y, STEP_Z);
                long lb = (long) per + (long) Math.max(0, per - 1) * hop;
                build += lb <= 0 ? 100 : Math.min(100.0, 100.0 * lb / row.preTicks);
                buildN++;
            }
        }
        s.eff = eff / mine.size();
        s.cap = cap / mine.size();
        s.speed = speed / mine.size();
        s.scale = scale / mine.size();
        s.mem = mem / mine.size();
        s.build = buildN == 0 ? 0 : build / buildN;
        Arrays.sort(times);
        s.medianMs = times[times.length / 2];
        return s;
    }

    static int bestOps(String shape) {
        int best = Integer.MAX_VALUE;
        for (Row row : ROWS) {
            if (!shape.equals(row.shape) || !row.ok()) {
                continue;
            }
            if ("math-plane-box".equals(row.algo)) {
                continue;
            }
            best = Math.min(best, row.ops);
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    static double speedScore(double ms) {
        if (ms < 0.05) {
            return 100;
        }
        if (ms < 1) {
            return 90;
        }
        if (ms < 5) {
            return 70;
        }
        if (ms < 16) {
            return 40;
        }
        if (ms < 50) {
            return 15;
        }
        return 0;
    }

    static double scaleScore(double ms) {
        double herd = ms * 20.0;
        if (herd < 5) {
            return 100;
        }
        if (herd < 20) {
            return 80;
        }
        if (herd < 50) {
            return 50;
        }
        return 10;
    }

    static double memScore(Row row) {
        if ("lattice".equals(row.algo) || row.algo.startsWith("math")) {
            return 100;
        }
        if (row.scratchBytes <= 64 * 1024) {
            return 90;
        }
        if (row.scratchBytes <= 1024 * 1024) {
            return 75;
        }
        return 40;
    }

    static final class Tee extends java.io.OutputStream {
        final java.io.OutputStream a;
        final java.io.OutputStream b;

        Tee(java.io.OutputStream a, java.io.OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int value) throws java.io.IOException {
            a.write(value);
            b.write(value);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws java.io.IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }

        @Override
        public void flush() throws java.io.IOException {
            a.flush();
            b.flush();
        }
    }
}
