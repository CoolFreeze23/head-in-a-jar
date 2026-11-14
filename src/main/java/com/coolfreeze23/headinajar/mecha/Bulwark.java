package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The Bulwark's Heavy Lifting, on the drive key, decided on the server. A press with empty fists grabs what stands
 * within reach ahead (a creature up to a horse's size, or a loose block by the Telekinesis rules); the fists close on
 * it at the grab clip's tick 10 and lift it overhead, and from then on its position is set every tick on the fists above
 * the jar, in the pilot's own view (never by a saved no-gravity flag, so whatever ends the hold leaves something that
 * simply falls). A press while holding throws it along the pilot's look at the throw clip's tick 6, faster the lighter it
 * is; a press looking down sets it on the ground in front. A load is never carried into a roof: the grab is refused
 * without room overhead for it, and a carried load meeting a low ceiling is let go with a line. Every refused press plays the refused grab (the fists close on air and the machine shrugs) and says why.
 * <p>
 * The load is put where it belongs after its own tick (an entity-tick listener), so whatever its own tick did (gravity,
 * its AI's steps) never shows: the position sent to every client is the hold's. A lifted block is a plain falling
 * block kept from landing, so whatever ends the hold leaves an ordinary falling block that lands as one.
 */
public final class Bulwark {
    /** Server: which machine holds which load (looked up after each entity's own tick). */
    private static final java.util.Map<Entity, MechaEntity> HOLDERS = new java.util.WeakHashMap<>();

    @net.neoforged.fml.common.EventBusSubscriber(modid = com.coolfreeze23.headinajar.HeadInAJar.MOD_ID)
    public static final class Events {
        private Events() {
        }

        /** After a held load's own tick: back to the hold. */
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

    /** The kinds of lift moment (the low two bits of the synced lift time). */
    public static final int GRAB = 0, REFUSED = 1, THROW = 2, SET = 3;
    /** The grab ({@code grab_high}): the fists close at 10 and lock overhead with the load at 21; the clip ends at 24. */
    public static final int GRAB_CLOSE = 10, GRAB_LIFT = 12, GRAB_HELD = 21, GRAB_TICKS = 24;
    /** The throw ({@code throw_high}) releases at 6 and ends at 19. */
    public static final int REFUSED_TICKS = 26, THROW_RELEASE = 6, THROW_TICKS = 19, SET_TICKS = 10;
    /**
     * FE: a grab, each tick of holding, a throw (on a 300 kFE battery: 600 grabs, 400 throws, 50 minutes of holding
     * with the 5 FE idle drain). Heat: a throw. A refused press costs nothing.
     */
    public static final int GRAB_FE = 500, HOLD_FE = 5, THROW_FE = 750;
    public static final float THROW_HEAT = 12;
    /** Reach ahead of the hull's front, blocks; the largest creature the fists take, blocks wide and tall. */
    public static final double REACH = 3.0;
    public static final float MAX_WIDTH = 1.5f, MAX_HEIGHT = 2.0f;
    /** Looking further down than this (degrees) a press sets the load down instead of throwing it. */
    public static final float SET_PITCH = 35;
    /**
     * The hold point in the model (pixels, front = -z): the fists' midpoint in {@code hold_high}, above the jar and a
     * little behind it. The load's feet go there: it stands on the fists, clear of the jar and in the pilot's view.
     */
    static final Vec3 HOLD = new Vec3(0, 57.2, 3.1);
    /** Where a set-down load is put, blocks ahead of the machine's centre. */
    static final double GROUND_AHEAD = 2.0;

    private Bulwark() {
    }

    public static boolean lifts(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null && rig.verb == MechaRig.Verb.LIFT;
    }

    static int length(MechaEntity m, int kind) {
        return switch (kind) {
            case GRAB -> GRAB_TICKS;
            case REFUSED -> REFUSED_TICKS;
            case THROW -> THROW_TICKS;
            default -> SET_TICKS;
        };
    }



    /** Ticks into the last lift moment, or -1 when its clip has ended (or there was none). */
    static double into(MechaEntity m, double now) {
        long lift = m.liftAt();
        if (lift < 0) return -1;
        double t = now - lift / 4;
        return t >= 0 && t < length(m, (int) (lift & 3)) ? t : -1;
    }

    /** The fists are on a load or busy reaching, throwing or setting down: the arms' own verbs wait. */
    public static boolean handsBusy(MechaEntity m, double now) {
        return lifts(m) && (m.heldId() >= 0 || into(m, now) >= 0);
    }

    /** The machine plants its feet while it reaches, throws or sets down (it may turn and walk again after). */
    /** While the fists reach for a load (before they close), the body still comes round to the pilot's look. */
    public static boolean turnsToGrab(MechaEntity m) {
        if (!lifts(m) || (m.liftAt() & 3) != GRAB) return false;
        double t = into(m, m.level().getGameTime());
        return t >= 0 && t < GRAB_CLOSE;
    }

    public static boolean rooted(MechaEntity m) {
        if (!lifts(m)) return false;
        double t = into(m, m.level().getGameTime());
        if (t < 0) return false;
        return (m.liftAt() & 3) != REFUSED || t < GRAB_LIFT;
    }

    // --- the drive key ------------------------------------------------------------------------------------------

    /** The pilot pressed (true) or let go of the drive key. Only the press acts, and every press answers. */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!held || !(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !lifts(m)) return;
        long now = m.level().getGameTime();
        if (into(m, now) >= 0) {
            Refusals.deny(pilot, Refuser.at(m), "mecha.lift.busy");
            return;
        }
        if (m.heldId() >= 0) {
            if (pilot.getXRot() >= SET_PITCH) {
                m.setLiftAt(now, SET);
            } else {
                m.setLiftAt(now, THROW);
                m.spend(THROW_FE);
                m.setHeat(m.heat() + THROW_HEAT);
            }
            return;
        }
        Component gantry = Component.translatable("block.headinajar.assembly_gantry");
        if (m.vent() > 0) {
            refuse(m, pilot, "mecha.lift.venting");
            return;
        }
        if (m.battery() < GRAB_FE) {
            refuse(m, pilot, "mecha.lift.no_power", gantry);
            return;
        }
        Entity target = nearest(m, pilot);
        if (target != null) {
            String why = whyNot(m, pilot, target);
            if (why == null && !roomOverhead(m, target.getBbWidth(), target.getBbHeight())) why = "mecha.lift.no_headroom";
            if (why != null) {
                refuse(m, pilot, why, target.getDisplayName());
                return;
            }
            m.liftTarget = target;
            m.liftBlock = null;
        } else {
            BlockPos block = block(m, pilot);
            if (block == null) {
                refuse(m, pilot, "mecha.lift.nothing");
                return;
            }
            String why = whyNot(pilot, m.serverLevel(), block, m.level().getBlockState(block));
            if (why == null && !roomOverhead(m, 0.98f, 0.98f)) why = "mecha.lift.no_headroom";
            if (why != null) {
                refuse(m, pilot, why);
                return;
            }
            m.liftTarget = null;
            m.liftBlock = block;
        }
        m.spend(GRAB_FE);
        m.setLiftAt(now, GRAB);
    }

    /** A refused press: the fists close on air (the clip's click at tick 10) and the reason is said. */
    static void refuse(MechaEntity m, ServerPlayer pilot, String why, Object... args) {
        m.setLiftAt(m.level().getGameTime(), REFUSED);
        Refusals.deny(pilot, Refuser.at(m), why, args);
    }

    static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    /**
     * The nearest creature within reach where the pilot looks (in a cone of 60 degrees each side), whatever it is: the
     * crosshair's way, not the body's, which turns after the look and may not have come round yet.
     */
    static Entity nearest(MechaEntity m, Player pilot) {
        Vec3 fwd = forward(pilot != null ? pilot.getYRot() : m.yBodyRot), at = m.position();
        double reach = m.getBbWidth() / 2 + REACH;
        AABB area = new AABB(at.x - reach, at.y - 1, at.z - reach, at.x + reach, at.y + 3, at.z + reach);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : m.level().getEntities(m, area, e -> e instanceof LivingEntity && e != pilot && e.isAlive() && !e.isSpectator())) {
            Vec3 to = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
            double d = to.length() - e.getBbWidth() / 2;
            if (d > reach || d < 0.01 && to.lengthSqr() < 1e-6) continue;
            if (to.normalize().dot(fwd) < 0.5) continue;
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** Why the fists won't take this creature, or null (bosses, other machines, the oversized, the ridden, untrusting players). */
    static String whyNot(MechaEntity m, Player pilot, Entity e) {
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return "mecha.lift.boss";
        if (e instanceof MechaEntity) return "mecha.lift.too_heavy";
        if (e instanceof Player p) {
            // the abilities, not isCreative(): what the game mode grants (a creative or spectator player is exempt)
            if (p.getAbilities().instabuild || p.isSpectator()) return "mecha.lift.exempt";
            if (!Hopper.trusts(p, pilot)) return "mecha.lift.untrusted";
        }
        if (e.isPassenger() || e.isVehicle()) return "mecha.lift.occupied";
        if (e.getBbWidth() > MAX_WIDTH || e.getBbHeight() > MAX_HEIGHT) return "mecha.lift.too_heavy";
        return null;
    }

    /** A loose block to lift: where the pilot looks, if within reach of the hull, else the one standing just ahead. */
    static BlockPos block(MechaEntity m, Player pilot) {
        Vec3 eye = pilot.getEyePosition(), end = eye.add(pilot.getLookAngle().scale(8));
        BlockHitResult hit = m.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot));
        double reach = m.getBbWidth() / 2 + REACH;
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 c = Vec3.atCenterOf(hit.getBlockPos());
            if (Math.hypot(c.x - m.getX(), c.z - m.getZ()) <= reach + 0.5 && c.y >= m.getY() - 0.5) return hit.getBlockPos();
        }
        Vec3 fwd = forward(m.yBodyRot);
        for (double ahead = m.getBbWidth() / 2 + 0.6; ahead <= reach; ahead += 0.5) {
            for (int up = 0; up < 2; up++) {
                BlockPos pos = BlockPos.containing(m.getX() + fwd.x * ahead, m.getY() + 0.5 + up, m.getZ() + fwd.z * ahead);
                if (!m.level().getBlockState(pos).isAir()) return pos;
            }
        }
        return null;
    }

    /** The Telekinesis module's rules for lifting a block (the same refusal reasons), applied to the Bulwark's fists. */
    static String whyNot(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState block) {
        if (block.isAir() || !block.getFluidState().isEmpty() && block.getFluidState().isSource()) return "mecha.lift.nothing";
        if (block.hasBlockEntity()) return "tk.machine";
        float hardness = block.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > Config.TELEKINESIS_MAX_HARDNESS.get()) return "tk.too_hard";
        if (block.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || block.hasProperty(BlockStateProperties.BED_PART)) return "tk.too_big";
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return "tk.protected";
        return NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, block, player)).isCanceled() ? "tk.protected" : null;
    }

    // --- every server tick ----------------------------------------------------------------------------------------

    public static void serverTick(MechaEntity m) {
        if (!lifts(m)) return;
        long now = m.level().getGameTime();
        Player pilot = Hosts.pilot(m);
        long lift = m.liftAt();
        if (lift >= 0) {
            int kind = (int) (lift & 3);
            long t = now - lift / 4;
            if (kind == GRAB && t == GRAB_CLOSE) take(m, pilot, now);
            else if (kind == REFUSED && t == GRAB_CLOSE) Hopper.play(m, "grab_dry");
            else if (kind == THROW && t == THROW_RELEASE) throwLoad(m, pilot);
            else if (kind == SET && t == SET_TICKS - 1) setDown(m);
        }
        if (m.heldId() < 0) return;
        Entity load = m.level().getEntity(m.heldId());
        if (load == null || !load.isAlive() || load.isRemoved() || load.level() != m.level() || pilot == null || load.isPassenger()
                || load.distanceToSqr(m) > 64 || m.isDeadOrDying()) {
            release(m, null);
            return;
        }
        if (!roomOverhead(m, load)) {
            release(m, null);
            if (pilot instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("hud.headinajar.lift_ceiling"), true);
            return;
        }
        if (m.vent() > 0 || m.battery() <= 0) {
            release(m, null);
            if (pilot instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(m.vent() > 0 ? "hud.headinajar.lift_vented" : "hud.headinajar.lift_flat"), true);
            return;
        }
        place(m, load, feet(m, load, now, m.liftFrom));
        m.spend(HOLD_FE);
    }

    /** The grab clip's tick 10: the fists close on what they reached for, if it is still there. */
    static void take(MechaEntity m, Player pilot, long now) {
        Entity target = m.liftTarget;
        BlockPos block = m.liftBlock;
        m.liftTarget = null;
        m.liftBlock = null;
        if (!(pilot instanceof ServerPlayer sp)) return;
        double reach = m.getBbWidth() / 2 + REACH + 1.5;
        String why = null;
        if (target != null) {
            if (!target.isAlive() || target.level() != m.level() || target.isPassenger()
                    || Math.hypot(target.getX() - m.getX(), target.getZ() - m.getZ()) - target.getBbWidth() / 2 > reach) why = "mecha.lift.slipped";
        } else if (block != null) {
            BlockState state = m.level().getBlockState(block);
            why = whyNot(sp, m.serverLevel(), block, state);
            if (why == null) {
                // a falling block, raised a little so it doesn't land where it stood; held up until it is let go
                m.level().removeBlock(block, false);
                FallingBlockEntity held = new FallingBlockEntity(m.level(), block.getX() + 0.5, block.getY() + 0.2, block.getZ() + 0.5, state);
                held.time = 1;
                m.level().addFreshEntity(held);
                target = held;
            }
        } else {
            why = "mecha.lift.slipped";
        }
        if (why != null) {
            // closed on air after all: the refused grab from its tick 10 on (the two clips match until then)
            m.setLiftAt(now - GRAB_CLOSE, REFUSED);
            Hopper.play(m, "grab_dry");
            Refusals.deny(sp, Refuser.at(m), why);
            return;
        }
        m.setHeldId(target.getId());
        m.liftFrom = target.position();
        HOLDERS.put(target, m);
        if (target instanceof Mob mob) mob.getNavigation().stop();
        Hopper.play(m, "grab_close");
    }

    /** The load's weight for a throw: its box's volume (a cow is about 1.1), never less than a quarter. */
    static double weight(Entity load) {
        return Math.max(0.25, load.getBbWidth() * load.getBbWidth() * load.getBbHeight());
    }

    /** The throw's speed, blocks a tick: 1.5 for a cow-sized load, less for heavier, at most 1.6. */
    public static double throwSpeed(Entity load) {
        return Mth.clamp(1.5 / Math.sqrt(weight(load)), 0.5, 1.6);
    }

    /** The throw's release (tick 11): along the pilot's look, a little up, faster the lighter the load. */
    static void throwLoad(MechaEntity m, Player pilot) {
        if (m.heldId() < 0) return;
        Entity load = m.level().getEntity(m.heldId());
        if (load == null) {
            release(m, null);
            return;
        }
        float yaw = pilot != null ? pilot.getYRot() : m.yBodyRot;
        float pitch = Mth.clamp(pilot != null ? pilot.getXRot() : 0, -50, 10);
        Vec3 v = Vec3.directionFromRotation(pitch, yaw).scale(throwSpeed(load)).add(0, 0.3, 0);
        m.lastThrow = v;
        load.fallDistance = 0;
        release(m, v);
        Hopper.play(m, "throw_release");
        dust(m, load.position(), 10);
    }

    /** The set-down's end: the load stands on the ground in front, still. */
    static void setDown(MechaEntity m) {
        if (m.heldId() < 0) return;
        Entity load = m.level().getEntity(m.heldId());
        if (load != null) {
            Vec3 at = feet(m, load, m.level().getGameTime() + 1, null);
            place(m, load, at);
            load.fallDistance = 0;
            dust(m, at, 6);
        }
        release(m, Vec3.ZERO);
        Hopper.play(m, "set_down");
    }

    /**
     * Lets go of the load, if any: with this velocity (a throw, a set-down) or none at all (it simply falls). Also what
     * every other way out ends in: the pilot leaving, the machine's death or removal, the load going away.
     */
    public static void release(MechaEntity m, Vec3 velocity) {
        if (m.level().isClientSide) return;
        m.liftTarget = null;
        m.liftBlock = null;
        int id = m.heldId();
        if (id < 0) return;
        m.setHeldId(-1);
        Entity load = m.level().getEntity(id);
        if (load == null) return;
        HOLDERS.remove(load);
        Vec3 v = velocity == null ? Vec3.ZERO : velocity;
        // a thrown block can hurt what it lands on (the Telekinesis throw's rule)
        if (load instanceof FallingBlockEntity block && v.lengthSqr() > 0) block.setHurtsEntities(2.0f, 40);
        load.setDeltaMovement(v);
        load.hurtMarked = true;
    }

    // --- where the load is ----------------------------------------------------------------------------------------

    /** A model point (pixels) in the world, turned by the machine's body yaw. */
    static Vec3 world(MechaEntity m, Vec3 px) {
        Vec3 fwd = forward(m.yBodyRot);
        return m.position().add(fwd.scale(-px.z / 16)).add(0, px.y / 16, 0);
    }

    /**
     * Where the load's feet go at {@code now}: lifted from where it stood to the fists overhead over the grab's ticks
     * 12-21, there while held and through the throw until its release, lowered to the ground ahead while set down.
     * Shared by the server (which owns it) and every client (which draws it from the same synced moments).
     */
    public static Vec3 feet(MechaEntity m, Entity load, double now, Vec3 from) {
        Vec3 hold = world(m, HOLD);
        long lift = m.liftAt();
        if (lift < 0) return hold;
        int kind = (int) (lift & 3);
        double t = now - lift / 4;
        if (kind == GRAB && t < GRAB_HELD && from != null) {
            double s = Mth.clamp((t - GRAB_LIFT) / (GRAB_HELD - GRAB_LIFT), 0, 1);
            return from.lerp(hold, s * s * (3 - 2 * s));
        }
        if (kind == SET && t < SET_TICKS + 2) {
            Vec3 ground = m.position().add(forward(m.yBodyRot).scale(m.getBbWidth() / 2 + GROUND_AHEAD - 0.8 + load.getBbWidth() / 2));
            double s = Mth.clamp(t / (SET_TICKS - 1), 0, 1);
            return hold.lerp(ground, s * s * (3 - 2 * s));
        }
        // held, and through the throw until its release: on the fists overhead
        return hold;
    }

    /** Room for a load this wide and tall standing on the fists overhead (no block in its way there). */
    static boolean roomOverhead(MechaEntity m, float width, float height) {
        Vec3 at = world(m, HOLD);
        double w = width / 2;
        return m.level().noCollision(new AABB(at.x - w, at.y, at.z - w, at.x + w, at.y + height, at.z + w));
    }

    /** Room for this carried load where it is held now (a load entering a block above means a ceiling came down on it). */
    static boolean roomOverhead(MechaEntity m, Entity load) {
        if (m.liftAt() >= 0 && (m.liftAt() & 3) != GRAB) return true;
        long t = m.level().getGameTime() - m.liftAt() / 4;
        if (m.liftAt() >= 0 && t < GRAB_HELD) return true;
        return roomOverhead(m, load.getBbWidth() * 0.9f, load.getBbHeight() * 0.9f);
    }

    /** Puts the load there, crosswise to the machine, still and not falling. */
    public static void place(MechaEntity m, Entity load, Vec3 at) {
        float yaw = m.yBodyRot + 90;
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
        // a held block is never let land or time out
        if (load instanceof FallingBlockEntity block) block.time = 1;
    }

    static void dust(MechaEntity m, Vec3 at, int count) {
        BlockState below = m.level().getBlockState(m.getBlockPosBelowThatAffectsMyMovement());
        if (below.isAir()) return;
        m.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), at.x, at.y + 0.3, at.z, count, 0.4, 0.2, 0.4, 0.1);
    }
}
