package com.coolfreeze23.headinajar.feedback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Noggin & Sons cuts are clean: where a head comes off, a white ring, a puff and one spark. Server side. */
public final class Sanitary {
    private Sanitary() {
    }

    public static void puff(ServerLevel level, double x, double y, double z) {
        // a ring that spreads flat from the cut
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            level.sendParticles(ParticleTypes.POOF, x, y, z, 0, Math.cos(a), 0.05, Math.sin(a), 0.09);
        }
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 4, 0.08, 0.05, 0.08, 0.01);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.1, z, 1, 0, 0, 0, 0);
    }
}
