package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gold Standard: piglins treat the carrier as wearing gold. Brute Strength: piglins are hostile to the carrier even in
 * gold. Anyone else: vanilla looks at the armour.
 */
@Mixin(PiglinAi.class)
public abstract class PiglinGoldMixin {
    @Inject(method = "isWearingGold", at = @At("HEAD"), cancellable = true)
    private static void headinajar$goldGenes(LivingEntity wearer, CallbackInfoReturnable<Boolean> cir) {
        Boolean gold = HostileGenes.piglinsSeeGold(wearer);
        if (gold != null) cir.setReturnValue(gold);
    }
}
