package com.wrights.client;

import com.wrights.block.WrightportLamp;
import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class PersonalWrightportRenderer implements ICurioRenderer {
    private static final int TEXTURE_WHITE = 0xFFFFFFFF;

    /** Per game tick. Slow enough that the staggered blades read as a spiral. */
    private static final float OPEN_STEP = 0.2F;
    private static final float CLOSE_STEP = 0.16F;

    private final Map<Integer, Door> doors = new HashMap<>();

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

        float open = doorFor(entity).open(entity, partialTicks);

        poseStack.pushPose();
        ICurioRenderer.translateIfSneaking(poseStack, entity);
        ICurioRenderer.rotateIfSneaking(poseStack, entity);
        M parent = renderLayerParent.getModel();
        if (parent instanceof HumanoidModel<?> humanoid) {
            humanoid.body.translateAndRotate(poseStack);
        }
        // Body space has Y down and +Z behind the player. The dock model is Y up with its
        // player side on the south face, so turn it over and put model (8, 24, 16) on the
        // top of the back, just behind the torso.
        poseStack.translate(0.0F, 0.0F, 2.0F / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(-0.5F, -1.5F, -1.0F);

        PartModels.render(poseStack, buffer, PartModels.PACK, light, TEXTURE_WHITE);
        PartModels.renderIris(poseStack, buffer, PartModels.PACK_BLADE, open, false, 8.0F, 18.0F, 0.6F, 0.03F, light);
        PartModels.render(poseStack, buffer, PartModels.PACK_LAMP, PartModels.FULL_BRIGHT, bulbTint(stack));
        PartModels.renderGauge(poseStack, buffer, PartModels.PACK_CELLS, com.wrights.energy.PortEnergy.itemBars(stack));
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
