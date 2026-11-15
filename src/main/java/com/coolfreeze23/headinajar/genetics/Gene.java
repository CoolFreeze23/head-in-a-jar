package com.coolfreeze23.headinajar.genetics;

import java.util.List;
import java.util.Locale;

/**
 * A gene: which creatures carry it, its load (how much room it takes in a serum or port), its stability cost, and
 * whether it is an active power (R / G) or a passive trait. Effects are in {@link GeneEffects}.
 */
public enum Gene {
    SPIDER_CLIMB(Category.MOVEMENT, 2, 10, false, "minecraft:spider", "minecraft:cave_spider", "twilightforest:hedge_spider", "twilightforest:king_spider",
            "orespawn:emperor_scorpion"),
    ENDER_BLINK(Category.MOVEMENT, 4, 20, true, "minecraft:enderman"),
    RABBIT_SPRING(Category.MOVEMENT, 1, 5, false, "minecraft:rabbit"),
    STRIDER_SOLES(Category.MOVEMENT, 3, 15, false, "minecraft:strider"),
    PHANTOM_MEMBRANE(Category.MOVEMENT, 3, 15, false, "minecraft:phantom", "minecraft:bat"),
    DOLPHIN_GRACE(Category.MOVEMENT, 2, 10, false, "minecraft:dolphin"),
    EQUINE_GAIT(Category.MOVEMENT, 2, 10, false, "minecraft:horse", "minecraft:camel", "minecraft:donkey", "minecraft:mule"),
    BREEZE_UPDRAFT(Category.MOVEMENT, 3, 15, true, "minecraft:breeze"),
    GILLS(Category.MOVEMENT, 2, 10, false, "minecraft:squid", "minecraft:cod", "minecraft:salmon", "minecraft:drowned", "minecraft:tropical_fish"),
    GOLEM_FRAME(Category.DEFENCE, 3, 15, false, "minecraft:iron_golem", "friendsandfoes:copper_golem", "friendsandfoes:tuff_golem",
            "twilightforest:carminite_golem"),
    TURTLE_SHELL(Category.DEFENCE, 2, 10, false, "minecraft:turtle"),
    BLAZE_CORE(Category.DEFENCE, 3, 15, false, "minecraft:blaze", "minecraft:magma_cube", "friendsandfoes:wildfire"),
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
    // from other mods in the pack (inert when the mod is absent: nothing carries them)
    MOA_WINGS(Category.MOVEMENT, 3, 15, false, "aether:moa"),
    SACI_WHIRL(Category.MOVEMENT, 3, 15, true, "brazil_legends:saci_perere"),
    MULE_FIRE(Category.OFFENCE, 3, 15, false, "brazil_legends:headless_mule"),
    FROST_BREATH(Category.OFFENCE, 4, 20, true, "mowziesmobs:frostmaw", "friendsandfoes:iceologer"),
    MUTANT_MIGHT(Category.OFFENCE, 3, 15, false, "mutantmonsters:mutant_zombie", "mutantmonsters:mutant_skeleton"),
    PACHYDERM_HIDE(Category.DEFENCE, 2, 10, false, "naturalist:elephant", "naturalist:rhino", "naturalist:hippo"),
    PHOTOSYNTHESIS(Category.METABOLISM, 1, 5, false, "plantz:sunflower", "plantz:marigold"),
    REGROWTH(Category.LEGENDARY, 5, 25, false, "twilightforest:hydra"),
    BLAZING_SOUL(Category.LEGENDARY, 6, 30, false, "cataclysm:ignis"),
    ATOMIC_BREATH(Category.LEGENDARY, 6, 35, true, "orespawn:godzilla"),
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
    CHIMERIC_HARMONY(Category.MUTATION, 1, -10, false),
    // 0.6.2: a gene for every natural creature that had none (appended, so the ordinals above never move: the Splicer
    // and its sync use them). Stability 5 per load. Numbers are in GeneNumbers.
    RUMINANT_GUT(Category.METABOLISM, 1, 5, false, "minecraft:cow"),
    STEW_GLANDS(Category.METABOLISM, 2, 10, false, "minecraft:mooshroom"),
    SWINE_APPETITE(Category.METABOLISM, 1, 5, false, "minecraft:pig"),
    FLEECE(Category.DEFENCE, 2, 10, false, "minecraft:sheep"),
    MOUNTAIN_HOOVES(Category.MOVEMENT, 2, 10, false, "minecraft:goat"),
    PHASE(Category.MOVEMENT, 4, 20, true, "minecraft:vex"),
    BAMBOO_DIET(Category.METABOLISM, 1, 5, false, "minecraft:panda"),
    SNOW_POUNCE(Category.MOVEMENT, 3, 15, true, "minecraft:fox"),
    POLLINATOR(Category.METABOLISM, 2, 10, false, "minecraft:bee"),
    ARCTIC_COAT(Category.DEFENCE, 2, 10, false, "minecraft:polar_bear"),
    ROLL_UP(Category.DEFENCE, 3, 15, false, "minecraft:armadillo"),
    KEEN_NOSE(Category.SENSES, 1, 5, false, "minecraft:sniffer"),
    MIMICRY(Category.SENSES, 1, 5, false, "minecraft:parrot"),
    TONGUE(Category.OFFENCE, 3, 15, true, "minecraft:frog"),
    TADPOLE_TAIL(Category.MOVEMENT, 1, 5, false, "minecraft:tadpole"),
    PUFFER(Category.DEFENCE, 2, 10, false, "minecraft:pufferfish"),
    SPIT_GLAND(Category.OFFENCE, 2, 10, true, "minecraft:llama", "minecraft:trader_llama"),
    DUSK_DRAUGHT(Category.DEFENCE, 2, 10, false, "minecraft:wandering_trader"),
    UNDEATH(Category.METABOLISM, 3, 15, false, "minecraft:zombie"),
    SUN_BAKED(Category.DEFENCE, 2, 10, false, "minecraft:husk"),
    REHABILITATED(Category.METABOLISM, 2, 10, false, "minecraft:zombie_villager"),
    GILDED_ROT(Category.OFFENCE, 2, 10, false, "minecraft:zombified_piglin"),
    RAMPAGE(Category.OFFENCE, 3, 15, false, "minecraft:zoglin"),
    BOWSTRING_ARMS(Category.OFFENCE, 2, 10, false, "minecraft:skeleton"),
    RIME_ARROWS(Category.OFFENCE, 2, 10, false, "minecraft:stray"),
    SPORE_ARROWS(Category.OFFENCE, 2, 10, false, "minecraft:bogged"),
    UNDERTOW_STRIDE(Category.MOVEMENT, 2, 10, false, "minecraft:skeleton_horse"),
    SNOW_TRAIL(Category.DEFENCE, 2, 10, false, "minecraft:snow_golem"),
    GOLD_STANDARD(Category.DEFENCE, 2, 10, false, "minecraft:piglin"),
    BRUTE_STRENGTH(Category.OFFENCE, 3, 15, false, "minecraft:piglin_brute"),
    BREAKING_AND_ENTERING(Category.OFFENCE, 2, 10, false, "minecraft:vindicator"),
    STEADY_AIM(Category.OFFENCE, 2, 10, false, "minecraft:pillager"),
    FANG_LINE(Category.OFFENCE, 4, 20, true, "minecraft:evoker"),
    POTION_TOLERANCE(Category.METABOLISM, 3, 15, false, "minecraft:witch"),
    BULLDOZE(Category.OFFENCE, 4, 20, false, "minecraft:ravager"),
    WEEPING_GLANDS(Category.METABOLISM, 4, 20, false, "minecraft:ghast"),
    PEARL_DIGESTION(Category.MOVEMENT, 2, 10, false, "minecraft:endermite"),
    LEVITATION_BOLT(Category.OFFENCE, 4, 20, true, "minecraft:shulker"),
    STONE_LUNGS(Category.DEFENCE, 2, 10, false, "minecraft:silverfish"),
    FETCH(Category.SENSES, 2, 10, false, "minecraft:allay");

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

