package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.Swarm;

public class WrightportBlockItem extends BlockItem {
    public WrightportBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static String labelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String name = stack.get(ModDataComponents.PORT_NAME.get());
        return WrightportBlockEntity.cleanLabel(name);
    }

    public static void writeLabel(ItemStack stack, String name) {
        String clean = WrightportBlockEntity.cleanLabel(name);
        if (clean.isBlank()) {
            stack.remove(ModDataComponents.PORT_NAME.get());
        } else {
            stack.set(ModDataComponents.PORT_NAME.get(), clean);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        String name = labelOf(stack);
        if (!name.isBlank()) {
            return WrightportBlockEntity.titled(name);
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Swarm swarm = Swarm.from(stack);
        if (!swarm.units().isEmpty()) {
            tooltip.add(Component.translatable("item.wrights.personal_wrightport.drones", swarm.units().size(), Swarm.capacity()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.wrights.wrightport.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
