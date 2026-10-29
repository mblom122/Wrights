package com.autobots.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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

import com.autobots.AutobotsConfig;
import com.autobots.AutobotsMod;
import com.autobots.job.JobManager;
import com.autobots.util.Messages;

@EventBusSubscriber(modid = AutobotsMod.MOD_ID)
public class ControllerItem extends Item {
    public ControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        ControllerData data = ControllerData.from(stack).ensureId();
        if (player.isShiftKeyDown()) {
            return aimOrLaunch(player, stack, data, context);
        }
        return setCorner(player, stack, data, GlobalPos.of(level.dimension(), context.getClickedPos()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        ControllerData data = ControllerData.from(stack).ensureId();
        ControllerData.write(stack, data);

        if (serverPlayer.isShiftKeyDown()) {
            boolean stopped = JobManager.cancel(serverPlayer, true);
            ControllerData current = ControllerData.from(stack);
            if (current.armedOrigin().isPresent()) {
                ControllerData.write(stack, current.withoutAim());
                if (!stopped) {
                    Messages.actionBar(serverPlayer, "message.autobots.aim_cancelled");
                }
            } else if (!stopped) {
                Messages.actionBar(serverPlayer, "message.autobots.nothing_to_stop");
            }
            return InteractionResultHolder.success(stack);
        }

        if (data.armedOrigin().isPresent()) {
            ControllerData.write(stack, data.withoutAim());
            Messages.actionBar(serverPlayer, "message.autobots.aim_cancelled");
            return InteractionResultHolder.success(stack);
        }
        if (JobManager.jobFor(serverPlayer).isPresent()) {
            Messages.actionBar(serverPlayer, "message.autobots.busy");
            return InteractionResultHolder.success(stack);
        }
        if (data.cornerA().isEmpty() || data.cornerB().isEmpty()) {
            Messages.actionBar(serverPlayer, "message.autobots.need_corners");
            return InteractionResultHolder.success(stack);
        }
        JobManager.startCopy(serverPlayer, stack, ControllerData.from(stack));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.autobots.controller.hint.corners").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.copy").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.paste").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.autobots.controller.hint.stop").withStyle(ChatFormatting.GRAY));
        ControllerData data = ControllerData.from(stack);
        if (data.hasSchematic()) {
            tooltip.add(Component.translatable(
                    "item.autobots.controller.stored",
                    data.blockCount(),
                    data.sizeX(),
                    data.sizeY(),
                    data.sizeZ()).withStyle(ChatFormatting.GREEN));
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof ControllerItem)) {
            return;
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.TRUE);
    }

    private static InteractionResult setCorner(ServerPlayer player, ItemStack stack, ControllerData data, GlobalPos pos) {
        if (data.cornerA().isEmpty() || data.cornerB().isPresent()) {
            ControllerData.write(stack, data.withCornerA(pos));
            BlockPos blockPos = pos.pos();
            Messages.actionBar(player, "message.autobots.corner1", blockPos.getX(), blockPos.getY(), blockPos.getZ());
            return InteractionResult.SUCCESS;
        }
        GlobalPos cornerA = data.cornerA().get();
        if (!cornerA.dimension().equals(pos.dimension())) {
            Messages.actionBar(player, "message.autobots.different_dimension");
            return InteractionResult.SUCCESS;
        }
        BlockPos first = cornerA.pos();
        BlockPos second = pos.pos();
        int sizeX = Math.abs(first.getX() - second.getX()) + 1;
        int sizeY = Math.abs(first.getY() - second.getY()) + 1;
        int sizeZ = Math.abs(first.getZ() - second.getZ()) + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (sizeX > AutobotsConfig.MAX_AXIS.getAsInt()
                || sizeY > AutobotsConfig.MAX_AXIS.getAsInt()
                || sizeZ > AutobotsConfig.MAX_AXIS.getAsInt()
                || volume > AutobotsConfig.MAX_BLOCKS.getAsInt()) {
            Messages.actionBar(player, "message.autobots.too_big", AutobotsConfig.MAX_AXIS.getAsInt(), AutobotsConfig.MAX_BLOCKS.getAsInt());
            return InteractionResult.SUCCESS;
        }
        ControllerData.write(stack, data.withCornerB(pos));
        Messages.actionBar(player, "message.autobots.corner2", sizeX, sizeY, sizeZ, volume);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult aimOrLaunch(ServerPlayer player, ItemStack stack, ControllerData data, UseOnContext context) {
        if (JobManager.jobFor(player).isPresent()) {
            Messages.actionBar(player, "message.autobots.busy");
            return InteractionResult.SUCCESS;
        }
        if (!data.hasSchematic()) {
            Messages.actionBar(player, "message.autobots.need_copy");
            return InteractionResult.SUCCESS;
        }
        BlockPos originPos = context.getClickedPos().relative(context.getClickedFace());
        GlobalPos origin = GlobalPos.of(context.getLevel().dimension(), originPos);
        if (data.armedOrigin().isPresent() && data.armedOrigin().get().equals(origin)) {
            JobManager.startPaste(player, stack, data, origin);
            return InteractionResult.SUCCESS;
        }
        ControllerData.write(stack, data.withAim(origin));
        Messages.actionBar(player, "message.autobots.armed");
        return InteractionResult.SUCCESS;
    }
}
