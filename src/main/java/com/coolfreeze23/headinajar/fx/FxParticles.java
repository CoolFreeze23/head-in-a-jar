package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The kit's six particle sprites, registered as the mod's own particle types (so resource packs can repaint them and
 * {@code /particle headinajar:mote} works). The client draws them tinted by meaning; see {@code client.fx.FxParticle}.
 */
public final class FxParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, HeadInAJar.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE = type("mote");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = type("spark");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BUBBLE = type("bubble");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PUFF = type("puff");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RING = type("ring");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STATIC = type("static");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> type(String name) {
        return TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private FxParticles() {
    }
}
