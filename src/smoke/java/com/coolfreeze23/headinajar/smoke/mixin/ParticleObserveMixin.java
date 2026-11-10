package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: every particle the client creates, by type. */
@Mixin(ParticleEngine.class)
public class ParticleObserveMixin {
    @Inject(method = "createParticle", at = @At("HEAD"))
    private void hij$particle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                              CallbackInfoReturnable<?> callback) {
        Observer.particle(String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType())), x, y, z);
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"))
    private void hij$emitter(Entity entity, ParticleOptions options, CallbackInfo callback) {
        Observer.particle(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()) + "(emitter)", entity.getX(), entity.getY(), entity.getZ());
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void hij$destroy(BlockPos pos, BlockState state, CallbackInfo callback) {
        Observer.particle("block_destroy", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Inject(method = "crack", at = @At("HEAD"))
    private void hij$crack(BlockPos pos, Direction side, CallbackInfo callback) {
        Observer.particle("block_crack", pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
