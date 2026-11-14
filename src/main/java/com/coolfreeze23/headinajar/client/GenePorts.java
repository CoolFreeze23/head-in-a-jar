package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.Executive;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * What a frame's Gene Port vials show, drawn after the clips (which never key the serum bones): each port's serum stands
 * as high as its load fills the port, in its serum's colour, dimmed while its gene rests after firing, and lit for a
 * moment when it pulses (the clips' {@code gene:pulse:<n>}, or the gene firing). An empty port shows no serum.
 */
public final class GenePorts {
    /** How long a pulse lights a vial (ticks), and how dim a resting one is. */
    static final double PULSE = 8;
    static final float RESTING = 0.4f;

    private GenePorts() {
    }

    /** Sets each serum bone's height from its port's load (an empty port's is hidden). */
    static void scale(MechaEntity m, HopperRenderer.FrameModel model) {
        if (m.frame().genePorts() <= 0) return;
        for (int n = 0; n < 4; n++) {
            GeoBone bone = model.cached("gene_port_" + n);
            if (bone == null) return;
            float fill = Executive.fill(m, n);
            bone.setHidden(fill <= 0);
            bone.setScaleY(Math.max(0.001f, fill));
        }
        // a rising resting bit is a gene that just fired: its vial pulses
        int resting = m.portsResting(), was = m.view.restingSeen;
        if (resting != was) {
            for (int n = 0; n < 4; n++) if ((resting & ~was & (1 << n)) != 0) pulse(m, n);
            m.view.restingSeen = resting;
        }
    }

    /** Red, green, blue (0-1) and whether it is lit, for port n's serum now. */
    static float[] tint(MechaEntity m, int n, float partialTick) {
        int c = Executive.colour(m, n);
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        double since = m.level().getGameTime() + partialTick - m.view.pulseAt[Math.max(0, Math.min(3, n))];
        float lit = since >= 0 && since < PULSE ? (float) (1 - since / PULSE) : 0;
        float k = Executive.resting(m, n) && lit == 0 ? RESTING : 1;
        // a pulse washes the colour towards white as it lights the vial
        return new float[]{Math.min(1, r * k + (1 - r) * lit * 0.6f), Math.min(1, g * k + (1 - g) * lit * 0.6f), Math.min(1, b * k + (1 - b) * lit * 0.6f), lit};
    }

    /** Port n pulses: lit for a moment, and a few motes of its colour rise from the vial's cap. */
    public static void pulse(MechaEntity m, int n) {
        if (n < 0 || n > 3) return;
        m.view.pulseAt[n] = m.level().getGameTime();
        if (Executive.fill(m, n) <= 0) return;
        int c = Executive.colour(m, n);
        Vec3 at = HopperKeyframes.locator(m, "fx_gene_" + n);
        var dust = new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 0.6f);
        for (int i = 0; i < 3; i++) {
            m.level().addParticle(dust, at.x + (m.getRandom().nextDouble() - 0.5) * 0.1, at.y + 0.05 * i, at.z + (m.getRandom().nextDouble() - 0.5) * 0.1, 0, 0.02, 0);
        }
    }
}
