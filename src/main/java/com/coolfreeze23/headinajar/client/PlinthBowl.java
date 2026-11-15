package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.io.Reader;

/**
 * The plinth's bowl and spoon: baked block models (the shelf always, the bowl at its broth step 0-4, the spoon) drawn by
 * the plinth's renderer in the plinth model's own space, turned as the blockstate turns the plinth, and lit as the
 * plinth is (its light, and the level's shade for each face). The spoon taps
 * the bowl on the block event {@link JarredHeadBlockEntity#EV_SPOON}: a turn about X at its pivot, by the art's curve
 * ({@code assets/headinajar/heads/plinth_spoon.json}), read once each resource reload.
 */
public final class PlinthBowl {
    static final ResourceLocation SHELF = HeadInAJar.id("block/plinth_bowl"), SPOON = HeadInAJar.id("block/plinth_spoon");
    static final ResourceLocation[] BROTH = new ResourceLocation[5];
    private static final ResourceLocation CURVE = HeadInAJar.id("heads/plinth_spoon.json");

    static {
        for (int i = 0; i < BROTH.length; i++) BROTH[i] = HeadInAJar.id("block/plinth_broth_" + i);
    }

    /** The tap: keys (tick, degrees, easing on the arriving key), the pivot (block px), its length (ticks). */
    private static float[] keyT = {0}, keyD = {0};
    private static String[] keyEase = {"linear"};
    private static float[] sampled;
    private static float sampleStep = 0.25f;
    private static float pivotX = 13.5f, pivotY = 10.121f, pivotZ = 1.621f, tapTicks = 6;
    private static final RandomSource RANDOM = RandomSource.create(42);
    /** Off only for a cost bench's A/B (the bowl's share of a plinth's frame time); always on in play. */
    public static boolean drawn = true;

