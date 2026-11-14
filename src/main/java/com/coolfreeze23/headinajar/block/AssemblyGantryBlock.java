package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.TallMachine;
import com.coolfreeze23.headinajar.registry.ModRegistry;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Assembly Gantry: one cell, a louvred pedestal wearing the lamp, with its turntable's deck standing 4 px into the
 * cell above (the frame stands on it, at y + 1.25). The arm and the turntable are drawn by its renderer.
 */
public class AssemblyGantryBlock extends MachineBlock<AssemblyGantryBlockEntity> {
    /** Facing north: the pedestal, the hub, the turntable's deck (into the cell above), the module tray. */
    private static final VoxelShape NORTH = Shapes.or(box(1, 0, 1, 15, 14, 15), box(5, 14, 5, 11, 16, 11),
            box(3, 16, 3, 13, 20, 13), box(2, 14, 0, 14, 15, 2));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];
    /** The deck's height above the block's base, in blocks: where a frame stands. */
    public static final double DECK = 20 / 16.0;

    public AssemblyGantryBlock(Properties properties) {
        super(properties, () -> ModRegistry.GANTRY_BE.get(), AssemblyGantryBlockEntity::new, AssemblyGantryBlockEntity::serverTick, NORTH);
        registerDefaultState(defaultBlockState().setValue(MachineLamp.LAMP, MachineLamp.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MachineLamp.LAMP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = state.getValue(FACING).get2DDataValue();
        if (SHAPES[i] == null) SHAPES[i] = TallMachine.rotate(NORTH, state.getValue(FACING));
        return SHAPES[i];
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        if (level.isClientSide) return (l, p, s, be) -> {
            if (be instanceof KitMachine machine) machine.clientTick();
        };
        return super.getTicker(level, state, type);
    }
}
