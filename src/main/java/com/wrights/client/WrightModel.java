package com.wrights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;

import com.wrights.drone.Loadout;
import com.wrights.drone.WrightTier;

/**
 * A wright built from its fitted parts: the chassis, a rotor of the fitted tier on each of the four mount
 * plates, and the Fabricator Head under the belly. Draws in the chassis' own 0..16 model space at the pose
 * origin, so the entity and the item icon share it.
 */
public final class WrightModel {
    /** Rotor mount plates in chassis pixels, matching PODS in tools/gen_wright_models.py. */
    private static final float[][] PODS = {{3.25F, 3.25F}, {12.75F, 3.25F}, {3.25F, 12.75F}, {12.75F, 12.75F}};
    /** Plate top, where a rotor's flange (its model y 3.5) stands. */
    private static final float PLATE_TOP = 7.0F;
    private static final float ROTOR_BASE = 3.5F;
    private static final float ROTOR_SCALE = 0.34F;
    /** Belly mount, where a Fabricator Head's base (its model y 0) is fixed, nozzles down. */
    private static final float BELLY = 5.0F;
    private static final float FABRICATOR_SCALE = 0.3F;
    /** Skid bottom in chassis pixels; callers stand the wright on this. */
    public static final float SKIDS = 0.8F;

    private WrightModel() {
    }

    /**
     * @param spin rotor angle in degrees; alternate rotors turn the other way
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Loadout parts, float spin, int light) {
        int accent = WrightTier.modelColor(Math.max(parts.rotor(), parts.fabricator()));
        PartModels.render(pose, buffers, PartModels.DRONE_BODY, light, accent);
        int rotor = parts.rotor();
        for (int i = 0; i < PODS.length; i++) {
            float px = PODS[i][0];
            float pz = PODS[i][1];
            // Each rotor model has its arm along -X; turn it to point at the body.
            float facing = (float) Math.toDegrees(Math.atan2(8.0F - pz, px - 8.0F));
            float turn = i == 0 || i == 3 ? spin : -spin;
            pose.pushPose();
            pose.translate(px / 16.0F, PLATE_TOP / 16.0F, pz / 16.0F);
            pose.mulPose(Axis.YP.rotationDegrees(facing));
            pose.scale(ROTOR_SCALE, ROTOR_SCALE, ROTOR_SCALE);
            pose.translate(-0.5F, -ROTOR_BASE / 16.0F, -0.5F);
            PartModels.render(pose, buffers, PartModels.ROTOR_MOUNTS.get(rotor), light, 0xFFFFFFFF);
            spun(pose, buffers, PartModels.ROTOR_BLADES.get(rotor), turn, light);
            if (rotor >= WrightTier.APEX) {
                spun(pose, buffers, PartModels.ROTOR_LOW, -turn, light);
            }
            pose.popPose();
        }
        if (parts.fabricator() > WrightTier.BASIC) {
            pose.pushPose();
            pose.translate(0.5F, BELLY / 16.0F, 0.5F);
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            pose.scale(FABRICATOR_SCALE, FABRICATOR_SCALE, FABRICATOR_SCALE);
            pose.translate(-0.5F, 0.0F, -0.5F);
            PartModels.render(pose, buffers, PartModels.FABRICATORS.get(parts.fabricator() - 1), light, 0xFFFFFFFF);
            pose.popPose();
        }
    }

    /** Draws blades turned {@code degrees} about the rotor's centre line. */
    private static void spun(PoseStack pose, MultiBufferSource buffers, net.minecraft.client.resources.model.ModelResourceLocation blades,
            float degrees, int light) {
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.translate(-0.5F, 0.0F, -0.5F);
        PartModels.render(pose, buffers, blades, light, 0xFFFFFFFF);
        pose.popPose();
    }
}
