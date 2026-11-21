package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.client.staff.StaffPoses;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Staff poses (a biped holding its crate, unpinning its badge, reading a Job Card; an animal sniffing one), set right
 * after the model's own animation for every model class: a zombie's or a piglin's own arm pose comes after
 * {@code HumanoidModel}'s, so the pose cannot go at the end of {@code HumanoidModel.setupAnim}.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererPoseMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void headinajar$staffPose(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        StaffPoses.after(model, entity, partialTick);
    }
}
