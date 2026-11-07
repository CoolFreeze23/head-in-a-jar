package com.coolfreeze23.headinajar.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A low plate that recharges a jar standing on it. */
public class ChargingDockBlock extends Block {
    public static final MapCodec<ChargingDockBlock> CODEC = simpleCodec(ChargingDockBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    public ChargingDockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Whether the player stands on a dock. */
    public static boolean isOn(Player player) {
        BlockPos pos = player.blockPosition();
        return player.level().getBlockState(pos).getBlock() instanceof ChargingDockBlock
                || player.onGround() && player.level().getBlockState(pos.below()).getBlock() instanceof ChargingDockBlock;
    }
}
