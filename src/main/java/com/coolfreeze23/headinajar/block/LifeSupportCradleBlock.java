package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A padded stand with tubes that keeps a head alive and safe. Use it to dock or take your head. */
public class LifeSupportCradleBlock extends BaseEntityBlock {
    public static final MapCodec<LifeSupportCradleBlock> CODEC = simpleCodec(LifeSupportCradleBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 5, 15), Block.box(3, 5, 3, 13, 16, 13));

    public LifeSupportCradleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
        return new LifeSupportCradleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.CRADLE_BE.get(), LifeSupportCradleBlockEntity::serverTick);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                                  Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LifeSupportCradleBlockEntity cradle)) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer server && player.getUUID().equals(cradle.owner()) && FormActions.fitGoggles(server, stack, null, cradle)) {
            stack.shrink(1);
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        if (stack.is(ModRegistry.SALINE_BOTTLE.get())) {
            if (!level.isClientSide && cradle.saline().fill(new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), 250),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) == 250) {
                cradle.saline().fill(new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), 250),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                stack.consume(1, player);
                player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LifeSupportCradleBlockEntity cradle)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        if (cradle.isEmpty()) {
            if (!FormActions.dockCarried(server, cradle)) player.displayClientMessage(Component.translatable("message.headinajar.cradle_empty"), true);
        } else if (!FormActions.undock(server, cradle)) {
            player.displayClientMessage(Component.translatable("message.headinajar.cradle_holds", cradle.ownerName()), true);
        }
        if (cradle.isEmpty() || !player.getUUID().equals(cradle.owner())) {
            player.displayClientMessage(Component.translatable("message.headinajar.cradle_status", cradle.energy().getEnergyStored(), cradle.saline().getFluidAmount()), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LifeSupportCradleBlockEntity cradle) {
            cradle.ejectHead();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
