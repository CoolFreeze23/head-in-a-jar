package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.JarRenderer;
import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.fx.BodySounds;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The jar's life support, readable by anyone who can see it, drawn inside the glass and on the lid only (the jar's
 * outline, size, lid and base are untouched):
 * <ul>
 * <li>the saline stands at its level in whole-pixel steps (the full jar is the fluid exactly as it always was; below that
 * fixed cubes of 6 down to 1 px, each with the fluid's own meniscus row on top; none when dry), tinting the head behind
 * it, never hiding it;</li>
 * <li>a lamp set flush into the lid's top face: steady green when charged, a slow amber pulse under a quarter, dark when
 * flat;</li>
 * <li>the glass is heard as it cracks past a half and a quarter of its integrity, and below a quarter it is heard
 * dripping from its base (heard only, {@link com.coolfreeze23.headinajar.fx.JarDrip}).</li>
 * </ul>
 * Built but switched off (each behind its own constant below): the surface's slosh, rising bubbles, chips and wet streaks
 * on failing glass, and drops with a wet patch under a cracked jar.
 * All of it comes from state the client already has: a player's synced jar state (saline, charge, integrity), or a
 * placed head's synced vitals.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class JarLife {
    private static final ResourceLocation[] CRACKS = {null, HeadInAJar.id("textures/entity/jar_cracks_1.png"),
            HeadInAJar.id("textures/entity/jar_cracks_2.png"), HeadInAJar.id("textures/entity/jar_cracks_3.png")};
    private static final ResourceLocation[] LEAKS = {null, null, HeadInAJar.id("textures/entity/jar_leak_2.png"),
            HeadInAJar.id("textures/entity/jar_leak_3.png")};
    private static final ResourceLocation PUDDLE = HeadInAJar.id("textures/entity/jar_puddle.png");
    public static final ResourceLocation LAMP = HeadInAJar.id("textures/entity/jar_lamp.png");

    /** The full fluid is 6.5 px tall (the jar's own fluid part); the lower levels are whole pixels. */
    private static final int LEVELS = 6;
    private static ModelPart[] levels;
    private static ModelPart bubble, lampGreen, lampAmber, lampOff;

    private static final class Slosh {
        Vec3 lastV = Vec3.ZERO;
        float tx, tz, vx, vz, txO, tzO;
        int lastSaline = -1, risingTicks;
        /** The wet patch under the jar, 0 to 1: spreads while it leaks standing still, fades when it stops. */
        float wet, wetO;
        int drop;
    }

    // the saline's surface tilting and settling with the jar's motion
    private static final boolean SLOSH_SHOWN = false;
    // bubbles rising through the saline, more while it is topped up
    private static final boolean BUBBLES_SHOWN = false;
    // chips on the glass as Jar Integrity falls, and wet streaks running down from them to the base
    private static final boolean CRACKS_SHOWN = false;
    // drops falling from the base of a cracked jar, each with its drip, and the wet patch they leave on the ground
    private static final boolean LEAK_SHOWN = false;

    private static final Map<Integer, Slosh> SLOSH = new HashMap<>();
    private static final Map<Integer, Integer> STAGE = new HashMap<>();
    /** Ticks to each badly cracked jar's next heard drip. */
    private static final Map<Integer, Integer> HEARD_DRIP = new HashMap<>();

    /** 0 (whole) to 3 (a quarter or less), from the share of glass left. */
    public static int stage(float glass) {
        return glass <= 0.25f ? 3 : glass <= 0.5f ? 2 : glass <= 0.75f ? 1 : 0;
    }

    public static int stage(JarState state) {
        return stage(state.integrity() / (float) Math.max(1, state.maxIntegrity()));
    }

    public static ResourceLocation crackTexture(int stage) {
        return CRACKS[Math.max(1, Math.min(3, stage))];
    }

    /** Whether the glass shows marks for this jar (chips, and streaks while there is saline to leak). */
    public static boolean drawsCracks(JarRenderer.Vitals v) {
        return CRACKS_SHOWN && v.cracks() > 0;
    }

    /** Whether a jar at this crack stage, with or without saline, drops anything or wets the ground. */
    public static boolean leaks(int stage, boolean wet) {
        return LEAK_SHOWN && stage >= 1 && wet;
    }

    /** How many bubbles rise in this jar. */
    public static int bubbles(JarRenderer.Vitals v) {
        return BUBBLES_SHOWN && v.saline() > 0 ? 2 + (v.filling() ? 3 : 0) + (v.cracks() >= 3 ? 2 : 0) : 0;
    }

    /** Whether the saline's surface moves with the jar. */
    public static boolean sloshes() {
        return SLOSH_SHOWN;
    }

    /** The saline's height: 0 dry, 1 to 6 whole pixels, 7 the full jar's own fluid. */
    public static int levelPixels(float saline) {
        saline = Mth.clamp(saline, 0, 1);
        return saline <= 0 ? 0 : saline >= 0.95f ? LEVELS + 1 : Mth.clamp(Mth.ceil(saline * 6.5f), 1, LEVELS);
    }

    /** The lid lamp: 0 dark (flat), 1 amber and pulsing (under a quarter), 2 steady green. */
    public static int lampState(float charge) {
        return charge <= 0 ? 0 : charge < 0.25f ? 1 : 2;
    }

    /** The marks on the glass, drawn over the glass part in the jar's scaled model space (outer faces only). */
    public static void glassMarks(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ModelPart glass, JarRenderer.Vitals v) {
        if (!drawsCracks(v)) return;
        glass.render(pose, buffers.getBuffer(RenderType.entityTranslucentCull(crackTexture(v.cracks()))), light, overlay);
        if (v.cracks() >= 2 && v.saline() > 0) {
            glass.render(pose, buffers.getBuffer(RenderType.entityTranslucentCull(leakTexture(v.cracks()))), light, overlay);
        }
    }

    /** The wet streaks down the outside of the glass, from the second stage (only while there is saline to leak). */
    public static ResourceLocation leakTexture(int stage) {
        return LEAKS[Math.max(2, Math.min(3, stage))];
    }

    /** Where the streaks reach the base, in the jar's own space (base at the origin, facing -Z): x, z in blocks. */
    private static final float[][] STREAK_FOOT = {{-0.284f, -0.47f}, {0.284f, -0.47f}};

    /**
     * The wet patch on the ground in front of the chip, in the caller's space (base at the origin, facing -Z), for a
     * leaking jar standing still on the ground.
     */
    public static void puddle(PoseStack pose, MultiBufferSource buffers, int light, JarRenderer.Vitals v, float partialTick) {
        if (!LEAK_SHOWN) return;
        Slosh s = v.sloshId() < 0 ? null : SLOSH.get(v.sloshId());
        if (s == null) return;
        float wet = Mth.lerp(partialTick, s.wetO, s.wet);
        if (wet <= 0.01f) return;
        int a = (int) (wet * 255);
        float cx = -0.26f, cz = -0.5f, hx = 0.12f + 0.1f * wet, hz = 0.08f + 0.08f * wet, y = 0.004f;
        var buffer = buffers.getBuffer(RenderType.entityTranslucent(PUDDLE));
        PoseStack.Pose last = pose.last();
        float[][] corners = {{cx - hx, cz - hz, 0, 0}, {cx - hx, cz + hz, 0, 1}, {cx + hx, cz + hz, 1, 1}, {cx + hx, cz - hz, 1, 0}};
        for (float[] c : corners) {
            buffer.addVertex(last, c[0], y, c[1]).setColor(255, 255, 255, a).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(last, 0, 1, 0);
        }
    }

    /** Whether this jar's saline has been rising in the last few seconds (being topped up). */
    public static boolean filling(int id, int saline) {
        Slosh s = id < 0 ? null : SLOSH.get(id);
        return s != null && s.risingTicks > 0;
    }

    private static void bake() {
        levels = new ModelPart[LEVELS + 1];
        for (int h = 1; h <= LEVELS; h++) {
            MeshDefinition mesh = new MeshDefinition();
            // the same place on the jar's texture as its own fluid: the top row of the sides is the meniscus
            mesh.getRoot().addOrReplaceChild("fluid", CubeListBuilder.create().texOffs(0, 46).addBox(-4.5f, 23 - h, -4.5f, 9, h, 9), PartPose.ZERO);
            levels[h] = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("fluid");
        }
        MeshDefinition mesh = new MeshDefinition();
        // a bubble: one pixel, the fluid's pale meniscus colour on its sides
        mesh.getRoot().addOrReplaceChild("bubble", CubeListBuilder.create().texOffs(9, 54).addBox(-0.5f, -0.5f, -0.5f, 1, 1, 1), PartPose.ZERO);
        bubble = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("bubble");
        lampGreen = lamp(0);
        lampAmber = lamp(4);
        lampOff = lamp(8);
    }

    /** A 2 x 2 px lamp lying on the lid's top face (jar model space: the lid's top at y 11), front right of the valve. */
    private static ModelPart lamp(int v) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(0, v).addBox(2.5f, 10.9f, -4.5f, 2, 0, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("lamp");
    }

    /** Clears the baked parts after a resource reload. */
    public static void reset() {
        levels = null;
    }

    /** The lamp: steady when charged, a slow pulse (about 0.6 Hz, never a flash) under a quarter, dark when flat. */
    public static void lamp(PoseStack pose, MultiBufferSource buffers, int light, JarRenderer.Vitals v, float age) {
        if (levels == null) bake();
        int state = lampState(v.charge());
        if (state == 0) {
            lampOff.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(LAMP)), light, OverlayTexture.NO_OVERLAY);
            return;
        }
        boolean low = state == 1;
        float glow = low ? 0.55f + 0.45f * (0.5f + 0.5f * Mth.sin(age * Mth.TWO_PI / 32f)) : 1;
        int c = (int) (glow * 255);
        int color = 0xFF000000 | c << 16 | c << 8 | c;
        ModelPart lit = low ? lampAmber : lampGreen;
        lit.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(LAMP)), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        // and its glow, so it reads as a light by night and under shader packs
        lit.render(pose, buffers.getBuffer(RenderType.eyes(LAMP)), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
    }

    /**
     * The saline, in the jar's scaled model space: its level, the surface's slosh (turned into the jar's own frame by
     * {@code yaw}, the total yaw the pose was turned by), and the bubbles.
     */
    public static void contents(PoseStack pose, MultiBufferSource buffers, int light, int overlay, int tint, ModelPart full,
                                JarRenderer.Vitals v, float yaw, float age) {
        if (levels == null) bake();
        int h = levelPixels(v.saline());
        if (h == 0) return;
        float top = h > LEVELS ? 16.5f : 23 - h;
        var buffer = buffers.getBuffer(RenderType.entityTranslucent(JarRenderer.TEXTURE));
        pose.pushPose();
        float[] tilt = SLOSH_SHOWN ? tilt(v.sloshId(), yaw, age - (int) age) : new float[]{0, 0};
        if (tilt[0] != 0 || tilt[1] != 0) {
            // the surface tilts: the saline is sheared (each column raised or lowered with its distance from the middle), so
            // it never leaves the glass's sides; the most it lowers an edge stays inside the base plate's thickness
            float kx = (float) Math.tan(tilt[1] * Mth.DEG_TO_RAD), kz = (float) -Math.tan(tilt[0] * Mth.DEG_TO_RAD);
            float over = (Math.abs(kx) + Math.abs(kz)) / 0.22f;
            if (over > 1) {
                kx /= over;
                kz /= over;
            }
            pose.last().pose().mul(new org.joml.Matrix4f().m01(kx).m21(kz));
        }
        (h > LEVELS ? full : levels[h]).render(pose, buffer, light, overlay, tint);
        // bubbles: a few always, more while topped up or when the glass is badly cracked; each a pure function of time
        int n = bubbles(v);
        float depth = 23 - top - 1;
        if (n > 0 && depth >= 1) {
            for (int i = 0; i < n; i++) {
                int seed = i * 73 + Math.max(0, v.sloshId()) * 31;
                float period = 34 + (seed & 15) + (v.filling() ? -12 : 0);
                float u = ((age + (seed & 63) * 3) % period) / period;
                bubble.x = ((seed >> 2) % 7) - 3 + Mth.sin((age + seed) * 0.21f) * 0.5f;
                bubble.z = ((seed >> 5) % 7) - 3;
                bubble.y = 22.5f - u * depth;
                bubble.render(pose, buffer, light, overlay, tint);
            }
        }
        pose.popPose();
    }

    /** The slosh tilt in the jar's model space: degrees about its x and z axes. */
    private static float[] tilt(int id, float yaw, float pt) {
        Slosh s = id < 0 ? null : SLOSH.get(id);
        if (s == null) return new float[]{0, 0};
        float k = Math.max(0.35f, Comfort.motion());
        float wx = Mth.lerp(pt, s.txO, s.tx) * k, wz = Mth.lerp(pt, s.tzO, s.tz) * k;
        if (Math.abs(wx) < 0.05f && Math.abs(wz) < 0.05f) return new float[]{0, 0};
        // world tilt turned into the jar's frame (the pose's yaw, then the model's flip of x and y)
        float c = Mth.cos(yaw * Mth.DEG_TO_RAD), sn = Mth.sin(yaw * Mth.DEG_TO_RAD);
        float lx = wx * c - wz * sn, lz = wx * sn + wz * c;
        return new float[]{-lx, lz};
    }

    /** The motion a jar's saline follows, one entity per jar (a jar player, a carrier, a placed head). */
    private static void follow(Entity e, int saline) {
        Slosh s = SLOSH.computeIfAbsent(e.getId(), k -> new Slosh());
        Vec3 v = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        Vec3 a = v.subtract(s.lastV);
        s.lastV = v;
        if (s.lastSaline >= 0 && saline > s.lastSaline) s.risingTicks = 60;
        else if (s.risingTicks > 0) s.risingTicks--;
        s.lastSaline = saline;
        if (!SLOSH_SHOWN) return;
        // a start, a stop or a knock pushes the surface against the change; a landing kicks it a little either way
        double land = a.y > 0.15 ? a.y : 0;
        s.vx += (float) (a.z * 140 + land * 30 * Mth.sin(e.tickCount));
        s.vz += (float) (-a.x * 140 + land * 30 * Mth.cos(e.tickCount));
        s.txO = s.tx;
        s.tzO = s.tz;
        s.vx += -0.3f * s.tx - 0.22f * s.vx;
        s.vz += -0.3f * s.tz - 0.22f * s.vz;
        s.tx = Mth.clamp(s.tx + s.vx, -12, 12);
        s.tz = Mth.clamp(s.tz + s.vz, -12, 12);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            STAGE.clear();
            SLOSH.clear();
            HEARD_DRIP.clear();
            return;
        }
        if (mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        SLOSH.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Player p : mc.level.players()) {
            JarState s = p.getData(ModRegistry.JAR_STATE);
            if (!s.form().isJarred() && !s.carryingHead()) {
                STAGE.remove(p.getId());
                continue;
            }
            follow(p, s.saline());
            // the jar's own facing: a hosted jar faces the way its host faces; a carried or rolling jar does not drip
            float yaw = p.getVehicle() instanceof net.minecraft.world.entity.LivingEntity host ? host.yBodyRot : p.yBodyRot;
            boolean placed = s.form().isJarred() && !s.tipped() && !(s.wheels() && s.fitted(com.coolfreeze23.headinajar.module.ModuleType.WHEELS));
            glass(mc, p, p.getId(), stage(s), s.saline() > 0, placed, yaw, cam);
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HeadJarEntity head) || e.distanceToSqr(cam) > 48 * 48) continue;
            follow(head, head.vitals() & 127);
            glass(mc, head, head.getId(), stage((head.vitals() >> 14 & 127) / 100f), (head.vitals() & 127) > 0, true, head.getYRot(), cam);
        }
    }

    /**
     * The glass heard as it cracks further (a crack at a half, a longer one at a quarter), and, when switched on, the leak: a drop from the foot of a streak now and then at the first stage,
     * steadily at the second, fast at the third (both streaks), with its drip, for jars in view and near; the wet patch
     * under a jar that stands still. A dry jar has nothing to leak; a carried or rolling jar drops nothing.
     *
     * @param yaw the jar's facing (degrees, as an entity's yaw)
     */
    private static void glass(Minecraft mc, Entity jar, int id, int now, boolean wet, boolean placed, float yaw, Vec3 cam) {
        Integer before = STAGE.put(id, now);
        Vec3 at = jar.position().add(0, 0.5, 0);
        if (before != null && now > before) {
            if (now == 2) mc.level.playLocalSound(at.x, at.y, at.z, BodySounds.CRACK.get(), SoundSource.PLAYERS, 1, 0.95f + mc.level.random.nextFloat() * 0.1f, false);
            if (now == 3) mc.level.playLocalSound(at.x, at.y, at.z, BodySounds.CRACK_LONG.get(), SoundSource.PLAYERS, 1, 0.95f + mc.level.random.nextFloat() * 0.1f, false);
        }
        if (!LEAK_SHOWN) {
            // the drops are not drawn, but a jar under a quarter of its glass is still heard dripping from its base
            if (!com.coolfreeze23.headinajar.fx.JarDrip.heard(now, wet, placed) || jar.distanceToSqr(cam) > 16 * 16) {
                HEARD_DRIP.remove(id);
                return;
            }
            int left = HEARD_DRIP.getOrDefault(id, com.coolfreeze23.headinajar.fx.JarDrip.gap(mc.level.random)) - 1;
            if (left <= 0) {
                left = com.coolfreeze23.headinajar.fx.JarDrip.gap(mc.level.random);
                mc.level.playLocalSound(jar.getX(), jar.getY() + 0.1, jar.getZ(), BodySounds.DRIP.get(), SoundSource.PLAYERS, 1,
                        0.94f + mc.level.random.nextFloat() * 0.12f, false);
            }
            HEARD_DRIP.put(id, left);
            return;
        }
        Slosh s = SLOSH.get(id);
        if (s == null) return;
        boolean leaking = leaks(now, wet) && placed;
        // the patch: spreads over three seconds while a streaked jar leaks standing on the ground, fades over ten once it
        // stops, and is gone as soon as the jar moves (it is where the jar was, not where it is)
        boolean still = jar.onGround() && !jar.isPassenger() && jar.position().distanceToSqr(jar.xo, jar.yo, jar.zo) < 1.0e-6;
        s.wetO = s.wet;
        if (!still || !placed) s.wetO = s.wet = 0;
        else if (leaking && now >= 2) s.wet = Math.min(1, s.wet + 1 / 60f);
        else s.wet = Math.max(0, s.wet - 1 / 200f);
        if (!leaking) {
            s.drop = 0;
            return;
        }
        int every = now >= 3 ? 10 : now == 2 ? 24 : 70;
        if (s.drop <= 0) s.drop = every + mc.level.random.nextInt(every);
        if (--s.drop > 0) return;
        s.drop = every + mc.level.random.nextInt(every);
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 look = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 to = jar.position().subtract(cam);
        if (to.lengthSqr() > 16 * 16 || (to.lengthSqr() > 1 && to.normalize().dot(look) < 0.5)) return;
        // from the foot of a streak (either one at the third stage), turned as the renderer turns the jar
        float[] foot = STREAK_FOOT[now >= 3 && mc.level.random.nextBoolean() ? 1 : 0];
        org.joml.Vector3f off = new org.joml.Vector3f(foot[0], 0, foot[1]).rotate(Axis.YP.rotationDegrees(180.0f - yaw));
        Vec3 base = jar.position().add(off.x(), 0.1, off.z());
        mc.level.playLocalSound(base.x, base.y, base.z, BodySounds.DRIP.get(), SoundSource.PLAYERS, 1, 0.94f + mc.level.random.nextFloat() * 0.12f, false);
        // a small saline drop from the foot of the streak
        com.coolfreeze23.headinajar.client.fx.FxParticle d = com.coolfreeze23.headinajar.client.fx.FxParticle.make(
                com.coolfreeze23.headinajar.client.fx.FxParticle.Look.DROPLET, base, new Vec3(0, -0.01, 0));
        if (d != null) d.color(com.coolfreeze23.headinajar.client.fx.FxColors.WET).size(0.05f).gravity(0.6f).life(14).add();
    }

    private JarLife() {
    }
}
