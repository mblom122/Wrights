package com.wrights.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

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
        for (int i = 0; i < ghosts.size(); i += step) {
            WrightsClientState.Ghost ghost = ghosts.get(i);
            BlockState state = Block.stateById(ghost.stateId());
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            pose.pushPose();
            pose.translate(ghost.x(), ghost.y(), ghost.z());
            renderer.renderSingleBlock(state, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffer.endBatch();
        RenderSystem.disableDepthTest();
        pose.popPose();
        graphics.disableScissor();
    }
}
