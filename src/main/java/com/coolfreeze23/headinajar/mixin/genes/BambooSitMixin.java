package com.coolfreeze23.headinajar.mixin.genes;

import com.coolfreeze23.headinajar.genetics.GentleGenes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bamboo Diet's cost: a carrier sits down to eat, legs forward as when riding (the body is lowered by the client's render event). */
@Mixin(HumanoidModel.class)
public abstract class BambooSitMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void headinajar$sitToEat(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (GentleGenes.sitsToEat(entity)) ((HumanoidModel<?>) (Object) this).riding = true;
    }
}
