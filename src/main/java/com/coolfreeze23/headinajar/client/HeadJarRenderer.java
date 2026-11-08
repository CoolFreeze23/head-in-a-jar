package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A player's head set down in its jar: it looks around idly and flinches red when hit. */
public class HeadJarRenderer extends EntityRenderer<HeadJarEntity> {
    public HeadJarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3f;
    }

    @Override
    public void render(HeadJarEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - entity.getYRot()));
        float look = Mth.sin(age * 0.02f) * 25 + Mth.sin(age * 0.007f) * 15;
        int overlay = OverlayTexture.pack(0, entity.hurtTicks() > 0 ? 3 : 10);
        JarRenderer.draw(pose, buffers, light, overlay, JarRenderer.skin(entity.ownerId()), look, Mth.sin(age * 0.013f) * 8, age, true, !entity.goggles().isEmpty());
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HeadJarEntity entity) {
        return JarRenderer.TEXTURE;
    }
}
