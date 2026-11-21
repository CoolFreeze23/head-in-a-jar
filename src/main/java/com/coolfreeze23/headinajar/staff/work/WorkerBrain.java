package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.world.entity.Mob;

/**
 * A worker's think, every {@code staff.thinkTicks} (10) ticks, staggered by entity id ({@code Workforce.tick}): what it
 * does next for the state it is in. Off shift, awaiting, off site and on its way to the clock are the site's day
 * ({@link Shifts}); on the job, the card's job ({@code jobs.Jobs}); on a detail, the Whistle's ({@link Detail}). An escort
 * seeing a colleague out does that first. The counters are what {@code /headoffice staff list} and the bench read.
 */
public final class WorkerBrain {
    /** Set while a worker thinks (a test counts chunk loads that happen while it is set). */
    public static final ThreadLocal<Boolean> THINKING = ThreadLocal.withInitial(() -> false);

    /** Thinks and the time they took, since the counters were last read. */
    private static long thinks, thinkNanos;
    private static long since = System.nanoTime();

    private WorkerBrain() {
    }

    public static void think(Mob mob, WorkerData d) {
        long t0 = System.nanoTime();
        THINKING.set(true);
        try {
            if (LetGo.escorting(mob)) return;
            Quirks.think(mob, d);
            switch (d.state) {
                case ON_JOB -> {
                    if (Shifts.stillOn(mob, d)) com.coolfreeze23.headinajar.staff.work.jobs.Jobs.think(mob, d);
                }
                case DETAIL -> {
                    if (!Quirks.holds(mob, d)) Detail.think(mob, d);
                }
                case NOTICE, SHUTTLE -> {
                }
                default -> {
                    if (!Quirks.holds(mob, d)) Shifts.think(mob, d);
                }
            }
        } finally {
            THINKING.set(false);
            thinks++;
            thinkNanos += System.nanoTime() - t0;
        }
    }

    /** Thinks a second, milliseconds of thinking a tick (at 20 ticks a second), since the last read; then resets. */
    public static double[] readCounters() {
        double secs = Math.max(1e-9, (System.nanoTime() - since) / 1e9);
        double[] out = {thinks / secs, thinkNanos / 1e6 / (secs * 20)};
        thinks = 0;
        thinkNanos = 0;
        since = System.nanoTime();
        return out;
    }
}
