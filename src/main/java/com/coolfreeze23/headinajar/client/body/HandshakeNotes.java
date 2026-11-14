package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.client.fx.FxParticle;
import com.coolfreeze23.headinajar.fx.BodySounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Neural Handshake plays the company jingle: each hit is the next note (C5, E5, G5), a perfect handshake ends on
 * C6 with a sparkle, and a miss is a low buzz after which the tune starts again from C5, so the player hears their
 * streak. Played at the jar for this player only.
 */
public final class HandshakeNotes {
    private static int streak;
    /** For tests and the harness: the notes played this handshake (0-3 for C5 to C6, -1 for a miss). */
    public static final java.util.List<Integer> PLAYED = new java.util.ArrayList<>();

    static void play(int note) {
        PLAYED.add(note);
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Vec3 at = mc.player.getEyePosition();
        if (note < 0) mc.level.playLocalSound(at.x, at.y, at.z, BodySounds.MISS.get(), SoundSource.PLAYERS, 1.0f, 1.0f, false);
        else mc.level.playLocalSound(at.x, at.y, at.z, BodySounds.NOTE.get(), SoundSource.PLAYERS, 1.0f, BodySounds.NOTES[note], false);
    }

    public static void start() {
        streak = 0;
        PLAYED.clear();
    }

    public static void press(boolean hit) {
        if (hit) play(Math.min(2, streak++));
        else {
            streak = 0;
            play(-1);
        }
    }

    public static void done(int hits) {
        if (hits < 3) return;
        BodyFx.later(3, () -> {
            play(3);
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            Vec3 at = mc.player.position().add(0, 0.6, 0);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                FxParticle s = FxParticle.make(FxParticle.Look.SPARK, at, new Vec3(Math.cos(a) * 0.06, 0.05, Math.sin(a) * 0.06));
                if (s != null) s.color(com.coolfreeze23.headinajar.client.fx.FxColors.SIGNAL_PALE).size(0.05f).life(12).add();
            }
        });
    }

    private HandshakeNotes() {
    }
}
