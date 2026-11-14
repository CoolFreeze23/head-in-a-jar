package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The first generator. Feed it meat (use, or a hopper); it powers the machines touching it. Drawn as the Volta press:
 * two cells tall (a hidden top cell above it), a glass pan of meat, a press that sinks as each piece burns.
 */
public class GalvanicPileBlock extends BaseEntityBlock implements com.coolfreeze23.headinajar.machine.TallMachine {
    public static final MapCodec<GalvanicPileBlock> CODEC = simpleCodec(GalvanicPileBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<com.coolfreeze23.headinajar.machine.MachineLamp> LAMP =
            com.coolfreeze23.headinajar.machine.MachineLamp.LAMP;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    /** Stack top, press, rods, terminals and yoke; the screw, bar and grips at rest. */
    private static final VoxelShape UPPER = net.minecraft.world.phys.shapes.Shapes.or(Block.box(0, 0, 0, 16, 12, 16), Block.box(0, 12, 7, 16, 16, 9));

    public GalvanicPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(LAMP, com.coolfreeze23.headinajar.machine.MachineLamp.OFF));
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind topKind() {
        return com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind.GALVANIC_PILE;
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return UPPER;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAMP);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GalvanicPileBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.PILE_BE.get(), level.isClientSide ? (l, p, s, pile) -> pile.clientTick() : GalvanicPileBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GalvanicPileBlockEntity pile)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (GalvanicPileBlockEntity.fuelValue(stack) <= 0) {
            // food it doesn't burn (cooked meat, bread): refused, naming what it does burn
            if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                if (!level.isClientSide) {
                    com.coolfreeze23.headinajar.feedback.Refusals.deny((ServerPlayer) player, com.coolfreeze23.headinajar.feedback.Refuser.at(pile), "pile.not_fuel",
                            stack.getHoverName(), net.minecraft.world.item.Items.ROTTEN_FLESH.getDescription(), net.minecraft.world.item.Items.SPIDER_EYE.getDescription());
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack left = pile.fuel().insertItem(0, stack.copy(), false);
            if (left.getCount() == stack.getCount()) {
                // nothing went in: the fuel slot is full, or holds another kind of meat
                ItemStack there = pile.fuel().getStackInSlot(0);
                com.coolfreeze23.headinajar.feedback.Refusals.deny((ServerPlayer) player, com.coolfreeze23.headinajar.feedback.Refuser.at(pile), "pile.full", there.getHoverName());
            } else {
                int fed = stack.getCount() - left.getCount();
                // how much went in and what it is worth: the pile shows no gauge of its own
                player.displayClientMessage(Component.translatable("message.headinajar.pile_fed", fed, stack.getHoverName(),
                        fed * GalvanicPileBlockEntity.fuelValue(stack), pile.fuelLeft()), true);
                stack.setCount(left.getCount());
                // the lump drops into the pan; the sound's hit is on its landing
                level.playSound(null, pos, com.coolfreeze23.headinajar.machine.MachineSounds.PILE_PLOP.get(), SoundSource.BLOCKS, 1,
                        com.coolfreeze23.headinajar.machine.MachineSounds.pitch("pile.plop", 1, level.random));
                pile.kit().event(com.coolfreeze23.headinajar.machine.MachineKit.EV_PLOP, 0);
                // a chair beside it is now a powered chair ("Take a Seat")
                com.coolfreeze23.headinajar.clarity.TakeASeat.pileChanged((ServerPlayer) player, level, pos);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GalvanicPileBlockEntity pile) {
            // what it holds, what its fuel is still worth, and where the power goes (an empty buffer while burning is normal)
            ItemStack fuel = pile.fuel().getStackInSlot(0);
            player.displayClientMessage(fuel.isEmpty() && pile.burning() <= 0
                    ? Component.translatable("message.headinajar.pile_status_empty", pile.energy().getEnergyStored(), GalvanicPileBlockEntity.CAPACITY,
                            net.minecraft.world.item.Items.ROTTEN_FLESH.getDescription(), net.minecraft.world.item.Items.SPIDER_EYE.getDescription())
                    : Component.translatable("message.headinajar.pile_status_burning", pile.energy().getEnergyStored(), GalvanicPileBlockEntity.CAPACITY,
                            fuel.getCount(), pile.fuelLeft()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GalvanicPileBlockEntity pile) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pile.fuel().getStackInSlot(0));
        }
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) com.coolfreeze23.headinajar.machine.TallMachine.removeTop(level, pos);
    }
}
