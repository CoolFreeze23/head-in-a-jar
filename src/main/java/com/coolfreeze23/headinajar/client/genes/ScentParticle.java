package com.coolfreeze23.headinajar.client.genes;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Keen Nose's scent: a faint two-frame mote left where an unread creature stood, which lingers a few seconds, drifts up
 * a little and fades. Only the carrier's own client makes them.
 */
public class ScentParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected ScentParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 70 + random.nextInt(30);
        this.gravity = 0;
        this.hasPhysics = false;
        this.xd = (random.nextFloat() - 0.5f) * 0.004f;
        this.yd = 0.004f + random.nextFloat() * 0.004f;
        this.zd = (random.nextFloat() - 0.5f) * 0.004f;
        this.quadSize = 0.06f + random.nextFloat() * 0.03f;
        this.alpha = 0.8f;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
            alpha = 0.8f * (1 - (float) age / lifetime);
        }
    }

    /** Full-bright: a scent is seen by the nose, not the eyes, so it shows at night as by day. */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new ScentParticle(level, x, y, z, sprites);
        }
    }
}
