package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.SalineMixerBlockEntity;
import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The kit's renderer: the big shape is the block's baked model (the chunk draws it), this draws only the moving
 * parts and the contents. The idle rule: a resting machine draws its parts and contents within 32 blocks and nothing
 * beyond; an awake one within its view distance (48). Code-driven bones are written absolutely, every frame, for the
 * instance being drawn (bones are shared by every machine of a kind).
 */
public class MachineRenderers {
    /** Beyond this a resting machine draws nothing of its own (its baked body stays). */
    public static final double REST_DISTANCE = 32;
    /** Moving parts are drawn within this many blocks, awake or not. */
    public static final int VIEW_DISTANCE = 32;
    /** Contents (items in a pan, bottles in a niche, vials on a rack) are drawn within this many blocks. */
    public static final double CONTENT_DISTANCE = 16;
    /** Dev measurement only (the smoke client's {@code machdebug}): 1 leaves out contents, 2 everything this draws, 4 the
     * animation pass, 8 the cubes, 16 the running particles; 32 draws as before the shared atlas buffers (a draw call per
     * texture switch), 64 as before the baked rest pose,
     * 128 the Array's shrunk heads, 256 a machine's parts in a shader pack's shadow pass too. */
    public static int debugOff;

    /**
     * Whether Iris is drawing its shadow pass now. A shader pack draws every block entity again from the sun for the
     * shadow map; a machine's moving parts and contents are small against its baked body (which casts the shadow), so
     * they are left out of that pass ({@code machdebug 256} draws them there too, for measuring).
     */
    static boolean shadowPass() {
        if ((debugOff & 256) != 0 || IRIS == null) return false;
        try {
            return (boolean) IRIS_SHADOW.invoke(IRIS);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static final Object IRIS;
    private static final java.lang.reflect.Method IRIS_SHADOW;

    static {
        Object api = null;
        java.lang.reflect.Method shadow = null;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            shadow = type.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException | LinkageError e) {
            api = null;
        }
        IRIS = api;
        IRIS_SHADOW = shadow;
    }

    /** Parts, items, jars and collars go to the block atlas's shared buffers ({@link AtlasConsumer}). */
    static boolean atlas() {
        return (debugOff & 32) == 0;
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.PILE_BE.get(), c -> new Pile());
        event.registerBlockEntityRenderer(ModRegistry.MIXER_BE.get(), c -> new Mixer());
        event.registerBlockEntityRenderer(ModRegistry.CENTRIFUGE_BE.get(), c -> new Centrifuge());
        event.registerBlockEntityRenderer(ModRegistry.CRADLE_BE.get(), c -> new Cradle());
        event.registerBlockEntityRenderer(ModRegistry.PRINTER_BE.get(), c -> new Printer(c));
        event.registerBlockEntityRenderer(ModRegistry.SPLICER_BE.get(), c -> new Splicer());
    }

    /** A kit machine's geo model: geometry, clips and the painted sheet by the machine's name; code bones by the renderer. */
    public static class Model<T extends BlockEntity & GeoAnimatable & KitMachine> extends GeoModel<T> {
        private final ResourceLocation geo, texture, animation;
        private Base<T> renderer;

        Model(String name) {
            this.geo = HeadInAJar.id("geo/block/" + name + ".geo.json");
            this.texture = HeadInAJar.id("textures/block/machine/" + name + ".png");
            this.animation = HeadInAJar.id("animations/block/" + name + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return animation;
        }

        /** The block atlas's shared cutout buffer: every machine's parts in one draw ({@link AtlasConsumer}). */
        @Override
        public RenderType getRenderType(T animatable, ResourceLocation texture) {
            return atlas() ? net.minecraft.client.renderer.Sheets.cutoutBlockSheet() : RenderType.entityCutoutNoCull(texture);
        }

        @Override
        public void setCustomAnimations(T machine, long instanceId, AnimationState<T> state) {
            if (renderer != null) renderer.pose(machine, this, state.getPartialTick());
        }

        GeoBone bone(String name) {
            return getBone(name).orElse(null);
        }
    }

    /** The shared part of every kit machine's renderer. */
    public abstract static class Base<T extends BlockEntity & GeoAnimatable & KitMachine> extends GeoBlockRenderer<T> {
        private final double[] box;

        /** {@code box}: x0, y0, z0, x1, y1, z1 in pixels about the block's bottom centre, covering every pose. */
        @SuppressWarnings("unchecked")
        protected Base(String name, double... box) {
            super(new Model<>(name));
            ((Model<T>) getGeoModel()).renderer = this;
            this.box = box;
        }

        /** Whether anything moves or stands off its rest pose. */
        protected abstract boolean awake(T machine, double now);

        /** Code-driven channels, written absolutely. */
        protected void pose(T machine, Model<T> model, float partial) {
        }

        /** Contents (items at the model's anchors), drawn after the bones in the model's own space. */
        protected void contents(T machine, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        }

