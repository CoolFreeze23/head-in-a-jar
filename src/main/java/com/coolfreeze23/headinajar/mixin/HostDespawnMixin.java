package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A host body is never despawned, not even a monster on Peaceful; nor is a worker hired with a Staff Lanyard. */
@Mixin(Mob.class)
public abstract class HostDespawnMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void headinajar$keepHosts(CallbackInfo ci) {
        if (Hosts.isHost((Entity) (Object) this) || com.coolfreeze23.headinajar.staff.work.Workforce.isWorker((Entity) (Object) this)) ci.cancel();
    }
}
