package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;

/** The Gene Splicer as a sewing machine on a lab bench: the strand's stage and the stability band as blockstates. */
public class GeneSplicerBlock extends KitMachineBlock<LabMachines.Splicer> {
    /** How far along the strand the stitches have come (0, 3, 5, 8 px). */
    public static final IntegerProperty STITCH = IntegerProperty.create("stitch", 0, 3);
    /** The stability band: 0 steady, 1 a wobble, 2 the bubble runs. */
    public static final IntegerProperty STAB = IntegerProperty.create("stab", 0, 2);

    public GeneSplicerBlock(Properties properties) {
        super(properties, () -> ModRegistry.SPLICER_BE.get(), LabMachines.Splicer::new, LabMachines.Splicer::kitTick,
                Shapes.or(Block.box(0, 0, 0, 16, 13, 16), Block.box(1, 13, 1, 15, 16, 13)),
                Shapes.or(Block.box(2, 0, 3, 15, 8, 12), Block.box(0, 0, 2, 3, 12, 14), Block.box(2, 8, 6, 16, 13, 12), Block.box(2, 0, 1, 8, 9, 4),
                        Block.box(11, 7, 2, 15, 9, 4)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STITCH, STAB);
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.GENE_SPLICER;
    }
}
