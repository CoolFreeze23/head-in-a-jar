package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.ModuleArt;
import com.coolfreeze23.headinajar.mecha.ModuleMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.math.value.Constant;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every fitted module of an animated frame, drawn from its own model at its socket: the size the frame takes (by the
 * frame's id and the module's, from {@link ModuleArt}), posed by its own clips as {@link ModuleMotion} chooses them, its
 * always-lit cubes through its glow mask, its working lights while it works, the battery's level lamps by the charge,
 * tinted fluids, and the grapple's line and flying hook. A frame brings nothing of its own but its socket bones.
 * <p>
 * No controller runs: one model serves every machine, and a clip is evaluated straight from GeckoLib's baked keyframes
 * onto the model's bones just before it is drawn, only when the pose differs from the one the bones already hold. A
 * parked machine, or one beyond {@link ModuleMotion#NEAR} blocks, shows every module at rest, so a row of parked
 * machines poses each module model once.
 */
final class ModuleLayer {
    private ModuleLayer() {
    }

    /** One size of one module as loaded: its model, its clips evaluated onto its bones, its textures and lit cubes. */
    static final class Loaded {
        final BakedGeoModel model;
        final ModuleArt.Art art;
        final RenderType base;
        final RenderType glow, firing, charge;
        final List<GeoCube> glowCubes = new ArrayList<>(), firingCubes = new ArrayList<>(), chargeCubes = new ArrayList<>();
        final Map<GeoCube, Integer> tints = new IdentityHashMap<>();
        final Map<GeoCube, Integer> lit = new IdentityHashMap<>();
        final Map<String, GeoBone> bones = new HashMap<>();
        /** The bones any clip moves, their pose as built (rot, pos, scale), and each clip's tracks by bone. */
        final GeoBone[] moved;
        final float[][] built;
        final Map<String, Animation> clips = new HashMap<>();
        final Map<String, int[]> trackBone = new HashMap<>();
        String posedClip = "\0";
        double posedTick = Double.NaN;
        /** A dark texel of the mount plate (the grapple's line is drawn with it). */
        final float lineU, lineV;
        /** The bone a ray or stream leaves from and the muzzle's place in the model (blocks, as GeckoLib draws it), or null. */
        final String muzzleBone;
        final Vector3f muzzlePx;

        Loaded(BakedGeoModel model, ModuleArt.Art art, MechaModule module) {
            this.model = model;
            this.art = art;
            String name = com.coolfreeze23.headinajar.mecha.ArmAim.muzzle(module);
            Object[] muzzle = locator(art.file(), name != null ? name : com.coolfreeze23.headinajar.mecha.ArmAim.grip(module));
            muzzleBone = muzzle == null ? null : (String) muzzle[0];
            muzzlePx = muzzle == null ? null : (Vector3f) muzzle[1];
            String file = art.file();
            base = RenderType.entityCutoutNoCull(tex(file));
            glow = RenderType.entityTranslucentEmissive(tex(file + "_glowmask"));
            firing = RenderType.entityTranslucentEmissive(tex(file + "_firing"));
            charge = RenderType.entityTranslucentEmissive(tex(file + "_charge"));
            SHEET.put(base, tex(file));
            SHEET.put(glow, tex(file + "_glowmask"));
            SHEET.put(firing, tex(file + "_firing"));
            SHEET.put(charge, tex(file + "_charge"));
            ArrayDeque<GeoBone> todo = new ArrayDeque<>(model.topLevelBones());
            while (!todo.isEmpty()) {
                GeoBone b = todo.poll();
                bones.put(b.getName(), b);
                todo.addAll(b.getChildBones());
            }
            resolve(art.glow(), glowCubes, 1);
            resolve(art.firing(), firingCubes, 2);
            resolve(art.charge(), chargeCubes, 4);
            for (ModuleArt.Cube c : art.tint()) {
                GeoCube cube = cube(c);
                if (cube != null) tints.put(cube, 0xFF000000 | c.tint());
            }
            BakedAnimations anims = GeckoLibCache.getBakedAnimations().get(HeadInAJar.id("animations/entity/mecha/module/" + file + ".animation.json"));
            List<GeoBone> movedList = new ArrayList<>();
            if (anims != null) {
                for (Animation a : anims.animations().values()) {
                    clips.put(a.name(), a);
                    int[] index = new int[a.boneAnimations().length];
                    for (int i = 0; i < index.length; i++) {
                        GeoBone b = bones.get(a.boneAnimations()[i].boneName());
                        if (b != null && !movedList.contains(b)) movedList.add(b);
                        index[i] = b == null ? -1 : movedList.indexOf(b);
                    }
                    trackBone.put(a.name(), index);
                }
            }
            moved = movedList.toArray(new GeoBone[0]);
            built = new float[moved.length][];
            for (int i = 0; i < moved.length; i++) {
                // no controller ever runs on a module model, so GeckoLib never snapshots it: read it as baked (first use, unposed)
                GeoBone b = moved[i];
                built[i] = new float[]{b.getRotX(), b.getRotY(), b.getRotZ(), b.getPosX(), b.getPosY(), b.getPosZ(), b.getScaleX(), b.getScaleY(), b.getScaleZ()};
            }
            float u = 0, v = 0;
            GeoBone mount = bones.getOrDefault("mount", model.topLevelBones().getFirst());
            if (!mount.getCubes().isEmpty()) {
                for (GeoQuad q : mount.getCubes().getFirst().quads()) {
                    if (q == null || q.direction() != Direction.NORTH) continue;
                    for (var vert : q.vertices()) {
                        u += vert.texU() / 4;
                        v += vert.texV() / 4;
                    }
                }
            }
            lineU = u;
            lineV = v;
        }

        private void resolve(List<ModuleArt.Cube> list, List<GeoCube> out, int kind) {
            for (ModuleArt.Cube c : list) {
                GeoCube cube = cube(c);
                if (cube == null) continue;
                out.add(cube);
                lit.merge(cube, kind, (a, b) -> a | b);
            }
        }

        private GeoCube cube(ModuleArt.Cube c) {
            GeoBone b = bones.get(c.bone());
            return b != null && c.index() < b.getCubes().size() ? b.getCubes().get(c.index()) : null;
        }

        /** Puts the bones in the clip's pose at the tick (null: as built), unless they already hold it. */
        void pose(String clip, double tick) {
            Animation a = clip == null ? null : clips.get(clip);
            if (a == null) clip = null;
            else tick = a.loopType() == Animation.LoopType.LOOP ? ((tick % a.length()) + a.length()) % a.length() : Mth.clamp(tick, 0, a.length());
            if (Objects.equals(clip, posedClip) && tick == posedTick) return;
            posedClip = clip;
            posedTick = tick;
            for (int i = 0; i < moved.length; i++) {
                float[] p = built[i];
                moved[i].updateRotation(p[0], p[1], p[2]);
                moved[i].updatePosition(p[3], p[4], p[5]);
                moved[i].updateScale(p[6], p[7], p[8]);
            }
            if (a == null) return;
            int[] index = trackBone.get(clip);
            BoneAnimation[] tracks = a.boneAnimations();
            for (int i = 0; i < tracks.length; i++) {
                if (index[i] < 0) continue;
                GeoBone b = moved[index[i]];
                float[] p = built[index[i]];
                BoneAnimation t = tracks[i];
                var rot = t.rotationKeyFrames();
                if (!rot.xKeyframes().isEmpty()) b.setRotX((float) value(rot.xKeyframes(), tick, true, Direction.Axis.X) + p[0]);
                if (!rot.yKeyframes().isEmpty()) b.setRotY((float) value(rot.yKeyframes(), tick, true, Direction.Axis.Y) + p[1]);
                if (!rot.zKeyframes().isEmpty()) b.setRotZ((float) value(rot.zKeyframes(), tick, true, Direction.Axis.Z) + p[2]);
                var pos = t.positionKeyFrames();
                if (!pos.xKeyframes().isEmpty()) b.setPosX((float) value(pos.xKeyframes(), tick, false, Direction.Axis.X));
                if (!pos.yKeyframes().isEmpty()) b.setPosY((float) value(pos.yKeyframes(), tick, false, Direction.Axis.Y));
                if (!pos.zKeyframes().isEmpty()) b.setPosZ((float) value(pos.zKeyframes(), tick, false, Direction.Axis.Z));
                var scale = t.scaleKeyFrames();
                if (!scale.xKeyframes().isEmpty()) b.setScaleX((float) value(scale.xKeyframes(), tick, false, Direction.Axis.X));
                if (!scale.yKeyframes().isEmpty()) b.setScaleY((float) value(scale.yKeyframes(), tick, false, Direction.Axis.Y));
                if (!scale.zKeyframes().isEmpty()) b.setScaleZ((float) value(scale.zKeyframes(), tick, false, Direction.Axis.Z));
            }
        }

        /** Something else (the drill's bit, the jets) set bones by hand: pose again next time. */
        void touched() {
            posedTick = Double.NaN;
        }
    }

    /**
     * A channel's value at a tick, the way GeckoLib's own controller reads its keyframes: the keyframe the tick falls
     * in, its start and end (degrees to radians, x and y negated, for a rotation that is not a baked constant), eased
     * by the keyframe's easing.
     */
    static double value(List<Keyframe<MathValue>> frames, double tick, boolean rotation, Direction.Axis axis) {
        double total = 0;
        Keyframe<MathValue> frame = null;
        double into = tick;
        for (Keyframe<MathValue> f : frames) {
            total += f.length();
            if (total > tick) {
                frame = f;
                into = tick - (total - f.length());
                break;
            }
        }
        if (frame == null) frame = frames.getLast();
        double start = frame.startValue().get(), end = frame.endValue().get();
        if (rotation) {
            if (!(frame.startValue() instanceof Constant)) {
                start = Math.toRadians(start);
                if (axis != Direction.Axis.Z) start = -start;
            }
            if (!(frame.endValue() instanceof Constant)) {
                end = Math.toRadians(end);
                if (axis != Direction.Axis.Z) end = -end;
            }
        }
        return EasingType.lerpWithOverride(new AnimationPoint(frame, into, frame.length(), start, end), null);
    }

    /**
     * A locator of a module's model (its bone, and its place in blocks with x mirrored as GeckoLib draws cubes), read
     * from the model file: GeckoLib keeps no locators. Null if the model has none of that name.
     */
    static Object[] locator(String file, String name) {
        if (name == null) return null;
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(HeadInAJar.id("geo/entity/mecha/module/" + file + ".geo.json"))) {
            var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (var g : root.getAsJsonArray("minecraft:geometry")) {
                for (var b : g.getAsJsonObject().getAsJsonArray("bones")) {
                    var bone = b.getAsJsonObject();
                    if (!bone.has("locators")) continue;
                    var l = bone.getAsJsonObject("locators").get(name);
                    if (l == null) continue;
                    var a = l.isJsonArray() ? l.getAsJsonArray() : l.getAsJsonObject().getAsJsonArray("offset");
                    return new Object[]{bone.get("name").getAsString(), new Vector3f(-a.get(0).getAsFloat() / 16, a.get(1).getAsFloat() / 16, a.get(2).getAsFloat() / 16)};
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static ResourceLocation tex(String file) {
        return HeadInAJar.id("textures/entity/mecha/module/" + file + ".png");
    }

    /** The loaded size of each module file, by its baked model (a resource reload bakes new models: loaded again). */
    private static final Map<BakedGeoModel, Loaded> LOADED = new IdentityHashMap<>();

    /** What one socket carries, worked out when the fitted modules change. */
    record Fit(MechaModule module, int slot, Loaded loaded, GeoBone mount) {
    }

    static final Fit NONE = new Fit(null, -1, null, null);

    /** The module at a socket of this machine, in the size its frame takes, or NONE. */
    static Fit make(MechaEntity m, String socket) {
        int slot = HopperSockets.slotOf(m.frame(), socket);
        MechaModule module = m.moduleAt(slot);
        if (module == null) return NONE;
        ModuleArt.Art art = ModuleArt.art(m.frame().id(), module);
        if (art == null) return NONE;
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(HeadInAJar.id("geo/entity/mecha/module/" + art.file() + ".geo.json"));
        if (model == null) return NONE;
        Loaded loaded = LOADED.computeIfAbsent(model, k -> new Loaded(k, art, module));
        String mount = HopperSockets.mountOf(socket);
        for (GeoBone top : model.topLevelBones()) {
            if (!top.getName().equals(mount)) continue;
            SEEN.add(m.frame().id() + ":" + art.file());
            return new Fit(module, slot, loaded, top);
        }
        return NONE;
    }

    /** Every module file put at a socket so far, as {@code frame:file} (for the dev harness's check). */
    static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The machine, slot, frame time and camera whose module is being walked (for its muzzle), or null. */
    private static MechaEntity muzzleOf;
    /** The claw's bone as walked this draw (the Manipulator Hand holds the pilot's item there), or null. */
    private static PoseStack.Pose gripPose;
    /** Items drawn in a hand so far (for the dev harness). */
    static int handItems;
    private static int muzzleSlot;
    private static double muzzleTime;
    private static Vec3 muzzleCam;

    private static final List<PoseStack.Pose> LIT_POSES = new ArrayList<>();
    private static final List<GeoCube> LIT_CUBES = new ArrayList<>();
    private static final List<RenderType> LIT_TYPES = new ArrayList<>();

    /** A socket's module waiting to be drawn once the frame is done: the socket's transform and its light. */
    private record Deferred(Fit fit, PoseStack.Pose pose, int light, int overlay) {
    }

    private static final List<Deferred> QUEUE = new ArrayList<>();
    /** Draw the modules after the frame (on), or each in the middle of the frame as the first version did (off, for measuring). */
    public static boolean DEFER = true;
    /**
     * Draw every module's sheet and lit masks into the block atlas's shared buffers (the sheets are in the block atlas,
     * {@code atlases/blocks.json}): every module of every machine then goes to the GPU in two batches at the end of the
     * frame, instead of one draw call per sheet and per mask. Off only for measuring.
     */
    public static boolean ATLAS = true;
    /** Each render type's texture, for drawing it on the block atlas. */
    private static final Map<RenderType, ResourceLocation> SHEET = new IdentityHashMap<>();

    /** Where a module's cubes go: its own buffer, or its sheet's place in the block atlas's shared one. */
    private static VertexConsumer buffer(MultiBufferSource buffers, RenderType type, boolean lit) {
        ResourceLocation sheet = ATLAS ? SHEET.get(type) : null;
        if (sheet == null) return buffers.getBuffer(type);
        return AtlasConsumer.of(buffers.getBuffer(lit ? net.minecraft.client.renderer.Sheets.translucentCullBlockSheet()
                : net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), sheet);
    }
    private static final PoseStack STACK = new PoseStack();

    /**
     * Keeps a socket's module to draw after the frame. Drawing it in the middle of the frame's own model would switch the
     * vertex buffer twice per socket (the module's sheet, then the frame's again), each switch a draw call of its own.
     */
    static void defer(PoseStack pose, Fit fit, int light, int overlay) {
        QUEUE.add(new Deferred(fit, pose.last().copy(), light, overlay));
    }

    /**
     * Draws this machine's kept modules: grouped by sheet (a legs dressing's four sockets in one go), then every lit
     * cube grouped by mask, so each sheet and mask is one batch.
     */
    static void flush(GeoRenderer<MechaEntity> renderer, MechaEntity m, float partialTick, MultiBufferSource buffers) {
        if (QUEUE.isEmpty()) return;
        LIT_POSES.clear();
        LIT_CUBES.clear();
        LIT_TYPES.clear();
        QUEUE.sort(java.util.Comparator.comparingInt(d -> System.identityHashCode(d.fit().loaded())));
        for (Deferred d : QUEUE) {
            STACK.pushPose();
            STACK.last().pose().set(d.pose().pose());
            STACK.last().normal().set(d.pose().normal());
            draw(renderer, STACK, m, d.fit(), partialTick, d.light(), d.overlay(), buffers);
            STACK.popPose();
        }
        QUEUE.clear();
        if (LIT_CUBES.isEmpty()) return;
        Integer[] order = new Integer[LIT_CUBES.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> System.identityHashCode(LIT_TYPES.get(i))));
        for (int i : order) {
            STACK.pushPose();
            STACK.last().pose().set(LIT_POSES.get(i).pose());
            STACK.last().normal().set(LIT_POSES.get(i).normal());
            renderer.renderCube(STACK, LIT_CUBES.get(i), buffer(buffers, LIT_TYPES.get(i), true), 0xF000F0,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            STACK.popPose();
        }
    }

    /**
     * Draws the fit at the socket: posed, walked bone by bone with the frame's renderer (tints per cube), and the
     * grapple's line and hook while they are out; its lit cubes are kept for {@link #flush}'s lit pass.
     */
    private static void draw(GeoRenderer<MechaEntity> renderer, PoseStack pose, MechaEntity m, Fit fit, float partialTick, int light, int overlay,
                             MultiBufferSource buffers) {
        Loaded l = fit.loaded();
        double now = m.level().getGameTime() + partialTick;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean far = m.distanceToSqr(cam) > ModuleMotion.NEAR * ModuleMotion.NEAR;
        ModuleMotion.Pose p = ModuleMotion.pose(m, fit.slot(), fit.module(), now, m.view.still || far);
        l.pose(p.clip(), p.tick());
        // the first three modules keep their own hand-worked motion
        if (fit.module() == MechaModule.DRILL) {
            l.model.getBone("bit").ifPresent(bit -> bit.setRotZ(HopperClient.drillAngle(m, partialTick)));
            l.touched();
        } else if (fit.module() == MechaModule.JUMP_JETS) {
            jets(m, l.model, partialTick);
            l.touched();
        }
        int lamps = 0;
        if (!l.chargeCubes.isEmpty()) lamps = (int) Math.ceil(3.0 * m.battery() / Math.max(1, m.batteryCapacity()));
        VertexConsumer vc = buffer(buffers, l.base, false);
        // the muzzle is recorded as the model is walked: every ray and stream leaves from where the tool is drawn
        muzzleOf = l.muzzleBone == null ? null : m;
        muzzleSlot = fit.slot();
        muzzleTime = now;
        muzzleCam = cam;
        gripPose = null;
        walk(renderer, pose, fit.mount(), vc, l, p.lit(), lamps, light, overlay);
        muzzleOf = null;
        if (gripPose != null && fit.module() == MechaModule.MANIPULATOR_HAND) heldItem(m, fit, l, light, buffers);
        if (fit.module() == MechaModule.GRAPPLE_HOOK) grapple(renderer, pose, m, fit, vc, now, light, overlay, cam);
    }

    private static void walk(GeoRenderer<MechaEntity> renderer, PoseStack pose, GeoBone bone, VertexConsumer vc, Loaded l, boolean working, int lamps,
                             int light, int overlay) {
        pose.pushPose();
        RenderUtil.prepMatrixForBone(pose, bone);
        if (muzzleOf != null && bone.getName().equals(l.muzzleBone)) {
            // the pose is the camera's offset from the world: the locator, through it, is where the muzzle is drawn
            Vector3f at = pose.last().pose().transformPosition(new Vector3f(l.muzzlePx));
            muzzleOf.view.muzzle[muzzleSlot] = new Vec3(at.x + muzzleCam.x, at.y + muzzleCam.y, at.z + muzzleCam.z);
            muzzleOf.view.muzzleAt[muzzleSlot] = muzzleTime;
            gripPose = pose.last().copy();
        }
        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                pose.pushPose();
                Integer tint = l.tints.get(cube);
                renderer.renderCube(pose, cube, vc, light, overlay, tint == null ? 0xFFFFFFFF : tint);
                pose.popPose();
                Integer kind = l.lit.get(cube);
                if (kind == null) continue;
                if ((kind & 1) != 0) keep(pose, cube, l.glow);
                if ((kind & 2) != 0 && working) keep(pose, cube, l.firing);
                if ((kind & 4) != 0 && l.chargeCubes.indexOf(cube) < lamps) keep(pose, cube, l.charge);
            }
        }
        if (!bone.isHidingChildren()) for (GeoBone child : bone.getChildBones()) walk(renderer, pose, child, vc, l, working, lamps, light, overlay);
        pose.popPose();
    }

    private static void keep(PoseStack pose, GeoCube cube, RenderType type) {
        LIT_POSES.add(pose.last().copy());
        LIT_CUBES.add(cube);
        LIT_TYPES.add(type);
    }

    /**
     * The grapple's line and flying hook, from the moment the deploy clip hides the hook at the launcher: out to where
     * the pilot looked (worked out on each client), two ticks on it, reeled back in. Drawn in the module's own space:
     * the target is brought into it through the socket's transform.
     */
    private static void grapple(GeoRenderer<MechaEntity> renderer, PoseStack pose, MechaEntity m, Fit fit, VertexConsumer vc, double now, int light,
                                int overlay, Vec3 cam) {
        int side = fit.slot() - m.frame().firstIndex(MechaModule.Slot.ARM);
        double out = ModuleMotion.hookOut(m, side, now);
        Vec3 to = m.view.modules.grappleTo[side];
        GeoBone hook = fit.loaded().bones.get("hook");
        if (out < 0 || to == null || hook == null) return;
        Matrix4f inverse = new Matrix4f(pose.last().pose()).invert();
        Vector4f target = inverse.transform(new Vector4f((float) (to.x - cam.x), (float) (to.y - cam.y), (float) (to.z - cam.z), 1));
        Vector3f launch = new Vector3f(hook.getPivotX() / 16f, hook.getPivotY() / 16f, hook.getPivotZ() / 16f);
        Vector3f handOff = new Vector3f(launch).add(0, 0, -40 / 16f);
        Vector3f at = new Vector3f(handOff).lerp(new Vector3f(target.x, target.y, target.z), (float) out);
        Vector3f dir = new Vector3f(at).sub(launch);
        if (dir.lengthSquared() < 1e-6f) return;
        dir.normalize();
        line(pose, vc, fit.loaded(), launch, at, light, overlay);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(new Quaternionf().rotationTo(0, 0, -1, dir.x, dir.y, dir.z));
        pose.translate(-launch.x, -launch.y, -launch.z);
        for (GeoCube cube : hook.getCubes()) {
            pose.pushPose();
            renderer.renderCube(pose, cube, vc, light, overlay, 0xFFFFFFFF);
            pose.popPose();
        }
        pose.popPose();
    }

    /** A thin dark cable between two points: two crossed ribbons, a sixth of a pixel short of a pixel wide. */
    private static void line(PoseStack pose, VertexConsumer vc, Loaded l, Vector3f a, Vector3f b, int light, int overlay) {
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f side = new Vector3f(d).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
        side.normalize(0.025f);
        Vector3f up = new Vector3f(side).cross(d).normalize(0.025f);
        PoseStack.Pose last = pose.last();
        for (Vector3f w : new Vector3f[]{side, up}) {
            quad(vc, last, a.x - w.x, a.y - w.y, a.z - w.z, a.x + w.x, a.y + w.y, a.z + w.z, b.x + w.x, b.y + w.y, b.z + w.z,
                    b.x - w.x, b.y - w.y, b.z - w.z, l, light, overlay);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, Loaded l, int light, int overlay) {
        float[][] v = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        for (float[] q : v) {
            vc.addVertex(p, q[0], q[1], q[2]).setColor(0xFFFFFFFF).setUv(l.lineU, l.lineV).setOverlay(overlay).setLight(light).setNormal(p, 0, 1, 0);
        }
    }

    /**
     * The Manipulator Hand holds what the pilot holds: the pilot's selected item, drawn in the claw at a size for the
     * frame (about twice a player's hand; the hand uses that item like a hand would), standing out of the claw along the forearm, lit like the
     * machine. Nothing is drawn for an empty hand.
     */
    private static void heldItem(MechaEntity m, Fit fit, Loaded l, int light, MultiBufferSource buffers) {
        if (!(m.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player pilot)) return;
        net.minecraft.world.item.ItemStack stack = pilot.getMainHandItem();
        if (stack.isEmpty()) return;
        handItems++;
        float size = l.art.file().endsWith("_heavy") ? 2.4f : 1.7f;
        PoseStack item = new PoseStack();
        item.last().pose().set(gripPose.pose());
        item.last().normal().set(gripPose.normal());
        item.translate(l.muzzlePx.x, l.muzzlePx.y, l.muzzlePx.z);
        item.scale(size, size, size);
        // the icon stands in the claw's plane, its grip end in the fingers and its far end out along the forearm (the
        // fixed display turns an icon half round, so a quarter turn the other way brings its top right forward)
        item.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
        item.translate(0.16, 0.12, 0);
        Minecraft.getInstance().getItemRenderer().renderStatic(pilot, stack, net.minecraft.world.item.ItemDisplayContext.FIXED, false, item,
                buffers, m.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, m.getId());
    }

    /**
     * The jets' burst: each pod kicks back 15 degrees and recovers, a tick apart (its clip, drawn from the burst's
     * time), and one pilot lamp goes out per burst spent until the machine lands.
     */
    private static void jets(MechaEntity m, BakedGeoModel model, float partialTick) {
        double t = m.view.jetAt < 0 ? 99 : m.level().getGameTime() + partialTick - m.view.jetAt;
        for (int i = 0; i < 2; i++) {
            double k = t - i;
            float kick = k < 0 || k > 5 ? 0 : (float) (k < 1 ? k : Math.max(0, 1 - (k - 1) / 4));
            model.getBone("nozzle_" + i).ifPresent(b -> b.setRotX(kick * 15 * Mth.DEG_TO_RAD));
        }
        for (int i = 0; i < 3; i++) {
            int lamp = i;
            model.getBone("pilot_" + i).ifPresent(b -> b.setHidden(lamp < m.view.jetsUsed));
        }
    }
}
