package com.autobots.block;

import com.autobots.AutobotsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AutobotsMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HangarBlockEntity>> HANGAR = TYPES.register("hangar",
            () -> BlockEntityType.Builder.of(HangarBlockEntity::new, ModBlocks.HANGAR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WrightportBlockEntity>> WRIGHTPORT = TYPES.register("wrightport",
            () -> BlockEntityType.Builder.of(WrightportBlockEntity::new, ModBlocks.WRIGHTPORT.get()).build(null));

    private ModBlockEntities() {
    }
}
