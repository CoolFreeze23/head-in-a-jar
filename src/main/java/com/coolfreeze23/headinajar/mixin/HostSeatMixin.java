package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The jar riding a host sits on its neck. */
@Mixin({Entity.class, LivingEntity.class})
public abstract class HostSeatMixin {
    @Inject(method = "getPassengerRidingPosition", at = @At("HEAD"), cancellable = true, require = 0)
    private void headinajar$neck(Entity passenger, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (passenger instanceof Player && self instanceof LivingEntity living && Hosts.isHost(self) && self.getFirstPassenger() == passenger) {
            cir.setReturnValue(HostControl.ridingPosition(living));
        }
    }
}
