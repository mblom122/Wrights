package com.wrights.client;

import com.mojang.datafixers.util.Either;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import com.wrights.WrightsMod;
import com.wrights.item.BlueprintItem;

@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class BlueprintTips {
    private BlueprintTips() {
    }

    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        if (!Screen.hasShiftDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!BlueprintItem.hasData(stack)) {
            return;
        }
        CompoundTag tag = BlueprintItem.data(stack);
        SchematicViews.Shot shot = SchematicViews.fromTag(tag);
        if (!shot.visible()) {
            return;
        }
        event.getTooltipElements().add(Either.<FormattedText, TooltipComponent>right(new BlueprintTip(tag)));
    }
}
