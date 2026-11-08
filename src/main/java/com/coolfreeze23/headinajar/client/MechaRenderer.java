package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * A mecha: the frame model with named bones (body, cockpit, arm_l, arm_r, leg_l, leg_r, back) and each fitted module drawn
 * at its bone from a shared module model. Arms swing while their buttons are held; the frame glows red when hot.
 */
public class MechaRenderer extends MobRenderer<MechaEntity, MechaRenderer.Model> {
    public static final ModelLayerLocation HOPPER = new ModelLayerLocation(HeadInAJar.id("hopper"), "main");
    public static final ModelLayerLocation BULWARK = new ModelLayerLocation(HeadInAJar.id("bulwark"), "main");
    public static final ModelLayerLocation MODULES = new ModelLayerLocation(HeadInAJar.id("mecha_modules"), "main");
    private static final ResourceLocation MODULE_TEXTURE = HeadInAJar.id("textures/entity/mecha_modules.png");
    private final ResourceLocation texture;

    public MechaRenderer(EntityRendererProvider.Context context, MechaFrame frame) {
        super(context, new Model(context.bakeLayer(frame == MechaFrame.BULWARK ? BULWARK : HOPPER)), frame.width * 0.5f);
        this.texture = HeadInAJar.id("textures/entity/" + frame.id() + ".png");
        addLayer(new ModuleLayer(this, context.bakeLayer(MODULES)));
    }

    @Override
    public ResourceLocation getTextureLocation(MechaEntity entity) {
        return texture;
    }

    public static class Model extends EntityModel<MechaEntity> {
        final ModelPart root, body, armL, armR, legL, legR, back, cockpit;

        public Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
            this.armL = root.getChild("arm_l");
            this.armR = root.getChild("arm_r");
            this.legL = root.getChild("leg_l");
            this.legR = root.getChild("leg_r");
            this.back = body.getChild("back");
            this.cockpit = body.getChild("cockpit");
        }

