package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import com.coolfreeze23.headinajar.Config;
import net.minecraft.server.level.ServerLevel;
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
            Block.box(0.85, 7, 0.85, 15.15, 16, 15.15),
            // the bowl's shelf at the front corner (it reaches 2 px past the block), so a click on the bowl lands here
            Block.box(13, 6, -2, 16, 8, 2));
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

    /**
     * Something held and used on the head. In order: a sampling tool draws its daily sample (the Living Library); a
     * Nutrient Broth Bucket fills its bowl (the first lunch hires a trophy); this species' desk item does its desk job; a
     * cake is a treat; then a villager head trades whatever else is in hand (an emerald included: a villager on a plinth
     * is never paid by hand); this species' wage pays it for today (the first time, that hires it); anything else that is
     * some species' wage is refused as the wrong wage; and anything else does what it always did.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                                  net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head) || head.entityType() == null) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        var type = head.entityType();
        var ok = net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        // (1) the Living Library
        if (stack.getItem() instanceof com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem) {
            if (level.isClientSide) return ok;
            if (HeadDonor.plinth((ServerPlayer) player, stack, hand, head)) return ok;
        }
        // (2) lunch
        if (stack.is(com.coolfreeze23.headinajar.registry.ModFluids.BROTH.bucket.get())) {
            if (player instanceof ServerPlayer server) DeskJobs.feed((ServerLevel) level, head, server, stack, hand);
            return ok;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem bucket && bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
            if (player instanceof ServerPlayer server) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(head), "desk.wrong_bucket", stack.getHoverName());
            }
            return ok;
        }
        // (3) the desk job's own item
        if (Config.get(Config.DESK_JOBS, true) && DeskJobs.deskItem(head, stack)) {
            if (player instanceof ServerPlayer server) DeskJobs.useItem((ServerLevel) level, head, server, stack, hand);
            return ok;
        }
        // (4) cake (used up, never placed on the plinth)
        if (stack.is(net.minecraft.world.item.Items.CAKE)) {
            if (player instanceof ServerPlayer server) DeskJobs.cake((ServerLevel) level, head, server, stack);
            return ok;
        }
        if (type == net.minecraft.world.entity.EntityType.VILLAGER) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // (5) the wage, and the wrong wage
        var wage = com.coolfreeze23.headinajar.staff.StaffTable.wage(type);
        boolean someWage = com.coolfreeze23.headinajar.staff.StaffTable.anyWage(stack);
        if (wage.matches(stack)) {
            if (player instanceof ServerPlayer server) com.coolfreeze23.headinajar.staff.StaffBlocks.payByHand(head, server, stack);
            return ok;
        }
        if (someWage) {
            if (player instanceof ServerPlayer server) {
                var refuser = com.coolfreeze23.headinajar.feedback.Refuser.at(head);
                if (wage.none()) com.coolfreeze23.headinajar.feedback.Refusals.deny(server, refuser, "staff.no_wage", type.getDescription());
                else {
                    var file = head.staff();
                    Component name = file != null ? com.coolfreeze23.headinajar.staff.Staff.name(file) : type.getDescription();
                    com.coolfreeze23.headinajar.feedback.Refusals.deny(server, refuser, "staff.wrong_wage", name,
                            com.coolfreeze23.headinajar.staff.StaffBlocks.wageName(wage));
                }
            }
            return ok;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Use with an empty hand (or anything a head takes no notice of): the bell. A villager head opens its trades as it
     * always did (and, fed, with an emerald in hand, reads its best offer aloud); every other head with lines rings and
     * answers; a head of a creature with no lines says what it always said.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        if (head.entityType() == net.minecraft.world.entity.EntityType.VILLAGER) {
            if (head.busy(server)) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(head), "jarred_head.busy");
                return InteractionResult.CONSUME;
            }
            boolean emerald = player.getMainHandItem().is(net.minecraft.world.item.Items.EMERALD);
            if (emerald && head.fed() && Config.get(Config.DESK_JOBS, true)) DeskJobs.bestOffer((ServerLevel) level, head, server);
            else if (player.getMainHandItem().isEmpty()) DeskJobs.ring((ServerLevel) level, head, server);
            head.trade(server);
            return InteractionResult.CONSUME;
        }
        if (head.entityType() != null && !DeskJobs.ring((ServerLevel) level, head, server)) {
            player.displayClientMessage(Component.translatable("message.headinajar.jarred_head", head.entityType().getDescription()), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Broken by a player: a member of staff says goodbye where it stood (a trophy says nothing). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head && head.entityType() != null
                && head.staff() != null && head.staff().staff()) {
            Component[] bye = HeadSpeak.goodbye(head.entityType());
            HeadSpeak.goodbyeAt((ServerLevel) level, pos, bye[0], bye[1]);
        }
        return super.playerWillDestroy(level, pos, state, player);
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
