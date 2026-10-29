package com.wrights.item;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.wrights.blueprint.BlueprintActions;
import com.wrights.network.BlueprintNetwork;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

public final class BlueprintSlips {
    public static final int NEW = 0;
    public static final int DIFFERENT = 1;
    public static final int EXACT = 2;
    public static final int LOAD = 0;
    public static final int REPLACE = 1;
    public static final int NEXT_NUMBER = 2;
    public static final int NAMED = 3;
    private static final int MAX_BYTES = 384 * 1024;

    private BlueprintSlips() {
    }

    public static void sendList(ServerPlayer player) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Inventory inventory = player.getInventory();
        List<BlueprintPayloads.BlueprintItemLine> lines = new ArrayList<>();
        for (int slot : slots(inventory)) {
            ItemStack stack = inventory.getItem(slot);
            Schematic stored = read(stack, player);
            if (stored == null) {
                continue;
            }
            Schematic match = named(library, stored.name());
            int kind = match == null ? NEW : match.sameBlocks(stored) ? EXACT : DIFFERENT;
            String next = BlueprintActions.uniqueName(stored.name(), BlueprintActions.takenBlueprintNames(library));
            lines.add(new BlueprintPayloads.BlueprintItemLine(
                    slot,
                    stored.name(),
                    stored.sizeX(),
                    stored.sizeY(),
                    stored.sizeZ(),
                    stored.blockCount(),
                    kind,
                    next,
                    match == null ? -1 : match.blockCount()));
        }
        BlueprintNetwork.syncLibrary(player);
        BlueprintNetwork.sendBlueprintItems(player, lines);
    }

    public static void write(ServerPlayer player, int slot, UUID schematicId) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic schematic = library.schematic(schematicId).orElse(null);
        if (schematic == null || schematic.ephemeral()) {
            Messages.actionBar(player, "message.wrights.blueprint_missing");
            return;
        }
        CompoundTag tag = schematic.save();
        if (!fits(tag)) {
            Messages.actionBar(player, "message.wrights.slip_too_big");
            return;
        }
        Inventory inventory = player.getInventory();
        int target = slot >= 0 ? slot : findBlank(inventory);
        if (target < 0 || !blank(inventory.getItem(target))) {
            Messages.actionBar(player, "message.wrights.slip_need_blank");
            return;
        }
        ItemStack stack = inventory.getItem(target);
        if (stack.getCount() == 1) {
            stack.set(ModDataComponents.BLUEPRINT.get(), tag);
        } else {
            stack.shrink(1);
            ItemStack one = new ItemStack(ModItems.BLUEPRINT.get());
            one.set(ModDataComponents.BLUEPRINT.get(), tag);
            if (!inventory.add(one)) {
                player.drop(one, false);
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        Messages.actionBar(player, "message.wrights.slip_saved", schematic.name());
    }

    public static void load(ServerPlayer player, int slot, int mode, String requestedName) {
        Schematic stored = read(player.getInventory().getItem(slot), player);
        if (stored == null) {
            Messages.actionBar(player, "message.wrights.slip_empty");
            sendList(player);
            return;
        }
        accept(player, stored, mode, requestedName);
        sendList(player);
    }

    public static void accept(ServerPlayer player, Schematic stored, int mode, String requestedName) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        List<String> taken = BlueprintActions.takenBlueprintNames(library);
        Schematic match = named(library, stored.name());
        boolean exact = match != null && match.sameBlocks(stored);
        if (mode == REPLACE) {
            if (match == null) {
                mode = LOAD;
            } else if (exact) {
                Messages.actionBar(player, "message.wrights.slip_same", match.name());
                return;
            } else if (library.isInUse(match.id())) {
                Messages.actionBar(player, "message.wrights.slip_in_use");
                return;
            } else {
                library.putSchematic(match.withContents(stored));
                Messages.actionBar(player, "message.wrights.slip_replaced", match.name());
                return;
            }
        }
        String name;
        if (mode == NEXT_NUMBER) {
            name = BlueprintActions.uniqueName(stored.name(), taken);
        } else if (mode == NAMED) {
            if (requestedName == null || requestedName.isBlank()) {
                Messages.actionBar(player, "message.wrights.name_taken");
                return;
            }
            String clean = BlueprintActions.cleanName(requestedName);
            if (!BlueprintActions.uniqueName(clean, taken).equals(clean)) {
                Messages.actionBar(player, "message.wrights.name_taken");
                return;
            }
            name = clean;
        } else if (match != null) {
            Messages.actionBar(player, "message.wrights.name_taken");
            return;
        } else {
            name = BlueprintActions.cleanName(stored.name());
        }
        library.putSchematic(stored.duplicate(UUID.randomUUID(), name));
        Messages.actionBar(player, "message.wrights.slip_loaded", name);
    }

    public static Preview preview(ServerPlayer player, Schematic stored) {
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Schematic match = named(library, stored.name());
        int kind = match == null ? NEW : match.sameBlocks(stored) ? EXACT : DIFFERENT;
        String next = BlueprintActions.uniqueName(stored.name(), BlueprintActions.takenBlueprintNames(library));
        return new Preview(stored.name(), stored.sizeX(), stored.sizeY(), stored.sizeZ(), stored.blockCount(), kind, next, match == null ? -1 : match.blockCount());
    }

    public record Preview(String name, int sizeX, int sizeY, int sizeZ, int blocks, int kind, String nextName, int libraryBlocks) {
    }

    private static Schematic read(ItemStack stack, ServerPlayer player) {
        if (!BlueprintItem.hasData(stack)) {
            return null;
        }
        try {
            return Schematic.load(BlueprintItem.data(stack), Schematic.lookup(player.registryAccess()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Schematic named(SchematicLibrary library, String name) {
        if (name == null) {
            return null;
        }
        for (Schematic schematic : library.schematics()) {
            if (!schematic.ephemeral() && schematic.name().equalsIgnoreCase(name)) {
                return schematic;
            }
        }
        return null;
    }

    private static boolean blank(ItemStack stack) {
        return stack.getItem() instanceof BlueprintItem && !BlueprintItem.hasData(stack);
    }

    private static int findBlank(Inventory inventory) {
        for (int slot : slots(inventory)) {
            if (blank(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static List<Integer> slots(Inventory inventory) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            slots.add(slot);
        }
        slots.add(Inventory.SLOT_OFFHAND);
        return slots;
    }

    private static boolean fits(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(out));
            return out.size() <= MAX_BYTES;
        } catch (IOException ignored) {
            return false;
        }
    }
}
