package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.block.WrightportLamp;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class PersonalWrightportRenderer implements ICurioRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "textures/entity/personal_wrightport.png");
    private static final int TEXTURE_WHITE = 0xFFFFFFFF;

    private final PersonalWrightportModel model;
    private float doorOpen;
    private float doorOpenO;

    public PersonalWrightportRenderer(PersonalWrightportModel model) {
        this.model = model;
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource buffer,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        LivingEntity entity = slotContext.entity();
        if (entity == null) {
            return;
        }

        float target = trafficOpen(entity) ? 1.0F : 0.0F;
        doorOpenO = doorOpen;
        if (doorOpen < target) {
            doorOpen = Math.min(target, doorOpen + 0.12F);
        } else if (doorOpen > target) {
            doorOpen = Math.max(target, doorOpen - 0.1F);
        }
        float open = Mth.lerp(partialTicks, doorOpenO, doorOpen);
        model.setupAnim(open);

        poseStack.pushPose();
        ICurioRenderer.translateIfSneaking(poseStack, entity);
        ICurioRenderer.rotateIfSneaking(poseStack, entity);
        M parent = renderLayerParent.getModel();
        if (parent instanceof HumanoidModel<?> humanoid) {
            humanoid.body.translateAndRotate(poseStack);
        }
        // Shell starts on the body back face; nudge it out so the plate clears the torso.
        poseStack.translate(0.0F, 0.0F, 0.0625F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderPack(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, TEXTURE_WHITE);
        model.renderDoors(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, TEXTURE_WHITE);
        model.renderAntenna(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, TEXTURE_WHITE, bulbTint(stack));
        poseStack.popPose();
    }

    /** Same three colors as the placed wrightport lamps. */
    private static int bulbTint(ItemStack stack) {
        return 0xFF000000 | WrightportLamp.color(Swarm.from(stack), id -> {
            for (var job : WrightsClientState.jobs) {
                if (job.finished() || job.saving()) {
                    continue;
                }
                if (job.crew().contains(id)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean trafficOpen(LivingEntity entity) {
        if (entity.level() == null) {
            return false;
        }
        Vec3 dock = behindClient(entity);
        AABB box = new AABB(dock.x - 2.2D, dock.y - 1.5D, dock.z - 2.2D, dock.x + 2.2D, dock.y + 3.5D, dock.z + 2.2D);
        return !entity.level().getEntitiesOfClass(DroneEntity.class, box, drone -> drone.isAlive()).isEmpty()
                || PersonalPortFx.doorHold() > 0;
    }

    private static Vec3 behindClient(LivingEntity entity) {
        Vec3 back = entity.getLookAngle();
        back = new Vec3(back.x, 0.0D, back.z);
        if (back.lengthSqr() < 1.0E-4D) {
            back = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            back = back.normalize();
        }
        return entity.position().add(back.scale(-0.85D)).add(0.0D, 0.95D, 0.0D);
    }
}
