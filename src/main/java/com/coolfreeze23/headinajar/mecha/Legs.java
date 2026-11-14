package com.coolfreeze23.headinajar.mecha;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The multi-leg skeleton's shared motion (the Skitter, the Spider Walker): legs of three bones each ({@code hip_*} yaws
 * toward the foot, {@code leg_*} the femur and {@code shin_*} the tibia pitch in that vertical plane, knee up), whose
 * clips are solved for flat ground. On uneven ground code takes over: the body tilts by the slope under the feet and
 * every leg is solved again so each pad stands on the block under it. Also what a client measures of a machine that
 * climbs walls: which face it clings to, since when, how fast it climbs. Plain arithmetic and block lookups.
 * <p>
 * Signs are the model's (Blockbench's): x right +, z front -, a rot x of +a lifts a bone modelled along -Z by a, a rot
 * y of +a turns it left, body rot x + is nose up and rot z + is left side down.
 */
public final class Legs {
    /**
     * One multi-leg rig: its legs' names and hips (model pixels), the body's pivot height (the hips' height), the
     * femur and tibia lengths (hip to knee, knee to the pad's contact), the most the body tilts on a slope (degrees) and
     * how far its rest pads reach out along each axis (px: the foot line it turns about onto a wall).
     */
    public record Spec(String[] names, double[] hipX, double[] hipZ, double bodyY, double femur, double tibia, float tiltMax, double footReach) {
        public int count() {
            return names.length;
        }
    }

    /** Above this many px a planted pad stands off its block, the code re-solves its leg. */
    public static final double REACH = 8;

    private Legs() {
    }

    /**
     * The two-bone leg in its own vertical plane, knee up: {@code r} the horizontal distance hip to foot (outward),
     * {@code dy} the foot's height less the hip's. Returns the femur's and the tibia's angles above horizontal (radians).
     */
    public static double[] solve(double r, double dy, double femur, double tibia) {
        double d = Math.hypot(r, dy);
        double dc = Mth.clamp(d, Math.abs(femur - tibia) + 0.01, femur + tibia - 0.001);
        double phi = Math.atan2(dy, r);
        double alpha = Math.acos(Mth.clamp((femur * femur + dc * dc - tibia * tibia) / (2 * femur * dc), -1, 1));
        double a1 = phi + alpha;
        double kx = femur * Math.cos(a1), ky = femur * Math.sin(a1);
        double ex = d < 1e-6 ? dc : r * dc / d, ey = d < 1e-6 ? 0 : dy * dc / d;
        return new double[]{a1, Math.atan2(ey - ky, ex - kx)};
    }

    /** The top of the ground under (x, z) against height y, px, within {@link #REACH} either way. */
    public static double ground(Level level, double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double reach = REACH / 16;
        for (int by = Mth.floor(y + reach); by >= Mth.floor(y - reach); by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = by + shape.max(Direction.Axis.Y);
            if (top <= y + reach + 1e-6) return Mth.clamp(top - y, -reach, reach) * 16;
        }
        return -REACH;
    }

    /** A model point (px, x right +, z front -) in the world for a machine at (x, z) facing {@code yaw} degrees. */
    public static double[] world(double x, double z, float yaw, double px, double pz) {
        float r = yaw * Mth.DEG_TO_RAD, sin = Mth.sin(r), cos = Mth.cos(r);
        double lx = -px / 16, lz = -pz / 16;
        return new double[]{x + lx * cos - lz * sin, z + lx * sin + lz * cos};
    }

    /**
     * The wall face a climbing machine is against, or null: a solid block within a whisker of its box on that side
     * (above its feet, below its top). When several, the one it faces most.
     */
    public static Direction wall(Entity e) {
        return wallOf(e);
    }

    /**
     * Some pad could stand on something: a block surface within the legs' reach under or round the box (0.7 below its
     * feet, 0.4 out to the sides). A machine dragged on a line keeps its ground stance while this holds.
     */
    public static boolean canStand(Entity e) {
        AABB box = e.getBoundingBox();
        AABB reach = new AABB(box.minX - 0.4, box.minY - 0.7, box.minZ - 0.4, box.maxX + 0.4, box.minY + 0.1, box.maxZ + 0.4);
        return e.level().getBlockCollisions(e, reach).iterator().hasNext();
    }

    private static Direction wallOf(Entity e) {
        AABB box = e.getBoundingBox();
        AABB band = new AABB(box.minX, box.minY + 0.2, box.minZ, box.maxX, Math.max(box.minY + 0.3, box.maxY - 0.1), box.maxZ);
        Direction best = null;
        double score = -2;
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            AABB probe = band.move(dir.getStepX() * 0.08, 0, dir.getStepZ() * 0.08);
            if (!e.level().getBlockCollisions(e, probe).iterator().hasNext()) continue;
            double s = dir.getStepX() * fx + dir.getStepZ() * fz;
            if (s > score) {
                score = s;
                best = dir;
            }
        }
        return best;
    }

    /** What code last did to a multi-leg model: the ground under each pad (px, chased) and the body's tilt (degrees). */
    public static final class Pose {
        public final float[] ground = new float[8];
        public float pitch, roll;
    }

    /** What a client measures of a wall-climbing machine, tick by tick: the face, since when, how fast it climbs. */
    public static final class Climb {
        /** The face it clings to (null: none), the tick it took to it and the tick it last left one. */
        public Direction face;
        public Direction lastFace;
        public long since = -1, leftAt = -1;
        /** Climbing speed, blocks a tick (chased), and the climb clip's phase. */
        public double rate, phase;
        double lastY = Double.NaN;
        int lost;

        /** One tick of a machine that climbs: it takes to a wall rising against it and leaves it on the ground or falling. */
        public void tick(Entity e, long tick, boolean grounded) {
            double dy = Double.isNaN(lastY) ? 0 : e.getY() - lastY;
            lastY = e.getY();
            rate += (dy - rate) * 0.5;
            Direction against = wall(e);
            if (face == null) {
                if (against != null && !grounded && dy > 0.05) {
                    face = against;
                    since = tick;
                    lost = 0;
                }
            } else if (grounded || dy < -0.3) {
                leave(tick);
            } else if (against == null) {
                if (++lost > 2) leave(tick);
            } else {
                lost = 0;
                face = against;
            }
        }

        private void leave(long tick) {
            lastFace = face;
            face = null;
            leftAt = tick;
        }

        /** Advances the climb clip's phase by the climbing speed over its design speed. */
        public void advance(double dt, double design) {
            phase = (phase + dt * Mth.clamp(Math.abs(rate) / design, 0.25, 2.5)) % MechaRig.PHASE_WRAP;
        }

        /** How far the model is turned onto the wall now (0 upright, 1 on the wall), over {@code ticks} each way. */
        public double turned(double now, int ticks) {
            if (face != null) return Mth.clamp((now - since) / ticks, 0, 1);
            if (leftAt >= 0) return Mth.clamp(1 - (now - leftAt) / ticks, 0, 1);
            return 0;
        }

        /** The face the model turns toward (the one it is on, or the one it is leaving). */
        public Direction turningFace() {
            return face != null ? face : lastFace;
        }
    }
}
