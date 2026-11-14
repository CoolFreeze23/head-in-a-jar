package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.util.Mth;

/**
 * What a client works out for itself about an animated mecha it draws, frame by frame: how fast it really moves, how
 * far into its walk it is, when it left the ground and landed, how fast its body turns, when an arm button went down
 * and up. Everything else the clips need is synced state on the entity. Plain arithmetic, no client classes.
 */
public final class HopperView {
    /** The Hopper's walk design speed, blocks a tick, and its limp's (each frame's are in {@link MechaRig}). */
    public static final double WALK_DESIGN = 0.2808, LIMP_DESIGN = 0.0842;
    /** Above this measured speed the machine walks; below it, it stands. */
    public static final double WALKING = 0.02;

    double lastNow = Double.NaN;
    long lastTick = Long.MIN_VALUE;
    /** Measured horizontal speed, blocks a tick, chased at k = 10 a second. */
    public double speed;
    double walkPhase, limpPhase;
    /** Body turn rate, degrees a tick (chased), and the yaw it was last seen at. */
    public float turn;
    float lastYaw = Float.NaN;
    boolean wasOnGround = true;
    int airTicks;
    double leftGroundY, peakY;
    public double landAt = -1, jumpAt = -1;
    public boolean landHeavy;
    /** Per arm: when the button went down and when it came up (game time), and the module's verb then. */
    final double[] pressAt = {-1, -1}, releaseAt = {-1, -1};
    final HopperAnimation.Verb[] verb = new HopperAnimation.Verb[2];
    /** The last boot tick whose relay clicks were played, and the drill's spin. */
    public double bootTick = -1;
    public float drillAngle, drillSpeed;
    /** A small forward lean after stopping (degrees), and the measured speed a moment ago. */
    public float stopLean;
    double lastSpeed;
    /** The chin sensor's chased look, degrees, and the time the last frame covered (ticks). */
    public float sensorYaw, sensorPitch;
    /** The jets' last burst (game time) and the bursts spent since the last landing. */
    public long jetAt = -1;
    public int jetsUsed;
    /** Drawn as a parked machine this frame (no controllers ran), and how many frames it has been processed as one. */
    public boolean still;
    public int stillFrames;
    /** Beyond the small motions' range from the camera this frame. */
    public boolean far;
    /** Ticks into getting out (client: the unboard's sounds), -1 when not. */
    public double unboardTick = -1;
    /** What this client remembers of the fitted modules' own motion (arm presses, deploys, the grapple's line). */
    public final ModuleMotion.State modules = new ModuleMotion.State();
    /** Each flat slot's tool muzzle as last drawn, in the world, and the frame's time (game time + partial) it was drawn at. */
    public final net.minecraft.world.phys.Vec3[] muzzle = new net.minecraft.world.phys.Vec3[MechaFrame.maxSlots()];
    public final double[] muzzleAt = new double[MechaFrame.maxSlots()];
    /** What each socket carries, and the module list it was worked out for (client renderer's cache). */
    public Object fits, fitsFor;
    double lastDt;
    /** Heavy Lifting: the load this client last saw held, and where it was when the fists took it. */
    public int heldSeen = -1;
    public net.minecraft.world.phys.Vec3 liftFrom;
    /** A flier: measured vertical speed (blocks a tick, chased), the wingbeat's rate and phase, when this client saw it
     * stall (flat in the air) and start its flare, and the body's roll into turns and pitch in a climb or dive (degrees). */
    public double vy, flapRate = 1, flapPhase, carryPhase;
    public double stallAt = -1, flareAt = -1;
    public float roll, pitch;
    /** The bank clip's start and the side it rolls to (1 right, -1 left). */
    public double bankAt = -1;
    public int bankSide;
    /** Client: the tracked bones' world matrices as this machine was last drawn (the effects' locators read them). */
    public final java.util.Map<String, org.joml.Matrix4f> bones = new java.util.HashMap<>();
    /** Gene Port vials: when each last pulsed (game time), and the resting ports last seen (a rise is a gene firing). */
    public final double[] pulseAt = {-99, -99, -99, -99};
    public int restingSeen;
    /** The Siege hum loop is playing for this machine. */
    public boolean humming;
    /** A frame on wheels: its wheels, steering, lean and ride ({@link Wheels}). */
    public final Wheels.State wheels = new Wheels.State();
    /** The buffer's last ram this client has answered (game time). */
    public long ramSeen = Long.MIN_VALUE;
    /** Measured speed along the body's facing (blocks a tick, + forward), chased, and the backing gait's phase. */
    public double forward, backPhase;
    /** A frame that climbs: the wall it is on ({@link Legs.Climb}). */
    public final Legs.Climb climb = new Legs.Climb();
    /** A multi-leg frame: the ground under each pad and the body's tilt on it, as code last set them ({@link Legs}). */
    public final Legs.Pose legs = new Legs.Pose();
    /** The Dragline's reel: its drawn turn (degrees) and the line's length it was last turned for. */
    public float reelAngle;
    public float reelLength = Float.NaN;
    /** The line gun's chased aim at the anchor (degrees) and the hanging model's chased up (toward the anchor). */
    public float aimYaw, aimPitch;
    public net.minecraft.world.phys.Vec3 lineUp = new net.minecraft.world.phys.Vec3(0, 1, 0);

