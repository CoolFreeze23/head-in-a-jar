package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.feedback.Refusals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * The item of a tall machine: it is placed only where the cell above is free for the machine's top, and says so when
 * it isn't (never a silent no). Once placed, the block puts its top cell.
 */
public class TallMachineItem extends BlockItem {
    public TallMachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BlockPlaceContext real = updatePlacementContext(context);
        if (real != null && real.canPlace() && !TallMachine.headroom(real.getLevel(), real.getClickedPos(), real)) {
            if (real.getPlayer() instanceof ServerPlayer player) Refusals.deny(player, "machine.no_headroom");
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.place(context);
        if (result.consumesAction() && real != null && !real.getLevel().isClientSide) TallMachine.ensureTop(real.getLevel(), real.getClickedPos());
        return result;
    }
}
