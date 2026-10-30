package com.wrights.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.wrights.util.Messages;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class HangarBlock extends BaseEntityBlock {
    public static final MapCodec<HangarBlock> CODEC = simpleCodec(HangarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 15, 2, 15),
            box(1, 2, 1, 4, 14, 4),
            box(12, 2, 1, 15, 14, 4),
            box(1, 2, 12, 4, 14, 15),
            box(12, 2, 12, 15, 14, 15),
            box(5, 2, 5, 11, 8, 11));

    public HangarBlock(Properties properties) {
        super(properties.strength(3.0F, 6.0F).sound(SoundType.COPPER).mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops());
    }

    @Override
    protected MapCodec<HangarBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.HANGAR.get(), HangarBlockEntity::tick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof HangarBlockEntity hangar) {
            Container items = hangar.items();
            for (int slot = 0; slot < items.getContainerSize(); slot++) {
                ItemStack stack = items.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                items.setItem(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
            hangar.setChanged();
            Messages.actionBar(server, "message.wrights.use_manage");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HangarBlockEntity hangar) {
            Containers.dropContents(level, pos, hangar.items());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
