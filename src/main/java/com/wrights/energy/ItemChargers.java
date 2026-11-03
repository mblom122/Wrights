package com.wrights.energy;

import com.wrights.WrightsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Wireless pads such as Mekanism's chargepad only charge one inventory item per tick, and only
 * look at Curios after the whole inventory has refused energy. A worn personal wrightport then
 * never fills while the player carries other batteries. Standing on a block in
 * {@link #ITEM_CHARGERS} pulls Forge Energy from those other items into every personal wrightport
 * the player is carrying or wearing, so the pad's charge still reaches the port.
 */
@EventBusSubscriber(modid = WrightsMod.MOD_ID)
public final class ItemChargers {
    public static final TagKey<Block> ITEM_CHARGERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "item_chargers"));

    private ItemChargers() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !standingOnCharger(player)) {
            return;
        }
        PortEnergy.fillPersonalPortsFromCarried(player);
    }

    private static boolean standingOnCharger(Player player) {
        BlockPos feet = player.blockPosition();
        return player.level().getBlockState(feet).is(ITEM_CHARGERS)
                || player.level().getBlockState(feet.below()).is(ITEM_CHARGERS);
    }
}
