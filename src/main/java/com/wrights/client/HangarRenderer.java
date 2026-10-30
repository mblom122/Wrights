package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.block.HangarBlockEntity;
import com.wrights.block.HangarSignal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class HangarRenderer implements BlockEntityRenderer<HangarBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/block/hangar_door.png");
    private static final int MAST = 0xFF1A1E24;
    private static final int DOOR = 0xFFB8C0C8;

    private final HangarExtrasModel model;

    public HangarRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new HangarExtrasModel(context.bakeLayer(WrightsClient.HANGAR_LAYER));
    }

    @Override
    public void render(HangarBlockEntity hangar, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float open = hangar.doorProgress(partialTick);
        model.setupAnim(open);

        pose.pushPose();
        // Model parts are authored in 0..16 block pixels.
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderDoors(pose, consumer, light, OverlayTexture.NO_OVERLAY, DOOR);

        HangarSignal signal = hangar.signal();
        int lamp = signal.tint();
        // Fullbright-ish lamp: keep block light but force a bright tint.
        model.renderAntenna(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY, MAST, lamp);
        pose.popPose();
    }
}
