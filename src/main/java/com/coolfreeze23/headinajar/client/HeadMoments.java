package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;

/**
 * The desk's moments on a placed head, as offsets on top of its turn (all 0 at rest; every curve starts and ends at 0
 * and is eased between its keys, read at the frame's partial tick, so nothing steps or snaps). Times in ticks from the
 * block event; pitch + is down, roll + leans to the head's left, forward + toward where it looks (px).
 * <ul>
 * <li>M1 the bell ({@link JarredHeadBlockEntity#EV_BELL}): a look-up, 9 degrees, up in 3 ticks, held 2, down by 10.</li>
 * <li>M2 the nod ({@link JarredHeadBlockEntity#EV_NOD}, a trade or a ring): as before, in {@code JarredHeadRenderer}.</li>
 * <li>M3 the wince ({@link JarredHeadBlockEntity#EV_WINCE}, blood drawn): 4 ticks, a flinch down, aside and back in 1,
 * eased out over 3.</li>
 * <li>M4 the ram ({@link JarredHeadBlockEntity#EV_RAM}, a goat): 9 ticks, head up and back 0.6 px over 2.5, a knock 1.5 px
 * forward and 20 degrees down in 1 (accelerating into the glass), a beat, back over 5.</li>
 * <li>M5 the admiring tilt ({@link JarredHeadBlockEntity#EV_ADMIRE}, a piglin; param = the inspection's ticks): down 14
 * and leaning 10 in 6 ticks, held for the inspection, back over 8.</li>
 * </ul>
 */
public final class HeadMoments {
    /** The offsets now. */
    public record Offsets(float pitch, float yaw, float roll, float forward, float rise) {
        static final Offsets REST = new Offsets(0, 0, 0, 0, 0);
    }

    // M1: tick, value (1 = full), easing on the arriving key
    private static final float[] BELL_T = {0, 3, 5, 10};
    private static final float[] BELL_V = {0, 1, 1, 0};
    private static final String[] BELL_E = {"linear", "easeOutSine", "linear", "easeInOutSine"};
    private static final float BELL_UP = 9;
    // M3
    private static final float[] WINCE_T = {0, 1, 4};
    private static final float[] WINCE_V = {0, 1, 0};
    private static final String[] WINCE_E = {"linear", "easeOutQuad", "easeInOutSine"};
    private static final float WINCE_PITCH = 8, WINCE_ROLL = 7, WINCE_BACK = 0.4f;
    // M4: two channels (forward px, pitch degrees)
    private static final float[] RAM_T = {0, 2.5f, 3.5f, 4, 9};
    private static final float[] RAM_FWD = {0, -0.6f, 1.5f, 1.5f, 0};
    private static final float[] RAM_PITCH = {0, -8, 20, 20, 0};
    private static final String[] RAM_E = {"linear", "easeInOutSine", "easeInQuad", "linear", "easeInOutSine"};
    // M5
    private static final float ADMIRE_IN = 6, ADMIRE_OUT = 8, ADMIRE_PITCH = 14, ADMIRE_ROLL = 10;

    private HeadMoments() {
    }

    /** The head's moments at {@code now} (game time with the partial tick). */
    public static Offsets at(JarredHeadBlockEntity head, double now) {
        double bell = head.since(JarredHeadBlockEntity.EV_BELL, now), wince = head.since(JarredHeadBlockEntity.EV_WINCE, now),
                ram = head.since(JarredHeadBlockEntity.EV_RAM, now), admire = head.since(JarredHeadBlockEntity.EV_ADMIRE, now);
        int inspect = Math.max((int) ADMIRE_IN, head.param(JarredHeadBlockEntity.EV_ADMIRE));
        boolean any = live(bell, BELL_T) || live(wince, WINCE_T) || live(ram, RAM_T) || (admire > 0 && admire < inspect + ADMIRE_OUT);
        if (!any) return Offsets.REST;
        float pitch = 0, roll = 0, forward = 0;
        if (live(bell, BELL_T)) pitch -= BELL_UP * curve(bell, BELL_T, BELL_V, BELL_E);
        if (live(wince, WINCE_T)) {
            float w = curve(wince, WINCE_T, WINCE_V, WINCE_E);
            pitch += WINCE_PITCH * w;
            roll += WINCE_ROLL * w;
            forward -= WINCE_BACK * w;
        }
        if (live(ram, RAM_T)) {
            forward += curve(ram, RAM_T, RAM_FWD, RAM_E);
            pitch += curve(ram, RAM_T, RAM_PITCH, RAM_E);
        }
        if (admire > 0 && admire < inspect + ADMIRE_OUT) {
            float a;
            if (admire < ADMIRE_IN) a = ease("easeInOutSine", (float) (admire / ADMIRE_IN));
            else if (admire < inspect) a = 1;
            else a = 1 - ease("easeInOutSine", (float) ((admire - inspect) / ADMIRE_OUT));
            pitch += ADMIRE_PITCH * a;
            roll += ADMIRE_ROLL * a;
        }
        return new Offsets(pitch, 0, roll, forward, 0);
    }

    private static boolean live(double t, float[] keys) {
        return t > 0 && t < keys[keys.length - 1];
    }

    /** The keyed curve at {@code t} (easing on the arriving key). */
    static float curve(double t, float[] times, float[] values, String[] eases) {
        for (int i = 1; i < times.length; i++) {
            if (t > times[i]) continue;
            float span = times[i] - times[i - 1];
            float u = span <= 0 ? 1 : (float) ((t - times[i - 1]) / span);
            return values[i - 1] + (values[i] - values[i - 1]) * ease(eases[i], u);
        }
        return values[values.length - 1];
    }

    private static float ease(String name, float u) {
        Float e = PlinthBowl.ease(name, u);
        return e == null ? u : e;
    }

    /** For the notes: each curve sampled every half tick. */
    public static String describe() {
        StringBuilder s = new StringBuilder();
        s.append("M1 bell pitch:");
        for (float t = 0; t <= 10; t += 0.5f) s.append(String.format(java.util.Locale.ROOT, " %.1f", -BELL_UP * curve(t, BELL_T, BELL_V, BELL_E)));
        s.append("\nM3 wince pitch:");
        for (float t = 0; t <= 4; t += 0.5f) s.append(String.format(java.util.Locale.ROOT, " %.1f", WINCE_PITCH * curve(t, WINCE_T, WINCE_V, WINCE_E)));
        s.append("\nM4 ram forward:");
        for (float t = 0; t <= 9; t += 0.5f) s.append(String.format(java.util.Locale.ROOT, " %.2f", curve(t, RAM_T, RAM_FWD, RAM_E)));
        return s.toString();
    }
}
