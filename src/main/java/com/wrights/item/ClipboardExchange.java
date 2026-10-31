package com.wrights.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;

import com.wrights.WrightsConfig;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

public final class ClipboardExchange {
    private static final int MAX_BYTES = 512_000;

    private ClipboardExchange() {
    }

    public static void copy(ServerPlayer player, java.util.UUID id, boolean folder) {
        if (id == null) {
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        CompoundTag body;
        if (folder) {
            body = BlueprintFolders.exportTag(library, id);
            if (body == null) {
                Messages.actionBar(player, "message.wrights.slip_folder_missing");
                return;
            }
        } else {
            Schematic schematic = library.schematic(id).orElse(null);
            if (schematic == null || schematic.ephemeral()) {
                Messages.actionBar(player, "message.wrights.blueprint_missing");
                return;
            }
            body = schematic.save();
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.putBoolean("folder", folder);
        wrapper.put("body", body);
        byte[] data = encode(wrapper);
        if (data == null || data.length > MAX_BYTES) {
            Messages.actionBar(player, "message.wrights.slip_too_big");
            return;
        }
        BlueprintNetwork.sendClipboard(player, data, status(player, wrapper));
        Messages.actionBar(player, "message.wrights.clipboard_copied", wrapper.getCompound("body").getString("name"));
    }

    public static void preview(ServerPlayer player, byte[] data) {
        CompoundTag wrapper = decode(data);
        if (wrapper == null) {
            BlueprintNetwork.sendClipboardStatus(player, BlueprintPayloads.ClipboardStatus.EMPTY);
            return;
        }
        BlueprintNetwork.sendClipboardStatus(player, status(player, wrapper));
    }

    public static void importClip(ServerPlayer player, byte[] data, int mode, String name) {
        if (WrightsConfig.refuse(player, WrightsConfig.allowTransfer())) {
            return;
        }
        CompoundTag wrapper = decode(data);
        if (wrapper == null || !wrapper.contains("body")) {
            Messages.actionBar(player, "message.wrights.clipboard_bad");
            BlueprintNetwork.sendClipboardStatus(player, BlueprintPayloads.ClipboardStatus.EMPTY);
            return;
        }
        CompoundTag body = wrapper.getCompound("body");
        if (wrapper.getBoolean("folder")) {
            if (!BlueprintFolders.importTag(player, body, mode, name)) {
                Messages.actionBar(player, "message.wrights.clipboard_bad");
            }
        } else {
            try {
                Schematic stored = Schematic.load(body, Schematic.lookup(player.registryAccess()));
                BlueprintSlips.accept(player, stored, mode, name);
            } catch (RuntimeException ignored) {
                Messages.actionBar(player, "message.wrights.clipboard_bad");
            }
        }
        BlueprintNetwork.syncLibrary(player);
        preview(player, data);
    }

    private static BlueprintPayloads.ClipboardStatus status(ServerPlayer player, CompoundTag wrapper) {
        CompoundTag body = wrapper.getCompound("body");
        if (wrapper.getBoolean("folder")) {
            BlueprintFolders.Preview preview = BlueprintFolders.preview(player, body);
            if (preview == null) {
                return BlueprintPayloads.ClipboardStatus.EMPTY;
            }
            return new BlueprintPayloads.ClipboardStatus(true, preview.name(), preview.folders(), preview.blueprints(), 0, preview.blueprints(), preview.kind(), preview.nextName(), preview.libraryBlueprints());
        }
        try {
            Schematic stored = Schematic.load(body, Schematic.lookup(player.registryAccess()));
            BlueprintSlips.Preview preview = BlueprintSlips.preview(player, stored);
            return new BlueprintPayloads.ClipboardStatus(false, preview.name(), preview.sizeX(), preview.sizeY(), preview.sizeZ(), preview.blocks(), preview.kind(), preview.nextName(), preview.libraryBlocks());
        } catch (RuntimeException ignored) {
            return BlueprintPayloads.ClipboardStatus.EMPTY;
        }
    }

    public static byte[] encode(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                NbtIo.write(tag, new DataOutputStream(gzip));
            }
            return out.toByteArray();
        } catch (IOException ignored) {
            return null;
        }
    }

    public static CompoundTag decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            return null;
        }
        try {
            return NbtIo.read(new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data))));
        } catch (IOException ignored) {
            return null;
        }
    }
}
