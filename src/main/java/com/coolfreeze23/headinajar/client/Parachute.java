package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib.animation.keyframe.event.data.SoundKeyframeData;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Golden Parachute over a jar fired clear of a dying mecha, drawn from its own model as an extra beside the jar (the
 * jar's own model and motion are untouched), at the jar's feet and turned with it. Nothing is scaled: the clips open
 * and fold it. Its clip is chosen every frame from the state the server sent and its age: {@code deploy} from the
 * eject (it snaps open on its tick 20), {@code streamer} when it tangled, then {@code descend}, {@code steer_l/r} while
 * the jar slides sideways faster than 0.03 blocks a tick, {@code flare} once within 2.5 blocks of the ground, and on the
 * fold {@code land} (40 ticks) or {@code water} (60) kept drawn where it came down. It swings with the jar's drift at
 * the swivel. Seen from the pilot's own camera looking down steeply, the panels and crown are drawn faint so the ground
 * shows through; the valance and tassels stay solid.
 */
public final class Parachute {
    private static final ResourceLocation GEO = HeadInAJar.id("geo/entity/golden_parachute.geo.json");
    private static final ResourceLocation ANIMATIONS = HeadInAJar.id("animations/entity/golden_parachute.animation.json");
    private static final ResourceLocation TEXTURE = HeadInAJar.id("textures/entity/golden_parachute.png");
    private static final int DEPLOY = 30, STREAM_FROM = 13, LAND = 40, WATER = 60, FLARE = 16, STEER = 12;
    /** Sideways drift that steers (blocks a tick), and the flare's height over the ground (blocks). */
    static final double STEER_SPEED = 0.03, FLARE_HEIGHT = 2.5;
    /** The faint cloth's alpha, and the pitch (degrees down) past which the pilot's own camera sees it faint. */
    private static final int FAINT_ALPHA = 0x55;
    private static final float FAINT_PITCH = 50;
    /** Bones whose first cube is cloth seen faint from above (the panels; the crown's and the pilot chute's are all cloth). */
    private static final Set<String> PANELS = Set.of("panel_f", "panel_b", "panel_l", "panel_r");

    /** One jar's parachute as this client knows it. */
    static final class Chute {
        int state;
        double firedAt, foldAt = -1, flareAt = -1, steerAt = -1;
        /** The deploy's rate (1 as drawn; faster for a short drop). */
        float rate = 1;
        int steerSide;
        boolean water;
        Vec3 landed;
        float landYaw;
        float swingX, swingZ;
        String clip = "";
        double tick = -1;
        Vec3 lastPos;
    }

    private static final Map<Integer, Chute> CHUTES = new HashMap<>();

    private Parachute() {
    }

    /** The server says this player's parachute changed state; {@code age} is the ticks since the jar was fired. */
    public static void set(int entityId, int state, int age, float rate) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Chute c = CHUTES.get(entityId);
        if (state == HopperNet.FOLDED || state == HopperNet.SUNK) {
            if (c == null) return;
            c.foldAt = now;
            c.water = state == HopperNet.SUNK;
            var e = level.getEntity(entityId);
            c.landed = e != null ? e.position() : c.lastPos;
            c.landYaw = e instanceof Player p ? p.yBodyRot : 0;
            return;
        }
        if (c == null || c.foldAt >= 0) {
            c = new Chute();
            CHUTES.put(entityId, c);
        }
        c.state = state;
        c.firedAt = now - age;
        c.rate = rate > 0 ? rate : 1;
    }

    public static boolean shown(int entityId) {
        return CHUTES.containsKey(entityId);
    }

    /** The clip and tick of a parachute now (also what the dev harness reports). */
    static String[] choose(Player player, Chute c, double now) {
        if (c.foldAt >= 0) {
            double t = now - c.foldAt;
            return new String[]{c.water ? "water" : "land", Double.toString(t)};
        }
        // the deploy's own ticks: played faster for a short drop
        double t = (now - c.firedAt) * c.rate;
        if (c.state == HopperNet.TANGLED) {
            return t < STREAM_FROM ? new String[]{"deploy", Double.toString(t)} : new String[]{"streamer", Double.toString(t - STREAM_FROM)};
        }
        if (t < DEPLOY) return new String[]{"deploy", Double.toString(t)};
        // the flare, once, when the ground comes within reach below
        if (c.flareAt < 0 && !player.level().noCollision(player, player.getBoundingBox().expandTowards(0, -FLARE_HEIGHT, 0))) c.flareAt = now;
        if (c.flareAt >= 0 && now - c.flareAt < FLARE) return new String[]{"flare", Double.toString(now - c.flareAt)};
        // steering: the drift across the jar's facing
        Vec3 v = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double across = v.x * -Mth.cos(yaw) + v.z * -Mth.sin(yaw);
        int side = across > STEER_SPEED ? 1 : across < -STEER_SPEED ? -1 : 0;
        if (side != 0) {
            if (c.steerSide != side || c.steerAt < 0) {
                c.steerSide = side;
                c.steerAt = now;
            }
            return new String[]{side > 0 ? "steer_r" : "steer_l", Double.toString(Math.min(now - c.steerAt, STEER - 0.01))};
        }
        c.steerAt = -1;
        c.steerSide = 0;
        return new String[]{"descend", Double.toString(now)};
    }

    /** Draws the parachute over a player's jar (the pose is at the player's feet). */
    public static void render(Player player, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Chute c = CHUTES.get(player.getId());
        if (c == null) return;
        double now = player.level().getGameTime() + partialTick;
        if (player.isRemoved() || now - c.firedAt > 1500 || c.foldAt >= 0 && now - c.foldAt >= (c.water ? WATER : LAND)) {
            CHUTES.remove(player.getId());
            return;
        }
        Model model = model();
        if (model == null) return;
        c.lastPos = player.position();
        String[] choice = choose(player, c, now);
        String clip = choice[0];
        double tick = Double.parseDouble(choice[1]);
        effects(player, c, model, clip, tick);
        model.pose(clip, tick);
        // the swing with the jar's drift, damped; eased out once it is down
        var v = player.getDeltaMovement();
        if (player != Minecraft.getInstance().player) v = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
        float down = c.foldAt >= 0 ? 0 : 1;
        c.swingX += ((float) Mth.clamp(-v.z * 60, -25, 25) * down - c.swingX) * 0.08f;
        c.swingZ += ((float) Mth.clamp(v.x * 60, -25, 25) * down - c.swingZ) * 0.08f;
        float yaw;
        pose.pushPose();
        if (c.foldAt >= 0 && c.landed != null) {
            // the cloth lies where it came down, whatever the jar does next
            double x = Mth.lerp(partialTick, player.xo, player.getX()), y = Mth.lerp(partialTick, player.yo, player.getY()), z = Mth.lerp(partialTick, player.zo, player.getZ());
            pose.translate(c.landed.x - x, c.landed.y - y, c.landed.z - z);
            yaw = c.landYaw;
        } else {
            yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        }
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        var mc = Minecraft.getInstance();
        boolean faint = player == mc.getCameraEntity() && !mc.options.getCameraType().isFirstPerson()
                && (mc.gameRenderer.getMainCamera().getXRot() > FAINT_PITCH
                || mc.gameRenderer.getMainCamera().getPosition().y > player.getY() + 39 / 16.0);
        // two passes, one buffer each (asking for the second buffer ends the first): the solid cubes, then the faint cloth
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (GeoBone top : model.baked.topLevelBones()) walk(pose, top, solid, false, faint, 180 - yaw, c, light);
        if (faint) {
            VertexConsumer thin = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
            for (GeoBone top : model.baked.topLevelBones()) walk(pose, top, thin, true, faint, 180 - yaw, c, light);
        }
        pose.popPose();
    }

    private static void walk(PoseStack pose, GeoBone bone, VertexConsumer vc, boolean thinPass, boolean faint, float turn, Chute c, int light) {
        pose.pushPose();
        if (bone.getName().equals("canopy") && (c.swingX != 0 || c.swingZ != 0)) {
            // the drift's swing, in the world's frame, about the swivel
            float px = bone.getPivotX() / 16f, py = bone.getPivotY() / 16f, pz = bone.getPivotZ() / 16f;
            pose.translate(px, py, pz);
            pose.mulPose(Axis.YP.rotationDegrees(-turn));
            pose.mulPose(Axis.XP.rotationDegrees(c.swingX));
            pose.mulPose(Axis.ZP.rotationDegrees(c.swingZ));
            pose.mulPose(Axis.YP.rotationDegrees(turn));
            pose.translate(-px, -py, -pz);
        }
        RenderUtil.prepMatrixForBone(pose, bone);
        if (!bone.isHidden()) {
            List<GeoCube> cubes = bone.getCubes();
            boolean cloth = bone.getName().equals("cloth") || bone.getName().equals("pilot");
            for (int i = 0; i < cubes.size(); i++) {
                boolean see = faint && (cloth || PANELS.contains(bone.getName()) && i == 0);
                if (see == thinPass) cube(pose, cubes.get(i), vc, light, see ? FAINT_ALPHA << 24 | 0xFFFFFF : 0xFFFFFFFF);
            }
        }
        if (!bone.isHidingChildren()) for (GeoBone child : bone.getChildBones()) walk(pose, child, vc, thinPass, faint, turn, c, light);
        pose.popPose();
    }

    /** One cube, as GeckoLib's own renderer draws it. */
    private static void cube(PoseStack pose, GeoCube cube, VertexConsumer vc, int light, int colour) {
        pose.pushPose();
        RenderUtil.translateToPivotPoint(pose, cube);
        RenderUtil.rotateMatrixAroundCube(pose, cube);
        RenderUtil.translateAwayFromPivotPoint(pose, cube);
        Matrix3f normalPose = pose.last().normal();
        Matrix4f posePose = new Matrix4f(pose.last().pose());
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;
            Vector3f normal = normalPose.transform(new Vector3f(quad.normal()));
            RenderUtil.fixInvertedFlatCube(cube, normal);
            for (GeoVertex vertex : quad.vertices()) {
                Vector4f p = posePose.transform(new Vector4f(vertex.position().x(), vertex.position().y(), vertex.position().z(), 1));
                vc.addVertex(p.x(), p.y(), p.z(), colour, vertex.texU(), vertex.texV(), OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
            }
        }
        pose.popPose();
    }

    /** The clips' sounds and particles, each once as its tick passes (none for a clip met part-way: nothing of the past). */
    private static void effects(Player player, Chute c, Model model, String clip, double tick) {
        double before = c.tick;
        boolean same = clip.equals(c.clip);
        c.clip = clip;
        c.tick = tick;
        Animation a = model.clips.get(clip);
        if (a == null || !same || tick <= before || tick - before > 3) return;
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(player.position()) > 32 * 32) return;
        Vec3 at = c.foldAt >= 0 && c.landed != null ? c.landed : player.position();
        for (SoundKeyframeData s : a.keyFrames().sounds()) {
            if (s.getStartTick() <= before || s.getStartTick() > tick) continue;
            String[] p = s.getSound().split("\\|");
            var sound = HopperSounds.get(p[0].trim());
            if (sound == null) continue;
            float pitch = p.length > 2 ? Float.parseFloat(p[2].trim()) : 1;
            player.level().playLocalSound(at.x, at.y + 2, at.z, sound, SoundSource.PLAYERS, 1, HopperSounds.pitch(p[0].trim(), pitch, player.getRandom()), false);
        }
        for (ParticleKeyframeData d : a.keyFrames().particles()) {
            if (d.getStartTick() <= before || d.getStartTick() > tick) continue;
            ParticleOptions type = switch (d.getEffect()) {
                case "gilt_flakes" -> new DustParticleOptions(new Vector3f(0.95f, 0.78f, 0.3f), 0.8f);
                case "bubble" -> ParticleTypes.BUBBLE;
                case "splash" -> ParticleTypes.SPLASH;
                default -> null;
            };
            if (type == null) continue;
            double y = at.y + (c.foldAt >= 0 ? 0.3 : 46 / 16.0);
            for (int i = 0; i < 6; i++) {
                player.level().addParticle(type, at.x + (player.getRandom().nextDouble() - 0.5) * 1.6, y, at.z + (player.getRandom().nextDouble() - 0.5) * 1.6,
                        0, 0.02, 0);
            }
        }
    }

    // --- the model -------------------------------------------------------------------------------------------------

    /** The parachute's baked model and clips, posed straight from GeckoLib's baked keyframes (no controller runs). */
    static final class Model {
        final BakedGeoModel baked;
        final Map<String, Animation> clips = new HashMap<>();
        final Map<String, GeoBone> bones = new HashMap<>();
        final GeoBone[] all;
        final float[][] built;
        String posedClip = "";
        double posedTick = Double.NaN;

        Model(BakedGeoModel baked, BakedAnimations animations) {
            this.baked = baked;
            ArrayDeque<GeoBone> todo = new ArrayDeque<>(baked.topLevelBones());
            java.util.List<GeoBone> list = new java.util.ArrayList<>();
            while (!todo.isEmpty()) {
                GeoBone b = todo.poll();
                bones.put(b.getName(), b);
                list.add(b);
                todo.addAll(b.getChildBones());
            }
            all = list.toArray(new GeoBone[0]);
            built = new float[all.length][];
            for (int i = 0; i < all.length; i++) {
                GeoBone b = all[i];
                built[i] = new float[]{b.getRotX(), b.getRotY(), b.getRotZ(), b.getPosX(), b.getPosY(), b.getPosZ(), b.getScaleX(), b.getScaleY(), b.getScaleZ()};
            }
            for (Animation a : animations.animations().values()) clips.put(a.name(), a);
        }

        void pose(String clip, double tick) {
            Animation a = clips.get(clip);
            if (a == null) return;
            tick = a.loopType() == Animation.LoopType.LOOP ? ((tick % a.length()) + a.length()) % a.length() : Mth.clamp(tick, 0, a.length());
            if (clip.equals(posedClip) && tick == posedTick) return;
            posedClip = clip;
            posedTick = tick;
            Map<GeoBone, float[]> rest = new HashMap<>();
            for (int i = 0; i < all.length; i++) {
                float[] p = built[i];
                all[i].updateRotation(p[0], p[1], p[2]);
                all[i].updatePosition(p[3], p[4], p[5]);
                all[i].updateScale(p[6], p[7], p[8]);
                rest.put(all[i], p);
            }
            for (BoneAnimation t : a.boneAnimations()) {
                GeoBone b = bones.get(t.boneName());
                if (b == null) continue;
                float[] p = rest.get(b);
                var rot = t.rotationKeyFrames();
                if (!rot.xKeyframes().isEmpty()) b.setRotX((float) ModuleLayer.value(rot.xKeyframes(), tick, true, Direction.Axis.X) + p[0]);
                if (!rot.yKeyframes().isEmpty()) b.setRotY((float) ModuleLayer.value(rot.yKeyframes(), tick, true, Direction.Axis.Y) + p[1]);
                if (!rot.zKeyframes().isEmpty()) b.setRotZ((float) ModuleLayer.value(rot.zKeyframes(), tick, true, Direction.Axis.Z) + p[2]);
                var pos = t.positionKeyFrames();
                if (!pos.xKeyframes().isEmpty()) b.setPosX((float) ModuleLayer.value(pos.xKeyframes(), tick, false, Direction.Axis.X));
                if (!pos.yKeyframes().isEmpty()) b.setPosY((float) ModuleLayer.value(pos.yKeyframes(), tick, false, Direction.Axis.Y));
                if (!pos.zKeyframes().isEmpty()) b.setPosZ((float) ModuleLayer.value(pos.zKeyframes(), tick, false, Direction.Axis.Z));
                var scale = t.scaleKeyFrames();
                if (!scale.xKeyframes().isEmpty()) b.setScaleX((float) ModuleLayer.value(scale.xKeyframes(), tick, false, Direction.Axis.X));
                if (!scale.yKeyframes().isEmpty()) b.setScaleY((float) ModuleLayer.value(scale.yKeyframes(), tick, false, Direction.Axis.Y));
                if (!scale.zKeyframes().isEmpty()) b.setScaleZ((float) ModuleLayer.value(scale.zKeyframes(), tick, false, Direction.Axis.Z));
            }
        }
    }

    private static Model loaded;

    /** The model as loaded (again after a resource reload bakes a new one), or null before GeckoLib has it. */
    static Model model() {
        BakedGeoModel baked = GeckoLibCache.getBakedModels().get(GEO);
        BakedAnimations animations = GeckoLibCache.getBakedAnimations().get(ANIMATIONS);
        if (baked == null || animations == null) return null;
        if (loaded == null || loaded.baked != baked) loaded = new Model(baked, animations);
        return loaded;
    }

    /** What the dev harness reports: each shown parachute's clip and tick. */
    public static String describe() {
        StringBuilder out = new StringBuilder();
        for (var e : CHUTES.entrySet()) out.append(e.getKey()).append(' ').append(e.getValue().clip).append(String.format(" %.1f; ", e.getValue().tick));
        return out.length() == 0 ? "none" : out.toString();
    }
}
