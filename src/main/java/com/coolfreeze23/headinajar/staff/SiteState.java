package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What the 0.7.2 parts tell each other about a site and its staff, so no part reaches into another's classes: whether a
 * clock's site is on strike, who is striking or the Rep, who has a grievance open, and the holds on a worker's brain
 * (a part that holds a worker runs it this tick in place of its work). Each part sets its own hooks at registration;
 * until then everything answers "no".
 */
public final class SiteState {
    private SiteState() {
    }

    /** Whether this clock's site is on strike. */
    public static Predicate<TimeClockBlockEntity> striking = c -> false;
    /** Whether this worker is on strike (stands in the ring). */
    public static Predicate<Mob> striker = m -> false;
    /** Whether this worker is its strike's Union Rep. */
    public static Predicate<Mob> rep = m -> false;
    /** Whether this worker has a Grievance open (it faces the wall and does no work). */
    public static Predicate<Mob> grievance = m -> {
        StaffFile f = Staff.of(m);
        return f != null && f.career.grievance;
    };
    /** A striking site's demands as the Roster shows them, one line a striker (the strikes fill it). */
    public static java.util.function.Function<TimeClockBlockEntity, List<net.minecraft.network.chat.Component>> demands = c -> List.of();
    /** Run for each clock right after its dawn (every worker on its roster has had theirs): the strike test. */
    public static final List<Consumer<TimeClockBlockEntity>> AFTER_DAWN = new ArrayList<>();

    /** A hold on a worker's brain: true when it has run the worker this tick, in place of its work. */
    public interface Hold {
        boolean hold(Mob mob, WorkerData d);
    }

    /** The holds, in order (the first that holds wins). */
    public static final List<Hold> HOLDS = new ArrayList<>();

    /** Whether a part holds this worker this tick (and has run it). */
    public static boolean held(Mob mob, WorkerData d) {
        for (Hold h : HOLDS) if (h.hold(mob, d)) return true;
        return false;
    }

    public static void afterDawn(TimeClockBlockEntity clock) {
        for (Consumer<TimeClockBlockEntity> c : AFTER_DAWN) c.accept(clock);
    }
}
