package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * What keeps a form honest whatever moved the player: a jar whose host or cockpit is gone is a plain jar again, a hold
 * of Telekinesis ends when the jar stops being a bare jar, and a jar carried by a host isn't crushed by the host's walls.
 */
public final class FormGuards {
    /** Keys a whole body pressed that only mean something to a head in a jar (answered by the client). */
    public static final String BODY_JAR_KEY = "body.jar_key";

    static {
        Refusals.allowFromClient(BODY_JAR_KEY, Refusals.Cue.PLAIN);
    }

    private FormGuards() {
    }

    private static JarState state(Player player) {
        return player.getData(ModRegistry.JAR_STATE);
    }

    /**
     * Runs for every player every server tick, whether or not their connection ticks them (a teleport, a dimension change,
     * a host that died or was removed by a command, a player the server moves): a HOSTED or PILOT form with nothing alive
     * under it ends as a plain jar where the player is.
     */
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(ModRegistry.JAR_STATE)) return;
        JarState state = state(player);
        Form form = state.form();
        if ((form == Form.HOSTED || form == Form.PILOT) && !onHost(player)) FormActions.leaveHost(player);
        if (state.heldEntity() >= 0 && state.form() != Form.JAR) Telekinesis.release(player, state);
    }

    private static boolean onHost(ServerPlayer player) {
        var vehicle = player.getVehicle();
        return vehicle != null && vehicle.isAlive() && !vehicle.isRemoved() && Hosts.isHost(vehicle);
    }

    /**
     * A jar riding a host sits at its neck, often over the front of its box: when the host stands against a wall the jar's
     * eye is inside it. The host is what met the wall, so the jar's glass is not ground away for it (a Rover's crash is
     * one bump, from the shell itself).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void inWall(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.hasData(ModRegistry.JAR_STATE) && event.getSource().is(DamageTypes.IN_WALL)
                && (state(player).form() == Form.HOSTED || state(player).form() == Form.PILOT) && player.getVehicle() != null) {
            event.setCanceled(true);
        }
    }
}
