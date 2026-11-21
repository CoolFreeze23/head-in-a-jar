package com.coolfreeze23.headinajar.mecha;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Legs that find their own footing: each pad stays where it was put down while the body moves on, and steps when it
 * has been left too far behind. A pad's home is its rest point under the body; it steps when it is further from home
 * than the trigger radius (small standing, large at speed), to the ground found under its home pushed ahead along the
 * motion (and round with the turn). Legs step in two alternating sets: a leg lifts only while its neighbours in front,
 * behind and across are down, just after they land, and a beat after the other legs of its own set lifted, so the sets
 * ripple instead of hopping. A step is an eased arc from where the pad lifted to where its target is now (the target is
 * followed all the way down), higher for a longer step and for a step up. The body's height follows the ground under
 * the pads and it leans with the plane through them. On a wall the body is turned onto it (front up the face, belly to
 * it) and the pads go where the turned body puts them: on the face, stepping up it as the body rises, or still on the
 * ground at its foot; past the top they reach over onto the new ground.
 * <p>
 * The technique is TheCymaera's procedural spider (github.com/TheCymaera/minecraft-spider: trigger and comfort
 * zones, diagonal sets, cooldowns, look-ahead, the scan for ground, the body following its feet), rebuilt here for a
 * GeckoLib model: the solving is the rig's own two-bone knee-up solver ({@link Legs#solve}).
 * <p>
 * It runs on the client only, frame by frame, from the entity's drawn position; nothing here changes how the machine
 * moves. Positions are world blocks; the model's own frame (px, x right +, z front -, y up from the feet) only at the
 * edges. Plain arithmetic and block lookups.
 */
public final class ProceduralLegs {
    /**
     * One rig: its legs (names, hips, bone lengths), each leg's rest point under the body (model px, on the ground), and
     * the gait's order: gait index g (pairs front to back, left then right) to the spec's leg index.
     */
    public record Rig(Legs.Spec spec, double[] restX, double[] restZ, int[] gait, double halfWidth, double runSpeed) {
        public int count() {
            return spec.count();
        }
    }

    /** What the legs ask of the world: the standable surface in a column, and whether a point is inside a block. */
    public interface Terrain {
        /** The highest standable surface at (x, z) between {@code top} and {@code bottom} (blocks), or NaN. */
        double scan(double x, double z, double top, double bottom);

        boolean solid(double x, double y, double z);

        /** The blocks of a level, by their collision shapes. */
        static Terrain of(Level level) {
            return new Terrain() {
                @Override
                public double scan(double x, double z, double top, double bottom) {
                    return ProceduralLegs.scan(level, x, z, top, bottom);
                }

                @Override
                public boolean solid(double x, double y, double z) {
                    return ProceduralLegs.solid(level, x, y, z);
                }
            };
        }
    }

    /** Trigger radius standing and at speed (px): how far a pad may be left behind before it steps. */
    static final double R_STILL = 2.5, R_MOVE = 10.0;
    /** How far ahead of home a step lands, as a share of the trigger radius. */
    static final double LOOKAHEAD = 0.8;
    /** A pad this far (px) past its trigger radius steps whatever its neighbours are doing. */
    static final double EMERGENCY = 9.0;
    /** The share of a leg's full length it may reach to a target (it is never drawn quite straight). */
    public static final double REACH = 0.94;
    /** The share of a leg's length a step may be aimed at (less than it can reach, so a stepping leg keeps its bend). */
    static final double TARGET_REACH = 0.86;
    /** A ground target further than this from home (blocks, sideways) is out of reach: the leg dangles. */
    static final double COMFORT = 1.15;
    /**
     * Cooldowns (ticks), standing and flat out: after a neighbour lands; after a leg of the same set lifted (slow, the
     * set ripples front to back; at a run it lifts together).
     */
    static final double CROSS_SLOW = 0.35, CROSS_FAST = 0.05, SAME_SLOW = 0.6, SAME_FAST = 0.0;
    /** Swing time (ticks): its least standing and at speed, and its most; the pad's speed (px a tick) and its share of the body's. */
    static final double DUR_SLOW = 2.6, DUR_FAST = 1.3, DUR_MAX = 3.5, FOOT_SPEED = 6.0, FOOT_SPEED_K = 1.6;
    /** Lift (px): base, per px of step, least, most; and extra per px of step up. */
    static final double LIFT_BASE = 1.6, LIFT_K = 0.2, LIFT_MIN = 2.2, LIFT_MAX = 6.5, LIFT_UP = 0.35;
    /** The body: most tilt (degrees), its nose-down lean at speed, its dip per lifted pad (px). */
    static final double TILT = 10, RUN_LEAN = 2.0, SAG = 0.45;
    /** The key's turn per pad set down while walking (degrees; a quarter turn a stride of eight). */
    static final double WIND = 90.0 / 8;

