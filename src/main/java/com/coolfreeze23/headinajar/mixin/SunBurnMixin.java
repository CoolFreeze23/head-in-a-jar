package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Staff do not burn in daylight while they wear a Hard Hat or stand within 2 blocks of their own Time Clock (the
 * awning). Zombies, skeletons and phantoms all ask this one method; a phantom asks it whatever it wears.
 */
@Mixin(Mob.class)
public abstract class SunBurnMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void headinajar$hardHat(CallbackInfoReturnable<Boolean> cir) {
        if (com.coolfreeze23.headinajar.staff.work.WorkRules.sheltered((Mob) (Object) this)) cir.setReturnValue(false);
    }
}
