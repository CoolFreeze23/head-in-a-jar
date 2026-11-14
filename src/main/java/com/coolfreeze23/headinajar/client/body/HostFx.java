package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.HostHeads;
import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.client.fx.FxColors;
import com.coolfreeze23.headinajar.client.fx.FxParticle;
import com.coolfreeze23.headinajar.client.fx.SoundLoops;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Taking a body, seen from anywhere: the saw's cork (the creature holds still for three ticks, then its head pops out of
 * the neck and is gone in a sanitary puff, or arcs into the jar in the cutter's off hand); the body's first moment
 * headless (a four-legged one steps back and shakes like a wet dog, a two-armed one pats the air where its head was);
 * a jar seating on the neck (the body braces under it); sync arriving in quarters (a teal ring runs down the body);
 * and rot (posture sags as it turns, then flies, then a stumble), never on an embalmed one.
 * <p>
 * Whole-body motion goes through the pose stack around the creature's own renderer, so it survives any re-modelling
 * resource pack; the patting arms are set on the model's own arm parts after it has posed, and a pack that animates
 * those parts itself may override them (the rest still plays).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class HostFx {
    /** The head pops this many ticks after the cut's memo (the cork's whine). */
    public static final int POP = 3;

    private record Cut(long at, boolean intoJar, int cutter, Vec3 neck) {
    }

    private static final Map<Integer, Cut> CUTS = new HashMap<>();
    private static final Map<Integer, Long> SEATED = new HashMap<>(), UNSEATED = new HashMap<>();
    private static final Set<Integer> EMBALMED = new HashSet<>();
    private static final Map<Integer, Float> LAST_SYNC = new HashMap<>();
    private static final ArrayDeque<Boolean> PUSHED = new ArrayDeque<>();

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    // --- memos -------------------------------------------------------------------------------------------------

    static void cut(Fx fx) {
        CUTS.put(fx.entityB(), new Cut(now(), fx.arg() > 0.5f, fx.entityA(), fx.a()));
        BodyFx.later(POP, () -> {
            // the pop: a white ring at the neck and a small puff (the server sends the sanitary puff itself)
            FxParticle r = FxParticle.make(FxParticle.Look.RING, fx.a(), Vec3.ZERO);
            if (r != null) r.color(0xf7f0d5).size(0.3f).grow(2.4f).life(7).flat(new Vec3(0, 1, 0)).add();
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == fx.entityA()) {
                com.coolfreeze23.headinajar.client.fx.CameraDesk.kick(0.8f, 1, 5);
                com.coolfreeze23.headinajar.client.fx.CameraDesk.shake(0.06f);
            }
        });
        // the cutter's hand pushes the saw through (first person)
        ToolFx.saw(fx.entityA());
    }

    static void seat(Fx fx) {
        SEATED.put(fx.entityB(), now());
        UNSEATED.remove(fx.entityB());
        // two small puffs from the twist, either side of the neck
        for (int s = -1; s <= 1; s += 2) {
            FxParticle p = FxParticle.make(FxParticle.Look.PUFF, fx.a().add(s * 0.3, 0, 0), new Vec3(s * 0.02, 0.02, 0));
            if (p != null) p.color(0xdfd7bf).size(0.12f).grow(1.6f).life(10).add();
        }
    }

    static void unseat(Fx fx) {
        UNSEATED.put(fx.entityB(), now());
        SEATED.remove(fx.entityB());
        FxParticle p = FxParticle.make(FxParticle.Look.PUFF, fx.a(), new Vec3(0, 0.03, 0));
        if (p != null) p.color(0xdfd7bf).size(0.15f).grow(1.8f).life(10).add();
    }

    static void embalmed(Fx fx) {
        EMBALMED.add(fx.entityB());
        if (fx.arg() > 0.5f) sparkle(fx.a(), 10);
    }

    private static void sparkle(Vec3 at, int n) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < Comfort.particles(n); i++) {
            Vec3 v = new Vec3(level.random.nextGaussian() * 0.03, 0.02 + level.random.nextFloat() * 0.04, level.random.nextGaussian() * 0.03);
            FxParticle s = FxParticle.make(FxParticle.Look.SPARK, at.add(v.scale(6)), v);
            if (s != null) s.color(0xc8b6f0).size(0.05f).life(14 + i % 6).add();
        }
    }

    /** Whether this creature's head part is hidden as it draws: a host's always, after the cut's pop. */
    public static boolean headHidden(LivingEntity e) {
        Cut c = CUTS.get(e.getId());
        if (c != null) return now() - c.at >= POP;
        return Hosts.isHost(e);
    }

    /** When this client saw a jar seat on this host (for the collar's twist), or null. */
    public static Long seatedAt(int hostId) {
        return SEATED.get(hostId);
    }

    /** For the harness: how many cuts, seatings and embalmed hosts the client is drawing. */
    public static String counts() {
        return "cuts " + CUTS.size() + ", seated " + SEATED.size() + ", embalmed " + EMBALMED.size();
    }

    // --- per tick: flies, sync pulses, forgetting ----------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.isPaused()) {
            if (level == null) {
                CUTS.clear();
                SEATED.clear();
                UNSEATED.clear();
                EMBALMED.clear();
                LAST_SYNC.clear();
            }
            return;
        }
        long now = level.getGameTime();
        CUTS.values().removeIf(c -> now - c.at > 60);
        SEATED.values().removeIf(t -> now - t > 40);
        UNSEATED.values().removeIf(t -> now - t > 40);
        EMBALMED.removeIf(id -> level.getEntity(id) == null);
        LAST_SYNC.keySet().removeIf(id -> level.getEntity(id) == null);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Mob mob) || !Hosts.isHost(mob)) continue;
            HostData data = Hosts.data(mob);
            if (data.shell) continue;
            // sync arriving: a teal ring runs down the body at each quarter, a larger one at 100
            Float before = LAST_SYNC.put(mob.getId(), data.sync);
            if (before != null && Math.floor(before / 25f) < Math.floor(data.sync / 25f) && data.sync > before) syncPulse(mob, data.sync >= 100);
            if (EMBALMED.contains(mob.getId()) || data.freshness >= 50 || e.distanceToSqr(cam) > 24 * 24) continue;
            int flies = data.freshness < 10 ? 3 : data.freshness < 25 ? 2 : 1;
            Vec3 neck = com.coolfreeze23.headinajar.body.BodyServer.neck(mob).add(0, 0.3, 0);
            for (int i = 0; i < flies; i++) fly(mob, neck, i, now);
            // two flies, not a swarm: vanilla's bee hum, very high and very quiet
            SoundLoops.want(mob, com.coolfreeze23.headinajar.fx.HouseSounds.FLIES.get(), SoundSource.NEUTRAL, neck, 0.06f + 0.03f * flies, 1.9f);
        }
    }

    /** One fly: a dark speck on a looping random walk round a point above the neck, leaving a two-tick trail. */
    private static void fly(Mob mob, Vec3 centre, int i, long now) {
        float t = now * 0.21f + i * 2.1f + mob.getId() * 0.37f;
        double r = 0.22 + 0.08 * Mth.sin(t * 0.7f + i);
        Vec3 p = centre.add(Mth.cos(t * (1.3f + i * 0.2f)) * r, 0.1 * Mth.sin(t * 2.3f), Mth.sin(t * (1.1f + i * 0.3f)) * r);
        FxParticle f = FxParticle.make(FxParticle.Look.DROPLET, p, Vec3.ZERO);
        if (f != null) f.color(0x151317).size(0.045f).life(2).add();
    }

    private static void syncPulse(Mob mob, boolean full) {
        float h = mob.getBbHeight();
        for (int k = 0; k < 4; k++) {
            int step = k;
            BodyFx.later(step * 2, () -> {
                Vec3 at = mob.position().add(0, h * (0.85 - step * 0.25), 0);
                FxParticle r = FxParticle.make(FxParticle.Look.RING, at, Vec3.ZERO);
                if (r != null) r.color(FxColors.SIGNAL).size(mob.getBbWidth() * 1.2f).grow(1.1f).life(6).flat(new Vec3(0, 1, 0)).add();
            });
        }
        if (full) BodyFx.later(8, () -> {
            FxParticle r = FxParticle.make(FxParticle.Look.RING, mob.position().add(0, 0.05, 0), Vec3.ZERO);
            if (r != null) r.color(FxColors.SIGNAL_PALE).size(mob.getBbWidth() * 1.5f).grow(2.4f).life(12).flat(new Vec3(0, 1, 0)).add();
        });
    }

    // --- whole-body poses ---------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        Cut cut = CUTS.get(e.getId());
        Long seat = SEATED.get(e.getId()), unseat = UNSEATED.get(e.getId());
        boolean host = Hosts.isHost(e) && !(e instanceof com.coolfreeze23.headinajar.entity.MechaEntity) && !Hosts.data(e).shell;
        if (cut == null && seat == null && unseat == null && !host) {
            PUSHED.push(false);
            return;
        }
        float pt = event.getPartialTick();
        float k = Math.max(0.35f, Comfort.motion());
        long now = now();
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        float h = e.getBbHeight();
        float dy = 0, back = 0, roll = 0, pitch = 0;
        if (cut != null && !cut.intoJar) {
            float t = now - cut.at - POP + pt;
            boolean biped = event.getRenderer().getModel() instanceof HumanoidModel<?>;
            if (t > 0 && t < 10) {
                // it doesn't know yet: perfectly still
            } else if (t >= 10 && !biped) {
                // two small steps back, then a wet-dog shake: a whole-body roll, quick and damped
                float s = Mth.clamp((t - 10) / 6f, 0, 1), r = Mth.clamp((t - 34) / 10f, 0, 1);
                back = 0.14f * (s * s * (3 - 2 * s)) * (1 - r * r * (3 - 2 * r));
                dy = -0.02f * Mth.sin(Mth.clamp((t - 10) / 3f, 0, 2) * Mth.PI) * (t < 16 ? 1 : 0);
                if (t > 16 && t < 30) roll = 6 * Mth.sin((t - 16) * 2.2f) * (float) Math.exp(-(t - 16) / 6f);
            } else if (t >= 10) {
                patArms(event.getRenderer().getModel(), t - 10);
            }
        }
        if (seat != null) {
            // it takes the jar's weight: down 1.5 px over 4 ticks, then up past level and settle
            float t = now - seat + pt;
            if (t < 4) dy -= 0.094f * (t / 4) * (t / 4);
            else if (t < 14) {
                float u = (t - 4) / 10f;
                dy -= 0.094f * (1 - u) * (1 - u) - 0.02f * Mth.sin(u * Mth.PI);
            }
        }
        if (unseat != null) {
            // the legs buckle a little as the jar comes off, and it stands back up
            float t = now - unseat + pt;
            if (t < 3) dy -= 0.12f * (t / 3);
            else if (t < 14) dy -= 0.12f * (1 - (t - 3) / 11f) * (1 - (t - 3) / 11f);
        }
        if (host && !EMBALMED.contains(e.getId())) {
            float fresh = Hosts.data(e).freshness;
            float age = e.tickCount + pt;
            // rot read from outside: it sags forward as it turns, sways when ripe, and stumbles when nearly gone
            if (fresh < 50) pitch += fresh < 25 ? 6 : 3;
            if (fresh < 25) roll += 1.5f * Mth.sin(age * Mth.TWO_PI / 80f);
            if (fresh < 10) {
                float c = (age + e.getId() * 13) % 110;
                if (c < 6) dy -= 0.09f * Mth.sin(c / 6 * Mth.PI);
            }
        }
        var pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.push(true);
        pose.translate(-fwd.x * back, dy * k, -fwd.z * back);
        if (Math.abs(roll) > 0.01f || Math.abs(pitch) > 0.01f) {
            // about the middle of the body: roll about its forward axis, pitch about its right
            pose.translate(0, h * 0.5, 0);
            if (Math.abs(roll) > 0.01f) pose.mulPose(Axis.of(new org.joml.Vector3f((float) fwd.x, 0, (float) fwd.z)).rotationDegrees(roll * k));
            if (Math.abs(pitch) > 0.01f) pose.mulPose(Axis.of(new org.joml.Vector3f(Mth.cos(yaw), 0, Mth.sin(yaw))).rotationDegrees(pitch));
            pose.translate(0, -h * 0.5, 0);
        }
    }

    /** A two-armed body reaches up to where its head was, pats the air twice, and lowers its hands (24 ticks). */
    private static void patArms(EntityModel<?> model, float t) {
        if (!(model instanceof HumanoidModel<?> humanoid) || t > 24) return;
        float up = t < 6 ? t / 6 : t < 18 ? 1 : 1 - (t - 18) / 6;
        up = up * up * (3 - 2 * up);
        float pat = t >= 6 && t < 18 ? Mth.sin((t - 6) / 12 * Mth.TWO_PI * 2) * 0.18f : 0;
        HostHeads.poseArm(humanoid.rightArm, -2.7f * up + pat, 0, 0.35f * up);
        HostHeads.poseArm(humanoid.leftArm, -2.7f * up - pat, 0, -0.35f * up);
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> event) {
        if (!PUSHED.isEmpty() && PUSHED.pop()) event.getPoseStack().popPose();
    }

    // --- the head itself, after it leaves the neck ---------------------------------------------------------------

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CUTS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = mc.level.getGameTime();
        for (Map.Entry<Integer, Cut> entry : CUTS.entrySet()) {
            Cut c = entry.getValue();
            float t = now - c.at - POP + pt;
            float length = c.intoJar ? 8 : 10;
            if (t < 0 || t > length) continue;
            if (!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity mob)) continue;
            float u = t / length;
            Vec3 at;
            float spin;
            if (c.intoJar && mc.level.getEntity(c.cutter) instanceof Player cutter) {
                // a short arc from the neck into the jar in the cutter's off hand
                float yaw = Mth.rotLerp(pt, cutter.yBodyRotO, cutter.yBodyRot) * Mth.DEG_TO_RAD;
                Vec3 hand = cutter.getPosition(pt).add(Mth.cos(yaw) * 0.38 - Mth.sin(yaw) * 0.3, 0.75, Mth.sin(yaw) * 0.38 + Mth.cos(yaw) * 0.3);
                at = c.neck.lerp(hand, u * u * (3 - 2 * u)).add(0, Math.sin(u * Math.PI) * 0.6, 0);
                spin = u * 200;
                if (t + 1 > length && t <= length) landed(hand);
            } else {
                // a champagne cork: out of the neck and up, turning over, gone in a puff at the top
                float e = 1 - (1 - u) * (1 - u) * (1 - u);
                at = c.neck.add(0, 1.3 * e, 0);
                spin = u * 540;
                if (t + 1 > length && t <= length) gone(at);
            }
            drawHead(mob, at, spin, pose, cam, buffers, mc);
        }
        buffers.endBatch();
    }

    private static final Set<Long> SAID = new HashSet<>();

    /** Puffs at the top of the cork's flight, once. */
    private static void gone(Vec3 at) {
        long key = Double.doubleToLongBits(at.x * 31 + at.z);
        if (!SAID.add(key)) return;
        if (SAID.size() > 64) SAID.clear();
        FxParticle p = FxParticle.make(FxParticle.Look.PUFF, at, new Vec3(0, 0.01, 0));
        if (p != null) p.color(0xf7f0d5).size(0.35f).grow(1.8f).life(12).add();
        FxParticle r = FxParticle.make(FxParticle.Look.RING, at, Vec3.ZERO);
        if (r != null) r.color(0xf7f0d5).size(0.25f).grow(2.2f).life(8).add();
    }

    private static void landed(Vec3 hand) {
        long key = Double.doubleToLongBits(hand.x * 17 + hand.y);
        if (!SAID.add(key)) return;
        FxParticle r = FxParticle.make(FxParticle.Look.RING, hand, Vec3.ZERO);
        if (r != null) r.color(FxColors.WET_PALE).size(0.2f).grow(2f).life(7).add();
    }

    /** Draws the creature's own head part, as its model has it, at {@code at} in the world, turned over by {@code spin}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drawHead(LivingEntity mob, Vec3 at, float spin, PoseStack pose, Vec3 cam, MultiBufferSource buffers, Minecraft mc) {
        if (!(mc.getEntityRenderDispatcher().getRenderer(mob) instanceof LivingEntityRenderer renderer)) return;
        ModelPart head = HostHeads.find(renderer.getModel());
        if (head == null) return;
        ResourceLocation texture = renderer.getTextureLocation(mob);
        float x = head.x, y = head.y, z = head.z, xr = head.xRot, yr = head.yRot, zr = head.zRot;
        boolean visible = head.visible;
        head.x = head.y = head.z = 0;
        head.xRot = head.yRot = head.zRot = 0;
        head.visible = true;
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - mob.yBodyRot));
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        pose.scale(-1, -1, 1);
        int light = LevelRenderer.getLightColor(mob.level(), BlockPos.containing(at));
        try {
            head.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        } finally {
            pose.popPose();
            head.x = x;
            head.y = y;
            head.z = z;
            head.xRot = xr;
            head.yRot = yr;
            head.zRot = zr;
            head.visible = visible;
        }
    }

    private HostFx() {
    }
}
