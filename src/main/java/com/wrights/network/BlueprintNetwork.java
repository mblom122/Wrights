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
        registrar.playToServer(BlueprintPayloads.LibraryMove.TYPE, BlueprintPayloads.LibraryMove.STREAM_CODEC, BlueprintNetwork::libraryMove);
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
        registrar.playToServer(BlueprintPayloads.SetPortOrder.TYPE, BlueprintPayloads.SetPortOrder.STREAM_CODEC, BlueprintNetwork::portOrder);
        registrar.playToServer(BlueprintPayloads.SetAssign.TYPE, BlueprintPayloads.SetAssign.STREAM_CODEC, BlueprintNetwork::assign);
        registrar.playToServer(BlueprintPayloads.DeployAsk.TYPE, BlueprintPayloads.DeployAsk.STREAM_CODEC, BlueprintNetwork::deployAsk);
        registrar.playToServer(BlueprintPayloads.SetJobDrones.TYPE, BlueprintPayloads.SetJobDrones.STREAM_CODEC, BlueprintNetwork::jobDrones);
        registrar.playToServer(BlueprintPayloads.MoveDrone.TYPE, BlueprintPayloads.MoveDrone.STREAM_CODEC, BlueprintNetwork::moveDrone);
        registrar.playToServer(BlueprintPayloads.HangarEdit.TYPE, BlueprintPayloads.HangarEdit.STREAM_CODEC, BlueprintNetwork::hangar);
        registrar.playToServer(BlueprintPayloads.HangarBatch.TYPE, BlueprintPayloads.HangarBatch.STREAM_CODEC, BlueprintNetwork::hangarBatch);
        registrar.playToServer(BlueprintPayloads.PortEdit.TYPE, BlueprintPayloads.PortEdit.STREAM_CODEC, BlueprintNetwork::portEdit);
        registrar.playToServer(BlueprintPayloads.PortBatch.TYPE, BlueprintPayloads.PortBatch.STREAM_CODEC, BlueprintNetwork::portBatch);
        registrar.playToServer(BlueprintPayloads.PortHold.TYPE, BlueprintPayloads.PortHold.STREAM_CODEC, BlueprintNetwork::portHold);
        registrar.playToServer(BlueprintPayloads.PlacedPortsAsk.TYPE, BlueprintPayloads.PlacedPortsAsk.STREAM_CODEC, BlueprintNetwork::placedPortsAsk);
        registrar.playToServer(BlueprintPayloads.PlacedSwarmAsk.TYPE, BlueprintPayloads.PlacedSwarmAsk.STREAM_CODEC, BlueprintNetwork::placedSwarmAsk);
        registrar.playToServer(BlueprintPayloads.PlacedVisit.TYPE, BlueprintPayloads.PlacedVisit.STREAM_CODEC, BlueprintNetwork::placedVisit);
        registrar.playToServer(BlueprintPayloads.PlacedEdit.TYPE, BlueprintPayloads.PlacedEdit.STREAM_CODEC, BlueprintNetwork::placedEdit);
        registrar.playToServer(BlueprintPayloads.PlacedBatch.TYPE, BlueprintPayloads.PlacedBatch.STREAM_CODEC, BlueprintNetwork::placedBatch);
        registrar.playToServer(BlueprintPayloads.RenameWrightport.TYPE, BlueprintPayloads.RenameWrightport.STREAM_CODEC, BlueprintNetwork::renameWrightport);
        registrar.playToServer(BlueprintPayloads.RenameWright.TYPE, BlueprintPayloads.RenameWright.STREAM_CODEC, BlueprintNetwork::renameWright);
        registrar.playToServer(BlueprintPayloads.BindSupply.TYPE, BlueprintPayloads.BindSupply.STREAM_CODEC, BlueprintNetwork::bindSupply);
        registrar.playToServer(BlueprintPayloads.BindPortChest.TYPE, BlueprintPayloads.BindPortChest.STREAM_CODEC, BlueprintNetwork::bindPortChest);
        registrar.playToServer(BlueprintPayloads.BindDigital.TYPE, BlueprintPayloads.BindDigital.STREAM_CODEC, BlueprintNetwork::bindDigital);
        registrar.playToServer(BlueprintPayloads.DigitalInfoRequest.TYPE, BlueprintPayloads.DigitalInfoRequest.STREAM_CODEC, BlueprintNetwork::digitalInfo);
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
        registrar.playToClient(BlueprintPayloads.PlacedPorts.TYPE, BlueprintPayloads.PlacedPorts.STREAM_CODEC, BlueprintNetwork::clientPlacedPorts);
        registrar.playToClient(BlueprintPayloads.PortGlow.TYPE, BlueprintPayloads.PortGlow.STREAM_CODEC, BlueprintNetwork::clientPortGlow);
        registrar.playToClient(BlueprintPayloads.PlacedSwarm.TYPE, BlueprintPayloads.PlacedSwarm.STREAM_CODEC, BlueprintNetwork::clientPlacedSwarm);
        registrar.playToClient(BlueprintPayloads.DeployPool.TYPE, BlueprintPayloads.DeployPool.STREAM_CODEC, BlueprintNetwork::clientDeployPool);
        registrar.playToClient(BlueprintPayloads.SupplySync.TYPE, BlueprintPayloads.SupplySync.STREAM_CODEC, BlueprintNetwork::clientSupply);
        registrar.playToClient(BlueprintPayloads.DigitalSync.TYPE, BlueprintPayloads.DigitalSync.STREAM_CODEC, BlueprintNetwork::clientDigital);
        registrar.playToClient(BlueprintPayloads.DigitalInfoSync.TYPE, BlueprintPayloads.DigitalInfoSync.STREAM_CODEC, BlueprintNetwork::clientDigitalInfo);
        registrar.playToClient(BlueprintPayloads.UndoPrompt.TYPE, BlueprintPayloads.UndoPrompt.STREAM_CODEC, BlueprintNetwork::clientUndoPrompt);
        registrar.playToClient(BlueprintPayloads.JobDone.TYPE, BlueprintPayloads.JobDone.STREAM_CODEC, BlueprintNetwork::clientJobDone);
        registrar.playToClient(BlueprintPayloads.DockToast.TYPE, BlueprintPayloads.DockToast.STREAM_CODEC, BlueprintNetwork::clientDockToast);
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
                BlueprintActions.openFolders(player),
                BlueprintSessions.get(player).libraryGlobal()));
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
            lines.add(new BlueprintPayloads.MaterialLine(cost.itemId(), cost.count(), cost.player(), cost.supply(), cost.digital()));
        }
        boolean digital = com.wrights.storage.ItemSource.linkOf(player).bound();
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.Materials(id, shortage, digital, lines));
    }

    public static void finished(ServerPlayer player, UUID id, boolean saved, boolean stay) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.CaptureFinished(id, saved, stay));
    }

    private static void library(BlueprintPayloads.LibraryRequest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            BlueprintActions.requestLibrary(player, payload.global());
            BlueprintNetwork.syncJobs(player);
        });
    }

    private static void libraryMove(BlueprintPayloads.LibraryMove payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.moveLibrary(player, payload.id(), payload.toGlobal()));
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
        context.enqueueWork(() -> BlueprintActions.confirm(
                player,
                payload.name(),
                payload.fluids(),
                payload.nbtBlocks(),
                payload.nbtSettings(),
                payload.nbtAll(),
                payload.buildAfter(),
                payload.turns()));
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
                payload.voidNbt(),
                payload.force(),
                payload.playerFirst(),
                payload.queue()));
    }

    private static void editCell(BlueprintPayloads.EditCell payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.editCell(player, payload.id(), payload.x(), payload.y(), payload.z(), payload.stateId(), payload.remove()));
    }

    private static void editLayer(BlueprintPayloads.EditLayer payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.editLayer(player, payload.id(), payload.operation(), payload.from(), payload.to(), payload.axis()));
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
        context.enqueueWork(() -> BlueprintActions.saveSession(player, payload.placeFluids(), payload.voidBlocks(), payload.voidFluids(), payload.voidNbt()));
    }

    private static void control(BlueprintPayloads.JobControl payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> JobManager.control(player, payload.id(), payload.action()));
    }

    private static void portOrder(BlueprintPayloads.SetPortOrder payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintSessions.get(player).setPortPlayerFirst(payload.playerFirst()));
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

    private static void deployAsk(BlueprintPayloads.DeployAsk payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DeployPool(
                com.wrights.job.JobManager.freeWrightCount(player),
                com.wrights.job.JobManager.storedWrightCount(player))));
    }

    private static void clientDeployPool(BlueprintPayloads.DeployPool payload, IPayloadContext context) {
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyPool(payload.free(), payload.stored()));
    }

    private static void hangarBatch(BlueprintPayloads.HangarBatch payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (player.containerMenu instanceof com.wrights.menu.WrightportMenu menu && menu.pos().equals(payload.pos())) {
                com.wrights.drone.WrightportOps.batch(player, menu, payload.action(), payload.value(), payload.units());
            }
        });
    }

    private static void portEdit(BlueprintPayloads.PortEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.HangarOps.editPort(player, payload.kind(), payload.index(), payload.action(), payload.unit()));
    }

    private static void portBatch(BlueprintPayloads.PortBatch payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.HangarOps.batchPort(player, payload.kind(), payload.index(), payload.action(), payload.value(), payload.units()));
    }

    private static void portHold(BlueprintPayloads.PortHold payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (!payload.active()) {
                com.wrights.drone.ManageLock.clear(player);
                return;
            }
            net.minecraft.world.item.ItemStack stack = com.wrights.drone.WrightportPlaces.stack(player, payload.kind(), payload.index());
            if (!(stack.getItem() instanceof com.wrights.item.PersonalWrightportItem)) {
                com.wrights.drone.ManageLock.clear(player);
                return;
            }
            com.wrights.drone.ManageLock.hold(player, payload.kind(), payload.index());
        });
    }

    private static void placedPortsAsk(BlueprintPayloads.PlacedPortsAsk payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.PlacedPortManage.sync(player));
    }

    private static void placedSwarmAsk(BlueprintPayloads.PlacedSwarmAsk payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.PlacedPortManage.swarm(player, payload.dimension(), payload.pos()));
    }

    private static void placedVisit(BlueprintPayloads.PlacedVisit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.PlacedPortManage.visit(player, payload.dimension(), payload.pos()));
    }

    private static void placedEdit(BlueprintPayloads.PlacedEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.PlacedPortManage.edit(player, payload.dimension(), payload.pos(), payload.action(), payload.unit()));
    }

    private static void placedBatch(BlueprintPayloads.PlacedBatch payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.PlacedPortManage.batch(player, payload.dimension(), payload.pos(), payload.action(), payload.value(), payload.units()));
    }

    private static void renameWrightport(BlueprintPayloads.RenameWrightport payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.drone.PlacedPortManage.rename(player, payload.dimension(), payload.pos(), payload.name()));
    }

    private static void renameWright(BlueprintPayloads.RenameWright payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (payload.mode() == 1) {
                com.wrights.drone.HangarOps.renamePort(player, payload.kind(), payload.port(), payload.unit(), payload.name());
                return;
            }
            if (payload.mode() == 2) {
                com.wrights.drone.PlacedPortManage.renameUnit(player, payload.dimension(), payload.pos(), payload.unit(), payload.name());
                return;
            }
            if (player.containerMenu instanceof com.wrights.menu.WrightportMenu menu && menu.pos().equals(payload.pos())) {
                com.wrights.drone.WrightportOps.rename(player, menu, payload.unit(), payload.name());
            }
        });
    }

    private static void clientPortGlow(BlueprintPayloads.PortGlow payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.glowPort(payload.dimension(), payload.pos(), payload.line(), payload.anchor()));
    }

    private static void clientPlacedPorts(BlueprintPayloads.PlacedPorts payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyPlacedPorts(payload.ports()));
    }

    private static void clientPlacedSwarm(BlueprintPayloads.PlacedSwarm payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyPlacedSwarm(payload.dimension(), payload.pos(), payload.missing(), payload.swarm()));
    }

    private static void hangar(BlueprintPayloads.HangarEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (player.containerMenu instanceof com.wrights.menu.WrightportMenu menu && menu.pos().equals(payload.pos())) {
                com.wrights.drone.WrightportOps.apply(player, menu, payload.action(), payload.index());
            }
        });
    }

    private static void bindSupply(BlueprintPayloads.BindSupply payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> JobManager.bindSupply(player, payload.pos(), payload.clear()));
    }

    private static void bindPortChest(BlueprintPayloads.BindPortChest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> JobManager.bindPortChest(player, payload.port(), payload.chest(), payload.clear()));
    }

    private static void bindDigital(BlueprintPayloads.BindDigital payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> com.wrights.storage.DigitalBinder.bind(player, payload.pos(), payload.clear()));
    }

    private static void digitalInfo(BlueprintPayloads.DigitalInfoRequest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> syncDigitalInfo(player));
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

    private static void clientDigital(BlueprintPayloads.DigitalSync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyDigital(payload));
    }

    private static void clientDigitalInfo(BlueprintPayloads.DigitalInfoSync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.applyDigitalInfo(payload));
    }

    private static void clientJobDone(BlueprintPayloads.JobDone payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.showJobDone(payload.name(), payload.seconds()));
    }

    private static void clientDockToast(BlueprintPayloads.DockToast payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.wrights.client.WrightsClientState.showDockToast(payload));
    }

    public static void dockToast(ServerPlayer player, int docked, BlockPos dock, String port, int digital, BlockPos storage, int dropped, BlockPos ground) {
        if (player == null) {
            return;
        }
        BlockPos dockAt = dock == null ? BlockPos.ZERO : dock;
        BlockPos storedAt = storage == null ? BlockPos.ZERO : storage;
        BlockPos droppedAt = ground == null ? BlockPos.ZERO : ground;
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DockToast(
                docked, dockAt.getX(), dockAt.getY(), dockAt.getZ(), port == null ? "" : port,
                digital, storedAt.getX(), storedAt.getY(), storedAt.getZ(),
                dropped, droppedAt.getX(), droppedAt.getY(), droppedAt.getZ()));
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
                    job.workedTicks(),
                    job.hold(),
                    job.waitKind(),
                    jobSource(job),
                    jobSourcePort(job),
                    labelPort(player, job),
                    job.queued(),
                    job.crewQuota(),
                    jobSourceName(player, job)));
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.JobSync(lines));
    }

    private static int jobSource(BuildJob job) {
        boolean you = false;
        boolean port = false;
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.homePort == null) {
                you = true;
            } else {
                port = true;
            }
        }
        if (you && port) {
            return 3;
        }
        if (port) {
            return 2;
        }
        return you ? 1 : 0;
    }

    private static String jobSourceName(ServerPlayer player, BuildJob job) {
        BlockPos pos = jobSourcePort(job);
        if (pos == null || pos.equals(BlockPos.ZERO) || player.getServer() == null) {
            return "";
        }
        var level = player.getServer().getLevel(job.dimension());
        if (level != null && level.getBlockEntity(pos) instanceof com.wrights.block.WrightportBlockEntity port) {
            return port.label();
        }
        return com.wrights.drone.WrightLedger.get(player.getServer()).label(job.dimension().location().toString(), pos);
    }

    private static BlockPos jobSourcePort(BuildJob job) {
        for (BuildJob.Hand hand : job.hands()) {
            if (hand.homePort != null) {
                return hand.homePort;
            }
        }
        return BlockPos.ZERO;
    }

    private static boolean labelPort(ServerPlayer player, BuildJob job) {
        net.minecraft.server.level.ServerLevel level = player.getServer().getLevel(job.dimension());
        return level != null && com.wrights.drone.WrightHomes.coveringPortCount(level, job.min(), player.getUUID()) > 1;
    }

    public static void syncSupply(ServerPlayer player) {
        BlockPos pos = BlueprintSessions.get(player).supplyPos();
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.SupplySync(pos != null, pos == null ? BlockPos.ZERO : pos));
    }

    public static void syncDigital(ServerPlayer player) {
        com.wrights.storage.DigitalLink link = BlueprintSessions.get(player).digitalLink();
        boolean online = false;
        BlockPos pos = BlockPos.ZERO;
        String dim = "";
        int kind = com.wrights.storage.DigitalLink.Kind.NONE.ordinal();
        if (link != null && link.bound()) {
            kind = link.kind().ordinal();
            pos = link.pos().pos();
            dim = link.pos().dimension().location().toString();
            var level = com.wrights.storage.DigitalNetworks.levelOf(player, link.pos());
            online = level != null && com.wrights.storage.DigitalNetworks.online(level, link);
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DigitalSync(kind, online, pos, dim));
    }

    public static void syncDigitalInfo(ServerPlayer player) {
        com.wrights.storage.DigitalLink link = com.wrights.storage.ItemSource.linkOf(player);
        boolean online = false;
        BlockPos pos = BlockPos.ZERO;
        String dim = "";
        int kind = com.wrights.storage.DigitalLink.Kind.NONE.ordinal();
        java.util.List<BlueprintPayloads.DigitalInfoLine> lines = new java.util.ArrayList<>();
        if (link != null && link.bound()) {
            kind = link.kind().ordinal();
            pos = link.pos().pos();
            dim = link.pos().dimension().location().toString();
            var level = com.wrights.storage.DigitalNetworks.levelOf(player, link.pos());
            online = level != null && com.wrights.storage.DigitalNetworks.online(level, link);
            if (level != null && online) {
                for (net.minecraft.world.item.Item item : com.wrights.storage.DigitalCatalog.items()) {
                    long stock = com.wrights.storage.DigitalNetworks.count(level, link, item);
                    boolean craftable = com.wrights.storage.DigitalNetworks.craftable(level, link, item);
                    lines.add(new BlueprintPayloads.DigitalInfoLine(net.minecraft.world.item.Item.getId(item), stock, craftable));
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.DigitalInfoSync(kind, online, pos, dim, lines));
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
        java.util.List<BlueprintPayloads.Bit> bits = hologramBits(job, schematic);
        int clipX = job.clipX() == Integer.MAX_VALUE ? schematic.sizeX() : job.clipX();
        int clipY = job.clipY() == Integer.MAX_VALUE ? schematic.sizeY() : job.clipY();
        int clipZ = job.clipZ() == Integer.MAX_VALUE ? schematic.sizeZ() : job.clipZ();
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.JobHologram(job.id(), job.min(), clipX, clipY, clipZ, false, bits, job.dimension().location().toString()));
    }

    /** Keeps a big build under the packet cap by stepping through it, so the ghost still shows. */
    private static java.util.List<BlueprintPayloads.Bit> hologramBits(BuildJob job, Schematic schematic) {
        int limit = Math.max(1, com.wrights.WrightsConfig.maxPreviewBlocks());
        int count = 0;
        for (Schematic.Entry entry : schematic.entries()) {
            if (!job.outsideClip(entry)) {
                count++;
            }
        }
        int step = Math.max(1, (count + limit - 1) / limit);
        java.util.List<BlueprintPayloads.Bit> bits = new java.util.ArrayList<>(Math.min(count, limit));
        int seen = 0;
        for (Schematic.Entry entry : schematic.entries()) {
            if (job.outsideClip(entry)) {
                continue;
            }
            if (seen % step == 0) {
                bits.add(new BlueprintPayloads.Bit(entry.x(), entry.y(), entry.z(), net.minecraft.world.level.block.Block.getId(entry.state())));
            }
            seen++;
        }
        return bits;
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
