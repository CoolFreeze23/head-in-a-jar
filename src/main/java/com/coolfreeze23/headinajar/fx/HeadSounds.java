package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Heads at work: the events a placed head's sounds play under, each with a subtitle of its own. All but the warden's
 * shush borrow a vanilla sound (sounds.json, from tools/gen_p38_heads.py); the shush is made by tools/make_sounds.py.
 */
public final class HeadSounds {
    /** The plinth's push bell, rung by hand. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DESK_BELL = sound("head.desk_bell");
    /** A head at its lunch, as a bucket fills its bowl. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SLURP = sound("head.slurp");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOD = sound("head.nod");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHICKEN_HOUR = sound("head.chicken_hour");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROG_FORECAST = sound("head.frog_forecast");
    /** The ghast's nightly wail: heard 24 blocks off by everyone (volume 1.5). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GHAST_WEEP = sound("head.ghast_weep");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_SHUSH = sound("head.warden_shush");
    /** A Lookout's double ding, on the same tick as its species' own sound. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LOOKOUT_ALARM = sound("head.lookout_alarm");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    private HeadSounds() {
    }
}
