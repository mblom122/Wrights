package com.wrights.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.network.BlueprintPayloads;

public final class BlueprintSlipScreens {
    private BlueprintSlipScreens() {
    }

    public static void open(int slot, boolean filled) {
        Minecraft minecraft = Minecraft.getInstance();
        if (filled) {
            minecraft.setScreen(new BlueprintLoadScreen(null));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestBlueprintItems());
        } else {
            minecraft.setScreen(new BlueprintSaveScreen(slot, null));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestSlipLibrary());
        }
    }

    public static void openFolder(int slot, boolean filled) {
        Minecraft minecraft = Minecraft.getInstance();
        if (filled) {
            minecraft.setScreen(new FolderLoadScreen(null));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestFolderItems());
        } else {
            minecraft.setScreen(new FolderSaveScreen(slot, null));
            PacketDistributor.sendToServer(new BlueprintPayloads.RequestSlipLibrary());
        }
    }

    public static void applyItems(List<BlueprintPayloads.BlueprintItemLine> lines) {
        if (Minecraft.getInstance().screen instanceof BlueprintLoadScreen screen) {
            screen.apply(lines);
        }
    }

    public static void applyFolders(List<BlueprintPayloads.FolderItemLine> lines) {
        if (Minecraft.getInstance().screen instanceof FolderLoadScreen screen) {
            screen.apply(lines);
        }
    }
}
