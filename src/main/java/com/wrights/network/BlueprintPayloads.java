package com.wrights.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;

public final class BlueprintPayloads {
    private BlueprintPayloads() {
    }

    public record LibraryRequest() implements CustomPacketPayload {
        public static final Type<LibraryRequest> TYPE = payloadType("library_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryRequest> STREAM_CODEC = empty(new LibraryRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Summary(UUID id, String name, int sizeX, int sizeY, int sizeZ, int blocks, String source, UUID folder, int order, boolean favorite, int uses, long lastUsed) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 48);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeVarInt(blocks);
            buf.writeUtf(source, 64);
            buf.writeUUID(folder);
            buf.writeVarInt(order);
            buf.writeBoolean(favorite);
            buf.writeVarInt(uses);
            buf.writeLong(lastUsed);
        }

        public Summary withFavorite(boolean favorite) {
            return new Summary(id, name, sizeX, sizeY, sizeZ, blocks, source, folder, order, favorite, uses, lastUsed);
        }

        public static Summary read(RegistryFriendlyByteBuf buf) {
            return new Summary(
                    buf.readUUID(),
                    buf.readUtf(48),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readUUID(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readLong());
        }
    }

    public record FolderLine(UUID id, String name, UUID parent, int order, boolean favorite) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 48);
            buf.writeUUID(parent);
            buf.writeVarInt(order);
            buf.writeBoolean(favorite);
        }

        public FolderLine withFavorite(boolean favorite) {
            return new FolderLine(id, name, parent, order, favorite);
        }

        public static FolderLine read(RegistryFriendlyByteBuf buf) {
            return new FolderLine(buf.readUUID(), buf.readUtf(48), buf.readUUID(), buf.readVarInt(), buf.readBoolean());
        }
    }

    public record LibrarySync(List<Summary> entries, boolean busy, List<FolderLine> folders, List<UUID> open) implements CustomPacketPayload {
        public static final Type<LibrarySync> TYPE = payloadType("library_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, LibrarySync> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), LibrarySync::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Summary entry : entries) {
                entry.write(buf);
            }
            buf.writeBoolean(busy);
            buf.writeVarInt(folders.size());
            for (FolderLine folder : folders) {
                folder.write(buf);
            }
            buf.writeVarInt(open.size());
            for (UUID id : open) {
                buf.writeUUID(id);
            }
        }

        public static LibrarySync read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<Summary> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(Summary.read(buf));
            }
            boolean busy = buf.readBoolean();
            int folders = buf.readVarInt();
            List<FolderLine> folderLines = new ArrayList<>(folders);
            for (int i = 0; i < folders; i++) {
                folderLines.add(FolderLine.read(buf));
            }
            int openCount = buf.readVarInt();
            List<UUID> open = new ArrayList<>(openCount);
            for (int i = 0; i < openCount; i++) {
                open.add(buf.readUUID());
            }
            return new LibrarySync(entries, busy, folderLines, open);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StartCapture() implements CustomPacketPayload {
        public static final Type<StartCapture> TYPE = payloadType("start_capture");
        public static final StreamCodec<RegistryFriendlyByteBuf, StartCapture> STREAM_CODEC = empty(new StartCapture());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CaptureSync(int mode, int generation, int seq, boolean hasA, BlockPos cornerA, boolean hasB, BlockPos cornerB, String name, boolean hasEdit, UUID editId) implements CustomPacketPayload {
        public static final Type<CaptureSync> TYPE = payloadType("capture_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptureSync> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), CaptureSync::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(mode);
            buf.writeVarInt(generation);
            buf.writeVarInt(seq);
            writePos(buf, hasA, cornerA);
            writePos(buf, hasB, cornerB);
            buf.writeUtf(name, 48);
            buf.writeBoolean(hasEdit);
            if (hasEdit) {
                buf.writeUUID(editId);
            }
        }

        public static CaptureSync read(RegistryFriendlyByteBuf buf) {
            int mode = buf.readByte();
            int generation = buf.readVarInt();
            int seq = buf.readVarInt();
            boolean hasA = buf.readBoolean();
            BlockPos cornerA = hasA ? buf.readBlockPos() : BlockPos.ZERO;
            boolean hasB = buf.readBoolean();
            BlockPos cornerB = hasB ? buf.readBlockPos() : BlockPos.ZERO;
            String name = buf.readUtf(48);
            boolean hasEdit = buf.readBoolean();
            UUID editId = hasEdit ? buf.readUUID() : net.minecraft.Util.NIL_UUID;
            return new CaptureSync(mode, generation, seq, hasA, cornerA, hasB, cornerB, name, hasEdit, editId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CaptureUpdate(int generation, int seq, boolean hasA, BlockPos cornerA, boolean hasB, BlockPos cornerB, String name) implements CustomPacketPayload {
        public static final Type<CaptureUpdate> TYPE = payloadType("capture_update");
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptureUpdate> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), CaptureUpdate::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(generation);
            buf.writeVarInt(seq);
            writePos(buf, hasA, cornerA);
            writePos(buf, hasB, cornerB);
            buf.writeUtf(name, 48);
        }

        public static CaptureUpdate read(RegistryFriendlyByteBuf buf) {
            int generation = buf.readVarInt();
            int seq = buf.readVarInt();
            boolean hasA = buf.readBoolean();
            BlockPos cornerA = hasA ? buf.readBlockPos() : BlockPos.ZERO;
            boolean hasB = buf.readBoolean();
            BlockPos cornerB = hasB ? buf.readBlockPos() : BlockPos.ZERO;
            return new CaptureUpdate(generation, seq, hasA, cornerA, hasB, cornerB, buf.readUtf(48));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CaptureConfirm(String name, boolean fluids, boolean buildAfter) implements CustomPacketPayload {
        public static final Type<CaptureConfirm> TYPE = payloadType("capture_confirm");
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptureConfirm> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.name, 48);
                    buf.writeBoolean(value.fluids);
                    buf.writeBoolean(value.buildAfter);
                },
                buf -> new CaptureConfirm(buf.readUtf(48), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Resume(int mode, UUID buildId) implements CustomPacketPayload {
        public static final Type<Resume> TYPE = payloadType("resume");
        public static final StreamCodec<RegistryFriendlyByteBuf, Resume> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.mode);
                    buf.writeUUID(value.buildId);
                },
                buf -> new Resume(buf.readByte(), buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CaptureCancel() implements CustomPacketPayload {
        public static final Type<CaptureCancel> TYPE = payloadType("capture_cancel");
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptureCancel> STREAM_CODEC = empty(new CaptureCancel());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShelfEdit(int action, UUID id, boolean folder, UUID parent, int index, String name) implements CustomPacketPayload {
        public static final int CREATE = 0;
        public static final int RENAME = 1;
        public static final int DELETE = 2;
        public static final int MOVE = 3;
        public static final int FAVORITE = 4;
        public static final int OPEN = 5;
        public static final Type<ShelfEdit> TYPE = payloadType("shelf_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShelfEdit> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), ShelfEdit::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(action);
            buf.writeUUID(id);
            buf.writeBoolean(folder);
            buf.writeUUID(parent);
            buf.writeVarInt(index);
            buf.writeUtf(name == null ? "" : name, 48);
        }

        public static ShelfEdit read(RegistryFriendlyByteBuf buf) {
            return new ShelfEdit(buf.readByte(), buf.readUUID(), buf.readBoolean(), buf.readUUID(), buf.readVarInt(), buf.readUtf(48));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeleteBlueprint(UUID id) implements CustomPacketPayload {
        public static final Type<DeleteBlueprint> TYPE = payloadType("delete_blueprint");
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteBlueprint> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.id),
                buf -> new DeleteBlueprint(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DuplicateBlueprint(UUID id, UUID copyId) implements CustomPacketPayload {
        public static final Type<DuplicateBlueprint> TYPE = payloadType("duplicate_blueprint");
        public static final StreamCodec<RegistryFriendlyByteBuf, DuplicateBlueprint> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeUUID(value.copyId);
                },
                buf -> new DuplicateBlueprint(buf.readUUID(), buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RenameBlueprint(UUID id, String name) implements CustomPacketPayload {
        public static final Type<RenameBlueprint> TYPE = payloadType("rename_blueprint");
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameBlueprint> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), RenameBlueprint::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 48);
        }

        public static RenameBlueprint read(RegistryFriendlyByteBuf buf) {
            return new RenameBlueprint(buf.readUUID(), buf.readUtf(48));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PreviewBlueprint(UUID id) implements CustomPacketPayload {
        public static final Type<PreviewBlueprint> TYPE = payloadType("preview_blueprint");
        public static final StreamCodec<RegistryFriendlyByteBuf, PreviewBlueprint> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.id),
                buf -> new PreviewBlueprint(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MaterialLine(int itemId, int count, int player, int supply) {
    }

    public record MaterialsRequest(UUID id, int clipX, int clipY, int clipZ, boolean placeFluids) implements CustomPacketPayload {
        public static final Type<MaterialsRequest> TYPE = payloadType("materials_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, MaterialsRequest> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeVarInt(value.clipX);
                    buf.writeVarInt(value.clipY);
                    buf.writeVarInt(value.clipZ);
                    buf.writeBoolean(value.placeFluids);
                },
                buf -> new MaterialsRequest(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Materials(UUID id, boolean shortage, List<MaterialLine> lines) implements CustomPacketPayload {
        public static final Type<Materials> TYPE = payloadType("materials");
        public static final StreamCodec<RegistryFriendlyByteBuf, Materials> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), Materials::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeBoolean(shortage);
            buf.writeVarInt(lines.size());
            for (MaterialLine line : lines) {
                buf.writeVarInt(line.itemId());
                buf.writeVarInt(line.count());
                buf.writeVarInt(line.player());
                buf.writeVarInt(line.supply());
            }
        }

        public static Materials read(RegistryFriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            boolean shortage = buf.readBoolean();
            int count = buf.readVarInt();
            List<MaterialLine> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(new MaterialLine(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new Materials(id, shortage, lines);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FinishEdit(boolean keep) implements CustomPacketPayload {
        public static final Type<FinishEdit> TYPE = payloadType("finish_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, FinishEdit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeBoolean(value.keep),
                buf -> new FinishEdit(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BeginEdit(UUID id) implements CustomPacketPayload {
        public static final Type<BeginEdit> TYPE = payloadType("begin_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, BeginEdit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.id),
                buf -> new BeginEdit(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BeginBuild(UUID id) implements CustomPacketPayload {
        public static final Type<BeginBuild> TYPE = payloadType("begin_build");
        public static final StreamCodec<RegistryFriendlyByteBuf, BeginBuild> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.id),
                buf -> new BeginBuild(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Bit(short x, short y, short z, int stateId) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeShort(x);
            buf.writeShort(y);
            buf.writeShort(z);
            buf.writeVarInt(stateId);
        }

        public static Bit read(RegistryFriendlyByteBuf buf) {
            return new Bit(buf.readShort(), buf.readShort(), buf.readShort(), buf.readVarInt());
        }
    }

    public record Preview(UUID id, String name, int sizeX, int sizeY, int sizeZ, int blocks, boolean sampled, boolean concealed, List<Bit> bits, int anchorX, int anchorY, int anchorZ, boolean forBuild, boolean openEditor, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) implements CustomPacketPayload {
        public static final Type<Preview> TYPE = payloadType("preview");
        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), Preview::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 48);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeVarInt(blocks);
            buf.writeBoolean(sampled);
            buf.writeBoolean(concealed);
            buf.writeVarInt(anchorX);
            buf.writeVarInt(anchorY);
            buf.writeVarInt(anchorZ);
            buf.writeBoolean(forBuild);
            buf.writeBoolean(openEditor);
            buf.writeBoolean(placeFluids);
            buf.writeBoolean(voidBlocks);
            buf.writeBoolean(voidFluids);
            buf.writeBoolean(voidNbt);
            buf.writeVarInt(bits.size());
            for (Bit bit : bits) {
                bit.write(buf);
            }
        }

        public static Preview read(RegistryFriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(48);
            int sizeX = buf.readVarInt();
            int sizeY = buf.readVarInt();
            int sizeZ = buf.readVarInt();
            int blocks = buf.readVarInt();
            boolean sampled = buf.readBoolean();
            boolean concealed = buf.readBoolean();
            int anchorX = buf.readVarInt();
            int anchorY = buf.readVarInt();
            int anchorZ = buf.readVarInt();
            boolean forBuild = buf.readBoolean();
            boolean openEditor = buf.readBoolean();
            boolean placeFluids = buf.readBoolean();
            boolean voidBlocks = buf.readBoolean();
            boolean voidFluids = buf.readBoolean();
            boolean voidNbt = buf.readBoolean();
            int count = buf.readVarInt();
            List<Bit> bits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                bits.add(Bit.read(buf));
            }
            return new Preview(id, name, sizeX, sizeY, sizeZ, blocks, sampled, concealed, bits, anchorX, anchorY, anchorZ, forBuild, openEditor, placeFluids, voidBlocks, voidFluids, voidNbt);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LaunchBuild(UUID id, BlockPos origin, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean force, boolean playerFirst) implements CustomPacketPayload {
        public static final Type<LaunchBuild> TYPE = payloadType("launch_build");
        public static final StreamCodec<RegistryFriendlyByteBuf, LaunchBuild> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), LaunchBuild::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeBlockPos(origin);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeVarInt(pivotX);
            buf.writeVarInt(pivotY);
            buf.writeVarInt(pivotZ);
            buf.writeVarInt(drones);
            buf.writeBoolean(placeFluids);
            buf.writeBoolean(voidBlocks);
            buf.writeBoolean(voidFluids);
            buf.writeBoolean(voidNbt);
            buf.writeBoolean(force);
            buf.writeBoolean(playerFirst);
        }

        public static LaunchBuild read(RegistryFriendlyByteBuf buf) {
            return new LaunchBuild(buf.readUUID(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CaptureFinished(UUID id, boolean saved, boolean stay) implements CustomPacketPayload {
        public static final Type<CaptureFinished> TYPE = payloadType("capture_finished");
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptureFinished> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeBoolean(value.saved);
                    buf.writeBoolean(value.stay);
                },
                buf -> new CaptureFinished(buf.readUUID(), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditCell(UUID id, int x, int y, int z, int stateId, boolean remove) implements CustomPacketPayload {
        public static final Type<EditCell> TYPE = payloadType("edit_cell");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditCell> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeVarInt(value.x);
                    buf.writeVarInt(value.y);
                    buf.writeVarInt(value.z);
                    buf.writeVarInt(value.stateId);
                    buf.writeBoolean(value.remove);
                },
                buf -> new EditCell(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditLayer(UUID id, int operation, int from, int to) implements CustomPacketPayload {
        public static final int REMOVE = 0;
        public static final int INSERT = 1;
        public static final int MOVE = 2;
        public static final int COPY = 3;
        public static final Type<EditLayer> TYPE = payloadType("edit_layer");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditLayer> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeByte(value.operation);
                    buf.writeVarInt(value.from);
                    buf.writeVarInt(value.to);
                },
                buf -> new EditLayer(buf.readUUID(), buf.readByte(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobHologram(UUID jobId, BlockPos origin, int clipX, int clipY, int clipZ, boolean clear, List<Bit> bits) implements CustomPacketPayload {
        public static final Type<JobHologram> TYPE = payloadType("job_hologram");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobHologram> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), JobHologram::read);

        public static JobHologram clearAll() {
            return new JobHologram(net.minecraft.Util.NIL_UUID, BlockPos.ZERO, 0, 0, 0, true, List.of());
        }

        public static JobHologram clear(UUID jobId) {
            return new JobHologram(jobId, BlockPos.ZERO, 0, 0, 0, true, List.of());
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(jobId);
            buf.writeBlockPos(origin);
            buf.writeVarInt(clipX);
            buf.writeVarInt(clipY);
            buf.writeVarInt(clipZ);
            buf.writeBoolean(clear);
            buf.writeVarInt(bits.size());
            for (Bit bit : bits) {
                bit.write(buf);
            }
        }

        public static JobHologram read(RegistryFriendlyByteBuf buf) {
            UUID jobId = buf.readUUID();
            BlockPos origin = buf.readBlockPos();
            int clipX = buf.readVarInt();
            int clipY = buf.readVarInt();
            int clipZ = buf.readVarInt();
            boolean clear = buf.readBoolean();
            int count = buf.readVarInt();
            List<Bit> bits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                bits.add(Bit.read(buf));
            }
            return new JobHologram(jobId, origin, clipX, clipY, clipZ, clear, bits);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobLine(UUID id, String name, int done, int total, int skipped, boolean paused, boolean saving, boolean finished, boolean canUndo, boolean undoing, boolean stopped, boolean undone, String dimension, BlockPos anchor, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, List<UUID> crew, int workedTicks) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 48);
            buf.writeVarInt(done);
            buf.writeVarInt(total);
            buf.writeVarInt(skipped);
            buf.writeBoolean(paused);
            buf.writeBoolean(saving);
            buf.writeBoolean(finished);
            buf.writeBoolean(canUndo);
            buf.writeBoolean(undoing);
            buf.writeBoolean(stopped);
            buf.writeBoolean(undone);
            buf.writeUtf(dimension, 64);
            buf.writeBlockPos(anchor);
            buf.writeBlockPos(origin);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeVarInt(drones);
            buf.writeVarInt(crew.size());
            for (UUID unit : crew) {
                buf.writeUUID(unit);
            }
            buf.writeVarInt(workedTicks);
        }

        public static JobLine read(RegistryFriendlyByteBuf buf) {
            return new JobLine(
                    buf.readUUID(),
                    buf.readUtf(48),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(64),
                    buf.readBlockPos(),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    readCrew(buf),
                    buf.readVarInt());
        }

        private static List<UUID> readCrew(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<UUID> crew = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                crew.add(buf.readUUID());
            }
            return List.copyOf(crew);
        }
    }

    public record JobSync(List<JobLine> jobs) implements CustomPacketPayload {
        public static final Type<JobSync> TYPE = payloadType("job_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobSync> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), JobSync::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(jobs.size());
            for (JobLine job : jobs) {
                job.write(buf);
            }
        }

        public static JobSync read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<JobLine> jobs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                jobs.add(JobLine.read(buf));
            }
            return new JobSync(jobs);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobControl(UUID id, int action) implements CustomPacketPayload {
        public static final int PAUSE = 0;
        public static final int RESUME = 1;
        public static final int STOP = 2;
        public static final int REDO = 3;
        public static final int UNDO = 4;
        public static final int UNDO_FORCE = 5;
        public static final int REMOVE = 6;
        public static final int VISIT = 7;
        public static final int PAUSE_ALL = 8;
        public static final int RESUME_ALL = 9;
        public static final int REMOVE_ACTIVE = 10;
        public static final int REMOVE_DONE = 11;
        public static final Type<JobControl> TYPE = payloadType("job_control");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobControl> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeByte(value.action);
                },
                buf -> new JobControl(buf.readUUID(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetPace(boolean instant) implements CustomPacketPayload {
        public static final Type<SetPace> TYPE = payloadType("set_pace");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPace> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeBoolean(value.instant),
                buf -> new SetPace(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetJobDrones(UUID id, UUID unit, boolean add) implements CustomPacketPayload {
        public static final Type<SetJobDrones> TYPE = payloadType("set_job_drones");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetJobDrones> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeUUID(value.unit);
                    buf.writeBoolean(value.add);
                },
                buf -> new SetJobDrones(buf.readUUID(), buf.readUUID(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MoveDrone(UUID unit, UUID job) implements CustomPacketPayload {
        public static final Type<MoveDrone> TYPE = payloadType("move_drone");
        public static final StreamCodec<RegistryFriendlyByteBuf, MoveDrone> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.unit);
                    buf.writeUUID(value.job);
                },
                buf -> new MoveDrone(buf.readUUID(), buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobDone(String name, int seconds) implements CustomPacketPayload {
        public static final Type<JobDone> TYPE = payloadType("job_done");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobDone> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.name, 48);
                    buf.writeVarInt(value.seconds);
                },
                buf -> new JobDone(buf.readUtf(48), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetAssign(int count) implements CustomPacketPayload {
        public static final Type<SetAssign> TYPE = payloadType("set_assign");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetAssign> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.count),
                buf -> new SetAssign(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HangarBatch(BlockPos pos, int action, int value, int[] units) implements CustomPacketPayload {
        public static final Type<HangarBatch> TYPE = payloadType("hangar_batch");
        public static final StreamCodec<RegistryFriendlyByteBuf, HangarBatch> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos);
                    buf.writeByte(value.action);
                    buf.writeVarInt(value.value);
                    int[] units = value.units == null ? new int[0] : value.units;
                    buf.writeVarInt(units.length);
                    for (int unit : units) {
                        buf.writeVarInt(unit);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int action = buf.readByte();
                    int value = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > 1024) {
                        throw new IllegalArgumentException("hangar");
                    }
                    int[] units = new int[count];
                    for (int i = 0; i < count; i++) {
                        units[i] = buf.readVarInt();
                    }
                    return new HangarBatch(pos, action, value, units);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HangarEdit(BlockPos pos, int action, int index) implements CustomPacketPayload {
        public static final Type<HangarEdit> TYPE = payloadType("hangar_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, HangarEdit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos);
                    buf.writeByte(value.action);
                    buf.writeVarInt(value.index);
                },
                buf -> new HangarEdit(buf.readBlockPos(), buf.readByte(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BindSupply(BlockPos pos, boolean clear) implements CustomPacketPayload {
        public static final Type<BindSupply> TYPE = payloadType("bind_supply");
        public static final StreamCodec<RegistryFriendlyByteBuf, BindSupply> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos);
                    buf.writeBoolean(value.clear);
                },
                buf -> new BindSupply(buf.readBlockPos(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SupplySync(boolean chest, BlockPos pos) implements CustomPacketPayload {
        public static final Type<SupplySync> TYPE = payloadType("supply_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, SupplySync> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.chest);
                    buf.writeBlockPos(value.pos);
                },
                buf -> new SupplySync(buf.readBoolean(), buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UndoPrompt(UUID id, int extra, int drop) implements CustomPacketPayload {
        public static final int AT_PLAYER = 0;
        public static final int AT_CHEST = 1;
        public static final int CHEST_GONE = 2;
        public static final Type<UndoPrompt> TYPE = payloadType("undo_prompt");
        public static final StreamCodec<RegistryFriendlyByteBuf, UndoPrompt> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeVarInt(value.extra);
                    buf.writeByte(value.drop);
                },
                buf -> new UndoPrompt(buf.readUUID(), buf.readVarInt(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetAnchor(UUID id, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<SetAnchor> TYPE = payloadType("set_anchor");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetAnchor> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), SetAnchor::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeVarInt(z);
        }

        public static SetAnchor read(RegistryFriendlyByteBuf buf) {
            return new SetAnchor(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TransformBlueprint(UUID id, int operation) implements CustomPacketPayload {
        public static final Type<TransformBlueprint> TYPE = payloadType("transform_blueprint");
        public static final StreamCodec<RegistryFriendlyByteBuf, TransformBlueprint> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeByte(value.operation);
                },
                buf -> new TransformBlueprint(buf.readUUID(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RecaptureBlueprint(UUID id) implements CustomPacketPayload {
        public static final Type<RecaptureBlueprint> TYPE = payloadType("recapture_blueprint");
        public static final StreamCodec<RegistryFriendlyByteBuf, RecaptureBlueprint> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.id),
                buf -> new RecaptureBlueprint(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CapturePick(BlockPos pos, int action) implements CustomPacketPayload {
        public static final int CORNER = 0;
        public static final int SECOND = 1;
        public static final int BOX_BEGIN = 2;
        public static final int BOX_COMMIT = 3;
        public static final Type<CapturePick> TYPE = payloadType("capture_pick");
        public static final StreamCodec<RegistryFriendlyByteBuf, CapturePick> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos);
                    buf.writeByte(value.action);
                },
                buf -> new CapturePick(buf.readBlockPos(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StopJob() implements CustomPacketPayload {
        public static final Type<StopJob> TYPE = payloadType("stop_job");
        public static final StreamCodec<RegistryFriendlyByteBuf, StopJob> STREAM_CODEC = empty(new StopJob());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestBlueprintItems() implements CustomPacketPayload {
        public static final Type<RequestBlueprintItems> TYPE = payloadType("request_blueprint_items");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestBlueprintItems> STREAM_CODEC = empty(new RequestBlueprintItems());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestSlipLibrary() implements CustomPacketPayload {
        public static final Type<RequestSlipLibrary> TYPE = payloadType("request_slip_library");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSlipLibrary> STREAM_CODEC = empty(new RequestSlipLibrary());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BlueprintItemLine(int slot, String name, int sizeX, int sizeY, int sizeZ, int blocks, int kind, String nextName, int libraryBlocks) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeUtf(name, 48);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeVarInt(blocks);
            buf.writeByte(kind);
            buf.writeUtf(nextName, 48);
            buf.writeVarInt(libraryBlocks + 1);
        }

        public static BlueprintItemLine read(RegistryFriendlyByteBuf buf) {
            return new BlueprintItemLine(
                    buf.readVarInt(),
                    buf.readUtf(48),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readUtf(48),
                    buf.readVarInt() - 1);
        }
    }

    public record BlueprintItemList(List<BlueprintItemLine> lines) implements CustomPacketPayload {
        public static final Type<BlueprintItemList> TYPE = payloadType("blueprint_item_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintItemList> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), BlueprintItemList::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(lines.size());
            for (BlueprintItemLine line : lines) {
                line.write(buf);
            }
        }

        public static BlueprintItemList read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<BlueprintItemLine> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(BlueprintItemLine.read(buf));
            }
            return new BlueprintItemList(lines);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WriteBlueprintItem(int slot, UUID schematicId) implements CustomPacketPayload {
        public static final Type<WriteBlueprintItem> TYPE = payloadType("write_blueprint_item");
        public static final StreamCodec<RegistryFriendlyByteBuf, WriteBlueprintItem> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.slot + 1);
                    buf.writeUUID(value.schematicId);
                },
                buf -> new WriteBlueprintItem(buf.readVarInt() - 1, buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LoadBlueprintItem(int slot, int mode, String name) implements CustomPacketPayload {
        public static final Type<LoadBlueprintItem> TYPE = payloadType("load_blueprint_item");
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadBlueprintItem> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.slot);
                    buf.writeByte(value.mode);
                    buf.writeUtf(value.name == null ? "" : value.name, 48);
                },
                buf -> new LoadBlueprintItem(buf.readVarInt(), buf.readByte(), buf.readUtf(48)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestFolderItems() implements CustomPacketPayload {
        public static final Type<RequestFolderItems> TYPE = payloadType("request_folder_items");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestFolderItems> STREAM_CODEC = empty(new RequestFolderItems());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FolderItemLine(int slot, String name, int folders, int blueprints, int kind, String nextName, int libraryBlueprints) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeUtf(name, 48);
            buf.writeVarInt(folders);
            buf.writeVarInt(blueprints);
            buf.writeByte(kind);
            buf.writeUtf(nextName, 48);
            buf.writeVarInt(libraryBlueprints + 1);
        }

        public static FolderItemLine read(RegistryFriendlyByteBuf buf) {
            return new FolderItemLine(buf.readVarInt(), buf.readUtf(48), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readUtf(48), buf.readVarInt() - 1);
        }
    }

    public record FolderItemList(List<FolderItemLine> lines) implements CustomPacketPayload {
        public static final Type<FolderItemList> TYPE = payloadType("folder_item_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, FolderItemList> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), FolderItemList::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(lines.size());
            for (FolderItemLine line : lines) {
                line.write(buf);
            }
        }

        public static FolderItemList read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<FolderItemLine> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(FolderItemLine.read(buf));
            }
            return new FolderItemList(lines);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WriteFolderItem(int slot, UUID folderId) implements CustomPacketPayload {
        public static final Type<WriteFolderItem> TYPE = payloadType("write_folder_item");
        public static final StreamCodec<RegistryFriendlyByteBuf, WriteFolderItem> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.slot + 1);
                    buf.writeUUID(value.folderId);
                },
                buf -> new WriteFolderItem(buf.readVarInt() - 1, buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LoadFolderItem(int slot, int mode, String name) implements CustomPacketPayload {
        public static final Type<LoadFolderItem> TYPE = payloadType("load_folder_item");
        public static final StreamCodec<RegistryFriendlyByteBuf, LoadFolderItem> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.slot);
                    buf.writeByte(value.mode);
                    buf.writeUtf(value.name == null ? "" : value.name, 48);
                },
                buf -> new LoadFolderItem(buf.readVarInt(), buf.readByte(), buf.readUtf(48)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClipboardStatus(boolean folder, String name, int a, int b, int c, int count, int kind, String nextName, int libraryStat) {
        public static final ClipboardStatus EMPTY = new ClipboardStatus(false, "", 0, 0, 0, 0, -1, "", -1);

        public boolean present() {
            return kind >= 0 && name != null && !name.isEmpty();
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(folder);
            buf.writeUtf(name == null ? "" : name, 48);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
            buf.writeVarInt(c);
            buf.writeVarInt(count);
            buf.writeByte(kind);
            buf.writeUtf(nextName == null ? "" : nextName, 48);
            buf.writeVarInt(libraryStat + 1);
        }

        public static ClipboardStatus read(RegistryFriendlyByteBuf buf) {
            return new ClipboardStatus(buf.readBoolean(), buf.readUtf(48), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readUtf(48), buf.readVarInt() - 1);
        }
    }

    public record CopyClipboard(UUID id, boolean folder) implements CustomPacketPayload {
        public static final Type<CopyClipboard> TYPE = payloadType("copy_clipboard");
        public static final StreamCodec<RegistryFriendlyByteBuf, CopyClipboard> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeBoolean(value.folder);
                },
                buf -> new CopyClipboard(buf.readUUID(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClipboardOffer(byte[] data, ClipboardStatus status) implements CustomPacketPayload {
        public static final Type<ClipboardOffer> TYPE = payloadType("clipboard_offer");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardOffer> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    writeBlob(buf, value.data);
                    value.status.write(buf);
                },
                buf -> new ClipboardOffer(readBlob(buf), ClipboardStatus.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClipboardPreview(byte[] data) implements CustomPacketPayload {
        public static final Type<ClipboardPreview> TYPE = payloadType("clipboard_preview");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPreview> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> writeBlob(buf, value.data),
                buf -> new ClipboardPreview(readBlob(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClipboardView(ClipboardStatus status) implements CustomPacketPayload {
        public static final Type<ClipboardView> TYPE = payloadType("clipboard_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardView> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> value.status.write(buf),
                buf -> new ClipboardView(ClipboardStatus.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClipboardImport(int mode, String name, byte[] data) implements CustomPacketPayload {
        public static final Type<ClipboardImport> TYPE = payloadType("clipboard_import");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardImport> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.mode);
                    buf.writeUtf(value.name == null ? "" : value.name, 48);
                    writeBlob(buf, value.data);
                },
                buf -> new ClipboardImport(buf.readByte(), buf.readUtf(48), readBlob(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveSession(boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt) implements CustomPacketPayload {
        public static final Type<SaveSession> TYPE = payloadType("save_session");
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveSession> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.placeFluids);
                    buf.writeBoolean(value.voidBlocks);
                    buf.writeBoolean(value.voidFluids);
                    buf.writeBoolean(value.voidNbt);
                },
                buf -> new SaveSession(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeBlob(RegistryFriendlyByteBuf buf, byte[] data) {
        byte[] bytes = data == null ? new byte[0] : data;
        buf.writeVarInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static byte[] readBlob(RegistryFriendlyByteBuf buf) {
        int length = buf.readVarInt();
        if (length < 0 || length > 512_000) {
            throw new IllegalArgumentException("clipboard");
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return data;
    }

    private static <T extends CustomPacketPayload> Type<T> payloadType(String path) {
        return new Type<>(ResourceLocation.fromNamespaceAndPath(WrightsMod.MOD_ID, path));
    }

    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> empty(T value) {
        return StreamCodec.of((buf, ignored) -> {
        }, buf -> value);
    }

    private static void writePos(RegistryFriendlyByteBuf buf, boolean present, BlockPos pos) {
        buf.writeBoolean(present);
        if (present && pos != null) {
            buf.writeBlockPos(pos);
        }
    }
}
