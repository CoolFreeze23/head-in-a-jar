package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/** The first generator. Feed it meat (use, or a hopper); it powers the machines touching it. */
public class GalvanicPileBlock extends BaseEntityBlock {
    public static final MapCodec<GalvanicPileBlock> CODEC = simpleCodec(GalvanicPileBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.5f, 0.9f, 1.0f), 0.6f);

    public GalvanicPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
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
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.PILE_BE.get(), GalvanicPileBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (GalvanicPileBlockEntity.fuelValue(stack) <= 0 || !(level.getBlockEntity(pos) instanceof GalvanicPileBlockEntity pile)) {
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
                level.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 1, 1);
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
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            level.addParticle(SPARK, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + random.nextDouble() * 0.9, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0, 0);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GalvanicPileBlockEntity pile) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pile.fuel().getStackInSlot(0));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
