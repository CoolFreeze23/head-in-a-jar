package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.RoverAnimation;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An animated Utility Shell drawn by GeckoLib from its own model: the Rover on the wheeled skeleton (castors spun,
 * steered and riding the ground by the same code as the Wheelhouse's wheels) and the Spider Walker on the multi-leg one
 * (its eight legs on the ground under them by the same code as the Skitter's, its nozzle aimed at the web). The real
 * jar and the rider's head are drawn by the jar's own code at {@code jar_anchor} (and, for the moment the get-off clip
 * hops it off, the jar of the one who just left), the lamps lit through the glow mask. The Lunk is drawn the same way
 * from its own model and sheet (its webcam takes the rider's look; it holds the jar in its fists while it sets it down).
 */
public class RoverRenderer extends GeoEntityRenderer<ShellEntity> {
    static final Map<ShellEntity.Kind, Model> MODELS = new java.util.EnumMap<>(ShellEntity.Kind.class);
    /** The Rover's model (its effects read its bones). */
    static final Model MODEL = model(ShellEntity.Kind.ROVER);

    static Model model(ShellEntity.Kind kind) {
        return MODELS.computeIfAbsent(kind, Model::new);
    }

    public RoverRenderer(EntityRendererProvider.Context context) {
        this(context, ShellEntity.Kind.ROVER);
    }

    public RoverRenderer(EntityRendererProvider.Context context, ShellEntity.Kind kind) {
        super(context, model(kind));
        shadowRadius = kind == ShellEntity.Kind.LUNK ? 0.8f : kind == ShellEntity.Kind.SPIDER_WALKER ? 0.7f : 0.6f;
        addRenderLayer(new Parts(this));
    }

    /** A bone of a shell's model as last drawn (the effects' locators), or null. */
    static GeoBone bone(ShellEntity.Kind kind, String name) {
        Model m = MODELS.get(kind);
        return m == null ? null : m.cached(name);
    }

    @Override
    public void render(ShellEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
        Model model = model(entity.kind());
        // the code's values must not stay on the bones (GeckoLib keeps a bone marked as changed where it is)
        for (GeoBone b : model.all) b.resetStateChanges();
        var bones = entity.kind() == ShellEntity.Kind.SPIDER_WALKER ? entity.walker.bones : entity.view.bones;
        for (String name : model.tracked) {
            GeoBone b = model.cached(name);
            if (b != null) bones.computeIfAbsent(name, k -> new org.joml.Matrix4f()).set(b.getWorldSpaceMatrix());
        }
    }

    /** The shell comes apart by its own clip, not by the vanilla death turn. */
    @Override
    protected float getDeathMaxRotation(ShellEntity animatable) {
        return 0;
    }

    @Override
    public int getPackedOverlay(ShellEntity animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0));
    }

    /** The model, sheet and clips by the shell's id; the wheels' or the legs' code on top of the clips. */
    static class Model extends GeoModel<ShellEntity> {
        private final ShellEntity.Kind kind;
        private final ResourceLocation geo, texture, animation;
        /** Bones whose world matrices the effects read, and the lamps lit through the glow mask. */
        final String[] tracked;
        final Set<String> lamps = Set.of("lamp_core", "lamp_tail");
        private BakedGeoModel bonesOf;
        private final Map<String, GeoBone> byName = new HashMap<>();
        private GeoBone[] all = new GeoBone[0];
        /** The parked pose, recorded once from a Rover that stood still a while (rot, pos, scale per bone). */
        private float[][] stillPose;
        private boolean bonesStill;
        private int stillFrames;

        Model(ShellEntity.Kind kind) {
            this.kind = kind;
            String id = kind.name().toLowerCase(java.util.Locale.ROOT);
            geo = HeadInAJar.id("geo/entity/shell/" + id + ".geo.json");
            texture = HeadInAJar.id("textures/entity/shell/" + id + ".png");
            animation = HeadInAJar.id("animations/entity/shell/" + id + ".animation.json");
            tracked = kind == ShellEntity.Kind.LUNK ? com.coolfreeze23.headinajar.entity.LunkAnimation.TRACKED
                    : kind == ShellEntity.Kind.SPIDER_WALKER
                    ? new String[]{"jar_anchor", "body", "launcher", "lamp_core", "reel", "lamp_tail", "shin_fl", "shin_l2", "shin_l3", "shin_rl",
                    "shin_fr", "shin_r2", "shin_r3", "shin_rr"}
                    : new String[]{"jar_anchor", "body", "buffer", "helm", "flag"};
        }

        boolean still(ShellEntity s, double now) {
            return switch (kind) {
                case SPIDER_WALKER -> com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.still(s, now);
                case LUNK -> com.coolfreeze23.headinajar.entity.LunkAnimation.still(s, now);
                default -> RoverAnimation.still(s, now);
            };
        }

        @Override
        public ResourceLocation getModelResource(ShellEntity animatable) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(ShellEntity animatable) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(ShellEntity animatable) {
            return animation;
        }

        GeoBone cached(String name) {
            BakedGeoModel baked = getBakedModel(geo);
            if (baked != bonesOf) {
                bonesOf = baked;
                byName.clear();
                java.util.List<GeoBone> list = new java.util.ArrayList<>();
                ArrayDeque<GeoBone> todo = new ArrayDeque<>(baked.topLevelBones());
                while (!todo.isEmpty()) {
                    GeoBone b = todo.poll();
                    byName.put(b.getName(), b);
                    list.add(b);
                    todo.addAll(b.getChildBones());
                }
                all = list.toArray(new GeoBone[0]);
                stillPose = null;
                bonesStill = false;
                for (String n : tracked) if (byName.containsKey(n)) byName.get(n).setTrackingMatrices(true);
            }
            return byName.get(name);
        }

        /**
         * A parked Rover nobody rides, with nothing happening to it, runs no controllers: it is given a parked pose
         * recorded once from one that stood still, and nothing is written while one parked Rover after another is drawn.
         */
        @Override
        public void handleAnimations(ShellEntity s, long instanceId, AnimationState<ShellEntity> state, float partialTick) {
            cached("root");
            boolean still = still(s, s.level().getGameTime() + partialTick);
            if (still && stillPose != null) {
                if (!bonesStill) {
                    for (int i = 0; i < all.length; i++) {
                        float[] p = stillPose[i];
                        all[i].updateRotation(p[0], p[1], p[2]);
                        all[i].updatePosition(p[3], p[4], p[5]);
                        all[i].updateScale(p[6], p[7], p[8]);
                    }
                    bonesStill = true;
                }
                return;
            }
            super.handleAnimations(s, instanceId, state, partialTick);
            bonesStill = false;
            if (!still) {
                stillFrames = 0;
            } else if (++stillFrames > 10) {
                float[][] pose = new float[all.length][];
                for (int i = 0; i < all.length; i++) {
                    GeoBone b = all[i];
                    pose[i] = new float[]{b.getRotX(), b.getRotY(), b.getRotZ(), b.getPosX(), b.getPosY(), b.getPosZ(), b.getScaleX(), b.getScaleY(), b.getScaleZ()};
                }
                stillPose = pose;
            }
        }

        @Override
        public void setCustomAnimations(ShellEntity s, long instanceId, AnimationState<ShellEntity> state) {
            cached("root");
            if (kind == ShellEntity.Kind.SPIDER_WALKER) {
                legs(s, state.getPartialTick());
                return;
            }
            if (kind == ShellEntity.Kind.LUNK) {
                // the webcam takes the rider's look (yaw against the body, a little pitch), as a frame's sensor does
                if (s.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player p && s.isAlive()) {
                    float partial = state.getPartialTick();
                    float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot) - Mth.rotLerp(partial, s.yBodyRotO, s.yBodyRot)), -40, 40);
                    float pitch = Mth.clamp(-Mth.lerp(partial, p.xRotO, p.getXRot()), -25, 20);
                    GeoBone cam = cached("sensor");
                    if (cam != null) {
                        cam.setRotY(cam.getRotY() - yaw * Mth.DEG_TO_RAD);
                        cam.setRotX(cam.getRotX() + pitch * Mth.DEG_TO_RAD);
                    }
                }
                return;
            }
            var w = s.view.wheels;
            boolean steering = com.coolfreeze23.headinajar.mecha.Wheels.onTheSpot(w) == 0;
            WheelBones.apply(RoverAnimation.SPEC, w, this::cached, steering, null, !s.isDeadOrDying());
        }

        /**
         * The Spider Walker: its eight pads on the ground under them (it climbs upright, so on a wall or in the air the
         * clip alone poses them), the flinch mirrored for a blow from the right, and the nozzle aimed at the web.
         */
        private void legs(ShellEntity s, float partial) {
            var v = s.walker;
            double now = s.level().getGameTime() + partial;
            boolean grounded = s.isAlive() && s.onGround() && v.climb.face == null;
            LegBones.ground(com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.LEGS, s, v.legs, this::cached, grounded, partial, v.frameDelta());
            long hit = s.hitAt();
            GeoBone body = cached("body");
            if (body != null && s.hurtTime > 0 && hit >= 0 && (hit & 1) == 1) {
                body.setRotZ(-body.getRotZ());
                body.setPosX(-body.getPosX());
            }
            // the nozzle takes the aim at the web while it spits (or coughs), else the rider's look, a little
            float yaw = 0, pitch = 0;
            long web = s.webAt();
            float bodyYaw = Mth.rotLerp(partial, s.yBodyRotO, s.yBodyRot);
            if (web >= 0 && now - web / 2 < 22 && (web & 1) == 0) {
                var at = s.webTarget();
                double x = Mth.lerp(partial, s.xo, s.getX()), y = Mth.lerp(partial, s.yo, s.getY()), z = Mth.lerp(partial, s.zo, s.getZ());
                double[] w = com.coolfreeze23.headinajar.mecha.Legs.world(x, z, bodyYaw, 0, -9);
                org.joml.Vector3f d = LegBones.toModel(at.getX() + 0.5 - w[0], at.getY() + 0.5 - (y + 5.5 / 16), at.getZ() + 0.5 - w[1], bodyYaw);
                yaw = (float) Math.toDegrees(Math.atan2(-d.x, -d.z));
                pitch = (float) Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            } else if (s.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player rider) {
                yaw = -Mth.wrapDegrees(Mth.rotLerp(partial, rider.yHeadRotO, rider.yHeadRot) - bodyYaw) * 0.5f;
                pitch = -Mth.lerp(partial, rider.xRotO, rider.getXRot()) * 0.5f;
            }
            yaw = Mth.clamp(yaw, -60, 60);
            pitch = Mth.clamp(pitch, -10, 35);
            float k = (float) (1 - Math.exp(-v.frameDelta() * 12 / 20.0));
            v.aimYaw += (yaw - v.aimYaw) * k;
            v.aimPitch += (pitch - v.aimPitch) * k;
            GeoBone nozzle = cached("launcher");
            if (nozzle != null) {
                nozzle.setRotY(nozzle.getRotY() + v.aimYaw * Mth.DEG_TO_RAD);
                nozzle.setRotX(nozzle.getRotX() + v.aimPitch * Mth.DEG_TO_RAD);
            }
        }
    }

    /** The pilot rides the Rover as its jar: that player is not drawn, the Rover draws the jar at its anchor. */
    public static boolean drawsRider(net.minecraft.world.entity.player.Player player) {
        return player.getVehicle() instanceof ShellEntity s && s.animated() && s.getFirstPassenger() == player
                && player.getData(ModRegistry.JAR_STATE).form() == Form.HOSTED;
    }

    /** The player who has just got off, while the get-off clip still hops their jar off the tray. */
    public static boolean hoppingOff(net.minecraft.world.entity.player.Player player) {
        if (player.getVehicle() != null || !player.getData(ModRegistry.JAR_STATE).form().isJarred()) return false;
        var level = player.level();
        for (var e : level.getEntitiesOfClass(ShellEntity.class, player.getBoundingBox().inflate(3), ShellEntity::animated)) {
            if (lastPilot(e) == player.getId() && hop(e, level.getGameTime()) >= 0) return true;
        }
        return false;
    }

    static int lastPilot(ShellEntity s) {
        return s.kind() == ShellEntity.Kind.SPIDER_WALKER ? s.walker.lastPilot : s.view.lastPilot;
    }

    /** Ticks into the get-off clip while the jar is still on its way off, else -1. */
    static double hop(ShellEntity s, double now) {
        // the Lunk holds the jar in its fists until it lets go on the floor in front
        if (s.kind() == ShellEntity.Kind.LUNK) return com.coolfreeze23.headinajar.entity.LunkAnimation.alighting(s, now);
        long boarded = s.boardedAt();
        if (RoverAnimation.piloted(s) || boarded >= -1) return -1;
        double t = now + boarded;
        int hop = s.kind() == ShellEntity.Kind.SPIDER_WALKER ? com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.GET_OFF_HOP : RoverAnimation.GET_OFF_HOP;
        return t >= 0 && t < hop ? t : -1;
    }

    /** The jar on the tray, and the lit lamps. */
    static class Parts extends GeoRenderLayer<ShellEntity> {
        Parts(GeoRenderer<ShellEntity> renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(PoseStack pose, ShellEntity s, GeoBone bone, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                  float partialTick, int light, int overlay) {
            String name = bone.getName();
            if (name.equals("jar_anchor")) jar(pose, s, bone, buffers, partialTick, light);
            else if (model(s.kind()).lamps.contains(name) && bone.getScaleY() > 0.01f && !bone.isHidden() && !s.isDeadOrDying()) {
                RenderType glow = AutoGlowingTexture.getRenderType(getTextureResource(s));
                getRenderer().renderCubesOfBone(pose, bone, buffers.getBuffer(glow), 0xF000F0, overlay, 0xFFFFFFFF);
            }
        }

        private static void jar(PoseStack pose, ShellEntity s, GeoBone bone, MultiBufferSource buffers, float partialTick, int light) {
            if (bone.isHidden() || bone.getScaleY() < 0.01f) return;
            var mc = Minecraft.getInstance();
            AbstractClientPlayer rider = null;
            if (s.getFirstPassenger() instanceof AbstractClientPlayer p && drawsRider(p)) rider = p;
            else if (hop(s, s.level().getGameTime() + partialTick) >= 0 && mc.level.getEntity(lastPilot(s)) instanceof AbstractClientPlayer p) rider = p;
            if (rider == null) return;
            // the rider's own eyes are above the jar: in first person they see the tray and the road, not their own head
            if (rider == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
            JarState state = rider.getData(ModRegistry.JAR_STATE);
            float body = Mth.rotLerp(partialTick, s.yBodyRotO, s.yBodyRot);
            float head = Mth.rotLerp(partialTick, rider.yHeadRotO, rider.yHeadRot);
            float pitch = Mth.lerp(partialTick, rider.xRotO, rider.getXRot());
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, bone);
            JarRenderer.draw(pose, buffers, light, LivingEntityRenderer.getOverlayCoords(rider, 0), rider.getSkin().texture(), head - body, pitch,
                    rider.tickCount + partialTick, state.charge() > 0, !state.goggles().isEmpty(), JarRenderer.shape(state.compact()), false,
                    JarRenderer.Vitals.of(state, rider.getId(), 180.0f - body));
            JarRenderer.helmet(rider, state, pose, buffers, light);
            JarRenderer.speech(state, pose, buffers, light);
            pose.popPose();
        }
    }
}
