package com.coolfreeze23.headinajar.fx;

/**
 * The link heard: a body driven over a weak link hears static in its own head, in bands by link quality (clean above
 * 70, a faint hiss above 40, steady crackle above 20, heavy static with a searching warble below), and the static
 * swells to full for exactly the ticks the weak link drops the body's inputs, the stutter the picture shows. The
 * stutter rule lives here so the movement handler and the static use one rule and cannot drift apart.
 */
public final class LinkStatic {
    public enum Band { CLEAN, FAINT, STEADY, HEAVY }

    /** The level each band's loop plays at between stutters; a stutter swells it to 1. */
    public static final float BAND_LEVEL = 0.7f;

    public static Band band(int quality) {
        return quality >= 70 ? Band.CLEAN : quality >= 40 ? Band.FAINT : quality >= 20 ? Band.STEADY : Band.HEAVY;
    }

    /** Whether the weak link drops the body's inputs on this tick: 4 ticks a second under 20, 2 ticks in 40 under 40. */
    public static boolean stutter(int quality, long gameTime) {
        return quality < 20 && Math.floorMod(gameTime, 20) < 4 || quality < 40 && Math.floorMod(gameTime, 40) < 2;
    }

    /** The static's volume on this tick (0 for a clean link), before the player's static-intensity setting. */
    public static float volume(int quality, long gameTime) {
        if (band(quality) == Band.CLEAN) return 0;
        return stutter(quality, gameTime) ? 1 : BAND_LEVEL;
    }

    private LinkStatic() {
    }
}
