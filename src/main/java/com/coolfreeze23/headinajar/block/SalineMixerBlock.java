package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;

/** The Saline Mixer as a soda fountain: two cells tall, its tank's saline and its sight glass's water as levels 0-4. */
public class SalineMixerBlock extends KitMachineBlock<SalineMixerBlockEntity> {
    public static final IntegerProperty SALINE = MachineLamp.level("saline");
    public static final IntegerProperty WATER = MachineLamp.level("water");

    public SalineMixerBlock(Properties properties) {
        super(properties, () -> ModRegistry.MIXER_BE.get(), SalineMixerBlockEntity::new, SalineMixerBlockEntity::serverTick,
                Block.box(1, 0, 1, 15, 16, 15),
                // tank, posts and lid; the canisters and caps; the tap's stick and knob on the right
                Shapes.or(Block.box(1, 0, 2, 15, 11, 14), Block.box(3, 11, 3, 13, 16, 13), Block.box(15, 0, 1, 16, 5, 7)));
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind topKind() {
        return com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind.SALINE_MIXER;
    }

    /** An empty bucket finds nothing to fill when the tank holds less than a bucket of saline: it says so (no screen). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                               net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                               net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        var result = super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && stack.is(net.minecraft.world.item.Items.BUCKET)
                && level.getBlockEntity(pos) instanceof SalineMixerBlockEntity mixer) {
            if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(mixer), "mixer.nothing_to_fill",
                        stack.getHoverName(), net.minecraft.network.chat.Component.translatable("fluid_type.headinajar.cranial_saline"));
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return result;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SALINE, WATER);
    }
}
