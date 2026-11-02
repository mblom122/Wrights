# Wrightport energy

Wrights spend Forge Energy from the port that launched them. The flying wright does not carry a battery. One block is one charge, taken after the block is legal to build and before the world changes. If the block does not stay, the charge is returned.

Creative mode does not pay. Set `energy` to false in the server config and every action is free.

## Who pays

| Launch | Where the energy comes from |
| --- | --- |
| Placed wrightport | That block's buffer. Cables can fill it. |
| Personal wrightport | Energy items in the player's inventory, then Curios slots. The worn port has no buffer of its own. |

A wright from a placed port never falls back to the player's batteries. If that port's chunk is not loaded, the action simply cannot be paid.

A placed port holds **1,000,000 FE** and accepts or emits **10,000 FE** per transfer. Placing one starts it empty. Cables or a battery have to fill it. After that, the saved amount is what it has.

A personal wright can pull at most **10,000 FE** for one block, and only if the items together hold the whole cost. A shortfall does not leave a partial drain.

## Cost per block

Defaults. All of these are server config.

| Action | Config | Default |
| --- | --- | --- |
| Place | `energyPlace` | 75 |
| Mirrored place | `energyMirrorPlace` | 100 |
| Exchange | `energyExchange` | 150 |
| Mine | `energyMine` | 125 |
| Void | `energyVoid` | 250 |

How they combine:

- A normal place is 75. Clearing a block first, then placing, is 75 + 250.
- A mirrored place is 100, plus 250 when that cell is cleared first.
- Exchange is 150 for the swap. The void cost is not added on top.
- Mining is 125. Mining with void drops is 250, and the mine cost is not added on top.

The buffer is charged for the whole block at once. It does not take a partial payment.

## When the buffer runs out

If the next block cannot be paid, the crew docks and the job stays queued. The ghost stays. The job shows **Stalled · no energy**.

Work resumes when some port on that crew, or the player's batteries for a personal wright, can pay one block of that job's place, mirror, exchange, or mine cost. A void surcharge is checked on the block itself, so a buffer that can pay a plain place may still stall again on a cell that must be cleared first.

While a placed port is empty and still has wrights stored, its corner lamps turn amber. Red, yellow, and green keep their usual meaning once the buffer has energy again: empty crew, everyone out, or someone home.

Unloaded chunks still pause the build the way they did before. No energy is spent on a cell the server cannot touch.

## Config keys

All live in the Wrights server config.

| Key | Default | Meaning |
| --- | --- | --- |
| `energy` | true | Turn the whole cost system on or off |
| `portEnergy` | 1000000 | Placed-port buffer size |
| `portEnergyTransfer` | 10000 | What cables can move into or out of that buffer per transfer |
| `personalEnergyRate` | 10000 | Most FE one personal-port block may pull from items |
| `energyPlace` | 75 | Place one block |
| `energyMirrorPlace` | 100 | Place one mirrored copy |
| `energyExchange` | 150 | Exchange one block |
| `energyMine` | 125 | Mine one block |
| `energyVoid` | 250 | Void one block, or the extra cost of clearing before a place |

## Later

This is the buffer, the per-block costs, and the stall. Port tiers, efficiency upgrades, a speed-energy curve, distance surcharge, an NBT surcharge, and Silk Touch or Fortune multipliers are not part of this version.
