package com.wrights.block;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.wrights.drone.Swarm;
import com.wrights.entity.DroneEntity;
import com.wrights.item.ModItems;
import com.wrights.job.BuildJob;
import com.wrights.menu.WrightportMenu;
import com.wrights.schematic.SchematicLibrary;

public class WrightportBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer items = new SimpleContainer(1);
    /** Client-only lid. 0 closed, 1 open. Not saved. */
    private float hatch;
    private float hatchPrev;
    private int hatchHold;
    /** Corner lamps. Red until the server has looked at the crew. */
    private int lamp = WrightportLamp.RED;
    /** Player who placed this port. Their stored wrights share one limit. */
    private UUID owner;
    private String ownerName = "";

    public WrightportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WRIGHTPORT.get(), pos, state);
        items.addListener(ignored -> setChanged());
    }

    public Swarm swarm() {
        return Swarm.from(items.getItem(0));
    }

    public void setSwarm(Swarm swarm) {
        ItemStack stack = items.getItem(0);
        if (stack.isEmpty()) {
            stack = new ItemStack(ModItems.PERSONAL_WRIGHTPORT.get());
            items.setItem(0, stack);
        }
        Swarm.write(stack, swarm);
        items.setChanged();
        refreshLamp();
        remember();
        sync();
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName == null ? "" : ownerName;
    }

    public void assignOwner(UUID owner) {
        assignOwner(owner, null);
    }

    public void assignOwner(UUID owner, String name) {
        if (owner == null) {
            return;
        }
        this.owner = owner;
        if (name != null && !name.isBlank()) {
            this.ownerName = name;
        }
        setChanged();
        remember();
        sync();
    }

    public Component ownerLabel() {
        if (owner == null) {
            return Component.translatable("block.wrights.wrightport.no_owner");
        }
        String name = ownerName();
        if (name.isBlank()) {
            name = owner.toString().substring(0, 8);
        }
        return Component.translatable("block.wrights.wrightport.owner", name);
    }

    private void remember() {
        if (level instanceof ServerLevel server) {
            com.wrights.drone.WrightLedger.get(server.getServer()).remember(owner, server.dimension(), worldPosition, swarm().units().size());
        }
    }

    public int lampColor() {
        return lamp;
    }

    public SimpleContainer items() {
        return items;
    }

    /** Opens while a wright is in the column above the port, then stays open a moment after it leaves. */
    public void animateHatch() {
        this.hatchPrev = this.hatch;
        Level level = this.level;
        boolean traffic = false;
        if (level != null) {
            BlockPos pos = this.worldPosition;
            AABB box = new AABB(pos.getX() - 6.0D, pos.getY() - 0.5D, pos.getZ() - 6.0D,
                    pos.getX() + 7.0D, pos.getY() + 7.5D, pos.getZ() + 7.0D);
            traffic = !level.getEntitiesOfClass(DroneEntity.class, box, Entity::isAlive).isEmpty();
        }
        if (traffic) {
            this.hatchHold = 16;
        } else if (this.hatchHold > 0) {
            this.hatchHold--;
        }
        float target = traffic || this.hatchHold > 0 ? 1.0F : 0.0F;
        if (this.hatch < target) {
            this.hatch = Math.min(target, this.hatch + 0.14F);
        } else if (this.hatch > target) {
            this.hatch = Math.max(target, this.hatch - 0.07F);
        }
    }

    public float hatchOpen(float partialTick) {
        return Mth.lerp(partialTick, this.hatchPrev, this.hatch);
    }

    /** Keeps the corner lamps in step with the crew and any job they are on. */
    public void tickLamp() {
        Level level = this.level;
        if (level == null || ((level.getGameTime() + this.worldPosition.asLong()) & 7L) != 0L) {
            return;
        }
        int previous = this.lamp;
        refreshLamp();
        if (previous != this.lamp) {
            sync();
        }
    }

    private void refreshLamp() {
        Level level = this.level;
        if (level == null || level.isClientSide || level.getServer() == null) {
            return;
        }
        Swarm swarm = swarm();
        Set<UUID> busy = new HashSet<>();
        if (!swarm.units().isEmpty()) {
            for (BuildJob job : SchematicLibrary.get(level.getServer()).jobs()) {
                if (job.finished() || job.kind() == BuildJob.Kind.COPY) {
                    continue;
                }
                busy.addAll(job.crewUnits());
            }
        }
        int next = WrightportLamp.color(swarm, busy::contains);
        if (next != this.lamp) {
            this.lamp = next;
            setChanged();
        }
    }

    private void sync() {
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.wrights.wrightport");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (items.getItem(0).isEmpty()) {
            setSwarm(Swarm.EMPTY);
        }
        return new WrightportMenu(id, inventory, this.getBlockPos());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Wrights", items.createTag(registries));
        tag.putInt("Lamp", lamp);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            if (!ownerName().isBlank()) {
                tag.putString("OwnerName", ownerName);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Wrights")) {
            items.fromTag(tag.getList("Wrights", Tag.TAG_COMPOUND), registries);
        }
        if (tag.contains("Lamp")) {
            lamp = tag.getInt("Lamp");
        }
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
        if (tag.contains("OwnerName")) {
            ownerName = tag.getString("OwnerName");
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        remember();
        resolveOwnerName();
    }

    private void resolveOwnerName() {
        if (owner == null || !ownerName().isBlank() || !(level instanceof ServerLevel server) || server.getServer() == null) {
            return;
        }
        var online = server.getServer().getPlayerList().getPlayer(owner);
        if (online != null) {
            assignOwner(owner, online.getGameProfile().getName());
            return;
        }
        server.getServer().getProfileCache().get(owner).ifPresent(profile -> {
            if (profile.getName() != null && !profile.getName().isBlank()) {
                assignOwner(owner, profile.getName());
            }
        });
    }

    /** Lamp updates reuse this packet. It has to carry the crew, or the client forgets who is in the port. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
