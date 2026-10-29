package com.wrights.item;

import com.wrights.WrightsMod;

import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WrightsMod.MOD_ID);

    public static final DeferredItem<ControllerItem> CONTROLLER = ITEMS.register("controller", () -> new ControllerItem(new net.minecraft.world.item.Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.CONTROLLER.get(), ControllerData.EMPTY)));

    public static final DeferredItem<PersonalWrightportItem> PERSONAL_WRIGHTPORT = ITEMS.register("personal_wrightport", () -> new PersonalWrightportItem(new net.minecraft.world.item.Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.SWARM.get(), com.wrights.drone.Swarm.EMPTY)));

    private ModItems() {
    }
}
