package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.block.WrightportBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class WrightportRenderer implements BlockEntityRenderer<WrightportBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/block/wrightport_copper.png");

    private final WrightportHatchModel model;

    public WrightportRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new WrightportHatchModel(context.bakeLayer(WrightsClient.WRIGHTPORT_HATCH_LAYER));
    }

    @Override
    public void render(WrightportBlockEntity port, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        model.setupAnim(port.hatchOpen(partialTick));
        pose.pushPose();
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderHatch(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
