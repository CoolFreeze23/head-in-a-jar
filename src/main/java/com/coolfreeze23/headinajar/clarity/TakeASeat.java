package com.coolfreeze23.headinajar.clarity;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * "Take a Seat": a Separation Chair touching a fed Galvanic Pile (one with fuel in it or power made). Earned when the
 * second of the two conditions comes true by the player's hand, in whichever order: the chair placed against a fed
 * pile, or a pile fed (or placed) beside a chair.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class TakeASeat {
    public static final String EVENT = "chair_powered";

    private TakeASeat() {
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getPlacedBlock().is(ModRegistry.SEPARATION_CHAIR.get()) && touchesFedPile(event.getLevel(), event.getPos())) {
            Advance.grant(player, EVENT);
        } else if (event.getPlacedBlock().is(ModRegistry.GALVANIC_PILE.get())) {
            pileChanged(player, event.getLevel(), event.getPos());
        }
    }

    /** A pile was fed or placed by this player: earned if it is fed now and a chair touches either of its cells. */
    public static void pileChanged(ServerPlayer player, BlockGetter level, BlockPos pile) {
        if (!fed(level, pile)) return;
        for (BlockPos cell : new BlockPos[]{pile, pile.above()}) {
            for (Direction d : Direction.values()) {
                if (level.getBlockState(cell.relative(d)).is(ModRegistry.SEPARATION_CHAIR.get())) {
                    Advance.grant(player, EVENT);
                    return;
                }
            }
        }
    }

    /** Whether a chair at this spot touches a fed pile (by the pile's own cell or its top cell). */
    public static boolean touchesFedPile(BlockGetter level, BlockPos chair) {
        for (Direction d : Direction.values()) {
            BlockPos at = chair.relative(d);
            if (level.getBlockState(at).getBlock() instanceof MachineTopBlock) {
                at = MachineTopBlock.machine(level, at);
                if (at == null) continue;
            }
            if (fed(level, at)) return true;
        }
        return false;
    }

    private static boolean fed(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GalvanicPileBlockEntity pile
                && (pile.fuelLeft() > 0 || pile.energy().getEnergyStored() > 0);
    }
}
