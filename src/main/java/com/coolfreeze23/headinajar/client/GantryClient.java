package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.machine.GantryPlan;
import com.coolfreeze23.headinajar.machine.KitController;
import com.coolfreeze23.headinajar.machine.KitController.Choice;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The Assembly Gantry in the client: its clips (chosen from the synced job, the pad, the screen and the refusal), its
 * weld sparks and arcs at the plan's points, its renderer (the turntable and the reel by code, the frame item on the
 * deck, the first modules on the tray, the head lamp lit at each weld, the charge lead from the reel to the frame),
 * and the yaw a frame is drawn at while the turntable carries it.
 */
public final class GantryClient {
    private GantryClient() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.GANTRY_BE.get(), c -> new Renderer());
    }

    static String id(MechaFrame frame) {
        return frame.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The clip a job plays, or null. */
    static String jobClip(AssemblyGantryBlockEntity g) {
        if (g.jobFrame() == null) return null;
        return switch (g.job()) {
            case AssemblyGantryBlockEntity.JOB_BUILD -> "build_" + id(g.jobFrame());
            case AssemblyGantryBlockEntity.JOB_DISASSEMBLE -> "disassemble_" + id(g.jobFrame());
            case AssemblyGantryBlockEntity.JOB_REPAIR -> "repair_" + id(g.jobFrame());
            default -> null;
        };
    }

    /** Boarding cut a build short this long ago: the arm folds home over 10 ticks from wherever it was. */
    static final int FOLD = 10;

    public static void controllers(AssemblyGantryBlockEntity gantry, AnimatableManager.ControllerRegistrar controllers) {
        // the arm: a job's clip at the job's tick; else standing off for a frame on the pad (load / unload), the charge lead
        add(controllers, new KitController<>(gantry, "arm", (g, now, c) -> {
            Choice held = KitController.toggle(c, g.loaded(), now, "load", 13, "unload", 16);
            String clip = jobClip(g);
            if (clip != null) {
                double tau = now - g.jobStart();
                c.lastState = 1;
                if (tau >= 0) return new Choice(clip, Math.min(tau, g.jobLength()));
            }
            if (c.lastState == 1) {
                // a job just ended: cut short by boarding, the arm folds home (a blend, not a clip); else it is stood off already
                c.lastState = 0;
                if (now - g.foldAt() < FOLD) c.blendNext = FOLD;
            }
            if (g.charging() && g.loaded()) {
                if (Double.isNaN(c.released)) c.released = now;
                return new Choice("charge", (now - c.released) % 40);
            }
            c.released = Double.NaN;
            return held;
        }, "charge"));
        // the head looks up at whoever has the screen open (its lamp lit); never during a job or the charge
        add(controllers, new KitController<>(gantry, "look", (g, now, c) -> {
            if (g.job() != AssemblyGantryBlockEntity.JOB_NONE || g.charging()) {
                c.lastBit = null;
                return null;
            }
            return g.loaded() ? KitController.toggle(c, g.kit().open(), now, "open_loaded", 7, "close_loaded", 6)
                    : KitController.toggle(c, g.kit().open(), now, "open", 7, "close", 8);
        }));
        // a refusal: the head shakes "no"; during a job the lamp and the sound say it alone
        add(controllers, new KitController<>(gantry, "refuse", (g, now, c) -> {
            if (g.job() != AssemblyGantryBlockEntity.JOB_NONE) return null;
            return MachineAnimations.refusal(g.kit(), now, g.loaded() ? "refuse_loaded" : "refuse") instanceof Choice r
                    && r.tick() < (g.loaded() ? 8 : 10) ? r : null;
        }));
    }

    private static void add(AnimatableManager.ControllerRegistrar controllers, KitController<AssemblyGantryBlockEntity> c) {
        c.setSoundKeyframeHandler(MachineKeyframes::sound);
        c.setParticleKeyframeHandler(GantryClient::particle);
        controllers.add(c);
    }

    /** A weld's sparks at its contact; an arc jumping from the welder to a socket out of its reach; a puff at the deck. */
    static void particle(ParticleKeyframeEvent<AssemblyGantryBlockEntity> event) {
        if (event.getAnimationTick() - event.getKeyframeData().getStartTick() > 2) return;
        AssemblyGantryBlockEntity g = event.getAnimatable();
        Level level = g.getLevel();
        if (level == null) return;
        String effect = event.getKeyframeData().getEffect(), where = event.getKeyframeData().getLocator();
        var random = level.random;
        switch (effect) {
            case "headinajar:gantry_weld" -> {
                Vec3 px = GantryPlan.locator(where);
                if (px == null) return;
                Vec3 at = MachineKeyframes.world(g, px);
                if (!LinkHud.photosensitive()) {
                    // a spray of sparks off the contact and a white point that hangs a moment (seen from across a yard)
                    for (int i = 0; i < 8; i++) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.25,
                                random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.25);
                    }
                    level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, 0.004, 0);
                    level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
                }
                level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.05, at.z, 0, 0.02, 0);
            }
            case "headinajar:gantry_arc" -> {
                Vec3[] arc = GantryPlan.arc(where);
                if (arc == null) return;
                Vec3 a = MachineKeyframes.world(g, arc[0]), b = MachineKeyframes.world(g, arc[1]);
                MachineKeyframes.play(g, "headinajar:gantry.arc", 1, b);
                if (LinkHud.photosensitive()) return;
                int n = Math.max(4, (int) (a.distanceTo(b) * 10));
                for (int i = 0; i <= n; i++) {
                    Vec3 p = a.lerp(b, i / (double) n);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + (random.nextDouble() - 0.5) * 0.04, p.y + (random.nextDouble() - 0.5) * 0.04,
                            p.z + (random.nextDouble() - 0.5) * 0.04, 0, 0, 0);
                }
                for (int i = 0; i < 4; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, (random.nextDouble() - 0.5) * 0.2,
                        random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.2);
            }
            case "minecraft:poof" -> {
                Vec3 at = MachineKeyframes.world(g, new Vec3(0, 22, 0));
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.POOF, at.x + (random.nextDouble() - 0.5) * 0.8, at.y + random.nextDouble() * 0.6,
                        at.z + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
            }
            default -> {
            }
        }
    }

    /**
     * The yaw a frame is drawn at: while the Gantry under it works on it, the turntable's, from the same function as the
     * server's yaw and the turntable bone (a yaw sent to the client arrives late and eased); else its own.
     */
    public static float yaw(MechaEntity m, float partial, float own) {
        if (m.isVehicle() || m.level() == null) return own;
        if (!(m.level().getBlockEntity(m.blockPosition().below()) instanceof AssemblyGantryBlockEntity g)) return own;
        if (g.job() == AssemblyGantryBlockEntity.JOB_NONE) return own;
        Vec3 deck = g.deck();
        if (Math.abs(m.getX() - deck.x) > 0.3 || Math.abs(m.getZ() - deck.z) > 0.3) return own;
        double angle = g.turntable(m.level().getGameTime() + partial);
        return Double.isNaN(angle) ? own : g.yawFor(angle);
    }

    /** The last frame of a clip, per bone (Blockbench degrees, x y z; and the position), read once from the exported clips. */
    private static final Map<String, Map<String, float[]>> ENDS = new HashMap<>();

    static Map<String, float[]> end(String clip) {
        return ENDS.computeIfAbsent(clip, GantryClient::readEnd);
    }

    private static Map<String, float[]> readEnd(String clip) {
        Map<String, float[]> out = new HashMap<>();
        var rm = Minecraft.getInstance().getResourceManager();
        var res = rm.getResource(HeadInAJar.id("animations/block/gantry.animation.json"));
        if (res.isEmpty()) return out;
        try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject anims = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            JsonObject a = anims.getAsJsonObject(clip);
            if (a == null) return out;
            for (var bone : a.getAsJsonObject("bones").entrySet()) {
                float[] v = new float[6];
                JsonObject b = bone.getValue().getAsJsonObject();
                float[] rot = last(b.get("rotation")), pos = last(b.get("position"));
                // the exported clips carry x and y negated (the Bedrock convention): back to Blockbench's
                if (rot != null) {
                    v[0] = -rot[0];
                    v[1] = -rot[1];
                    v[2] = rot[2];
                }
                if (pos != null) {
                    v[3] = -pos[0];
                    v[4] = pos[1];
                    v[5] = pos[2];
                }
                out.put(bone.getKey(), v);
            }
        } catch (java.io.IOException | RuntimeException e) {
            HeadInAJar.LOGGER.warn("the Gantry's clip {} could not be read", clip, e);
        }
        return out;
    }

    private static float[] last(JsonElement channel) {
        if (channel == null) return null;
        JsonElement v = channel;
        if (channel.isJsonObject()) {
            double best = -1;
            for (var e : channel.getAsJsonObject().entrySet()) {
                double t = Double.parseDouble(e.getKey());
                if (t >= best) {
                    best = t;
                    v = e.getValue();
                }
            }
            if (v.isJsonObject()) v = v.getAsJsonObject().get(v.getAsJsonObject().has("vector") ? "vector" : "post");
        }
        if (v == null || !v.isJsonArray()) return null;
        var arr = v.getAsJsonArray();
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
    }

    // --- the renderer --------------------------------------------------------------------------------------------

    static class Renderer extends MachineRenderers.Base<AssemblyGantryBlockEntity> {
        Renderer() {
            // the arm reaches up to 59 px out from the block's centre and, telescoped for a tall frame, 113 px up
            super("gantry", -60, 0, -60, 60, 120, 60);
        }

        @Override
        public int getViewDistance() {
            return 64;
        }

        static double now(AssemblyGantryBlockEntity g) {
            return MachineAnimations.now(g);
        }

        static KitController<?> controller(AssemblyGantryBlockEntity g, String name) {
            return g.getAnimatableInstanceCache().getManagerForId(0).getAnimationControllers().get(name) instanceof KitController<?> k ? k : null;
        }

        @Override
        protected boolean awake(AssemblyGantryBlockEntity g, double now) {
            return g.job() != AssemblyGantryBlockEntity.JOB_NONE || g.charging() || g.loaded() || g.kit().open()
                    || g.kit().since(MachineKit.EV_REFUSE, now) < 10 || now - g.foldAt() < FOLD + 2
                    || MachineAnimations.toggling(g, "arm", now, 16) || MachineAnimations.toggling(g, "look", now, 8)
                    || g.idleAngle != g.idleAnglePrev;
        }

        /** Nothing moves and no clip plays: a frame parked on the pad (the arm stood off), the screen open, or at rest. */
        @Override
        protected boolean still(AssemblyGantryBlockEntity g) {
            double now = now(g);
            if (g.job() != AssemblyGantryBlockEntity.JOB_NONE || g.charging() || g.kit().since(MachineKit.EV_REFUSE, now) < 10
                    || now - g.foldAt() < FOLD + 2 || g.idleAngle != g.idleAnglePrev) return false;
            KitController<?> arm = controller(g, "arm"), look = controller(g, "look");
            if (arm != null && arm.lastBit != null && now - arm.mark < 16) return false;
            return look == null || look.lastBit == null || now - look.mark >= 8;
        }

        @Override
        protected void heldPose(AssemblyGantryBlockEntity g, MachineRenderers.Model<AssemblyGantryBlockEntity> model) {
            KitController<?> look = controller(g, "look");
            boolean open = look != null && look.lastBit == Boolean.TRUE;
            String clip = g.loaded() ? (open ? "open_loaded" : "loaded") : open ? "open" : null;
            if (clip == null) return;
            for (var e : end(clip).entrySet()) {
                GeoBone bone = model.bone(e.getKey());
                if (bone == null) continue;
                var s = bone.getInitialSnapshot();
                float[] v = e.getValue();
                bone.setRotX(s.getRotX() + (float) Math.toRadians(v[0]));
                bone.setRotY(s.getRotY() + (float) Math.toRadians(v[1]));
                bone.setRotZ(s.getRotZ() + (float) Math.toRadians(v[2]));
                bone.setPosY(s.getOffsetY() + v[4]);
                bone.setPosZ(s.getOffsetZ() + v[5]);
            }
        }

        /** The turntable by the plan's function during a job, else under the frame parked on it; the reel by the lead. */
        @Override
        protected void pose(AssemblyGantryBlockEntity g, MachineRenderers.Model<AssemblyGantryBlockEntity> model, float partial) {
            GeoBone turntable = model.bone("turntable"), reel = model.bone("reel");
            double angle = g.turntable(now(g));
            if (Double.isNaN(angle)) angle = Mth.lerp(partial, g.idleAnglePrev, g.idleAngle);
            if (turntable != null) {
                turntable.setRotX(turntable.getInitialSnapshot().getRotX());
                turntable.setRotY(turntable.getInitialSnapshot().getRotY() + (float) Math.toRadians(angle));
                turntable.setRotZ(turntable.getInitialSnapshot().getRotZ());
            }
            if (reel != null) {
                reel.setRotX(reel.getInitialSnapshot().getRotX() + (float) Math.toRadians(Mth.lerp(partial, g.reelAnglePrev, g.reelAngle)));
            }
        }

        /** The head's lamp lit at each weld (and while it looks up at an open screen). */
        boolean lensLit(AssemblyGantryBlockEntity g) {
            if (g.kit().open() && g.job() == AssemblyGantryBlockEntity.JOB_NONE && !g.charging()) return true;
            if (g.job() != AssemblyGantryBlockEntity.JOB_BUILD || g.jobFrame() == null) return false;
            GantryPlan.Frame plan = GantryPlan.of(g.jobFrame());
            if (plan == null) return false;
            double t = now(g) - g.jobStart() - GantryPlan.LEAD;
            for (GantryPlan.Contact c : plan.contacts()) if (t >= c.contact() - 1 && t <= c.contact() + 3) return true;
            return false;
        }

        @Override
        public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
            if (animatable != null && bone.getName().equals("head") && !bone.isHidden() && lensLit(animatable)) {
                for (GeoCube cube : bone.getCubes()) {
                    boolean lens = cube.size().x == 3 && cube.size().y == 1 && cube.size().z == 1;
                    pose.pushPose();
                    renderCube(pose, cube, buffer, lens ? 0xF000F0 : light, overlay, colour);
                    pose.popPose();
                }
                return;
            }
            super.renderCubesOfBone(pose, bone, buffer, light, overlay, colour);
            sleeve(pose, bone, buffer, light, overlay, colour);
        }

        /**
         * The telescopes' sleeves (gantry_geo.py): on the upper arm, the forearm and the forearm's top rod, a 3x3 steel
         * cube as long as the most that rod ever shows. It is never drawn as it stands (a 3x3 rod inside a 3x3 bar
         * would fight its faces); it is drawn scaled along its length from its fixed end to the stage's extension now,
         * so a rod that is out shows as a steel member of the bar's own section, and nothing at all at rest. Its
         * paint is the same all along its length, so the scale stretches no pixel. Its ends are left out (both sit
         * inside a joint or against the bar's mouth, and a scaled end would be lit wrongly).
         */
        static int sleeveIndex(GeoBone bone) {
            String n = bone.getName();
            if (!n.equals("upper") && !n.equals("fore") && !n.equals("fore_t2")) return -1;
            var cubes = bone.getCubes();
            for (int i = 0; i < cubes.size(); i++) {
                var size = cubes.get(i).size();
                if (Math.round(size.x) == 3 && Math.round(size.z) == 3) return i;
            }
            return -1;
        }

        @Override
        protected int hiddenCubes(AssemblyGantryBlockEntity g, GeoBone bone) {
            int i = sleeveIndex(bone);
            return i < 0 ? 0 : 1 << i;
        }

        /** How far the stage the sleeve shows is out now, in pixels. */
        static double extension(GeoBone bone) {
            GeoBone stage = bone.getName().equals("fore_t2") ? bone : null;
            if (stage == null) {
                String child = bone.getName().equals("upper") ? "upper_t" : "fore_t1";
                for (GeoBone c : bone.getChildBones()) if (c.getName().equals(child)) stage = c;
            }
            return stage == null ? 0 : stage.getPosY() - stage.getInitialSnapshot().getOffsetY();
        }

        void sleeve(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
            int i = sleeveIndex(bone);
            if (i < 0 || bone.isHidden()) return;
            GeoCube cube = bone.getCubes().get(i);
            double length = cube.size().y, out = extension(bone);
            if (out < 0.02 || length <= 0) return;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                for (var v : quad.vertices()) {
                    lo = Math.min(lo, v.position().y());
                    hi = Math.max(hi, v.position().y());
                }
            }
            // the fixed end: the shoulder's and the elbow's at the bottom, the top rod's under the head at the top
            float anchor = bone.getName().equals("fore_t2") ? hi : lo;
            float scale = (float) Math.min(1.0, out / length);
            pose.pushPose();
            pose.translate(0, anchor, 0);
            pose.scale(1, scale, 1);
            pose.translate(0, -anchor, 0);
            org.joml.Matrix3f normals = pose.last().normal();
            Matrix4f at = new Matrix4f(pose.last().pose());
            for (var quad : cube.quads()) {
                if (quad == null || Math.abs(quad.normal().y()) > 0.5f) continue;
                org.joml.Vector3f n = normals.transform(new org.joml.Vector3f(quad.normal())).normalize();
                createVerticesOfQuad(quad, at, n, buffer, light, overlay, colour);
            }
            pose.popPose();
        }

        @Override
        protected void contents(AssemblyGantryBlockEntity g, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            // the frame before Assemble, lying face-up on the deck; the first three modules standing on the tray
            if (!g.loaded() && g.job() == AssemblyGantryBlockEntity.JOB_NONE && g.shownFrame() != Items.AIR) {
                flat(pose, buffers, light, new ItemStack(g.shownFrame()), 0, 20.05, 0, 0.5f);
            }
            double[] tray = {-4, 0, 4};
            for (int i = 0; i < 3; i++) {
                if (g.shownModule(i) != Items.AIR) item(pose, buffers, light, g, new ItemStack(g.shownModule(i)), tray[i], 15, -7, 0.25f, 0, 0);
            }
            boolean lead = g.charging() && g.loaded() || g.job() == AssemblyGantryBlockEntity.JOB_REPAIR;
            if (lead) {
                MechaEntity m = g.onPad();
                if (m != null) cable(g, m, pose, buffers, light, partial);
            }
        }

        /** An item lying flat, face up, centred at (x, y, z) pixels. */
        static void flat(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, double x, double y, double z, float scale) {
            var sprite = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(stack).getParticleIcon();
            VertexConsumer v = buffers.getBuffer(MachineRenderers.atlas() ? net.minecraft.client.renderer.Sheets.cutoutBlockSheet()
                    : RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
            pose.pushPose();
            pose.translate(x / 16, y / 16, z / 16);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(scale, scale, scale);
            var m = pose.last();
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            float[][] corners = {{0.5f, 0.5f, u0, v0}, {0.5f, -0.5f, u0, v1}, {-0.5f, -0.5f, u1, v1}, {-0.5f, 0.5f, u1, v0}};
            for (float[] c : corners) {
                v.addVertex(m, c[0], c[1], 0).setColor(255, 255, 255, 255).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, 0, 0, -1);
            }
            pose.popPose();
        }

        /** The charge lead: from the reel, sagging, to the frame's hull on the reel's side; rubber with a copper band. */
        static void cable(AssemblyGantryBlockEntity g, MechaEntity m, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            Direction facing = facing(g);
            float turn = switch (facing) {
                case SOUTH -> 180;
                case WEST -> 90;
                case EAST -> 270;
                default -> 0;
            };
            var p = g.getBlockPos();
            Vec3 base = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            Vec3 mid = new Vec3(Mth.lerp(partial, m.xo, m.getX()), Mth.lerp(partial, m.yo, m.getY()), Mth.lerp(partial, m.zo, m.getZ()));
            // into the Gantry's own space (blocks, facing north)
            Vec3 to = mid.subtract(base).yRot((float) Math.toRadians(-turn));
            Vec3 from = new Vec3(-10.5 / 16, 8 / 16.0, 0);
            Vec3 side = new Vec3(from.x - to.x, 0, from.z - to.z);
            double reach = Math.min(m.getBbWidth() * 0.42, side.length());
            if (side.lengthSqr() > 1e-6) to = to.add(side.normalize().scale(reach));
            to = to.add(0, Math.min(m.getBbHeight() * 0.35, 0.9), 0);
            Vec3 d = to.subtract(from);
            double length = d.length();
            if (length < 0.05) return;
            double sag = Math.min(0.5, 0.12 * length);
            Matrix4f matrix = pose.last().pose();
            Vec3 across = d.cross(new Vec3(0, 1, 0));
            if (across.lengthSqr() < 1e-6) across = new Vec3(1, 0, 0);
            Vec3 a1 = across.normalize().scale(0.03);
            Vec3 a2 = d.cross(a1).normalize().scale(0.03);
            int segments = 16;
            for (Vec3 w : new Vec3[]{a1, a2}) {
                VertexConsumer out = buffers.getBuffer(RenderType.leash());
                for (int i = 0; i <= segments; i++) {
                    float f = i / (float) segments;
                    Vec3 q = from.add(d.scale(f)).subtract(0, sag * 4 * f * (1 - f), 0);
                    boolean band = i >= segments - 1;
                    float r = band ? 0.72f : 0.16f, gg = band ? 0.42f : 0.15f, b = band ? 0.22f : 0.14f;
                    out.addVertex(matrix, (float) (q.x + w.x), (float) (q.y + w.y), (float) (q.z + w.z)).setColor(r, gg, b, 1).setLight(light);
                    out.addVertex(matrix, (float) (q.x - w.x), (float) (q.y - w.y), (float) (q.z - w.z)).setColor(r, gg, b, 1).setLight(light);
                }
            }
        }

        static Direction facing(BlockEntity machine) {
            return MachineRenderers.Base.facing(machine);
        }
    }
}
