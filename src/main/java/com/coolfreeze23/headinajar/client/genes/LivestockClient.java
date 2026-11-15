package com.coolfreeze23.headinajar.client.genes;

import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.genetics.GentleGenes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;

/** The client side of the gentler 0.6.2 genes (Roll Up, Puffer, the fleece, Keen Nose). */
public final class LivestockClient {
    private LivestockClient() {
    }

    /** A gene memo of this stream's kinds (see {@link Fx}). */
    public static void fx(Fx fx) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!(level.getEntity(fx.entityA()) instanceof LivingEntity body)) return;
        switch (fx.kind()) {
            // the curl and the puff: this client's copy, so the body poses, sizes and moves as the server has it
            case Fx.GENE_ROLL_UP -> GentleGenes.mirror(body, Fx.GENE_ROLL_UP, (int) fx.arg());
            case Fx.GENE_PUFFER -> {
                GentleGenes.mirror(body, Fx.GENE_PUFFER, (int) fx.arg());
                // the deflate lets out a few bubbles too (the puff's are the server's)
                if (fx.arg() <= 0) {
                    for (int i = 0; i < 6; i++) {
                        level.addParticle(ParticleTypes.BUBBLE_POP, body.getRandomX(0.6), body.getRandomY(), body.getRandomZ(0.6), 0, 0.02, 0);
                    }
                }
            }
            default -> {
            }
        }
    }
}
