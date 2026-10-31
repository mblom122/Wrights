package com.wrights.blueprint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.wrights.WrightsConfig;
import com.wrights.item.ControllerItem;
import com.wrights.job.JobManager;
import com.wrights.network.BlueprintPayloads;
import com.wrights.schematic.Schematic;
import com.wrights.schematic.SchematicLibrary;
import com.wrights.util.BlockEntityNbt;
import com.wrights.util.BlockFilters;
import com.wrights.util.Messages;

/** Starts one wright job for each reflected copy. */
public final class MirrorLaunch {
    public record Result(int started, boolean nowhere) {
    }

    private record Stamp(long tick, BlockPos pos) {
    }

    /** One click can reach the server twice, once for each hand. The second place stacks a slab. */
    private static final Map<UUID, Stamp> LAST_PLACE = new ConcurrentHashMap<>();

    private MirrorLaunch() {
    }

    /** Places the block in the other hand, then asks wrights to build the reflected copies. */
    public static void placeHeld(ServerPlayer player, BlueprintPayloads.MirrorPlace payload) {
        if (!ControllerItem.isHeld(player) || payload == null || WrightsConfig.refuse(player, WrightsConfig.allowMirrorBlocks())) {
            return;
        }
        InteractionHand hand = blockHand(player);
        if (hand == null) {
            Messages.actionBar(player, "message.wrights.mirror_need_block");
            return;
        }
        Direction face = Direction.from3DDataValue(Mth.clamp(payload.face(), 0, 5));
        BlockPos clicked = payload.pos();
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        if (player.distanceToSqr(at) > range()) {
            Messages.actionBar(player, "message.wrights.place_out_of_range");
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos side = clicked.relative(face);
        BlockState beforeClick = level.getBlockState(clicked);
        BlockState beforeSide = level.getBlockState(side);
        ItemStack stack = player.getItemInHand(hand);
        UseOnContext context = new UseOnContext(player, hand, new BlockHitResult(at, face, clicked, false));
        BlockPlaceContext planned = new BlockPlaceContext(context);
        if (!planned.canPlace()) {
            return;
        }
        BlockPos target = planned.getClickedPos();
        boolean replace = payload.replace() && WrightsConfig.allowMirrorReplace();
        if (!replace && !level.getBlockState(target).isAir()) {
            return;
        }
        if (placedAlready(player, level.getGameTime(), target)) {
            return;
        }
        InteractionResult result = stack.useOn(context);
        if (!result.consumesAction()) {
            return;
        }
        player.swing(hand, true);
        BlockPos placed = null;
        if (!level.getBlockState(clicked).equals(beforeClick)) {
            placed = clicked.immutable();
        } else if (!level.getBlockState(side).equals(beforeSide)) {
            placed = side.immutable();
        }
        if (placed == null || !payload.mirror() || !payload.enabled() || payload.anchor() == null) {
            if (placed != null && payload.enabled() && !payload.mirror()) {
                Messages.actionBar(player, "message.wrights.mirror_need_anchor");
            }
            return;
        }
        if (player.distanceToSqr(payload.anchor().getX() + 0.5D, payload.anchor().getY() + 0.5D, payload.anchor().getZ() + 0.5D) > range()) {
            Messages.actionBar(player, "message.wrights.mirror_far");
            return;
        }
        BlockState state = level.getBlockState(placed);
        boolean copyNbt = payload.copyNbt();
        boolean settings = copyNbt && payload.copySettings() && WrightsConfig.copySettings();
        boolean all = copyNbt && payload.copyAll() && WrightsConfig.copyAll() && player.getAbilities().instabuild && !player.isSpectator();
        if (all) {
            settings = false;
        }
        if (!BlockFilters.canCopy(level, placed, state, false, copyNbt)) {
            if (state.hasBlockEntity()) {
                Messages.actionBar(player, "message.wrights.mirror_skip_nbt");
            }
            return;
        }
        CompoundTag nbt = null;
        boolean full = false;
        if (state.hasBlockEntity() && (settings || all)) {
            BlockEntity entity = level.getBlockEntity(placed);
            if (entity != null) {
                CompoundTag raw = entity.saveWithoutMetadata(level.registryAccess());
                if (all) {
                    nbt = raw.isEmpty() ? null : raw;
                    full = nbt != null;
                } else if (WrightsConfig.settingsAllowed(state.getBlock())) {
                    nbt = BlockEntityNbt.settings(raw);
                }
            }
        }
        Schematic.Entry entry = new Schematic.Entry((short) 0, (short) 0, (short) 0, state, BlockFilters.isSecondary(state), nbt, full);
        Schematic source = new Schematic(UUID.randomUUID(), "Mirror", 1, 1, 1, List.of(entry), placed, placed, "", 0, 0, 0, true);
        Direction plane = Direction.from3DDataValue(Mth.clamp(payload.anchorFace(), 0, 5));
        List<Mirror.Placed> copies = Mirror.place(source, placed, 1, 1, 1, payload.anchor(), plane, WrightsConfig.allowedPattern(payload.pattern()), payload.shapes(), WrightsConfig.allowedAxes(payload.axes()));
        if (copies.isEmpty()) {
            Messages.actionBar(player, "message.wrights.mirror_same");
            return;
        }
        List<Mirror.Placed> ready = new java.util.ArrayList<>();
        for (Mirror.Placed copy : copies) {
            BlockPos min = copy.origin();
            Schematic schematic = copy.schematic();
            BlockPos far = min.offset(schematic.sizeX() - 1, schematic.sizeY() - 1, schematic.sizeZ() - 1);
            if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(far)) {
                continue;
            }
            if (!replace && (!level.isLoaded(min) || !level.getBlockState(min).isAir())) {
                continue;
            }
            ready.add(copy);
        }
        int started = startUnited(player, ready, 0, false, replace, replace, replace, true, true, false, copyNbt, settings, all);
        if (started > 0) {
            Messages.actionBar(player, "message.wrights.mirror_started", started);
        }
    }

