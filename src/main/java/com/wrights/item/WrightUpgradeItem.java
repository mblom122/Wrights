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
    private final boolean operations;

    public WrightUpgradeItem(Properties properties, int tier) {
        this(properties, tier, false);
    }

    public WrightUpgradeItem(Properties properties, int tier, boolean operations) {
        super(properties);
        this.tier = tier;
        this.operations = operations;
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
        if (operations) {
            int blocks = WrightTier.operationCap(tier);
            String reach = switch (WrightTier.operationReachSqr(tier)) {
                case 0 -> "item.wrights.wright_ops.cell";
                case 1 -> "item.wrights.wright_ops.faces";
                default -> "item.wrights.wright_ops.sphere";
            };
            tooltip.add(Component.translatable("item.wrights.wright_ops.blocks", blocks)
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(reach).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                            "item.wrights.wright_ops.hint",
                            Component.translatable(WrightTier.nameKey(tier)))
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.wrights.wright_upgrade.speed",
                        WrightTier.flyRateLabel(tier),
                        WrightTier.flyRateLabel(WrightTier.BASE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wrights.wright_upgrade.effect")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.wrights.wright_upgrade.hint",
                        Component.translatable(WrightTier.nameKey(tier - 1)))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
