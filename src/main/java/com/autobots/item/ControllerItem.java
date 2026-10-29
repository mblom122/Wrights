package com.autobots.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.autobots.AutobotsMod;
import com.autobots.blueprint.BlueprintClientFlags;
import com.autobots.blueprint.BlueprintSessions;
import com.autobots.job.JobManager;
import com.autobots.util.Messages;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID)
public class ControllerItem extends Item {
    public ControllerItem(Properties properties) {
        super(properties);
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().getItem() instanceof ControllerItem
                || player.getOffhandItem().getItem() instanceof ControllerItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (BlueprintSessions.suppressController(player)) {
            return InteractionResult.SUCCESS;
        }
        quietLegacy(player);
        Messages.actionBar(player, "hud.autobots.task_ready_sub");
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (BlueprintSessions.suppressController(serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        quietLegacy(serverPlayer);
        if (serverPlayer.isShiftKeyDown()) {
            boolean stopped = JobManager.cancel(serverPlayer, true);
            if (!stopped) {
                Messages.actionBar(serverPlayer, "message.autobots.nothing_to_stop");
            }
            return InteractionResultHolder.success(stack);
        }
        Messages.actionBar(serverPlayer, "hud.autobots.task_ready_sub");
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.autobots.controller.hint.menu").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.corners").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.copy").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.paste").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.stop").withStyle(ChatFormatting.GRAY));
        ControllerData data = ControllerData.from(stack);
        if (data.hasSchematic()) {
            tooltip.add(Component.translatable(
                    "item.autobots.controller.stored",
                    data.sizeX(),
                    data.sizeY(),
                    data.sizeZ(),
                    (long) data.sizeX() * data.sizeY() * data.sizeZ(),
                    data.blockCount()).withStyle(ChatFormatting.GREEN));
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof ControllerItem)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && BlueprintSessions.suppressController(player)) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            return;
        }
        if (event.getLevel().isClientSide() && BlueprintClientFlags.suppressController) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            return;
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.TRUE);
    }

    public static void quietLegacy(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof ControllerItem)) {
                continue;
            }
            ControllerData data = ControllerData.from(stack);
            ControllerData cleared = data.withoutSelection();
            if (cleared != data) {
                ControllerData.write(stack, cleared);
            }
        }
    }
}
