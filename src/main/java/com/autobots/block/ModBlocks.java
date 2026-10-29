package com.autobots.block;

import com.autobots.AutobotsMod;
import com.autobots.drone.Swarm;
import com.autobots.item.ModDataComponents;
import com.autobots.item.ModItems;
import com.autobots.item.WrightportBlockItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AutobotsMod.MOD_ID);

    public static final DeferredBlock<HangarBlock> HANGAR = BLOCKS.register("hangar", () -> new HangarBlock(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> HANGAR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(HANGAR);

    public static final DeferredBlock<WrightportBlock> WRIGHTPORT = BLOCKS.register("wrightport", () -> new WrightportBlock(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> WRIGHTPORT_ITEM = ModItems.ITEMS.register("wrightport",
            () -> new WrightportBlockItem(WRIGHTPORT.get(), new Item.Properties().stacksTo(1).component(ModDataComponents.SWARM.get(), Swarm.EMPTY)));

    private ModBlocks() {
    }
}
