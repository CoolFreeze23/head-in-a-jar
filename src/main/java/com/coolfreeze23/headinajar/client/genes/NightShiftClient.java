package com.coolfreeze23.headinajar.client.genes;

import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/** The client side of the hostile creatures' 0.6.2 genes: Rampage's streak, during which you cannot sneak. */
public final class NightShiftClient {
    /** The local player's game time at which its Rampage streak ends (it cannot sneak until then). */
    private static long streakUntil = Long.MIN_VALUE;

    private NightShiftClient() {
    }

    /** A gene memo of this stream's kinds (see {@link Fx}). */
    public static void fx(Fx fx) {
        Minecraft mc = Minecraft.getInstance();
        if (fx.kind() == HostileGenes.FX_RAMPAGE && mc.player != null && mc.level != null && fx.entityA() == mc.player.getId()) {
            streakUntil = mc.level.getGameTime() + (long) fx.arg();
        }
    }

    /** Rampage's cost: on a streak the sneak key does nothing. */
    public static void movement(MovementInputUpdateEvent event) {
        var level = event.getEntity().level();
        if (level.getGameTime() < streakUntil) event.getInput().shiftKeyDown = false;
        else if (streakUntil != Long.MIN_VALUE && level.getGameTime() > streakUntil + 200) streakUntil = Long.MIN_VALUE;
    }
}
