package com.coolfreeze23.headinajar.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A horizontal-facing machine: buckets and bottles go into its fluid handler, use opens its menu, and its items drop
 * when it is broken. The block entity ticks on the server with {@code ticker}.
 */
public class MachineBlock<T extends BlockEntity> extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Supplier<BlockEntityType<T>> type;
    private final BlockEntityTicker<T> ticker;
    private final BiFunction<BlockPos, BlockState, T> factory;
    private final VoxelShape shape;

    public MachineBlock(Properties properties, Supplier<BlockEntityType<T>> type, BiFunction<BlockPos, BlockState, T> factory,
                        BlockEntityTicker<T> ticker, VoxelShape shape) {
        super(properties);
        this.type = type;
        this.factory = factory;
        this.ticker = ticker;
        this.shape = shape == null ? Shapes.block() : shape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("machine blocks are not data-driven");
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
        return shape;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <E extends BlockEntity> BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> blockEntityType) {
        return level.isClientSide || ticker == null ? null : createTickerHelper(blockEntityType, type.get(), ticker);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, hit.getDirection());
        if (tank == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        // a Saline Bottle pours its 250 mB into a tank that takes saline (the Body Printer)
        if (stack.is(com.coolfreeze23.headinajar.registry.ModRegistry.SALINE_BOTTLE.get())) {
            var bottle = new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), BOTTLE_MB);
            if (tank.fill(bottle, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) == BOTTLE_MB) {
                if (!level.isClientSide) {
                    tank.fill(bottle, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                        com.coolfreeze23.headinajar.form.FormActions.give((ServerPlayer) player, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
                    }
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        // a bucket or bottle of something this tank doesn't take: say so instead of opening the menu
        var held = FluidUtil.getFluidContained(stack);
        if (held.isPresent() && !held.get().isEmpty() || stack.is(com.coolfreeze23.headinajar.registry.ModRegistry.SALINE_BOTTLE.get())) {
            if (!level.isClientSide) {
                var fluid = held.isPresent() && !held.get().isEmpty() ? held.get()
                        : new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get(), BOTTLE_MB);
                boolean takes = false;
                for (int i = 0; i < tank.getTanks(); i++) takes |= tank.isFluidValid(i, fluid);
                com.coolfreeze23.headinajar.feedback.Refusals.deny((ServerPlayer) player, com.coolfreeze23.headinajar.feedback.Refuser.at(level.getBlockEntity(pos)),
                        takes ? "machine.tank_full" : "machine.wrong_fluid", fluid.getHoverName(), getName());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** What a Saline Bottle holds. */
    public static final int BOTTLE_MB = 250;

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MenuProvider menu) {
            if (!level.isClientSide) ((ServerPlayer) player).openMenu(menu, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HasItems machine) {
            IItemHandler items = machine.items();
            for (int i = 0; i < items.getSlots(); i++) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(i));
            for (ItemStack stack : machine.consumed()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
