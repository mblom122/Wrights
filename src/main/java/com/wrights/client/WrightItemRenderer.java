package com.wrights.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.wrights.item.WrightItem;

/** Draws a wright item as the wright it holds: the chassis with the parts in its {@link WrightItem#parts}. */
public final class WrightItemRenderer extends BlockEntityWithoutLevelRenderer {
    /** Props rest at this angle so the two-blade stock prop reads as a cross. */
    private static final float REST = 22.5F;

    public WrightItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        WrightModel.render(pose, buffers, WrightItem.parts(stack), REST, light);
    }
}
