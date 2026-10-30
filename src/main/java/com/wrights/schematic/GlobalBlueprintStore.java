package com.wrights.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import com.wrights.WrightsMod;

/**
 * Blueprints shared outside any single Minecraft instance, so modpacks can reuse them.
 * Windows: {@code %APPDATA%/Wrights/blueprints/library.dat}
 * Other: {@code ~/.wrights/blueprints/library.dat}
 */
public final class GlobalBlueprintStore {
    private static GlobalBlueprintStore instance;

    private final Map<UUID, Schematic> schematics = new HashMap<>();
    private final BlueprintShelf shelf = new BlueprintShelf();

    private GlobalBlueprintStore() {
    }

    public static synchronized GlobalBlueprintStore get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static Path directory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "Wrights", "blueprints");
        }
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".wrights", "blueprints");
    }

    private static Path file() {
        return directory().resolve("library.dat");
    }

    private static GlobalBlueprintStore load() {
        GlobalBlueprintStore store = new GlobalBlueprintStore();
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return store;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            var blocks = BuiltInRegistries.BLOCK.asLookup();
            ListTag schematics = tag.getList("schematics", Tag.TAG_COMPOUND);
            for (int i = 0; i < schematics.size(); i++) {
                Schematic schematic = Schematic.load(schematics.getCompound(i), blocks);
                store.schematics.put(schematic.id(), schematic);
            }
            if (tag.contains("shelf", Tag.TAG_COMPOUND)) {
                store.shelf.load(tag.getCompound("shelf"));
            }
        } catch (Exception ex) {
            WrightsMod.LOGGER.warn("Could not load global Wrights blueprints from {}", path, ex);
        }
        return store;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(directory());
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (Schematic schematic : schematics.values()) {
                list.add(schematic.save());
            }
            tag.put("schematics", list);
            tag.put("shelf", shelf.save());
            NbtIo.writeCompressed(tag, file());
        } catch (IOException ex) {
            WrightsMod.LOGGER.warn("Could not save global Wrights blueprints to {}", file(), ex);
        }
    }

    public Optional<Schematic> schematic(UUID id) {
        return Optional.ofNullable(schematics.get(id));
    }

    public Collection<Schematic> schematics() {
        return schematics.values();
    }

    public BlueprintShelf shelf() {
        return shelf;
    }

    public void putSchematic(Schematic schematic) {
        schematics.put(schematic.id(), schematic);
        save();
    }

    public void removeSchematic(UUID id) {
        if (schematics.remove(id) != null) {
            shelf.forget(id);
            save();
        }
    }

    public void markShelfChanged() {
        save();
    }

    public static synchronized void reload() {
        instance = load();
    }

    public static void warm(MinecraftServer server) {
        get();
    }
}
