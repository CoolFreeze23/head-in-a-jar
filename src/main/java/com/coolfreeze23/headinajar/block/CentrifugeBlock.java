package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;

/** The Centrifuge as a chair-o-plane on a lab bench: two cells tall, the canopy in the top cell. */
public class CentrifugeBlock extends KitMachineBlock<LabMachines.Centrifuge> {
    public CentrifugeBlock(Properties properties) {
        super(properties, () -> ModRegistry.CENTRIFUGE_BE.get(), LabMachines.Centrifuge::new, LabMachines.Centrifuge::serverTick,
                // the bench, the drum, the vial rack, the motor housing
                Shapes.or(Block.box(0, 0, 0, 16, 13, 16), Block.box(3, 13, 3, 13, 15, 13), Block.box(1, 13, 1, 15, 14, 3), Block.box(5, 15, 5, 11, 16, 11)),
                // the pole's foot, the pole, the hangers' sweep, the canopy
                Shapes.or(Block.box(5, 0, 5, 11, 1, 11), Block.box(6, 1, 6, 10, 14, 10), Block.box(1, 3, 1, 15, 12, 15), Block.box(1, 12, 1, 15, 16, 15)));
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind topKind() {
        return com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind.CENTRIFUGE;
    }
}
