package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
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

/**
 * A wingback armchair that keeps a head alive and safe, breathing for it. Use it to dock or take your head. Two cells
 * tall (the hidden top cell holds the back, the headrest and the bag); a jar can climb into its seat.
 */
public class LifeSupportCradleBlock extends BaseEntityBlock implements com.coolfreeze23.headinajar.machine.TallMachine {
    public static final MapCodec<LifeSupportCradleBlock> CODEC = simpleCodec(LifeSupportCradleBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty SALINE = com.coolfreeze23.headinajar.machine.MachineLamp.level("saline");
    /** What is drawn and clicked: the whole chair. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape UPPER = Shapes.or(Block.box(0, 0, 13, 16, 12, 16), Block.box(1, 0, 1, 15, 8, 15), Block.box(9, 0, 9, 13, 12, 13));
    /** What collides: the base under the cushion and the back; the seat is free for a jar to stand in. */
    private static final VoxelShape SOLID = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(0, 6, 13, 16, 16, 16));
    private static final VoxelShape UPPER_SOLID = Block.box(0, 0, 13, 16, 12, 16);
    private final VoxelShape[] solid = new VoxelShape[4];

    public LifeSupportCradleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(com.coolfreeze23.headinajar.machine.MachineLamp.LAMP, com.coolfreeze23.headinajar.machine.MachineLamp.OFF).setValue(SALINE, 0));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, com.coolfreeze23.headinajar.machine.MachineLamp.LAMP, SALINE);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind topKind() {
        return com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind.LIFE_SUPPORT_CRADLE;
    }

    @Override
    public boolean lampAbove() {
        return true;
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return UPPER;
    }

    @Override
    public VoxelShape upperCollisionNorth() {
        return UPPER_SOLID;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = facingOf(state).get2DDataValue();
        if (solid[i] == null) solid[i] = com.coolfreeze23.headinajar.machine.TallMachine.rotate(SOLID, facingOf(state));
        return solid[i];
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
        return createTickerHelper(type, ModRegistry.CRADLE_BE.get(), level.isClientSide ? (l, p, s, c) -> c.clientTick() : LifeSupportCradleBlockEntity::kitTick);
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
            } else if (player instanceof ServerPlayer server) {
                Refusals.deny(server, Refuser.at(cradle), "cradle.full");
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // a bucket of something else: the tank only takes saline
        var carried = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack);
        if (carried.isPresent() && !carried.get().isEmpty() && !carried.get().getFluid().isSame(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get())) {
            if (player instanceof ServerPlayer server) Refusals.deny(server, Refuser.at(cradle), "cradle.wrong_fluid", Component.translatable("fluid_type.headinajar.cranial_saline"));
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LifeSupportCradleBlockEntity cradle)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        Component satchel = Component.translatable("item.headinajar.head_satchel");
        boolean wasEmpty = cradle.isEmpty();
        if (cradle.isEmpty()) {
            if (FormActions.state(server).form() == com.coolfreeze23.headinajar.form.Form.JAR) {
                // a jar climbs in: sitting in the Cradle, a swap leaves the head docked here
                if (!climbIn(server, pos)) Refusals.deny(server, Refuser.at(cradle), "cradle.blocked");
                return InteractionResult.CONSUME;
            }
            if (!FormActions.dockCarried(server, cradle)) Refusals.deny(server, Refuser.at(cradle), "cradle.empty", satchel);
        } else if (!FormActions.undock(server, cradle)) {
            if (player.getUUID().equals(cradle.owner())) Refusals.deny(server, Refuser.at(cradle), "cradle.need_satchel", satchel);
            else player.displayClientMessage(Component.translatable("message.headinajar.cradle_holds", cradle.ownerName()), true);
        }
        if (cradle.isEmpty() || !player.getUUID().equals(cradle.owner())) {
            player.displayClientMessage(Component.translatable("message.headinajar.cradle_status", cradle.energy().getEnergyStored(), cradle.saline().getFluidAmount()), false);
        }
        // a Cradle holding someone's head and short of power or saline says what it lacks and what feeds it (its owner
        // heard it when the head was docked)
        if (!wasEmpty && !cradle.isEmpty() && !player.getUUID().equals(cradle.owner())) cradle.tellWhatIsMissing(server);
        return InteractionResult.CONSUME;
    }

    /** Moves a jar onto the Cradle's seat cushion, if there is room for it there. */
    private static boolean climbIn(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        // on the cushion (6 px up), as far back as a jar fits in front of the back (it stands 2 px forward of centre)
        net.minecraft.world.phys.Vec3 seat = com.coolfreeze23.headinajar.machine.TallMachine.local(pos, level.getBlockState(pos), 0, 6, -2);
        if (!level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(seat))) return false;
        player.teleportTo(seat.x, seat.y, seat.z);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.headinajar.cradle_climbed"), true);
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LifeSupportCradleBlockEntity cradle) {
            cradle.ejectHead();
        }
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) com.coolfreeze23.headinajar.machine.TallMachine.removeTop(level, pos);
    }
}
