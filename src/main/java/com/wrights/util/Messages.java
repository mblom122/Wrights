package com.wrights.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public final class Messages {
    private Messages() {
    }

    public static void actionBar(ServerPlayer player, String key, Object... args) {
        actionBar(player, Component.translatable(key, args));
    }

    public static void actionBar(ServerPlayer player, Component text) {
        if (player == null || text == null) {
            return;
        }
        player.displayClientMessage(text, true);
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.Notice(text));
    }
}