        /** A cube of {@code bone} drawn in a colour this frame: the cube's index in the top byte, the colour in the rest; -1 for none. */
        protected int cubeTint(T machine, GeoBone bone) {
            return -1;
        }

        /** Cubes of {@code bone} not drawn for this machine this frame (a bit per cube index). */
        protected int hiddenCubes(T machine, GeoBone bone) {
            return 0;
        }

        @Override
        public void render(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if ((debugOff & 2) != 0 || shadowPass()) return;
            double now = MachineAnimations.now(machine);
            boolean awake = awake(machine, now);
            if (machine.getLevel() != null && (debugOff & 64) == 0) {
                // the baked rest pose: handed to the chunk while nothing moves, taken back the moment something should
                MachineKit kit = machine.kit();
                long frame = RestBake.frame();
                boolean resting = !awake || still(machine);
                RestBake.Baked baked = kit.restBake instanceof RestBake.Baked b ? b : null;
                if (resting) {
                    long time = machine.getLevel().getGameTime();
                    if (baked == null) {
                        kit.restBake = capture(machine, frame, time);
                        RestBake.remesh(machine.getLevel(), machine.getBlockPos());
                    } else if (baked.shown(frame)) {
                        if (time - baked.checkedAt() >= 20) {
                            // a still pose can change without a clip (a tint, a hidden cube): bake again if it did
                            RestBake.Baked again = baked.again(capture(machine, baked.frame(), time));
                            kit.restBake = again;
                            if (again.signature() != baked.signature()) RestBake.remesh(machine.getLevel(), machine.getBlockPos());
                        }
                        contentsOnly(machine, partialTick, pose, buffers, light, overlay);
                        return;
                    }
                } else if (baked != null) {
                    kit.restBake = null;
                    RestBake.remesh(machine.getLevel(), machine.getBlockPos());
                }
            } else if (machine.kit().restBake != null && machine.getLevel() != null) {
                machine.kit().restBake = null;
                RestBake.remesh(machine.getLevel(), machine.getBlockPos());
            }
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double d2 = machine.getBlockPos().getCenter().distanceToSqr(camera);
            if (d2 > REST_DISTANCE * REST_DISTANCE && !awake && (debugOff & 64) != 0) return;
            super.render(machine, partialTick, pose, buffers, light, overlay);
        }

        /** The pose this renderer would draw now, still, drawn into quads of the block's own space. */
        @SuppressWarnings("unchecked")
        private RestBake.Baked capture(T machine, long frame, long time) {
            RestBake.Capture capture = new RestBake.Capture();
            PoseStack pose = new PoseStack();
            BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(machine));
            this.animatable = machine;
            this.blockRenderTranslations = new org.joml.Matrix4f(pose.last().pose());
            pose.translate(0.5, 0, 0.5);
            rotateBlock(getFacing(machine), pose);
            this.modelRenderTranslations = new org.joml.Matrix4f(pose.last().pose());
            for (GeoBone bone : model.topLevelBones()) restPose(bone);
            pose(machine, (Model<T>) getGeoModel(), 0);
            heldPose(machine, (Model<T>) getGeoModel());
            for (GeoBone bone : model.topLevelBones()) {
                renderRecursively(pose, machine, bone, null, null, capture, false, 0, 0, OverlayTexture.NO_OVERLAY, -1);
            }
            // a machine of one cell keeps all of it in its own cell (nothing above to hand the top to)
            return capture.bake(AtlasConsumer.sprite(getGeoModel().getTextureResource(machine)), frame, time,
                    machine.getBlockState().getBlock() instanceof com.coolfreeze23.headinajar.machine.TallMachine);
        }

        /** A machine whose pose the chunk draws: only its contents, in the model's space. */
        private void contentsOnly(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if ((debugOff & 1) != 0) return;
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            rotateBlock(getFacing(machine), pose);
            postRender(pose, machine, null, buffers, null, false, partialTick, light, overlay, -1);
            pose.popPose();
        }

        @Override
        public void postRender(PoseStack pose, T machine, BakedGeoModel model, MultiBufferSource buffers, @Nullable VertexConsumer buffer, boolean isReRender,
                               float partialTick, int light, int overlay, int colour) {
            super.postRender(pose, machine, model, buffers, buffer, isReRender, partialTick, light, overlay, colour);
            if (isReRender || buffers == null || (debugOff & 1) != 0) return;
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (machine.getBlockPos().getCenter().distanceToSqr(camera) > CONTENT_DISTANCE * CONTENT_DISTANCE) return;
            // inside a machine its own cell's light is dark: take the brighter of its cell and the one in front
            int front = machine.getLevel() == null ? light : LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().relative(facing(machine)));
            contents(machine, pose, buffers, Math.max(light, front), partialTick);
        }

