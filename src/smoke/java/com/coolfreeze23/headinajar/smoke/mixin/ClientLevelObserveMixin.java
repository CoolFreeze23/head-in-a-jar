package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: marks particles and sounds made by the world's ambient tick (torches, cave dust) so they don't count. */
@Mixin(ClientLevel.class)
public class ClientLevelObserveMixin {
    @Inject(method = "animateTick", at = @At("HEAD"))
    private void hij$ambientStart(int x, int y, int z, CallbackInfo callback) {
        Observer.ambient++;
    }

    @Inject(method = "animateTick", at = @At("RETURN"))
    private void hij$ambientEnd(int x, int y, int z, CallbackInfo callback) {
        Observer.ambient--;
    }
}
