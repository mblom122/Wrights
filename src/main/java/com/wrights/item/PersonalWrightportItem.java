package com.wrights.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.wrights.drone.PersonalHangars;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightportPlaces;
import com.wrights.drone.Wrightports;
import com.wrights.energy.PortEnergy;

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
        // Right-click opens the hangar; equip from the Curios inventory instead.
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            WrightportPlaces.Spot spot = hand == InteractionHand.OFF_HAND
                    ? new WrightportPlaces.Spot(WrightportPlaces.OFF, 0)
                    : new WrightportPlaces.Spot(WrightportPlaces.MAIN, player.getInventory().selected);
            PersonalHangars.open(server, spot);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Right-click the item in an inventory / Curios slot (empty cursor) to open its hangar.
     */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack stack,
            ItemStack other,
            Slot slot,
            ClickAction action,
            Player player,
            SlotAccess access) {
        if (action != ClickAction.SECONDARY || !other.isEmpty()) {
            return false;
        }
        if (player instanceof ServerPlayer server) {
            WrightportPlaces.Spot spot = WrightportPlaces.find(server, stack);
            if (spot != null) {
                PersonalHangars.open(server, spot);
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Swarm swarm = Swarm.from(stack);
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.drones", swarm.units().size(), Swarm.capacity()).withStyle(ChatFormatting.GRAY));
        PortEnergy.appendItemTooltip(stack, tooltip);
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.open").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return PortEnergy.itemBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return PortEnergy.itemBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return PortEnergy.itemBarColor(stack) & 0xFFFFFF;
    }
}
