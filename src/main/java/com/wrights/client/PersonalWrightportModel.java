package com.wrights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/** Backpack-style personal wrightport worn on the player's back. */
public class PersonalWrightportModel extends Model {
    private final ModelPart root;
    private final ModelPart pack;
    private final ModelPart doorN;
    private final ModelPart doorE;
    private final ModelPart doorS;
    private final ModelPart doorW;
    private final ModelPart antenna;
    private final ModelPart lamp;

    public PersonalWrightportModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.pack = root.getChild("pack");
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
        CubeDeformation none = CubeDeformation.NONE;

        // Chassis on the upper back. Local +Z faces away from the player after body attach.
        root.addOrReplaceChild("pack",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 10.0F, 3.5F, none)
                        .texOffs(0, 14).addBox(-3.5F, 1.0F, -0.6F, 7.0F, 8.0F, 0.6F, none),
                PartPose.offset(0.0F, 1.0F, 2.2F));

        // Four hatch slices on the outward face (+Z).
        root.addOrReplaceChild("door_n",
                CubeListBuilder.create().texOffs(24, 0).addBox(-2.5F, -0.35F, -0.35F, 5.0F, 0.7F, 2.5F, none),
                PartPose.offset(0.0F, 4.0F, 5.6F));
        root.addOrReplaceChild("door_s",
                CubeListBuilder.create().texOffs(24, 4).addBox(-2.5F, -0.35F, -2.15F, 5.0F, 0.7F, 2.5F, none),
                PartPose.offset(0.0F, 8.0F, 5.6F));
        root.addOrReplaceChild("door_w",
                CubeListBuilder.create().texOffs(24, 8).addBox(-0.35F, -2.0F, -2.15F, 0.7F, 4.0F, 2.5F, none),
                PartPose.offset(-2.5F, 6.0F, 5.6F));
        root.addOrReplaceChild("door_e",
                CubeListBuilder.create().texOffs(32, 8).addBox(-0.35F, -2.0F, -2.15F, 0.7F, 4.0F, 2.5F, none),
                PartPose.offset(2.5F, 6.0F, 5.6F));

        // Thin antenna mast on top of the pack.
        root.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(40, 0).addBox(-0.35F, 0.0F, -0.35F, 0.7F, 3.5F, 0.7F, none),
                PartPose.offset(0.0F, 11.0F, 3.6F));
        root.addOrReplaceChild("lamp",
                CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F, none),
                PartPose.offset(0.0F, 14.5F, 3.6F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    public void setupAnim(float open) {
        float t = Mth.clamp(open, 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        float slide = eased * 2.8F;
        float tip = eased * 0.7F;

        doorN.z = slide;
        doorN.xRot = -tip;
        doorS.z = slide;
        doorS.xRot = tip;
        doorW.z = slide;
        doorW.yRot = tip;
        doorE.z = slide;
        doorE.yRot = -tip;
    }

    public void renderPack(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        pack.render(pose, buffer, light, overlay, color);
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
