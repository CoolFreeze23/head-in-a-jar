package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Separation Chair: sit down (use) and sign the waiver to have your head removed. Sneak-use loads it without
 * sitting. A Replacement Blade used on it resets blade wear. Unbreakable while an operation runs.
 */
public class SeparationChairBlock extends BaseEntityBlock {
    public static final MapCodec<SeparationChairBlock> CODEC = simpleCodec(SeparationChairBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The seat and the backrest, the chair facing north (the backrest to the south), turned with the chair. */
    private static final java.util.Map<Direction, VoxelShape> SHAPES = new java.util.EnumMap<>(Direction.class);

    static {
        double[][] boxes = {{1, 0, 1, 15, 6, 10}, {1, 4, 10, 15, 17, 13}};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) shape = Shapes.or(shape, turned(b, facing));
            SHAPES.put(facing, shape);
        }
    }

    /** A box of the north-facing chair (pixels) turned to face {@code facing}. */
    private static VoxelShape turned(double[] b, Direction facing) {
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, b[1], 16 - z2, 16 - x1, b[4], 16 - z1);
            case WEST -> Block.box(z1, b[1], 16 - x2, z2, b[4], 16 - x1);
            case EAST -> Block.box(16 - z2, b[1], x1, 16 - z1, b[4], x2);
            default -> Block.box(x1, b[1], z1, x2, b[4], z2);
        };
    }

    public SeparationChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeparationChairBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModRegistry.CHAIR_BE.get(), SeparationChairBlockEntity::clientTick)
                : createTickerHelper(type, ModRegistry.CHAIR_BE.get(), SeparationChairBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModRegistry.REPLACEMENT_BLADE.get()) || !(level.getBlockEntity(pos) instanceof SeparationChairBlockEntity chair)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (chair.running()) {
            if (player instanceof ServerPlayer sp) com.coolfreeze23.headinajar.feedback.Refusals.deny(sp, chair, "chair.blade_busy");
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            chair.setWear(0);
            stack.consume(1, player);
            level.playSound(null, pos, com.coolfreeze23.headinajar.fx.HouseSounds.NEW_BLADE.get(), SoundSource.BLOCKS, 1, 1.2f);
            player.displayClientMessage(Component.translatable("dolores.headinajar.new_blade"), true);
            chair.voiceRefusal("new_blade");
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SeparationChairBlockEntity chair)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        if (chair.running()) {
            // nothing may be taken out or put in until it is over; whoever clicks is told so
            com.coolfreeze23.headinajar.feedback.Refusals.deny(server, chair, "chair.in_use");
            return InteractionResult.CONSUME;
        }
        // a jar can't sit in the chair, but it may read the chair's panel
        boolean jar = player.getData(ModRegistry.JAR_STATE).form() == com.coolfreeze23.headinajar.form.Form.JAR;
        if (player.isShiftKeyDown() || chair.seated() != null || jar) {
            server.openMenu(chair, pos);
            return InteractionResult.CONSUME;
        }
        // someone already on one of the company's machines or shells gets down first: the chair never pulls them off
        net.minecraft.world.entity.Entity ridden = server.getVehicle();
        if (ridden != null && com.coolfreeze23.headinajar.HeadInAJar.MOD_ID.equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ridden.getType()).getNamespace())) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(server, chair, "chair.riding", ridden.getDisplayName());
            return InteractionResult.CONSUME;
        }
        // a seat left empty in this chair by an earlier try goes before a new one is made
        for (SeatEntity stale : level.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(0.5),
                s -> s.getPassengers().isEmpty() && s.chairPos().equals(pos))) {
            stale.discard();
        }
        // sitting down is always the player's own action: nobody can be put in the chair by someone else
        SeatEntity seat = SeatEntity.at(level, pos, state.getValue(FACING));
        if (server.startRiding(seat)) {
            server.openMenu(chair, pos);
            return InteractionResult.CONSUME;
        }
        LOGGER.debug("Separation Chair at {}: {} could not sit: {}", pos, server.getGameProfile().getName(), whyNotSeated(server, seat));
        seat.discard();
        com.coolfreeze23.headinajar.feedback.Refusals.deny(server, chair, "chair.no_seat");
        return InteractionResult.CONSUME;
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Why a player could not ride a fresh seat, as far as the game lets it be seen (for the debug log). */
    static String whyNotSeated(ServerPlayer player, SeatEntity seat) {
        if (player.getVehicle() == seat) return "already on this seat";
        if (!seat.isAddedToLevel()) return "the seat did not join the level";
        if (!seat.getPassengers().isEmpty()) return "the seat already carries " + seat.getFirstPassenger().getName().getString();
        if (player.isShiftKeyDown()) return "sneaking";
        if (player.getVehicle() != null) return "riding " + player.getVehicle().getName().getString();
        return "the boarding cooldown after getting off something, or the mount event was cancelled";
    }

    /** Hitting or mining a chair mid-operation gets an answer; it can't be broken until it is done. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SeparationChairBlockEntity chair && chair.running()
                && sp.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.CREATIVE) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(sp, chair, "chair.no_break");
        }
        super.attack(state, level, pos, player);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SeparationChairBlockEntity chair && chair.running()) return 0;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SeparationChairBlockEntity chair) {
            // a chair broken mid-operation gives back the parts in use too
            chair.dropAll();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
