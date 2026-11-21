package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.StaffBadge;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * What a worker wears, drawn on every living creature's renderer: the 1-px cord loop round its neck and rel60's badge
 * in its employer's colour on a hang that sways with its walk and turns, the Hard Hat's 3D model on the head of every
 * shape the armour layer does not draw, and the crate it carries. Where each goes is the species' fit
 * ({@link StaffFit}); a species with none is drawn from the host neck table. The hire drops the loop over the head and
 * Let go pops it off ({@link LanyardMotion}). Nothing is drawn for a creature the server has not said is staff, except
 * a Hard Hat it wears.
 */
public class StaffLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** The worn lanyard's sheet (the art's), and rel60's badge sheet when it is not there (before the art is in). */
    public static final ResourceLocation LANYARD = HeadInAJar.id("textures/entity/staff_lanyard.png");
    public static final String[] HAT_SIZES = {"xs", "s", "m", "l", "xl"};
    public static final ResourceLocation CRATE = HeadInAJar.id("entity/staff_crate");
    /** The sheet's regions (px of 16): the full badge's frame and face, the mini's, the cord's lit and shaded columns. */
    private static final float[] FRAME = {0, 0, 5, 5}, FACE = {6, 0, 9, 2}, MINI_FRAME = {0, 6, 3, 9}, MINI_FACE = {6, 6, 7, 7};
    private static final float CORD_LIT = 14, CORD_SHADE = 15;
    /**
     * A snow golem without its pumpkin (sheared): the size-l hat's brim 3 px (its dome) below the snow head's top
     * (y -8 in the head part), as the art seats every hat; the crate comes down by the 2.5 px the pumpkin stood above it.
     */
    private static final float SNOW_HAT_Y = -5, SNOW_DROP = 2.5f;
    /** Whether the art's sheet is loaded (checked once a resource load). */
    static boolean artSheet = true;

    public StaffLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static ResourceLocation hatModel(String size) {
        return HeadInAJar.id("entity/hard_hat_" + size);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        WorkerLines.Look look = WorkerLines.look(entity);
        boolean hat = entity.getItemBySlot(EquipmentSlot.HEAD).is(WorkRegistry.HARD_HAT.get());
        // a collared host (0.7.1): its hat is on its jar's lid and its badge on the collar (SecondeeLayer draws both)
        boolean collared = com.coolfreeze23.headinajar.client.second.SecondeeClient.collared(entity);
        if (collared) hat = false;
        if (look == null && !hat) return;
        M model = getParentModel();
        StaffFit.Fit fit = StaffFit.of(entity.getType());
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        boolean carrying = look != null && look.has(WorkerNet.CARRYING) && fit != null && fit.crate() != null;
        boolean snowHead = entity instanceof SnowGolem golem && !golem.hasPumpkin();
        pose.pushPose();
        // a model drawn through a transform of its own (the rabbit's 0.6): the fit is drawn in the same space as its parts
        if (fit != null && fit.render() != null) fit.render().apply(pose);
        if (hat) hat(pose, buffers, light, overlay, model, fit, carrying, snowHead);
        if (look != null) {
            if (carrying && fit.crate().frame() != null && !vanillaShown(entity, fit.crate())) {
                pose.pushPose();
                if (StaffFit.enter(pose, model, fit.crate().frame())) {
                    // the snow golem's crate sits on its pumpkin: without one, down onto the snow head
                    if (snowHead) pose.translate(0, SNOW_DROP / 16f, 0);
                    baked(pose, buffers, light, overlay, CRATE);
                }
                pose.popPose();
            }
            if (look.colour >= 0 && StaffBadge.shown() && !collared) lanyard(pose, buffers, light, overlay, entity, model, fit, look, partialTick);
        }
        pose.popPose();
    }

    /**
     * Whether the carry already shows the vanilla way: an enderman holding a block (its barrel, or a block it held before
     * it was hired). Until it holds one, the fit's crate goes where vanilla's carried block would be.
     */
    private static boolean vanillaShown(LivingEntity entity, StaffFit.Crate crate) {
        return "block".equals(crate.vanilla()) && entity instanceof EnderMan man && man.getCarriedBlock() != null;
    }

    // --- the hat and the crate --------------------------------------------------------------------------------------

    private void hat(PoseStack pose, MultiBufferSource buffers, int light, int overlay, M model, StaffFit.Fit fit, boolean carrying,
                     boolean snowHead) {
        if (fit != null && fit.hat() != null) {
            if (fit.hat().armor()) return;
            StaffFit.Frame frame = fit.hat().frame();
            String size = fit.hat().size();
            if (snowHead) {
                // the fit seats an xl hat on the pumpkin; a sheared golem wears the head-sized hat on its snow head
                frame = new StaffFit.Frame(frame.bone(), new float[]{frame.at()[0], SNOW_HAT_Y, frame.at()[2]}, frame.rot());
                size = "l";
            }
            pose.pushPose();
            if (carrying && fit.crate().hatLift() != 0) pose.translate(0, -fit.crate().hatLift() / 16f, 0);
            if (StaffFit.enter(pose, model, frame)) baked(pose, buffers, light, overlay, hatModel(size));
            pose.popPose();
            return;
        }
        // no fit: on the head part's top, if the model says which it is (a humanoid's armour layer draws its own)
        if (model instanceof net.minecraft.client.model.HumanoidModel<?> || !(model instanceof HeadedModel headed)) return;
        pose.pushPose();
        headed.getHead().translateAndRotate(pose);
        pose.translate(0, -8 / 16f, 0);
        baked(pose, buffers, light, overlay, hatModel("l"));
        pose.popPose();
    }

    /** A baked model (the hat, the crate: Java block space, the origin the centre of its underside) at the pose, upright. */
    private static void baked(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ResourceLocation id) {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id));
        pose.pushPose();
        // bone space is y down: turn the model upright (north stays the creature's front) and centre it
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.translate(-0.5f, 0, -0.5f);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, m, 1, 1, 1, light, overlay, ModelData.EMPTY, Sheets.cutoutBlockSheet());
        pose.popPose();
    }

    // --- the lanyard ------------------------------------------------------------------------------------------------

    private void lanyard(PoseStack pose, MultiBufferSource buffers, int light, int overlay, T entity, M model, StaffFit.Fit fit,
                         WorkerLines.Look look, float pt) {
        float sinceHire = WorkerLines.since(look.hiredAt, pt), sincePop = WorkerLines.since(look.poppedAt, pt);
        float[] pop = null;
        if (look.has(WorkerNet.NOTICE)) {
            pop = LanyardMotion.pop(sincePop);
            if (pop == null) return;
        }
        float now = entity.tickCount + pt;
        sway(entity, look, now, sinceHire, pt);
        StaffFit.Lanyard l = fit == null ? null : fit.lanyard();
        pose.pushPose();
        // the hire drop and the off-pop move the whole lanyard in model space, before the bone chain
        float lift = LanyardMotion.hireOffset(sinceHire) + (pop == null ? 0 : pop[0]);
        if (lift != 0 || pop != null) pose.translate(0, -lift / 16f, pop == null ? 0 : -pop[1] / 16f);
        boolean on = l != null && StaffFit.enter(pose, model, l.frame());
        if (!on) {
            l = fallback(entity);
            ring(pose, entity);
        }
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(artSheet ? LANYARD : StaffBadge.TEXTURE));
        int tint = 0xFF000000 | look.colour;
        for (float[] b : l.cord()) box(v, pose.last(), b, tint, light, overlay);
        pose.pushPose();
        pose.translate(l.pivot()[0] / 16f, l.pivot()[1] / 16f, l.pivot()[2] / 16f);
        StaffFit.rotate(pose, l.hangRot());
        float pitch = look.swing[0] * l.sway() + (pop == null ? 0 : pop[2]), roll = look.swing[1] * l.sway();
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        for (float[] b : l.strands()) box(v, pose.last(), b, tint, light, overlay);
        badge(v, pose.last(), l.badgeAt(), l.mini(), tint, light, overlay);
        pose.popPose();
        pose.popPose();
    }

    /** Steps the worker's pendulum to this frame (once a frame, however many times it is drawn). */
    private static void sway(LivingEntity e, WorkerLines.Look look, float now, float sinceHire, float pt) {
        float dt = Float.isNaN(look.swingAt) ? 0 : Mth.clamp(now - look.swingAt, 0, 5);
        look.swingAt = now;
        if (dt <= 0) return;
        // the badge's kick as the drop lands
        if (sinceHire >= LanyardMotion.DROP_TICKS && sinceHire - dt < LanyardMotion.DROP_TICKS) look.swing[2] += LanyardMotion.KICK;
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float rate = Float.isNaN(look.lastYaw) ? 0 : Mth.wrapDegrees(yaw - look.lastYaw) / dt;
        look.lastYaw = yaw;
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        float rad = yaw * Mth.DEG_TO_RAD;
        float forward = (float) (-dx * Mth.sin(rad) + dz * Mth.cos(rad));
        LanyardMotion.step(look.swing, dt, forward, e.walkAnimation.speed(pt), e.walkAnimation.position(pt), rate);
    }

    /** No fit: a horizontal loop round the host neck table's neck, in model space (px from 1.501 blocks up, y down). */
    private static StaffFit.Lanyard fallback(LivingEntity e) {
        float s = Math.max(0.01f, e.getScale());
        float r = Mth.clamp(e.getBbWidth() / s * 16 * 0.3f, 2, 8);
        float[][] cord = {{-r - 1, 0, -r - 1, r + 1, 1, -r}, {-r - 1, 0, r, r + 1, 1, r + 1}, {-r - 1, 0, -r, -r, 1, r}, {r, 0, -r, r + 1, 1, r}};
        return new StaffFit.Lanyard(null, cord, new float[]{0, 1, -r}, new float[3], new float[][]{{-1, 0, -1, 1, 2, 0}},
                new float[]{0, 1, -1.01f}, false, 1);
    }

    private static void ring(PoseStack pose, LivingEntity e) {
        Vec3 n = com.coolfreeze23.headinajar.host.Hosts.neck(e);
        float s = Math.max(0.01f, e.getScale());
        pose.translate(-n.x / s, 1.501 - n.y / s, -n.z / s);
    }

    /** A cord box (px): every face a 1-texel column of the cord strip, lit or shaded, running along the box's long side. */
    private static void box(VertexConsumer v, PoseStack.Pose m, float[] b, int argb, int light, int overlay) {
        float x0 = b[0] / 16, y0 = b[1] / 16, z0 = b[2] / 16, x1 = b[3] / 16, y1 = b[4] / 16, z1 = b[5] / 16;
        float[] cordUv = artSheet ? new float[]{CORD_LIT, CORD_SHADE} : new float[]{0, 0};
        // -y (up), +y (down), -z (north), +z (south), -x, +x
        face(v, m, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, 0, -1, 0, cordUv[0], argb, light, overlay);
        face(v, m, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, 0, 1, 0, cordUv[1], argb, light, overlay);
        face(v, m, new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, 0, 0, -1, cordUv[0], argb, light, overlay);
        face(v, m, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, 0, 0, 1, cordUv[1], argb, light, overlay);
        face(v, m, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, -1, 0, 0, cordUv[0], argb, light, overlay);
        face(v, m, new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, 1, 0, 0, cordUv[1], argb, light, overlay);
    }

    /** One face (corners in order round it): u across the short side within the column, v along the long side (repeating). */
    private static void face(VertexConsumer v, PoseStack.Pose m, float[][] c, float nx, float ny, float nz, float column,
                             int argb, int light, int overlay) {
        float ea = dist(c[0], c[1]), eb = dist(c[1], c[2]);
        boolean alongA = ea >= eb;
        float len = Math.max(ea, eb) * 16 / 16f;
        float u0 = column / 16f, u1 = (column + 1) / 16f;
        for (int i = 0; i < 4; i++) {
            // corners 0,1 / 1,2 / ...: the long side runs from corner 0 to 1 (alongA) or 1 to 2
            boolean far = alongA ? (i == 1 || i == 2) : (i == 2 || i == 3);
            boolean side = alongA ? (i == 2 || i == 3) : (i == 1 || i == 2);
            float uu = side ? u1 : u0, vv = far ? len : 0;
            v.addVertex(m, c[i][0], c[i][1], c[i][2]).setColor(argb).setUv(uu, vv).setOverlay(overlay).setLight(light).setNormal(m, nx, ny, nz);
        }
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The badge: its frame (untinted) and its face in the employer's colour a hair in front, facing -z, top centre at {@code at}. */
    private static void badge(VertexConsumer v, PoseStack.Pose m, float[] at, boolean mini, int tint, int light, int overlay) {
        boolean small = mini && artSheet;
        float w = small ? 3 : 5;
        float[] frame = small ? MINI_FRAME : FRAME, face = small ? MINI_FACE : FACE;
        float fx = small ? 1 : 1, fy = small ? 1 : 2, fw = small ? 1 : 3, fh = small ? 1 : 2;
        float s = (mini && !artSheet) ? 0.6f : 1;
        float x0 = at[0] - w * s / 2, y0 = at[1];
        quad(v, m, x0, y0, x0 + w * s, y0 + w * s, at[2], frame, 0xFFFFFFFF, light, overlay);
        quad(v, m, x0 + fx * s, y0 + fy * s, x0 + (fx + fw) * s, y0 + (fy + fh) * s, at[2] - 0.02f, face, tint, light, overlay);
    }

    /** A quad facing -z in px: u grows with +x (the viewer's right), v with +y (down). */
    private static void quad(VertexConsumer v, PoseStack.Pose m, float x0, float y0, float x1, float y1, float z, float[] uv,
                             int argb, int light, int overlay) {
        float k = 1 / 16f;
        vertex(v, m, x0 * k, y0 * k, z * k, uv[0] * k, uv[1] * k, argb, light, overlay);
        vertex(v, m, x0 * k, y1 * k, z * k, uv[0] * k, uv[3] * k, argb, light, overlay);
        vertex(v, m, x1 * k, y1 * k, z * k, uv[2] * k, uv[3] * k, argb, light, overlay);
        vertex(v, m, x1 * k, y0 * k, z * k, uv[2] * k, uv[1] * k, argb, light, overlay);
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose m, float x, float y, float z, float u, float vv, int argb, int light, int overlay) {
        v.addVertex(m, x, y, z).setColor(argb).setUv(u, vv).setOverlay(overlay).setLight(light).setNormal(m, 0, 0, -1);
    }

    /** A head part a pose can dip: the fit's hat bone's last part, or the model's own head. */
    static ModelPart head(EntityModel<?> model, StaffFit.Fit fit) {
        if (model instanceof HeadedModel h) return h.getHead();
        if (fit != null && fit.hat() != null && fit.hat().frame() != null) {
            ModelPart[] chain = StaffFit.chain(model, fit.hat().frame().bone());
            if (chain != null) return chain[chain.length - 1];
        }
        return null;
    }
}
