package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.fx.LoopMix;
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
 * Vec3, float, float)} (or {@link #inHead} for a sound in the player's own head): a key (the thing making the sound),
 * where it is, and the volume and pitch it should be at now. The mix ({@link LoopMix}) groups identical loops within 16
 * blocks into one at their mean position and lets at most six be heard, the loudest-and-nearest first. Each heard
 * group is one tickable instance, created once, that starts from silence and eases toward its targets (volume by 0.2 a
 * tick, pitch at most 0.05 a tick), with a fixed pitch offset of its own so two never phase; a group no longer wanted
 * or no longer among the six fades out before it stops.
 */
public final class SoundLoops {
    public static final int MAX_AUDIBLE = LoopMix.MAX_AUDIBLE;

    private record Want(SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch, boolean relative) {
    }

    private static final Map<Object, Want> WANTED = new HashMap<>();
    private static final List<Loop> PLAYING = new ArrayList<>();
    private static long made;
    private static int audible;
    /** A signature moment heard nearby pulls the machine loops around it down 6 dB for its length (LoopMix.ducked). */
    private static Vec3 duckAt;
    private static int duckTicks;

    /** Every sound the client starts: a signature stinger (the jingles, a mecha booting, the cut) ducks the machines near it. */
    public static void heard(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        var sound = event.getSound();
        if (sound == null || sound.isRelative()) return;
        int ticks = LoopMix.duckTicks(sound.getLocation().toString());
        if (ticks <= 0) return;
        duckAt = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        duckTicks = Math.max(duckTicks, ticks);
    }

    /** Call every client tick while the thing should be heard. */
    public static void want(Object key, SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch) {
        WANTED.put(key, new Want(sound, source, at, Mth.clamp(volume, 0, 1), Mth.clamp(pitch, 0.5f, 2f), false));
    }

    /** As {@link #want}, for a sound heard in the listener's own head (not placed in the world). */
    public static void inHead(Object key, SoundEvent sound, SoundSource source, float volume, float pitch) {
        WANTED.put(key, new Want(sound, source, Vec3.ZERO, Mth.clamp(volume, 0, 1), Mth.clamp(pitch, 0.5f, 2f), true));
    }

    /** Mixes this tick's wants, gives each heard group its loop, then forgets them (so a key that stops asking fades out). */
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.forEach(Loop::end);
            PLAYING.clear();
            WANTED.clear();
            audible = 0;
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        Map<SoundEvent, SoundSource> sources = new HashMap<>();
        List<LoopMix.Want> wants = new ArrayList<>();
        for (Want w : WANTED.values()) {
            sources.putIfAbsent(w.sound, w.source);
            wants.add(new LoopMix.Want(w.sound, w.at.x, w.at.y, w.at.z, w.volume, w.pitch, w.relative));
        }
        List<Loop> used = new ArrayList<>();
        audible = 0;
        for (LoopMix.Group g : LoopMix.plan(wants, ear.x, ear.y, ear.z)) {
            SoundEvent sound = (SoundEvent) g.sound;
            Vec3 at = new Vec3(g.x(), g.y(), g.z());
            Loop loop = null;
            for (Loop l : PLAYING) {
                if (!used.contains(l) && l.sound == sound && l.relative == g.relative && l.bucket == g.bucket && !l.isStopped()
                        && (g.relative || l.at.distanceToSqr(at) < 24 * 24)) {
                    loop = l;
                    break;
                }
            }
            if (loop == null) {
                if (!g.audible()) continue;
                loop = new Loop(sound, sources.get(sound), at, g.relative, g.bucket, LoopMix.offset(++made));
                PLAYING.add(loop);
                mc.getSoundManager().play(loop);
            }
            used.add(loop);
            if (g.audible()) audible++;
            float duck = duckTicks > 0 && !g.relative && sources.get(sound) == SoundSource.BLOCKS && duckAt.distanceToSqr(at) < 16 * 16 ? LoopMix.DUCK : 1;
            loop.target(at, g.audible() ? g.volume() * duck : 0, g.pitch());
        }
        for (Loop l : PLAYING) {
            if (!used.contains(l)) {
                l.target(l.at, 0, l.pitchTarget);
                // a loop the sound engine never ticks (sound off, no free channel) is let go here instead
                if (++l.unused > 20) l.end();
            } else {
                l.unused = 0;
            }
        }
        PLAYING.removeIf(Loop::isStopped);
        WANTED.clear();
        if (duckTicks > 0) duckTicks--;
    }

    /** How many loop instances are playing now, fading ones included (for the harness and tests). */
    public static int playing() {
        return PLAYING.size();
    }

    /** How many groups were heard this tick (never more than {@link #MAX_AUDIBLE}). */
    public static int audible() {
        return audible;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final SoundEvent sound;
        final boolean relative;
        final int bucket;
        final float offset;
        float volumeTarget, pitchTarget = 1;
        Vec3 at;
        boolean started;
        int unused;

        Loop(SoundEvent sound, SoundSource source, Vec3 at, boolean relative, int bucket, float offset) {
            super(sound, source, SoundInstance.createUnseededRandom());
            this.sound = sound;
            this.relative = relative;
            this.bucket = bucket;
            this.offset = relative ? 1 : offset;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = this.offset;
            this.at = at;
            if (relative) {
                this.attenuation = SoundInstance.Attenuation.NONE;
                this.x = this.y = this.z = 0;
            } else {
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
            }
        }

        @Override
        public boolean isRelative() {
            return relative;
        }

        void end() {
            stop();
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
            if (!relative) {
                x = at.x;
                y = at.y;
                z = at.z;
            }
            // a sound in the head (the link's static) follows at once, so its swell keeps time with the stutter
            float chase = relative ? 0.6f : 0.2f;
            volume += Mth.clamp((volumeTarget - volume) * chase, -chase, chase);
            pitch += Mth.clamp(pitchTarget * offset - pitch, -0.05f, 0.05f);
            if (started && volumeTarget <= 0 && volume <= 0.01f) stop();
        }
    }

    private SoundLoops() {
    }
}
