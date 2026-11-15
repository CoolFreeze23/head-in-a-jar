package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Brute Strength: piglins never barter with the carrier, so they leave alone what it threw. */
@Mixin(PiglinAi.class)
public abstract class PiglinBarterMixin {
    @Inject(method = "wantsToPickup", at = @At("HEAD"), cancellable = true)
    private static void headinajar$bruteStrength(Piglin piglin, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (HostileGenes.spurned(stack)) cir.setReturnValue(false);
    }
}
