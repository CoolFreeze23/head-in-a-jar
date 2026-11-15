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

    /** A sampling tool draws from the first director that has not given today (the Living Library). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                                  net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.getItem() instanceof com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem && level.getBlockEntity(pos) instanceof CerebralArrayBlockEntity a) {
            if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;
            if (HeadDonor.array((net.minecraft.server.level.ServerPlayer) player, stack, hand, a)) return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        if (stack.is(net.minecraft.world.item.Items.CAKE) && level.getBlockEntity(pos) instanceof CerebralArrayBlockEntity a && a.heads() > 0) {
            if (!level.isClientSide) com.coolfreeze23.headinajar.feedback.Refusals.deny((net.minecraft.server.level.ServerPlayer) player,
                    com.coolfreeze23.headinajar.feedback.Refuser.at(a), "staff.board_cake");
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
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
