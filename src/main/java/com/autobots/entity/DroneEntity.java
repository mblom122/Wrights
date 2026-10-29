package com.autobots.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.autobots.job.BuildJob;
import com.autobots.schematic.SchematicLibrary;

public class DroneEntity extends Entity {
    private static final EntityDataAccessor<String> JOB_ID = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(JOB_ID, "");
    }

    public Optional<UUID> jobId() {
        String raw = this.entityData.get(JOB_ID);
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public void setJobId(UUID id) {
        this.entityData.set(JOB_ID, id == null ? "" : id.toString());
    }

    public void moveToward(Vec3 target, double step) {
        Vec3 current = this.position();
        Vec3 delta = target.subtract(current);
        double distance = delta.length();
        if (distance > 1.0E-4) {
            Vec3 stepDelta = distance > step ? delta.scale(step / distance) : delta;
            current = current.add(stepDelta);
        }
        double dx = target.x - current.x;
        double dz = target.z - current.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.moveTo(current.x, current.y, current.z, yaw, 0.0F);
        this.setYRot(yaw);
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Optional<UUID> id = jobId();
        SchematicLibrary library = SchematicLibrary.get(serverLevel.getServer());
        BuildJob job = id.flatMap(library::job).orElse(null);
        if (job == null || job.droneId() == null || !job.droneId().equals(this.getUUID())) {
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("JobId")) {
            setJobId(tag.getUUID("JobId"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        jobId().ifPresent(id -> tag.putUUID("JobId", id));
    }
}
