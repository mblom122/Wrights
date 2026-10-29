package com.autobots.client;

import com.autobots.AutobotsMod;
import com.autobots.item.ControllerData;
import com.autobots.item.ControllerItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
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
        if (AutobotsClientState.mode == AutobotsClientState.Mode.CAPTURE || AutobotsClientState.mode == AutobotsClientState.Mode.SAVING) {
            drawCapture(pose, buffers);
        } else if (AutobotsClientState.mode == AutobotsClientState.Mode.BUILD) {
            drawPreview(pose, buffers);
        } else {
            drawController(player, pose, buffers);
        }
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

    private static void drawPreview(PoseStack pose, MultiBufferSource.BufferSource buffers) {
        BlockPos anchor = AutobotsClientState.anchor;
        if (anchor == null) {
            return;
        }
        VertexConsumer fill = buffers.getBuffer(RenderType.debugFilledBox());
        int shown = 0;
        for (AutobotsClientState.Ghost ghost : AutobotsClientState.ghosts) {
            if (ghost.x() >= AutobotsClientState.viewX || ghost.y() >= AutobotsClientState.viewY || ghost.z() >= AutobotsClientState.viewZ) {
                continue;
            }
            if (shown++ > 4000) {
                break;
            }
            double x = anchor.getX() - AutobotsClientState.pivotX + ghost.x();
            double y = anchor.getY() - AutobotsClientState.pivotY + ghost.y();
            double z = anchor.getZ() - AutobotsClientState.pivotZ + ghost.z();
            boolean pivot = ghost.x() == AutobotsClientState.pivotX && ghost.y() == AutobotsClientState.pivotY && ghost.z() == AutobotsClientState.pivotZ;
            double inset = pivot ? 0.04D : 0.12D;
            float alpha = pivot ? 0.92F : 0.62F;
            LevelRenderer.addChainedFilledBoxVertices(pose, fill, x + inset, y + inset, z + inset, x + 1.0D - inset, y + 1.0D - inset, z + 1.0D - inset, ghost.r(), ghost.g(), ghost.b(), alpha);
        }
        buffers.endBatch(RenderType.debugFilledBox());
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
        drawBox(pose, lines, marker, 0.95F, 0.62F, 0.22F, 1.0F);
        buffers.endBatch(RenderType.lines());
    }

    private static void drawController(LocalPlayer player, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        ItemStack stack = player.getMainHandItem().getItem() instanceof ControllerItem
                ? player.getMainHandItem()
                : player.getOffhandItem();
        ControllerData data = ControllerData.from(stack);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (data.cornerA().isPresent() && data.cornerB().isPresent()
                && data.cornerA().get().dimension().equals(player.level().dimension())
                && data.cornerB().get().dimension().equals(player.level().dimension())) {
            drawBox(pose, lines, selectionBox(data.cornerA().get().pos(), data.cornerB().get().pos()), 1.0F, 0.78F, 0.2F, 1.0F);
        }
        if (data.armedOrigin().isPresent()
                && data.armedOrigin().get().dimension().equals(player.level().dimension())
                && data.sizeX() > 0 && data.sizeY() > 0 && data.sizeZ() > 0) {
            BlockPos origin = data.armedOrigin().get().pos();
            AABB box = new AABB(
                    origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + data.sizeX(), origin.getY() + data.sizeY(), origin.getZ() + data.sizeZ());
            drawBox(pose, lines, box, 0.3F, 0.85F, 1.0F, 1.0F);
        }
        buffers.endBatch(RenderType.lines());
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
