package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws the docked head in its jar on top of the Cradle's base. */
public class CradleRenderer implements BlockEntityRenderer<LifeSupportCradleBlockEntity> {
    public CradleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LifeSupportCradleBlockEntity cradle, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (cradle.isEmpty() || cradle.getLevel() == null) return;
        float age = cradle.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 5 / 16.0, 0.5);
        pose.scale(0.9f, 0.9f, 0.9f);
        JarRenderer.drawStill(pose, buffers, light, JarRenderer.skin(cradle.owner()), age, !cradle.goggles().isEmpty());
        pose.popPose();
    }
}
