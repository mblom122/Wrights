package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.wrights.drone.Wrightports;
import com.wrights.drone.Swarm;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class PersonalWrightportItem extends Item implements ICurioItem {
    public PersonalWrightportItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return Wrightports.SLOT.equals(slotContext.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return canEquip(slotContext, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Swarm swarm = Swarm.from(stack);
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.drones", swarm.units().size(), Swarm.capacity()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.assign", swarm.assignCount()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