    private static final int GROUND = 0, WALL = 1;

    /** One leg. */
    static final class Leg {
        final double[] foot = new double[3], start = new double[3], target = new double[3], aim = new double[3], home = new double[3];
        /** The surface's normal at the target (up on the ground, out of the wall on a wall). */
        final double[] n = {0, 1, 0};
        boolean swinging, targetGrounded = true;
        int mode = GROUND;
        double t, dur, lift, sinceBegin = 99, sinceStop = 99, arc;
        /** How far past its trigger radius it is, as a share of it (0 if it does not want to step). */
        double urgency;
    }

    /** What one walker's legs are doing (kept by the client per machine). */
    public static final class State {
        Leg[] legs;
        boolean init;
        double lastNow = Double.NaN, lastX, lastY, lastZ;
        float lastYaw;
        double vx, vy, vz, yawRate;
        /** The body's world height (blocks) and lean (degrees), chased. */
        double bodyY, pitch, roll;
        /** How far the legs are this code's (1) rather than the clip's (0). */
        public double weight;
        /** The turn onto a wall: eased share (0 to 1) and the wall's direction in the model's frame. */
        double ts, tdx, tdz;
        /** The key's wound angle (degrees), and as drawn. */
        double wind;
        public double windShown;
        /** Each leg's pad in the model's frame (px), for the solver. */
        public final double[][] model = new double[8][3];
        /** The body's height offset (px) and lean (degrees) to add to the clip's. */
        public double offY, offPitch, offRoll;
        /** Where the clip puts each pad this frame (px), filled by the caller. */
        public final double[][] clip = new double[8][3];

        /** Each pad's world place, for the footfalls. */
        public double[] footWorld(int i) {
            return legs[i].foot;
        }

        /** Whether leg i has somewhere to stand (else it dangles). */
        public boolean grounded(int i) {
            return legs[i].targetGrounded;
        }

        /** Whether leg i is in the air, stepping. */
        public boolean swinging(int i) {
            return legs[i].swinging;
        }

        /** The surface each pad is on: its normal (up on the ground, out of a wall's face). */
        public double[] normal(int i) {
            return legs[i].n;
        }
    }

    private ProceduralLegs() {
    }

    /**
     * Brings the legs up to this frame. {@code clipFeet}: where the clip puts each pad (model px), the legs' start when
     * the code takes them over and what they follow while it does not. {@code wall}: the face it climbs, or null; {@code turnFace} and {@code turned}: the face the model is turned toward
     * and how far (0 to 1, before easing).
     * {@code striding}: this code has the legs (standing, walking, turning, climbing), else the clip has them.
     * Returns the legs set down this frame, as bits.
     */
    public static int update(Rig rig, State st, Level level, double x, double y, double z, float yaw, double now,
                             Direction wall, Direction turnFace, double turned, boolean striding, boolean limp, double[][] clipFeet) {
        return update(rig, st, Terrain.of(level), x, y, z, yaw, now, wall, turnFace, turned, striding, limp, clipFeet);
    }

