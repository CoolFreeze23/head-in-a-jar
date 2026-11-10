package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: every key mapping whose click was consumed by someone (vanilla or a mod), for the smoke report. */
@Mixin(KeyMapping.class)
public class KeyMappingObserveMixin {
    @Inject(method = "consumeClick", at = @At("RETURN"))
    private void hij$consumed(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) Observer.consumed(((KeyMapping) (Object) this).getName());
    }
}
