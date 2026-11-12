package com.coolfreeze23.headinajar.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The upper cell of a tall machine ({@code headinajar:machine_top}): no item, no block entity; it draws the machine's upper half (lit as its own cell). Its
 * shape is the machine's upper body; a click on it is a click on the machine; breaking it breaks the machine (which
 * drops, as a door's upper half does); hoppers, pipes and cables that meet it reach the machine (the capabilities are
 * forwarded in the registry). With no tall machine below, it is nothing and goes away.
 */
public class MachineTopBlock extends Block {
    public static final MapCodec<MachineTopBlock> CODEC = simpleCodec(MachineTopBlock::new);
    /** Which machine stands below (its upper half is drawn by this cell, lit as this cell), and how it faces. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Kind> MACHINE =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("machine", Kind.class);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    /** A level the upper half shows (the Mixer's saline above its counter). */
    /** The upper half's level (saline, broth: 0-4; a serum's colour: 0-7), named for the first machine that had one. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty SALINE =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("saline", 0, 7);
    /** The lamp, for a machine whose lens stands in the upper half (the Cradle's headrest, the Printer's marquee). */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<MachineLamp> LAMP = MachineLamp.LAMP;
    /** The Gene Splicer's stability sight glass on its arm. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty STAB = net.minecraft.world.level.block.state.properties.IntegerProperty.create("stab", 0, 2);

    /** The machines with a top cell. Saved by name: add new ones anywhere. */
    /** The machine a click at {@code pos} is for: the machine below when {@code pos} is a top cell, else {@code pos}. */
    public static net.minecraft.core.BlockPos machineAt(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MachineTopBlock ? pos.below() : pos;
    }

    public enum Kind implements net.minecraft.util.StringRepresentable {
        NONE, GALVANIC_PILE, SALINE_MIXER, CENTRIFUGE, LIFE_SUPPORT_CRADLE, BODY_PRINTER, GENE_SPLICER,
        CEREBRAL_ARRAY, DNA_SEQUENCER, INJECTION_RIG, TESLA_NODE, CHARGING_DOCK,
        RELAY_MAST, RELAY_MAST_MK2, TURRET_PEDESTAL, BODY_LOCKER,
        LEYDEN_JAR, LEECH_JAR;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public MachineTopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MACHINE, Kind.NONE).setValue(FACING, Direction.NORTH).setValue(SALINE, 0)
                .setValue(LAMP, MachineLamp.OFF).setValue(STAB, 0));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MACHINE, FACING, SALINE, LAMP, STAB);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** The tall machine this cell belongs to, or null. */
    public static BlockPos machine(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).getBlock() instanceof TallMachine ? below : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos below = machine(level, pos);
        if (below == null) return Shapes.empty();
        BlockState machine = level.getBlockState(below);
        return ((TallMachine) machine.getBlock()).upperShape(machine);
    }

    /** What collides may be less than what is drawn and clicked (a seat a jar can stand in). */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos below = machine(level, pos);
        if (below == null) return Shapes.empty();
        BlockState machine = level.getBlockState(below);
        TallMachine tall = (TallMachine) machine.getBlock();
        return TallMachine.rotate(tall.upperCollisionNorth(), tall.facingOf(machine));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos below = machine(level, pos);
        if (below == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockHitResult down = new BlockHitResult(hit.getLocation(), hit.getDirection(), below, hit.isInside());
        BlockState machine = level.getBlockState(below);
        ItemInteractionResult r = machine.useItemOn(stack, level, player, hand, down);
        if (r == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
            InteractionResult plain = machine.useWithoutItem(level, player, down);
            return plain.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return r;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos below = machine(level, pos);
        if (below == null) return InteractionResult.PASS;
        return level.getBlockState(below).useWithoutItem(level, player, new BlockHitResult(hit.getLocation(), hit.getDirection(), below, hit.isInside()));
    }

    /** It breaks as fast as the machine, with the same tool. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockPos below = machine(level, pos);
        return below == null ? 1 : level.getBlockState(below).getDestroyProgress(player, level, below);
    }

    /** Breaking the top breaks the machine: it drops itself (with the right tool) and everything in it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos below = machine(level, pos);
        if (below != null && !level.isClientSide) {
            BlockState machine = level.getBlockState(below);
            boolean drops = !player.getAbilities().instabuild && player.hasCorrectToolForDrops(machine, level, below);
            level.destroyBlock(below, drops, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        BlockPos below = machine(level, pos);
        if (below != null) level.destroyBlock(below, true);
    }

    /** The machine below is gone (or was replaced): nothing is left here. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction != Direction.DOWN) return state;
        // follows the machine below: its kind, facing and shown level; nothing left when it is gone
        return neighbor.getBlock() instanceof TallMachine ? TallMachine.topState(neighbor) : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof TallMachine;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        BlockState machine = level.getBlockState(below);
        return machine.getBlock() instanceof TallMachine ? machine.getBlock().getCloneItemStack(level, below, machine) : ItemStack.EMPTY;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
