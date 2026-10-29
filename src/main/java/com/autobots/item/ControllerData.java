package com.autobots.item;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record ControllerData(
        Optional<UUID> controllerId,
        Optional<GlobalPos> cornerA,
        Optional<GlobalPos> cornerB,
        Optional<UUID> schematicId,
        int sizeX,
        int sizeY,
        int sizeZ,
        int blockCount,
        Optional<GlobalPos> armedOrigin
) {
    public static final ControllerData EMPTY = new ControllerData(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            0,
            0,
            0,
            Optional.empty());

    public static final Codec<ControllerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("controller_id").forGetter(ControllerData::controllerId),
            GlobalPos.CODEC.optionalFieldOf("corner_a").forGetter(ControllerData::cornerA),
            GlobalPos.CODEC.optionalFieldOf("corner_b").forGetter(ControllerData::cornerB),
            UUIDUtil.CODEC.optionalFieldOf("schematic_id").forGetter(ControllerData::schematicId),
            Codec.INT.optionalFieldOf("size_x", 0).forGetter(ControllerData::sizeX),
            Codec.INT.optionalFieldOf("size_y", 0).forGetter(ControllerData::sizeY),
            Codec.INT.optionalFieldOf("size_z", 0).forGetter(ControllerData::sizeZ),
            Codec.INT.optionalFieldOf("block_count", 0).forGetter(ControllerData::blockCount),
            GlobalPos.CODEC.optionalFieldOf("armed_origin").forGetter(ControllerData::armedOrigin)
    ).apply(instance, ControllerData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static ControllerData from(ItemStack stack) {
        ControllerData data = stack.get(ModDataComponents.CONTROLLER.get());
        return data == null ? EMPTY : data;
    }

    public static void write(ItemStack stack, ControllerData data) {
        stack.set(ModDataComponents.CONTROLLER.get(), data);
    }

    public ControllerData ensureId() {
        if (controllerId.isPresent()) {
            return this;
        }
        return new ControllerData(Optional.of(UUID.randomUUID()), cornerA, cornerB, schematicId, sizeX, sizeY, sizeZ, blockCount, armedOrigin);
    }

    public ControllerData withCornerA(GlobalPos pos) {
        return new ControllerData(controllerId, Optional.of(pos), Optional.empty(), schematicId, sizeX, sizeY, sizeZ, blockCount, Optional.empty());
    }

    public ControllerData withCornerB(GlobalPos pos) {
        return new ControllerData(controllerId, cornerA, Optional.of(pos), schematicId, sizeX, sizeY, sizeZ, blockCount, Optional.empty());
    }

    public ControllerData withSchematic(UUID id, int sx, int sy, int sz, int blocks) {
        return new ControllerData(controllerId, cornerA, cornerB, Optional.of(id), sx, sy, sz, blocks, Optional.empty());
    }

    public ControllerData withoutSchematic() {
        return new ControllerData(controllerId, cornerA, cornerB, Optional.empty(), 0, 0, 0, 0, Optional.empty());
    }

    public ControllerData withAim(GlobalPos origin) {
        return new ControllerData(controllerId, cornerA, cornerB, schematicId, sizeX, sizeY, sizeZ, blockCount, Optional.of(origin));
    }

    public ControllerData withoutAim() {
        if (armedOrigin.isEmpty()) {
            return this;
        }
        return new ControllerData(controllerId, cornerA, cornerB, schematicId, sizeX, sizeY, sizeZ, blockCount, Optional.empty());
    }

    public boolean hasSchematic() {
        return schematicId.isPresent() && sizeX > 0 && sizeY > 0 && sizeZ > 0 && blockCount > 0;
    }
}
