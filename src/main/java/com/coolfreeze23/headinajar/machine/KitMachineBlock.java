package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.block.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A machine with a screen built on the kit: a {@link MachineBlock} (facing, fluids, menu, drops) that is two cells
 * tall, wears the lamp, ticks on the client for its moving parts, and turns its shapes with its facing.
 */
public abstract class KitMachineBlock<T extends BlockEntity> extends MachineBlock<T> implements TallMachine {
    private final VoxelShape lowerNorth, upperNorth;
    private final VoxelShape[] lower = new VoxelShape[4];

    public KitMachineBlock(Properties properties, Supplier<BlockEntityType<T>> type, BiFunction<BlockPos, BlockState, T> factory,
                           BlockEntityTicker<T> ticker, VoxelShape lowerNorth, VoxelShape upperNorth) {
        super(properties, type, factory, ticker, lowerNorth);
        this.lowerNorth = lowerNorth;
        this.upperNorth = upperNorth;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MachineLamp.LAMP);
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return upperNorth;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = facingOf(state).get2DDataValue();
        if (lower[i] == null) lower[i] = TallMachine.rotate(lowerNorth, facingOf(state));
        return lower[i];
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) TallMachine.removeTop(level, pos);
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> blockEntityType) {
        if (level.isClientSide) return (l, p, s, be) -> {
            if (be instanceof KitMachine machine) machine.clientTick();
        };
        return super.getTicker(level, state, blockEntityType);
    }
}
