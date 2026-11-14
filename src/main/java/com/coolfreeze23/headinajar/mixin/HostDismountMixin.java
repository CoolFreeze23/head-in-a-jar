package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.FlierSeat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A jar getting off a flying host is set down on the ground, not let go from the top of the flier's box. */
@Mixin(LivingEntity.class)
public abstract class HostDismountMixin {
    @Inject(method = "dismountVehicle", at = @At("TAIL"))
    private void headinajar$setDown(Entity vehicle, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && vehicle instanceof LivingEntity host) FlierSeat.dismounted(host, player);
    }
}
