package com.autobots.item;

import com.autobots.AutobotsMod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AutobotsMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ControllerData>> CONTROLLER =
            DATA_COMPONENTS.registerComponentType("controller", builder -> builder
                    .persistent(ControllerData.CODEC)
                    .networkSynchronized(ControllerData.STREAM_CODEC));

    private ModDataComponents() {
    }
}
