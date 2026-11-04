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

    /** Hangar tip color; 0 keeps the client's default tip color. */
    public static void actionBar(ServerPlayer player, int color, String key, Object... args) {
        actionBar(player, color, Component.translatable(key, args));
    }

    public static void actionBar(ServerPlayer player, Component text) {
        actionBar(player, 0, text);
    }

    public static void actionBar(ServerPlayer player, int color, Component text) {
        if (player == null || text == null) {
            return;
        }
        player.displayClientMessage(text, true);
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.Notice(text, false, color));
    }

    /** Short white mid-screen tip for no-op / nothing-to-do feedback — not a job toast. */
    public static void centerTip(ServerPlayer player, String key, Object... args) {
        centerTip(player, Component.translatable(key, args));
    }

    public static void centerTip(ServerPlayer player, Component text) {
        if (player == null || text == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.Notice(text, true));
    }
}
