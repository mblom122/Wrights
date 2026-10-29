package com.wrights.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class BlueprintModelView {
    private static final int DRAW_LIMIT = 1200;

    private BlueprintModelView() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height, List<WrightsClientState.Ghost> ghosts, int sizeX, int sizeY, int sizeZ, float yaw, float pitch) {
        if (ghosts == null || ghosts.isEmpty() || width < 8 || height < 8) {
            return;
        }
        graphics.flush();
        graphics.enableScissor(x, y, x + width, y + height);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0D, y + height / 2.0D, 250.0D);
        float span = Math.max(1, Math.max(sizeX, Math.max(sizeY, sizeZ)));
        float scale = Math.min(width, height) * 0.62F / span;
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-sizeX / 2.0D, -sizeY / 2.0D, -sizeZ / 2.0D);

        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher renderer = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        int step = Math.max(1, (ghosts.size() + DRAW_LIMIT - 1) / DRAW_LIMIT);
        RenderSystem.enableDepthTest();
        for (int pass = 0; pass < 2; pass++) {
            boolean fluids = pass == 1;
            for (int i = 0; i < ghosts.size(); i += step) {
                WrightsClientState.Ghost ghost = ghosts.get(i);
                BlockState state = Block.stateById(ghost.stateId());
                boolean liquid = state.getBlock() instanceof LiquidBlock;
                if (liquid != fluids || state.isAir()) {
                    continue;
                }
                if (!liquid && state.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }
                pose.pushPose();
                pose.translate(ghost.x(), ghost.y(), ghost.z());
                if (liquid) {
                    renderFluid(pose, buffer, state);
                } else {
                    renderer.renderSingleBlock(state, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
                pose.popPose();
            }
            buffer.endBatch();
        }
        RenderSystem.disableDepthTest();
        pose.popPose();
        graphics.disableScissor();
    }

    /** Water and lava have no block model, so the preview draws the fluid texture as a cube. */
    private static void renderFluid(PoseStack pose, MultiBufferSource buffer, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getType());
        ResourceLocation texture = fluid.isSource() ? ext.getStillTexture() : ext.getFlowingTexture();
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(texture);
        int tint = ext.getTintColor();
        int alpha = state.is(Blocks.WATER) ? 170 : (tint >>> 24) == 0 ? 255 : (tint >>> 24);
        int red = (tint >> 16) & 255;
        int green = (tint >> 8) & 255;
        int blue = tint & 255;
        float top = state.is(Blocks.LAVA) ? 1.0F : 0.9F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.translucent());
        Matrix4f matrix = pose.last().pose();
        float u0 = sprite.getU(0.0F);
        float u1 = sprite.getU(1.0F);
        float v0 = sprite.getV(0.0F);
        float v1 = sprite.getV(1.0F);
        quad(consumer, matrix, 0, top, 1, 1, top, 1, 1, top, 0, 0, top, 0, u0, v0, u1, v1, red, green, blue, alpha, 0, 1, 0);
        quad(consumer, matrix, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, u0, v0, u1, v1, red, green, blue, alpha, 0, -1, 0);
        quad(consumer, matrix, 0, 0, 1, 0, top, 1, 1, top, 1, 1, 0, 1, u0, v0, u1, v1, red, green, blue, alpha, 0, 0, 1);
        quad(consumer, matrix, 1, 0, 0, 1, top, 0, 0, top, 0, 0, 0, 0, u0, v0, u1, v1, red, green, blue, alpha, 0, 0, -1);
        quad(consumer, matrix, 1, 0, 1, 1, top, 1, 1, top, 0, 1, 0, 0, u0, v0, u1, v1, red, green, blue, alpha, 1, 0, 0);
        quad(consumer, matrix, 0, 0, 0, 0, top, 0, 0, top, 1, 0, 0, 1, u0, v0, u1, v1, red, green, blue, alpha, -1, 0, 0);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
            float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1, int red, int green, int blue, int alpha, float nx, float ny, float nz) {
        vertex(consumer, matrix, x0, y0, z0, u0, v1, red, green, blue, alpha, nx, ny, nz);
        vertex(consumer, matrix, x1, y1, z1, u0, v0, red, green, blue, alpha, nx, ny, nz);
        vertex(consumer, matrix, x2, y2, z2, u1, v0, red, green, blue, alpha, nx, ny, nz);
        vertex(consumer, matrix, x3, y3, z3, u1, v1, red, green, blue, alpha, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
            float u, float v, int red, int green, int blue, int alpha, float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
