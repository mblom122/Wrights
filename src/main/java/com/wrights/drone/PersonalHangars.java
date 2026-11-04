package com.wrights.drone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import com.wrights.item.PersonalWrightportItem;
import com.wrights.menu.PersonalHangarMenu;

/** Opens the hangar UI for a carried personal wrightport (inventory or curios). */
public final class PersonalHangars {
    private PersonalHangars() {
    }

    public static void open(ServerPlayer player, WrightportPlaces.Spot spot) {
        if (player == null || spot == null) {
            return;
        }
        ItemStack stack = WrightportPlaces.stack(player, spot.kind(), spot.index());
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            return;
        }
        Component title = stack.getHoverName();
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> new PersonalHangarMenu(id, inventory, spot.kind(), spot.index()),
                title), buf -> {
            buf.writeByte(spot.kind());
            buf.writeVarInt(spot.index());
        });
    }
}
