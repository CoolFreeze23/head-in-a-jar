package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The effect kit's sounds. Every file carries its own loudness: play at volume 1.0, pitch per play from {@link #pitch}.
 * Files and subtitles are in sounds.json (tools/gen_p14_effects.py); recipes in tools/snd/recipes_fx.py.
 */
public final class FxSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> TK_GRAB = sound("fx.tk.grab");
    /** The hold loop (client, {@code SoundLoops}): pitch and volume follow what is held. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TK_HOLD = sound("fx.tk.hold");
    public static final DeferredHolder<SoundEvent, SoundEvent> TK_THROW = sound("fx.tk.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> TK_SET_DOWN = sound("fx.tk.set_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> TK_FIZZLE = sound("fx.tk.fizzle");
    /** Head to body: the wipe sweeps up; its click lands 250 ms (5 ticks) in, the middle of the doer's 10-tick wipe. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_UP = sound("fx.swap.up");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP_DOWN = sound("fx.swap.down");
    /** Heard 24 blocks off whatever the volume. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER = ModSounds.SOUNDS.register("fx.laser.fire",
            () -> SoundEvent.createFixedRangeEvent(HeadInAJar.id("fx.laser.fire"), 24));
    /** The Tesla arc loop (client). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TESLA_ARC = sound("fx.tesla.arc");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    /** One-shots that can repeat get pitch 0.94-1.06 per play (the takes do the rest). */
    public static float pitch(RandomSource random) {
        return 0.94f + random.nextFloat() * 0.12f;
    }

    private FxSounds() {
    }
}
