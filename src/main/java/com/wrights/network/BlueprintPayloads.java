package com.wrights.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

import com.wrights.WrightsMod;
import com.wrights.drone.Swarm;

public final class BlueprintPayloads {
    private BlueprintPayloads() {
    }

    public record LibraryRequest(boolean global) implements CustomPacketPayload {
        public static final Type<LibraryRequest> TYPE = payloadType("library_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryRequest> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeBoolean(value.global),
                buf -> new LibraryRequest(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Move a blueprint between the world library and the global AppData library. */
    public record LibraryMove(UUID id, boolean toGlobal) implements CustomPacketPayload {
        public static final Type<LibraryMove> TYPE = payloadType("library_move");
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryMove> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeBoolean(value.toGlobal);
                },
                buf -> new LibraryMove(buf.readUUID(), buf.readBoolean()));

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

    public record LibrarySync(List<Summary> entries, boolean busy, List<FolderLine> folders, List<UUID> open, boolean global) implements CustomPacketPayload {
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
            buf.writeBoolean(global);
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
            boolean global = buf.readBoolean();
            return new LibrarySync(entries, busy, folderLines, open, global);
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

    public record CaptureConfirm(String name, boolean fluids, boolean nbtBlocks, boolean nbtSettings, boolean nbtAll, boolean buildAfter, byte[] turns) implements CustomPacketPayload {
        public static final Type<CaptureConfirm> TYPE = payloadType("capture_confirm");
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptureConfirm> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.name, 48);
                    buf.writeBoolean(value.fluids);
                    buf.writeBoolean(value.nbtBlocks);
                    buf.writeBoolean(value.nbtSettings);
                    buf.writeBoolean(value.nbtAll);
                    buf.writeBoolean(value.buildAfter);
                    int count = Math.min(value.turns.length, 24);
                    buf.writeByte(count);
                    for (int i = 0; i < count; i++) {
                        buf.writeByte(value.turns[i]);
                    }
                },
                buf -> {
                    String name = buf.readUtf(48);
                    boolean fluids = buf.readBoolean();
                    boolean nbtBlocks = buf.readBoolean();
                    boolean nbtSettings = buf.readBoolean();
                    boolean nbtAll = buf.readBoolean();
                    boolean buildAfter = buf.readBoolean();
                    int count = buf.readUnsignedByte();
                    byte[] turns = new byte[Math.min(count, 24)];
                    for (int i = 0; i < turns.length; i++) {
                        turns[i] = buf.readByte();
                    }
                    int skip = count - turns.length;
                    for (int i = 0; i < skip; i++) {
                        buf.readByte();
                    }
                    return new CaptureConfirm(name, fluids, nbtBlocks, nbtSettings, nbtAll, buildAfter, turns);
                });

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

    public record MaterialLine(int itemId, int count, int player, int supply, int digital) {
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

    public record Materials(UUID id, boolean shortage, boolean digital, List<MaterialLine> lines) implements CustomPacketPayload {
        public static final Type<Materials> TYPE = payloadType("materials");
        public static final StreamCodec<RegistryFriendlyByteBuf, Materials> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), Materials::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeBoolean(shortage);
            buf.writeBoolean(digital);
            buf.writeVarInt(lines.size());
            for (MaterialLine line : lines) {
                buf.writeVarInt(line.itemId());
                buf.writeVarInt(line.count());
                buf.writeVarInt(line.player());
                buf.writeVarInt(line.supply());
                buf.writeVarInt(line.digital());
            }
        }