    /** The gene with this id, or null for an id this version doesn't know (a gene renamed or removed since). */
    public static Gene byId(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            com.coolfreeze23.headinajar.HeadInAJar.LOGGER.warn("Skipping unknown gene '{}'", id);
            return null;
        }
    }

    /** Genes a creature carries. */
    public static List<Gene> of(String entityId) {
        return java.util.Arrays.stream(values()).filter(g -> g.sources.contains(entityId)).toList();
    }

    /**
     * Pairs that fight (-15 stability each) and pairs that help (+10), counted per strand (one serum). Each pair's name
     * or the Splicer's line is {@code synergy.headinajar.<a>.<b>} / {@code conflict.headinajar.<a>.<b>} (ids in this
     * order), a synergy's comment {@code ...<a>.<b>.comment} where it does something extra.
     */
    public static final List<Gene[]> CONFLICTS = List.of(
            new Gene[]{BLAZE_CORE, GILLS}, new Gene[]{BLAZE_CORE, AXOLOTL_REGROWTH}, new Gene[]{ENDER_BLINK, GOLEM_FRAME},
            new Gene[]{CREEPER_GLAND, TURTLE_SHELL}, new Gene[]{CAT_EYES, ECHOLOCATION},
            // 0.6.2
            new Gene[]{SNOW_POUNCE, RABBIT_SPRING}, new Gene[]{ROLL_UP, SPIDER_CLIMB}, new Gene[]{PEARL_DIGESTION, ENDER_BLINK},
            new Gene[]{ARCTIC_COAT, BLAZE_CORE}, new Gene[]{SNOW_TRAIL, BLAZE_CORE}, new Gene[]{UNDEATH, AXOLOTL_REGROWTH},
            new Gene[]{BREAKING_AND_ENTERING, HEROS_BLOOD}, new Gene[]{REHABILITATED, BREAKING_AND_ENTERING},
            new Gene[]{BRUTE_STRENGTH, GOLD_STANDARD}, new Gene[]{SPORE_ARROWS, RIME_ARROWS}, new Gene[]{TONGUE, SPIT_GLAND},
            new Gene[]{BULLDOZE, POLLINATOR}, new Gene[]{PHASE, GOLEM_FRAME});
    public static final List<Gene[]> SYNERGIES = List.of(
            new Gene[]{SPIDER_CLIMB, VENOM_SAC}, new Gene[]{ENDER_BLINK, ECHOLOCATION}, new Gene[]{BLAZE_CORE, STRIDER_SOLES},
            new Gene[]{GLOW_CELLS, CAT_EYES}, new Gene[]{GOLEM_FRAME, HOGLIN_TUSKS},
            // 0.6.2
            new Gene[]{FLEECE, ARCTIC_COAT}, new Gene[]{SNOW_TRAIL, ARCTIC_COAT}, new Gene[]{UNDEATH, SUN_BAKED},
            new Gene[]{SPIT_GLAND, DUSK_DRAUGHT}, new Gene[]{SPIT_GLAND, VENOM_SAC}, new Gene[]{MIMICRY, ECHOLOCATION},
            new Gene[]{KEEN_NOSE, ECHOLOCATION}, new Gene[]{POLLINATOR, STEW_GLANDS}, new Gene[]{POTION_TOLERANCE, STEW_GLANDS},
            new Gene[]{ROLL_UP, TURTLE_SHELL}, new Gene[]{BOWSTRING_ARMS, RIME_ARROWS}, new Gene[]{RAMPAGE, HOGLIN_TUSKS},
            new Gene[]{GOLD_STANDARD, GILDED_ROT}, new Gene[]{REHABILITATED, HEROS_BLOOD}, new Gene[]{PHASE, STONE_LUNGS},
            new Gene[]{LEVITATION_BOLT, BREEZE_UPDRAFT}, new Gene[]{BULLDOZE, GOLEM_FRAME},
            new Gene[]{FANG_LINE, BREAKING_AND_ENTERING}, new Gene[]{SNOW_POUNCE, CAT_EYES}, new Gene[]{UNDERTOW_STRIDE, GILLS},
            new Gene[]{WEEPING_GLANDS, GUARDIAN_EYE}, new Gene[]{MOUNTAIN_HOOVES, RABBIT_SPRING});

    /** The named pairs a strand holds (synergies or conflicts), in table order. */
    public static List<Gene[]> pairsIn(java.util.Collection<Gene> strand, boolean synergies) {
        List<Gene[]> out = new java.util.ArrayList<>();
        for (Gene[] pair : synergies ? SYNERGIES : CONFLICTS) if (strand.contains(pair[0]) && strand.contains(pair[1])) out.add(pair);
        return out;
    }

    /** A pair's language key: {@code synergy.headinajar.<a>.<b>} or {@code conflict.headinajar.<a>.<b>}. */
    public static String pairKey(Gene[] pair, boolean synergy) {
        return (synergy ? "synergy" : "conflict") + ".headinajar." + pair[0].id() + "." + pair[1].id();
    }

    /** Whether this gene came in 0.6.2 or later (it has a written cost line; the 49 before it have none). */
    public boolean hasCostLine() {
        return ordinal() > CHIMERIC_HARMONY.ordinal();
    }

    /**
     * The gene's department: its first installed source species' department (the entity type tags; the Ender Dragon's
     * is The Board); a gene with no installed species, or a mutation, is General Staff. Readable on both sides.
     */
    public String department() {
        for (String id : sources) {
            var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl == null) continue;
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
            if (type.isPresent()) return com.coolfreeze23.headinajar.staff.Staff.department(type.get());
        }
        return com.coolfreeze23.headinajar.staff.Staff.GENERAL;
    }
}
