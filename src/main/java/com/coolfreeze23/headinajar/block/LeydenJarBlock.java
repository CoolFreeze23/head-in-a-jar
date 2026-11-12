package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.machine.TallMachine;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Leyden Jar on the kit: a glass battery jar in a carrying cage, two cells tall. Its charge shows as lit cells in
 * three windows ({@code level} 0-4), the lamp says whether it is charging, and the knob sparks when it is full. It is
 * the same battery as before: nothing moves, so it has no renderer.
 */
public class LeydenJarBlock extends PowerBlock<PowerBlockEntities.LeydenJar> implements TallMachine {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty LEVEL = MachineLamp.level("level");
    private static final VoxelShape LOWER = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape UPPER = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(6, 0, 0, 10, 3, 2), Block.box(3, 4, 3, 13, 6, 13),
            Block.box(5, 6, 5, 11, 8, 11), Block.box(6, 8, 6, 10, 13, 10), Block.box(0, 0, 0, 2, 6, 16), Block.box(14, 0, 0, 16, 6, 16),
            Block.box(0, 6, 7, 2, 16, 9), Block.box(14, 6, 7, 16, 16, 9), Block.box(0, 14, 7, 16, 16, 9));

    public LeydenJarBlock(Properties properties) {
        super(properties, () -> ModRegistry.LEYDEN_BE.get(), PowerBlockEntities.LeydenJar::new, PowerBlockEntities.LeydenJar::kitTick, LOWER);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineLamp.LAMP, MachineLamp.OFF).setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MachineLamp.LAMP, LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.LEYDEN_JAR;
    }

    /** The lamp is on the dark glass band, above the cell line; the top cell follows it. */
    @Override
    public boolean lampAbove() {
        return true;
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
