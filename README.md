# Wrights

A NeoForge mod for Minecraft 1.21.1. Wrights copy a build and place it again. Mark a structure, save it as a blueprint, and send a wright to build it from your inventory, a bound chest, or a linked digital network. Mirror can repeat the block in your other hand, or a whole blueprint, around an anchor.

Curios is required. The personal wrightport uses that slot.

## Playing

Hold the **Wright Controller**. **Ctrl + Shift + scroll** changes mode: Build, Jobs, Manage, Info, and Link. Inside Build, **Shift + scroll** picks the tool. Press **B** to open blueprints, jobs, and wrights.

**New** marks a blueprint. Two corners, a dragged box, or a box you slide into place. Chests and machines can be saved empty, with their settings, or, in Creative, as an exact copy.

**Build** places a blueprint. A hologram follows where you look. The list shows every block and where it will come from. Survival spends those items. Creative builds for free. A job waits in the queue when no wright is free.

**Mirror** uses an anchor. Shift-right-click a block to set it, and shift-right-click air to clear it. Blocks mode places the block in your other hand. Blueprints mode builds mirrored copies of the blueprint you are placing. A slider makes 1 to 32 copies around the anchored face, or the copy can mirror through the middle of the anchor on X, Y, and Z. Breaking a block can send wrights to mine the copies as one job.

The bulb on the personal wrightport, and the lamps on a placed wrightport, use the same colors. Red means no wrights are stored. Yellow means every stored wright is out on a job. Green means at least one is free.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1 or newer
- Curios API
- Java 21

Jade, JEI, Applied Energistics 2, Refined Storage, and Mekanism are optional. AE2 links from a wireless access point. Refined Storage links from Link mode.

Server limits live in the world's `serverconfig/wrights-server.toml`.

## License

All Rights Reserved. You can read this source. Republishing the mod or the code needs permission.

## Development

`gradlew runClient` opens the test instance. The first run needs a network connection so Gradle can download Minecraft, NeoForge, and a Java 21 toolchain.

In a new world with cheats:

```
/gamemode creative
/give @s wrights:controller
```

`gradlew isolatedPack` writes the jar to `modpack/mods`. That instance still needs Curios beside it. Install Jade too when you want the wright tooltip.
