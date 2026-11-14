package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The walking mecha's sound events ("mecha.*", and a frame's own "mecha.<frame>.*" that replace them for it), played at volume 1.0 (the files carry their own loudness) with their
 * pitch range per play. Files, attenuation and subtitles are in sounds.json (tools/gen_p17_mecha.py, from the sound
 * hand-off tools/snd/events.json).
 */
public final class HopperSounds {
    private static final Map<String, float[]> PITCH = new HashMap<>();
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> EVENTS = new HashMap<>();
    /** Borrowed takes under events of their own (name, the event whose files it plays); kept in step with the generator. */
    static final String[][] ALIASES = {
            {"skitter.line_charge", "pogo_charge"}, {"skitter.reel", "pogo_ratchet"}, {"skitter.line_fire", "pogo_launch"},
            {"skitter.line_cough", "wheelhouse.engine_misfire"}, {"skitter.line_cut", "bar_unlatch"},
            {"spider_walker.key_wind", "pogo_ratchet"}, {"spider_walker.gland_squeeze", "pogo_charge"},
            {"spider_walker.nozzle_cough", "wheelhouse.engine_misfire"}, {"rover.reverse_beep", "wheelhouse.reverse_beep"},
            {"rover.bell", "ding"},
            // the shells' and the Skitter's clip sounds that borrowed a take whose subtitle named another machine's part
            {"spider_walker.tin_scrape", "wheelhouse.tyre_scrub"}, {"spider_walker.tin_clatter", "wheelhouse.wheel_clatter"},
            {"spider_walker.tin_creak", "wheelhouse.suspension_creak"}, {"spider_walker.key_rundown", "servo_fail"},
            {"spider_walker.nozzle_tap", "tap"}, {"rover.drawer_knock", "vent_clank"}, {"rover.drawer_slam", "bar_clank"},
            {"rover.drawer_slide", "servo"}, {"rover.motor_rev", "servo_chord"}, {"rover.jar_hop", "servo_tick"},
            {"rover.castor_squeak", "servo_tick"}, {"rover.strap_slap", "rattle"}, {"skitter.line_creak", "wheelhouse.suspension_creak"},
    };

