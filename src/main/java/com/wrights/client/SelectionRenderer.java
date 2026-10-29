package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.WrightsConfig;
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
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
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

    private SelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !ControllerItem.isHeld(player)) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean placing = minecraft.screen instanceof PlacementScreen;
        boolean adjust = minecraft.screen instanceof BlueprintAdjustScreen;
        boolean gui = minecraft.screen != null;
        boolean capture = WrightsClientState.mode == WrightsClientState.Mode.CAPTURE || WrightsClientState.mode == WrightsClientState.Mode.SAVING;
        if ((adjust || !gui) && capture) {
            drawCapture(pose, buffers, camera);
            drawCaptureGhosts(pose, buffers, camera, minecraft);
        } else if ((!gui || placing) && WrightsClientState.mode == WrightsClientState.Mode.BUILD && !WrightsClientState.bindSupply) {
            drawPreview(pose, buffers, camera);
        }
        if ((!gui || placing) && WrightsClientState.mode == WrightsClientState.Mode.BUILD && (WrightsClientState.bindSupply || WrightsClientState.supplyBound)) {
            drawSupplyMarkers(pose, buffers, camera, minecraft);
        }
        drawActiveBuilds(pose, buffers, camera, minecraft, !gui);
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
        float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 180.0D);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean any = false;
        for (BlueprintPayloads.JobLine job : WrightsClientState.jobs) {
            if (!WrightsClientState.markedJobs.contains(job.id()) || !here.equals(job.dimension())) {
                continue;
            }
            BlockPos origin = job.origin();
            AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + job.sizeX(), origin.getY() + job.sizeY(), origin.getZ() + job.sizeZ());
            drawBox(pose, lines, box, camera, 0.95F, 0.78F, 0.25F, pulse);
            BlockPos anchor = job.anchor();
            AABB marker = new AABB(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 1.0D, anchor.getY() + 1.0D, anchor.getZ() + 1.0D);
            drawBox(pose, lines, marker, camera, 0.35F, 0.75F, 1.0F, 1.0F);
            any = true;
        }
        if (any) {
            buffers.endBatch(RenderType.lines());
        }
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
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
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
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
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
        if (models) {
            for (WrightsClientState.ActiveBuild build : WrightsClientState.activeBuilds.values()) {
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
                    if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
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
        for (WrightsClientState.ActiveBuild build : WrightsClientState.activeBuilds.values()) {
            BlockPos origin = build.origin();
            AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + build.clipX(), origin.getY() + build.clipY(), origin.getZ() + build.clipZ());
            drawBox(pose, lines, box, camera, 0.45F, 0.78F, 1.0F, 0.45F);
            boxed = true;
        }
        if (boxed) {
            buffers.endBatch(RenderType.lines());
        }
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
        LevelRenderer.renderLineBox(pose, lines, box.move(-camera.x, -camera.y, -camera.z), red, green, blue, alpha);
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
            output = type -> new GhostConsumer(source.getBuffer(RenderType.translucent()), packed);
        }
        renderer.renderSingleBlock(state, pose, output, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
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
