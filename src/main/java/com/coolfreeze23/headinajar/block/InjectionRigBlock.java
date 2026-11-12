package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;

/** The Injection Rig as a petrol pump whose nozzle is a syringe: the globe shows the loaded serum's colour, the counter rolls during a shot. */
public class InjectionRigBlock extends KitMachineBlock<LabMachines.InjectionRig> {
    /** The globe: 0 unlit milk glass, 1-7 lit in the loaded serum's category colour (7 also the flash of a mutation). */
    public static final IntegerProperty SERUM = IntegerProperty.create("serum", 0, 7);
    /** The dial's counter rolls while a shot is given. */
    public static final IntegerProperty COUNTER = IntegerProperty.create("counter", 0, 1);

    public InjectionRigBlock(Properties properties) {
        super(properties, () -> ModRegistry.RIG_BE.get(), LabMachines.InjectionRig::new, LabMachines.InjectionRig::kitTick,
                Block.box(1, 0, 1, 16, 16, 14),
                Shapes.or(Block.box(2, 0, 4, 14, 3, 12), Block.box(5, 3, 5, 16, 7, 11), Block.box(14, 0, 7, 16, 7, 10), Block.box(4, 6, 6, 12, 14, 10),
                        Block.box(15, 7, 6, 16, 12, 11), Block.box(13, 6, 7, 15, 16, 10)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SERUM, COUNTER);
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.INJECTION_RIG;
    }
}
