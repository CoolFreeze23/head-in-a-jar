package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** The Neural Socket block: a redstone source on all six faces, driven by its block entity. */
public class NeuralSocketBlock extends HeadHolderBlock<NeuralSocketBlockEntity> {
    public NeuralSocketBlock(Properties properties) {
        super(properties, () -> com.coolfreeze23.headinajar.registry.ModRegistry.SOCKET_BE.get(), NeuralSocketBlockEntity::new,
                NeuralSocketBlockEntity::serverTick, null);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(com.coolfreeze23.headinajar.machine.MachineLamp.LAMP, com.coolfreeze23.headinajar.machine.MachineLamp.OFF));
    }

    /** The kit's lamp on its front. */
    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(com.coolfreeze23.headinajar.machine.MachineLamp.LAMP);
    }

    /** The clamps' and bars' client tick; the server's as before. */
    @Override
    public <E extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<E> getTicker(
            net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<E> type) {
        if (level.isClientSide) {
            return type == com.coolfreeze23.headinajar.registry.ModRegistry.SOCKET_BE.get()
                    ? (l, p, s, be) -> ((com.coolfreeze23.headinajar.machine.KitMachine) be).clientTick() : null;
        }
        return super.getTicker(level, state, type);
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof NeuralSocketBlockEntity socket) socket.removed();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** {@code direction} points from the neighbour asking into this block; the face it sees is the opposite one. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof NeuralSocketBlockEntity socket ? socket.output(direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
