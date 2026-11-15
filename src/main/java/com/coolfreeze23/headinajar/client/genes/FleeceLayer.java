package com.coolfreeze23.headinajar.client.genes;

import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneSet;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;

/**
 * Fleece: a grown fleece drawn over its carrier in vanilla's own wool texture (by its resource location; nothing is
 * copied), tinted the fleece's dye colour. A player wears it as the inflated outer-armour shape with no hood; any other
 * body (a host) gets its own model again, a little larger about its middle, as the Custody Shell is drawn. A sheared
 * carrier shows nothing.
 */
public class FleeceLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final ResourceLocation WOOL = ResourceLocation.withDefaultNamespace("textures/block/white_wool.png");
    /** For a humanoid parent (players): the outer-armour model, already inflated, which the fleece is drawn on. */
    private final HumanoidModel<T> coat;

    public FleeceLayer(RenderLayerParent<T, M> parent, HumanoidModel<T> coat) {
        super(parent);
        this.coat = coat;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !entity.hasData(ModRegistry.GENES)) return;
        GeneSet genes = entity.getData(ModRegistry.GENES);
        if (genes.isEmpty() || !genes.has(Gene.FLEECE) || !genes.fleeced()) return;
        int colour = DyeColor.byId(genes.fleeceColour).getTextureDiffuseColor() | 0xFF000000;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WOOL));
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        if (coat != null && getParentModel() instanceof HumanoidModel<T> body) {
            body.copyPropertiesTo(coat);
            coat.setAllVisible(true);
            coat.head.visible = false;
            coat.hat.visible = false;
            coat.renderToBuffer(pose, vc, light, overlay, colour);
            return;
        }
        // model space runs downward from 1.5 blocks above the feet: grow the coat about the body's middle
        float mid = 1.501f - entity.getBbHeight() / 2 / Math.max(0.01f, entity.getScale());
        pose.pushPose();
        pose.translate(0, mid, 0);
        pose.scale(1.07f, 1.07f, 1.07f);
        pose.translate(0, -mid, 0);
        getParentModel().renderToBuffer(pose, vc, light, overlay, colour);
        pose.popPose();
    }
}
