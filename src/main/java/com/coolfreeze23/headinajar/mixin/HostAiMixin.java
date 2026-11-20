package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.host.HeadlessChicken;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A host's own mind is switched off: no goals, no brain. The mod may drive the body itself instead, through
 * {@code mind.MobMotor}: the headless chicken's run (0.7.0's staff brain goes beside it here).
 */
@Mixin(Mob.class)
public abstract class HostAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void headinajar$noAi(CallbackInfo ci) {
        if (!Hosts.isHost((Entity) (Object) this)) return;
        ci.cancel();
        Mob mob = (Mob) (Object) this;
        if (HeadlessChicken.runs(mob)) HeadlessChicken.tick(mob);
        else HeadlessChicken.rest(mob);
    }
}