        /**
         * At rest (or for measuring, {@code machdebug 4}) GeckoLib's animation pass is skipped: the bones are put at
         * their rest pose (plus the code channels) and drawn as they are. {@code machdebug 8} runs the animation
         * pass and draws no cubes.
         */
        @Override
        public void actuallyRender(PoseStack pose, T machine, BakedGeoModel model, @Nullable RenderType renderType, MultiBufferSource buffers,
                                   @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay, int colour) {
            // the sheet's UVs moved onto its place in the block atlas (the render type is the atlas's shared one)
            // (a render layer's pass, a re-render, draws with its own texture: left as it is)
            if (atlas() && !isReRender && buffer != null && !(buffer instanceof AtlasConsumer)) buffer = AtlasConsumer.of(buffer, getGeoModel().getTextureResource(machine));
            if (isReRender || (debugOff & 4) == 0 && !still(machine)) {
                super.actuallyRender(pose, machine, model, renderType, buffers, buffer, isReRender, partialTick, light, overlay, colour);
                return;
            }
            rotateBlock(getFacing(machine), pose);
            this.modelRenderTranslations = new org.joml.Matrix4f(pose.last().pose());
            for (GeoBone bone : model.topLevelBones()) restPose(bone);
            pose(machine, (Model<T>) getGeoModel(), partialTick);
            heldPose(machine, (Model<T>) getGeoModel());
            if (buffer == null) {
                if (renderType == null) return;
                buffer = buffers.getBuffer(renderType);
                if (atlas()) buffer = AtlasConsumer.of(buffer, getGeoModel().getTextureResource(machine));
            }
            for (GeoBone bone : model.topLevelBones()) {
                renderRecursively(pose, machine, bone, renderType, buffers, buffer, false, partialTick, light, overlay, colour);
            }
        }

        /** Nothing moves and no clip is playing: no animation pass is needed (parts held open or up: {@link #heldPose}). */
        protected boolean still(T machine) {
            return false;
        }

        /** The last frame of the clips a still machine holds (a flag up, a drawer out, caps lifted), written directly. */
        protected void heldPose(T machine, Model<T> model) {
        }

        /** A held rotation about x (degrees, Blockbench's sign) and offsets (pixels) on top of the rest pose. */
        protected static void hold(GeoBone bone, float rotX, float posY, float posZ) {
            if (bone == null) return;
            var s = bone.getInitialSnapshot();
            bone.setRotX(s.getRotX() + (float) Math.toRadians(rotX));
            bone.setPosY(s.getOffsetY() + posY);
            bone.setPosZ(s.getOffsetZ() + posZ);
        }

        private static void restPose(GeoBone bone) {
            var s = bone.getInitialSnapshot();
            bone.setRotX(s.getRotX());
            bone.setRotY(s.getRotY());
            bone.setRotZ(s.getRotZ());
            bone.setPosX(s.getOffsetX());
            bone.setPosY(s.getOffsetY());
            bone.setPosZ(s.getOffsetZ());
            bone.setScaleX(s.getScaleX());
            bone.setScaleY(s.getScaleY());
            bone.setScaleZ(s.getScaleZ());
            for (GeoBone child : bone.getChildBones()) restPose(child);
        }

        @Override
        public void renderRecursively(PoseStack pose, T machine, GeoBone bone, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                                      boolean isReRender, float partialTick, int light, int overlay, int colour) {
            if ((debugOff & 8) != 0) return;
            super.renderRecursively(pose, machine, bone, renderType, buffers, buffer, isReRender, partialTick, light, overlay, colour);
        }

