package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.client.HeadCarry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Carrying your head in both arms replaces the whole first-person hand pass, not just the vanilla hands, so mods that draw
 * their own first-person arms (Punchy, for one) don't add a third hand over the jar.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCarryMixin {
    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    private void headinajar$carryHands(ItemInHandRenderer hands, float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                       LocalPlayer player, int light, Operation<Void> original) {
        if (!HeadCarry.drawFirstPersonPass(pose, buffers, player, light, partialTick)) {
            original.call(hands, partialTick, pose, buffers, player, light);
        }
    }
}
