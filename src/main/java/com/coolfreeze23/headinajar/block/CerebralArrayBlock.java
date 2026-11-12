package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;

/** The Cerebral Array as a small jury box of nine specimen jars with the chairman's desk on top; the urn shows the broth. */
public class CerebralArrayBlock extends KitMachineBlock<CerebralArrayBlockEntity> {
    public static final IntegerProperty BROTH = MachineLamp.level("broth");

    public CerebralArrayBlock(Properties properties) {
        super(properties, () -> ModRegistry.ARRAY_BE.get(), CerebralArrayBlockEntity::new, CerebralArrayBlockEntity::kitTick,
                Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 10, 3, 16, 16, 16)),
                Shapes.or(Block.box(0, 0, 3, 16, 2, 16), Block.box(0, 2, 7, 16, 10, 16), Block.box(0, 10, 10, 16, 16, 16)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BROTH);
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.CEREBRAL_ARRAY;
    }

    /** The lamp is on the gavel's bracket, on the desk. */
    @Override
    public boolean lampAbove() {
        return true;
    }
}
