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
 * The Employee Mount as a small cubicle, the kit's one-block variant: the lamp, the client's tick for its parts and a
 * shape turned with its facing, but no top cell (its 19 px stand in its own cell).
 */
public class EmployeeMountBlock extends HeadHolderBlock<EmployeeMountBlockEntity> {
    /** The partition, the rail, the desk, the jar on its foot, the phone, the tray, the lamp, the stamp (facing north). */
    private static final VoxelShape NORTH = Shapes.or(Block.box(1, 0, 13, 15, 18, 16), Block.box(0, 18, 12, 16, 19, 15), Block.box(0, 4, 0, 16, 6, 12),
            Block.box(4, 6, 4, 12, 17, 12), Block.box(10, 6, 1, 16, 11, 4), Block.box(1, 6, 1, 5, 7, 4), Block.box(12, 6, 8, 16, 11, 12),
            Block.box(0, 6, 6, 4, 12, 9));
    private final VoxelShape[] shapes = new VoxelShape[4];

    public EmployeeMountBlock(Properties properties) {
        super(properties, () -> ModRegistry.MOUNT_BE.get(), EmployeeMountBlockEntity::new, EmployeeMountBlockEntity::kitTick, NORTH);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MachineLamp.LAMP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        int i = facing.get2DDataValue();
        if (shapes[i] == null) shapes[i] = TallMachine.rotate(NORTH, facing);
        return shapes[i];
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        if (level.isClientSide) return type == ModRegistry.MOUNT_BE.get() ? (l, p, s, be) -> ((KitMachine) be).clientTick() : null;
        return super.getTicker(level, state, type);
    }
}
