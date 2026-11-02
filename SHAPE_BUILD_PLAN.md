# Shape hover plan

Isolated measurement of how a wright should stand and how many blocks one hover should place. Nothing here is wired into `JobManager` yet. The numbers come from `tools/shapeplan/ShapePlanSim.java`, run on the same sphere the game uses.

Netherite, which is the default this study holds fixed:

- The wright stands on one block and may touch every block whose squared distance is at most 2. That is the 3×3×3 cube without its eight corners: **19 blocks**.
- One hover places at most 19 of those, and it places them in one server tick (`operationCap` / `placeBudget`).
- A job gets at most **10 wrights** (`maxWrightsPerJob`, default 10). The sim takes that as an input; the same dispatch works if the config changes.
- Flight is netherite close speed, 2.75 blocks/tick, 2.1× past 8 blocks. The wright is “there” once it is inside 1.5 blocks of the hover (`DRONE_REACH_SQR` is 2.25).

Re-run:

```
javac -d tools/shapeplan/out tools/shapeplan/ShapePlanSim.java
java -cp tools/shapeplan/out ShapePlanSim
```

Raw tables land in `tools/shapeplan/results.txt`. Shapes follow the same inclusion tests as `Shapes` (solid and hollow cubes, spheres, cylinders, pyramids, domes, disks, walls, pillars), from 1×1×1 through 100×100×100.

## The ceiling

A repeating crawl cannot place 19 fresh blocks every tick.

Two hovers whose spheres are disjoint have to stand at least 3 blocks apart (`(2,2,1)` and `(3,0,0)` share nothing). That is already outside the 1.5-block arrive radius, so the wright spends a fly tick between them: 19 blocks / 2 ticks = **9.5 blocks per tick** per wright.

A one-block diagonal step does better. Standing at `(1,1,0)` relative to the previous hover shares 8 blocks and uncovers **11 new ones**, and the step is 1.41, inside the arrive radius, so there is no fly tick. No integer step that is still inside 1.5 blocks uncovers more than 11. Steps that uncover 13–19 all cost the fly tick and come out slower (6.5–9.5 blocks/tick).

| Step | Distance | Shared | New blocks | Fly ticks | Blocks/tick |
| --- | ---: | ---: | ---: | ---: | ---: |
| (1,0,0) | 1.00 | 10 | 9 | 0 | 9.0 |
| (1,1,0) | 1.41 | 8 | 11 | 0 | **11.0** |
| (1,1,1) | 1.73 | 6 | 13 | 1 | 6.5 |
| (2,0,0) | 2.00 | 5 | 14 | 1 | 7.0 |
| (2,2,0) | 2.83 | 1 | 18 | 1 | 9.0 |
| (2,2,1) | 3.00 | 0 | 19 | 1 | 9.5 |

So the fast pattern is not “fill the sphere”. It is “place 11, slide one diagonal, place 11 again”.

There is a lattice that does exactly that. Centers are the blocks where

```
x + y + 4z ≡ 0  (mod 11)
```

Every block in space belongs to exactly one of those centers, the blocks of a center all sit inside its 19-sphere, and the shortest hop is `(1,-1,0)`. Interior hovers place 11. A 100³ cube measured 10.37 per hover (the missing 0.63 is the crust, where the sphere sticks out of the build).

No lattice fills a full 19. Every index-19 lattice is a linear form modulo 19 (19 is prime), and every such form collides inside the sphere. A separate exact-cover search proved the same thing on the smallest torus where the sphere does not wrap (19×3×3, 58,595 nodes, 27 ms).

Ten wrights at 11 blocks/tick place 2,200 blocks/second. A million-block cube is about **7.6 minutes** of placing once they are on the pattern, before any walk in from the port. Chasing disjoint 19s is slower (about 8.8 minutes) because of the fly tick.

Gold’s cap is 11, so this same grouping is one tick for gold too. Diamond (cap 15) and netherite (cap 19) do not go faster by staying put to empty a bigger sphere: the extra blocks are the ones the diagonal step was going to pick up next, and waiting costs the tick the slide would have used.

## What the live planner does

Two different mechanisms, both in `JobManager`.

**Crew size for a shape of at most 192 blocks** runs `planShapeStations`. It tries every remaining block as the next stand point and keeps the one that covers the most. The stand point has to be a block that still needs placing. Above 192 blocks it refuses and `usefulCrewFor` divides each side of the box by 3.

