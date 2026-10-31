package com.wrights.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsConfig;
import com.wrights.drone.WrightHomes;
import com.wrights.drone.WrightTier;
import com.wrights.job.BuildJob;
import com.wrights.schematic.SchematicLibrary;

public class DroneEntity extends Entity {
    private static final EntityDataAccessor<String> JOB_ID = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> CARRIED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);

    private boolean returning;
    private boolean stranded;
    private int returnTicks;
    private int returnLimit;
    private UUID ownerId;
    private UUID unitId;
    private String unitName = "";
    /** Null = personal wrightport (player back). Set = placed wrightport block. */
    @Nullable
    private BlockPos homePort;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setHomePort(@Nullable BlockPos homePort) {
        this.homePort = homePort == null ? null : homePort.immutable();
    }

    @Nullable
    public BlockPos homePort() {
        return homePort;
    }

    /** The dock this wright left was broken. It still flies back, then looks for somewhere to go. */
    public void strand(@Nullable UUID unit) {
        strand(unit, null);
    }

    public void strand(@Nullable UUID unit, @Nullable String name) {
        this.stranded = true;
        if (unit != null) {
            this.unitId = unit;
        }
        if (name != null && !name.isBlank()) {
            this.unitName = name;
        }
    }

    public boolean stranded() {
        return this.stranded;
    }

    public boolean returning() {
        return this.returning;
    }

    public void forgetStrand() {
        this.stranded = false;
    }

    /** The port wright this flyer stands for. Set while it is out, so a return cannot be double-booked. */
    public void assignUnit(@Nullable UUID unit) {
        if (unit != null) {
            this.unitId = unit;
        }
    }

    @Nullable
    public UUID unitId() {
        return this.unitId;
    }

    /** Saves a wright whose dock is already gone, when it will not get to fly home. */
    public void recoverIfStranded() {
        if (!this.stranded || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        this.stranded = false;
        ServerPlayer player = this.ownerId == null ? null : server.getServer().getPlayerList().getPlayer(this.ownerId);
        BlockPos where = this.homePort != null ? this.homePort : this.blockPosition();
        BlockPos docked = com.wrights.drone.DockRecovery.recover(server, player, where, this.unitId, this.tier(), this.unitName);
        if (this.unitId == null) {
            return;
        }
        if (docked != null) {
            com.wrights.job.JobManager.noteDocked(server.getServer(), this.unitId, docked);
        } else {
            com.wrights.job.JobManager.noteReleased(server.getServer(), this.unitId);
        }
    }

    /** Leaves the personal port behind the player, aimed at the work site. */
    public void departFromPlayer(ServerPlayer player, Vec3 work, int lane, int lanes) {
        setHomePort(null);
        Vec3 from = WrightHomes.behindPlayer(player);
        launchToward(from, work, lane, lanes, player.getYRot());
    }

    /** Leaves a placed wrightport hatch, aimed at the work site. */
    public void departFromBlock(BlockPos port, Vec3 work, int lane, int lanes) {
        setHomePort(port);
        Vec3 from = WrightHomes.lip(port);
        launchToward(from, work, lane, lanes, 0.0F);
    }

    private void launchToward(Vec3 from, Vec3 work, int lane, int lanes, float fallbackYaw) {
        Vec3 toward = work.subtract(from);
        if (toward.lengthSqr() < 1.0E-4D) {
            toward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        toward = toward.normalize();
        Vec3 side = new Vec3(-toward.z, 0.0D, toward.x);
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        double spread = lanes <= 1 ? 0.0D : (lane - (lanes - 1) * 0.5D) * 0.45D;
        Vec3 start = from.add(toward.scale(0.35D)).add(side.scale(spread));
        float yaw = (float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0F;
        if (!Float.isFinite(yaw)) {
            yaw = fallbackYaw;
        }
        this.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        this.setYRot(yaw);
    }

    /** Returns to the personal port (behind the player) or a placed wrightport hatch. */
    public void flyHome(ServerPlayer player, int slot, int count, @Nullable BlockPos port) {
        this.returning = true;
        this.returnTicks = 0;
        this.ownerId = player.getUUID();
        this.setJobId(null);
        setHomePort(port);
        if (!WrightsConfig.flyHome()) {
            int n = Math.max(1, count);
            double angle = (Math.PI * 2.0D * slot) / n;
            double radius = n <= 1 ? 0.0D : 0.55D;
            Vec3 above = port != null
                    ? WrightHomes.approach(port, slot, count)
                    : WrightHomes.behindPlayer(player).add(Math.cos(angle) * radius, 4.0D, Math.sin(angle) * radius);
            this.moveTo(above.x, above.y, above.z, player.getYRot(), 0.0F);
            this.setYRot(player.getYRot());
        }
        Vec3 dock = port != null ? WrightHomes.hatch(port) : WrightHomes.behindPlayer(player);
        this.returnLimit = returnBudget(dock);
        if (this.unitId != null && !this.level().isClientSide) {
            com.wrights.job.JobManager.holdReturn(this.unitId);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.unitId != null && this.level() instanceof ServerLevel) {
            com.wrights.job.JobManager.releaseReturn(this.unitId);
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(JOB_ID, "");
        builder.define(TIER, com.wrights.drone.WrightTier.BASE);
        builder.define(CARRIED, ItemStack.EMPTY);
    }

    /** The drop this wright is bringing home. Empty while it is still on the way out. */
    public ItemStack carried() {
        return this.entityData.get(CARRIED);
    }

    public void setCarried(ItemStack stack) {
        this.entityData.set(CARRIED, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
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

    private int returnBudget(Vec3 dock) {
        double dist = this.position().distanceTo(dock);
        int ticks = 160 + (int) Math.ceil(dist / 0.15D);
        return Math.min(ticks, 12000);
    }

    /** Cruise like a working wright, then slow for the last few blocks into the hatch. */
    private double returnStep(Vec3 dock, boolean blockHome) {
        double distSqr = this.distanceToSqr(dock);
        if (blockHome && distSqr < 49.0D) {
            return 0.3D;
        }
        if (!blockHome && distSqr < 25.0D) {
            return 0.5D;
        }
        double step = WrightTier.flyClose(this.tier());
        if (distSqr > 64.0D) {
            step *= 2.1D;
        }
        return step;
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
            if (this.unitId != null) {
                com.wrights.job.JobManager.holdReturn(this.unitId);
            }
            ServerPlayer player = this.ownerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(this.ownerId);
            boolean loadedHome = homePort != null && serverLevel.isLoaded(homePort);
            if (!this.stranded && (player == null || player.level() != this.level())) {
                this.discard();
                return;
            }
            boolean blockHome = homePort != null && (this.stranded || loadedHome);
            Vec3 dock;
            if (blockHome) {
                dock = WrightHomes.hatch(homePort);
            } else if (player != null && player.level() == this.level()) {
                dock = WrightHomes.behindPlayer(player);
            } else {
                finishReturn(serverLevel, player, this.blockPosition());
                return;
            }
            if (this.returnLimit <= 0) {
                this.returnLimit = returnBudget(dock);
            }
            boolean timedOut = ++this.returnTicks > this.returnLimit;
            boolean arrived = this.distanceToSqr(dock) <= (blockHome ? 0.04D : 1.15D);
            if (timedOut || arrived) {
                BlockPos arrivedAt = this.stranded && this.homePort != null
                        ? this.homePort
                        : (loadedHome ? homePort : this.blockPosition());
                finishReturn(serverLevel, player, arrivedAt);
                return;
            }
            this.moveToward(dock, returnStep(dock, blockHome));
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
        if (tag.contains("HomePort")) {
            this.homePort = NbtUtils.readBlockPos(tag, "HomePort").orElse(null);
        }
        this.stranded = tag.getBoolean("Stranded");
        if (tag.hasUUID("UnitId")) {
            this.unitId = tag.getUUID("UnitId");
        }
        if (this.returning && this.unitId != null && this.level() instanceof ServerLevel) {
            com.wrights.job.JobManager.holdReturn(this.unitId);
        }
        this.unitName = tag.getString("UnitName");
        if (tag.contains("Carried")) {
            setCarried(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("Carried")));
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
        if (this.homePort != null) {
            tag.put("HomePort", NbtUtils.writeBlockPos(this.homePort));
        }
        if (this.stranded) {
            tag.putBoolean("Stranded", true);
        }
        if (this.unitId != null) {
            tag.putUUID("UnitId", this.unitId);
        }
        if (this.unitName != null && !this.unitName.isBlank()) {
            tag.putString("UnitName", this.unitName);
        }
        if (!carried().isEmpty()) {
            tag.put("Carried", carried().save(this.registryAccess()));
        }
    }

    private void finishReturn(ServerLevel level, ServerPlayer player, BlockPos where) {
        if (this.stranded) {
            this.stranded = false;
            BlockPos docked = com.wrights.drone.DockRecovery.recover(level, player, where, this.unitId, this.tier(), this.unitName);
            if (this.unitId != null) {
                if (docked != null) {
                    com.wrights.job.JobManager.noteDocked(level.getServer(), this.unitId, docked);
                } else {
                    com.wrights.job.JobManager.noteReleased(level.getServer(), this.unitId);
                }
            }
        }
        this.discard();
    }
}
