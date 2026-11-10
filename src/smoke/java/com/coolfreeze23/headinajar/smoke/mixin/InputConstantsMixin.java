package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Harness;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: a key the smoke harness holds reads as down, as GLFW reports a real held key (Shift-click, Ctrl). */
@Mixin(InputConstants.class)
public class InputConstantsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void hij$heldByHarness(long window, int key, CallbackInfoReturnable<Boolean> callback) {
        if (Harness.HELD_KEYS.contains(key)) callback.setReturnValue(true);
    }
}
