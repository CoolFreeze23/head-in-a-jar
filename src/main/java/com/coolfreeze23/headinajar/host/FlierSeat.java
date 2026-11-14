package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Sneak on a flying host high in the air does not drop its jar: it is told to fly lower first. Within a short drop of
 * the ground (or on it, or in water) sneak lets the jar off as anywhere else. Anything else that takes a rider off in
 * the air (a teleport, spectator mode, the host dying) is let through.
 */
public final class FlierSeat {
    private FlierSeat() {
    }

    /** When each rider last asked to get off too high (game time). Not saved. */
    private static final java.util.Map<java.util.UUID, Long> ASKED = new java.util.concurrent.ConcurrentHashMap<>();

    /** The deepest drop a jar is let off a flier into. */
    public static final int SAFE_DROP = 3;

    /** How far the host's feet are above whatever is under them (more than 16 counts as 16). */
    public static double drop(LivingEntity host) {
        var from = host.position();
        var hit = host.level().clip(new net.minecraft.world.level.ClipContext(from, from.subtract(0, 16, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, host));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 16 : from.y - hit.getLocation().y;
    }

    /** Whether this host is a flier still in the air, where sneak means "down", not "off". */
    public static boolean aloft(LivingEntity host) {
        HostDefinition def = Hosts.definition(host);
        return def != null && def.has("flight") && !host.onGround() && !host.isInWater() && !host.isInLava();
    }

    /** Called for every dismount ({@link com.coolfreeze23.headinajar.form.KeyAnswers}): sneak in the air is "down", not "off". */
    public static void dismount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        if (!(event.getEntityBeingMounted() instanceof LivingEntity host) || !host.isAlive() || !Hosts.isHost(host)
                || host instanceof com.coolfreeze23.headinajar.entity.MechaEntity) return;
        if (!(event.getEntityMounting() instanceof Player p) || !p.isAlive() || p.isSpectator() || !p.isShiftKeyDown()
                || FormActions.state(p).form() != Form.HOSTED || host.getFirstPassenger() != p) return;
        if (aloft(host) && drop(host) > SAFE_DROP) {
            event.setCanceled(true);
            // held sneak asks again every few ticks: one answer per press (a gap of a second is a new press)
            long now = host.level().getGameTime();
            Long last = ASKED.put(p.getUUID(), now);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp && (last == null || now - last > 20 || now < last)) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(sp, com.coolfreeze23.headinajar.feedback.Refuser.at(host), "host.too_high", host.getDisplayName(), net.minecraft.network.chat.Component.keybind("key.sneak"));
            }
        }
    }
}
