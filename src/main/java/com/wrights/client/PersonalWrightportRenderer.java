package com.wrights.client;

import com.wrights.WrightsMod;
import com.wrights.block.WrightportLamp;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
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
    /** Per game tick. About what 0.12 and 0.1 per frame felt like at 60 fps. */
    private static final float OPEN_STEP = 0.36F;
    private static final float CLOSE_STEP = 0.30F;

    private final Map<Integer, Door> doors = new HashMap<>();

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

        model.setupAnim(doorFor(entity).open(entity, partialTicks));

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

    /** Door state for one wearer. Every wearer gets their own, since this renderer is shared. */
    private Door doorFor(LivingEntity entity) {
        long now = entity.level().getGameTime();
        if (doors.size() > 16) {
            doors.values().removeIf(door -> Math.abs(now - door.tick) > 100L);
        }
        return doors.computeIfAbsent(entity.getId(), id -> new Door());
    }

    /**
     * Opens and closes in game ticks, with the partial tick smoothing between them, so the speed
     * does not depend on frame rate. The wright scan also runs once per tick instead of per frame.
     */
    private static final class Door {
        private float open;
        private float previous;
        private long tick = Long.MIN_VALUE;

        float open(LivingEntity entity, float partialTicks) {
            long now = entity.level().getGameTime();
            if (now != tick) {
                tick = now;
                previous = open;
                float target = trafficOpen(entity) ? 1.0F : 0.0F;
                if (open < target) {
                    open = Math.min(target, open + OPEN_STEP);
                } else if (open > target) {
                    open = Math.max(target, open - CLOSE_STEP);
                }
            }
            return Mth.lerp(partialTicks, previous, open);
        }
    }

    private static boolean trafficOpen(LivingEntity entity) {
        // The hold pulse comes from the local player's own wrights, so it only belongs on their pack.
        if (entity == Minecraft.getInstance().player && PersonalPortFx.doorHold() > 0) {
            return true;
        }
        Vec3 dock = behindClient(entity);
        AABB box = new AABB(dock.x - 5.0D, dock.y - 1.5D, dock.z - 5.0D, dock.x + 5.0D, dock.y + 56.0D, dock.z + 5.0D);
        return !entity.level().getEntitiesOfClass(DroneEntity.class, box, drone -> drone.isAlive()).isEmpty();
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
