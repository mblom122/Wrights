package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.drone.WrightTier;
import com.wrights.entity.DroneEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DroneRenderer extends EntityRenderer<DroneEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/entity/drone.png");
    private final DroneModel model;
    private final ItemRenderer items;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DroneModel(context.bakeLayer(WrightsClient.DRONE_LAYER));
        this.items = context.getItemRenderer();
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
        int color = WrightTier.modelColor(entity.tier());
        this.model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, color);
        this.model.renderLens(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // Place/Exchange outbound: place material. Mine / Exchange after swap (void off): haul. Home: carried.
        ItemStack cargo = entity.displayCargo();
        if (!cargo.isEmpty()) {
            pose.pushPose();
            pose.translate(0.0D, -0.35D, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 3.0F));
            pose.scale(0.55F, 0.55F, 0.55F);
            this.items.renderStatic(cargo, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneEntity entity) {
        return TEXTURE;
    }
}
