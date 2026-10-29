# Wrights

A NeoForge mod for Minecraft 1.21.1. The Wright Controller copies a region of blocks, then a small wright pastes that copy where you aim it.

The first test instance is this mod alone. `gradlew runClient` opens that instance. Do not add All the Mods until a build has been checked here.

## Run the isolated instance

Requirements: a network connection the first time, so Gradle can download Minecraft, NeoForge, and a Java 21 toolchain.

```
gradlew runClient
```

Create a new world with cheats enabled. In that world:

```
/gamemode creative
/give @s wrights:controller
```

The jar for a hand-made empty NeoForge 1.21.1 instance is produced with:

```
gradlew isolatedPack
```

It is written to `modpack/mods`. That instance should not contain any other mods.

## Controls

Hold the Wright Controller.

- Use on a block: set corner 1, then corner 2. A gold wireframe shows the selection.
- Use on air: copy the selection. The original blocks stay. Air, chests, machines, portals, and unbreakable blocks are not stored.
- Sneak-use on a block face: aim the paste. A blue wireframe shows the destination. Sneak-use that same spot again to launch the wright.
- Use on air while a paste is aimed: cancel the aim.
- Sneak-use on air: stop the running job and dismiss the wright.

Press **B** to open the Wrights menu. Blueprints are saved copies the wright can build again.

- New: right-click sets corner 1, sneak-right-click sets corner 2. The gold box can be moved and resized, then saved.
- Pick a blueprint and press Build. A hologram follows the block you look at. Sneak-right-click to type the position and size, then send the wright.

Survival spends matching blocks from your inventory. Creative places them for free. The paste will not overwrite solid blocks. A door or tall plant costs one item. The wright pauses if you walk away or the chunk is not loaded, and it resumes when you come back.

## Test checklist

- Mods screen lists only Wrights (plus NeoForge and Minecraft).
- Creative tab "Wrights" contains the controller. The crafting recipe uses copper, redstone, an amethyst shard, and iron.
- Corner 1, corner 2, and the gold box appear. A third click starts a new corner 1.
- A selection larger than 32 on a side, or 32768 blocks, is rejected.
- Use on air copies. The action bar reports how many blocks were stored. A chest's contents are not part of the copy.
- Sneak-use shows the blue destination box. Sneak-use again on that spot launches one wright. The wright is visible while blocks appear.
- Creative paste spends nothing. Survival paste spends items and skips blocks you do not have.
- Solid blocks already in the destination are left in place and counted as skipped.
- Sneak-use on air stops the wright. Walking away pauses it. Rejoining the world lets it continue.
- A door in the copy costs one door, not two.

Server limits live in the world's `serverconfig/wrights-server.toml`.
