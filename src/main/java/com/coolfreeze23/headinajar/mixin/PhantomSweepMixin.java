package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A phantom breaks off its swoop for a fed cat head within 16 blocks, as vanilla's own goal does for a live cat. With
 * no fed cat head in the level this is one map read.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomSweepAttackGoal")
public abstract class PhantomSweepMixin {
    @Shadow
    @Final
    Phantom this$0;

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void headinajar$catHead(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Phantom phantom = this$0;
        if (com.coolfreeze23.headinajar.block.DeskIndex.any(phantom.level(), "cat", phantom.position(), com.coolfreeze23.headinajar.block.DeskJobs.CAT_PHANTOMS)) {
            cir.setReturnValue(false);
        }
    }
}
