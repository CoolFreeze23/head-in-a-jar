package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

import java.io.Reader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The per-species staff fit: where {@code StaffLayer} draws the lanyard, the Hard Hat and the carried crate on each
 * creature. Client resources {@code assets/<ns>/staff_fit/<species>.json} (written by the art's fit script, never by
 * hand), keyed by their {@code entity} field. Every number is in model px in the named bone's own space. A species with
 * no file (or whose model has not got the bone the file names, as under a model-replacing pack) is drawn from the host
 * neck table instead.
 */
public final class StaffFit {
    /** A part of the model: a Java field of the model class (or {@code root}: the model's root) and a child path below it. */
    public record Bone(String field, String path) {
    }

    /** A frame on a bone: {@code at} (px) and {@code rot} (degrees, applied Z, Y, X as model parts are). */
    public record Frame(Bone bone, float[] at, float[] rot) {
    }

    /**
     * The loop (cord boxes in the ring's frame) and the hang (pivot, its turn, strands, the badge's top centre), and a
     * Team Lead's tie on the hang (null: none on this species).
     */
    public record Lanyard(Frame frame, float[][] cord, float[] pivot, float[] hangRot, float[][] strands, float[] badgeAt,
                          boolean mini, float sway, Tie tie) {
        public Lanyard(Frame frame, float[][] cord, float[] pivot, float[] hangRot, float[][] strands, float[] badgeAt,
                       boolean mini, float sway) {
            this(frame, cord, pivot, hangRot, strands, badgeAt, mini, sway, null);
        }
    }

    /**
     * The tie in the hang frame, flat quads facing -z: the blade's top-left {@code at} (px), 1 px wide and {@code length}
     * long ({@code bladeUv} its column's top {@code length} texels), and its 3 x 2 broad end's top-left {@code tip} (null:
     * the blade alone, hidden behind the card).
     */
    public record Tie(float[] at, float length, float[] tip, float[] bladeUv) {
    }

    /** The Hard Hat: drawn by the armour layer ({@code armor}) or the baked hat of {@code size} at the frame. */
    public record Hat(boolean armor, Frame frame, String size) {
    }

    /**
     * The crate: the baked crate at the frame, or carried the vanilla way ({@code vanilla}: mouth, hands, block); a crate
     * carried on top of the head lifts the hat by {@code hatLift} px (model up) while both are worn.
     */
    public record Crate(String vanilla, Frame frame, float hatLift) {
    }

    /**
     * A model that draws itself through a transform of its own (the rabbit draws an adult at 0.6): {@code scale}, then
     * {@code offset} px in the scaled space, applied before every bone chain so the fit lands where the model is drawn.
     */
    public record Render(float scale, float[] offset) {
        public void apply(PoseStack pose) {
            pose.scale(scale, scale, scale);
            pose.translate(offset[0] / 16f, offset[1] / 16f, offset[2] / 16f);
        }
    }

    /** {@code render}: null for a model drawn as its parts say. */
    public record Fit(Lanyard lanyard, Hat hat, Crate crate, Render render) {
    }

    private static volatile Map<EntityType<?>, Fit> fits = Map.of();
    /** Each model's resolved parts by bone (null in the map: that model has no such bone). */
    private static final Map<EntityModel<?>, Map<Bone, ModelPart[]>> CHAINS = new WeakHashMap<>();
    private static final ModelPart[] NONE = new ModelPart[0];

    private StaffFit() {
    }

    /** The fit of a species, or null. */
    public static Fit of(EntityType<?> type) {
        return fits.get(type);
    }

    public static int count() {
        return fits.size();
    }

    // --- bones ------------------------------------------------------------------------------------------------------

    /** The part chain of a bone on this model (root first), or null when the model has not got it. */
    public static ModelPart[] chain(EntityModel<?> model, Bone bone) {
        Map<Bone, ModelPart[]> known = CHAINS.computeIfAbsent(model, m -> new HashMap<>());
        ModelPart[] chain = known.get(bone);
        if (chain == null) {
            chain = resolve(model, bone);
            known.put(bone, chain);
        }
        return chain.length == 0 ? null : chain;
    }

