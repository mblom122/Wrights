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

/** Four copper panels that cover the wrightport bay and slide out toward the corner posts. */
public class WrightportHatchModel extends Model {
    private final ModelPart nw;
    private final ModelPart ne;
    private final ModelPart se;
    private final ModelPart sw;

    public WrightportHatchModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.nw = root.getChild("hatch_nw");
        this.ne = root.getChild("hatch_ne");
        this.se = root.getChild("hatch_se");
        this.sw = root.getChild("hatch_sw");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Panels meet at the bay center (8, 8) and sit in the frame under the corner lights.
        PartPose pose = PartPose.offset(8.0F, 12.45F, 8.0F);
        root.addOrReplaceChild("hatch_nw",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.95F, -0.35F, -3.95F, 3.95F, 0.7F, 3.95F),
                pose);
        root.addOrReplaceChild("hatch_ne",
                CubeListBuilder.create().texOffs(8, 0).addBox(0.0F, -0.35F, -3.95F, 3.95F, 0.7F, 3.95F),
                pose);
        root.addOrReplaceChild("hatch_se",
                CubeListBuilder.create().texOffs(0, 8).addBox(0.0F, -0.35F, 0.0F, 3.95F, 0.7F, 3.95F),
                pose);
        root.addOrReplaceChild("hatch_sw",
                CubeListBuilder.create().texOffs(8, 8).addBox(-3.95F, -0.35F, 0.0F, 3.95F, 0.7F, 3.95F),
                pose);
        return LayerDefinition.create(mesh, 16, 16);
    }

    public void setupAnim(float open) {
        float eased = Mth.clamp(open, 0.0F, 1.0F);
        eased = eased * eased * (3.0F - 2.0F * eased);
        float slide = eased * 4.2F;
        float lift = eased * 1.1F;

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

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        renderHatch(pose, buffer, light, overlay);
    }
}
