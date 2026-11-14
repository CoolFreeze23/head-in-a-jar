package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.client.PilotClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The third-person camera turns about a point on the machine's centre line (about two thirds of its height) instead of
 * the pilot's eye, which on a low machine sits far above it and on some far to the back: the machine then sits in the
 * middle of the view, not at its bottom edge. Vanilla's third person and Shoulder Surfing's both start from this point.
 * The Hopper keeps its pilot's eye (its framing is the reference).
 */
@Mixin(Camera.class)
public abstract class CameraPivotMixin {
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void headinajar$pivot(Camera camera, double x, double y, double z, Operation<Void> original,
                                  @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 0) boolean detached,
                                  @Local(argsOnly = true) float partialTick) {
        Vec3 pivot = detached ? PilotClient.pivot(entity, partialTick) : null;
        if (pivot != null) original.call(camera, pivot.x, pivot.y, pivot.z);
        else original.call(camera, x, y, z);
    }
}
