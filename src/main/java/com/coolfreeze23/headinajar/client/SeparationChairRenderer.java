package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChairTimeline;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.UUID;

/**
 * The Separation Chair's moving parts, drawn by GeckoLib (the parts that never move are the block's baked model), and
 * what the machine handles: the patient's own head on the claw and in the jar, the Life Support Jar standing at the
 * outlet, its lid, the saline, the goggles and the Collar Cam.
 */
public class SeparationChairRenderer extends GeoBlockRenderer<SeparationChairBlockEntity> {
    /** The one model instance (its bones are what the keyframe effects read their positions from). */
    static final ChairModel MODEL = new ChairModel();

    public SeparationChairRenderer(BlockEntityRendererProvider.Context context) {
        super(MODEL);
        addRenderLayer(new Handled(this));
        // the lit lamp caps (separation_chair_glowmask.png)
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }

    /** What the chair being drawn handles this frame. */
    private static View frame = View.NONE;

    @Override
    public void render(SeparationChairBlockEntity chair, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        frame = view(chair);
        super.render(chair, partialTick, pose, buffers, light, overlay);
    }

    static GeoBone bone(String name) {
        return MODEL.getBone(name).orElse(null);
    }

    /** A signature machine: it animates out to 64 blocks. */
    @Override
    public int getViewDistance() {
        return 64;
    }

    /** Every pose: the hood swings two blocks out to the jar and the mast rises above the block. */
    @Override
    public AABB getRenderBoundingBox(SeparationChairBlockEntity chair) {
        BlockPos pos = chair.getBlockPos();
        return new AABB(pos.getX() - 2, pos.getY(), pos.getZ() - 2, pos.getX() + 3, pos.getY() + 2.6, pos.getZ() + 3);
    }

    /** The geo model: tracks the bones the effects need, and keeps the scan sheet out of the patient's own eyes. */
    static class ChairModel extends DefaultedBlockGeoModel<SeparationChairBlockEntity> {
        private boolean tracked;

        ChairModel() {
            super(HeadInAJar.id("separation_chair"));
        }

        @Override
        public void setCustomAnimations(SeparationChairBlockEntity chair, long instanceId, AnimationState<SeparationChairBlockEntity> state) {
            if (!tracked) {
                for (String name : ChairKeyframes.TRACKED) getBone(name).ifPresent(b -> b.setTrackingMatrices(true));
                tracked = true;
            }
            var mc = Minecraft.getInstance();
            boolean ownEyes = mc.player != null && mc.player.getUUID().equals(chair.patient()) && mc.options.getCameraType().isFirstPerson();
            // a zero-thickness sheet passing through the camera reads as a glitch: the overlay draws the line instead
            getBone("scan_plane").ifPresent(b -> b.setHidden(ownEyes));
        }
    }

    // --- what the chair handles --------------------------------------------------------------------------------

    /** One frame's view of the chair: what to draw where. */
    record View(float t, boolean running, int jarKind, UUID owner, boolean headOnClaw, boolean headInJar, float fill,
                boolean goggles, boolean collar, boolean upsideDown) {
        static final View NONE = new View(-1, false, SeparationChairBlockEntity.JAR_NONE, null, false, false, 0, false, false, false);
    }

    static View view(SeparationChairBlockEntity chair) {
        Level level = chair.getLevel();
        if (level == null) return View.NONE;
        float t = ChairClock.elapsed(chair);
        if (t >= 0) {
            // the operation: the jar in use stands at the outlet from the first tick to the hand-over
            if (t >= ChairTimeline.LENGTH - 4 && realJarArrived(chair, chair.patient())) return View.NONE;
            float fill = Mth.clamp((t - ChairTimeline.FILL_START) / (ChairTimeline.FILL_END - ChairTimeline.FILL_START), 0, 1);
            boolean onClaw = t >= ChairTimeline.HEAD_OFF && t < ChairTimeline.IN_JAR;
            return new View(t, true, chair.jarKind(), chair.patient(), onClaw, t >= ChairTimeline.IN_JAR, fill,
                    t >= ChairTimeline.GOGGLES_ON, t >= ChairTimeline.COLLAR_ON, chair.botched() && t >= ChairTimeline.IN_JAR);
        }
        double now = ChairClock.now(level);
        if (chair.finishAt() >= 0 && chair.handoff() == SeparationChairBlockEntity.HANDOFF_ENTITY
                && now - chair.finishAt() < ChairTimeline.HANDOFF_MAX && !realJarArrived(chair, chair.lastPatient())) {
            // the real jar has not arrived yet: keep drawing ours where it will appear, so there is no gap
            return new View(ChairTimeline.LENGTH, false, chair.lastJarKind(), chair.lastPatient(), false, true, 1, true, false, false);
        }
        if (chair.jarKind() != SeparationChairBlockEntity.JAR_NONE
                && SeparationChairBlockEntity.blockedAt(level, chair.getBlockPos(), chair.facing()) == null) {
            // loaded: the jar waits at the outlet, the way a bottle waits in a brewing stand
            return new View(-1, false, chair.jarKind(), null, false, false, 0, false, false, false);
        }
        return View.NONE;
    }