    public double frameDelta() {
        return lastDt;
    }

    /** Brings the view up to {@code now} (game time plus partial tick). */
    public void update(MechaEntity m, double now) {
        double dt = Double.isNaN(lastNow) ? 0 : Mth.clamp(now - lastNow, 0, 5);
        lastNow = now;
        lastDt = dt;
        double measured = Math.sqrt(Mth.square(m.getX() - m.xo) + Mth.square(m.getZ() - m.zo));
        speed += (measured - speed) * (1 - Math.exp(-dt * 10 / 20.0));
        if (speed < 1e-4) speed = 0;
        MechaRig rig = HopperAnimation.rig(m);
        // the phase wraps at a whole number of every walk's cycles, so switching walks or carrying never jumps the feet
        walkPhase = (walkPhase + dt * Mth.clamp(speed / rig.walkDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
        limpPhase = (limpPhase + dt * Mth.clamp(speed / rig.limpDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
        float yawRad = m.yBodyRot * Mth.DEG_TO_RAD;
        double along = (m.getX() - m.xo) * -Mth.sin(yawRad) + (m.getZ() - m.zo) * Mth.cos(yawRad);
        forward += (along - forward) * (1 - Math.exp(-dt * 10 / 20.0));
        if (rig.backDesign > 0) backPhase = (backPhase + dt * Mth.clamp(speed / rig.backDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
        if (rig.climbs) climb.advance(dt, rig.climbDesign);
        if (rig.wheels != null) {
            boolean drifting = HopperAnimation.drifting(m, wheels);
            wheels.update(m, rig.wheels, now, Wheels.onTheSpot(wheels) == 0 && !drifting, drifting);
        }
        // the motion study's stop: a 1 degree lean forward that settles over 8 ticks
        if (lastSpeed > WALKING && speed <= WALKING) stopLean = 1;
        stopLean = (float) Math.max(0, stopLean - dt / 8);
        lastSpeed = speed;
        if (rig.flies()) fly(m, rig, now, dt);
        long tick = (long) Math.floor(now);
        if (tick != lastTick) {
            lastTick = tick;
            float yaw = m.yBodyRot;
            float rate = Float.isNaN(lastYaw) ? 0 : Mth.wrapDegrees(yaw - lastYaw);
            turn += (rate - turn) * 0.5f;
            lastYaw = yaw;
            boolean ground = m.onGround();
            if (rig.climbs) climb.tick(m, tick, ground);
            if (ground) {
                jetsUsed = 0;
                if (!wasOnGround && airTicks > 1) {
                    landAt = tick;
                    landHeavy = peakY - m.getY() >= 4;
                }
                airTicks = 0;
            } else {
                if (wasOnGround) {
                    leftGroundY = peakY = m.getY();
                    if (m.getDeltaMovement().y > 0.05 || m.getY() > m.yo) jumpAt = tick;
                }
                peakY = Math.max(peakY, m.getY());
                airTicks++;
            }
            wasOnGround = ground;
        }
    }

    /**
     * A flier's motion: the wingbeat's playback, clamp(0.7 + 0.6 x vy / 0.12 + 0.3 x speed / 0.6, 0.5, 1.8) chased at
     * k 6 (harder climbing and fast, slower level; 0.85 of it carrying), the stall and the flare's start (a ground probe
     * within two blocks while coming down), and the roll (2.5 x the turn rate, at most 30 degrees) and pitch (60 x the
     * sink rate, -20 to 15) on the whole machine in flight, chased at k 8.
     */
    private void fly(MechaEntity m, MechaRig rig, double now, double dt) {
        double measuredY = m.getY() - m.yo;
        vy += (measuredY - vy) * (1 - Math.exp(-dt * 10 / 20.0));
        double rate = Mth.clamp(0.7 + 0.6 * (vy / 0.12) + 0.3 * (speed / MechaFrame.FLIGHT_SPEED), 0.5, 1.8);
        flapRate += (rate - flapRate) * (1 - Math.exp(-dt * 6 / 20.0));
        flapPhase = (flapPhase + dt * flapRate) % MechaRig.PHASE_WRAP;
        carryPhase = (carryPhase + dt * flapRate * 0.85) % MechaRig.PHASE_WRAP;
        boolean air = !m.onGround();
        if (air && m.battery() <= 0 && HopperAnimation.piloted(m)) {
            if (stallAt < 0) stallAt = now;
        } else {
            stallAt = -1;
        }
        boolean descending = air && vy < -0.02;
        if (descending && !m.level().noCollision(m, m.getBoundingBox().expandTowards(0, -2.0, 0))) {
            if (flareAt < 0) flareAt = now;
        } else if (!air || vy > 0.02) {
            flareAt = -1;
        }
        float wantRoll = air ? (float) Mth.clamp(turn * 2.5, -30, 30) : 0;
        float wantPitch = air ? (float) Mth.clamp(-vy * 60, -20, 15) : 0;
        float k = (float) (1 - Math.exp(-dt * 8 / 20.0));
        roll += (wantRoll - roll) * k;
        pitch += (wantPitch - pitch) * k;
        if (Math.abs(roll) < 0.01f) roll = 0;
        if (Math.abs(pitch) < 0.01f) pitch = 0;
    }

    public double walkPhase() {
        return walkPhase;
    }

    public double limpPhase() {
        return limpPhase;
    }
}
