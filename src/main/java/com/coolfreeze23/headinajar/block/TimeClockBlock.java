package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.SiteIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Time Clock: a brass punch clock on a post with a card rack and a bell. It makes a site (a box 16 out from it, 8 up
 * and 8 down) where its staff work, are paid at dawn and stand in line off shift. It wears the Noggin lamp (READY with
 * staff and nothing wrong, WAIT when one is held up, WORK while any is on the job, FAULT for a refusal). Two clocks
 * need {@code staff.clockSpacing} blocks between them on the level; a closer one is refused as it is placed.
 */
public class TimeClockBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Cards in the rack (the slips drawn: slip n while cards >= n). */
    public static final IntegerProperty CARDS = IntegerProperty.create("cards", 0, TimeClockBlockEntity.RACK);
    /** The stand and rack, the cabinet with its dial, throat and lever, the awning (facing north; the bell is not in it). */
    private static final VoxelShape NORTH = Shapes.or(Block.box(1, 0, 5, 15, 7, 13), Block.box(1, 7, 4, 15, 15, 12), Block.box(1, 15, 3, 15, 16, 13));
    private final VoxelShape[] shapes = new VoxelShape[4];

    public TimeClockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineLamp.LAMP, MachineLamp.OFF).setValue(CARDS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MachineLamp.LAMP, CARDS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        int i = f.get2DDataValue();
        if (shapes[i] == null) shapes[i] = com.coolfreeze23.headinajar.machine.TallMachine.rotate(NORTH, f);
        return shapes[i];
    }

    /** No path runs through the clock (its shape is less than a block, so vanilla would plan straight through it). */
    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            int spacing = Config.get(Config.CLOCK_SPACING, 8);
            if (SiteIndex.get(level).tooClose(context.getClickedPos(), spacing) != null) {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    Refusals.deny(player, "clock.too_close", spacing);
                    // the game placed the clock ahead of the server and took it from the hand: put the hand's stack back now,
                    // not at the next inventory resend
                    int slot = context.getHand() == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
                    player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, player.getItemInHand(context.getHand()).copy()));
                }
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof TimeClockBlockEntity clock)) return;
        if (placer instanceof Player p) clock.setOwner(p);
        clock.registerSite();
        if (placer instanceof ServerPlayer p) com.coolfreeze23.headinajar.clarity.Memos.send(p, com.coolfreeze23.headinajar.clarity.Memos.Memo.FIRST_CLOCK);
    }

    /** A Clipboard used on the clock is the Clipboard's to answer (it binds); anything else opens the screen. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(WorkRegistry.CLIPBOARD.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TimeClockBlockEntity clock) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        clock.use(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TimeClockBlockEntity clock) clock.checkPower();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof TimeClockBlockEntity clock) {
            clock.broken(server);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimeClockBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != WorkRegistry.TIME_CLOCK_BE.get()) return null;
        if (level.isClientSide) return (l, p, s, be) -> ((TimeClockBlockEntity) be).clientTick();
        return (l, p, s, be) -> ((TimeClockBlockEntity) be).serverTick();
    }
}
