package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachine;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Tesla Node as a small Tesla coil on the kit: two cells tall, facing, the lamp on its drum. */
public class TeslaNodeBlock extends PowerBlock<PowerBlockEntities.TeslaNode> implements TallMachine {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 9, 15), Block.box(3, 8, 3, 13, 16, 13));
    private static final VoxelShape UPPER = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(1, 2, 1, 15, 14, 15));

    public TeslaNodeBlock(Properties properties) {
        super(properties, () -> ModRegistry.TESLA_BE.get(), PowerBlockEntities.TeslaNode::new, PowerBlockEntities.TeslaNode::kitTick, LOWER);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineLamp.LAMP, MachineLamp.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MachineLamp.LAMP);
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
        return MachineTopBlock.Kind.TESLA_NODE;
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return UPPER;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TallMachine.rotate(LOWER, facingOf(state));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) TallMachine.removeTop(level, pos);
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        if (level.isClientSide) {
            return type == ModRegistry.TESLA_BE.get() ? (l, p, s, be) -> ((KitMachine) be).clientTick() : null;
        }
        return super.getTicker(level, state, type);
    }
}