        public static Materials read(RegistryFriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            boolean shortage = buf.readBoolean();
            boolean digital = buf.readBoolean();
            int count = buf.readVarInt();
            List<MaterialLine> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(new MaterialLine(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new Materials(id, shortage, digital, lines);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FinishEdit(boolean keep, boolean placeFluids, boolean placeNbt, boolean placeSettings, boolean placeAll) implements CustomPacketPayload {
        public static final Type<FinishEdit> TYPE = payloadType("finish_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, FinishEdit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.keep);
                    buf.writeBoolean(value.placeFluids);
                    buf.writeBoolean(value.placeNbt);
                    buf.writeBoolean(value.placeSettings);
                    buf.writeBoolean(value.placeAll);
                },
                buf -> new FinishEdit(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

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

    public record Preview(UUID id, String name, int sizeX, int sizeY, int sizeZ, int blocks, boolean sampled, boolean concealed, List<Bit> bits, int anchorX, int anchorY, int anchorZ, boolean forBuild, boolean openEditor, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean storedSettings, boolean storedAll) implements CustomPacketPayload {
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
            buf.writeBoolean(storedSettings);
            buf.writeBoolean(storedAll);
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
            boolean storedSettings = buf.readBoolean();
            boolean storedAll = buf.readBoolean();
            int count = buf.readVarInt();
            List<Bit> bits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                bits.add(Bit.read(buf));
            }
            return new Preview(id, name, sizeX, sizeY, sizeZ, blocks, sampled, concealed, bits, anchorX, anchorY, anchorZ, forBuild, openEditor, placeFluids, voidBlocks, voidFluids, voidNbt, storedSettings, storedAll);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LaunchBuild(UUID id, BlockPos origin, int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY, int pivotZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean force, boolean playerFirst, boolean queue, boolean placeNbt, boolean placeSettings, boolean placeAll, boolean mirror, BlockPos mirrorAnchor, int mirrorFace, int mirrorPattern, boolean mirrorShapes, int mirrorAxes) implements CustomPacketPayload {
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
            buf.writeBoolean(queue);
            buf.writeBoolean(placeNbt);
            buf.writeBoolean(placeSettings);
            buf.writeBoolean(placeAll);
            buf.writeBoolean(mirror);
            buf.writeBlockPos(mirrorAnchor);
            buf.writeVarInt(mirrorFace);
            buf.writeVarInt(mirrorPattern);
            buf.writeBoolean(mirrorShapes);
            buf.writeVarInt(mirrorAxes);
        }

        public static LaunchBuild read(RegistryFriendlyByteBuf buf) {
            return new LaunchBuild(buf.readUUID(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
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

    public record EditLayer(UUID id, int operation, int from, int to, int axis) implements CustomPacketPayload {
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
                    buf.writeByte(value.axis);
                },
                buf -> new EditLayer(buf.readUUID(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobHologram(UUID jobId, BlockPos origin, int clipX, int clipY, int clipZ, boolean clear, List<Bit> bits, String dimension, boolean mine) implements CustomPacketPayload {
        public static final Type<JobHologram> TYPE = payloadType("job_hologram");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobHologram> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), JobHologram::read);

        public static JobHologram clearAll() {
            return new JobHologram(net.minecraft.Util.NIL_UUID, BlockPos.ZERO, 0, 0, 0, true, List.of(), "", false);
        }

        public static JobHologram clear(UUID jobId) {
            return new JobHologram(jobId, BlockPos.ZERO, 0, 0, 0, true, List.of(), "", false);
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
            buf.writeUtf(dimension == null ? "" : dimension, 64);
            buf.writeBoolean(mine);
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
            return new JobHologram(jobId, origin, clipX, clipY, clipZ, clear, bits, buf.readUtf(64), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Drops one placed cell from a job's ghost preview. */
    public record JobGhostDrop(UUID jobId, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<JobGhostDrop> TYPE = payloadType("job_ghost_drop");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobGhostDrop> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.jobId);
                    buf.writeShort(value.x);
                    buf.writeShort(value.y);
                    buf.writeShort(value.z);
                },
                buf -> new JobGhostDrop(buf.readUUID(), Short.toUnsignedInt(buf.readShort()), Short.toUnsignedInt(buf.readShort()), Short.toUnsignedInt(buf.readShort())));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobLine(UUID id, String name, int done, int total, int skipped, int held, boolean paused, boolean saving, boolean finished, boolean canUndo, boolean undoing, boolean stopped, boolean undone, String dimension, BlockPos anchor, BlockPos origin, int sizeX, int sizeY, int sizeZ, int drones, List<UUID> crew, int workedTicks, int hold, int waitKind, int source, BlockPos sourcePort, boolean labelPort, boolean queued, int quota, String sourceName, boolean cellMarks) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 48);
            buf.writeVarInt(done);
            buf.writeVarInt(total);
            buf.writeVarInt(skipped);
            buf.writeVarInt(held);
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
            buf.writeVarInt(hold);
            buf.writeVarInt(waitKind);
            buf.writeVarInt(source);
            buf.writeBlockPos(sourcePort);
            buf.writeBoolean(labelPort);
            buf.writeBoolean(queued);
            buf.writeVarInt(quota);
            buf.writeUtf(sourceName == null ? "" : sourceName, 32);
            buf.writeBoolean(cellMarks);
        }

        public static JobLine read(RegistryFriendlyByteBuf buf) {
            return new JobLine(
                    buf.readUUID(),
                    buf.readUtf(48),
                    buf.readVarInt(),
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
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBlockPos(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readUtf(32),
                    buf.readBoolean());
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

    public record SetPortOrder(boolean playerFirst) implements CustomPacketPayload {
        public static final Type<SetPortOrder> TYPE = payloadType("set_port_order");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPortOrder> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeBoolean(value.playerFirst),
                buf -> new SetPortOrder(buf.readBoolean()));

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

    /** Where one wright on a job is, so it can be highlighted after the flyer is out of sight. 0 working, 1 flying home, 2 not out. */
    public record WrightSpot(UUID unit, String dimension, BlockPos pos, int state) {
    }

    public record WrightSpots(List<WrightSpot> spots) implements CustomPacketPayload {
        public static final Type<WrightSpots> TYPE = payloadType("wright_spots");
        public static final StreamCodec<RegistryFriendlyByteBuf, WrightSpots> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.spots.size());
                    for (WrightSpot spot : value.spots) {
                        buf.writeUUID(spot.unit);
                        buf.writeUtf(spot.dimension == null ? "" : spot.dimension, 64);
                        buf.writeBlockPos(spot.pos == null ? BlockPos.ZERO : spot.pos);
                        buf.writeByte(spot.state);
                    }
                },
                buf -> {
                    int count = buf.readVarInt();
                    List<WrightSpot> spots = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        spots.add(new WrightSpot(buf.readUUID(), buf.readUtf(64), buf.readBlockPos(), buf.readByte()));
                    }
                    return new WrightSpots(spots);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record VisitWright(UUID unit) implements CustomPacketPayload {
        public static final Type<VisitWright> TYPE = payloadType("visit_wright");
        public static final StreamCodec<RegistryFriendlyByteBuf, VisitWright> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.unit),
                buf -> new VisitWright(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DockToast(int docked, int dx, int dy, int dz, String port, int digital, int sx, int sy, int sz, int dropped, int gx, int gy, int gz) implements CustomPacketPayload {
        public static final Type<DockToast> TYPE = payloadType("dock_toast");
        public static final StreamCodec<RegistryFriendlyByteBuf, DockToast> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.docked);
                    buf.writeVarInt(value.dx);
                    buf.writeVarInt(value.dy);
                    buf.writeVarInt(value.dz);
                    buf.writeUtf(value.port == null ? "" : value.port, 32);
                    buf.writeVarInt(value.digital);
                    buf.writeVarInt(value.sx);
                    buf.writeVarInt(value.sy);
                    buf.writeVarInt(value.sz);
                    buf.writeVarInt(value.dropped);
                    buf.writeVarInt(value.gx);
                    buf.writeVarInt(value.gy);
                    buf.writeVarInt(value.gz);
                },
                buf -> new DockToast(
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobDone(Component name, Component detail, int seconds) implements CustomPacketPayload {
        public static final Type<JobDone> TYPE = payloadType("job_done");
        public static final StreamCodec<RegistryFriendlyByteBuf, JobDone> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeJsonWithCodec(ComponentSerialization.CODEC, value.name);
                    buf.writeJsonWithCodec(ComponentSerialization.CODEC, value.detail == null ? Component.empty() : value.detail);
                    buf.writeVarInt(value.seconds);
                },
                buf -> new JobDone(
                        buf.readJsonWithCodec(ComponentSerialization.CODEC),
                        buf.readJsonWithCodec(ComponentSerialization.CODEC),
                        buf.readVarInt()));

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

    public record DeployAsk() implements CustomPacketPayload {
        public static final Type<DeployAsk> TYPE = payloadType("deploy_ask");
        public static final StreamCodec<RegistryFriendlyByteBuf, DeployAsk> STREAM_CODEC = empty(new DeployAsk());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeployPool(int free, int stored) implements CustomPacketPayload {
        public static final Type<DeployPool> TYPE = payloadType("deploy_pool");
        public static final StreamCodec<RegistryFriendlyByteBuf, DeployPool> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.free);
                    buf.writeVarInt(value.stored);
                },
                buf -> new DeployPool(buf.readVarInt(), buf.readVarInt()));

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

    public record PortEdit(int kind, int index, int action, int unit) implements CustomPacketPayload {
        public static final Type<PortEdit> TYPE = payloadType("port_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, PortEdit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.kind);
                    buf.writeVarInt(value.index);
                    buf.writeByte(value.action);
                    buf.writeVarInt(value.unit);
                },
                buf -> new PortEdit(buf.readByte(), buf.readVarInt(), buf.readByte(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PortBatch(int kind, int index, int action, int value, int[] units) implements CustomPacketPayload {
        public static final Type<PortBatch> TYPE = payloadType("port_batch");
        public static final StreamCodec<RegistryFriendlyByteBuf, PortBatch> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.kind);
                    buf.writeVarInt(value.index);
                    buf.writeByte(value.action);
                    buf.writeVarInt(value.value);
                    int[] units = value.units == null ? new int[0] : value.units;
                    buf.writeVarInt(units.length);
                    for (int unit : units) {
                        buf.writeVarInt(unit);
                    }
                },
                buf -> {
                    int kind = buf.readByte();
                    int index = buf.readVarInt();
                    int action = buf.readByte();
                    int value = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > 1024) {
                        throw new IllegalArgumentException("port");
                    }
                    int[] units = new int[count];
                    for (int i = 0; i < count; i++) {
                        units[i] = buf.readVarInt();
                    }
                    return new PortBatch(kind, index, action, value, units);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PortHold(int kind, int index, boolean active) implements CustomPacketPayload {
        public static final Type<PortHold> TYPE = payloadType("port_hold");
        public static final StreamCodec<RegistryFriendlyByteBuf, PortHold> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.kind);
                    buf.writeVarInt(value.index);
                    buf.writeBoolean(value.active);
                },
                buf -> new PortHold(buf.readByte(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedPort(String dimension, BlockPos pos, int units, boolean unowned, String name) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(dimension);
            buf.writeBlockPos(pos);
            buf.writeVarInt(units);
            buf.writeBoolean(unowned);
            buf.writeUtf(name == null ? "" : name, 32);
        }

        public static PlacedPort read(RegistryFriendlyByteBuf buf) {
            return new PlacedPort(buf.readUtf(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(32));
        }
    }

    public record RenameWrightport(String dimension, BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<RenameWrightport> TYPE = payloadType("rename_wrightport");
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameWrightport> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
                    buf.writeBlockPos(value.pos);
                    buf.writeUtf(value.name == null ? "" : value.name, 32);
                },
                buf -> new RenameWrightport(buf.readUtf(), buf.readBlockPos(), buf.readUtf(32)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Blink a wrightport in the world for a few seconds. */
    public record PortGlow(String dimension, BlockPos pos, boolean line, BlockPos anchor) implements CustomPacketPayload {
        public static final Type<PortGlow> TYPE = payloadType("port_glow");
        public static final StreamCodec<RegistryFriendlyByteBuf, PortGlow> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
                    buf.writeBlockPos(value.pos);
                    buf.writeBoolean(value.line);
                    buf.writeBlockPos(value.anchor == null ? BlockPos.ZERO : value.anchor);
                },
                buf -> new PortGlow(buf.readUtf(), buf.readBlockPos(), buf.readBoolean(), buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** mode 0 is the open wrightport block, 1 a carried personal port, 2 a placed port from Manage. */
    public record RenameWright(int mode, String dimension, BlockPos pos, int kind, int port, int unit, String name) implements CustomPacketPayload {
        public static final Type<RenameWright> TYPE = payloadType("rename_wright");
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameWright> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.mode);
                    buf.writeUtf(value.dimension == null ? "" : value.dimension);
                    buf.writeBlockPos(value.pos == null ? BlockPos.ZERO : value.pos);
                    buf.writeByte(value.kind);
                    buf.writeVarInt(value.port);
                    buf.writeVarInt(value.unit);
                    buf.writeUtf(value.name == null ? "" : value.name, 32);
                },
                buf -> new RenameWright(buf.readByte(), buf.readUtf(), buf.readBlockPos(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedPortsAsk() implements CustomPacketPayload {
        public static final Type<PlacedPortsAsk> TYPE = payloadType("placed_ports_ask");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedPortsAsk> STREAM_CODEC = empty(new PlacedPortsAsk());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedPorts(List<PlacedPort> ports) implements CustomPacketPayload {
        public static final Type<PlacedPorts> TYPE = payloadType("placed_ports");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedPorts> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    List<PlacedPort> ports = value.ports == null ? List.of() : value.ports;
                    buf.writeVarInt(ports.size());
                    for (PlacedPort port : ports) {
                        port.write(buf);
                    }
                },
                buf -> {
                    int count = buf.readVarInt();
                    if (count < 0 || count > 512) {
                        throw new IllegalArgumentException("ports");
                    }
                    List<PlacedPort> ports = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        ports.add(PlacedPort.read(buf));
                    }
                    return new PlacedPorts(ports);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedSwarmAsk(String dimension, BlockPos pos) implements CustomPacketPayload {
        public static final Type<PlacedSwarmAsk> TYPE = payloadType("placed_swarm_ask");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedSwarmAsk> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
                    buf.writeBlockPos(value.pos);
                },
                buf -> new PlacedSwarmAsk(buf.readUtf(), buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedSwarm(String dimension, BlockPos pos, boolean missing, Swarm swarm) implements CustomPacketPayload {
        public static final Type<PlacedSwarm> TYPE = payloadType("placed_swarm");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedSwarm> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
                    buf.writeBlockPos(value.pos);
                    buf.writeBoolean(value.missing);
                    Swarm.STREAM_CODEC.encode(buf, value.swarm == null ? Swarm.EMPTY : value.swarm);
                },
                buf -> new PlacedSwarm(buf.readUtf(), buf.readBlockPos(), buf.readBoolean(), Swarm.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedVisit(String dimension, BlockPos pos) implements CustomPacketPayload {
        public static final Type<PlacedVisit> TYPE = payloadType("placed_visit");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedVisit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
                    buf.writeBlockPos(value.pos);
                },
                buf -> new PlacedVisit(buf.readUtf(), buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedEdit(String dimension, BlockPos pos, int action, int unit) implements CustomPacketPayload {
        public static final Type<PlacedEdit> TYPE = payloadType("placed_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedEdit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
                    buf.writeBlockPos(value.pos);
                    buf.writeByte(value.action);
                    buf.writeVarInt(value.unit);
                },
                buf -> new PlacedEdit(buf.readUtf(), buf.readBlockPos(), buf.readByte(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlacedBatch(String dimension, BlockPos pos, int action, int value, int[] units) implements CustomPacketPayload {
        public static final Type<PlacedBatch> TYPE = payloadType("placed_batch");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacedBatch> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUtf(value.dimension);
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
                    String dimension = buf.readUtf();
                    BlockPos pos = buf.readBlockPos();
                    int action = buf.readByte();
                    int value = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > 1024) {
                        throw new IllegalArgumentException("port");
                    }
                    int[] units = new int[count];
                    for (int i = 0; i < count; i++) {
                        units[i] = buf.readVarInt();
                    }
                    return new PlacedBatch(dimension, pos, action, value, units);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BindPortChest(BlockPos port, BlockPos chest, boolean clear) implements CustomPacketPayload {
        public static final Type<BindPortChest> TYPE = payloadType("bind_port_chest");
        public static final StreamCodec<RegistryFriendlyByteBuf, BindPortChest> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.port);
                    buf.writeBlockPos(value.chest);
                    buf.writeBoolean(value.clear);
                },
                buf -> new BindPortChest(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean()));

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

    public record BindDigital(BlockPos pos, boolean clear) implements CustomPacketPayload {
        public static final Type<BindDigital> TYPE = payloadType("bind_digital");
        public static final StreamCodec<RegistryFriendlyByteBuf, BindDigital> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos);
                    buf.writeBoolean(value.clear);
                },
                buf -> new BindDigital(buf.readBlockPos(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DigitalSync(int kind, boolean online, BlockPos pos, String dim) implements CustomPacketPayload {
        public static final Type<DigitalSync> TYPE = payloadType("digital_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, DigitalSync> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.kind);
                    buf.writeBoolean(value.online);
                    buf.writeBlockPos(value.pos == null ? BlockPos.ZERO : value.pos);
                    buf.writeUtf(value.dim == null ? "" : value.dim, 128);
                },
                buf -> new DigitalSync(buf.readByte(), buf.readBoolean(), buf.readBlockPos(), buf.readUtf(128)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DigitalInfoRequest() implements CustomPacketPayload {
        public static final Type<DigitalInfoRequest> TYPE = payloadType("digital_info_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, DigitalInfoRequest> STREAM_CODEC = empty(new DigitalInfoRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DigitalInfoLine(int itemId, long stock, boolean craftable) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(itemId);
            buf.writeVarLong(stock);
            buf.writeBoolean(craftable);
        }

        public static DigitalInfoLine read(RegistryFriendlyByteBuf buf) {
            return new DigitalInfoLine(buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
        }
    }

    public record DigitalInfoSync(int kind, boolean online, BlockPos pos, String dim, java.util.List<DigitalInfoLine> lines) implements CustomPacketPayload {
        public static final Type<DigitalInfoSync> TYPE = payloadType("digital_info_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, DigitalInfoSync> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeByte(value.kind);
                    buf.writeBoolean(value.online);
                    buf.writeBlockPos(value.pos == null ? BlockPos.ZERO : value.pos);
                    buf.writeUtf(value.dim == null ? "" : value.dim, 128);
                    java.util.List<DigitalInfoLine> lines = value.lines == null ? java.util.List.of() : value.lines;
                    buf.writeVarInt(lines.size());
                    for (DigitalInfoLine line : lines) {
                        line.write(buf);
                    }
                },
                buf -> {
                    int kind = buf.readByte();
                    boolean online = buf.readBoolean();
                    BlockPos pos = buf.readBlockPos();
                    String dim = buf.readUtf(128);
                    int count = buf.readVarInt();
                    java.util.List<DigitalInfoLine> lines = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        lines.add(DigitalInfoLine.read(buf));
                    }
                    return new DigitalInfoSync(kind, online, pos, dim, lines);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DigitalUseLine(int itemId, int pulled, int crafted) {
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(itemId);
            buf.writeVarInt(pulled);
            buf.writeVarInt(crafted);
        }

        public static DigitalUseLine read(RegistryFriendlyByteBuf buf) {
            return new DigitalUseLine(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** Shown on top of an open manage window. The vanilla action bar sits behind that window. */
    public record Notice(Component text) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = payloadType("notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeJsonWithCodec(ComponentSerialization.CODEC, value.text == null ? Component.empty() : value.text),
                buf -> new Notice(buf.readJsonWithCodec(ComponentSerialization.CODEC)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DigitalUse(java.util.List<DigitalUseLine> lines) implements CustomPacketPayload {
        public static final Type<DigitalUse> TYPE = payloadType("digital_use");
        public static final StreamCodec<RegistryFriendlyByteBuf, DigitalUse> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    java.util.List<DigitalUseLine> lines = value.lines == null ? java.util.List.of() : value.lines;
                    buf.writeVarInt(lines.size());
                    for (DigitalUseLine line : lines) {
                        line.write(buf);
                    }
                },
                buf -> {
                    int count = buf.readVarInt();
                    java.util.List<DigitalUseLine> lines = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        lines.add(DigitalUseLine.read(buf));
                    }
                    return new DigitalUse(lines);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveSession(boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean placeNbt, boolean placeSettings, boolean placeAll) implements CustomPacketPayload {
        public static final Type<SaveSession> TYPE = payloadType("save_session");
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveSession> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.placeFluids);
                    buf.writeBoolean(value.voidBlocks);
                    buf.writeBoolean(value.voidFluids);
                    buf.writeBoolean(value.voidNbt);
                    buf.writeBoolean(value.placeNbt);
                    buf.writeBoolean(value.placeSettings);
                    buf.writeBoolean(value.placeAll);
                },
                buf -> new SaveSession(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MirrorPlace(BlockPos pos, int face, double x, double y, double z, boolean mirror, BlockPos anchor, int anchorFace, int pattern, boolean shapes, boolean enabled, boolean copyNbt, boolean copySettings, boolean copyAll, boolean replace, int axes) implements CustomPacketPayload {
        public static final Type<MirrorPlace> TYPE = payloadType("mirror_place");
        public static final StreamCodec<RegistryFriendlyByteBuf, MirrorPlace> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), MirrorPlace::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeBoolean(mirror);
            buf.writeBlockPos(anchor);
            buf.writeVarInt(anchorFace);
            buf.writeVarInt(pattern);
            buf.writeBoolean(shapes);
            buf.writeBoolean(enabled);
            buf.writeBoolean(copyNbt);
            buf.writeBoolean(copySettings);
            buf.writeBoolean(copyAll);
            buf.writeBoolean(replace);
            buf.writeVarInt(axes);
        }

        public static MirrorPlace read(RegistryFriendlyByteBuf buf) {
            return new MirrorPlace(buf.readBlockPos(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MirrorMine(BlockPos pos, int stateId, BlockPos anchor, int anchorFace, int pattern, int axes, boolean any, boolean voidBlocks) implements CustomPacketPayload {
        public static final Type<MirrorMine> TYPE = payloadType("mirror_mine");
        public static final StreamCodec<RegistryFriendlyByteBuf, MirrorMine> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), MirrorMine::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(stateId);
            buf.writeBlockPos(anchor);
            buf.writeVarInt(anchorFace);
            buf.writeVarInt(pattern);
            buf.writeVarInt(axes);
            buf.writeBoolean(any);
            buf.writeBoolean(voidBlocks);
        }

        public static MirrorMine read(RegistryFriendlyByteBuf buf) {
            return new MirrorMine(buf.readBlockPos(), buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShapePlace(BlockPos pos, int face, boolean useAnchor, boolean anchorSet, BlockPos anchor, int kind, int size, int height, int thickness, int plane, int yaw, boolean hollow, boolean flipVertical, boolean replace, boolean voidBlocks, boolean force, int fillMode, int orderBatch, String materials, String supplyOrder) implements CustomPacketPayload {
        public static final Type<ShapePlace> TYPE = payloadType("shape_place");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapePlace> STREAM_CODEC = StreamCodec.of((buf, value) -> value.write(buf), ShapePlace::read);

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face);
            buf.writeBoolean(useAnchor);
            buf.writeBoolean(anchorSet);
            buf.writeBlockPos(anchor);
            buf.writeVarInt(kind);
            buf.writeVarInt(size);
            buf.writeVarInt(height);
            buf.writeVarInt(thickness);
            buf.writeVarInt(plane);
            buf.writeVarInt(yaw);
            buf.writeBoolean(hollow);
            buf.writeBoolean(flipVertical);
            buf.writeBoolean(replace);
            buf.writeBoolean(voidBlocks);
            buf.writeBoolean(force);
            buf.writeVarInt(fillMode);
            buf.writeVarInt(orderBatch);
            buf.writeUtf(materials == null ? "" : materials, 512);
            buf.writeUtf(supplyOrder == null ? "" : supplyOrder, 64);
        }

        public static ShapePlace read(RegistryFriendlyByteBuf buf) {
            return new ShapePlace(
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(512),
                    buf.readUtf(64));
        }

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
