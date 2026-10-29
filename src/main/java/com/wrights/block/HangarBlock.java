package com.wrights.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HangarBlock extends BaseEntityBlock {
    public static final MapCodec<HangarBlock> CODEC = simpleCodec(HangarBlock::new);

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

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof HangarBlockEntity hangar) {
            server.openMenu(hangar, buffer -> buffer.writeBlockPos(pos));
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
