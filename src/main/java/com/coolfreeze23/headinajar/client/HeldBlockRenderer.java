package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A lifted block, turning slowly. */
public class HeldBlockRenderer extends EntityRenderer<HeldBlockEntity> {
    public HeldBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.4f;
    }

    @Override
    public void render(HeldBlockEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.5, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 2.0f));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.1f) * 6.0f));
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(entity.getBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HeldBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