    private static ModelPart[] resolve(EntityModel<?> model, Bone bone) {
        try {
            ModelPart first = null;
            if ("root".equals(bone.field()) && model instanceof HierarchicalModel<?> h) first = h.root();
            else {
                for (Class<?> c = model.getClass(); c != null && first == null; c = c.getSuperclass()) {
                    try {
                        Field f = c.getDeclaredField(bone.field());
                        f.setAccessible(true);
                        if (f.get(model) instanceof ModelPart p) first = p;
                    } catch (NoSuchFieldException ignored) {
                        // a superclass may hold it
                    }
                }
            }
            if (first == null) return NONE;
            java.util.List<ModelPart> out = new java.util.ArrayList<>();
            out.add(first);
            ModelPart at = first;
            if (!bone.path().isEmpty()) {
                for (String child : bone.path().split("/")) {
                    if (!at.hasChild(child)) return NONE;
                    at = at.getChild(child);
                    out.add(at);
                }
            }
            return out.toArray(new ModelPart[0]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeadInAJar.LOGGER.debug("No staff fit bone {} on {}", bone, model.getClass().getName());
            return NONE;
        }
    }

    /** Moves {@code pose} into a frame: through the bone's parts, then to {@code at} and turned by {@code rot}. False: no bone. */
    public static boolean enter(PoseStack pose, EntityModel<?> model, Frame frame) {
        ModelPart[] chain = chain(model, frame.bone());
        if (chain == null) return false;
        for (ModelPart p : chain) p.translateAndRotate(pose);
        pose.translate(frame.at()[0] / 16f, frame.at()[1] / 16f, frame.at()[2] / 16f);
        rotate(pose, frame.rot());
        return true;
    }

    /** A turn in degrees, Z then Y then X as {@code ModelPart} turns (X applied to the vector first). */
    public static void rotate(PoseStack pose, float[] rot) {
        if (rot[0] != 0 || rot[1] != 0 || rot[2] != 0) {
            pose.mulPose(new Quaternionf().rotationZYX(rot[2] * (float) (Math.PI / 180), rot[1] * (float) (Math.PI / 180), rot[0] * (float) (Math.PI / 180)));
        }
    }

    // --- loading ----------------------------------------------------------------------------------------------------

    static Fit parse(JsonObject o) {
        Lanyard lanyard = null;
        if (o.get("lanyard") instanceof JsonObject l) {
            JsonObject hang = l.getAsJsonObject("hang");
            JsonObject badge = hang.getAsJsonObject("badge");
            lanyard = new Lanyard(frame(l), boxes(l.getAsJsonArray("cord")), vec(hang.get("pivot")), vec(hang.get("rot")),
                    boxes(hang.getAsJsonArray("cord")), vec(badge.get("at")), "mini".equals(string(badge, "size", "full")),
                    hang.has("sway") ? hang.get("sway").getAsFloat() : 1, tie(hang.get("tie")));
        }
        Hat hat = null;
        if (o.get("hat") instanceof JsonObject h) {
            hat = h.has("armor") && h.get("armor").getAsBoolean() ? new Hat(true, null, null) : new Hat(false, frame(h), string(h, "size", "l"));
        }
        Crate crate = null;
        if (o.get("crate") instanceof JsonObject c) {
            float lift = c.has("hat_lift") ? c.get("hat_lift").getAsFloat() : 0;
            // a vanilla carry may name a frame too: where the crate goes while the vanilla way has nothing to show
            crate = c.has("vanilla") ? new Crate(c.get("vanilla").getAsString(), c.has("bone") ? frame(c) : null, lift)
                    : new Crate(null, frame(c), lift);
        }
        Render render = null;
        if (o.get("render") instanceof JsonObject r) {
            render = new Render(r.has("scale") ? r.get("scale").getAsFloat() : 1, vec(r.get("offset")));
        }
        return new Fit(lanyard, hat, crate, render);
    }

    private static Tie tie(JsonElement e) {
        if (!(e instanceof JsonObject t) || !t.has("length")) return null;
        float length = Math.max(0, Math.min(16, t.get("length").getAsFloat()));
        float[] blade = com.coolfreeze23.headinajar.client.StaffBadge.TIE;
        return new Tie(vec(t.get("at")), length, t.has("tip") && !t.get("tip").isJsonNull() ? vec(t.get("tip")) : null,
                new float[]{blade[0], blade[1], blade[2], blade[1] + length});
    }

    private static Frame frame(JsonObject o) {
        JsonObject b = o.getAsJsonObject("bone");
        return new Frame(new Bone(b.get("field").getAsString(), string(b, "path", "")), vec(o.get("at")), vec(o.get("rot")));
    }

    private static String string(JsonObject o, String key, String or) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : or;
    }

    private static float[] vec(JsonElement e) {
        float[] v = new float[3];
        if (e instanceof JsonArray a) for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    private static float[][] boxes(JsonArray a) {
        if (a == null) return new float[0][];
        float[][] out = new float[a.size()][6];
        for (int i = 0; i < a.size(); i++) {
            JsonArray b = a.get(i).getAsJsonArray();
            for (int k = 0; k < 6; k++) out[i][k] = b.get(k).getAsFloat();
        }
        return out;
    }

    /** Reads every {@code staff_fit/**.json} of every namespace when resources load. */
    public static final class Loader extends SimplePreparableReloadListener<Map<EntityType<?>, Fit>> {
        @Override
        protected Map<EntityType<?>, Fit> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<EntityType<?>, Fit> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> file : manager.listResources("staff_fit", id -> id.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = file.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    String path = file.getKey().getPath();
                    String name = path.substring("staff_fit/".length(), path.length() - 5);
                    ResourceLocation id = o.has("entity") ? ResourceLocation.parse(o.get("entity").getAsString())
                            : name.contains("/") ? ResourceLocation.fromNamespaceAndPath(name.substring(0, name.indexOf('/')), name.substring(name.indexOf('/') + 1))
                            : ResourceLocation.withDefaultNamespace(name);
                    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
                    if (type.isPresent()) out.put(type.get(), parse(o));
                } catch (Exception e) {
                    HeadInAJar.LOGGER.warn("Staff fit {} could not be read: {}", file.getKey(), e.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<EntityType<?>, Fit> loaded, ResourceManager manager, ProfilerFiller profiler) {
            fits = Map.copyOf(loaded);
            CHAINS.clear();
        }
    }
}
