package com.wrights.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import com.wrights.block.ModBlocks;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.network.PortEnergySync;

public class WrightportMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    private int sentStored = Integer.MIN_VALUE;
    private int sentCapacity = Integer.MIN_VALUE;

    public WrightportMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public WrightportMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.WRIGHTPORT.get(), id);
        this.pos = pos.immutable();
        this.player = inventory.player;
        Level level = inventory.player.level();
        this.access = ContainerLevelAccess.create(level, this.pos);
        net.minecraft.world.Container container = level.getBlockEntity(this.pos) instanceof WrightportBlockEntity port
                ? port.items()
                : new SimpleContainer(1);
        addSlot(new Slot(container, 0, 0, 0) {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        access.execute((level, at) -> {
            if (!(level.getBlockEntity(at) instanceof WrightportBlockEntity port)) {
                return;
            }
            int stored = port.energyStored();
            int capacity = port.energyCapacity();
            if (stored == sentStored && capacity == sentCapacity) {
                return;
            }
            sentStored = stored;
            sentCapacity = capacity;
            PacketDistributor.sendToPlayer(server, new PortEnergySync(at, stored, capacity));
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.WRIGHTPORT.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
