package com.wrights.drone;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.wrights.item.PersonalWrightportItem;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Every personal wrightport the player is carrying, in the order the Manage
 * screen offers them: curio, main hand, off hand, hotbar, then the bag from
 * the top left to the bottom right.
 */
public final class WrightportPlaces {
    public static final int CURIO = 0;
    public static final int MAIN = 1;
    public static final int OFF = 2;
    public static final int HOTBAR = 3;
    public static final int BAG = 4;

    public record Spot(int kind, int index) {
    }

    private WrightportPlaces() {
    }

    public static List<Spot> list(Player player) {
        List<Spot> spots = new ArrayList<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler != null) {
            handler.getStacksHandler(Wrightports.SLOT).ifPresent(stacks -> {
                IDynamicStackHandler inv = stacks.getStacks();
                for (int i = 0; i < inv.getSlots(); i++) {
                    if (inv.getStackInSlot(i).getItem() instanceof PersonalWrightportItem) {
                        spots.add(new Spot(CURIO, i));
                    }
                }
            });
        }
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        if (inventory.getItem(selected).getItem() instanceof PersonalWrightportItem) {
            spots.add(new Spot(MAIN, selected));
        }
        if (player.getOffhandItem().getItem() instanceof PersonalWrightportItem) {
            spots.add(new Spot(OFF, 0));
        }
        for (int i = 0; i < 9; i++) {
            if (i == selected && inventory.getItem(selected).getItem() instanceof PersonalWrightportItem) {
                continue;
            }
            if (inventory.getItem(i).getItem() instanceof PersonalWrightportItem) {
                spots.add(new Spot(HOTBAR, i));
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inventory.getItem(i).getItem() instanceof PersonalWrightportItem) {
                spots.add(new Spot(BAG, i));
            }
        }
        return spots;
    }

    public static ItemStack stack(Player player, int kind, int index) {
        return switch (kind) {
            case CURIO -> curio(player, index);
            case MAIN, HOTBAR, BAG -> {
                if (index < 0 || index >= 36) {
                    yield ItemStack.EMPTY;
                }
                yield player.getInventory().getItem(index);
            }
            case OFF -> player.getOffhandItem();
            default -> ItemStack.EMPTY;
        };
    }

    public static boolean write(Player player, int kind, int index, Swarm swarm) {
        ItemStack stack = stack(player, kind, index);
        if (!(stack.getItem() instanceof PersonalWrightportItem)) {
            return false;
        }
        Swarm.write(stack, swarm);
        return switch (kind) {
            case CURIO -> {
                ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
                if (handler == null) {
                    yield false;
                }
                handler.setEquippedCurio(Wrightports.SLOT, index, stack);
                yield true;
            }
            case OFF -> {
                player.setItemSlot(EquipmentSlot.OFFHAND, stack);
                yield true;
            }
            case MAIN, HOTBAR, BAG -> {
                player.getInventory().setItem(index, stack);
                yield true;
            }
            default -> false;
        };
    }

    public static Component where(Spot spot) {
        return switch (spot.kind()) {
            case CURIO -> spot.index() <= 0
                    ? Component.translatable("menu.wrights.port_curio")
                    : Component.translatable("menu.wrights.port_curio_n", spot.index() + 1);
            case MAIN -> Component.translatable("menu.wrights.port_main");
            case OFF -> Component.translatable("menu.wrights.port_off");
            case HOTBAR -> Component.translatable("menu.wrights.port_hotbar", spot.index() + 1);
            case BAG -> Component.translatable("menu.wrights.port_bag", spot.index() - 8);
            default -> Component.empty();
        };
    }

    private static ItemStack curio(Player player, int index) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        return handler.getStacksHandler(Wrightports.SLOT).map(stacks -> {
            IDynamicStackHandler inv = stacks.getStacks();
            if (index < 0 || index >= inv.getSlots()) {
                return ItemStack.EMPTY;
            }
            return inv.getStackInSlot(index);
        }).orElse(ItemStack.EMPTY);
    }
}
