package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.client.fx.FxColors;
import com.coolfreeze23.headinajar.client.fx.FxManager;
import com.coolfreeze23.headinajar.client.fx.FxParticle;
import com.coolfreeze23.headinajar.fx.Fx;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Promotion's client side (0.7.2): the confetti over the candidate's head at the ceremony's tick 85, 60 flecks of
 * teal and gold paper thrown up over the head and fluttering down past it over nearly three seconds, lit by the world
 * (paper is not a light). Under the photosensitive option it is a single grey puff. The pattern comes from the memo's seed, so every client sees the
 * same throw. The cut that follows (the head into the jar, the body kept and shaking) is {@code HostFx}'s.
 */
public final class CeremonyClient {
    /** How many flecks, and how long the whole throw lasts in ticks. */
    public static final int FLECKS = 60, TICKS = 55;
    /** The confetti's colours (palette D): teal glass 2 and 3, brass 4 and 3; teal and gold half and half. */
    static final int[] COLOURS = {0x5AA6A4, 0x86CAC1, 0xF2C650, 0xD99A35};
    /** Flecks thrown this many ticks after the first pop make a second, smaller one. */
    static final int SECOND_POP = 4;

    /** How many confetti memos this client has drawn (the harness reads it). */
    public static int drawn;

    public static void confetti(Fx fx) {
        drawn++;
        Vec3 at = fx.a();
        if (Comfort.photosensitive()) {
            FxParticle p = FxParticle.make(FxParticle.Look.PUFF, at, new Vec3(0, 0.01, 0));
            if (p != null) p.color(FxColors.GREY).size(0.4f).grow(1.6f).life(16).add();
            return;
        }
        RandomSource r = RandomSource.create(fx.seed());
        int n = Comfort.particles(FLECKS);
        for (int i = 0; i < n; i++) {
            int delay = i < n * 2 / 3 ? 0 : SECOND_POP;
            float angle = r.nextFloat() * Mth.TWO_PI;
            double out = 0.04 + r.nextDouble() * 0.09, up = 0.10 + r.nextDouble() * 0.10;
            Vec3 v = new Vec3(Mth.cos(angle) * out, up, Mth.sin(angle) * out);
            int colour = COLOURS[(i + (r.nextBoolean() ? 0 : 2)) % COLOURS.length];
            int frame = r.nextInt(2);
            float size = 0.08f + r.nextFloat() * 0.04f;
            int life = TICKS - delay - r.nextInt(10);
            Runnable throwOne = () -> {
                FxParticle p = FxParticle.make(FxParticle.Look.MOTE, at, v);
                if (p != null) p.color(colour).size(size).frame(frame).gravity(0.4f).drag(0.85f).life(life).lit().add();
            };
            if (delay == 0) throwOne.run();
            else FxManager.later(delay, throwOne);
        }
    }

    private CeremonyClient() {
    }
}
