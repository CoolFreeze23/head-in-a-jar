package com.coolfreeze23.headinajar.fx;

import net.minecraft.util.RandomSource;

/**
 * A badly cracked jar is heard dripping: below a quarter of its glass, with saline left in it, and standing (not carried,
 * rolled or driven), a slow drip from its base every 2 to 6 seconds. Heard only: no drop is drawn while the leak's
 * drops are switched off. The rule lives here, apart from the client that plays it, so it can be checked on its own.
 */
public final class JarDrip {
    /** The crack stage (0 whole to 3 a quarter or less) at which the drip starts. */
    public static final int STAGE = 3;
    public static final int MIN_GAP = 40, MAX_GAP = 120;

    public static boolean heard(int stage, boolean wet, boolean placed) {
        return stage >= STAGE && wet && placed;
    }

    /** Ticks to the next drip, 2 to 6 seconds. */
    public static int gap(RandomSource random) {
        return MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP + 1);
    }

    private JarDrip() {
    }
}
