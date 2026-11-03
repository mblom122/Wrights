package com.wrights.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.wrights.WrightsConfig;
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
        for (Component line : stats(slot, tier)) {
            tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.wrights.part.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Bullet lines for this part (Manage, wright tooltip, part tooltip). */
    public static List<Component> stats(PartSlot slot, int tier) {
        List<Component> lines = new ArrayList<>(3);
        switch (slot) {
            case ROTOR -> {
                int bonus = WrightTier.flyBonusPercent(tier);
                String signed = bonus > 0 ? "+" + bonus : Integer.toString(bonus);
                lines.add(Component.translatable("item.wrights.rotor.speed", WrightTier.flyRateLabel(tier), signed));
            }
            case FABRICATOR -> {
                // Energy is paid once per cell action — Apex 19 actions/tick = up to 19 charges that tick.
                int perAction = WrightsConfig.ENERGY.get() ? WrightsConfig.ENERGY_PLACE.get() : 0;
                lines.add(Component.translatable("item.wrights.fabricator.actions", WrightTier.actionsPerTick(tier)));
                lines.add(Component.translatable("item.wrights.fabricator.energy", perAction));
                lines.add(Component.translatable(reachKey(tier)));
            }
            case CORE -> {
                lines.add(Component.translatable("item.wrights.core.efficiency", WrightTier.coreDrainPercent(tier)));
            }
        }
        return lines;
    }

    /** First stat line — kept for callers that only need one. */
    public static MutableComponent stat(PartSlot slot, int tier) {
        return stats(slot, tier).get(0).copy();
    }

    /** Reach copy is per fabricator tier so Scout…Apex do not share one string. */
    public static String reachKey(int tier) {
        return switch (WrightTier.clamp(tier)) {
            case WrightTier.BASIC -> "item.wrights.fabricator.reach_basic";
            case WrightTier.SCOUT -> "item.wrights.fabricator.reach_scout";
            case WrightTier.RANGER -> "item.wrights.fabricator.reach_ranger";
            case WrightTier.VECTOR -> "item.wrights.fabricator.reach_vector";
            case WrightTier.TEMPEST -> "item.wrights.fabricator.reach_tempest";
            default -> "item.wrights.fabricator.reach_apex";
        };
    }
}
