package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The baked rest pose, a kit feature for every machine. A machine whose moving parts all stand still (at rest, or held
 * open or up) has the renderer draw that pose once into quads, in the block's own space, cut at the top of its cell;
 * those quads join its block model (the lower cell's half here, the upper half in the machine's top cell) through
 * model data, so the chunk draws them like the rest of the machine and GeckoLib draws nothing. The moment anything
 * should move, the quads are dropped and GeckoLib draws again. Both hand-overs ask for the section to be rebuilt at
 * once, and the renderer keeps drawing until the chunk has meshed the baked pose and {@link #OVERLAP} frames more, so no frame shows the part
 * missing (the reverse hand-over shows the rest pose under the first frame of the moving one, where they coincide).
 * What is baked is exactly what the renderer would have drawn (the same pose code, hidden cubes and tints), so the
 * hand-over changes nothing but who draws it.
 */
public final class RestBake {
    /** Frames the renderer keeps drawing after it handed its pose to the chunk. */
    public static final int OVERLAP = 2;
    private static final ModelProperty<List<BakedQuad>> QUADS = new ModelProperty<>();
    private static long frame;

    /** The baked pose of one machine: its two halves and when it was handed over; the signature finds a changed pose. */
    public static final class Baked {
        final List<BakedQuad> lower, upper;
        final long frame, checkedAt;
        final int signature;
        /** The frame the chunk meshed these quads (set from the meshing thread; -1 not yet): the renderer stops after it. */
        volatile long meshedFrame = -1;

        Baked(List<BakedQuad> lower, List<BakedQuad> upper, long frame, int signature, long checkedAt) {
            this.lower = lower;
            this.upper = upper;
            this.frame = frame;
            this.signature = signature;
            this.checkedAt = checkedAt;
        }

        public long frame() {
            return frame;
        }

        public int signature() {
            return signature;
        }

        public long checkedAt() {
            return checkedAt;
        }

        List<BakedQuad> lower() {
            return lower;
        }

        List<BakedQuad> upper() {
            return upper;
        }

        /** The chunk has drawn the baked pose for at least a frame: the renderer may stop. */
        public boolean shown(long now) {
            return meshedFrame >= 0 && now > meshedFrame + OVERLAP;
        }

        /** The same pose re-checked, keeping what the chunk has already meshed. */
        Baked again(Baked next) {
            next.meshedFrame = next.signature == signature ? meshedFrame : -1;
            return next;
        }
    }

    public static long frame() {
        return frame;
    }

    public static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }

    /** Collects what a model draws, in quads of the block's own space (no light, no overlay). */
    public static final class Capture implements VertexConsumer {
        final List<float[]> quads = new ArrayList<>();
        private float[] quad = new float[4 * 9];
        private int n;

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int o = n * 9;
            quad[o] = x;
            quad[o + 1] = y;
            quad[o + 2] = z;
            quad[o + 3] = Float.intBitsToFloat(color);
            quad[o + 4] = u;
            quad[o + 5] = v;
            quad[o + 6] = nx;
            quad[o + 7] = ny;
            quad[o + 8] = nz;
            if (++n == 4) {
                quads.add(quad);
                quad = new float[4 * 9];
                n = 0;
            }
        }

        // a cube's quads come through the one call above
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        int signature() {
            int h = quads.size();
            for (float[] q : quads) h = 31 * h + Arrays.hashCode(q);
            return h;
        }

        /** The quads cut at the top of the lower cell (y = 1): the lower part, and the upper part moved down a block. */
        Baked bake(TextureAtlasSprite sprite, long frame, long time) {
            return bake(sprite, frame, time, true);
        }

        /** As above; uncut ({@code cut} false), every quad stays in the machine's own cell (a machine with no top cell). */
        Baked bake(TextureAtlasSprite sprite, long frame, long time, boolean cut) {
            List<BakedQuad> lower = new ArrayList<>(), upper = new ArrayList<>();
            QuadBakingVertexConsumer baker = new QuadBakingVertexConsumer();
            baker.setSprite(sprite);
            // lit as the renderer lit it: the entity diffuse light baked into the colour, no face shade, no occlusion
            // (the chunk's own shading differs, and a part would change shade at the hand-over)
            baker.setShade(false);
            baker.setHasAmbientOcclusion(false);
            float[] kept = new float[8 * 9];
            for (float[] q : quads) {
                if (!cut) {
                    lower.add(quad(baker, sprite, q, 0, 1, 2, 3, 0));
                    continue;
                }
                for (int side = 0; side < 2; side++) {
                    int k = clip(q, side == 0, kept);
                    if (k < 3) continue;
                    List<BakedQuad> to = side == 0 ? lower : upper;
                    float shift = side == 0 ? 0 : -1;
                    to.add(quad(baker, sprite, kept, 0, 1, 2, k > 3 ? 3 : 2, shift));
                    if (k == 5) to.add(quad(baker, sprite, kept, 0, 3, 4, 4, shift));
                }
            }
            return new Baked(List.copyOf(lower), List.copyOf(upper), frame, signature(), time);
        }

        /** Sutherland-Hodgman against y = 1, keeping below or above; 3 to 5 corners into {@code kept}. */
        private static int clip(float[] q, boolean below, float[] kept) {
            int k = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                float da = below ? 1 - q[i * 9 + 1] : q[i * 9 + 1] - 1, db = below ? 1 - q[j * 9 + 1] : q[j * 9 + 1] - 1;
                if (da >= 0) System.arraycopy(q, i * 9, kept, 9 * k++, 9);
                if (da > 0 && db < 0 || da < 0 && db > 0) {
                    float t = da / (da - db);
                    for (int c = 0; c < 9; c++) kept[9 * k + c] = c == 3 ? q[i * 9 + 3] : q[i * 9 + c] + (q[j * 9 + c] - q[i * 9 + c]) * t;
                    k++;
                }
            }
            return k;
        }

        /** The level's entity lighting for a face normal (two lights, 0.6 of their sum over 0.4 ambient), as the shader computes it. */
        static float diffuse(float nx, float ny, float nz) {
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len == 0) return 1;
            nx /= len;
            ny /= len;
            nz /= len;
            float a = (0.2f * nx + 1f * ny - 0.7f * nz) / (float) Math.sqrt(0.04 + 1 + 0.49);
            float b = (-0.2f * nx + 1f * ny + 0.7f * nz) / (float) Math.sqrt(0.04 + 1 + 0.49);
            return Math.min(1, (Math.max(0, a) + Math.max(0, b)) * 0.6f + 0.4f);
        }

        private static BakedQuad quad(QuadBakingVertexConsumer baker, TextureAtlasSprite sprite, float[] v, int a, int b, int c, int d, float shift) {
            float nx = v[a * 9 + 6], ny = v[a * 9 + 7], nz = v[a * 9 + 8];
            baker.setDirection(Direction.getNearest(nx, ny, nz));
            float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
            float light = diffuse(nx, ny, nz);
            for (int i : new int[]{a, b, c, d}) {
                int o = i * 9, color = Float.floatToRawIntBits(v[o + 3]);
                baker.addVertex(v[o], v[o + 1] + shift, v[o + 2])
                        .setColor(Math.round(((color >> 16) & 0xFF) * light), Math.round(((color >> 8) & 0xFF) * light), Math.round((color & 0xFF) * light),
                                (color >>> 24) & 0xFF)
                        .setUv(u0 + v[o + 4] * du, v0 + v[o + 5] * dv)
                        .setUv2(0, 0)
                        .setNormal(nx, ny, nz);
            }
            return baker.bakeQuad();
        }
    }

    /** Asks for the machine's two cells to be meshed again before the next frame is drawn. */
    public static void remesh(Level level, BlockPos pos) {
        var renderer = Minecraft.getInstance().levelRenderer;
        BlockState state = level.getBlockState(pos);
        renderer.blockChanged(level, pos, state, state, Block.UPDATE_IMMEDIATE);
        BlockState top = level.getBlockState(pos.above());
        renderer.blockChanged(level, pos.above(), top, top, Block.UPDATE_IMMEDIATE);
    }

    /** A machine's block model (either cell) with its baked rest pose added when it has one. */
    static final class SwitchModel extends BakedModelWrapper<BakedModel> {
        private final boolean upper;

        SwitchModel(BakedModel original, boolean upper) {
            super(original);
            this.upper = upper;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            ModelData data = super.getModelData(level, pos, state, modelData);
            var be = level.getBlockEntity(upper ? pos.below() : pos);
            if (be instanceof KitMachine machine && machine.kit().restBake instanceof Baked baked) {
                // meshed now: the renderer hands over once this has been on screen
                if (baked.meshedFrame < 0) baked.meshedFrame = frame;
                List<BakedQuad> quads = upper ? baked.upper() : baked.lower();
                if (!quads.isEmpty()) return data.derive().with(QUADS, quads).build();
            }
            return data;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
            List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
            List<BakedQuad> rest = side == null && (renderType == null || renderType == RenderType.cutout()) ? data.get(QUADS) : null;
            if (rest == null) return base;
            List<BakedQuad> all = new ArrayList<>(base.size() + rest.size());
            all.addAll(base);
            all.addAll(rest);
            return all;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
            return data.get(QUADS) == null ? base : ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.cutout()));
        }
    }

    /** Every state of every kit machine, and of the top cell, gets the switch. */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Block block : KitMachines.blocks()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (key, model) -> new SwitchModel(model, false));
            }
        }
        for (BlockState state : ModRegistry.MACHINE_TOP.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (key, model) -> new SwitchModel(model, true));
        }
    }

    /** The kit machines' blocks (the ones whose renderers bake their rest pose). */
    static final class KitMachines {
        static List<Block> blocks() {
            return List.of(ModRegistry.GALVANIC_PILE.get(), ModRegistry.SALINE_MIXER.get(), ModRegistry.CENTRIFUGE.get(),
                    ModRegistry.CRADLE.get(), ModRegistry.BODY_PRINTER.get(), ModRegistry.GENE_SPLICER.get(),
                    ModRegistry.CHARGING_DOCK.get(), ModRegistry.TESLA_NODE.get(), ModRegistry.INJECTION_RIG.get(), ModRegistry.DNA_SEQUENCER.get(), ModRegistry.CEREBRAL_ARRAY.get(), ModRegistry.EMPLOYEE_MOUNT.get(),
                    ModRegistry.RELAY_MAST.get(), ModRegistry.RELAY_MAST_MK2.get(), ModRegistry.SIGNAL_JAMMER.get(), ModRegistry.TURRET_PEDESTAL.get(), ModRegistry.BODY_LOCKER.get(),
                    ModRegistry.NEURAL_SOCKET.get(), ModRegistry.LEECH_JAR.get());
        }
    }

    private RestBake() {
    }
}
