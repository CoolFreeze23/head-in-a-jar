package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Splicing math (stability, conflicts, synergies, catalysts), mutation, blood-type rejection and injection. */
public final class Genetics {
    private Genetics() {
    }

    public enum Target {
        /** a mecha's Gene Port: never rejects, load 8 per port */
        MECHA(8, 3),
        /** a mob host: one serum, load 6 */
        HOST(6, 1),
        /** your own body: two serums, load 3 each */
        BODY(3, 2);

        public final int loadCap, serums;

        Target(int loadCap, int serums) {
            this.loadCap = loadCap;
            this.serums = serums;
        }
    }

    public enum Result { OK, MUTATED, REJECTED, FULL, TOO_HEAVY }

    public static GeneSet genes(LivingEntity entity) {
        return entity.getData(ModRegistry.GENES);
    }

    public static int catalystBonus(ItemStack catalyst) {
        if (catalyst.is(Items.NETHER_STAR)) return 40;
        if (catalyst.is(Items.ECHO_SHARD)) return 20;
        if (catalyst.is(Items.AMETHYST_SHARD)) return 10;
        if (catalyst.is(Items.SLIME_BALL)) return 5;
        return 0;
    }

    /** Stability of a strand: 100, less each gene's cost at its band, -15 per conflict, +10 per synergy, plus the catalyst. */
    public static int stability(List<Gene> strand, Map<Gene, Float> readouts, int catalyst) {
        float s = 100;
        for (Gene g : strand) s -= g.stability * GeneArchive.costFactor(readouts.getOrDefault(g, 100f));
        Set<Gene> set = new HashSet<>(strand);
        for (Gene[] pair : Gene.CONFLICTS) if (set.contains(pair[0]) && set.contains(pair[1])) s -= 15;
        for (Gene[] pair : Gene.SYNERGIES) if (set.contains(pair[0]) && set.contains(pair[1])) s += 10;
        return Math.max(0, Math.min(100, Math.round(s) + catalyst));
    }

    /** Chance (0-1) that injecting a serum of this stability mutates: none at 60+, 60% at 20. */
    public static float mutationChance(int stability) {
        return Math.max(0, 60 - stability) * 0.015f;
    }

    /** Builds a serum from a strand at the potency of each gene's readout. */
    public static Serum splice(List<Gene> strand, Map<Gene, Float> readouts, int catalyst) {
        List<Serum.Entry> entries = new ArrayList<>();
        for (Gene g : strand) entries.add(new Serum.Entry(g, GeneArchive.potency(readouts.getOrDefault(g, 0f))));
        return new Serum(entries, stability(strand, readouts, catalyst));
    }

    /** Whether a gene's source creatures share a compatible blood type with the target. */
    public static boolean compatible(Gene gene, BloodType target) {
        if (gene.sources.isEmpty()) return true;
        for (String id : gene.sources) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id));
            if (type.isPresent() && BloodType.of(type.get()).compatible(target)) return true;
        }
        return false;
    }

    /**
     * Injects a serum. Hosts and bodies may reject genes of an incompatible blood type (30%, unless Hero's Blood is in the
     * serum or the body); then a mutation may roll. Mecha never reject.
     */
    public static Result inject(LivingEntity target, Serum serum, Target kind, RandomSource random) {
        GeneSet set = genes(target);
        if (serum.load() > kind.loadCap) return Result.TOO_HEAVY;
        if (set.serums.size() >= kind.serums) return Result.FULL;
        if (kind != Target.MECHA) {
            BloodType blood = BloodType.of(target);
            boolean donor = serum.genes().stream().anyMatch(e -> e.gene() == Gene.HEROS_BLOOD) || set.has(Gene.HEROS_BLOOD);
            boolean mismatch = serum.genes().stream().anyMatch(e -> !compatible(e.gene(), blood));
            if (mismatch && !donor && random.nextFloat() < 0.3f) {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
                return Result.REJECTED;
            }
        }
        Result result = Result.OK;
        if (random.nextFloat() < mutationChance(serum.stability())) {
            Gene mutation = rollMutation(random);
            if (mutation == null) return Result.REJECTED;
            if (!set.mutations.contains(mutation)) set.mutations.add(mutation);
            result = Result.MUTATED;
        }
        set.serums.add(serum);
        set.changed();
        return result;
    }

    /** The mutation table: common silly/bad ones, an uncommon dud (null: the serum is inert), rare good ones. */
    public static Gene rollMutation(RandomSource random) {
        int roll = random.nextInt(100);
        Gene[] common = {Gene.HICCUPS, Gene.CLUCKING, Gene.BIOLUMINESCENCE, Gene.BRITTLE_BONES, Gene.PHOTOPHOBIA, Gene.GLASS_JAW,
                Gene.GIGANTISM, Gene.SHRINKAGE, Gene.MAGNETIC_PERSONALITY};
        if (roll < 80) return common[random.nextInt(common.length)];
        if (roll < 92) return null;
        return random.nextBoolean() ? Gene.HYBRID_VIGOUR : Gene.CHIMERIC_HARMONY;
    }

    /** Sequencing gain for each of a creature's genes: quality x accuracy x 0.25 / number of its genes. */
    public static float readGain(int quality, float accuracy, int geneCount) {
        return geneCount <= 0 ? 0 : quality * accuracy * 0.25f / geneCount;
    }
}
