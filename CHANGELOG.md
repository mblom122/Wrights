# Changelog

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