That pair search is cheap at the cap and then falls off a cliff. Times are the nested loop only, no world calls. A negative hover count means it was cut at 0.75 s.

| Blocks | Shape | Hovers | Time |
| ---: | --- | ---: | ---: |
| 27 | solid 3³ | 9 | 0.03 ms |
| 192 | solid | 29 | 0.6 ms |
| 512 | solid | 63 | 4.5 ms |
| 1,000 | solid | 115 | 24 ms |
| 4,096 | solid | 459 | 523 ms |
| 8,000 | solid | stopped | 756 ms |
| 192 | straight line | 64 | 1.2 ms |
| 800 | straight line | 267 | 73 ms |

A line is slower than a cube of the same count, because nothing along a line ever fills 19, so the inner loop cannot stop early. Scaled as time ∝ (blocks)² from the 4,096-block run, a million-block solid is on the order of **hours**. The 192 cap exists so that a click cannot hitch the server. The cap is right. The substitute is not.

`usefulCrewFor` on a 3×3×3 box returns **1** wright. The pair search on those 27 blocks returns **9** hovers. Both are the wrong crew for a solid 3³, which finishes in **3** hovers if the wright is allowed to stand on a block it has already placed (see below). A 100-block pillar also returns 1, because the column cap is `max(width, depth)` and a vertical post has both equal to 1. A horizontal beam of the same length is allowed 34 stations. Ten wrights fit on that beam; the pillar currently gets one.

**While the job is running**, a large shape does not use the pair search. Each wright, every time it needs a new hover, probes a 17×17×17 cube around itself (`FLY_NEAR_RADIUS` is 8: 4,913 cells) and stands on the nearest block that still needs placing. The pure arithmetic of that probe measured **20 µs**. That part is fine. The probe also classifies each cell (shape test, permissions, block lookup). At an assumed 200 ns of world work per visited cell — an assumption, not a measurement — the tick cost is:

| Jobs | 10 wrights, 4,913 visits each | Against a 50 ms tick |
| ---: | ---: | --- |
| 1 | 9.8 ms | fine |
| 5 | 49 ms | the whole tick |
| 10 | 98 ms | over |
| 20 | 197 ms | over |

A streamed hover does not search. The next stand point is a few integer adds. Ten of those measured **2 ns**. The world is then read only for the blocks about to be placed (at most 19), which for 20 jobs is under 1 ms even at the same 200 ns assumption.

Standing on the nearest leftover block also wastes the sphere. On a solid cube, “claim whatever sphere is around the next unscanned block” settles at **4.0 blocks per hover** (21% of 19) from 8³ up through 100³. Starting at the center, a corner, a face middle, or an edge middle does not change that once the first hole is punched: the next stand point sits on the edge of the hole. Splitting the box into the live 16³ chunks (`SHAPE_CUBE`) matches that same 4.0, clipped or not.

## Small shapes, where a search still pays

For a few thousand blocks, score every legal stand point around the next seed and keep the fullest. The stand point may be air or a block that was already placed. That is the whole change that turns a solid 3³ from 9 hovers into 3, and it is what the hollow-middle comment in the placer is already trying to do.

| Shape | Blocks | Pair search (stand on a leftover) | Local fullest (stand anywhere in the sphere) | Mod-11 lattice |
| --- | ---: | ---: | ---: | ---: |
| Solid 1³ | 1 | 1 | 1 | 1 |
| Solid 3³ | 27 | 9 | **3** | 9 |
| Sphere, diameter 3 (the 19-cell sphere) | 19 | 1 if you start at its middle | **1** | 7 |
| Solid 8³ | 512 | 63 (word scan, 1.6 ms) | **54** (0.15 ms) | 85 |
| Solid 16³ | 4,096 | 459 (523 ms) | **366** (0.56 ms) | 521 |
| Solid 32³ | 32,768 | 3,539 (559 ms) | **2,683** (4.0 ms) | 3,552 (0.08 ms) |

The 3³ is the one players will notice. The center hover places the 19 non-corners. Each corner is squared-distance 3 from the center, so it is outside that hover, and no two corners are inside each other’s sphere. If the wright may only stand on a block that still needs placing, each corner is its own hover: 1 + 8 = 9. If it may stand again on the face it just built, one face covers four corners and the opposite face covers the other four: **3 hovers, 3 wrights, not 9 and not 1**.

