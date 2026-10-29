package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    public static boolean hasData(ItemStack stack) {
        CompoundTag tag = stack.get(ModDataComponents.BLUEPRINT.get());
        return tag != null && tag.contains("blocks");
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.get(ModDataComponents.BLUEPRINT.get());
    }

    public static int handSlot(Player player, InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (tag == null || !tag.contains("name")) {
            return Component.translatable("item.wrights.blueprint");
        }
        String name = tag.getString("name");
        if (name.isBlank()) {
            return Component.translatable("item.wrights.blueprint");
        }
        return Component.translatable("item.wrights.blueprint.filled", name).withColor(0x7EB6FF);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = data(stack);
        if (tag == null || !tag.contains("blocks")) {
            tooltip.add(Component.translatable("item.wrights.blueprint.hint_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        tooltip.add(Component.translatable("item.wrights.blueprint.stored", tag.getInt("sx"), tag.getInt("sy"), tag.getInt("sz"), blocks).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.wrights.blueprint.hint_filled").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.wrights.client.BlueprintSlipScreens.open(handSlot(player, hand), hasData(stack));
        }
        return InteractionResultHolder.success(stack);
    }
}
