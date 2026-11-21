package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.mind.MobMotor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * How each species gets about at work: its movement family (the staff file's {@code move}).
 * <ul>
 * <li>{@code walk}, {@code lava}, {@code water}, {@code fly}: the mob's own navigation (ground, lava, swimming or
 * amphibious, flying) plans the path and its own move control walks, swims or flies it.</li>
 * <li>{@code climb}: the same, and a spider's {@link WallClimberNavigation} climbs the last stretch up to a high stop.</li>
 * <li>{@code hop}: the navigation's path as waypoints, each hop aimed at the next one through {@code SlimeMoveControl}.</li>
 * <li>{@code fly_free} (ghast, blaze, phantom): no path; straight legs to the stop, every leg swept with the mob's own
 * box first (over the top when the straight way is blocked), its motion set towards the next point each tick.</li>
 * <li>{@code squid}: straight legs through water only, steered by {@link Squid#setMovementVector}.</li>
 * <li>{@code teleport} (enderman): no path; a hop to a standable spot within 2 of the stop, inside the box.</li>
 * </ul>
 * Every block it reads away from itself is read only if its chunk is loaded. A species whose family cannot reach a stop
 * 16 blocks away is refused at hire ({@link #canMove}). How often it may ask, and the box, are {@link WorkerPaths}'.
 */
public final class Movers {
    public static final String WALK = "walk", CLIMB = "climb", FLY = "fly", FLY_FREE = "fly_free", HOP = "hop",
            WATER = "water", SQUID = "squid", TELEPORT = "teleport", LAVA = "lava";
    public static final List<String> FAMILIES = List.of(WALK, CLIMB, FLY, FLY_FREE, HOP, WATER, SQUID, TELEPORT, LAVA);
    /** A stop is reached when its centre is this close to the mob's box. */
    public static final double REACH = 1.5;
    /** An open spot (a place to stand) is reached this close to its centre. */
    public static final double ON_SPOT = 0.8;
    /** Flying straight (fly_free), blocks a tick at walk 1 and speed 1: 2 blocks a second. */
    static final double CRUISE = 0.1;
    /** A squid's movement vector at walk 1 (it swims at about half of it, pulse by pulse). */
    static final double SWIM = 0.32;
    /** How far from the stop an enderman may land. */
    static final int TELEPORT_REACH = 2;
    /** A straight route that has not got closer for this long is dropped (the next request counts it a failure). */
    static final int STUCK_TICKS = 60;
    /** A climb's last stretch (no path left, the climber's own push) is given this long before it counts as done. */
    static final int CLIMB_TICKS = 100;
    /** A horse's speed attribute varies from horse to horse; workers are walked as if each had the average. */
    static final double HORSE_SPEED = 0.225;
    /** A size 2 slime's speed attribute (0.2 + 0.1 a size): the work table's slime numbers are for that size. */
    static final double SLIME_SPEED = 0.4;
    /** At work a slime hops at least this often (a magma cube's own wait is four times a slime's). */
    static final int HOP_TICKS = 10;
    /** A hop's pace through the air, blocks a tick at walk 1. */
    static final double HOP_PACE = 0.16;

    /** The way each worker is going now (not saved: a reload asks again). */
    private static final Map<Mob, Route> ROUTES = new WeakHashMap<>();

    /** Where a worker is going and how. {@code points} for the straight families; empty for those that follow a path. */
    static final class Route {
        final BlockPos to;
        final String family;
        final List<Vec3> points;
        final double speed;
        final long started;
        int next;
        double best = Double.MAX_VALUE;
        long bestAt;

        Route(BlockPos to, String family, List<Vec3> points, double speed, long started) {
            this.to = to;
            this.family = family;
            this.points = points;
            this.speed = speed;
            this.started = started;
            this.bestAt = started;
        }

        boolean straight() {
            return !points.isEmpty();
        }
    }

    private Movers() {
    }

    /** The movement family of a worker (its species' row; walk if it has none). */
    public static String family(Mob mob) {
        WorkTable.Spec spec = WorkTable.of(mob);
        // a seconded body hops or hovers only with its trick, else it walks (0.7.1, staff.second.BodyWork)
        return spec == null ? WALK : com.coolfreeze23.headinajar.staff.second.BodyWork.family(mob, spec.move());
    }

    /**
     * Whether this creature can get about as a worker: its family is one the mod drives and the creature has what that
     * family needs (the kind of navigation, a slime's hop, a squid's swim). False refuses the hire with
     * {@code lanyard.unknown}.
     */
    public static boolean canMove(Mob mob, WorkTable.Spec spec) {
        PathNavigation nav = mob.getNavigation();
        return switch (spec.move()) {
            // a drowned swims when it is in water; a frog walks with its amphibious navigation
            case WALK, LAVA -> nav instanceof GroundPathNavigation || nav instanceof AmphibiousPathNavigation || mob instanceof Drowned;
            case CLIMB -> nav instanceof WallClimberNavigation;
            case FLY -> nav instanceof FlyingPathNavigation;
            case WATER -> nav instanceof WaterBoundPathNavigation || nav instanceof AmphibiousPathNavigation;
            case HOP -> mob instanceof Slime && mob.getMoveControl() instanceof Slime.SlimeMoveControl && nav instanceof GroundPathNavigation;
            case SQUID -> mob instanceof Squid;
            case FLY_FREE, TELEPORT -> true;
            default -> false;
        };
    }

    /**
     * Whether a path can be planned for it now: a walker, climber, hopper or strider only from the ground (or in a
     * liquid), as vanilla plans them; a swimmer in water or on the ground; the rest at any time. A worker in the air
     * asks again when it lands, without using up its turn.
     */
    static boolean canPlanNow(Mob mob) {
        return switch (family(mob)) {
            case FLY, FLY_FREE, SQUID, TELEPORT -> true;
            case WATER -> mob.isInWater() || mob.onGround();
            default -> mob.onGround() || mob.isInLiquid() || mob.isPassenger();
        };
    }

    /**
     * Whether it is there: beside a solid stop (a container: its centre within {@link #REACH} of the mob's box), or on an
     * open spot (a place to stand: within {@link #ON_SPOT} of its centre on the level, more for a mob wider than a block).
     */
    public static boolean arrived(Mob mob, BlockPos to) {
        if (solid(mob.level(), to)) return mob.getBoundingBox().inflate(REACH).contains(Vec3.atCenterOf(to));
        // a climber still on the wall below a spot at the top of it is not there: stopped there, it falls off
        if (clinging(mob)) return false;
        double dx = mob.getX() - (to.getX() + 0.5), dz = mob.getZ() - (to.getZ() + 0.5);
        double r = ON_SPOT + Math.max(0, Mth.ceil(mob.getBbWidth()) - 1);
        return dx * dx + dz * dz <= r * r && Math.abs(mob.getY() - to.getY()) < 1.5;
    }

    /** Whether it is still on its way to {@code to} by its last request (no new one is needed). */
    static boolean onTheWay(Mob mob, BlockPos to) {
        Route r = ROUTES.get(mob);
        return r != null && r.to.equals(to) && active(mob, r);
    }

    /** Whether its last request was for {@code to} and is over without it getting there. */
    static boolean cameShort(Mob mob, BlockPos to) {
        Route r = ROUTES.get(mob);
        return r != null && r.to.equals(to) && !active(mob, r) && !arrived(mob, to);
    }

    private static boolean active(Mob mob, Route r) {
        if (r.straight()) return r.next < r.points.size();
        if (TELEPORT.equals(r.family)) return false;
        PathNavigation nav = mob.getNavigation();
        // still following its own path to this stop (not one something else has since given it)
        if (!nav.isDone()) return nav.getTargetPos() == null || nav.getTargetPos().closerThan(r.to, 3);
        // a climber on its wall is still on its way, however long the climb is
        return CLIMB.equals(r.family) && (clinging(mob) || mob.level().getGameTime() - r.started < CLIMB_TICKS);
    }

    /** A climber off the ground (on a wall, or over the edge at its top), not riding and not swimming. */
    static boolean clinging(Mob mob) {
        return !mob.onGround() && !mob.isInLiquid() && !mob.isPassenger() && CLIMB.equals(family(mob));
    }

    /** Whether it has a way to go it has not finished (the pause stops it). */
    static boolean moving(Mob mob) {
        return ROUTES.containsKey(mob);
    }

    /** Stops it where it is and forgets where it was going. */
    public static void halt(Mob mob) {
        ROUTES.remove(mob);
        MobMotor.halt(mob);
        if (mob instanceof Squid squid) squid.setMovementVector(0, 0, 0);
        hold(mob);
    }

    /**
     * Starts it towards {@code to} at {@code speed} times its species' walk, every point of the way inside {@code box}
     * (null: anywhere). False when there is no way (nothing is changed then but the old route, which is dropped).
     */
    static boolean go(Mob mob, BlockPos to, double speed, AABB box) {
        ROUTES.remove(mob);
        // a seconded body may hop, sprint or hover to this stop with its Ability (0.7.1, staff.second.BodyWork)
        com.coolfreeze23.headinajar.staff.second.BodyWork.setOff(mob, to);
        String family = family(mob);
        long now = mob.level().getGameTime();
        double pace = speed * walk(mob);
        switch (family) {
            case TELEPORT -> {
                if (!teleport(mob, to, box)) return false;
                ROUTES.put(mob, new Route(to, family, List.of(), pace, now));
                return true;
            }
            case FLY_FREE, SQUID -> {
                List<Vec3> points = straight(mob, to, box, SQUID.equals(family));
                if (points == null) return false;
                ROUTES.put(mob, new Route(to, family, points, pace, now));
                return true;
            }
            default -> {
                if (!follow(mob, family, to, pace, box)) return false;
                ROUTES.put(mob, new Route(to, family, List.of(), pace, now));
                return true;
            }
        }
    }

    /** Its species' walk, with a horse's own speed evened out. */
    static double walk(Mob mob) {
        WorkTable.Spec spec = WorkTable.of(mob);
        double walk = spec == null ? 1 : spec.walk();
        if (mob instanceof Horse) {
            double base = mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            if (base > 0.01) walk *= HORSE_SPEED / base;
        } else if (mob instanceof Slime slime) {
            // a slime's speed grows with its size; every size hops at a size 2's pace
            double base = mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            if (base > 0.01) walk *= SLIME_SPEED / base;
        }
        return walk;
    }

    // --- following a path (walk, climb, fly, water, lava, hop) ------------------------------------------------------

    private static boolean follow(Mob mob, String family, BlockPos to, double pace, AABB box) {
        PathNavigation nav = mob.getNavigation();
        double base = mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE);
        // vanilla sizes the search by the follow range the mob was made with (x16); hold it to WorkerPaths.MAX_NODES
        nav.setMaxVisitedNodesMultiplier((float) (WorkerPaths.MAX_NODES / Math.max(16.0, Mth.floor(Math.max(1, base) * 16.0))));
        Path path;
        if (CLIMB.equals(family)) {
            // the climber's own createPath remembers the stop and climbs the rest of the way when the path runs out
            path = nav.createPath(to, 1);
            if (path == null || !(path.canReach() || closeBelow(path, to))) return false;
        } else {
            // a mob wider than a block plans by its wider footprint, whose corner cannot stand right by the stop
            int wide = Math.max(0, Mth.ceil(mob.getBbWidth()) - 1);
            path = nav.createPath(targets(mob.level(), to), wide);
            if (path == null || !path.canReach()) path = nav.createPath(to, (solid(mob.level(), to) ? 1 : 0) + wide);
            if (path == null || !path.canReach()) return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node n = path.getNode(i);
            if (box != null && !box.contains(n.x + 0.5, n.y + 0.1, n.z + 0.5)) return false;
        }
        return nav.moveTo(path, pace);
    }

    /** A climber's partial path that ends at the foot of the stop (it climbs from there). */
    private static boolean closeBelow(Path path, BlockPos to) {
        Node end = path.getEndNode();
        if (end == null) return false;
        int dx = Math.abs(end.x - to.getX()), dz = Math.abs(end.z - to.getZ());
        return dx <= 2 && dz <= 2 && end.y <= to.getY() + 1;
    }

    /** The spots a walker may stand at for a stop: the stop itself if it is open, else beside it and on top of it. */
    static Set<BlockPos> targets(Level level, BlockPos to) {
        Set<BlockPos> out = new LinkedHashSet<>();
        if (!solid(level, to)) {
            out.add(to);
            return out;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = to.relative(dir);
            if (WorkerPaths.loaded(level, p) && !solid(level, p)) out.add(p);
        }
        // on top of it only when it cannot be reached from the side
        if (out.isEmpty()) out.add(to.above());
        return out;
    }

    /** Open here and above, something to stand on below (loaded chunks only). */
    static boolean standable(Level level, BlockPos p) {
        return WorkerPaths.loaded(level, p.below()) && !solid(level, p) && !solid(level, p.above()) && solid(level, p.below());
    }

    private static boolean solid(Level level, BlockPos p) {
        return WorkerPaths.loaded(level, p) && !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    // --- straight legs (fly_free, squid) ----------------------------------------------------------------------------

    /**
     * Straight legs from the mob to a spot by the stop: the direct line if it is clear, else up and over (legs up, across
     * and down, each clear; a swimmer also tries under). Null: no clear way inside the box.
     */
    static List<Vec3> straight(Mob mob, BlockPos to, AABB box, boolean water) {
        Vec3 start = mob.position();
        Vec3 goal = spotBy(mob, to, box, water);
        if (goal == null) return null;
        if (clear(mob, start, goal, water)) return List.of(goal);
        for (int up = 2; up <= 8; up += 2) {
            for (int sign : water ? new int[]{1, -1} : new int[]{1}) {
                double y = (sign > 0 ? Math.max(start.y, goal.y) : Math.min(start.y, goal.y)) + sign * up;
                Vec3 a = new Vec3(start.x, y, start.z), b = new Vec3(goal.x, y, goal.z);
                if (!fits(mob, a, box) || !fits(mob, b, box)) continue;
                if (clear(mob, start, a, water) && clear(mob, a, b, water) && clear(mob, b, goal, water)) {
                    List<Vec3> out = new ArrayList<>();
                    out.add(a);
                    out.add(b);
                    out.add(goal);
                    return out;
                }
            }
        }
        return null;
    }

    /** A spot (the bottom centre of its box) where the mob fits by the stop: the stop, above it, or beside it. */
    static Vec3 spotBy(Mob mob, BlockPos to, AABB box, boolean water) {
        List<BlockPos> tries = new ArrayList<>();
        tries.add(to);
        for (int up = 1; up <= 3; up++) tries.add(to.above(up));
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            tries.add(to.relative(dir));
            tries.add(to.relative(dir).above());
        }
        for (BlockPos p : tries) {
            Vec3 at = Vec3.atBottomCenterOf(p);
            if (fits(mob, at, box) && (!water || wet(mob.level(), at, mob))) return at;
        }
        return null;
    }

    /** Whether the mob's box placed at {@code at} is inside {@code box}, loaded, and touches no block. */
    static boolean fits(Mob mob, Vec3 at, AABB box) {
        AABB bb = boxAt(mob, at);
        if (box != null && !box.contains(at.x, at.y + 0.1, at.z)) return false;
        return loaded(mob.level(), bb) && mob.level().noCollision(mob, bb);
    }

    /** Whether the way from {@code a} to {@code b} is clear for the mob's box (and all water, for a swimmer). */
    static boolean clear(Mob mob, Vec3 a, Vec3 b, boolean water) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, Mth.ceil(d.length() / 0.5));
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            AABB bb = boxAt(mob, p);
            if (!loaded(mob.level(), bb) || !mob.level().noCollision(mob, bb)) return false;
            if (water && !wet(mob.level(), p, mob)) return false;
        }
        return true;
    }

    private static AABB boxAt(Mob mob, Vec3 at) {
        return mob.getBoundingBox().move(at.x - mob.getX(), at.y - mob.getY(), at.z - mob.getZ()).deflate(0.05);
    }

    private static boolean wet(Level level, Vec3 at, Mob mob) {
        BlockPos p = BlockPos.containing(at.x, at.y + mob.getBbHeight() * 0.5, at.z);
        return WorkerPaths.loaded(level, p) && level.getFluidState(p).is(FluidTags.WATER);
    }

    /** Whether every chunk a box touches is loaded (a box is never wider than a chunk, so its corners tell). */
    static boolean loaded(Level level, AABB bb) {
        return WorkerPaths.loaded(level, BlockPos.containing(bb.minX, bb.minY, bb.minZ))
                && WorkerPaths.loaded(level, BlockPos.containing(bb.maxX, bb.minY, bb.maxZ))
                && WorkerPaths.loaded(level, BlockPos.containing(bb.minX, bb.minY, bb.maxZ))
                && WorkerPaths.loaded(level, BlockPos.containing(bb.maxX, bb.minY, bb.minZ));
    }

    // --- the enderman -----------------------------------------------------------------------------------------------

    /** A hop to a standable spot within {@link #TELEPORT_REACH} of the stop, inside the box, with the sound and the purple. */
    static boolean teleport(Mob mob, BlockPos to, AABB box) {
        Level level = mob.level();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(to.offset(-TELEPORT_REACH, -TELEPORT_REACH, -TELEPORT_REACH),
                to.offset(TELEPORT_REACH, TELEPORT_REACH, TELEPORT_REACH))) {
            if (!WorkerPaths.loaded(level, p) || !WorkerPaths.loaded(level, p.below())) continue;
            if (!level.getBlockState(p.below()).blocksMotion()) continue;
            Vec3 at = Vec3.atBottomCenterOf(p);
            if (!fits(mob, at, box) || level.containsAnyLiquid(boxAt(mob, at))) continue;
            double dd = p.distSqr(to) * 100 + p.distSqr(mob.blockPosition()) * 0.01;
            if (dd < bestD) {
                bestD = dd;
                best = p.immutable();
            }
        }
        if (best == null) return false;
        Vec3 from = mob.position();
        mob.getNavigation().stop();
        mob.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(mob));
        level.broadcastEntityEvent(mob, (byte) 46);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1, 1);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1, 1);
        return true;
    }

    // --- each tick --------------------------------------------------------------------------------------------------

    /** Each tick of a working worker, after its body's tick ({@code Workforce.tick}): steers the families that need it. */
    public static void tick(Mob mob) {
        Route r = ROUTES.get(mob);
        if (r == null || !active(mob, r)) {
            hold(mob);
            return;
        }
        switch (r.family) {
            case HOP -> hop(mob, r);
            case CLIMB -> climbOn(mob, r);
            case FLY_FREE -> fly(mob, r);
            case SQUID -> {
                if (mob instanceof Squid squid) swim(squid, r);
            }
            default -> {
            }
        }
    }

    /**
     * A climber at the top of its wall with its path done: the spider's own last stretch lets go as soon as it is level
     * with a stop near the edge, and it hung over the edge and dropped (six blocks: three hearts, enough to kill a
     * host). It is steered on over the top onto the stop instead.
     */
    private static void climbOn(Mob mob, Route r) {
        if (!mob.getNavigation().isDone() || !clinging(mob) || mob.getY() < r.to.getY() - 1) return;
        mob.getMoveControl().setWantedPosition(r.to.getX() + 0.5, r.to.getY(), r.to.getZ() + 0.5, r.speed);
    }

    /** With nowhere to go: a squid stops paddling, a phantom circles where it is, a ghast stops drifting. */
    private static void hold(Mob mob) {
        if (mob instanceof Squid squid) {
            if (squid.hasMovementVector()) squid.setMovementVector(0, 0, 0);
        } else if (mob instanceof Phantom phantom) {
            if (phantom.moveTargetPoint.distanceToSqr(phantom.position()) > 9) phantom.moveTargetPoint = phantom.position();
        } else if (mob instanceof Ghast ghast) {
            var c = ghast.getMoveControl();
            if (c.hasWanted() && ghast.position().distanceToSqr(c.getWantedX(), c.getWantedY(), c.getWantedZ()) > 1) {
                c.setWantedPosition(ghast.getX(), ghast.getY(), ghast.getZ(), 1);
            }
        }
    }

    /**
     * A slime: each hop aimed at the path's next point, and carried there through the air at a steady pace (its own air
     * control would overshoot the points of a path or barely move it, by size and kind).
     */
    private static void hop(Mob mob, Route r) {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone() || !(mob.getMoveControl() instanceof Slime.SlimeMoveControl c)) return;
        Vec3 next = path.getNextEntityPos(mob);
        double dx = next.x - mob.getX(), dz = next.z - mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        // hopping as a slime hops at a target (three times as often as it idles), and a magma cube no slower
        c.setDirection(yaw, true);
        if (c.jumpDelay > HOP_TICKS) c.jumpDelay = HOP_TICKS;
        if (!mob.onGround()) {
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.05) {
                double v = Math.min(HOP_PACE * r.speed, dist);
                mob.setDeltaMovement(dx / dist * v, mob.getDeltaMovement().y, dz / dist * v);
            }
        }
    }

    /** A ghast, blaze or phantom: its motion set straight at the next point (the last stretch slowing down). */
    private static void fly(Mob mob, Route r) {
        Vec3 next = r.points.get(r.next);
        Vec3 d = next.subtract(mob.position());
        double dist = d.length();
        if (!progress(mob, r, dist)) return;
        if (dist < 0.4) {
            r.next++;
            if (r.next >= r.points.size()) {
                mob.setDeltaMovement(Vec3.ZERO);
                if (mob instanceof Phantom p) p.moveTargetPoint = mob.position();
                if (mob instanceof Ghast g) g.getMoveControl().setWantedPosition(g.getX(), g.getY(), g.getZ(), 1);
            } else {
                r.best = Double.MAX_VALUE;
            }
            return;
        }
        double v = Math.min(CRUISE * r.speed, dist * 0.5);
        // the move itself uses the motion as set (a blaze's gravity comes after it and is set back next tick)
        mob.setDeltaMovement(d.scale(v / dist));
        mob.hasImpulse = true;
        mob.resetFallDistance();
        if (d.horizontalDistanceSqr() > 0.01) {
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
            mob.yHeadRot = yaw;
        }
        if (mob instanceof Phantom p) p.moveTargetPoint = next;
    }

    /** A squid: its paddle aimed at the next point. */
    private static void swim(Squid squid, Route r) {
        Vec3 next = r.points.get(r.next);
        Vec3 d = next.subtract(squid.position());
        double dist = d.length();
        if (!progress(squid, r, dist)) return;
        if (dist < 0.7) {
            r.next++;
            if (r.next >= r.points.size()) squid.setMovementVector(0, 0, 0);
            else r.best = Double.MAX_VALUE;
            return;
        }
        Vec3 m = d.scale(SWIM * r.speed / dist);
        squid.setMovementVector((float) m.x, (float) m.y, (float) m.z);
    }

    /** Keeps the best distance to the next point; false (and the route over) when it has not improved for a while. */
    private static boolean progress(Mob mob, Route r, double dist) {
        long now = mob.level().getGameTime();
        if (dist < r.best - 0.25) {
            r.best = dist;
            r.bestAt = now;
        } else if (now - r.bestAt > STUCK_TICKS) {
            r.next = r.points.size();
            return false;
        }
        return true;
    }

    // --- random points ----------------------------------------------------------------------------------------------

    /** A random point inside {@code box} (null: anywhere near) the worker can go to: patrols, an idle wander. Null: none found. */
    public static BlockPos randomPoint(Mob mob, AABB box) {
        return randomPoint(mob, box, null, 0);
    }

    /**
     * A random point inside {@code box} (null: anywhere near), at least {@code away} blocks from {@code from} when that is
     * given (the walk away on Let go). From vanilla's own pickers for a pathfinding mob; a ghast, phantom, slime or squid
     * picks its own: an open (or water) spot, checked by a sweep from where it is. Null: none found in ten tries (away
     * from someone: sixteen, and failing a point far enough, the farthest found that is further than it stands now).
     */
    public static BlockPos randomPoint(Mob mob, AABB box, Vec3 from, double away) {
        String family = family(mob);
        int reach = away > 0 ? (int) away + 4 : 10;
        BlockPos best = null;
        double bestAway = from == null ? 0 : mob.position().distanceTo(from) + 2;
        for (int i = 0; i < (from == null ? 10 : 16); i++) {
            Vec3 v;
            if (mob instanceof PathfinderMob pm && !SQUID.equals(family) && !FLY_FREE.equals(family) && !HOP.equals(family)) {
                v = switch (family) {
                    case WATER -> BehaviorUtils.getRandomSwimmablePos(pm, reach, 7);
                    case FLY -> from != null ? DefaultRandomPos.getPosAway(pm, reach, 7, from) : DefaultRandomPos.getPos(pm, reach, 7);
                    default -> from == null ? LandRandomPos.getPos(pm, reach, 7) : LandRandomPos.getPosAway(pm, reach, 7, from);
                };
            } else {
                v = own(mob, family, box, reach);
            }
            if (v == null) continue;
            BlockPos p = BlockPos.containing(v);
            if (box != null && !box.contains(Vec3.atCenterOf(p))) continue;
            if (!WorkerPaths.loaded(mob.level(), p)) continue;
            // a walker's point is a place to stand: open, with something under it
            if ((WALK.equals(family) || CLIMB.equals(family) || TELEPORT.equals(family)) && !standable(mob.level(), p)) continue;
            if (from == null || v.distanceTo(from) >= away - 1) return p;
            // none far enough yet: keep the farthest that is at least further than it stands now
            if (v.distanceTo(from) > bestAway) {
                bestAway = v.distanceTo(from);
                best = p;
            }
        }
        return best;
    }

    /**
     * A point a ghast, phantom, slime or squid picks for itself: a slime a standable spot below a random point; a flier or
     * a squid the far end of a random line from where it is, as far as the way is clear for its box (and all water).
     */
    private static Vec3 own(Mob mob, String family, AABB box, int reach) {
        var rnd = mob.getRandom();
        Level level = mob.level();
        if (HOP.equals(family)) {
            Vec3 at = mob.position().add(rnd.nextInt(2 * reach + 1) - reach, rnd.nextInt(5) - 2, rnd.nextInt(2 * reach + 1) - reach);
            BlockPos p = BlockPos.containing(at);
            for (int down = 0; down < 8; down++, p = p.below()) {
                if (!WorkerPaths.loaded(level, p.below())) return null;
                if (level.getBlockState(p.below()).blocksMotion()) {
                    Vec3 stand = Vec3.atBottomCenterOf(p);
                    return fits(mob, stand, box) ? stand : null;
                }
            }
            return null;
        }
        boolean water = SQUID.equals(family);
        double yaw = rnd.nextDouble() * Math.PI * 2, pitch = (rnd.nextDouble() - 0.5) * 0.8;
        Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
        double length = 3 + rnd.nextDouble() * Math.max(1, reach - 3);
        Vec3 here = mob.position(), last = null;
        for (double t = 0.5; t <= length; t += 0.5) {
            Vec3 p = here.add(dir.scale(t));
            if (!fits(mob, p, box) || (water && !wet(level, p, mob))) break;
            last = p;
        }
        return last != null && last.distanceTo(here) >= 2 ? last : null;
    }
}
