package com.autobots.client;

import com.autobots.AutobotsMod;
import com.autobots.item.ControllerData;
import com.autobots.item.ControllerItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID, value = Dist.CLIENT)
public final class SelectionRenderer {
    private SelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack stack = heldController(player);
        if (stack.isEmpty()) {
            return;
        }
        ControllerData data = ControllerData.from(stack);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        if (data.cornerA().isPresent() && data.cornerB().isPresent()
                && data.cornerA().get().dimension().equals(player.level().dimension())
                && data.cornerB().get().dimension().equals(player.level().dimension())) {
            drawBox(pose, lines, selectionBox(data.cornerA().get().pos(), data.cornerB().get().pos()), 1.0F, 0.78F, 0.2F, 1.0F);
        }
        if (data.armedOrigin().isPresent()
                && data.armedOrigin().get().dimension().equals(player.level().dimension())
                && data.sizeX() > 0 && data.sizeY() > 0 && data.sizeZ() > 0) {
            BlockPos origin = data.armedOrigin().get().pos();
            AABB box = new AABB(
                    origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + data.sizeX(), origin.getY() + data.sizeY(), origin.getZ() + data.sizeZ());
            drawBox(pose, lines, box, 0.3F, 0.85F, 1.0F, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static ItemStack heldController(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ControllerItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ControllerItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static AABB selectionBox(BlockPos first, BlockPos second) {
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1.0D,
                Math.max(first.getY(), second.getY()) + 1.0D,
                Math.max(first.getZ(), second.getZ()) + 1.0D);
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB box, float red, float green, float blue, float alpha) {
        LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, alpha);
    }
}
