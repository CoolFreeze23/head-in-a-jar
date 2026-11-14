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
import java.util.Set;

/**
 * The Assembly Gantry's arm reaches every frame: its upper arm and forearm telescope for the tall frames, so the tool
 * meets the Colossus's and the Executive's sockets up to their shoulders instead of striking an arc from the knees;
 * every contact is on its seat tick and no module shows before the arm has come to it; the telescopes are home at the
 * end of every clip and in every held pose.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GantryReachGameTests {
    /**
     * Contacts struck as an arc, frame id + "_" + slot: a socket with no reach at the angle the frame stands at (a core
     * inside the hull, a back socket behind it), or a reach with no clean way in from the contact before (or from
     * standing off) in time, where the arc lands on time instead.
     */
    static final Set<String> ARCS = Set.of("hopper_core", "bulwark_back", "bulwark_back_1", "bulwark_core", "bulwark_torso_2",
            "colossus_torso_3", "executive_back", "wheelhouse_back_1", "wheelhouse_core", "skitter_back", "skitter_core",
            "kite_arm_l", "chimera_back");

    /**
     * Every contact of every frame meets its seat tick: the weld lands as the part does, not after it. The frames' own
     * builds leave the arm the time: the Colossus lands its left shoulder last, after the turntable has turned that
     * shoulder to the arm; the Executive fits one side, the sensors on top, then the other side; the Skitter turns a
     * little at a time, its sensors before its second side plate and its arm last.
     */
    @GameTest(template = "platform")
    public static void everyContactIsOnItsSeat(GameTestHelper h) {
        for (MechaFrame frame : MechaFrame.values()) {
            GantryPlan.Frame plan = GantryPlan.of(frame);
            h.assertTrue(plan != null, frame + " has no plan");
            for (GantryPlan.Contact c : plan.contacts()) {
                h.assertTrue(c.contact() == c.seat(),
                        frame + " " + c.slot() + ": seat " + c.seat() + ", contact " + c.contact());
            }
        }
        h.succeed();
    }

    /**
     * No module shows before the arm has come to it: in every frame's own build clip (the file the game plays) each
     * socket stays hidden until the tick the plan's arm is at its approach, a few pixels off the part, and the part
     * seats as the tool presses it home.
     */
    @GameTest(template = "platform")
    public static void noModuleShowsBeforeTheArmComes(GameTestHelper h) {
        for (MechaFrame frame : MechaFrame.values()) {
            JsonObject bones;
            try (InputStream in = GantryReachGameTests.class.getResourceAsStream("/assets/headinajar/animations/entity/mecha/" + frame.id() + ".animation.json")) {
                h.assertTrue(in != null, frame + ": its clips are missing");
                bones = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations")
                        .getAsJsonObject("build").getAsJsonObject("bones");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            for (GantryPlan.Contact c : GantryPlan.of(frame).contacts()) {
                String[] sockets = c.slot().equals("legs") ? new String[]{"socket_thigh_l", "socket_thigh_r", "socket_shin_l", "socket_shin_r"}
                        : new String[]{"socket_" + c.slot()};
                h.assertTrue(c.arrive() < c.contact(), frame + " " + c.slot() + ": arm at " + c.arrive() + ", contact " + c.contact());
                for (String socket : sockets) {
                    if (!bones.has(socket)) continue;
                    double shown = shown(bones.getAsJsonObject(socket));
                    h.assertTrue(shown >= c.arrive() - 1e-6, frame + " " + socket + ": shows at " + shown + ", the arm comes at " + c.arrive());
                }
            }
        }
        h.succeed();
    }

    /** The tick a socket's scale first leaves 0 (its module shows). */
    private static double shown(JsonObject bone) {
        JsonElement scale = bone.get("scale");
        if (scale == null || !scale.isJsonObject()) return 0;
        double first = Double.MAX_VALUE;
        for (var k : scale.getAsJsonObject().entrySet()) {
            JsonElement v = k.getValue();
            if (v.isJsonObject()) v = v.getAsJsonObject().get(v.getAsJsonObject().has("vector") ? "vector" : "post");
            if (v.getAsJsonArray().get(0).getAsDouble() > 0) first = Math.min(first, Double.parseDouble(k.getKey()) * 20);
        }
        return first;
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

    /**
     * The telescopes are heard: the heavies' builds key gantry.extend and gantry.retract (each telescope stroke's
     * start), and every clip that runs a telescope out keys at least one of each.
     */
    @GameTest(template = "platform")
    public static void theTelescopesAreHeard(GameTestHelper h) {
        JsonObject anims;
        try (InputStream in = GantryReachGameTests.class.getResourceAsStream("/assets/headinajar/animations/block/gantry.animation.json")) {
            h.assertTrue(in != null, "the Gantry's clips are missing");
            anims = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (var clip : anims.entrySet()) {
            JsonObject c = clip.getValue().getAsJsonObject();
            JsonObject bones = c.getAsJsonObject("bones");
            double out = 0;
            for (String bone : new String[]{"upper_t", "fore_t1", "fore_t2"}) if (bones.has(bone)) out = Math.max(out, most(bones.getAsJsonObject(bone)));
            int extend = 0, retract = 0;
            if (c.has("sound_effects")) for (var k : c.getAsJsonObject("sound_effects").entrySet()) {
                String effect = k.getValue().getAsJsonObject().get("effect").getAsString();
                if (effect.startsWith("headinajar:gantry.extend|")) extend++;
                if (effect.startsWith("headinajar:gantry.retract|")) retract++;
            }
            if (out > 5) h.assertTrue(extend > 0 && retract > 0, clip.getKey() + ": the telescopes run out " + out + " px unheard");
            if (clip.getKey().equals("build_colossus") || clip.getKey().equals("build_executive")) {
                h.assertTrue(extend >= 3 && retract >= 3, clip.getKey() + ": " + extend + " extend and " + retract + " retract keys");
            }
        }
        h.assertTrue(com.coolfreeze23.headinajar.machine.MachineSounds.byId("gantry.extend") != null
                && com.coolfreeze23.headinajar.machine.MachineSounds.byId("gantry.retract") != null, "the telescope sounds are not registered");
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
