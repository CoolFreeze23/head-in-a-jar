package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An animated mecha frame (the Hopper, the Bulwark) drawn by GeckoLib from its exported model: the real Life Support Jar and the
 * pilot's head at {@code jar_anchor} (with the jar's helmet and words), every fitted module at its socket from its own
 * model ({@link ModuleLayer}), the lit lamp caps through the glow mask.
 * <p>
 * A parked, unpiloted machine runs no controllers: it is given a parked pose recorded once from a machine that stood
 * still, and nothing is written to the bones at all while one parked machine after another is drawn.
 */
public class HopperRenderer extends GeoEntityRenderer<MechaEntity> {
    /**
     * The cheap path (on). Off = every machine animates every frame and the whole model is drawn a second time through
     * the glow mask, as the first version did; kept switchable so the two can be measured in one run.
     */
    public static boolean LEAN = true;
    /** The model each animated frame is drawn with (its bones are what the effects read positions from). */
    static final Map<MechaFrame, FrameModel> MODELS = new HashMap<>();

    public HopperRenderer(EntityRendererProvider.Context context, MechaFrame frame) {
        super(context, MODELS.computeIfAbsent(frame, FrameModel::new));
        shadowRadius = frame.width * 0.5f;
        addRenderLayer(new Parts(this));
        // the Chimera's veins in the grafted creature's blood colour, for the minute the graft lasts
        if (MODELS.get(frame).rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.GRAFT) addRenderLayer(new Veins(this, frame));
        // the first version's glow, the whole model again through the glow mask: only when LEAN is off
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack pose, MechaEntity m, BakedGeoModel model, RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                               float partialTick, int light, int overlay) {
                if (!LEAN) super.render(pose, m, model, type, buffers, buffer, partialTick, light, overlay);
            }
        });
    }

    /** The machine being drawn now (one renderer draws every machine of its frame in turn). */
    private MechaEntity drawing;
    private float drawingPartial;
    /**
     * Bones whose own cubes are left out for the pilot's own first-person eye this draw ({@code MechaRig.eyeInside}: the
     * Kite's eye sits inside its body). With {@code MechaRig.firstPersonEye} (where the eye is) and the hat left off for
     * the same eye, this is the one first-person rule: what the pilot looks out of is not drawn for that pilot.
     */
    private java.util.Set<String> eyeInside = java.util.Set.of();

    @Override
    public void render(MechaEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // a parked machine is drawn from what was kept of it when it parked (ParkedDraw); only its name tag and lead here
        MultiBufferSource source = ParkedDraw.begin(this, entity, partialTick, pose, buffers, light);
        if (source == null) {
            renderFinal(pose, entity, null, buffers, null, partialTick, light, OverlayTexture.NO_OVERLAY, -1);
            return;
        }
        drawing = entity;
        drawingPartial = partialTick;
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(entity.frame());
        eyeInside = rig != null && !rig.eyeInside.isEmpty() && pilotsEye(entity) ? rig.eyeInside : java.util.Set.of();
        super.render(entity, entityYaw, partialTick, pose, source, light);
        drawing = null;
        // the hot vents again at full bright, tinted by the heat (kept while the frame was drawn, drawn after it)
        if (!VENTS.isEmpty()) {
            int glow = MechaWear.ventGlow(entity);
            var vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(entity)));
            PoseStack at = new PoseStack();
            for (Object[] v : VENTS) {
                at.last().pose().set(((PoseStack.Pose) v[0]).pose());
                at.last().normal().set(((PoseStack.Pose) v[0]).normal());
                super.renderCubesOfBone(at, (GeoBone) v[1], vc, 0xF000F0, OverlayTexture.NO_OVERLAY, glow);
            }
            VENTS.clear();
        }
        eyeInside = java.util.Set.of();
        // the modules kept at their sockets while the frame was drawn, in batches of their own
        ModuleLayer.flush(this, entity, partialTick, source);
        ParkedDraw.end(entity);
        FrameModel model = MODELS.get(entity.frame());
        if (model == null) return;
        // what code added on top of the clips this frame must not stay: GeckoLib leaves a bone it sees marked as changed
        // where it is instead of easing it back, so a value added each frame to a bone no clip keys would pile up
        model.clearMarks();
        // one model serves every machine: keep this one's tracked bones for its effects (a still machine has none)
        if (LEAN && entity.view.still) return;
        for (String name : model.rig.tracked) {
            GeoBone b = model.cached(name);
            if (b != null) entity.view.bones.computeIfAbsent(name, k -> new org.joml.Matrix4f()).set(b.getWorldSpaceMatrix());
        }
        // the Dragline, from the gun's muzzle to the anchor
        if (com.coolfreeze23.headinajar.mecha.Skitter.out(entity)) DraglineRibbon.draw(entity, partialTick, pose, buffers);
    }

    /** The name tag and the lead: drawn every frame, never kept with a parked machine's look. */
    @Override
    public void renderFinal(PoseStack pose, MechaEntity m, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                            int light, int overlay, int colour) {
        ParkedDraw.pause();
        super.renderFinal(pose, m, model, buffers, buffer, partialTick, light, overlay, colour);
        ParkedDraw.resume();
    }

    /** A machine off screen whose line is not still shows its line. */
    @Override
    public boolean shouldRender(MechaEntity m, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(m, frustum, x, y, z)) return true;
        return com.coolfreeze23.headinajar.mecha.Skitter.out(m) && frustum.isVisible(new net.minecraft.world.phys.AABB(m.position(), m.anchor()).inflate(0.5));
    }

    /**
     * A Gene Port's serum is drawn in its serum's colour (the painted fill is a light grey to be multiplied), dimmed
     * while its gene rests after firing, and lit up for a moment when it pulses (the clips' {@code gene:pulse:<n>}, or
     * its gene firing).
     */
    /** The vent bones' poses kept this draw for their glow (pose, bone). */
    private static final List<Object[]> VENTS = new ArrayList<>();

    @Override
    public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
        // the pilot's own first-person eye sits inside these bones: they are not drawn for it
        if (!eyeInside.isEmpty() && eyeInside.contains(bone.getName())) return;
        MechaEntity m = drawing;
        if (m != null && bone.getName().startsWith("vent_") && !bone.isHidden() && MechaWear.ventGlow(m) != 0) VENTS.add(new Object[]{pose.last().copy(), bone});
        if (m != null && bone.getName().equals(MODELS.get(m.frame()).rig.hatBone) && (wearsHelmet(m) || pilotsEye(m))) {
            // the jar wears the Hat Mount's helmet where the frame's own hat would sit: the hat is not drawn, its strut is
            int keep = MODELS.get(m.frame()).rig.hatKeeps;
            var cubes = bone.getCubes();
            for (int i = 0; i < keep && i < cubes.size(); i++) {
                pose.pushPose();
                renderCube(pose, cubes.get(i), buffer, light, overlay, colour);
                pose.popPose();
            }
            return;
        }
        if (m != null && bone.getName().startsWith("gene_port_")) {
            int n = bone.getName().charAt(bone.getName().length() - 1) - '0';
            float[] tint = GenePorts.tint(m, n, drawingPartial);
            colour = net.minecraft.util.FastColor.ARGB32.color(255, (int) (tint[0] * 255), (int) (tint[1] * 255), (int) (tint[2] * 255));
            if (tint[3] > 0) light = 0xF000F0;
        }
        super.renderCubesOfBone(pose, bone, buffer, light, overlay, colour);
    }

    /**
     * The local pilot looking out in first person: their eye is under the hat, so the hat (like their own jar) is not
     * drawn for them; it would fill the view.
     */
    static boolean pilotsEye(MechaEntity m) {
        var mc = Minecraft.getInstance();
        return m.getFirstPassenger() == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    /** The pilot's jar wears the Hat Mount's helmet (drawn by the jar's own code on the lid). */
    static boolean wearsHelmet(MechaEntity m) {
        if (!(m.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player pilot) || !drawsPilot(pilot)) return false;
        JarState state = pilot.getData(ModRegistry.JAR_STATE);
        return state.fitted(com.coolfreeze23.headinajar.module.ModuleType.HAT_MOUNT)
                && !pilot.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
    }

    /**
     * The Chimera's veins after a Graft: the model again through the vein overlay (only the vein pixels, a light
     * neutral), tinted with the grafted creature's blood colour and lit, for as long as the grafted gene lasts.
     * Outside that minute nothing is drawn and the sheet's own teal veins show.
     */
    static class Veins extends GeoRenderLayer<MechaEntity> {
        private final RenderType type;

        Veins(GeoRenderer<MechaEntity> renderer, MechaFrame frame) {
            super(renderer);
            type = RenderType.entityCutoutNoCull(HeadInAJar.id("textures/entity/mecha/" + frame.id() + "_veins.png"));
        }

        @Override
        public void render(PoseStack pose, MechaEntity m, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                           float partialTick, int light, int overlay) {
            if (com.coolfreeze23.headinajar.mecha.Chimera.grafted(m) == null) return;
            int c = com.coolfreeze23.headinajar.mecha.Chimera.tint(m);
            getRenderer().reRender(model, pose, buffers, m, type, buffers.getBuffer(type), partialTick, 0xF000F0, overlay, 0xFF000000 | c);
        }
    }

    /**
     * A flier rolls into its turns and noses down in a dive (its clips are authored level): the whole machine, with what
     * it carries, turned about its feet after its facing. (On the pose, not a bone: a bone no clip keys keeps what code
     * gave it, so adding to it every frame would wind it round.)
     */
    @Override
    protected void applyRotations(MechaEntity m, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        // on a working Assembly Gantry's turntable the frame turns with it, by the same function as the turntable
        super.applyRotations(m, pose, ageInTicks, GantryClient.yaw(m, partialTick, rotationYaw), partialTick, nativeScale);
        var v = m.view;
        if (v.roll != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-v.roll));
        if (v.pitch != 0) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-v.pitch));
    }

    /** A machine topples by its own clip, not by the vanilla death turn. */
    @Override
    protected float getDeathMaxRotation(MechaEntity animatable) {
        return 0;
    }

    /**
     * The red flash is for the blow (hurt time) only: a machine collapses by its own clip over 40 ticks and is not
     * tinted red throughout, as a dying mob is.
     */
    @Override
    public int getPackedOverlay(MechaEntity animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0));
    }

    /** Draw fitted modules after the frame, in batches (on), or each in the middle of the frame (off): for measuring. */
    public static void deferModules(boolean on) {
        ModuleLayer.DEFER = on;
    }

    /** Module sheets and masks on the block atlas's shared buffers (on), or each in its own (off, for measuring). */
    public static void modulesOnAtlas(boolean on) {
        ModuleLayer.ATLAS = on;
    }

    /** For the dev harness: items drawn in a Manipulator Hand since the game started. */
    public static int handItemsDrawn() {
        return ModuleLayer.handItems;
    }

    /** Every module file put at a socket of an animated frame since the game started, as {@code frame:file} (sorted). */
    public static java.util.List<String> modulesSeen() {
        return ModuleLayer.SEEN.stream().sorted().toList();
    }

    static GeoBone bone(MechaFrame frame, String name) {
        FrameModel model = MODELS.get(frame);
        return model == null ? null : model.cached(name);
    }

    /** The frame's geo, clips and sheet, by its id; the sensor takes the pilot's look on top of the clips. */
    static class FrameModel extends GeoModel<MechaEntity> {
        private final ResourceLocation geo, texture, animation;
        final com.coolfreeze23.headinajar.mecha.MechaRig rig;
        private BakedGeoModel bonesOf;
        private final Map<String, GeoBone> byName = new HashMap<>();
        private GeoBone[] all = new GeoBone[0];
        /** The parked pose, recorded once from a machine that had stood still for a while: rot, pos, scale per bone. */
        private float[][] stillPose;
        /** The bones hold the parked pose now (the last machine drawn was a still one). */
        private boolean bonesStill;

        FrameModel(MechaFrame frame) {
            rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(frame);
            geo = HeadInAJar.id("geo/entity/mecha/" + frame.id() + ".geo.json");
            texture = HeadInAJar.id("textures/entity/mecha/" + frame.id() + ".png");
            animation = HeadInAJar.id("animations/entity/mecha/" + frame.id() + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(MechaEntity animatable) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(MechaEntity animatable) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(MechaEntity animatable) {
            return animation;
        }

        void clearMarks() {
            for (GeoBone b : all) b.resetStateChanges();
        }

        /** The parked pose has been recorded: a parked machine is shown in it without running anything. */
        boolean settled() {
            cached("root");
            return stillPose != null;
        }

        /** A bone by name, from a table rebuilt only when the model is (re)loaded. */
        GeoBone cached(String name) {
            BakedGeoModel baked = getBakedModel(geo);
            if (baked != bonesOf) {
                bonesOf = baked;
                byName.clear();
                List<GeoBone> list = new ArrayList<>();
                ArrayDeque<GeoBone> todo = new ArrayDeque<>(baked.topLevelBones());
                while (!todo.isEmpty()) {
                    GeoBone b = todo.poll();
                    byName.put(b.getName(), b);
                    list.add(b);
                    todo.addAll(b.getChildBones());
                }
                all = list.toArray(new GeoBone[0]);
                for (String n : rig.tracked) if (byName.containsKey(n)) byName.get(n).setTrackingMatrices(true);
                stillPose = null;
                bonesStill = false;
            }
            return byName.get(name);
        }

        @Override
        public void handleAnimations(MechaEntity m, long instanceId, AnimationState<MechaEntity> state, float partialTick) {
            cached("root");
            boolean still = LEAN && HopperAnimation.still(m, m.level().getGameTime() + partialTick);
            m.view.still = still;
            if (still && stillPose != null) {
                // nothing runs for a parked machine: the bones already hold (or are given once) the recorded parked pose
                if (!bonesStill) {
                    for (int i = 0; i < all.length; i++) {
                        float[] p = stillPose[i];
                        all[i].updateRotation(p[0], p[1], p[2]);
                        all[i].updatePosition(p[3], p[4], p[5]);
                        all[i].updateScale(p[6], p[7], p[8]);
                    }
                    bonesStill = true;
                }
                // the vials' fill is the game's, never a clip's: set on every machine after its pose, parked or not
                GenePorts.scale(m, this);
                return;
            }
            super.handleAnimations(m, instanceId, state, partialTick);
            GenePorts.scale(m, this);
            bonesStill = false;
            if (!still) {
                m.view.stillFrames = 0;
            } else if (++m.view.stillFrames > 10) {
                // this machine has shown the parked pose, fully settled, for ten frames: record it for every parked one
                float[][] pose = new float[all.length][];
                for (int i = 0; i < all.length; i++) {
                    GeoBone b = all[i];
                    pose[i] = new float[]{b.getRotX(), b.getRotY(), b.getRotZ(), b.getPosX(), b.getPosY(), b.getPosZ(), b.getScaleX(), b.getScaleY(), b.getScaleZ()};
                }
                stillPose = pose;
            }
        }

        @Override
        public void setCustomAnimations(MechaEntity m, long instanceId, AnimationState<MechaEntity> state) {
            float partial = state.getPartialTick();
            var v = m.view;
            // the chin sensor takes the pilot's look (yaw against the body, and pitch), chased at k 12; not far off
            float yaw = 0, pitch = 0;
            if (m.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player pilot && m.isAlive() && !v.far) {
                float head = Mth.rotLerp(partial, pilot.yHeadRotO, pilot.yHeadRot), body = Mth.rotLerp(partial, m.yBodyRotO, m.yBodyRot);
                yaw = Mth.clamp(Mth.wrapDegrees(head - body), -rig.lookYawLimit, rig.lookYawLimit);
                pitch = Mth.clamp(-Mth.lerp(partial, pilot.xRotO, pilot.getXRot()), rig.lookPitchMin, rig.lookPitchMax);
            }
            float k = (float) (1 - Math.exp(-v.frameDelta() * 12 / 20.0));
            v.sensorYaw += (yaw - v.sensorYaw) * k;
            v.sensorPitch += (pitch - v.sensorPitch) * k;
            if (Math.abs(v.sensorYaw) < 0.01f) v.sensorYaw = 0;
            if (Math.abs(v.sensorPitch) < 0.01f) v.sensorPitch = 0;
            // GeckoLib turns code-set x and y rotations the way its own head-turn example does: degrees negated
            GeoBone sensor = cached(rig.lookYawBone), tilt = cached(rig.lookPitchBone);
            if (sensor != null && v.sensorYaw != 0) sensor.setRotY(sensor.getRotY() - v.sensorYaw * Mth.DEG_TO_RAD);
            if (tilt != null && v.sensorPitch != 0) tilt.setRotX(tilt.getRotX() + v.sensorPitch * Mth.DEG_TO_RAD);
            if (rig.wheels != null) {
                // the wheeled skeleton's code: spin, steering, lean, ride, tilt, drift, brake lamps
                var w = v.wheels;
                double now = m.level().getGameTime() + partial;
                long rush = m.rushAt();
                boolean refused = rush >= 0 && (rush & 3) == com.coolfreeze23.headinajar.mecha.Wheelhouse.REFUSED
                        && now - rush / 4 < com.coolfreeze23.headinajar.mecha.Wheelhouse.REFUSED_TICKS;
                boolean braking = w.slowing || refused || w.brakeAt >= 0 && now - w.brakeAt < rig.wheels.brakeLength();
                boolean steering = com.coolfreeze23.headinajar.mecha.Wheels.onTheSpot(w) == 0 && !HopperAnimation.drifting(m, w);
                WheelBones.apply(rig.wheels, w, this::cached, steering, braking && HopperAnimation.piloted(m), !m.isDeadOrDying());
            }
            GeoBone torso = cached("torso");
            if (v.stopLean > 0 && torso != null) torso.setRotX(torso.getRotX() - v.stopLean * Mth.DEG_TO_RAD);
            // a dying machine has already thrown its pilot clear: the Executive's sunroof stands open over the empty seat
            GeoBone sunroof = m.isDeadOrDying() ? cached("sunroof") : null;
            if (sunroof != null) {
                float open = Mth.clamp((m.deathTime + partial) / 3f, 0, 1);
                sunroof.setRotX(SUNROOF_OPEN * Mth.DEG_TO_RAD * open * (2 - open));
            }
            // the flinch is authored for a blow from the left: mirrored for one from the right
            long hit = m.hitAt();
            GeoBone root = cached("root");
            if (root != null && m.hurtTime > 0 && hit >= 0 && (hit & 1) == 1) root.setRotZ(-root.getRotZ());
            if (rig.legs != null) {
                // the multi-leg skeleton: pads on the ground under them, onto the wall it climbs, the Dragline
                double now = m.level().getGameTime() + partial;
                float yawBody = Mth.rotLerp(partial, m.yBodyRotO, m.yBodyRot);
                double turned = rig.climbs ? v.climb.turned(now, WALL_TURN) : 0;
                // dragged on its line over the ground (a pad still within reach of a surface) it keeps its stance too
                boolean grounded = m.isAlive() && turned <= 0 && !v.far
                        && (m.onGround() || com.coolfreeze23.headinajar.mecha.Skitter.out(m) && com.coolfreeze23.headinajar.mecha.Legs.canStand(m));
                LegBones.ground(rig.legs, m, v.legs, this::cached, grounded, partial, v.frameDelta());
                if (turned > 0) LegBones.wall(root, v.climb.turningFace(), yawBody, turned, rig.legs.footReach(), m.getBbWidth() * 8);
                if (rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.DRAGLINE) dragline(m, root, yawBody, partial, now);
            }
        }

        /** Ticks the model takes to turn onto a wall and back. */
        static final int WALL_TURN = 6;

        /**
         * The Dragline on the model: the gun aimed at the anchor (from the press, through the fire, while the line is
         * out), the reel turned by the line it winds in or pays out, and a hanging machine turned along its line.
         */
        private void dragline(MechaEntity m, GeoBone root, float yawBody, float partial, double now) {
            var v = m.view;
            long line = m.lineAt();
            boolean out = com.coolfreeze23.headinajar.mecha.Skitter.out(m);
            boolean aiming = out || line >= 0 && (line & 3) == com.coolfreeze23.headinajar.mecha.Skitter.FIRED
                    && now - line / 4 < com.coolfreeze23.headinajar.mecha.Skitter.FIRE_TICKS;
            float yaw = 0, pitch = 0;
            net.minecraft.world.phys.Vec3 anchor = m.anchor();
            double x = Mth.lerp(partial, m.xo, m.getX()), y = Mth.lerp(partial, m.yo, m.getY()), z = Mth.lerp(partial, m.zo, m.getZ());
            if (aiming) {
                double[] w = com.coolfreeze23.headinajar.mecha.Legs.world(x, z, yawBody, 11.5, -8.5);
                org.joml.Vector3f d = LegBones.toModel(anchor.x - w[0], anchor.y - (y + 19.5 / 16), anchor.z - w[1], yawBody);
                yaw = Mth.clamp((float) Math.toDegrees(Math.atan2(-d.x, -d.z)), -80, 80);
                pitch = Mth.clamp((float) Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -30, 60);
            }
            float k = (float) (1 - Math.exp(-v.frameDelta() * 12 / 20.0));
            v.aimYaw += (yaw - v.aimYaw) * k;
            v.aimPitch += (pitch - v.aimPitch) * k;
            GeoBone gun = cached("launcher");
            if (gun != null) {
                gun.setRotY(gun.getRotY() + v.aimYaw * Mth.DEG_TO_RAD);
                gun.setRotX(gun.getRotX() + v.aimPitch * Mth.DEG_TO_RAD);
            }
            int mode = m.lineMode();
            GeoBone reel = cached("reel");
            if (reel != null && out && (mode == com.coolfreeze23.headinajar.mecha.Skitter.REEL || mode == com.coolfreeze23.headinajar.mecha.Skitter.RAPPEL)) {
                reel.setRotX(v.reelAngle * Mth.DEG_TO_RAD);
            }
            // hanging: the body's up toward the anchor (to 60 degrees), round the hang point over the reel
            net.minecraft.world.phys.Vec3 up = new net.minecraft.world.phys.Vec3(0, 1, 0);
            // tilted toward the anchor only once it hangs clear (no pad within reach of a surface); eased by the chase below
            if (out && mode != com.coolfreeze23.headinajar.mecha.Skitter.SLACK && !m.onGround() && v.climb.face == null
                    && !com.coolfreeze23.headinajar.mecha.Legs.canStand(m)) {
                double[] h = com.coolfreeze23.headinajar.mecha.Legs.world(x, z, yawBody, 0, 14);
                org.joml.Vector3f d = LegBones.toModel(anchor.x - h[0], anchor.y - (y + 18 / 16.0), anchor.z - h[1], yawBody);
                if (d.lengthSquared() > 1e-4) up = new net.minecraft.world.phys.Vec3(d.x, d.y, d.z).normalize();
            }
            float kl = (float) (1 - Math.exp(-v.frameDelta() * 8 / 20.0));
            v.lineUp = v.lineUp.add(up.subtract(v.lineUp).scale(kl)).normalize();
            if (v.lineUp.y < 0.9999) LegBones.hang(root, v.lineUp, new org.joml.Vector3f(0, 18, 14), 60);
        }
    }

    /** The Executive's sunroof fully open (degrees, as GeckoLib applies the eject clip's -96 on x). */
    static final float SUNROOF_OPEN = 96;

    /** The pilot's own player is not drawn while in an animated frame: the frame draws the real jar at its anchor. */
    public static boolean drawsPilot(net.minecraft.world.entity.player.Player player) {
        return player.getVehicle() instanceof MechaEntity m && m.frame().animated() && m.getFirstPassenger() == player
                && player.getData(ModRegistry.JAR_STATE).form() == Form.PILOT;
    }

    /** What the frame carries on its bones: the jar at its anchor, modules at their sockets, the lit lamp caps. */
    static class Parts extends GeoRenderLayer<MechaEntity> {
        Parts(GeoRenderer<MechaEntity> renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(PoseStack pose, MechaEntity m, GeoBone bone, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                  float partialTick, int light, int overlay) {
            String name = bone.getName();
            if (name.startsWith("socket_")) module(pose, m, bone, buffers, partialTick, light, overlay);
            else if (name.equals("jar_anchor")) jar(pose, m, bone, buffers, partialTick, light);
            else if (LEAN && !m.view.still && com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame()).lamps.contains(name) && bone.getScaleY() > 0.01f && !bone.isHidden()) {
                // the lit cap again through the glow mask (the first version drew the whole model a second time for this)
                RenderType glow = AutoGlowingTexture.getRenderType(getTextureResource(m));
                getRenderer().renderCubesOfBone(pose, bone, buffers.getBuffer(glow), 0xF000F0, overlay, 0xFFFFFFFF);
            }
        }

        /** The real jar and head, drawn by the jar's own code at {@code jar_anchor}, with what the jar carries. */
        private static void jar(PoseStack pose, MechaEntity m, GeoBone bone, MultiBufferSource buffers, float partialTick, int light) {
            if (bone.isHidden() || bone.getScaleY() < 0.01f) return;
            if (!(m.getFirstPassenger() instanceof AbstractClientPlayer pilot) || !drawsPilot(pilot)) return;
            var mc = Minecraft.getInstance();
            // the pilot's own eyes sit above the jar: in first person they see the hoop and the world, not their own head
            if (pilot == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
            JarState state = pilot.getData(ModRegistry.JAR_STATE);
            float body = Mth.rotLerp(partialTick, m.yBodyRotO, m.yBodyRot);
            float head = Mth.rotLerp(partialTick, pilot.yHeadRotO, pilot.yHeadRot);
            float pitch = Mth.lerp(partialTick, pilot.xRotO, pilot.getXRot());
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, bone);
            JarRenderer.draw(pose, buffers, light, LivingEntityRenderer.getOverlayCoords(pilot, 0), pilot.getSkin().texture(), head - body, pitch,
                    pilot.tickCount + partialTick, state.charge() > 0, !state.goggles().isEmpty(), JarRenderer.shape(state.compact()), false,
                    JarRenderer.Vitals.of(state, pilot.getId(), 180.0f - body));
            // the Hat Mount's helmet and the Speaker Grille's words, by the jar's own code, as on any jar
            JarRenderer.helmet(pilot, state, pose, buffers, light);
            JarRenderer.speech(state, pose, buffers, light);
            pose.popPose();
        }

        @SuppressWarnings("unchecked")
        private static Object fit(MechaEntity m, String socket) {
            var v = m.view;
            Object key = m.modulesKey();
            if (v.fitsFor != key || v.fits == null) {
                v.fitsFor = key;
                v.fits = new HashMap<String, Object>();
            }
            return ((Map<String, Object>) v.fits).computeIfAbsent(socket, n -> ModuleLayer.make(m, n));
        }

        /**
         * Every fitted module at the socket for its slot, with the socket's full transform (rest, clip and scale), so it
         * points out of its mount and moves with its limb: drawn from its own model by {@link ModuleLayer}, in the size
         * the frame takes. Adding a module model or a frame never touches this code.
         */
        private void module(PoseStack pose, MechaEntity m, GeoBone bone, MultiBufferSource buffers, float partialTick, int light, int overlay) {
            Object fit = fit(m, bone.getName());
            // during the build a module shows only once its socket has appeared (the clip scales it in)
            if (fit == ModuleLayer.NONE || bone.getScaleX() < 0.01f) return;
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, bone);
            if (fit instanceof ModuleLayer.Fit f) {
                ModuleLayer.defer(pose, f, light, overlay);
                // the first version drew each module in the middle of the frame (kept switchable for measuring)
                if (!ModuleLayer.DEFER) ModuleLayer.flush(getRenderer(), m, partialTick, buffers);
            }
            pose.popPose();
        }

    }
}
