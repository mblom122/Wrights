package com.wrights.compat.jade;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import com.wrights.WrightsMod;
import com.wrights.drone.WrightTier;
import com.wrights.entity.DroneEntity;
import com.wrights.job.BuildJob;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;

import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

public enum WrightJadeProvider implements IEntityComponentProvider, StreamServerDataProvider<EntityAccessor, WrightJadeProvider.WrightStatus> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, "wright");
    private static final StreamCodec<RegistryFriendlyByteBuf, WrightStatus> CODEC = StreamCodec.of(WrightStatus::write, WrightStatus::read);

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (accessor.getEntity() instanceof DroneEntity drone) {
            tooltip.add(Component.translatable("jade.wrights.tier", Component.translatable(WrightTier.nameKey(drone.tier()))));
            if (!drone.currentBlock().isEmpty()) {
                tooltip.add(Component.translatable("hud.wrights.info_wright_placing", drone.currentBlock().getHoverName()));
            }
            if (!drone.nextBlock().isEmpty()) {
                tooltip.add(Component.translatable("hud.wrights.info_wright_next", drone.nextBlock().getHoverName()));
            }
            Component waiting = waitLine(drone.waitReason());
            if (waiting != null) {
                tooltip.add(waiting);
            }
        }
        decodeFromData(accessor).ifPresent(status -> {
            tooltip.add(Component.translatable(status.kind().key(), status.name()));
            int percent = Mth.clamp(status.done() * 100 / Math.max(1, status.total()), 0, 100);
            tooltip.add(Component.translatable("hud.wrights.job_detail", status.done(), status.total(), percent, status.skipped()));
            tooltip.add(Component.translatable("jade.wrights.size", status.sizeX(), status.sizeY(), status.sizeZ()));
        });
    }

    private static Component waitLine(int reason) {
        return switch (reason) {
            case DroneEntity.WAIT_FLOOR -> Component.translatable("hud.wrights.info_wright_wait_floor");
            case DroneEntity.WAIT_ITEMS -> Component.translatable("hud.wrights.info_wright_wait_items");
            case DroneEntity.WAIT_CHUNK -> Component.translatable("hud.wrights.info_wright_wait_chunk");
            case DroneEntity.WAIT_IDLE -> Component.translatable("hud.wrights.info_wright_idle");
            case DroneEntity.WAIT_HOME -> Component.translatable("hud.wrights.info_wright_home");
            case DroneEntity.WAIT_SHELTER -> Component.translatable("hud.wrights.info_wright_shelter");
            case DroneEntity.WAIT_PAUSED -> Component.translatable("hud.wrights.info_wright_paused");
            default -> null;
        };
    }

    @Override
    public @Nullable WrightStatus streamData(EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof DroneEntity drone) || !(accessor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        SchematicLibrary library = SchematicLibrary.get(level.getServer());
        for (BuildJob job : library.jobs()) {
            if (job.ownsDrone(drone.getUUID())) {
                return WrightStatus.from(job, library);
            }
        }
        return null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, WrightStatus> streamCodec() {
        return CODEC;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    public record WrightStatus(String name, Kind kind, int done, int total, int skipped, int sizeX, int sizeY, int sizeZ) {
        enum Kind {
            BUILDING("jade.wrights.building"),
            PAUSED("jade.wrights.paused"),
            UNDOING("jade.wrights.undoing"),
            SAVING("jade.wrights.saving"),
            WAITING("jade.wrights.waiting"),
            STALLED("jade.wrights.stalled");

            private final String key;

            Kind(String key) {
                this.key = key;
            }

            String key() {
                return key;
            }

            static Kind byId(int id) {
                Kind[] values = values();
                if (id < 0 || id >= values.length) {
                    return BUILDING;
                }
                return values[id];
            }
        }

        static WrightStatus from(BuildJob job, SchematicLibrary library) {
            Kind kind;
            if (job.undoing()) {
                kind = Kind.UNDOING;
            } else if (job.paused()) {
                kind = Kind.PAUSED;
            } else if (job.kind() == BuildJob.Kind.COPY) {
                kind = Kind.SAVING;
            } else if (job.waitKind() == BuildJob.WAIT_ITEMS) {
                kind = Kind.STALLED;
            } else if (job.awaitingDrones()) {
                kind = Kind.WAITING;
            } else {
                kind = Kind.BUILDING;
            }
            int done = job.progressDone();
            int total = job.undoing() ? Math.max(1, job.undoTotal()) : job.total();
            int sizeX;
            int sizeY;
            int sizeZ;
            if (job.kind() == BuildJob.Kind.COPY || job.clipX() != Integer.MAX_VALUE) {
                sizeX = job.boxSizeX();
                sizeY = job.boxSizeY();
                sizeZ = job.boxSizeZ();
            } else {
                sizeX = job.spanX();
                sizeY = job.spanY();
                sizeZ = job.spanZ();
            }
            return new WrightStatus(nameOf(job, library), kind, done, total, job.progressSkipped(), sizeX, sizeY, sizeZ);
        }

        private static String nameOf(BuildJob job, SchematicLibrary library) {
            String name = job.blueprintName();
            if (name == null || name.isBlank()) {
                name = job.kind() == BuildJob.Kind.COPY ? "Saving" : "Build";
                if (job.schematicId() != null) {
                    name = library.schematic(job.schematicId()).map(Schematic::name).orElse(name);
                }
            }
            if (name.length() > 48) {
                name = name.substring(0, 48);
            }
            return name;
        }

        static void write(RegistryFriendlyByteBuf buf, WrightStatus status) {
            buf.writeUtf(status.name, 48);
            buf.writeByte(status.kind.ordinal());
            buf.writeVarInt(status.done);
            buf.writeVarInt(status.total);
            buf.writeVarInt(status.skipped);
            buf.writeVarInt(status.sizeX);
            buf.writeVarInt(status.sizeY);
            buf.writeVarInt(status.sizeZ);
        }

        static WrightStatus read(RegistryFriendlyByteBuf buf) {
            return new WrightStatus(
                    buf.readUtf(48),
                    Kind.byId(buf.readUnsignedByte()),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt());
        }
    }
}
