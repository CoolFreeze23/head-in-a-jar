package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

/**
 * The custody shell: while a creature is held by Telekinesis, its own model is drawn once more, a hair larger, in a
 * scrolling teal energy swirl (the charged creeper's render type). Added to every living renderer; it does nothing
 * (one map lookup) for anything not held. Whatever the model is at this frame (a resource pack's re-animated cow
 * included) is what the shell follows, since it re-renders the parent model as posed.
 */
public class ShellLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/fx/custody_shell.png");

    public ShellLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** The shell's colour at {@code alpha} (additive, so the fade goes into the colour). */
    public static int color(float alpha) {
        float a = Math.min(1, alpha) * 0.85f;
        int rgb = FxColors.SIGNAL;
        return FastColor.ARGB32.color(255, (int) ((rgb >> 16 & 255) * a), (int) ((rgb >> 8 & 255) * a), (int) ((rgb & 255) * a));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float alpha = TetherFx.shell(entity, partialTick);
        if (alpha <= 0.01f) return;
        float scroll = (ageInTicks * 0.01f) % 1;
        var vc = buffers.getBuffer(RenderType.energySwirl(TEXTURE, scroll, scroll));
        pose.pushPose();
        // model space runs downward from 1.5 blocks above the feet: grow the shell about the body's middle
        float mid = 1.501f - entity.getBbHeight() / 2 / Math.max(0.01f, entity.getScale());
        pose.translate(0, mid, 0);
        pose.scale(1.06f, 1.06f, 1.06f);
        pose.translate(0, -mid, 0);
        getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, color(alpha));
        pose.popPose();
    }
}
