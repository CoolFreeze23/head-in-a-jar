package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Undeath: Instant Damage heals the carrier and Instant Health hurts it, as for a zombie. Instant effects skip every
 * effect event (a splash or a drink applies them at once), so the body's own answer is changed.
 */
@Mixin(LivingEntity.class)
public abstract class UndeathHealMixin {
    @Inject(method = "isInvertedHealAndHarm", at = @At("RETURN"), cancellable = true)
    private void headinajar$undeath(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && HostileGenes.invertsHealAndHarm((LivingEntity) (Object) this)) cir.setReturnValue(true);
    }
}
