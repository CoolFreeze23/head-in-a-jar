package com.coolfreeze23.headinajar.client.genes;

import com.coolfreeze23.headinajar.client.fx.Beams;
import com.coolfreeze23.headinajar.client.fx.Comfort;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client side of the 0.6.2 actives and Weeping Glands: Phase's trail, the tongue (a pink line drawn like
 * Telekinesis' tether, with a tip), Snow Pounce's landing, the Fang Line cast, the tears and sobs, the cockpit glass that
 * fogs when a mecha sobs, and the jar's lid hopping when the host it rides licks or spits.
 */
public final class ActivesClient {
    /** The tongue: shoots out in 2 ticks, holds 1, comes back in 5. */
    private static final float TONGUE_OUT = 2, TONGUE_HOLD = 3, TONGUE_LIFE = 8;
    /** The lid hop: 4 ticks, at most this high (blocks). */
    private static final float HOP_TICKS = 4, HOP_HEIGHT = 2.5f / 16;
    private static final Beams.Ribbon TONGUE_LOOK = new Beams.Ribbon(0x9C3A55, 0xF08AA6, 2f / 16, 1.4f / 16, 1f, 0);
    private static final int TIP = 0xE0607E;

    private record Tongue(int body, int caught, Vec3 mouth, Vec3 tip, long start) {
    }

    private static final List<Tongue> TONGUES = new ArrayList<>();
    /** A jar's lid hop: the jar player's id and the game time it started. */
    private static final Map<Integer, Long> HOPS = new HashMap<>();
    private static int ticks, fogUntil = -1, fogFrom;

    private ActivesClient() {
    }

    /** Puts the tongue's drawing, the per-tick upkeep and the cockpit fog on the client's events (once, from the effect kit). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(ActivesClient::tick);
        NeoForge.EVENT_BUS.addListener(ActivesClient::render);
        NeoForge.EVENT_BUS.addListener(ActivesClient::gui);
    }

    /** A gene memo of this stream's kinds (see {@link Fx}). */
    public static void fx(Fx fx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity body = fx.entityA() >= 0 ? level.getEntity(fx.entityA()) : null;
        switch (fx.kind()) {
            case Fx.GENE_PHASE -> phase(level, fx.a(), fx.b());
            case Fx.GENE_TONGUE -> {
                TONGUES.add(new Tongue(fx.entityA(), fx.entityB(), fx.a(), fx.b(), level.getGameTime()));
                hop(level, body);
            }
            case Fx.GENE_SPIT -> hop(level, body);
            case Fx.GENE_POUNCE -> pounce(level, fx.a(), (int) fx.arg());
            case Fx.GENE_FANGS -> fangs(level, body == null ? fx.a() : body.position(), body == null ? 1 : body.getBbHeight(), fx.arg() > 0);
            case Fx.GENE_WEEP -> weep(level, body, fx.a(), fx.arg() > 0);
            default -> {
            }
        }
    }

    // --- the jar's lid ------------------------------------------------------------------------------------------------

    /** The jar riding a host that just licked or spat hops its lid. */
    private static void hop(ClientLevel level, Entity body) {
        if (body == null) return;
        for (Entity rider : body.getPassengers()) {
            if (rider instanceof Player p && p.getData(ModRegistry.JAR_STATE).form() == Form.HOSTED) HOPS.put(p.getId(), level.getGameTime());
        }
    }

    /** How far this jar's lid is lifted now (blocks; 0 when it is not hopping). */
    public static float lidHop(Player jar, float partialTick) {
        Long start = HOPS.get(jar.getId());
        if (start == null) return 0;
        float age = jar.level().getGameTime() - start + partialTick;
        if (age < 0 || age > HOP_TICKS) return 0;
        return Mth.sin(age / HOP_TICKS * Mth.PI) * HOP_HEIGHT;
    }

    // --- particles ----------------------------------------------------------------------------------------------------

