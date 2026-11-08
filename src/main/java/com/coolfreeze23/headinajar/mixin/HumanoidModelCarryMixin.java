package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.client.HeadCarry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses the arms of a player carrying their own head. Runs before PlayerModel copies the arms onto the sleeves. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelCarryMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void headinajar$carry(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        HeadCarry.poseArms((HumanoidModel<?>) (Object) this, entity, limbSwing, limbSwingAmount, age);
    }
}
