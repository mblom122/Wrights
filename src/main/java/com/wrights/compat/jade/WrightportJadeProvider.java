package com.wrights.compat.jade;

import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;
import com.wrights.block.WrightportBlockEntity;

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
            tooltip.add(port.ownerLabel());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
