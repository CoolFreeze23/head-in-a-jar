package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.GentleGenes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mountain Hooves walks on powder snow. Vanilla asks the boots (leather ones), and hooves take no boots, so the gene
 * answers first. Both sides: a player's own client moves it over the snow.
 */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowHoovesMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void headinajar$hooves(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (GentleGenes.walksOnPowderSnow(entity)) cir.setReturnValue(true);
    }
}
