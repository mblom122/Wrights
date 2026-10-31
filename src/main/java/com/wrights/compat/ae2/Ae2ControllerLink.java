package com.wrights.compat.ae2;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.menu.implementations.WirelessAccessPointMenu;

import com.wrights.item.ControllerItem;
import com.wrights.item.ModDataComponents;
import com.wrights.item.ModItems;
import com.wrights.storage.DigitalBinder;

/**
 * Lets the controller sit in an ME wireless access point's link slot, the same way
 * Logistics Networks registers its wrench with {@link GridLinkables}.
 */
public final class Ae2ControllerLink {
    private Ae2ControllerLink() {
    }

    public static void register() {
        GridLinkables.register(ModItems.CONTROLLER.get(), new Handler());
    }

    private static final class Handler implements IGridLinkableHandler {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof ControllerItem;
        }

        @Override
        public void link(ItemStack stack, GlobalPos pos) {
            if (pos == null) {
                return;
            }
            stack.set(ModDataComponents.AE_LINK.get(), pos);
            Player player = opener(pos);
            if (player instanceof ServerPlayer serverPlayer) {
                DigitalBinder.bindLinked(serverPlayer, pos);
            }
        }

        @Override
        public void unlink(ItemStack stack) {
            stack.remove(ModDataComponents.AE_LINK.get());
        }
    }

    private static Player opener(GlobalPos pos) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || pos == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof WirelessAccessPointMenu menu)) {
                continue;
            }
            BlockEntity block = menu.getBlockEntity();
            if (block == null || block.getLevel() == null) {
                continue;
            }
            if (block.getBlockPos().equals(pos.pos()) && block.getLevel().dimension().equals(pos.dimension())) {
                return player;
            }
        }
        return null;
    }
}
