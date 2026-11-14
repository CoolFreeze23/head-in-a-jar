package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** "With condolences, HR": a folded card, 4 x 3 px, on a corpse's chest, under its folded hands. */
public final class HrCard {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/entity/hr_card.png");
    private static ModelPart card;

    private static ModelPart card() {
        if (card == null) {
            MeshDefinition mesh = new MeshDefinition();
            // in the body part's space: the neck at y = 0, the chest's front face at z = -2
            mesh.getRoot().addOrReplaceChild("card", CubeListBuilder.create().texOffs(0, 0).addBox(-2, 3, -2.1f, 4, 3, 0), PartPose.ZERO);
            card = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        }
        return card;
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, HeadlessBody body) {
        // the card lies still, but flutters a degree in the wind
        pose.pushPose();
        float t = body.tickCount;
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(net.minecraft.util.Mth.sin(t * net.minecraft.util.Mth.TWO_PI / 40f)));
        card().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private HrCard() {
    }
}
