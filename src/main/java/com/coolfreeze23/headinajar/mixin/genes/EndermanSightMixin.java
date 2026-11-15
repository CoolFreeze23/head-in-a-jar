package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pearl Digestion: an enderman that can see the carrier takes it as being stared at (it attacks on sight). */
@Mixin(EnderMan.class)
public abstract class EndermanSightMixin {
    @Inject(method = "isLookingAtMe", at = @At("HEAD"), cancellable = true)
    private void headinajar$pearlDigestion(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (HostileGenes.provokesEndermen(player, (EnderMan) (Object) this)) cir.setReturnValue(true);
    }
}
