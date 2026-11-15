package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.HostileGenes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gilded Rot: a golden sword or axe loses no durability on the undead it hits (the wear after a hit is skipped). */
@Mixin(ItemStack.class)
public abstract class GildedEdgeMixin {
    @Inject(method = "postHurtEnemy", at = @At("HEAD"), cancellable = true)
    private void headinajar$gildedRot(LivingEntity target, Player attacker, CallbackInfo ci) {
        if (HostileGenes.keepsGoldEdge((ItemStack) (Object) this, target, attacker)) ci.cancel();
    }
}
