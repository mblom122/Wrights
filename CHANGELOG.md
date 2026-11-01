# Changelog

## 0.1.7

### Features
- Shape build mode under Mirror: pick a kind, materials, and hollow option, then place with look aim or an anchor.
- Weighted shape materials with Random, Priority, and Order fills, a batch picker, and a spinning preview that shows mixed fills.
- Shape Anchor section with a Change screen, live nearby-block preview, look-mode snap lock, and Set under me.
- Selectable AABB pivot for look and anchor placement, with frame and pivot drawn in the menu and in the world.
- Square and Line as quick flat options; Line uses length and square thickness; rings get thickness and can grow into a torus.
- Split shape Plane into Axis and Rotation for every kind; rotate holograms with Ctrl+scroll in the world.
- Undo the last finished build with Ctrl+Shift+right-click.
- Rotate build templates with Ctrl+scroll; hold Ctrl for resize and rotate help.
- Drag plain blocks from JEI onto shape Pick blocks.
- Server caps and feature toggles for Shape builds.

### Changes
- Shape menu preview stays fixed on the right; only the options column scrolls. Size and block count sit above the sample.
- Height slider only appears for shapes that use it; slider labels follow the kind and Axis.
- Look-mode shapes pin to the bottom corner of their box; Void blocks replaces the old Replace / Clear first pair and also clears fluids.
- Remember controller mode and build sub-tool across world rejoin.
- Job time left freezes while paused or stalled and keeps the last estimate.
- Shared copper pivot marker for shape, build, and mirror anchors.
- Fit menus to the window at high GUI scale; short screens scroll place/edit and New blueprint overflow.
- Jobs list nests Working and Finished scrolling; Manage list gets a border and scrollbar.
- Jobs and Manage mutate actions stay disabled without a controller; Add stays off when nothing can be added.
- Restrict shape Pick blocks to plain non-NBT cubes; Confirm renames the old Add action.

### Fixes
- Jobs card buttons no longer ignore clicks on large Waiting builds; the hold-controller warning draws on top of the Jobs card.
- Large jobs stay responsive: settled-cell checks are O(1), and crew scans plus holograms are capped.
- Shape ring thickness stays independent of size; tipped shapes yaw around world up so tips swing N/E/S/W.
- Shape pivot “Middle of shape” no longer overlaps Done; Anchor point layout and live preview are tightened.
- Info stock tooltips no longer cover the rows; Mirror no longer overlaps Close; Materials sits on the Build/Close row when the detail card is short.

## 0.1.6

### Building
- Wrights stay on site until the job is finished, and available crew counts stay accurate.
- Idle helpers spread across different blocks instead of stacking on one cell.
- Nearby unfinished blocks are preferred once a wright is already on site.
- Falling blocks like sand hang in place during a paste, the same way worldgen can leave them floating.
- Cactus and sand no longer loop forever on impossible plant cells.
- Mirror mine and mirror place only run while the Build mirror tool is selected.

### Crew
- Hovering a wright shows its current block, next block, and why it is waiting.
- Blocks held for later are listed separately from permanent skips.
- Crew progress counts are fixed, and idle wrights keep helping or return home.
- Creative manage can jump straight to any higher tier with only that upgrade.

### Menus
- Placement and blueprint edit share the same tighter layout, with location, size, and anchor side by side.
- Add layer inserts an empty layer in one click.
- Import and export copy to and paste from the clipboard on purpose, show what is being exported, and no longer stack conflict buttons on top of each other.
- Empty load-from-item messages wrap onto separate lines instead of overlapping.
- The block picker stays open while editing a library blueprint even if the controller is not held.

### Performance
- Wright flight is smoother because blueprints are no longer fully scanned every tick.
