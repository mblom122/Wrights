# Changelog

## 0.1.8

### Features
- Place build mode: Look, To me, and Bridge. Look forms cover Floor, Wall, Pillar, Beam, Circle, and Stairs.
- Place Circle Mount (Floor / Wall), Left/Right rotate for Floor, Wall, Beam, and Circle, and Void on Bridge / Stairs paths.
- Bridge and Stairs path offset, width, and To me width/height (1–12), with a clear ON/OFF banner while a path is armed.
- Exchange tool with Floor / Wall / Pillar / Beam / Circle forms, Match look, Similar blocks, silk touch, and Void that only suppresses returned drops.
- Shape, Exchange, and Mirror Shift+right-click quick tweaks; middle-click picks Shape and Exchange materials.
- Configurable wrights per Shape / Exchange / Place job (shown under Build Mode); unfinished jobs and finished history caps in config.
- Help as its own left-nav page; mode title above the hotbar by default, with timed helper tips for every controller mode.
- Wrights show the block they are carrying under them for place, mine, and exchange.
- Manage closes while new wrights fly in to dock, then reopens when the last one lands.

### Changes
- Shape sits under Exchange in the menu and controller; form presets replace the old 3D Exchange shapes.
- Equal sides defaults on for Floor and Wall; Pillar / Beam default length is 5; plates default to a 3×3 footprint.
- Jobs cards show mode, detail, size, and block count more tightly; finished cards show the job source.
- Shorter HUD tips; Ctrl/Shift mode menus can sit beside the hotbar from Options.
- Mirror gets a Shift+right-click Blocks / Blueprints quick tweak.
- Departing wrights leave from a random height above home; Manage-add dock-ins come in from high up.

### Fixes
- Bind inventory HUD tip no longer shows the pick-blueprint line.
- Shape undo works again for creative and void Shape jobs.
- Shape / Exchange / Mirror anchor wires stay one block and no longer resize when Ctrl+scroll rotates a shape.
- Creative middle-click pick is cancelled while the controller is in the main hand.
- Two-corners New template keeps both pins when you re-click after Cancel.
- Job flight works again; the Manage dock-in path only runs when adding wrights.
- Stuck-block wait tooltips show for every job kind.
- Bottom HUD sits cleanly above the health and hunger bars.

## 0.1.7


### Features
- Shape build mode under Mirror: pick a kind, materials, and hollow option, then place with look aim or an anchor.
- Weighted shape materials with Random, Priority, and Order fills, a batch picker, and a spinning preview that shows mixed fills.
- Shape Anchor section with a Change screen, live nearby-block preview, look-mode snap lock, and Set under me.
- Selectable AABB pivot for look and anchor placement, with frame and pivot drawn in the menu and in the world.
- Square (Width×Height), Box (Width×Depth×Height), equal-Size Cube and Rectangle, Circle by diameter, and rings that can grow into a torus.
- Split shape Plane into Axis and Rotation for every kind; rotate holograms with Ctrl+scroll in the world (relative to facing / around the pin like blueprints).
- Large shape boxes (default max axis 512 / maxBlocks 512³) with streaming placement — no full BlockPos list at launch.
- Configurable shape air-snap offset (Options) so look-at-air previews sit clear of the player.
- −/+ beside each shape dimension slider; hold-drag the track; click the value to type. Shared Ctrl/Shift/Alt step scale on all +/- controls.
- Undo the last finished build with Ctrl+Shift+right-click.
- Rotate build templates with Ctrl+scroll; hold Ctrl for resize and rotate help.
- Drag plain blocks from JEI onto shape Pick blocks.
- Server caps and feature toggles for Shape builds.

### Changes
- Shape menu preview stays fixed on the right; only the options column scrolls. Size and block count sit above the sample (Preview / size / blocks, blue size text).
- Height slider only appears for shapes that use it; slider labels follow the kind and Axis.
- Look-mode shapes pin to the chosen AABB pivot; Void blocks replaces the old Replace / Clear first pair and also clears fluids.
- Line became Box; Cube is a single equal Size again (width/depth/height labels when a prism needs them).
- Remember controller mode and build sub-tool across world rejoin.
- Job time left freezes while paused or stalled and keeps the last estimate.
- Shared copper pivot marker for shape, build, and mirror anchors.
- Fit menus to the window at high GUI scale; short screens scroll place/edit and New blueprint overflow.
- Jobs list nests Working and Finished scrolling; Manage list gets a border and scrollbar.
- Jobs and Manage mutate actions stay disabled without a controller; Add stays off when nothing can be added.
- Restrict shape Pick blocks to plain non-NBT cubes; Confirm renames the old Add action; material weights cap at 999.
- Format block counts with thousand separators; section headers versus field labels are standardized.
- Large shapes and blueprints sample or wire-box preview instead of freezing the client.

### Fixes
- Shape builds no longer OutOfMemory on huge solids; cells stream during the job instead of materializing millions of BlockPos.
- Jobs card buttons no longer ignore clicks on large Waiting builds; the hold-controller warning draws on top of the Jobs card.
- Large jobs stay responsive: settled-cell checks are O(1), and crew scans plus holograms are capped.
- Shape ring thickness stays independent of size; tipped shapes yaw around world up so tips swing N/E/S/W.
- Shape pivot “Middle of shape” no longer overlaps Done; Anchor point layout and live preview are tightened.
- Info stock tooltips no longer cover the rows; Mirror no longer overlaps Close; Materials sits on the Build/Close row when the detail card is short.
- Shape menu no longer crashes on B; preview frame vertical edges and mesh alignment are fixed.
- Nearest-to-player ghosts stay cheap; million-block shape ghosts are no longer rebuilt every frame.

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
