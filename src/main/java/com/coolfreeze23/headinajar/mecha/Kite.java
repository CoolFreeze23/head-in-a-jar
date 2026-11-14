package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * The Kite, the company's air courier, on the server: its takeoff (a crouch, then the wings' first down-stroke lifts it
 * off at the takeoff clip's push), the climb the pilot holds (synced, so every client beats the wings to it), and the
 * Talon Lift on the drive key.
 * <p>
 * The Talon Lift takes creatures only. A press in the air picks the nearest creature just below and a little ahead,
 * under the weight limit; the talons close on it at the grab clip's tick 10 and from then on it hangs from the cargo
 * point under the hips, put there every tick after its own tick (never by a saved no-gravity flag, so whatever ends the
 * hold leaves a creature that simply falls). A press while carrying lets it go on the drop clip's tick 1. A carried
 * creature that touches the ground is set down there; one that snags on a block is let go. Every refused press plays the
 * refused grab (the talons snap shut on air and the machine shrugs) and says why. Every way out ends the hold: the
 * pilot leaving, the machine's death or removal, the load going away, venting or a flat battery.
 */
public final class Kite {
    /** Server: which machine carries which load (looked up after each entity's own tick). */
    private static final java.util.Map<Entity, MechaEntity> HOLDERS = new java.util.WeakHashMap<>();

    @net.neoforged.fml.common.EventBusSubscriber(modid = com.coolfreeze23.headinajar.HeadInAJar.MOD_ID)
    public static final class Events {
        private Events() {
        }