        @Override
        public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, int colour) {
            int hidden = animatable == null ? 0 : hiddenCubes(animatable, bone);
            int tint = animatable == null ? -1 : cubeTint(animatable, bone);
            if (tint != -1) {
                // one cube of the bone tinted (a spool's thread): the cubes drawn in turn, that one in its colour
                if (bone.isHidden()) return;
                int i = 0;
                for (GeoCube cube : bone.getCubes()) {
                    int index = i++;
                    if ((hidden & (1 << index)) != 0) continue;
                    pose.pushPose();
                    renderCube(pose, cube, buffer, light, overlay, index == (tint >>> 24) ? (0xFF000000 | (tint & 0xFFFFFF)) : colour);
                    pose.popPose();
                }
                return;
            }
            if (hidden == 0) {
                super.renderCubesOfBone(pose, bone, buffer, light, overlay, colour);
                return;
            }
            if (bone.isHidden()) return;
            int i = 0;
            for (GeoCube cube : bone.getCubes()) {
                if ((hidden & (1 << i++)) != 0) continue;
                pose.pushPose();
                renderCube(pose, cube, buffer, light, overlay, colour);
                pose.popPose();
            }
        }

        @Override
        public int getViewDistance() {
            return VIEW_DISTANCE;
        }

        @Override
        public AABB getRenderBoundingBox(T machine) {
            var p = machine.getBlockPos();
            // turned with the machine: the larger horizontal reach either way
            double r = Math.max(Math.max(-box[0], box[3]), Math.max(-box[2], box[5])) / 16;
            return new AABB(p.getX() + 0.5 - r, p.getY() + box[1] / 16, p.getZ() + 0.5 - r, p.getX() + 0.5 + r, p.getY() + box[4] / 16, p.getZ() + 0.5 + r);
        }

        static Direction facing(BlockEntity machine) {
            var state = machine.getBlockState();
            return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        }

        /**
         * An item standing upright at an anchor (pixels, the item's bottom centre), facing the machine's front, drawn as
         * one flat quad of its sprite: an item drawn as an item is hundreds of edge quads, and at 2-4 px nobody sees its
         * thickness.
         */
        protected static void item(PoseStack pose, MultiBufferSource buffers, int light, BlockEntity machine, ItemStack stack,
                                   double x, double y, double z, float scale, float lift, float roll) {
            if (stack.isEmpty()) return;
            var sprite = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(stack).getParticleIcon();
            // the block atlas's shared cutout buffer (one draw for every machine's items); it culls, so a back face too
            VertexConsumer v = buffers.getBuffer(atlas() ? net.minecraft.client.renderer.Sheets.cutoutBlockSheet()
                    : RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
            pose.pushPose();
            pose.translate(x / 16, (y + lift) / 16 + scale / 2, z / 16);
            if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
            pose.scale(scale, scale, scale);
            var m = pose.last();
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            // seen from the front (-Z) the sprite's left edge is at +X
            quadVertex(v, m, 0.5f, 0.5f, u0, v0, light);
            quadVertex(v, m, 0.5f, -0.5f, u0, v1, light);
            quadVertex(v, m, -0.5f, -0.5f, u1, v1, light);
            quadVertex(v, m, -0.5f, 0.5f, u1, v0, light);
            quadVertex(v, m, -0.5f, 0.5f, u1, v0, light);
            quadVertex(v, m, -0.5f, -0.5f, u1, v1, light);
            quadVertex(v, m, 0.5f, -0.5f, u0, v1, light);
            quadVertex(v, m, 0.5f, 0.5f, u0, v0, light);
            pose.popPose();
        }

        private static void quadVertex(VertexConsumer v, PoseStack.Pose m, float x, float y, float u, float vv, int light) {
            v.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, 0, 0, -1);
        }

        protected static void rest(GeoBone bone) {
            if (bone == null) return;
            bone.setRotX(bone.getInitialSnapshot().getRotX());
            bone.setRotY(bone.getInitialSnapshot().getRotY());
            bone.setRotZ(bone.getInitialSnapshot().getRotZ());
        }
    }

    // --- Galvanic Pile -------------------------------------------------------------------------------------------

    static class Pile extends Base<GalvanicPileBlockEntity> {
        Pile() {
            // the grips sweep to 8.06 px; the lens stands at z -9
            super("galvanic_pile", -9, 0, -9, 9, 32, 9);
        }

        @Override
        protected boolean awake(GalvanicPileBlockEntity pile, double now) {
            return MachineAnimations.awake(pile, now);
        }

        @Override
        protected boolean still(GalvanicPileBlockEntity pile) {
            return !MachineAnimations.awake(pile, MachineAnimations.now(pile));
        }

        /** The squeeze: the upper stack sinks 1 px and the press 2 px over a burn. */
        @Override
        protected void pose(GalvanicPileBlockEntity pile, Model<GalvanicPileBlockEntity> model, float partial) {
            float v = Mth.lerp(partial, pile.squeezePrev, pile.squeeze);
            GeoBone stack = model.bone("stack_top"), press = model.bone("press");
            if (stack != null) stack.setPosY(stack.getInitialSnapshot().getOffsetY() - v);
            if (press != null) press.setPosY(press.getInitialSnapshot().getOffsetY() - 2 * v);
        }

        private static final double[][] LUMPS = {{0, 4, -2}, {-3, 4, 1}, {3, 4, 1}};

        @Override
        protected void contents(GalvanicPileBlockEntity pile, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            int n = pile.lumps();
            if (n <= 0 || pile.shown() == Items.AIR) return;
            ItemStack meat = new ItemStack(pile.shown());
            double now = MachineAnimations.now(pile);
            double plop = pile.kit().since(MachineKit.EV_PLOP, now);
            // the twitch: every 30 s while working (offset per pile), the newest piece hops and rolls for 4 ticks
            long phase = Math.floorMod((long) now + pile.getBlockPos().hashCode(), 600L);
            boolean twitch = MachineAnimations.working(pile) && phase < 4;
            for (int i = 0; i < n; i++) {
                float lift = 0, roll = 0;
                if (i == n - 1) {
                    if (plop >= 0 && plop < 8) lift = plopLift((float) plop);
                    else if (twitch) {
                        lift = 0.5f * Mth.sin((float) (phase * Math.PI / 4));
                        roll = 5 * Mth.sin((float) (phase * Math.PI / 4));
                    }
                }
                item(pose, buffers, light, pile, meat, LUMPS[i][0], LUMPS[i][1], LUMPS[i][2], 0.25f, lift, roll);
            }
        }

        /** +3 px at the drop, down by tick 4 (falling faster), a half-pixel bounce at 6, at rest by 8. */
        static float plopLift(float t) {
            if (t < 4) {
                float k = t / 4;
                return 3 * (1 - k * k);
            }
            if (t < 6) return 0.5f * Mth.sin((t - 4) / 2 * Mth.HALF_PI);
            return 0.5f * (1 - (t - 6) / 2);
        }
    }

    // --- Saline Mixer ----------------------------------------------------------------------------------------

    static class Mixer extends Base<SalineMixerBlockEntity> {
        Mixer() {
            super("saline_mixer", -11, 0, -11, 11, 35, 11);
        }

        @Override
        protected boolean awake(SalineMixerBlockEntity mixer, double now) {
            return MachineAnimations.awake(mixer, now);
        }

        @Override
        protected boolean still(SalineMixerBlockEntity mixer) {
            return MachineAnimations.still(mixer, MachineAnimations.now(mixer));
        }

        @Override
        protected void heldPose(SalineMixerBlockEntity mixer, Model<SalineMixerBlockEntity> model) {
            if (mixer.kit().open()) for (String cap : new String[]{"cap_s", "cap_k", "cap_r"}) hold(model.bone(cap), 0, 2, 0);
            if (mixer.hasOutput()) hold(model.bone("flag"), 90, 0, 0);
        }

        @Override
        protected void pose(SalineMixerBlockEntity mixer, Model<SalineMixerBlockEntity> model, float partial) {
            GeoBone paddle = model.bone("paddle");
            if (paddle != null) {
                float angle = Mth.lerp(partial, mixer.paddleAnglePrev, mixer.paddleAngle) % 360;
                paddle.setRotY(paddle.getInitialSnapshot().getRotY() - (float) Math.toRadians(angle));
            }
        }

        @Override
        protected void contents(SalineMixerBlockEntity mixer, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            int ing = mixer.ingredients();
            if ((ing & 1) != 0) item(pose, buffers, light, mixer, new ItemStack(Items.SUGAR), 0, 27.5, -3, 0.25f, 0, 0);
            if ((ing & 6) != 0) item(pose, buffers, light, mixer, new ItemStack((ing & 2) != 0 ? Items.KELP : Items.DRIED_KELP), -3, 27.5, 3, 0.25f, 0, 0);
            if ((ing & 8) != 0) item(pose, buffers, light, mixer, new ItemStack(Items.REDSTONE), 3, 27.5, 3, 0.125f, 0, 0);
            Item saline = ModRegistry.SALINE_BOTTLE.get();
            // under the spout: the bottle being filled, else an empty one waiting
            if (mixer.bottling()) item(pose, buffers, light, mixer, new ItemStack(saline), -2.5, 5, -3.5, 0.25f, 0, 0);
            else if (mixer.bottlesIn()) item(pose, buffers, light, mixer, new ItemStack(Items.GLASS_BOTTLE), -2.5, 5, -3.5, 0.25f, 0, 0);
            int out = mixer.bottlesOut();
            if (out >= 1) item(pose, buffers, light, mixer, new ItemStack(saline), -4.5, 5, -6, 0.25f, 0, 0);
            if (out >= 2) item(pose, buffers, light, mixer, new ItemStack(saline), -0.5, 5, -6, 0.25f, 0, 0);
            if (out >= 3) item(pose, buffers, light, mixer, new ItemStack(saline), -2.5, 5, -6.5, 0.25f, 0, 0);
        }
    }

    // --- Centrifuge ------------------------------------------------------------------------------------------

    static class Centrifuge extends Base<LabMachines.Centrifuge> {
        private static final ResourceLocation BALANCE = HeadInAJar.id("textures/block/machine/centrifuge_balance.png");
        private static final String[] HANGERS = {"hanger_n", "hanger_e", "hanger_s", "hanger_w"};

        Centrifuge() {
            // the tubes at 60 degrees reach 10.9 px
            super("centrifuge", -11, 0, -11, 11, 32, 11);
        }

        @Override
        protected boolean awake(LabMachines.Centrifuge c, double now) {
            return MachineAnimations.awake(c, now);
        }

        @Override
        protected boolean still(LabMachines.Centrifuge c) {
            return MachineAnimations.still(c, MachineAnimations.now(c));
        }

        @Override
        protected void heldPose(LabMachines.Centrifuge c, Model<LabMachines.Centrifuge> model) {
            if (c.kit().open()) hold(model.bone("drawer"), 0, 0, -3);
            if (c.hasOutput()) hold(model.bone("flag"), 90, 0, 0);
        }

        @Override
        protected void pose(LabMachines.Centrifuge c, Model<LabMachines.Centrifuge> model, float partial) {
            float s = c.speed;
            GeoBone rotor = model.bone("rotor");
            if (rotor != null) {
                float angle = Mth.lerp(partial, c.anglePrev, c.angle) % 360;
                rotor.setRotY(rotor.getInitialSnapshot().getRotY() + (float) Math.toRadians(angle));
                // unevenly loaded, the rotor rides tilted on an axis that turns with it
                rotor.setRotX(rotor.getInitialSnapshot().getRotX() + (float) Math.toRadians(c.unbalanced() ? 3.5 * s : 0));
                rotor.setRotZ(rotor.getInitialSnapshot().getRotZ());
            }
            float theta = (float) Math.toRadians(Mth.lerp(partial, c.swingPrev, c.swing));
            set(model.bone("hanger_n"), theta, 0);
            set(model.bone("hanger_s"), -theta, 0);
            set(model.bone("hanger_e"), 0, theta);
            set(model.bone("hanger_w"), 0, -theta);
        }

        private static void set(GeoBone bone, float x, float z) {
            if (bone == null) return;
            bone.setRotX(bone.getInitialSnapshot().getRotX() + x);
            bone.setRotY(bone.getInitialSnapshot().getRotY());
            bone.setRotZ(bone.getInitialSnapshot().getRotZ() + z);
        }

        /** An empty hanger's tube (its glass and sample cubes) is not drawn: tubes on the first n hangers, n, e, s, w. */
        @Override
        protected int hiddenCubes(LabMachines.Centrifuge c, GeoBone bone) {
            String name = bone.getName();
            if (!name.startsWith("hanger_")) return 0;
            int index = java.util.Arrays.asList(HANGERS).indexOf(name);
            return index >= 0 && index >= c.samples() ? (1 << 2) | (1 << 3) : 0;
        }

        @Override
        protected void contents(LabMachines.Centrifuge c, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            ItemStack dna = new ItemStack(ModRegistry.DNA_FRACTION.get());
            ItemStack vial = new ItemStack(c.bottles() ? Items.GLASS_BOTTLE : ModRegistry.PLASMA_VIAL.get());
            if (c.dna() >= 1) item(pose, buffers, light, c, dna, -5, 13, -6, 0.25f, 0, 0);
            if (c.dna() >= 2) item(pose, buffers, light, c, dna, -2, 13, -6, 0.25f, 0, 0);
            if (c.vials() >= 1) item(pose, buffers, light, c, vial, 2, 13, -6, 0.25f, 0, 0);
            if (c.vials() >= 2) item(pose, buffers, light, c, vial, 5, 13, -6, 0.25f, 0, 0);
            // "PLEASE BALANCE ROTOR", front and back, while it spins unevenly loaded
            if (c.unbalanced() && c.speed > 0.05f) {
                VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(BALANCE));
                sign(pose, v, light, -7.5f, -1);
                sign(pose, v, light, 7.5f, 1);
            }
        }

        /** A 6x2 px quad at z (pixels) facing {@code dir} (-1 front, +1 back), y 29-31, the sprite's [0,0,6,2]. */
        private static void sign(PoseStack pose, VertexConsumer v, int light, float z, int dir) {
            var m = pose.last();
            float zz = z / 16, y0 = 29 / 16f, y1 = 31 / 16f, x = 3 / 16f;
            float u1 = 6 / 16f, v1 = 2 / 16f;
            float l = dir < 0 ? -x : x, r = -l;
            // seen from outside: left to right as the viewer reads it
            vertex(v, m, l, y1, zz, 0, 0, light, dir);
            vertex(v, m, l, y0, zz, 0, v1, light, dir);
            vertex(v, m, r, y0, zz, u1, v1, light, dir);
            vertex(v, m, r, y1, zz, u1, 0, light, dir);
        }

        private static void vertex(VertexConsumer v, PoseStack.Pose m, float x, float y, float z, float u, float vv, int light, int dir) {
            v.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, 0, 0, dir);
        }
    }

    // --- Life Support Cradle ---------------------------------------------------------------------------------

    static class Cradle extends Base<com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity> {
        Cradle() {
            // the arms overhang 2 px a side, the bellows rises to 34, the back stands 2 px out behind
            super("cradle", -14, 0, -17, 14, 35, 11);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity c, double now) {
            return MachineAnimations.awake(c, now);
        }

        /** A base has one or two: the chair and its head are drawn as far as the chair was before. */
        @Override
        public int getViewDistance() {
            return 64;
        }

        /** The docked head, the real jar at 1:1 on the cushion, facing out. */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity c, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            if (c.owner() == null || c.getLevel() == null) return;
            pose.pushPose();
            pose.translate(0, 6 / 16.0, -1 / 16.0);
            float age = c.getLevel().getGameTime() + partial;
            // the head through the cutout pass: a shader pack draws a block entity's translucent layers in its own
            // order, and with BSL the saline came first and hid the head; cut out, it is drawn before any of them
            net.minecraft.resources.ResourceLocation skin = JarRenderer.skin(c.owner());
            RenderType headLayer = RenderType.entityTranslucent(skin);
            // the jar's frame, fluid and glass (and the goggles) go to the block atlas's shared buffers: only the head is a draw of its own
            MultiBufferSource atlas = atlas() ? AtlasConsumer.onAtlas(buffers, JarRenderer.TEXTURE, JarRenderer.GOGGLES_TEXTURE) : buffers;
            MultiBufferSource ordered = type -> atlas.getBuffer(type == headLayer ? RenderType.entityCutoutNoCull(skin) : type);
            JarRenderer.draw(pose, ordered, light, OverlayTexture.NO_OVERLAY, skin, 0, 0, age, true,
                    !c.goggles().isEmpty(), c.compact() ? JarRenderer.COMPACT : JarRenderer.WIDE, c.upsideDown());
            pose.popPose();
        }

        /** The jar is the point of the chair: drawn as far as the chair's parts are. */
        @Override
        public void postRender(PoseStack pose, com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity c, BakedGeoModel model, MultiBufferSource buffers,
                               @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay, int colour) {
            if (!isReRender && buffers != null && (debugOff & 1) == 0) {
                int front = c.getLevel() == null ? light : LevelRenderer.getLightColor(c.getLevel(), c.getBlockPos().relative(facing(c)));
                contents(c, pose, buffers, Math.max(light, front), partialTick);
            }
        }
    }

    // --- Body Printer ------------------------------------------------------------------------------------------

    static class Printer extends Base<com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity> {
        private final net.minecraft.client.model.PlayerModel<com.coolfreeze23.headinajar.entity.HeadlessBody> wide, slim;
        private com.coolfreeze23.headinajar.entity.HeadlessBody dummy;

        Printer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
            super("body_printer", -11, 0, -24, 10, 32, 9);
            wide = new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
            slim = new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity p, double now) {
            return MachineAnimations.awake(p, now);
        }

        @Override
        public int getViewDistance() {
            return 64;
        }

        private static final net.minecraft.world.item.Item[] MATS = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};
        private static final double[][] MAT_AT = {{-3.5, 3, -3}, {3.5, 3, -3}, {-3.5, 3, 2}, {3.5, 3, 2}};

        @Override
        protected void contents(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity p, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            double now = MachineAnimations.now(p);
            double t = MachineAnimations.printTick(p, now);
            if (t >= 0) {
                GeoBone ring = getGeoModel().getBone("ring").orElse(null);
                float plane = 3 + (ring == null ? 0 : ring.getPosY());
                body(p, pose, buffers, light, t, plane);
                return;
            }
            for (int i = 0; i < 4; i++) {
                if ((p.materials() & (1 << i)) != 0) item(pose, buffers, light, p, new ItemStack(MATS[i]), MAT_AT[i][0], MAT_AT[i][1], MAT_AT[i][2], 0.375f, 0, 0);
            }
        }

        /** The printed body: below the print bar in the base material while printing, developed from the top down, then walking out. */
        private void body(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity p, PoseStack pose, MultiBufferSource buffers, int light, double t, float plane) {
            if (t >= 1200 || p.getLevel() == null) return;
            var skin = skinOf(p);
            var model = skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? slim : wide;
            if (dummy == null || dummy.level() != p.getLevel()) dummy = new com.coolfreeze23.headinajar.entity.HeadlessBody(ModRegistry.HEADLESS_BODY.get(), p.getLevel());
            double x = 0, y = 3, z = 0, yaw = 45, swing = 0, amount = 0;
            boolean crouch = false;
            if (t >= 1178) {
                // printer_anim.walk: out through the door, turning to face the front, ducking under the sign
                double s = (t - 1178) / 22;
                z = -16 * easeInOutSine(s);
                yaw = 45 * (1 - Mth.clamp((s - 0.72) / 0.2, 0, 1));
                crouch = s >= 0.28 && s < 0.92;
                y = 3 - 3 * (s < 0.88 ? 0 : Math.pow((s - 0.88) / 0.12, 2));
                swing = 4 * s;
                amount = 0.8 * Math.sin(Math.PI * s);
            }
            model.head.visible = false;
            model.hat.visible = false;
            model.crouching = crouch;
            model.attackTime = 0;
            model.riding = false;
            model.young = false;
            model.setupAnim(dummy, (float) swing, (float) amount, 0, 0, 0);
            // the planes are horizontal in the world: where the bar stands, in the camera-relative space the vertices arrive in
            float worldPlane = pose.last().pose().transformPosition(0, plane / 16f, 0, new org.joml.Vector3f()).y;
            pose.pushPose();
            pose.translate(x / 16, y / 16, z / 16);
            pose.mulPose(Axis.YP.rotationDegrees((float) yaw));
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.scale(-1, -1, 1);
            pose.translate(0, -1.501, 0);
            RenderType type = RenderType.entityCutoutNoCull(skin.texture());
            int base = 0xFFD9B8A6;
            if (t < 1000) {
                // printing: only what is below the bar, in the base material
                model.renderToBuffer(pose, new ClipConsumer(buffers.getBuffer(type), worldPlane, true), light, OverlayTexture.NO_OVERLAY, base);
            } else if (t < 1008) {
                model.renderToBuffer(pose, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, base);
            } else if (t < 1140) {
                // the develop sweeps down with the bar: skin above it, base below
                model.renderToBuffer(pose, new ClipConsumer(buffers.getBuffer(type), worldPlane, false), light, OverlayTexture.NO_OVERLAY, -1);
                model.renderToBuffer(pose, new ClipConsumer(buffers.getBuffer(type), worldPlane, true), light, OverlayTexture.NO_OVERLAY, base);
            } else {
                model.renderToBuffer(pose, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, -1);
            }
            if (t >= 1000) {
                // the Collar Cam, fitted whole at the print's end
                pose.pushPose();
                model.body.translateAndRotate(pose);
                CollarLayer.collar().render(pose, atlas() ? AtlasConsumer.of(buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), CollarLayer.TEXTURE)
                        : buffers.getBuffer(RenderType.entityCutoutNoCull(CollarLayer.TEXTURE)),
                        light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }

        private static net.minecraft.client.resources.PlayerSkin skinOf(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity p) {
            if (p.skin() == null) return net.minecraft.client.resources.DefaultPlayerSkin.get(net.minecraft.Util.NIL_UUID);
            var connection = Minecraft.getInstance().getConnection();
            var info = connection == null ? null : connection.getPlayerInfo(p.skin());
            return info != null ? info.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(p.skin());
        }

        private static double easeInOutSine(double s) {
            return -(Math.cos(Math.PI * Mth.clamp(s, 0, 1)) - 1) / 2;
        }
    }

    // --- Gene Splicer --------------------------------------------------------------------------------------------

    static class Splicer extends Base<LabMachines.Splicer> {
        /** The thread's colour by the gene's category (movement, defence, senses, offence, metabolism, legendary, mutation). */
        private static final int[] CATEGORY = {0x5FA8D3, 0x8E9BA6, 0xB48AD8, 0xD9534F, 0x7CB342, 0xE8B931, 0x9C3FB0};

        Splicer() {
            super("gene_splicer", -12, 0, -9, 11, 32, 9);
        }

        @Override
        protected boolean awake(LabMachines.Splicer s, double now) {
            return MachineAnimations.awake(s, now);
        }

        /** The spools turn while their gene is being sewn, never reset; a pick turns one a quarter more. */
        @Override
        protected void pose(LabMachines.Splicer s, Model<LabMachines.Splicer> model, float partial) {
            double now = MachineAnimations.now(s);
            for (int i = 0; i < 3; i++) {
                GeoBone spool = model.bone("spool_" + (i + 1));
                if (spool == null) continue;
                float angle = Mth.lerp(partial, s.spoolAnglePrev[i], s.spoolAngle[i]);
                double pick = Mth.clamp((now - s.pickedAt[i] - 1) / 6, 0, 1);
                if (now - s.pickedAt[i] < 10) angle += 90 * (float) (-(Math.cos(Math.PI * pick) - 1) / 2);
                spool.setRotY(spool.getInitialSnapshot().getRotY() + (float) Math.toRadians(angle % 360));
            }
        }

        /** The cassette is there only while one is being made or waits to be taken. */
        @Override
        protected int hiddenCubes(LabMachines.Splicer s, GeoBone bone) {
            if (!bone.getName().equals("cassette")) return 0;
            return s.kit().running() || s.hasOutput() ? 0 : 0xFF;
        }

        @Override
        protected int cubeTint(LabMachines.Splicer s, GeoBone bone) {
            String name = bone.getName();
            if (!name.startsWith("spool_")) return -1;
            int gene = s.spool(name.charAt(6) - '1');
            if (gene <= 0 || gene > com.coolfreeze23.headinajar.genetics.Gene.values().length) return -1;
            int category = com.coolfreeze23.headinajar.genetics.Gene.values()[gene - 1].category.ordinal();
            return (1 << 24) | CATEGORY[Math.min(category, CATEGORY.length - 1)];
        }

        private static final double[][] AT = {{6, 13, 6}, {-4, 13, 6}};

        @Override
        protected void contents(LabMachines.Splicer s, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            ItemStack catalyst = s.catalystShown(), gel = s.gelShown() ? new ItemStack(ModRegistry.BIO_GEL.get()) : ItemStack.EMPTY;
            if (!catalyst.isEmpty()) item(pose, buffers, light, s, catalyst, AT[0][0], AT[0][1], AT[0][2], 0.25f, 0, 0);
            if (!gel.isEmpty()) item(pose, buffers, light, s, gel, AT[1][0], AT[1][1], AT[1][2], 0.25f, 0, 0);
        }
    }
}
