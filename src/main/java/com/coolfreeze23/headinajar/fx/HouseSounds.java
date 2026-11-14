package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The house sounds and the sound families that follow a state: the company's jingles and its desk bell, a jar getting
 * about (wheels, rolling, hops, the propeller), the link's static by quality, the Neural Socket's switchboard, and the
 * events of their own that a borrowed sound now plays under (the same vanilla sound, a subtitle that names what is
 * heard). Recipes in tools/snd/recipes_house.py, recipes_travel.py and recipes_switchboard.py; sounds.json entries and
 * subtitles from tools/gen_p35_sound.py. Each recipe file carries its own loudness: play at volume 1.0 unless a call
 * says otherwise.
 */
public final class HouseSounds {
    // the house family: the company talking to the player
    /** C5 E5 G5 C6, the last a little flat: something big done for you. Heard 48 blocks off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> JINGLE = fixed("house.jingle", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> JINGLE_SHUTDOWN = sound("house.jingle_shutdown");
    /** The botched operation's jingle (booth_oopsie plays the same files). */
    public static final DeferredHolder<SoundEvent, SoundEvent> JINGLE_BOTCH = sound("house.jingle_botch");
    /** "All done here, next please": after the saw's cut, an embalming, a suture. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DESK_DING = sound("house.desk_ding");
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_PATCH = sound("gantry.patch");
    public static final DeferredHolder<SoundEvent, SoundEvent> GANTRY_STRIP = sound("gantry.strip");
    /** A memo from DOLORES arriving (her beep). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MEMO_BEEP = sound("memo.beep");

    // a jar getting about
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEELS_DOWN = sound("jar.wheels_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEELS_UP = sound("jar.wheels_up");
    /** Loop: pitch 0.75-1.5 and volume by the jar's speed on its wheels. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE = sound("jar.drive");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIP_OVER = sound("jar.tip_over");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIT_UP = sound("jar.sit_up");
    /** Loop: pitch 0.8-1.2 and volume by the tipped jar's rolling speed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL = sound("jar.roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOP = sound("jar.hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOP_LAND = sound("jar.hop_land");
    /** Loop: pitch 0.75-1.3 and volume by thrust while the propeller flies the jar. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PROPELLER = sound("jar.propeller");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCK_LID = sound("jar.shock_lid");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAR_KNOCK = sound("jar.knock");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAR_SHATTER = sound("jar.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAR_PATCH = sound("jar.patch");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAR_BAGGED = sound("jar.bagged");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAR_GOGGLES = sound("jar.goggles");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAR_PLUG_IN = sound("jar.plug_in");

    // the link heard: one loop per band of quality, in the driver's own head (LinkStatic)
    public static final DeferredHolder<SoundEvent, SoundEvent> STATIC_FAINT = sound("link.static_faint");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATIC = sound("link.static");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATIC_HEAVY = sound("link.static_heavy");

    // the Neural Socket as a switchboard
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_UP = sound("socket.relay_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_DOWN = sound("socket.relay_down");
    /** One relay click a face, at {@link #CHANNEL_PITCH} (a major scale, low to high by face). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHANNEL_ON = sound("socket.channel_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHANNEL_OFF = sound("socket.channel_off");
    /** A zombie head's 1 Hz clock: a relay tick each second, its groan every ten. */
    public static final DeferredHolder<SoundEvent, SoundEvent> METRONOME = sound("socket.metronome");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZOMBIE_GROAN = sound("socket.zombie_groan");
    /** Pitches of the six channels, by face index (down, up, north, south, west, east). */
    public static final float[] CHANNEL_PITCH = {1.0f, 1.1225f, 1.2599f, 1.3348f, 1.4983f, 1.6818f};

    // the head and the body, under subtitles of their own
    public static final DeferredHolder<SoundEvent, SoundEvent> FLIES = sound("body.flies");
    public static final DeferredHolder<SoundEvent, SoundEvent> SYRINGE_JAB = sound("body.syringe_jab");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNIP = sound("body.snip");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBALM = sound("body.embalm");
    public static final DeferredHolder<SoundEvent, SoundEvent> REATTACH = sound("body.reattach");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOTHES = sound("body.clothes");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLUMP = sound("body.slump");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUTURE = sound("body.suture");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETACH = sound("body.detach");
    public static final DeferredHolder<SoundEvent, SoundEvent> LACE = sound("body.lace");

    // machines, genes and gadgets, under subtitles of their own
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_ACCEPTED = sound("gene.accepted");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_REJECTED = sound("gene.rejected");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_REGROWTH = sound("gene.regrowth");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_FROST = sound("gene.frost");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_SETTLE = sound("centrifuge.settle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_READ = sound("sequencer.read");
    public static final DeferredHolder<SoundEvent, SoundEvent> COURIER_BOOKED = sound("courier.booked");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_DRY_CLICK = sound("turret.dry_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_THUMP = sound("turret.thump");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_FIT = sound("turret.fit");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOCK_CHARGING = sound("dock.charging");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_BURST = sound("emp.burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_THROW = sound("emp.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEW_BLADE = sound("chair.new_blade");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_AWAY = sound("chair.pipe_away");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAR_INSTALL = sound("collar.install");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENTANGLE = sound("quantum.entangle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRINTER_SQUELCH = sound("printer.squelch");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_WORK = sound("mount.work");

    // the mecha's arm and back modules: two retired stand-ins remade, the rest under subtitles of their own
    /** The Arc Cannon's crack (it was a lightning bolt, heard far off): heard 32 blocks off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_CANNON = fixed("module.arc_cannon", 32);
    /** The Tesla Coil's zap (it was a bee's angry loop played once). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_COIL = sound("module.tesla_coil");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIST_STRIKE = sound("mecha.fist_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAPPLE_FIRE = sound("module.grapple_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIVET_FIRE = sound("module.rivet_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINSAW = sound("module.chainsaw");
    public static final DeferredHolder<SoundEvent, SoundEvent> JETS = sound("mecha.jets");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_STEAM = sound("mecha.vent_steam");
    public static final DeferredHolder<SoundEvent, SoundEvent> MEDICAL = sound("module.medical");
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR = sound("module.mortar");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILES = sound("module.missiles");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORE_SCAN = sound("module.ore_scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARGO_OPEN = sound("mecha.cargo_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELL_CRASH = sound("mecha.shell_crash");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_SHOT = sound("mecha.line_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_ANCHOR = sound("mecha.line_anchor");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_SNIP = sound("mecha.line_snip");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> fixed(String name, float range) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(HeadInAJar.id(name), range));
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    /** A one-shot's pitch drawn from 0.95-1.05 (the takes do the rest). */
    public static float pitch(RandomSource random) {
        return 0.95f + random.nextFloat() * 0.1f;
    }

    private HouseSounds() {
    }
}
