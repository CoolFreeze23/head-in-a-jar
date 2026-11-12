package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A wall notice. Use it to cycle through the twelve company safety notices; each one reads out in chat. */
public class SafetyPlacardBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty NOTICE = IntegerProperty.create("notice", 0, 11);

    public SafetyPlacardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(NOTICE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NOTICE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) face = context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return level.getBlockState(pos.relative(facing.getOpposite())).isFaceSturdy(level, pos.relative(facing.getOpposite()), facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
    }

    /** The framed board, its back on the wall: the whole face of the block, 3 px deep (facing north: the wall to the south). */
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 13, 16, 16, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isVertical()) return Shapes.block();
        int i = facing.get2DDataValue();
        if (SHAPES[i] == null) SHAPES[i] = com.coolfreeze23.headinajar.machine.TallMachine.rotate(SHAPE_NORTH, facing);
        return SHAPES[i];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int next = (state.getValue(NOTICE) + 1) % 12;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(NOTICE, next), 3);
            player.displayClientMessage(Component.translatable("placard.headinajar." + next), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
