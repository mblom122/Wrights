package com.wrights.client;

import com.wrights.entity.DroneEntity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

public class DroneModel extends EntityModel<DroneEntity> {
    private final ModelPart root;
    private final ModelPart rotorNw;
    private final ModelPart rotorNe;
    private final ModelPart rotorSw;
    private final ModelPart rotorSe;

    public DroneModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.rotorNw = root.getChild("rotor_nw");
        this.rotorNe = root.getChild("rotor_ne");
        this.rotorSw = root.getChild("rotor_sw");
        this.rotorSe = root.getChild("rotor_se");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -3.0F, -3.0F, 8.0F, 3.0F, 6.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(28, 8).addBox(-2.0F, -3.4F, -2.0F, 4.0F, 1.0F, 4.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(32, 0).addBox(-1.0F, -2.4F, -4.2F, 2.0F, 2.0F, 1.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("lens_ring",
                CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, -2.8F, -4.0F, 3.0F, 2.5F, 0.6F),
                PartPose.ZERO);
        root.addOrReplaceChild("skid_left",
                CubeListBuilder.create().texOffs(0, 10).addBox(-3.4F, 0.0F, -2.2F, 1.0F, 1.0F, 4.4F),
                PartPose.ZERO);
        root.addOrReplaceChild("skid_right",
                CubeListBuilder.create().texOffs(0, 10).addBox(2.4F, 0.0F, -2.2F, 1.0F, 1.0F, 4.4F),
                PartPose.ZERO);
        root.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -5.2F, -0.5F, 1.0F, 2.2F, 1.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("arm_nw",
                CubeListBuilder.create().texOffs(16, 10).addBox(-1.0F, -0.4F, -0.4F, 3.0F, 0.8F, 0.8F),
                PartPose.offset(-4.0F, -2.6F, -2.2F));
        root.addOrReplaceChild("arm_ne",
                CubeListBuilder.create().texOffs(16, 10).addBox(-2.0F, -0.4F, -0.4F, 3.0F, 0.8F, 0.8F),
                PartPose.offset(4.0F, -2.6F, -2.2F));
        root.addOrReplaceChild("arm_sw",
                CubeListBuilder.create().texOffs(16, 10).addBox(-1.0F, -0.4F, -0.4F, 3.0F, 0.8F, 0.8F),
                PartPose.offset(-4.0F, -2.6F, 2.2F));
        root.addOrReplaceChild("arm_se",
                CubeListBuilder.create().texOffs(16, 10).addBox(-2.0F, -0.4F, -0.4F, 3.0F, 0.8F, 0.8F),
                PartPose.offset(4.0F, -2.6F, 2.2F));
        root.addOrReplaceChild("rotor_nw",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offset(-5.0F, -3.5F, -2.5F));
        root.addOrReplaceChild("rotor_ne",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offset(5.0F, -3.5F, -2.5F));
        root.addOrReplaceChild("rotor_sw",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offset(-5.0F, -3.5F, 2.5F));
        root.addOrReplaceChild("rotor_se",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -0.5F, -2.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offset(5.0F, -3.5F, 2.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(DroneEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float spin = ageInTicks * 0.7F;
        this.rotorNw.yRot = spin;
        this.rotorSe.yRot = spin;
        this.rotorNe.yRot = -spin;
        this.rotorSw.yRot = -spin;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, int color) {
        this.root.render(pose, buffer, light, overlay, color);
    }

    public void renderLens(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay) {
        this.root.getChild("lens").render(pose, buffer, light, overlay, 0xFFFFFFFF);
    }
}
