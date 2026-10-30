package com.wrights.sound;

import com.wrights.WrightsMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WrightsMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_LOOP = SOUNDS.register(
            "entity.drone.loop",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "entity.drone.loop")));

    private ModSounds() {
    }
}
