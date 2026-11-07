package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.JarState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Draws a player in jar form: frame, their own head floating in fluid, then the glass. */
public final class JarRenderer {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/entity/jar.png");
    private static JarModel.Parts jar;
    private static ModelPart head;
    private static ModelPart hat;

    private static void bake() {
        var models = Minecraft.getInstance().getEntityModels();
        jar = JarModel.Parts.of(models.bakeLayer(JarModel.JAR));
        ModelPart root = models.bakeLayer(JarModel.HEAD);
        head = root.getChild("head");
        hat = root.getChild("hat");
    }

    /** Clears the baked parts after a resource reload. */
    public static void reset() {
        jar = null;
    }

    public static void render(AbstractClientPlayer player, JarState state, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (jar == null) bake();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float age = player.tickCount + partialTick;
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);

        jar.frame().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);

        // the head bobs a little in the fluid and looks where the player looks
        float bob = Mth.sin(age * 0.08f) * 0.35f;
        head.y = hat.y = JarModel.NECK_Y + bob;
        head.yRot = hat.yRot = Mth.wrapDegrees(headYaw - bodyYaw) * Mth.DEG_TO_RAD;
        head.xRot = hat.xRot = Mth.clamp(pitch, -35, 35) * Mth.DEG_TO_RAD;
        ResourceLocation skin = player.getSkin().texture();
        head.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay);
        hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay);

        float charge = state.charge() / (float) JarState.maxCharge();
        int fluid = charge > 0 ? 0xFFFFFFFF : 0xFF707070;
        jar.fluid().render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay, fluid);
        jar.glass().render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay);
        pose.popPose();
    }

    /** True when this entity should be drawn as a jar. */
    public static boolean isJar(LivingEntity entity, JarState state) {
        return switch (state.form()) {
            case JAR, SOCKET -> true;
            default -> false;
        };
    }

    private JarRenderer() {
    }
}
