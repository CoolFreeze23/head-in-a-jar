package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The workforce's sounds: the punch, the clock's bell, a card printed, the whistle, the lanyard on and off. */
public final class WorkSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> PUNCH_IN = sound("time_clock.punch_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOCK_BELL = sound("time_clock.bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_PRINTED = sound("time_clock.card_printed");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE = sound("whistle.blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE_TWICE = sound("whistle.two_short");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANYARD_ON = sound("lanyard.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANYARD_OFF = sound("lanyard.off");

    private WorkSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    /** Loads the class (its sounds join the register before it is handed over). */
    public static void init() {
    }
}
