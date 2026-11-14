package com.coolfreeze23.headinajar.fx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The mix of the mod's looping sounds, decided once a tick from what every machine, jar and link wants heard: identical
 * loops near each other are grouped into one (a room of ten Centrifuges is one whine, a little louder, not ten), and at
 * most {@link #MAX_AUDIBLE} groups are heard, the loudest-and-nearest first; the rest are faded out. Pure arithmetic with
 * no client classes, so the client's loop player ({@code client.fx.SoundLoops}) and the tests use the same rule.
 */
public final class LoopMix {
    public static final int MAX_AUDIBLE = 6;
    /** Identical loops within this many blocks of a group's middle join it. */
    public static final double GROUP_RADIUS = 16;
    /** Loops are only grouped with loops at a like pitch (a machine at full speed is not merged with one idling). */
    public static final int PITCH_BUCKETS = 5;
    /** Several identical sources are heard a little louder than one: x(0.8 + 0.2 n), at most x1.6, never above full. */
    public static final double GROUP_GAIN_PER = 0.2, GROUP_GAIN_BASE = 0.8, GROUP_GAIN_MAX = 1.6;

    /**
     * One thing asking to be heard this tick. {@code sound} is anything that identifies the sound (the event);
     * {@code relative} for a sound in the listener's own head (the link's static), which is never grouped by place.
     */
    public record Want(Object sound, double x, double y, double z, float volume, float pitch, boolean relative) {
    }

    /** A group of wants heard as one loop. */
    public static final class Group {
        public final Object sound;
        public final int bucket;
        public final boolean relative;
        private double sx, sy, sz, pitchSum;
        private float loudest;
        private int count;
        private boolean audible;

        Group(Object sound, int bucket, boolean relative) {
            this.sound = sound;
            this.bucket = bucket;
            this.relative = relative;
        }

        void add(Want w) {
            sx += w.x;
            sy += w.y;
            sz += w.z;
            pitchSum += w.pitch;
            loudest = Math.max(loudest, w.volume);
            count++;
        }

        public double x() {
            return sx / count;
        }

        public double y() {
            return sy / count;
        }

        public double z() {
            return sz / count;
        }

        public int count() {
            return count;
        }

        public boolean audible() {
            return audible;
        }

        /** The group's volume: its loudest member's, raised a little by its numbers, never above full. */
        public float volume() {
            return (float) Math.min(1, loudest * Math.min(GROUP_GAIN_MAX, GROUP_GAIN_BASE + GROUP_GAIN_PER * count));
        }

        /** The members' mean pitch. */
        public float pitch() {
            return (float) (pitchSum / count);
        }

        double distanceTo(double x, double y, double z) {
            if (relative) return 0;
            double dx = x() - x, dy = y() - y, dz = z() - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** Loudness times nearness: what decides which six are heard. */
        public double priority(double ex, double ey, double ez) {
            return volume() / (1 + distanceTo(ex, ey, ez));
        }
    }

    /** Which of five pitch bands (0.5 to 2.0) a pitch falls in. */
    public static int bucket(float pitch) {
        int b = (int) Math.floor((pitch - 0.5f) / 1.5f * PITCH_BUCKETS);
        return Math.max(0, Math.min(PITCH_BUCKETS - 1, b));
    }

    /** Groups this tick's wants and marks which groups are heard; the result is sorted, the heard ones first. */
    public static List<Group> plan(Collection<Want> wants, double ex, double ey, double ez) {
        List<Group> groups = new ArrayList<>();
        for (Want w : wants) {
            if (w.volume <= 0) continue;
            int b = bucket(w.pitch);
            Group into = null;
            for (Group g : groups) {
                if (!g.sound.equals(w.sound) || g.bucket != b || g.relative != w.relative) continue;
                double dx = g.x() - w.x, dy = g.y() - w.y, dz = g.z() - w.z;
                if (w.relative || dx * dx + dy * dy + dz * dz < GROUP_RADIUS * GROUP_RADIUS) {
                    into = g;
                    break;
                }
            }
            if (into == null) groups.add(into = new Group(w.sound, b, w.relative));
            into.add(w);
        }
        groups.sort((a, c) -> Double.compare(c.priority(ex, ey, ez), a.priority(ex, ey, ez)));
        for (int i = 0; i < groups.size(); i++) groups.get(i).audible = i < MAX_AUDIBLE;
        return groups;
    }

    /** Machine loops within 16 blocks of a signature stinger play at this share of their volume (-6 dB) while it lasts. */
    public static final float DUCK = 0.5f;

    /** How many ticks a sound ducks the machines near it: the company's jingles, a mecha booting, the chair's cut; else 0. */
    public static int duckTicks(String id) {
        return switch (id) {
            case "headinajar:house.jingle", "headinajar:house.jingle_botch", "headinajar:booth_oopsie", "headinajar:mecha_boot" -> 30;
            case "headinajar:house.jingle_shutdown", "headinajar:chair.cut" -> 20;
            default -> 0;
        };
    }

    /** A fixed pitch offset per loop instance (0.97 to 1.03), so two loops of one sound never phase against each other. */
    public static float offset(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        return 0.97f + 0.06f * ((h >>> 40) & 1023) / 1023f;
    }

    private LoopMix() {
    }
}
