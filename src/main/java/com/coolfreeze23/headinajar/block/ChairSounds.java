package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The Separation Chair's sound events, and how each is played: every one at volume 1.0 (the files carry their own
 * loudness), with its pitch range per play. Files and subtitles are in sounds.json (tools/gen_p13_chair.py).
 */
public final class ChairSounds {
    /** id (without "chair.") -> pitch range per play; {1, 1} plays at the pitch the caller gives. */
    private static final Map<String, float[]> PITCH = new HashMap<>();
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> EVENTS = new HashMap<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> SERVO_BARRIER = sound("servo_barrier", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRIER_DROP = sound("barrier_drop", 0.98f, 1.02f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRIER_LATCH = sound("barrier_latch", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRIER_SETTLE = sound("barrier_settle", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HOOD_NOD = sound("hood_nod", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_HISS = sound("gas_hiss", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BEEP = sound("beep", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SERVO_TICK = sound("servo_tick", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN_CHIRP = sound("scan_chirp", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> LATCH = sound("latch", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> WHINE_RISE = sound("whine_rise", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> CURTAIN = sound("curtain", 0.95f, 1.05f);
    /** The cut is heard 24 blocks off whatever the volume. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CUT = ModSounds.SOUNDS.register("chair.cut",
            () -> SoundEvent.createFixedRangeEvent(HeadInAJar.id("chair.cut"), 24));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLONK = sound("plonk", 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DING = sound("ding", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> LID_POP = sound("lid_pop", 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SALINE_GLUG = sound("saline_glug", 0.8f, 0.9f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAMP = sound("clamp", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRAULIC = sound("hydraulic", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRAULIC_SHORT = sound("hydraulic_short", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> SERVO = sound("servo", 0.98f, 1.02f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SERVO_HOME = sound("servo_home", 0.98f, 1.02f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLOP = sound("plop", 0.6f, 0.7f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HANDSHAKE = sound("handshake", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CLINK = sound("clink", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK = sound("spark", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> RATCHET = sound("ratchet", 1, 1);
    public static final DeferredHolder<SoundEvent, SoundEvent> UNLATCH = sound("unlatch", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRIER_LIFT = sound("barrier_lift", 0.98f, 1.02f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TAP = sound("tap", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAWER_OUT = sound("drawer_out", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAWER_IN = sound("drawer_in", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAWER_STOP = sound("drawer_stop", 0.94f, 1.06f);
    public static final DeferredHolder<SoundEvent, SoundEvent> SERVO_HICCUP = sound("servo_hiccup", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> OOPSIE_SQUEAK = sound("oopsie_squeak", 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLATE = sound("deflate", 0.97f, 1.03f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HISS = sound("hiss", 0.95f, 1.05f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOW_CHIME = sound("bow_chime", 1, 1);

    static {
        PITCH.put("cut", new float[]{0.95f, 1.05f});
        EVENTS.put("cut", CUT);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name, float low, float high) {
        DeferredHolder<SoundEvent, SoundEvent> holder = ModSounds.SOUNDS.register("chair." + name,
                () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("chair." + name)));
        PITCH.put(name, new float[]{low, high});
        EVENTS.put(name, holder);
        return holder;
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    /** The registered event for a chair sound id ("headinajar:chair.servo" or "chair.servo"), or null. */
    public static SoundEvent byId(String id) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (!name.startsWith("chair.")) return null;
        DeferredHolder<SoundEvent, SoundEvent> holder = EVENTS.get(name.substring(6));
        return holder == null || !holder.isBound() ? null : holder.get();
    }

    /** The pitch to play an event at: the caller's pitch times a draw from the event's range. */
    public static float pitch(String id, float base, RandomSource random) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        float[] range = PITCH.get(name.startsWith("chair.") ? name.substring(6) : name);
        if (range == null || range[0] == range[1]) return base;
        return base * (range[0] + random.nextFloat() * (range[1] - range[0]));
    }

    private ChairSounds() {
    }
}
