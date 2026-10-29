package com.autobots.network;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.autobots.blueprint.BlueprintActions;
import com.autobots.blueprint.BlueprintSessions;
import com.autobots.schematic.Requirements;
import com.autobots.schematic.Schematic;

public final class BlueprintNetwork {
    private BlueprintNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BlueprintPayloads.LibraryRequest.TYPE, BlueprintPayloads.LibraryRequest.STREAM_CODEC, BlueprintNetwork::library);
        registrar.playToServer(BlueprintPayloads.StartCapture.TYPE, BlueprintPayloads.StartCapture.STREAM_CODEC, BlueprintNetwork::startCapture);
        registrar.playToServer(BlueprintPayloads.CaptureUpdate.TYPE, BlueprintPayloads.CaptureUpdate.STREAM_CODEC, BlueprintNetwork::update);
        registrar.playToServer(BlueprintPayloads.CapturePick.TYPE, BlueprintPayloads.CapturePick.STREAM_CODEC, BlueprintNetwork::pick);
        registrar.playToServer(BlueprintPayloads.CaptureConfirm.TYPE, BlueprintPayloads.CaptureConfirm.STREAM_CODEC, BlueprintNetwork::confirm);
        registrar.playToServer(BlueprintPayloads.CaptureCancel.TYPE, BlueprintPayloads.CaptureCancel.STREAM_CODEC, BlueprintNetwork::cancel);
        registrar.playToServer(BlueprintPayloads.DeleteBlueprint.TYPE, BlueprintPayloads.DeleteBlueprint.STREAM_CODEC, BlueprintNetwork::delete);
        registrar.playToServer(BlueprintPayloads.RenameBlueprint.TYPE, BlueprintPayloads.RenameBlueprint.STREAM_CODEC, BlueprintNetwork::rename);
        registrar.playToServer(BlueprintPayloads.PreviewBlueprint.TYPE, BlueprintPayloads.PreviewBlueprint.STREAM_CODEC, BlueprintNetwork::previewBlueprint);
        registrar.playToServer(BlueprintPayloads.MaterialsRequest.TYPE, BlueprintPayloads.MaterialsRequest.STREAM_CODEC, BlueprintNetwork::materialsRequest);
        registrar.playToServer(BlueprintPayloads.BeginEdit.TYPE, BlueprintPayloads.BeginEdit.STREAM_CODEC, BlueprintNetwork::edit);
        registrar.playToServer(BlueprintPayloads.BeginBuild.TYPE, BlueprintPayloads.BeginBuild.STREAM_CODEC, BlueprintNetwork::build);
        registrar.playToServer(BlueprintPayloads.LaunchBuild.TYPE, BlueprintPayloads.LaunchBuild.STREAM_CODEC, BlueprintNetwork::launch);
        registrar.playToServer(BlueprintPayloads.StopJob.TYPE, BlueprintPayloads.StopJob.STREAM_CODEC, BlueprintNetwork::stop);
        registrar.playToServer(BlueprintPayloads.SetAnchor.TYPE, BlueprintPayloads.SetAnchor.STREAM_CODEC, BlueprintNetwork::anchor);
        registrar.playToServer(BlueprintPayloads.TransformBlueprint.TYPE, BlueprintPayloads.TransformBlueprint.STREAM_CODEC, BlueprintNetwork::transform);
        registrar.playToServer(BlueprintPayloads.RecaptureBlueprint.TYPE, BlueprintPayloads.RecaptureBlueprint.STREAM_CODEC, BlueprintNetwork::recapture);

        registrar.playToClient(BlueprintPayloads.LibrarySync.TYPE, BlueprintPayloads.LibrarySync.STREAM_CODEC, BlueprintNetwork::clientLibrary);
        registrar.playToClient(BlueprintPayloads.CaptureSync.TYPE, BlueprintPayloads.CaptureSync.STREAM_CODEC, BlueprintNetwork::clientCapture);
        registrar.playToClient(BlueprintPayloads.Preview.TYPE, BlueprintPayloads.Preview.STREAM_CODEC, BlueprintNetwork::clientPreview);
        registrar.playToClient(BlueprintPayloads.Materials.TYPE, BlueprintPayloads.Materials.STREAM_CODEC, BlueprintNetwork::clientMaterials);
        registrar.playToClient(BlueprintPayloads.CaptureFinished.TYPE, BlueprintPayloads.CaptureFinished.STREAM_CODEC, BlueprintNetwork::clientFinished);
    }

    public static void syncLibrary(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.LibrarySync(BlueprintActions.summaries(player), BlueprintActions.busy(player)));
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

    public static void finished(ServerPlayer player, UUID id, boolean saved) {
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.CaptureFinished(id, saved));
    }

    private static void library(BlueprintPayloads.LibraryRequest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.requestLibrary(player));
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
        context.enqueueWork(() -> BlueprintActions.confirm(player, payload.name()));
    }

    private static void cancel(BlueprintPayloads.CaptureCancel payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.cancel(player));
    }

    private static void delete(BlueprintPayloads.DeleteBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.delete(player, payload.id()));
    }

    private static void rename(BlueprintPayloads.RenameBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.rename(player, payload.id(), payload.name()));
    }

    private static void previewBlueprint(BlueprintPayloads.PreviewBlueprint payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.previewBlueprint(player, payload.id()));
    }

    private static void materialsRequest(BlueprintPayloads.MaterialsRequest payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.sendMaterials(player, payload.id(), payload.clipX(), payload.clipY(), payload.clipZ()));
    }

    private static void edit(BlueprintPayloads.BeginEdit payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> BlueprintActions.beginEdit(player, payload.id()));
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
                payload.pivotZ()));
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
        context.enqueueWork(() -> BlueprintActions.stop(player));
    }

    private static void clientLibrary(BlueprintPayloads.LibrarySync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.autobots.client.AutobotsClientState.applyLibrary(payload));
    }

    private static void clientCapture(BlueprintPayloads.CaptureSync payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.autobots.client.AutobotsClientState.applyCapture(payload));
    }

    private static void clientMaterials(BlueprintPayloads.Materials payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.autobots.client.MaterialsScreen.show(payload));
    }

    private static void clientPreview(BlueprintPayloads.Preview payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.autobots.client.AutobotsClientState.applyPreview(payload));
    }

    private static void clientFinished(BlueprintPayloads.CaptureFinished payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.autobots.client.AutobotsClientState.applyFinished(payload));
    }
}