    private PlinthBowl() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(PlinthBowl::models);
        modBus.addListener(PlinthBowl::reloads);
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(SHELF));
        event.register(ModelResourceLocation.standalone(SPOON));
        for (ResourceLocation id : BROTH) event.register(ModelResourceLocation.standalone(id));
    }

    private static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) PlinthBowl::load);
    }

    /** Reads the tap curve (a missing or broken file leaves the spoon still). */
    static void load(ResourceManager resources) {
        keyT = new float[]{0};
        keyD = new float[]{0};
        keyEase = new String[]{"linear"};
        sampled = null;
        var res = resources.getResource(CURVE);
        if (res.isEmpty()) return;
        try (Reader reader = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray pivot = o.getAsJsonArray("pivot");
            pivotX = pivot.get(0).getAsFloat();
            pivotY = pivot.get(1).getAsFloat();
            pivotZ = pivot.get(2).getAsFloat();
            JsonArray tap = o.getAsJsonArray("tap");
            int n = tap == null ? 0 : tap.size();
            float[] t = new float[n], d = new float[n];
            String[] e = new String[n];
            for (int i = 0; i < n; i++) {
                JsonArray k = tap.get(i).getAsJsonArray();
                t[i] = k.get(0).getAsFloat();
                d[i] = k.get(1).getAsFloat();
                e[i] = k.size() > 2 ? k.get(2).getAsString() : "linear";
            }
            if (n > 0) {
                keyT = t;
                keyD = d;
                keyEase = e;
            }
            tapTicks = o.has("tap_ticks") ? o.get("tap_ticks").getAsFloat() : n > 0 ? t[n - 1] : 0;
            if (o.has("tap_sampled")) {
                JsonArray s = o.getAsJsonArray("tap_sampled");
                sampled = new float[s.size()];
                for (int i = 0; i < s.size(); i++) sampled[i] = s.get(i).getAsFloat();
                sampleStep = s.size() > 1 ? tapTicks / (s.size() - 1) : 0.25f;
            }
        } catch (Exception ex) {
            HeadInAJar.LOGGER.warn("plinth spoon: could not read {}", CURVE, ex);
        }
    }

    /** The spoon's turn (degrees about X) {@code t} ticks into its tap; 0 outside it. */
    static float tap(double t) {
        if (!(t > 0) || t >= tapTicks) return 0;
        int n = keyT.length;
        for (int i = 1; i < n; i++) {
            if (t > keyT[i]) continue;
            float span = keyT[i] - keyT[i - 1];
            float u = span <= 0 ? 1 : (float) ((t - keyT[i - 1]) / span);
            Float eased = ease(keyEase[i], u);
            if (eased == null) return sampled(t);
            return Mth.lerp(eased, keyD[i - 1], keyD[i]);
        }
        return keyD[n - 1];
    }

    /** The sampled curve (every quarter tick), for an easing this file does not know. */
    private static float sampled(double t) {
        if (sampled == null || sampled.length == 0) return 0;
        double at = t / sampleStep;
        int i = Mth.clamp((int) Math.floor(at), 0, sampled.length - 1), j = Math.min(i + 1, sampled.length - 1);
        return Mth.lerp((float) (at - i), sampled[i], sampled[j]);
    }

    /** GeckoLib's easing names (the ones the art uses and their family), or null for one not known. */
    static Float ease(String name, float u) {
        double x = Mth.clamp(u, 0, 1);
        return switch (name) {
            case "linear" -> (float) x;
            case "step" -> x >= 1 ? 1f : 0f;
            case "easeInSine" -> (float) (1 - Math.cos(x * Math.PI / 2));
            case "easeOutSine" -> (float) Math.sin(x * Math.PI / 2);
            case "easeInOutSine" -> (float) (-(Math.cos(Math.PI * x) - 1) / 2);
            case "easeInQuad" -> (float) (x * x);
            case "easeOutQuad" -> (float) (1 - (1 - x) * (1 - x));
            case "easeInOutQuad" -> (float) (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);
            case "easeInCubic" -> (float) (x * x * x);
            case "easeOutCubic" -> (float) (1 - Math.pow(1 - x, 3));
            case "easeInOutCubic" -> (float) (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
            default -> null;
        };
    }

    /** Each model's quads (all faces), taken once per model bake (the model manager's model is checked by identity). */
    private static final java.util.Map<ResourceLocation, Object[]> QUADS = new java.util.HashMap<>();

    private static java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads(ResourceLocation id, net.minecraft.world.level.block.state.BlockState state) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id));
        Object[] known = QUADS.get(id);
        if (known != null && known[0] == model) {
            @SuppressWarnings("unchecked")
            var list = (java.util.List<net.minecraft.client.renderer.block.model.BakedQuad>) known[1];
            return list;
        }
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> list = new java.util.ArrayList<>();
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            list.addAll(model.getQuads(state, side, RANDOM, ModelData.EMPTY, RenderType.cutout()));
        }
        list.addAll(model.getQuads(state, null, RANDOM, ModelData.EMPTY, RenderType.cutout()));
        QUADS.put(id, new Object[]{model, list});
        return list;
    }

    /**
     * Draws the shelf, the bowl at the head's step and the spoon, in the block's space as the renderer has it (the pose
     * at the block's corner, unturned), turned as the plinth's blockstate turns its model. Lit as the plinth is: the
     * plinth's own light and the level's shade for each face's direction, as the chunk shades a block (no ambient
     * occlusion: on 1 px parts it is not seen, and working it out each frame for every plinth cost 0.7 ms per 40).
     */
    static void draw(JarredHeadBlockEntity head, double now, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = head.getLevel();
        if (level == null || !drawn) return;
        var state = head.getBlockState();
        var buffer = buffers.getBuffer(RenderType.cutout());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(com.coolfreeze23.headinajar.block.JarredHeadBlock.FACING).toYRot()));
        pose.translate(-0.5, 0, -0.5);
        put(level, quads(SHELF, state), pose, buffer, light, overlay);
        put(level, quads(BROTH[Mth.clamp(head.bowlStep(), 0, BROTH.length - 1)], state), pose, buffer, light, overlay);
        float turn = tap(head.since(JarredHeadBlockEntity.EV_SPOON, now));
        if (turn != 0) {
            pose.translate(pivotX / 16f, pivotY / 16f, pivotZ / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(turn));
            pose.translate(-pivotX / 16f, -pivotY / 16f, -pivotZ / 16f);
        }
        put(level, quads(SPOON, state), pose, buffer, light, overlay);
        pose.popPose();
    }

    private static void put(net.minecraft.world.level.BlockAndTintGetter level, java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                            PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay) {
        var last = pose.last();
        for (var quad : quads) {
            float shade = level.getShade(quad.getDirection(), quad.isShade());
            buffer.putBulkData(last, quad, shade, shade, shade, 1, light, overlay);
        }
    }

    /** For a check of the curve in the notes: the turn at each quarter tick of the tap. */
    public static String describe() {
        StringBuilder s = new StringBuilder("pivot ").append(pivotX).append(',').append(pivotY).append(',').append(pivotZ).append(" tap");
        for (float t = 0; t <= tapTicks + 0.001f; t += 0.25f) s.append(' ').append(String.format(java.util.Locale.ROOT, "%.2f", tap(t)));
        return s.toString();
    }
}
