package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachineBlock;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/** The Body Locker as a theatre cloakroom kiosk, two cells tall: a click checks the nearest host in, or out. */
public class BodyLockerBlock extends KitMachineBlock<BodyLockerBlockEntity> {
    public BodyLockerBlock(Properties properties) {
        super(properties, () -> ModRegistry.LOCKER_BE.get(), BodyLockerBlockEntity::new, BodyLockerBlockEntity::kitTick,
                Shapes.or(Block.box(1, 0, 3, 15, 12, 14), Block.box(0, 12, 1, 16, 13, 15), Block.box(2, 13, 5, 14, 16, 14)),
                Block.box(2, 0, 5, 14, 16, 14));
    }

    @Override
    public MachineTopBlock.Kind topKind() {
        return MachineTopBlock.Kind.BODY_LOCKER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BodyLockerBlockEntity locker) locker.use(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Broken (or blown up): what it holds walks out rather than vanishing with it. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BodyLockerBlockEntity locker) locker.releaseOnBreak();
        super.onRemove(state, level, pos, newState, moved);
    }
}
