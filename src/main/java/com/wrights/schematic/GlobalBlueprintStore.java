package com.wrights.schematic;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    /** True after a load that skipped something, so saves leave the last good backup alone. */
    private boolean protectBackup;

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

    private static Path backupFile() {
        return directory().resolve("library.dat.bak");
    }

    /** What one file gave back. {@code clean} is false when anything in it had to be skipped. */
    private record Read(GlobalBlueprintStore store, boolean clean) {
    }

    /**
     * Loads the library. A file that cannot be read cleanly is never trusted blindly: the next save
     * would replace it, so a copy is kept first, and the last good backup is used when it is better.
     */
    private static GlobalBlueprintStore load() {
        Path main = file();
        boolean present = Files.isRegularFile(main);
        Read first = present ? read(main) : null;
        if (first != null && first.clean()) {
            return first.store();
        }
        if (present) {
            keepCopy(main);
        }
        Path backup = backupFile();
        Read second = Files.isRegularFile(backup) ? read(backup) : null;
        if (second != null && second.clean()
                && (first == null || second.store().schematics.size() > first.store().schematics.size())) {
            WrightsMod.LOGGER.warn("Using the Wrights blueprint backup {}", backup);
            // The main file on disk is the damaged one. The first save must not copy it over this backup.
            second.store().protectBackup = present;
            return second.store();
        }
        if (first != null) {
            return first.store();
        }
        return second != null ? second.store() : new GlobalBlueprintStore();
    }

    /** Returns null when the file cannot be read at all. A bad entry is skipped and marks the read unclean. */
    private static Read read(Path path) {
        GlobalBlueprintStore store = new GlobalBlueprintStore();
        boolean clean = true;
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            var blocks = BuiltInRegistries.BLOCK.asLookup();
            ListTag schematics = tag.getList("schematics", Tag.TAG_COMPOUND);
            for (int i = 0; i < schematics.size(); i++) {
                try {
                    Schematic schematic = Schematic.load(schematics.getCompound(i), blocks);
                    store.schematics.put(schematic.id(), schematic);
                } catch (RuntimeException ex) {
                    clean = false;
                    WrightsMod.LOGGER.warn("Skipped blueprint {} in {}", i, path, ex);
                }
            }
            if (tag.contains("shelf", Tag.TAG_COMPOUND)) {
                try {
                    store.shelf.load(tag.getCompound("shelf"));
                } catch (RuntimeException ex) {
                    clean = false;
                    WrightsMod.LOGGER.warn("Skipped the blueprint shelf in {}", path, ex);
                }
            }
        } catch (IOException | RuntimeException ex) {
            WrightsMod.LOGGER.warn("Could not load global Wrights blueprints from {}", path, ex);
            return null;
        }
        // Saving this store later must not rotate its damaged source over the good backup.
        store.protectBackup = !clean;
        return new Read(store, clean);
    }

    /** Copies a damaged file aside so a later save cannot destroy the only copy of what was in it. */
    private static void keepCopy(Path path) {
        Path copy = path.resolveSibling(path.getFileName() + ".damaged-" + System.currentTimeMillis());
        try {
            Files.copy(path, copy);
            WrightsMod.LOGGER.warn("Kept a copy of the unreadable blueprint library at {}", copy);
        } catch (IOException ex) {
            WrightsMod.LOGGER.warn("Could not keep a copy of {}", path, ex);
        }
    }

    /**
     * Writes to a temporary file and swaps it in, so a crash mid-save leaves the old library
     * intact instead of a truncated one. The previous library is kept as {@code library.dat.bak}.
     */
    public synchronized void save() {
        Path target = file();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory());
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (Schematic schematic : schematics.values()) {
                list.add(schematic.save());
            }
            tag.put("schematics", list);
            tag.put("shelf", shelf.save());
            NbtIo.writeCompressed(tag, temp);
            if (!protectBackup && Files.isRegularFile(target)) {
                try {
                    Files.copy(target, backupFile(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    WrightsMod.LOGGER.warn("Could not refresh the blueprint backup", ex);
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            // The file on disk is now one this store wrote itself, so it is safe to keep as the backup.
            protectBackup = false;
        } catch (IOException ex) {
            WrightsMod.LOGGER.warn("Could not save global Wrights blueprints to {}", target, ex);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // A leftover temp file is overwritten by the next save.
            }
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
