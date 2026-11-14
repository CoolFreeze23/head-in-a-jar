package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Assembly Gantry's build plan, one per frame, written with its clips from the same script
 * ({@code assets/headinajar/gantry_plan.json}): how long each job's clip runs, the turntable's angle through the build
 * (a pure function of the build clock, so the server's yaw, the Gantry's turntable and the frame drawn on it never
 * drift apart), when the turntable presents the frame to its owner, the weld contacts and the points of the arcs.
 * <p>
 * Angles are degrees counter-clockwise seen from above, 0 = the frame facing the Gantry's front.
 */
public final class GantryPlan {
    /** Ticks from the Assemble press to the frame standing on the pad (the arm stands off first). */
    public static final int LEAD;

    /** One turntable key: at frame-clock tick {@code t} the angle is {@code angle}, eased in from the key before. */
    public record Key(double t, double angle, boolean eased) {
    }

    public record Contact(String slot, int seat, int contact, String locator, boolean reach) {
    }

    public record Frame(int length, int buildLength, List<Key> turn, double presentFrom, double presentTo, List<Contact> contacts,
                        int disassemble, List<Key> disassembleTurn, int repair, List<Key> repairTurn) {
    }

    private static final Map<MechaFrame, Frame> FRAMES = new EnumMap<>(MechaFrame.class);
    /** Points of the model (pixels, the Gantry facing north): the welds' places and the arcs' two ends. */
    private static final Map<String, Vec3> LOCATORS = new HashMap<>();
    private static final Map<String, Vec3[]> ARCS = new HashMap<>();

    static {
        int lead = 10;
        try (InputStream in = GantryPlan.class.getResourceAsStream("/assets/headinajar/gantry_plan.json")) {
            if (in == null) throw new IllegalStateException("the Gantry's plan is missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            lead = root.get("lead").getAsInt();
            for (var e : root.getAsJsonObject("locators").entrySet()) LOCATORS.put(e.getKey(), vec(e.getValue().getAsJsonArray()));
            for (var e : root.getAsJsonObject("arcs").entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                ARCS.put(e.getKey(), new Vec3[]{vec(a.getAsJsonArray("from")), vec(a.getAsJsonArray("to"))});
            }
            for (var e : root.getAsJsonObject("frames").entrySet()) {
                MechaFrame frame = MechaFrame.valueOf(e.getKey().toUpperCase(java.util.Locale.ROOT));
                JsonObject f = e.getValue().getAsJsonObject();
                List<Contact> contacts = new ArrayList<>();
                for (JsonElement c : f.getAsJsonArray("contacts")) {
                    JsonObject o = c.getAsJsonObject();
                    contacts.add(new Contact(o.get("slot").getAsString(), o.get("seat").getAsInt(), o.get("contact").getAsInt(),
                            o.get("locator").getAsString(), o.get("reach").getAsBoolean()));
                }
                JsonArray present = f.getAsJsonArray("present");
                FRAMES.put(frame, new Frame(f.get("length").getAsInt(), f.get("build_length").getAsInt(), keys(f.getAsJsonArray("turn")),
                        present.get(0).getAsDouble(), present.get(1).getAsDouble(), contacts,
                        f.get("disassemble").getAsInt(), keys(f.getAsJsonArray("disassemble_turn")),
                        f.get("repair").getAsInt(), keys(f.getAsJsonArray("repair_turn"))));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("the Gantry's plan could not be read", e);
        }
        LEAD = lead;
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static List<Key> keys(JsonArray a) {
        List<Key> out = new ArrayList<>();
        for (JsonElement k : a) {
            JsonArray v = k.getAsJsonArray();
            out.add(new Key(v.get(0).getAsDouble(), v.get(1).getAsDouble(), !v.get(2).isJsonNull()));
        }
        return out;
    }

    private GantryPlan() {
    }

    public static Frame of(MechaFrame frame) {
        return FRAMES.get(frame);
    }

    public static Vec3 locator(String name) {
        return LOCATORS.get(name);
    }

    /** An arc's two ends (the tool tip, the socket), or null. */
    public static Vec3[] arc(String name) {
        return ARCS.get(name);
    }

    static double easeInOutSine(double f) {
        return -(Math.cos(Math.PI * f) - 1) / 2;
    }

    /** Piecewise between keys (eased or linear), held outside them; {@code zero} stands in for every key at 0 degrees. */
    static double along(List<Key> keys, double t, double zero) {
        if (keys.isEmpty()) return zero;
        if (t <= keys.get(0).t()) return value(keys.get(0), zero);
        for (int i = 1; i < keys.size(); i++) {
            Key a = keys.get(i - 1), b = keys.get(i);
            if (t <= b.t()) {
                double f = b.t() == a.t() ? 1 : (t - a.t()) / (b.t() - a.t());
                if (b.eased()) f = easeInOutSine(f);
                double va = value(a, zero), vb = value(b, zero);
                return va + (vb - va) * f;
            }
        }
        return value(keys.get(keys.size() - 1), zero);
    }

    private static double value(Key k, double zero) {
        return k.angle() == 0 ? zero : k.angle();
    }

    /** The equivalent of {@code a} nearest {@code ref} (degrees). */
    public static double near(double a, double ref) {
        return ref + net.minecraft.util.Mth.wrapDegrees(a - ref);
    }

    /**
     * The turntable through a build, at frame-clock tick {@code t} (ticks since the frame stood on the pad): the plan's
     * keys, then from {@code presentFrom} a turn to {@code face} (the owner's bearing, the equivalent nearest the angle
     * it turns from), there by {@code presentTo}.
     */
    public static double buildAngle(MechaFrame frame, double t, double face) {
        Frame f = FRAMES.get(frame);
        if (f == null) return 0;
        double last = along(f.turn(), f.presentFrom(), 0);
        if (t <= f.presentFrom()) return along(f.turn(), t, 0);
        double target = near(face, last);
        double k = Math.min(1, (t - f.presentFrom()) / Math.max(1, f.presentTo() - f.presentFrom()));
        return last + (target - last) * easeInOutSine(k);
    }

    /**
     * A repair or a take-apart, at {@code tau} ticks from the press: the plan turns a frame delivered facing the front;
     * a parked frame stands at {@code start} instead, so every key at 0 is read as {@code start} (the equivalent nearest
     * the turned angle, so it turns the short way).
     */
    public static double jobAngle(List<Key> keys, double tau, double start) {
        double turned = -135;
        for (Key k : keys) if (k.angle() != 0) turned = k.angle();
        return along(keys, tau, near(start, turned));
    }
}
