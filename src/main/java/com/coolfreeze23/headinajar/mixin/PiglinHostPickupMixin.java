package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A piglin host picks nothing up: its Barter pays out at its feet, and a body with its mind off would otherwise pocket
 * the nuggets (and hold up a stray ingot in admiration for ever, since its own brain never finishes admiring).
 */
@Mixin(Piglin.class)
public abstract class PiglinHostPickupMixin {
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void headinajar$hostTakesNothing(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Hosts.isHost((Piglin) (Object) this)) cir.setReturnValue(false);
    }
}
