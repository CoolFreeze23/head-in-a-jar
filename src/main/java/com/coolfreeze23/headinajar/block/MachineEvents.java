package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Machines and the players they hold: a jar is never left in a Neural Socket that no longer holds it. */
public final class MachineEvents {
    private MachineEvents() {
    }

    /** Twice a second: a jar whose socket is gone (broken, carried off) goes back to a plain jar. */
    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) NeuralSocketBlockEntity.reconcile(player);
    }

    /** Logging back in unplugs: the socket let go of its jar when the player left. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FormActions.state(player).form() == Form.SOCKET) {
            NeuralSocketBlockEntity.unplug(player, "socket.headinajar.relogged");
        }
    }
}
