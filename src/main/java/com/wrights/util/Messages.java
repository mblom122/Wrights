package com.wrights.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Messages {
    private Messages() {
    }

    public static void actionBar(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