    /** The patient's Head Jar is standing at the outlet (the chair's own jar must then go). */
    private static boolean realJarArrived(SeparationChairBlockEntity chair, UUID owner) {
        Level level = chair.getLevel();
        if (level == null || owner == null) return false;
        Vec3 at = SeparationChairBlockEntity.outlet(chair.getBlockPos(), chair.facing());
        return !level.getEntitiesOfClass(HeadJarEntity.class, new AABB(at, at).inflate(0.5),
                jar -> owner.equals(jar.ownerId())).isEmpty();
    }

    /** Draws the head, the jar and the collar at their bones. */
    static class Handled extends GeoRenderLayer<SeparationChairBlockEntity> {
        Handled(GeoBlockRenderer<SeparationChairBlockEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, SeparationChairBlockEntity chair, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
        }

        @Override
        public void renderForBone(PoseStack pose, SeparationChairBlockEntity chair, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, float partialTick, int light, int overlay) {
            View v = frame;
            switch (bone.getName()) {
                case "head_carry" -> {
                    if (!v.headOnClaw()) return;
                    pose.pushPose();
                    RenderUtil.translateToPivotPoint(pose, bone);
                    // the seated head is the player's 0.9375 scale; it grows to the jar's life size as it drops in
                    float s = 0.9375f + 0.0625f * Mth.clamp((v.t() - ChairTimeline.DROP_START) / (ChairTimeline.IN_JAR - ChairTimeline.DROP_START), 0, 1);
                    Heads.draw(pose, buffers, outletLight(chair), JarRenderer.skin(v.owner()), s, v.goggles(), 0);
                    pose.popPose();
                }
                case "jar_anchor" -> {
                    if (v.jarKind() == SeparationChairBlockEntity.JAR_NONE) return;
                    pose.pushPose();
                    RenderUtil.translateToPivotPoint(pose, bone);
                    GeoBone lid = bone("jar_lid");
                    Jars.draw(pose, buffers, outletLight(chair), v, lid == null ? 0 : lid.getRotZ());
                    pose.popPose();
                }
                case "neck_anchor" -> {
                    if (!v.collar() || !v.running()) return;
                    pose.pushPose();
                    RenderUtil.translateToPivotPoint(pose, bone);
                    pose.scale(-0.9375f, -0.9375f, 0.9375f);
                    CollarLayer.collar().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CollarLayer.TEXTURE)), light, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
                default -> {
                }
            }
        }

