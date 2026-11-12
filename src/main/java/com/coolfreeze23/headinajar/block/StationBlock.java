package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.machine.TallMachine;
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
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A relay station on the kit (the Relay Masts, two cells tall; the Signal Jammer, one): facing, the lamp, its parts
 * drawn by the kit's renderer. A null upper shape makes it a one-block machine with no top cell.
 */
public class StationBlock extends PowerBlock<PowerBlockEntities.Station> implements TallMachine {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final Supplier<BlockEntityType<PowerBlockEntities.Station>> type;
    private final VoxelShape lowerNorth, upperNorth;
    private final MachineTopBlock.Kind kind;
    private final boolean lampAbove;
    private final VoxelShape[] lower = new VoxelShape[4];

    public StationBlock(Properties properties, Supplier<BlockEntityType<PowerBlockEntities.Station>> type,
                        BiFunction<BlockPos, BlockState, PowerBlockEntities.Station> factory, VoxelShape lowerNorth, VoxelShape upperNorth,
                        MachineTopBlock.Kind kind, boolean lampAbove) {
        super(properties, type, factory, PowerBlockEntities.Station::kitTick, lowerNorth);
        this.type = type;
        this.lowerNorth = lowerNorth;
        this.upperNorth = upperNorth;
        this.kind = kind;
        this.lampAbove = lampAbove;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineLamp.LAMP, MachineLamp.OFF));
    }

    /** Two cells tall (a top cell above it). */
    public boolean tall() {
        return upperNorth != null;
    }

    @Override
    public boolean hasTop() {
        return tall();
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
        return kind;
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return upperNorth == null ? net.minecraft.world.phys.shapes.Shapes.empty() : upperNorth;
    }

    @Override
    public boolean lampAbove() {
        return lampAbove;
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
        if (tall() && !state.is(newState.getBlock())) TallMachine.removeTop(level, pos);
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> blockEntityType) {
        if (level.isClientSide) return blockEntityType == type.get() ? (l, p, s, be) -> ((KitMachine) be).clientTick() : null;
        return super.getTicker(level, state, blockEntityType);
    }
}
