package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.wrights.drone.Cradles;
import com.wrights.drone.Swarm;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class CradleItem extends Item implements ICurioItem {
    public CradleItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return Cradles.SLOT.equals(slotContext.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Swarm swarm = Swarm.from(stack);
        tooltip.add(Component.translatable("item.wrights.drone_cradle.drones", swarm.units().size(), Swarm.MAX).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wrights.drone_cradle.assign", swarm.assignCount()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wrights.drone_cradle.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
