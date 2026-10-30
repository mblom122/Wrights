package com.wrights.item;

import com.mojang.serialization.Codec;
import com.wrights.WrightsMod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WrightsMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ControllerData>> CONTROLLER =
            DATA_COMPONENTS.registerComponentType("controller", builder -> builder
                    .persistent(ControllerData.CODEC)
                    .networkSynchronized(ControllerData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.wrights.drone.Swarm>> SWARM =
            DATA_COMPONENTS.registerComponentType("swarm", builder -> builder
                    .persistent(com.wrights.drone.Swarm.CODEC)
                    .networkSynchronized(com.wrights.drone.Swarm.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> BLUEPRINT =
            DATA_COMPONENTS.registerComponentType("blueprint", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.TRUSTED_COMPOUND_TAG));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FOLDER =
            DATA_COMPONENTS.registerComponentType("blueprint_folder", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.TRUSTED_COMPOUND_TAG));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PORT_NAME =
            DATA_COMPONENTS.registerComponentType("port_name", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.stringUtf8(32)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WRIGHT_TIER =
            DATA_COMPONENTS.registerComponentType("wright_tier", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {
    }
}
