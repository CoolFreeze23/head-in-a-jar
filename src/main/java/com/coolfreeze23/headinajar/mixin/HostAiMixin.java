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
 * {@code mind.MobMotor}: the headless chicken's run. A worker hired with a Staff Lanyard has its own mind switched off
 * the same way and runs the staff brain instead ({@code staff.work.Workforce.tick}).
 */
@Mixin(Mob.class)
public abstract class HostAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void headinajar$noAi(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (com.coolfreeze23.headinajar.staff.work.Workforce.isWorker(mob) && !Hosts.isHost(mob)) {
            ci.cancel();
            com.coolfreeze23.headinajar.staff.work.Workforce.tick(mob);
            return;
        }
        if (!Hosts.isHost((Entity) (Object) this)) return;
        ci.cancel();
        if (HeadlessChicken.runs(mob)) HeadlessChicken.tick(mob);
        else HeadlessChicken.rest(mob);
    }
}
