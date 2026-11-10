package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: an arm swing seen on the client, the player's own or another living thing's. */
@Mixin(LivingEntity.class)
public class SwingObserveMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void hij$swing(InteractionHand hand, boolean updateSelf, CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) Observer.swing(self, hand);
    }
}
