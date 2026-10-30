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

/**
 * Worn personal wrightport. Built in Blockbench with Y up and the antenna above the collar,
 * then flipped into entity space where Y points down the player's back.
 */
public class PersonalWrightportModel extends Model {
    private final ModelPart root;
    private final ModelPart pack;
    private final ModelPart doorTl;
    private final ModelPart doorTr;
    private final ModelPart doorBl;
    private final ModelPart doorBr;
    private final ModelPart antenna;
    private final ModelPart lamp;

    public PersonalWrightportModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.pack = root.getChild("pack");
        this.doorTl = root.getChild("door_tl");
        this.doorTr = root.getChild("door_tr");
        this.doorBl = root.getChild("door_bl");
        this.doorBr = root.getChild("door_br");
        this.antenna = root.getChild("antenna");
        this.lamp = root.getChild("lamp");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("pack", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, 1.0F, 2.0F, 10.0F, 10.0F, 4.0F)
                        .texOffs(0, 16).addBox(-4.0F, 2.0F, 1.0F, 8.0F, 8.0F, 1.0F)
                        .texOffs(32, 0).addBox(-4.0F, -1.0F, 2.0F, 8.0F, 2.0F, 4.0F)
                        .texOffs(32, 16).addBox(-3.0F, 2.0F, 5.0F, 6.0F, 7.0F, 1.0F)
                        .texOffs(0, 30).addBox(-3.0F, 1.0F, 5.0F, 6.0F, 1.0F, 2.0F)
                        .texOffs(20, 30).addBox(-3.0F, 9.0F, 5.0F, 6.0F, 1.0F, 2.0F)
                        .texOffs(0, 48).addBox(-4.0F, 2.0F, 5.0F, 1.0F, 7.0F, 2.0F)
                        .texOffs(8, 48).addBox(3.0F, 2.0F, 5.0F, 1.0F, 7.0F, 2.0F)
                        .texOffs(56, 0).addBox(-6.0F, 2.0F, 3.0F, 1.0F, 8.0F, 2.0F)
                        .texOffs(56, 14).addBox(5.0F, 2.0F, 3.0F, 1.0F, 8.0F, 2.0F)
                        .texOffs(0, 36).addBox(-3.0F, -1.0F, 5.0F, 1.0F, 1.0F, 1.0F)
                        .texOffs(6, 36).addBox(2.0F, -1.0F, 5.0F, 1.0F, 1.0F, 1.0F),
                PartPose.ZERO);

        // Hinges sit on the outer edge of each hatch panel. +Z is away from the player.
        root.addOrReplaceChild("door_tl",
                CubeListBuilder.create().texOffs(0, 42).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 3.0F, 1.0F),
                PartPose.offset(-1.5F, 2.0F, 6.5F));
        root.addOrReplaceChild("door_tr",
                CubeListBuilder.create().texOffs(10, 42).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 3.0F, 1.0F),
                PartPose.offset(1.5F, 2.0F, 6.5F));
        root.addOrReplaceChild("door_bl",
                CubeListBuilder.create().texOffs(20, 42).addBox(-1.5F, -4.0F, -0.5F, 3.0F, 4.0F, 1.0F),
                PartPose.offset(-1.5F, 9.0F, 6.5F));
        root.addOrReplaceChild("door_br",
                CubeListBuilder.create().texOffs(30, 42).addBox(-1.5F, -4.0F, -0.5F, 3.0F, 4.0F, 1.0F),
                PartPose.offset(1.5F, 9.0F, 6.5F));

        root.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(40, 28).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, -1.0F, 4.0F));
        root.addOrReplaceChild("lamp",
                CubeListBuilder.create().texOffs(48, 28).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, -4.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Top pair swings up, bottom pair swings down, both toward +Z. */
    public void setupAnim(float open) {
        float t = Mth.clamp(open, 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        float swing = eased * 2.1F;

        doorTl.resetPose();
        doorTr.resetPose();
        doorBl.resetPose();
        doorBr.resetPose();
        doorTl.xRot += swing;
        doorTr.xRot += swing;
        doorBl.xRot -= swing;
        doorBr.xRot -= swing;
    }

    public void renderPack(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        pack.render(pose, buffer, light, overlay, color);
    }

    public void renderDoors(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        doorTl.render(pose, buffer, light, overlay, color);
        doorTr.render(pose, buffer, light, overlay, color);
        doorBl.render(pose, buffer, light, overlay, color);
        doorBr.render(pose, buffer, light, overlay, color);
    }

    public void renderAntenna(PoseStack pose, VertexConsumer buffer, int light, int overlay, int mastColor, int lampColor) {
        antenna.render(pose, buffer, light, overlay, mastColor);
        lamp.render(pose, buffer, 0xF000F0, overlay, lampColor);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        this.root.render(pose, buffer, light, overlay, color);
    }
}
