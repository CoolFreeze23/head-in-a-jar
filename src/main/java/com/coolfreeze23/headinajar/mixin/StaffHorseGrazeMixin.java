package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Staff horses, donkeys and mules do not stop to graze between stops: a grazing horse cannot move for 50 ticks (it
 * counts as immobile, so its worker's brain and steering do not run), and vanilla starts it one tick in 300 on grass.
 * A worker who is let go grazes again.
 */
@Mixin(AbstractHorse.class)
public abstract class StaffHorseGrazeMixin {
    @Inject(method = "canEatGrass", at = @At("HEAD"), cancellable = true)
    private void headinajar$noGrazingOnShift(CallbackInfoReturnable<Boolean> cir) {
        if (com.coolfreeze23.headinajar.staff.work.Workforce.isWorker((AbstractHorse) (Object) this)) cir.setReturnValue(false);
    }
}
