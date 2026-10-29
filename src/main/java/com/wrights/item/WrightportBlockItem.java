package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import com.wrights.drone.Swarm;

public class WrightportBlockItem extends BlockItem {
    public WrightportBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Swarm swarm = Swarm.from(stack);
        if (!swarm.units().isEmpty()) {
            tooltip.add(Component.translatable("item.wrights.drone_cradle.drones", swarm.units().size(), Swarm.MAX).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.wrights.wrightport.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
