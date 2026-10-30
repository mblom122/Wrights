package com.wrights.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;
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
        if (accessor.getBlockEntity() instanceof WrightportBlockEntity port) {
            if (!port.label().isBlank()) {
                tooltip.add(port.title());
            }
            tooltip.add(port.ownerLabel());
            tooltip.add(Component.translatable("item.wrights.personal_wrightport.drones", port.swarm().units().size(), Swarm.capacity()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
