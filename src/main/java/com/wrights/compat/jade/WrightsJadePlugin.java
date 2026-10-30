package com.wrights.compat.jade;

import com.wrights.block.WrightportBlock;
import com.wrights.entity.DroneEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class WrightsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(WrightJadeProvider.INSTANCE, DroneEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(WrightJadeProvider.INSTANCE, DroneEntity.class);
        registration.registerBlockComponent(WrightportJadeProvider.INSTANCE, WrightportBlock.class);
    }
}
