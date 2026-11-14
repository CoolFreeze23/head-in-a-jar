package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.machine.MachineTopBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Body Locker holding someone's body is theirs to break: whoever checked it in, or someone they trust. Anyone else
 * breaking either of its cells is refused with one line and the locker stays (the game sends the block back to the
 * breaker's client). An empty locker, or one holding a body stored before owners were saved, breaks for anyone.
 * <p>
 * Holding the attack button on it breaks it over and over: the client digs on through the block that comes back and
 * sends a finished dig each time, but no new start. So the line is said once per hold: a refused break with no new
 * start of digging since the last refusal at the same locker is the same held button, and is refused without a word.
 * Letting go mid-dig (an abort) or pressing again (a start) is a new attempt and is answered. In creative the client
 * sends a start every few ticks while the button is held; one that soon after a refusal is the same hold too.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class LockerGuard {
    private LockerGuard() {
    }

    /** Ticks after a refused break within which a creative player's next start of digging is the same held button. */
    public static final int CREATIVE_REPEAT = 7;

    /** Per player: the locker last refused to them, when, and whether a refused break now continues that hold. */
    private record Hold(BlockPos locker, long refusedAt, boolean held) {
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void broke(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;
        BlockPos pos = locker(event.getLevel(), event.getPos());
        if (pos == null) return;
        if (!(event.getLevel().getBlockEntity(pos) instanceof BodyLockerBlockEntity locker) || locker.mayBreak(player)) return;
        event.setCanceled(true);
        Hold before = HOLDS.put(player.getUUID(), new Hold(pos.immutable(), player.serverLevel().getGameTime(), true));
        if (before == null || !before.held() || !before.locker().equals(pos)) locker.refuseBreak(player);
    }

    /** The digging the attack button does: started (a new press), or let go before the block broke. */
    @SubscribeEvent
    public static void dig(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null) return;
        switch (event.getAction()) {
            case START -> {
                long since = player.serverLevel().getGameTime() - hold.refusedAt();
                boolean repeat = player.isCreative() && since >= 0 && since <= CREATIVE_REPEAT
                        && hold.locker().equals(locker(event.getLevel(), event.getPos()));
                HOLDS.put(player.getUUID(), new Hold(hold.locker(), hold.refusedAt(), repeat));
            }
            case ABORT -> HOLDS.put(player.getUUID(), new Hold(hold.locker(), hold.refusedAt(), false));
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        HOLDS.remove(event.getEntity().getUUID());
    }

    /** The locker's own cell for either of its cells (the machine for its top cell); null for a top with no machine. */
    private static BlockPos locker(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MachineTopBlock ? MachineTopBlock.machine(level, pos) : pos;
    }
}
