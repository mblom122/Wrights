package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.WrightsConfig;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.item.ControllerItem;
import com.wrights.network.BlueprintPayloads;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class SelectionRenderer {
    private static int previewBudget() {
        return WrightsConfig.ghostBudget();
    }

    private static double rangeSqr() {
        double range = WrightsConfig.ghostRange();
        return range * range;
    }
    private static int[] order = new int[0];
    private static double[] distanceSq = new double[0];
    /** Look direction for this frame, so box edges can stop just in front of the camera. */
    private static Vec3 viewLook = new Vec3(0.0D, 0.0D, 1.0D);
    /** Keep line vertices this far in front of the camera. A vertex behind it makes the whole edge vanish. */
    private static final double EDGE_NEAR = 0.08D;

    private SelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        viewLook = Vec3.directionFromRotation(event.getCamera().getXRot(), event.getCamera().getYRot());
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        drawPortBounds(pose, buffers, camera, minecraft);
        drawPortGlow(pose, buffers, camera, minecraft);
        drawMarkedPorts(pose, buffers, camera, minecraft);
        boolean placing = minecraft.screen instanceof PlacementScreen;
        boolean adjust = minecraft.screen instanceof BlueprintAdjustScreen;
        boolean gui = minecraft.screen != null;
        boolean capture = WrightsClientState.mode == WrightsClientState.Mode.CAPTURE || WrightsClientState.mode == WrightsClientState.Mode.SAVING;
        if (!capture) {
            drawActiveBuilds(pose, buffers, camera, minecraft, !gui);
        }
        if (!ControllerItem.isHeld(player)) {
            return;
        }
        if ((adjust || !gui) && capture) {
            drawCapture(pose, buffers, camera);
            drawCaptureGhosts(pose, buffers, camera, minecraft);
        } else if ((!gui || placing) && WrightsClientState.mode == WrightsClientState.Mode.BUILD && !WrightsClientState.bindSupply) {
            drawPreview(pose, buffers, camera);
        }
        if ((!gui || placing) && WrightsClientState.mode == WrightsClientState.Mode.BUILD && (WrightsClientState.bindSupply || WrightsClientState.supplyBound)) {
            drawSupplyMarkers(pose, buffers, camera, minecraft);
        }
        drawMarkedJobs(pose, buffers, camera, minecraft);
    }

    public static BlockPos supplyTarget(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        if (!isSupplyContainer(minecraft.level, pos) || minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) > 96.0D * 96.0D) {
            return null;
        }
        return pos;
    }

    public static boolean isSupplyContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            return true;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container;
    }

    private static void drawPortBounds(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft) {
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        java.util.List<BlockPos> ports = WrightportBounds.visible(level.dimension());
        if (ports.isEmpty()) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean any = false;
        for (BlockPos pos : ports) {
            if (level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof WrightportBlockEntity)) {
                WrightportBounds.forget(pos);
                continue;
            }
            drawBox(pose, lines, WrightportBounds.cube(pos), camera, 0.77F, 0.48F, 0.23F, 1.0F);
            any = true;
        }
        if (any) {
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void drawSupplyMarkers(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft) {
        Level level = minecraft.level;
        if (level == null) {
            return;
        }
        BlockPos bound = WrightsClientState.supplyBound ? WrightsClientState.supplyPos : null;
        BlockPos aim = WrightsClientState.bindSupply ? supplyTarget(minecraft) : null;
        if (bound != null && !level.isLoaded(bound)) {
            bound = null;
        }
        boolean same = bound != null && aim != null && sameContainer(level, bound, aim);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean any = false;
        if (bound != null && !same) {
            drawBox(pose, lines, supplyBox(level, bound), camera, 0.35F, 0.62F, 1.0F, 1.0F);
            any = true;
        }
        if (WrightsClientState.supplyHighlighting() && bound != null) {
            float pulse = 0.4F + 0.6F * (float) Math.sin(System.currentTimeMillis() / 90.0D);
            AABB glow = supplyBox(level, bound).inflate(0.14D);
            drawBox(pose, lines, glow, camera, 1.0F, 0.86F, 0.2F, pulse);
            drawBox(pose, lines, glow.inflate(0.08D), camera, 1.0F, 0.96F, 0.55F, pulse * 0.7F);
            any = true;
        }
        if (aim != null) {
            drawBox(pose, lines, supplyBox(level, aim), camera, 0.95F, 0.78F, 0.22F, 1.0F);
            any = true;
        }
        if (any) {
            buffers.endBatch(RenderType.lines());
        }
    }

    private static boolean sameContainer(Level level, BlockPos first, BlockPos second) {
        return first.equals(second) || connectedChest(level, first).equals(second) || connectedChest(level, second).equals(first);
    }

    private static BlockPos connectedChest(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return pos;
    }

    private static AABB supplyBox(Level level, BlockPos pos) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        BlockPos other = connectedChest(level, pos);
        if (!other.equals(pos)) {
            box = box.minmax(new AABB(other.getX(), other.getY(), other.getZ(), other.getX() + 1.0D, other.getY() + 1.0D, other.getZ() + 1.0D));
        }
        return box.inflate(0.02D);
    }

    private static void drawMarkedJobs(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft) {
        if (WrightsClientState.markedJobs.isEmpty() || minecraft.level == null) {
            return;
        }
        String here = minecraft.level.dimension().location().toString();
        boolean any = false;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (markedHere(job, here)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 180.0D);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!markedHere(job, here)) {
                continue;
            }
            AABB local = markedBox(job).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.addChainedFilledBoxVertices(
                    pose, fill, local.minX, local.minY, local.minZ, local.maxX, local.maxY, local.maxZ,
                    0.95F, 0.82F, 0.28F, 0.14F + 0.08F * pulse);
        }
        buffers.endBatch(RenderType.debugFilledBox());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (markedHere(job, here)) {
                drawBox(pose, lines, markedBox(job), camera, 0.98F, 0.86F, 0.32F, pulse);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawPortGlow(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft) {
        if (!WrightsClientState.portGlowing() || minecraft.level == null) {
            return;
        }
        if (!minecraft.level.dimension().location().toString().equals(WrightsClientState.portGlowDim)) {
            return;
        }
        boolean on = (System.currentTimeMillis() / 180L) % 2L == 0L;
        float alpha = on ? 1.0F : 0.2F;
        AABB box = new AABB(WrightsClientState.portGlowPos).inflate(0.08D);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        AABB local = box.move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.addChainedFilledBoxVertices(
                pose, fill, local.minX, local.minY, local.minZ, local.maxX, local.maxY, local.maxZ,
                0.95F, 0.72F, 0.18F, on ? 0.28F : 0.06F);
        buffers.endBatch(RenderType.debugFilledBox());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        drawBox(pose, lines, box, camera, 1.0F, 0.78F, 0.2F, alpha);
        drawBox(pose, lines, box.inflate(0.06D), camera, 1.0F, 0.92F, 0.45F, alpha * 0.7F);
        if (WrightsClientState.portGlowLine && WrightsClientState.portGlowAnchor != null) {
            Vec3 from = Vec3.atCenterOf(WrightsClientState.portGlowAnchor).add(0.0D, 0.55D, 0.0D);
            Vec3 to = Vec3.atCenterOf(WrightsClientState.portGlowPos).add(0.0D, 0.65D, 0.0D);
            drawLine(pose, lines, camera, from, to, 1.0F, 0.78F, 0.2F, alpha);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawMarkedPorts(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft) {
        if (WrightsClientState.markedPorts.isEmpty() || minecraft.level == null || minecraft.player == null || WrightsClientState.placedPorts.isEmpty()) {
            return;
        }
        String here = minecraft.level.dimension().location().toString();
        float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 180.0D);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean any = false;
        Vec3 eye = minecraft.player.getEyePosition();
        for (BlueprintPayloads.PlacedPort port : WrightsClientState.placedPorts) {
            if (!here.equals(port.dimension()) || !WrightsClientState.portMarked(port.dimension(), port.pos())) {
                continue;
            }
            drawBox(pose, lines, WrightportBounds.cube(port.pos()), camera, 0.30F, 0.92F, 0.42F, pulse);
            AABB block = new AABB(port.pos()).inflate(0.12D);
            drawBox(pose, lines, block, camera, 0.55F, 1.0F, 0.62F, 1.0F);
            drawBox(pose, lines, block.inflate(0.08D), camera, 0.85F, 1.0F, 0.55F, pulse);
            drawLine(pose, lines, camera, eye, Vec3.atCenterOf(port.pos()), 0.45F, 1.0F, 0.55F, pulse);
            any = true;
        }
        if (any) {
            buffers.endBatch(RenderType.lines());
        }
    }

    private static boolean markedHere(BlueprintPayloads.JobLine job, String dimension) {
        return WrightsClientState.markedJobs.contains(job.id()) && dimension.equals(job.dimension());
    }

    private static AABB markedBox(BlueprintPayloads.JobLine job) {
        BlockPos origin = job.origin();
        return new AABB(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                origin.getX() + Math.max(1, job.sizeX()),
                origin.getY() + Math.max(1, job.sizeY()),
                origin.getZ() + Math.max(1, job.sizeZ())).inflate(0.04D);
    }

    private static void drawCapture(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        BlockPos first = WrightsClientState.cornerA;
        BlockPos second = WrightsClientState.cornerB;
        if (first == null && second == null) {
            return;
        }
        float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 220.0D);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        if (first != null && second != null) {
            AABB box = selectionBox(first, second);
            float alpha = com.wrights.WrightsClientConfig.wallAlpha();
            if (alpha > 0.01F) {
                int rgb = com.wrights.WrightsClientConfig.wallRgb();
                AABB wall = box.inflate(0.02D).move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.addChainedFilledBoxVertices(
                        pose, fill, wall.minX, wall.minY, wall.minZ, wall.maxX, wall.maxY, wall.maxZ,
                        com.wrights.WrightsClientConfig.channel(rgb, 16),
                        com.wrights.WrightsClientConfig.channel(rgb, 8),
                        com.wrights.WrightsClientConfig.channel(rgb, 0),
                        alpha);
            }
            if (com.wrights.WrightsClientConfig.CORNER_MARKERS.get()) {
                drawCorners(pose, fill, box, camera, 1.0F, 0.72F, 0.25F);
            }
            buffers.endBatch(RenderType.debugFilledBox());
            int edge = com.wrights.WrightsClientConfig.edgeRgb();
            drawBox(pose, buffers.getBuffer(RenderType.lines()), box, camera,
                    com.wrights.WrightsClientConfig.channel(edge, 16),
                    com.wrights.WrightsClientConfig.channel(edge, 8),
                    com.wrights.WrightsClientConfig.channel(edge, 0),
                    pulse * com.wrights.WrightsClientConfig.edgeAlpha());
        } else {
            BlockPos only = first != null ? first : second;
            AABB box = new AABB(only.getX(), only.getY(), only.getZ(), only.getX() + 1.0D, only.getY() + 1.0D, only.getZ() + 1.0D);
            drawCorners(pose, fill, box, camera, 1.0F, 0.72F, 0.25F);
            buffers.endBatch(RenderType.debugFilledBox());
            drawBox(pose, buffers.getBuffer(RenderType.lines()), box, camera, 1.0F, 0.78F, 0.2F, pulse);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawCaptureGhosts(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft) {
        if (WrightsClientState.captureConcealed || minecraft.level == null) {
            return;
        }
        var ghosts = WrightsClientState.captureGhosts;
        BlockPos first = WrightsClientState.cornerA;
        BlockPos second = WrightsClientState.cornerB;
        if (ghosts.isEmpty() || first == null || second == null) {
            return;
        }
        BlockPos origin = com.wrights.blueprint.BoxEdits.min(first, second);
        prepare(ghosts.size());
        int count = 0;
        for (int i = 0; i < ghosts.size(); i++) {
            WrightsClientState.Ghost ghost = ghosts.get(i);
            double x = origin.getX() + ghost.x();
            double y = origin.getY() + ghost.y();
            double z = origin.getZ() + ghost.z();
            double dx = x + 0.5D - camera.x;
            double dy = y + 0.5D - camera.y;
            double dz = z + 0.5D - camera.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > rangeSqr()) {
                continue;
            }
            BlockState state = Block.stateById(ghost.stateId());
            if (!ghostVisible(state)) {
                continue;
            }
            order[count] = i;
            distanceSq[count] = distance;
            count++;
        }
        int keep = Math.min(count, previewBudget());
        nearest(count, keep);
        BlockRenderDispatcher renderer = minecraft.getBlockRenderer();
        boolean any = false;
        for (int n = 0; n < keep; n++) {
            WrightsClientState.Ghost ghost = ghosts.get(order[n]);
            any = true;
            drawGhost(pose, buffers, renderer, camera, origin.getX() + ghost.x(), origin.getY() + ghost.y(), origin.getZ() + ghost.z(), Block.stateById(ghost.stateId()));
        }
        if (any) {
            buffers.endBatch();
        }
    }

    private static void drawPreview(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        BlockPos anchor = WrightsClientState.anchor;
        if (anchor == null) {
            return;
        }
        var ghosts = WrightsClientState.ghosts;
        prepare(ghosts.size());
        int count = 0;
        for (int i = 0; i < ghosts.size(); i++) {
            WrightsClientState.Ghost ghost = ghosts.get(i);
            if (outsideView(ghost)) {
                continue;
            }
            double x = anchor.getX() - WrightsClientState.pivotX + ghost.x();
            double y = anchor.getY() - WrightsClientState.pivotY + ghost.y();
            double z = anchor.getZ() - WrightsClientState.pivotZ + ghost.z();
            double dx = x + 0.5D - camera.x;
            double dy = y + 0.5D - camera.y;
            double dz = z + 0.5D - camera.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > rangeSqr()) {
                continue;
            }
            BlockPos world = BlockPos.containing(x, y, z);
            if (WrightsClientState.activeCells.contains(world.asLong()) || alreadyBuilt(world, ghost.stateId())) {
                continue;
            }
            BlockState state = Block.stateById(ghost.stateId());
            if (!ghostVisible(state)) {
                continue;
            }
            order[count] = i;
            distanceSq[count] = distance;
            count++;
        }
        int keep = Math.min(count, previewBudget());
        nearest(count, keep);
        BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
        boolean any = false;
        for (int n = 0; n < keep; n++) {
            WrightsClientState.Ghost ghost = ghosts.get(order[n]);
            double x = anchor.getX() - WrightsClientState.pivotX + ghost.x();
            double y = anchor.getY() - WrightsClientState.pivotY + ghost.y();
            double z = anchor.getZ() - WrightsClientState.pivotZ + ghost.z();
            any = true;
            drawGhost(pose, buffers, renderer, camera, x, y, z, Block.stateById(ghost.stateId()));
        }
        if (any) {
            buffers.endBatch();
        }
        float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 260.0D);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB box = new AABB(
                anchor.getX() - WrightsClientState.pivotX,
                anchor.getY() - WrightsClientState.pivotY,
                anchor.getZ() - WrightsClientState.pivotZ,
                anchor.getX() - WrightsClientState.pivotX + WrightsClientState.viewX,
                anchor.getY() - WrightsClientState.pivotY + WrightsClientState.viewY,
                anchor.getZ() - WrightsClientState.pivotZ + WrightsClientState.viewZ);
        drawBox(pose, lines, box, camera, 0.45F, 0.78F, 1.0F, pulse);
        AABB marker = new AABB(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 1.0D, anchor.getY() + 1.0D, anchor.getZ() + 1.0D);
        drawBox(pose, lines, marker, camera, 0.49F, 0.71F, 1.0F, 1.0F);
        buffers.endBatch(RenderType.lines());
    }

    private static void drawActiveBuilds(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft, boolean models) {
        if (WrightsClientState.activeBuilds.isEmpty() || minecraft.level == null) {
            return;
        }
        BlockRenderDispatcher renderer = minecraft.getBlockRenderer();
        boolean any = false;
        String here = minecraft.level.dimension().location().toString();
        if (models) {
            for (WrightsClientState.ActiveBuild build : WrightsClientState.activeBuilds.values()) {
                if (!build.dimension().isEmpty() && !build.dimension().equals(here)) {
                    continue;
                }
                var ghosts = build.ghosts();
                prepare(ghosts.size());
                int count = 0;
                for (int i = 0; i < ghosts.size(); i++) {
                    WrightsClientState.Ghost ghost = ghosts.get(i);
                    if (ghost.x() >= build.clipX() || ghost.y() >= build.clipY() || ghost.z() >= build.clipZ()) {
                        continue;
                    }
                    BlockPos world = build.origin().offset(ghost.x(), ghost.y(), ghost.z());
                    if (alreadyBuilt(world, ghost.stateId())) {
                        continue;
                    }
                    double x = world.getX() + 0.5D - camera.x;
                    double y = world.getY() + 0.5D - camera.y;
                    double z = world.getZ() + 0.5D - camera.z;
                    double distance = x * x + y * y + z * z;
                    if (distance > rangeSqr()) {
                        continue;
                    }
                    order[count] = i;
                    distanceSq[count] = distance;
                    count++;
                }
                int keep = Math.min(count, previewBudget());
                nearest(count, keep);
                for (int n = 0; n < keep; n++) {
                    WrightsClientState.Ghost ghost = ghosts.get(order[n]);
                    BlockState state = Block.stateById(ghost.stateId());
                    if (!ghostVisible(state)) {
                        continue;
                    }
                    BlockPos world = build.origin().offset(ghost.x(), ghost.y(), ghost.z());
                    any = true;
                    drawGhost(pose, buffers, renderer, camera, world.getX(), world.getY(), world.getZ(), state);
                }
            }
        }
        if (any) {
            buffers.endBatch();
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean boxed = false;
        for (var entry : WrightsClientState.activeBuilds.entrySet()) {
            WrightsClientState.ActiveBuild build = entry.getValue();
            if (!build.dimension().isEmpty() && !build.dimension().equals(here)) {
                continue;
            }
            BlockPos origin = build.origin();
            AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + build.clipX(), origin.getY() + build.clipY(), origin.getZ() + build.clipZ());
            boolean queued = queuedBuild(entry.getKey());
            int rgb = queued ? com.wrights.WrightsClientConfig.queueEdgeRgb() : 0x73C7FF;
            drawBox(pose, lines, box, camera,
                    com.wrights.WrightsClientConfig.channel(rgb, 16),
                    com.wrights.WrightsClientConfig.channel(rgb, 8),
                    com.wrights.WrightsClientConfig.channel(rgb, 0),
                    queued ? 0.9F : 0.45F);
            boxed = true;
        }
        if (boxed) {
            buffers.endBatch(RenderType.lines());
        }
    }

    private static boolean queuedBuild(java.util.UUID id) {
        for (com.wrights.network.BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (job.id().equals(id)) {
                return job.queued();
            }
        }
        return false;
    }

    private static boolean alreadyBuilt(BlockPos pos, int stateId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.hasChunkAt(pos)) {
            return false;
        }
        return minecraft.level.getBlockState(pos).getBlock() == Block.stateById(stateId).getBlock();
    }

    private static boolean outsideView(WrightsClientState.Ghost ghost) {
        return ghost.x() >= WrightsClientState.viewX || ghost.y() >= WrightsClientState.viewY || ghost.z() >= WrightsClientState.viewZ;
    }

    private static void drawCorners(PoseStack pose, VertexConsumer fill, AABB box, Vec3 camera, float red, float green, float blue) {
        AABB local = box.move(-camera.x, -camera.y, -camera.z);
        double[] xs = {local.minX, local.maxX - 0.18D};
        double[] ys = {local.minY, local.maxY - 0.18D};
        double[] zs = {local.minZ, local.maxZ - 0.18D};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    LevelRenderer.addChainedFilledBoxVertices(pose, fill, x, y, z, x + 0.18D, y + 0.18D, z + 0.18D, red, green, blue, 0.95F);
                }
            }
        }
    }

    private static AABB selectionBox(BlockPos first, BlockPos second) {
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1.0D,
                Math.max(first.getY(), second.getY()) + 1.0D,
                Math.max(first.getZ(), second.getZ()) + 1.0D);
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB box, Vec3 camera, float red, float green, float blue, float alpha) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        edge(pose, lines, camera, x0, y0, z0, x1, y0, z0, red, green, blue, alpha);
        edge(pose, lines, camera, x1, y0, z0, x1, y0, z1, red, green, blue, alpha);
        edge(pose, lines, camera, x1, y0, z1, x0, y0, z1, red, green, blue, alpha);
        edge(pose, lines, camera, x0, y0, z1, x0, y0, z0, red, green, blue, alpha);
        edge(pose, lines, camera, x0, y1, z0, x1, y1, z0, red, green, blue, alpha);
        edge(pose, lines, camera, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
        edge(pose, lines, camera, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        edge(pose, lines, camera, x0, y1, z1, x0, y1, z0, red, green, blue, alpha);
        edge(pose, lines, camera, x0, y0, z0, x0, y1, z0, red, green, blue, alpha);
        edge(pose, lines, camera, x1, y0, z0, x1, y1, z0, red, green, blue, alpha);
        edge(pose, lines, camera, x1, y0, z1, x1, y1, z1, red, green, blue, alpha);
        edge(pose, lines, camera, x0, y0, z1, x0, y1, z1, red, green, blue, alpha);
    }

    /** Draws one box edge, cutting it off just in front of the camera so standing close does not hide it. */
    private static void edge(PoseStack pose, VertexConsumer lines, Vec3 camera, double x1, double y1, double z1, double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        double ax = x1 - camera.x;
        double ay = y1 - camera.y;
        double az = z1 - camera.z;
        double bx = x2 - camera.x;
        double by = y2 - camera.y;
        double bz = z2 - camera.z;
        double da = ax * viewLook.x + ay * viewLook.y + az * viewLook.z;
        double db = bx * viewLook.x + by * viewLook.y + bz * viewLook.z;
        boolean inA = da >= EDGE_NEAR;
        boolean inB = db >= EDGE_NEAR;
        if (!inA && !inB) {
            return;
        }
        if (inA && inB) {
            drawLine(pose, lines, camera, new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), red, green, blue, alpha);
            return;
        }
        double span = db - da;
        if (Math.abs(span) < 1.0E-6D) {
            return;
        }
        double t = Mth.clamp((EDGE_NEAR - da) / span, 0.0D, 1.0D);
        double hx = x1 + (x2 - x1) * t;
        double hy = y1 + (y2 - y1) * t;
        double hz = z1 + (z2 - z1) * t;
        if (inA) {
            drawLine(pose, lines, camera, new Vec3(x1, y1, z1), new Vec3(hx, hy, hz), red, green, blue, alpha);
        } else {
            drawLine(pose, lines, camera, new Vec3(hx, hy, hz), new Vec3(x2, y2, z2), red, green, blue, alpha);
        }
    }

    private static void drawLine(PoseStack pose, VertexConsumer lines, Vec3 camera, Vec3 from, Vec3 to, float red, float green, float blue, float alpha) {
        float x1 = (float) (from.x - camera.x);
        float y1 = (float) (from.y - camera.y);
        float z1 = (float) (from.z - camera.z);
        float x2 = (float) (to.x - camera.x);
        float y2 = (float) (to.y - camera.y);
        float z2 = (float) (to.z - camera.z);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        PoseStack.Pose last = pose.last();
        lines.addVertex(last, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(last, dx, dy, dz);
        lines.addVertex(last, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(last, dx, dy, dz);
    }

    private static void drawGhost(PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher renderer, Vec3 camera, double x, double y, double z, BlockState state) {
        float alpha = com.wrights.WrightsClientConfig.ghostAlpha();
        if (alpha <= 0.004F) {
            return;
        }
        pose.pushPose();
        pose.translate(x - camera.x, y - camera.y, z - camera.z);
        MultiBufferSource output = buffers;
        if (alpha < 0.996F && buffers instanceof MultiBufferSource.BufferSource source) {
            int packed = Math.round(alpha * 255.0F);
            output = type -> new GhostConsumer(source.getBuffer(ghostLayer(type)), packed);
        }
        if (state.getBlock() instanceof LiquidBlock) {
            BlueprintModelView.renderFluid(pose, output, state);
        } else {
            renderer.renderSingleBlock(state, pose, output, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }

    /** Source water and lava have no block model, but the preview still draws them. */
    private static boolean ghostVisible(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return true;
        }
        return state.getRenderShape() != RenderShape.INVISIBLE;
    }

    private static void prepare(int needed) {
        if (order.length >= needed) {
            return;
        }
        int size = Math.max(needed, 256);
        order = new int[size];
        distanceSq = new double[size];
    }

    private static void nearest(int count, int keep) {
        if (keep <= 0 || keep >= count) {
            return;
        }
        selectNearest(0, count - 1, keep);
    }

    private static void selectNearest(int left, int right, int keep) {
        while (left < right) {
            int mid = partition(left, right);
            if (mid == keep) {
                return;
            }
            if (mid > keep) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
    }

    private static int partition(int left, int right) {
        int pivot = left + ((right - left) >>> 1);
        swap(pivot, right);
        double value = distanceSq[right];
        int store = left;
        for (int index = left; index < right; index++) {
            if (distanceSq[index] < value) {
                swap(store, index);
                store++;
            }
        }
        swap(store, right);
        return store;
    }

    private static void swap(int first, int second) {
        int index = order[first];
        order[first] = order[second];
        order[second] = index;
        double distance = distanceSq[first];
        distanceSq[first] = distanceSq[second];
        distanceSq[second] = distance;
    }

    /**
     * Ordinary blocks share the block atlas, so they can fade on the translucent layer.
     * Chests, shulkers, beds, banners and pots use their own atlas; drawing those UVs on the block atlas shows a collage of item sprites.
     */
    private static RenderType ghostLayer(RenderType type) {
        if (usesBlockAtlas(type)) {
            return RenderType.translucent();
        }
        if (type == Sheets.chestSheet()) {
            return RenderType.entityTranslucentCull(Sheets.CHEST_SHEET);
        }
        if (type == Sheets.shulkerBoxSheet()) {
            return RenderType.entityTranslucent(Sheets.SHULKER_SHEET);
        }
        if (type == Sheets.signSheet()) {
            return RenderType.entityTranslucent(Sheets.SIGN_SHEET);
        }
        if (type == Sheets.bedSheet()) {
            return RenderType.entityTranslucentCull(Sheets.BED_SHEET);
        }
        if (type == Sheets.bannerSheet() || type == RenderType.entitySolid(Sheets.BANNER_SHEET)) {
            return RenderType.entityTranslucent(Sheets.BANNER_SHEET);
        }
        if (type == RenderType.entitySolid(Sheets.DECORATED_POT_SHEET)) {
            return RenderType.entityTranslucentCull(Sheets.DECORATED_POT_SHEET);
        }
        return type;
    }

    private static boolean usesBlockAtlas(RenderType type) {
        return type == RenderType.solid()
                || type == RenderType.cutout()
                || type == RenderType.cutoutMipped()
                || type == RenderType.translucent()
                || type == RenderType.tripwire()
                || type == Sheets.solidBlockSheet()
                || type == Sheets.cutoutBlockSheet()
                || type == Sheets.translucentCullBlockSheet()
                || type == Sheets.translucentItemSheet()
                || type == RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    }

    private static final class GhostConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private GhostConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int ignored) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
