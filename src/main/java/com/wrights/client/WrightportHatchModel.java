package com.wrights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/** Four copper panels that cover the wrightport bay and slide out over the roof ring. */
public class WrightportHatchModel extends Model {
    private final ModelPart nw;
    private final ModelPart ne;
    private final ModelPart se;
    private final ModelPart sw;
    private final ModelPart lampNw;
    private final ModelPart lampNe;
    private final ModelPart lampSw;
    private final ModelPart lampSe;

    public WrightportHatchModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.nw = root.getChild("hatch_nw");
        this.ne = root.getChild("hatch_ne");
        this.se = root.getChild("hatch_se");
        this.sw = root.getChild("hatch_sw");
        this.lampNw = root.getChild("lamp_nw");
        this.lampNe = root.getChild("lamp_ne");
        this.lampSw = root.getChild("lamp_sw");
        this.lampSe = root.getChild("lamp_se");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // The roof opening is x/z 6..10. Panels meet at its center and sit on the ring.
        PartPose pose = PartPose.offset(8.0F, 12.7F, 8.0F);
        root.addOrReplaceChild("hatch_nw",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -0.2F, -2.0F, 2.0F, 0.4F, 2.0F),
                pose);
        root.addOrReplaceChild("hatch_ne",
                CubeListBuilder.create().texOffs(8, 0).addBox(0.0F, -0.2F, -2.0F, 2.0F, 0.4F, 2.0F),
                pose);
        root.addOrReplaceChild("hatch_se",
                CubeListBuilder.create().texOffs(0, 8).addBox(0.0F, -0.2F, 0.0F, 2.0F, 0.4F, 2.0F),
                pose);
        root.addOrReplaceChild("hatch_sw",
                CubeListBuilder.create().texOffs(8, 8).addBox(-2.0F, -0.2F, 0.0F, 2.0F, 0.4F, 2.0F),
                pose);
        // Above the roof corners, clear of the orange frame. Drawn each frame so the color can change.
        lamp(root, "lamp_nw", 1.15F, 12.2F, 2.15F);
        lamp(root, "lamp_ne", 13.1F, 12.2F, 2.15F);
        lamp(root, "lamp_sw", 1.15F, 12.2F, 13.1F);
        lamp(root, "lamp_se", 13.1F, 12.2F, 13.1F);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static void lamp(PartDefinition root, String name, float x, float y, float z) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, 1.75F, 1.0F, 1.75F),
                PartPose.ZERO);
    }

    public void setupAnim(float open) {
        float eased = Mth.clamp(open, 0.0F, 1.0F);
        eased = eased * eased * (3.0F - 2.0F * eased);
        float slide = eased * 2.4F;
        float lift = eased * 0.6F;

        nw.resetPose();
        ne.resetPose();
        se.resetPose();
        sw.resetPose();

        nw.x -= slide;
        nw.z -= slide;
        nw.y += lift;
        ne.x += slide;
        ne.z -= slide;
        ne.y += lift;
        se.x += slide;
        se.z += slide;
        se.y += lift;
        sw.x -= slide;
        sw.z += slide;
        sw.y += lift;
    }

    public void renderHatch(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        int color = 0xFFFFFFFF;
        nw.render(pose, buffer, light, overlay, color);
        ne.render(pose, buffer, light, overlay, color);
        se.render(pose, buffer, light, overlay, color);
        sw.render(pose, buffer, light, overlay, color);
    }

    public void renderLamps(PoseStack pose, VertexConsumer buffer, int overlay, int color) {
        int light = 0xF000F0;
        lampNw.render(pose, buffer, light, overlay, color);
        lampNe.render(pose, buffer, light, overlay, color);
        lampSw.render(pose, buffer, light, overlay, color);
        lampSe.render(pose, buffer, light, overlay, color);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        renderHatch(pose, buffer, light, overlay);
    }
}