        private static int outletLight(SeparationChairBlockEntity chair) {
            Level level = chair.getLevel();
            if (level == null) return 0xF000F0;
            return LevelRenderer.getLightColor(level, BlockPos.containing(SeparationChairBlockEntity.outlet(chair.getBlockPos(), chair.facing()).add(0, 0.3, 0)));
        }
    }

    /** The patient's head, drawn centred on the pose's origin (y up, facing -Z), with its hat layer and goggles. */
    static final class Heads {
        private static ModelPart head, hat, goggles;

        static void draw(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation skin, float scale, boolean withGoggles, float flipDegrees) {
            if (head == null) {
                var models = Minecraft.getInstance().getEntityModels();
                ModelPart root = models.bakeLayer(JarModel.HEAD);
                head = root.getChild("head");
                hat = root.getChild("hat");
                goggles = models.bakeLayer(JarModel.GOGGLES).getChild("goggles");
            }
            pose.pushPose();
            // the botched head went in somersaulted (about the temples)
            if (flipDegrees != 0) pose.mulPose(Axis.XP.rotationDegrees(flipDegrees));
            // from the head's centre down to its neck pivot, then into model space (y down)
            pose.translate(0, -4 * scale / 16f, 0);
            pose.scale(-scale, -scale, scale);
            for (ModelPart part : new ModelPart[]{head, hat, goggles}) {
                part.x = part.y = part.z = 0;
                part.xRot = part.yRot = part.zRot = 0;
                part.xScale = part.yScale = part.zScale = 1;
            }
            head.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
            hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
            if (withGoggles) goggles.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JarRenderer.GOGGLES_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /**
     * The Life Support Jar exactly as {@link JarRenderer} draws it (same boxes, same texture, same sizes), split so the
     * lid can open on its hinge and the saline can rise.
     */
    static final class Jars {
        private static ModelPart body, lid, fluid, glass;

        /** The jar's own boxes (JarModel.jar), with the lid and valve as a part of their own. */
        private static void bake() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("body", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-5.5f, 23.0f, -5.5f, 11, 1, 11)
                    .texOffs(44, 5).addBox(5.0f, 15.0f, -0.5f, 1, 6, 1)
                    .texOffs(52, 5).addBox(-6.0f, 15.0f, -0.5f, 1, 6, 1)
                    .texOffs(44, 14).addBox(-1.0f, 22.0f, 5.0f, 2, 1, 1), PartPose.ZERO);
            root.addOrReplaceChild("lid", CubeListBuilder.create()
                    .texOffs(0, 12).addBox(-5.5f, 11.0f, -5.5f, 11, 1, 11)
                    .texOffs(44, 0).addBox(-1.5f, 9.5f, -1.5f, 3, 2, 3), PartPose.ZERO);
            ModelPart jar = Minecraft.getInstance().getEntityModels().bakeLayer(JarModel.JAR);
            ModelPart split = LayerDefinition.create(mesh, 64, 64).bakeRoot();
            body = split.getChild("body");
            lid = split.getChild("lid");
            fluid = jar.getChild("fluid");
            glass = jar.getChild("glass");
        }

        static void draw(PoseStack pose, MultiBufferSource buffers, int light, View v, float lidRot) {
            if (body == null) bake();
            JarRenderer.Shape shape = JarRenderer.shape(v.jarKind() == SeparationChairBlockEntity.JAR_COMPACT);
            int overlay = OverlayTexture.NO_OVERLAY;
            // the lid's hinge: the jar's far top edge (its lid's left edge, seen from the front), at the lid's underside
            float hingeX = -5.5f * shape.wide(), hingeY = 12f * shape.tall();
            if (lidRot != 0) {
                pose.pushPose();
                pose.translate(hingeX / 16f, hingeY / 16f, 0);
                pose.mulPose(Axis.ZP.rotation(lidRot));
                pose.translate(-hingeX / 16f, -hingeY / 16f, 0);
                model(pose, shape);
                lid.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JarRenderer.TEXTURE)), light, overlay);
                pose.popPose();
                pose.popPose();
            }
            model(pose, shape);
            body.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JarRenderer.TEXTURE)), light, overlay);
            if (lidRot == 0) lid.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JarRenderer.TEXTURE)), light, overlay);
            pose.popPose();
            if (v.headInJar()) {
                // where the jar holds its head: neck at shape.neck() in model units, so the centre 4 px above it
                pose.pushPose();
                pose.translate(0, (24.016f - shape.neck() + 4) / 16f, 0);
                Heads.draw(pose, buffers, light, JarRenderer.skin(v.owner()), 1, v.goggles(), v.upsideDown() ? 180 : 0);
                pose.popPose();
            }
            model(pose, shape);
            if (v.fill() > 0) {
                pose.pushPose();
                pose.translate(0, 23f / 16f, 0);
                pose.scale(1, v.fill(), 1);
                pose.translate(0, -23f / 16f, 0);
                fluid.render(pose, buffers.getBuffer(RenderType.entityTranslucent(JarRenderer.TEXTURE)), light, overlay, 0xFFFFFFFF);
                pose.popPose();
            }
            glass.render(pose, buffers.getBuffer(RenderType.entityTranslucent(JarRenderer.TEXTURE)), light, overlay);
            pose.popPose();
        }

        /** Into the jar's model space (y down, base underside at 24), scaled to the jar's size about its base. */
        private static void model(PoseStack pose, JarRenderer.Shape shape) {
            pose.pushPose();
            pose.scale(-1.0f, -1.0f, 1.0f);
            pose.translate(0.0f, -1.501f, 0.0f);
            pose.translate(0, 1.5f, 0);
            pose.scale(shape.wide(), shape.tall(), shape.wide());
            pose.translate(0, -1.5f, 0);
        }
    }
}
