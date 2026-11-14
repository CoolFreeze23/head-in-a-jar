package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The machine kit's sound events (the shared {@code machine.*} set) and each kit machine's own. Files and subtitles
 * are in sounds.json, written by the generator from the sound hand-off (tools/snd/events.json); the volume and pitch
 * range each is played at are the hand-off's, here. The files carry their own loudness, so a keyframe's volume is not
 * used: an event plays at its own volume, at the keyframe's pitch times a draw from its range.
 */
public final class MachineSounds {
    private record Play(DeferredHolder<SoundEvent, SoundEvent> event, float volume, float low, float high) {
    }

    private static final Map<String, Play> PLAY = new HashMap<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> DRAWER_OUT = sound("machine.drawer_out", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAWER_STOP = sound("machine.drawer_stop", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAWER_SHUT = sound("machine.drawer_shut", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HATCH_OPEN = sound("machine.hatch_open", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HATCH_SHUT = sound("machine.hatch_shut", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAG_UP = sound("machine.flag_up", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAG_DOWN = sound("machine.flag_down", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RATTLE = sound("machine.rattle", 0.4f, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PILE_CRACKLE = sound("pile.crackle", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PILE_RATCHET = sound("pile.ratchet", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PILE_SIGH = sound("pile.sigh", 1, 0.97f, 1.03f);
    /** Played by the server at the click; its hit is 4 ticks into the file, on the lump's landing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PILE_PLOP = sound("pile.plop", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PILE_BZZT = sound("pile.bzzt", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_TINK = sound("mixer.tink", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_CAPS_UP = sound("mixer.caps_up", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_CAP_DOWN = sound("mixer.cap_down", 0.6f, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_GLUG = sound("mixer.glug", 1, 0.95f, 1.05f);
    /** Loops, played by the client while the part moves: volume and pitch follow its speed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_PADDLE = sound("mixer.paddle", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_WHINE = sound("centrifuge.whine", 1, 1, 1);

    // the Life Support Cradle, the Body Printer, the Gene Splicer (the whirr is a loop, its pitch set by the code)
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_HOSE_SWING = sound("cradle.hose_swing", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_HOSE_CLICK = sound("cradle.hose_click", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_BLANKET_WHOOSH = sound("cradle.blanket_whoosh", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_FLUMP = sound("cradle.flump", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_SEAL_PFFF = sound("cradle.seal_pfff", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_HOSE_HANG = sound("cradle.hose_hang", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_INHALE = sound("cradle.inhale", 1, 0.96f, 1.04f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRADLE_EXHALE = sound("cradle.exhale", 1, 0.96f, 1.04f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_START = sound("printer.start", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_TAP = sound("printer.tap", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_ZIP = sound("printer.zip", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_RATCHET = sound("printer.ratchet", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_DEVELOP = sound("printer.develop", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_THUNK = sound("printer.thunk", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_UNLATCH = sound("printer.unlatch", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_DOOR_STOP = sound("printer.door_stop", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_STEP = sound("printer.step", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_DOOR_SWING = sound("printer.door_swing", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_DOOR_SHUT = sound("printer.door_shut", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_READY = sound("printer.ready", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_WIND_DOWN = sound("printer.wind_down", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_WHIRR = sound("printer.whirr", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_STITCH = sound("splicer.stitch", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_STUTTER = sound("splicer.stutter", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_CLUTCH = sound("splicer.clutch", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_COAST = sound("splicer.coast", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_HIC = sound("splicer.hic", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_EJECT = sound("splicer.eject", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_THREAD = sound("splicer.thread", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_SEAT = sound("splicer.seat", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPLICER_BELL = sound("splicer.bell", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRAY_CARD = sound("array.card", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRAY_CARDS_DOWN = sound("array.cards_down", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRAY_GAVEL = sound("array.gavel", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRAY_MURMUR = sound("array.murmur", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRAY_STEAM = sound("array.steam", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_RECEIVER_LIFT = sound("mount.receiver_lift", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_HR_RINGBACK = sound("mount.hr_ringback", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_HR_MUMBLE = sound("mount.hr_mumble", 1, 0.96f, 1.04f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_RECEIVER_CLACK = sound("mount.receiver_clack", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_STAMP_LIFT = sound("mount.stamp_lift", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_STAMP_THUMP = sound("mount.stamp_thump", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_CLINK = sound("mount.clink", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_CHEER = sound("mount.cheer", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_BELL_TING = sound("mount.bell_ting", 0.5f, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_SIGH = sound("mount.sigh", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_KEY = sound("sequencer.key", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_CRANK = sound("sequencer.crank", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_TAB = sound("sequencer.tab", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_TAB_DOWN = sound("sequencer.tab_down", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_TEAR = sound("sequencer.tear", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_BELL = sound("sequencer.bell", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_UNLATCH = sound("rig.unlatch", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_SERVO = sound("rig.servo", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_PRIME = sound("rig.prime", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_PLUNGE = sound("rig.plunge", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_DING = sound("rig.ding", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_BURP = sound("rig.burp", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_COUNTER = sound("rig.counter", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_SPROING = sound("rig.sproing", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_HOLSTER = sound("rig.holster", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_HISS = sound("rig.hiss", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_CANCEL = sound("rig.cancel", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_FILL = sound("rig.fill", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIG_DRAIN = sound("rig.drain", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_PERK = sound("tesla.perk", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_LOOK = sound("tesla.look", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_SETTLE = sound("tesla.settle", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_SWING = sound("tesla.swing", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_LINK_ZAP = sound("tesla.link_zap", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_UNLINK = sound("tesla.unlink", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_WIND_DOWN = sound("tesla.wind_down", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_HUM = sound("tesla.hum", 1, 0.85f, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_CLACK = sound("dock.clack", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_FALL = sound("dock.fall", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_DING = sound("dock.ding", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_FIZZLE = sound("dock.fizzle", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_METER_TICK = sound("dock.meter_tick", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_SPARK = sound("dock.spark", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_LEVER = sound("dock.lever", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_UNCLACK = sound("dock.unclack", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_TAP = sound("dock.tap", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAST_SWING = sound("mast.swing", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAST_DROOP = sound("mast.droop", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAST_FLINCH = sound("mast.flinch", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAST_STAGE_UP = sound("mast.stage_up", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> MAST_STAGE_DOWN = sound("mast.stage_down", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER_WHIRR = sound("jammer.whirr", 1, 0.5f, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER_STATIC = sound("jammer.static", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_WINDUP = sound("turret.windup", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_LUNGE = sound("turret.lunge", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_HISS = sound("turret.hiss", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_SERVO = sound("turret.servo", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_LOCK = sound("turret.lock", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_BELL = sound("locker.bell", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_RATCHET = sound("locker.ratchet", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_THUNK = sound("locker.thunk", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_UNLATCH = sound("locker.unlatch", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_CHECKIN = sound("locker.checkin", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_PORTRAIT_ON = sound("locker.portrait_on", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_PORTRAIT_OFF = sound("locker.portrait_off", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_TICKET_CLINK = sound("locker.ticket_clink", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCKER_TICKET_RATTLE = sound("locker.ticket_rattle", 0.4f, 0.95f, 1.05f);

    // the Leech Jar, the Neural Socket, the Leyden Jar
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_STIR = sound("leech.stir", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_FLOP = sound("leech.flop", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_NOZZLE_POP = sound("leech.nozzle_pop", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_CRANK_RATCHET = sound("leech.crank_ratchet", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_PLINK = sound("leech.plink", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_VIALS_CLATTER = sound("leech.vials_clatter", 1, 0.95f, 1.05f);
    /** The sample clip's instruction {@code leech:bite;}. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LEECH_BITE = sound("leech.bite", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOCKET_SERVO = sound("socket.servo", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOCKET_CLAMP = sound("socket.clamp", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOCKET_PLUG = sound("socket.plug", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOCKET_SIGH = sound("socket.sigh", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOCKET_DROP = sound("socket.drop", 1, 0.95f, 1.05f);
    /** Played by the server once, on the tick the jar comes to full charge. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LEYDEN_FULL = sound("leyden.full", 1, 0.94f, 1.06f);
    // the Assembly Gantry (its arm, its turntable, its welder; all from its clips)
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_KLAXON = sound("gantry.klaxon", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_SERVO = sound("gantry.servo", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_SERVO_STOP = sound("gantry.servo_stop", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_TOOL_OUT = sound("gantry.tool_out", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_WELD = sound("gantry.weld", 1, 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_ARC = sound("gantry.arc", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_TURNTABLE = sound("gantry.turntable", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_SCAN = sound("gantry.scan", 1, 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_STAMP = sound("gantry.stamp", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_PARK = sound("gantry.park", 1, 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_CHARGE_TICK = sound("gantry.charge_tick", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_TAP = sound("gantry.tap", 1, 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_RATCHET = sound("gantry.ratchet", 1, 0.97f, 1.03f);
    // the clips' names for two of them
    static {
        PLAY.put("dock.thunk", PLAY.get("dock.fall"));
        PLAY.put("dock.tick", PLAY.get("dock.meter_tick"));
        // the Locker's check-in ends on the company's own "done"
        PLAY.put("machine.done", new Play(ModSounds.FEEDBACK_DONE, 1, 1, 1));
    }


    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name, float volume, float low, float high) {
        DeferredHolder<SoundEvent, SoundEvent> holder = ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
        PLAY.put(name, new Play(holder, volume, low, high));
        return holder;
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    private static Play play(String id) {
        return PLAY.get(id.startsWith("headinajar:") ? id.substring(11) : id);
    }

    /** The registered event for an id ("headinajar:pile.crackle" or "pile.crackle"), or null for a name with none. */
    public static SoundEvent byId(String id) {
        Play p = play(id);
        return p == null || !p.event().isBound() ? null : p.event().get();
    }

    public static float volume(String id) {
        Play p = play(id);
        return p == null ? 1 : p.volume();
    }

    public static float pitch(String id, float base, RandomSource random) {
        Play p = play(id);
        if (p == null || p.low() == p.high()) return base;
        return base * (p.low() + random.nextFloat() * (p.high() - p.low()));
    }

    private MachineSounds() {
    }
}
