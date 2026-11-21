package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.second.SecondeeClient;
import com.coolfreeze23.headinajar.client.staff.StaffFit;
import com.coolfreeze23.headinajar.client.staff.StaffLayer;
import com.coolfreeze23.headinajar.client.staff.WorkerLines;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A strike as the client draws it (0.7.2). A worker the server marks as on the picket line ({@link WorkerNet#PICKET})
 * carries its sign: held up in the right hand (humanoids, the enderman, the iron golem: that arm put in the fit's pose
 * while it holds it), in folded arms (villagers, illagers, the witch), in a stick arm (the snow golem), or strapped
 * across the back with two cord straps in the employer's colour (everything else; a small card on small creatures). A
 * collared secondee has its sign taped to the side of its jar. The Union Rep ({@link WorkerNet#REP}) wears a red rosette
 * beside its badge. Where each goes is the species' fit (the optional {@code sign} and {@code lanyard.hang.rosette}
 * keys of {@code staff_fit/<species>.json}, read here by a reload listener of its own); a species with neither draws
 * nothing for it, and a sign model or texture that is not there draws nothing either.
 * <p>
 * The chant ({@link Fx#CHANT}, one per striker per chant at its first beat): two pumps in its 12 ticks, eased in and out.
 * Bipeds raise both arms (the sign arm lifts its sign a little), quadrupeds and everything else pitch the head up, a
 * collared secondee bobs its jar 1 px. The Time Clock's red pulse over its lamp's lens is drawn here too
 * ({@code TimeClockRenderer}).
 * <p>
 * Cost: a bit test for every worker drawn; per-entity chant state in a weak map, looked at only while a chant runs.
 */
public final class StrikeClient {
    /** Slogans 0-5: DENTAL NOW, WARMER BROTH, THE JAR IS NOT AN OFFICE, WE ARE NOT LUNCH, FAIR WAGES, MOO. */
    public static final int SLOGANS = 6, MOO = 5;
    /** The baked sign models (written from the art's picket sign geometry): the held sign, the board, the small card. */
    private static final ModelResourceLocation[] SIGN = models("sign"), BOARD = models("board"), CARD = models("card");
    /** The Rep's rosette on the lanyard sheet and the collar's badge sheet (px of 16; untinted). */
    private static final float[] ROSETTE = {9, 0, 12, 5};
    /**
     * The rosette on a collar (StaffBadge's convention: y up, the sheet's left column at the larger x): the creature's
     * left of the badge, a hair in front of the badge's plane (wide ring front 8, compact 6.5).
     */
    private static final float COLLAR_ROSETTE_X0 = -5.5f, COLLAR_ROSETTE_Y0 = -4.5f, COLLAR_ROSETTE_X1 = -2.5f, COLLAR_ROSETTE_Y1 = 0.5f;
    private static final float COLLAR_ROSETTE_Z_WIDE = -8.055f, COLLAR_ROSETTE_Z_COMPACT = -6.555f;
    /** The taped sign: its foot centre on the jar's left side (px, the collar's y-up space), turned to face the side. */
    private static final float TAPED_X_WIDE = -7.0f, TAPED_X_COMPACT = -5.5f, TAPED_Y = 5;

    /** The chant: its length when the server does not say, the arms' ease in and out (ticks), how far each part goes. */
    private static final float CHANT_TICKS = 12, RISE = 2.5f;
    /** A free arm's raised pose (radians: overhead, a little out), the sign arm's and folded arms' lift, the head's pitch up. */
    private static final float ARM_UP_X = -2.9f, ARM_UP_Z = 0.2f, SIGN_LIFT = -0.45f, FOLDED_LIFT = -0.5f, HEAD_UP = -0.45f;
    /** The jar's bob (px up). */
    private static final float JAR_BOB = 1;

    /** The Time Clock's lamp lens (block-model px: from 7, 3, 5 to 9, 5, 6; its front faces north) and the sprite it shows on a fault. */
    private static final ResourceLocation LENS = HeadInAJar.id("block/machine/kit/lamp_fault");
    private static final float LENS_X0 = 7, LENS_Y0 = 3, LENS_Z0 = 5, LENS_X1 = 9, LENS_Y1 = 5, LENS_Z1 = 6, LENS_GROW = 0.03f;
    /** The pulse: one a second (ticks), from this brightness up to full. */
    private static final float PULSE_TICKS = 20, PULSE_LOW = 0.55f;

    /** Where a species carries its sign: held on a part (with the part's pose, or none) or strapped (with its straps' frame and cord). */
    public record Sign(boolean held, ModelResourceLocation[] models, StaffFit.Frame frame, StaffFit.Bone poseBone, float[] poseRot,
                       StaffFit.Frame straps, float[][] cord) {
    }

    /** A species' strike fit: its sign (or null) and the rosette's top-left in the hang frame (or null). */
    public record Fit(Sign sign, float[] rosette) {
    }

    private static volatile Map<EntityType<?>, Fit> fits = Map.of();

    /** A chant as heard: the game time it came, the frame time it started (NaN until first drawn), its length. */
    private static final class Chant {
        long heard;
        float start = Float.NaN, length;
    }

    private static final Map<Entity, Chant> CHANTS = new WeakHashMap<>();

    private StrikeClient() {
    }

    private static ModelResourceLocation[] models(String kind) {
        ModelResourceLocation[] out = new ModelResourceLocation[SLOGANS];
        for (int n = 0; n < SLOGANS; n++) out[n] = ModelResourceLocation.standalone(HeadInAJar.id("entity/picket_" + kind + "_" + n));
        return out;
    }

    /** The fit's reload listener and the eighteen sign models (one line in the client setup). */
    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new Loader()));
        modBus.addListener((ModelEvent.RegisterAdditional e) -> {
            for (ModelResourceLocation[] set : new ModelResourceLocation[][]{SIGN, BOARD, CARD}) for (ModelResourceLocation id : set) e.register(id);
        });
    }

    /** The strike fit of a species, or null. */
    public static Fit of(EntityType<?> type) {
        return fits.get(type);
    }

    /** Which slogan a striker carries: one of the first five by its UUID; a cow or mooshroom one time in three says MOO. */
    public static int slogan(UUID id, EntityType<?> type) {
        int h = id.hashCode();
        if ((type == EntityType.COW || type == EntityType.MOOSHROOM) && Math.floorMod(h >> 3, 3) == 0) return MOO;
        return Math.floorMod(h, 5);
    }

    /** The baked model, or null while it is not there (the model manager's missing model). */
    private static BakedModel baked(ModelResourceLocation id) {
        var models = Minecraft.getInstance().getModelManager();
        BakedModel m = models.getModel(id);
        return m == null || m == models.getMissingModel() ? null : m;
    }

    // --- the chant --------------------------------------------------------------------------------------------------

    /** From {@code FxManager}: a striker's chant began (entityA the striker, arg its length in ticks). */
    public static void chant(Fx fx) {
        Level level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(fx.entityA());
        if (e == null) return;
        Chant c = new Chant();
        c.heard = level.getGameTime();
        c.length = fx.arg() > 0 ? fx.arg() : CHANT_TICKS;
        CHANTS.put(e, c);
    }

    /**
     * Ticks into this entity's chant this frame, or -1. It starts on the first frame it is drawn (so it never jumps in),
     * unless it has already run unseen for more than a tick and a half.
     */
    private static float chantAt(Entity e, float pt) {
        if (CHANTS.isEmpty()) return -1;
        Chant c = CHANTS.get(e);
        if (c == null) return -1;
        float now = e.level().getGameTime() + pt;
        if (Float.isNaN(c.start)) c.start = now - c.heard <= 1.5f ? now : c.heard;
        float t = now - c.start;
        if (t >= c.length) {
            CHANTS.remove(e);
            return -1;
        }
        return Math.max(0, t);
    }

    private static float length(Entity e) {
        Chant c = CHANTS.get(e);
        return c == null ? CHANT_TICKS : c.length;
    }

    /** Two pumps over the chant, each from rest and back (sin squared: no step at either end or between). */
    private static float pumps(float t, float len) {
        float s = Mth.sin(Mth.PI * t / (len / 2));
        return s * s;
    }

    /** The arms' weight: up over the first ticks, two punches at the top, down over the last ticks. */
    private static float armWeight(float t, float len) {
        float rise = RISE * len / CHANT_TICKS;
        float env = smooth(t / rise) * smooth((len - t) / rise);
        return env * (0.6f + 0.4f * pumps(t, len));
    }

    private static float smooth(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    /**
     * From {@code StaffPoses.after} (right after the model's own animation): a held sign's arm put in its pose, and the
     * chant's lifts on top. A collared secondee is left alone (its sign is on its jar, its chant is the jar's bob).
     */
    public static void pose(EntityModel<?> model, LivingEntity entity, WorkerLines.Look look, float pt) {
        boolean picket = look.has(WorkerNet.PICKET);
        if (!picket && CHANTS.isEmpty()) return;
        float t = chantAt(entity, pt);
        if (!picket && t < 0) return;
        if (SecondeeClient.collared(entity)) return;
        ModelPart holding = null;
        Fit fit = picket ? fits.get(entity.getType()) : null;
        if (fit != null && fit.sign() != null && fit.sign().held()) {
            Sign sign = fit.sign();
            ModelPart[] chain = StaffFit.chain(model, sign.frame().bone());
            if (chain != null) holding = chain[chain.length - 1];
            if (sign.poseBone() != null) {
                ModelPart[] arm = StaffFit.chain(model, sign.poseBone());
                if (arm != null) {
                    ModelPart p = arm[arm.length - 1];
                    p.xRot = sign.poseRot()[0] * Mth.DEG_TO_RAD;
                    p.yRot = sign.poseRot()[1] * Mth.DEG_TO_RAD;
                    p.zRot = sign.poseRot()[2] * Mth.DEG_TO_RAD;
                }
            }
        }
        if (t >= 0) chantPose(model, entity, holding, t);
        // a player-shaped model's sleeves were copied from its arms in its own animation: again, after the arms moved
        if (model instanceof PlayerModel<?> p) {
            p.rightSleeve.copyFrom(p.rightArm);
            p.leftSleeve.copyFrom(p.leftArm);
        }
    }

    /** The chant's lifts, {@code t} ticks in: both arms of a biped (folded arms as one), else the head. */
    private static void chantPose(EntityModel<?> model, LivingEntity entity, ModelPart holding, float t) {
        float len = length(entity);
        // bipeds: both arms (the folded arms of a villager or illager as one)
        ModelPart right = null, left = null;
        if (model instanceof HumanoidModel<?> h) {
            right = h.rightArm;
            left = h.leftArm;
        } else if (model instanceof IronGolemModel<?> g) {
            right = g.root().getChild("right_arm");
            left = g.root().getChild("left_arm");
        } else if (model instanceof IllagerModel<?> il) {
            ModelPart folded = il.root().getChild("arms");
            if (folded.visible) right = folded;
            else {
                right = il.root().getChild("right_arm");
                left = il.root().getChild("left_arm");
            }
        } else if (model instanceof VillagerModel<?> v) {
            right = v.root().getChild("arms");
        }
        if (right != null) {
            float w = armWeight(t, len);
            boolean folded = left == null;
            lift(right, holding, folded, -1, w);
            if (left != null) lift(left, holding, false, 1, w);
            return;
        }
        ModelPart head = StaffLayer.head(model, StaffFit.of(entity.getType()));
        if (head != null) head.xRot += HEAD_UP * pumps(t, len);
    }

    /** One arm's chant: the sign arm and folded arms lift a little, a free arm goes up overhead (side -1 right, 1 left). */
    private static void lift(ModelPart arm, ModelPart holding, boolean folded, int side, float w) {
        if (arm == holding) arm.xRot += SIGN_LIFT * w;
        else if (folded) arm.xRot += FOLDED_LIFT * w;
        else {
            arm.xRot = Mth.lerp(w, arm.xRot, ARM_UP_X);
            arm.yRot = Mth.lerp(w, arm.yRot, 0);
            // raised, a negative turn about z takes the right arm out from the body (bone space: +x is the creature's left)
            arm.zRot = Mth.lerp(w, arm.zRot, side * ARM_UP_Z);
        }
    }

    /** A collared secondee's jar bob this frame (px up), 0 when it is not chanting. */
    private static float jarBob(Entity host, float pt) {
        float t = chantAt(host, pt);
        return t < 0 ? 0 : JAR_BOB * pumps(t, length(host));
    }

    // --- on a worker (StaffLayer) -----------------------------------------------------------------------------------

    /**
     * From {@code StaffLayer.render}, in the space the fit is drawn in: a striker's picket sign at the fit's {@code sign}
     * frame (drawn as the crate is), and for a strapped one its straps, cord in the employer's colour.
     */
    public static void sign(PoseStack pose, MultiBufferSource buffers, int light, int overlay, LivingEntity entity, EntityModel<?> model,
                            WorkerLines.Look look) {
        if (!look.has(WorkerNet.PICKET)) return;
        Fit fit = fits.get(entity.getType());
        if (fit == null || fit.sign() == null) return;
        Sign sign = fit.sign();
        ModelPart[] chain = StaffFit.chain(model, sign.frame().bone());
        // a part the model is not drawing (a pillager's folded arms) holds nothing
        if (chain == null || !chain[chain.length - 1].visible) return;
        BakedModel m = baked(sign.models()[slogan(entity.getUUID(), entity.getType())]);
        if (m == null) return;
        pose.pushPose();
        if (StaffFit.enter(pose, model, sign.frame())) {
            pose.pushPose();
            // bone space is y down: the model turned upright (north stays the creature's front) and centred, as the crate
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.translate(-0.5f, 0, -0.5f);
            render(pose, buffers, m, light, overlay);
            pose.popPose();
        }
        pose.popPose();
        if (sign.straps() == null || sign.cord().length == 0) return;
        pose.pushPose();
        if (StaffFit.enter(pose, model, sign.straps())) {
            VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(StaffLayer.artSheet ? StaffLayer.LANYARD : StaffBadge.TEXTURE));
            int tint = 0xFF000000 | look.colour;
            for (float[] b : sign.cord()) StaffLayer.box(v, pose.last(), b, tint, light, overlay);
        }
        pose.popPose();
    }

    /** From {@code StaffLayer}'s lanyard, in the hang frame (after the pivot, its turn and the sway): the Rep's rosette. */
    public static void rosette(VertexConsumer v, PoseStack.Pose m, LivingEntity entity, WorkerLines.Look look, int light, int overlay) {
        if (!look.has(WorkerNet.REP)) return;
        Fit fit = fits.get(entity.getType());
        if (fit == null || fit.rosette() == null) return;
        float[] at = fit.rosette();
        // a flat quad facing -z: u with +x, v with +y (down), as the badge's
        float k = 1 / 16f, x0 = at[0] * k, y0 = at[1] * k, x1 = (at[0] + 3) * k, y1 = (at[1] + 5) * k, z = at[2] * k;
        vertex(v, m, x0, y0, z, ROSETTE[0] * k, ROSETTE[1] * k, light, overlay);
        vertex(v, m, x0, y1, z, ROSETTE[0] * k, ROSETTE[3] * k, light, overlay);
        vertex(v, m, x1, y1, z, ROSETTE[2] * k, ROSETTE[3] * k, light, overlay);
        vertex(v, m, x1, y0, z, ROSETTE[2] * k, ROSETTE[1] * k, light, overlay);
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose m, float x, float y, float z, float u, float vv, int light, int overlay) {
        v.addVertex(m, x, y, z).setColor(0xFFFFFFFF).setUv(u, vv).setOverlay(overlay).setLight(light).setNormal(m, 0, 0, -1);
    }

    private static void render(PoseStack pose, MultiBufferSource buffers, BakedModel m, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, m, 1, 1, 1, light, overlay, ModelData.EMPTY, Sheets.cutoutBlockSheet());
    }

    // --- on a collar (SecondeeLayer) --------------------------------------------------------------------------------

    /**
     * From {@code SecondeeLayer.draw}, after the collar's badge and before the jar, in the collar's space (y up, the front
     * at -z): the Rep's rosette beside the badge (turning with the ring's twist), then the chant's bob, which stays on the
     * pose so the jar and its hat drawn next bob with it, then the sign taped to the jar's side.
     */
    public static void collar(LivingEntity host, SecondeeClient.Seen s, PoseStack pose, MultiBufferSource buffers, int light, boolean compact,
                              float twist, float pt) {
        WorkerLines.Look look = WorkerLines.look(host);
        if (look != null && look.has(WorkerNet.REP) && StaffBadge.shown()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(-twist));
            collarRosette(buffers.getBuffer(RenderType.entityCutoutNoCull(StaffBadge.TEXTURE)), pose.last(),
                    compact ? COLLAR_ROSETTE_Z_COMPACT : COLLAR_ROSETTE_Z_WIDE, light);
            pose.popPose();
        }
        float bob = jarBob(host, pt);
        if (bob != 0) pose.translate(0, bob / 16f, 0);
        if (look == null || !look.has(WorkerNet.PICKET)) return;
        BakedModel m = baked((compact ? CARD : BOARD)[slogan(host.getUUID(), s.head)]);
        if (m == null) return;
        pose.pushPose();
        if (compact) {
            // the Compact Jar turns as a whole past its little turn (JarredHeadRenderer): the card on it turns with it
            float bodyYaw = Mth.rotLerp(pt, host.yBodyRotO, host.yBodyRot);
            float turn = Mth.wrapDegrees(Mth.rotLerp(pt, host.yHeadRotO, host.yHeadRot) - bodyYaw);
            float inside = Mth.clamp(turn, -JarRenderer.COMPACT.turn(), JarRenderer.COMPACT.turn());
            pose.mulPose(Axis.YP.rotationDegrees(-(turn - inside)));
        }
        pose.translate((compact ? TAPED_X_COMPACT : TAPED_X_WIDE) / 16f, TAPED_Y / 16f, 0);
        // upright already (y up here): turned so its front faces out of the jar's left side
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.translate(-0.5f, 0, -0.5f);
        render(pose, buffers, m, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** The rosette on the collar, StaffBadge's way: the sheet's top row at y1, its left column at the larger x. */
    private static void collarRosette(VertexConsumer v, PoseStack.Pose m, float z, int light) {
        float k = 1 / 16f;
        int o = OverlayTexture.NO_OVERLAY;
        vertex(v, m, COLLAR_ROSETTE_X1 * k, COLLAR_ROSETTE_Y1 * k, z * k, ROSETTE[0] * k, ROSETTE[1] * k, light, o);
        vertex(v, m, COLLAR_ROSETTE_X1 * k, COLLAR_ROSETTE_Y0 * k, z * k, ROSETTE[0] * k, ROSETTE[3] * k, light, o);
        vertex(v, m, COLLAR_ROSETTE_X0 * k, COLLAR_ROSETTE_Y0 * k, z * k, ROSETTE[2] * k, ROSETTE[3] * k, light, o);
        vertex(v, m, COLLAR_ROSETTE_X0 * k, COLLAR_ROSETTE_Y1 * k, z * k, ROSETTE[2] * k, ROSETTE[1] * k, light, o);
    }

    // --- the Time Clock (TimeClockRenderer) -------------------------------------------------------------------------

    /**
     * The red pulse over the clock's lamp lens while its site is on strike, in the clock model's space (blocks, the
     * origin the block's bottom centre, its front at -z): the lens's own fault sprite, full bright, its brightness easing
     * up and down once a second, with a glow pass so it reads as a light by night and under shader packs. Under the
     * photosensitive option it is a steady red.
     */
    public static void lens(PoseStack pose, MultiBufferSource buffers, Level level, float pt) {
        float g = 1;
        if (!LinkHud.photosensitive() && level != null) {
            float t = (level.getGameTime() % (long) PULSE_TICKS) + pt;
            g = PULSE_LOW + (1 - PULSE_LOW) * (0.5f - 0.5f * Mth.cos(Mth.TWO_PI * t / PULSE_TICKS));
        }
        int c = (int) (g * 255);
        int argb = 0xFF000000 | c << 16 | c << 8 | c;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LENS);
        PoseStack.Pose m = pose.last();
        lensBox(buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), m, sprite, argb);
        lensBox(buffers.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS)), m, sprite, argb);
    }

    /** The lens's five outer faces, a hair outside it, with the block model's UVs (sprite px of 16). */
    private static void lensBox(VertexConsumer v, PoseStack.Pose m, TextureAtlasSprite s, int argb) {
        float g = LENS_GROW;
        float x0 = (LENS_X0 - g - 8) / 16, x1 = (LENS_X1 + g - 8) / 16, y0 = (LENS_Y0 - g) / 16, y1 = (LENS_Y1 + g) / 16;
        float z0 = (LENS_Z0 - g - 8) / 16, z1 = (LENS_Z1 - 8) / 16;
        // north (the front), east, west, up, down
        lensFace(v, m, s, argb, new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}}, 1, 1, 3, 3, 0, 0, -1);
        lensFace(v, m, s, argb, new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}}, 0, 1, 1, 3, 1, 0, 0);
        lensFace(v, m, s, argb, new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}}, 3, 1, 4, 3, -1, 0, 0);
        lensFace(v, m, s, argb, new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}, {x0, y1, z1}}, 1, 0, 3, 1, 0, 1, 0);
        lensFace(v, m, s, argb, new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y0, z0}}, 3, 0, 5, 1, 0, -1, 0);
    }

    /** One face: corners top-left, bottom-left, bottom-right, top-right as seen from outside, the sprite's [u0, v0, u1, v1). */
    private static void lensFace(VertexConsumer v, PoseStack.Pose m, TextureAtlasSprite s, int argb, float[][] c, float u0, float v0, float u1,
                                 float v1, float nx, float ny, float nz) {
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int i = 0; i < 4; i++) {
            v.addVertex(m, c[i][0], c[i][1], c[i][2]).setColor(argb).setUv(s.getU(uv[i][0] / 16f), s.getV(uv[i][1] / 16f))
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(m, nx, ny, nz);
        }
    }

    // --- loading ----------------------------------------------------------------------------------------------------

    static Fit parse(JsonObject o) {
        Sign sign = null;
        if (o.get("sign") instanceof JsonObject s) {
            ModelResourceLocation[] set = switch (string(s, "model", "")) {
                case "sign" -> SIGN;
                case "board" -> BOARD;
                case "card" -> CARD;
                default -> null;
            };
            if (set != null && s.get("bone") instanceof JsonObject) {
                boolean held = "held".equals(string(s, "mode", ""));
                StaffFit.Bone poseBone = null;
                float[] poseRot = new float[3];
                if (held && s.get("pose") instanceof JsonObject p && p.get("bone") instanceof JsonObject) {
                    poseBone = bone(p);
                    poseRot = vec(p.get("rot"));
                }
                StaffFit.Frame straps = null;
                float[][] cord = new float[0][];
                if (!held && s.get("straps") instanceof JsonObject st && st.get("bone") instanceof JsonObject) {
                    straps = frame(st);
                    cord = boxes(st.get("cord"));
                }
                sign = new Sign(held, set, frame(s), poseBone, poseRot, straps, cord);
            }
        }
        float[] rosette = null;
        if (o.get("lanyard") instanceof JsonObject l && l.get("hang") instanceof JsonObject hang && hang.get("rosette") instanceof JsonObject r) {
            rosette = vec(r.get("at"));
        }
        return sign == null && rosette == null ? null : new Fit(sign, rosette);
    }

    private static StaffFit.Bone bone(JsonObject o) {
        JsonObject b = o.getAsJsonObject("bone");
        return new StaffFit.Bone(b.get("field").getAsString(), string(b, "path", ""));
    }

    private static StaffFit.Frame frame(JsonObject o) {
        return new StaffFit.Frame(bone(o), vec(o.get("at")), vec(o.get("rot")));
    }

    private static String string(JsonObject o, String key, String or) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : or;
    }

    private static float[] vec(JsonElement e) {
        float[] v = new float[3];
        if (e instanceof JsonArray a) for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    private static float[][] boxes(JsonElement e) {
        if (!(e instanceof JsonArray a)) return new float[0][];
        float[][] out = new float[a.size()][6];
        for (int i = 0; i < a.size(); i++) {
            JsonArray b = a.get(i).getAsJsonArray();
            for (int k = 0; k < 6; k++) out[i][k] = b.get(k).getAsFloat();
        }
        return out;
    }

    /** Reads the strike's keys of every {@code staff_fit/**.json} when resources load (StaffFit reads the rest). */
    public static final class Loader extends SimplePreparableReloadListener<Map<EntityType<?>, Fit>> {
        @Override
        protected Map<EntityType<?>, Fit> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<EntityType<?>, Fit> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> file : manager.listResources("staff_fit", id -> id.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = file.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    String path = file.getKey().getPath();
                    String name = path.substring("staff_fit/".length(), path.length() - 5);
                    ResourceLocation id = o.has("entity") ? ResourceLocation.parse(o.get("entity").getAsString())
                            : name.contains("/") ? ResourceLocation.fromNamespaceAndPath(name.substring(0, name.indexOf('/')), name.substring(name.indexOf('/') + 1))
                            : ResourceLocation.withDefaultNamespace(name);
                    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
                    Fit fit = type.isPresent() ? parse(o) : null;
                    if (fit != null) out.put(type.get(), fit);
                } catch (Exception e) {
                    HeadInAJar.LOGGER.warn("Staff fit {}: its strike keys could not be read: {}", file.getKey(), e.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<EntityType<?>, Fit> loaded, ResourceManager manager, ProfilerFiller profiler) {
            fits = Map.copyOf(loaded);
        }
    }
}
