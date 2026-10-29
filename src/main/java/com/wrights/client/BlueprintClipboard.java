package com.wrights.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

public final class BlueprintClipboard {
    private static final String PREFIX = "wrights:1:";
    private static byte[] data = new byte[0];

    private BlueprintClipboard() {
    }

    public static boolean has() {
        return data.length > 0;
    }

    public static byte[] data() {
        return data;
    }

    public static void load() {
        byte[] fromClipboard = decode(Minecraft.getInstance().keyboardHandler.getClipboard());
        if (fromClipboard != null) {
            data = fromClipboard;
            writeFile(PREFIX + Base64.getEncoder().encodeToString(data));
            return;
        }
        try {
            Path path = path();
            if (Files.exists(path)) {
                byte[] saved = decode(Files.readString(path));
                data = saved == null ? new byte[0] : saved;
            }
        } catch (Exception ignored) {
            data = new byte[0];
        }
    }

    public static void store(byte[] incoming) {
        data = incoming == null ? new byte[0] : incoming;
        if (data.length == 0) {
            return;
        }
        String text = PREFIX + Base64.getEncoder().encodeToString(data);
        writeFile(text);
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private static void writeFile(String text) {
        try {
            Files.writeString(path(), text);
        } catch (Exception ignored) {
            // The copy still sits on the system clipboard for this session.
        }
    }

    private static byte[] decode(String text) {
        if (text == null || !text.startsWith(PREFIX)) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(text.substring(PREFIX.length()).trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("wrights-clipboard.txt");
    }
}
