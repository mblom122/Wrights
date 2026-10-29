package com.autobots.entity;

import com.autobots.AutobotsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AutobotsMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE = ENTITIES.register("drone", () -> EntityType.Builder
            .<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
            .sized(0.6F, 0.3F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .fireImmune()
            .build("drone"));

    private ModEntities() {
    }
}
