package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rehabilitated: a villager gives the carrier the cured-zombie discount when it opens its trades. Added at the end of
 * the price update, not to the reputation itself, which also decides whom the village's golems defend.
 */
@Mixin(Villager.class)
public abstract class RehabilitatedPricesMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void headinajar$rehabilitated(Player player, CallbackInfo ci) {
        HostileGenes.rehabilitatedPrices((Villager) (Object) this, player);
    }
}
