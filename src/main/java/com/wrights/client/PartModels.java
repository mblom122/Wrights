package com.wrights.client;

import java.util.List;

import com.wrights.WrightsMod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Standalone JSON models under {@code models/part}, drawn by the block entity, curio and drone renderers.
 * They come from tools/gen_wright_models.py and can be edited in Blockbench as Java block models.
 */
public final class PartModels {
    public static final ModelResourceLocation DRONE_BODY = id("drone_body");
    /** Index is the rotor tier; 0 is the stock motor and prop. */
    public static final List<ModelResourceLocation> ROTOR_MOUNTS = tiers("rotor_mount_mk", 0);
    public static final List<ModelResourceLocation> ROTOR_BLADES = tiers("rotor_blades_mk", 0);
    /** Counter-rotating lower blades, Apex only. */
    public static final ModelResourceLocation ROTOR_LOW = id("rotor_low_mk5");
    /** Index is the fabricator tier minus one; Basic draws no head. */
    public static final List<ModelResourceLocation> FABRICATORS = tiers("fabricator_mk", 1);
    /** Index is the Power Core tier minus one; Basic leaves the top plate bare. */
    public static final List<ModelResourceLocation> CORES = tiers("core_mk", 1);
    public static final ModelResourceLocation PACK = id("pack");
    public static final ModelResourceLocation PACK_BLADE = id("pack_blade");
    public static final ModelResourceLocation PACK_LAMP = id("pack_lamp");
    public static final ModelResourceLocation IRIS_BLADE = id("iris_blade");
    public static final ModelResourceLocation PORT_LAMPS = id("wrightport_lamps");
    /** Index is the number of lit charge bars, 0..6. */
    public static final List<ModelResourceLocation> PACK_CELLS = gauge("pack_cells_");
    public static final List<ModelResourceLocation> PORT_BARS = gauge("wrightport_bars_");

    public static final List<ModelResourceLocation> ALL = all();

    /** Full bright, for lamps and glowing parts drawn outside the chunk mesh. */
    public static final int FULL_BRIGHT = 0xF000F0;

    private static final int BLADES = 8;
    /** Each blade starts this much later than the one before, so the hatch opens in a spiral. */
    private static final float BLADE_DELAY = 0.07F;
    /** Matches Iris.SLIDE and Iris.SKEW in the generator. */
    private static final float SLIDE = 4.85F;
    private static final float TWIST = 22.5F;

    private PartModels() {
    }

    private static List<ModelResourceLocation> tiers(String prefix, int first) {
        List<ModelResourceLocation> ids = new java.util.ArrayList<>();
        for (int tier = first; tier <= com.wrights.drone.WrightTier.APEX; tier++) {
            ids.add(id(prefix + tier));
        }
        return List.copyOf(ids);
    }

    private static List<ModelResourceLocation> gauge(String prefix) {
        List<ModelResourceLocation> ids = new java.util.ArrayList<>();
        for (int lit = 0; lit <= com.wrights.energy.EnergyBars.BARS; lit++) {
            ids.add(id(prefix + lit));
        }
        return List.copyOf(ids);
    }

    /** The charge gauge for {@code lit} bars, from {@link #PACK_CELLS} or {@link #PORT_BARS}. */
    public static void renderGauge(PoseStack pose, MultiBufferSource buffers, List<ModelResourceLocation> gauge, int lit) {
        render(pose, buffers, gauge.get(Mth.clamp(lit, 0, gauge.size() - 1)), FULL_BRIGHT, 0xFFFFFFFF);
    }

    private static List<ModelResourceLocation> all() {
        List<ModelResourceLocation> ids = new java.util.ArrayList<>(List.of(DRONE_BODY, ROTOR_LOW, PACK, PACK_BLADE, PACK_LAMP, IRIS_BLADE, PORT_LAMPS));
        ids.addAll(ROTOR_MOUNTS);
        ids.addAll(ROTOR_BLADES);
        ids.addAll(FABRICATORS);
        ids.addAll(CORES);
        ids.addAll(PACK_CELLS);
        ids.addAll(PORT_BARS);
        return List.copyOf(ids);
    }

    private static ModelResourceLocation id(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "part/" + name));
    }

    /** Draws a part in block units with its own 0..16 model space at the pose origin. Tinted faces take {@code color}. */
    public static void render(PoseStack pose, MultiBufferSource buffers, ModelResourceLocation id, int light, int color) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(id);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        var type = Sheets.cutoutBlockSheet();
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffers.getBuffer(type), null, model, r, g, b, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, type);
    }

    /** How far blade {@code index} has opened when the hatch as a whole is at {@code open}. */
    public static float bladeOpen(float open, int index) {
        float span = 1.0F - BLADE_DELAY * (BLADES - 1);
        float t = Mth.clamp((Mth.clamp(open, 0.0F, 1.0F) - BLADE_DELAY * index) / span, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Draws the eight iris blades. Each copy is turned 45° about the hatch center, then slides out
     * along its own length while twisting, staggered by index.
     *
     * @param flat true for a hatch lying in the XZ plane (tower roof), false for one standing in XY (worn dock)
     * @param center hatch center in model pixels; for a flat hatch the second value is Z
     * @param scale blade scale from the generator (1 for the tower, 0.6 for the dock)
     * @param step pixel offset between stacked blades, toward the outside of the hatch
     */
    public static void renderIris(PoseStack pose, MultiBufferSource buffers, ModelResourceLocation blade, float open,
            boolean flat, float centerA, float centerB, float scale, float step, int light) {
        float cx = centerA / 16.0F;
        float cb = centerB / 16.0F;
        for (int i = 0; i < BLADES; i++) {
            float p = bladeOpen(open, i);
            pose.pushPose();
            if (flat) {
                pose.translate(cx, step * i / 16.0F, cb);
                pose.mulPose(Axis.YP.rotationDegrees(45.0F * i + TWIST * p));
                pose.translate(SLIDE * scale * p / 16.0F, 0.0F, 0.0F);
                pose.translate(-cx, 0.0F, -cb);
            } else {
                pose.translate(cx, cb, -step * i / 16.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(45.0F * i + TWIST * p));
                pose.translate(SLIDE * scale * p / 16.0F, 0.0F, 0.0F);
                pose.translate(-cx, -cb, 0.0F);
            }
            render(pose, buffers, blade, light, 0xFFFFFFFF);
            pose.popPose();
        }
    }
}
