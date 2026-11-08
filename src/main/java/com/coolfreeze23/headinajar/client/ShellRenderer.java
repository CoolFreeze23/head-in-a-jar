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

/** The three Utility Shells: boxy robots with a socket where a jar sits. */
public class ShellRenderer extends MobRenderer<ShellEntity, ShellRenderer.Model> {
    public static final ModelLayerLocation LUNK = new ModelLayerLocation(HeadInAJar.id("lunk"), "main");
    public static final ModelLayerLocation ROVER = new ModelLayerLocation(HeadInAJar.id("rover"), "main");
    public static final ModelLayerLocation SPIDER = new ModelLayerLocation(HeadInAJar.id("spider_walker"), "main");
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
                if (e.kind() == ShellEntity.Kind.SPIDER_WALKER) leg.yRot = leg.getInitialPose().yRot + Mth.cos(limbSwing * 0.66f + phase) * 0.4f * limbSwingAmount;
                else leg.xRot = Mth.cos(limbSwing * 0.66f + phase) * 1.0f * limbSwingAmount;
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

    public static LayerDefinition lunk() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -14, -4, 16, 24, 8)
                .texOffs(48, 0).addBox(-3, -16, -3, 6, 2, 6), PartPose.ZERO);
        root.addOrReplaceChild("arm_0", CubeListBuilder.create().texOffs(0, 32).addBox(-4, -2, -3, 4, 22, 6), PartPose.offset(-8, -12, 0));
        root.addOrReplaceChild("arm_1", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(0, -2, -3, 4, 22, 6), PartPose.offset(8, -12, 0));
        root.addOrReplaceChild("leg_0", CubeListBuilder.create().texOffs(20, 32).addBox(-2.5f, 0, -2.5f, 5, 14, 5), PartPose.offset(-4, 10, 0));
        root.addOrReplaceChild("leg_1", CubeListBuilder.create().texOffs(20, 32).mirror().addBox(-2.5f, 0, -2.5f, 5, 14, 5), PartPose.offset(4, 10, 0));
        return LayerDefinition.create(mesh, 64, 64);
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

    public static LayerDefinition spiderWalker() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6, 13, -6, 12, 6, 12)
                .texOffs(0, 18).addBox(-3, 11, -3, 6, 2, 6), PartPose.ZERO);
        for (int i = 0; i < 8; i++) {
            boolean left = i < 4;
            float z = -4.5f + (i % 4) * 3;
            float yaw = (left ? 1 : -1) * (0.6f - (i % 4) * 0.4f);
            root.addOrReplaceChild("leg_" + i, CubeListBuilder.create().texOffs(24, 18).addBox(left ? -14 : 0, -1, -1, 14, 2, 2),
                    PartPose.offsetAndRotation(left ? -5 : 5, 16, z, 0, yaw, (left ? -1 : 1) * 0.6f));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
