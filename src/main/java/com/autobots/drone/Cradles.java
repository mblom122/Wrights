package com.autobots.drone;

import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.autobots.item.CradleItem;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

public final class Cradles {
    public static final String SLOT = "drone_cradle";

    private Cradles() {
    }

    public static ItemStack worn(Player player) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        return handler.findFirstCurio(stack -> stack.getItem() instanceof CradleItem)
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static Swarm swarm(Player player) {
        return Swarm.from(worn(player));
    }

    public static boolean replaceWorn(Player player, Swarm swarm) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return false;
        }
        Optional<SlotResult> found = handler.findFirstCurio(stack -> stack.getItem() instanceof CradleItem);
        if (found.isEmpty()) {
            return false;
        }
        SlotResult result = found.get();
        ItemStack stack = result.stack();
        Swarm.write(stack, swarm);
        handler.setEquippedCurio(result.slotContext().identifier(), result.slotContext().index(), stack);
        return true;
    }
}
