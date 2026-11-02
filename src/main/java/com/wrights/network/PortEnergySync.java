package com.wrights.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;

/** Stored and capacity of one placed wrightport, sent while its screen is open. */
public record PortEnergySync(BlockPos pos, int stored, int capacity) implements CustomPacketPayload {
    public static final Type<PortEnergySync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "port_energy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortEnergySync> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBlockPos(value.pos);
                buf.writeVarInt(value.stored);
                buf.writeVarInt(value.capacity);
            },
            buf -> new PortEnergySync(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
