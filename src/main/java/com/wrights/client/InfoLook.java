package com.wrights.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsClientConfig;
import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.blueprint.Mirror;
import com.wrights.entity.DroneEntity;
import com.wrights.job.BuildJob;
import com.wrights.network.BlueprintPayloads;

/** What an info-mode crosshair is pointing at, and where that overlay came from. */
public final class InfoLook {
    private InfoLook() {
    }

    public static List<Component> describe(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return List.of();
        }
        String here = minecraft.level.dimension().location().toString();
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(WrightsConfig.MAX_RANGE.getAsInt()));
        BlockPos crosshair = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        Hit bestSolid = null;
        Hit bestAimed = null;
        Hit bestVolume = null;
        for (var entry : WrightsClientState.activeBuilds.entrySet()) {
            WrightsClientState.ActiveBuild build = entry.getValue();
            if (!build.dimension().isEmpty() && !build.dimension().equals(here)) {
                continue;
            }
            BlueprintPayloads.JobLine job = job(entry.getKey());
            BlockPos origin = build.origin();
            for (WrightsClientState.Ghost ghost : build.ghosts()) {
                if (ghost.x() >= build.clipX() || ghost.y() >= build.clipY() || ghost.z() >= build.clipZ()) {
                    continue;
                }
                BlockPos world = origin.offset(ghost.x(), ghost.y(), ghost.z());
                Vec3 at = clip(new AABB(world).inflate(0.04D), eye, end);
                if (at == null) {
                    continue;
                }
                BlockState state = net.minecraft.world.level.block.Block.stateById(ghost.stateId());
                Hit cell = new Hit(eye.distanceToSqr(at), world, cellLines(build.mine(), state, world, job));
                bestSolid = nearer(bestSolid, cell);
                if (world.equals(crosshair)) {
                    bestAimed = nearer(bestAimed, cell);
                }
            }
            if (!build.mine() && (job == null || !job.cellMarks())) {
                AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + Math.max(1, build.clipX()), origin.getY() + Math.max(1, build.clipY()), origin.getZ() + Math.max(1, build.clipZ()));
                Vec3 at = clip(box, eye, end);
                if (at != null) {
                    bestVolume = nearer(bestVolume, new Hit(eye.distanceToSqr(at), null, volumeLines(job, origin, "hud.wrights.info_volume")));
                }
            }
        }
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!WrightsClientState.markedJobs.contains(job.id()) || !here.equals(job.dimension())) {
                continue;
            }
            if (job.cellMarks()) {
                continue;
            }
            BlockPos origin = job.origin();
            AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + Math.max(1, job.sizeX()), origin.getY() + Math.max(1, job.sizeY()), origin.getZ() + Math.max(1, job.sizeZ())).inflate(0.04D);
            Vec3 at = clip(box, eye, end);
            if (at != null) {
                bestVolume = nearer(bestVolume, new Hit(eye.distanceToSqr(at), null, volumeLines(job, origin, "hud.wrights.info_highlight")));
            }
        }
        if (WrightsClientConfig.mirrorPlaced(here)) {
            BlockPos anchor = WrightsClientConfig.mirrorAnchor();
            Vec3 at = clip(new AABB(anchor).inflate(0.06D), eye, end);
            if (at != null) {
                Hit mirror = new Hit(eye.distanceToSqr(at), anchor, mirrorLines(here, anchor));
                bestSolid = nearer(bestSolid, mirror);
                if (anchor.equals(crosshair)) {
                    bestAimed = nearer(bestAimed, mirror);
                }
            }
        }
        if (WrightsClientState.mirrorGlowing() && here.equals(WrightsClientState.mirrorGlowDim) && WrightsClientState.mirrorGlowPos != null) {
            BlockPos glow = WrightsClientState.mirrorGlowPos;
            Vec3 at = clip(new AABB(glow).inflate(0.1D), eye, end);
            if (at != null) {
                Hit mirror = new Hit(eye.distanceToSqr(at), glow, mirrorLines(here, glow));
                bestSolid = nearer(bestSolid, mirror);
                if (glow.equals(crosshair)) {
                    bestAimed = nearer(bestAimed, mirror);
                }
            }
        }
        bestSolid = nearer(bestSolid, droneHit(minecraft, eye, end));
        Hit port = portBlockHit(minecraft, here, eye, end);
        bestSolid = nearer(bestSolid, port);
        if (port != null && port.block() != null && port.block().equals(crosshair)) {
            bestAimed = nearer(bestAimed, port);
        }
        bestVolume = nearer(bestVolume, portReachHit(minecraft, here, eye, end));
        Hit chosen = bestAimed != null ? bestAimed : bestSolid;
        if (bestSolid != null && (chosen == null || bestSolid.distance < chosen.distance)) {
            chosen = bestSolid;
        }
        if (bestVolume != null && (chosen == null || (bestAimed == null && bestVolume.distance < chosen.distance))) {
            chosen = bestVolume;
        }
        return chosen == null ? List.of() : chosen.lines;
    }

    private static Hit droneHit(Minecraft minecraft, Vec3 eye, Vec3 end) {
        Hit best = null;
        AABB sweep = new AABB(eye, end).inflate(1.5D);
        for (DroneEntity drone : minecraft.level.getEntitiesOfClass(DroneEntity.class, sweep, entity -> entity.isAlive())) {
            Vec3 at = clip(drone.getBoundingBox().inflate(0.35D), eye, end);
            if (at == null) {
                continue;
            }
            best = nearer(best, new Hit(eye.distanceToSqr(at), null, droneLines(drone)));
        }
        return best;
    }

    private static Hit portBlockHit(Minecraft minecraft, String here, Vec3 eye, Vec3 end) {
        Hit best = null;
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (minecraft.level.getBlockEntity(pos) instanceof WrightportBlockEntity port) {
                Vec3 at = clip(new AABB(pos).inflate(0.08D), eye, end);
                if (at != null) {
                    best = new Hit(eye.distanceToSqr(at), pos, portLines(here, pos, port));
                }
            }
        }
        if (WrightsClientState.portGlowing() && here.equals(WrightsClientState.portGlowDim) && WrightsClientState.portGlowPos != null) {
            BlockPos pos = WrightsClientState.portGlowPos;
            Vec3 at = clip(new AABB(pos).inflate(0.12D), eye, end);
            if (at != null) {
                WrightportBlockEntity port = minecraft.level.getBlockEntity(pos) instanceof WrightportBlockEntity block ? block : null;
                Hit glow = new Hit(eye.distanceToSqr(at), pos, portLines(here, pos, port));
                best = nearer(best, glow);
            }
        }
        return best;
    }

    private static Hit portReachHit(Minecraft minecraft, String here, Vec3 eye, Vec3 end) {
        Hit best = null;
        for (BlockPos pos : WrightportBounds.visible(minecraft.level.dimension())) {
            Vec3 at = clip(WrightportBounds.cube(pos), eye, end);
            if (at == null || eye.distanceToSqr(at) > 12.0D * 12.0D) {
                continue;
            }
            best = nearer(best, new Hit(eye.distanceToSqr(at), null, reachLines(here, pos, "hud.wrights.info_port_reach")));
        }
        for (BlueprintPayloads.PlacedPort port : WrightsClientState.placedPorts) {
            if (!here.equals(port.dimension()) || !WrightsClientState.portMarked(port.dimension(), port.pos())) {
                continue;
            }
            Vec3 at = clip(WrightportBounds.cube(port.pos()), eye, end);
            if (at == null || eye.distanceToSqr(at) > 12.0D * 12.0D) {
                continue;
            }
            best = nearer(best, new Hit(eye.distanceToSqr(at), null, reachLines(here, port.pos(), "hud.wrights.info_port_marked")));
        }
        return best;
    }

    private static List<Component> cellLines(boolean mine, BlockState state, BlockPos world, BlueprintPayloads.JobLine job) {
        List<Component> lines = new ArrayList<>();
        Component block = state == null || state.isAir() ? Component.translatable("hud.wrights.info_block_unknown") : state.getBlock().getName();
        lines.add(Component.translatable(mine ? "hud.wrights.info_mine" : "hud.wrights.info_ghost", block));
        addJob(lines, job, world);
        return cap(lines);
    }

    private static List<Component> volumeLines(BlueprintPayloads.JobLine job, BlockPos origin, String title) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(title));
        addJob(lines, job, null);
        if (job == null) {
            lines.add(Component.translatable("menu.wrights.job_anchor", origin.getX(), origin.getY(), origin.getZ()));
        }
        return cap(lines);
    }

    private static void addJob(List<Component> lines, BlueprintPayloads.JobLine job, BlockPos cell) {
        if (job == null) {
            if (cell != null) {
                lines.add(Component.translatable("hud.wrights.info_at", cell.getX(), cell.getY(), cell.getZ()));
            }
            return;
        }
        lines.add(Component.translatable("hud.wrights.info_from", job.name()));
        lines.add(status(job));
        String left = WrightsClientState.timeLeft(job);
        if (job.finished() || left.isEmpty()) {
            lines.add(Component.translatable("menu.wrights.job_percent", WrightsClientState.percent(job), job.done(), job.total()));
        } else {
            lines.add(Component.translatable("menu.wrights.job_eta", WrightsClientState.percent(job), job.done(), job.total(), left));
        }
        if (job.skipped() > 0) {
            int total = Math.max(1, job.total());
            lines.add(Component.translatable("menu.wrights.job_skipped", job.skipped(), job.skipped() * 100 / total));
        }
        int wanted = Math.max(job.crew().size(), job.quota());
        if (!job.finished() && wanted > 0) {
            lines.add(Component.translatable("hud.wrights.info_wrights", job.drones(), wanted));
        } else if (job.drones() > 0) {
            lines.add(Component.translatable("hud.wrights.info_wrights_out", job.drones()));
        }
        Component source = source(job);
        if (!source.getString().isEmpty()) {
            lines.add(source);
        }
        if (cell != null) {
            lines.add(Component.translatable("hud.wrights.info_at", cell.getX(), cell.getY(), cell.getZ()));
        }
        BlockPos anchor = job.anchor();
        lines.add(Component.translatable("menu.wrights.job_anchor", anchor.getX(), anchor.getY(), anchor.getZ()));
        lines.add(Component.translatable("jade.wrights.size", Math.max(1, job.sizeX()), Math.max(1, job.sizeY()), Math.max(1, job.sizeZ())));
    }

    private static Component status(BlueprintPayloads.JobLine job) {
        if (job.undoing()) {
            return styled("menu.wrights.undoing", ChatFormatting.AQUA);
        }
        if (job.finished()) {
            if (job.stopped()) {
                return styled("menu.wrights.stopped", ChatFormatting.RED);
            }
            if (job.undone()) {
                return styled("menu.wrights.undone", ChatFormatting.GRAY);
            }
            return styled("menu.wrights.completed", ChatFormatting.GREEN);
        }
        if (job.saving()) {
            return styled("menu.wrights.saving", ChatFormatting.GOLD);
        }
        if (job.paused()) {
            return styled(holdKey(job.hold(), true), ChatFormatting.GOLD);
        }
        if (job.waitKind() == BuildJob.WAIT_ITEMS) {
            return styled("menu.wrights.stalled", ChatFormatting.GOLD);
        }
        if (job.waitKind() == BuildJob.WAIT_FLOOR) {
            return styled("hud.wrights.wait_floor", ChatFormatting.GOLD);
        }
        if (job.waitKind() == BuildJob.WAIT_CHUNK) {
            return styled("hud.wrights.wait_chunk", ChatFormatting.GOLD);
        }
        if (job.queued()) {
            return styled("hud.wrights.info_queued", ChatFormatting.AQUA);
        }
        if (job.hold() != BuildJob.HOLD_NONE && job.hold() != BuildJob.HOLD_MANUAL) {
            return styled(holdKey(job.hold(), false), ChatFormatting.GOLD);
        }
        return styled("menu.wrights.running", ChatFormatting.GREEN);
    }

    private static String holdKey(int hold, boolean paused) {
        return switch (hold) {
            case BuildJob.HOLD_NO_WRIGHTS -> "menu.wrights.hold_no_wrights";
            case BuildJob.HOLD_PLAYER_RANGE -> paused ? "menu.wrights.hold_player" : "menu.wrights.hold_waiting_player";
            case BuildJob.HOLD_PORT_RANGE -> "menu.wrights.hold_port";
            case BuildJob.HOLD_DIMENSION -> "menu.wrights.hold_dimension";
            case BuildJob.HOLD_CHUNK -> "menu.wrights.hold_chunk";
            default -> "menu.wrights.paused";
        };
    }

    private static Component source(BlueprintPayloads.JobLine job) {
        BlockPos at = job.sourcePort();
        boolean coords = job.labelPort() && at != null && !at.equals(BlockPos.ZERO);
        String named = job.sourceName();
        boolean hasName = named != null && !named.isBlank();
        return switch (job.source()) {
            case 1 -> Component.translatable("menu.wrights.job_from_you");
            case 2 -> hasName
                    ? (coords
                            ? Component.translatable("menu.wrights.job_from_port_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port_named", named))
                    : (coords
                            ? Component.translatable("menu.wrights.job_from_port_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_port"));
            case 3 -> hasName
                    ? (coords
                            ? Component.translatable("menu.wrights.job_from_both_named_at", named, at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both_named", named))
                    : (coords
                            ? Component.translatable("menu.wrights.job_from_both_at", at.getX(), at.getY(), at.getZ())
                            : Component.translatable("menu.wrights.job_from_both"));
            default -> Component.empty();
        };
    }

    private static List<Component> mirrorLines(String dimension, BlockPos anchor) {
        List<Component> lines = new ArrayList<>();
        Direction face = Direction.from3DDataValue(WrightsClientConfig.MIRROR_FACE.get());
        lines.add(Component.translatable("hud.wrights.info_mirror"));
        lines.add(Component.translatable("hud.wrights.info_mirror_face", anchor.getX(), anchor.getY(), anchor.getZ(), Component.translatable("menu.wrights.face." + face.getName())));
        int axes = WrightsClientConfig.MIRROR_AXES.get() & 7;
        if (axes == 0) {
            lines.add(Component.translatable("hud.wrights.info_mirror_copies", Mirror.copyCount(WrightsClientConfig.MIRROR_PATTERN.get(), 0)));
        } else {
            StringBuilder mix = new StringBuilder();
            if ((axes & Mirror.AXIS_X) != 0) {
                mix.append("X");
            }
            if ((axes & Mirror.AXIS_Y) != 0) {
                if (!mix.isEmpty()) {
                    mix.append(", ");
                }
                mix.append("Y");
            }
            if ((axes & Mirror.AXIS_Z) != 0) {
                if (!mix.isEmpty()) {
                    mix.append(", ");
                }
                mix.append("Z");
            }
            lines.add(Component.translatable("hud.wrights.info_mirror_axes", mix.toString(), Mirror.copyCount(WrightsClientConfig.MIRROR_PATTERN.get(), axes)));
        }
        boolean armed = WrightsClientConfig.mirrorArmed(dimension);
        lines.add(Component.translatable(armed ? "hud.wrights.info_mirror_on" : "hud.wrights.info_mirror_paused").withStyle(armed ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        lines.add(Component.translatable(WrightsClientConfig.mirrorBlocks() ? "hud.wrights.info_mirror_blocks" : "hud.wrights.info_mirror_plans"));
        if (WrightsClientConfig.mirrorMine()) {
            lines.add(Component.translatable(WrightsClientConfig.mirrorMineVoid() ? "hud.wrights.info_mirror_void" : WrightsClientConfig.mirrorMineAny() ? "hud.wrights.info_mirror_mine_any" : "hud.wrights.info_mirror_mine_same"));
        }
        return lines;
    }

    private static List<Component> droneLines(DroneEntity drone) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.wrights.info_wright", Component.translatable(com.wrights.drone.WrightTier.nameKey(drone.tier()))));
        BlueprintPayloads.JobLine job = drone.jobId().map(InfoLook::job).orElse(null);
        if (job != null) {
            lines.add(Component.translatable("hud.wrights.info_wright_job", job.name()));
            lines.add(status(job));
            String left = WrightsClientState.timeLeft(job);
            if (!job.finished() && !left.isEmpty()) {
                lines.add(Component.translatable("menu.wrights.job_eta", WrightsClientState.percent(job), job.done(), job.total(), left));
            } else {
                lines.add(Component.translatable("menu.wrights.job_percent", WrightsClientState.percent(job), job.done(), job.total()));
            }
        } else {
            lines.add(Component.translatable("hud.wrights.info_wright_home").withStyle(ChatFormatting.AQUA));
        }
        if (!drone.carried().isEmpty()) {
            lines.add(Component.translatable("hud.wrights.info_wright_carry", drone.carried().getHoverName()));
        }
        return lines;
    }

    private static List<Component> portLines(String dimension, BlockPos pos, WrightportBlockEntity port) {
        List<Component> lines = new ArrayList<>();
        BlueprintPayloads.PlacedPort known = placed(dimension, pos);
        if (port != null) {
            lines.add(port.title());
            lines.add(port.ownerLabel());
        } else if (known != null && known.name() != null && !known.name().isBlank()) {
            lines.add(Component.translatable("block.wrights.wrightport.named", known.name()));
        } else {
            lines.add(Component.translatable("hud.wrights.info_port"));
        }
        if (known != null) {
            lines.add(Component.translatable("hud.wrights.info_port_wrights", known.units()));
            if (port == null && known.unowned()) {
                lines.add(Component.translatable("block.wrights.wrightport.no_owner"));
            }
        }
        lines.add(Component.translatable("hud.wrights.info_at", pos.getX(), pos.getY(), pos.getZ()));
        if (WrightsClientState.portMarked(dimension, pos) || WrightportBounds.showing(pos)) {
            lines.add(Component.translatable(WrightportBounds.showing(pos) ? "hud.wrights.info_port_reach_on" : "hud.wrights.info_port_marked_on"));
        }
        if (port != null && port.supplyChest() != null) {
            BlockPos chest = port.supplyChest();
            lines.add(Component.translatable("hud.wrights.info_port_chest", chest.getX(), chest.getY(), chest.getZ()));
        }
        return cap(lines);
    }

    private static List<Component> reachLines(String dimension, BlockPos pos, String title) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(title));
        BlueprintPayloads.PlacedPort known = placed(dimension, pos);
        if (known != null && known.name() != null && !known.name().isBlank()) {
            lines.add(Component.translatable("hud.wrights.info_from", Component.translatable("block.wrights.wrightport.named", known.name())));
            lines.add(Component.translatable("hud.wrights.info_port_wrights", known.units()));
        } else {
            lines.add(Component.translatable("hud.wrights.info_port"));
        }
        lines.add(Component.translatable("hud.wrights.info_at", pos.getX(), pos.getY(), pos.getZ()));
        return lines;
    }

    private static BlueprintPayloads.PlacedPort placed(String dimension, BlockPos pos) {
        for (BlueprintPayloads.PlacedPort port : WrightsClientState.placedPorts) {
            if (dimension.equals(port.dimension()) && pos.equals(port.pos())) {
                return port;
            }
        }
        return null;
    }

    private static BlueprintPayloads.JobLine job(UUID id) {
        if (id == null) {
            return null;
        }
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.id().equals(id)) {
                return job;
            }
        }
        return null;
    }

    private static Vec3 clip(AABB box, Vec3 eye, Vec3 end) {
        return box.clip(eye, end).orElse(null);
    }

    private static Hit nearer(Hit current, Hit next) {
        if (next == null) {
            return current;
        }
        if (current == null || next.distance < current.distance) {
            return next;
        }
        return current;
    }

    private static List<Component> cap(List<Component> lines) {
        return lines.size() <= 9 ? lines : List.copyOf(lines.subList(0, 9));
    }

    private static Component styled(String key, ChatFormatting color) {
        return Component.translatable(key).withStyle(color);
    }

    private record Hit(double distance, BlockPos block, List<Component> lines) {
    }
}
