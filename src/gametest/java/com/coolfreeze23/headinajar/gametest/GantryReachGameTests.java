package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.machine.GantryPlan;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * The Assembly Gantry's arm reaches every frame: its upper arm and forearm telescope for the tall frames, so the tool
 * meets the Colossus's and the Executive's sockets up to their shoulders instead of striking an arc from the knees;
 * the contacts are on their seat ticks but for the few listed; the telescopes are home at the end of every clip and in
 * every held pose.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GantryReachGameTests {
    /**
     * Contacts struck as an arc, frame id + "_" + slot: a core socket inside the hull, or a reach the arm cannot come
     * into from the contact before (or from standing off) cleanly in time, where the arc lands on time instead.
     */
    static final Set<String> ARCS = Set.of("hopper_core", "bulwark_back", "bulwark_back_1", "bulwark_torso_2",
            "colossus_legs", "colossus_back", "colossus_back_1", "executive_back", "wheelhouse_back_1", "wheelhouse_core",
            "skitter_back", "skitter_core", "skitter_arm_l", "kite_core");

    /**
     * Contacts that still come after their seat, and at most how many ticks: the Colossus's second shoulder pod can be
     * met only with the frame turned 150 degrees from where its other shoulders and sensors are met, six ticks apart.
     */
    static final Map<String, Integer> LATE = Map.ofEntries(Map.entry("colossus_torso_4", 1), Map.entry("colossus_arm_2", 6),
            Map.entry("colossus_arm_3", 11), Map.entry("colossus_sensor", 16), Map.entry("colossus_sensor_1", 14),
            Map.entry("bulwark_torso_2", 1), Map.entry("wheelhouse_torso_1", 1), Map.entry("skitter_torso_1", 4),
            Map.entry("skitter_arm_l", 1), Map.entry("kite_core", 3), Map.entry("kite_torso_0", 4), Map.entry("kite_arm_l", 5),
            Map.entry("kite_arm_r", 2), Map.entry("chimera_back", 5), Map.entry("chimera_core", 2));

    /** Every other contact of every frame meets its seat tick: the weld lands as the part does, not after it. */
    @GameTest(template = "platform")
    public static void everyContactIsOnItsSeat(GameTestHelper h) {
        for (MechaFrame frame : MechaFrame.values()) {
            GantryPlan.Frame plan = GantryPlan.of(frame);
            h.assertTrue(plan != null, frame + " has no plan");
            for (GantryPlan.Contact c : plan.contacts()) {
                int late = LATE.getOrDefault(frame.id() + "_" + c.slot(), 0);
                h.assertTrue(c.contact() >= c.seat() && c.contact() <= c.seat() + late,
                        frame + " " + c.slot() + ": seat " + c.seat() + ", contact " + c.contact());
            }
        }
        h.succeed();
    }

    /**
     * The tool meets every socket it can reach, the tall frames' too, up to the Colossus's shoulders: a contact is an arc
     * only where it is listed, and the highest weld on each heavy is at its top.
     */
    @GameTest(template = "platform")
    public static void theToolMeetsTheTallFramesSockets(GameTestHelper h) {
        for (MechaFrame frame : MechaFrame.values()) {
            double top = 0;
            for (GantryPlan.Contact c : GantryPlan.of(frame).contacts()) {
                String key = frame.id() + "_" + c.slot();
                h.assertTrue(c.reach() || ARCS.contains(key), key + ": an arc, the tool does not meet the socket");
                h.assertTrue(!c.reach() || !ARCS.contains(key), key + ": listed as an arc but reached");
                Vec3 p = GantryPlan.locator(c.locator());
                h.assertTrue(p != null, key + ": no weld point");
                if (c.reach()) top = Math.max(top, p.y);
            }
            if (frame == MechaFrame.COLOSSUS) h.assertTrue(top >= 95, "the Colossus's highest weld is at " + top + " px");
            if (frame == MechaFrame.EXECUTIVE) h.assertTrue(top >= 90, "the Executive's highest weld is at " + top + " px");
        }
        h.succeed();
    }

    /**
     * The telescopes run out in the heavies' builds and are home at the end of every clip, so the held poses (stood off,
     * looking up) and the baked rest pose are the arm as it always was.
     */
    @GameTest(template = "platform")
    public static void theTelescopesComeHome(GameTestHelper h) {
        JsonObject anims;
        try (InputStream in = GantryReachGameTests.class.getResourceAsStream("/assets/headinajar/animations/block/gantry.animation.json")) {
            h.assertTrue(in != null, "the Gantry's clips are missing");
            anims = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (String heavy : new String[]{"build_colossus", "build_executive"}) {
            JsonObject bones = anims.getAsJsonObject(heavy).getAsJsonObject("bones");
            for (String bone : new String[]{"fore_t1", "fore_t2"}) {
                h.assertTrue(bones.has(bone), heavy + ": " + bone + " never moves");
                h.assertTrue(most(bones.getAsJsonObject(bone)) > 5, heavy + ": " + bone + " never runs out");
            }
        }
        for (var clip : anims.entrySet()) {
            JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            for (String bone : new String[]{"upper_t", "fore_t1", "fore_t2"}) {
                if (!bones.has(bone)) continue;
                h.assertTrue(Math.abs(last(bones.getAsJsonObject(bone))) < 1e-3, clip.getKey() + ": " + bone + " is not home at the end");
            }
        }
        h.succeed();
    }

    private static JsonObject keys(JsonObject bone) {
        JsonElement p = bone.get("position");
        return p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
    }

    private static double y(JsonElement key) {
        JsonElement v = key.isJsonObject() ? key.getAsJsonObject().get(key.getAsJsonObject().has("vector") ? "vector" : "post") : key;
        return v.getAsJsonArray().get(1).getAsDouble();
    }

    private static double most(JsonObject bone) {
        double m = 0;
        for (var k : keys(bone).entrySet()) m = Math.max(m, y(k.getValue()));
        return m;
    }

    private static double last(JsonObject bone) {
        double t = -1, v = 0;
        for (var k : keys(bone).entrySet()) {
            double at = Double.parseDouble(k.getKey());
            if (at >= t) {
                t = at;
                v = y(k.getValue());
            }
        }
        return v;
    }
}
