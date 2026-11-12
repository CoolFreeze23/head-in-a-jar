package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.machine.TallMachine;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Turret Pedestal as a searchlight-style gun mount carrying the real jar at full size: two cells tall. */
public class TurretPedestalBlock extends HeadHolderBlock<TurretPedestalBlockEntity> implements TallMachine {
    private static final VoxelShape LOWER = Block.box(1, 0, 1, 15, 16, 15), UPPER = Block.box(1, 0, 1, 15, 14, 15);

    public TurretPedestalBlock(Properties properties) {
        super(properties, () -> ModRegistry.TURRET_BE.get(), TurretPedestalBlockEntity::new, TurretPedestalBlockEntity::kitTick, LOWER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MachineLamp.LAMP);
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.TURRET_PEDESTAL;
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return UPPER;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LOWER;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) TallMachine.removeTop(level, pos);
    }
}
