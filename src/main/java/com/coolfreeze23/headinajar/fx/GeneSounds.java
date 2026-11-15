package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The 0.6.2 genes' sounds: each borrows a vanilla sound (sounds.json, from tools/gen_p39_dna.py) under an event of its
 * own, so a person who moos is subtitled "Colleague moos", not "Cow moos". The quirks ({@code gene.quirk.*}) are what a
 * gene costs its carrier in dignity; the client option {@code geneQuirks} mutes other people's.
 */
public final class GeneSounds {
    /** Ruminant Gut's cost: you moo when hurt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOO = sound("gene.quirk.moo");
    /** Swine Appetite's cost: you oink at every bite. */
    public static final DeferredHolder<SoundEvent, SoundEvent> OINK = sound("gene.quirk.oink");
    /** Tongue's cost: you croak when you jump. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CROAK = sound("gene.quirk.croak");
    /** Keen Nose's cost: you sneeze near flowers. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SNEEZE = sound("gene.quirk.sneeze");
    /** Weeping Glands' cost: you sob, audibly, every few minutes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SOB = sound("gene.quirk.sob");
    public static final DeferredHolder<SoundEvent, SoundEvent> TONGUE = sound("gene.tongue");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIT = sound("gene.spit");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE = sound("gene.phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> POUNCE = sound("gene.pounce");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFF = sound("gene.puff");

    /** The quirk events, which the client option {@code geneQuirks} mutes when they come from someone else. */
    public static boolean quirk(net.minecraft.resources.ResourceLocation id) {
        return id.getNamespace().equals(HeadInAJar.MOD_ID) && id.getPath().startsWith("gene.quirk.");
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return ModSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id(name)));
    }

    /** Loads the class, so the events are registered with the mod's sound register. */
    public static void load() {
    }

    private GeneSounds() {
    }
}
