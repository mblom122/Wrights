package com.wrights.util;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Keeps machine configuration out of blueprint NBT while stripping anything that
 * could duplicate items, fluids, energy, or upgrades (Mekanism-configurator style).
 */
public final class BlockEntityNbt {
    private static final Set<String> DROP_EXACT = Set.of(
            "id", "x", "y", "z", "UUID", "uuid", "Owner", "owner", "ownerUUID", "OwnerUUID",
            "Items", "items", "Inventory", "inventory", "Item", "item", "Contents", "contents",
            "Storage", "storage", "Slots", "slots", "Slot", "slot", "Stacks", "stacks",
            "Fuel", "fuel", "BurnTime", "burnTime", "CookTime", "cookTime",
            "Energy", "energy", "EnergyContainer", "energyContainer", "EnergyContainers", "energyContainers",
            "Mana", "mana", "FE", "fe", "Power", "power",
            "Fluid", "fluid", "Fluids", "fluids", "Tank", "tank", "Tanks", "tanks",
            "FluidTank", "fluidTank", "FluidTanks", "fluidTanks", "TankContents", "tankContents",
            "Gas", "gas", "Gases", "gases", "GasTank", "gasTank", "GasTanks", "gasTanks",
            "Chemical", "chemical", "Chemicals", "chemicals", "ChemicalTank", "chemicalTank",
            "Infusion", "infusion", "Pigment", "pigment", "Slurry", "slurry",
            "Upgrades", "upgrades", "Upgrade", "upgrade", "UpgradeInventory", "upgradeInventory",
            "Modules", "modules", "Module", "module",
            "LootTable", "lootTable", "LootTableSeed", "lootTableSeed",
            "RecipesUsed", "recipesUsed", "Recipe", "recipe",
            "ForgeData", "forgeData", "ForgeCaps", "forgeCaps",
            "neoforge:attachments", "neoForgeData", "NeoForgeData");

    private BlockEntityNbt() {
    }

    /** Settings-only copy: drop inventories, tanks, energy, upgrades, and identity. */
    @Nullable
    public static CompoundTag settings(@Nullable CompoundTag raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        CompoundTag clean = raw.copy();
        strip(clean);
        return clean.isEmpty() ? null : clean;
    }

    private static void strip(CompoundTag tag) {
        Iterator<String> keys = Set.copyOf(tag.getAllKeys()).iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            if (shouldDrop(key)) {
                tag.remove(key);
                continue;
            }
            Tag value = tag.get(key);
            if (value instanceof CompoundTag child) {
                strip(child);
                if (child.isEmpty()) {
                    tag.remove(key);
                }
            } else if (value instanceof ListTag list) {
                if (list.isEmpty()) {
                    continue;
                }
                byte type = list.getElementType();
                if (type == Tag.TAG_COMPOUND) {
                    for (int i = list.size() - 1; i >= 0; i--) {
                        CompoundTag child = list.getCompound(i);
                        if (looksLikeStack(child)) {
                            list.remove(i);
                            continue;
                        }
                        strip(child);
                        if (child.isEmpty()) {
                            list.remove(i);
                        }
                    }
                    if (list.isEmpty()) {
                        tag.remove(key);
                    }
                } else if (looksLikeItemListKey(key)) {
                    tag.remove(key);
                }
            }
        }
    }

    private static boolean shouldDrop(String key) {
        if (DROP_EXACT.contains(key)) {
            return true;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return looksLikeItemListKey(key)
                || lower.contains("inventory")
                || lower.contains("itemstack")
                || lower.contains("itemhandler")
                || lower.endsWith("items")
                || lower.contains("upgrade")
                || lower.contains("module")
                || lower.contains("energystorage")
                || lower.contains("energycontainer")
                || lower.contains("fluidtank")
                || lower.contains("gastank")
                || lower.contains("chemicaltank")
                || lower.contains("tankcontent")
                || lower.equals("buffer")
                || lower.contains("storedenergy")
                || lower.contains("storedfluid")
                || lower.contains("storedgas")
                || lower.contains("storedchemical")
                || lower.contains("loot");
    }

    private static boolean looksLikeItemListKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.equals("items")
                || lower.equals("item")
                || lower.endsWith("_items")
                || lower.endsWith("items")
                || lower.contains("itemlist");
    }

    private static boolean looksLikeStack(CompoundTag tag) {
        return tag.contains("id") && (tag.contains("count") || tag.contains("Count") || tag.contains("components") || tag.contains("tag"));
    }
}
