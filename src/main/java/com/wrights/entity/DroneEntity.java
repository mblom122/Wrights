package com.wrights.entity;

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

import com.wrights.job.BuildJob;
import com.wrights.schematic.SchematicLibrary;

public class DroneEntity extends Entity {
    private static final EntityDataAccessor<String> JOB_ID = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);

    private boolean returning;
    private int returnTicks;
    private UUID ownerId;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Leaves the player's eyes, aimed at the block this wright is going to work on. */
    public void departFrom(net.minecraft.server.level.ServerPlayer player, Vec3 work, int lane, int lanes) {
        Vec3 from = player.getEyePosition();
        Vec3 toward = work.subtract(from);
        if (toward.lengthSqr() < 1.0E-4D) {
            toward = player.getLookAngle();
        }
        toward = toward.normalize();
        Vec3 side = new Vec3(-toward.z, 0.0D, toward.x);
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        double spread = lanes <= 1 ? 0.0D : (lane - (lanes - 1) * 0.5D) * 0.45D;
        Vec3 start = from.add(toward.scale(0.75D)).add(side.scale(spread));
        float yaw = (float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0F;
        this.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        this.setYRot(yaw);
    }

    /** Appears above the player, then flies down into them. */
    public void flyHome(net.minecraft.server.level.ServerPlayer player, int slot, int count) {
        this.returning = true;
        this.returnTicks = 0;
        this.ownerId = player.getUUID();
        this.setJobId(null);
        int n = Math.max(1, count);
        double angle = (Math.PI * 2.0D * slot) / n;
        double radius = n <= 1 ? 0.0D : 0.55D;
        Vec3 above = player.position().add(Math.cos(angle) * radius, 5.0D, Math.sin(angle) * radius);
        this.moveTo(above.x, above.y, above.z, player.getYRot(), 0.0F);
        this.setYRot(player.getYRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(JOB_ID, "");
        builder.define(TIER, 1);
    }

    public int tier() {
        return com.wrights.drone.WrightTier.clamp(this.entityData.get(TIER));
    }

    public void setTier(int tier) {
        this.entityData.set(TIER, com.wrights.drone.WrightTier.clamp(tier));
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
        if (this.returning) {
            net.minecraft.server.level.ServerPlayer player = this.ownerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(this.ownerId);
            if (player == null || player.level() != this.level() || ++this.returnTicks > 200) {
                this.discard();
                return;
            }
            Vec3 dock = player.position().add(0.0D, 0.9D, 0.0D);
            if (this.distanceToSqr(dock) <= 1.0D) {
                this.discard();
                return;
            }
            this.moveToward(dock, 0.5D);
            return;
        }
        Optional<UUID> id = jobId();
        SchematicLibrary library = SchematicLibrary.get(serverLevel.getServer());
        BuildJob job = id.flatMap(library::job).orElse(null);
        if (job == null || !job.ownsDrone(this.getUUID())) {
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
        if (tag.contains("Tier")) {
            setTier(tag.getInt("Tier"));
        }
        this.returning = tag.getBoolean("Returning");
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        jobId().ifPresent(id -> tag.putUUID("JobId", id));
        tag.putInt("Tier", tier());
        tag.putBoolean("Returning", this.returning);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
    }
}
