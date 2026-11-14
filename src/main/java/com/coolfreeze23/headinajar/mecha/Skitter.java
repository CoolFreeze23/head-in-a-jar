package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Skitter's Dragline, on the drive key: a press fires the company's hi-vis safety line from the line gun at the
 * block the pilot looks at, within 24 blocks; from the fire clip's tick 9 the machine hangs from it. Held, the reel
 * winds the line in (up a drop, across a gap, along the ground); the jump key held while hanging pays it out (the
 * rappel); let go, the brake holds and the machine swings under the anchor. A tap of the drive key cuts the line (on its
 * cut clip's tick 4). On the ground or a wall the line trails, paid out and taken up as the machine goes.
 * <p>
 * Server-authoritative: the moments, the anchor, the length and what the reel does are synced on the entity. The line
 * itself holds the machine where the machine is moved (its pilot's client, {@link #hold}), so the swing is never undone
 * by the server's zeroed motion. A press it can't honour (venting, no power, nothing to fix to) plays the refusal clip
 * and says why once. Nothing is saved: a line in use is let go when the session ends.
 */
public final class Skitter {
    /** Kinds of the synced moment. */
    public static final int FIRED = 0, CUT = 1, REFUSED = 2;
    /** What the reel does: the line trails (slack), winds in, pays out, holds (the brake). */
    public static final int SLACK = 0, REEL = 1, RAPPEL = 2, HANG = 3;
    /** The clips' lengths and their moments: the line leaves the gun on the fire's tick 9, the cutter snaps on the cut's 4. */
    public static final int FIRE_TICKS = 18, LINE_OUT = 9, CUT_TICKS = 16, CUT_AT = 4, REFUSED_TICKS = 30;
    /** A press released within this many ticks is a tap (it cuts); held longer, the reel winds in. */
    public static final int TAP = 6;
    /**
     * Reach of the line gun, the longest and shortest line (wound fully in, the cage and the jar on it stay clear of the
     * anchor's block), and the reel's and the rappel's speeds (blocks, blocks a tick).
     */
    public static final double RANGE = 24, MAX_LENGTH = 32, MIN_LENGTH = 2.5, REEL_SPEED = 0.25, RAPPEL_SPEED = 0.15;
    /**
     * Power, by the frames' rule: a full-strength use costs about 1% of the battery (200 kFE: 2,000 FE a line fired), the
     * reel while it winds the frame's own idle drain a tick (3 FE). Heat at the fire.
     */
    public static final int FIRE_FE = 2000, REEL_FE = 3;
    public static final float FIRE_HEAT = 3;
    /** The hang point over the reel and the gun's muzzle (model px, the machine facing north). */
    static final Vec3 HANG_PX = new Vec3(0, 18, 14), MUZZLE_PX = new Vec3(11.5, 19.5, -18.5);

    private Skitter() {
    }

    static boolean draglines(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null && rig.verb == MechaRig.Verb.DRAGLINE;
    }

    /** Where the line hangs from: over the reel, turned with the body. */
    public static Vec3 hangPoint(MechaEntity m) {
        return Wheelhouse.at(m, HANG_PX);
    }

    /** Why a line can't be fired now, or null. */
    public static String refusal(MechaEntity m) {
        if (m.vent() > 0) return "mecha.dragline.venting";
        if (m.battery() < FIRE_FE) return "mecha.dragline.no_power";
        return null;
    }

    /**
     * The block the pilot's look meets within the gun's reach (only what has a solid face to take the line), or null.
     * The world's invisible walls (barriers) take no line.
     */
    static BlockHitResult aim(Player pilot) {
        Vec3 eye = pilot.getEyePosition();
        BlockHitResult hit = pilot.level().clip(new ClipContext(eye, eye.add(pilot.getLookAngle().scale(RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, pilot));
        if (hit.getType() != HitResult.Type.BLOCK || pilot.level().getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.BARRIER)) return null;
        return hit;
    }

    /** The line is out and the machine off the ground: the line holds it (the jump key is the rappel then). */
    public static boolean lineHolds(MechaEntity m) {
        return draglines(m) && out(m) && !m.onGround();
    }

    /**
     * A frame that climbs holds on to the wall it is against, off the ground, while the pilot neither sneaks nor
     * steers away from the wall, with power and not venting (pushing into it climbs, by the existing rule).
     */
    public static boolean clings(MechaEntity m, Player pilot) {
        MechaRig rig = MechaRig.of(m.frame());
        if (rig == null || !rig.climbs) return false;
        net.minecraft.core.Direction wall = m.onGround() ? null : Legs.wall(m);
        float yaw = m.getYRot() * Mth.DEG_TO_RAD;
        double x = pilot.xxa * Mth.cos(yaw) - pilot.zza * Mth.sin(yaw), z = pilot.zza * Mth.cos(yaw) + pilot.xxa * Mth.sin(yaw);
        double into = wall == null ? 0 : x * wall.getStepX() + z * wall.getStepZ();
        // a sneak lets go until the machine is off the wall or the pilot steers it back into the wall: a tap is enough
        if (wall == null || into > 0.5) m.clingLetGo = false;
        if (pilot.isShiftKeyDown() || pilot.isCrouching()) m.clingLetGo = true;
        if (wall == null || m.clingLetGo || m.battery() <= 0 || m.vent() > 0) return false;
        return into >= -0.1;
    }

    /**
     * Each server tick of a frame that climbs, on a wall: with a flat battery or venting it cannot hold on, and the pilot
     * is told once for each time on a wall (the client lets go by the same rule).
     */
    public static void clingTick(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        if (rig == null || !rig.climbs) return;
        boolean onWall = !m.onGround() && Legs.wall(m) != null;
        if (!onWall) {
            m.clingSaid = false;
            return;
        }
        if (!m.clingSaid && (m.battery() <= 0 || m.vent() > 0) && Hosts.pilot(m) instanceof ServerPlayer sp) {
            m.clingSaid = true;
            Refusals.deny(sp, Refuser.at(m), m.vent() > 0 ? "mecha.cling.venting" : "mecha.cling.no_power",
                    Component.translatable("block.headinajar.assembly_gantry"));
        }
    }

    /** The line is out (fixed, from the fire's tick 9 until it is cut or let go). */
    public static boolean out(MechaEntity m) {
        return m.lineLength() != 0;
    }

    /** The drive key went down or up in the Skitter's cockpit (the pogo payload's handler routes it here). */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !draglines(m)) return;
        long now = m.level().getGameTime();
        if (!held) {
            // a tap cuts; a hold that wound the reel just stops it
            if (m.linePressAt >= 0 && now - m.linePressAt < TAP && out(m)) {
                m.setLineAt(now, CUT);
                Hopper.play(m, "servo");
            }
            m.linePressAt = -1;
            return;
        }
        long line = m.lineAt();
        double t = line < 0 ? Double.MAX_VALUE : now - line / 4;
        int kind = (int) (line & 3);
        if (line >= 0 && (kind == FIRED && t < FIRE_TICKS || kind == CUT && t < CUT_TICKS)) {
            // the gun is in the middle of a clip: said in text only (replaying the refusal would break the clip)
            Refusals.deny(pilot, Refuser.at(m), "mecha.dragline.busy");
            return;
        }
        if (out(m)) {
            m.linePressAt = now;
            return;
        }
        String why = refusal(m);
        BlockHitResult hit = why == null ? aim(pilot) : null;
        if (why == null && hit == null) why = "mecha.dragline.nothing";
        if (why != null) {
            m.setLineAt(now, REFUSED);
            Hopper.play(m, "line_cough");
            Refusals.deny(pilot, Refuser.at(m), why, Component.translatable("block.headinajar.assembly_gantry"));
            return;
        }
        m.spend(FIRE_FE);
        m.setHeat(m.heat() + FIRE_HEAT);
        m.setAnchor(hit.getLocation());
        m.lineBlock = hit.getBlockPos();
        m.setLineLength(0);
        m.setLineMode(SLACK);
        m.setLineAt(now, FIRED);
        // the firing press, held on past the fire, winds the reel in at once
        m.linePressAt = now;
    }

    /** The pilot has gone (or the machine is going): whatever line is out, or about to be, is let go. */
    public static void letGo(MechaEntity m) {
        if (!draglines(m)) return;
        long line = m.lineAt();
        if (line >= 0 && (line & 3) == FIRED && m.level().getGameTime() - line / 4 < LINE_OUT) m.setLineAt(-1, FIRED);
        m.setLineLength(0);
        m.setLineMode(SLACK);
        m.linePressAt = -1;
    }

    /** Each server tick of a Skitter: the line's moments, what the reel does, and whatever ends the line. */
    public static void serverTick(MechaEntity m) {
        clingTick(m);
        if (!draglines(m)) return;
        ServerLevel level = m.serverLevel();
        long now = level.getGameTime();
        Player pilot = Hosts.pilot(m);
        long line = m.lineAt();
        if (line >= 0) {
            long t = now - line / 4;
            int kind = (int) (line & 3);
            if (kind == FIRED && t == LINE_OUT) {
                if (pilot == null || !m.isAlive()) {
                    letGo(m);
                    return;
                }
                // the line leaves the gun: it is fixed from now on, at the length it reaches
                double reach = hangPoint(m).distanceTo(m.anchor());
                if (m.onGround()) {
                    m.setLineLength(-1);
                    m.setLineMode(SLACK);
                } else {
                    m.setLineLength((float) Mth.clamp(reach, MIN_LENGTH, MAX_LENGTH));
                    m.setLineMode(HANG);
                }
                Hopper.play(m, "line_fire");
                Vec3 muzzle = Wheelhouse.at(m, MUZZLE_PX);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, com.coolfreeze23.headinajar.fx.HouseSounds.LINE_SHOT.get(), SoundSource.NEUTRAL, 1, 0.8f);
                level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.02);
                Vec3 a = m.anchor();
                level.playSound(null, a.x, a.y, a.z, com.coolfreeze23.headinajar.fx.HouseSounds.LINE_ANCHOR.get(), SoundSource.NEUTRAL, 1, 1.2f);
            } else if (kind == CUT && t == CUT_AT && out(m)) {
                Vec3 muzzle = Wheelhouse.at(m, MUZZLE_PX);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, com.coolfreeze23.headinajar.fx.HouseSounds.LINE_SNIP.get(), SoundSource.NEUTRAL, 1, 0.9f);
                letGo(m);
            }
        }
        if (!out(m)) return;
        if (pilot == null || !m.isAlive()) {
            letGo(m);
            return;
        }
        Vec3 anchor = m.anchor();
        if (m.lineBlock == null || level.getBlockState(m.lineBlock).getCollisionShape(level, m.lineBlock).isEmpty()) {
            lost(m, pilot, "hud.headinajar.dragline_lost");
            return;
        }
        double reach = hangPoint(m).distanceTo(anchor);
        if (reach > MAX_LENGTH + 2) {
            lost(m, pilot, "hud.headinajar.dragline_snapped");
            return;
        }
        boolean grounded = m.onGround() || Legs.wall(m) != null;
        boolean reeling = m.linePressAt >= 0 && now - m.linePressAt >= TAP && m.battery() > 0 && m.vent() == 0;
        float length = m.lineLength() < 0 ? (float) Mth.clamp(reach, MIN_LENGTH, MAX_LENGTH) : m.lineLength();
        if (reeling) {
            m.setLineMode(REEL);
            m.setLineLength((float) Math.max(MIN_LENGTH, length - REEL_SPEED));
            m.spend(REEL_FE);
        } else if (!grounded && pilot.jumping) {
            m.setLineMode(RAPPEL);
            m.setLineLength((float) Math.min(MAX_LENGTH, length + RAPPEL_SPEED));
        } else if (grounded) {
            m.setLineMode(SLACK);
            m.setLineLength(-1);
        } else {
            m.setLineMode(HANG);
            m.setLineLength(length);
        }
        // the line holds it: no fall builds up while it hangs
        if (!m.onGround()) m.fallDistance = 0;
    }

    private static void lost(MechaEntity m, Player pilot, String line) {
        letGo(m);
        Hopper.play(m, "servo_sag");
        if (pilot instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(line), true);
    }

    /**
     * The line holds the machine where it is moved (its pilot's client, from {@code MechaEntity.travel}): past the
     * line's length from the anchor, the hang point is drawn back onto it and the speed outward along the line is taken
     * away; the speed across it is kept, so the machine swings. A slack line holds nothing.
     */
    public static void hold(MechaEntity m) {
        if (!draglines(m) || m.lineLength() <= 0) return;
        Vec3 h = hangPoint(m), a = m.anchor();
        Vec3[] held = constrain(h, m.getDeltaMovement(), a, m.lineLength());
        if (held == null) return;
        m.move(MoverType.SELF, held[0].subtract(h));
        m.setDeltaMovement(held[1]);
        m.fallDistance = 0;
    }

    /**
     * A point on a line of {@code length} from {@code anchor}: where it is held to and the speed it keeps, or null if
     * the line is not taut. Pure arithmetic.
     */
    public static Vec3[] constrain(Vec3 point, Vec3 velocity, Vec3 anchor, double length) {
        Vec3 d = point.subtract(anchor);
        double dist = d.length();
        if (dist <= length || dist < 1e-6) return null;
        Vec3 dir = d.scale(1 / dist);
        double outward = velocity.dot(dir);
        Vec3 kept = outward > 0 ? velocity.subtract(dir.scale(outward)) : velocity;
        return new Vec3[]{anchor.add(dir.scale(length)), kept};
    }

    /** The reel's drawn turn for a change of the line's length (blocks): the 4 px drum, wound in is +. */
    public static float reelTurn(float shortened) {
        return (float) Math.toDegrees(shortened * 16 / 4.0);
    }
}
