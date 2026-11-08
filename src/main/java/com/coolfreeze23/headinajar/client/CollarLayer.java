package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** On a linked (headless) player: the Collar Cam on the neck, and their own head in its jar on the hip when carried. */
public class CollarLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/entity/collar_cam.png");
    private static ModelPart collar;

    public CollarLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static ModelPart collar() {
        if (collar == null) collar = Minecraft.getInstance().getEntityModels().bakeLayer(JarModel.COLLAR).getChild("collar");
        return collar;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (state.form() != Form.LINKED) return;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        collar().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        if (state.carryingHead()) {
            // the satchel on the right hip, the jar facing forward
            pose.translate(-5.2 / 16.0, 9.5 / 16.0, 0);
            pose.scale(0.42f, 0.42f, 0.42f);
            pose.mulPose(Axis.XP.rotationDegrees(180));
            pose.translate(0, -0.7, 0);
            JarRenderer.draw(pose, buffers, light, OverlayTexture.NO_OVERLAY, player.getSkin().texture(), 0, 0, age, true, !state.goggles().isEmpty());
        }
        pose.popPose();
    }
}
