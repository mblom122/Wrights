package com.wrights.compat.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.wrights.WrightsMod;
import com.wrights.block.WrightportBlock;
import com.wrights.block.WrightportBlockEntity;
import com.wrights.drone.Swarm;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum WrightportJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "wrightport");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        WrightportBlockEntity port = portAt(accessor);
        if (port == null) {
            return;
        }
        if (!port.label().isBlank()) {
            tooltip.add(port.title());
        }
        tooltip.add(port.ownerLabel());
        tooltip.add(Component.translatable("item.wrights.personal_wrightport.drones", port.swarm().units().size(), Swarm.capacity()));
    }

    /** Upper half has no BE — read the lower half so both ends show the same tooltip. */
    private static WrightportBlockEntity portAt(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof WrightportBlockEntity port) {
            return port;
        }
        Level level = accessor.getLevel();
        BlockState state = accessor.getBlockState();
        if (level == null || state == null || !(state.getBlock() instanceof WrightportBlock)) {
            return null;
        }
        BlockPos base = WrightportBlock.base(state, accessor.getPosition());
        return level.getBlockEntity(base) instanceof WrightportBlockEntity port ? port : null;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
