package com.autobots.client;

import com.autobots.AutobotsMod;
import com.autobots.entity.DroneEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DroneRenderer extends EntityRenderer<DroneEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutobotsMod.MOD_ID, "textures/entity/drone.png");
    private final DroneModel model;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DroneModel(context.bakeLayer(AutobotsClient.DRONE_LAYER));
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(DroneEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float bob = Mth.sin((entity.tickCount + partialTick) * 0.15F) * 0.03F;
        pose.translate(0.0F, 0.15F + bob, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-0.7F, -0.7F, 0.7F);
        this.model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTick, 0.0F, 0.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneEntity entity) {
        return TEXTURE;
    }
}
