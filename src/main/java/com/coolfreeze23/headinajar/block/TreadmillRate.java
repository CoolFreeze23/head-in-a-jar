package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import java.util.Optional;
import java.util.Set;

/**
 * What a species makes on a Corporate Wellness Treadmill, or why it won't walk one: the data map
 * {@code headinajar:treadmill} on entity types ({@code data/<ns>/data_maps/entity_type/treadmill.json}, values
 * {@code {"fe": 10}} or {@code {"refuse": "flier"}}). Server only, not synced: the belt decides, the client only sees
 * the belt run. A modpack adds rows with a datapack; a species with no row is judged by its body (see
 * {@link TreadmillBlockEntity#verdict}). Also registers the belt's whir.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record TreadmillRate(Optional<Integer> fe, Optional<String> refuse) {
    /** The refusals a row may name (each has its line, {@code refusal.headinajar.treadmill.<kind>}). */
    public static final Set<String> KINDS = Set.of("flier", "ghast", "swimmer", "strider", "heavy", "bounce",
            // 0.8.0: a creature that walks only while nobody watches (the belt's console is always watching)
            "watched");

    public static final Codec<TreadmillRate> CODEC = RecordCodecBuilder.<TreadmillRate>create(i -> i.group(
            Codec.intRange(0, 100_000).optionalFieldOf("fe").forGetter(TreadmillRate::fe),
            Codec.STRING.validate(k -> KINDS.contains(k) ? DataResult.success(k) : DataResult.error(() -> "unknown Treadmill refusal " + k + ", one of " + KINDS))
                    .optionalFieldOf("refuse").forGetter(TreadmillRate::refuse)
    ).apply(i, TreadmillRate::new)).validate(r -> r.fe.isPresent() != r.refuse.isPresent() ? DataResult.success(r)
            : DataResult.error(() -> "a Treadmill row needs exactly one of fe or refuse"));

    public static final DataMapType<EntityType<?>, TreadmillRate> TYPE =
            DataMapType.builder(HeadInAJar.id("treadmill"), Registries.ENTITY_TYPE, CODEC).build();

    /** The belt's whir, played every 40 ticks while it runs (the file is made by tools/make_sounds.py). */
    public static final ResourceLocation RUN_ID = HeadInAJar.id("treadmill.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUN = DeferredHolder.create(Registries.SOUND_EVENT, RUN_ID);

    /** A row for the species, or null. */
    public static TreadmillRate of(EntityType<?> type) {
        return type.builtInRegistryHolder().getData(TYPE);
    }

    @SubscribeEvent
    public static void dataMaps(RegisterDataMapTypesEvent event) {
        event.register(TYPE);
    }

    @SubscribeEvent
    public static void sounds(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> helper.register(RUN_ID, SoundEvent.createVariableRangeEvent(RUN_ID)));
    }
}
