package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** A player model with no head, wearing its owner's skin and armour, with a neck stump on top. */
public class HeadlessBodyRenderer extends LivingEntityRenderer<HeadlessBody, PlayerModel<HeadlessBody>> {
    private static final ResourceLocation STUMP_TEXTURE = HeadInAJar.id("textures/entity/neck_stump.png");
    private final PlayerModel<HeadlessBody> wide;
    private final PlayerModel<HeadlessBody> slim;
    private final ModelPart stump;

    public HeadlessBodyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.45f);
        wide = model;
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        stump = context.bakeLayer(JarModel.STUMP).getChild("stump");
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    private static PlayerSkin skin(HeadlessBody body) {
        if (body.ownerId() == null) return DefaultPlayerSkin.get(net.minecraft.Util.NIL_UUID);
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(body.ownerId());
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(body.ownerId());
    }

    @Override
    public void render(HeadlessBody body, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = skin(body).model() == PlayerSkin.Model.SLIM ? slim : wide;
        model.head.visible = false;
        model.hat.visible = false;
        model.crouching = false;
        super.render(body, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected void setupRotations(HeadlessBody body, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(body, pose, bob, yBodyRot, partialTick, scale);
        if (body.isCorpse()) {
            // a corpse lies on its back
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
            pose.translate(0, -0.9, -0.15);
        }
    }

    @Override
    protected float getBob(HeadlessBody body, float partialTick) {
        return body.isCorpse() ? 0 : super.getBob(body, partialTick);
    }

    @Override
    protected void scale(HeadlessBody body, PoseStack pose, float partialTick) {
        pose.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    protected boolean shouldShowName(HeadlessBody body) {
        return body.hasCustomName() && super.shouldShowName(body);
    }

    @Override
    public ResourceLocation getTextureLocation(HeadlessBody body) {
        return skin(body).texture();
    }

    /** Draws the neck stump; called by the body layer after the model is posed. */
    void renderStump(HeadlessBody body, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        model.body.translateAndRotate(pose);
        if (body.hasCollar()) CollarLayer.collar().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CollarLayer.TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        else stump.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(STUMP_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    {
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, HeadlessBody body, float limbSwing, float limbSwingAmount,
                               float partialTick, float age, float headYaw, float headPitch) {
                renderStump(body, pose, buffers, light);
            }
        });
    }
}
