package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The arm tools' sounds, a hot frame cooling and a damaged one sparking. Every file carries its own loudness: played
 * at volume 1.0, pitch per play from {@link #pitch}. Files and subtitles are in sounds.json (tools/gen_p34_arms.py);
 * recipes in tools/snd/recipes_arms.py. Loops are played by the client for as long as the tool works.
 */
public final class ArmSounds {
    /** At the press: the Arc Cannon's capacitor (its shot follows at the recoil's contact). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_CHARGE = fixed("arms.arc_charge", 24);
    /** The Arc Cannon's shot, heard 32 blocks off (it replaces a thunder clap that carried across the map). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_CRACK = fixed("arms.arc_crack", 32);
    /** Each jump of the arc to the next creature (client, from the memo). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_HOP = sound("arms.arc_hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_IGNITE = fixed("arms.lance_ignite", 24);
    /** Loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_HUM = sound("arms.lance_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_OUT = sound("arms.lance_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAME_IGNITE = sound("arms.flame_ignite");
    /** Loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAME_ROAR = sound("arms.flame_roar");
    /** Loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACTOR_HUM = sound("arms.tractor_hum");
    /** At the press: the fist's wind-up (the blow follows at the strike's contact). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FIST_HISS = sound("arms.fist_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIST_STRIKE = fixed("arms.fist_strike", 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIVET_PAK = fixed("arms.rivet_pak", 24);
    /** Loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WELD_HISS = sound("arms.weld_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAND_GRIP = sound("arms.hand_grip");
    /** A frame above 60% heat, every one to four seconds. */
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMAL_TINK = sound("arms.thermal_tink");
    /** A frame below half health, with its sparks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_FIZZ = sound("arms.spark_fizz");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> fixed(String name, float range) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(HeadInAJar.id(name), range));
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    /** One-shots that can repeat get pitch 0.94-1.06 per play (the takes do the rest). */
    public static float pitch(RandomSource random) {
        return 0.94f + random.nextFloat() * 0.12f;
    }

    private ArmSounds() {
    }
}
