package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;

/** The DNA Sequencer as a brass cash register on a lab bench, two cells tall. */
public class SequencerBlock extends KitMachineBlock<LabMachines.Sequencer> {
    public SequencerBlock(Properties properties) {
        super(properties, () -> ModRegistry.SEQUENCER_BE.get(), LabMachines.Sequencer::new, LabMachines.Sequencer::kitTick,
                Shapes.or(Block.box(0, 0, 0, 16, 13, 16), Block.box(0, 13, 0, 16, 16, 15)),
                Shapes.or(Block.box(0, 0, 0, 16, 3, 5), Block.box(0, 0, 6, 14, 5, 15), Block.box(3, 5, 7, 13, 11, 14), Block.box(4, 11, 8, 13, 16, 13),
                        Block.box(0, 5, 8, 3, 12, 12)));
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.DNA_SEQUENCER;
    }
}
