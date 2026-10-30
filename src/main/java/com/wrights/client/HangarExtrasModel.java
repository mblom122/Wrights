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

/** Animated hangar iris door (4 slices) plus a thin north-side status antenna. */
public class HangarExtrasModel extends Model {
    private final ModelPart root;
    private final ModelPart doorN;
    private final ModelPart doorE;
    private final ModelPart doorS;
    private final ModelPart doorW;
    private final ModelPart antenna;
    private final ModelPart lamp;

    public HangarExtrasModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.doorN = root.getChild("door_n");
        this.doorE = root.getChild("door_e");
        this.doorS = root.getChild("door_s");
        this.doorW = root.getChild("door_w");
        this.antenna = root.getChild("antenna");
        this.lamp = root.getChild("lamp");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Four hatch slices around the dock center (8,8). Closed they form a roundish cover.
        root.addOrReplaceChild("door_n",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -0.4F, -3.0F, 6.0F, 0.8F, 3.0F),
                PartPose.offset(8.0F, 13.2F, 8.0F));
        root.addOrReplaceChild("door_e",
                CubeListBuilder.create().texOffs(0, 4).addBox(0.0F, -0.4F, -3.0F, 3.0F, 0.8F, 6.0F),
                PartPose.offset(8.0F, 13.2F, 8.0F));
        root.addOrReplaceChild("door_s",
                CubeListBuilder.create().texOffs(0, 11).addBox(-3.0F, -0.4F, 0.0F, 6.0F, 0.8F, 3.0F),
                PartPose.offset(8.0F, 13.2F, 8.0F));
        root.addOrReplaceChild("door_w",
                CubeListBuilder.create().texOffs(18, 0).addBox(-3.0F, -0.4F, -3.0F, 3.0F, 0.8F, 6.0F),
                PartPose.offset(8.0F, 13.2F, 8.0F));

        // Thin black mast on the north top edge, centered on X, standing up from the roof.
        root.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(0, 20).addBox(-0.35F, 0.0F, -0.35F, 0.7F, 3.5F, 0.7F),
                PartPose.offset(8.0F, 13.0F, 2.2F));
        root.addOrReplaceChild("lamp",
                CubeListBuilder.create().texOffs(4, 20).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(8.0F, 16.5F, 2.2F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    public void setupAnim(float open) {
        float t = Mth.clamp(open, 0.0F, 1.0F);
        // Ease out so the last bit of close feels snappy.
        float eased = t * t * (3.0F - 2.0F * t);
        float slide = eased * 3.6F;
        float tip = eased * 0.55F;

        doorN.z = -slide;
        doorN.xRot = -tip;
        doorS.z = slide;
        doorS.xRot = tip;
        doorE.x = slide;
        doorE.zRot = tip;
        doorW.x = -slide;
        doorW.zRot = -tip;

        // Slight spin of each slice as it retracts.
        doorN.yRot = eased * 0.15F;
        doorS.yRot = -eased * 0.15F;
        doorE.yRot = eased * 0.15F;
        doorW.yRot = -eased * 0.15F;
    }

    public void renderDoors(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        doorN.render(pose, buffer, light, overlay, color);
        doorE.render(pose, buffer, light, overlay, color);
        doorS.render(pose, buffer, light, overlay, color);
        doorW.render(pose, buffer, light, overlay, color);
    }

    public void renderAntenna(PoseStack pose, VertexConsumer buffer, int light, int overlay, int mastColor, int lampColor) {
        antenna.render(pose, buffer, light, overlay, mastColor);
        lamp.render(pose, buffer, light, overlay, lampColor);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        this.root.render(pose, buffer, light, overlay, color);
    }
}
