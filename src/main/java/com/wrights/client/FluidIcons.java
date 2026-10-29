package com.wrights.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** The still-fluid picture JEI uses, instead of the bucket item. */
public final class FluidIcons {
    private FluidIcons() {
    }

    public static void draw(GuiGraphics graphics, BlockState state, int x, int y, int size) {
        if (state == null || size <= 0) {
            return;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getType());
        ResourceLocation texture = fluid.isSource() ? ext.getStillTexture() : ext.getFlowingTexture();
        if (texture == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(texture);
        int tint = ext.getTintColor();
        float alpha = ((tint >>> 24) & 255) / 255.0F;
        if (alpha <= 0.0F) {
            alpha = 1.0F;
        }
        float red = ((tint >> 16) & 255) / 255.0F;
        float green = ((tint >> 8) & 255) / 255.0F;
        float blue = (tint & 255) / 255.0F;
        graphics.blit(x, y, 0, size, size, sprite, red, green, blue, alpha);
    }
}
