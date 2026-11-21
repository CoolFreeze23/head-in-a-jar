package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Paths for workers, rationed: at most one request a worker each {@link #PATH_TICKS} (one a think in combat), a search
 * held to {@link #MAX_NODES} visited nodes, and a path with any node outside its box thrown away as a failure. A stop
 * whose chunk is not loaded is never read: it is skipped at once ("Can't get there. Moving on."). A stop that fails
 * {@link #RETRIES} more times is skipped the same way; it is tried again when the worker asks for another stop, or
 * after {@link #RETRY_AFTER} ticks. How a species follows a path is its movement family's ({@link Movers}).
 * <p>
 * Callers ask every think until {@link #arrived}: while the worker is still on its way to the same stop no new path is
 * planned, so asking is cheap.
 */
public final class WorkerPaths {
    public static final int PATH_TICKS = 40;
    public static final int MAX_NODES = 400;
    /** A failed stop is tried this many more times, then skipped ("Can't get there. Moving on."). */
    public static final int RETRIES = 2;
    /** A skipped stop may be tried again this long after the last try. */
    public static final int RETRY_AFTER = 200;

    /** The answer to a request. */
    public enum Result {
        /** It is on its way (or already there). */
        WALKING,
        /** Not this time: its last request was too recent; ask again next think. */
        WAIT,
        /** No path inside the box (or the spot is not loaded). */
        FAILED
    }

    /** The stop each worker last asked for (a new stop starts its failures from 0). */
    private static final Map<Mob, BlockPos> ASKED = new WeakHashMap<>();
    /** Path requests and the time they took, and stops skipped unread, since the counters were last read. */
    private static long paths, pathNanos, longestNanos, unloaded;
    private static long since = System.nanoTime();

    private WorkerPaths() {
    }

    /**
     * Asks for the worker to go to {@code to} at {@code speed} (times its species' walk), every node inside {@code box}
     * (null: anywhere). {@code urgent} rations it to one a think instead of one a {@link #PATH_TICKS} (combat).
     */
    public static Result go(Mob mob, WorkerData d, BlockPos to, double speed, AABB box, boolean urgent) {
        Level level = mob.level();
        long now = level.getGameTime();
        BlockPos asked = ASKED.get(mob);
        if (!to.equals(asked)) {
            ASKED.put(mob, to.immutable());
            d.pathFails = 0;
        }
        if (!loaded(level, to)) {
            // fix 15: never read (so never load) a stop away in a chunk that is not there: skipped this trip
            unloaded++;
            giveUp(mob, d, urgent);
            return Result.FAILED;
        }
        if (Movers.arrived(mob, to) || Movers.onTheWay(mob, to)) return Result.WALKING;
        if (!Movers.canPlanNow(mob)) return Result.WAIT;
        if (givenUp(d)) {
            if (now - d.lastPath < RETRY_AFTER) return Result.FAILED;
            d.pathFails = 0;
        }
        if (now - d.lastPath < (urgent ? Workforce.thinkTicks() : PATH_TICKS)) return Result.WAIT;
        // the last way to this stop ran out short of it: that try failed
        if (Movers.cameShort(mob, to) && fail(mob, d, urgent)) return Result.FAILED;
        d.lastPath = now;
        long t0 = System.nanoTime();
        boolean ok;
        try {
            ok = Movers.go(mob, to, speed, box);
        } finally {
            long took = System.nanoTime() - t0;
            paths++;
            pathNanos += took;
            longestNanos = Math.max(longestNanos, took);
        }
        if (ok) return Result.WALKING;
        fail(mob, d, urgent);
        return Result.FAILED;
    }

    /** One more failure for the current stop; true (and the line said) when that was the last try. */
    private static boolean fail(Mob mob, WorkerData d, boolean urgent) {
        d.pathFails++;
        if (!givenUp(d)) return false;
        if (!urgent) Workforce.say(mob, "cant_get_there");
        return true;
    }

    private static void giveUp(Mob mob, WorkerData d, boolean urgent) {
        boolean was = givenUp(d);
        d.pathFails = RETRIES + 1;
        if (!was && !urgent) Workforce.say(mob, "cant_get_there");
    }

    /** Whether it has run out of tries for its current stop. */
    public static boolean givenUp(WorkerData d) {
        return d.pathFails > RETRIES;
    }

    /** Whether the worker is at {@code to} (its centre within reach of the worker's box). */
    public static boolean arrived(Mob mob, BlockPos to) {
        return Movers.arrived(mob, to);
    }

    /**
     * Whether a block may be read: its chunk is loaded (and, on a server, is there now, so reading it cannot load it).
     * Everything a worker reads away from itself goes through this first.
     */
    public static boolean loaded(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        return !(level instanceof ServerLevel server) || server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /** Stops walking (and forgets where it was going). */
    public static void stop(Mob mob) {
        Movers.halt(mob);
    }

    /** Paths a second, average ms a path, longest ms, stops skipped unread a second, since the last read; then resets. */
    public static double[] readCounters() {
        double secs = Math.max(1e-9, (System.nanoTime() - since) / 1e9);
        double[] out = {paths / secs, paths == 0 ? 0 : pathNanos / 1e6 / paths, longestNanos / 1e6, unloaded / secs};
        paths = 0;
        pathNanos = 0;
        longestNanos = 0;
        unloaded = 0;
        since = System.nanoTime();
        return out;
    }
}
