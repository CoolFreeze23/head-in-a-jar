package com.coolfreeze23.headinajar.registry;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Head Office's own sounds (made by tools/make_sounds.py). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HeadInAJar.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BOOTH_CUT = sound("booth_cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOTH_OOPSIE = sound("booth_oopsie");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINK_LOST = sound("link_lost");
    public static final DeferredHolder<SoundEvent, SoundEvent> LINK_RESTORED = sound("link_restored");
    public static final DeferredHolder<SoundEvent, SoundEvent> MECHA_BOOT = sound("mecha_boot");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_SPLICE = sound("gene_splice");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_MUTATION = sound("gene_mutation");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRAY_MURMUR = sound("array_murmur");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAFF_COMPLAIN = sound("staff_complain");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }

    private ModSounds() {
    }
}
