package com.coolfreeze23.headinajar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** A block that holds a jarred head (Turret Pedestal, Employee Mount): use it with a head to fit one, empty-handed to work it. */
public class HeadHolderBlock<T extends BlockEntity & HeadHolderBlock.Holder> extends MachineBlock<T> {
    public interface Holder {
        void use(ServerPlayer player, ItemStack held);

        default void placedBy(LivingEntity placer) {
        }
    }

    public HeadHolderBlock(Properties properties, Supplier<BlockEntityType<T>> type, BiFunction<BlockPos, BlockState, T> factory,
                           BlockEntityTicker<T> ticker, VoxelShape shape) {
        super(properties, type, factory, ticker, shape);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof Holder holder) {
            if (!level.isClientSide) holder.use((ServerPlayer) player, stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof Holder holder) {
            if (!level.isClientSide) holder.use((ServerPlayer) player, ItemStack.EMPTY);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** The turret's head turns on the client's own tick (so its speed doesn't follow the frame rate). */
    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> blockEntityType) {
        if (level.isClientSide) {
            return createTickerHelper(blockEntityType, com.coolfreeze23.headinajar.registry.ModRegistry.TURRET_BE.get(), TurretPedestalBlockEntity::clientTick);
        }
        return super.getTicker(level, state, blockEntityType);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof Holder holder) holder.placedBy(placer);
    }
}
