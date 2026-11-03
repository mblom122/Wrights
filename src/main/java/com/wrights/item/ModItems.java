package com.wrights.item;

import java.util.ArrayList;
import java.util.List;

import com.wrights.WrightsMod;
import com.wrights.drone.PartSlot;
import com.wrights.drone.WrightTier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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

    /** Rotor Assembly Scout (mk1) through Apex (mk5). */
    public static final List<DeferredItem<WrightPartItem>> ROTORS = parts(PartSlot.ROTOR);

    /** Fabricator Head Scout (mk1) through Apex (mk5). */
    public static final List<DeferredItem<WrightPartItem>> FABRICATORS = parts(PartSlot.FABRICATOR);

    /** Metal names the parts were registered under before they were renamed; kept as aliases so saved items load. */
    private static final String[] RETIRED_TIERS = { "copper", "iron", "gold", "diamond", "netherite" };

    private static List<DeferredItem<WrightPartItem>> parts(PartSlot slot) {
        List<DeferredItem<WrightPartItem>> parts = new ArrayList<>();
        for (int tier = WrightTier.SCOUT; tier <= WrightTier.APEX; tier++) {
            int mark = tier;
            String id = slot.getSerializedName() + "_mk" + mark;
            parts.add(ITEMS.register(id, () -> new WrightPartItem(new Item.Properties().stacksTo(64)
                    .rarity(mark == WrightTier.APEX ? Rarity.UNCOMMON : Rarity.COMMON), slot, mark)));
            String retired = (slot == PartSlot.ROTOR ? "wright_upgrade_" : "wright_ops_") + RETIRED_TIERS[mark - 1];
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, retired),
                    ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, id));
        }
        return List.copyOf(parts);
    }

    private ModItems() {
    }
}