Past about 4,000 blocks the local search is still the best packing and it stops being cheap (4 ms at 32³, and it was not run at 100³). It also cannot beat the 11-block crawl on a large solid: 12.2 blocks per hover at 32³ is only possible if some of those hovers are more than 1.5 apart, and the shift table says a repeating step cannot uncover more than 11 without a fly tick. Use the local search only while the whole shape is small. Use the lattice once it is large.

## Fat shapes

Mod-11 lattice, walked along `(1,-1,0)`, one wright per slab of the lattice.

| Shape | Blocks | Lower bound (÷19) | Lattice hovers | Blocks/hover | Plan time (build the whole list) | Build, 10 wrights, list walked in x-slabs | Build if each hop is the short diagonal |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Cube 16 | 4,096 | 216 | 521 | 7.9 | 0.07 ms | 8.0 s | ~2.6 s |
| Cube 32 | 32,768 | 1,725 | 3,552 | 9.2 | 0.08 ms | 37 s | ~18 s |
| Cube 64 | 262,144 | 13,798 | 26,095 | 10.0 | 0.49 ms | — | ~2.2 min |
| Cube 100 | 1,000,000 | 52,632 | 96,410 | 10.4 | 1.8 ms | 21.0 min | **8.0 min** |
| Sphere 100 | 523,984 | 27,579 | 50,816 | 10.3 | 4.0 ms | 13.1 min | ~4.2 min |
| Hall 100×8×40 | 32,000 | 1,685 | 3,864 | 8.3 | 0.15 ms | 50 s | ~19 s |
| Cylinder 100×20 | 157,200 | 8,274 | 16,370 | 9.6 | — | 3.8 min | ~1.4 min |
| Pyramid 64 | 89,440 | 4,708 | 9,655 | 9.3 | — | 2.0 min | ~48 s |
| Dome 64×32 | 70,296 | 3,700 | 7,449 | 9.4 | 0.76 ms | 1.5 min | ~37 s |

“Plan time” is the cost of writing every hover into a list. A million-block cube is 1.8 ms and about 1.5 MB (16 bytes × 96,410). Twenty such lists on the same tick is 36 ms and 30 MB. The wright does not need the list. It stores the last center and adds `(1,-1,0)`, skipping any center whose 11 blocks miss the shape. Startup is a handful of integers. The 1.8 ms figure is the upper bound if someone materializes the route anyway.

The x-slab times are what the sim actually flew: sort by x, split across 10 wrights, serpentine. That path is about **2.6 ticks per hover** for both the lattice and the corner crawl, so the lattice’s only win on that path is “fewer hovers” (21 min versus 54 min for the crawl on a 100³). The other half of the win, down to 8 minutes, is walking the short diagonal so the fly tick disappears. Shipping the lattice without that walk leaves the fly tick in.

Scan-from-a-corner on the same 100³ cube: 250,000 hovers, 13.9 ms to plan, **54 minutes** to build. The 3-step grid plus a cleanup pass: 114,544 hovers, 6.7 ms, still more hovers than the lattice and it does not give you the 1.41-block hop.

## Flats, lines, shells

A flat wall never holds more than 9 blocks of the sphere (the 3×3 in the plane; the rest of the sphere is air). A straight line never holds more than 3. Dividing those by 19 makes a correct algorithm look bad. The right comparison is against 9 and against 3.

| Shape | Blocks | Geometric max | Easy math | Hovers | Plan | Lattice hovers |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| Pillar 100 | 100 | 3 per hover | step 3 along the post | **34** | ~0 | 64 |
| Beam 2×100 | 200 | 6 | 3-step tiles | **34** | ~0 | 92 |
| Wall 100×100 | 10,000 | 9 | `ceil(n/3)²` | **1,156** | ~0 | 2,800 |
| Disk 100 | 7,860 | 9 | same tiles, skip empty ones | **916** | ~0 | 2,193 |
| Slab 100×1×100 | 10,000 | 9 | same | **1,156** | ~0 | 2,792 |
| Hollow cube 100 | 58,808 | 9 | tile each face, then leftovers | **6,734** (1.5 ms) | 1.5 ms | 15,980 |
| Hollow sphere 100 | 31,016 | ~6 (curved) | face tiles are the wrong tool | 7,702 | 2.0 ms | 8,882 |

