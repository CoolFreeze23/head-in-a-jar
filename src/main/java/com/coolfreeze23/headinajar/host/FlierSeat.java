package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Sneak on a flying host high in the air does not drop its jar: it is told to fly lower first. Within a short drop of
 * the ground (or on it, or in water) sneak lets the jar off, and the jar is set down on the ground beside the flier
 * (vanilla would let it go from the top of the flier's box: four blocks up from a ghast). Anything else that takes a
 * rider off in the air (a teleport, spectator mode, the host dying) is let through, and is set down the same way when
 * there is ground within a short drop.
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

    /**
     * A jar has just come off a flying host (called after vanilla has put it where it gets off): it is set down on the
     * ground beside the flier, or under it, with no fall to take, facing the flier (so a camera behind the jar is clear
     * of it), and its client is told where it stands and where it faces. Left as vanilla put it when no ground is
     * within a short drop.
     */
    public static void dismounted(LivingEntity host, net.minecraft.server.level.ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || FormActions.state(p).form() != Form.HOSTED || p.connection == null) return;
        HostDefinition def = Hosts.definition(host);
        if (def == null || !def.has("flight")) return;
        net.minecraft.world.phys.Vec3 at = setDownAt(host, p);
        if (at == null) return;
        p.connection.teleport(at.x, at.y, at.z, facing(at, host, p.getYRot()), p.getXRot());
        p.setYHeadRot(p.getYRot());
        p.setYBodyRot(p.getYRot());
        p.resetFallDistance();
    }

    /** The yaw from {@code at} toward the flier's centre; {@code otherwise} when standing under it. */
    public static float facing(net.minecraft.world.phys.Vec3 at, LivingEntity host, float otherwise) {
        double dx = host.getX() - at.x, dz = host.getZ() - at.z;
        if (dx * dx + dz * dz < 1e-4) return otherwise;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Where a jar getting off this flier stands: on the ground just clear of the flier's box, the way the jar faces
     * first, then round it; else under it. Null when there is no room or no ground within {@link #SAFE_DROP} (and a
     * block) below the flier's feet.
     */
    public static net.minecraft.world.phys.Vec3 setDownAt(LivingEntity host, Player jar) {
        double clear = host.getBbWidth() / 2 + jar.getBbWidth() / 2 + 0.15;
        for (int turn : new int[]{0, 45, -45, 90, -90, 135, -135, 180}) {
            double a = Math.toRadians(jar.getYRot() + turn), dx = -Math.sin(a), dz = Math.cos(a);
            // far enough along this heading to be outside the (square) box, the diagonals too
            double out = clear / Math.max(Math.abs(dx), Math.abs(dz));
            var spot = floor(host, jar, host.getX() + dx * out, host.getZ() + dz * out);
            if (spot != null) return spot;
        }
        return floor(host, jar, host.getX(), host.getZ());
    }

    /** The top of whatever is under (x, z), within a short drop of the flier's feet, if the jar fits standing there. */
    private static net.minecraft.world.phys.Vec3 floor(LivingEntity host, Player jar, double x, double z) {
        var level = host.level();
        var from = new net.minecraft.world.phys.Vec3(x, host.getY() + 0.5, z);
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, from.subtract(0, SAFE_DROP + 1.5, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, jar));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
        double y = hit.getLocation().y, w = jar.getBbWidth() / 2;
        var box = new net.minecraft.world.phys.AABB(x - w, y + 1e-4, z - w, x + w, y + jar.getBbHeight(), z + w);
        return level.noCollision(jar, box) ? new net.minecraft.world.phys.Vec3(x, y, z) : null;
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
