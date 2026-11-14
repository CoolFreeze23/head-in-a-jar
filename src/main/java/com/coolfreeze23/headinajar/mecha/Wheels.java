package com.coolfreeze23.headinajar.mecha;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The wheeled skeleton's shared motion (the Wheelhouse, the Rover): what a client measures of a machine on wheels and
 * what code, not a clip, does to its bones. The wheels are spun from the distance each one rolls (the tyre on
 * {@code steer_*} never spins; only {@code wheel_*} does), the steering angle comes from the body's turn rate and its
 * speed, the body leans in the turn, each wheel station rides the ground under it, and the drive's clips (start, drive,
 * reverse, brake, bump) are chosen here from the measured motion. Plain arithmetic and block lookups, no client classes.
 * <p>
 * Signs are the model's (Blockbench's): {@code steer_*} y + is turned left, {@code wheel_*} x - is rolling forward,
 * {@code body} z + is left side down, {@code helm} z + is steering left. A value set in code on a GeckoLib bone takes
 * the same sign (the exported clips are in Bedrock's convention and GeckoLib turns them back).
 */
public final class Wheels {
    /** Above this measured speed (blocks a tick) the machine is driving. */
    public static final double MOVING = 0.02;

    /**
     * One wheeled rig: the contacts (model pixels, x left -, z front -), the spin per block rolled and its cap per tick,
     * the design speeds the drive clips were authored at, the steering's lock (front), the rear's share (turned the
     * other way) and the helm's, the lean (degrees per degree-a-tick x block-a-tick, its limit and sign: +1 into the
     * turn, -1 out of it), how far a wheel station rides up or down (px), the bone the lean sways the other way
     * (or null), and the playback clips' names.
     */
    public record Spec(double[] contactX, double[] contactZ, double degPerBlock, double spinCap,
                       double driveDesign, double reverseDesign, double limpDesign, double rushDesign,
                       float steerLock, float rearShare, float helmRatio, float lean, float leanMax, float leanSign,
                       float strutTravel, String swayBone, String startClip, int startLength, int brakeLength, int bumpLength) {
        /** Wheelbase, blocks (front to rear contacts). */
        public double wheelbase() {
            return (contactZ[2] - contactZ[0]) / 16;
        }

        /** Half the track, blocks. */
        public double halfTrack() {
            return Math.abs(contactX[0]) / 16;
        }
    }

    /** Wheel order everywhere: front left, front right, rear left, rear right. */
    public static final String[] CORNERS = {"fl", "fr", "rl", "rr"};

    /** What one client measures of one machine on wheels, frame by frame. */
    public static final class State {
        double lastNow = Double.NaN;
        long lastTick = Long.MIN_VALUE;
        /** Signed speed along the body's facing (blocks a tick, + forward), chased at k 10 a second. */
        public double signed;
        /** Body turn rate, degrees a tick (+ to the right, the game's yaw), chased. */
        public float turn;
        float lastYaw = Float.NaN;
        double lastY = Double.NaN;
        /** Playback phases of the looping drive clips. */
        public double drivePhase, reversePhase, limpPhase, rushPhase;
        /** When the drive started, braked hard and took a step at speed (game ticks; -1 none). */
        public double startAt = -1, brakeAt = -1, bumpAt = -1;
        boolean wasMoving;
        private final double[] history = new double[8];
        private int historyAt;
        /** The wheels' accumulated spin, degrees (fl, fr, rl, rr). */
        public final float[] spin = new float[4];
        /** The front wheels' steering angle (degrees, + left), the lean (degrees, + left side down), the drift's swing. */
        public float steer, lean, drift;
        /** Each wheel station's ride (px, + up into its arch): the ground under it, then chased. */
        final float[] groundTarget = new float[4];
        public final float[] ride = new float[4];
        /** The whole machine's tilt on uneven ground (degrees: pitch + nose up, roll + left side down). */
        float pitchTarget, rollTarget;
        public float pitch, roll;
        /** Decelerating this frame (the brake lamps). */
        public boolean slowing;
        double dt;

        public double frameDelta() {
            return dt;
        }

        /** The speed as a positive number. */
        public double speed() {
            return Math.abs(signed);
        }

        /**
         * Brings the state up to {@code now}. {@code steering}: no turn or drift clip holds the steering (code steers);
         * {@code drifting}: the rear wheels slide (their spin is frozen).
         */
        public void update(LivingEntity e, Spec spec, double now, boolean steering, boolean drifting) {
            dt = Double.isNaN(lastNow) ? 0 : Mth.clamp(now - lastNow, 0, 5);
            lastNow = now;
            float yawRad = e.yBodyRot * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
            double measured = (e.getX() - e.xo) * fx + (e.getZ() - e.zo) * fz;
            double before = signed;
            signed += (measured - signed) * (1 - Math.exp(-dt * 10 / 20.0));
            if (Math.abs(signed) < 1e-4) signed = 0;
            slowing = signed > 0.05 && signed < before - 0.004 * dt;
            double speed = Math.abs(signed);
            drivePhase = (drivePhase + dt * Mth.clamp(speed / spec.driveDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            reversePhase = (reversePhase + dt * Mth.clamp(speed / spec.reverseDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            limpPhase = (limpPhase + dt * Mth.clamp(speed / spec.limpDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            rushPhase = (rushPhase + dt * Mth.clamp(speed / spec.rushDesign, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
            long tick = (long) Math.floor(now);
            if (tick != lastTick) {
                boolean first = lastTick == Long.MIN_VALUE;
                lastTick = tick;
                float rate = Float.isNaN(lastYaw) ? 0 : Mth.wrapDegrees(e.yBodyRot - lastYaw);
                turn += (rate - turn) * 0.5f;
                lastYaw = e.yBodyRot;
                // the drive starts, a hard stop, a step taken at speed
                boolean moving = speed > MOVING;
                if (moving && !wasMoving && !first) startAt = tick;
                wasMoving = moving;
                history[historyAt++ % history.length] = speed;
                double peak = 0;
                for (double h : history) peak = Math.max(peak, h);
                if (peak > 0.15 && speed < 0.05 && (brakeAt < 0 || tick - brakeAt > spec.brakeLength)) {
                    brakeAt = tick;
                    java.util.Arrays.fill(history, 0);
                }
                if (!Double.isNaN(lastY) && e.onGround() && e.getY() - lastY >= 0.45 && speed > 0.15) bumpAt = tick;
                lastY = e.getY();
                ground(e, spec);
            }
            // each wheel rolls its own distance: the body's forward motion, plus the turn (a right turn rolls the left
            // wheels forward); the drawn spin is capped a tick so a fast wheel never seems to turn backwards
            double omega = turn * Mth.DEG_TO_RAD, half = spec.halfTrack();
            for (int i = 0; i < 4; i++) {
                if (drifting && i >= 2) continue;
                double side = i % 2 == 0 ? 1 : -1;
                double rolled = signed + side * omega * half;
                double perTick = Mth.clamp(rolled * spec.degPerBlock, -spec.spinCap, spec.spinCap);
                spin[i] = (float) ((spin[i] - perTick * dt) % 360);
            }
            // steering from the turn: four-wheel steering's tan(front) + tan(rear) = turn x wheelbase / speed, the rear a
            // share of the front turned the other way; full lock only when the turn asks for it
            float target = 0;
            if (steering && speed > MOVING) {
                double tanFront = omega * spec.wheelbase() / (signed * (1 + spec.rearShare));
                target = (float) Mth.clamp(-Math.toDegrees(Math.atan(tanFront)), -spec.steerLock, spec.steerLock);
            }
            float k = (float) (1 - Math.exp(-dt * 12 / 20.0));
            steer += (target - steer) * k;
            float leanTarget = Mth.clamp(-turn * (float) speed * spec.lean, -spec.leanMax, spec.leanMax) * spec.leanSign;
            lean += (leanTarget - lean) * k;
            // the rear swings out: the body points further into the turn than it travels (a right turn: turned right, y -)
            float driftTarget = drifting ? -Mth.clamp(turn * 2, -25, 25) : 0;
            drift += (driftTarget - drift) * k;
            float kr = (float) (1 - Math.exp(-dt * 15 / 20.0));
            for (int i = 0; i < 4; i++) ride[i] += (groundTarget[i] - ride[i]) * kr;
            pitch += (pitchTarget - pitch) * kr;
            roll += (rollTarget - roll) * kr;
            if (Math.abs(steer) < 0.01f) steer = 0;
            if (Math.abs(lean) < 0.01f) lean = 0;
        }

        /**
         * The ground under each wheel against the machine's own footing: the whole machine tilts to the plane through
         * them, and each station rides what is left over, within its travel.
         */
        private void ground(LivingEntity e, Spec spec) {
            float yawRad = e.yBodyRot * Mth.DEG_TO_RAD;
            float sin = Mth.sin(yawRad), cos = Mth.cos(yawRad);
            double[] h = new double[4];
            for (int i = 0; i < 4; i++) {
                // model x (left -) and z (front -) to the world: the machine faces its yaw
                double lx = -spec.contactX[i] / 16, lz = -spec.contactZ[i] / 16;
                double wx = e.getX() + lx * cos - lz * sin, wz = e.getZ() + lx * sin + lz * cos;
                h[i] = height(e.level(), wx, e.getY(), wz);
            }
            double front = (h[0] + h[1]) / 2, rear = (h[2] + h[3]) / 2, left = (h[0] + h[2]) / 2, right = (h[1] + h[3]) / 2;
            double base = spec.wheelbase(), track = spec.halfTrack() * 2;
            pitchTarget = (float) Mth.clamp(Math.toDegrees(Math.atan2(front - rear, base)), -12, 12);
            rollTarget = (float) Mth.clamp(Math.toDegrees(Math.atan2(right - left, track)), -10, 10);
            double mean = (h[0] + h[1] + h[2] + h[3]) / 4;
            for (int i = 0; i < 4; i++) {
                double plane = mean + ((i < 2 ? 1 : -1) * (front - rear) + (i % 2 == 0 ? -1 : 1) * (right - left)) / 2;
                groundTarget[i] = (float) Mth.clamp((h[i] - plane) * 16, -spec.strutTravel, spec.strutTravel);
            }
        }
    }

    /** The top of the ground under (x, z) against height y, within half a block either way (a wheel over a drop hangs). */
    static double height(Level level, double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y + 0.5); by >= Mth.floor(y - 0.5); by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = by + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (top <= y + 0.5 + 1e-6) return Mth.clamp(top - y, -0.5, 0.5);
        }
        return -0.5;
    }

    /** A drive clip at a tick (the main controller's locomotion), or null when the machine stands still. */
    public record Drive(String clip, double tick, int blend) {
    }

    /**
     * The drive's clip from what was measured: a hard stop's brake, a step's bump, reversing, the limp (flat), the boost
     * ({@code rushing}), the start, the drive; null when standing (the caller idles).
     */
    public static Drive drive(Spec spec, State s, double now, boolean flat, boolean rushing, java.util.function.Predicate<String> has) {
        if (s.brakeAt >= 0 && now - s.brakeAt < spec.brakeLength) return new Drive("brake", now - s.brakeAt, 1);
        if (s.bumpAt >= 0 && now - s.bumpAt < spec.bumpLength && has.test("bump")) return new Drive("bump", now - s.bumpAt, 1);
        if (s.speed() <= MOVING) return null;
        if (s.signed < 0) return new Drive("reverse", s.reversePhase, 4);
        if (flat) return new Drive("limp", s.limpPhase, 4);
        if (rushing) return new Drive("rush", s.rushPhase, 3);
        if (s.startAt >= 0 && now - s.startAt < spec.startLength) return new Drive(spec.startClip, now - s.startAt, 2);
        return new Drive("drive", s.drivePhase, 4);
    }

    /** Turning on the spot: the turn clip's direction (+1 right, -1 left), or 0. */
    public static int onTheSpot(State s) {
        if (s.speed() > MOVING || Math.abs(s.turn) <= 1.5f) return 0;
        return s.turn > 0 ? 1 : -1;
    }

    private Wheels() {
    }
}
