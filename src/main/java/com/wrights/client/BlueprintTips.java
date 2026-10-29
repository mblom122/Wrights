package com.wrights.client;

import com.mojang.datafixers.util.Either;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import com.wrights.WrightsMod;
import com.wrights.item.BlueprintItem;
import com.wrights.item.PersonalWrightportItem;

@EventBusSubscriber(modid = WrightsMod.MOD_ID, value = Dist.CLIENT)
public final class BlueprintTips {
    private BlueprintTips() {
    }

    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        nameMod(event);
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

    /** The creative search tooltip already inserts the tab name. The full tooltip does not. */
    private static void nameMod(RenderTooltipEvent.GatherComponents event) {
        if (!(event.getItemStack().getItem() instanceof PersonalWrightportItem)) {
            return;
        }
        String name = ModList.get().getModContainerById(WrightsMod.MOD_ID).map(container -> container.getModInfo().getDisplayName()).orElse("Wrights");
        List<Either<FormattedText, TooltipComponent>> lines = event.getTooltipElements();
        if (lines.isEmpty() || (lines.size() > 1 && text(lines.get(1)).equals(name))) {
            return;
        }
        lines.add(1, Either.left(Component.literal(name).withStyle(ChatFormatting.BLUE)));
    }

    private static String text(Either<FormattedText, TooltipComponent> line) {
        return line.left().map(text -> text instanceof Component component ? component.getString() : "").orElse("");
    }
}
