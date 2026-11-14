package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Sounds of the head and the body: the saw's cork, the bayonet mount on a host's neck, the handshake's notes, a body
 * powering down and up, small tool sounds, and the jar's glass. Recipes in tools/snd/recipes_body.py; sounds.json
 * entries and subtitles from tools/gen_p26_body.py. Each file carries its own loudness: play at volume 1.0.
 */
public final class BodySounds {
    /** The whine-up and the pop; the pop lands 150 ms (3 ticks) after the start. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CORK = sound("body.cork");
    public static final DeferredHolder<SoundEvent, SoundEvent> BAYONET = sound("body.bayonet");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNSEAT = sound("body.unseat");
    /** One note of the jingle on C5: played at pitch 1.0, 1.26, 1.50 and 2.0 for C5, E5, G5 and C6. */
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE = sound("body.note");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISS = sound("body.miss");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_DOWN = sound("body.power_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_UP = sound("body.power_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNICK = sound("body.snick");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLIP = sound("body.plip");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRACK = sound("jar.crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRACK_LONG = sound("jar.crack_long");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP = sound("jar.drip");

    /** The jingle's notes as pitches of {@link #NOTE}: C5, E5, G5, C6. */
    public static final float[] NOTES = {1.0f, 1.26f, 1.498f, 2.0f};

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    private BodySounds() {
    }
}
