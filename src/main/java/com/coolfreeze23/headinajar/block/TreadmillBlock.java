package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Corporate Wellness Treadmill: a belt two cells long, placed like a bed. The foot is where the player clicked and
 * holds the block entity (it makes and pushes the FE); the head lies away from the player and carries the console and
 * the Wellness Meter. {@code FACING} points from the foot to the head; a walker stands on the belt at the seam, facing
 * the head. Breaking either half takes the other with it and drops one Treadmill (the foot's loot).
 */
public class TreadmillBlock extends BaseEntityBlock {
    public static final MapCodec<TreadmillBlock> CODEC = simpleCodec(TreadmillBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final IntegerProperty DIAL = IntegerProperty.create("dial", 0, 4);

    /** The belt's top, in blocks above the cell's floor (the art's deck: 6 px). Walkers stand here. */
    public static final double DECK = 6 / 16.0;
    /** From the seam to the head's motor hood, in blocks (a walker's body must not reach into it). */
    public static final double HOOD_CLEARANCE = 10 / 16.0;
    /** The height of the console end's wall for collisions, in pixels (a block's collision may reach 24). */
    public static final double CONSOLE_WALL = 24;

    /** The art's collision and outline, Java cell coordinates, facing north (the head is the -Z cell). */
    private static final VoxelShape FOOT_NORTH = Block.box(0, 0, 0, 16, 6, 16);
    private static final VoxelShape HEAD_NORTH = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(0, 0, 0, 16, 8, 6), Block.box(0, 8, 2, 16, 16, 5));
    private static final Map<Direction, VoxelShape> HEAD_SHAPES = new EnumMap<>(Direction.class);
    /**
     * What a body bumps into at the head: the deck, and over the hood's footprint one wall as high as a block can be
     * (24 px). The drawn hood (8 px) and console (16 px) were a stair a body walked up and over; a ridden body steps a
     * whole block, and none steps more than that beside a belt ({@link Treadmills#stepBesideBelt}), so it stops at the
     * console or turns off the side. The outline stays the art's.
     */
    private static final VoxelShape HEAD_COLLISION_NORTH = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(0, 0, 0, 16, CONSOLE_WALL, 6));
    private static final Map<Direction, VoxelShape> HEAD_COLLISIONS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            HEAD_SHAPES.put(d, turn(HEAD_NORTH, d));
            HEAD_COLLISIONS.put(d, turn(HEAD_COLLISION_NORTH, d));
        }
    }

    public enum Part implements StringRepresentable {
        FOOT, HEAD;

        @Override
        public String getSerializedName() {
            return this == FOOT ? "foot" : "head";
        }
    }

    public TreadmillBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.FOOT)
                .setValue(RUNNING, false).setValue(DIAL, 0));
    }

    /** A shape modelled facing north, turned to face {@code d}. */
    private static VoxelShape turn(VoxelShape north, Direction d) {
        VoxelShape[] out = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            VoxelShape box = switch (d) {
                case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
                case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
                case WEST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
                default -> Shapes.box(x0, y0, z0, x1, y1, z1);
            };
            out[0] = Shapes.or(out[0], box);
        });
        return out[0].optimize();
    }

    /** The foot of the belt this cell belongs to (itself for the foot). */
    public static BlockPos foot(BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.FOOT ? pos : pos.relative(state.getValue(FACING).getOpposite());
    }

    /** The other half's cell. */
    private static Direction toOther(BlockState state) {
        return state.getValue(PART) == Part.FOOT ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, RUNNING, DIAL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == Part.FOOT ? FOOT_NORTH : HEAD_SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == Part.FOOT ? FOOT_NORTH : HEAD_COLLISIONS.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // --- placing and breaking, as a bed ------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        if (level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)) {
            return defaultBlockState().setValue(FACING, facing);
        }
        // never a silent failure: the head's cell is taken
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            Vec3 at = Vec3.atCenterOf(head);
            Refusals.deny(player, new Refuser() {
                @Override
                public Vec3 refusalOrigin() {
                    return at;
                }

                @Override
                public void showRefusal(ServerPlayer who, String reason) {
                    who.serverLevel().sendParticles(who, ParticleTypes.SMOKE, true, at.x, at.y + 0.5, at.z, 6, 0.25, 0.15, 0.25, 0.01);
                }
            }, "treadmill.no_room");
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, Part.HEAD), 3);
        level.blockUpdated(pos, Blocks.AIR);
        state.updateNeighbourShapes(level, pos, 3);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TreadmillBlockEntity belt) belt.setOwner(player.getUUID());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == toOther(state)) {
            // the other half went: this one goes too (with its drops, so breaking the head still drops the belt)
            return neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART) && neighbour.getValue(FACING) == state.getValue(FACING)
                    ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // breaking the head takes the foot with it; in creative, or without a pickaxe, without the foot's drop (the item)
        if (!level.isClientSide && state.getValue(PART) == Part.HEAD && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            BlockPos foot = pos.relative(toOther(state));
            BlockState other = level.getBlockState(foot);
            if (other.is(this) && other.getValue(PART) == Part.FOOT) {
                level.setBlock(foot, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, foot, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        BlockPos foot = foot(state, pos);
        return net.minecraft.util.Mth.getSeed(foot.getX(), pos.getY(), foot.getZ());
    }

    // --- use -----------------------------------------------------------------------------------------------------

    /** Empty hand on either half: who is walking and what it makes, or why what stands on it won't. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(foot(state, pos)) instanceof TreadmillBlockEntity belt) {
            belt.report(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // --- the block entity (on the foot only) ----------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.FOOT ? new TreadmillBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.FOOT) return null;
        return createTickerHelper(type, ModRegistry.TREADMILL_BE.get(), TreadmillBlockEntity::serverTick);
    }
}
