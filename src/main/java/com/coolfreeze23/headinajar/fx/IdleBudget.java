package com.coolfreeze23.headinajar.fx;

/**
 * Idle life is heard sparingly: of the small sounds machines make on their own (a socketed zombie's clock, an unpaid
 * employee's sigh), at most {@link #PER_SECOND} start in any one second on a client, whatever the number of machines
 * in range. A room of forty machines ticks no louder than a room of three.
 */
public final class IdleBudget {
    public static final int PER_SECOND = 3;
    private static long second = Long.MIN_VALUE;
    private static int used;

    /** Whether an idle sound may start on this game tick (and counts it if so). */
    public static boolean take(long gameTime) {
        long s = Math.floorDiv(gameTime, 20);
        if (s != second) {
            second = s;
            used = 0;
        }
        if (used >= PER_SECOND) return false;
        used++;
        return true;
    }

    /** Forgets the count (tests). */
    public static void reset() {
        second = Long.MIN_VALUE;
        used = 0;
    }

    private IdleBudget() {
    }
}