The wall’s 1,156 hovers place 8.65 blocks each, against a hard maximum of 9. The hollow cube’s face tiles place 8.73, same story. Local search on that shell found 6,641 hovers and took 5.4 ms; the extra 1% is not worth a search. On the hollow sphere the faces are curved, face tiles fall behind, and the local search is the one that wins (5,128 hovers, 7.6 ms). That 7.6 ms is acceptable for one job and is not acceptable for twenty jobs on the same tick (150 ms). For a curved shell, score only the **next** hover (19 candidate stand points × 19 blocks) when the wright arrives. That is the same choice as the up-front search, spread across the build, and it is microseconds per tick.

A pillar of 100 is 34 hovers. Ten wrights finish it in under a second once they are standing on it. The live column cap would send one wright and take about ten times as long.

## Where they start

Ten wrights, slowest wright, seconds. Port is 8 blocks off the west face. “Pre” means the wright is already on its first hover.

| Shape | Pattern | Port | Center | Corner | Face middle | Edge middle | Pre | Port tax |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Cube 1 | any | 0.20 | 0.05 | 0.05 | 0.05 | 0.05 | 0.05 | the whole job |
| Cube 3 | lattice / scan | 0.25 | 0.10 | 0.10 | 0.05–0.10 | 0.05–0.10 | 0.05 | most of the job |
| Cube 8 | lattice | 1.35 | 1.25 | 1.20 | 1.20 | 1.20 | 1.15 | 17% |
| Cube 16 | lattice | 8.15 | 8.15 | 8.20 | 8.15 | 8.10 | 7.95 | 2.5% |
| Cube 32 | lattice | 37.7 | 37.7 | 37.6 | 37.6 | 37.6 | 37.3 | 0.9% |
| Cube 100 | lattice | 1261.6 | 1261.3 | 1261.4 | 1261.2 | 1261.0 | 1260.5 | 0.1% |
| Wall 100 | lattice | 31.4 | 31.1 | 30.6 | 31.0 | 31.0 | 30.5 | 3% |
| Pillar 100 | scan | 1.35 | 0.90 | 0.90 | 0.75 | 0.75 | 0.45 | the walk matters |

Past a 16³, changing the launch corner, the face, the edge, or the middle moves the finish by less than the noise in the path. The 100³ port tax is one second on a 21-minute slab walk. Small jobs are the opposite: a single block is one place tick (0.05 s) plus the flight from the port (0.15 s). Send one wright. Do not plan a formation.

The pattern matters far more than the launch point. On the 100³ the corner crawl took 54 minutes from every launch point. The lattice took 21 minutes from every launch point, and 8 minutes if the walk follows the diagonal. Launching “from the middle” does not fix a 4-block hover.

## Scores

0–100. Higher is better.

- **Efficiency** — hovers compared with the best finisher on that shape, or with the 11-block crawl on a large solid. 100 means no wasted hover.
- **Plan speed** — time to decide, on the largest shape that planner was allowed to touch. 100 under 0.05 ms, 90 under 1 ms, 70 under 5 ms, 40 under 16 ms, 15 under 50 ms, 0 after a tick.
- **Scale** — 20 plans or 20 live jobs (10 players, 2 jobs) inside one 50 ms tick. 100 if that herd is under 5 ms, 80 under 20, 50 under 50, 10 if it spills the tick.
- **Memory** — extra per job while the build runs. 100 is a cursor per wright. A stored million-block route is about 1.5 MB and scores lower.
- **Build** — placed blocks per second against the ceiling for that shape (11/tick on a solid, 9/tick with a fly on a flat, 3/tick with a fly on a line), with 10 wrights.

