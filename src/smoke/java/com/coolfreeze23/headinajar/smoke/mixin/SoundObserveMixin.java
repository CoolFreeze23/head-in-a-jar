package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: every sound the client starts, heard or not (the smoke client is muted). */
@Mixin(SoundManager.class)
public class SoundObserveMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void hij$sound(SoundInstance sound, CallbackInfo callback) {
        Observer.sound(sound);
    }

    @Inject(method = "playDelayed", at = @At("HEAD"))
    private void hij$soundDelayed(SoundInstance sound, int delay, CallbackInfo callback) {
        Observer.sound(sound);
    }
}
