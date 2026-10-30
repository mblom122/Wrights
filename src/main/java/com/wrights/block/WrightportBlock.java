package com.wrights.block;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import com.wrights.drone.Swarm;
import com.wrights.drone.WrightLedger;

public class WrightportBlock extends BaseEntityBlock {
    public static final MapCodec<WrightportBlock> CODEC = simpleCodec(WrightportBlock::new);

    public WrightportBlock(Properties properties) {
        super(properties.strength(3.5F, 6.0F).sound(SoundType.COPPER).mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops());
    }

    @Override
    protected MapCodec<WrightportBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WrightportBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, ModBlockEntities.WRIGHTPORT.get(), (lvl, pos, blockState, port) -> port.animateHatch());
        }
        return createTickerHelper(type, ModBlockEntities.WRIGHTPORT.get(), (lvl, pos, blockState, port) -> port.tickLamp());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof WrightportBlockEntity port) {
            if (placer instanceof Player player) {
                port.assignOwner(player.getUUID(), player.getGameProfile().getName());
            }
            String carried = com.wrights.item.WrightportBlockItem.labelOf(stack);
            if (!carried.isBlank()) {
                port.setLabel(carried);
            }
            port.setSwarm(Swarm.from(stack));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof WrightportBlockEntity port) {
            server.openMenu(port, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            com.wrights.job.JobManager.strandFromDock(server, pos);
            WrightLedger.get(server.getServer()).forget(server.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack stack = new ItemStack(ModBlocks.WRIGHTPORT_ITEM.get());
        BlockEntity entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (entity instanceof WrightportBlockEntity port) {
            Swarm swarm = port.swarm();
            if (params.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
                swarm = swarm.without(com.wrights.job.JobManager.strandFromDock(server, port.getBlockPos()));
            }
            if (!swarm.units().isEmpty()) {
                Swarm.write(stack, swarm);
            }
            com.wrights.item.WrightportBlockItem.writeLabel(stack, port.label());
        }
        return List.of(stack);
    }
}
