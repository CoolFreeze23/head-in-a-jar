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
        // the texture is painted teal itself (a shader pack that drops the vertex colour still shows teal), so the
        // vertex colour is a grey that only fades it
        int a = (int) (Math.min(1, alpha) * 255);
        return FastColor.ARGB32.color(255, a, a, a);
    }

    /**
     * The shell's render type: vanilla's scrolling energy swirl, except while an Iris shader pack is on. BSL (and packs
     * like it) draw the energy swirl in their own warm white whatever its colour, while they keep the colour of the
     * plain emissive type; so under a shader pack the shell is drawn with that one (its streaks then hold still).
     */
    public static RenderType type(float u, float v) {
        return shaderPackOn() ? RenderType.eyes(TEXTURE) : RenderType.energySwirl(TEXTURE, u, v);
    }

    private static java.lang.reflect.Method irisInUse;
    private static Object iris;
    private static boolean irisLooked;

    static boolean shaderPackOn() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                iris = null;
            }
        }
        if (iris == null) return false;
        try {
            return (Boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float alpha = Math.max(TetherFx.shell(entity, partialTick), 0.85f * ArmBeams.shell(entity));
        if (alpha <= 0.01f) return;
        float scroll = (ageInTicks * 0.01f) % 1;
        // model space runs downward from 1.5 blocks above the feet: grow the shell about the body's middle
        float mid = 1.501f - entity.getBbHeight() / 2 / Math.max(0.01f, entity.getScale());
        // two layers of streaks scrolling different ways, so the shell is seen by day without looking for it
        for (int layer = 0; layer < 2; layer++) {
            float u = layer == 0 ? scroll : 0.5f - scroll * 1.3f, v = layer == 0 ? scroll : 0.25f + scroll * 0.7f;
            var vc = buffers.getBuffer(type(u, v));
            pose.pushPose();
            pose.translate(0, mid, 0);
            float grow = layer == 0 ? 1.06f : 1.1f;
            pose.scale(grow, grow, grow);
            pose.translate(0, -mid, 0);
            getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, color(alpha));
            pose.popPose();
        }
    }
}
