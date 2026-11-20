package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.mind.MobMotor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A chicken sawn without a jar runs in tight flapping circles round where it was cut, and never further than
 * {@link #RADIUS} blocks from that spot: the mod's own small run, driven through {@link MobMotor} from the host's
 * cancelled AI step. It runs while nobody rides it (from {@link #SETTLE} ticks after the last ride), it is not on a
 * Treadmill, not on a lead or in a vehicle, and the server allows it ({@code bodies.headlessChicken}). A Personnel File
 * does not stop it: every host has one.
 * <p>
 * When its path is done and it is on the ground (at most every {@link #REPLAN} ticks), it picks the next point on a small circle ({@link #CIRCLE} blocks)
 * round a centre that drifts within {@link #DRIFT} of the spot, re-picked every few points. A point must be dry, solid
 * ground; its path must reach it, stay inside the circle, never cross water or lava and never drop more than a block.
 * It hops every 10 to 16 ticks (the vanilla flap plays in the air) and stops for a breath every 6 seconds. Shoved
 * outside, it heads back to the spot first. Server side; nothing here is saved but the spot ({@link HostData#runAnchor}).
 */
public final class HeadlessChicken {
    /** It never goes further than this from where it was cut. */
    public static final double RADIUS = 12.0;
    /** The circle it runs round its centre, and how far the centre wanders from the spot. */
    public static final double CIRCLE = 2.5, DRIFT = 8.0;
    /** Points run before the centre moves. */
    static final int POINTS_PER_CENTRE = 6;
    /** Fewest ticks between two paths (more after a failed look), and the pace (times its walking speed). */
    static final int REPLAN = 10, REPLAN_FAILED = 20;
    static final double SPEED = 1.4;
    /** A hop every 10 to 16 ticks; a pause of {@link #PAUSE} ticks every {@link #PAUSE_EVERY}. */
    static final int HOP_MIN = 10, HOP_MAX = 16, PAUSE = 20, PAUSE_EVERY = 120;
    /** Ticks after a ride ends before it starts running. */
    public static final int SETTLE = 40;
    /** The hidden advancement: ticks a player must watch it run, from within this many blocks, looking this close. */
    static final int WATCH_TICKS = 200;
    static final double WATCH_RANGE = 16, WATCH_DEGREES = 25;
    /** Nodes of its path stay this far inside the circle (a node is a block: its far edge counts). */
    static final double MARGIN = 1.0;
    /** The legs it tries on its way back in (blocks), and the turns aside from straight home (radians), in order. */
    static final double[] BACK_LEGS = {8, 6, 4, 2};
    static final double[] BACK_TURNS = {0, 0.5, -0.5, 1.0, -1.0};
    private static final Map<PathType, Float> AVOID = Map.of(PathType.WATER, -1f, PathType.WATER_BORDER, -1f,
            PathType.LAVA, -1f, PathType.DANGER_FIRE, -1f, PathType.DAMAGE_FIRE, -1f);

    /** What a running chicken remembers between ticks (not saved: a reload starts a fresh lap round its spot). */
    private static final class Run {
        Vec3 centre;
        int points;
        double angle;
        long nextHop, startedAt, nextPlan;
        /** Whether the path it walks is a leg back toward the spot (not a lap of its circle). */
        boolean goingBack;
        final Map<UUID, Integer> watched = new HashMap<>();
    }

    private static final Map<Mob, Run> RUNS = new WeakHashMap<>();

    private HeadlessChicken() {
    }

    /** Whether this host runs this tick (asked from its cancelled AI step). */
    public static boolean runs(Mob mob) {
        if (!(mob instanceof Chicken) || mob.level().isClientSide || !mob.isAlive() || !Hosts.isHost(mob)) return false;
        HostData data = Hosts.data(mob);
        if (data.shell || mob.isVehicle() || mob.isPassenger() || mob.isLeashed()) return false;
        if (mob.level().getGameTime() - data.lastRidden < SETTLE) return false;
        // 0.7.1: a host in a Secondment Collar works instead of running
        if (com.coolfreeze23.headinajar.block.Treadmills.isWalker(mob)) return false;
        return Config.get(Config.HEADLESS_CHICKEN, true);
    }

    /** Asked when {@link #runs} says no: a chicken that was running stops where it is. */
    public static void rest(Mob mob) {
        if (RUNS.remove(mob) != null) MobMotor.release(mob);
    }

    /** Whether it is running now. */
    public static boolean running(Mob mob) {
        return RUNS.containsKey(mob);
    }

    /** The spot it runs round, or null if it has none yet. */
    public static BlockPos anchor(Mob mob) {
        long a = Hosts.data(mob).runAnchor;
        return a == HostData.NO_ANCHOR ? null : BlockPos.of(a);
    }

    /** Sets the spot it runs round (the saw: where it was cut). */
    public static void anchorAt(Mob mob, BlockPos at) {
        Hosts.data(mob).runAnchor = at.asLong();
    }

    /** One tick of the run. */
    public static void tick(Mob mob) {
        ServerLevel level = (ServerLevel) mob.level();
        HostData data = Hosts.data(mob);
        long time = level.getGameTime();
        // an old chicken host, or a printed one, runs round where it stands
        if (data.runAnchor == HostData.NO_ANCHOR) data.runAnchor = mob.blockPosition().asLong();
        BlockPos anchor = BlockPos.of(data.runAnchor);
        Run run = RUNS.get(mob);
        if (run == null) {
            run = new Run();
            run.startedAt = time;
            run.angle = mob.getRandom().nextDouble() * Math.PI * 2;
            run.nextHop = time + HOP_MIN;
            RUNS.put(mob, run);
            MobMotor.avoid(mob, AVOID);
        }
        // shoved or carried out of its circle in the middle of a lap: the lap's path would walk it straight on, through a
        // pond if one is in the way; it is dropped and the way back planned instead
        if (!run.goingBack && !MobMotor.idle(mob) && flat(mob.position(), Vec3.atBottomCenterOf(anchor)) > RADIUS - MARGIN) MobMotor.halt(mob);
        MobMotor.tick(mob);
        long age = time - run.startedAt;
        if (age % PAUSE_EVERY >= PAUSE_EVERY - PAUSE) {
            // a breath: it stands still for a second
            if (!MobMotor.idle(mob)) MobMotor.halt(mob);
        } else {
            if (time >= run.nextHop && mob.onGround()) {
                mob.getJumpControl().jump();
                run.nextHop = time + HOP_MIN + mob.getRandom().nextInt(HOP_MAX - HOP_MIN + 1);
            }
            // a path can only be planned from the ground (vanilla's rule), and a chicken spends its hops fluttering down
            if (time >= run.nextPlan && MobMotor.idle(mob) && mob.onGround()) {
                run.nextPlan = time + (next(mob, run, anchor) ? REPLAN : REPLAN_FAILED);
            }
        }
        if ((time + mob.getId()) % 20 == 0) watch(level, mob, run);
    }

    /** Picks and walks to the next point of the lap, or back toward the spot if it is outside the circle; whether it walks. */
    private static boolean next(Mob mob, Run run, BlockPos anchor) {
        Vec3 home = Vec3.atBottomCenterOf(anchor);
        if (flat(mob.position(), home) > RADIUS - MARGIN) {
            // shoved or knocked out (or at the edge): back toward the spot, 8 blocks at a time when it is too far for one
            // path. A leg that would end in a pond or a pit is shortened, then turned aside: the same wet leg tried again
            // and again kept a chicken standing outside its circle for good.
            Vec3 toward = home.subtract(mob.position()).multiply(1, 0, 1);
            double d = toward.length();
            run.centre = null;
            if (d < 1e-3) return false;
            Vec3 dir = toward.scale(1 / d);
            run.goingBack = true;
            for (double leg : BACK_LEGS) {
                double len = Math.min(leg, d);
                for (double turn : BACK_TURNS) {
                    Vec3 step = len >= d && turn == 0 ? home : mob.position().add(dir.yRot((float) turn).scale(len));
                    BlockPos ground = ground(mob, BlockPos.containing(step.x, mob.getY(), step.z));
                    if (ground != null && MobMotor.walkTo(mob, ground, SPEED, null)) return true;
                }
                if (len >= d) break;
            }
            return false;
        }
        run.goingBack = false;
        for (int tries = 0; tries < 6; tries++) {
            if (run.centre == null || run.points >= POINTS_PER_CENTRE) {
                double a = mob.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(mob.getRandom().nextDouble()) * DRIFT;
                run.centre = home.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                run.points = 0;
            }
            // round the circle a sixth at a time, a little unevenly
            run.angle += Math.PI / 3 * (0.8 + mob.getRandom().nextDouble() * 0.4);
            run.points++;
            Vec3 p = run.centre.add(Math.cos(run.angle) * CIRCLE, 0, Math.sin(run.angle) * CIRCLE);
            BlockPos ground = ground(mob, BlockPos.containing(p.x, mob.getY(), p.z));
            if (ground == null || flat(Vec3.atBottomCenterOf(ground), home) > RADIUS - MARGIN) {
                // a bad centre (a pond, a wall, the edge): move it
                run.points = POINTS_PER_CENTRE;
                continue;
            }
            int[] lastY = {mob.getBlockY()};
            boolean walking = MobMotor.walkTo(mob, ground, SPEED, n -> {
                boolean inside = flat(new Vec3(n.x + 0.5, 0, n.z + 0.5), new Vec3(home.x, 0, home.z)) <= RADIUS - MARGIN;
                boolean noDrop = n.y >= lastY[0] - 1;
                lastY[0] = n.y;
                return inside && noDrop && n.type != PathType.WATER && n.type != PathType.LAVA;
            });
            if (walking) return true;
            run.points = POINTS_PER_CENTRE;
        }
        return false;
    }

    /** Dry, solid ground for a chicken's feet in this column, a few blocks either side of {@code at}, or null. */
    private static BlockPos ground(Mob mob, BlockPos at) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = at.above(dy);
            if (!mob.level().isLoaded(pos)) return null;
            if (WalkNodeEvaluator.getPathTypeStatic(mob, pos) == PathType.WALKABLE) return pos;
        }
        return null;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.sqrt(Mth.square(a.x - b.x) + Mth.square(a.z - b.z));
    }

    /** "Running Around Like a Headless Chicken": ten seconds watched by a player near enough, looking at it. */
    private static void watch(ServerLevel level, Mob mob, Run run) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(WATCH_RANGE))) {
            if (p.isSpectator() || p.distanceTo(mob) > WATCH_RANGE || !p.hasLineOfSight(mob)) continue;
            Vec3 to = mob.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize();
            double degrees = Math.toDegrees(Math.acos(Mth.clamp(to.dot(p.getLookAngle()), -1, 1)));
            if (degrees > WATCH_DEGREES) continue;
            int seen = run.watched.merge(p.getUUID(), 20, Integer::sum);
            if (seen >= WATCH_TICKS) com.coolfreeze23.headinajar.Advance.grant(p, "headless_chicken");
        }
    }
}
