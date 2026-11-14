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
        // a botched head floats upside down here as it would have on the chair's tray
        JarRenderer.draw(pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, JarRenderer.skin(cradle.owner()), 0, 0, age, true,
                !cradle.goggles().isEmpty(), JarRenderer.WIDE, cradle.upsideDown(),
                // on its Cradle the jar is kept full and charged, and is being topped up
                new JarRenderer.Vitals(1, 1, 0, true, -1, 0));
        pose.popPose();
    }

    /** The jar rises above the cradle's block; cull by the space it is drawn in. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(LifeSupportCradleBlockEntity cradle) {
        return new net.minecraft.world.phys.AABB(cradle.getBlockPos()).expandTowards(0, 0.6, 0);
    }
}