        @Override
        public void setupAnim(MechaEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
            legL.xRot = Mth.cos(limbSwing * 0.6f) * 0.9f * limbSwingAmount;
            legR.xRot = Mth.cos(limbSwing * 0.6f + Mth.PI) * 0.9f * limbSwingAmount;
            int arms = e.armsShown();
            armL.xRot = (arms & 1) != 0 ? -1.4f + Mth.sin(age * 0.8f) * 0.1f : Mth.cos(limbSwing * 0.6f + Mth.PI) * 0.5f * limbSwingAmount;
            armR.xRot = (arms & 2) != 0 ? -1.4f + Mth.sin(age * 0.8f) * 0.1f : Mth.cos(limbSwing * 0.6f) * 0.5f * limbSwingAmount;
            body.y = e.vent() > 0 ? Mth.sin(age * 2f) * 0.3f : 0;
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
            root.render(pose, buffer, light, overlay, color);
        }
    }

    /** Draws each module's shape at the bone of its slot. */
    static class ModuleLayer extends RenderLayer<MechaEntity, Model> {
        private final ModelPart modules;

        ModuleLayer(RenderLayerParent<MechaEntity, Model> parent, ModelPart modules) {
            super(parent);
            this.modules = modules;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, MechaEntity m, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float headYaw, float headPitch) {
            Model model = getParentModel();
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(MODULE_TEXTURE));
            int overlay = OverlayTexture.NO_OVERLAY;
            int backCount = 0, torsoCount = 0;
            for (int i = 0; i < m.frame().totalSlots(); i++) {
                MechaModule mod = m.moduleAt(i);
                if (mod == null) continue;
                String name = mod.name().toLowerCase(Locale.ROOT);
                if (!modules.hasChild(name)) continue;
                ModelPart part = modules.getChild(name);
                pose.pushPose();
                switch (mod.slot) {
                    case ARM -> {
                        boolean left = i == m.frame().firstIndex(MechaModule.Slot.ARM);
                        (left ? model.armL : model.armR).translateAndRotate(pose);
                        pose.translate(0, (m.frame() == MechaFrame.BULWARK ? 18 : 14) / 16f, 0);
                        if (mod == MechaModule.DRILL && (m.armsShown() & (left ? 1 : 2)) != 0) pose.mulPose(com.mojang.math.Axis.YP.rotation(age * 0.9f));
                    }
                    case BACK -> {
                        model.body.translateAndRotate(pose);
                        model.back.translateAndRotate(pose);
                        pose.translate((backCount++ == 0 ? -1 : 1) * 0.25, 0, 0);
                    }
                    case TORSO -> {
                        model.body.translateAndRotate(pose);
                        pose.translate(0, 0.2 + 0.18 * torsoCount++, -0.02);
                    }
                    case SENSOR, CORE -> {
                        model.body.translateAndRotate(pose);
                        model.cockpit.translateAndRotate(pose);
                    }
                    case LEGS -> {
                        model.legL.translateAndRotate(pose);
                        part.render(pose, vc, light, overlay);
                        pose.popPose();
                        pose.pushPose();
                        model.legR.translateAndRotate(pose);
                    }
                }
                part.render(pose, vc, light, overlay);
                pose.popPose();
            }
            // heat glow on the body as it nears its limit
            float hot = m.heat() / m.frame().heatCap;
            if (hot > 0.6f) {
                int a = (int) Mth.clamp((hot - 0.6f) * 2.5f * 160, 0, 160);
                pose.pushPose();
                model.body.translateAndRotate(pose);
                pose.scale(1.02f, 1.02f, 1.02f);
                model.body.getChild("hull").render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(m))), light, overlay,
                        (a << 24) | 0xFF3010);
                pose.popPose();
            }
        }
    }

    // --- frame models (y down, ground at 24) --------------------------------------------------------

    private static void limbs(PartDefinition root, float shoulderY, float shoulderX, int armLen, float hipY, float hipX, int legLen, int armW, int legW) {
        root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(0, 40).addBox(-armW, -2, -armW / 2f, armW, armLen, armW), PartPose.offset(-shoulderX, shoulderY, 0));
        root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 40).mirror().addBox(0, -2, -armW / 2f, armW, armLen, armW), PartPose.offset(shoulderX, shoulderY, 0));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(24, 40).addBox(-legW / 2f, 0, -legW / 2f, legW, legLen, legW), PartPose.offset(-hipX, hipY, 0));
        root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(24, 40).mirror().addBox(-legW / 2f, 0, -legW / 2f, legW, legLen, legW), PartPose.offset(hipX, hipY, 0));
    }

    /** Hopper: 2.5 tall, a slim walker with an open cockpit cage. */
    public static LayerDefinition hopper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -12, -4, 14, 14, 8), PartPose.ZERO);
        body.addOrReplaceChild("cockpit", CubeListBuilder.create().texOffs(44, 0).addBox(-4, -2, -4, 8, 2, 8), PartPose.offset(0, -12, 0));
        body.addOrReplaceChild("back", CubeListBuilder.create(), PartPose.offset(0, -5, 5));
        limbs(root, -10, 7, 16, 2, 4, 22, 4, 5);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Bulwark: 3.1 tall, a broad golem frame. */
    public static LayerDefinition bulwark() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-10, -22, -6, 20, 22, 12), PartPose.ZERO);
        body.addOrReplaceChild("cockpit", CubeListBuilder.create().texOffs(64, 0).addBox(-5, -2, -5, 10, 2, 10), PartPose.offset(0, -22, 0));
        body.addOrReplaceChild("back", CubeListBuilder.create(), PartPose.offset(0, -11, 7));
        limbs(root, -19, 10, 22, 0, 5, 24, 6, 7);
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** One small shape per module, at the origin of its bone. Texture: a 64x64 sheet of colour swatches. */
    public static LayerDefinition modules() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        add(root, "drill", 0, 0, -2, 0, -2, 4, 6, 4);
        add(root, "chainsaw", 16, 0, -1, -1, -5, 2, 10, 6);
        add(root, "hydraulic_fist", 32, 0, -3.5f, 0, -3.5f, 7, 6, 7);
        add(root, "grapple_hook", 0, 12, -1.5f, 0, -1.5f, 3, 5, 3);
        add(root, "tractor_beam", 16, 12, -2, 0, -2, 4, 4, 4);
        add(root, "rivet_gun", 32, 12, -1.5f, 0, -4, 3, 3, 8);
        add(root, "riot_shield", 0, 24, -1, -6, -6, 2, 14, 12);
        add(root, "jump_jets", 28, 24, -2, -3, 0, 4, 7, 4);
        add(root, "rocket_pack", 44, 24, -3, -4, 0, 6, 9, 5);
        add(root, "glider_wings", 0, 44, -12, -2, 0, 24, 2, 1);
        add(root, "cargo_hold", 28, 38, -4, -3, 0, 8, 7, 6);
        add(root, "passenger_pod", 28, 38, -4, -4, 0, 8, 8, 7);
        add(root, "radiator_fins", 50, 40, -3, -4, 0, 6, 8, 1);
        add(root, "battery_rack", 50, 50, -3, -2, 0, 6, 5, 3);
        add(root, "armor_plating", 0, 50, -6, -4, -5, 12, 6, 1);
        add(root, "thorn_plating", 0, 50, -6, -4, -5.5f, 12, 6, 1);
        add(root, "night_optics", 26, 56, -3, -3, -3, 2, 2, 2);
        add(root, "bio_scanner", 26, 56, 1, -3, -3, 2, 2, 2);
        add(root, "galvanic_stack", 36, 56, -1, -4, 1, 2, 3, 2);
        add(root, "combustion_kernel", 36, 56, -1.5f, -5, 1, 3, 4, 3);
        add(root, "hydraulic_legs", 44, 56, -2, 6, -3, 4, 3, 1);
        add(root, "spring_heels", 44, 56, -2, 14, -1, 4, 2, 4);
        add(root, "anchor_clamps", 44, 56, -3, 18, -3, 6, 2, 6);
        add(root, "aqua_fins", 44, 56, -1, 12, 2, 2, 4, 3);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void add(PartDefinition root, String name, int u, int v, float x, float y, float z, float w, float h, float d) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.ZERO);
    }
}
