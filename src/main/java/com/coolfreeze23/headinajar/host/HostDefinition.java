package com.coolfreeze23.headinajar.host;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A host creature's data file, {@code data/<ns>/headinajar/host/<name>.json}: where its neck is, its blood type, how
 * fast it decays, and what a jar gets from piloting it at each sync level (attributes at 0%, traits at 25%, a primary
 * ability at 50%, a secondary at 75%, +10% stats at 100%). A creature with no file is still a host with no extras.
 */
public record HostDefinition(ResourceLocation entity, List<Double> neck, String blood, float decayPerMinute, Map<String, Double> attributes,
                             List<String> flags, List<String> drawbacks, Optional<Ability> primary, Optional<Ability> secondary,
                             List<String> neutralTo) {

    /** An ability: a type the mod implements, with a cooldown in ticks and free parameters. */
    public record Ability(String type, int cooldown, Map<String, Double> params) {
        public static final Codec<Ability> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Ability::type),
                Codec.INT.optionalFieldOf("cooldown", 60).forGetter(Ability::cooldown),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(Ability::params)
        ).apply(i, Ability::new));

        public double param(String key, double fallback) {
            return params.getOrDefault(key, fallback);
        }
    }

    public static final Codec<HostDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(HostDefinition::entity),
            Codec.DOUBLE.listOf().optionalFieldOf("neck", List.of()).forGetter(HostDefinition::neck),
            Codec.STRING.optionalFieldOf("blood", "warm").forGetter(HostDefinition::blood),
            Codec.FLOAT.optionalFieldOf("decay_per_minute", 2f).forGetter(HostDefinition::decayPerMinute),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("attributes", Map.of()).forGetter(HostDefinition::attributes),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(HostDefinition::flags),
            Codec.STRING.listOf().optionalFieldOf("drawbacks", List.of()).forGetter(HostDefinition::drawbacks),
            Ability.CODEC.optionalFieldOf("primary").forGetter(HostDefinition::primary),
            Ability.CODEC.optionalFieldOf("secondary").forGetter(HostDefinition::secondary),
            Codec.STRING.listOf().optionalFieldOf("neutral_to", List.of()).forGetter(HostDefinition::neutralTo)
    ).apply(i, HostDefinition::new));

    /** The neck relative to the host's feet (x right, y up, z forward), or null to use the default. */
    public Vec3 neckOffset() {
        return neck.size() == 3 ? new Vec3(neck.get(0), neck.get(1), neck.get(2)) : null;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }
}
