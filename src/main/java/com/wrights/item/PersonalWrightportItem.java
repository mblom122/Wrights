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
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.hint").withStyle(ChatFormatting.DARK_GRAY));
        if (energyView != null) {
            energyView.tooltip(tooltip);
        }
    }

    /** Client hook for the curio slot bar and its energy numbers. The server leaves this unset. */
    public static EnergyView energyView;

    public interface EnergyView {
        boolean visible();

        int width();

        int color();

        void tooltip(List<Component> tooltip);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energyView != null && energyView.visible();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return energyView == null ? 0 : energyView.width();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return energyView == null ? 0x3D9A62 : energyView.color();
    }
}
