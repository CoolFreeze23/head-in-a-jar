package com.coolfreeze23.headinajar.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client loops that follow a synced value. A caller asks every tick with {@link #want(Object, SoundEvent, SoundSource,
 * Vec3, float, float)}: a key (the thing making the sound), where it is, and the volume and pitch it should be at now.
 * Each loop is one tickable instance, created once, that starts from silence and eases toward its targets (volume by
 * 0.2 a tick, pitch at most 0.05 a tick), and fades out to 0.01 before it stops when its key stops asking. Identical
 * sounds within 16 blocks of each other are grouped into one instance at their mean position; at most six loops are
 * audible, the loudest-and-nearest first; the rest ease out.
 */
public final class SoundLoops {
    public static final int MAX_AUDIBLE = 6;

    private record Want(SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch) {
    }

    private static final Map<Object, Want> WANTED = new HashMap<>();
    private static final List<Loop> PLAYING = new ArrayList<>();

    /** Call every client tick while the thing should be heard. */
    public static void want(Object key, SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch) {
        WANTED.put(key, new Want(sound, source, at, Mth.clamp(volume, 0, 1), Mth.clamp(pitch, 0.5f, 2f)));
    }

    /** Groups this tick's wants, assigns them to loops, then forgets them (so a key that stops asking fades out). */
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.clear();
            WANTED.clear();
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        // group identical sounds that are near each other
        List<Group> groups = new ArrayList<>();
        for (Want w : WANTED.values()) {
            Group into = null;
            for (Group g : groups) {
                if (g.sound == w.sound && g.mean().distanceToSqr(w.at) < 16 * 16) {
                    into = g;
                    break;
                }
            }
            if (into == null) groups.add(into = new Group(w.sound, w.source));
            into.add(w);
        }
        groups.sort((x, y) -> Double.compare(y.priority(ear), x.priority(ear)));
        List<Loop> used = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            boolean audible = i < MAX_AUDIBLE;
            Loop loop = null;
            for (Loop l : PLAYING) {
                if (!used.contains(l) && l.sound == g.sound && !l.isStopped() && l.position().distanceToSqr(g.mean()) < 24 * 24) {
                    loop = l;
                    break;
                }
            }
            if (loop == null) {
                if (!audible) continue;
                loop = new Loop(g.sound, g.source, g.mean());
                PLAYING.add(loop);
                mc.getSoundManager().play(loop);
            }
            used.add(loop);
            loop.target(g.mean(), audible ? g.volume() : 0, g.pitch());
        }
        for (Loop l : PLAYING) {
            if (!used.contains(l)) {
                l.target(l.position(), 0, l.pitchTarget);
                // a loop the sound engine never ticks (sound off, no free channel) is let go here instead
                if (++l.unused > 20) l.end();
            } else {
                l.unused = 0;
            }
        }
        PLAYING.removeIf(Loop::isStopped);
        WANTED.clear();
    }

    /** How many loops are playing now (for the harness and tests). */
    public static int playing() {
        return PLAYING.size();
    }

    private static final class Group {
        final SoundEvent sound;
        final SoundSource source;
        double x, y, z, volume, pitch;
        int count;

        Group(SoundEvent sound, SoundSource source) {
            this.sound = sound;
            this.source = source;
        }

        void add(Want w) {
            x += w.at.x;
            y += w.at.y;
            z += w.at.z;
            volume = Math.max(volume, w.volume);
            pitch += w.pitch;
            count++;
        }

        Vec3 mean() {
            return new Vec3(x / count, y / count, z / count);
        }

        float volume() {
            // several identical sources are a little louder than one, never more than full volume
            return (float) Math.min(1, volume * Math.min(1.6, 0.8 + 0.2 * count));
        }

        float pitch() {
            return (float) (pitch / count);
        }

        double priority(Vec3 ear) {
            return volume / (1 + mean().distanceTo(ear));
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final SoundEvent sound;
        float volumeTarget, pitchTarget;
        Vec3 at;
        boolean started;
        int unused;

        void end() {
            stop();
        }

        Loop(SoundEvent sound, SoundSource source, Vec3 at) {
            super(sound, source, SoundInstance.createUnseededRandom());
            this.sound = sound;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = 1;
            this.at = at;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            // a fixed offset per instance, so two loops never phase
            this.pitchTarget = 1;
        }

        Vec3 position() {
            return at;
        }

        void target(Vec3 at, float volume, float pitch) {
            this.at = at;
            this.volumeTarget = volume;
            this.pitchTarget = pitch;
            started = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            x = at.x;
            y = at.y;
            z = at.z;
            volume += Mth.clamp((volumeTarget - volume) * 0.2f, -0.2f, 0.2f);
            pitch += Mth.clamp(pitchTarget - pitch, -0.05f, 0.05f);
            if (started && volumeTarget <= 0 && volume <= 0.01f) stop();
        }
    }

    private SoundLoops() {
    }
}
