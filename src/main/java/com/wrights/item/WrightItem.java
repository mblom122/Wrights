package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.wrights.drone.WrightTier;

public class WrightItem extends Item {
    public WrightItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(int tier, int count) {
        ItemStack stack = new ItemStack(ModItems.WRIGHT.get(), count);
        int clamped = WrightTier.clamp(tier);
        if (clamped > WrightTier.COPPER) {
            stack.set(ModDataComponents.WRIGHT_TIER.get(), clamped);
        }
        return stack;
    }

    public static int tier(ItemStack stack) {
        Integer tier = stack.get(ModDataComponents.WRIGHT_TIER.get());
        return tier == null ? WrightTier.COPPER : WrightTier.clamp(tier);
    }

    @Override
    public Component getName(ItemStack stack) {
        int tier = tier(stack);
        return Component.translatable("item.wrights.wright.named", Component.translatable(WrightTier.nameKey(tier)))
                .withColor(WrightTier.textColor(tier) & 0xFFFFFF);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wrights.wright.hint").withStyle(ChatFormatting.GRAY));
    }
}
