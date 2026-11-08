package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.RelayRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** A simple powered block: Leyden Jar, Tesla Node, Relay Masts, Signal Jammer. Use shows its charge. */
public class PowerBlock<T extends PowerBlockEntities.Powered> extends BaseEntityBlock {
    private final Supplier<BlockEntityType<T>> type;
    private final BiFunction<BlockPos, BlockState, T> factory;
    private final BlockEntityTicker<T> ticker;
    private final VoxelShape shape;

    public PowerBlock(Properties properties, Supplier<BlockEntityType<T>> type, BiFunction<BlockPos, BlockState, T> factory,
                      BlockEntityTicker<T> ticker, VoxelShape shape) {
        super(properties);
        this.type = type;
        this.factory = factory;
        this.ticker = ticker;
        this.shape = shape;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("power blocks are not data-driven");
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, type.get(), ticker);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (placer != null && level.getBlockEntity(pos) instanceof PowerBlockEntities.Station station) station.setOwner(placer.getUUID());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PowerBlockEntities.Powered powered) {
            player.displayClientMessage(Component.translatable("message.headinajar.energy_status", getName(),
                    powered.energy().getEnergyStored(), powered.energy().getMaxEnergyStored()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getServer() != null) RelayRegistry.get(level.getServer()).remove(level.dimension(), pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
