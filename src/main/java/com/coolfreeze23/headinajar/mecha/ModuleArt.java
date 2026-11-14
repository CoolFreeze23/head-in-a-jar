package com.coolfreeze23.headinajar.mecha;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What each module brings to a frame, found by name: which of its two sizes a frame takes, its clips, and which of its
 * cubes are lit (always, while it works, by the charge) or tinted. Read once from {@code mecha/module_art.json}, which
 * the data generator writes from the module models, their masks and the size table. A frame needs nothing here of its
 * own: a frame id the table does not name takes the light size of every module.
 * <p>
 * Files: {@code geo/entity/mecha/module/<file>.geo.json}, {@code animations/entity/mecha/module/<file>.animation.json}
 * and {@code textures/entity/mecha/module/<file>.png} (with {@code _glowmask}, {@code _firing}, {@code _charge}), where
 * {@code <file>} is the module's id, or its id + {@code _heavy}.
 */
public final class ModuleArt {
    /** A cube of a module model by its bone and its place among that bone's cubes, and a tint (rgb, or -1). */
    public record Cube(String bone, int index, int tint) {
    }

    /** One size of one module: the file stem and its lit and tinted cubes. */
    public record Art(String file, List<Cube> glow, List<Cube> firing, List<Cube> charge, List<Cube> tint) {
    }

    /** A clip of a module: its length in ticks and how it ends ({@code loop}, {@code hold} on its last frame, or {@code once}). */
    public record Clip(double length, String loop) {
        public boolean loops() {
            return loop.equals("loop") || loop.equals("true");
        }
    }

    private static final String PATH = "/assets/headinajar/mecha/module_art.json";
    private static Map<String, Map<String, Clip>> clips;
    private static Map<String, Art[]> sizes;
    private static Map<String, Set<String>> heavy;
    private static Map<String, MechaModule.Slot> slots;

    private ModuleArt() {
    }

    public static String id(MechaModule module) {
        return module.name().toLowerCase(Locale.ROOT);
    }

    /** Whether the table knows this module (the generator found its model). */
    public static boolean has(MechaModule module) {
        load();
        return sizes.containsKey(id(module));
    }

    /** The size a frame takes of a module: heavy only where the table says so and the module has a heavy file. */
    public static Art art(String frame, MechaModule module) {
        load();
        Art[] both = sizes.get(id(module));
        if (both == null) return null;
        Set<String> big = heavy.get(frame);
        return big != null && big.contains(id(module)) && both[1] != null ? both[1] : both[0];
    }

    public static boolean heavy(String frame, MechaModule module) {
        Art art = art(frame, module);
        return art != null && art.file().endsWith("_heavy");
    }

    /** The module's clips by name (both sizes carry the same). */
    public static Map<String, Clip> clips(MechaModule module) {
        load();
        return clips.getOrDefault(id(module), Map.of());
    }

    public static MechaModule.Slot slot(MechaModule module) {
        load();
        return slots.get(id(module));
    }

    /** The frames the size table names. */
    public static Set<String> frames() {
        load();
        return heavy.keySet();
    }

    private static synchronized void load() {
        if (sizes != null) return;
        Map<String, Map<String, Clip>> c = new HashMap<>();
        Map<String, Art[]> s = new HashMap<>();
        Map<String, Set<String>> h = new HashMap<>();
        Map<String, MechaModule.Slot> sl = new HashMap<>();
        try (InputStream in = ModuleArt.class.getResourceAsStream(PATH)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.getAsJsonObject("modules").entrySet()) {
                    JsonObject m = e.getValue().getAsJsonObject();
                    Map<String, Clip> list = new HashMap<>();
                    for (var k : m.getAsJsonObject("clips").entrySet()) {
                        JsonArray a = k.getValue().getAsJsonArray();
                        list.put(k.getKey(), new Clip(a.get(0).getAsDouble(), a.get(1).getAsString()));
                    }
                    c.put(e.getKey(), Map.copyOf(list));
                    sl.put(e.getKey(), MechaModule.Slot.valueOf(m.get("slot").getAsString()));
                    Art light = art(e.getKey(), m.getAsJsonObject("light"));
                    Art big = m.get("two_sizes").getAsBoolean() ? art(e.getKey() + "_heavy", m.getAsJsonObject("heavy")) : null;
                    s.put(e.getKey(), new Art[]{light, big});
                }
                for (var e : root.getAsJsonObject("heavy").entrySet()) {
                    Set<String> set = new HashSet<>();
                    for (JsonElement x : e.getValue().getAsJsonArray()) set.add(x.getAsString());
                    h.put(e.getKey(), Set.copyOf(set));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("cannot read " + PATH, ex);
        }
        clips = c;
        heavy = h;
        slots = sl;
        sizes = s;
    }

    private static Art art(String file, JsonObject parts) {
        if (parts == null) parts = new JsonObject();
        return new Art(file, cubes(parts, "glow"), cubes(parts, "firing"), cubes(parts, "charge"), cubes(parts, "tint"));
    }

    private static List<Cube> cubes(JsonObject parts, String kind) {
        List<Cube> out = new ArrayList<>();
        if (!parts.has(kind)) return List.of();
        for (JsonElement x : parts.getAsJsonArray(kind)) {
            JsonArray a = x.getAsJsonArray();
            out.add(new Cube(a.get(0).getAsString(), a.get(1).getAsInt(), a.size() > 2 ? Integer.parseInt(a.get(2).getAsString(), 16) : -1));
        }
        return List.copyOf(out);
    }
}
