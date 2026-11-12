package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Leech Jar on the kit: a gumball machine whose globe holds a leech. It samples a creature beside it as before;
 * the vials it holds show in the pedestal's window ({@code samples} 0-3), and a use empties it into the player's hands.
 */
public class LeechJarBlock extends KitMachineBlock<LeechJarBlockEntity> {
    /** The vials in the window: none, then one for 1-3 samples, two for 4-6, three for 7-9. */
    public static final IntegerProperty SAMPLES = IntegerProperty.create("samples", 0, 3);
    private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 13, 15), Block.box(2, 13, 2, 14, 15, 14),
            Block.box(1, 15, 1, 15, 16, 15), Block.box(6, 15, 0, 10, 16, 1), Block.box(14, 8, 7, 15, 10, 9));
    private static final VoxelShape UPPER = Shapes.or(Block.box(1, 0, 1, 15, 7, 15), Block.box(2, 7, 2, 14, 9, 14),
            Block.box(5, 9, 5, 11, 11, 11), Block.box(7, 11, 7, 9, 13, 9), Block.box(6, 0, 0, 10, 3, 1));

    public LeechJarBlock(Properties properties) {
        super(properties, () -> ModRegistry.LEECH_JAR_BE.get(), LeechJarBlockEntity::new, LeechJarBlockEntity::serverTick, LOWER, UPPER);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineLamp.LAMP, MachineLamp.READY).setValue(SAMPLES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SAMPLES);
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.LEECH_JAR;
    }

    /** Any use empties it (as before: no screen). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LeechJarBlockEntity jar) jar.empty(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
