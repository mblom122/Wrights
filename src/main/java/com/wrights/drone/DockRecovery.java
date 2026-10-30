package com.wrights.drone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.wrights.block.WrightportBlockEntity;
import com.wrights.item.WrightItem;
import com.wrights.storage.DigitalLink;
import com.wrights.storage.DigitalNetworks;
import com.wrights.storage.ItemSource;
import com.wrights.util.Messages;

/** Puts a wright somewhere safe when the dock it left is gone. */
public final class DockRecovery {
    private static final Map<UUID, Notice> NOTICES = new HashMap<>();

    private DockRecovery() {
    }

    /**
     * Saves one wright. Tries {@code where} first, then any other owned port in range of that spot.
     * Returns the port it docked in, or null when the wright went to digital storage or the ground.
     */
    @Nullable
    public static BlockPos recover(ServerLevel level, ServerPlayer player, BlockPos where, @Nullable UUID unitId, int tier) {
        return recover(level, player, where, unitId, tier, "");
    }

    /**
     * Saves one wright. Tries {@code where} first, then any other owned port in range of that spot.
     * Returns the port it docked in, or null when the wright went to digital storage or the ground.
     */
    @Nullable
    public static BlockPos recover(ServerLevel level, ServerPlayer player, BlockPos where, @Nullable UUID unitId, int tier, @Nullable String name) {
        if (level == null || where == null) {
            return null;
        }
        if (redock(level, player, where, unitId, tier, name)) {
            notice(player, level, null, where, Where.DOCK);
            return where;
        }
        UUID owner = player == null ? null : player.getUUID();
        BlockPos other = WrightHomes.openPort(level, where, owner, null);
        if (other != null && !other.equals(where) && redock(level, player, other, unitId, tier, name)) {
            notice(player, level, null, other, Where.DOCK);
            return other;
        }
        ItemStack stack = WrightItem.stack(tier, 1);
        BlockPos digitalAt = insertDigital(player, level, stack);
        if (stack.isEmpty()) {
            notice(player, level, digitalAt, where, Where.DIGITAL);
            return null;
        }
        drop(level, where, stack);
        notice(player, level, digitalAt, where, Where.GROUND);
        return null;
    }

    private static boolean redock(ServerLevel level, ServerPlayer player, BlockPos where, @Nullable UUID unitId, int tier, @Nullable String name) {
        if (!level.isLoaded(where)) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(where);
        if (!(entity instanceof WrightportBlockEntity port)) {
            return false;
        }
        UUID owner = port.owner();
        if (player != null && owner != null && !owner.equals(player.getUUID())) {
            return false;
        }
        if (unitId != null && port.swarm().contains(unitId)) {
            return true;
        }
        Swarm next = unitId == null
                ? port.swarm().addUnit(tier)
                : port.swarm().adopt(new Swarm.Unit(unitId, tier, 0, name == null ? "" : name));
        if (next == port.swarm()) {
            return false;
        }
        port.setSwarm(next);
        return true;
    }

    private static BlockPos insertDigital(ServerPlayer player, ServerLevel level, ItemStack stack) {
        if (player == null || stack.isEmpty()) {
            return null;
        }
        DigitalLink link = ItemSource.linkOf(player);
        if (link == null || !link.bound()) {
            return null;
        }
        ServerLevel stored = DigitalNetworks.levelOf(player, link.pos());
        if (stored == null || !DigitalNetworks.online(stored, link)) {
            return null;
        }
        int count = stack.getCount();
        long accepted = DigitalNetworks.insert(player, stored, link, stack);
        if (accepted <= 0L) {
            return null;
        }
        stack.shrink((int) Math.min(count, accepted));
        return link.pos().pos();
    }

    private static void drop(ServerLevel level, BlockPos where, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(level, where.getX() + 0.5D, where.getY() + 1.0D, where.getZ() + 0.5D, stack.copy());
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
        stack.setCount(0);
    }

    private enum Where {
        DIGITAL,
        GROUND,
        DOCK
    }

    private static void notice(ServerPlayer player, ServerLevel level, BlockPos digitalAt, BlockPos droppedAt, Where where) {
        if (player == null) {
            return;
        }
        long now = level.getGameTime();
        Notice notice = NOTICES.get(player.getUUID());
        if (notice == null || now - notice.tick > 40L) {
            notice = new Notice();
            NOTICES.put(player.getUUID(), notice);
        }
        notice.tick = now;
        if (where == Where.DIGITAL) {
            notice.digital++;
            notice.digitalAt = digitalAt;
        } else if (where == Where.DOCK) {
            notice.docked++;
            notice.dockAt = droppedAt;
        } else {
            notice.dropped++;
            notice.droppedAt = droppedAt;
        }
        net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.translatable("message.wrights.dock_gone");
        if (notice.digital > 0 && notice.digitalAt != null) {
            text.append(" ").append(net.minecraft.network.chat.Component.translatable("message.wrights.dock_gone_digital_part",
                    notice.digital, notice.digitalAt.getX(), notice.digitalAt.getY(), notice.digitalAt.getZ()));
        }
        if (notice.docked > 0 && notice.dockAt != null) {
            text.append(" ").append(net.minecraft.network.chat.Component.translatable("message.wrights.dock_gone_dock_part",
                    notice.docked, notice.dockAt.getX(), notice.dockAt.getY(), notice.dockAt.getZ()));
        }
        if (notice.dropped > 0 && notice.droppedAt != null) {
            text.append(" ").append(net.minecraft.network.chat.Component.translatable("message.wrights.dock_gone_dropped_part",
                    notice.dropped, notice.droppedAt.getX(), notice.droppedAt.getY(), notice.droppedAt.getZ()));
        }
        Messages.actionBar(player, text);
    }

    private static final class Notice {
        private long tick;
        private int digital;
        private int dropped;
        private int docked;
        private BlockPos digitalAt;
        private BlockPos droppedAt;
        private BlockPos dockAt;
    }
}
