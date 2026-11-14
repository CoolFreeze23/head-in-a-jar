package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Rover's old code-built model, kept registered for its layer; every Utility Shell is now drawn by {@link RoverRenderer}. */
public class ShellRenderer extends MobRenderer<ShellEntity, ShellRenderer.Model> {
    public static final ModelLayerLocation ROVER = new ModelLayerLocation(HeadInAJar.id("rover"), "main");
    private final ResourceLocation texture;

    public ShellRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String texture, float shadow) {
        super(context, new Model(context.bakeLayer(layer)), shadow);
        this.texture = HeadInAJar.id("textures/entity/" + texture + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(ShellEntity entity) {
        return texture;
    }

    /** Any shell: a root of parts; parts named leg_* swing, arm_* swing opposite, wheel_* spin. */
    public static class Model extends EntityModel<ShellEntity> {
        private final ModelPart root;

        public Model(ModelPart root) {
            this.root = root;
        }

        @Override
        public void setupAnim(ShellEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            int i = 0;
            for (String name : new String[]{"leg_0", "leg_1", "leg_2", "leg_3", "leg_4", "leg_5", "leg_6", "leg_7"}) {
                if (!root.hasChild(name)) continue;
                ModelPart leg = root.getChild(name);
                float phase = (i++ % 2 == 0 ? 0 : Mth.PI);
                leg.xRot = Mth.cos(limbSwing * 0.66f + phase) * 1.0f * limbSwingAmount;
            }
            if (root.hasChild("arm_0")) {
                root.getChild("arm_0").xRot = Mth.cos(limbSwing * 0.66f + Mth.PI) * 0.8f * limbSwingAmount;
                root.getChild("arm_1").xRot = Mth.cos(limbSwing * 0.66f) * 0.8f * limbSwingAmount;
            }
            for (String name : new String[]{"wheel_0", "wheel_1", "wheel_2", "wheel_3"}) {
                if (root.hasChild(name)) root.getChild(name).xRot = limbSwing * 0.8f;
            }
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
            root.render(pose, buffer, light, overlay, color);
        }
    }

    public static LayerDefinition rover() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8, 12, -10, 16, 6, 20)
                .texOffs(0, 26).addBox(-3, 9, -9, 6, 3, 6), PartPose.ZERO);
        int n = 0;
        for (float x : new float[]{-8.5f, 8.5f}) {
            for (float z : new float[]{-7, 7}) {
                root.addOrReplaceChild("wheel_" + n++, CubeListBuilder.create().texOffs(40, 26).addBox(-1.5f, -3, -3, 3, 6, 6),
                        PartPose.offset(x, 21, z));
            }
        }
        return LayerDefinition.create(mesh, 72, 40);
    }
}
