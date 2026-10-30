package com.wrights.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.drone.Swarm;
import com.wrights.item.PersonalWrightportItem;
import com.wrights.menu.HangarMenu;

import javax.annotation.Nullable;

public class HangarBlockEntity extends BlockEntity implements MenuProvider {
    private static final float DOOR_SPEED = 0.08F;

    private final SimpleContainer items = new SimpleContainer(1);
    private boolean doorOpen;
    private int doorHoldTicks;
    private float doorProgress;
    private float doorProgressO;
    private HangarSignal signal = HangarSignal.RED;

    public HangarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HANGAR.get(), pos, state);
        items.addListener(ignored -> setChanged());
    }

    public Container items() {
        return items;
    }

    public float doorProgress(float partialTick) {
        return Mth.lerp(partialTick, doorProgressO, doorProgress);
    }

    public HangarSignal signal() {
        return signal;
    }

    public boolean doorOpen() {
        return doorOpen;
    }

    public void requestDoorOpen(int holdTicks) {
        boolean was = doorOpen;
        doorOpen = true;
        doorHoldTicks = Math.max(doorHoldTicks, holdTicks);
        if (!was) {
            sync();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HangarBlockEntity hangar) {
        hangar.doorProgressO = hangar.doorProgress;
        if (!level.isClientSide) {
            boolean traffic = Hangars.hasDroneTraffic(level, pos, 5.5D);
            boolean wasOpen = hangar.doorOpen;
            HangarSignal wasSignal = hangar.signal;
            if (traffic) {
                hangar.doorOpen = true;
                hangar.doorHoldTicks = Math.max(hangar.doorHoldTicks, 12);
            } else if (hangar.doorHoldTicks > 0) {
                hangar.doorHoldTicks--;
                hangar.doorOpen = true;
            } else {
                hangar.doorOpen = false;
            }
            hangar.signal = hangar.computeSignal(traffic);
            if (wasOpen != hangar.doorOpen || wasSignal != hangar.signal || level.getGameTime() % 10L == 0L) {
                hangar.sync();
            }
        }

        float target = hangar.doorOpen ? 1.0F : 0.0F;
        if (Math.abs(hangar.doorProgress - target) > 0.0001F) {
            if (hangar.doorProgress < target) {
                hangar.doorProgress = Math.min(target, hangar.doorProgress + DOOR_SPEED);
            } else {
                hangar.doorProgress = Math.max(target, hangar.doorProgress - DOOR_SPEED);
            }
            if (!level.isClientSide) {
                hangar.setChanged();
            }
        }
    }

    private HangarSignal computeSignal(boolean traffic) {
        ItemStack stack = items.getItem(0);
        if (stack.isEmpty() || !(stack.getItem() instanceof PersonalWrightportItem)) {
            return HangarSignal.RED;
        }
        if (traffic || doorOpen || doorProgress > 0.05F) {
            return HangarSignal.YELLOW;
        }
        Swarm swarm = Swarm.from(stack);
        return swarm.size() > 0 ? HangarSignal.GREEN : HangarSignal.RED;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.wrights.hangar");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HangarMenu(id, inventory, this.getBlockPos());
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Wrightport", items.createTag(registries));
        tag.putBoolean("DoorOpen", doorOpen);
        tag.putFloat("DoorProgress", doorProgress);
        tag.putByte("Signal", (byte) signal.id());
        tag.putInt("DoorHold", doorHoldTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String key = tag.contains("Wrightport") ? "Wrightport" : "Cradle";
        items.fromTag(tag.getList(key, Tag.TAG_COMPOUND), registries);
        doorOpen = tag.getBoolean("DoorOpen");
        doorProgress = tag.contains("DoorProgress") ? tag.getFloat("DoorProgress") : (doorOpen ? 1.0F : 0.0F);
        doorProgressO = doorProgress;
        signal = HangarSignal.byId(tag.getByte("Signal"));
        doorHoldTicks = tag.getInt("DoorHold");
    }
}
