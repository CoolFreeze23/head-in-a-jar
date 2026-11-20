package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.block.Treadmills;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Beside a Corporate Wellness Treadmill no body steps up more than a block (it would climb over the console end). */
@Mixin(LivingEntity.class)
public abstract class TreadmillStepMixin {
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void headinajar$besideBelt(CallbackInfoReturnable<Float> cir) {
        float step = cir.getReturnValueF();
        if (step > Treadmills.MAX_STEP) cir.setReturnValue(Treadmills.stepBesideBelt((LivingEntity) (Object) this, step));
    }
}
