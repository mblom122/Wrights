package com.wrights.block;

import com.wrights.job.DroneFleet;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import com.wrights.drone.Swarm;
import com.wrights.drone.WrightLedger;

/**
 * Two blocks tall. The lower half owns the block entity and draws the whole tower;
 * the upper half is only there for collision and to keep the space above free.
 */
public class WrightportBlock extends BaseEntityBlock {
    public static final MapCodec<WrightportBlock> CODEC = simpleCodec(WrightportBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public WrightportBlock(Properties properties) {
        super(properties.strength(3.5F, 6.0F).sound(SoundType.COPPER).mapColor(MapColor.COLOR_ORANGE)
                .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<WrightportBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    public static boolean isUpper(BlockState state) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    /** The block that holds the port: itself for the lower half, the one below for the upper. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return isUpper(state) ? pos.below() : pos;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isUpper(state) ? null : new WrightportBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (isUpper(state)) {
            return null;
        }
        if (level.isClientSide) {
            return createTickerHelper(type, ModBlockEntities.WRIGHTPORT.get(), (lvl, pos, blockState, port) -> port.animateHatch());
        }
        return createTickerHelper(type, ModBlockEntities.WRIGHTPORT.get(), (lvl, pos, blockState, port) -> port.tickLamp());
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof WrightportBlockEntity port) {
            if (placer instanceof Player player) {
                port.assignOwner(player.getUUID(), player.getGameProfile().getName());
            }
            String carried = com.wrights.item.WrightportBlockItem.labelOf(stack);
            if (!carried.isBlank()) {
                port.setLabel(carried);
            }
            port.setSwarm(Swarm.from(stack));
            port.loadEnergyFromItem(stack);
        }
    }

    /** An upper half with no port under it goes away. A lower half stays even without its top, so wrights are never lost. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (isUpper(state) && direction == Direction.DOWN && !(neighbor.is(this) && !isUpper(neighbor))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Breaking the top breaks the port below it, with the same drops as breaking the port. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && isUpper(state)) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && !isUpper(lower)) {
                level.destroyBlock(below, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return openPort(state, level, pos, player);
    }

    /** Either half of a placed wrightport. */
    public static boolean isPort(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return pos != null && level.getBlockState(pos).getBlock() instanceof WrightportBlock;
    }

    /** Holding a controller (or any item) still opens the port — build clicks must not steal this. */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        InteractionResult opened = openPort(state, level, pos, player);
        return opened.consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult openPort(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos at = base(state, pos);
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(at) instanceof WrightportBlockEntity port) {
            if (port.supplyChest() == null) {
                boolean digital = false;
                if (server.getServer() != null) {
                    var link = com.wrights.storage.ItemSource.linkOf(server);
                    digital = link != null && link.bound();
                }
                if (!digital) {
                    com.wrights.util.Messages.actionBar(server, "message.wrights.port_need_supply");
                }
            }
            server.openMenu(port, buffer -> buffer.writeBlockPos(at));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!isUpper(state) && !state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            com.wrights.job.DroneFleet.strandFromDock(server, pos);
            WrightLedger.get(server.getServer()).forget(server.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (isUpper(state)) {
            return List.of();
        }
        ItemStack stack = new ItemStack(ModBlocks.WRIGHTPORT_ITEM.get());
        BlockEntity entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (entity instanceof WrightportBlockEntity port) {
            Swarm swarm = port.swarm();
            if (params.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
                swarm = swarm.without(com.wrights.job.DroneFleet.strandFromDock(server, port.getBlockPos()));
            }
            if (!swarm.units().isEmpty()) {
                Swarm.write(stack, swarm);
            }
            com.wrights.item.WrightportBlockItem.writeLabel(stack, port.label());
            port.saveEnergyToItem(stack);
        }
        return List.of(stack);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(ModBlocks.WRIGHTPORT_ITEM.get());
        if (level.getBlockEntity(base(state, pos)) instanceof WrightportBlockEntity port) {
            Swarm swarm = port.swarm();
            if (!swarm.units().isEmpty()) {
                Swarm.write(stack, swarm);
            }
            com.wrights.item.WrightportBlockItem.writeLabel(stack, port.label());
            port.saveEnergyToItem(stack);
        }
        return stack;
    }
}
