package com.autobots.client;

import com.autobots.AutobotsMod;
import com.autobots.item.ControllerItem;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID, value = Dist.CLIENT)
public final class SelectionRenderer {
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
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        boolean placing = minecraft.screen instanceof PlacementScreen;
        boolean gui = minecraft.screen != null;
        if (!gui && (AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE || AutobotsClientState.mode == AutobotsClientState.Mode.SAVING)) {
            drawCapture(pose, buffers);
        } else if ((!gui || placing) && AutobotsClientState.mode == AutobotsClientState.Mode.BUILD) {
            drawPreview(pose, buffers, camera);
        }
        drawActiveBuilds(pose, buffers, camera, minecraft, !gui);
        pose.popPose();
    }

    private static void drawCapture(PoseStack pose, MultiBufferSource.BufferSource buffers) {
        BlockPos first = AutobotsClientState.cornerA;
        BlockPos second = AutobotsClientState.cornerB;
        if (first == null && second == null) {
            return;
        }
        float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 220.0D);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        if (first != null && second != null) {
            AABB box = selectionBox(first, second);
            float alpha = com.autobots.AutobotsClientConfig.wallAlpha();
            if (alpha > 0.01F) {
                int rgb = com.autobots.AutobotsClientConfig.wallRgb();
                AABB wall = box.inflate(0.02D);
                LevelRenderer.addChainedFilledBoxVertices(
                        pose, fill, wall.minX, wall.minY, wall.minZ, wall.maxX, wall.maxY, wall.maxZ,
                        com.autobots.AutobotsClientConfig.channel(rgb, 16),
                        com.autobots.AutobotsClientConfig.channel(rgb, 8),
                        com.autobots.AutobotsClientConfig.channel(rgb, 0),
                        alpha);
            }
            if (com.autobots.AutobotsClientConfig.CORNER_MARKERS.get()) {
                drawCorners(pose, fill, box, 1.0F, 0.72F, 0.25F);
            }
            buffers.endBatch(RenderType.debugFilledBox());
            int edge = com.autobots.AutobotsClientConfig.edgeRgb();
            drawBox(pose, buffers.getBuffer(RenderType.lines()), box,
                    com.autobots.AutobotsClientConfig.channel(edge, 16),
                    com.autobots.AutobotsClientConfig.channel(edge, 8),
                    com.autobots.AutobotsClientConfig.channel(edge, 0),
                    pulse * com.autobots.AutobotsClientConfig.edgeAlpha());
        } else {
            BlockPos only = first != null ? first : second;
            AABB box = new AABB(only.getX(), only.getY(), only.getZ(), only.getX() + 1.0D, only.getY() + 1.0D, only.getZ() + 1.0D);
            drawCorners(pose, fill, box, 1.0F, 0.72F, 0.25F);
            buffers.endBatch(RenderType.debugFilledBox());
            drawBox(pose, buffers.getBuffer(RenderType.lines()), box, 1.0F, 0.78F, 0.2F, pulse);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawPreview(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        BlockPos anchor = AutobotsClientState.anchor;
        if (anchor == null) {
            return;
        }
        int inRange = 0;
        for (AutobotsClientState.Ghost ghost : AutobotsClientState.ghosts) {
            if (outsideView(ghost)) {
                continue;
            }
            if (nearCamera(camera, anchor, ghost)) {
                inRange++;
            }
        }
        int step = Math.max(1, (inRange + 479) / 480);
        int seen = 0;
        BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
        for (AutobotsClientState.Ghost ghost : AutobotsClientState.ghosts) {
            if (outsideView(ghost) || !nearCamera(camera, anchor, ghost)) {
                continue;
            }
            boolean pivot = ghost.x() == AutobotsClientState.pivotX && ghost.y() == AutobotsClientState.pivotY && ghost.z() == AutobotsClientState.pivotZ;
            if (!pivot && seen++ % step != 0) {
                continue;
            }
            BlockState state = Block.stateById(ghost.stateId());
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            double x = anchor.getX() - AutobotsClientState.pivotX + ghost.x();
            double y = anchor.getY() - AutobotsClientState.pivotY + ghost.y();
            double z = anchor.getZ() - AutobotsClientState.pivotZ + ghost.z();
            BlockPos world = BlockPos.containing(x, y, z);
            if (AutobotsClientState.activeCells.contains(world.asLong()) || alreadyBuilt(world, ghost.stateId())) {
                continue;
            }
            pose.pushPose();
            pose.translate(x + 0.5D, y + 0.5D, z + 0.5D);
            pose.scale(0.92F, 0.92F, 0.92F);
            pose.translate(-0.5D, -0.5D, -0.5D);
            renderer.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
        float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 260.0D);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB box = new AABB(
                anchor.getX() - AutobotsClientState.pivotX,
                anchor.getY() - AutobotsClientState.pivotY,
                anchor.getZ() - AutobotsClientState.pivotZ,
                anchor.getX() - AutobotsClientState.pivotX + AutobotsClientState.viewX,
                anchor.getY() - AutobotsClientState.pivotY + AutobotsClientState.viewY,
                anchor.getZ() - AutobotsClientState.pivotZ + AutobotsClientState.viewZ);
        drawBox(pose, lines, box, 0.45F, 0.78F, 1.0F, pulse);
        AABB marker = new AABB(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 1.0D, anchor.getY() + 1.0D, anchor.getZ() + 1.0D);
        drawBox(pose, lines, marker, 0.49F, 0.71F, 1.0F, 1.0F);
        buffers.endBatch(RenderType.lines());
    }

    private static void drawActiveBuilds(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, Minecraft minecraft, boolean models) {
        if (AutobotsClientState.activeBuilds.isEmpty() || minecraft.level == null) {
            return;
        }
        BlockRenderDispatcher renderer = minecraft.getBlockRenderer();
        boolean any = false;
        int budget = 480;
        if (models) {
        for (AutobotsClientState.ActiveBuild build : AutobotsClientState.activeBuilds.values()) {
            if (budget <= 0) {
                break;
            }
            int nearby = 0;
            for (AutobotsClientState.Ghost ghost : build.ghosts()) {
                if (ghost.x() >= build.clipX() || ghost.y() >= build.clipY() || ghost.z() >= build.clipZ()) {
                    continue;
                }
                BlockPos world = build.origin().offset(ghost.x(), ghost.y(), ghost.z());
                if (nearBlock(camera, world) && !alreadyBuilt(world, ghost.stateId())) {
                    nearby++;
                }
            }
            int step = Math.max(1, (nearby + budget - 1) / budget);
            int seen = 0;
            for (AutobotsClientState.Ghost ghost : build.ghosts()) {
                if (ghost.x() >= build.clipX() || ghost.y() >= build.clipY() || ghost.z() >= build.clipZ()) {
                    continue;
                }
                BlockPos world = build.origin().offset(ghost.x(), ghost.y(), ghost.z());
                if (!nearBlock(camera, world) || alreadyBuilt(world, ghost.stateId())) {
                    continue;
                }
                if (seen++ % step != 0) {
                    continue;
                }
                BlockState state = Block.stateById(ghost.stateId());
                if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }
                any = true;
                budget--;
                pose.pushPose();
                pose.translate(world.getX() + 0.5D, world.getY() + 0.5D, world.getZ() + 0.5D);
                pose.scale(0.92F, 0.92F, 0.92F);
                pose.translate(-0.5D, -0.5D, -0.5D);
                renderer.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        }
        if (any) {
            buffers.endBatch();
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean boxed = false;
        for (AutobotsClientState.ActiveBuild build : AutobotsClientState.activeBuilds.values()) {
            BlockPos origin = build.origin();
            AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + build.clipX(), origin.getY() + build.clipY(), origin.getZ() + build.clipZ());
            drawBox(pose, lines, box, 0.45F, 0.78F, 1.0F, 0.45F);
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

    private static boolean nearBlock(Vec3 camera, BlockPos pos) {
        double x = pos.getX() + 0.5D - camera.x;
        double y = pos.getY() + 0.5D - camera.y;
        double z = pos.getZ() + 0.5D - camera.z;
        return x * x + y * y + z * z <= 48.0D * 48.0D;
    }

    private static boolean outsideView(AutobotsClientState.Ghost ghost) {
        return ghost.x() >= AutobotsClientState.viewX || ghost.y() >= AutobotsClientState.viewY || ghost.z() >= AutobotsClientState.viewZ;
    }

    private static boolean nearCamera(Vec3 camera, BlockPos anchor, AutobotsClientState.Ghost ghost) {
        double x = anchor.getX() - AutobotsClientState.pivotX + ghost.x() + 0.5D - camera.x;
        double y = anchor.getY() - AutobotsClientState.pivotY + ghost.y() + 0.5D - camera.y;
        double z = anchor.getZ() - AutobotsClientState.pivotZ + ghost.z() + 0.5D - camera.z;
        return x * x + y * y + z * z <= 48.0D * 48.0D;
    }

    private static void drawCorners(PoseStack pose, VertexConsumer fill, AABB box, float red, float green, float blue) {
        double[] xs = {box.minX, box.maxX - 0.18D};
        double[] ys = {box.minY, box.maxY - 0.18D};
        double[] zs = {box.minZ, box.maxZ - 0.18D};
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

    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB box, float red, float green, float blue, float alpha) {
        LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, alpha);
    }
}