        /** After a carried load's own tick: back under the talons. */
        @net.neoforged.bus.api.SubscribeEvent
        public static void afterTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
            Entity load = event.getEntity();
            if (load.level().isClientSide || HOLDERS.isEmpty()) return;
            MechaEntity m = HOLDERS.get(load);
            if (m == null) return;
            if (m.isRemoved() || m.heldId() != load.getId() || load.isRemoved()) {
                HOLDERS.remove(load);
                return;
            }
            place(m, load, feet(m, load, m.level().getGameTime(), m.liftFrom));
            Aloft.carried(load);
        }
    }

    /** The kinds of talon moment (the low two bits of the synced lift time). */
    public static final int GRAB = 0, REFUSED = 1, DROP = 2;
    /** The grab ({@code talon_grab}): the talons close at 10, the load is under the hips by 13; the clip ends at 16. */
    public static final int GRAB_CLOSE = 10, GRAB_HELD = 13, GRAB_TICKS = 16;
    /** The drop ({@code talon_drop}) opens the talons at 1 and ends at 12; the refusal ({@code talon_refused}) lasts 30. */
    public static final int DROP_RELEASE = 1, DROP_TICKS = 12, REFUSED_TICKS = 30;
    /** The takeoff: the crouch, then the push that leaves the ground at tick 8; the clip ends at 16. */
    public static final int TAKEOFF_PUSH = 8, TAKEOFF_TICKS = 16;
    /**
     * FE: a grab, and each tick of carrying (on a 250 kFE battery a grab is a quarter of a hundredth, the hold the
     * frame's own idle drain). A drop and a refused press cost nothing.
     */
    public static final int GRAB_FE = 625, HOLD_FE = 2;
    /** The heaviest load (its box's volume: a sheep is 1.05, a cow 1.13) and the tallest the talons take, blocks. */
    public static final double MAX_WEIGHT = 1.1;
    public static final float MAX_HEIGHT = 2.0f;
    /** Reach: the load's top within this far below the feet, and this far ahead of the machine's centre. */
    public static final double BELOW = 2.0, AHEAD = 1.5;
    /** The cargo point in the model (pixels, front = -z): under the hips, a little forward. The load's top hangs there. */
    static final Vec3 CARGO = new Vec3(0, -6, -3);
    /** Coming down faster than this (blocks a tick) at touchdown is a heavy landing: about a four-block drop. */
    public static final double HEAVY_SINK = 0.6;

    private Kite() {
    }

    public static boolean talons(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null && rig.verb == MechaRig.Verb.TALON;
    }

    static int length(int kind) {
        return switch (kind) {
            case GRAB -> GRAB_TICKS;
            case REFUSED -> REFUSED_TICKS;
            default -> DROP_TICKS;
        };
    }

    /** Ticks into the last talon moment, or -1 when its clip has ended (or there was none). */
    public static double into(MechaEntity m, double now) {
        long lift = m.liftAt();
        if (lift < 0 || !talons(m)) return -1;
        double t = now - lift / 4;
        return t >= 0 && t < length((int) (lift & 3)) ? t : -1;
    }

    // --- takeoff and climb ---------------------------------------------------------------------------------------

    /**
     * Whether the wings may lift the machine this tick (both sides: the pilot's client moves it). In the air always; on
     * the ground only once a takeoff's crouch has reached its push, so the machine leaves the ground on the clip's tick 8.
     */
    public static boolean mayLift(MechaEntity m) {
        if (!talons(m) || !m.onGround()) return true;
        long at = m.takeoffAt();
        return at >= 0 && m.level().getGameTime() - at >= TAKEOFF_PUSH;
    }

    // --- the drive key ------------------------------------------------------------------------------------------

    /** The pilot pressed (true) or let go of the drive key. Only the press acts, and every press answers. */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!held || !(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !talons(m)) return;
        long now = m.level().getGameTime();
        if (into(m, now) >= 0) {
            Refusals.deny(pilot, Refuser.at(m), "mecha.talon.busy");
            return;
        }
        if (m.heldId() >= 0) {
            m.setLiftAt(now, DROP);
            return;
        }
        Component gantry = Component.translatable("block.headinajar.assembly_gantry");
        if (m.vent() > 0) {
            refuse(m, pilot, "mecha.talon.venting");
            return;
        }
        if (m.battery() < GRAB_FE) {
            refuse(m, pilot, "mecha.talon.no_power", gantry);
            return;
        }
        if (m.onGround()) {
            refuse(m, pilot, "mecha.talon.grounded");
            return;
        }
        Entity target = nearest(m, pilot);
        if (target == null) {
            refuse(m, pilot, "mecha.talon.nothing");
            return;
        }
        String why = whyNot(pilot, target);
        if (why != null) {
            refuse(m, pilot, why, target.getDisplayName());
            return;
        }
        m.liftTarget = target;
        m.liftBlock = null;
        m.spend(GRAB_FE);
        m.setLiftAt(now, GRAB);
    }

    /** A refused press: the talons snap shut on air and the machine shrugs (the clip), and the reason is said. */
    static void refuse(MechaEntity m, ServerPlayer pilot, String why, Object... args) {
        m.setLiftAt(m.level().getGameTime(), REFUSED);
        Refusals.deny(pilot, Refuser.at(m), why, args);
    }

    /** The nearest creature hanging within reach: its top just below the feet, and ahead or under the body. */
    static Entity nearest(MechaEntity m, Player pilot) {
        Vec3 fwd = Bulwark.forward(m.yBodyRot), at = m.position();
        AABB area = new AABB(at.x - 3, at.y - BELOW - 2.5, at.z - 3, at.x + 3, at.y + 0.5, at.z + 3);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : m.level().getEntities(m, area, e -> e instanceof LivingEntity && e != pilot && e.isAlive() && !e.isSpectator())) {
            double top = e.getY() + e.getBbHeight();
            double below = at.y - top;
            if (below < -0.5 || below > BELOW) continue;
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            double ahead = dx * fwd.x + dz * fwd.z, side = Math.abs(dx * fwd.z - dz * fwd.x);
            double half = e.getBbWidth() / 2;
            if (ahead < -0.9 - half || ahead > AHEAD + half || side > 0.9 + half) continue;
            double d = Math.sqrt(dx * dx + dz * dz) + Math.max(0, below);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** The load's weight: its box's volume (a sheep is about 1.05, a cow 1.13). */
    static double weight(Entity e) {
        return e.getBbWidth() * e.getBbWidth() * e.getBbHeight();
    }

    /** Why the talons won't take this creature, or null (bosses, machines, the heavy, the ridden, untrusting players). */
    static String whyNot(Player pilot, Entity e) {
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return "mecha.talon.boss";
        if (e instanceof MechaEntity) return "mecha.talon.too_heavy";
        if (e instanceof Player p) {
            if (p.getAbilities().instabuild || p.isSpectator()) return "mecha.talon.exempt";
            if (!Hopper.trusts(p, pilot)) return "mecha.lift.untrusted";
        }
        if (e.isPassenger() || e.isVehicle()) return "mecha.talon.occupied";
        if (weight(e) > MAX_WEIGHT || e.getBbHeight() > MAX_HEIGHT) return "mecha.talon.too_heavy";
        return null;
    }

    // --- every server tick ----------------------------------------------------------------------------------------

    public static void serverTick(MechaEntity m) {
        if (!talons(m)) return;
        long now = m.level().getGameTime();
        Player pilot = Hosts.pilot(m);
        boolean jump = pilot != null && pilot.jumping && m.isAlive();
        if (m.climbing() != jump) m.setClimbing(jump);
        // the takeoff: a press of the climb on the ground starts the crouch; letting go before the push calls it off
        long take = m.takeoffAt();
        if (m.onGround()) {
            boolean inTakeoff = take >= 0 && now - take <= TAKEOFF_TICKS;
            if (jump && !inTakeoff && MechaLogic.canFly(m) && m.vent() == 0) m.setTakeoffAt(now);
            else if (!jump && take >= 0 && now - take < TAKEOFF_PUSH) m.setTakeoffAt(-1);
        }
        // how fast it was coming down, for the landing's weight (Hopper.serverTick decides it)
        if (m.lastPos != null) m.motion = m.position().subtract(m.lastPos);
        // nobody aboard in the air (an eject, a pilot who got out, the collapse): it comes down. Vanilla leaves a hosted
        // machine hanging where it was, so it is moved here, only when nothing else moved it this tick.
        if (pilot == null && !m.onGround() && !m.isInWater() && m.lastPos != null && m.motion.lengthSqr() < 1e-8) {
            Vec3 v = m.getDeltaMovement();
            v = new Vec3(v.x * 0.91, (Math.min(0, v.y) - 0.08) * 0.98, v.z * 0.91);
            m.setDeltaMovement(v);
            m.move(net.minecraft.world.entity.MoverType.SELF, v);
        }
        if (!m.onGround()) m.sinkRate = Math.max(0, -m.motion.y);
        m.lastPos = m.position();

        long lift = m.liftAt();
        if (lift >= 0) {
            int kind = (int) (lift & 3);
            long t = now - lift / 4;
            if (kind == GRAB && t == GRAB_CLOSE) take(m, pilot, now);
            else if (kind == DROP && t == DROP_RELEASE) drop(m);
        }
        if (m.heldId() < 0) return;
        Entity load = m.level().getEntity(m.heldId());
        if (load == null || !load.isAlive() || load.isRemoved() || load.level() != m.level() || pilot == null || load.isPassenger()
                || load.distanceToSqr(m) > 64 || m.isDeadOrDying()) {
            release(m, null);
            return;
        }
        if (m.vent() > 0 || m.battery() <= 0) {
            release(m, null);
            if (pilot instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(m.vent() > 0 ? "hud.headinajar.talon_vented" : "hud.headinajar.talon_flat"), true);
            return;
        }
        boolean dropping = (m.liftAt() & 3) == DROP && into(m, now) >= 0;
        if (!dropping && settled(m, load, now)) {
            // its feet are on the ground: set down where it hangs, the talons opening as for a press
            m.setLiftAt(now - DROP_RELEASE, DROP);
            drop(m);
            return;
        }
        Vec3 at = feet(m, load, now, m.liftFrom);
        if (!dropping && into(m, now) < 0 && !m.level().noCollision(load, boxAt(load, at).deflate(0.05))) {
            release(m, null);
            if (pilot instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("hud.headinajar.talon_snagged"), true);
            return;
        }
        place(m, load, at);
        m.spend(HOLD_FE);
    }

    /**
     * The carried load has been set down: the machine is coming down (or down) and the load's feet, where it hangs, are
     * on a block. Climbing, a load still standing on the ground is being lifted off it, not set down.
     */
    static boolean settled(MechaEntity m, Entity load, long now) {
        if (into(m, now) >= 0 && (m.liftAt() & 3) == GRAB) return false;
        if (m.motion.y > -0.02 && !m.onGround()) return false;
        AABB box = boxAt(load, feet(m, load, now, m.liftFrom));
        return !m.level().noCollision(load, new AABB(box.minX + 0.05, box.minY - 0.15, box.minZ + 0.05, box.maxX - 0.05, box.minY + 0.05, box.maxZ - 0.05));
    }

    static AABB boxAt(Entity load, Vec3 feet) {
        double w = load.getBbWidth() / 2;
        return new AABB(feet.x - w, feet.y, feet.z - w, feet.x + w, feet.y + load.getBbHeight(), feet.z + w);
    }

    /** The grab clip's tick 10: the talons close on what they reached for, if it is still in reach. */
    static void take(MechaEntity m, Player pilot, long now) {
        Entity target = m.liftTarget;
        m.liftTarget = null;
        m.liftBlock = null;
        if (!(pilot instanceof ServerPlayer sp)) return;
        boolean gone = target == null || !target.isAlive() || target.level() != m.level() || target.isPassenger()
                || target.distanceToSqr(m) > Mth.square(BELOW + 3);
        if (gone) {
            // closed on air after all: the refusal from its snap on (the two clips share the wind-up)
            m.setLiftAt(now - 8, REFUSED);
            Refusals.deny(sp, Refuser.at(m), "mecha.talon.slipped");
            return;
        }
        m.setHeldId(target.getId());
        m.liftFrom = target.position();
        HOLDERS.put(target, m);
        if (target instanceof Mob mob) mob.getNavigation().stop();
        Hopper.play(m, "talon_close");
    }

    /** The drop's tick 1: the talons spring open and the load goes on the machine's own way (rising, sinking, flying on). */
    static void drop(MechaEntity m) {
        if (m.heldId() < 0) return;
        release(m, m.motion);
        Hopper.play(m, "talon_open");
    }

    /**
     * Lets go of the load, if any: with this velocity or none at all (it simply falls). Also what every other way out
     * ends in: the pilot leaving, the machine's death or removal, the load going away.
     */
    public static void release(MechaEntity m, Vec3 velocity) {
        if (m.level().isClientSide || !talons(m)) return;
        m.liftTarget = null;
        int id = m.heldId();
        if (id < 0) return;
        m.setHeldId(-1);
        Entity load = m.level().getEntity(id);
        if (load == null) return;
        HOLDERS.remove(load);
        load.setDeltaMovement(velocity == null ? Vec3.ZERO : velocity);
        load.fallDistance = 0;
        load.hurtMarked = true;
    }

    // --- where the load is ----------------------------------------------------------------------------------------

    /**
     * Where the load's feet go at {@code now}: drawn up from where it stood to hang under the hips over the grab's ticks
     * 10-13, its top at the cargo point, and there while carried. Shared by the server (which owns it) and every client
     * (which draws it from the same synced moments).
     */
    public static Vec3 feet(MechaEntity m, Entity load, double now, Vec3 from) {
        Vec3 hold = Bulwark.world(m, CARGO).subtract(0, load.getBbHeight(), 0);
        long lift = m.liftAt();
        if (lift >= 0 && (lift & 3) == GRAB && from != null) {
            double t = now - lift / 4;
            if (t < GRAB_HELD) {
                double s = Mth.clamp((t - GRAB_CLOSE) / (GRAB_HELD - GRAB_CLOSE), 0, 1);
                return standing(m, load, from.lerp(hold, s * s * (3 - 2 * s)));
            }
        }
        return standing(m, load, hold);
    }

    /**
     * A load held lower than the ground under it stands on the ground instead (still in the talons) until the machine
     * climbs enough to lift it clear: raised out of the blocks below, at most a block and a half.
     */
    static Vec3 standing(MechaEntity m, Entity load, Vec3 at) {
        for (int i = 0; i < 12 && !m.level().noCollision(load, boxAt(load, at).deflate(0.02)); i++) at = at.add(0, 0.125, 0);
        return at;
    }

    /** Puts the load there, facing the way the machine flies, still and not falling. */
    public static void place(MechaEntity m, Entity load, Vec3 at) {
        float yaw = m.yBodyRot;
        load.setDeltaMovement(Vec3.ZERO);
        load.fallDistance = 0;
        if (load instanceof ServerPlayer sp && sp.connection != null) {
            sp.teleportTo(at.x, at.y, at.z);
        } else {
            load.setPos(at.x, at.y, at.z);
            load.setYRot(yaw);
            if (load instanceof LivingEntity living) {
                living.yBodyRot = yaw;
                living.yHeadRot = yaw;
            }
        }
        if (load instanceof Mob mob && !mob.level().isClientSide) mob.getNavigation().stop();
    }
}
