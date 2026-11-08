package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A host mob is controlled by the jar riding it, whatever its own riding rules say. */
@Mixin({Entity.class, Mob.class, AbstractHorse.class, Pig.class, Strider.class})
public abstract class HostControlMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void headinajar$hostPilot(CallbackInfoReturnable<LivingEntity> cir) {
        Entity self = (Entity) (Object) this;
        if (Hosts.isHost(self)) {
            Player pilot = Hosts.pilot(self);
            cir.setReturnValue(pilot);
        }
    }
}
