package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class BlueprintFolderItem extends Item {
    public BlueprintFolderItem(Properties properties) {
        super(properties);
    }

    public static boolean hasData(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag != null && tag.contains("entries");
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.get(ModDataComponents.FOLDER.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = data(stack);
        if (tag == null || !tag.contains("name") || tag.getString("name").isBlank()) {
            return Component.translatable("item.wrights.blueprint_folder");
        }
        return Component.translatable("item.wrights.blueprint_folder.filled", tag.getString("name")).withColor(0x7EB6FF);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = data(stack);
        if (!hasData(stack)) {
            tooltip.add(Component.translatable("item.wrights.blueprint_folder.hint_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int folders = 0;
        int blueprints = 0;
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.getCompound(i).getBoolean("folder")) {
                folders++;
            } else {
                blueprints++;
            }
        }
        tooltip.add(Component.translatable("item.wrights.blueprint_folder.stored", folders, blueprints).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.wrights.blueprint_folder.hint_filled").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.wrights.client.BlueprintSlipScreens.openFolder(BlueprintItem.handSlot(player, hand), hasData(stack));
        }
        return InteractionResultHolder.success(stack);
    }
}