    /** As the one on a {@link Level}, against any terrain. */
    public static int update(Rig rig, State st, Terrain level, double x, double y, double z, float yaw, double now,
                             Direction wall, Direction turnFace, double turned, boolean striding, boolean limp, double[][] clipFeet) {
        int n = rig.count();
        double dt = Double.isNaN(st.lastNow) ? 0 : now - st.lastNow;
        boolean jump = !st.init || dt < 0 || dt > 10 || sq(x - st.lastX) + sq(z - st.lastZ) > 16;
        if (jump) {
            reset(rig, st, x, y, z, yaw, clipFeet);
            dt = 0;
        }
        st.lastNow = now;
        double cos = Mth.cos(yaw * Mth.DEG_TO_RAD), sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        // the model's turn onto the wall it climbs (or is leaving), eased as the renderer eases it
        st.ts = 0;
        if (turnFace != null && turned > 0) {
            double t = Mth.clamp(turned, 0, 1);
            st.ts = t * t * (3 - 2 * t);
            st.tdx = -(turnFace.getStepX() * cos + turnFace.getStepZ() * sin);
            st.tdz = -(-turnFace.getStepX() * sin + turnFace.getStepZ() * cos);
        }
        if (dt > 0) {
            double k = 1 - Math.exp(-dt / 2.0);
            st.vx += ((x - st.lastX) / dt - st.vx) * k;
            st.vy += ((y - st.lastY) / dt - st.vy) * k;
            st.vz += ((z - st.lastZ) / dt - st.vz) * k;
            st.yawRate += (Mth.wrapDegrees(yaw - st.lastYaw) / dt - st.yawRate) * k;
        }
        st.lastX = x;
        st.lastY = y;
        st.lastZ = z;
        st.lastYaw = yaw;
        st.weight += ((striding ? 1 : 0) - st.weight) * (1 - Math.exp(-dt / 2.0));
        if (st.weight < 0.01) st.weight = 0;
        if (st.weight > 0.99) st.weight = 1;
        if (!striding) {
            // the clip has the legs: once its share is all, the pads follow it, so the code takes over from where they are
            if (st.weight == 0) {
                for (int i = 0; i < n; i++) {
                    Leg l = st.legs[i];
                    toWorld(clipFeet[i], x, y, z, cos, sin, l.foot);
                    l.swinging = false;
                    l.sinceBegin = l.sinceStop = 99;
                }
                st.bodyY = y;
                st.offY = st.offPitch = st.offRoll = 0;
            }
            writeModel(rig, st, x, y, z, cos, sin);
            return 0;
        }
        double speed = Math.sqrt(st.vx * st.vx + st.vz * st.vz + st.vy * st.vy) * 16;
        double sf = Mth.clamp(Math.max(speed / (rig.runSpeed() * 16), Math.abs(st.yawRate) / 8), 0, 1);
        double r = Mth.lerp(Math.sqrt(sf), R_STILL, R_MOVE);
        // the wall's direction in the model's frame, and how far its face is from the middle
        double dx = 0, dz = 0;
        if (wall != null) {
            double lx = wall.getStepX() * cos + wall.getStepZ() * sin, lz = -wall.getStepX() * sin + wall.getStepZ() * cos;
            dx = -lx;
            dz = -lz;
        }
        for (int i = 0; i < n; i++) target(rig, st, st.legs[i], i, level, x, y, z, cos, sin, wall, dx, dz, r, sf);
        for (int i = 0; i < n; i++) {
            Leg l = st.legs[i];
            l.sinceBegin += dt;
            l.sinceStop += dt;
            l.urgency = l.swinging || !wants(l, r) ? 0 : tangential(l.foot, l.home, l.n) * 16 / r;
        }
        // step decisions, the first set first so the sets keep their turns
        int mask = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int g = 0; g < n; g++) {
                if (diagonal1(g) != (pass == 0)) continue;
                int i = rig.gait()[g];
                Leg l = st.legs[i];
                if (l.swinging) {
                    if (swing(l, dt)) {
                        mask |= 1 << i;
                        if (sf > 0.05) st.wind -= WIND;
                    }
                } else if (canStep(rig, st, g, r, sf)) {
                    begin(l, st, sf, speed, limp && i == 0);
                }
            }
        }
        body(rig, st, x, y, z, cos, sin, wall, dx, dz, dt, sf);
        double wk = 1 - Math.exp(-dt / 1.2);
        st.windShown += (st.wind - st.windShown) * wk;
        if (Math.abs(st.wind) > 3600) {
            double turns = Math.floor(st.wind / 360) * 360;
            st.wind -= turns;
            st.windShown -= turns;
        }
        writeModel(rig, st, x, y, z, cos, sin);
        return mask;
    }

    private static void reset(Rig rig, State st, double x, double y, double z, float yaw, double[][] clipFeet) {
        int n = rig.count();
        if (st.legs == null || st.legs.length != n) {
            st.legs = new Leg[n];
            for (int i = 0; i < n; i++) st.legs[i] = new Leg();
        }
        double cos = Mth.cos(yaw * Mth.DEG_TO_RAD), sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        for (int i = 0; i < n; i++) {
            Leg l = st.legs[i];
            toWorld(clipFeet[i], x, y, z, cos, sin, l.foot);
            l.swinging = false;
            l.sinceBegin = l.sinceStop = 99;
        }
        st.lastX = x;
        st.lastY = y;
        st.lastZ = z;
        st.lastYaw = yaw;
        st.vx = st.vy = st.vz = st.yawRate = 0;
        st.bodyY = y;
        st.init = true;
    }

    /** Leg i's home and the target it would step to now. */
    private static void target(Rig rig, State st, Leg l, int i, Terrain level, double x, double y, double z, double cos, double sin,
                               Direction wall, double dx, double dz, double r, double sf) {
        double rx = rig.restX()[i], rz = rig.restZ()[i];
        int was = l.mode;
        boolean onWall = false;
        // on a wall every pad goes where the turned body puts it: on the face, or at its foot on the ground
        if (wall != null) onWall = wallTarget(rig, st, l, level, x, y, z, cos, sin, wall, dx, dz, rx, rz, r);
        if (!onWall) groundTarget(rig, st, l, i, level, x, y, z, cos, sin, rx, rz, r, sf, wall != null);
        l.mode = onWall ? WALL : GROUND;
        if (l.mode != was && !l.swinging) l.sinceStop = 99;
    }

    private static void groundTarget(Rig rig, State st, Leg l, int i, Terrain level, double x, double y, double z, double cos, double sin,
                                     double rx, double rz, double r, double sf, boolean climbing) {
        double[] w = new double[3];
        toWorld(rx, 0, rz, x, y, z, cos, sin, w);
        copy(w, l.home);
        // pushed ahead along the motion, and round with the turn
        double hx = w[0], hz = w[2];
        double v = Math.hypot(st.vx, st.vz);
        if (v > 0.004) {
            double ahead = r * LOOKAHEAD / 16 * Mth.clamp(v * 16 / 2.0, 0, 1);
            hx += st.vx / v * ahead;
            hz += st.vz / v * ahead;
        }
        if (Math.abs(st.yawRate) > 0.05) {
            double a = -st.yawRate * 1.5 * Mth.DEG_TO_RAD, c = Math.cos(a), s = Math.sin(a);
            double ox = hx - x, oz = hz - z;
            hx = x + ox * c - oz * s;
            hz = z + ox * s + oz * c;
        }
        double top = y + 1.1, bottom = y - (climbing ? 0.9 : 1.05);
        double[] hip = new double[3];
        toWorld(rig.spec().hipX()[i], rig.spec().bodyY(), rig.spec().hipZ()[i], x, st.bodyY, z, cos, sin, hip);
        double reach = (rig.spec().femur() + rig.spec().tibia()) * TARGET_REACH / 16;
        double best = level.scan(hx, hz, top, bottom), bx = hx, bz = hz;
        if (!Double.isNaN(best) && sq(hx - hip[0]) + sq(best - hip[1]) + sq(hz - hip[2]) > reach * reach) best = Double.NaN;
        double straight = best;
        if (Double.isNaN(best) || best < y - 0.3 || best > y + 0.6) {
            // near an edge: the block edges round the point (Cymaera's alternatives), the nearest to where it ought to be
            double pref = y, prefD = Double.MAX_VALUE;
            double fx = -sin, fz = cos;
            if (level.solid(hx + fx * 0.6, y + 0.5, hz + fz * 0.6)) pref += 0.5;
            if (!Double.isNaN(best)) prefD = sq(best - pref);
            double m = 2 / 16.0;
            double[] xs = {Math.floor(hx) - m, hx, Math.ceil(hx) + m}, zs = {Math.floor(hz) - m, hz, Math.ceil(hz) + m};
            for (double cx : xs) {
                for (double cz : zs) {
                    if (cx == hx && cz == hz) continue;
                    double h = level.scan(cx, cz, top, bottom);
                    if (Double.isNaN(h) || sq(cx - hip[0]) + sq(h - hip[1]) + sq(cz - hip[2]) > reach * reach) continue;
                    double d = sq(cx - hx) + sq(cz - hz) + sq(h - pref);
                    if (d < prefD) {
                        prefD = d;
                        best = h;
                        bx = cx;
                        bz = cz;
                    }
                }
            }
        }
        if (!Double.isNaN(best) && sq(bx - w[0]) + sq(bz - w[2]) >= COMFORT * COMFORT && !Double.isNaN(straight)) {
            // the edge it preferred is out of comfort: the ground straight down will do
            best = straight;
            bx = hx;
            bz = hz;
        }
        if (!Double.isNaN(best) && sq(bx - w[0]) + sq(bz - w[2]) < COMFORT * COMFORT) {
            l.target[0] = bx;
            l.target[1] = best;
            l.target[2] = bz;
            l.targetGrounded = true;
        } else {
            // nothing to stand on: it dangles a little under its rest point
            l.target[0] = hx;
            l.target[1] = y - 0.2;
            l.target[2] = hz;
            l.targetGrounded = false;
        }
        l.n[0] = 0;
        l.n[1] = 1;
        l.n[2] = 0;
    }

    /**
     * A pad on the wall's face: its rest point as the body is turned onto the wall (front up the face, back below),
     * put on the face's plane, pushed up by the climb. Past the top, onto the new ground beyond the edge. False if
     * neither is there, or if the point is still down at the wall's foot (the ground has it).
     */
    private static boolean wallTarget(Rig rig, State st, Leg l, Terrain level, double x, double y, double z, double cos, double sin,
                                      Direction wall, double dx, double dz, double rx, double rz, double r) {
        double[] m = {rx, 0, rz};
        turn(rig, st, m, false);
        double along = m[0] * dx + m[2] * dz, plane = rig.halfWidth() * 16 - 0.5;
        // not yet turned far enough to bring it to the face: it is still a point on the ground
        if (plane - along > 6) return false;
        m[0] += dx * (plane - along);
        m[2] += dz * (plane - along);
        double[] w = new double[3];
        toWorld(m, x, y, z, cos, sin, w);
        double ay = w[1];
        if (Math.abs(st.vy) > 0.02) ay += Math.signum(st.vy) * r * LOOKAHEAD / 16;
        double sx = wall.getStepX(), sz = wall.getStepZ();
        // still down in the ground at the wall's foot (or under the box's feet, before the climb has lifted it): the ground has it
        if (w[1] < y + 0.15 && level.solid(w[0] - sx * 0.06, ay, w[2] - sz * 0.06)) return false;
        if (w[1] < y - 0.05 && !Double.isNaN(level.scan(w[0] - sx * 0.3, w[2] - sz * 0.3, y + 0.1, ay))) return false;
        copy(w, l.home);
        if (level.solid(w[0] + sx * 0.12, ay, w[2] + sz * 0.12)) {
            l.target[0] = w[0];
            l.target[1] = ay;
            l.target[2] = w[2];
            l.targetGrounded = true;
            l.n[0] = -sx;
            l.n[1] = 0;
            l.n[2] = -sz;
            return true;
        }
        // over the top: the ground beyond the edge
        double ox = w[0] + sx * 0.35, oz = w[2] + sz * 0.35;
        double top = level.scan(ox, oz, ay + 0.45, w[1] - 1.2);
        if (!Double.isNaN(top)) {
            l.target[0] = ox;
            l.target[1] = top;
            l.target[2] = oz;
            l.targetGrounded = true;
            l.n[0] = 0;
            l.n[1] = 1;
            l.n[2] = 0;
            return true;
        }
        l.targetGrounded = false;
        return false;
    }

    /** Pairs front to back, left then right: the first set is the left of the 1st and 3rd pairs, the right of the 2nd and 4th. */
    static boolean diagonal1(int g) {
        return (g / 2) % 2 == 0 ? g % 2 == 0 : g % 2 == 1;
    }

    private static Leg gaitLeg(Rig rig, State st, int g) {
        return g < 0 || g >= rig.count() ? null : st.legs[rig.gait()[g]];
    }

    /** Whether the leg at gait index g may lift now (Cymaera's walking rule). */
    /** Whether a pad wants to step: left further than the radius from home, or off the surface its target is on. */
    private static boolean wants(Leg l, double r) {
        if (tangential(l.foot, l.home, l.n) * 16 > r) return true;
        return Math.abs(dot(sub(l.foot, l.target), l.n)) * 16 >= 3;
    }

    static boolean canStep(Rig rig, State st, int g, double r, double sf) {
        double cross = Mth.lerp(sf, CROSS_SLOW, CROSS_FAST), same = Mth.lerp(sf, SAME_SLOW, SAME_FAST);
        Leg l = st.legs[rig.gait()[g]];
        double away = tangential(l.foot, l.home, l.n) * 16;
        boolean there = sq(l.foot[0] - l.target[0]) + sq(l.foot[1] - l.target[1]) + sq(l.foot[2] - l.target[2]) < sq(0.5 / 16);
        if (!wants(l, r) || there) return false;
        if (!l.targetGrounded) return true;
        Leg front = gaitLeg(rig, st, g - 2), back = gaitLeg(rig, st, g + 2), across = gaitLeg(rig, st, g % 2 == 0 ? g + 1 : g - 1);
        for (Leg a : new Leg[]{front, back, across}) {
            if (a == null) continue;
            if (a.swinging && a.targetGrounded) return false;
        }
        // left far behind: it goes as soon as its neighbours are down, cooldowns or not
        if (away > r + EMERGENCY) return true;
        for (Leg a : new Leg[]{front, back, across}) {
            if (a == null) continue;
            if (a.targetGrounded && a.sinceStop < cross) return false;
            // fairness: a neighbour left further behind goes first (else one set can starve a leg of the other)
            if (a.targetGrounded && a.urgency > l.urgency + 0.15) return false;
        }
        boolean left = g % 2 == 0;
        Leg diagFront = gaitLeg(rig, st, left ? g - 1 : g - 3), diagBack = gaitLeg(rig, st, left ? g + 3 : g + 1);
        for (Leg a : new Leg[]{diagFront, diagBack}) {
            if (a != null && a.targetGrounded && a.sinceBegin < same) return false;
        }
        return true;
    }

    private static void begin(Leg l, State st, double sf, double speed, boolean dragged) {
        l.swinging = true;
        l.t = 0;
        l.sinceBegin = 0;
        copy(l.foot, l.start);
        copy(l.target, l.aim);
        double dist = Math.sqrt(sq(l.target[0] - l.foot[0]) + sq(l.target[1] - l.foot[1]) + sq(l.target[2] - l.foot[2])) * 16;
        double footSpeed = FOOT_SPEED + FOOT_SPEED_K * speed;
        l.dur = Mth.clamp(dist / footSpeed, Mth.lerp(sf, DUR_SLOW, DUR_FAST), DUR_MAX);
        double up = Math.max(0, dot(sub(l.target, l.foot), l.n) * 16);
        l.lift = Mth.clamp(LIFT_BASE + LIFT_K * dist, LIFT_MIN, LIFT_MAX) + LIFT_UP * up;
        if (dragged) {
            // the jar's charge is nearly out: the front-left leg is dragged, not lifted
            l.lift = 0.3;
            l.dur *= 1.8;
        }
    }

    /** One frame of a step; true when it lands. */
    private static boolean swing(Leg l, double dt) {
        l.t += dt;
        double p = Math.min(1, l.t / l.dur);
        // the target is followed along the surface at once; a jump across it (an edge, a step) is eased in
        double e = dot(sub(l.aim, l.target), l.n) * Math.exp(-dt / 0.7) * (1 - smooth(0.65, 1, p));
        for (int k = 0; k < 3; k++) l.aim[k] = l.target[k] + l.n[k] * e;
        double[] d = sub(l.aim, l.start);
        double dn = dot(d, l.n);
        double et = easeInOutSine(p);
        double en = dn > 0 ? 1 - Math.pow(1 - p, 2.5) : easeInOutSine(p);
        l.arc = 4 * p * (1 - p);
        for (int k = 0; k < 3; k++) {
            double normal = l.n[k] * dn;
            l.foot[k] = l.start[k] + (d[k] - normal) * et + normal * en + l.n[k] * l.arc * l.lift / 16;
        }
        if (p < 1) return false;
        copy(l.target, l.foot);
        l.swinging = false;
        l.arc = 0;
        l.sinceStop = 0;
        return true;
    }

    /** The body: its height over the pads' ground, its lean with the plane through them (or onto the wall). */
    private static void body(Rig rig, State st, double x, double y, double z, double cos, double sin, Direction wall, double dx, double dz,
                             double dt, double sf) {
        int n = rig.count();
        double sx = 0, sz = 0, sh = 0, sxx = 0, szz = 0, sxz = 0, sxh = 0, szh = 0, lifted = 0, wallL = 0, wallR = 0;
        int m = 0, wl = 0, wr = 0;
        double[] p = new double[3];
        for (int i = 0; i < n; i++) {
            Leg l = st.legs[i];
            lifted += l.arc;
            if (!l.targetGrounded) continue;
            double[] at = l.swinging ? l.aim : l.foot;
            toModel(at, x, y, z, cos, sin, p);
            if (l.mode == WALL) {
                if (p[0] < 0) {
                    wallL += p[1];
                    wl++;
                } else {
                    wallR += p[1];
                    wr++;
                }
                continue;
            }
            if (l.n[1] < 0.5) continue;
            double fx = p[0], fz = p[2], h = p[1];
            sx += fx;
            sz += fz;
            sh += h;
            sxx += fx * fx;
            szz += fz * fz;
            sxz += fx * fz;
            sxh += fx * h;
            szh += fz * h;
            m++;
        }
        double heightT = 0, pitchT = 0, rollT = 0;
        if (wall != null && wl > 0 && wr > 0) {
            // on the wall it sways a little toward the side whose pads are higher
            rollT = Mth.clamp(Math.toDegrees(Math.atan((wallR / wr - wallL / wl) / 18)) * 0.3, -3, 3);
        }
        if (m >= 3) {
            double mx = sx / m, mz = sz / m, mh = sh / m;
            if (wall == null) heightT = mh;
            double cxx = sxx / m - mx * mx, czz = szz / m - mz * mz, cxz = sxz / m - mx * mz, cxh = sxh / m - mx * mh, czh = szh / m - mz * mh;
            double det = cxx * czz - cxz * cxz;
            if (wall == null && Math.abs(det) > 1e-6) {
                double b = (cxh * czz - czh * cxz) / det, c = (czh * cxx - cxh * cxz) / det;
                // nose up when the ground rises ahead (z front -), left side down when it rises to the right
                pitchT = Mth.clamp(Math.toDegrees(Math.atan(-c)), -TILT, TILT);
                rollT = Mth.clamp(Math.toDegrees(Math.atan(b)), -TILT, TILT);
            }
        }
        // at speed it leans into the run a little; every lifted pad lets it down a fraction
        double fwd = (st.vx * -sin + st.vz * cos) / rig.runSpeed();
        if (wall == null) pitchT -= RUN_LEAN * Mth.clamp(fwd, 0, 1);
        heightT = Mth.clamp(heightT, -12, 12) - SAG * lifted;
        double kh = 1 - Math.exp(-dt / 1.1), ka = 1 - Math.exp(-dt / 2.4);
        // it rises and falls with the box as it goes (up a stair, a wall) and settles onto the pads' ground from there
        st.bodyY += st.vy * dt * (wall != null ? 1 : 0.8);
        st.bodyY += (y + heightT / 16 - st.bodyY) * kh;
        st.bodyY = Mth.clamp(st.bodyY, y - 13 / 16.0, y + 13 / 16.0);
        st.pitch += (pitchT - st.pitch) * ka;
        st.roll += (rollT - st.roll) * ka;
        st.offY = (st.bodyY - y) * 16;
        st.offPitch = st.pitch;
        st.offRoll = st.roll;
    }

    private static void writeModel(Rig rig, State st, double x, double y, double z, double cos, double sin) {
        for (int i = 0; i < rig.count(); i++) {
            toModel(st.legs[i].foot, x, y, z, cos, sin, st.model[i]);
            turn(rig, st, st.model[i], true);
        }
    }

    /**
     * The turn onto a wall (the whole model): about the body's middle (the hips' height), front up the face and belly to
     * it, then moved toward the wall until the model's ground lies on the face. {@code inverse}: from the drawn place
     * back into the model's own frame. In place, model px.
     */
    static void turn(Rig rig, State st, double[] m, boolean inverse) {
        if (st.ts <= 0) return;
        double h = rig.spec().bodyY(), shift = (rig.halfWidth() * 16 - h) * st.ts;
        double kx = -st.tdz, kz = st.tdx;
        double a = (inverse ? -1 : 1) * st.ts * Math.PI / 2, c = Math.cos(a), sn = Math.sin(a);
        double vx = m[0], vy = m[1] - h, vz = m[2];
        if (inverse) {
            vx -= st.tdx * shift;
            vz -= st.tdz * shift;
        }
        // Rodrigues, about the horizontal axis k (k.y = 0)
        double kv = kx * vx + kz * vz;
        double cx = -kz * vy, cy = kz * vx - kx * vz, cz = kx * vy;
        double rx = vx * c + cx * sn + kx * kv * (1 - c), ry = vy * c + cy * sn, rz = vz * c + cz * sn + kz * kv * (1 - c);
        m[0] = rx;
        m[1] = ry + h;
        m[2] = rz;
        if (!inverse) {
            m[0] += st.tdx * shift;
            m[2] += st.tdz * shift;
        }
    }

    /** How far the model is turned onto a wall (0 to 1, eased), for the renderer. */
    public static double turned(State st) {
        return st.ts;
    }

    /** The wall the model is turned toward, in the model's frame (x, z), for the renderer. */
    public static double[] turnDirection(State st) {
        return new double[]{st.tdx, st.tdz};
    }

    // ---- the world ------------------------------------------------------------------------------------------------

    /** The highest standable surface at (x, z) between {@code top} and {@code bottom} (blocks), or NaN. */
    static double scan(Level level, double x, double z, double top, double bottom) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double fx = x - bx, fz = z - bz;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = Mth.floor(top); by >= Mth.floor(bottom); by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double best = Double.NaN;
            for (AABB b : shape.toAabbs()) {
                if (fx >= b.minX - 1e-6 && fx <= b.maxX + 1e-6 && fz >= b.minZ - 1e-6 && fz <= b.maxZ + 1e-6) {
                    double t = by + b.maxY;
                    if (t <= top + 1e-6 && (Double.isNaN(best) || t > best)) best = t;
                }
            }
            if (Double.isNaN(best) || best < bottom) continue;
            if (!solid(level, x, best + 0.03, z)) return best;
        }
        return Double.NaN;
    }

    /** The highest standable surface at (x, z) between {@code top} and {@code bottom} (blocks), or NaN. */
    public static double groundAt(Level level, double x, double z, double top, double bottom) {
        return scan(level, x, z, top, bottom);
    }

    /** Whether the point is inside a block's collision. */
    static boolean solid(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double fx = x - pos.getX(), fy = y - pos.getY(), fz = z - pos.getZ();
        for (AABB b : shape.toAabbs()) {
            if (fx >= b.minX && fx <= b.maxX && fy >= b.minY && fy <= b.maxY && fz >= b.minZ && fz <= b.maxZ) return true;
        }
        return false;
    }

    // ---- frames ---------------------------------------------------------------------------------------------------

    /** A model point (px) in the world, for a machine at (x, y, z) facing yaw (as cos, sin). */
    static void toWorld(double px, double py, double pz, double x, double y, double z, double cos, double sin, double[] out) {
        double lx = -px / 16, lz = -pz / 16;
        out[0] = x + lx * cos - lz * sin;
        out[1] = y + py / 16;
        out[2] = z + lx * sin + lz * cos;
    }

    static void toWorld(double[] p, double x, double y, double z, double cos, double sin, double[] out) {
        toWorld(p[0], p[1], p[2], x, y, z, cos, sin, out);
    }

    /** A world point in the model's frame (px). */
    static void toModel(double[] w, double x, double y, double z, double cos, double sin, double[] out) {
        double wx = w[0] - x, wz = w[2] - z;
        double lx = wx * cos + wz * sin, lz = -wx * sin + wz * cos;
        out[0] = -lx * 16;
        out[1] = (w[1] - y) * 16;
        out[2] = -lz * 16;
    }

    // ---- arithmetic -----------------------------------------------------------------------------------------------

    private static double tangential(double[] a, double[] b, double[] n) {
        double[] d = sub(a, b);
        double dn = dot(d, n);
        return Math.sqrt(Math.max(0, dot(d, d) - dn * dn));
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void copy(double[] from, double[] to) {
        System.arraycopy(from, 0, to, 0, 3);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double easeInOutSine(double p) {
        return -(Math.cos(Math.PI * p) - 1) / 2;
    }

    private static double smooth(double a, double b, double p) {
        double t = Mth.clamp((p - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
