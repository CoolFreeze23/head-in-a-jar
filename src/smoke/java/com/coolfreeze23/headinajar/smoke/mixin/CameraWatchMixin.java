package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Watch;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The harness's outside camera (Watch): replaces the camera's place and angles once the game has set them up. */
@Mixin(Camera.class)
public abstract class CameraWatchMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void headinajar$watch(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity, boolean detached,
                                  boolean mirror, float partialTick, CallbackInfo ci) {
        double[] w = Watch.camera(partialTick);
        if (w == null) return;
        setRotation((float) w[3], (float) w[4]);
        setPosition(w[0], w[1], w[2]);
    }
}
