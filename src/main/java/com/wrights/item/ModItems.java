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
            .component(ModDataComponents.SWARM.get(), com.wrights.drone.Swarm.EMPTY)
            .component(ModDataComponents.PORT_ENERGY.get(), 0)));

    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.register("blueprint", () -> new BlueprintItem(new net.minecraft.world.item.Item.Properties().stacksTo(16)));

    public static final DeferredItem<BlueprintFolderItem> BLUEPRINT_FOLDER = ITEMS.register("blueprint_folder", () -> new BlueprintFolderItem(new net.minecraft.world.item.Item.Properties().stacksTo(16)));

    public static final DeferredItem<WrightItem> WRIGHT = ITEMS.register("wright", () -> new WrightItem(new net.minecraft.world.item.Item.Properties().stacksTo(64)));

    public static final DeferredItem<WrightUpgradeItem> UPGRADE_COPPER = ITEMS.register("wright_upgrade_copper",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.COPPER));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_IRON = ITEMS.register("wright_upgrade_iron",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.IRON));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_GOLD = ITEMS.register("wright_upgrade_gold",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.GOLD));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_DIAMOND = ITEMS.register("wright_upgrade_diamond",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.DIAMOND));
    public static final DeferredItem<WrightUpgradeItem> UPGRADE_NETHERITE = ITEMS.register("wright_upgrade_netherite",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), WrightTier.NETHERITE));

    public static final DeferredItem<WrightUpgradeItem> OPS_COPPER = ITEMS.register("wright_ops_copper",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.COPPER, true));
    public static final DeferredItem<WrightUpgradeItem> OPS_IRON = ITEMS.register("wright_ops_iron",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.IRON, true));
    public static final DeferredItem<WrightUpgradeItem> OPS_GOLD = ITEMS.register("wright_ops_gold",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.GOLD, true));
    public static final DeferredItem<WrightUpgradeItem> OPS_DIAMOND = ITEMS.register("wright_ops_diamond",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64), WrightTier.DIAMOND, true));
    public static final DeferredItem<WrightUpgradeItem> OPS_NETHERITE = ITEMS.register("wright_ops_netherite",
            () -> new WrightUpgradeItem(new net.minecraft.world.item.Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), WrightTier.NETHERITE, true));

    private ModItems() {
    }
}
