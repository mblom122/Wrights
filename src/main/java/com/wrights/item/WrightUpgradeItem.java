package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.wrights.drone.WrightTier;

public class WrightUpgradeItem extends Item {
    private final int tier;

    public WrightUpgradeItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withColor(WrightTier.textColor(tier) & 0xFFFFFF);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wrights.wright_upgrade.hint", Component.translatable(WrightTier.nameKey(tier - 1)))
                .withStyle(ChatFormatting.GRAY));
    }
}