    private static boolean placedAlready(ServerPlayer player, long tick, BlockPos target) {
        Stamp stamp = new Stamp(tick, target.immutable());
        Stamp previous = LAST_PLACE.put(player.getUUID(), stamp);
        return previous != null && previous.tick == tick && previous.pos.equals(target);
    }

    private static InteractionHand blockHand(ServerPlayer player) {
        boolean mainTool = player.getMainHandItem().getItem() instanceof ControllerItem;
        boolean offTool = player.getOffhandItem().getItem() instanceof ControllerItem;
        if (mainTool && player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        if (offTool && player.getMainHandItem().getItem() instanceof BlockItem) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static double range() {
        double reach = WrightsConfig.MAX_RANGE.getAsInt();
        return reach * reach;
    }

    public static Result spawn(ServerPlayer player, Schematic source, BlockPos origin, int clipX, int clipY, int clipZ, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean playerFirst, boolean queue, boolean deferSupplies, boolean placeNbt, boolean placeSettings, boolean placeAll, BlockPos plane, int face, int pattern, boolean shapes, int axes) {
        Direction direction = Direction.from3DDataValue(Mth.clamp(face, 0, 5));
        java.util.List<Mirror.Placed> copies = Mirror.place(source, origin, clipX, clipY, clipZ, plane, direction, pattern, shapes, axes);
        if (copies.isEmpty()) {
            return new Result(0, true);
        }
        List<Mirror.Placed> ready = new java.util.ArrayList<>();
        for (Mirror.Placed placed : copies) {
            BlockPos min = placed.origin();
            Schematic schematic = placed.schematic();
            BlockPos far = min.offset(schematic.sizeX() - 1, schematic.sizeY() - 1, schematic.sizeZ() - 1);
            if (!BoxEdits.inWorld(min) || !BoxEdits.inWorld(far)) {
                continue;
            }
            ready.add(placed);
        }
        int started = startUnited(player, ready, drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll);
        return new Result(started, false);
    }

    /** Starts one job that holds every copy. Falls back to a job per copy when they cannot share a schematic. */
    private static int startUnited(ServerPlayer player, List<Mirror.Placed> copies, int drones, boolean placeFluids, boolean voidBlocks, boolean voidFluids, boolean voidNbt, boolean playerFirst, boolean queue, boolean deferSupplies, boolean placeNbt, boolean placeSettings, boolean placeAll) {
        if (copies.isEmpty()) {
            return 0;
        }
        SchematicLibrary library = SchematicLibrary.get(player.getServer());
        Mirror.Placed united = Mirror.unite(copies);
        if (united != null) {
            Schematic schematic = united.schematic();
            library.putSchematic(schematic);
            boolean ok = JobManager.startBlueprintBuild(player, schematic.id(), united.origin(), schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll);
            if (ok) {
                return copies.size();
            }
            library.removeSchematic(schematic.id());
        }
        int started = 0;
        for (Mirror.Placed placed : copies) {
            Schematic schematic = placed.schematic();
            library.putSchematic(schematic);
            boolean ok = JobManager.startBlueprintBuild(player, schematic.id(), placed.origin(), schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), drones, placeFluids, voidBlocks, voidFluids, voidNbt, playerFirst, queue, deferSupplies, placeNbt, placeSettings, placeAll);
            if (!ok) {
                library.removeSchematic(schematic.id());
                continue;
            }
            started++;
        }
        return started;
    }

    /** Breaking one block also breaks the reflected copies that match the mining rule. */
    public static void mine(ServerPlayer player, BlueprintPayloads.MirrorMine payload) {
        if (player == null || payload == null || WrightsConfig.refuse(player, WrightsConfig.allowMirrorMine())) {
            return;
        }
        if (!ControllerItem.isHeld(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = payload.pos();
        double reach = player.blockInteractionRange() + 1.5D;
        if (origin == null || player.distanceToSqr(origin.getCenter()) > reach * reach) {
            return;
        }
        BlockState mined = net.minecraft.world.level.block.Block.stateById(payload.stateId());
        if (mined.isAir()) {
            return;
        }
        BlockState now = level.getBlockState(origin);
        if (!now.isAir() && now.getBlock() != mined.getBlock()) {
            return;
        }
        Direction face = Direction.from3DDataValue(Mth.clamp(payload.anchorFace(), 0, 5));
        int pattern = WrightsConfig.allowedPattern(payload.pattern());
        int axes = WrightsConfig.allowedAxes(payload.axes());
        List<BlockPos> copies = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos copy : Mirror.copiesOf(origin, payload.anchor(), face, pattern, axes)) {
            if (!level.isLoaded(copy) || !level.mayInteract(player, copy) || !accepts(level, copy, mined.getBlock(), payload.any())) {
                continue;
            }
            copies.add(copy.immutable());
            minX = Math.min(minX, copy.getX());
            minY = Math.min(minY, copy.getY());
            minZ = Math.min(minZ, copy.getZ());
            maxX = Math.max(maxX, copy.getX());
            maxY = Math.max(maxY, copy.getY());
            maxZ = Math.max(maxZ, copy.getZ());
        }
        if (copies.isEmpty()) {
            com.wrights.util.Messages.actionBar(player, "message.wrights.mine_none");
            return;
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        if (sizeX > Short.MAX_VALUE || sizeY > Short.MAX_VALUE || sizeZ > Short.MAX_VALUE) {
            com.wrights.util.Messages.actionBar(player, "message.wrights.mine_none");
            return;
        }
        List<Schematic.Entry> entries = new ArrayList<>(copies.size());
        for (BlockPos copy : copies) {
            entries.add(new Schematic.Entry(
                    (short) (copy.getX() - minX),
                    (short) (copy.getY() - minY),
                    (short) (copy.getZ() - minZ),
                    level.getBlockState(copy),
                    false));
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        Schematic schematic = new Schematic(UUID.randomUUID(), "Mirror - Mine", sizeX, sizeY, sizeZ, entries, min, max, level.dimension().location().toString(), 0, 0, 0, true);
        boolean voiding = payload.voidBlocks() && WrightsConfig.allowVoidBlocks();
        JobManager.startMirrorMine(player, schematic, origin, payload.any(), voiding, miningTool(player));
    }

    /** Breaks the block with the tool that started the job. Void removes it with no drops. */
    public static void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack tool, boolean voiding) {
        if (voiding) {
            BlockEntity stored = level.getBlockEntity(pos);
            if (stored instanceof net.minecraft.world.Container container) {
                container.clearContent();
            }
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS);
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        ItemStack used = tool == null ? ItemStack.EMPTY : tool;
        Set<UUID> already = player.getAbilities().instabuild ? Set.of() : nearbyItems(level, pos);
        if (!player.getAbilities().instabuild) {
            net.minecraft.world.level.block.Block.dropResources(state, level, pos, entity, player, used);
        }
        level.destroyBlock(pos, false, player);
        if (!player.getAbilities().instabuild) {
            claimDrops(player, level, pos, already);
        }
    }

    /** The tool that mined the block. Its enchantments, including a modded silk touch, decide the drops. */
    private static ItemStack miningTool(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof ControllerItem) && !main.isEmpty()) {
            return main.copy();
        }
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof ControllerItem) && !off.isEmpty()) {
            return off.copy();
        }
        return ItemStack.EMPTY;
    }

    /** Item entities already sitting on the block, so the new drops can be told apart. */
    private static Set<UUID> nearbyItems(ServerLevel level, BlockPos pos) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0D))) {
            ids.add(entity.getUUID());
        }
        return ids;
    }

    /** Mirror drops go into the inventory. Whatever does not fit lands at the player. */
    private static void claimDrops(ServerPlayer player, ServerLevel level, BlockPos pos, Set<UUID> already) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0D))) {
            if (!entity.isAlive() || already.contains(entity.getUUID())) {
                continue;
            }
            ItemStack stack = entity.getItem().copy();
            entity.discard();
            giveOrDrop(player, stack);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (player.getInventory().add(stack) || stack.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ItemEntity dropped = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
        dropped.setPickUpDelay(0);
        dropped.setDeltaMovement(0.0D, 0.05D, 0.0D);
        level.addFreshEntity(dropped);
    }

    /** Same block, or any block that does not store data. */
    public static boolean accepts(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.level.block.Block mined, boolean any) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return false;
        }
        if (any) {
            return !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock);
        }
        return state.getBlock() == mined;
    }
}
