package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The Body Printer as a photo booth: two cells tall, its saline sight face as a level, the lamp in the marquee. */
public class BodyPrinterBlock extends KitMachineBlock<BodyPrinterBlockEntity> {
    public static final IntegerProperty SALINE = MachineLamp.level("saline");

    public BodyPrinterBlock(Properties properties) {
        super(properties, () -> ModRegistry.PRINTER_BE.get(), BodyPrinterBlockEntity::new, BodyPrinterBlockEntity::kitTick,
                Block.box(0, 0, 0, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 16));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SALINE);
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.BODY_PRINTER;
    }

    @Override
    public boolean lampAbove() {
        return true;
    }
}