| Planner | Use it when | Eff | Speed | Scale | Mem | Build |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| **Dispatch below** | every shape | 95 | 100 | 100 | 100 | 90 |
| Mod-11 lattice, short diagonal | solids, fat boxes, spheres, domes, pyramids | 90 | 100 streamed (90 if the list is built up front: 1.8–4 ms) | 100 | 100 | 90 |
| Local fullest stand | ≤ ~4,000 blocks, and the next hover of a curved shell | 100 on those shapes | 90 at 16³ (0.6 ms), 70 at 32³ (4 ms) | 80 under 16³, 10 at 32³ × 20 jobs | 75 (scratch bitset) | 95 on small shapes |
| Face tiles | hollow boxes | 97 vs the flat maximum of 9 | 90 (1.5 ms at hollow 100³) | 80 | 75 | 90 |
| Plane / line formula | one side is 1 block, or two sides are | 100 vs 9 or vs 3 | 100 | 100 | 100 | 95 |
| 3-step grid + cleanup | nowhere it wins | 70–80 | 70 (6.7 ms at 100³) | 50 | 75 | 60 |
| Stand on the next block (scan, center, corner, face, edge, 16³ chunks) | nowhere | 50 | 40–90 | 50–90 | 75 | 30 on a solid (4 blocks/hover) |
| Pair search, today’s `planShapeStations` | nowhere past a toy | 40 (9 hovers on a 3³) | 0 past ~1,000 blocks | 10 | 40 | 40 |
| Per-tick 4,913-cell probe | nowhere | same as “stand on the next block” | the probe itself is 20 µs of math | 10 once world lookups join in | 100 | 30 |

The dispatch scores higher than any single row because each shape gets the row that fits it. A pillar is not sent through the solid lattice (64 hovers instead of 34). A 100³ cube is not sent through the local search (multi-millisecond, and it cannot beat 11/tick).

Client and server split cleanly. The client only needs an ETA and a crew count, both closed form: `ceil(length/3)` on a line, `ceil(w/3)·ceil(d/3)` on a plate, `blocks/11` on a fat solid, `surface/9` on a box shell. No block list, no world. The server streams stand points and never builds the route.

## The isolated planner to build next

One small type, used by crew assignment and by the placer, with `JobManager` no longer searching.

```
next hover(shape, wright index, wright count) -> stand position + the blocks of that hover
```

State per wright is the last center. No bitset, no block list, no probe.

1. **Line.** Two of the three sizes are 1. Stand every 3 blocks along the long axis. Crew is `min(config, ceil(length/3))`, including vertical pillars.
2. **Plate.** One size is 1, including disks. Tile that plane by 3. Skip a tile that misses the disk. Crew is `min(config, tile count)`.
3. **Box shell.** Hollow box, or any shape whose sample of stand points never sees more than 9 blocks. Tile each face by 3, and let a face tile keep the edge blocks it reaches so edges are not placed twice. Crew is `min(config, tile count)`.
4. **Small volume.** Under about 4,000 blocks (a 16³ is 0.6 ms). Score the 19 possible stand points that can see the next seed, including air and blocks already placed. This is the 3-hover solid cube and the 1-hover 19-cell sphere. Crew is the hover count, capped by config. A solid 3³ asks for 3 wrights, not 1 and not 9.
5. **Fat volume.** Everything else: sphere, cylinder, pyramid, dome, hall, solid cube. Centers `x + y + 4z ≡ 0 (mod 11)`. Each wright walks its slab by `(1,-1,0)`. A center with no blocks left in the shape is skipped. Crew is `min(config, center count)`, which for anything bigger than a few blocks is just the config value (10 today).

Do not keep `planShapeStations` behind the 192 cap. At 192 blocks it is already slower and worse than step 4 (0.6 ms and 9 hovers on a 3³, versus 0.01 ms and 3). Do not keep the 17³ probe as a fallback for large jobs. That probe is the multi-job lag, and the stand point it picks is the 4-block hover.

`usefulCrewFor` should return the hover count from this dispatch, still clamped by `maxWrightsPerJob`. The “divide each side by 3, then clamp to the footprint columns” estimate is what assigns one wright to a 3³ and one wright to a pillar.

## What this does not change

Placing still happens on the server, one hover per wright per tick, cap 19. The 8-minute figure for a 100³ cube is the placing itself, with ten netherite wrights and no item, energy, or chunk stalls. No planner makes that cube finish in a second; 11 fresh blocks per tick per wright is the repeating maximum the sphere allows.

Start position is a one-time flight. It is worth caring about for a post or a 3³. It is not worth a search on a hall.

The sim does not place blocks into a world, so it does not model item stalls, energy, or unloaded chunks. Those add time on top of the hover clock. They do not change which hover pattern is cheapest.
