package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Host steering: the rider's input, turning and speed, in place of each mob's own riding code. */
@Mixin({LivingEntity.class, AbstractHorse.class, Pig.class, Strider.class, Camel.class})
public abstract class HostRiddenMixin {
    @Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true, require = 0)
    private void headinajar$input(Player player, Vec3 travel, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (Hosts.isHost(self)) cir.setReturnValue(HostControl.riddenInput(self, player));
    }

    @Inject(method = "tickRidden", at = @At("HEAD"), cancellable = true, require = 0)
    private void headinajar$tick(Player player, Vec3 travel, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (Hosts.isHost(self)) {
            HostControl.tickRidden(self, player);
            ci.cancel();
        }
    }

    @Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true, require = 0)
    private void headinajar$speed(Player player, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (Hosts.isHost(self)) cir.setReturnValue(HostControl.riddenSpeed(self, player));
    }
}
