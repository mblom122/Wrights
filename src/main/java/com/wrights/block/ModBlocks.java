package com.wrights.block;

import com.wrights.WrightsMod;
import com.wrights.drone.Swarm;
import com.wrights.item.ModDataComponents;
import com.wrights.item.ModItems;
import com.wrights.item.WrightportBlockItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WrightsMod.MOD_ID);

    public static final DeferredBlock<HangarBlock> HANGAR = BLOCKS.register("hangar", () -> new HangarBlock(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> HANGAR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(HANGAR);

    public static final DeferredBlock<WrightportBlock> WRIGHTPORT = BLOCKS.register("wrightport", () -> new WrightportBlock(BlockBehaviour.Properties.of()));
    public static final DeferredItem<BlockItem> WRIGHTPORT_ITEM = ModItems.ITEMS.register("wrightport",
            () -> new WrightportBlockItem(WRIGHTPORT.get(), new Item.Properties().stacksTo(64).component(ModDataComponents.SWARM.get(), Swarm.EMPTY)));

    private ModBlocks() {
    }
}
