package com.wrights.client;

import com.wrights.entity.DroneEntity;
import com.wrights.sound.ModSounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Looping wright hum that rises in pitch and volume with flight speed. */
public class DroneFlightSoundInstance extends AbstractTickableSoundInstance {
    private final DroneEntity drone;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLast;
    private float smoothedSpeed;

    public DroneFlightSoundInstance(DroneEntity drone) {
        super(ModSounds.DRONE_LOOP.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 0.75F;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
        this.smoothedSpeed = 0.35F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.drone.isRemoved()) {
            this.stop();
            return;
        }

        this.x = this.drone.getX();
        this.y = this.drone.getY();
        this.z = this.drone.getZ();

        if (!this.hasLast) {
            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;
            this.hasLast = true;
            return;
        }

        double dx = this.x - this.lastX;
        double dy = this.y - this.lastY;
        double dz = this.z - this.lastZ;
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;

        // Blocks moved this tick; clamp teleport/sync spikes.
        float instant = (float) Math.min(3.0D, Math.sqrt(dx * dx + dy * dy + dz * dz));
        this.smoothedSpeed = Mth.lerp(0.28F, this.smoothedSpeed, instant);

        // ~0.23 copper close … ~2+ fast / long-haul. Map into a clear pitch range.
        float t = Mth.clamp(this.smoothedSpeed / 1.65F, 0.0F, 1.0F);
        this.pitch = 0.68F + t * 1.05F;
        this.volume = 0.16F + t * 0.58F;
    }
}
