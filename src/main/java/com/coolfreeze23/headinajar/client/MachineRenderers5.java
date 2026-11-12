package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/** The fifth batch's renderers on the kit ({@link MachineRenderers.Base}): the Neural Socket and the Leech Jar. */
public final class MachineRenderers5 {
    private MachineRenderers5() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.SOCKET_BE.get(), c -> new Socket());
        event.registerBlockEntityRenderer(ModRegistry.LEECH_JAR_BE.get(), c -> new Leech());
    }

    // --- Neural Socket -----------------------------------------------------------------------------------------

    static class Socket extends MachineRenderers.Base<NeuralSocketBlockEntity> {
        private static final net.minecraft.resources.ResourceLocation LEVEL = HeadInAJar.id("block/machine/neural_socket_level");
        /** Off by this much (px) outside its plane, so a bar never fights the face it lies on. */
        private static final float PROUD = 0.02f;

        Socket() {
            // the clamps rise to 18 px; the jar on top is drawn by the jar's own code up to 33 px
            super("neural_socket", -8, 0, -8, 8, 34, 8);
        }

        @Override
        protected boolean awake(NeuralSocketBlockEntity s, double now) {
            return MachineAnimations5.awake(s, now);
        }

        @Override
        protected boolean still(NeuralSocketBlockEntity s) {
            return !MachineAnimations5.awake(s, MachineAnimations.now(s));
        }

        /** Shut: the clamps up 6 px round the jar's base, the jack up 1 px into it (the clamp clip's last frame). */
        @Override
        protected void heldPose(NeuralSocketBlockEntity s, MachineRenderers.Model<NeuralSocketBlockEntity> model) {
            if (!s.occupied()) return;
            hold(model.bone("claws"), 0, 6, 0);
            hold(model.bone("plug"), 0, 1, 0);
        }

        /** A placed head is a placed thing, seen as far as the socket is (the old renderer's distance). */
        @Override
        public int getViewDistance() {
            return 64;
        }

        /** The head and the bars are what the socket is for: drawn wherever the socket is drawn, not only within 16 blocks. */
        @Override
        public void postRender(PoseStack pose, NeuralSocketBlockEntity s, BakedGeoModel model, MultiBufferSource buffers,
                               @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay, int colour) {
            if (isReRender || buffers == null || (MachineRenderers.debugOff & 1) != 0 || s.getLevel() == null) return;
            contents(s, pose, buffers, light, partialTick);
        }

        @Override
        protected void contents(NeuralSocketBlockEntity s, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
            var level = s.getLevel();
            if (level == null) return;
            if (!s.head().isEmpty()) {
                // the real jar at 1:1, its base on the deck at (0, 16, 0) px, square to the socket's front
                int above = LevelRenderer.getLightColor(level, s.getBlockPos().above());
                pose.pushPose();
                pose.translate(0, 1, 0);
                HeadWorkRenderers.drawHead(s.head(), s.headInfo(), pose, buffers, Math.max(light, above), level.getGameTime() + partial, 1.0f);
                pose.popPose();
            }
            bars(s, pose, buffers, level.getGameTime(), MachineAnimations.now(s));
        }

        /** The six channels as lit bars: four slots, the band under the cap (up) and the foot band (down), full-bright. */
        private static void bars(NeuralSocketBlockEntity s, PoseStack pose, MultiBufferSource buffers, long time, double now) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LEVEL);
            VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            Direction facing = MachineRenderers.Base.facing(s);
            // the six light once in turn as the clamps bite (down, up, north, south, west, east, a tick each)
            double chase = s.occupied() ? now - s.changedAt - MachineAnimations5.CLAMP : -1;
            int[] lv = new int[6];
            for (Direction d : Direction.values()) {
                int i = d.get3DDataValue();
                lv[i] = chase >= 0 && chase < 6 && (int) chase == i ? 15 : s.shown(d, time);
            }
            int light = LightTexture.FULL_BRIGHT;
            // the four sides, in the model's frame (its front is -Z); each shows the channel of the world face it is turned to
            for (int q = 0; q < 4; q++) {
                // q: 0 front (-Z), 1 right (+X), 2 back (+Z), 3 left (-X), clockwise from above
                Direction world = turn(Direction.NORTH, q + turns(facing));
                int level = lv[world.get3DDataValue()];
                if (level > 0) {
                    float h = 7 * level / 15f;
                    // the front slot is off-centre (x -5..-1); the others centred (-2..2)
                    float u0 = q == 0 ? -5 : -2, u1 = q == 0 ? -1 : 2;
                    side(pose, v, sprite, light, q, 6, u0, u1, 5, 5 + h);
                }
                int up = lv[Direction.UP.get3DDataValue()], down = lv[Direction.DOWN.get3DDataValue()];
                if (up > 0) {
                    float w = 5 * up / 15f;
                    side(pose, v, sprite, light, q, 6, -w, w, 13, 15);
                }
                if (down > 0) {
                    float w = 7 * down / 15f;
                    side(pose, v, sprite, light, q, 7, -w, w, 0, 2);
                }
            }
        }

        /** Quarter turns clockwise from north. */
        private static int turns(Direction facing) {
            return switch (facing) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
        }

        private static Direction turn(Direction d, int clockwise) {
            for (int i = 0; i < Math.floorMod(clockwise, 4); i++) d = d.getClockWise();
            return d;
        }

        /**
         * A lit rectangle on side {@code q} of the model (0 front -Z, 1 right +X, 2 back +Z, 3 left -X), on the plane
         * {@code plane} px from the axis, across {@code u0..u1} px (left to right as seen from outside) and {@code y0..y1} px.
         */
        private static void side(PoseStack pose, VertexConsumer v, TextureAtlasSprite sprite, int light, int q, float plane, float u0, float u1, float y0, float y1) {
            var m = pose.last();
            float d = (plane + PROUD) / 16f, a0 = u0 / 16f, a1 = u1 / 16f, b0 = y0 / 16f, b1 = y1 / 16f;
            float su0 = sprite.getU0(), su1 = sprite.getU1(), sv0 = sprite.getV0(), sv1 = sprite.getV1();
            // the four corners as (x, z) for each side, wound to face outward
            // the corners as (x, z) in the model's own px (+X is the machine's right, the viewer's left from the front)
            float[][] c = switch (q) {
                case 0 -> new float[][]{{a0, -d}, {a1, -d}};
                case 1 -> new float[][]{{d, a0}, {d, a1}};
                case 2 -> new float[][]{{a0, d}, {a1, d}};
                default -> new float[][]{{-d, a0}, {-d, a1}};
            };
            float nx = q == 1 ? 1 : q == 3 ? -1 : 0, nz = q == 0 ? -1 : q == 2 ? 1 : 0;
            vertex(v, m, c[0][0], b1, c[0][1], su0, sv0, light, nx, nz);
            vertex(v, m, c[0][0], b0, c[0][1], su0, sv1, light, nx, nz);
            vertex(v, m, c[1][0], b0, c[1][1], su1, sv1, light, nx, nz);
            vertex(v, m, c[1][0], b1, c[1][1], su1, sv0, light, nx, nz);
        }

        private static void vertex(VertexConsumer v, PoseStack.Pose m, float x, float y, float z, float u, float vv, int light, float nx, float nz) {
            v.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, nx, 0, nz);
        }
    }

    // --- Leech Jar -----------------------------------------------------------------------------------------------

    static class Leech extends MachineRenderers.Base<LeechJarBlockEntity> {
        /** The leech's third cube is the Butcher's paper hat. */
        private static final int HAT = 1 << 2;

        Leech() {
            // the nozzle popped out at a side reaches 12 px; the cap's finial 29
            super("leech_jar", -12, 0, -12, 12, 30, 12);
        }

        @Override
        protected boolean awake(LeechJarBlockEntity j, double now) {
            return MachineAnimations5.awake(j, now);
        }

        @Override
        protected boolean still(LeechJarBlockEntity j) {
            return !MachineAnimations5.awake(j, MachineAnimations.now(j));
        }

        /** Full and sulking: curled on its side (the sulk clip's last frame). */
        @Override
        protected void heldPose(LeechJarBlockEntity j, MachineRenderers.Model<LeechJarBlockEntity> model) {
            GeoBone leech = model.bone("leech");
            if (j.full() && leech != null) leech.setRotZ(leech.getInitialSnapshot().getRotZ() + (float) Math.toRadians(25));
        }

        /** The carrier (the leech and the nozzle) turned to the creature's side, a quarter turn clockwise (from above) per side. */
        @Override
        protected void pose(LeechJarBlockEntity j, MachineRenderers.Model<LeechJarBlockEntity> model, float partial) {
            GeoBone carrier = model.bone("carrier");
            if (carrier == null) return;
            float angle = j.carrierPrev + Mth.wrapDegrees(j.carrier - j.carrierPrev) * partial;
            carrier.setRotY(carrier.getInitialSnapshot().getRotY() - (float) Math.toRadians(angle));
        }

        @Override
        protected int hiddenCubes(LeechJarBlockEntity j, GeoBone bone) {
            return bone.getName().equals("leech") && !j.hat ? HAT : 0;
        }
    }
}
