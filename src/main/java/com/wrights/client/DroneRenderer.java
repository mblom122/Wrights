package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.entity.DroneEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DroneRenderer extends EntityRenderer<DroneEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/item/part/shell.png");
    private static final float SCALE = 0.7F;
    private final ItemRenderer items;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(DroneEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float age = entity.tickCount + partialTick;
        float bob = Mth.sin(age * 0.15F) * 0.03F;
        pose.translate(0.0F, 0.15F + bob, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5F, -WrightModel.SKIDS / 16.0F, -0.5F);
        WrightModel.render(pose, buffers, entity.parts(), age * 40.0F, light);
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
