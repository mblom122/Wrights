package com.wrights.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsConfig;
import com.wrights.blueprint.BlueprintSessions;
import com.wrights.item.ControllerItem;
import com.wrights.network.BlueprintNetwork;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.Messages;

/** Server-side bind / unbind for the per-player digital storage link. */
public final class DigitalBinder {
    private DigitalBinder() {
    }

    public static void bind(ServerPlayer player, BlockPos pos, boolean clear) {
        if (!ControllerItem.isHeld(player) && !clear) {
            Messages.actionBar(player, "message.wrights.need_controller");
            return;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        BlueprintSessions.BlueprintSession session = BlueprintSessions.get(player);
        if (clear || pos == null || pos.equals(BlockPos.ZERO)) {
            session.setDigitalLink(DigitalLink.NONE);
            library.setDigital(player.getUUID(), DigitalLink.NONE);
            BlueprintNetwork.syncDigital(player);
            Messages.actionBar(player, "message.wrights.digital_unbound");
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double range = WrightsConfig.MAX_RANGE.getAsInt();
        if (!level.mayInteract(player, pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) {
            Messages.actionBar(player, "message.wrights.out_of_range");
            return;
        }
        DigitalLink.Kind kind = DigitalNetworks.bindKindAt(level, pos);
        if (kind == DigitalLink.Kind.NONE) {
            Messages.actionBar(player, "message.wrights.digital_not_target");
            return;
        }
        DigitalLink link = new DigitalLink(kind, GlobalPos.of(level.dimension(), pos.immutable()));
        if (!DigitalNetworks.online(level, link)) {
            Messages.actionBar(player, "message.wrights.digital_offline");
            return;
        }
        session.setDigitalLink(link);
        library.setDigital(player.getUUID(), link);
        BlueprintNetwork.syncDigital(player);
        Messages.actionBar(player, kind == DigitalLink.Kind.AE2
                ? "message.wrights.digital_bound_ae2"
                : "message.wrights.digital_bound_rs");
    }
}
