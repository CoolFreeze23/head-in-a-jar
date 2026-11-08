package com.coolfreeze23.headinajar.genetics;

import java.util.List;
import java.util.Locale;

/**
 * A gene: which creatures carry it, its load (how much room it takes in a serum or port), its stability cost, and
 * whether it is an active power (R / G) or a passive trait. Effects are in {@link GeneEffects}.
 */
public enum Gene {
    SPIDER_CLIMB(Category.MOVEMENT, 2, 10, false, "minecraft:spider", "minecraft:cave_spider"),
    ENDER_BLINK(Category.MOVEMENT, 4, 20, true, "minecraft:enderman"),
    RABBIT_SPRING(Category.MOVEMENT, 1, 5, false, "minecraft:rabbit"),
    STRIDER_SOLES(Category.MOVEMENT, 3, 15, false, "minecraft:strider"),
    PHANTOM_MEMBRANE(Category.MOVEMENT, 3, 15, false, "minecraft:phantom", "minecraft:bat"),
    DOLPHIN_GRACE(Category.MOVEMENT, 2, 10, false, "minecraft:dolphin"),
    EQUINE_GAIT(Category.MOVEMENT, 2, 10, false, "minecraft:horse", "minecraft:camel", "minecraft:donkey", "minecraft:mule"),
    BREEZE_UPDRAFT(Category.MOVEMENT, 3, 15, true, "minecraft:breeze"),
    GILLS(Category.MOVEMENT, 2, 10, false, "minecraft:squid", "minecraft:cod", "minecraft:salmon", "minecraft:drowned", "minecraft:tropical_fish"),
    GOLEM_FRAME(Category.DEFENCE, 3, 15, false, "minecraft:iron_golem"),
    TURTLE_SHELL(Category.DEFENCE, 2, 10, false, "minecraft:turtle"),
    BLAZE_CORE(Category.DEFENCE, 3, 15, false, "minecraft:blaze", "minecraft:magma_cube"),
    SLIME_BODY(Category.DEFENCE, 2, 10, false, "minecraft:slime"),
    WITHER_WARD(Category.DEFENCE, 3, 15, false, "minecraft:wither_skeleton"),
    CAT_EYES(Category.SENSES, 1, 5, false, "minecraft:cat", "minecraft:ocelot"),
    GLOW_CELLS(Category.SENSES, 1, 5, false, "minecraft:glow_squid"),
    ECHOLOCATION(Category.SENSES, 1, 5, false, "minecraft:bat"),
    VENOM_SAC(Category.OFFENCE, 2, 10, false, "minecraft:cave_spider"),
    PACK_INSTINCT(Category.OFFENCE, 2, 10, false, "minecraft:wolf"),
    HOGLIN_TUSKS(Category.OFFENCE, 2, 10, false, "minecraft:hoglin"),
    GUARDIAN_EYE(Category.OFFENCE, 4, 20, true, "minecraft:guardian", "minecraft:elder_guardian"),
    CREEPER_GLAND(Category.OFFENCE, 3, 20, false, "minecraft:creeper"),
    AXOLOTL_REGROWTH(Category.METABOLISM, 2, 10, false, "minecraft:axolotl"),
    FEATHER_BONES(Category.METABOLISM, 1, 5, false, "minecraft:chicken"),
    HEROS_BLOOD(Category.METABOLISM, 1, 0, false, "minecraft:villager"),
    DRACONIC_WINGS(Category.LEGENDARY, 6, 30, false, "minecraft:ender_dragon"),
    SONIC_BOOM(Category.LEGENDARY, 6, 30, true, "minecraft:warden"),
    DECAY_AURA(Category.LEGENDARY, 6, 30, true, "minecraft:wither"),
    // mutations: never sequenced from a species, only from a mutated body
    HICCUPS(Category.MUTATION, 1, 0, false),
    CLUCKING(Category.MUTATION, 1, 0, false),
    BIOLUMINESCENCE(Category.MUTATION, 1, 0, false),
    BRITTLE_BONES(Category.MUTATION, 1, 0, false),
    PHOTOPHOBIA(Category.MUTATION, 1, 0, false),
    GLASS_JAW(Category.MUTATION, 1, 0, false),
    GIGANTISM(Category.MUTATION, 1, 0, false),
    SHRINKAGE(Category.MUTATION, 1, 0, false),
    MAGNETIC_PERSONALITY(Category.MUTATION, 1, 0, false),
    HYBRID_VIGOUR(Category.MUTATION, 1, 0, false),
    CHIMERIC_HARMONY(Category.MUTATION, 1, -10, false);

    public enum Category { MOVEMENT, DEFENCE, SENSES, OFFENCE, METABOLISM, LEGENDARY, MUTATION }

    public final Category category;
    public final int load, stability;
    public final boolean active;
    public final List<String> sources;

    Gene(Category category, int load, int stability, boolean active, String... sources) {
        this.category = category;
        this.load = load;
        this.stability = stability;
        this.active = active;
        this.sources = List.of(sources);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Genes a creature carries. */
    public static List<Gene> of(String entityId) {
        return java.util.Arrays.stream(values()).filter(g -> g.sources.contains(entityId)).toList();
    }

    /** Pairs that fight (-15 stability each) and pairs that help (+10). */
    public static final List<Gene[]> CONFLICTS = List.of(
            new Gene[]{BLAZE_CORE, GILLS}, new Gene[]{BLAZE_CORE, AXOLOTL_REGROWTH}, new Gene[]{ENDER_BLINK, GOLEM_FRAME},
            new Gene[]{CREEPER_GLAND, TURTLE_SHELL}, new Gene[]{CAT_EYES, ECHOLOCATION});
    public static final List<Gene[]> SYNERGIES = List.of(
            new Gene[]{SPIDER_CLIMB, VENOM_SAC}, new Gene[]{ENDER_BLINK, ECHOLOCATION}, new Gene[]{BLAZE_CORE, STRIDER_SOLES},
            new Gene[]{GLOW_CELLS, CAT_EYES}, new Gene[]{GOLEM_FRAME, HOGLIN_TUSKS});
}
