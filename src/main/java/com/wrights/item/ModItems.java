package com.wrights.item;

import com.wrights.WrightsMod;
import com.wrights.drone.WrightTier;

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

    public static final DeferredItem<WrightItem> WRIGHT = ITEMS.register("wright", () -> new WrightItem(new net.minecraft.world.item.Item.Properties().stacksTo(64)));

    public static final DeferredItem<WrightUpgradeItem> UPGRADE_IRON = ITEMS.register("wright_upgrade_iron",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.IRON));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_GOLD = ITEMS.register("wright_upgrade_gold",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.GOLD));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_DIAMOND = ITEMS.register("wright_upgrade_diamond",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.DIAMOND));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_NETHERITE = ITEMS.register("wright_upgrade_netherite",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), WrightTier.NETHERITE));

    private ModItems() {
    }
}
