package com.wrights.client;

import com.mojang.datafixers.util.Either;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
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
    /** Soft cap so Wrights item hints wrap instead of spanning the whole screen. */
    private static final int MAX_LINE = 200;

    private BlueprintTips() {
    }

    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        nameMod(event);
        wrapWrightsLines(event);
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

    /** Wrap long Wrights tooltip lines so hints stay readable. */
    private static void wrapWrightsLines(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.getItem().builtInRegistryHolder().key().location().getNamespace().equals(WrightsMod.MOD_ID)) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int max = Math.min(MAX_LINE, Math.max(140, Minecraft.getInstance().getWindow().getGuiScaledWidth() / 3));
        List<Either<FormattedText, TooltipComponent>> lines = event.getTooltipElements();
        List<Either<FormattedText, TooltipComponent>> wrapped = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Either<FormattedText, TooltipComponent> line = lines.get(i);
            // Keep the item name on one line; wrap description / hint text only.
            if (i == 0 || line.right().isPresent()) {
                wrapped.add(line);
                continue;
            }
            FormattedText text = line.left().orElse(null);
            if (text == null || font.width(text) <= max) {
                wrapped.add(line);
                continue;
            }
            Style style = text instanceof Component component ? component.getStyle() : Style.EMPTY;
            for (FormattedText part : font.getSplitter().splitLines(text, max, style)) {
                wrapped.add(Either.left(part));
            }
        }
        lines.clear();
        lines.addAll(wrapped);
    }

    private static String text(Either<FormattedText, TooltipComponent> line) {
        return line.left().map(text -> text instanceof Component component ? component.getString() : "").orElse("");
    }
}