    private static void phase(ClientLevel level, Vec3 a, Vec3 b) {
        if (!Comfort.nearEnough(a) && !Comfort.nearEnough(b)) return;
        var random = level.random;
        int n = Comfort.particles((int) Math.ceil(a.distanceTo(b) * 10));
        for (int i = 0; i < n; i++) {
            Vec3 p = a.lerp(b, random.nextDouble());
            level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x + random.nextGaussian() * 0.15, p.y + random.nextGaussian() * 0.3, p.z + random.nextGaussian() * 0.15,
                    0, 0.02, 0);
        }
        for (int i = 0; i < Comfort.particles(16); i++) {
            level.addParticle(ParticleTypes.PORTAL, a.x, a.y, a.z, random.nextGaussian() * 0.5, random.nextGaussian() * 0.5, random.nextGaussian() * 0.5);
            level.addParticle(ParticleTypes.REVERSE_PORTAL, b.x, b.y, b.z, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05);
        }
    }

    private static void pounce(ClientLevel level, Vec3 at, int caught) {
        if (!Comfort.nearEnough(at)) return;
        var random = level.random;
        int n = Comfort.particles(20);
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.addParticle(ParticleTypes.SNOWFLAKE, at.x + dx * 0.6, at.y + 0.1, at.z + dz * 0.6, dx * 0.18, 0.05 + random.nextDouble() * 0.05, dz * 0.18);
        }
        for (int i = 0; i < Comfort.particles(6 + 2 * caught); i++) {
            level.addParticle(ParticleTypes.CLOUD, at.x + random.nextGaussian() * 0.5, at.y + 0.2, at.z + random.nextGaussian() * 0.5, 0, 0.03, 0);
        }
    }

    /** The evoker's spell particles over the caster (vanilla's colour for the fangs spell). */
    private static void fangs(ClientLevel level, Vec3 at, float height, boolean ring) {
        if (!Comfort.nearEnough(at)) return;
        var random = level.random;
        var spell = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.4f, 0.3f, 0.35f);
        for (int i = 0; i < Comfort.particles(ring ? 24 : 14); i++) {
            double a = random.nextDouble() * Mth.TWO_PI, r = ring ? 0.6 + random.nextDouble() : 0.5;
            level.addParticle(spell, at.x + Math.cos(a) * r, at.y + height * (0.6 + random.nextDouble() * 0.5), at.z + Math.sin(a) * r, 0, 0, 0);
        }
    }

    private static void weep(ClientLevel level, Entity body, Vec3 eyes, boolean tear) {
        var random = level.random;
        if (Comfort.nearEnough(eyes)) {
            for (int i = 0; i < Comfort.particles(tear ? 6 : 3); i++) {
                level.addParticle(tear ? ParticleTypes.FALLING_WATER : ParticleTypes.DRIPPING_WATER, eyes.x + random.nextGaussian() * 0.2, eyes.y - 0.1,
                        eyes.z + random.nextGaussian() * 0.2, 0, 0, 0);
            }
        }
        // a sob fogs the cockpit glass for the one inside, never under the photosensitive option
        var mc = Minecraft.getInstance();
        if (!tear && body instanceof MechaEntity && mc.player != null && mc.player.getVehicle() == body && !Comfort.photosensitive()) {
            fogFrom = ticks;
            fogUntil = ticks + com.coolfreeze23.headinajar.genetics.GeneNumbers.WEEP_FOG;
        }
    }

    /** Whether the cockpit glass is fogged now, for the harness. */
    public static boolean fogged() {
        return fogUntil > ticks;
    }

    // --- the tongue ---------------------------------------------------------------------------------------------------

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ticks++;
        if (mc.level == null) {
            TONGUES.clear();
            HOPS.clear();
            fogUntil = -1;
            return;
        }
        long now = mc.level.getGameTime();
        TONGUES.removeIf(t -> now - t.start > TONGUE_LIFE + 1 || now < t.start);
        HOPS.values().removeIf(start -> now - start > HOP_TICKS + 1 || now < start);
    }

    /** Where the tongue leaves from now: the jar riding the body, or the body's own mouth (from below the view for the doer). */
    private static Vec3 mouth(ClientLevel level, Tongue t, float pt) {
        Entity body = level.getEntity(t.body);
        if (body == null) return t.mouth;
        Entity from = body;
        for (Entity rider : body.getPassengers()) if (rider instanceof Player) from = rider;
        var mc = Minecraft.getInstance();
        Vec3 eye = from.getEyePosition(pt);
        if (from == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return eye.add(mc.player.getViewVector(pt).scale(0.4)).subtract(0, 0.3, 0);
        }
        return eye.subtract(0, 0.15, 0);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || TONGUES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        var frame = new Beams.Frame(pose, mc.renderBuffers().bufferSource(), event.getCamera().getPosition(), event.getFrustum(), pt, level.getGameTime() + pt);
        for (Tongue t : TONGUES) {
            float age = level.getGameTime() - t.start + pt;
            if (age < 0 || age > TONGUE_LIFE) continue;
            Vec3 mouth = mouth(level, t, pt);
            Entity caught = t.caught >= 0 ? level.getEntity(t.caught) : null;
            // the tip sticks to a creature it caught; an item it fetched is already on its way home
            Vec3 far = caught != null && !(caught instanceof ItemEntity) && age < TONGUE_HOLD ? caught.getBoundingBox().getCenter() : t.tip;
            Vec3 tip;
            if (age < TONGUE_OUT) tip = mouth.lerp(far, age / TONGUE_OUT);
            else if (age < TONGUE_HOLD) tip = far;
            else {
                float k = (age - TONGUE_HOLD) / (TONGUE_LIFE - TONGUE_HOLD);
                tip = far.lerp(mouth, k * k);
            }
            double len = mouth.distanceTo(tip);
            if (len < 0.05) continue;
            Vec3 control = mouth.lerp(tip, 0.5).subtract(0, len * 0.06, 0);
            Beams.ribbon(frame, Beams.curve(mouth, control, tip, 8), TONGUE_LOOK, null);
            cube(frame, tip, 0.11f);
        }
        mc.renderBuffers().bufferSource().endBatch();
    }

    /** The tongue's tip: a small pink cube, shaded per face. */
    private static void cube(Beams.Frame f, Vec3 at, float size) {
        Vec3 p = at.subtract(f.camera());
        if (p.lengthSqr() < 0.3 * 0.3) return;
        VertexConsumer b = f.buffers().getBuffer(RenderType.debugQuads());
        Matrix4f mat = f.pose().last().pose();
        float s = size / 2, x = (float) p.x, y = (float) p.y, z = (float) p.z;
        float r = (TIP >> 16 & 255) / 255f, g = (TIP >> 8 & 255) / 255f, bl = (TIP & 255) / 255f;
        float[][] faces = {
                {-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s, 0.6f}, {-s, s, -s, -s, s, s, s, s, s, s, s, -s, 1f},
                {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s, 0.8f}, {-s, -s, s, s, -s, s, s, s, s, -s, s, s, 0.8f},
                {-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, 0.7f}, {s, -s, -s, s, s, -s, s, s, s, s, -s, s, 0.7f}};
        for (float[] q : faces) {
            float shade = q[12];
            for (int i = 0; i < 4; i++) b.addVertex(mat, x + q[i * 3], y + q[i * 3 + 1], z + q[i * 3 + 2]).setColor(r * shade, g * shade, bl * shade, 1f);
        }
    }

    // --- the cockpit fog ----------------------------------------------------------------------------------------------

    private static void gui(RenderGuiEvent.Post event) {
        if (fogUntil <= ticks) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof MechaEntity) || !mc.options.getCameraType().isFirstPerson() || Comfort.photosensitive()) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = ticks - fogFrom + pt, left = fogUntil - ticks - pt;
        // the glass mists over in a moment and clears slowly
        float a = Mth.clamp(Math.min(age / 6f, left / 25f), 0, 1) * 0.55f;
        if (a <= 0.01f) return;
        var g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int top = (int) (a * 0.75f * 255) << 24 | 0xD6E2E8, bottom = (int) (a * 255) << 24 | 0xE4ECEF;
        g.fillGradient(0, 0, w, h, top, bottom);
    }
}
