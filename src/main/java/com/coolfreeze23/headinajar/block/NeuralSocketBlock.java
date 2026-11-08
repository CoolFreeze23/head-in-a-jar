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
