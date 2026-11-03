package com.wrights.block;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import com.wrights.drone.Swarm;
import com.wrights.energy.PortEnergy;
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
    /** Last result of the wright scan above the port. Client-only. */
    private boolean traffic;
    /** Corner lamps. Red until the server has looked at the crew. */
    private int lamp = WrightportLamp.RED;
    private final PortEnergy.Buffer energy = new PortEnergy.Buffer();
    /** Player who placed this port. Their stored wrights share one limit. */
    private UUID owner;
    private String ownerName = "";
    /** Short name chosen for this port. Blank keeps the plain Wrightport label. */
    private String label = "";
    /** Container this port's wrights may pull from. It has to stay inside the port boundary. */
    private BlockPos supplyChest;

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

    public String label() {
        return label == null ? "" : label;
    }

    public BlockPos supplyChest() {
        return supplyChest;
    }

    public void setSupplyChest(BlockPos pos) {
        this.supplyChest = pos == null ? null : pos.immutable();
        setChanged();
        sync();
    }

    public void setLabel(String name) {
        String next = cleanLabel(name);
        if (next.equals(label())) {
            return;
        }
        this.label = next;
        setChanged();
        remember();
        sync();
    }

    public Component title() {
        return titled(label());
    }

    public static Component titled(String name) {
        if (name == null || name.isBlank()) {
            return Component.translatable("block.wrights.wrightport");
        }
        return Component.translatable("block.wrights.wrightport.named", name);
    }

    public static String cleanLabel(String raw) {
        return com.wrights.drone.Swarm.cleanName(raw);
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
            com.wrights.drone.WrightLedger.get(server.getServer()).remember(owner, server.dimension(), worldPosition, swarm().units().size(), label());
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
        // The column scan is the costly part, so look every few ticks. The hold below covers the gap.
        if (level == null) {
            this.traffic = false;
        } else if (((level.getGameTime() + this.worldPosition.asLong()) & 3L) == 0L) {
            BlockPos pos = this.worldPosition;
            AABB box = new AABB(pos.getX() - 6.0D, pos.getY() - 0.5D, pos.getZ() - 6.0D,
                    pos.getX() + 7.0D, pos.getY() + 57.0D, pos.getZ() + 7.0D);
            this.traffic = !level.getEntitiesOfClass(DroneEntity.class, box, Entity::isAlive).isEmpty();
        }
        boolean traffic = this.traffic;
        if (traffic) {
            this.hatchHold = 16;
        } else if (this.hatchHold > 0) {
            this.hatchHold--;
        }
        float target = traffic || this.hatchHold > 0 ? 1.0F : 0.0F;
        // Slow enough that the staggered blades read as a spiral.
        if (this.hatch < target) {
            this.hatch = Math.min(target, this.hatch + 0.09F);
        } else if (this.hatch > target) {
            this.hatch = Math.max(target, this.hatch - 0.06F);
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
        restoreTop(level);
        int previous = this.lamp;
        refreshLamp();
        if (previous != this.lamp) {
            sync();
        }
    }

    /** Ports placed before the tower was two blocks tall have no top half. Add it once the space is free. */
    private void restoreTop(Level level) {
        BlockState state = getBlockState();
        if (level.isClientSide || !state.hasProperty(WrightportBlock.HALF) || WrightportBlock.isUpper(state)) {
            return;
        }
        BlockPos above = this.worldPosition.above();
        if (level.isInWorldBounds(above) && level.getBlockState(above).isAir()) {
            level.setBlock(above, state.setValue(WrightportBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
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
        boolean starved = energy.getEnergyStored() <= 0 && !swarm.units().isEmpty();
        int next = WrightportLamp.color(swarm, busy::contains, starved);
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
        return title();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (items.getItem(0).isEmpty()) {
            setSwarm(Swarm.EMPTY);
        }
        return new WrightportMenu(id, inventory, this.getBlockPos());
    }

    public PortEnergy.Buffer energyStorage() {
        return energy;
    }

    public int energyStored() {
        return energy.stored();
    }

    public int energyCapacity() {
        return energy.limit();
    }

    public boolean consumeEnergy(int cost) {
        boolean wasEmpty = energy.getEnergyStored() <= 0;
        if (!energy.consume(cost)) {
            return false;
        }
        setChanged();
        if (!wasEmpty && energy.getEnergyStored() <= 0) {
            refreshLamp();
            sync();
        }
        return true;
    }

    public void refundEnergy(int amount) {
        boolean wasEmpty = energy.getEnergyStored() <= 0;
        energy.refund(amount);
        setChanged();
        if (wasEmpty && energy.getEnergyStored() > 0) {
            refreshLamp();
            sync();
        }
    }

    /** Copy the buffer into a dropped / picked wrightport item. */
    public void saveEnergyToItem(ItemStack stack) {
        PortEnergy.writeItem(stack, energy.stored());
    }

    /** Restore the buffer from a placed wrightport item (empty craft / JEI = 0). */
    public void loadEnergyFromItem(ItemStack stack) {
        energy.loadAmount(PortEnergy.itemStored(stack));
        setChanged();
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
        if (!label().isBlank()) {
            tag.putString("Label", label());
        }
        if (supplyChest != null) {
            tag.put("SupplyChest", NbtUtils.writeBlockPos(supplyChest));
        }
        energy.save(tag);
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
        label = tag.contains("Label") ? cleanLabel(tag.getString("Label")) : "";
        supplyChest = tag.contains("SupplyChest") ? NbtUtils.readBlockPos(tag, "SupplyChest").orElse(null) : null;
        energy.load(tag);
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
