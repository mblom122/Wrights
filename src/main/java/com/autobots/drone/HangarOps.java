package com.autobots.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.autobots.block.HangarBlockEntity;
import com.autobots.menu.HangarMenu;
import com.autobots.util.Messages;

public final class HangarOps {
    public static final int BUILD = 0;
    public static final int REMOVE = 1;
    public static final int SPEED = 2;
    public static final int ASSIGN = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;

    public static final int IRON = 4;
    public static final int REDSTONE = 2;
    public static final int AMETHYST = 1;

    private HangarOps() {
    }

    public static void apply(ServerPlayer player, HangarMenu menu, int action, int index) {
        if (!(player.level() instanceof Level level)) {
            return;
        }
        if (!(level.getBlockEntity(menu.pos()) instanceof HangarBlockEntity hangar)) {
            return;
        }
        if (!(player.containerMenu instanceof HangarMenu open) || !open.pos().equals(menu.pos())) {
            return;
        }
        ItemStack cradle = hangar.items().getItem(0);
        if (cradle.isEmpty()) {
            Messages.actionBar(player, "message.autobots.dock_cradle");
            return;
        }
        Swarm swarm = Swarm.from(cradle);
        Swarm next = switch (action) {
            case BUILD -> build(player, swarm);
            case REMOVE -> swarm.removeUnit(index);
            case SPEED -> speed(player, swarm, index);
            case ASSIGN -> swarm.withAssign(index);
            case UP -> swarm.move(index, -1);
            case DOWN -> swarm.move(index, 1);
            default -> swarm;
        };
        if (next == swarm) {
            return;
        }
        Swarm.write(cradle, next);
        hangar.setChanged();
        open.broadcastChanges();
    }

    private static Swarm build(ServerPlayer player, Swarm swarm) {
        if (swarm.units().size() >= Swarm.MAX) {
            Messages.actionBar(player, "message.autobots.drone_full", Swarm.MAX);
            return swarm;
        }
        if (!pay(player, Items.IRON_INGOT, IRON) || !pay(player, Items.REDSTONE, REDSTONE) || !pay(player, Items.AMETHYST_SHARD, AMETHYST)) {
            Messages.actionBar(player, "message.autobots.drone_parts", IRON, REDSTONE, AMETHYST);
            return swarm;
        }
        take(player, Items.IRON_INGOT, IRON);
        take(player, Items.REDSTONE, REDSTONE);
        take(player, Items.AMETHYST_SHARD, AMETHYST);
        Messages.actionBar(player, "message.autobots.drone_built", swarm.units().size() + 1);
        return swarm.addUnit();
    }

    private static Swarm speed(ServerPlayer player, Swarm swarm, int index) {
        if (index < 0 || index >= swarm.units().size()) {
            return swarm;
        }
        int speed = swarm.units().get(index).speed();
        if (speed >= Swarm.MAX_SPEED) {
            Messages.actionBar(player, "message.autobots.speed_max");
            return swarm;
        }
        int gold = speed;
        int redstone = speed;
        if (!pay(player, Items.GOLD_INGOT, gold) || !pay(player, Items.REDSTONE, redstone)) {
            Messages.actionBar(player, "message.autobots.speed_parts", gold, redstone);
            return swarm;
        }
        take(player, Items.GOLD_INGOT, gold);
        take(player, Items.REDSTONE, redstone);
        Messages.actionBar(player, "message.autobots.speed_up", speed + 1);
        return swarm.withSpeed(index, speed + 1);
    }

    private static boolean pay(Player player, Item item, int count) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int have = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        return have >= count;
    }

    private static void take(Player player, Item item, int count) {
        if (player.getAbilities().instabuild) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize() && count > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int used = Math.min(count, stack.getCount());
            stack.shrink(used);
            count -= used;
        }
    }
}