    static {
        // name, lowest and highest pitch drawn per play ({1, 1}: the caller's pitch, the key's for clip sounds)
        Object[][] table = {
                {"motor", 1f, 1f}, {"step", 0.94f, 1.06f}, {"step_light", 0.94f, 1.06f}, {"step_heavy", 0.95f, 1.05f},
                {"step_drag", 0.95f, 1.05f}, {"tap", 0.94f, 1.06f}, {"land_light", 0.95f, 1.05f}, {"servo", 1f, 1f},
                {"servo_tick", 0.94f, 1.06f}, {"servo_chord", 1f, 1f}, {"servo_settle", 0.95f, 1.05f}, {"servo_sag", 1f, 1f},
                {"servo_catch", 1f, 1f}, {"servo_fail", 0.97f, 1.03f}, {"relay_click", 1f, 1f}, {"relay_tick", 0.94f, 1.06f},
                {"standby_blip", 1f, 1f}, {"hydraulic_stand", 0.98f, 1.02f}, {"pogo_charge", 0.98f, 1.02f}, {"pogo_ratchet", 1f, 1f},
                {"pogo_launch", 0.95f, 1.05f}, {"whoosh", 0.95f, 1.05f}, {"bar_unlatch", 0.95f, 1.05f}, {"bar_latch", 0.95f, 1.05f},
                {"clamp_close", 0.95f, 1.05f}, {"jar_seat", 0.95f, 1.05f}, {"bar_blow", 0.95f, 1.05f}, {"bar_clank", 0.95f, 1.05f},
                {"dry_fire", 0.95f, 1.05f}, {"hurt", 0.94f, 1.06f}, {"collapse", 0.97f, 1.03f}, {"vent_blast", 0.95f, 1.05f},
                {"vent_clank", 0.95f, 1.05f}, {"vent_whistle", 0.97f, 1.03f}, {"eject_pop", 0.95f, 1.05f},
                {"parachute_pop", 0.95f, 1.05f}, {"parachute_fold", 0.95f, 1.05f}, {"sigh", 0.95f, 1.05f},
                {"power_down", 0.97f, 1.03f}, {"rattle", 0.94f, 1.06f}, {"build_seat", 0.95f, 1.05f}, {"scan_chirp", 1f, 1f},
                {"ding", 1f, 1f},
                // the Golden Parachute's brocade, cords and envelope
                {"envelope_pop", 0.95f, 1.05f}, {"pilot_whoosh", 0.95f, 1.05f}, {"brocade_stream", 0.96f, 1.04f},
                {"brocade_breathe", 0.95f, 1.05f}, {"brocade_spill", 0.95f, 1.05f}, {"brocade_flare", 0.95f, 1.05f},
                {"brocade_flump", 0.95f, 1.05f}, {"brocade_rustle", 0.95f, 1.05f}, {"brocade_slide", 0.95f, 1.05f},
                {"brocade_slap", 0.95f, 1.05f}, {"gold_sink", 0.95f, 1.05f}, {"brocade_flap", 0.95f, 1.05f},
                {"tassel_jingle", 0.94f, 1.06f}, {"cords_creak", 0.95f, 1.05f}, {"cords_slack", 0.95f, 1.05f},
        };
        for (Object[] row : table) {
            String name = (String) row[0];
            EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
            PITCH.put(name, new float[]{(float) row[1], (float) row[2]});
        }
        // the Bulwark's own, heavier set (mecha.bulwark.*); what it lacks it borrows from the shared set above
        Object[][] bulwark = {
                {"motor", 0.97f, 1.03f}, {"step_heavy", 0.95f, 1.05f}, {"step", 0.94f, 1.06f}, {"step_drag", 0.95f, 1.05f},
                {"stomp", 0.97f, 1.03f}, {"land_light", 0.95f, 1.05f}, {"land_heavy", 0.96f, 1.04f}, {"servo", 1.0f, 1.0f},
                {"servo_settle", 0.95f, 1.05f}, {"servo_sag", 1.0f, 1.0f}, {"servo_catch", 1.0f, 1.0f}, {"servo_fail", 0.97f, 1.03f},
                {"hydraulic_stand", 0.98f, 1.02f}, {"hydraulic_lift", 0.98f, 1.02f}, {"hydraulic_strain", 0.95f, 1.05f},
                {"clamp_release", 0.95f, 1.05f}, {"clamp_close", 0.95f, 1.05f}, {"clamp_blow", 0.95f, 1.05f}, {"jar_seat", 0.95f, 1.05f},
                {"grip_clack", 0.95f, 1.05f}, {"grab_close", 0.95f, 1.05f}, {"grab_dry", 0.95f, 1.05f}, {"shrug_hiss", 0.95f, 1.05f},
                {"whoosh", 0.95f, 1.05f}, {"throw_release", 0.95f, 1.05f}, {"set_down", 0.95f, 1.05f}, {"neck_crack", 0.95f, 1.05f},
                {"vent_blast", 0.95f, 1.05f}, {"vent_clank", 0.95f, 1.05f}, {"collapse", 0.97f, 1.03f}, {"hurt", 0.94f, 1.06f},
                {"dry_fire", 0.95f, 1.05f}, {"rattle", 0.94f, 1.06f}, {"sigh", 0.95f, 1.05f}, {"power_down", 0.97f, 1.03f},
        };
        for (Object[] row : bulwark) {
            String name = "bulwark." + row[0];
            EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
            PITCH.put(name, new float[]{(float) row[1], (float) row[2]});
        }
        // the Colossus's and the Executive's own (mecha.colossus.*, mecha.executive.*); the rest they borrow from the Bulwark's
        Object[][] heavy = {
                {"colossus.planning_chime", 1f, 1f}, {"colossus.permit_denied", 1f, 1f}, {"colossus.hydraulic_sink", 0.98f, 1.02f},
                {"colossus.hydraulic_groan", 0.97f, 1.03f}, {"colossus.hydraulic_hiss", 0.95f, 1.05f}, {"colossus.latch_clunk", 0.95f, 1.05f},
                {"colossus.turret_whir", 0.96f, 1.04f}, {"colossus.siege_hum", 1f, 1f}, {"colossus.snip", 0.95f, 1.05f},
                {"colossus.step_quake", 0.95f, 1.05f}, {"colossus.siege_anchor", 0.96f, 1.04f},
                {"executive.premium_jingle", 1f, 1f}, {"executive.cloth_tug", 0.94f, 1.06f}, {"executive.shoe_tap", 0.94f, 1.06f},
                {"executive.watch_tick", 1f, 1f}, {"executive.sunroof_motor", 0.97f, 1.03f}, {"executive.sunroof_shut", 0.95f, 1.05f},
                {"executive.step_quiet", 0.94f, 1.06f},
        };
        for (Object[] row : heavy) {
            String name = (String) row[0];
            EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
            PITCH.put(name, new float[]{(float) row[1], (float) row[2]});
        }
        // the Wheelhouse's own set (mecha.wheelhouse.*): its engine, running gear and Rush Hour
        Object[][] wheelhouse = {
                {"motor", 0.97f, 1.03f}, {"engine_rev", 0.97f, 1.03f}, {"engine_catch", 0.97f, 1.03f},
                {"starter_whirr", 1.0f, 1.0f}, {"engine_stop", 0.97f, 1.03f}, {"engine_misfire", 0.95f, 1.05f},
                {"backfire_pop", 0.95f, 1.05f}, {"exhaust_pop", 0.95f, 1.05f}, {"rush_go", 0.98f, 1.02f},
                {"rush_ram", 0.95f, 1.05f}, {"rush_refused", 1.0f, 1.0f}, {"rush_wind_down", 0.97f, 1.03f},
                {"flag_snap", 0.95f, 1.05f}, {"flag_fold", 0.95f, 1.05f}, {"flag_flop", 0.95f, 1.05f},
                {"flap_clank", 0.95f, 1.05f}, {"buffer_spring", 0.95f, 1.05f}, {"brake_squeal", 0.95f, 1.05f},
                {"brake_hiss", 0.95f, 1.05f}, {"tyre_scrub", 0.94f, 1.06f}, {"tyre_skid", 0.95f, 1.05f},
                {"suspension_clunk", 0.94f, 1.06f}, {"suspension_creak", 0.95f, 1.05f},
                {"suspension_rise", 0.98f, 1.02f}, {"steer_servo", 0.95f, 1.05f}, {"ring_latch", 0.95f, 1.05f},
                {"catch_release", 0.95f, 1.05f}, {"ring_blow", 0.95f, 1.05f}, {"ring_clank", 0.95f, 1.05f},
                {"jar_seat", 0.95f, 1.05f}, {"ram_stroke", 0.95f, 1.05f}, {"wheel_pop", 0.95f, 1.05f},
                {"wheel_clatter", 0.95f, 1.05f}, {"reverse_beep", 1.0f, 1.0f}, {"cooling_tick", 0.92f, 1.08f},
        };
        for (Object[] row : wheelhouse) {
            String name = "wheelhouse." + row[0];
            EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
            PITCH.put(name, new float[]{(float) row[1], (float) row[2]});
        }
        // the Chimera's own (mecha.chimera.*) and the Lunk's (mecha.lunk.*, a shell's, played by its own client code)
        String[] chimera = {"yawn", "joint_crack", "shiver", "twitch", "tut", "sniff", "squelch_gulp", "retch_dry", "cough", "stand_creak", "hard_hat_clonk"};
        String[] lunk = {"drawer_slide", "drawer_slam", "drawers_burst", "bulldog_clip", "pat", "servo_down", "servo_wind_down", "strain", "clunk",
                "knuckle", "step_lunk"};
        for (String[] set : new String[][]{chimera, lunk}) {
            for (String n : set) {
                String name = (set == chimera ? "chimera." : "lunk.") + n;
                EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
                // the takes differ; a little pitch spread on the footfalls and the walk's knuckles only
                PITCH.put(name, n.equals("knuckle") || n.equals("step_lunk") ? new float[]{0.94f, 1.06f} : new float[]{1, 1});
            }
        }
        // its horn carries 48 blocks
        EVENTS.put("wheelhouse.horn", ModSounds.SOUNDS.register("mecha.wheelhouse.horn", () -> SoundEvent.createFixedRangeEvent(HeadInAJar.id("mecha.wheelhouse.horn"), 48)));
        PITCH.put("wheelhouse.horn", new float[]{1, 1});
        // a borrowed take whose own subtitle named the wrong thing (a pogo spring on the Skitter's line gun, an engine on
        // a web nozzle): the same files under an event of its own, with its own subtitle (tools/gen_p30_mecha_merge.py)
        for (String[] alias : ALIASES) {
            String name = alias[0];
            EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
            PITCH.put(name, PITCH.getOrDefault(alias[1], new float[]{1, 1}));
        }
        // the Kite's own (mecha.kite.*): wings, the lantern's lift floor, talons; the rest it borrows from the shared set
        Object[][] kite = {
                {"wingbeat", 0.94f, 1.06f}, {"wingbeat_light", 0.94f, 1.06f}, {"wingbeat_heavy", 0.95f, 1.05f}, {"wingbeat_weak", 0.95f, 1.05f},
                {"wing_unfurl", 0.97f, 1.03f}, {"wing_fold", 0.97f, 1.03f}, {"wing_brake", 0.96f, 1.04f}, {"wing_shrug", 0.96f, 1.04f},
                {"wing_scrape", 0.95f, 1.05f}, {"wing_slump", 0.96f, 1.04f}, {"feather_ruffle", 0.94f, 1.06f}, {"wind_rush", 0.95f, 1.05f},
                {"floor_release", 0.95f, 1.05f}, {"floor_latch", 0.95f, 1.05f}, {"floor_blow", 0.96f, 1.04f}, {"cable_winch", 0.97f, 1.03f},
                {"talon_snap", 0.95f, 1.05f}, {"talon_close", 0.95f, 1.05f}, {"talon_open", 0.95f, 1.05f},
        };
        for (Object[] row : kite) {
            String name = "kite." + row[0];
            EVENTS.put(name, ModSounds.SOUNDS.register("mecha." + name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("mecha." + name))));
            PITCH.put(name, new float[]{(float) row[1], (float) row[2]});
        }
        // the eject klaxon is heard 48 blocks off whatever the volume
        EVENTS.put("klaxon", ModSounds.SOUNDS.register("mecha.klaxon", () -> SoundEvent.createFixedRangeEvent(HeadInAJar.id("mecha.klaxon"), 48)));
        PITCH.put("klaxon", new float[]{1, 1});
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    /** The registered event by name ("servo", "mecha.servo" or "headinajar:mecha.servo"), or null. */
    public static SoundEvent get(String id) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (name.startsWith("mecha.")) name = name.substring(6);
        DeferredHolder<SoundEvent, SoundEvent> holder = EVENTS.get(name);
        return holder == null || !holder.isBound() ? null : holder.get();
    }

    /** This frame's own event for a name ({@code mecha.<frame>.<name>}) when it has one, else the shared one, or null. */
    public static SoundEvent get(MechaFrame frame, String id) {
        return get(resolve(frame, id));
    }

    /** The name the frame actually plays for {@code id} (its own event's name when it has one): for pitch ranges. */
    public static String resolve(MechaFrame frame, String id) {
        String name = bare(id);
        MechaRig rig = MechaRig.of(frame);
        if (rig != null && !name.contains(".")) for (String set : rig.soundSets) if (EVENTS.containsKey(set + "." + name)) return set + "." + name;
        if (rig != null && !EVENTS.containsKey(name) && rig.borrowed.containsKey(name)) return rig.borrowed.get(name);
        return name;
    }

    /** Whether this frame has its own event for a name. */
    public static boolean own(MechaFrame frame, String id) {
        return !resolve(frame, id).equals(bare(id));
    }

    private static String bare(String id) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        return name.startsWith("mecha.") ? name.substring(6) : name;
    }

    /** The pitch to play at: the caller's pitch times a draw from the event's range. */
    public static float pitch(String id, float base, RandomSource random) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (name.startsWith("mecha.")) name = name.substring(6);
        float[] range = PITCH.get(name);
        if (range == null || range[0] == range[1]) return base;
        return base * (range[0] + random.nextFloat() * (range[1] - range[0]));
    }

    private HopperSounds() {
    }
}
