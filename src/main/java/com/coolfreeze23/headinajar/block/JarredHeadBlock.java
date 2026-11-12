package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** A placed jarred head (villager heads trade). Breaking it gives the head back with everything it was. */
public class JarredHeadBlock extends BaseEntityBlock {
    public static final MapCodec<JarredHeadBlock> CODEC = simpleCodec(JarredHeadBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The plinth (its front, with the plaque and bell, toward where the head looks) and the jar on it, facing north. */
    private static final VoxelShape SHAPE_NORTH = net.minecraft.world.phys.shapes.Shapes.or(Block.box(0, 0, 2, 16, 7, 16),
            Block.box(0.85, 7, 0.85, 15.15, 16, 15.15));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    public JarredHeadBlock(Properties properties) {
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

    /** The plinth is the block's model; the jar and the head are drawn by the block entity's renderer. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The plinth under the jar's bounds; the lid and valve above the cell are drawn, not solid. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        int i = facing.get2DDataValue();
        // the plinth's front is toward where the head looks (the opposite of the block's facing)
        if (SHAPES[i] == null) SHAPES[i] = com.coolfreeze23.headinajar.machine.TallMachine.rotate(SHAPE_NORTH, facing.getOpposite());
        return SHAPES[i];
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return type == ModRegistry.JARRED_HEAD_BE.get() ? (l, p, s, be) -> ((JarredHeadBlockEntity) be).clientTick() : null;
        }
        return createTickerHelper(type, ModRegistry.JARRED_HEAD_BE.get(), JarredHeadBlockEntity::serverTick);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JarredHeadBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (head.entityType() == net.minecraft.world.entity.EntityType.VILLAGER && head.busy((ServerPlayer) player)) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny((ServerPlayer) player, com.coolfreeze23.headinajar.feedback.Refuser.at(head), "jarred_head.busy");
            return InteractionResult.CONSUME;
        }
        if (!head.trade((ServerPlayer) player) && head.entityType() != null) {
            player.displayClientMessage(Component.translatable("message.headinajar.jarred_head", head.entityType().getDescription()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof JarredHeadBlockEntity head) {
            // broken mid-trade: the trade so far is kept in the head, and the screen closes
            head.settle();
            return List.of(head.toItem());
        }
        return List.of();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }
}
