package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.wrights.drone.PartSlot;
import com.wrights.drone.WrightTier;

/** One drone part. Fitted into its {@link PartSlot} on a wright, from Manage or by clicking it onto a wright item. */
public class WrightPartItem extends Item {
    private final PartSlot slot;
    private final int tier;

    public WrightPartItem(Properties properties, PartSlot slot, int tier) {
        super(properties);
        this.slot = slot;
        this.tier = WrightTier.clamp(tier);
    }

    public PartSlot slot() {
        return slot;
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
        tooltip.add(stat(slot, tier).withStyle(ChatFormatting.GRAY));
        if (slot == PartSlot.FABRICATOR) {
            tooltip.add(Component.translatable(reachKey(tier)).withStyle(ChatFormatting.DARK_GRAY));
        } else if (slot == PartSlot.CORE) {
            tooltip.add(Component.translatable("item.wrights.core.match", tierName(tier)).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.wrights.part.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** What a part of this slot and tier does, e.g. "Flight Speed: 17.5 blocks/s (+250% boost)". */
    public static net.minecraft.network.chat.MutableComponent stat(PartSlot slot, int tier) {
        return switch (slot) {
            case ROTOR -> Component.translatable("item.wrights.rotor.speed",
                    WrightTier.flyRateLabel(tier), WrightTier.flyBoostPercent(tier));
            case FABRICATOR -> Component.translatable("item.wrights.fabricator.blocks", WrightTier.operationCap(tier));
            case CORE -> Component.translatable("item.wrights.core.rating", WrightTier.rotorDraw(tier));
        };
    }

    private static Component tierName(int tier) {
        return Component.translatable(WrightTier.nameKey(tier)).withColor(WrightTier.textColor(tier) & 0xFFFFFF);
    }

    public static String reachKey(int tier) {
        return switch (WrightTier.operationReachSqr(tier)) {
            case 0 -> "item.wrights.fabricator.cell";
            case 1 -> "item.wrights.fabricator.faces";
            default -> "item.wrights.fabricator.sphere";
        };
    }
}
