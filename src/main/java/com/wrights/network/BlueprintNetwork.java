package com.wrights.network;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.wrights.blueprint.BlueprintActions;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.job.BuildJob;
import com.wrights.job.JobManager;
import com.wrights.schematic.Requirements;
import com.wrights.schematic.Schematic;

public final class BlueprintNetwork {
    private BlueprintNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BlueprintPayloads.LibraryRequest.TYPE, BlueprintPayloads.LibraryRequest.STREAM_CODEC, BlueprintNetwork::library);
        registrar.playToServer(BlueprintPayloads.ShelfEdit.TYPE, BlueprintPayloads.ShelfEdit.STREAM_CODEC, BlueprintNetwork::shelf);
        registrar.playToServer(BlueprintPayloads.StartCapture.TYPE, BlueprintPayloads.StartCapture.STREAM_CODEC, BlueprintNetwork::startCapture);
        registrar.playToServer(BlueprintPayloads.CaptureUpdate.TYPE, BlueprintPayloads.CaptureUpdate.STREAM_CODEC, BlueprintNetwork::update);
        registrar.playToServer(BlueprintPayloads.CapturePick.TYPE, BlueprintPayloads.CapturePick.STREAM_CODEC, BlueprintNetwork::pick);
        registrar.playToServer(BlueprintPayloads.CaptureConfirm.TYPE, BlueprintPayloads.CaptureConfirm.STREAM_CODEC, BlueprintNetwork::confirm);
        registrar.playToServer(BlueprintPayloads.CaptureCancel.TYPE, BlueprintPayloads.CaptureCancel.STREAM_CODEC, BlueprintNetwork::cancel);
        registrar.playToServer(BlueprintPayloads.Resume.TYPE, BlueprintPayloads.Resume.STREAM_CODEC, BlueprintNetwork::resume);
        registrar.playToServer(BlueprintPayloads.DeleteBlueprint.TYPE, BlueprintPayloads.DeleteBlueprint.STREAM_CODEC, BlueprintNetwork::delete);
        registrar.playToServer(BlueprintPayloads.RenameBlueprint.TYPE, BlueprintPayloads.RenameBlueprint.STREAM_CODEC, BlueprintNetwork::rename);
        registrar.playToServer(BlueprintPayloads.DuplicateBlueprint.TYPE, BlueprintPayloads.DuplicateBlueprint.STREAM_CODEC, BlueprintNetwork::duplicate);
        registrar.playToServer(BlueprintPayloads.PreviewBlueprint.TYPE, BlueprintPayloads.PreviewBlueprint.STREAM_CODEC, BlueprintNetwork::previewBlueprint);
        registrar.playToServer(BlueprintPayloads.MaterialsRequest.TYPE, BlueprintPayloads.MaterialsRequest.STREAM_CODEC, BlueprintNetwork::materialsRequest);
        registrar.playToServer(BlueprintPayloads.BeginEdit.TYPE, BlueprintPayloads.BeginEdit.STREAM_CODEC, BlueprintNetwork::edit);
        registrar.playToServer(BlueprintPayloads.FinishEdit.TYPE, BlueprintPayloads.FinishEdit.STREAM_CODEC, BlueprintNetwork::finishEdit);
        registrar.playToServer(BlueprintPayloads.BeginBuild.TYPE, BlueprintPayloads.BeginBuild.STREAM_CODEC, BlueprintNetwork::build);
        registrar.playToServer(BlueprintPayloads.LaunchBuild.TYPE, BlueprintPayloads.LaunchBuild.STREAM_CODEC, BlueprintNetwork::launch);
        registrar.playToServer(BlueprintPayloads.StopJob.TYPE, BlueprintPayloads.StopJob.STREAM_CODEC, BlueprintNetwork::stop);
        registrar.playToServer(BlueprintPayloads.SaveSession.TYPE, BlueprintPayloads.SaveSession.STREAM_CODEC, BlueprintNetwork::saveSession);
        registrar.playToServer(BlueprintPayloads.JobControl.TYPE, BlueprintPayloads.JobControl.STREAM_CODEC, BlueprintNetwork::control);
        registrar.playToServer(BlueprintPayloads.SetPace.TYPE, BlueprintPayloads.SetPace.STREAM_CODEC, BlueprintNetwork::pace);
        registrar.playToServer(BlueprintPayloads.SetAssign.TYPE, BlueprintPayloads.SetAssign.STREAM_CODEC, BlueprintNetwork::assign);
        registrar.playToServer(BlueprintPayloads.SetJobDrones.TYPE, BlueprintPayloads.SetJobDrones.STREAM_CODEC, BlueprintNetwork::jobDrones);
        registrar.playToServer(BlueprintPayloads.MoveDrone.TYPE, BlueprintPayloads.MoveDrone.STREAM_CODEC, BlueprintNetwork::moveDrone);
        registrar.playToServer(BlueprintPayloads.HangarEdit.TYPE, BlueprintPayloads.HangarEdit.STREAM_CODEC, BlueprintNetwork::hangar);
        registrar.playToServer(BlueprintPayloads.HangarBatch.TYPE, BlueprintPayloads.HangarBatch.STREAM_CODEC, BlueprintNetwork::hangarBatch);
        registrar.playToServer(BlueprintPayloads.BindSupply.TYPE, BlueprintPayloads.BindSupply.STREAM_CODEC, BlueprintNetwork::bindSupply);
        registrar.playToServer(BlueprintPayloads.SetAnchor.TYPE, BlueprintPayloads.SetAnchor.STREAM_CODEC, BlueprintNetwork::anchor);
        registrar.playToServer(BlueprintPayloads.EditCell.TYPE, BlueprintPayloads.EditCell.STREAM_CODEC, BlueprintNetwork::editCell);
        registrar.playToServer(BlueprintPayloads.EditLayer.TYPE, BlueprintPayloads.EditLayer.STREAM_CODEC, BlueprintNetwork::editLayer);
        registrar.playToServer(BlueprintPayloads.TransformBlueprint.TYPE, BlueprintPayloads.TransformBlueprint.STREAM_CODEC, BlueprintNetwork::transform);
        registrar.playToServer(BlueprintPayloads.RecaptureBlueprint.TYPE, BlueprintPayloads.RecaptureBlueprint.STREAM_CODEC, BlueprintNetwork::recapture);
        registrar.playToServer(BlueprintPayloads.RequestBlueprintItems.TYPE, BlueprintPayloads.RequestBlueprintItems.STREAM_CODEC, BlueprintNetwork::requestBlueprintItems);
        registrar.playToServer(BlueprintPayloads.RequestSlipLibrary.TYPE, BlueprintPayloads.RequestSlipLibrary.STREAM_CODEC, BlueprintNetwork::requestSlipLibrary);
        registrar.playToServer(BlueprintPayloads.WriteBlueprintItem.TYPE, BlueprintPayloads.WriteBlueprintItem.STREAM_CODEC, BlueprintNetwork::writeBlueprintItem);
        registrar.playToServer(BlueprintPayloads.LoadBlueprintItem.TYPE, BlueprintPayloads.LoadBlueprintItem.STREAM_CODEC, BlueprintNetwork::loadBlueprintItem);
        registrar.playToServer(BlueprintPayloads.RequestFolderItems.TYPE, BlueprintPayloads.RequestFolderItems.STREAM_CODEC, BlueprintNetwork::requestFolderItems);
        registrar.playToServer(BlueprintPayloads.WriteFolderItem.TYPE, BlueprintPayloads.WriteFolderItem.STREAM_CODEC, BlueprintNetwork::writeFolderItem);
        registrar.playToServer(BlueprintPayloads.LoadFolderItem.TYPE, BlueprintPayloads.LoadFolderItem.STREAM_CODEC, BlueprintNetwork::loadFolderItem);
        registrar.playToServer(BlueprintPayloads.CopyClipboard.TYPE, BlueprintPayloads.CopyClipboard.STREAM_CODEC, BlueprintNetwork::copyClipboard);
        registrar.playToServer(BlueprintPayloads.ClipboardPreview.TYPE, BlueprintPayloads.ClipboardPreview.STREAM_CODEC, BlueprintNetwork::previewClipboard);
        registrar.playToServer(BlueprintPayloads.ClipboardImport.TYPE, BlueprintPayloads.ClipboardImport.STREAM_CODEC, BlueprintNetwork::importClipboard);

        registrar.playToClient(BlueprintPayloads.LibrarySync.TYPE, BlueprintPayloads.LibrarySync.STREAM_CODEC, BlueprintNetwork::clientLibrary);
        registrar.playToClient(BlueprintPayloads.CaptureSync.TYPE, BlueprintPayloads.CaptureSync.STREAM_CODEC, BlueprintNetwork::clientCapture);
        registrar.playToClient(BlueprintPayloads.Preview.TYPE, BlueprintPayloads.Preview.STREAM_CODEC, BlueprintNetwork::clientPreview);
        registrar.playToClient(BlueprintPayloads.Materials.TYPE, BlueprintPayloads.Materials.STREAM_CODEC, BlueprintNetwork::clientMaterials);
        registrar.playToClient(BlueprintPayloads.CaptureFinished.TYPE, BlueprintPayloads.CaptureFinished.STREAM_CODEC, BlueprintNetwork::clientFinished);
        registrar.playToClient(BlueprintPayloads.JobHologram.TYPE, BlueprintPayloads.JobHologram.STREAM_CODEC, BlueprintNetwork::clientJobHologram);
        registrar.playToClient(BlueprintPayloads.JobSync.TYPE, BlueprintPayloads.JobSync.STREAM_CODEC, BlueprintNetwork::clientJobs);
        registrar.playToClient(BlueprintPayloads.SupplySync.TYPE, BlueprintPayloads.SupplySync.STREAM_CODEC, BlueprintNetwork::clientSupply);
        registrar.playToClient(BlueprintPayloads.UndoPrompt.TYPE, BlueprintPayloads.UndoPrompt.STREAM_CODEC, BlueprintNetwork::clientUndoPrompt);
        registrar.playToClient(BlueprintPayloads.JobDone.TYPE, BlueprintPayloads.JobDone.STREAM_CODEC, BlueprintNetwork::clientJobDone);
        registrar.playToClient(BlueprintPayloads.BlueprintItemList.TYPE, BlueprintPayloads.BlueprintItemList.STREAM_CODEC, BlueprintNetwork::clientBlueprintItems);
        registrar.playToClient(BlueprintPayloads.FolderItemList.TYPE, BlueprintPayloads.FolderItemList.STREAM_CODEC, BlueprintNetwork::clientFolderItems);
        registrar.playToClient(BlueprintPayloads.ClipboardOffer.TYPE, BlueprintPayloads.ClipboardOffer.STREAM_CODEC, BlueprintNetwork::clientClipboardOffer);
        registrar.playToClient(BlueprintPayloads.ClipboardView.TYPE, BlueprintPayloads.ClipboardView.STREAM_CODEC, BlueprintNetwork::clientClipboardView);
    }

    public static void syncLibrary(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.LibrarySync(
                BlueprintActions.summaries(player),
                BlueprintActions.busy(player),
                BlueprintActions.folders(player),
                BlueprintActions.openFolders(player)));
    }

    public static void syncCapture(ServerPlayer player) {
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        boolean hasA = session.cornerA() != null;
        boolean hasB = session.cornerB() != null;
        boolean hasEdit = session.editingId() != null;
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.CaptureSync(
                session.mode().ordinal(),
                session.generation(),
                session.seq(),
                hasA,
                hasA ? session.cornerA() : net.minecraft.core.BlockPos.ZERO,
                hasB,
                hasB ? session.cornerB() : net.minecraft.core.BlockPos.ZERO,
                session.name(),
                hasEdit,
                hasEdit ? session.editingId() : Util.NIL_UUID));
    }

    public static void preview(ServerPlayer player, Schematic schematic, boolean forBuild, boolean openEditor) {
        PacketDistributor.sendToPlayer(player, BlueprintActions.previewOf(schematic, forBuild, openEditor));
    }

    public static void materials(ServerPlayer player, java.util.UUID id, boolean shortage, java.util.List<Requirements.Cost> costs) {
        java.util.List<BlueprintPayloads.MaterialLine> lines = new java.util.ArrayList<>(costs.size());
        for (Requirements.Cost cost : costs) {
            lines.add(new BlueprintPayloads.MaterialLine(cost.itemId(), cost.count()));
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.Materials(id, shortage, lines));
    }

    public static void finished(ServerPlayer player, UUID id, boolean saved, boolean stay) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.CaptureFinished(id, saved, stay));
    }

    private static void library(BlueprintPayloads.LibraryRequest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            BlueprintActions.requestLibrary(player);
            BlueprintNetwork.syncJobs(player);
        });
    }

    private static void shelf(BlueprintPayloads.ShelfEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.shelf(player, payload));
    }

    private static void startCapture(BlueprintPayloads.StartCapture payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.startCapture(player));
    }

    private static void pick(BlueprintPayloads.CapturePick payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.pick(player, payload.pos(), payload.action()));
    }

    private static void update(BlueprintPayloads.CaptureUpdate payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.updateCapture(
                player,
                payload.generation(),
                payload.seq(),
                payload.hasA(),
                payload.cornerA(),
                payload.hasB(),
                payload.cornerB(),
                payload.name()));
    }

    private static void confirm(BlueprintPayloads.CaptureConfirm payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.confirm(player, payload.name(), payload.fluids(), payload.buildAfter()));
    }

    private static void cancel(BlueprintPayloads.CaptureCancel payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.cancel(player));
    }

    private static void resume(BlueprintPayloads.Resume payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.resume(player, payload.mode(), payload.buildId()));
    }

    private static void delete(BlueprintPayloads.DeleteBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.delete(player, payload.id()));
    }

    private static void rename(BlueprintPayloads.RenameBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.rename(player, payload.id(), payload.name()));
    }

    private static void duplicate(BlueprintPayloads.DuplicateBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.duplicate(player, payload.id(), payload.copyId()));
    }

    private static void previewBlueprint(BlueprintPayloads.PreviewBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.previewBlueprint(player, payload.id()));
    }

    private static void materialsRequest(BlueprintPayloads.MaterialsRequest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.sendMaterials(player, payload.id(), payload.clipX(), payload.clipY(), payload.clipZ(), payload.placeFluids()));
    }

    private static void edit(BlueprintPayloads.BeginEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.beginEdit(player, payload.id()));
    }

    private static void finishEdit(BlueprintPayloads.FinishEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.finishEdit(player, payload.keep()));
    }

    private static void build(BlueprintPayloads.BeginBuild payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.beginBuild(player, payload.id()));
    }

    private static void launch(BlueprintPayloads.LaunchBuild payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.launch(
                player,
                payload.id(),
                payload.origin(),
                payload.sizeX(),
                payload.sizeY(),
                payload.sizeZ(),
                payload.pivotX(),
                payload.pivotY(),
                payload.pivotZ(),
                payload.drones(),
                payload.placeFluids(),
                payload.voidBlocks(),
                payload.voidFluids(),
                payload.voidNbt()));
    }

    private static void editCell(BlueprintPayloads.EditCell payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.editCell(player, payload.id(), payload.x(), payload.y(), payload.z(), payload.stateId(), payload.remove()));
    }

    private static void editLayer(BlueprintPayloads.EditLayer payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.editLayer(player, payload.id(), payload.operation(), payload.from(), payload.to()));
    }

    private static void anchor(BlueprintPayloads.SetAnchor payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.setAnchor(player, payload.id(), payload.x(), payload.y(), payload.z()));
    }

    private static void transform(BlueprintPayloads.TransformBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.transform(player, payload.id(), payload.operation()));
    }

    private static void recapture(BlueprintPayloads.RecaptureBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.recapture(player, payload.id()));
    }

    private static void stop(BlueprintPayloads.StopJob payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            BlueprintActions.stop(player);
            syncJobs(player);
        });
    }

    private static void saveSession(BlueprintPayloads.SaveSession payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.saveSession(player));
    }

    private static void control(BlueprintPayloads.JobControl payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> JobManager.control(player, payload.id(), payload.action()));
    }

    private static void pace(BlueprintPayloads.SetPace payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (player.getAbilities().instabuild && !player.isSpectator()) {
                BlueprintSessions.get(player).setInstantJobs(payload.instant());
            } else {
                BlueprintSessions.get(player).setInstantJobs(false);
            }
        });
    }

    private static void jobDrones(BlueprintPayloads.SetJobDrones payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.job.JobManager.setJobDrones(player, payload.id(), payload.unit(), payload.add()));
    }

    private static void moveDrone(BlueprintPayloads.MoveDrone payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.job.JobManager.moveDrone(player, payload.unit(), payload.job()));
    }

    private static void assign(BlueprintPayloads.SetAssign payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            com.wrights.drone.Swarm swarm = com.wrights.drone.Wrightports.swarm(player);
            com.wrights.drone.Wrightports.replaceWorn(player, swarm.withAssign(payload.count()));
        });
    }

    private static void hangarBatch(BlueprintPayloads.HangarBatch payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (player.containerMenu instanceof com.wrights.menu.HangarMenu menu && menu.pos().equals(payload.pos())) {
                com.wrights.drone.HangarOps.batch(player, menu, payload.action(), payload.value(), payload.units());
            }
        });
    }

    private static void hangar(BlueprintPayloads.HangarEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (player.containerMenu instanceof com.wrights.menu.HangarMenu menu && menu.pos().equals(payload.pos())) {
                com.wrights.drone.HangarOps.apply(player, menu, payload.action(), payload.index());
            } else if (player.containerMenu instanceof com.wrights.menu.WrightportMenu menu && menu.pos().equals(payload.pos())) {
                com.wrights.drone.WrightportOps.apply(player, menu, payload.action(), payload.index());
            }
        });
    }

    private static void bindSupply(BlueprintPayloads.BindSupply payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> JobManager.bindSupply(player, payload.pos(), payload.clear()));
    }

    private static void clientLibrary(BlueprintPayloads.LibrarySync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyLibrary(payload));
    }

    private static void clientCapture(BlueprintPayloads.CaptureSync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyCapture(payload));
    }

    private static void clientMaterials(BlueprintPayloads.Materials payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.MaterialsScreen.show(payload));
    }

    private static void clientPreview(BlueprintPayloads.Preview payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyPreview(payload));
    }

    private static void clientFinished(BlueprintPayloads.CaptureFinished payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyFinished(payload));
    }

    private static void clientJobHologram(BlueprintPayloads.JobHologram payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyJobHologram(payload));
    }

    private static void clientJobs(BlueprintPayloads.JobSync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyJobs(payload));
    }

    private static void clientSupply(BlueprintPayloads.SupplySync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applySupply(payload));
    }

    private static void clientJobDone(BlueprintPayloads.JobDone payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.showJobDone(payload.name(), payload.seconds()));
    }

    private static void clientUndoPrompt(BlueprintPayloads.UndoPrompt payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.openUndoPrompt(payload));
    }

    public static void sendBlueprintItems(ServerPlayer player, java.util.List<BlueprintPayloads.BlueprintItemLine> lines) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.BlueprintItemList(lines));
    }

    private static void requestBlueprintItems(BlueprintPayloads.RequestBlueprintItems payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.BlueprintSlips.sendList((ServerPlayer) context.player()));
    }

    private static void requestSlipLibrary(BlueprintPayloads.RequestSlipLibrary payload, IPayloadContext context) {
        context.enqueueWork(() -> syncLibrary((ServerPlayer) context.player()));
    }

    private static void writeBlueprintItem(BlueprintPayloads.WriteBlueprintItem payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.BlueprintSlips.write((ServerPlayer) context.player(), payload.slot(), payload.schematicId()));
    }

    private static void loadBlueprintItem(BlueprintPayloads.LoadBlueprintItem payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.BlueprintSlips.load((ServerPlayer) context.player(), payload.slot(), payload.mode(), payload.name()));
    }

    public static void sendClipboard(ServerPlayer player, byte[] data, BlueprintPayloads.ClipboardStatus status) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.ClipboardOffer(data, status));
    }

    public static void sendClipboardStatus(ServerPlayer player, BlueprintPayloads.ClipboardStatus status) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.ClipboardView(status));
    }

    private static void copyClipboard(BlueprintPayloads.CopyClipboard payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.ClipboardExchange.copy((ServerPlayer) context.player(), payload.id(), payload.folder()));
    }

    private static void previewClipboard(BlueprintPayloads.ClipboardPreview payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.ClipboardExchange.preview((ServerPlayer) context.player(), payload.data()));
    }

    private static void importClipboard(BlueprintPayloads.ClipboardImport payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.ClipboardExchange.importClip((ServerPlayer) context.player(), payload.data(), payload.mode(), payload.name()));
    }

    private static void clientClipboardOffer(BlueprintPayloads.ClipboardOffer payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            com.wrights.client.BlueprintClipboard.store(payload.data());
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.wrights.client.ImportExportScreen screen) {
                screen.apply(payload.status());
            }
        });
    }

    private static void clientClipboardView(BlueprintPayloads.ClipboardView payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.wrights.client.ImportExportScreen screen) {
                screen.apply(payload.status());
            }
        });
    }

    public static void sendFolderItems(ServerPlayer player, java.util.List<BlueprintPayloads.FolderItemLine> lines) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.FolderItemList(lines));
    }

    private static void requestFolderItems(BlueprintPayloads.RequestFolderItems payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.BlueprintFolders.sendList((ServerPlayer) context.player()));
    }

    private static void writeFolderItem(BlueprintPayloads.WriteFolderItem payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.BlueprintFolders.write((ServerPlayer) context.player(), payload.slot(), payload.folderId()));
    }

    private static void loadFolderItem(BlueprintPayloads.LoadFolderItem payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.item.BlueprintFolders.load((ServerPlayer) context.player(), payload.slot(), payload.mode(), payload.name()));
    }

    private static void clientFolderItems(BlueprintPayloads.FolderItemList payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.BlueprintSlipScreens.applyFolders(payload.lines()));
    }

    private static void clientBlueprintItems(BlueprintPayloads.BlueprintItemList payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.BlueprintSlipScreens.applyItems(payload.lines()));
    }

    public static void syncJobs(ServerPlayer player) {
        com.wrights.schematic.SchematicLibrary library = com.wrights.schematic.SchematicLibrary.get(player.getServer());
        com.wrights.job.JobManager.bindCrewIds(player, library);
        java.util.List<BuildJob> jobs = new java.util.ArrayList<>(library.jobsFor(player.getUUID()));
        jobs.sort(java.util.Comparator.comparing(BuildJob::finished).thenComparing(java.util.Comparator.comparingInt(BuildJob::historyOrder).reversed()));
        java.util.List<BlueprintPayloads.JobLine> lines = new java.util.ArrayList<>();
        for (BuildJob job : jobs) {
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
            int done = job.undoing() ? job.undoTotal() - job.refundCount() : job.cursor();
            int total = job.undoing() ? Math.max(1, job.undoTotal()) : job.total();
            lines.add(new BlueprintPayloads.JobLine(
                    job.id(),
                    name,
                    done,
                    total,
                    job.skipped(),
                    job.paused(),
                    job.kind() == BuildJob.Kind.COPY,
                    job.finished(),
                    job.canUndo(),
                    job.undoing(),
                    job.stopped(),
                    job.undone(),
                    job.dimension().location().toString(),
                    job.anchor(),
                    job.min(),
                    job.boxSizeX(),
                    job.boxSizeY(),
                    job.boxSizeZ(),
                    job.crewUnits().size(),
                    job.crewUnits(),
                    job.workedTicks()));
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.JobSync(lines));
    }

    public static void syncSupply(ServerPlayer player) {
        BlockPos pos = BlueprintSessions.get(player).supplyPos();
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.SupplySync(pos != null, pos == null ? BlockPos.ZERO : pos));
    }

    public static void jobDone(ServerPlayer player, String name, int seconds) {
        String title = name == null || name.isBlank() ? "Build" : name;
        if (title.length() > 48) {
            title = title.substring(0, 48);
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.JobDone(title, Math.max(0, seconds)));
    }

    public static void undoPrompt(ServerPlayer player, java.util.UUID id, int extra, int drop) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.UndoPrompt(id, extra, drop));
    }

    public static void showJobHologram(ServerPlayer player, BuildJob job, Schematic schematic) {
        if (job.finished() || schematic == null) {
            return;
        }
        java.util.List<BlueprintPayloads.Bit> bits = new java.util.ArrayList<>();
        if (schematic != null && schematic.blockCount() <= com.wrights.WrightsConfig.maxPreviewBlocks()) {
            for (Schematic.Entry entry : schematic.entries()) {
                if (job.outsideClip(entry)) {
                    continue;
                }
                bits.add(new BlueprintPayloads.Bit(entry.x(), entry.y(), entry.z(), net.minecraft.world.level.block.Block.getId(entry.state())));
            }
        }
        int clipX = job.clipX() == Integer.MAX_VALUE ? schematic.sizeX() : job.clipX();
        int clipY = job.clipY() == Integer.MAX_VALUE ? schematic.sizeY() : job.clipY();
        int clipZ = job.clipZ() == Integer.MAX_VALUE ? schematic.sizeZ() : job.clipZ();
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.JobHologram(job.id(), job.min(), clipX, clipY, clipZ, false, bits));
    }

    public static void clearJobHologram(ServerPlayer player, java.util.UUID jobId) {
        PacketDistributor.sendToPlayer(player, BlueprintPayloads.JobHologram.clear(jobId));
    }

    public static void syncJobHolograms(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, BlueprintPayloads.JobHologram.clearAll());
        com.wrights.schematic.SchematicLibrary library = com.wrights.schematic.SchematicLibrary.get(player.getServer());
        for (com.wrights.job.BuildJob job : library.jobsFor(player.getUUID())) {
            if (job.finished() || job.kind() != com.wrights.job.BuildJob.Kind.PASTE || job.schematicId() == null) {
                continue;
            }
            library.schematic(job.schematicId()).ifPresent(schematic -> showJobHologram(player, job, schematic));
        }
    }
}
