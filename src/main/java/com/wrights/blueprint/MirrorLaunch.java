package com.wrights.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import com.wrights.job.JobManager;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;

/** Starts one wright job for each reflected copy. */
public final class MirrorLaunch {
    public record Result(int started, boolean nowhere) {
    }

    private MirrorLaunch() {
    }

    public static Result spawn(ServerPlayer player, Schematic source, BlockPos origin, int clipX, int clipY, int clipZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean playerFirst, boolean queue, boolean deferSupplies, boolean placeNbt, boolean placeSettings, boolean placeAll, BlockPos plane, int face, int pattern, boolean shapes) {
        Direction direction = Direction.from3DDataValue(Mth.clamp(face, 0, 5));
        java.util.List<Mirror.Placed> copies = Mirror.place(source, origin, clipX, clipY, clipZ, plane, direction, pattern, shapes);
        if (copies.isEmpty()) {
            return new Result(0, true);
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        int started = 0;
        for (Mirror.Placed placed : copies) {
            BlockPos min = placed.origin();
            Schematic schematic = placed.schematic();
            BlockPos far = min.offset(schematic.sizeX() - 1, schematic.sizeY() - 1, schematic.sizeZ() - 1);
            if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(far)) {
                continue;
            }
            library.putSchematic(schematic);
            boolean ok = JobManager.startBlueprintBuild(player, schematic.id(), min, schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll);
            if (!ok) {
                library.removeSchematic(schematic.id());
                continue;
            }
            started++;
        }
        return new Result(started, false);
    }
}
